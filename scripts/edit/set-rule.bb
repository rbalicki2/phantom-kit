#!/usr/bin/env bb
;; Set a rule by ID (delete existing, then add)
;;
;; Usage:
;;   bb set-rule.bb <edn-file> <rule-id> [<rule-file> | -]
;;
;; Example:
;;   cat << 'EOFR' | bb set-rule.bb src/karabiner.edn R1515 -
;;   [{:key :!Of9, :id "R1515 [profile=Default:device=Desktop:layer=1:submode=2]"} [:!Sgrave] [["dsk_layer" 1] ["dsk_ins_sub_mode" 2]]]
;;   EOFR
;;
;; Validates:
;;   - ID format (R followed by digits, with state string)
;;   - ID state string matches actual conditions
;;   - Condition completeness
;;   - Layer transitions set all state variables
;;
;; The rule will be added and sync should be run afterward to reorder.

(require '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.pprint :as pp]
         '[clojure.set :as set])

;; Load shared state library
(load-file (str (System/getProperty "user.dir") "/scripts/lib/state.bb"))

(defn rule-id [rule]
  "Extract ID from a rule's from-clause"
  (when (vector? rule)
    (let [from (first rule)]
      (cond
        (map? from) (:id from)
        :else nil))))

(defn extract-id-prefix [id-str]
  "Extract just the rule ID (e.g., 'R1515' from 'R1515 [profile=...]')"
  (when id-str
    (first (str/split id-str #" "))))

(defn remove-rule-by-id [config target-id]
  "Remove any rule with matching ID from all blocks"
  (let [target-prefix (extract-id-prefix target-id)]
    (update config :main
            (fn [blocks]
              (mapv (fn [block]
                      (update block :rules
                              (fn [rules]
                                (if (vector? rules)
                                  (vec (remove (fn [item]
                                                 (when (vector? item)
                                                   (let [id (rule-id item)
                                                         prefix (extract-id-prefix id)]
                                                     (= prefix target-prefix))))
                                               rules))
                                  rules))))
                    blocks)))))

(defn find-or-create-block [config des-pattern]
  "Find block matching des pattern, or return nil to create new"
  (let [blocks (:main config)]
    (first (filter #(= (:des %) des-pattern) blocks))))

(defn add-rule-to-config [config rule des-pattern]
  "Add a rule to the appropriate block (creates block if needed)"
  (let [existing-block (find-or-create-block config des-pattern)]
    (if existing-block
      ;; Add to existing block
      (update config :main
              (fn [blocks]
                (mapv (fn [block]
                        (if (= (:des block) des-pattern)
                          (update block :rules
                                  (fn [rules]
                                    (let [rules-vec (if (vector? rules) rules [])
                                          ;; Find where rules start (after keywords like :!apple_internal)
                                          keywords (take-while keyword? rules-vec)
                                          actual-rules (drop-while keyword? rules-vec)]
                                      (vec (concat keywords actual-rules [rule])))))
                          block))
                      blocks)))
      ;; Create new block
      (update config :main conj {:des des-pattern
                                 :rules [:!apple_internal rule]}))))

(defn infer-des-from-condition [rule]
  "Infer the :des pattern from a rule's condition"
  (when (vector? rule)
    (let [condition (when (> (count rule) 2) (nth rule 2))]
      (cond
        ;; Array of conditions like [["dsk_layer" 1] ["dsk_ins_sub_mode" 2]]
        (and (vector? condition) (every? vector? condition))
        (str "Desktop " (pr-str condition))

        ;; Single condition (shouldn't happen but handle it)
        (vector? condition)
        (str "Desktop " (pr-str [condition]))

        ;; No condition - global block
        :else
        "Desktop []"))))

;; ============================================================================
;; Validation Functions
;; ============================================================================

(defn validate-id-format [rule-id]
  "Validate ID format: R followed by digits, optionally with state string in brackets"
  (let [prefix (extract-id-prefix rule-id)]
    (cond
      (nil? prefix)
      {:error "Rule ID is required"}

      (not (re-matches #"R\d+" prefix))
      {:error (format "Rule ID must be R followed by digits (got: %s)" prefix)}

      :else nil)))

(defn extract-conditions-from-rule [rule]
  "Extract condition variables from a rule's condition array"
  (when (and (vector? rule) (>= (count rule) 3))
    (let [condition (nth rule 2)]
      (cond
        ;; Array of conditions like [["dsk_layer" 1] ["dsk_ins_sub_mode" 2]]
        (and (vector? condition) (every? vector? condition))
        (->> condition
             (filter #(and (vector? %) (= 2 (count %)) (string? (first %))))
             (map (fn [[k v]] [(keyword k) v]))
             (into {}))

        ;; Single condition [["dsk_layer" 1]]
        (vector? condition)
        (when (and (= 2 (count condition)) (string? (first condition)))
          {(keyword (first condition)) (second condition)})

        :else nil))))

(defn has-layer-condition? [conditions]
  "Check if conditions include dsk_layer"
  (contains? conditions :dsk_layer))

(defn is-in-label-layer? [conditions]
  "Check if rule is in layer 13 (Label mode)"
  (= 13 (:dsk_layer conditions)))

(defn parse-id-state-string
  "Parse state values from the bracketed portion of a rule ID.
   E.g., 'R1234 [profile=Default:device=Desktop:dsk_layer=1]' -> {:layer 1, ...}"
  [rule-id]
  (when rule-id
    (when-let [marker (second (re-find #"R\d+[a-z]?\s+\[([^\]]*)\]" rule-id))]
      (if (empty? marker)
        {}
        (let [pairs (str/split marker #":")
              parsed (into {} (for [p pairs
                                    :let [[k v] (str/split p #"=")]
                                    :when (and k v)]
                                [k v]))]
          {:profile (get parsed "profile")
           :device (get parsed "device")
           :layer (when-let [l (get parsed "dsk_layer")] (parse-long l))
           :submode (when-let [s (get parsed "dsk_ins_sub_mode")] (parse-long s))
           :return-to (when-let [r (get parsed "dsk_return_to_layer")] (parse-long r))})))))

(defn validate-id-matches-conditions [rule target-id]
  "Validate that the ID's state string matches the rule's actual conditions"
  (let [from (first rule)
        rule-id-in-from (when (map? from) (:id from))
        actual-conds (extract-conditions-from-rule rule)]
    (cond
      ;; No ID in the from clause
      (nil? rule-id-in-from)
      {:error "Rule's from-clause must include :id"}

      ;; ID doesn't start with the target
      (not (str/starts-with? rule-id-in-from target-id))
      {:error (format "Rule ID in from-clause (%s) must start with %s"
                      (extract-id-prefix rule-id-in-from) target-id)}

      :else
      ;; Validate state string matches conditions
      (let [parsed (parse-id-state-string rule-id-in-from)
            mismatches (cond-> []
                         (and (:layer parsed) (not= (:layer parsed) (:dsk_layer actual-conds)))
                         (conj (format "dsk_layer: ID=%s actual=%s"
                                       (:layer parsed) (:dsk_layer actual-conds)))

                         (and (:submode parsed) (not= (:submode parsed) (:dsk_ins_sub_mode actual-conds)))
                         (conj (format "dsk_ins_sub_mode: ID=%s actual=%s"
                                       (:submode parsed) (:dsk_ins_sub_mode actual-conds)))

                         (and (:return-to parsed) (not= (:return-to parsed) (:dsk_return_to_layer actual-conds)))
                         (conj (format "dsk_return_to_layer: ID=%s actual=%s"
                                       (:return-to parsed) (:dsk_return_to_layer actual-conds))))]
        (when (seq mismatches)
          {:error (format "ID state doesn't match conditions: %s" (str/join ", " mismatches))})))))

(defn extract-variable-sets [action]
  "Extract variable sets from action (e.g., [\"dsk_layer\" 1])"
  (when (vector? action)
    (->> action
         (filter #(and (vector? %) (= 2 (count %)) (string? (first %))))
         (map (fn [[k v]] [(keyword k) v]))
         (into {}))))

(def state-variables
  "The three state variables that must all be set together"
  #{:dsk_layer :dsk_ins_sub_mode :dsk_return_to_layer})

(defn is-vk-none-only? [action]
  "Check if action is just [:vk_none] with no other content"
  (and (vector? action)
       (= 1 (count action))
       (= :vk_none (first action))))

(defn validate-complete-state-transition [rule]
  "Ensure all state variables are set together when any is set.

   Rule: If you set ANY state var, you must set ALL of them.

   Exceptions:
   - [:vk_none] alone requires no state vars (blocked rules)
   - Rules in layer 13 (Label mode) don't need to set dsk_return_to_layer
     (because return-to-layer behaves differently in that layer)"
  (let [action (second rule)
        conditions (extract-conditions-from-rule rule)
        var-sets (extract-variable-sets action)
        has-any-state-var (some #(contains? var-sets %) state-variables)]
    (when (and has-any-state-var
               (not (is-vk-none-only? action)))
      (let [;; Exception: layer 13 doesn't require return-to-layer in action
            required-vars (if (is-in-label-layer? conditions)
                           #{:dsk_layer :dsk_ins_sub_mode}
                           #{:dsk_layer :dsk_ins_sub_mode :dsk_return_to_layer})
            missing (clojure.set/difference required-vars (set (keys var-sets)))]
        (when (seq missing)
          {:error (format "Action sets state vars but missing: %s. All required vars must be set together."
                          (str/join ", " (map name missing)))})))))

(defn validate-rule [rule target-id]
  "Run all validations on a rule. Returns nil if valid, {:error msg} if not."
  (or (validate-id-format target-id)
      (validate-id-matches-conditions rule target-id)
      (validate-complete-state-transition rule)))

(defn -main [& args]
  (let [[edn-file rule-id rule-source] args]
    (when (or (nil? edn-file) (nil? rule-id))
      (println "Usage: bb set-rule.bb <edn-file> <rule-id> [<rule-file> | -]")
      (println "  Use '-' to read rule from stdin")
      (println "Example:")
      (println "  echo '[{:key :!Of9} [:output] [[\"dsk_layer\" 1]]]' | bb set-rule.bb src/karabiner.edn R1515 -")
      (System/exit 1))

    (let [rule-str (cond
                     (= rule-source "-") (slurp *in*)
                     (nil? rule-source) (slurp *in*)
                     :else (slurp rule-source))
          rule (edn/read-string rule-str)

          ;; Validate before modifying config
          validation-error (validate-rule rule rule-id)]

      (when validation-error
        (println (str "Validation error: " (:error validation-error)))
        (System/exit 1))

      (let [config (edn/read-string (slurp edn-file))
            des-pattern (infer-des-from-condition rule)

            ;; Remove existing rule with this ID
            config-without (remove-rule-by-id config rule-id)

            ;; Add the new rule
            config-with (add-rule-to-config config-without rule des-pattern)]

        ;; Write back
        (spit edn-file (pr-str config-with))
        (println (str "Set rule " rule-id " in block: " des-pattern))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
