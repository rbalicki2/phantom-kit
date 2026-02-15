#!/usr/bin/env bb
;; Karabiner Condition Ordering Validator
;;
;; Validates condition ordering as a DAG (directed acyclic graph):
;;
;; CANONICAL STRUCTURE:
;;   Device (required, first)
;;     └── Layer (required, second)
;;           ├── App (leaf) - mutually exclusive with:
;;           ├── Sub-mode (leaf, only Layer 1) - mutually exclusive with:
;;           └── Return-to-layer (leaf, only Layer 13)
;;
;; RULES:
;;   1. Device must come first (if present)
;;   2. Layer must come after device
;;   3. App/sub-mode/return-to-layer are leaves - only ONE can be present
;;   4. Leaf-to-root ordering: specific rules before general for same key
;;
;; Usage:
;;   bb validate-ordering.bb <edn-file>

(ns validate-ordering
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]))

;; ============================================================================
;; Condition Categories
;; ============================================================================

;; Condition types in canonical order
(def condition-order
  {:device 1    ;; :!apple_internal, :apple_internal
   :layer 2     ;; dsk_layer
   :leaf 3})    ;; app, dsk_ins_sub_mode, dsk_return_to_layer (mutually exclusive)

;; Layer variables: base vs leaf
(def base-layer-variables #{:dsk_layer})
(def leaf-layer-variables #{:dsk_ins_sub_mode :dsk_return_to_layer})

;; Device conditions
(def device-conditions #{:!apple_internal :apple_internal})

;; ============================================================================
;; Condition Extraction
;; ============================================================================

(defn get-app-conditions [config]
  (set (keys (:applications config))))

(defn categorize-condition [condition config]
  "Categorize a condition. Returns {:category :type :value ...} or nil."
  (let [apps (get-app-conditions config)]
    (cond
      ;; Device keyword
      (and (keyword? condition) (device-conditions condition))
      {:category :device :value condition}

      ;; App keyword
      (and (keyword? condition) (contains? apps condition))
      {:category :leaf :type :app :value condition}

      ;; Profile keyword - ignore
      (= condition :Desktop) nil

      ;; Variable condition
      (and (vector? condition) (= 2 (count condition)) (string? (first condition)))
      (let [var-name (keyword (first condition))
            value (second condition)]
        (cond
          (base-layer-variables var-name)
          {:category :layer :variable var-name :value value}

          (leaf-layer-variables var-name)
          {:category :leaf :type :variable :variable var-name :value value}

          :else nil))

      :else nil)))

(defn extract-all-conditions [rule-block rule config]
  "Extract all conditions from block and rule."
  (let [rules-vec (:rules rule-block)
        block-kws (when (vector? rules-vec)
                    (take-while keyword? rules-vec))
        block-conds (->> block-kws
                         (map #(categorize-condition % config))
                         (remove nil?))

        rule-cond (when (>= (count rule) 3) (nth rule 2))
        rule-cond-info (categorize-condition rule-cond config)]
    {:block-conditions (vec block-conds)
     :rule-condition rule-cond-info
     :all-conditions (vec (concat block-conds (when rule-cond-info [rule-cond-info])))}))

;; ============================================================================
;; Condition Signature for DAG
;; ============================================================================

(defn condition-to-fact [cond-info]
  "Convert a condition to a fact for signature comparison"
  (case (:category cond-info)
    :device [:device (:value cond-info)]
    :layer [:layer (:variable cond-info) (:value cond-info)]
    :leaf (if (= (:type cond-info) :app)
            [:app (:value cond-info)]
            [:var (:variable cond-info) (:value cond-info)])
    nil))

(defn conditions-to-signature [conditions]
  "Convert conditions to a set of facts"
  (->> conditions
       (map condition-to-fact)
       (remove nil?)
       set))

;; ============================================================================
;; Validation 1: Condition Order
;; ============================================================================

(defn check-condition-order [rule-info]
  "Check that conditions follow: device → layer → leaf"
  (let [all-conds (:all-conditions rule-info)
        order-nums (map #(condition-order (:category %)) all-conds)]
    (when (seq order-nums)
      (let [out-of-order? (some (fn [[a b]] (and a b (> a b)))
                               (partition 2 1 order-nums))]
        (when out-of-order?
          (let [categories (map :category all-conds)]
            {:type :condition-order-violation
             :description (:description rule-info)
             :message (format "Conditions out of order. Expected: device → layer → leaf. Got: %s"
                             (str/join " → " (map name categories)))
             :rule (:rule rule-info)}))))))

;; ============================================================================
;; Validation 2: Multiple Leaves
;; ============================================================================

(defn check-multiple-leaves [rule-info]
  "Check that only one leaf condition is present"
  (let [leaves (->> (:all-conditions rule-info)
                    (filter #(= (:category %) :leaf)))]
    (when (> (count leaves) 1)
      {:type :multiple-leaf-conditions
       :description (:description rule-info)
       :message (format "Multiple leaf conditions found. Only one allowed: %s"
                       (pr-str leaves))
       :rule (:rule rule-info)})))

;; ============================================================================
;; Validation 3: Leaf Variable in Wrong Layer
;; ============================================================================

(defn check-leaf-variable-context [rule-info]
  "Check that dsk_ins_sub_mode only appears with layer 1, etc."
  (let [all-conds (:all-conditions rule-info)
        layer-cond (first (filter #(and (= (:category %) :layer)
                                        (= (:variable %) :dsk_layer)) all-conds))
        leaf-cond (first (filter #(and (= (:category %) :leaf)
                                       (= (:type %) :variable)) all-conds))]
    (when (and layer-cond leaf-cond)
      (let [layer-val (:value layer-cond)
            leaf-var (:variable leaf-cond)]
        (cond
          (and (= leaf-var :dsk_ins_sub_mode) (not= layer-val 1))
          {:type :invalid-leaf-context
           :description (:description rule-info)
           :message (format "dsk_ins_sub_mode used with layer %d, but only valid in layer 1" layer-val)
           :rule (:rule rule-info)}

          (and (= leaf-var :dsk_return_to_layer) (not= layer-val 13))
          {:type :invalid-leaf-context
           :description (:description rule-info)
           :message (format "dsk_return_to_layer used with layer %d, but only valid in layer 13" layer-val)
           :rule (:rule rule-info)}

          :else nil)))))

;; ============================================================================
;; Validation 4: DAG Ordering (Leaf-to-Root)
;; ============================================================================

(defn signature-is-subset? [sig1 sig2]
  "Check if sig1 is a proper subset of sig2"
  (and (not= sig1 sig2)
       (set/subset? sig1 sig2)))

(defn signatures-compatible? [sig1 sig2]
  "Check if two signatures could match the same input"
  (let [get-vars (fn [sig] (->> sig (filter #(#{:layer :var} (first %))) set))
        v1 (get-vars sig1)
        v2 (get-vars sig2)
        ;; Conflict if same variable has different value
        conflict? (some (fn [[type var val :as fact]]
                         (some (fn [[type2 var2 val2]]
                                (and (= type type2) (= var var2) (not= val val2)))
                               v2))
                       v1)]
    (not conflict?)))

(defn find-dag-violation [rule-info earlier-rules]
  "Check if a more general rule appears before this specific rule"
  (let [current-sig (:signature rule-info)
        from-key (first (:rule rule-info))]
    (some (fn [earlier]
            (when (= (first (:rule earlier)) from-key)
              (let [earlier-sig (:signature earlier)]
                (when (and (signature-is-subset? earlier-sig current-sig)
                           (signatures-compatible? earlier-sig current-sig))
                  {:type :dag-ordering-violation
                   :description (:description rule-info)
                   :message (format "More general rule in '%s' shadows this specific rule"
                                   (:description earlier))
                   :rule (:rule rule-info)
                   :earlier-rule (:rule earlier)
                   :current-sig current-sig
                   :earlier-sig earlier-sig}))))
          earlier-rules)))

(defn find-specific-after-global [rule-info earlier-rules]
  "Check for rules with conditions after unconditional rules"
  (let [current-sig (:signature rule-info)
        from-key (first (:rule rule-info))]
    (when (not-empty current-sig)
      (some (fn [earlier]
              (when (and (= (first (:rule earlier)) from-key)
                         (empty? (:signature earlier)))
                {:type :specific-after-global
                 :description (:description rule-info)
                 :message (format "Rule with conditions after global rule in '%s'"
                                 (:description earlier))
                 :rule (:rule rule-info)
                 :global-rule (:rule earlier)}))
            earlier-rules))))

;; ============================================================================
;; Main Validation
;; ============================================================================

(defn extract-all-rules [config]
  "Extract all rules with condition info"
  (for [block (:main config)
        :let [rules-vec (:rules block)
              actual-rules (when (vector? rules-vec)
                            (->> rules-vec
                                 (drop-while keyword?)
                                 (filter vector?)))]
        [idx rule] (map-indexed vector actual-rules)
        :let [cond-info (extract-all-conditions block rule config)
              signature (conditions-to-signature (:all-conditions cond-info))]]
    (merge cond-info
           {:description (:des block)
            :rule rule
            :index idx
            :signature signature})))

(defn validate-ordering [config]
  "Run all ordering validations"
  (let [all-rules (vec (extract-all-rules config))

        ;; Check condition order
        order-issues (keep check-condition-order all-rules)

        ;; Check multiple leaves
        leaf-issues (keep check-multiple-leaves all-rules)

        ;; Check leaf context
        context-issues (keep check-leaf-variable-context all-rules)

        ;; Check DAG ordering
        dag-issues
        (loop [remaining all-rules
               seen []
               issues []]
          (if (empty? remaining)
            issues
            (let [current (first remaining)
                  dag-issue (find-dag-violation current seen)
                  global-issue (find-specific-after-global current seen)
                  new-issues (remove nil? [dag-issue global-issue])]
              (recur (rest remaining)
                     (conj seen current)
                     (into issues new-issues)))))]

    (concat order-issues leaf-issues context-issues dag-issues)))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn print-issue [issue]
  (println (str "  [" (name (:type issue)) "]"))
  (println (str "  Block: " (:description issue)))
  (println (str "  " (:message issue)))
  (println (str "  Rule: " (pr-str (:rule issue))))
  (when (:earlier-rule issue)
    (println (str "  Earlier: " (pr-str (:earlier-rule issue)))))
  (when (:global-rule issue)
    (println (str "  Global: " (pr-str (:global-rule issue)))))
  (println))

(defn -main [& args]
  (when (empty? args)
    (println "Usage: bb validate-ordering.bb <edn-file>")
    (System/exit 1))

  (let [edn-file (first args)
        config (edn/read-string (slurp edn-file))
        issues (validate-ordering config)
        by-type (group-by :type issues)]

    (println "=== Karabiner Ordering Validation ===")
    (println (str "File: " edn-file))
    (println (str "Total issues: " (count issues)))
    (println)

    (if (empty? issues)
      (println "✓ No ordering issues found!")
      (doseq [[issue-type type-issues] (sort-by first by-type)]
        (println (str "--- " (name issue-type) " (" (count type-issues) " issues) ---"))
        (doseq [issue type-issues]
          (print-issue issue))))

    (System/exit (if (empty? issues) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
