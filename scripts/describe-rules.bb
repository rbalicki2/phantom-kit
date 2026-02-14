#!/usr/bin/env bb

;; Generate meaningful human-readable descriptions for rules
;;
;; Usage:
;;   bb describe-rules.bb <input-edn> <output-edn>

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; Load shared key mappings
(load-file "scripts/lib/keys.bb")
(require '[lib.keys :as keys])

;; === Input key formatting ===

(defn format-input-key [from-map]
  "Format the input key with modifiers"
  (if (map? from-map)
    (let [key-val (:key from-map)
          pkey (:pkey from-map)
          modi (:modi from-map)
          mandatory (get modi :mandatory [])
          raw-key (or key-val pkey :unknown)
          key-str (name raw-key)
          ;; Translate the key
          key-name (keys/translate-key raw-key)]
      (if (and (seq mandatory) (not (str/starts-with? key-str "!")))
        ;; Has modifiers that aren't encoded in the key itself
        (let [mod-names (map #(case %
                                :shift "Shift"
                                :right_shift "RShift"
                                :left_shift "LShift"
                                :right_control "RCtrl"
                                :right_command "RCmd"
                                :left_option "LOpt"
                                :control "Ctrl"
                                (name %))
                             mandatory)]
          (str (str/join "+" mod-names) "+" key-name))
        key-name))
    (if (keyword? from-map)
      (keys/translate-key from-map)
      "?")))

;; === Rule condition extraction ===

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

