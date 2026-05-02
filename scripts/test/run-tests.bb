#!/usr/bin/env bb
;; Test Runner
;;
;; Validates generated snapshot tests against current karabiner.edn.
;; For each test file, runs match-rules and compares output.
;;
;; Usage:
;;   bb scripts/test/run-tests.bb [options]
;;
;; Options:
;;   --verbose    Show all test results (not just failures)
;;   --fail-fast  Stop on first failure
;;   --state S    Filter tests by state string (must start with profile=)
;;                Example: --state "profile=Default:device=Desktop:layer=7"
;;
;; Exit codes:
;;   0 - All tests passed
;;   1 - Some tests failed

(ns run-tests
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]
            [cheshire.core :as json]
            [babashka.fs :as fs]))

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
(def test-dir (str project-root "/tests/unit"))

;; Load config once at startup
(def config (atom nil))

(defn get-config []
  (when-not @config
    (reset! config (match-rules/parse-edn-file edn-file)))
  @config)

;; ============================================================================
;; Test Loading
;; ============================================================================

(defn load-test-file [path]
  "Load and parse a test JSON file"
  (try
    (json/parse-string (slurp path) true)
    (catch Exception e
      (println "Error loading test file:" path)
      (println "  " (.getMessage e))
      nil)))

(defn list-test-files
  "List all test files in the test directory"
  []
  (->> (fs/glob test-dir "*.json")
       (map str)
       sort))

(defn test-matches-state?
  "Check if a test's initial_state matches the filter state.
   Filter state fields that are present must match."
  [test-data filter-state]
  (let [{:keys [initial_state]} test-data
        {:keys [profile device layer submode return-to app]} filter-state]
    (and
      (or (nil? profile) (= (:profile initial_state) profile))
      (or (nil? device)
          (and (= device :desktop) (= (:device initial_state) "Desktop"))
          (and (= device :laptop) (= (:device initial_state) "Laptop")))
      (or (nil? layer) (= (:dsk_layer initial_state) layer))
      (or (nil? submode) (= (:dsk_ins_sub_mode initial_state) submode))
      (or (nil? return-to) (= (:dsk_return_to_layer initial_state) return-to))
      (or (nil? app)
          (= (name app) (:application initial_state))))))

;; ============================================================================
;; Rule Matching
;; ============================================================================

(defn run-match
  "Run match-rules for a test case and return parsed output"
  [{:keys [initial_state key]}]
  (let [{:keys [dsk_layer dsk_ins_sub_mode dsk_return_to_layer dsk_2h_mode application]} initial_state
        internal-state {:dsk_layer dsk_layer
                        :dsk_in_modal_layer 0
                        :dsk_ins_sub_mode dsk_ins_sub_mode
                        :dsk_return_to_layer dsk_return_to_layer
                        :dsk_2h_mode (or dsk_2h_mode 0)}
        key-name (keyword (:key key))
        mod-set (->> (dissoc key :key)
                     keys
                     (map keyword)
                     set)
        ;; Map application name to bundle ID
        app-bundle-id (case application
                        "Chrome" "com.google.Chrome"
                        "iTerm" "com.googlecode.iterm2"
                        "VSCode" "com.microsoft.VSCode"
                        nil)
        result (match-rules/find-matching-rules (get-config)
                                                 key-name
                                                 mod-set
                                                 internal-state
                                                 app-bundle-id
                                                 :external)]
    (:first-match result)))

;; ============================================================================
;; Comparison
;; ============================================================================

(defn normalize-key
  "Normalize a map key to keyword"
  [k]
  (if (keyword? k)
    k
    (keyword k)))

(defn normalize-value
  "Normalize a value to keyword if it's a string key name"
  [v]
  (cond
    (keyword? v) v
    (string? v) (keyword v)
    :else v))

