#!/usr/bin/env bb

;; Parse and validate a Karabiner state string
;;
;; Usage:
;;   bb parse-state.bb <state-string>
;;
;; State format (DAG-validated):
;;   ""                      - Global (no profile)
;;   "Desktop"               - Desktop profile
;;   "Desktop:0"             - Desktop, Normal mode
;;   "Desktop:1"             - Desktop, Ins mode
;;   "Desktop:1:submode=1"   - Desktop, Ins mode, shift_mirror_oneshot
;;   "Desktop:1:submode=2"   - Desktop, Ins mode, shift_oneshot
;;   "Desktop:1:submode=3"   - Desktop, Ins mode, rcmd_h chord
;;   "Desktop:1:submode=4"   - Desktop, Ins mode, rcmd_n chord
;;   "Desktop:13:return=0"   - Desktop, Label mode, return to Normal
;;   "Desktop:13:return=1"   - Desktop, Label mode, return to Ins
;;   "Laptop"                - Laptop profile
;;
;; Validates the DAG constraints:
;;   - Profile must be Desktop or Laptop (or empty for global)
;;   - Layer requires a profile
;;   - submode requires layer=1
;;   - return requires layer=13
;;
;; Returns canonical EDN representation:
;;   {:profile "Desktop" :layer 1 :submode 1 :in-modal true :return-to -1}
;;
;; The canonical form always includes all 4 state variables:
;;   - :layer (0-28)
;;   - :in-modal (boolean, derived from layer >= 2)
;;   - :submode (-1 when layer != 1, otherwise 0-4)
;;   - :return-to (-1 when layer != 13, otherwise 0-1)

(require '[clojure.string :as str])

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

(defn parse-state [state-str]
  "Parse state string, validate DAG, return canonical form"
  (if (or (nil? state-str) (empty? state-str))
    ;; Global state - no specific profile/layer
    {:profile nil :layer nil :in-modal nil :submode nil :return-to nil}

    (let [parts (str/split state-str #":")
          profile (first parts)
          rest-parts (rest parts)]

      ;; Validate profile
      (when-not (#{"Desktop" "Laptop"} profile)
        (binding [*out* *err*]
          (println (str "Error: Invalid profile '" profile "'. Must be 'Desktop' or 'Laptop'.")))
        (System/exit 1))

      (if (empty? rest-parts)
        ;; Profile only - no specific layer
        {:profile profile :layer nil :in-modal nil :submode nil :return-to nil}

        ;; Parse layer
        (let [layer-str (first rest-parts)
              layer (parse-long layer-str)]

          (when-not layer
            (binding [*out* *err*]
              (println (str "Error: Invalid layer '" layer-str "'. Must be a number 0-28.")))
            (System/exit 1))

          (when-not (contains? layer-names layer)
            (binding [*out* *err*]
              (println (str "Error: Unknown layer " layer ". Valid layers: " (keys layer-names))))
            (System/exit 1))

          ;; Parse additional constraints
          (let [constraints (into {} (for [p (rest rest-parts)
                                           :let [[k v] (str/split p #"=")]]
                                       [(keyword k) (parse-long v)]))
                submode-val (:submode constraints)
                return-val (:return constraints)]

            ;; Validate DAG constraints
            (when (and submode-val (not= layer 1))
              (binding [*out* *err*]
                (println "Error: submode only valid when layer=1 (Ins mode)"))
              (System/exit 1))

            (when (and return-val (not= layer 13))
              (binding [*out* *err*]
                (println "Error: return only valid when layer=13 (Label mode)"))
              (System/exit 1))

            (when (and submode-val (not (<= -1 submode-val 4)))
              (binding [*out* *err*]
                (println "Error: submode must be -1 to 4"))
              (System/exit 1))

            (when (and return-val (not (#{-1 0 1} return-val)))
              (binding [*out* *err*]
                (println "Error: return must be -1, 0, or 1"))
              (System/exit 1))

            ;; Build canonical state
            {:profile profile
             :layer layer
             :in-modal (>= layer 2)
             :submode (cond
                        (not= layer 1) -1
                        submode-val submode-val
                        :else nil)  ; nil means "any submode in layer 1"
             :return-to (cond
                          (not= layer 13) -1
                          return-val return-val
                          :else nil)}))))))  ; nil means "any return-to in layer 13"

(defn format-state [state]
  "Format state for human-readable output"
  (if (nil? (:profile state))
    "Global (no profile)"
    (str (:profile state)
         (when (:layer state)
           (str " > " (get layer-names (:layer state) (str "Layer " (:layer state)))))
         (when (and (= 1 (:layer state)) (:submode state) (not= -1 (:submode state)))
           (str " > " (get submode-names (:submode state))))
         (when (and (= 13 (:layer state)) (:return-to state) (not= -1 (:return-to state)))
           (str " > return to " (if (= 0 (:return-to state)) "Normal" "Ins"))))))

(defn -main [& args]
  (when (empty? args)
    (println "Usage: bb parse-state.bb <state-string>")
    (println "")
    (println "State format:")
    (println "  \"\"                      - Global")
    (println "  \"Desktop\"               - Desktop profile")
    (println "  \"Desktop:1\"             - Desktop, Ins mode")
    (println "  \"Desktop:1:submode=1\"   - Desktop, Ins, shift_mirror_oneshot")
    (println "  \"Desktop:13:return=0\"   - Desktop, Label, return to Normal")
    (System/exit 0))

  (let [state-str (first args)
        state (parse-state state-str)]
    (println (str "; Input: \"" state-str "\""))
    (println (str "; Human: " (format-state state)))
    (println)
    (prn state)))

(apply -main *command-line-args*)
