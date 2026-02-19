#!/usr/bin/env bb
;; Reorder karabiner.edn rules by state
;;
;; Groups all rules by their condition state in canonical order:
;; 1. None
;; 2. Default (catch-all)
;; 3. Laptop
;; 4. Desktop + layers (with submodes/return-to first, then catch-all)
;; 5. Desktop (catch-all)
;;
;; Usage:
;;   bb reorder-by-state.bb <input.edn> <output.edn>
;;   bb reorder-by-state.bb src/karabiner.edn src/karabiner.edn  # in-place

(ns reorder-by-state
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

;; Load libraries
(load-file (str (System/getProperty "user.dir") "/scripts/lib/state.bb"))
(load-file (str (System/getProperty "user.dir") "/scripts/lib/key-order.bb"))

;; ============================================================================
;; Rule Extraction
;; ============================================================================

(defn extract-block-info
  "Extract device/profile/app info from block-level keywords."
  [block]
  (let [rules-vec (:rules block)
        keywords (when (vector? rules-vec)
                   (take-while keyword? rules-vec))]
    {:profile (cond
                (some #{:None} keywords) "None"
                :else "Default")
     :device (cond
               (some #{:apple_internal} keywords) :laptop
               (some #{:!apple_internal} keywords) :desktop
               :else nil)
     :app (first (filter state/all-apps keywords))}))

(defn extract-rule-condition
  "Extract condition state from a rule's condition array (3rd element)."
  [rule]
  (when (and (vector? rule) (>= (count rule) 3))
    (let [cond-arr (nth rule 2)]
      (state/parse-condition-array cond-arr))))

(defn get-rule-state
  "Get the full condition state for a rule (block info + rule condition)."
  [block-info rule]
  (let [rule-cond (extract-rule-condition rule)]
    (merge block-info rule-cond)))

(defn extract-all-rules
  "Extract all rules from config with their condition states.
   Returns seq of {:rule [...] :state {...} :block-des '...'}."
  [config]
  (for [block (:main config)
        :let [block-info (extract-block-info block)
              rules-vec (:rules block)
              actual-rules (when (vector? rules-vec)
                             (->> rules-vec
                                  (drop-while keyword?)
                                  (filter vector?)))]
        rule actual-rules]
    {:rule rule
     :state (get-rule-state block-info rule)
     :block-des (:des block)}))

;; ============================================================================
;; Key Parsing (for sorting by specificity)
;; ============================================================================

;; Goku modifier prefixes - each character represents a mandatory modifier
(def modifier-chars
  "Set of modifier prefix characters in Goku syntax"
  #{\C \S \T \O \Q \E \R \W \F \A})

(defn parse-key-string
  "Parse a key string like :!Of19 or :f19 into {:bare 'f19' :mods ['O']}.
   Returns bare key and list of mandatory modifier chars."
  [key-val]
  (let [key-str (if (keyword? key-val) (name key-val) (str key-val))]
    (if-let [[_ mods bare] (re-matches #"!([A-Z]+)(.+)" key-str)]
      {:bare bare
       :mods (vec (filter modifier-chars mods))}
      ;; No modifier prefix, or ## prefix (optional)
      {:bare (if (clojure.string/starts-with? key-str "##")
               (subs key-str 2)
               key-str)
       :mods []})))

(defn modifier-sort-key
  "Create a canonical sort key for modifiers.
   More modifiers = lower number (sorts first).
   Then alphabetical by modifier chars for stability."
  [mods]
  (let [count-key (- 100 (count mods))  ;; More mods = lower number
        alpha-key (apply str (sort mods))]
    [count-key alpha-key]))

(defn rule-sort-key
  "Create sort key for a rule: [bare-key-position modifier-sort-key].
   Rules are sorted by bare keycode position (for correct shadowing behavior),
   then by specificity (most mandatory mods first).

   This ensures that !Of19 (specific: requires opt) comes before f19 with
   {:optional [:any]} (catches all), since both involve the f19 keycode."
  [rule]
  (when (vector? rule)
    (let [key-val (get-in rule [0 :key])
          {:keys [bare mods]} (parse-key-string key-val)
          ;; Use bare key position so overlapping rules are grouped correctly
          position (lib.key-order/get-key-position bare)]
      [position (modifier-sort-key mods)])))

(defn sort-rules-by-specificity
  "Sort rules within a group by bare key, then by specificity (most mods first)."
  [rules]
  (sort-by rule-sort-key rules))

;; ============================================================================
;; State Matching
;; ============================================================================

(defn state-matches-grouping-state?
  "Check if a rule's state matches a grouping state exactly.
   A rule belongs to a grouping state if all its condition fields match."
  [rule-state grouping-state]
  (and
    ;; Profile must match
    (= (:profile rule-state) (:profile grouping-state))
    ;; Device must match (nil matches nil)
    (= (:device rule-state) (:device grouping-state))
    ;; Layer must match
    (= (:layer rule-state) (:layer grouping-state))
    ;; Submode must match (nil in grouping = catch-all, matches any rule with that layer but no submode condition)
    (= (:submode rule-state) (:submode grouping-state))
    ;; Return-to must match
    (= (:return-to rule-state) (:return-to grouping-state))
    ;; App must match
    (= (:app rule-state) (:app grouping-state))))

(defn find-grouping-state
  "Find the grouping state that a rule belongs to."
  [rule-state all-grouping-states]
  (first (filter #(state-matches-grouping-state? rule-state %) all-grouping-states)))

;; ============================================================================
;; Output Generation
;; ============================================================================

(defn group-rules-by-state
  "Group rules by their matching grouping state.
   Within each group, sort by bare key then specificity (most mandatory mods first)."
  [rules all-grouping-states]
  (let [grouped (group-by #(find-grouping-state (:state %) all-grouping-states) rules)]
    ;; Return in order of grouping states
    (for [gs all-grouping-states
          :let [rules-for-state (get grouped gs [])]
          :when (seq rules-for-state)]
      {:state gs
       :rules (sort-rules-by-specificity (map :rule rules-for-state))})))

(defn state-to-block
  "Convert a grouped state to an EDN block."
  [{:keys [state rules]}]
  (let [des (state/condition-state-to-des state)
        keywords (state/condition-state-to-block-keywords state)]
    {:des des
     :rules (vec (concat keywords rules))}))

(defn rebuild-config
  "Rebuild config with rules grouped by state."
  [original-config grouped-rules]
  (let [new-main (mapv state-to-block grouped-rules)]
    (assoc original-config :main new-main)))

;; ============================================================================
;; EDN Formatting (preserve style)
;; ============================================================================

(defn format-rule
  "Format a single rule for output."
  [rule]
  (pr-str rule))

(defn format-block
  "Format a block for output."
  [{:keys [des rules]}]
  (let [keywords (take-while keyword? rules)
        actual-rules (drop-while keyword? rules)
        keyword-str (str/join " " (map pr-str keywords))
        rules-str (str/join "\n   " (map format-rule actual-rules))]
    (str "{:des " (pr-str des) "\n"
         " :rules [" keyword-str "\n"
         "   " rules-str "]}")))

(defn format-config
  "Format the full config for output."
  [config]
  (let [;; Extract non-main keys
        other-keys (dissoc config :main)
        other-str (when (seq other-keys)
                    (str/join "\n "
                              (for [[k v] other-keys]
                                (str (pr-str k) " " (pr-str v)))))
        ;; Format main blocks
        blocks-str (str/join "\n\n " (map format-block (:main config)))]
    (str "{"
         (when other-str (str other-str "\n "))
         ":main\n ["
         blocks-str
         "]}\n")))

;; ============================================================================
;; Main
;; ============================================================================

(defn -main [& args]
  (when (< (count args) 2)
    (println "Usage: bb reorder-by-state.bb <input.edn> <output.edn>")
    (System/exit 1))

  (let [input-file (first args)
        output-file (second args)
        config (edn/read-string (slurp input-file))

        ;; Get all grouping states
        all-grouping-states (state/all-condition-states-for-grouping)

        ;; Extract all rules with their states
        all-rules (extract-all-rules config)
        _ (println (str "Found " (count all-rules) " rules"))

        ;; Check for rules that don't match any grouping state
        unmatched (filter #(nil? (find-grouping-state (:state %) all-grouping-states)) all-rules)
        _ (when (seq unmatched)
            (println "WARNING: Found rules that don't match any grouping state:")
            (doseq [r unmatched]
              (println "  State:" (:state r))
              (println "  Rule:" (pr-str (:rule r))))
            (System/exit 1))

        ;; Group rules by state
        grouped (group-rules-by-state all-rules all-grouping-states)
        _ (println (str "Grouped into " (count grouped) " blocks"))

        ;; Rebuild config
        new-config (rebuild-config config grouped)

        ;; Write output
        output-str (format-config new-config)]

    (spit output-file output-str)
    (println (str "Wrote " output-file))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