(defn normalize-action
  "Normalize an action map, handling both:
   - {\"key\" \"x\", \"mod\" true} format (from parse-output-key)
   - {:key :x, :modi [:mod]} format (from raw maps)"
  [action]
  (if-not (map? action)
    action
    (let [key-val (or (:key action) (get action "key"))
          modi-val (or (:modi action) (get action "modi"))
          ;; Get explicit modifier flags (like left_command: true)
          mod-flags (->> action
                        (filter (fn [[k v]]
                                  (and (true? v)
                                       (let [kn (if (keyword? k) (name k) k)]
                                         (str/includes? kn "_")))))
                        (map (fn [[k _]] (normalize-key k)))
                        set)]
      (cond
        ;; Has :modi array - convert to flags format
        modi-val
        (let [all-mods (into mod-flags (map normalize-value modi-val))]
          (merge {:key (normalize-value key-val)}
                 (into {} (map (fn [m] [m true]) (sort-by name all-mods)))))

        ;; Has explicit modifier flags - normalize them
        (seq mod-flags)
        (merge {:key (normalize-value key-val)}
               (into {} (map (fn [m] [m true]) (sort-by name mod-flags))))

        ;; Just key, no modifiers - could be shell or other action
        key-val
        {:key (normalize-value key-val)}

        ;; Other action type (shell, etc)
        :else
        (->> action
             (map (fn [[k v]] [(normalize-key k) v]))
             (into {}))))))

(defn normalize-output
  "Normalize output for comparison (remove nil fields, convert keys to keywords, sort maps)"
  [output]
  (cond
    (nil? output) nil

    ;; Handle action array specially
    (and (map? output)
         (or (:actions output) (get output "actions")))
    (let [actions (or (:actions output) (get output "actions"))
          rs (or (:resulting_state output) (get output "resulting_state"))]
      {:actions (mapv normalize-action actions)
       :resulting_state (normalize-output rs)})

    ;; Regular map
    (map? output)
    (->> output
         (filter (fn [[k v]] (some? v)))
         (map (fn [[k v]] [(normalize-key k) (normalize-output v)]))
         (sort-by (comp name first))
         (into {}))

    (vector? output) (mapv normalize-output output)
    :else output))

(defn outputs-equal?
  "Compare two outputs for equality after normalization"
  [expected actual]
  (= (normalize-output expected)
     (normalize-output actual)))

(defn compare-output-section
  "Compare a single output section (to/held/afterup/alone)"
  [section expected actual]
  (let [exp-norm (normalize-output expected)
        act-norm (normalize-output actual)]
    (if (= exp-norm act-norm)
      {:section section :pass true}
      {:section section
       :pass false
       :expected exp-norm
       :actual act-norm})))

(defn compare-test-result
  "Compare expected test output with actual match result.
   Test format uses matched_rule which contains id, to, held, afterup, alone."
  [test-data match-result]
  (let [expected-rule (:matched_rule test-data)
        parsed (when match-result (:parsed-output match-result))
        actual-id (when match-result (get-in match-result [:from :id]))
        sections [:to :held :afterup :alone]
        comparisons (map (fn [s]
                          (compare-output-section s
                                                  (get expected-rule s)
                                                  (when parsed (get parsed s))))
                        sections)
        ;; Also compare rule ID
        id-match (= (:id expected-rule) actual-id)
        all-pass (and id-match (every? :pass comparisons))]
    {:pass all-pass
     :sections (if id-match
                 comparisons
                 (cons {:section :id
                        :pass false
                        :expected (:id expected-rule)
                        :actual actual-id}
                       comparisons))}))

;; ============================================================================
;; State Validation
;; ============================================================================

(defn validate-test-state
  "Validate that test states conform to invariants"
  [{:keys [initial_state]}]
  (let [{:keys [dsk_layer dsk_ins_sub_mode dsk_return_to_layer]} initial_state
        validation (state/validate-state {:layer dsk_layer
                                          :submode dsk_ins_sub_mode
                                          :return-to dsk_return_to_layer})]
    (when validation
      {:error :invalid-initial-state
       :message (:message validation)})))

;; ============================================================================
;; Diff Display
;; ============================================================================

(defn format-diff
  "Format a diff between expected and actual for display"
  [section expected actual]
  (str "  " (name section) ":\n"
       "    expected: " (pr-str expected) "\n"
       "    actual:   " (pr-str actual)))

(defn format-failure
  "Format a test failure for display"
  [test-file test-data result]
  (let [failed-sections (filter #(not (:pass %)) (:sections result))]
    (str "FAIL: " (fs/file-name test-file) "\n"
         (str/join "\n" (map #(format-diff (:section %)
                                          (:expected %)
                                          (:actual %))
                            failed-sections)))))

;; ============================================================================
;; Test Runner
;; ============================================================================

(defn run-single-test
  "Run a single test and return result"
  [test-file]
  (let [test-data (load-test-file test-file)]
    (if-not test-data
      {:file test-file :error :load-error}
      (if-let [state-error (validate-test-state test-data)]
        {:file test-file :error (:error state-error) :message (:message state-error)}
        (let [match-result (run-match test-data)
              comparison (compare-test-result test-data match-result)]
          {:file test-file
           :pass (:pass comparison)
           :sections (:sections comparison)
           :test-data test-data})))))

(defn run-single-test-result
  "Run a single test and return full result (for parallel execution)"
  [{:keys [file data]}]
  (let [test-data (or data (load-test-file file))]
    (if-not test-data
      {:file file :error :load-error}
      (if-let [state-error (validate-test-state test-data)]
        {:file file :error (:error state-error) :message (:message state-error)}
        (let [match-result (run-match test-data)
              comparison (compare-test-result test-data match-result)]
          {:file file
           :pass (:pass comparison)
           :sections (:sections comparison)
           :test-data test-data})))))

(defn run-all-tests
  "Run all tests and collect results (parallelized)"
  [{:keys [verbose fail-fast state-filter]}]
  (let [all-files (list-test-files)
        ;; Filter by state if provided (sequential - fast enough)
        test-items (if state-filter
                     (let [filtered (atom [])]
                       (doseq [f all-files]
                         (let [test-data (load-test-file f)]
                           (when (and test-data (test-matches-state? test-data state-filter))
                             (swap! filtered conj {:file f :data test-data}))))
                       @filtered)
                     (map (fn [f] {:file f :data nil}) all-files))
        total (count test-items)]

    (when state-filter
      (println (format "State filter: profile=%s device=%s layer=%s submode=%s return-to=%s app=%s"
                       (or (:profile state-filter) "*")
                       (or (some-> (:device state-filter) name) "*")
                       (or (:layer state-filter) "*")
                       (or (:submode state-filter) "*")
                       (or (:return-to state-filter) "*")
                       (or (some-> (:app state-filter) name) "*"))))
    (println (format "Running %d tests (parallel)..." total))
    (println)

    ;; Run tests in parallel
    (let [results (doall (pmap run-single-test-result test-items))
          ;; Categorize results
          errors-list (filter :error results)
          failures (filter #(and (not (:error %)) (not (:pass %))) results)
          passes (filter :pass results)]

      ;; Print errors
      (doseq [r errors-list]
        (println (format "ERROR: %s - %s"
                        (fs/file-name (:file r))
                        (or (:message r) (name (:error r))))))

      ;; Print failures
      (doseq [r failures]
        (println (format-failure (:file r) (:test-data r) r))
        (println)
        (when fail-fast
          (println "Stopping after first failure (--fail-fast)")
          (System/exit 1)))

      ;; Print passes if verbose
      (when verbose
        (doseq [r passes]
          (println (format "PASS: %s" (fs/file-name (:file r))))))

      {:total total
       :passed (count passes)
       :failed (count failures)
       :errors (count errors-list)})))

;; ============================================================================
;; CLI
;; ============================================================================

(defn parse-state-filter
  "Parse and validate a state filter string using state library functions"
  [state-str]
  (try
    ;; Use parse-complete-state to validate hierarchy (requires profile)
    (let [{:keys [state]} (state/parse-complete-state state-str)]
      (when-not state
        (throw (ex-info "Empty state" {})))
      ;; Also validate state invariants if layer is specified
      (when (:layer state)
        (when-let [err (state/validate-state state)]
          (throw (ex-info (:message err) {:error err}))))
      state)
    (catch Exception e
      (println (str "Error: " (.getMessage e)))
      (println "  Example: --state \"profile=Default:device=Desktop:layer=7\"")
      (System/exit 1))))

(defn parse-args [args]
  (loop [args args
         result {:verbose false :fail-fast false :state-filter nil}]
    (if (empty? args)
      result
      (let [[arg & rest-args] args]
        (case arg
          "--verbose" (recur rest-args (assoc result :verbose true))
          "--fail-fast" (recur rest-args (assoc result :fail-fast true))
          "--state" (let [state (parse-state-filter (first rest-args))]
                      (recur (rest rest-args) (assoc result :state-filter state)))
          (recur rest-args result))))))

(defn -main [& args]
  (let [opts (parse-args args)
        _ (get-config)  ;; Pre-load config
        {:keys [total passed failed errors]} (run-all-tests opts)]

    (println)
    (println "=== Results ===")
    (println (format "Total:  %d" total))
    (println (format "Passed: %d" passed))
    (println (format "Failed: %d" failed))
    (println (format "Errors: %d" errors))

    (when (or (pos? failed) (pos? errors))
      (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
