#!/usr/bin/env bb
;; Delete a rule by ID
;;
;; Usage:
;;   bb scripts/edit/delete-rule.bb <edn-file> <rule-id>
;;
;; Example:
;;   bb scripts/edit/delete-rule.bb src/karabiner.edn R0081

(ns delete-rule
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

(defn rule-has-id? [rule id-prefix]
  "Check if a rule has an ID matching the prefix (e.g., R0081)"
  (when (and (vector? rule) (map? (first rule)))
    (let [rule-id (get (first rule) :id)]
      (when rule-id
        (str/starts-with? rule-id id-prefix)))))

(defn delete-rule-from-block [block rule-id]
  "Remove a rule with the given ID from a block"
  (let [rules-vec (:rules block)
        ;; Separate keywords (conditions) from actual rules
        keywords (take-while keyword? rules-vec)
        actual-rules (drop-while keyword? rules-vec)
        ;; Filter out the rule with matching ID
        filtered-rules (remove #(rule-has-id? % rule-id) actual-rules)
        deleted-count (- (count actual-rules) (count filtered-rules))]
    {:block (assoc block :rules (vec (concat keywords filtered-rules)))
     :deleted deleted-count}))

(defn delete-rule [config rule-id]
  "Delete a rule from the config, returns {:config ... :deleted-count ...}"
  (let [results (map #(delete-rule-from-block % rule-id) (:main config))
        new-blocks (map :block results)
        total-deleted (reduce + (map :deleted results))]
    {:config (assoc config :main (vec new-blocks))
     :deleted-count total-deleted}))

(defn format-edn [config]
  "Format config as EDN string"
  (with-out-str (pp/pprint config)))

(defn -main [& args]
  (when (< (count args) 2)
    (println "Usage: bb delete-rule.bb <edn-file> <rule-id>")
    (System/exit 1))

  (let [[edn-file rule-id] args
        config (edn/read-string (slurp edn-file))
        {:keys [config deleted-count]} (delete-rule config rule-id)]

    (if (zero? deleted-count)
      (do
        (println (str "Error: No rule found with ID " rule-id))
        (System/exit 1))
      (do
        (spit edn-file (format-edn config))
        (println (str "Deleted " deleted-count " rule(s) with ID " rule-id))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
