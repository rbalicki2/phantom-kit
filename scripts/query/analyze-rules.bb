#!/usr/bin/env bb
;; Analyze karabiner.edn rules for common issues
;;
;; Checks for:
;; - Rules where dsk_ins_sub_mode might be wrong (0 when should be -1)
;; - Rules where dsk_return_to_layer might be wrong
;; - Variable set patterns that could cause issues
;;
;; Usage:
;;   bb analyze-rules.bb <edn-file> [--state STATE]
;;
;; Examples:
;;   bb analyze-rules.bb src/karabiner.edn
;;   bb analyze-rules.bb src/karabiner.edn --state "layer=1"

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; Load state library
(def script-dir (-> (System/getProperty "babashka.file")
                    (java.io.File.)
                    (.getParentFile)
                    (.getAbsolutePath)))
(load-file (str script-dir "/../lib/state.bb"))

(defn parse-args [args]
  (loop [args args
         result {:file nil :state nil}]
    (if (empty? args)
      result
      (let [[arg & rest] args]
        (cond
          (= arg "--state")
          (recur (drop 1 rest) (assoc result :state (first rest)))

          (nil? (:file result))
          (recur rest (assoc result :file arg))

          :else
          (recur rest result))))))

(defn extract-rules-with-context [config]
  "Extract all rules with their block context"
  (for [block (:main config)
        :let [des (:des block)
              rules-vec (:rules block)
              block-conds (when (sequential? rules-vec)
                           (vec (take-while #(or (keyword? %)
                                                 (and (vector? %) (not (map? (first %)))))
                                            rules-vec)))
              actual-rules (when block-conds
                            (drop (count block-conds) rules-vec))]
        rule (filter vector? (or actual-rules rules-vec))]
    {:block des
     :block-conditions block-conds
     :rule rule
     :from (first rule)
     :to (second rule)
     :condition (when (>= (count rule) 3) (nth rule 2))}))

(defn get-var-sets [action]
  "Extract variable sets from action"
  (when (vector? action)
    (->> action
         (filter #(and (vector? %) (= 2 (count %)) (string? (first %))))
         (map (fn [[k v]] [(keyword k) v]))
         (into {}))))

(defn get-condition-value [condition var-name]
  (when (and (vector? condition) (every? vector? condition))
    (some (fn [[k v]] (when (= k var-name) v)) condition)))

(defn check-submode-issues [rules]
  "Find rules setting dsk_ins_sub_mode=0 outside layer 1"
  (for [{:keys [block rule to condition]} rules
        :let [var-sets (get-var-sets to)
              dest-layer (:dsk_layer var-sets)
              dest-submode (:dsk_ins_sub_mode var-sets)]
        :when (and dest-layer
                   (not= dest-layer 1)
                   (= dest-submode 0))]
    {:issue "dsk_ins_sub_mode=0 outside layer 1 (should be -1)"
     :block block
     :dest-layer dest-layer
     :rule-id (when (map? (first rule)) (:id (first rule)))}))

(defn check-return-to-issues [rules]
  "Find rules setting dsk_return_to_layer outside layer 13"
  (for [{:keys [block rule to condition]} rules
        :let [var-sets (get-var-sets to)
              dest-layer (:dsk_layer var-sets)
              dest-return (:dsk_return_to_layer var-sets)]
        :when (and dest-layer
                   (not= dest-layer 13)
                   dest-return
                   (not= dest-return -1))]
    {:issue "dsk_return_to_layer set outside layer 13"
     :block block
     :dest-layer dest-layer
     :return-to dest-return
     :rule-id (when (map? (first rule)) (:id (first rule)))}))

(defn filter-by-state [rules state-str]
  "Filter rules by state string"
  (if (nil? state-str)
    rules
    (let [parsed (state/parse-state-string state-str)]
      (filter (fn [{:keys [condition]}]
                (let [rule-layer (get-condition-value condition "dsk_layer")
                      rule-submode (get-condition-value condition "dsk_ins_sub_mode")]
                  (and (or (nil? (:layer parsed)) (= rule-layer (:layer parsed)))
                       (or (nil? (:submode parsed)) (= rule-submode (:submode parsed))))))
              rules))))

(defn -main [& args]
  (let [{:keys [file state]} (parse-args args)]
    (when (nil? file)
      (println "Usage: bb analyze-rules.bb <edn-file> [--state STATE]")
      (System/exit 1))

    (println (str "=== Analyzing " file " ===\n"))

    (let [config (edn/read-string (slurp file))
          all-rules (extract-rules-with-context config)
          rules (filter-by-state all-rules state)
          submode-issues (check-submode-issues rules)
          return-issues (check-return-to-issues rules)]

      (when state
        (println (str "Filtered to state: " state "\n")))

      (println (str "Total rules analyzed: " (count rules)))

      (when (seq submode-issues)
        (println "\n--- Submode Issues ---")
        (doseq [{:keys [issue block dest-layer rule-id]} submode-issues]
          (println (str "  " (or rule-id "unknown") ": " issue))))

      (when (seq return-issues)
        (println "\n--- Return-to Issues ---")
        (doseq [{:keys [issue block dest-layer return-to rule-id]} return-issues]
          (println (str "  " (or rule-id "unknown") ": " issue " (return=" return-to ")"))))

      (when (and (empty? submode-issues) (empty? return-issues))
        (println "\n✓ No issues found!"))

      (println "\n=== Analysis complete ==="))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
