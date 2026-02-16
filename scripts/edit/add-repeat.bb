#!/usr/bin/env bb
;; Post-process karabiner.json to enable key repeat in insert mode
;;
;; Problem: set_variable in the `to` array blocks key repeat
;; Solution: Move variable sets to `to_after_key_up` for repeatable keys
;;
;; Run this after goku generates karabiner.json

(require '[cheshire.core :as json]
         '[clojure.java.io :as io])

(def karabiner-json-path (str (System/getenv "HOME") "/.config/karabiner/karabiner.json"))

;; Keys that should repeat in insert mode
(def repeatable-keys
  #{"a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m"
    "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z"
    "0" "1" "2" "3" "4" "5" "6" "7" "8" "9"
    "hyphen" "equal_sign" "open_bracket" "close_bracket"
    "backslash" "semicolon" "quote" "grave_accent_and_tilde"
    "comma" "period" "slash" "spacebar"
    "delete_or_backspace" "delete_forward"
    "up_arrow" "down_arrow" "left_arrow" "right_arrow"
    "page_up" "page_down" "home" "end"})

(defn should-enable-repeat?
  "Check if this manipulator should have repeat enabled"
  [manipulator]
  (let [to-events (get manipulator "to" [])
        first-to (first to-events)]
    (and (map? first-to)
         (contains? first-to "key_code")
         (not (contains? first-to "shell_command"))
         (contains? repeatable-keys (get first-to "key_code")))))

(defn is-layer-1-block?
  "Check if this rule block is for insert mode (layer 1)"
  [rule]
  (let [desc (get rule "description" "")]
    (re-find #"dsk_layer\" 1\]" desc)))

(defn enable-repeat-for-manipulator
  "Enable repeat by:
   1. Adding repeat: true to the key_code event
   2. Moving set_variable events to to_after_key_up"
  [manipulator]
  (let [to-events (get manipulator "to" [])
        first-to (first to-events)
        rest-to (rest to-events)
        ;; Separate key events from variable sets
        key-event (when (and (map? first-to) (contains? first-to "key_code"))
                    (assoc first-to "repeat" true))
        ;; Get all set_variable events (could be in first-to or rest)
        var-sets (filter #(contains? % "set_variable") rest-to)
        ;; Get any other events that aren't set_variable
        other-events (remove #(contains? % "set_variable") rest-to)
        ;; Build new to array: just the key (with repeat) + other non-var events
        new-to (if key-event
                 (into [key-event] other-events)
                 to-events)
        ;; Existing to_after_key_up events
        existing-after (get manipulator "to_after_key_up" [])
        ;; New to_after_key_up: existing + variable sets
        new-after (into (vec existing-after) var-sets)]
    (cond-> manipulator
      true (assoc "to" new-to)
      (seq new-after) (assoc "to_after_key_up" new-after))))

(defn process-manipulator
  "Process a single manipulator"
  [manipulator]
  (if (should-enable-repeat? manipulator)
    (enable-repeat-for-manipulator manipulator)
    manipulator))

(defn process-rule
  "Process a rule block, enabling repeat for layer 1 rules"
  [rule]
  (if (is-layer-1-block? rule)
    (update rule "manipulators"
            (fn [manips]
              (mapv process-manipulator manips)))
    rule))

(defn process-config
  "Process the entire config"
  [config]
  (update-in config ["profiles" 0 "complex_modifications" "rules"]
             (fn [rules]
               (mapv process-rule rules))))

(defn main []
  (let [config (json/parse-string (slurp karabiner-json-path))
        processed (process-config config)
        output (json/generate-string processed {:pretty true})]
    (spit karabiner-json-path output)
    (println "Enabled key repeat for insert mode (moved var sets to to_after_key_up)")))

(main)
