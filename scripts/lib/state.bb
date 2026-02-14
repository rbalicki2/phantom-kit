;; Shared state parsing and validation library
;;
;; This library handles parsing and validating Karabiner state specifications.
;; It enforces the DAG constraints:
;;   - Profile must be Desktop or Laptop (or nil for global)
;;   - Layer requires a profile
;;   - submode requires layer=1
;;   - return-to requires layer=13
;;   - in-modal = (layer >= 2)

(ns lib.state
  (:require [clojure.string :as str]))

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
  "Parse state string into canonical map. Returns map or throws on invalid input.

  State format (all parts are key=value, colon-separated):
    ''                                   -> {:profile nil :layer nil ...}
    'profile=Desktop'                    -> {:profile 'Desktop' :layer nil ...}
    'profile=Desktop:layer=1'            -> {:profile 'Desktop' :layer 1 ...}
    'profile=Desktop:layer=1:submode=1'  -> {:profile 'Desktop' :layer 1 :submode 1 ...}
    'profile=Desktop:layer=13:return=0'  -> {:profile 'Desktop' :layer 13 :return-to 0 ...}

  The canonical form includes:
    :profile   - 'Desktop', 'Laptop', or nil
    :layer     - 0-28 or nil (nil = any layer)
    :in-modal  - true, false, or nil (derived from layer, nil when layer is nil)
    :submode   - -1, 0-4, or nil (-1 when layer != 1, nil = any submode in layer 1)
    :return-to - -1, 0, 1, or nil (-1 when layer != 13, nil = any return in layer 13)"
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
        (throw (ex-info (str "Invalid profile '" profile "'. Must be 'Desktop' or 'Laptop'.") {:profile profile})))

      ;; Validate layer requires profile
      (when (and layer-str (not profile))
        (throw (ex-info "layer requires profile to be set first" {:layer layer-str})))

      ;; Validate layer value
      (when (and layer-str (nil? layer))
        (throw (ex-info (str "Invalid layer '" layer-str "'. Must be a number.") {:layer layer-str})))

      (when (and layer (not (contains? layer-names layer)))
        (throw (ex-info (str "Unknown layer " layer ". Valid: 0-28") {:layer layer})))

      ;; DAG constraint validation
      (when (and submode-val (or (nil? layer) (not= layer 1)))
        (throw (ex-info "submode requires layer=1" {:layer layer :submode submode-val})))

      (when (and return-val (or (nil? layer) (not= layer 13)))
        (throw (ex-info "return requires layer=13" {:layer layer :return return-val})))

      (when (and submode-val (not (<= 0 submode-val 4)))
        (throw (ex-info "submode must be 0-4" {:submode submode-val})))

      (when (and return-val (not (#{0 1} return-val)))
        (throw (ex-info "return must be 0 or 1" {:return return-val})))

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
                    :else nil)}))))

(defn format-state
  "Format state map for human-readable output"
  [state]
  (if (nil? (:profile state))
    "Global (no profile)"
    (str (:profile state)
         (when (:layer state)
           (str " > " (get layer-names (:layer state) (str "Layer " (:layer state)))))
         (when (and (= 1 (:layer state)) (:submode state) (pos? (:submode state)))
           (str " > " (get submode-names (:submode state))))
         (when (and (= 13 (:layer state)) (:return-to state) (not (neg? (:return-to state))))
           (str " > return to " (if (= 0 (:return-to state)) "Normal" "Ins"))))))

(defn state->conditions
  "Convert state map to list of Karabiner conditions.
  Returns conditions that should be checked, not derived ones."
  [state]
  (cond-> []
    (:layer state) (conj ["dsk_layer" (:layer state)])
    (and (= 1 (:layer state)) (:submode state)) (conj ["dsk_ins_sub_mode" (:submode state)])
    (and (= 13 (:layer state)) (:return-to state)) (conj ["dsk_return_to_layer" (:return-to state)])))

(defn validate-rule-conditions
  "Validate that a rule's conditions don't violate DAG constraints.
  Returns list of violations or empty list if valid."
  [conditions]
  (let [get-val (fn [var-name]
                  (some (fn [c]
                          (when (and (vector? c) (= (first c) var-name))
                            (second c)))
                        conditions))
        layer (get-val "dsk_layer")
        in-modal (get-val "dsk_in_modal_layer")
        submode (get-val "dsk_ins_sub_mode")
        return-to (get-val "dsk_return_to_layer")]
    (cond-> []
      ;; in-modal should match layer
      (and layer in-modal (not= in-modal (if (>= layer 2) 1 0)))
      (conj {:violation :in-modal-mismatch
             :message (str "dsk_in_modal_layer=" in-modal " but dsk_layer=" layer)})

      ;; submode only valid in layer 1
      (and submode (not= -1 submode) layer (not= layer 1))
      (conj {:violation :submode-wrong-layer
             :message (str "dsk_ins_sub_mode=" submode " but dsk_layer=" layer " (should be 1)")})

      ;; return-to only valid in layer 13
      (and return-to (not= -1 return-to) layer (not= layer 13))
      (conj {:violation :return-to-wrong-layer
             :message (str "dsk_return_to_layer=" return-to " but dsk_layer=" layer " (should be 13)")}))))
