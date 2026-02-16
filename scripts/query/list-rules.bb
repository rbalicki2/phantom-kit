#!/usr/bin/env bb

;; List rules from karabiner.edn that match a given state
;;
;; Usage:
;;   bb list-rules.bb <edn-file> <state> [--format FORMAT] [--exact]
;;
;; State format (same as match-rules.bb):
;;   ""                                              - Global rules (no profile)
;;   "device=Desktop"                                - Desktop device, any layer
;;   "device=Desktop:layer=0"                        - Desktop, layer 0 (Normal)
;;   "device=Desktop:layer=1"                        - Desktop, layer 1 (Ins)
;;   "device=Desktop:layer=1:submode=1"              - Layer 1, mirror mode
;;   "profile=Default:device=Desktop:layer=1"       - Full state specification
;;
;; Flags:
;;   --exact   Only rules defined at exactly this state level (no inheritance)
;;             Without this flag, returns all rules that WOULD APPLY at this state
;;
;; Formats: edn (default), ids, summary
;;
;; Examples:
;;   bb list-rules.bb src/karabiner.edn "device=Desktop:layer=1:submode=1"
;;   bb list-rules.bb src/karabiner.edn "device=Desktop:layer=1" --format ids
;;   bb list-rules.bb src/karabiner.edn "layer=1" --exact

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; Load shared state library
(def script-dir (-> (System/getProperty "babashka.file")
                    (java.io.File.)
                    (.getParentFile)
                    (.getAbsolutePath)))
(load-file (str script-dir "/../lib/state.bb"))

;; Use layer names from state library
(def layer-names state/layer-names)

(def submode-names
  {-1 "N/A" 0 "base" 1 "mirror" 2 "shift_oneshot"
   3 "rcmd_h_chord" 4 "rcmd_n_chord"})

(defn parse-state
  "Parse state string using shared library, return map with :profile :layer :submode :return-to"
  [state-str]
  (if (or (nil? state-str) (empty? state-str))
    {:profile nil :layer nil :in-modal nil :submode nil :return-to nil}
    (let [parsed (state/parse-state-string state-str)]
      {:profile (:profile parsed)
       :layer (:layer parsed)
       :in-modal (when (:layer parsed) (>= (:layer parsed) 2))
       :submode (:submode parsed)
       :return-to (:return-to parsed)})))

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
      (println "State format (same as match-rules.bb):")
      (println "  \"\"                                    - Global rules")
      (println "  \"layer=1\"                             - Ins mode (any device)")
      (println "  \"device=Desktop:layer=1\"              - Desktop, Ins mode")
      (println "  \"device=Desktop:layer=1:submode=1\"    - Mirror mode")
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
