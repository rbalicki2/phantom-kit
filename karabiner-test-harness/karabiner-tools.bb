#!/usr/bin/env bb
;; Karabiner EDN Tools
;;
;; Commands:
;;   fix-transitions  - Fix incomplete layer transitions
;;   add-rule-ids     - Add unique IDs to all rules
;;   validate-order   - Validate layer ordering (leaf-to-root)
;;   reorder          - Reorder rules by layer
;;   add-rule         - Add a new rule at correct position
;;   remove-rule      - Remove a rule by ID
;;
;; Usage:
;;   bb karabiner-tools.bb <command> <input.edn> [output.edn] [options...]

(ns karabiner-tools
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.java.io :as io]))

;; ============================================================================
;; Constants
;; ============================================================================

(def state-variables [:dsk_layer :dsk_in_modal_layer :dsk_ins_sub_mode :dsk_return_to_layer])

(defn expected-values-for-layer [layer]
  "Return expected state variable values for a given layer"
  {:dsk_layer layer
   :dsk_in_modal_layer (if (>= layer 2) 1 0)
   :dsk_ins_sub_mode (if (= layer 1) 0 -1)
   :dsk_return_to_layer (if (= layer 13) 0 -1)})  ;; Default to 0 for Label mode

;; ============================================================================
;; EDN Utilities
;; ============================================================================