(defn get-profile-and-device [block-conditions]
  (let [is-none (some #(= % :None) block-conditions)
        is-desktop (or (some #(= % :Desktop) block-conditions)
                       (some #(= % :!apple_internal) block-conditions))
        is-laptop (or (some #(= % :Laptop) block-conditions)
                      (some #(= % :apple_internal) block-conditions))]
    {:profile (if is-none "None" "Default")
     :device (cond is-desktop "Desktop" is-laptop "Laptop" :else nil)}))

(defn generate-state-string [profile device layer submode return-to]
  (let [parts (cond-> []
                profile (conj (str "profile=" profile))
                device (conj (str "device=" device))
                layer (conj (str "dsk_layer=" layer))
                (and submode (not= submode -1)) (conj (str "dsk_ins_sub_mode=" submode))
                (and return-to (not= return-to -1)) (conj (str "dsk_return_to_layer=" return-to)))]
    (if (empty? parts) "UNKNOWN" (str/join ":" parts))))

;; === Rule description generation ===

(defn analyze-rule [from action rule block-des layer submode]
  "Analyze a rule and generate a meaningful description"
  (let [action-vec (if (vector? action) action [action])
        sets-layer (some #(when (and (vector? %) (= "dsk_layer" (first %))) (second %)) action-vec)
        sets-submode (some #(when (and (vector? %) (= "dsk_ins_sub_mode" (first %))) (second %)) action-vec)
        sets-return (some #(when (and (vector? %) (= "dsk_return_to_layer" (first %))) (second %)) action-vec)
        first-output (first (filter #(and (keyword? %) (not (str/starts-with? (name %) "type-"))) action-vec))
        typed-string (first (filter string? action-vec))
        shell-cmd (some #(when (map? %) (:shell %)) action-vec)
        afterup (get (nth rule 3 {}) :afterup)
        afterup-sets-submode (when (vector? afterup)
                               (some #(when (and (vector? %) (= "dsk_ins_sub_mode" (first %))) (second %)) afterup))
        input-key (format-input-key from)
        output-name (when first-output (name first-output))
        output-key (when first-output (keys/translate-output first-output))]

    (cond
      ;; Typed string (Term layer) - show what gets typed
      typed-string
      (let [short-str (if (> (count typed-string) 25)
                        (str (subs typed-string 0 22) "...")
                        (str/trim typed-string))]
        (str input-key " types '" short-str "'"))

      ;; Mirror letter in Ins mode (Fn+key → mirrored letter)
      (and (= layer 1)
           (nil? submode)
           first-output
           (not= first-output :vk_none)
           (get keys/mirror-all output-name))
      (let [is-shift (str/starts-with? output-name "!S")
            base-letter (if is-shift (subs output-name 2) output-name)
            display (if is-shift (str/upper-case base-letter) base-letter)]
        (str input-key " → " display " (mirror)"))

      ;; Oneshot clear in shift-mirror-oneshot mode
      (and (= submode 1) (= sets-submode 0) first-output (not= first-output :vk_none))
      (let [is-shift (str/starts-with? output-name "!S")
            base (if is-shift (subs output-name 2) output-name)
            display (if is-shift (str/upper-case base) base)]
        (str input-key " → " display " (oneshot clear)"))

      ;; Oneshot clear in shift-oneshot mode
      (and (= submode 2) (= sets-submode 0) first-output (not= first-output :vk_none))
      (let [is-shift (str/starts-with? output-name "!S")
            display (if is-shift (str "Shift+" (subs output-name 2)) output-name)]
        (str input-key " → " display " (oneshot clear)"))

      ;; Label mode entry with return destination
      (and sets-return (= sets-layer 13))
      (str input-key " → Label mode (return to " (if (= sets-return 0) "Normal" "Ins") ")")

      ;; Layer entry
      (and sets-layer (>= sets-layer 2))
      (str input-key " → " (get keys/layer-names sets-layer (str "layer " sets-layer)))

      ;; Exit to Normal
      (and (= sets-layer 0) (or (nil? first-output) (= first-output :vk_none)))
      (str input-key " → Normal")

      ;; Exit to Ins
      (and (= sets-layer 1) (or (nil? first-output) (= first-output :vk_none)))
      (str input-key " → Ins")

      ;; Chord mode entry (afterup clears submode = held mode)
      (and sets-submode (> sets-submode 0) (= afterup-sets-submode 0))
      (str input-key " (hold) → " (get keys/submode-names sets-submode "chord"))

      ;; Oneshot mode entry
      (and sets-submode (> sets-submode 0))
      (str input-key " → " (get keys/submode-names sets-submode "oneshot"))

      ;; Key blocked
      (= first-output :vk_none)
      (str input-key " blocked")

      ;; Key with layer exit
      (and first-output (= sets-layer 0))
      (str input-key " → " output-key ", Normal")

      (and first-output (= sets-layer 1))
      (str input-key " → " output-key ", Ins")

      ;; Simple key output
      first-output
      (str input-key " → " output-key)

      ;; Shell command
      shell-cmd
      (cond
        (str/includes? (str shell-cmd) "cleanup-external-state") (str input-key " cleans state")
        (str/includes? (str shell-cmd) "warpd") (str input-key " → warpd")
        (str/includes? (str shell-cmd) "hs -c") (str input-key " → hammerspoon")
        (str/includes? (str shell-cmd) "osascript") (str input-key " → osascript")
        (= shell-cmd ":") (str input-key " noop")
        :else (str input-key " → shell"))

      :else (str input-key " → ?"))))

;; === Build replacement map ===

(defn build-replacement-map [config]
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
          :let [rule-num (second (re-find #"^(R\d+[a-z]?)" old-id))
                rule-conds (extract-rule-conditions rule)
                all-conds (concat (filter vector? block-conditions) rule-conds)
                {:keys [profile device]} (get-profile-and-device block-conditions)
                layer (get-condition-value all-conds "dsk_layer")
                submode (get-condition-value all-conds "dsk_ins_sub_mode")
                return-to (get-condition-value all-conds "dsk_return_to_layer")
                state-string (generate-state-string profile device layer submode return-to)
                description (analyze-rule from action rule block-des layer submode)
                new-id (str rule-num " [" state-string "] " description)]]
      [old-id new-id])))

(defn replace-ids [content replacements]
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
      (println "Usage: bb describe-rules.bb <input-edn> <output-edn>")
      (System/exit 1))

    (println "Parsing EDN...")
    (let [config (edn/read-string (slurp input-file))
          replacements (build-replacement-map config)]

      (println (str "Found " (count replacements) " rule IDs to update"))

      (println "\nSample descriptions:")
      (doseq [[old-id new-id] (take 40 (sort-by first replacements))]
        (println (str "  " new-id)))

      (println "\nApplying replacements...")
      (let [original-content (slurp input-file)
            updated-content (replace-ids original-content replacements)]
        (spit output-file updated-content)
        (println (str "Written to " output-file))
        (println "Done!")))))

(apply -main *command-line-args*)
