#!/usr/bin/env bb

;; Fix rule IDs to have accurate state strings and descriptive names
;;
;; Usage:
;;   bb fix-rule-ids.bb <input-edn> <output-edn>
;;
;; This script:
;; 1. Parses EDN to extract actual conditions
;; 2. Generates state string in format: dsk_layer=1:dsk_ins_sub_mode=0
;; 3. Extracts the key mapping (from -> to)
;; 4. Creates concise, descriptive rule IDs

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

;; === Key description ===

(defn format-key [from-map]
  "Extract a readable key description from the from map"
  (if (map? from-map)
    (let [key-val (:key from-map)
          pkey (:pkey from-map)
          modi (:modi from-map)
          mandatory (get modi :mandatory [])
          key-name (name (or key-val pkey :unknown))]
      (if (seq mandatory)
        (str (str/join "+" (map name mandatory)) "+" key-name)
        key-name))
    (if (keyword? from-map)
      (name from-map)
      "?")))

(defn format-action [action]
  "Extract a readable action description"
  (cond
    (keyword? action) (name action)
    (vector? action)
    (let [first-item (first action)]
      (cond
        (keyword? first-item) (name first-item)
        (map? first-item) (if-let [shell (:shell first-item)]
                           "shell"
                           "action")
        :else "action"))
    :else "action"))

;; === Build replacement map ===

(defn build-replacement-map [config]
  "Build map of old-id-pattern -> new-full-id"
  (into {}
    (for [block (:main config)
          :let [block-des (:des block)
                rules-vec (:rules block)
                block-conditions (extract-block-conditions rules-vec)
                actual-rules (if block-conditions
                               (drop (count block-conditions) rules-vec)
                               rules-vec)]
          rule actual-rules
          :let [from (first rule)
                action (second rule)
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

                ;; Generate state string
                state-string (generate-state-string profile layer submode return-to)

                ;; Get key mapping description
                key-desc (format-key from)
                action-desc (format-action action)

                ;; Build new ID: "R0051 [state] key -> action"
                new-id (str rule-num " [" state-string "] " key-desc " -> " action-desc)]]
      [old-id new-id])))

;; === String replacement ===

(defn escape-regex [s]
  "Escape special regex characters in a string"
  (str/escape s {\[ "\\["
                 \] "\\]"
                 \( "\\("
                 \) "\\)"
                 \{ "\\{"
                 \} "\\}"
                 \. "\\."
                 \+ "\\+"
                 \* "\\*"
                 \? "\\?"
                 \^ "\\^"
                 \$ "\\$"
                 \| "\\|"
                 \\ "\\\\"}))

(defn replace-ids [content replacements]
  "Replace rule IDs in the file content"
  (reduce
   (fn [text [old-id new-id]]
     (str/replace text
                  (str ":id \"" old-id "\"")
                  (str ":id \"" new-id "\"")))
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
      (doseq [[old-id new-id] (take 10 (sort-by first replacements))]
        (println (str "  OLD: " old-id))
        (println (str "  NEW: " new-id))
        (println))

      ;; Read original file as string and do replacements
      (println "Applying replacements...")
      (let [original-content (slurp input-file)
            updated-content (replace-ids original-content replacements)]

        (spit output-file updated-content)
        (println (str "Written to " output-file))
        (println "Done!")))))

(apply -main *command-line-args*)