(defn extract-variable-sets [action]
  "Extract variable sets from an action vector"
  (when (vector? action)
    (->> action
         (filter #(and (vector? %) (= 2 (count %)) (string? (first %))))
         (map (fn [[k v]] [(keyword k) v]))
         (into {}))))

(defn has-layer-transition? [action]
  "Check if action sets dsk_layer"
  (let [vars (extract-variable-sets action)]
    (contains? vars :dsk_layer)))

(defn get-dest-layer [action]
  "Get the destination layer from action"
  (let [vars (extract-variable-sets action)]
    (:dsk_layer vars)))

;; ============================================================================
;; Fix Incomplete Transitions
;; ============================================================================

(defn fix-action-transition [action]
  "Add missing state variables to a layer transition action"
  (if (and (vector? action) (has-layer-transition? action))
    (let [current-vars (extract-variable-sets action)
          dest-layer (:dsk_layer current-vars)
          expected (expected-values-for-layer dest-layer)
          ;; Find position to insert variables (after existing ones, before shell)
          var-items (filter #(and (vector? %) (= 2 (count %)) (string? (first %))) action)
          non-var-items (remove #(and (vector? %) (= 2 (count %)) (string? (first %))) action)
          shell-item (first (filter #(and (map? %) (contains? % :shell)) non-var-items))
          other-items (remove #(and (map? %) (contains? % :shell)) non-var-items)
          ;; Build complete variable set
          complete-vars (merge expected current-vars)
          var-vectors (mapv (fn [k] [(name k) (get complete-vars k)]) state-variables)]
      ;; Reconstruct: other-items + vars + shell
      (vec (concat other-items var-vectors (when shell-item [shell-item]))))
    action))

(defn fix-rule-transitions [rule]
  "Fix transitions in a rule"
  (if (and (vector? rule) (>= (count rule) 2))
    (let [from (first rule)
          to (second rule)
          rest-items (vec (drop 2 rule))
          fixed-to (fix-action-transition to)]
      (vec (concat [from fixed-to] rest-items)))
    rule))

(defn fix-block-transitions [block]
  "Fix transitions in a rule block"
  (if (and (map? block) (:rules block))
    (let [rules-vec (:rules block)
          leading-kws (vec (take-while keyword? rules-vec))
          actual-rules (drop-while keyword? rules-vec)
          fixed-rules (map fix-rule-transitions actual-rules)]
      (assoc block :rules (vec (concat leading-kws fixed-rules))))
    block))

(defn fix-transitions [config]
  "Fix all incomplete transitions in config"
  (if-let [main (:main config)]
    (assoc config :main (mapv fix-block-transitions main))
    config))

;; ============================================================================
;; Rule ID Management
;; ============================================================================

(def id-counter (atom 0))

(defn next-rule-id []
  "Generate next rule ID"
  (let [id (swap! id-counter inc)]
    (format "R%04d" id)))

(defn extract-rule-id [rule]
  "Extract existing rule ID from a rule (looks for R#### pattern in first element)"
  (when (and (vector? rule) (map? (first rule)))
    (when-let [id (:id (first rule))]
      id)))

(defn format-conditions-string [rule]
  "Format conditions as string for rule name"
  (when (and (vector? rule) (>= (count rule) 3))
    (let [condition (nth rule 2)]
      (cond
        (nil? condition) "global"
        (keyword? condition) (name condition)
        (vector? condition)
        (let [format-single (fn [c]
                              (if (and (vector? c) (= 2 (count c)) (string? (first c)))
                                (let [[var val] c]
                                  (str (str/replace var "dsk_" "") ":" val))
                                (str c)))]
          (if (and (vector? (first condition)) (string? (ffirst condition)))
            ;; Array of conditions
            (str/join "," (map format-single condition))
            ;; Single condition wrapped in array
            (format-single (first condition))))
        :else (str condition)))))

(defn add-id-to-rule [rule description]
  "Add ID metadata to rule's from clause"
  (if (and (vector? rule) (>= (count rule) 2) (map? (first rule)))
    (let [from (first rule)
          existing-id (:id from)
          id (or existing-id (next-rule-id))
          cond-str (format-conditions-string rule)
          ;; Create ID string: "R0001 [layer:0] description"
          id-str (str id " [" (or cond-str "?") "] " (or description ""))
          new-from (assoc from :id id-str)]
      (assoc rule 0 new-from))
    rule))

(defn add-ids-to-block [block]
  "Add IDs to all rules in a block"
  (if (and (map? block) (:rules block))
    (let [desc (:des block)
          rules-vec (:rules block)
          leading-kws (vec (take-while keyword? rules-vec))
          actual-rules (drop-while keyword? rules-vec)
          rules-with-ids (map #(add-id-to-rule % desc) actual-rules)]
      (assoc block :rules (vec (concat leading-kws rules-with-ids))))
    block))

(defn add-rule-ids [config]
  "Add IDs to all rules in config"
  (reset! id-counter 0)
  (if-let [main (:main config)]
    (assoc config :main (mapv add-ids-to-block main))
    config))

;; ============================================================================
;; Layer Ordering Validation
;; ============================================================================

(defn extract-layer-from-condition [condition]
  "Extract layer number from condition"
  (cond
    (nil? condition) nil
    (keyword? condition) nil  ;; App condition
    (vector? condition)
    (let [first-item (first condition)]
      (cond
        ;; Array of conditions [[...] [...]]
        (and (vector? first-item) (string? (first first-item)))
        (let [layer-cond (first (filter #(= "dsk_layer" (first %)) condition))]
          (when layer-cond (second layer-cond)))
        ;; Single condition wrapped [["dsk_layer" 0]]
        (and (= 1 (count condition)) (vector? first-item))
        (when (= "dsk_layer" (first first-item))
          (second first-item))
        :else nil))
    :else nil))

(defn get-rule-layer [rule]
  "Get layer number for a rule"
  (when (and (vector? rule) (>= (count rule) 3))
    (extract-layer-from-condition (nth rule 2))))

(defn validate-layer-order [config]
  "Validate that rules are ordered by layer (descending - leaf-to-root).
   Higher layer numbers (more specific/leaf) should come before lower (root)."
  (let [issues (atom [])]
    (doseq [block (:main config)]
      (when (and (map? block) (:rules block))
        (let [rules-vec (:rules block)
              actual-rules (drop-while keyword? rules-vec)
              layers (keep-indexed
                       (fn [idx rule]
                         (when-let [layer (get-rule-layer rule)]
                           {:idx idx :layer layer :rule rule}))
                       actual-rules)]
          ;; Check ordering - should be descending (leaf-to-root)
          (doseq [[prev curr] (partition 2 1 layers)]
            (when (< (:layer prev) (:layer curr))
              (swap! issues conj
                     {:block (:des block)
                      :message (format "Layer %d rule appears before layer %d rule (should be leaf-to-root: higher layers first)"
                                      (:layer prev) (:layer curr))
                      :prev-rule (:rule prev)
                      :curr-rule (:rule curr)}))))))
    @issues))

;; ============================================================================
;; Pretty Printing
;; ============================================================================

(defn pprint-config [config]
  "Pretty print config"
  (binding [pp/*print-right-margin* 120
            pp/*print-miser-width* 80]
    (pp/pprint config)))

(defn pprint-to-string [config]
  (with-out-str (pprint-config config)))

;; ============================================================================
;; Rule Addition/Removal
;; ============================================================================

(defn find-insertion-point [rules-vec new-layer]
  "Find index to insert a rule for given layer (maintains descending/leaf-to-root order)"
  (let [actual-rules (vec (drop-while keyword? rules-vec))
        leading-count (- (count rules-vec) (count actual-rules))]
    ;; Find first rule with layer < new-layer (insert before it to maintain descending)
    (loop [idx 0]
      (if (>= idx (count actual-rules))
        (+ leading-count idx)  ;; Add at end
        (let [rule (nth actual-rules idx)
              rule-layer (get-rule-layer rule)]
          (if (and rule-layer (< rule-layer new-layer))
            (+ leading-count idx)
            (recur (inc idx))))))))

(defn reorder-rules-by-layer [rules-vec]
  "Reorder rules within a block by layer number (descending - leaf-to-root)"
  (let [leading-kws (vec (take-while keyword? rules-vec))
        actual-rules (vec (drop-while keyword? rules-vec))
        ;; Sort rules: those with layers first (descending), then rules without layers
        sorted-rules (sort-by (fn [rule]
                                (if-let [layer (get-rule-layer rule)]
                                  (- layer)  ;; Negate for descending sort
                                  1000))     ;; Rules without layers go last
                              actual-rules)]
    (vec (concat leading-kws sorted-rules))))

(defn reorder-block [block]
  "Reorder rules in a single block"
  (if (and (map? block) (:rules block))
    (update block :rules reorder-rules-by-layer)
    block))

(defn reorder-config [config]
  "Reorder all rules in config by layer (leaf-to-root)"
  (if-let [main (:main config)]
    (assoc config :main (mapv reorder-block main))
    config))

(defn remove-rule-by-id [config rule-id]
  "Remove a rule by its ID"
  (letfn [(remove-from-rules [rules-vec]
            (let [leading-kws (vec (take-while keyword? rules-vec))
                  actual-rules (drop-while keyword? rules-vec)
                  filtered (remove #(and (vector? %)
                                         (map? (first %))
                                         (when-let [id (:id (first %))]
                                           (str/starts-with? id rule-id)))
                                   actual-rules)]
              (vec (concat leading-kws filtered))))
          (process-block [block]
            (if (and (map? block) (:rules block))
              (update block :rules remove-from-rules)
              block))]
    (update config :main #(mapv process-block %))))

;; ============================================================================
;; Validation: Unique Rule IDs
;; ============================================================================

(defn extract-all-rule-ids [config]
  "Extract all rule IDs from config"
  (for [block (:main config)
        :when (and (map? block) (:rules block))
        :let [rules-vec (:rules block)
              actual-rules (drop-while keyword? rules-vec)]
        rule actual-rules
        :when (and (vector? rule) (map? (first rule)))
        :let [id (:id (first rule))]
        :when id]
    {:id id :block (:des block) :rule rule}))

(defn validate-unique-ids [config]
  "Validate that all rule IDs are unique"
  (let [all-ids (extract-all-rule-ids config)
        id-codes (map #(first (str/split (:id %) #" ")) all-ids)
        freqs (frequencies id-codes)
        duplicates (filter #(> (val %) 1) freqs)]
    (when (seq duplicates)
      (map (fn [[id count]]
             {:type :duplicate-id
              :id id
              :count count})
           duplicates))))

;; ============================================================================
;; CLI
;; ============================================================================

(defn print-usage []
  (println "Karabiner EDN Tools")
  (println)
  (println "Commands:")
  (println "  fix-transitions <input> [output]  - Fix incomplete layer transitions")
  (println "  add-rule-ids <input> [output]     - Add unique IDs to all rules")
  (println "  validate-order <input>            - Validate layer ordering (leaf-to-root)")
  (println "  validate-ids <input>              - Validate unique rule IDs")
  (println "  reorder <input> [output]          - Reorder rules by layer (leaf-to-root)")
  (println "  remove-rule <input> <output> <id> - Remove rule by ID")
  (println))

(defn -main [& args]
  (when (empty? args)
    (print-usage)
    (System/exit 1))

  (let [[cmd & rest-args] args]
    (case cmd
      "fix-transitions"
      (let [[input output] rest-args
            config (edn/read-string (slurp input))
            fixed (fix-transitions config)
            output-str (pprint-to-string fixed)]
        (if output
          (do (spit output output-str)
              (println "Fixed transitions written to" output))
          (print output-str)))

      "add-rule-ids"
      (let [[input output] rest-args
            config (edn/read-string (slurp input))
            with-ids (add-rule-ids config)
            output-str (pprint-to-string with-ids)]
        (if output
          (do (spit output output-str)
              (println "Added rule IDs, written to" output))
          (print output-str)))

      "validate-order"
      (let [[input] rest-args
            config (edn/read-string (slurp input))
            issues (validate-layer-order config)]
        (if (empty? issues)
          (println "✓ Layer ordering is valid")
          (do
            (println (str "Found " (count issues) " ordering issues:"))
            (doseq [issue issues]
              (println "  Block:" (:block issue))
              (println "  " (:message issue))
              (println)))))

      "validate-ids"
      (let [[input] rest-args
            config (edn/read-string (slurp input))
            issues (validate-unique-ids config)]
        (if (empty? issues)
          (println "✓ All rule IDs are unique")
          (do
            (println (str "Found " (count issues) " duplicate IDs:"))
            (doseq [issue issues]
              (println "  ID:" (:id issue) "appears" (:count issue) "times")))))

      "reorder"
      (let [[input output] rest-args
            config (edn/read-string (slurp input))
            reordered (reorder-config config)
            output-str (pprint-to-string reordered)]
        (if output
          (do (spit output output-str)
              (println "Reordered rules written to" output))
          (print output-str)))

      "remove-rule"
      (let [[input output rule-id] rest-args
            config (edn/read-string (slurp input))
            updated (remove-rule-by-id config rule-id)
            output-str (pprint-to-string updated)]
        (spit output output-str)
        (println "Removed rule" rule-id ", written to" output))

      ;; Unknown command
      (do
        (println "Unknown command:" cmd)
        (print-usage)
        (System/exit 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
