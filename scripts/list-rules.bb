#!/usr/bin/env bb

;; List rules from karabiner.edn that match a given state
;;
;; Usage:
;;   bb list-rules.bb <edn-file> <state> [--format FORMAT]
;;
;; State format (key=value pairs, colon-separated, DAG-validated):
;;   ""                                    - Global rules (no profile)
;;   "profile=Desktop"                     - Desktop profile, any layer
;;   "profile=Desktop:layer=0"             - Desktop, layer 0 (Normal)
;;   "profile=Desktop:layer=1"             - Desktop, layer 1 (Ins)
;;   "profile=Desktop:layer=1:submode=1"   - Layer 1, shift_mirror_oneshot
;;   "profile=Desktop:layer=13:return=0"   - Layer 13, return to Normal
;;
;; Formats: edn (default), ids, summary
;;
;; Examples:
;;   bb list-rules.bb src/karabiner.edn "profile=Desktop:layer=1:submode=1"
;;   bb list-rules.bb src/karabiner.edn "profile=Desktop:layer=1" --format ids

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

(defn rule-matches-state? [rule block-conditions state]
  (let [rule-conds (extract-rule-conditions rule)
        all-conds (concat (filter vector? block-conditions) rule-conds)
        rule-layer (get-condition-value all-conds "dsk_layer")
        rule-submode (get-condition-value all-conds "dsk_ins_sub_mode")
        rule-return (get-condition-value all-conds "dsk_return_to_layer")
        state-layer (:layer state)
        state-submode (:submode state)
        state-return (:return-to state)]

    (and
     ;; Layer matching
     (cond
       (nil? state-layer) true
       :else (or (= rule-layer state-layer)
                 (and (= state-layer 1) rule-submode (not= rule-submode -1) (nil? rule-layer))
                 (and (= state-layer 13) rule-return (not= rule-return -1) (nil? rule-layer))))

     ;; Submode matching
     (cond
       (nil? state-submode) true
       (= -1 state-submode) true
       :else (= rule-submode state-submode))

     ;; Return-to matching
     (cond
       (nil? state-return) true
       (= -1 state-return) true
       :else (= rule-return state-return)))))

(defn extract-matching-rules [config state]
  (for [block (:main config)
        :let [des (:des block)
              rules-vec (:rules block)
              block-conds (extract-block-conditions rules-vec)
              actual-rules (if block-conds
                            (drop (count block-conds) rules-vec)
                            rules-vec)]
        :when (block-matches-profile? block-conds state)
        :let [matching (filter #(rule-matches-state? % block-conds state) actual-rules)]
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

(defn -main [& args]
  (let [[file state-str & rest-args] args
        format-arg (when (= "--format" (first rest-args)) (second rest-args))
        output-format (or format-arg "edn")]

    (when (or (nil? file) (nil? state-str))
      (println "Usage: bb list-rules.bb <edn-file> <state> [--format FORMAT]")
      (println "")
      (println "State format (key=value pairs, colon-separated):")
      (println "  \"\"                                    - Global rules")
      (println "  \"profile=Desktop\"                     - Desktop profile")
      (println "  \"profile=Desktop:layer=1\"             - Ins mode")
      (println "  \"profile=Desktop:layer=1:submode=1\"   - shift_mirror_oneshot")
      (println "")
      (println "Formats: edn (default), ids, summary")
      (System/exit 1))

    (try
      (let [state (parse-state state-str)
            config (edn/read-string (slurp file))
            matches (extract-matching-rules config state)]

        (println (str "; State: " (format-state state)))
        (println (str "; Parsed: " (pr-str state)))
        (println (str "; Blocks: " (count matches)
                     ", Rules: " (reduce + (map #(count (:rules %)) matches))))
        (println)

        (case output-format
          "ids" (format-ids matches)
          "summary" (format-summary matches)
          (format-edn matches)))

      (catch Exception e
        (println (str "Error: " (.getMessage e)))
        (System/exit 1)))))

(apply -main *command-line-args*)
