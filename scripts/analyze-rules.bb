#!/usr/bin/env bb
;; Analyze karabiner.edn rules for common issues
;;
;; Checks for:
;; - Rules where dsk_ins_sub_mode might be wrong (0 when should be -1)
;; - Rules where dsk_return_to_layer might be wrong
;; - Variable set patterns that could cause issues
;;
;; Usage: bb bb-scripts/analyze-rules.bb

(require '[clojure.edn :as edn])
(require '[clojure.string :as str])

(def content (slurp "karabiner.edn"))
(def lines (str/split-lines content))

(println "=== Analyzing karabiner.edn ===\n")

;; Check for dsk_ins_sub_mode=0 with dsk_layer != 1
(println "Rules setting dsk_layer to 0 or >=2 with dsk_ins_sub_mode=0 (should be -1):")
(doseq [[idx line] (map-indexed vector lines)]
  (when (str/includes? line "[\"dsk_ins_sub_mode\" 0]")
    (let [context-start (max 0 (- idx 5))
          context-end (min (count lines) (+ idx 2))
          context-str (str/join "\n" (subvec (vec lines) context-start context-end))]
      (when (and (or (str/includes? context-str "[\"dsk_layer\" 0]")
                     (re-find #"\[\"dsk_layer\" [2-9]\]" context-str)
                     (re-find #"\[\"dsk_layer\" [12][0-9]\]" context-str))
                 (not (str/includes? context-str "[\"dsk_layer\" 1]")))
        (println (str "  Line " (inc idx) ": " (str/trim line)))))))

(println "\n=== Analysis complete ===")
