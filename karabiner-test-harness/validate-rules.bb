#!/usr/bin/env bb
;; Karabiner Rule Validator
;;
;; Validates karabiner.edn rules for:
;; - Unreachable rules (conditions can never be satisfied given invariants)
;; - Redundant rules (always shadowed by earlier rules)
;; - Invariant violations (rules that assume impossible state combinations)
;;
;; Invariants (from mental_model.md):
;; 1. dsk_in_modal_layer = (dsk_layer >= 2 ? 1 : 0)
;; 2. dsk_ins_sub_mode = -1 when dsk_layer != 1
;; 3. dsk_return_to_layer = -1 when dsk_layer != 13 (Label mode)
;;
;; Usage:
;;   bb validate-rules.bb <edn-file>

(ns validate-rules
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]))

;; ============================================================================
;; State Invariants
;; ============================================================================

(def layer-names
  {0 "Normal"
   1 "Ins"
   2 "Nav"
   3 "Chrome"
   4 "VSCode"
   5 "TMUX"
   6 "Comma"
   7 "L (base)"
   8 "Term"
   9 "Admin"
   10 "InApp"
   11 "AppSwitcher"
   12 "WindowSwitcher"
   13 "Label (Mouse)"
   28 "Grid"})

(defn modal-layer? [layer]
  "Returns true if the layer is modal (requires dsk_in_modal_layer=1)"
  (>= layer 2))

(defn expected-modal-value [layer]
  "Returns expected dsk_in_modal_layer value for given dsk_layer"
  (if (modal-layer? layer) 1 0))

(defn valid-submode-for-layer? [layer submode]
  "Check if submode value is valid for the given layer.
   Submodes only apply in Ins mode (layer 1).
   -1 = N/A (for non-Ins modes)
   0-4 = various submodes in Ins mode"
  (if (= layer 1)
    ;; In Ins mode, submode should be 0-4
    (and (>= submode 0) (<= submode 4))
    ;; Outside Ins mode, submode should be -1
    (= submode -1)))

(defn valid-return-to-for-layer? [layer return-to]
  "Check if return_to_layer value is valid for the given layer.
   Only meaningful in Label mode (layer 13).
   -1 = N/A (for non-Label modes)
   0 = return to Normal
   1 = return to Ins"
  (if (= layer 13)
    ;; In Label mode, return-to should be 0 or 1
    (or (= return-to 0) (= return-to 1))
    ;; Outside Label mode, return-to should be -1
    (= return-to -1)))

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

(defn check-modal-invariant [rule-info]
  "Check if rule violates the dsk_in_modal_layer invariant"
  (let [cond-map (extract-conditions-from-rule (:rule rule-info))]
    (when cond-map
      (let [layer (get cond-map :dsk_layer)
            modal (get cond-map :dsk_in_modal_layer)]
        (when (and layer modal)
          (let [expected (expected-modal-value layer)]
            (when (not= modal expected)
              {:type :modal-invariant-violation
               :description (:description rule-info)
               :message (format "Layer %d (%s) expects dsk_in_modal_layer=%d, but condition has %d"
                               layer (get layer-names layer "unknown") expected modal)
               :rule (:rule rule-info)})))))))

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

(defn find-shadowed-rule [rule-info earlier-rules]
  "Check if rule-info is always shadowed by an earlier rule.
   Only considers shadowing if both rules have same device AND app conditions."
  (let [rule (:rule rule-info)
        from (first rule)
        condition (:condition rule-info)
        device (:block-device rule-info)
        apps (:block-apps rule-info)]
    (some (fn [earlier]
            (when (and (rules-have-same-from? rule-info earlier)
                       (condition-implies? condition (:condition earlier))
                       ;; Only shadow if device conditions overlap
                       (or (= device :any)
                           (= (:block-device earlier) :any)
                           (= device (:block-device earlier)))
                       ;; Only shadow if app conditions overlap
                       (or (= apps :any)
                           (= (:block-apps earlier) :any)
                           (and (set? apps) (set? (:block-apps earlier))
                                (not (empty? (set/intersection apps (:block-apps earlier)))))))
              {:type :shadowed-rule
               :description (:description rule-info)
               :message (format "Rule is always shadowed by earlier rule in '%s'"
                               (:description earlier))
               :rule rule
               :shadowed-by (:rule earlier)}))
          earlier-rules)))

;; ============================================================================
;; Action Analysis
;; ============================================================================

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

(defn check-action-invariants [rule-info]
  "Check if the action sets variables in violation of invariants"
  (let [action (second (:rule rule-info))
        var-sets (extract-variable-sets-from-action action)]
    (when var-sets
      (let [layer (get var-sets :dsk_layer)
            modal (get var-sets :dsk_in_modal_layer)
            submode (get var-sets :dsk_ins_sub_mode)
            return-to (get var-sets :dsk_return_to_layer)]
        (cond-> []
          ;; Check modal invariant in action
          (and layer modal (not= modal (expected-modal-value layer)))
          (conj {:type :action-modal-violation
                 :description (:description rule-info)
                 :message (format "Action sets dsk_layer=%d but dsk_in_modal_layer=%d (expected %d)"
                                 layer modal (expected-modal-value layer))
                 :rule (:rule rule-info)})

          ;; Check submode invariant in action
          (and layer submode (not (valid-submode-for-layer? layer submode)))
          (conj {:type :action-submode-violation
                 :description (:description rule-info)
                 :message (format "Action sets dsk_layer=%d but dsk_ins_sub_mode=%d (expected -1)"
                                 layer submode)
                 :rule (:rule rule-info)})

          ;; Check return-to invariant in action
          (and layer return-to (not (valid-return-to-for-layer? layer return-to)))
          (conj {:type :action-return-to-violation
                 :description (:description rule-info)
                 :message (format "Action sets dsk_layer=%d but dsk_return_to_layer=%d (expected -1)"
                                 layer return-to)
                 :rule (:rule rule-info)}))))))

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
              issue [(check-modal-invariant rule-info)
                     (check-submode-invariant rule-info)
                     (check-return-to-invariant rule-info)]
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
                     (if shadowed (conj issues shadowed) issues)))))]

    (concat invariant-issues action-issues shadowing-issues)))

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
