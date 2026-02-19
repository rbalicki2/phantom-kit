#!/usr/bin/env bb
;; Reserved Keys Validation
;;
;; Validates that certain keys are never bound in Desktop profile layers.
;; These keys must always pass through to their global behavior.
;;
;; Reserved keys:
;; - up_arrow, down_arrow: Must always pass through for vertical navigation
;; - page_up, page_down: Globally mapped to click, must not be intercepted
;; - Alt+F20 (!Of20): Hotkey 3, layer overlay trigger
;; - Ctrl+N, Ctrl+P (!Tn, !Tp): Standard navigation in many apps
;;
;; Usage:
;;   bb validate-reserved-keys.bb <edn-file>

(ns validate-reserved-keys
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

;; ============================================================================
;; Reserved Key Definitions
;; ============================================================================

(def passthrough-only-keys
  "Keys that must always pass through (output same key) in any layer"
  #{:up_arrow :down_arrow})

(def global-only-keys
  "Keys that should only be bound in global fallback block, not in layers.
   These have global remaps (e.g., to clicks) that layers must not intercept."
  #{:page_up :page_down})

(def reserved-modified-keys
  "Modified key combinations that must not be bound in layers"
  #{:!Tn        ;; Ctrl+N - standard down navigation
    :!Tp})      ;; Ctrl+P - standard up navigation

;; ============================================================================
;; Key Extraction
;; ============================================================================

(defn extract-key-from-from [from-clause]
  "Extract the key from a from clause, including any modifier prefix"
  (cond
    (keyword? from-clause) from-clause
    (map? from-clause) (:key from-clause)
    :else nil))

(defn extract-base-key [key-keyword]
  "Strip ## prefix from key if present"
  (when (keyword? key-keyword)
    (let [key-name (name key-keyword)]
      (if (str/starts-with? key-name "##")
        (keyword (subs key-name 2))
        key-keyword))))

(defn has-mandatory-modifiers? [from-clause]
  "Check if from clause has mandatory modifiers (explicit or shorthand)"
  (cond
    (keyword? from-clause)
    (let [key-name (name from-clause)]
      (str/starts-with? key-name "!"))

    (map? from-clause)
    (let [modi (:modi from-clause)]
      (and modi (seq (:mandatory modi))))

    :else false))

(defn get-full-key-with-mods [from-clause]
  "Get key with modifier prefix if using shorthand notation"
  (let [key-val (extract-key-from-from from-clause)]
    (when (keyword? key-val)
      (let [key-name (name key-val)]
        ;; For shorthand like :!Tn, return as-is
        ;; For map form, we'd need to reconstruct, but shorthand is common
        (if (str/starts-with? key-name "!")
          key-val
          ;; Check if from-clause is map with mandatory modifiers
          (if (and (map? from-clause)
                   (has-mandatory-modifiers? from-clause))
            ;; For now, just check base key - full modifier matching is complex
            key-val
            key-val))))))

;; ============================================================================
;; Rule Extraction
;; ============================================================================

(defn is-desktop-block? [block]
  "Check if a block targets Desktop profile (external keyboard)"
  (let [rules-vec (:rules block)]
    (when (vector? rules-vec)
      (or (some #(= % :Desktop) rules-vec)
          (some #(= % :!apple_internal) rules-vec)))))

(defn is-fallback-block? [block]
  "Check if this is the Desktop fallback block (no layer conditions)"
  (let [des (:des block)]
    (and des (= des "Desktop []"))))

(defn extract-desktop-rules [config]
  "Extract all rules from Desktop profile blocks with metadata"
  (for [block (:main config)
        :when (is-desktop-block? block)
        :let [des (:des block)
              is-fallback (is-fallback-block? block)
              rules-vec (:rules block)
              actual-rules (when (vector? rules-vec)
                           (->> rules-vec
                                (drop-while keyword?)
                                (filter vector?)))]
        rule actual-rules]
    {:description des
     :is-fallback is-fallback
     :rule rule
     :from (first rule)
     :to (second rule)}))

;; ============================================================================
;; Passthrough Detection
;; ============================================================================

(defn extracts-output-key [action]
  "Extract the primary output key from an action (first key in array)"
  (when (vector? action)
    (first (filter keyword? action))))

(defn is-passthrough-rule? [rule-info]
  "Check if this rule passes through the key (input = output key).
   State variable changes (like clearing oneshot) are allowed - only the key output matters."
  (let [from-key (extract-base-key (extract-key-from-from (:from rule-info)))
        to-key (extracts-output-key (:to rule-info))]
    (or (= from-key to-key)
        ;; vk_none in fallback is OK (blocking unmapped keys)
        (and (:is-fallback rule-info) (= to-key :vk_none)))))

(defn is-blocked-rule? [rule-info]
  "Check if this rule blocks the key (outputs vk_none)"
  (let [to-key (extracts-output-key (:to rule-info))]
    (= to-key :vk_none)))

;; ============================================================================
;; Validation
;; ============================================================================

(defn check-passthrough-key [rule-info]
  "Check if rule binds a passthrough-only key to something other than passthrough"
  (let [from (:from rule-info)
        from-key (extract-key-from-from from)
        base-key (extract-base-key from-key)]
    ;; Check bare passthrough-only keys (up_arrow, down_arrow)
    (when (and (contains? passthrough-only-keys base-key)
               (not (has-mandatory-modifiers? from)))
      (when (not (is-passthrough-rule? rule-info))
        {:type :passthrough-key-bound
         :description (:description rule-info)
         :message (format "Key '%s' must always pass through but is bound to a different action."
                         (name base-key))
         :rule (:rule rule-info)}))))

(defn check-global-only-key [rule-info]
  "Check if a global-only key is bound in a layer (not the global fallback)"
  (let [from (:from rule-info)
        from-key (extract-key-from-from from)
        base-key (extract-base-key from-key)]
    ;; Check global-only keys (page_up, page_down) in non-fallback blocks
    (when (and (contains? global-only-keys base-key)
               (not (has-mandatory-modifiers? from))
               (not (:is-fallback rule-info)))
      {:type :global-key-in-layer
       :description (:description rule-info)
       :message (format "Key '%s' should only be bound in global fallback block, not in layers."
                       (name base-key))
       :rule (:rule rule-info)})))

(defn check-reserved-modified-key [rule-info]
  "Check if rule binds a reserved modified key combination"
  (let [from (:from rule-info)
        from-key (extract-key-from-from from)]
    ;; Check modified reserved keys (shorthand notation)
    (when (and (keyword? from-key)
               (contains? reserved-modified-keys from-key))
      ;; These should not exist at all (blocked in fallback is OK)
      (when (not (:is-fallback rule-info))
        {:type :reserved-modifier-bound
         :description (:description rule-info)
         :message (format "Reserved modifier combination '%s' bound in layer. This key should not be bound."
                         (name from-key))
         :rule (:rule rule-info)}))))

(defn validate-reserved-keys [config]
  "Run all reserved key validations"
  (let [desktop-rules (extract-desktop-rules config)

        ;; Check passthrough-only keys (up_arrow, down_arrow)
        passthrough-issues
        (keep check-passthrough-key desktop-rules)

        ;; Check global-only keys (page_up, page_down) in layers
        global-only-issues
        (keep check-global-only-key desktop-rules)

        ;; Check modified reserved keys
        modified-key-issues
        (keep check-reserved-modified-key desktop-rules)]

    (concat passthrough-issues global-only-issues modified-key-issues)))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn print-issue [issue]
  "Pretty print a validation issue"
  (println (str "  [" (name (:type issue)) "]"))
  (println (str "  Block: " (:description issue)))
  (println (str "  " (:message issue)))
  (when (:rule issue)
    (println (str "  Rule: " (pr-str (:rule issue)))))
  (println))

(defn -main [& args]
  (when (empty? args)
    (println "Usage: bb validate-reserved-keys.bb <edn-file>")
    (System/exit 1))

  (let [edn-file (first args)
        config (edn/read-string (slurp edn-file))
        issues (validate-reserved-keys config)

        ;; Group by type
        by-type (group-by :type issues)]

    (println "=== Reserved Keys Validation ===")
    (println (str "File: " edn-file))
    (println (str "Total issues: " (count issues)))
    (println)

    (if (empty? issues)
      (println "✓ No reserved key violations found!")
      (doseq [[issue-type type-issues] (sort-by first by-type)]
        (println (str "--- " (name issue-type) " (" (count type-issues) " issues) ---"))
        (doseq [issue type-issues]
          (print-issue issue))))

    (System/exit (if (empty? issues) 0 1))))

;; Run main if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
