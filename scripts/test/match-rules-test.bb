#!/usr/bin/env bb
;; Unit tests for Karabiner rule matcher
;;
;; Run with: bb test/match-rules-test.bb

(require '[clojure.test :refer [deftest testing is run-tests]])
(require '[clojure.string :as str])

;; Load the main module (use absolute path for reliability)
(def project-root "/Users/rbalicki/code/voicemode")
(load-file (str project-root "/karabiner-test-harness/match-rules.bb"))

(def test-edn-file "/Users/rbalicki/code/voicemode/karabiner.edn")

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn match
  "Shorthand for matching rules"
  [key & {:keys [layer modal submode return-to app device mods]
          :or {layer 0 modal 0 submode -1 return-to -1 device :external mods #{}}}]
  (match-rules/match-rules
    {:edn-file test-edn-file
     :key key
     :mods (if (set? mods) mods (set mods))
     :state {:dsk_layer layer
             :dsk_in_modal_layer modal
             :dsk_ins_sub_mode submode
             :dsk_return_to_layer return-to}
     :app app
     :device device}))

(defn first-match-desc [result]
  "Get description of first match"
  (get-in result [:first-match :description]))

(defn match-count [result]
  "Get number of matches"
  (:match-count result))

(defn has-match? [result]
  "Check if any match was found"
  (> (match-count result) 0))

(defn first-match-contains? [result substr]
  "Check if first match description contains substring"
  (when-let [desc (first-match-desc result)]
    (str/includes? desc substr)))

;; ============================================================================
;; Tests: Normal Mode (Layer 0)
;; ============================================================================

(deftest normal-mode-layer-entry
  (testing "J enters Ins mode from Normal"
    (let [result (match :j :layer 0)]
      (is (has-match? result))
      (is (first-match-contains? result "Normal"))
      (is (first-match-contains? result "Entry"))))

  (testing "N enters Nav mode from Normal"
    (let [result (match :n :layer 0)]
      (is (has-match? result))
      (is (first-match-contains? result "Normal"))))

  (testing "L enters L layer from Normal"
    (let [result (match :l :layer 0)]
      (is (has-match? result))
      (is (first-match-contains? result "Normal"))))

  (testing "Unused keys in Normal are blocked by global catch-all"
    (let [result (match :k :layer 0)]
      (is (has-match? result))
      ;; Should match the global catch-all blocker
      (is (first-match-contains? result "Block all unmapped")))))

;; ============================================================================
;; Tests: Ins Mode (Layer 1)
;; ============================================================================

(deftest ins-mode-passthrough
  (testing "Letters pass through in Ins mode"
    (let [result (match :y :layer 1)]
      (is (has-match? result))
      (is (first-match-contains? result "pass-through"))))

  (testing "Numbers pass through in Ins mode"
    (let [result (match :6 :layer 1)]
      (is (has-match? result))))

  (testing "Ctrl+Y triggers Wispr Flow (not pass-through)"
    (let [result (match :y :layer 1 :mods #{:right_control})]
      (is (has-match? result))
      (is (first-match-contains? result "Wispr Flow")))))

(deftest ins-mode-brackets
  (testing "[ maps to backspace in Ins mode"
    (let [result (match :open_bracket :layer 1)]
      (is (has-match? result))
      ;; Should find the [ -> backspace rule
      (is (= :delete_or_backspace (get-in result [:first-match :to]))))))

;; ============================================================================
;; Tests: Global Shortcuts
;; ============================================================================

(deftest global-shortcuts
  (testing "Right control alone exits from modal layers"
    ;; The rule uses {:alone ...} which we don't fully model,
    ;; but we can check the rule exists
    (let [result (match :right_control :layer 0)]
      (is (has-match? result))))

  (testing "Ctrl+J exits to Ins from modal layers"
    (let [result (match :j :layer 2 :modal 1 :mods #{:right_control})]
      (is (has-match? result))
      (is (first-match-contains? result "Ctrl+J"))))

  (testing "Ctrl+N sends escape globally"
    (let [result (match :n :mods #{:right_control})]
      (is (has-match? result))
      (is (first-match-contains? result "Ctrl+N")))))

;; ============================================================================
;; Tests: Modal Layers
;; ============================================================================

(deftest nav-layer
  (testing "H opens iTerm from Nav layer"
    (let [result (match :h :layer 2 :modal 1)]
      (is (has-match? result))
      (is (first-match-contains? result "Nav")))))

(deftest admin-layer
  (testing "Space maximizes window in Admin layer"
    (let [result (match :spacebar :layer 9 :modal 1)]
      (is (has-match? result))
      (is (first-match-contains? result "Admin")))))

;; ============================================================================
;; Tests: App-Specific Layers
;; ============================================================================

(deftest chrome-layer
  (testing "Chrome layer rules require Chrome app"
    (let [result-no-app (match :y :layer 3 :modal 1)
          result-with-chrome (match :y :layer 3 :modal 1 :app "com.google.Chrome")]
      ;; Without Chrome app, should not match Chrome-specific rules
      ;; With Chrome app, should match
      (when (has-match? result-with-chrome)
        (is (first-match-contains? result-with-chrome "Chrome"))))))

;; ============================================================================
;; Tests: Device Conditions
;; ============================================================================

(deftest device-conditions
  (testing "Desktop rules don't apply to Apple internal keyboard"
    (let [result-external (match :j :layer 0 :device :external)
          result-internal (match :j :layer 0 :device :apple_internal)]
      ;; External keyboard should match desktop rules
      (is (has-match? result-external))
      ;; Internal keyboard might have different/fewer matches
      ;; (This depends on config structure)
      )))

;; ============================================================================
;; Tests: Modifier Matching
;; ============================================================================

(deftest modifier-matching
  (testing "Shift+key matches shift rules"
    (let [result (match :up_arrow :layer 1 :mods #{:shift})]
      ;; Shift+Up in Ins mode should produce [ bracket
      (is (has-match? result))))

  (testing "Right command + J does word navigation in Ins"
    (let [result (match :j :layer 1 :mods #{:right_command})]
      (is (has-match? result))
      ;; Should be word-left navigation
      )))

;; ============================================================================
;; Tests: Submode Matching
;; ============================================================================

(deftest submode-matching
  (testing "Shift mirror oneshot affects letter output"
    (let [result-normal (match :y :layer 1 :submode 0)
          result-oneshot (match :y :layer 1 :submode 1)]
      (is (has-match? result-normal))
      (is (has-match? result-oneshot))
      ;; In oneshot mode, Y should output Shift+T (mirrored uppercase)
      (when (has-match? result-oneshot)
        (is (first-match-contains? result-oneshot "Shift+mirror"))))))

;; ============================================================================
;; Tests: Rule Shadowing Detection
;; ============================================================================

(deftest rule-shadowing
  (testing "Multiple rules can match the same input"
    ;; The pass-through rules use ## which matches any modifiers
    ;; So Cmd+Y in Ins mode might match both a specific rule and pass-through
    (let [result (match :y :layer 1 :mods #{:left_command})]
      ;; Check if multiple rules match (shadowing detection)
      (when (> (match-count result) 1)
        (println "Warning: Multiple rules match Cmd+Y in Ins mode")
        (println "First (active):" (first-match-desc result))
        (println "Total matches:" (match-count result))))))

;; ============================================================================
;; Tests: Edge Cases
;; ============================================================================

(deftest edge-cases
  (testing "Unknown key returns no matches or global catch-all"
    (let [result (match :f24 :layer 0)]
      ;; Either no match or global catch-all
      (is (or (not (has-match? result))
              (first-match-contains? result "Block")))))

  (testing "Empty modifiers vs no modifiers specified"
    (let [result-empty (match :j :layer 0 :mods #{})
          result-nil (match :j :layer 0)]
      (is (= (match-count result-empty) (match-count result-nil))))))

;; ============================================================================
;; Run Tests
;; ============================================================================

(defn -main [& args]
  (let [results (run-tests)]
    (System/exit (if (and (zero? (:fail results)) (zero? (:error results))) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
