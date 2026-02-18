#!/usr/bin/env bb
;; Karabiner Rule Matcher
;;
;; Given a state (layer variables, front app, device), key, and modifiers,
;; finds all matching rules in a karabiner.edn file.
;;
;; Usage:
;;   bb match-rules.bb <edn-file> <key> [options]
;;
;; Options:
;;   --state STRING      Complete state string (e.g., "profile=Default:device=Desktop:layer=1")
;;                       Partial states match all rules in subtree.
;;   --layer N           Set dsk_layer (default: 0)
;;   --modal N           Set dsk_in_modal_layer (default: 0)
;;   --submode N         Set dsk_ins_sub_mode (default: -1)
;;   --return-to N       Set dsk_return_to_layer (default: -1)
;;   --app BUNDLE_ID     Set frontmost app (e.g., com.google.Chrome)
;;   --device TYPE       Set device type: apple_internal or external (default: external)
;;   --mod MODIFIER      Add modifier (can repeat: --mod shift --mod right_control)
;;
;; Examples:
;;   bb match-rules.bb karabiner.edn j --layer 0 --mod right_control
;;   bb match-rules.bb karabiner.edn p --state "profile=Default:device=Desktop"
;;   bb match-rules.bb karabiner.edn p --state "profile=Default:device=Desktop:layer=1"

(ns match-rules
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]
            [cheshire.core :as json]))

;; Load state library from relative path
(def script-dir (-> (System/getProperty "babashka.file")
                    (java.io.File.)
                    (.getParentFile)
                    (.getAbsolutePath)))
(load-file (str script-dir "/../lib/state.bb"))

;; ============================================================================
;; EDN Parsing
;; ============================================================================

(defn parse-edn-file [path]
  "Parse a karabiner.edn file and return the config map"
  (edn/read-string (slurp path)))

(defn extract-rules [config]
  "Extract all rule blocks from :main section"
  (:main config))

(defn get-applications [config]
  "Get application bundle ID mappings"
  (:applications config))

(defn get-devices [config]
  "Get device definitions"
  (:devices config))

;; ============================================================================
;; Modifier Handling
;; ============================================================================

(def modifier-aliases
  "Map Goku shorthand to full modifier names"
  {:C :left_command
   :Q :right_command
   :T :left_control
   :O :left_option
   :E :right_option
   :S :left_shift
   :R :right_shift
   :W :fn})

(def all-modifiers
  #{:left_command :right_command :left_control :right_control
    :left_option :right_option :left_shift :right_shift
    :fn :caps_lock})

