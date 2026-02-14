#!/usr/bin/env bb
;; Additional Karabiner Rule Validations
;;
;; Checks for issues not covered by validate-rules.bb:
;; - Action arrays starting with variable sets (causes null in JSON)
;; - Nested key arrays like [[:key]] instead of [:key]
;; - Multiple shell commands in one rule (only last executes)
;; - Incomplete layer transitions (should set all 4 state variables)
;; - Missing layer overlay files (layers/*.txt)
;; - Undefined application references
;; - Layer code mismatches (dsk_layer vs /tmp/karabiner-layer)
;;
;; Usage:
;;   bb validate-extras.bb <edn-file>

(ns validate-extras
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]))

;; ============================================================================
;; Layer Code Mapping
;; ============================================================================

(def layer-codes
  "Map of dsk_layer values to expected /tmp/karabiner-layer codes"
  {0 "norm"
   1 "ins"
   2 "n"       ;; Nav
   3 "chrome"
   4 "vscode"
   5 "tmux"
   6 "comma"
   7 "l"       ;; L base
   8 "term"
   9 "i"       ;; Admin
   10 "inapp"
   11 "appsw"  ;; AppSwitcher
   12 "winsw"  ;; WindowSwitcher
   13 "label"  ;; Label/Mouse
   14 "lC"     ;; L-Cmd
   15 "lCS"    ;; L-Cmd-Shift
   16 "lT"     ;; L-Ctrl
   17 "lTS"    ;; L-Ctrl-Shift
   18 "lTC"    ;; L-CtrlCmd
   19 "lTCS"   ;; L-CtrlCmd-Shift
   20 "lTO"    ;; L-CtrlAlt
   21 "lTOS"   ;; L-CtrlAlt-Shift
   22 "lO"     ;; L-Alt
   23 "lOS"    ;; L-Alt-Shift
   24 "lOC"    ;; L-AltCmd
   25 "lOCS"   ;; L-AltCmd-Shift
   26 "lCTO"   ;; L-Hyper (Ctrl+Cmd+Alt)
   27 "lCTOS"  ;; L-Hyper-Shift
   28 "grid"})

(def layer-overlay-files
  "Map of dsk_layer values to expected overlay files"
  {0 "norm.txt"
   1 "ins.txt"
   2 "nav.txt"
   3 "chrome.txt"
   4 "vscode.txt"
   5 "tmux.txt"
   6 "comma.txt"
   7 "l.txt"
   8 "git.txt"   ;; Term layer shows as "Git" in SwiftBar
   9 "i.txt"})   ;; Only main layers need overlay files

;; ============================================================================
;; Rule Extraction
;; ============================================================================

(defn extract-all-rules [config]
  "Extract all rules from config with block metadata"
  (for [block (:main config)
        :let [des (:des block)
              rules-vec (:rules block)
              actual-rules (when (vector? rules-vec)
                            (->> rules-vec
                                 (drop-while keyword?)
                                 (filter vector?)))]
        rule actual-rules]
    {:description des
     :rule rule
     :from (first rule)
     :to (second rule)}))

;; ============================================================================
;; Action Array Syntax Checks
;; ============================================================================

(defn is-variable-set? [item]
  "Check if item looks like a variable set [\"name\" value]"
  (and (vector? item)
       (= 2 (count item))
       (string? (first item))))

(defn is-nested-key-array? [item]
  "Check if item is a nested array containing a single keyword like [[:key]]"
  (and (vector? item)
       (= 1 (count item))
       (keyword? (first item))))

(defn check-action-starts-with-variable [rule-info]
  "Check if action array starts with a variable set (causes null in generated JSON)"
  (let [action (:to rule-info)]
    (when (and (vector? action)
               (not-empty action)
               (is-variable-set? (first action)))
      {:type :action-starts-with-variable
       :description (:description rule-info)
       :message "Action array starts with variable set (causes null in JSON). Prepend :vk_none."
       :rule (:rule rule-info)})))

(defn check-nested-key-arrays [rule-info]
  "Check if action array contains nested key arrays like [[:key]]"
  (let [action (:to rule-info)]
    (when (vector? action)
      (let [nested-keys (filter is-nested-key-array? action)]
        (when (seq nested-keys)
          {:type :nested-key-array
           :description (:description rule-info)
           :message (format "Action contains nested key array(s): %s. Use :key instead of [:key]."
                           (pr-str (vec nested-keys)))
           :rule (:rule rule-info)})))))

;; ============================================================================
;; Multiple Shell Commands Detection
;; ============================================================================

(defn count-shell-commands [action]
  "Count {:shell ...} occurrences in an action"
  (if (vector? action)
    (count (filter #(and (map? %) (contains? % :shell)) action))
    0))

(defn check-multiple-shells [rule-info]
  "Check if rule has multiple shell commands (only last executes)"
  (let [action (:to rule-info)
        shell-count (count-shell-commands action)]
    (when (> shell-count 1)
      {:type :multiple-shell-commands
       :description (:description rule-info)
       :message (format "Rule has %d shell commands but only the last one will execute. Combine with &&." shell-count)
       :rule (:rule rule-info)})))

;; ============================================================================
;; Incomplete Layer Transition Detection
;; ============================================================================

(def state-variables
  #{:dsk_layer :dsk_in_modal_layer :dsk_ins_sub_mode :dsk_return_to_layer})

(defn extract-variable-sets [action]
  "Extract all variable sets from an action"
  (when (vector? action)
    (->> action
         (filter #(and (vector? %)
                       (= 2 (count %))
                       (string? (first %))))
         (map (fn [[var-name value]] [(keyword var-name) value]))
         (into {}))))

(defn is-layer-transition? [var-sets]
  "Check if this looks like a layer transition (sets dsk_layer)"
  (contains? var-sets :dsk_layer))

(defn expected-values-for-layer [layer]
  "Return expected values for dsk_in_modal_layer, dsk_ins_sub_mode, dsk_return_to_layer
   based on the layer number and invariants."
  {:dsk_in_modal_layer (if (>= layer 2) 1 0)
   :dsk_ins_sub_mode -1  ;; Only 0+ in Ins mode, but we can't know submode from layer alone
   :dsk_return_to_layer -1})  ;; Only 0/1 in Label mode (13)

(defn extract-source-layer [rule]
  "Extract the source layer from the rule's condition"
  (when (>= (count rule) 3)
    (let [condition (nth rule 2)]
      (when (and (vector? condition)
                 (= 2 (count condition))
                 (= "dsk_layer" (first condition)))
        (second condition)))))

(defn check-incomplete-transition [rule-info]
  "Check if a layer transition sets all 4 state variables.
   Allows omitting variables if transitioning between layers with same expected values."
  (let [action (:to rule-info)
        rule (:rule rule-info)
        var-sets (extract-variable-sets action)]
    (when (is-layer-transition? var-sets)
      (let [dest-layer (:dsk_layer var-sets)
            source-layer (extract-source-layer rule)
            missing (clojure.set/difference state-variables (set (keys var-sets)))]
        (when (seq missing)
          ;; Check if missing variables would have same value in source and dest
          (let [source-expected (when source-layer (expected-values-for-layer source-layer))
                dest-expected (expected-values-for-layer dest-layer)
                truly-missing (if (and source-expected dest-expected)
                               ;; Only flag if values would differ
                               (filter (fn [var]
                                        (not= (get source-expected var)
                                              (get dest-expected var)))
                                       missing)
                               missing)]
            (when (seq truly-missing)
              {:type :incomplete-layer-transition
               :description (:description rule-info)
               :message (format "Layer transition missing variables: %s" (str/join ", " (map name truly-missing)))
               :rule rule})))))))

;; ============================================================================
;; Layer Code Mismatch Detection
;; ============================================================================

(defn extract-layer-code-from-shell [shell-cmd]
  "Extract layer code from 'echo X > /tmp/karabiner-layer' pattern"
  (when shell-cmd
    (second (re-find #"echo\s+(\S+)\s*>\s*/tmp/karabiner-layer" shell-cmd))))

(defn get-shell-command [action]
  "Get the shell command from an action (last one if multiple)"
  (when (vector? action)
    (let [shell-maps (filter #(and (map? %) (contains? % :shell)) action)]
      (:shell (last shell-maps)))))

(defn check-layer-code-mismatch [rule-info]
  "Check if the layer code written to /tmp matches dsk_layer"
  (let [action (:to rule-info)
        var-sets (extract-variable-sets action)
        dsk-layer (:dsk_layer var-sets)
        shell-cmd (get-shell-command action)
        written-code (extract-layer-code-from-shell shell-cmd)
        expected-code (get layer-codes dsk-layer)]
    (when (and dsk-layer written-code expected-code
               (not= written-code expected-code))
      {:type :layer-code-mismatch
       :description (:description rule-info)
       :message (format "dsk_layer=%d expects code '%s' but writes '%s' to /tmp/karabiner-layer"
                       dsk-layer expected-code written-code)
       :rule (:rule rule-info)})))

;; ============================================================================
;; Missing Layer Overlay File Detection
;; ============================================================================

(defn check-missing-overlay-files [config layers-dir]
  "Check that overlay files exist for all main layers"
  (let [issues (for [[layer-num filename] layer-overlay-files
                     :let [filepath (str layers-dir "/" filename)]
                     :when (not (.exists (io/file filepath)))]
                 {:type :missing-overlay-file
                  :description "Layer overlay files"
                  :message (format "Missing overlay file for layer %d: %s" layer-num filepath)
                  :rule nil})]
    (vec issues)))

;; ============================================================================
;; Undefined Application Reference Detection
;; ============================================================================

(def known-placeholder-keywords
  "Keywords that are intentionally undefined (placeholders)"
  #{:None})

(defn extract-app-keywords-from-blocks [config]
  "Extract all app keywords used in rule blocks"
  (let [defined-apps (set (keys (:applications config)))]
    (for [block (:main config)
          :let [rules-vec (:rules block)
                keywords-in-block (when (vector? rules-vec)
                                   (filter keyword? rules-vec))]
          kw keywords-in-block
          :when (and (not (#{:!apple_internal :apple_internal :Desktop} kw))
                     (not (contains? defined-apps kw))
                     (not (contains? known-placeholder-keywords kw))
                     ;; Heuristic: app keywords are typically capitalized
                     (Character/isUpperCase (first (name kw))))]
      {:type :undefined-app-reference
       :description (:des block)
       :message (format "App keyword '%s' used but not defined in :applications" (name kw))
       :rule nil})))

;; ============================================================================
;; Main Validation
;; ============================================================================

(defn validate-extras [config layers-dir]
  "Run all extra validations"
  (let [all-rules (extract-all-rules config)

        ;; Check for action arrays starting with variable sets
        var-first-issues
        (keep check-action-starts-with-variable all-rules)

        ;; Check for nested key arrays
        nested-key-issues
        (keep check-nested-key-arrays all-rules)

        ;; Check for multiple shell commands
        multi-shell-issues
        (keep check-multiple-shells all-rules)

        ;; Check for incomplete layer transitions
        incomplete-issues
        (keep check-incomplete-transition all-rules)

        ;; Check for layer code mismatches
        mismatch-issues
        (keep check-layer-code-mismatch all-rules)

        ;; Check for missing overlay files
        overlay-issues
        (check-missing-overlay-files config layers-dir)

        ;; Check for undefined app references
        app-issues
        (extract-app-keywords-from-blocks config)]

    (concat var-first-issues
            nested-key-issues
            multi-shell-issues
            incomplete-issues
            mismatch-issues
            overlay-issues
            app-issues)))

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
    (println "Usage: bb validate-extras.bb <edn-file>")
    (System/exit 1))

  (let [edn-file (first args)
        ;; Derive layers-dir from edn-file location
        edn-dir (.getParent (io/file edn-file))
        layers-dir (str edn-dir "/layers")
        config (edn/read-string (slurp edn-file))
        issues (validate-extras config layers-dir)

        ;; Group by type
        by-type (group-by :type issues)]

    (println "=== Extra Karabiner Validations ===")
    (println (str "File: " edn-file))
    (println (str "Total issues: " (count issues)))
    (println)

    (if (empty? issues)
      (println "✓ No extra issues found!")
      (doseq [[issue-type type-issues] (sort-by first by-type)]
        (println (str "--- " (name issue-type) " (" (count type-issues) " issues) ---"))
        (doseq [issue type-issues]
          (print-issue issue))))

    (System/exit (if (empty? issues) 0 1))))

;; Run main if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
