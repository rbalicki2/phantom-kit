#!/usr/bin/env bb
;; Fix Fallback Modifiers
;;
;; Changes {:optional [:shift]} to {:optional [:any]} ONLY for fallback blocker rules
;; in the Desktop [] block (no layer conditions).
;;
;; Usage:
;;   bb scripts/edit/fix-fallback-modifiers.bb src/karabiner.edn

(ns fix-fallback-modifiers
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn is-fallback-block?
  "Check if this is the Desktop fallback block (des = 'Desktop []')"
  [block]
  (= (:des block) "Desktop []"))

(defn is-blocker-rule?
  "Check if this rule is a blocker (has 'blocked' in ID)"
  [rule]
  (when (vector? rule)
    (let [from-map (first rule)]
      (when (map? from-map)
        (let [id (:id from-map)]
          (and (string? id)
               (str/includes? id "blocked")))))))

(defn needs-fix?
  "Check if rule has {:optional [:shift]} that needs to be changed"
  [rule]
  (when (vector? rule)
    (let [from-map (first rule)]
      (and (map? from-map)
           (= (get-in from-map [:modi :optional]) [:shift])))))

(defn fix-rule
  "Fix a single rule's :modi to use [:any] instead of [:shift]"
  [rule]
  (let [from-map (first rule)
        rest-rule (rest rule)
        fixed-from (update from-map :modi #(assoc % :optional [:any]))]
    (vec (cons fixed-from rest-rule))))

(defn -main [& args]
  (when (empty? args)
    (println "Usage: bb fix-fallback-modifiers.bb <edn-file>")
    (System/exit 1))

  (let [edn-file (first args)
        content (slurp edn-file)
        config (edn/read-string content)

        ;; Find the fallback block
        fallback-block (->> (:main config)
                            (filter is-fallback-block?)
                            first)

        ;; Find blocker rules that need fixing
        rules-to-fix (->> (:rules fallback-block)
                          (filter vector?)
                          (filter is-blocker-rule?)
                          (filter needs-fix?))]

    (println "Fallback block found:" (boolean fallback-block))
    (println "Blocker rules to fix:" (count rules-to-fix))

    (if (seq rules-to-fix)
      (do
        ;; Get the rule IDs that need fixing
        (println "\nRules to fix:")
        (doseq [rule rules-to-fix]
          (println "  -" (get-in rule [0 :id])))

        ;; For each rule, do a targeted string replacement
        ;; We'll find the rule by its ID and replace {:optional [:shift]} with {:optional [:any]}
        (let [fixed-content
              (reduce (fn [content rule]
                        (let [rule-id (get-in rule [0 :id])
                              ;; Find the rule in content and replace its modifier
                              ;; Pattern: find the line with this ID and {:optional [:shift]}
                              pattern (re-pattern (str "(:id \"" (java.util.regex.Pattern/quote rule-id) "\".*?):optional \\[:shift\\]"))
                              replacement (str "$1:optional [:any]")]
                          (str/replace content pattern replacement)))
                      content
                      rules-to-fix)]
          (spit edn-file fixed-content)
          (println "\nFixed" (count rules-to-fix) "rules")
          (println "Done!")))
      (println "No changes needed"))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
