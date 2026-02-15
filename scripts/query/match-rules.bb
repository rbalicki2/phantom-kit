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
;;   --layer N           Set dsk_layer (default: 0)
;;   --modal N           Set dsk_in_modal_layer (default: 0)
;;   --submode N         Set dsk_ins_sub_mode (default: -1)
;;   --return-to N       Set dsk_return_to_layer (default: -1)
;;   --app BUNDLE_ID     Set frontmost app (e.g., com.google.Chrome)
;;   --device TYPE       Set device type: apple_internal or external (default: external)
;;   --mod MODIFIER      Add modifier (can repeat: --mod shift --mod right_control)
;;
;; Example:
;;   bb match-rules.bb karabiner.edn j --layer 0 --mod right_control

(ns match-rules
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]))

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

(defn normalize-modifier [mod]
  "Convert modifier shorthand to full name"
  (get modifier-aliases mod mod))

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

(defn modifiers-match? [from-parsed input-mods]
  "Check if input modifiers satisfy the from clause requirements.
   - All mandatory modifiers must be present
   - If optional is :any, any additional modifiers are allowed
   - If optional is a set, only those additional modifiers are allowed"
  (let [{:keys [mandatory optional]} from-parsed
        mandatory-set (set mandatory)
        input-set (set input-mods)]
    (and
      ;; All mandatory modifiers must be present
      (set/subset? mandatory-set input-set)
      ;; Check optional modifiers
      (let [extra-mods (set/difference input-set mandatory-set)]
        (cond
          ;; ## means any modifiers allowed
          (= optional :any) true
          ;; Optional set specified - extra mods must be in optional set
          (set? optional) (or (empty? extra-mods)
                              (set/subset? extra-mods optional))
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
           :condition (when (> (count rule) 2) (nth rule 2))})]

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
                 :device :external}]
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

          ;; Options
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

(defn -main [& args]
  (let [parsed (parse-args args)]
    (when (or (not (:edn-file parsed)) (not (:key parsed)))
      (println "Usage: bb match-rules.bb <edn-file> <key> [options]")
      (println "Options:")
      (println "  --layer N       Set dsk_layer (default: 0)")
      (println "  --modal N       Set dsk_in_modal_layer (default: 0)")
      (println "  --submode N     Set dsk_ins_sub_mode (default: -1)")
      (println "  --return-to N   Set dsk_return_to_layer (default: -1)")
      (println "  --app BUNDLE    Set frontmost app")
      (println "  --device TYPE   Set device: apple_internal or external (default: external)")
      (println "  --mod MODIFIER  Add modifier (repeatable)")
      (System/exit 1))

    (let [config (parse-edn-file (:edn-file parsed))
          result (find-matching-rules config
                                      (:key parsed)
                                      (set (:mods parsed))
                                      (:state parsed)
                                      (:app parsed)
                                      (:device parsed))]
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
          (print-match match))))))

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
