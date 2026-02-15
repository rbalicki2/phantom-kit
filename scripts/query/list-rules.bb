#!/usr/bin/env bb

;; List rules from karabiner.edn that match a given state
;;
;; Usage:
;;   bb list-rules.bb <edn-file> <state> [--format FORMAT] [--exact]
;;
;; State format (key=value pairs, colon-separated, DAG-validated):
;;   ""                                    - Global rules (no profile)
;;   "profile=Desktop"                     - Desktop profile, any layer
;;   "profile=Desktop:layer=0"             - Desktop, layer 0 (Normal)
;;   "profile=Desktop:layer=1"             - Desktop, layer 1 (Ins)
;;   "profile=Desktop:layer=1:submode=1"   - Layer 1, shift_mirror_oneshot
;;   "profile=Desktop:layer=13:return=0"   - Layer 13, return to Normal
;;
;; Flags:
;;   --exact   Only rules defined at exactly this state level (no inheritance)
;;             Without this flag, returns all rules that WOULD APPLY at this state
;;
;; Formats: edn (default), ids, summary
;;
;; Examples:
;;   bb list-rules.bb src/karabiner.edn "profile=Desktop:layer=1:submode=1"
;;   bb list-rules.bb src/karabiner.edn "profile=Desktop:layer=1" --format ids
;;   bb list-rules.bb src/karabiner.edn "profile=Desktop:layer=1" --exact
;;   bb list-rules.bb src/karabiner.edn "profile=Desktop" --exact  # Only profile-level rules

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; === State parsing (from lib/state.bb) ===

(def layer-names
  {0 "Normal" 1 "Ins" 2 "Nav" 3 "Chrome" 4 "VSCode" 5 "TMUX"
   6 "Comma" 7 "L" 8 "Term" 9 "Admin" 10 "InApp" 11 "AppSwitcher"
   12 "WindowSwitcher" 13 "Label" 14 "L-Cmd" 15 "L-Cmd-Shift"
   16 "L-Ctrl" 17 "L-Ctrl-Shift" 18 "L-CtrlCmd" 19 "L-CtrlCmd-Shift"
   20 "L-CtrlAlt" 21 "L-CtrlAlt-Shift" 22 "L-Alt" 23 "L-Alt-Shift"
   24 "L-AltCmd" 25 "L-AltCmd-Shift" 26 "L-Hyper" 27 "L-Hyper-Shift"
   28 "Grid"})

(def submode-names
  {-1 "N/A" 0 "base" 1 "shift_mirror_oneshot" 2 "shift_oneshot"
   3 "rcmd_h_chord" 4 "rcmd_n_chord"})

