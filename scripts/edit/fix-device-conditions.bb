#!/usr/bin/env bb
;; Post-process karabiner.json to fix missing device conditions
;;
;; Problem: Goku drops device_unless conditions when a rule block combines
;;          :!apple_internal with app conditions like :Chrome
;; Solution: Add the device_unless condition to any manipulator that has
;;           frontmost_application_if but no device condition
;;
;; Run this after goku generates karabiner.json

(require '[cheshire.core :as json]
         '[clojure.java.io :as io])

(def karabiner-json-path (str (System/getenv "HOME") "/.config/karabiner/karabiner.json"))

;; The device condition that should be on all app-specific rules.
;; Must mirror the :apple_internal device list in src/karabiner.edn. The list is
;; OR'd by Karabiner: matches the old MacBook's USB keyboard (vendor/product) OR
;; any built-in keyboard. Newer MacBooks expose the built-in keyboard over FIFO
;; with NO vendor_id/product_id, so is_built_in_keyboard is the only identifier
;; that matches them — without it, app-specific :!apple_internal rules swallow
;; keys on the built-in keyboard and nothing is emitted.
(def apple-internal-device-unless
  {"type" "device_unless"
   "identifiers" [{"vendor_id" 1452
                   "product_id" 835
                   "is_keyboard" true}
                  {"is_built_in_keyboard" true}]})

(defn has-device-condition?
  "Check if a manipulator has any device_if or device_unless condition"
  [manipulator]
  (let [conditions (get manipulator "conditions" [])]
    (some #(#{"device_if" "device_unless"} (get % "type")) conditions)))

(defn has-app-condition?
  "Check if a manipulator has a frontmost_application_if condition"
  [manipulator]
  (let [conditions (get manipulator "conditions" [])]
    (some #(= "frontmost_application_if" (get % "type")) conditions)))

(defn fix-manipulator
  "Add device_unless condition if manipulator has app condition but no device condition"
  [manipulator]
  (if (and (has-app-condition? manipulator)
           (not (has-device-condition? manipulator)))
    (update manipulator "conditions" conj apple-internal-device-unless)
    manipulator))

(defn process-config
  "Process the entire config, fixing device conditions"
  [config]
  (update-in config ["profiles" 0 "complex_modifications" "rules"]
             (fn [rules]
               (mapv (fn [rule]
                       (update rule "manipulators"
                               (fn [manips]
                                 (mapv fix-manipulator manips))))
                     rules))))

(defn main []
  (let [config (json/parse-string (slurp karabiner-json-path))
        ;; Count affected rules before fix
        all-manips (mapcat #(get % "manipulators" [])
                           (get-in config ["profiles" 0 "complex_modifications" "rules"]))
        affected (count (filter #(and (has-app-condition? %)
                                      (not (has-device-condition? %)))
                                all-manips))
        processed (process-config config)
        output (json/generate-string processed {:pretty true})]
    (spit karabiner-json-path output)
    (println (str "Fixed " affected " app-specific rules: added device_unless for apple_internal keyboard"))))

(main)
