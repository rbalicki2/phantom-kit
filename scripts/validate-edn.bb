#!/usr/bin/env bb
;; Validation script for karabiner.edn
;; Checks for patterns that cause null outputs in generated JSON
;;
;; The issue: When an action array STARTS with a variable set like ["dsk_layer" 0],
;; Goku generates null as the first element of the "to" array. The fix is to
;; prepend :vk_none so there's a valid key output first.
;;
;; Valid patterns:
;;   [:key] - single key output
;;   [:vk_none] - single no-op output
;;   [{:shell "..."}] - single shell command (as entire action)
;;   [:key ["var" 0] {:shell "..."}] - key first, then var sets, then shell
;;   [:vk_none ["var" 0] {:shell "..."}] - vk_none first, then var sets
;;
;; Invalid patterns:
;;   [["dsk_layer" 0] ...] - variable set first (causes null)
;;   [[key] ...] where [key] is a wrapped single key - unnecessary wrapper
;;   [{:shell "..."} ... {:shell "..."}] - multiple shells (only LAST executes!)

(require '[clojure.edn :as edn])

(def content (slurp "karabiner.edn"))
(def parsed (edn/read-string content))

(def errors (atom []))

(defn is-variable-set?
  "Check if item looks like a variable set [\"name\" value]"
  [item]
  (and (vector? item)
       (= 2 (count item))
       (string? (first item))))

(defn is-shell-command?
  "Check if item is a shell command {:shell \"...\"}"
  [item]
  (and (map? item) (contains? item :shell)))

(defn is-rule?
  "Check if data looks like a Goku rule"
  [data]
  (and (vector? data)
       (>= (count data) 2)
       (or (keyword? (first data))
           (map? (first data)))))

(defn validate-action-array
  "Validate the action (to) array of a rule"
  [to-arr path]
  (when (and (vector? to-arr) (not-empty to-arr))
    (let [first-elem (first to-arr)
          shell-commands (filter is-shell-command? to-arr)]
      ;; Check if the action array starts with a variable set
      (when (is-variable-set? first-elem)
        (swap! errors conj {:path (conj path 0)
                            :issue "Action array starts with variable set (causes null)"
                            :value (pr-str first-elem)
                            :suggestion "Prepend :vk_none to the action array"}))
      ;; Check for multiple shell commands (only LAST one executes!)
      (when (> (count shell-commands) 1)
        (swap! errors conj {:path path
                            :issue (str "Multiple shell commands (" (count shell-commands) ") - only LAST executes!")
                            :value (pr-str (vec shell-commands))
                            :suggestion "Combine into single shell with && or ;"})))))

(defn validate-rule [rule path]
  (let [to (second rule)]
    ;; Only validate if the action is an array
    (when (vector? to)
      (validate-action-array to (conj path :to)))))

(defn walk-rules [data path]
  (cond
    (is-rule? data)
    (do
      (validate-rule data path)
      (doseq [[idx item] (map-indexed vector data)]
        (walk-rules item (conj path idx))))

    (map? data)
    (doseq [[k v] data]
      (walk-rules v (conj path k)))

    (sequential? data)
    (doseq [[idx item] (map-indexed vector data)]
      (walk-rules item (conj path idx)))))

;; Run validation
(walk-rules parsed [])

;; Report results
(if (empty? @errors)
  (do
    (println "✓ All rules validated successfully!")
    (System/exit 0))
  (do
    (println (str "✗ Found " (count @errors) " issue(s):\n"))
    (doseq [err @errors]
      (println (str "  Path: " (:path err)))
      (println (str "  Issue: " (:issue err)))
      (println (str "  Value: " (:value err)))
      (when (:suggestion err)
        (println (str "  Fix: " (:suggestion err))))
      (println))
    (System/exit 1)))
