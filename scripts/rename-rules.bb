#!/usr/bin/env bb

;; Rename rules with human-readable descriptions
;;
;; Usage:
;;   bb rename-rules.bb <input-edn> <output-edn>
;;
;; This script analyzes each rule and generates a descriptive name based on
;; what the rule actually does, not just the key mapping.

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; === Modifier conversion ===

(def modifier-map
  {"!S" "shift+"
   "!R" "rshift+"
   "!C" "cmd+"
   "!Q" "rcmd+"
   "!T" "ctrl+"
   "!O" "opt+"
   "!E" "ropt+"
   "!CS" "cmd+shift+"
   "!SC" "shift+cmd+"
   "!SO" "shift+opt+"
   "!OS" "opt+shift+"
   "!CO" "cmd+opt+"
   "!OC" "opt+cmd+"
   "!TC" "ctrl+cmd+"
   "!CT" "cmd+ctrl+"
   "!TO" "ctrl+opt+"
   "!OT" "opt+ctrl+"
   "!TOS" "ctrl+opt+shift+"
   "!TOC" "ctrl+opt+cmd+"
   "!TCS" "ctrl+cmd+shift+"
   "##" ""})

(defn convert-modifiers [s]
  "Convert !S, !C etc to shift+, cmd+ etc"
  (reduce (fn [text [pattern replacement]]
            (str/replace text pattern replacement))
          (str s)
          modifier-map))

;; === Layer and submode names ===

(def layer-names
  {0 "Normal" 1 "Ins" 2 "Nav" 3 "Chrome" 4 "VSCode" 5 "TMUX"
   6 "Comma" 7 "L" 8 "Term" 9 "Admin" 10 "InApp" 11 "AppSwitcher"
   12 "WindowSwitcher" 13 "Label" 14 "L-Cmd" 15 "L-Cmd-Shift"
   28 "Grid"})

(def submode-names
  {0 "base" 1 "shift-mirror-oneshot" 2 "shift-oneshot"
   3 "delete-chord" 4 "select-chord"})

;; === Condition extraction ===

(defn extract-block-conditions [rules-vec]
  (when (sequential? rules-vec)
    (vec (take-while #(or (keyword? %)
                          (and (vector? %) (not (map? (first %)))))
                     rules-vec))))

(defn get-condition-value [conds var-name]
  (some (fn [c]
          (when (and (vector? c) (string? (first c)) (= (first c) var-name))
            (second c)))
        conds))

(defn extract-rule-conditions [rule]
  (let [conds (nth rule 2 nil)]
    (when (sequential? conds)
      (if (and (vector? (first conds)) (string? (ffirst conds)))
        conds
        [conds]))))

