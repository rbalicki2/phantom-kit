#!/usr/bin/env bb
;; Karabiner Rule Validator
;;
;; Validates karabiner.edn rules for:
;; - Unreachable rules (conditions can never be satisfied given invariants)
;; - Redundant rules (always shadowed by earlier rules)
;; - Invariant violations (rules that assume impossible state combinations)
;;
;; Usage:
;;   bb validate-rules.bb <edn-file>

(ns validate-rules
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]))

;; Load shared state library
(load-file (str (System/getProperty "user.dir") "/scripts/lib/state.bb"))

;; Import from state library
(def layer-names state/layer-names)
(def valid-submode-for-layer? state/valid-submode-for-layer?)
(def valid-return-to-for-layer? state/valid-return-to-for-layer?)

;; ============================================================================
;; Condition Analysis
;; ============================================================================

(defn parse-condition [condition]
  "Parse a rule condition into a map of variable constraints.
   Returns nil if condition is nil or not a variable condition."
  (when (and (vector? condition)
             (= 2 (count condition))
             (string? (first condition)))
    (let [[var-name value] condition]
      {(keyword var-name) value})))

(defn extract-conditions-from-rule [rule]
  "Extract all variable conditions from a rule.
   Rule format: [from to] or [from to condition] or [from to condition options]"
  (when (and (vector? rule) (>= (count rule) 3))
    (let [condition (nth rule 2)]
      (parse-condition condition))))

(defn conditions-are-contradictory? [cond1 cond2]
  "Check if two condition maps contradict each other"
  (when (and cond1 cond2)
    (some (fn [[k v]]
            (let [v2 (get cond2 k)]
              (and v2 (not= v v2))))
          cond1)))

;; ============================================================================
;; Rule Block Analysis
;; ============================================================================

(defn extract-rules-from-block [rule-block]
  "Extract individual rules from a rule block with metadata"
  (let [des (:des rule-block)
        rules-vec (:rules rule-block)
        ;; Skip device/app conditions at the start
        actual-rules (when (vector? rules-vec)
                       (->> rules-vec
                            (drop-while keyword?)
                            (filter vector?)))]
    (map-indexed
      (fn [idx r]
        {:description des
         :rule r
         :index idx
         :from (first r)
         :to (second r)
         :condition (when (>= (count r) 3) (nth r 2))})
      actual-rules)))

(defn get-block-device-condition [rule-block]
  "Get device condition from block (:!apple_internal or :apple_internal)"
  (let [rules-vec (:rules rule-block)
        first-item (when (vector? rules-vec) (first rules-vec))]
    (cond
      (= first-item :!apple_internal) :external-only
      (= first-item :apple_internal) :internal-only
      :else :any)))

