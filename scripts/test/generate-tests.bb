#!/usr/bin/env bb
;; BFS Test Generator
;;
;; Generates snapshot tests by exploring all reachable states from root.
;; Uses BFS to discover state transitions and generates a test file for
;; each unique (initial_state, key) combination that produces output.
;;
;; Usage:
;;   bb scripts/test/generate-tests.bb
;;
;; Output:
;;   Creates JSON test files in tests/unit/

(ns generate-tests
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]
            [cheshire.core :as json]))

;; Load libraries from relative path
(def script-dir (-> (System/getProperty "babashka.file")
                    (java.io.File.)
                    (.getParentFile)
                    (.getAbsolutePath)))
(def project-root (-> script-dir (java.io.File.) (.getParentFile) (.getParentFile) (.getAbsolutePath)))

(load-file (str script-dir "/../lib/state.bb"))
(load-file (str script-dir "/../query/match-rules.bb"))

;; ============================================================================
;; Configuration
;; ============================================================================

(def edn-file (str project-root "/src/karabiner.edn"))
(def inputs-file (str project-root "/tests/inputs.json"))
(def output-dir (str project-root "/tests/unit"))

;; Layers that have app-specific rules and need app variations tested
;; Other layers use "other" app only to reduce test count ~4x
;; Layer 0 (Normal) has app-specific "h" key, Layer 10 (InApp) has app-specific rules
(def layers-with-app-rules #{0 10})

;; Layer names for human-readable output
(def layer-names
  {0 "Normal" 1 "Ins" 2 "Nav" 3 "Chrome" 4 "VSCode" 5 "TMUX"
   6 "Comma" 7 "AltIns" 8 "Term" 9 "Admin" 10 "InApp"
   11 "AppSwitcher" 12 "WinSwitcher" 13 "Label" 28 "Grid"})

(def fn-key-comments
  "Map of F-keys to human-readable Fn+key comments"
  {"f7" "Fn+6"
   "f8" "Fn+7"
   "f9" "Fn+8"
   "f10" "Fn+9"
   "f11" "Fn+0"
   "f12" "Fn+hyphen"
   "f15" "Fn+y"
   "f16" "Fn+u"
   "f17" "Fn+i"
   "f18" "Fn+o"
   "f19" "Fn+p"
   "f20" "Fn+backslash"
   "f21" "Fn+HK4"
   "f22" "Fn+h"
   "f23" "Fn+j"
   "f24" "Fn+k"})

;; ============================================================================
;; Input Loading
;; ============================================================================

(defn load-inputs []
  "Load test inputs from JSON file"
  (json/parse-string (slurp inputs-file) true))

;; Load config once at startup
(def config (atom nil))

(defn get-config []
  (when-not @config
    (reset! config (match-rules/parse-edn-file edn-file)))
  @config)

;; ============================================================================
;; State Representation
;; ============================================================================

(defn make-initial-state
  "Create an initial state map for testing"
  [layer submode return-to app]
  {:dsk_layer layer
   :dsk_ins_sub_mode submode
   :dsk_return_to_layer return-to
   :application app})

(defn state-key
  "Create a unique key for a state (for visited tracking).
   Includes app for layers that have app-specific rules."
  [{:keys [dsk_layer dsk_ins_sub_mode dsk_return_to_layer application]}]
  (if (contains? layers-with-app-rules dsk_layer)
    [dsk_layer dsk_ins_sub_mode dsk_return_to_layer application]
    [dsk_layer dsk_ins_sub_mode dsk_return_to_layer]))

(defn format-initial-state
  "Format initial state for JSON output (canonical key order)"
  [{:keys [dsk_layer dsk_ins_sub_mode dsk_return_to_layer application]}]
  {:application (or application "other")
   :device "Desktop"
   :dsk_ins_sub_mode dsk_ins_sub_mode
   :dsk_layer dsk_layer
   :dsk_return_to_layer dsk_return_to_layer
   :profile "Default"})

;; ============================================================================
;; Key Object Helpers
;; ============================================================================

(defn make-key-obj
  "Create a key object from key name and modifier map"
  [key-name modifiers]
  (merge {:key key-name} modifiers))

(defn format-key-for-filename
  "Format key object for filename (key + modifiers alphabetically)"
  [{:keys [key] :as key-obj}]
  (let [mods (dissoc key-obj :key)
        mod-strs (sort (map name (keys mods)))]
    (str/join "_" (concat [key] mod-strs))))

;; ============================================================================
;; Test File Naming
;; ============================================================================

(defn test-filename
  "Generate test filename from initial state and key"
  [initial-state key-obj]
  (let [{:keys [profile device application dsk_layer dsk_ins_sub_mode dsk_return_to_layer]}
        (format-initial-state initial-state)
        key-part (format-key-for-filename key-obj)]
    (format "profile=%s_device=%s_app=%s_layer=%d_sub=%d_ret=%d__key=%s.json"
            profile device application dsk_layer dsk_ins_sub_mode dsk_return_to_layer key-part)))

;; ============================================================================
;; Rule Matching (in-process)
;; ============================================================================

(defn do-match-rules
  "Match rules using in-process library call"
  [state key-name mods app-bundle-id]
  (let [{:keys [dsk_layer dsk_ins_sub_mode dsk_return_to_layer]} state
        internal-state {:dsk_layer dsk_layer
                        :dsk_in_modal_layer 0
                        :dsk_ins_sub_mode dsk_ins_sub_mode
                        :dsk_return_to_layer dsk_return_to_layer}
        mod-set (set (map keyword (keys mods)))
        result (match-rules/find-matching-rules (get-config)
                                                 (keyword key-name)
                                                 mod-set
                                                 internal-state
                                                 app-bundle-id
                                                 :external)]
    (:first-match result)))

;; ============================================================================
;; State Extraction from Output
;; ============================================================================

(defn extract-resulting-state
  "Extract resulting state variables from parsed output"
  [output-section]
  (when-let [rs (:resulting_state output-section)]
    (let [layer (get rs :dsk_layer)
          submode (get rs :dsk_ins_sub_mode)
          return-to (get rs :dsk_return_to_layer)]
      (when (or layer submode return-to)
        {:dsk_layer layer
         :dsk_ins_sub_mode submode
         :dsk_return_to_layer return-to}))))

(defn merge-resulting-state
  "Merge resulting state with current state (only set fields override)"
  [current resulting]
  (merge current
         (when (:dsk_layer resulting) {:dsk_layer (:dsk_layer resulting)})
         (when (:dsk_ins_sub_mode resulting) {:dsk_ins_sub_mode (:dsk_ins_sub_mode resulting)})
         (when (:dsk_return_to_layer resulting) {:dsk_return_to_layer (:dsk_return_to_layer resulting)})))

(defn collect-new-states
  "Collect all new states from a match result"
  [current-state match-result]
  (let [sections [:to :held :afterup :alone]
        states (for [section sections
                     :let [output (get match-result section)
                           rs (extract-resulting-state output)]
                     :when rs]
                 (merge-resulting-state current-state rs))]
    (distinct states)))

;; ============================================================================
;; Comment Generation
;; ============================================================================

(defn generate-comment
  "Generate human-readable comment for a key"
  [key-obj]
  (let [key-name (:key key-obj)
        mods (dissoc key-obj :key)
        fn-comment (get fn-key-comments key-name)
        mod-strs (sort (map (fn [[k _]]
                              (case k
                                :left_shift "LShift"
                                :right_shift "RShift"
                                :left_command "LCmd"
                                :right_command "RCmd"
                                :left_option "LOpt"
                                :right_option "ROpt"
                                :left_control "LCtrl"
                                :right_control "RCtrl"
                                (name k)))
                            mods))]
    (cond
      (and fn-comment (empty? mod-strs)) fn-comment
      (and fn-comment (seq mod-strs)) (str (str/join "+" mod-strs) " + " fn-comment)
      (seq mod-strs) (str (str/join "+" mod-strs) " + " key-name)
      :else nil)))

;; ============================================================================
;; Test Generation
;; ============================================================================

(defn has-meaningful-output?
  "Check if a match result has meaningful output (not just no_match error)"
  [result]
  (and result (not (:error result))))

(defn format-test-output
  "Format a test case for JSON output.
   matched_rule is null if no rule matched, otherwise contains:
   - id: the rule ID
   - to, held, afterup, alone: the parsed outputs (nullable)
   - comment: human-readable key description"
  [initial-state key-obj match-result]
  (let [formatted-state (format-initial-state initial-state)
        comment (generate-comment key-obj)
        parsed (:parsed-output match-result)
        rule-id (when match-result
                  (get-in match-result [:from :id]))]
    {:initial_state formatted-state
     :key key-obj
     :matched_rule (when match-result
                     (cond-> {:id rule-id
                              :to (:to parsed)
                              :held (:held parsed)
                              :afterup (:afterup parsed)
                              :alone (:alone parsed)}
                       comment (assoc :comment comment)))}))

(defn write-test-file
  "Write a test case to a JSON file"
  [initial-state key-obj match-result]
  (let [filename (test-filename initial-state key-obj)
        filepath (str output-dir "/" filename)
        test-data (format-test-output initial-state key-obj match-result)
        json-str (json/generate-string test-data {:pretty true})]
    (spit filepath json-str)
    filepath))

;; ============================================================================
;; BFS Exploration (Parallelized)
;; ============================================================================

(defn valid-state?
  "Check if a state is valid according to our state invariants"
  [{:keys [dsk_layer dsk_ins_sub_mode dsk_return_to_layer]}]
  (let [validation (state/validate-state {:layer dsk_layer
                                          :submode dsk_ins_sub_mode
                                          :return-to dsk_return_to_layer})]
    (nil? validation)))

(defn process-single-test
  "Process a single key/modifier/app combination. Returns {:test ... :new-states ...}"
  [state key-name modifier app-info]
  (let [{:keys [name bundle_id]} app-info
        key-obj (make-key-obj key-name modifier)
        test-state (assoc state :application name)
        match (do-match-rules test-state key-name modifier bundle_id)
        parsed (when match (:parsed-output match))
        new-states (when parsed (collect-new-states test-state parsed))]
    ;; Always create a test, even for no-match (to catch accidental new matches)
    ;; Pass full match object so format-test-output can extract :id from [:from :id]
    {:test {:state test-state :key key-obj :result match}
     :new-states (or new-states [])}))

(defn explore-state
  "Generate tests for all key/modifier combos in a state (parallelized).
   App is part of the state for layers that care about apps.
   Returns {:tests [...] :new-states [...]}"
  [state inputs visited-states]
  (let [{:keys [keys modifiers fixed_combos applications]} inputs
        layer (:dsk_layer state)
        ;; Get app info for this state
        app-name (:application state)
        app-info (or (first (filter #(= (:name %) app-name) applications))
                     {:name "other" :bundle_id nil})
        ;; Generate all key/modifier combinations (cross product)
        regular-combos (for [key-name keys
                             modifier modifiers]
                         {:key key-name :modifiers modifier})
        ;; Add fixed combos (F-keys with specific modifiers from Kinesis firmware)
        fixed-combo-maps (map (fn [{:keys [key modifiers]}]
                                {:key key :modifiers (or modifiers {})})
                              (or fixed_combos []))
        ;; Combine all combos
        all-combos (concat regular-combos fixed-combo-maps)
        ;; Process in parallel
        results (doall (pmap (fn [{:keys [key modifiers]}]
                              (process-single-test state key modifiers app-info))
                            all-combos))
        ;; Collect tests and new states
        tests (map :test results)
        ;; For new states in app-specific layers, expand to all apps
        raw-new-states (mapcat :new-states results)
        expanded-new-states (mapcat (fn [new-state]
                                      (if (contains? layers-with-app-rules (:dsk_layer new-state))
                                        ;; Expand to all app variants
                                        (map #(assoc new-state :application (:name %)) applications)
                                        ;; Just use "other" app
                                        [(assoc new-state :application "other")]))
                                    raw-new-states)
        all-new-states (->> expanded-new-states
                            (filter #(and (not (contains? visited-states (state-key %)))
                                         (valid-state? %)))
                            distinct)]
    {:tests (vec tests)
     :new-states (set all-new-states)}))

(defn root-states
  "Return the root states for BFS exploration.
   Layer 0 starts with all app variants since it has app-specific rules."
  [applications]
  (for [app applications]
    {:dsk_layer 0
     :dsk_ins_sub_mode -1
     :dsk_return_to_layer -1
     :application (:name app)}))

(defn format-state-str
  "Format state for display, including app for layers that care"
  [{:keys [dsk_layer dsk_ins_sub_mode dsk_return_to_layer application]}]
  (let [layer-name (get layer-names dsk_layer (str "L" dsk_layer))]
    (if (contains? layers-with-app-rules dsk_layer)
      (format "%s (layer=%d) sub=%d ret=%d app=%s"
              layer-name dsk_layer dsk_ins_sub_mode dsk_return_to_layer (or application "other"))
      (format "%s (layer=%d) sub=%d ret=%d"
              layer-name dsk_layer dsk_ins_sub_mode dsk_return_to_layer))))

(defn run-bfs
  "Run BFS exploration with parallel test generation.
   Phase 1: BFS to discover states and generate tests (sequential for correctness)
   Phase 2: Write test files (parallel)"
  [inputs]
  (println "Starting BFS exploration...")
  (println "Keys:" (count (:keys inputs)))
  (println "Modifiers:" (count (:modifiers inputs)))
  (println "Fixed combos:" (count (:fixed_combos inputs)))
  (println "Applications:" (count (:applications inputs)))
  (println "Combos per state:" (+ (* (count (:keys inputs)) (count (:modifiers inputs)))
                                  (count (:fixed_combos inputs))))
  (println "Layers with app-specific states:" (sort layers-with-app-rules))
  (println)

  (let [roots (root-states (:applications inputs))
        queue (atom (vec roots))
        visited (atom (set (map state-key roots)))
        all-results (atom [])
        all-discovered (atom (set roots))  ;; Track actual state objects
        state-count (atom 0)]

    (while (seq @queue)
      (let [current (first @queue)]
        (swap! queue rest)
        (swap! state-count inc)

        (let [{:keys [tests new-states]} (explore-state current inputs @visited)]
          ;; Store results for this state
          (swap! all-results conj {:state current :tests tests})
          (println (format "State %d: %s - %d tests, %d new states"
                           @state-count
                           (format-state-str current)
                           (count tests)
                           (count new-states)))

          ;; Queue new states
          (doseq [s new-states]
            (let [sk (state-key s)]
              (when-not (contains? @visited sk)
                (swap! visited conj sk)
                (swap! all-discovered conj s)
                (swap! queue conj s)))))))

    {:tests (mapcat :tests @all-results)
     :states-explored @state-count
     :states-found (count @visited)
     :discovered-states @all-discovered}))

;; ============================================================================
;; State Reachability Validation
;; ============================================================================

(defn expected-bfs-states
  "Get all expected states that BFS should reach.
   Returns set of state-keys that should be discovered."
  [applications]
  ;; Get all valid desktop states from state library
  (->> (state/all-valid-states)
       ;; Filter to desktop only (laptop and None don't apply to BFS)
       (filter #(= (:device %) :desktop))
       ;; Expand app-specific layers
       (mapcat (fn [{:keys [layer submode return-to app] :as s}]
                 (if (contains? layers-with-app-rules layer)
                   ;; Expand to all app variants
                   (map #(vector layer submode return-to (:name %)) applications)
                   ;; Non-app layers use "other"
                   [[layer submode return-to "other"]])))
       set))

(defn validate-state-reachability
  "Validate that all expected states were reached by BFS.
   Returns nil if all reached, or {:missing [...] :extra [...]}."
  [discovered-states expected-states]
  (let [;; Convert discovered states to comparable format (layer, submode, return-to, app)
        discovered-set (set (map (fn [{:keys [dsk_layer dsk_ins_sub_mode dsk_return_to_layer application]}]
                                   [dsk_layer dsk_ins_sub_mode dsk_return_to_layer (or application "other")])
                                 discovered-states))
        missing (clojure.set/difference expected-states discovered-set)
        extra (clojure.set/difference discovered-set expected-states)]
    (when (or (seq missing) (seq extra))
      {:missing (sort missing)
       :extra (sort extra)})))

;; ============================================================================
;; Coverage Validation
;; ============================================================================

(defn desktop-block?
  "Check if a block is for Desktop device (has :!apple_internal marker)"
  [block]
  (let [rules-vec (:rules block)
        first-item (when (vector? rules-vec) (first rules-vec))]
    (= first-item :!apple_internal)))

(defn lhs-blocked-rule?
  "Check if a rule ID is for a LHS blocked key (expected to be uncovered).
   These are global blocking rules for left-hand side keys."
  [rule-id]
  (and (string? rule-id)
       (str/includes? rule-id "device=Desktop]")  ;; Global Desktop block (no layer condition)
       (str/includes? rule-id "blocked")))

(defn extract-all-rule-ids
  "Extract all rule IDs from the parsed config for Desktop device only.
   Config has :main which is a vector of blocks, each with :rules.
   Filters out Laptop (:apple_internal) and None profile rules.
   Also filters out LHS blocked rules (expected to be uncovered)."
  [config]
  (let [blocks (:main config)
        ;; Only include Desktop blocks (those with :!apple_internal)
        desktop-blocks (filter desktop-block? blocks)
        all-rules (mapcat (fn [block]
                           ;; Skip the :!apple_internal marker and app keywords
                           (->> (:rules block)
                                (filter vector?)))
                          desktop-blocks)]
    (->> all-rules
         (map (fn [rule]
                ;; Rules are vectors, first element is the from-map with :id
                (when (vector? rule)
                  (get (first rule) :id))))
         (filter some?)
         ;; Exclude LHS blocked rules - they're expected to be uncovered
         (remove lhs-blocked-rule?)
         set)))

(defn collect-matched-ids
  "Collect all rule IDs that were matched in tests"
  [tests]
  (->> tests
       (map :result)
       (filter some?)
       (map #(get-in % [:from :id]))
       (filter some?)
       set))

(defn validate-coverage
  "Validate that all rule IDs are covered by at least one test.
   Returns nil if all covered, or a set of uncovered IDs."
  [config tests]
  (let [all-ids (extract-all-rule-ids config)
        matched-ids (collect-matched-ids tests)
        uncovered (set/difference all-ids matched-ids)]
    (when (seq uncovered)
      uncovered)))

;; ============================================================================
;; Main
;; ============================================================================

(defn -main [& args]
  (println "=== BFS Test Generator ===")
  (println)

  ;; Ensure output directory exists
  (let [dir (java.io.File. output-dir)]
    (when-not (.exists dir)
      (.mkdirs dir)))

  ;; Load inputs
  (println "Loading inputs from" inputs-file)
  (let [inputs (load-inputs)]
    (println "Loaded" (count (:keys inputs)) "keys,"
             (count (:modifiers inputs)) "modifier combos,"
             (count (:applications inputs)) "apps")
    (println)

    ;; Pre-load config before parallel work
    (println "Pre-loading config...")
    (get-config)
    (println)

    ;; Run BFS
    (let [{:keys [tests states-explored states-found discovered-states]} (run-bfs inputs)]
      (println)
      (println "=== Writing Test Files (parallel) ===")

      ;; Write each test in parallel
      (let [write-results (doall (pmap (fn [{:keys [state key result]}]
                                         (write-test-file state key result))
                                       tests))]
        (println)
        (println "=== Summary ===")
        (println "States explored:" states-explored)
        (println "States discovered:" states-found)
        (println "Tests generated:" (count write-results))
        (println "Output directory:" output-dir)

        ;; Validate state reachability
        (println)
        (println "=== State Reachability Validation ===")
        (let [expected (expected-bfs-states (:applications inputs))
              reachability-result (validate-state-reachability discovered-states expected)]
          (if reachability-result
            (do
              (when (seq (:missing reachability-result))
                (println "ERROR: Expected states NOT reached by BFS:")
                (doseq [[layer submode ret app] (:missing reachability-result)]
                  (println (format "  - layer=%d submode=%d return-to=%d app=%s"
                                   layer submode ret app)))
                (println "Total missing:" (count (:missing reachability-result))))
              (when (seq (:extra reachability-result))
                (println)
                (println "WARNING: Unexpected states reached by BFS (not in valid-states):")
                (doseq [[layer submode ret app] (:extra reachability-result)]
                  (println (format "  - layer=%d submode=%d return-to=%d app=%s"
                                   layer submode ret app)))
                (println "Total extra:" (count (:extra reachability-result))))
              (System/exit 1))
            (println "All" (count expected) "expected states were reached")))

        ;; Validate coverage
        (println)
        (println "=== Coverage Validation ===")
        (let [uncovered (validate-coverage (get-config) tests)]
          (if uncovered
            (do
              (println "ERROR: The following rule IDs are not covered by any test:")
              (doseq [id (sort uncovered)]
                (println "  -" id))
              (println)
              (println "Total uncovered:" (count uncovered))
              (System/exit 1))
            (println "All" (count (extract-all-rule-ids (get-config))) "rule IDs are covered by tests")))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