(defn parse-state
  "Parse state string into canonical map."
  [state-str]
  (if (or (nil? state-str) (empty? state-str))
    {:profile nil :layer nil :in-modal nil :submode nil :return-to nil}
    (let [parts (str/split state-str #":")
          params (into {} (for [p parts
                                :let [[k v] (str/split p #"=" 2)]]
                            [(keyword k) v]))
          profile (:profile params)
          layer-str (:layer params)
          layer (when layer-str (parse-long layer-str))
          submode-str (:submode params)
          submode-val (when submode-str (parse-long submode-str))
          return-str (:return params)
          return-val (when return-str (parse-long return-str))]

      ;; Validate profile
      (when (and profile (not (#{"Desktop" "Laptop"} profile)))
        (throw (ex-info (str "Invalid profile '" profile "'") {:profile profile})))

      ;; Validate layer requires profile
      (when (and layer-str (not profile))
        (throw (ex-info "layer requires profile" {:layer layer-str})))

      ;; Validate layer value
      (when (and layer-str (nil? layer))
        (throw (ex-info (str "Invalid layer '" layer-str "'") {:layer layer-str})))

      (when (and layer (not (contains? layer-names layer)))
        (throw (ex-info (str "Unknown layer " layer) {:layer layer})))

      ;; DAG constraints
      (when (and submode-val (or (nil? layer) (not= layer 1)))
        (throw (ex-info "submode requires layer=1" {:layer layer :submode submode-val})))

      (when (and return-val (or (nil? layer) (not= layer 13)))
        (throw (ex-info "return requires layer=13" {:layer layer :return return-val})))

      {:profile profile
       :layer layer
       :in-modal (when layer (>= layer 2))
       :submode (cond
                  (nil? layer) nil
                  (not= layer 1) -1
                  submode-val submode-val
                  :else nil)
       :return-to (cond
                    (nil? layer) nil
                    (not= layer 13) -1
                    return-val return-val
                    :else nil)})))

(defn format-state [state]
  (if (nil? (:profile state))
    "Global (no profile)"
    (str (:profile state)
         (when (:layer state)
           (str " > " (get layer-names (:layer state) (str "Layer " (:layer state)))))
         (when (and (= 1 (:layer state)) (:submode state) (pos? (:submode state)))
           (str " > " (get submode-names (:submode state))))
         (when (and (= 13 (:layer state)) (:return-to state) (not (neg? (:return-to state))))
           (str " > return to " (if (= 0 (:return-to state)) "Normal" "Ins"))))))

;; === Rule extraction ===

(defn parse-id-state-marker
  "Extract state from rule ID marker. Supports both formats:
   Old: '[layer:1]' or '[ins_sub_mode:1]' (colon-separated)
   New: '[dsk_layer=1]' or '[profile=Desktop:dsk_layer=1]' (equals-separated)
   Returns map with :layer, :submode, :return keys (nil if not present).
   Infers layer=1 if submode is present but layer is not."
  [rule-id]
  (when rule-id
    (let [;; Match the first [...] after the rule number
          marker (second (re-find #"R\d+[a-z]?\s+\[([^\]]+)\]" rule-id))]
      (when marker
        (let [;; Detect format: new uses "=" for key-value, old uses ":"
              is-new-format (str/includes? marker "=")
              parsed (if is-new-format
                       ;; New format: "profile=Desktop:dsk_layer=1"
                       (into {} (for [p (str/split marker #":")
                                      :let [[k v] (str/split p #"=")
                                            v-num (when v (parse-long v))]]
                                  [(keyword k) (or v-num v)]))
                       ;; Old format: "layer:1 ins_sub_mode:2"
                       (into {} (for [p (str/split marker #"\s+")
                                      :let [[k v] (str/split p #":")
                                            v-num (when v (parse-long v))]]
                                  [(keyword k) v-num])))
              ;; Extract values (handle both old and new key names)
              layer (or (:dsk_layer parsed) (:layer parsed))
              submode (or (:dsk_ins_sub_mode parsed) (:ins_sub_mode parsed))
              return-to (or (:dsk_return_to_layer parsed) (:return_to_layer parsed))
              ;; Infer layer=1 if submode is present
              layer (or layer (when submode 1))]
          {:layer layer
           :submode submode
           :return return-to})))))

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

(defn block-matches-profile? [block-conditions state]
  (let [has-desktop (some #(= % :Desktop) block-conditions)
        has-laptop (some #(= % :Laptop) block-conditions)
        profile (:profile state)]
    (cond
      (nil? profile) (and (not has-desktop) (not has-laptop))
      (= "Desktop" profile) (or has-desktop (not (or has-desktop has-laptop)))
      (= "Laptop" profile) (or has-laptop (not (or has-desktop has-laptop)))
      :else false)))

(defn rule-matches-state?
  "Check if rule matches state. When exact? is true, only match rules at exactly
   that state level (based on rule ID marker). When false (default), match all
   rules that would apply (based on actual conditions)."
  [rule block-conditions state exact?]
  (let [rule-conds (extract-rule-conditions rule)
        all-conds (concat (filter vector? block-conditions) rule-conds)
        ;; For hierarchical matching, use actual conditions
        rule-layer (get-condition-value all-conds "dsk_layer")
        rule-submode (get-condition-value all-conds "dsk_ins_sub_mode")
        rule-return (get-condition-value all-conds "dsk_return_to_layer")
        ;; For exact matching, use ID state marker
        rule-id (when (map? (first rule)) (:id (first rule)))
        id-state (parse-id-state-marker rule-id)
        state-layer (:layer state)
        state-submode (:submode state)
        state-return (:return-to state)]

    (if exact?
      ;; Exact matching: use rule ID state marker
      (and
       ;; Layer must match exactly
       (= (:layer id-state) state-layer)
       ;; Submode must match exactly (nil matches nil)
       (= (:submode id-state) state-submode)
       ;; Return-to must match exactly (nil matches nil, but -1 in state means "any")
       (or (= -1 state-return)
           (= (:return id-state) state-return)))

      ;; Hierarchical matching: all rules that would apply at this state
      (and
       ;; Layer matching
       (cond
         (nil? state-layer) true
         :else (or (= rule-layer state-layer)
                   (nil? rule-layer)  ; Global rules apply
                   (and (= state-layer 1) rule-submode (not= rule-submode -1))
                   (and (= state-layer 13) rule-return (not= rule-return -1))))

       ;; Submode matching
       (cond
         (nil? state-submode) true
         (= -1 state-submode) true
         :else (or (= rule-submode state-submode)
                   (nil? rule-submode)))  ; Base layer rules apply

       ;; Return-to matching
       (cond
         (nil? state-return) true
         (= -1 state-return) true
         :else (or (= rule-return state-return)
                   (nil? rule-return)))))))

(defn extract-matching-rules [config state exact?]
  (for [block (:main config)
        :let [des (:des block)
              rules-vec (:rules block)
              block-conds (extract-block-conditions rules-vec)
              actual-rules (if block-conds
                            (drop (count block-conds) rules-vec)
                            rules-vec)]
        :when (block-matches-profile? block-conds state)
        :let [matching (filter #(rule-matches-state? % block-conds state exact?) actual-rules)]
        :when (seq matching)]
    {:des des
     :conditions block-conds
     :rules (vec matching)}))

;; === Output formatting ===

(defn format-edn [matches]
  (doseq [block matches]
    (println (str "{:des \"" (:des block) "\""))
    (println " :rules")
    (print " [")
    (when-let [conds (:conditions block)]
      (doseq [c conds] (prn c))
      (print "  "))
    (doseq [[i rule] (map-indexed vector (:rules block))]
      (when (pos? i) (print "  "))
      (prn rule))
    (println " ]}")
    (println)))

(defn format-ids [matches]
  (doseq [block matches
          rule (:rules block)]
    (when-let [id (when (map? (first rule)) (:id (first rule)))]
      (println id))))

(defn format-summary [matches]
  (doseq [block matches]
    (println (str "Block: " (:des block)))
    (println (str "  Rules: " (count (:rules block))))
    (doseq [rule (:rules block)]
      (let [from (first rule)
            id (when (map? from) (:id from))
            key-name (if (map? from) (:key from) from)]
        (println (str "    " (or id key-name)))))
    (println)))

;; === Main ===

(defn parse-args [args]
  (loop [remaining args
         result {:file nil :state nil :format "edn" :exact false}]
    (if (empty? remaining)
      result
      (let [arg (first remaining)]
        (cond
          (= "--format" arg)
          (recur (drop 2 remaining) (assoc result :format (second remaining)))

          (= "--exact" arg)
          (recur (rest remaining) (assoc result :exact true))

          (nil? (:file result))
          (recur (rest remaining) (assoc result :file arg))

          (nil? (:state result))
          (recur (rest remaining) (assoc result :state arg))

          :else
          (recur (rest remaining) result))))))

(defn -main [& args]
  (let [{:keys [file state format exact]} (parse-args args)]

    (when (or (nil? file) (nil? state))
      (println "Usage: bb list-rules.bb <edn-file> <state> [--format FORMAT] [--exact]")
      (println "")
      (println "State format (key=value pairs, colon-separated):")
      (println "  \"\"                                    - Global rules")
      (println "  \"profile=Desktop\"                     - Desktop profile")
      (println "  \"profile=Desktop:layer=1\"             - Ins mode")
      (println "  \"profile=Desktop:layer=1:submode=1\"   - shift_mirror_oneshot")
      (println "")
      (println "Flags:")
      (println "  --exact   Only rules defined at exactly this state level")
      (println "            Without this flag, returns all rules that would apply")
      (println "")
      (println "Formats: edn (default), ids, summary")
      (System/exit 1))

    (try
      (let [parsed-state (parse-state state)
            config (edn/read-string (slurp file))
            matches (extract-matching-rules config parsed-state exact)]

        (println (str "; State: " (format-state parsed-state)))
        (println (str "; Parsed: " (pr-str parsed-state)))
        (println (str "; Mode: " (if exact "exact" "hierarchical")))
        (println (str "; Blocks: " (count matches)
                     ", Rules: " (reduce + (map #(count (:rules %)) matches))))
        (println)

        (case format
          "ids" (format-ids matches)
          "summary" (format-summary matches)
          (format-edn matches)))

      (catch Exception e
        (println (str "Error: " (.getMessage e)))
        (System/exit 1)))))

(apply -main *command-line-args*)