(defn get-block-app-conditions [rule-block applications]
  "Get app conditions from block"
  (let [rules-vec (:rules rule-block)
        app-keywords (when (vector? rules-vec)
                       (->> rules-vec
                            (filter keyword?)
                            (remove #{:!apple_internal :apple_internal})
                            (filter #(contains? applications %))))]
    (if (empty? app-keywords)
      :any
      (set app-keywords))))

;; ============================================================================
;; Invariant Violation Detection
;; ============================================================================

(defn check-submode-invariant [rule-info]
  "Check if rule violates the dsk_ins_sub_mode invariant"
  (let [cond-map (extract-conditions-from-rule (:rule rule-info))]
    (when cond-map
      (let [layer (get cond-map :dsk_layer)
            submode (get cond-map :dsk_ins_sub_mode)]
        (when (and layer submode (not (valid-submode-for-layer? layer submode)))
          {:type :submode-invariant-violation
           :description (:description rule-info)
           :message (format "Layer %d (%s) should have dsk_ins_sub_mode=-1, but condition has %d"
                           layer (get layer-names layer "unknown") submode)
           :rule (:rule rule-info)})))))

(defn check-return-to-invariant [rule-info]
  "Check if rule violates the dsk_return_to_layer invariant"
  (let [cond-map (extract-conditions-from-rule (:rule rule-info))]
    (when cond-map
      (let [layer (get cond-map :dsk_layer)
            return-to (get cond-map :dsk_return_to_layer)]
        (when (and layer return-to (not (valid-return-to-for-layer? layer return-to)))
          {:type :return-to-invariant-violation
           :description (:description rule-info)
           :message (format "Layer %d (%s) should have dsk_return_to_layer=-1, but condition has %d"
                           layer (get layer-names layer "unknown") return-to)
           :rule (:rule rule-info)})))))

(defn check-condition-completeness [rule-info]
  "Check if rule condition is a complete state.
   Complete = device (block level) + layer + leaf (if present).
   Uses the state library's validate-condition function."
  (let [rule (:rule rule-info)
        condition (when (>= (count rule) 3) (nth rule 2))
        block-device (:block-device rule-info)]
    (when condition
      ;; Parse the full condition array using state library
      (let [parsed (state/parse-condition-array condition)]
        (when (seq parsed)
          (cond
            ;; Check device at block level - leaf conditions need device context
            (and (or (:submode parsed) (:return-to parsed) (:layer parsed))
                 (= block-device :any))
            {:type :missing-device-condition
             :description (:description rule-info)
             :message "Rule has variable conditions but block has no device condition (:!apple_internal or :apple_internal)"
             :rule rule}

            ;; Check condition validity (layer required for leaf conditions)
            :else
            (let [error (state/validate-condition parsed)]
              (when error
                {:type (:type error)
                 :description (:description rule-info)
                 :message (:message error)
                 :rule rule}))))))))

;; ============================================================================
;; Shadowing Detection
;; ============================================================================

(defn rules-have-same-from? [rule1 rule2]
  "Check if two rules have the same from clause (would match same key)"
  (= (first (:rule rule1)) (first (:rule rule2))))

(defn condition-implies? [cond1 cond2]
  "Check if cond1 being true implies cond2 is also true.
   nil condition means 'always true' (matches everything).
   Returns true if whenever cond1 matches, cond2 also matches."
  (cond
    ;; If cond2 is nil (no condition), it always matches
    (nil? cond2) true
    ;; If cond1 is nil but cond2 is not, cond1 doesn't imply cond2
    (nil? cond1) false
    ;; Both have conditions - check if they're the same or cond1 is more specific
    :else
    (let [c1-map (parse-condition cond1)
          c2-map (parse-condition cond2)]
      (cond
        ;; If we can't parse them, assume no implication
        (or (nil? c1-map) (nil? c2-map)) false
        ;; Check if all constraints in c2 are satisfied by c1
        :else (every? (fn [[k v]] (= (get c1-map k) v)) c2-map)))))

(defn app-condition-covers? [earlier-apps later-apps]
  "Check if earlier-apps covers all cases that later-apps would match.
   Returns true if earlier rule would catch all inputs that later rule matches.
   - :any covers everything (any app matches)
   - A specific set only covers if later is subset of earlier"
  (cond
    ;; Earlier catches all apps - covers everything
    (= earlier-apps :any) true
    ;; Earlier is specific but later catches all - NOT covered
    (= later-apps :any) false
    ;; Both are sets - earlier covers later if later is subset of earlier
    (and (set? earlier-apps) (set? later-apps))
    (set/subset? later-apps earlier-apps)
    ;; Fallback - no coverage
    :else false))

(defn device-condition-covers? [earlier-device later-device]
  "Check if earlier-device covers all cases that later-device would match.
   - :any covers everything
   - Specific device only covers if they match"
  (cond
    ;; Earlier catches all devices - covers everything
    (= earlier-device :any) true
    ;; Earlier is specific but later catches all - NOT covered
    (= later-device :any) false
    ;; Both specific - must match
    :else (= earlier-device later-device)))

(defn find-shadowed-rule [rule-info earlier-rules]
  "Check if rule-info is always shadowed by an earlier rule.
   A rule is shadowed if an earlier rule would match ALL inputs that this rule matches.
   This means earlier's conditions must be >= (cover) later's conditions."
  (let [rule (:rule rule-info)
        from (first rule)
        condition (:condition rule-info)
        device (:block-device rule-info)
        apps (:block-apps rule-info)]
    (some (fn [earlier]
            (when (and (rules-have-same-from? rule-info earlier)
                       (condition-implies? condition (:condition earlier))
                       ;; Earlier device must cover later device
                       (device-condition-covers? (:block-device earlier) device)
                       ;; Earlier apps must cover later apps
                       (app-condition-covers? (:block-apps earlier) apps))
              {:type :shadowed-rule
               :description (:description rule-info)
               :message (format "Rule is always shadowed by earlier rule in '%s'"
                               (:description earlier))
               :rule rule
               :shadowed-by (:rule earlier)}))
          earlier-rules)))

;; ============================================================================
;; Modifier Specificity Ordering
;; ============================================================================

(defn extract-key-from-from [from-clause]
  "Extract the key code from a from clause"
  (cond
    (keyword? from-clause) from-clause
    (map? from-clause) (or (:key from-clause) (:pkey from-clause))
    :else nil))

(defn extract-modifiers-from-from [from-clause]
  "Extract modifiers from a from clause.
   Returns {:mandatory [...] :optional [...]} or nil if no modifiers specified"
  (when (map? from-clause)
    (:modi from-clause)))

(defn has-mandatory-modifiers? [modi]
  "Check if modifiers have any mandatory requirements"
  (and modi
       (seq (:mandatory modi))))

(defn modifier-specificity [modi]
  "Return a number representing modifier specificity.
   Higher number = more specific = should come first.
   - With mandatory modifiers: 2 (most specific, should match first)
   - With optional-only (restrictive): 1 (should match after mandatory)
   - No modifiers specified: 0 (least specific, matches anything)"
  (cond
    ;; Has mandatory modifiers - most specific
    (has-mandatory-modifiers? modi) 2
    ;; Has optional but not 'any' - somewhat restrictive
    (and modi
         (:optional modi)
         (not (some #{:any} (:optional modi)))) 1
    ;; No modifiers or optional: any - matches everything
    :else 0))

(defn devices-can-conflict? [device1 device2]
  "Check if two device conditions can both match the same input.
   - :any can conflict with anything
   - :external-only and :internal-only cannot conflict with each other"
  (or (= device1 :any)
      (= device2 :any)
      (= device1 device2)))

(defn apps-can-conflict? [apps1 apps2]
  "Check if two app conditions can both match the same input.
   - :any can conflict with anything
   - Two specific app sets conflict if they overlap"
  (or (= apps1 :any)
      (= apps2 :any)
      (and (set? apps1) (set? apps2) (seq (set/intersection apps1 apps2)))))

;; NOTE: Modifier ordering validation for {:optional [:caps_lock]} vs {:mandatory [:shift]}
;; was removed after empirical testing showed no shadowing occurs between them.
;; See .claude/karabiner-modifiers.md for test results.
;;
;; However, ##key (optional: any) DOES shadow everything - it's a true catch-all.
;; We validate that catch-all rules come AFTER specific rules for the same key.

(defn is-catch-all-modifier? [modi]
  "Check if modifiers are catch-all (optional: any or no restrictions).
   ##key syntax translates to {:optional [:any]}"
  (and modi
       (:optional modi)
       (some #{:any} (:optional modi))))

(defn key-has-hash-prefix? [key-code]
  "Check if a key has ## prefix (Goku catch-all syntax)"
  (when (keyword? key-code)
    (str/starts-with? (name key-code) "##")))

(defn strip-hash-prefix [key-code]
  "Strip ## prefix from key if present. Returns base key as keyword."
  (if (key-has-hash-prefix? key-code)
    (keyword (subs (name key-code) 2))
    key-code))

(defn is-catch-all-from? [from-clause]
  "Check if a from clause is a catch-all (matches any modifiers).
   This includes:
   - ##key syntax (keyword starting with ##)
   - {:modi {:optional [:any]}} explicit form"
  (cond
    (keyword? from-clause)
    (key-has-hash-prefix? from-clause)

    (map? from-clause)
    (or (key-has-hash-prefix? (:key from-clause))
        (is-catch-all-modifier? (:modi from-clause)))

    :else false))

(defn get-base-key [from-clause]
  "Get the base key from a from clause, stripping ## prefix if present."
  (let [key-code (extract-key-from-from from-clause)]
    (strip-hash-prefix key-code)))

(defn find-catch-all-shadowing [rule-info earlier-rules]
  "Check if this rule is shadowed by an earlier catch-all rule for the same key.
   A ##key rule matches ALL modifier combinations, so any specific modifier
   rule for the same key coming after it will never fire."
  (let [from (:from rule-info)
        base-key (get-base-key from)
        condition (:condition rule-info)
        device (:block-device rule-info)
        apps (:block-apps rule-info)]
    ;; Only check rules that are NOT themselves catch-alls
    (when (and base-key (not (is-catch-all-from? from)))
      (some (fn [earlier]
              (let [earlier-from (:from earlier)
                    earlier-base-key (get-base-key earlier-from)]
                ;; Found shadowing if:
                ;; 1. Earlier rule IS a catch-all
                ;; 2. Same base key
                ;; 3. Conditions can conflict
                (when (and (is-catch-all-from? earlier-from)
                           (= base-key earlier-base-key)
                           (condition-implies? condition (:condition earlier))
                           (devices-can-conflict? device (:block-device earlier))
                           (apps-can-conflict? apps (:block-apps earlier)))
                  {:type :catch-all-shadowing
                   :description (:description rule-info)
                   :message (format "Rule is shadowed by earlier catch-all (##%s) in '%s'. Move the catch-all AFTER this rule."
                                   (name base-key) (:description earlier))
                   :rule (:rule rule-info)
                   :shadowed-by (:rule earlier)
                   :earlier-block (:description earlier)})))
            earlier-rules))))

;; ============================================================================
;; Action Analysis
;; ============================================================================

(def state-variables
  "The three state variables that must all be set together"
  #{:dsk_layer :dsk_ins_sub_mode :dsk_return_to_layer})

(defn extract-variable-sets-from-action [action]
  "Extract variable sets from an action array.
   Variable sets look like [\"dsk_layer\" 0]"
  (when (vector? action)
    (->> action
         (filter #(and (vector? %)
                       (= 2 (count %))
                       (string? (first %))))
         (map (fn [[var-name value]] {(keyword var-name) value}))
         (apply merge))))

(defn is-vk-none-only? [action]
  "Check if action is just [:vk_none] with no other content."
  (and (vector? action)
       (= 1 (count action))
       (= :vk_none (first action))))

(defn is-desktop-fallback? [rule-info]
  "Check if this rule is a desktop fallback (no layer condition).
   Desktop fallbacks have block descriptions like 'Desktop []' - no layer condition.
   These rules are exempt from state variable requirements."
  (let [desc (:description rule-info)]
    (and desc
         (str/starts-with? desc "Desktop ")
         (str/ends-with? desc "[]"))))

(defn get-layer-from-block-desc [desc]
  "Extract dsk_layer value from block description like 'Desktop [[\"dsk_layer\" 13]]'"
  (when (and desc (str/includes? desc "dsk_layer"))
    (when-let [match (re-find #"dsk_layer\"\s+(\d+)" desc)]
      (parse-long (second match)))))

(defn action-stays-in-same-layer? [action rule-info]
  "Check if action outputs to the same layer as the block condition.
   Used for exempting rules that stay in their current layer."
  (let [desc (:description rule-info)
        block-layer (get-layer-from-block-desc desc)]
    (when (and block-layer (vector? action))
      (let [var-sets (extract-variable-sets-from-action action)
            action-layer (get var-sets :dsk_layer)]
        (= block-layer action-layer)))))

(defn has-any-state-var? [var-sets]
  "Check if any state variable is present"
  (and var-sets
       (some #(contains? var-sets %) state-variables)))

(defn has-all-state-vars? [var-sets]
  "Check if all three state variables are present"
  (and var-sets
       (every? #(contains? var-sets %) state-variables)))

(defn validate-action-state [action context rule-info]
  "Validate state variables in an action (main action, afterup, or alone).
   Returns a list of issues (empty if valid).

   Rules:
   1. Desktop fallback rules (no layer condition) are exempt from state var requirements
   2. [:vk_none] alone is exempt (key blocking, doesn't change state)
   3. Rules staying in the same layer are exempt (can't know return_to dynamically)
   4. Otherwise, ALL THREE state vars MUST be set
   5. The resulting state must be valid per invariants"
  (let [stays-in-same-layer (action-stays-in-same-layer? action rule-info)]
    (if (or (is-desktop-fallback? rule-info)
            (is-vk-none-only? action)
            stays-in-same-layer)
      [] ;; Exempt from state var requirements
      (let [var-sets (extract-variable-sets-from-action action)
            layer (get var-sets :dsk_layer)
            submode (get var-sets :dsk_ins_sub_mode)
            return-to (get var-sets :dsk_return_to_layer)
            missing (clojure.set/difference state-variables (set (keys (or var-sets {}))))]
        (cond-> []
          ;; ALL actions (except exempted) must have all three state vars
          (seq missing)
          (conj {:type :incomplete-state-transition
                 :description (:description rule-info)
                 :message (format "%s missing state vars: %s"
                                 context (str/join ", " (map name missing)))
                 :rule (:rule rule-info)})

          ;; If all vars present, validate the state is valid
          (and (empty? missing)
               (not (valid-submode-for-layer? layer submode)))
          (conj {:type :invalid-state-submode
                 :description (:description rule-info)
                 :message (format "%s creates invalid state: layer=%d cannot have submode=%d"
                                 context layer submode)
                 :rule (:rule rule-info)})

          (and (empty? missing)
               (not (valid-return-to-for-layer? layer return-to)))
          (conj {:type :invalid-state-return-to
                 :description (:description rule-info)
                 :message (format "%s creates invalid state: layer=%d cannot have return_to=%d"
                                 context layer return-to)
                 :rule (:rule rule-info)}))))))

(defn check-action-invariants [rule-info]
  "Check state variables in main action, afterup, and alone handlers.
   Returns a list of all issues found.

   NOTE: This validation only applies to Default profile + Desktop device rules.
   This is a bit weird/hardcoded, but other profiles/devices (Laptop, None) don't
   use the state machine in the same way. External factors like frontmost app
   and profile affect state validity, but we don't track those as inner nodes
   in our state model currently."
  ;; Only validate Desktop rules (block-device = :external-only means !apple_internal)
  (if (not= :external-only (:block-device rule-info))
    [] ;; Skip non-Desktop rules
    (let [rule (:rule rule-info)
          main-action (second rule)
          opts (when (>= (count rule) 4) (nth rule 3))
          afterup (when (map? opts) (:afterup opts))
          alone (when (map? opts) (:alone opts))]
      (concat
        (validate-action-state main-action "Action" rule-info)
        (when afterup
          (validate-action-state afterup "afterup" rule-info))
        (when alone
          (validate-action-state alone "alone" rule-info))))))

;; ============================================================================
;; Main Validation
;; ============================================================================

(defn validate-rules [config]
  "Validate all rules in the config.
   Returns a list of validation issues."
  (let [main-blocks (:main config)
        applications (or (:applications config) {})

        all-rules
        (for [block main-blocks
              rule-info (extract-rules-from-block block)]
          (assoc rule-info
                 :block-device (get-block-device-condition block)
                 :block-apps (get-block-app-conditions block applications)))

        ;; Collect all issues
        invariant-issues
        (for [rule-info all-rules
              issue [(check-submode-invariant rule-info)
                     (check-return-to-invariant rule-info)
                     (check-condition-completeness rule-info)]
              :when issue]
          issue)

        action-issues
        (for [rule-info all-rules
              issue (check-action-invariants rule-info)
              :when issue]
          issue)

        ;; Check for shadowed rules (expensive - compare each rule to all earlier ones)
        shadowing-issues
        (loop [remaining all-rules
               seen []
               issues []]
          (if (empty? remaining)
            issues
            (let [current (first remaining)
                  shadowed (find-shadowed-rule current seen)]
              (recur (rest remaining)
                     (conj seen current)
                     (if shadowed (conj issues shadowed) issues)))))

        ;; Check for catch-all shadowing (##key rules must come AFTER specific rules)
        catch-all-issues
        (loop [remaining all-rules
               seen []
               issues []]
          (if (empty? remaining)
            issues
            (let [current (first remaining)
                  shadowed (find-catch-all-shadowing current seen)]
              (recur (rest remaining)
                     (conj seen current)
                     (if shadowed (conj issues shadowed) issues)))))]

    (concat invariant-issues action-issues shadowing-issues catch-all-issues)))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn print-issue [issue]
  "Pretty print a validation issue"
  (println (str "  [" (name (:type issue)) "]"))
  (println (str "  Block: " (:description issue)))
  (println (str "  " (:message issue)))
  (println (str "  Rule: " (pr-str (:rule issue))))
  (when (:shadowed-by issue)
    (println (str "  Shadowed by: " (pr-str (:shadowed-by issue)))))
  (println))

(defn -main [& args]
  (when (empty? args)
    (println "Usage: bb validate-rules.bb <edn-file>")
    (System/exit 1))

  (let [edn-file (first args)
        config (edn/read-string (slurp edn-file))
        issues (validate-rules config)

        ;; Group by type
        by-type (group-by :type issues)]

    (println "=== Karabiner Rule Validation ===")
    (println (str "File: " edn-file))
    (println (str "Total issues: " (count issues)))
    (println)

    (if (empty? issues)
      (println "✓ No issues found!")
      (doseq [[issue-type type-issues] (sort-by first by-type)]
        (println (str "--- " (name issue-type) " (" (count type-issues) " issues) ---"))
        (doseq [issue type-issues]
          (print-issue issue))))

    (System/exit (if (empty? issues) 0 1))))

;; Run main if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
