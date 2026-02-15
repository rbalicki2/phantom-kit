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
;; - Left-side modifiers in Desktop profile (should use right_shift, right_control, etc.)
;; - ID state string mismatches (rule ID state should match actual conditions)
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
;; Rule Structure Checks (from must be map, to must be array)
;; ============================================================================

(defn check-from-is-map [rule-info]
  "Check that 'from' is always a map {:key ...}, never a bare keyword"
  (let [from (:from rule-info)]
    (when (keyword? from)
      {:type :bare-from-key
       :description (:description rule-info)
       :message (format "from is bare keyword %s. Must be {:key %s} or {:key %s :modi {...}}"
                       from from from)
       :rule (:rule rule-info)})))

(defn check-to-is-array [rule-info]
  "Check that 'to' is always an array [...], never a bare keyword"
  (let [to (:to rule-info)]
    (when (keyword? to)
      {:type :bare-to-key
       :description (:description rule-info)
       :message (format "to is bare keyword %s. Must be [%s] or [%s ...]"
                       to to to)
       :rule (:rule rule-info)})))

(defn check-condition-is-array [rule-info]
  "Check that condition (if present) is always an array, even for single conditions"
  (let [rule (:rule rule-info)]
    (when (>= (count rule) 3)
      (let [condition (nth rule 2)]
        ;; Condition should be nil, a vector of conditions, or a keyword (app)
        ;; If it's a single variable condition like ["dsk_layer" 0], it should be wrapped
        (when (and (vector? condition)
                   (= 2 (count condition))
                   (string? (first condition)))
          {:type :unwrapped-condition
           :description (:description rule-info)
           :message (format "Single condition %s should be wrapped: [[%s]]"
                           (pr-str condition) (pr-str condition))
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
  #{:dsk_layer :dsk_ins_sub_mode :dsk_return_to_layer})

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
  "Return expected values for dsk_ins_sub_mode, dsk_return_to_layer
   based on the layer number and invariants."
  {:dsk_ins_sub_mode -1  ;; Only 0+ in Ins mode, but we can't know submode from layer alone
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
;; Left-Side Modifier Detection (Desktop profile)
;; ============================================================================

(def left-side-modifiers
  "Left-side modifiers that shouldn't appear in Desktop profile inputs.
   Exception: left_option is allowed because Kinesis Fn layer sends it."
  #{:left_shift :left_command :left_control})

(def left-modifier-shorthand
  "Goku shorthand for left modifiers (except O = left_option which Fn uses)"
  {"S" :left_shift
   "C" :left_command
   "T" :left_control})

(defn extract-modifiers-from-shorthand [key-keyword]
  "Extract modifier letters from Goku shorthand like :!CSj -> [\"C\" \"S\"]"
  (let [key-str (name key-keyword)]
    (when (str/starts-with? key-str "!")
      (let [mod-part (subs key-str 1)]
        (take-while #(Character/isUpperCase %) mod-part)))))

(defn has-explicit-left-modifier? [modi-map]
  "Check if explicit :modi map contains left-side modifiers"
  (let [mandatory (get modi-map :mandatory [])
        optional (get modi-map :optional [])]
    (some left-side-modifiers (concat mandatory optional))))

(defn has-shorthand-left-modifier? [from-clause]
  "Check if from uses Goku shorthand with left modifiers"
  (let [key-val (if (map? from-clause) (:key from-clause) from-clause)]
    (when (keyword? key-val)
      (let [mod-chars (extract-modifiers-from-shorthand key-val)]
        (some left-modifier-shorthand mod-chars)))))

(defn is-desktop-block? [block]
  "Check if a block targets Desktop profile"
  (let [rules-vec (:rules block)]
    (when (vector? rules-vec)
      (some #(= % :Desktop) rules-vec))))

(defn check-left-modifier-in-desktop [config]
  "Check for left-side modifiers in Desktop profile rule inputs"
  (let [desktop-blocks (filter is-desktop-block? (:main config))]
    (for [block desktop-blocks
          :let [des (:des block)
                rules-vec (:rules block)
                actual-rules (when (vector? rules-vec)
                              (->> rules-vec
                                   (drop-while keyword?)
                                   (filter vector?)))]
          rule actual-rules
          :let [from (first rule)
                explicit-bad (when (map? from) (has-explicit-left-modifier? (:modi from)))
                shorthand-bad (has-shorthand-left-modifier? from)]
          :when (or explicit-bad shorthand-bad)]
      {:type :left-modifier-in-desktop
       :description des
       :message (format "Desktop rule uses left-side modifier in input. Use right_shift/right_control/right_command instead: %s"
                       (if explicit-bad "explicit modi" "shorthand"))
       :rule rule})))

;; ============================================================================
;; ID State String Validation
;; ============================================================================

(defn parse-id-state-string [rule-id]
  "Extract state values from rule ID. Returns map with :profile, :device, :layer, :submode, :return"
  (when rule-id
    (when-let [marker (second (re-find #"R\d+[a-z]?\s+\[([^\]]*)\]" rule-id))]
      (if (empty? marker)
        {:profile nil :device nil :layer nil :submode nil :return nil}
        (let [pairs (str/split marker #":")
              parsed (into {} (for [p pairs
                                    :let [[k v] (str/split p #"=")]
                                    :when (and k v)]
                                [k v]))]
          {:profile (get parsed "profile")
           :device (get parsed "device")
           :layer (when-let [l (get parsed "dsk_layer")] (parse-long l))
           :submode (when-let [s (get parsed "dsk_ins_sub_mode")] (parse-long s))
           :return (when-let [r (get parsed "dsk_return_to_layer")] (parse-long r))})))))

(defn extract-actual-conditions [rule block-conditions]
  "Extract actual condition values from rule and block"
  (let [rule-conds (nth rule 2 nil)
        rule-conds-list (when (sequential? rule-conds)
                          (if (and (vector? (first rule-conds)) (string? (ffirst rule-conds)))
                            rule-conds
                            [rule-conds]))
        all-conds (concat (filter vector? block-conditions) rule-conds-list)
        get-val (fn [var-name]
                  (some (fn [c]
                          (when (and (vector? c) (string? (first c)) (= (first c) var-name))
                            (second c)))
                        all-conds))]
    {:layer (get-val "dsk_layer")
     :submode (get-val "dsk_ins_sub_mode")
     :return (get-val "dsk_return_to_layer")}))

(defn extract-block-profile-device [block-conditions]
  "Extract profile and device from block conditions"
  (let [is-none (some #(= % :None) block-conditions)
        is-desktop (or (some #(= % :Desktop) block-conditions)
                       (some #(= % :!apple_internal) block-conditions))
        is-laptop (or (some #(= % :Laptop) block-conditions)
                      (some #(= % :apple_internal) block-conditions))]
    {:profile (cond is-none "None" :else "Default")
     :device (cond is-desktop "Desktop" is-laptop "Laptop" :else nil)}))

(defn check-id-matches-conditions [config]
  "Check that each rule's ID state string matches its actual conditions"
  (for [block (:main config)
        :let [des (:des block)
              rules-vec (:rules block)
              block-conditions (when (sequential? rules-vec)
                                (vec (take-while #(or (keyword? %)
                                                      (and (vector? %) (not (map? (first %)))))
                                                 rules-vec)))
              actual-rules (when block-conditions
                            (drop (count block-conditions) rules-vec))]
        rule (or actual-rules rules-vec)
        :when (vector? rule)
        :let [from (first rule)
              rule-id (when (map? from) (:id from))]
        :when rule-id
        :let [id-state (parse-id-state-string rule-id)
              actual-conds (extract-actual-conditions rule block-conditions)
              {:keys [profile device]} (extract-block-profile-device block-conditions)
              ;; Compare ID state to actual
              mismatches (cond-> []
                           (and (:profile id-state) (not= (:profile id-state) profile))
                           (conj (format "profile: ID=%s actual=%s" (:profile id-state) profile))

                           (and (:device id-state) (not= (:device id-state) device))
                           (conj (format "device: ID=%s actual=%s" (:device id-state) device))

                           (and (:layer id-state) (not= (:layer id-state) (:layer actual-conds)))
                           (conj (format "dsk_layer: ID=%s actual=%s" (:layer id-state) (:layer actual-conds)))

                           (and (:submode id-state) (not= (:submode id-state) (:submode actual-conds)))
                           (conj (format "dsk_ins_sub_mode: ID=%s actual=%s" (:submode id-state) (:submode actual-conds)))

                           (and (:return id-state) (not= (:return id-state) (:return actual-conds)))
                           (conj (format "dsk_return_to_layer: ID=%s actual=%s" (:return id-state) (:return actual-conds))))]
        :when (seq mismatches)]
    {:type :id-state-mismatch
     :description des
     :message (format "Rule ID state doesn't match conditions: %s" (str/join ", " mismatches))
     :rule rule-id}))

;; ============================================================================
;; Left-Hand Side Key Usage Detection
;; ============================================================================

(def lhs-physical-keys
  "Physical keys on the left-hand side of Kinesis Advantage 360.
   These should ONLY appear in blocking rules (one rule each) since this is a RHS-only setup.
   Note: backslash is on RHS on Kinesis 360 (not LHS like standard keyboards)."
  #{;; Top row
    :equal_sign :1 :2 :3 :4 :5
    ;; Second row
    :tab :q :w :e :r :t
    ;; Third row (home row)
    :caps_lock :a :s :d :f :g
    ;; Fourth row
    :left_shift :z :x :c :v :b
    ;; Bottom row / thumb cluster
    :grave_accent_and_tilde
    :left_command :left_option
    :delete_or_backspace :delete_forward
    :home :end
    ;; Escape is typically LHS
    :escape})

(defn extract-base-key [from-clause]
  "Extract the base key from a from clause, stripping ## and modifier prefixes"
  (let [key-val (cond
                  (keyword? from-clause) from-clause
                  (map? from-clause) (:key from-clause)
                  :else nil)]
    (when (keyword? key-val)
      (let [key-name (name key-val)
            ;; Strip ## prefix
            stripped (if (str/starts-with? key-name "##")
                       (subs key-name 2)
                       key-name)
            ;; Strip ! modifier prefix (e.g., !CSj -> j)
            base (if (str/starts-with? stripped "!")
                   (let [after-bang (subs stripped 1)]
                     ;; Find where modifiers end (uppercase) and key begins
                     (apply str (drop-while #(Character/isUpperCase %) after-bang)))
                   stripped)]
        (when (not (str/blank? base))
          (keyword base))))))

(defn is-desktop-rule? [block]
  "Check if a block targets Desktop profile (external keyboard)"
  (let [rules-vec (:rules block)]
    (when (vector? rules-vec)
      (or (some #(= % :Desktop) rules-vec)
          (some #(= % :!apple_internal) rules-vec)))))

(defn extract-desktop-rules [config]
  "Extract all rules from Desktop profile blocks with metadata"
  (for [block (:main config)
        :when (is-desktop-rule? block)
        :let [des (:des block)
              rules-vec (:rules block)
              actual-rules (when (vector? rules-vec)
                           (->> rules-vec
                                (drop-while keyword?)
                                (filter vector?)))]
        rule actual-rules]
    {:description des
     :rule rule
     :from (first rule)}))

(def allowed-lhs-blocks
  "Patterns matching blocks where LHS keys ARE allowed (blocking/disabling blocks)"
  [#"\[Global\] Block all unmapped keys"
   #"\[Desktop\] Disable backspace and delete keys"])

(defn is-allowed-lhs-block? [description]
  "Check if this is a block where LHS keys are allowed (blocking/disabling)"
  (and description
       (some #(re-find % description) allowed-lhs-blocks)))

(defn check-lhs-key-usage [config]
  "Check that LHS physical keys in Desktop profile only appear in allowed blocking blocks.
   This is a RHS-only setup for external keyboard - LHS keys should be blocked, not bound."
  (let [desktop-rules (extract-desktop-rules config)
        ;; Find LHS key usages outside allowed blocks
        violations (for [rule-info desktop-rules
                        :let [base-key (extract-base-key (:from rule-info))
                              description (:description rule-info)]
                        :when (and (contains? lhs-physical-keys base-key)
                                   (not (is-allowed-lhs-block? description)))]
                    {:key base-key :rule-info rule-info})
        ;; Group by key for reporting
        by-key (group-by :key violations)]
    ;; Report each LHS key that appears outside global block
    (for [[key-name key-violations] by-key]
      {:type :lhs-key-outside-global-block
       :description "Left-hand side keys"
       :message (format "LHS key '%s' appears in %d Desktop rules outside global block (should ONLY be in '[Global] Block all unmapped keys'). Blocks: %s"
                       (name key-name)
                       (count key-violations)
                       (str/join ", " (distinct (map #(str "'" (get-in % [:rule-info :description]) "'") key-violations))))
       :rule nil})))

;; ============================================================================
;; Main Validation
;; ============================================================================

(defn validate-extras [config layers-dir]
  "Run all extra validations"
  (let [all-rules (extract-all-rules config)

        ;; Check for bare from keys (must be maps)
        bare-from-issues
        (keep check-from-is-map all-rules)

        ;; Check for bare to keys (must be arrays)
        bare-to-issues
        (keep check-to-is-array all-rules)

        ;; Check for unwrapped single conditions
        unwrapped-cond-issues
        (keep check-condition-is-array all-rules)

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
        (extract-app-keywords-from-blocks config)

        ;; Check for left-side modifiers in Desktop profile
        left-mod-issues
        (check-left-modifier-in-desktop config)

        ;; Check ID state strings match actual conditions
        id-state-issues
        (check-id-matches-conditions config)

        ;; Check LHS keys only appear once each (blocking rules)
        lhs-key-issues
        (check-lhs-key-usage config)]

    (concat bare-from-issues
            bare-to-issues
            unwrapped-cond-issues
            var-first-issues
            nested-key-issues
            multi-shell-issues
            incomplete-issues
            mismatch-issues
            overlay-issues
            app-issues
            left-mod-issues
            id-state-issues
            lhs-key-issues)))

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
