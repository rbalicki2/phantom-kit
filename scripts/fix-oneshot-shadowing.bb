#!/usr/bin/env bb

;; Fix oneshot letter rules that shadow Shift+letter rules
;;
;; The bare letter rules in submode 1 (like `l → S`) have no :modi clause,
;; which means they match ANY modifiers. This shadows the Shift+letter rules.
;;
;; Fix: Add :modi {:optional [:caps_lock]} to restrict bare letter rules
;; to only match without modifiers.
;;
;; Usage:
;;   bb fix-oneshot-shadowing.bb <input-edn> <output-edn>

(require '[clojure.string :as str])

(defn fix-bare-key-rule [line]
  "Add :modi {:optional [:caps_lock]} to bare key rules in submode blocks"
  ;; Match rules like: [{:key :l, :id "..."} ...]
  ;; But NOT rules that already have :modi
  (if (and (str/includes? line "{:key :")
           (str/includes? line "dsk_ins_sub_mode=")
           (not (str/includes? line ":modi"))
           ;; Only fix letter keys in oneshot blocks, not F-keys etc
           (re-find #"\{:key :([a-z]|semicolon|comma|period|slash)," line))
    (str/replace line
                 #"\{:key :([a-z]+|semicolon|comma|period|slash), :id"
                 "{:key :$1, :modi {:optional [:caps_lock]}, :id")
    line))

(defn -main [& args]
  (let [[input-file output-file] args]
    (when (or (nil? input-file) (nil? output-file))
      (println "Usage: bb fix-oneshot-shadowing.bb <input-edn> <output-edn>")
      (System/exit 1))

    (let [lines (str/split-lines (slurp input-file))
          fixed-lines (map fix-bare-key-rule lines)
          pairs (map vector lines fixed-lines)
          changes (count (filter (fn [[old new]] (not= old new)) pairs))]

      (println (str "Fixed " changes " rules"))

      (when (pos? changes)
        (println "\nSample changes:")
        (doseq [[old new] (take 5 (filter #(not= (first %) (second %))
                                          (map vector lines fixed-lines)))]
          (println "  Before:" (subs old 0 (min 80 (count old))) "...")
          (println "  After: " (subs new 0 (min 80 (count new))) "...")
          (println)))

      (spit output-file (str/join "\n" fixed-lines))
      (println (str "Written to " output-file)))))

(apply -main *command-line-args*)
