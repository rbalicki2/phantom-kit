#!/usr/bin/env bb

;; Generic Karabiner Rule Simulator
;;
;; Simulates rule matching given an EDN config, initial state, and key input.
;; This is the core engine that can work with any Karabiner/Goku config.
;;
;; Usage as library:
;;   (load-file "scripts/lib/simulator.bb")
;;   (require '[lib.simulator :as sim])
;;   (sim/simulate config state {:key :l :modifiers [:shift]})

(ns lib.simulator
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

;; ============================================================================
;; Rule Extraction
;; ============================================================================

(defn extract-block-conditions [rules-vec]
  "Extract block-level conditions (keywords and variable conditions before rules)"
  (when (sequential? rules-vec)
    (vec (take-while #(or (keyword? %)
                          (and (vector? %) (not (map? (first %)))))
                     rules-vec))))

(defn extract-rules-from-block [block]
  "Extract all rules from a block with their conditions"
  (let [des (:des block)
        rules-vec (:rules block)
        block-conditions (extract-block-conditions rules-vec)
        actual-rules (if block-conditions
                       (drop (count block-conditions) rules-vec)
                       rules-vec)]
    (for [rule actual-rules
          :when (vector? rule)]
      {:description des
       :block-conditions block-conditions
       :rule rule
       :from (first rule)
       :to (second rule)
       :rule-conditions (nth rule 2 nil)
       :options (nth rule 3 nil)})))

(defn extract-all-rules [config]
  "Extract all rules from config in order"
  (mapcat extract-rules-from-block (:main config)))

;; ============================================================================
;; Condition Matching
;; ============================================================================

(defn matches-device-condition? [condition state]
  "Check if a device condition matches the state"
  (case condition
    :!apple_internal (= (:device state) :desktop)
    :apple_internal (= (:device state) :laptop)
    :Desktop (= (:device state) :desktop)
    :Laptop (= (:device state) :laptop)
    ;; App conditions - check if current app matches
    (if (keyword? condition)
      (or (= (:app state) condition)
          (nil? (:app state)))  ;; If no app specified, allow
      true)))

(defn matches-variable-condition? [condition state]
  "Check if a variable condition [\"var_name\" value] matches state"
  (when (and (vector? condition)
             (= 2 (count condition))
             (string? (first condition)))
    (let [[var-name expected-value] condition
          actual-value (get-in state [:variables (keyword var-name)])]
      (= actual-value expected-value))))

(defn matches-conditions? [conditions state]
  "Check if all conditions match the current state"
  (if (nil? conditions)
    true
    (let [cond-list (if (and (vector? conditions)
                             (vector? (first conditions)))
                      conditions  ;; Already a list of conditions
                      [conditions])]  ;; Single condition, wrap it
      (every? (fn [c]
                (cond
                  (keyword? c) (matches-device-condition? c state)
                  (vector? c) (matches-variable-condition? c state)
                  :else true))
              cond-list))))

(defn matches-block-conditions? [block-conditions state]
  "Check if block-level conditions match"
  (every? (fn [c]
            (cond
              (keyword? c) (matches-device-condition? c state)
              (vector? c) (matches-variable-condition? c state)
              :else true))
          block-conditions))

;; ============================================================================
;; Key Matching
;; ============================================================================

(defn normalize-key [key-input]
  "Normalize a key input to {:key :xxx :modifiers [...] :pkey ...}"
  (cond
    (keyword? key-input) {:key key-input :modifiers []}
    (map? key-input) key-input
    :else {:key key-input :modifiers []}))

(defn parse-goku-key [from-clause]
  "Parse a Goku from clause into {:key :xxx :required-mods [...] :optional-mods [...]}"
  (if (map? from-clause)
    (let [key-val (or (:key from-clause) (:pkey from-clause))
          modi (:modi from-clause)
          mandatory (get modi :mandatory [])
          optional (get modi :optional [])
          ;; IMPORTANT: In Karabiner/Goku, if no :modi is specified at all,
          ;; the rule matches ANY modifiers. Only if :modi is specified
          ;; (even with empty mandatory/optional) does it restrict modifiers.
          no-modi-clause (nil? modi)]
      {:key key-val
       :pkey (:pkey from-clause)
       :required-mods (set mandatory)
       :optional-mods (set optional)
       :has-any-optional (or no-modi-clause (some #{:any} optional))})
    ;; Bare keyword - parse Goku shorthand
    (if (keyword? from-clause)
      (let [key-str (name from-clause)]
        (if (str/starts-with? key-str "!")
          ;; Has modifier prefix like !Sj or !CSj
          (let [mod-part (re-find #"![A-Z]+" key-str)
                base-key (keyword (subs key-str (count mod-part)))
                mods (set (for [c (subs mod-part 1)]
                           (case c
                             \S :left_shift
                             \R :right_shift
                             \C :left_command
                             \Q :right_command
                             \T :left_control
                             \O :left_option
                             \E :right_option
                             nil)))]
            {:key base-key :required-mods mods :optional-mods #{} :has-any-optional false})
          ;; Check for ## prefix (optional any)
          (if (str/starts-with? key-str "##")
            {:key (keyword (subs key-str 2))
             :required-mods #{}
             :optional-mods #{}
             :has-any-optional true}
            {:key from-clause :required-mods #{} :optional-mods #{} :has-any-optional false})))
      {:key from-clause :required-mods #{} :optional-mods #{} :has-any-optional false})))

;; Generic modifiers that match either left or right variant
(def generic-modifier-expansions
  {:shift #{:left_shift :right_shift :shift}
   :control #{:left_control :right_control :control}
   :command #{:left_command :right_command :command}
   :option #{:left_option :right_option :option}})

(defn expands-to-match? [required-mod input-mod]
  "Check if a required modifier matches an input modifier.
   Handles generic modifiers like :shift matching :left_shift or :right_shift"
  (or (= required-mod input-mod)
      ;; Check if required is generic and input is specific
      (when-let [variants (get generic-modifier-expansions required-mod)]
        (contains? variants input-mod))
      ;; Check if input is generic and required is specific
      (some (fn [[generic variants]]
              (and (contains? variants required-mod)
                   (= generic input-mod)))
            generic-modifier-expansions)))

(defn required-mod-satisfied? [required-mod input-mod-set]
  "Check if a required modifier is satisfied by the input modifier set"
  (some #(expands-to-match? required-mod %) input-mod-set))

(defn input-mod-allowed? [input-mod required-mods optional-mods]
  "Check if an input modifier is allowed by the rule"
  (or (= input-mod :caps_lock)  ;; caps_lock always allowed
      (some #(expands-to-match? % input-mod) required-mods)
      (some #(expands-to-match? % input-mod) optional-mods)))

(defn modifier-matches? [required-mods optional-mods has-any-optional input-mods]
  "Check if input modifiers match the rule's modifier requirements"
  (let [input-mod-set (set input-mods)]
    (cond
      ;; Rule allows any modifiers
      has-any-optional
      (every? #(required-mod-satisfied? % input-mod-set) required-mods)

      ;; Rule has specific requirements
      :else
      (and
       ;; All required mods must be present
       (every? #(required-mod-satisfied? % input-mod-set) required-mods)
       ;; No extra mods beyond allowed
       (every? #(input-mod-allowed? % required-mods optional-mods) input-mod-set)))))

(defn key-matches? [from-clause key-input]
  "Check if a key input matches a rule's from clause"
  (let [parsed (parse-goku-key from-clause)
        normalized (normalize-key key-input)]
    (and
     ;; Key must match
     (= (:key parsed) (:key normalized))
     ;; Modifiers must match
     (modifier-matches? (:required-mods parsed)
                        (:optional-mods parsed)
                        (:has-any-optional parsed)
                        (:modifiers normalized)))))

;; ============================================================================
;; Rule Matching
;; ============================================================================

(defn rule-matches? [rule-info state key-input]
  "Check if a rule matches given state and key input"
  (and
   ;; Block conditions match
   (matches-block-conditions? (:block-conditions rule-info) state)
   ;; Rule conditions match
   (matches-conditions? (:rule-conditions rule-info) state)
   ;; Key matches
   (key-matches? (:from rule-info) key-input)))

(defn find-matching-rule [config state key-input]
  "Find the first matching rule for the given state and key input"
  (let [all-rules (extract-all-rules config)]
    (first (filter #(rule-matches? % state key-input) all-rules))))

(defn find-all-matching-rules [config state key-input]
  "Find ALL matching rules (for debugging shadowing)"
  (let [all-rules (extract-all-rules config)]
    (filter #(rule-matches? % state key-input) all-rules)))

;; ============================================================================
;; Action Execution
;; ============================================================================

(defn extract-variable-sets [action]
  "Extract variable sets from an action"
  (when (vector? action)
    (->> action
         (filter #(and (vector? %)
                       (= 2 (count %))
                       (string? (first %))))
         (map (fn [[var-name value]] [(keyword var-name) value]))
         (into {}))))

(defn extract-key-output [action]
  "Extract the output key(s) from an action"
  (cond
    (keyword? action) action
    (vector? action) (first (filter keyword? action))
    :else nil))

(defn apply-action [state action]
  "Apply an action to the state, returning new state and output"
  (let [var-sets (extract-variable-sets action)
        key-output (extract-key-output action)
        new-variables (merge (:variables state) var-sets)]
    {:new-state (assoc state :variables new-variables)
     :output key-output
     :variable-changes var-sets}))

;; ============================================================================
;; Simulation
;; ============================================================================

(defn simulate-key [config state key-input]
  "Simulate pressing a key, returning result with new state"
  (let [matching-rule (find-matching-rule config state key-input)
        all-matches (find-all-matching-rules config state key-input)]
    (if matching-rule
      (let [{:keys [new-state output variable-changes]}
            (apply-action state (:to matching-rule))]
        {:matched true
         :rule-id (get-in matching-rule [:from :id])
         :rule-description (:description matching-rule)
         :from (:from matching-rule)
         :to (:to matching-rule)
         :output output
         :variable-changes variable-changes
         :new-state new-state
         :shadowed-rules (rest all-matches)})
      {:matched false
       :new-state state
       :output key-input  ;; Pass through
       :variable-changes {}
       :shadowed-rules []})))

(defn simulate-sequence [config initial-state key-sequence]
  "Simulate a sequence of keys, returning all intermediate states"
  (loop [state initial-state
         keys key-sequence
         results []]
    (if (empty? keys)
      {:final-state state
       :steps results}
      (let [key-input (first keys)
            result (simulate-key config state key-input)
            new-state (:new-state result)]
        (recur new-state
               (rest keys)
               (conj results (assoc result :input key-input :state-before state)))))))
