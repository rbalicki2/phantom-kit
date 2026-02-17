#!/usr/bin/env bb
;; Swap ALL Insert mode transitions to AltIns mode
;; Properly parses EDN to distinguish between:
;; - Rules WITHIN layer 1 (condition contains dsk_layer=1) - leave unchanged
;; - Rules that TRANSITION TO layer 1 (output sets dsk_layer to 1) - change to layer 7

(require '[clojure.string :as str]
         '[clojure.edn :as edn])

(def input-file (first *command-line-args*))

(when-not input-file
  (println "Usage: bb scripts/edit/swap-ins-to-altins-v3.bb <karabiner.edn>")
  (System/exit 1))

(def content (slurp input-file))
(def config (edn/read-string content))

(defn get-condition-layer
  "Extract the dsk_layer value from a rule's condition (last element)"
  [rule]
  (let [condition (last rule)]
    (when (vector? condition)
      (some (fn [c]
              (when (and (vector? c) (= (first c) "dsk_layer"))
                (second c)))
            condition))))

(defn get-output-layer
  "Extract the dsk_layer value from a rule's output (second element)"
  [rule]
  (let [output (second rule)]
    (when (vector? output)
      (some (fn [o]
              (when (and (vector? o) (= (first o) "dsk_layer"))
                (second o)))
            output))))

(defn rule-id [rule]
  (get-in rule [0 :id] ""))

(defn transitions-to-layer-1?
  "Check if this rule transitions TO layer 1 (not a rule within layer 1)"
  [rule]
  (let [condition-layer (get-condition-layer rule)
        output-layer (get-output-layer rule)]
    (and (= output-layer 1)
         (not= condition-layer 1))))

(defn preserve-rule?
  "Rules to preserve as legacy Insert mode"
  [rule]
  (str/includes? (rule-id rule) "R0060"))

(defn update-output-layer
  "Change dsk_layer from 1 to 7 in the output"
  [output]
  (if (vector? output)
    (mapv (fn [o]
            (if (and (vector? o) (= (first o) "dsk_layer") (= (second o) 1))
              ["dsk_layer" 7]
              o))
          output)
    output))

(defn update-shell-command
  "Change 'echo ins' to 'echo altins' and add overlay call"
  [output]
  (if (vector? output)
    (mapv (fn [o]
            (if (and (map? o) (:shell o))
              (let [shell (:shell o)]
                (if (str/includes? shell "echo ins > /tmp/karabiner-layer")
                  (assoc o :shell
                         (str/replace shell
                                      "echo ins > /tmp/karabiner-layer"
                                      "echo altins > /tmp/karabiner-layer && /opt/homebrew/bin/hs -c 'showPermanentLayerOverlay()'"))
                  o))
              o))
          output)
    output))

(defn transform-rule
  "Transform a rule that transitions to layer 1"
  [rule]
  (if (and (transitions-to-layer-1? rule)
           (not (preserve-rule? rule)))
    (let [from (first rule)
          output (second rule)
          condition (last rule)
          rest-elements (drop 2 (butlast rule))
          new-output (-> output
                         update-output-layer
                         update-shell-command)]
      (vec (concat [from new-output] rest-elements [condition])))
    rule))

(defn transform-section
  "Transform rules in a section"
  [section]
  (if (:rules section)
    (update section :rules
            (fn [rules]
              (mapv (fn [r]
                      (if (= r :!apple_internal)
                        r
                        (transform-rule r)))
                    rules)))
    section))

(def transformed-config
  (update config :main
          (fn [sections]
            (mapv transform-section sections))))

;; Count changes
(def changes
  (atom 0))

(doseq [section (:main config)]
  (when (:rules section)
    (doseq [rule (:rules section)]
      (when (and (vector? rule)
                 (transitions-to-layer-1? rule)
                 (not (preserve-rule? rule)))
        (swap! changes inc)))))

(spit input-file (pr-str transformed-config))

(println (str "Transformed " @changes " rules from Insert to AltIns"))
(println "R0060 (/ key) preserved as legacy Insert")
