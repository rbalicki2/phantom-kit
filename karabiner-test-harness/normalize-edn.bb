#!/usr/bin/env bb
;; Normalize karabiner.edn to use explicit syntax:
;; - from: bare keyword → {:key :keyword}
;; - to: bare keyword → [:keyword]
;; - condition: single ["var" val] → [["var" val]]
;;
;; Usage:
;;   bb normalize-edn.bb <input.edn> [output.edn]
;;   If output not specified, prints to stdout

(ns normalize-edn
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]))

;; ============================================================================
;; Rule Normalization
;; ============================================================================

(defn normalize-from [from]
  "Convert bare keyword to {:key :keyword}"
  (if (keyword? from)
    {:key from}
    from))

(defn normalize-to [to]
  "Convert bare keyword to [:keyword], leave vectors alone"
  (cond
    (keyword? to) [to]
    (map? to) [to]  ;; Single map like {:shell ...} → wrap in array
    (vector? to) to
    :else [to]))

(defn is-single-variable-condition? [condition]
  "Check if condition is a single variable like [\"dsk_layer\" 0]"
  (and (vector? condition)
       (= 2 (count condition))
       (string? (first condition))
       (not (vector? (second condition)))))

(defn normalize-condition [condition]
  "Wrap single variable condition in array"
  (cond
    (nil? condition) nil
    (keyword? condition) condition  ;; App keyword like :Chrome
    (is-single-variable-condition? condition) [condition]  ;; Wrap ["var" val] → [["var" val]]
    :else condition))

(defn normalize-rule [rule]
  "Normalize a single rule [from to] or [from to cond] or [from to cond opts]"
  (if (and (vector? rule) (>= (count rule) 2))
    (let [from (first rule)
          to (second rule)
          rest-items (drop 2 rule)
          normalized-from (normalize-from from)
          normalized-to (normalize-to to)]
      (if (empty? rest-items)
        [normalized-from normalized-to]
        (let [;; Check if 3rd element is condition or options
              third (first rest-items)
              has-condition? (or (nil? third)
                                (keyword? third)
                                (vector? third))
              condition (when has-condition? (normalize-condition third))
              options (if has-condition?
                        (second rest-items)
                        third)]
          (cond
            (and condition options) [normalized-from normalized-to condition options]
            condition [normalized-from normalized-to condition]
            options [normalized-from normalized-to nil options]
            :else [normalized-from normalized-to]))))
    rule))

(defn normalize-rules-vector [rules-vec]
  "Normalize all rules in a :rules vector, preserving leading keywords"
  (if (vector? rules-vec)
    (let [leading-kws (vec (take-while keyword? rules-vec))
          actual-rules (drop-while keyword? rules-vec)
          normalized-rules (map normalize-rule actual-rules)]
      (vec (concat leading-kws normalized-rules)))
    rules-vec))

(defn normalize-block [block]
  "Normalize a rule block"
  (if (map? block)
    (if-let [rules (:rules block)]
      (assoc block :rules (normalize-rules-vector rules))
      block)
    block))

(defn normalize-config [config]
  "Normalize the entire config"
  (if-let [main (:main config)]
    (assoc config :main (mapv normalize-block main))
    config))

;; ============================================================================
;; Pretty Printing
;; ============================================================================

(defn pprint-config [config]
  "Pretty print config with maximum readability"
  (binding [pp/*print-right-margin* 100
            pp/*print-miser-width* 60]
    (pp/pprint config)))

(defn pprint-config-to-string [config]
  "Pretty print config to string"
  (with-out-str (pprint-config config)))

;; ============================================================================
;; Main
;; ============================================================================

(defn -main [& args]
  (when (empty? args)
    (println "Usage: bb normalize-edn.bb <input.edn> [output.edn]")
    (System/exit 1))

  (let [input-file (first args)
        output-file (second args)
        config (edn/read-string (slurp input-file))
        normalized (normalize-config config)
        output-str (pprint-config-to-string normalized)]

    (if output-file
      (do
        (spit output-file output-str)
        (println (str "Normalized config written to " output-file)))
      (print output-str))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
