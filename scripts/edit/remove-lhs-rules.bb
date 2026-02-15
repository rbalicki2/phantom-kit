#!/usr/bin/env bb
;; Remove LHS key rules from Desktop profile blocks where they shouldn't appear.
;; LHS keys should ONLY be in:
;; - [Global] Block all unmapped keys
;; - [Desktop] Disable backspace and delete keys
;;
;; Usage:
;;   bb remove-lhs-rules.bb <input-edn> <output-edn>

(ns remove-lhs-rules
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

(def lhs-physical-keys
  "Physical keys on the left-hand side of Kinesis Advantage 360.
   Note: backslash is on RHS on Kinesis 360 (not LHS like standard keyboards)."
  #{:equal_sign :1 :2 :3 :4 :5
    :tab :q :w :e :r :t
    :caps_lock :a :s :d :f :g
    :left_shift :z :x :c :v :b
    :grave_accent_and_tilde
    :left_command :left_option
    :delete_or_backspace :delete_forward
    :home :end :escape})

(def allowed-blocks
  "Regex patterns for blocks where LHS keys ARE allowed"
  [#"\[Global\] Block all unmapped keys"
   #"\[Desktop\] Disable backspace and delete keys"])

(defn allowed-block? [des]
  (and des (some #(re-find % des) allowed-blocks)))

(defn desktop-block? [rules-vec]
  "Check if block targets Desktop profile"
  (when (vector? rules-vec)
    (or (some #(= % :Desktop) rules-vec)
        (some #(= % :!apple_internal) rules-vec))))

(defn extract-base-key [from-clause]
  "Extract base key from a from clause"
  (let [key-val (cond
                  (keyword? from-clause) from-clause
                  (map? from-clause) (:key from-clause)
                  :else nil)]
    (when (keyword? key-val)
      (let [key-name (name key-val)
            stripped (if (str/starts-with? key-name "##")
                       (subs key-name 2)
                       key-name)
            base (if (str/starts-with? stripped "!")
                   (apply str (drop-while #(Character/isUpperCase %) (subs stripped 1)))
                   stripped)]
        (when (not (str/blank? base))
          (keyword base))))))

(defn is-lhs-rule? [rule]
  "Check if a rule uses an LHS key"
  (when (vector? rule)
    (let [from (first rule)
          base-key (extract-base-key from)]
      (contains? lhs-physical-keys base-key))))

(defn filter-rules [rules-vec]
  "Remove LHS key rules from a rules vector, preserving keywords"
  (let [keywords (take-while keyword? rules-vec)
        rules (drop (count keywords) rules-vec)
        filtered-rules (remove is-lhs-rule? rules)]
    (vec (concat keywords filtered-rules))))

(defn process-block [block]
  "Process a single block, removing LHS rules if it's a Desktop block that's not allowed"
  (let [des (:des block)
        rules-vec (:rules block)]
    (if (and (desktop-block? rules-vec)
             (not (allowed-block? des)))
      (let [original-count (count (filter vector? rules-vec))
            filtered (filter-rules rules-vec)
            new-count (count (filter vector? filtered))
            removed (- original-count new-count)]
        (when (> removed 0)
          (println (format "  Removed %d LHS rules from '%s'" removed des)))
        (assoc block :rules filtered))
      block)))

(defn -main [& args]
  (when (< (count args) 2)
    (println "Usage: bb remove-lhs-rules.bb <input-edn> <output-edn>")
    (System/exit 1))

  (let [input-file (first args)
        output-file (second args)
        config (edn/read-string (slurp input-file))
        _ (println "Processing blocks...")
        new-main (mapv process-block (:main config))
        new-config (assoc config :main new-main)]

    (println "\nWriting output...")
    (spit output-file (with-out-str (pp/pprint new-config)))
    (println (format "Done! Output written to %s" output-file))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
