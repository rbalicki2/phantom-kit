;; State Library for Karabiner Config
;;
;; Single canonical source for:
;; 1. State space definition (valid layers, submodes, return-to values)
;; 2. Parsing state strings into state maps
;; 3. Parsing conditions (from rule condition arrays)
;; 4. Parsing transitions (from action arrays)
;; 5. Validating states, conditions, and transitions
;;
;; All validation scripts should use this library.

(ns state
  (:require [clojure.string :as str]))

;; ============================================================================
;; State Space Definition
;; ============================================================================

(def all-layers
  "All valid dsk_layer values"
  #{0 1 2 3 4 5 6 8 9 10 11 12 13 28 29 30})

(def layer-names
  "Human-readable names for layers"
  {0 "Normal"
   1 "Ins"
   2 "Nav"
   3 "Chrome"
   4 "VSCode"
   5 "TMUX"
   6 "Comma"
   8 "Term"
   9 "Admin"
   10 "InApp"
   11 "AppSwitcher"
   12 "WindowSwitcher"
   13 "Label"
   28 "Grid"
   29 "L-Entry"
   30 "L-Exec"})

(def submode-layers
  "Layers where dsk_ins_sub_mode is meaningful (not -1)"
  #{1})

(def valid-submodes
  "Valid dsk_ins_sub_mode values when in submode-layers"
  #{0 1 2 3 4})

(def return-to-layers
  "Layers where dsk_return_to_layer MUST be 0 or 1 (not -1).
   Only Label mode (13) - Grid mode (28) always returns to Normal so doesn't use return-to."
  #{13})

(def valid-return-to
  "Valid dsk_return_to_layer values when in return-to-layers (NOT -1)"
  #{0 1})

(def devices
  "Valid device conditions"
  #{:apple_internal :!apple_internal})

(def profiles
  "Valid profile names"
  #{"Default" "None"})

(def all-apps
  "All application conditions used in the config"
  #{:Chrome :VSCode :iTerm})

(def app-layers
  "Layers where app conditions are meaningful (app-specific rules exist)"
  #{0 10})

;; ============================================================================
;; State Parsing - From Strings
;; ============================================================================

(defn parse-state-string
  "Parse a state string like 'profile=Default:device=Desktop:dsk_layer=13:dsk_return_to_layer=1'
   into a state map {:profile 'Default' :device :!apple_internal :layer 13 :submode nil :return-to 1}"
  [s]
  (when (and s (string? s) (not (str/blank? s)))
    (let [parts (str/split s #":")
          pairs (map #(str/split % #"=" 2) parts)]
      (reduce
        (fn [state [k v]]
          (case k
            "profile" (assoc state :profile v)
            "device" (assoc state :device (if (= v "Desktop") :!apple_internal :apple_internal))
            "dsk_layer" (assoc state :layer (parse-long v))
            "dsk_ins_sub_mode" (assoc state :submode (parse-long v))
            "dsk_return_to_layer" (assoc state :return-to (parse-long v))
            state))
        {}
        pairs))))

;; ============================================================================
;; State Parsing - From Condition Arrays
;; ============================================================================

(defn parse-condition-array
  "Parse a condition array like [['dsk_layer' 13] ['dsk_return_to_layer' 1]]
   into a state map {:layer 13 :return-to 1}"
  [cond-arr]
  (when (vector? cond-arr)
    (reduce
      (fn [state item]
        (if (and (vector? item) (= 2 (count item)) (string? (first item)))
          (let [[var-name value] item]
            (case var-name
              "dsk_layer" (assoc state :layer value)
              "dsk_ins_sub_mode" (assoc state :submode value)
              "dsk_return_to_layer" (assoc state :return-to value)
              state))
          state))
      {}
      cond-arr)))

(defn parse-block-conditions
  "Parse block-level keywords into device/profile.
   Returns {:device :!apple_internal} or similar."
  [keywords]
  (reduce
    (fn [state kw]
      (cond
        (= kw :!apple_internal) (assoc state :device :!apple_internal)
        (= kw :apple_internal) (assoc state :device :apple_internal)
        (= kw :Desktop) (assoc state :profile "Desktop")
        (= kw :None) (assoc state :profile "None")
        :else state))
    {}
    keywords))

;; ============================================================================
;; State Parsing - From Action Arrays (Transitions)
;; ============================================================================

(defn parse-transition
  "Parse an action array to extract the target state.
   Action arrays contain variable sets like ['dsk_layer' 0].
   Returns {:layer 0 :submode -1 :return-to -1} or partial state."
  [action]
  (when (vector? action)
    (reduce
      (fn [state item]
        (if (and (vector? item) (= 2 (count item)) (string? (first item)))
          (let [[var-name value] item]
            (case var-name
              "dsk_layer" (assoc state :layer value)
              "dsk_ins_sub_mode" (assoc state :submode value)
              "dsk_return_to_layer" (assoc state :return-to value)
              state))
          state))
      {}
      action)))

;; ============================================================================
;; State Validation
;; ============================================================================

(defn valid-submode-for-layer?
  "Check if submode value is valid for the given layer."
  [layer submode]
  (if (submode-layers layer)
    (valid-submodes submode)
    (= submode -1)))

(defn valid-return-to-for-layer?
  "Check if return-to value is valid for the given layer.
   In mouse modes (13, 28), return-to MUST be 0 or 1.
   In other modes, return-to MUST be -1."
  [layer return-to]
  (if (return-to-layers layer)
    (valid-return-to return-to)  ;; Must be 0 or 1, NOT -1
    (= return-to -1)))

(defn validate-state
  "Validate a complete state map.
   Returns nil if valid, or an error map {:type :message} if invalid."
  [{:keys [layer submode return-to]}]
  (cond
    ;; Layer must be valid
    (and layer (not (all-layers layer)))
    {:type :invalid-layer
     :message (format "Invalid layer %d" layer)}

    ;; Submode validation
    (and layer submode (not (valid-submode-for-layer? layer submode)))
    {:type :invalid-submode
     :message (format "Layer %d (%s) cannot have dsk_ins_sub_mode=%d"
                      layer (get layer-names layer "?") submode)}

    ;; Return-to validation
    (and layer return-to (not (valid-return-to-for-layer? layer return-to)))
    {:type :invalid-return-to
     :message (format "Layer %d (%s) cannot have dsk_return_to_layer=%d"
                      layer (get layer-names layer "?") return-to)}

    :else nil))

(defn validate-condition
  "Validate a condition state map (partial state from rule conditions).
   Checks that leaf conditions (submode, return-to) have required parent (layer).
   Returns nil if valid, or an error map if invalid."
  [{:keys [layer submode return-to] :as state}]
  (cond
    ;; Submode condition requires layer 1
    (and submode (not layer))
    {:type :submode-without-layer
     :message "dsk_ins_sub_mode condition requires dsk_layer condition"}

    (and submode layer (not (submode-layers layer)))
    {:type :submode-wrong-layer
     :message (format "dsk_ins_sub_mode condition requires layer 1, got %d" layer)}

    ;; Return-to condition requires mouse layer
    (and return-to (not layer))
    {:type :return-to-without-layer
     :message "dsk_return_to_layer condition requires dsk_layer condition"}

    (and return-to layer (not (return-to-layers layer)))
    {:type :return-to-wrong-layer
     :message (format "dsk_return_to_layer condition requires layer 13 or 28, got %d" layer)}

    ;; Also validate the state itself
    :else (validate-state state)))

(defn validate-transition
  "Validate a transition (target state from action).
   Returns nil if valid, or an error map if invalid."
  [target-state]
  (validate-state target-state))

;; ============================================================================
;; Completeness Checks
;; ============================================================================

(defn state-complete?
  "Check if a state has all three variable values set."
  [{:keys [layer submode return-to]}]
  (and (some? layer) (some? submode) (some? return-to)))

(defn transition-complete?
  "Check if a transition sets all three state variables."
  [transition]
  (state-complete? transition))

;; ============================================================================
;; State Enumeration
;; ============================================================================

(defn all-valid-states
  "Returns a sequence of all valid complete states in canonical order.
   Each state is {:profile :device :layer :submode :return-to}.

   Canonical ordering (root to leaf):
   1. None profile (placeholder, no real state)
   2. Default + Laptop (no layer variables apply)
   3. Default + Desktop + each layer with valid submode/return-to

   State rules:
   - Layer 1 (Ins): submode must be 0-4, return-to=-1
   - Layers 13/28 (mouse): submode=-1, return-to must be 0 or 1
   - Other layers: submode=-1, return-to=-1"
  []
  (concat
    ;; 1. None profile (placeholder)
    [{:profile "None" :device nil :layer nil :submode nil :return-to nil}]

    ;; 2. Default + Laptop
    [{:profile "Default" :device :laptop :layer nil :submode nil :return-to nil}]

    ;; 3. Default + Desktop + each valid layer state
    (for [layer (sort all-layers)
          submode (sort (if (submode-layers layer) valid-submodes #{-1}))
          return-to (sort (if (return-to-layers layer) valid-return-to #{-1}))]
      {:profile "Default" :device :desktop :layer layer :submode submode :return-to return-to})))

(defn all-valid-conditions
  "Returns a sequence of all valid partial states (conditions) in leaf-to-root order.
   More specific conditions come first (for rule ordering).

   Ordering (leaf to root):
   1. Desktop + layer + leaf (submode or return-to)
   2. Desktop + layer only
   3. Desktop only (no layer)
   4. Laptop only
   5. None profile"
  []
  (concat
    ;; Most specific: Desktop + layer + leaf
    (for [layer (sort all-layers)
          :when (submode-layers layer)
          submode (sort valid-submodes)]
      {:profile "Default" :device :desktop :layer layer :submode submode :return-to nil})

    (for [layer (sort all-layers)
          :when (return-to-layers layer)
          return-to (sort valid-return-to)]
      {:profile "Default" :device :desktop :layer layer :submode nil :return-to return-to})

    ;; Medium: Desktop + layer
    (for [layer (sort all-layers)]
      {:profile "Default" :device :desktop :layer layer :submode nil :return-to nil})

    ;; Less specific: Desktop only
    [{:profile "Default" :device :desktop :layer nil :submode nil :return-to nil}]

    ;; Laptop
    [{:profile "Default" :device :laptop :layer nil :submode nil :return-to nil}]

    ;; None profile
    [{:profile "None" :device nil :layer nil :submode nil :return-to nil}]))

(defn all-condition-states-for-grouping
  "Returns all condition states in order for rule grouping.
   This includes catch-all states and is ordered for proper rule file organization.

   Ordering:
   1. None (placeholder)
   2. Default (catch-all, no device)
   3. Default + Laptop
   4. Default + Desktop + layers in numerical order, where each layer includes:
      - If layer has apps: each app first, then layer catch-all
      - If layer has submodes: submode 0, 1, 2, 3, 4, then layer catch-all
      - If layer has return-to: return-to 0, 1, then layer catch-all
      - Otherwise: just the layer
   5. Default + Desktop (catch-all, no layer) - last"
  []
  (concat
    ;; 1. None profile
    [{:profile "None" :device nil :layer nil :submode nil :return-to nil :app nil}]

    ;; 2. Default catch-all (no device)
    [{:profile "Default" :device nil :layer nil :submode nil :return-to nil :app nil}]

    ;; 3. Default + Laptop
    [{:profile "Default" :device :laptop :layer nil :submode nil :return-to nil :app nil}]

    ;; 4. Default + Desktop + layers
    (mapcat
      (fn [layer]
        (cond
          ;; Layer 1: submodes 0-4, then catch-all
          (submode-layers layer)
          (concat
            (for [submode (sort valid-submodes)]
              {:profile "Default" :device :desktop :layer layer :submode submode :return-to nil :app nil})
            [{:profile "Default" :device :desktop :layer layer :submode nil :return-to nil :app nil}])

          ;; Layer 13: return-to 0, 1, then catch-all
          (return-to-layers layer)
          (concat
            (for [return-to (sort valid-return-to)]
              {:profile "Default" :device :desktop :layer layer :submode nil :return-to return-to :app nil})
            [{:profile "Default" :device :desktop :layer layer :submode nil :return-to nil :app nil}])

          ;; Layers 0, 10: app-specific rules first, then catch-all
          (app-layers layer)
          (concat
            (for [app (sort-by name all-apps)]
              {:profile "Default" :device :desktop :layer layer :submode nil :return-to nil :app app})
            [{:profile "Default" :device :desktop :layer layer :submode nil :return-to nil :app nil}])

          ;; Other layers: just the layer
          :else
          [{:profile "Default" :device :desktop :layer layer :submode nil :return-to nil :app nil}]))
      (sort all-layers))

    ;; 5. Default + Desktop catch-all (no layer)
    [{:profile "Default" :device :desktop :layer nil :submode nil :return-to nil :app nil}]))

(defn condition-state-to-des
  "Convert a condition state to a :des string for rule blocks.
   Format: 'Profile:Device [conditions]' or just 'Profile' for None."
  [{:keys [profile device layer submode return-to app]}]
  (let [prefix (cond
                 (= profile "None") "None"
                 (nil? device) "Default"
                 (= device :laptop) "Laptop"
                 :else "Desktop")
        conditions (cond-> []
                     layer (conj ["dsk_layer" layer])
                     submode (conj ["dsk_ins_sub_mode" submode])
                     return-to (conj ["dsk_return_to_layer" return-to]))
        ;; Add app suffix if present
        app-suffix (when app (str " " (name app)))]
    (if (and (= profile "None") (empty? conditions) (nil? app))
      "None"
      (str prefix " " (pr-str conditions) app-suffix))))

(defn condition-state-to-block-keywords
  "Convert a condition state to block-level keywords for :rules.
   Returns a vector like [:!apple_internal :Chrome] or [:None].
   Note: Default profile doesn't need :Desktop keyword, only None profile needs :None."
  [{:keys [profile device app]}]
  (cond
    (= profile "None") [:None]
    (nil? device) []  ;; Default catch-all (no device specified)
    (= device :laptop) [:apple_internal]
    :else (if app
            [:!apple_internal app]
            [:!apple_internal])))

(defn rule-condition-matches-state?
  "Check if a rule's condition matches a condition state.
   The rule condition is the partial state from the rule's condition array.
   Returns true if the rule belongs to this state (most specific match)."
  [rule-cond state]
  (let [{:keys [layer submode return-to]} rule-cond
        state-layer (:layer state)
        state-submode (:submode state)
        state-return-to (:return-to state)]
    ;; Exact match on all specified fields
    (and (= layer state-layer)
         (= submode state-submode)
         (= return-to state-return-to))))