(defn get-profile-from-block [block-conditions]
  "Determine profile from block conditions.
   :!apple_internal or :Desktop = Desktop profile
   :apple_internal or :Laptop = Laptop profile
   Neither = truly global (no profile)"
  (cond
    (some #(= % :Desktop) block-conditions) "Desktop"
    (some #(= % :!apple_internal) block-conditions) "Desktop"
    (some #(= % :Laptop) block-conditions) "Laptop"
    (some #(= % :apple_internal) block-conditions) "Laptop"
    :else nil))

;; === State string generation ===

(defn generate-state-string [profile layer submode return-to]
  (let [parts (cond-> []
                profile (conj (str "profile=" profile))
                layer (conj (str "dsk_layer=" layer))
                (and submode (not= submode -1)) (conj (str "dsk_ins_sub_mode=" submode))
                (and return-to (not= return-to -1)) (conj (str "dsk_return_to_layer=" return-to)))]
    (if (empty? parts)
      ""  ;; Empty string for global rules
      (str/join ":" parts))))

;; === Key description ===

(defn format-key [from-map]
  "Extract a readable key description from the from map"
  (if (map? from-map)
    (let [key-val (:key from-map)
          pkey (:pkey from-map)
          modi (:modi from-map)
          mandatory (get modi :mandatory [])
          key-name (name (or key-val pkey :unknown))]
      (if (seq mandatory)
        (str (str/join "+" (map name mandatory)) "+" key-name)
        key-name))
    (if (keyword? from-map)
      (name from-map)
      "?")))

;; === Action analysis ===

(defn analyze-action [action rule block-des]
  "Analyze what the rule's action does and return a description"
  (let [;; Check for variable sets in action
        action-vec (if (vector? action) action [action])
        sets-layer (some #(when (and (vector? %) (= "dsk_layer" (first %))) (second %)) action-vec)
        sets-submode (some #(when (and (vector? %) (= "dsk_ins_sub_mode" (first %))) (second %)) action-vec)
        sets-return (some #(when (and (vector? %) (= "dsk_return_to_layer" (first %))) (second %)) action-vec)
        ;; Check for shell command
        shell-cmd (some #(when (map? %) (:shell %)) action-vec)
        ;; Check for typed string (Term layer uses strings in action)
        typed-string (first (filter string? action-vec))
        ;; Check afterup for variable sets
        afterup (get (nth rule 3 {}) :afterup)
        afterup-vec (if (vector? afterup) afterup [])
        afterup-sets-submode (some #(when (and (vector? %) (= "dsk_ins_sub_mode" (first %))) (second %)) afterup-vec)
        ;; Get first output key (skip template names like :type-enter-ins)
        first-output (first (filter #(and (keyword? %)
                                          (not (str/starts-with? (name %) "type-")))
                                    action-vec))
        ;; Check if this is vk_none (no-op / block)
        is-vk-none (= first-output :vk_none)
        ;; Check if there's a real layer change (not just resetting invariants)
        real-layer-change (and sets-layer
                               (or (not= sets-layer 0) (not= sets-layer 1))
                               (not= sets-layer -1))]

    (cond
      ;; Typed string (Term layer) - show what gets typed
      typed-string
      (let [short-str (if (> (count typed-string) 20)
                        (str (subs typed-string 0 17) "...")
                        (str/trim typed-string))
            exit-desc (cond
                        (= sets-layer 0) ", exit to Normal"
                        (= sets-layer 1) ", exit to Ins"
                        :else "")]
        ;; Use single quotes to avoid escaping issues in EDN
        (str "type '" short-str "'" exit-desc))

      ;; Key output WITH submode clear = oneshot output
      (and first-output (not is-vk-none) (= sets-submode 0))
      (str (convert-modifiers (name first-output)) " (clear oneshot)")

      ;; Key output WITH layer change to Normal
      (and first-output (not is-vk-none) (= sets-layer 0))
      (str (convert-modifiers (name first-output)) ", exit to Normal")

      ;; Key output WITH layer change to Ins
      (and first-output (not is-vk-none) (= sets-layer 1))
      (str (convert-modifiers (name first-output)) ", exit to Ins")

      ;; Layer entry (layer >= 2)
      (and sets-layer (>= sets-layer 2))
      (str "enter " (get layer-names sets-layer (str "layer " sets-layer)))

      ;; Pure exit to Normal (no meaningful key output)
      (and (= sets-layer 0) (or (nil? first-output) is-vk-none))
      "exit to Normal"

      ;; Pure exit to Ins (no meaningful key output)
      (and (= sets-layer 1) (or (nil? first-output) is-vk-none))
      "exit to Ins"

      ;; Entering a submode (check if afterup clears it - that means it's a chord/hold mode)
      (and sets-submode (> sets-submode 0) (= afterup-sets-submode 0))
      (str "hold for " (get submode-names sets-submode (str "submode " sets-submode)))

      ;; Entering a oneshot submode (submode > 0)
      (and sets-submode (> sets-submode 0))
      (str "enter " (get submode-names sets-submode (str "submode " sets-submode)))

      ;; Return-to set (entering label mode)
      (and sets-return (= sets-return 0))
      "enter Label (return to Normal)"

      (and sets-return (= sets-return 1))
      "enter Label (return to Ins)"

      ;; vk_none = block/disable key
      is-vk-none
      "blocked"

      ;; Just a key output
      first-output
      (convert-modifiers (name first-output))

      ;; Shell command - try to describe what it does
      shell-cmd
      (cond
        (str/includes? (str shell-cmd) "osascript")
        (cond
          (str/includes? block-des "Term") "type git cmd"
          (str/includes? (str shell-cmd) "Chrome") "chrome action"
          :else "osascript")
        (str/includes? (str shell-cmd) "cleanup-external-state") "cleanup state"
        (str/includes? (str shell-cmd) "warpd") "warpd"
        (str/includes? (str shell-cmd) "hs -c") "hammerspoon"
        (= shell-cmd ":") "noop"
        :else "shell")

      ;; Default
      :else "action")))

;; === Description generation ===

(defn generate-description [from-map action rule block-des]
  "Generate a human-readable description of what the rule does"
  (let [key-desc (format-key from-map)
        action-desc (analyze-action action rule block-des)]
    (str key-desc " → " action-desc)))

;; === Build replacement map ===

(defn build-replacement-map [config]
  "Build map of old-id -> new-id"
  (into {}
    (for [block (:main config)
          :let [block-des (:des block)
                rules-vec (:rules block)
                block-conditions (extract-block-conditions rules-vec)
                actual-rules (if block-conditions
                               (drop (count block-conditions) rules-vec)
                               rules-vec)]
          rule actual-rules
          :let [from (first rule)
                action (second rule)
                old-id (when (map? from) (:id from))]
          :when old-id
          :let [;; Extract rule number
                rule-num (second (re-find #"^(R\d+[a-z]?)" old-id))

                ;; Get conditions
                rule-conds (extract-rule-conditions rule)
                all-conds (concat (filter vector? block-conditions) rule-conds)

                ;; Get actual values
                profile (get-profile-from-block block-conditions)
                layer (get-condition-value all-conds "dsk_layer")
                submode (get-condition-value all-conds "dsk_ins_sub_mode")
                return-to (get-condition-value all-conds "dsk_return_to_layer")

                ;; Generate state string
                state-string (generate-state-string profile layer submode return-to)

                ;; Generate description
                description (generate-description from action rule block-des)

                ;; Build new ID
                new-id (str rule-num " [" state-string "] " description)]]
      [old-id new-id])))

;; === String replacement ===

(defn replace-ids [content replacements]
  "Replace rule IDs in the file content"
  (reduce
   (fn [text [old-id new-id]]
     (str/replace text
                  (str ":id \"" old-id "\"")
                  (str ":id \"" new-id "\"")))
   content
   replacements))

;; === Main ===

(defn -main [& args]
  (let [[input-file output-file] args]
    (when (or (nil? input-file) (nil? output-file))
      (println "Usage: bb rename-rules.bb <input-edn> <output-edn>")
      (System/exit 1))

    (println "Parsing EDN...")
    (let [config (edn/read-string (slurp input-file))
          replacements (build-replacement-map config)]

      (println (str "Found " (count replacements) " rule IDs to update"))

      ;; Show some examples
      (println "\nSample replacements:")
      (doseq [[old-id new-id] (take 20 (sort-by first replacements))]
        (println (str "  " old-id))
        (println (str "  → " new-id))
        (println))

      ;; Read original file as string and do replacements
      (println "Applying replacements...")
      (let [original-content (slurp input-file)
            updated-content (replace-ids original-content replacements)]

        (spit output-file updated-content)
        (println (str "Written to " output-file))
        (println "Done!")))))

(apply -main *command-line-args*)
