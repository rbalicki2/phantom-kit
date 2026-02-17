#!/usr/bin/env bb
;; Find the next available rule ID
;;
;; Usage:
;;   bb scripts/query/next-id.bb [edn-file]
;;
;; If no file specified, defaults to src/karabiner.edn

(ns next-id
  (:require [clojure.edn :as edn]))

(defn find-max-id [config]
  "Find the maximum rule ID number in the config"
  (->> (:main config)
       (mapcat :rules)
       (filter vector?)
       (map first)
       (filter map?)
       (map :id)
       (filter some?)
       (map #(second (re-find #"R(\d+)" %)))
       (filter some?)
       (map parse-long)
       (apply max 0)))

(defn -main [& args]
  (let [edn-file (or (first args) "src/karabiner.edn")
        config (edn/read-string (slurp edn-file))
        max-id (find-max-id config)
        next-id (inc max-id)]
    (println next-id)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
