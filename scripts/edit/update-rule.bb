#!/usr/bin/env bb

;; Update rules in karabiner.edn by ID or pattern
;;
;; Usage:
;;   bb update-rule.bb <edn-file> <output-file> <operation> [options]
;;
;; Operations:
;;   add-to-action <value>     Add value to action array (e.g., '["dsk_ins_sub_mode" 0]')
;;   set-action <value>        Replace entire action with value
;;   add-condition <value>     Add condition to rule
;;   remove-condition <var>    Remove condition for variable
;;
;; Selectors (at least one required):
;;   --id PATTERN             Match rules with ID containing PATTERN
;;   --block NAME             Match rules in block containing NAME
;;   --layer N                Match rules with dsk_layer=N
;;   --submode N              Match rules with dsk_ins_sub_mode=N
;;
;; Examples:
;;   # Add submode clear to all submode 1 rules
;;   bb update-rule.bb input.edn output.edn add-to-action '["dsk_ins_sub_mode" 0]' --submode 1
;;
;;   # Add condition to specific rule by ID
;;   bb update-rule.bb input.edn output.edn add-condition '["dsk_layer" 1]' --id "R0091"

(require '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.pprint :as pprint])

(defn parse-args [args]
  (loop [args args
         opts {:selectors {}}]
    (if (empty? args)
      opts
      (let [[arg & rest-args] args]
        (cond
          (= "--id" arg)
          (recur (rest rest-args) (assoc-in opts [:selectors :id] (first rest-args)))

          (= "--block" arg)
          (recur (rest rest-args) (assoc-in opts [:selectors :block] (first rest-args)))

          (= "--layer" arg)
          (recur (rest rest-args) (assoc-in opts [:selectors :layer] (parse-long (first rest-args))))

          (= "--submode" arg)
          (recur (rest rest-args) (assoc-in opts [:selectors :submode] (parse-long (first rest-args))))

          (nil? (:input opts))
          (recur rest-args (assoc opts :input arg))

          (nil? (:output opts))
          (recur rest-args (assoc opts :output arg))

          (nil? (:operation opts))
          (recur rest-args (assoc opts :operation arg))

          (nil? (:value opts))
          (recur rest-args (assoc opts :value arg))

          :else
          (recur rest-args opts))))))

(defn extract-condition-value [conditions var-name]
  "Extract value for a variable from conditions"
  (when (sequential? conditions)
    (let [conds (if (and (vector? (first conditions))
                         (string? (ffirst conditions)))
                  conditions
                  [conditions])]
      (some (fn [c]
              (when (and (vector? c) (= (first c) var-name))
                (second c)))
            conds))))

(defn rule-matches-selectors? [rule block-des block-conditions selectors]
  "Check if rule matches all selectors"
  (let [rule-from (first rule)
        rule-id (when (map? rule-from) (:id rule-from))
        rule-conds (nth rule 2 nil)
        all-conds (concat
                   (when (sequential? block-conditions)
                     (filter vector? block-conditions))
                   (when (sequential? rule-conds)
                     (if (and (vector? (first rule-conds))
                              (string? (ffirst rule-conds)))
                       rule-conds
                       [rule-conds])))]
    (and
     (if-let [id-pat (:id selectors)]
       (and rule-id (str/includes? (str/lower-case rule-id) (str/lower-case id-pat)))
       true)

     (if-let [block-pat (:block selectors)]
       (and block-des (str/includes? (str/lower-case block-des) (str/lower-case block-pat)))
       true)

     (if-let [layer (:layer selectors)]
       (= (extract-condition-value all-conds "dsk_layer") layer)
       true)

     (if-let [submode (:submode selectors)]
       (= (extract-condition-value all-conds "dsk_ins_sub_mode") submode)
       true))))

(defn update-rule-action [rule operation value-str]
  "Apply operation to rule's action"
  (let [from (first rule)
        action (second rule)
        conditions (nth rule 2 nil)
        options (nth rule 3 nil)
        parsed-value (edn/read-string value-str)]

    (case operation
      "add-to-action"
      (let [new-action (if (vector? action)
                        (conj (vec action) parsed-value)
                        [action parsed-value])]
        (cond
          options [from new-action conditions options]
          conditions [from new-action conditions]
          :else [from new-action]))

      "set-action"
      (cond
        options [from parsed-value conditions options]
        conditions [from parsed-value conditions]
        :else [from parsed-value])

      "add-condition"
      (let [new-conds (if conditions
                       (if (and (vector? (first conditions))
                                (string? (ffirst conditions)))
                         (conj (vec conditions) parsed-value)
                         [conditions parsed-value])
                       [parsed-value])]
        (cond
          options [from action new-conds options]
          :else [from action new-conds]))

      "remove-condition"
      (let [var-name value-str
            new-conds (when conditions
                       (let [conds (if (and (vector? (first conditions))
                                            (string? (ffirst conditions)))
                                    conditions
                                    [conditions])]
                         (vec (remove #(and (vector? %) (= (first %) var-name)) conds))))]
        (cond
          (and options (seq new-conds)) [from action new-conds options]
          options [from action nil options]
          (seq new-conds) [from action new-conds]
          :else [from action]))

      ;; Unknown operation
      rule)))

(defn process-block [block selectors operation value]
  "Process a block, updating matching rules"
  (let [des (:des block)
        rules-vec (:rules block)
        ;; Extract block-level conditions
        block-conditions (when (and (sequential? rules-vec)
                                    (or (keyword? (first rules-vec))
                                        (and (vector? (first rules-vec))
                                             (not (map? (ffirst rules-vec))))))
                          (vec (take-while #(or (keyword? %)
                                                (and (vector? %)
                                                     (not (map? (first %)))))
                                           rules-vec)))
        actual-rules (if block-conditions
                      (drop (count block-conditions) rules-vec)
                      rules-vec)
        ;; Update matching rules
        updated-rules (mapv (fn [rule]
                             (if (rule-matches-selectors? rule des block-conditions selectors)
                               (update-rule-action rule operation value)
                               rule))
                           actual-rules)
        ;; Reconstruct rules vector
        new-rules-vec (if block-conditions
                       (vec (concat block-conditions updated-rules))
                       (vec updated-rules))]
    (assoc block :rules new-rules-vec)))

(defn count-matches [config selectors]
  "Count how many rules match the selectors"
  (let [main-blocks (get-in config [:main])]
    (reduce + (for [block main-blocks
                    :let [des (:des block)
                          rules-vec (:rules block)
                          block-conditions (when (and (sequential? rules-vec)
                                                      (or (keyword? (first rules-vec))
                                                          (and (vector? (first rules-vec))
                                                               (not (map? (ffirst rules-vec))))))
                                            (vec (take-while #(or (keyword? %)
                                                                  (and (vector? %)
                                                                       (not (map? (first %)))))
                                                             rules-vec)))
                          actual-rules (if block-conditions
                                        (drop (count block-conditions) rules-vec)
                                        rules-vec)]]
                (count (filter #(rule-matches-selectors? % des block-conditions selectors) actual-rules))))))

(defn -main [& args]
  (let [{:keys [input output operation value selectors]} (parse-args args)]
    (when (or (nil? input) (nil? output) (nil? operation) (empty? selectors))
      (println "Usage: bb update-rule.bb <input> <output> <operation> <value> [selectors]")
      (println "")
      (println "Operations:")
      (println "  add-to-action <value>     Add value to action array")
      (println "  set-action <value>        Replace entire action")
      (println "  add-condition <value>     Add condition to rule")
      (println "  remove-condition <var>    Remove condition for variable name")
      (println "")
      (println "Selectors (at least one required):")
      (println "  --id PATTERN             Match rules with ID containing PATTERN")
      (println "  --block NAME             Match rules in block containing NAME")
      (println "  --layer N                Match rules with dsk_layer=N")
      (println "  --submode N              Match rules with dsk_ins_sub_mode=N")
      (System/exit 1))

    (let [config (edn/read-string (slurp input))
          match-count (count-matches config selectors)]
      (println (str "Found " match-count " matching rule(s)"))

      (if (zero? match-count)
        (println "No changes made.")
        (let [updated-config (update config :main
                                    (fn [blocks]
                                      (mapv #(process-block % selectors operation value) blocks)))]
          (spit output (with-out-str (pprint/pprint updated-config)))
          (println (str "Updated " match-count " rule(s), written to " output)))))))

(apply -main *command-line-args*)