;; Map generic modifiers to their specific variants
(def generic-modifier-expansions
  {:shift #{:left_shift :right_shift}
   :command #{:left_command :right_command}
   :control #{:left_control :right_control}
   :option #{:left_option :right_option}})

(defn normalize-modifier [mod]
  "Convert modifier shorthand to full name"
  (get modifier-aliases mod mod))

(defn expand-generic-modifier [mod]
  "Expand a generic modifier to its specific variants.
   E.g., :shift -> #{:left_shift :right_shift}
   Returns a set containing the modifier(s)."
  (get generic-modifier-expansions mod #{mod}))

(defn parse-shorthand-key [k]
  "Parse Goku shorthand like :!Cj, :!TOSf19, or :##y
   Returns {:key <key> :mandatory #{...} :optional #{...}}"
  (let [s (name k)]
    (cond
      ;; ## prefix = any modifiers optional, no mandatory
      (str/starts-with? s "##")
      (let [key-str (subs s 2)]
        {:key (keyword key-str)
         :mandatory #{}
         :optional :any})

      ;; ! prefix = has mandatory modifiers (and optional # for any)
      (str/starts-with? s "!")
      (let [rest-str (subs s 1)
            ;; Find where modifiers end and key begins
            ;; Modifiers are uppercase letters or #
            mod-chars (take-while #(or (Character/isUpperCase %) (= % \#)) rest-str)
            key-str (subs rest-str (count mod-chars))
            key-kw (keyword key-str)
            mandatory (set (map (comp normalize-modifier keyword str)
                               (remove #(= % \#) mod-chars)))
            optional (if (some #(= % \#) mod-chars) :any #{})]
        {:key key-kw
         :mandatory mandatory
         :optional optional})

      ;; No prefix - plain key
      :else
      {:key k :mandatory #{} :optional #{}})))

(defn parse-from-clause [from-clause]
  "Parse a from clause into normalized form.
   Input can be:
   - keyword like :j or :!Cj
   - map like {:key :j :modi {:mandatory [:shift]}}
   - map like {:pkey :button1 :modi [...]}
   Returns {:key <key> :mandatory #{...} :optional #{...}}"
  (cond
    ;; Simple keyword (possibly with shorthand modifiers)
    (keyword? from-clause)
    (parse-shorthand-key from-clause)

    ;; Map form
    (map? from-clause)
    (let [key-val (or (:key from-clause) (:pkey from-clause))
          ;; Parse shorthand in key-val if present (e.g., :##page_up)
          parsed-key (if (keyword? key-val)
                       (parse-shorthand-key key-val)
                       {:key key-val :mandatory #{} :optional #{}})
          modi (:modi from-clause)
          ;; Explicit modi in map takes precedence over shorthand
          mandatory (cond
                      (map? modi) (set (map normalize-modifier (:mandatory modi)))
                      (vector? modi) (set (map normalize-modifier modi))
                      :else (:mandatory parsed-key))
          optional (cond
                     (map? modi) (let [opt (:optional modi)]
                                   (if (= opt [:any])
                                     :any
                                     (set (map normalize-modifier opt))))
                     ;; If no explicit :modi but shorthand had ##, use that
                     (nil? modi) (:optional parsed-key)
                     :else #{})]
      {:key (:key parsed-key)
       :mandatory mandatory
       :optional optional})

    ;; Unknown
    :else nil))

;; ============================================================================
;; Condition Matching
;; ============================================================================

(defn match-variable-condition [condition state]
  "Check if a variable condition like [\"dsk_layer\" 0] matches state"
  (when (and (vector? condition)
             (= 2 (count condition))
             (string? (first condition)))
    (let [[var-name expected-val] condition
          var-key (keyword var-name)
          actual-val (get state var-key)]
      (= actual-val expected-val))))

(defn match-device-condition [rule-block input-device devices]
  "Check if device condition matches.
   rule-block may start with :!apple_internal (NOT apple_internal)
   or :apple_internal (IS apple_internal)"
  (let [rules-vec (:rules rule-block)
        first-item (when (vector? rules-vec) (first rules-vec))]
    (cond
      ;; :!apple_internal means NOT apple_internal (external keyboard)
      (= first-item :!apple_internal)
      (not= input-device :apple_internal)

      ;; :apple_internal means IS apple_internal
      (= first-item :apple_internal)
      (= input-device :apple_internal)

      ;; No device condition - matches all
      :else true)))

(defn match-app-condition [rule-block input-app applications]
  "Check if app condition matches.
   rule-block :rules may contain app keywords like :Chrome"
  (let [rules-vec (:rules rule-block)
        ;; Find app keywords in the rules vector (they appear after :!apple_internal if present)
        app-keywords (->> rules-vec
                          (filter keyword?)
                          (remove #{:!apple_internal :apple_internal})
                          (filter #(contains? applications %)))]
    (if (empty? app-keywords)
      ;; No app condition - matches all
      true
      ;; Check if input-app matches any of the app conditions
      (some (fn [app-kw]
              (let [bundle-ids (get applications app-kw)]
                (some #(= input-app %) bundle-ids)))
            app-keywords))))

(defn single-variable-condition? [x]
  "Check if x is a single variable condition like [\"dsk_layer\" 0]"
  (and (vector? x)
       (= 2 (count x))
       (string? (first x))
       (number? (second x))))

(defn match-rule-condition [rule state]
  "Check if a rule's condition matches the state.
   Rule format: [from to condition] or [from to] or [from to condition options]"
  (cond
    ;; Rule has 2 elements - no condition
    (<= (count rule) 2)
    true

    ;; Rule has 3+ elements - third is condition
    :else
    (let [condition (nth rule 2)]
      (cond
        ;; nil condition means always match
        (nil? condition)
        true

        ;; Single variable condition like ["dsk_layer" 0] - ERROR, should be wrapped
        (single-variable-condition? condition)
        (throw (ex-info (str "Invalid condition format - should be array of arrays: " (pr-str condition))
                        {:condition condition :rule rule}))

        ;; Array of variable conditions like [["dsk_layer" 1] ["dsk_ins_sub_mode" 1]]
        ;; ALL must match (AND logic)
        (and (vector? condition)
             (seq condition)
             (every? single-variable-condition? condition))
        (every? #(match-variable-condition % state) condition)

        ;; App keyword condition like :Chrome
        (keyword? condition)
        true  ;; App conditions are handled at block level

        ;; Unknown condition type - be conservative, don't match
        :else false))))

;; ============================================================================
;; Key Matching
;; ============================================================================

(defn mandatory-modifier-satisfied?
  "Check if a mandatory modifier is satisfied by the input.
   Handles generic modifiers like :shift matching :left_shift or :right_shift."
  [required-mod input-set]
  (let [expanded (expand-generic-modifier required-mod)]
    ;; At least one of the expanded variants must be present
    (some #(contains? input-set %) expanded)))

(defn get-satisfied-mods
  "Given a mandatory modifier that was satisfied, return which input mods satisfied it."
  [required-mod input-set]
  (let [expanded (expand-generic-modifier required-mod)]
    (set/intersection expanded input-set)))

(defn modifiers-match? [from-parsed input-mods]
  "Check if input modifiers satisfy the from clause requirements.
   - All mandatory modifiers must be present (generic modifiers like :shift match :left_shift/:right_shift)
   - If optional is :any, any additional modifiers are allowed
   - If optional is a set, only those additional modifiers are allowed"
  (let [{:keys [mandatory optional]} from-parsed
        mandatory-set (set mandatory)
        input-set (set input-mods)]
    (and
      ;; All mandatory modifiers must be present (with generic expansion)
      (every? #(mandatory-modifier-satisfied? % input-set) mandatory-set)
      ;; Check optional modifiers
      (let [;; Calculate which input mods were used to satisfy mandatory requirements
            used-mods (reduce (fn [acc req-mod]
                               (set/union acc (get-satisfied-mods req-mod input-set)))
                             #{} mandatory-set)
            extra-mods (set/difference input-set used-mods)]
        (cond
          ;; ## means any modifiers allowed
          (= optional :any) true
          ;; Optional set specified - extra mods must be in optional set (with generic expansion)
          (set? optional) (or (empty? extra-mods)
                              (let [expanded-optional (reduce (fn [acc opt]
                                                               (set/union acc (expand-generic-modifier opt)))
                                                             #{} optional)]
                                (set/subset? extra-mods expanded-optional)))
          ;; No optional specified - no extra mods allowed
          :else (empty? extra-mods))))))

(defn key-matches? [from-parsed input-key input-mods]
  "Check if a from clause matches the input key and modifiers"
  (and
    (= (:key from-parsed) input-key)
    (modifiers-match? from-parsed input-mods)))

(defn rule-matches? [rule input-key input-mods state]
  "Check if a single rule matches the input"
  (let [from-clause (first rule)
        from-parsed (parse-from-clause from-clause)]
    (and
      from-parsed
      (key-matches? from-parsed input-key input-mods)
      (match-rule-condition rule state))))

;; ============================================================================
;; Rule Extraction from Blocks
;; ============================================================================

(defn extract-rules-from-block [rule-block]
  "Extract individual rules from a rule block.
   Block format: {:des \"description\" :rules [:!apple_internal rule1 rule2 ...]}
   Returns list of {:des ... :rule ...}"
  (let [des (:des rule-block)
        rules-vec (:rules rule-block)
        ;; Skip device/app conditions at the start
        actual-rules (if (vector? rules-vec)
                       (->> rules-vec
                            (drop-while keyword?)
                            (filter vector?))
                       [])]
    (map (fn [r] {:des des :rule r}) actual-rules)))

;; ============================================================================
;; Output Parsing
;; ============================================================================

(defn extract-rule-options [rule]
  "Extract options map (held, afterup, alone, parameters) from rule.
   Rule format: [from to condition options] where options is a map"
  (when (>= (count rule) 4)
    (let [fourth (nth rule 3)]
      (when (map? fourth)
        fourth))))

(defn parse-output-key [item]
  "Parse a key output like :j, :!Cj, :vk_none into structured form"
  (when (keyword? item)
    (let [parsed (parse-shorthand-key item)]
      (merge {:key (name (:key parsed))}
             (when (seq (:mandatory parsed))
               (into {} (map (fn [m] [(name m) true]) (:mandatory parsed))))))))

(defn parse-output-array [arr]
  "Parse an output array into {resulting_state, actions}.
   Output array contains: key outputs, variable sets, shell commands, etc."
  (when (vector? arr)
    (let [resulting-state (atom {})
          actions (atom [])]
      (doseq [item arr]
        (cond
          ;; Variable set like ["dsk_layer" 7]
          (and (vector? item) (= 2 (count item)) (string? (first item)))
          (let [[var-name val] item]
            (swap! resulting-state assoc (keyword var-name) val))

          ;; Shell command like {:shell "..."}
          (and (map? item) (:shell item))
          (swap! actions conj {:shell (:shell item)})

          ;; Key output (keyword)
          (keyword? item)
          (when-let [parsed (parse-output-key item)]
            (swap! actions conj parsed))

          ;; Other maps (could be other options)
          (map? item)
          (swap! actions conj item)))

      {:resulting_state @resulting-state
       :actions @actions})))

(defn parse-rule-output [rule]
  "Parse a complete rule into structured output format.
   Returns {:to {...} :held {...} :afterup {...} :alone {...}}
   Each contains {:resulting_state {...} :actions [...]}"
  (let [to-array (second rule)
        options (extract-rule-options rule)
        parse-output (fn [arr]
                       (when arr
                         (parse-output-array (if (vector? arr) arr [arr]))))]
    {:to (parse-output to-array)
     :held (parse-output (:held options))
     :afterup (parse-output (:afterup options))
     :alone (parse-output (:alone options))}))

;; ============================================================================
;; Main Matching Logic
;; ============================================================================

(defn find-matching-rules [config input-key input-mods state input-app input-device]
  "Find all rules that match the given input.
   Returns {:first-match ... :all-matches [...]}"
  (let [main-blocks (extract-rules config)
        applications (get-applications config)
        devices (get-devices config)

        all-matches
        (for [block main-blocks
              :when (and (match-device-condition block input-device devices)
                         (match-app-condition block input-app applications))
              {:keys [des rule]} (extract-rules-from-block block)
              :when (rule-matches? rule input-key input-mods state)]
          {:description des
           :rule rule
           :from (first rule)
           :to (second rule)
           :condition (when (> (count rule) 2) (nth rule 2))
           :options (extract-rule-options rule)
           :parsed-output (parse-rule-output rule)})]

    {:first-match (first all-matches)
     :all-matches (vec all-matches)
     :match-count (count all-matches)}))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn parse-args [args]
  "Parse command line arguments"
  (loop [args args
         result {:mods []
                 :state {:dsk_layer 0
                         :dsk_in_modal_layer 0
                         :dsk_ins_sub_mode -1
                         :dsk_return_to_layer -1}
                 :app nil
                 :device :external
                 :state-string nil
                 :json false}]
    (if (empty? args)
      result
      (let [[arg & rest-args] args]
        (cond
          ;; EDN file (first positional arg)
          (and (not (:edn-file result))
               (not (str/starts-with? arg "--")))
          (recur rest-args (assoc result :edn-file arg))

          ;; Key (second positional arg)
          (and (:edn-file result)
               (not (:key result))
               (not (str/starts-with? arg "--")))
          (recur rest-args (assoc result :key (keyword arg)))

          ;; --json: output JSON instead of human-readable
          (= arg "--json")
          (recur rest-args (assoc result :json true))

          ;; --state: complete state string (takes precedence over individual flags)
          (= arg "--state")
          (recur (rest rest-args)
                 (assoc result :state-string (first rest-args)))

          ;; Options (legacy, overridden by --state)
          (= arg "--layer")
          (recur (rest rest-args)
                 (assoc-in result [:state :dsk_layer] (parse-long (first rest-args))))

          (= arg "--modal")
          (recur (rest rest-args)
                 (assoc-in result [:state :dsk_in_modal_layer] (parse-long (first rest-args))))

          (= arg "--submode")
          (recur (rest rest-args)
                 (assoc-in result [:state :dsk_ins_sub_mode] (parse-long (first rest-args))))

          (= arg "--return-to")
          (recur (rest rest-args)
                 (assoc-in result [:state :dsk_return_to_layer] (parse-long (first rest-args))))

          (= arg "--app")
          (recur (rest rest-args)
                 (assoc result :app (first rest-args)))

          (= arg "--device")
          (recur (rest rest-args)
                 (assoc result :device (keyword (first rest-args))))

          (= arg "--mod")
          (recur (rest rest-args)
                 (update result :mods conj (keyword (first rest-args))))

          :else
          (recur rest-args result))))))

(defn print-match [match]
  "Pretty print a single match"
  (println "  Description:" (:description match))
  (println "  From:" (pr-str (:from match)))
  (println "  To:" (pr-str (:to match)))
  (when (:condition match)
    (println "  Condition:" (pr-str (:condition match))))
  (println))

(defn format-json-output [match input-state input-key input-mods input-app input-device]
  "Format a match result as JSON for test generation.
   Returns a map ready for JSON serialization."
  (let [parsed (:parsed-output match)
        ;; Build the key object with modifiers as boolean flags
        key-obj (merge {:key (name input-key)}
                       (into {} (map (fn [m] [(name m) true]) input-mods)))]
    {:initial_state {:application (or input-app "other")
                     :device (name input-device)
                     :dsk_ins_sub_mode (:dsk_ins_sub_mode input-state)
                     :dsk_layer (:dsk_layer input-state)
                     :dsk_return_to_layer (:dsk_return_to_layer input-state)
                     :profile "Default"}
     :key key-obj
     :rule_id (-> match :from :id)
     :to (:to parsed)
     :held (:held parsed)
     :afterup (:afterup parsed)
     :alone (:alone parsed)}))

(defn complete-state->match-state
  "Convert a complete state from state library to match-rules internal format"
  [{:keys [layer submode return-to]}]
  {:dsk_layer (or layer 0)
   :dsk_in_modal_layer 0
   :dsk_ins_sub_mode (or submode -1)
   :dsk_return_to_layer (or return-to -1)})

(defn complete-state->device
  "Extract device from complete state"
  [{:keys [device]}]
  (case device
    :desktop :external
    :laptop :apple_internal
    :external))

(defn format-state-for-display
  "Format a complete state for display"
  [{:keys [profile device layer submode return-to app]}]
  (str (when profile (str "profile=" profile))
       (when device (str ":device=" (name device)))
       (when layer (str ":layer=" layer))
       (when (and submode (not= submode -1)) (str ":submode=" submode))
       (when (and return-to (not= return-to -1)) (str ":return-to=" return-to))
       (when app (str ":app=" (name app)))))

(defn -main [& args]
  (let [parsed (parse-args args)]
    (when (or (not (:edn-file parsed)) (not (:key parsed)))
      (println "Usage: bb match-rules.bb <edn-file> <key> [options]")
      (println "Options:")
      (println "  --state STRING  Complete state string (e.g., \"profile=Default:device=Desktop:layer=1\")")
      (println "  --layer N       Set dsk_layer (default: 0)")
      (println "  --modal N       Set dsk_in_modal_layer (default: 0)")
      (println "  --submode N     Set dsk_ins_sub_mode (default: -1)")
      (println "  --return-to N   Set dsk_return_to_layer (default: -1)")
      (println "  --app BUNDLE    Set frontmost app (e.g., com.google.Chrome)")
      (println "  --device TYPE   Set device: apple_internal or external (default: external)")
      (println "  --mod MODIFIER  Add modifier (repeatable)")
      (println "  --json          Output JSON for programmatic use")
      (System/exit 1))

    (let [config (parse-edn-file (:edn-file parsed))
          state-str (:state-string parsed)]

      (if state-str
        ;; New: --state provided, parse and possibly expand
        (let [parsed-state (state/parse-state-string state-str)
              _ (when-not parsed-state
                  (println "Error: Invalid state string:" state-str)
                  (System/exit 1))
              ;; Expand partial state into all matching complete states
              complete-states (state/expand-partial-state parsed-state)
              _ (when (empty? complete-states)
                  (println "Error: No valid states match:" state-str)
                  (System/exit 1))]

          (println "=== Input ===")
          (println "Key:" (:key parsed))
          (println "Modifiers:" (or (seq (:mods parsed)) "none"))
          (println "State filter:" state-str)
          (println "Matching" (count complete-states) "complete state(s)")
          (println)

          ;; Run matches for each complete state
          (let [all-results
                (for [cs complete-states
                      :let [match-state (complete-state->match-state cs)
                            device (complete-state->device cs)
                            result (find-matching-rules config
                                                        (:key parsed)
                                                        (set (:mods parsed))
                                                        match-state
                                                        (:app parsed)
                                                        device)]
                      :when (pos? (:match-count result))]
                  {:state cs :result result})

                ;; Dedupe rules by ID (same rule may appear in multiple states)
                unique-rules (->> all-results
                                  (mapcat #(-> % :result :all-matches))
                                  (group-by #(-> % :from :id))
                                  vals
                                  (map first))]

            (println "=== Results ===")
            (println "Unique rules matching:" (count unique-rules))
            (println)

            (doseq [[idx match] (map-indexed vector unique-rules)]
              (println (str "#" (inc idx)))
              (print-match match))))

        ;; Legacy: individual flags
        (let [result (find-matching-rules config
                                          (:key parsed)
                                          (set (:mods parsed))
                                          (:state parsed)
                                          (:app parsed)
                                          (:device parsed))]
          (if (:json parsed)
            ;; JSON output mode
            (let [first-match (:first-match result)]
              (if first-match
                (println (json/generate-string
                          (format-json-output first-match
                                              (:state parsed)
                                              (:key parsed)
                                              (set (:mods parsed))
                                              (:app parsed)
                                              (:device parsed))
                          {:pretty true}))
                (println (json/generate-string {:error "no_match"} {:pretty true}))))

            ;; Human-readable output mode
            (do
              (println "=== Input ===")
              (println "Key:" (:key parsed))
              (println "Modifiers:" (or (seq (:mods parsed)) "none"))
              (println "State:" (:state parsed))
              (println "App:" (or (:app parsed) "any"))
              (println "Device:" (:device parsed))
              (println)

              (println "=== Results ===")
              (println "Total matches:" (:match-count result))
              (println)

              (when-let [first-match (:first-match result)]
                (println "--- First Match (ACTIVE) ---")
                (print-match first-match))

              (when (> (:match-count result) 1)
                (println "--- All Matches (including shadowed) ---")
                (doseq [[idx match] (map-indexed vector (:all-matches result))]
                  (println (str "#" (inc idx) (if (= idx 0) " (ACTIVE)" " (SHADOWED)")))
                  (print-match match))))))))))

;; ============================================================================
;; Library API (for use from tests)
;; ============================================================================

(defn match-rules
  "Library function for programmatic use.
   Returns {:first-match ... :all-matches [...] :match-count N}"
  [{:keys [edn-file key mods state app device]
    :or {mods #{}
         state {:dsk_layer 0
                :dsk_in_modal_layer 0
                :dsk_ins_sub_mode -1
                :dsk_return_to_layer -1}
         app nil
         device :external}}]
  (let [config (parse-edn-file edn-file)]
    (find-matching-rules config key mods state app device)))

;; Run main if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
