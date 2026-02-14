#!/usr/bin/env bb

;; Fix rule IDs to have accurate state strings based on actual conditions
;;
;; Usage:
;;   bb fix-rule-ids.bb <input-edn> <output-edn>
;;
;; This script parses the EDN to understand conditions, then does string
;; replacement on rule IDs to update the state marker.

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; === Condition extraction ===

(defn extract-block-conditions [rules-vec]
  (when (sequential? rules-vec)
    (vec (take-while #(or (keyword? %)
                          (and (vector? %) (not (map? (first %)))))
                     rules-vec))))

(defn get-condition-value [conds var-name]
  (some (fn [c]
          (when (and (vector? c) (string? (first c)) (= (first c) var-name))
            (second c)))
        conds))

(defn extract-rule-conditions [rule]
  (let [conds (nth rule 2 nil)]
    (when (sequential? conds)
      (if (and (vector? (first conds)) (string? (ffirst conds)))
        conds
        [conds]))))

(defn get-profile-from-block [block-conditions]
  (cond
    (some #(= % :Desktop) block-conditions) "Desktop"
    (some #(= % :Laptop) block-conditions) "Laptop"
    :else nil))

;; === State string generation ===

(defn generate-state-string [profile layer submode return-to]
  (let [parts (cond-> []
                profile (conj (str "profile=" profile))
                layer (conj (str "dsk_layer=" layer))
                (and submode (not= submode -1)) (conj (str "dsk_ins_sub_mode=" submode))
                (and return-to (not= return-to -1)) (conj (str "dsk_return_to_layer=" return-to)))]
    (if (empty? parts)
      "global"
      (str/join ":" parts))))

;; === Build replacement map ===

(defn build-replacement-map [config]
  "Build map of rule-id -> new-state-string"
  (into {}
    (for [block (:main config)
          :let [rules-vec (:rules block)
                block-conditions (extract-block-conditions rules-vec)
                actual-rules (if block-conditions
                               (drop (count block-conditions) rules-vec)
                               rules-vec)]
          rule actual-rules
          :let [from (first rule)
                old-id (when (map? from) (:id from))]
          :when old-id
          :let [;; Extract rule number
                rule-num (second (re-find #"^(R\d+[a-z]?)" old-id))

                ;; Get conditions
                rule-conds (extract-rule-conditions rule)
                all-conds (concat (filter vector? block-conditions) rule-conds)

                ;; Get actual values
                profile (get-profile-from-block block-conditions)
                layer (get-condition-value all-conds "dsk_layer")
                submode (get-condition-value all-conds "dsk_ins_sub_mode")
                return-to (get-condition-value all-conds "dsk_return_to_layer")

                ;; Generate new state string
                state-string (generate-state-string profile layer submode return-to)]]
      [rule-num state-string])))

;; === String replacement ===

(defn replace-state-markers [content replacements]
  "Replace state markers in the file content using regex"
  (reduce
   (fn [text [rule-num new-state]]
     ;; Match: "R0051 [anything] rest"
     ;; Replace with: "R0051 [new-state] rest"
     (str/replace text
                  (re-pattern (str "\"" rule-num " \\[[^\\]]*\\]"))
                  (str "\"" rule-num " [" new-state "]")))
   content
   replacements))

;; === Main ===

(defn -main [& args]
  (let [[input-file output-file] args]
    (when (or (nil? input-file) (nil? output-file))
      (println "Usage: bb fix-rule-ids.bb <input-edn> <output-edn>")
      (System/exit 1))

    (println "Parsing EDN...")
    (let [config (edn/read-string (slurp input-file))
          replacements (build-replacement-map config)]

      (println (str "Found " (count replacements) " rule IDs to update"))

      ;; Show some examples
      (println "\nSample replacements:")
      (doseq [[rule-num state] (take 5 (sort replacements))]
        (println (str "  " rule-num " -> [" state "]")))

      ;; Read original file as string and do replacements
      (println "\nApplying replacements...")
      (let [original-content (slurp input-file)
            updated-content (replace-state-markers original-content replacements)]

        (spit output-file updated-content)
        (println (str "Written to " output-file))
        (println "Done!")))))

(apply -main *command-line-args*)
