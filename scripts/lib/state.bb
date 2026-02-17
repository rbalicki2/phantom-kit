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
  #{0 1 2 3 4 5 6 7 8 9 10 11 12 13 28 29 30})

(def layer-names
  "Human-readable names for layers"
  {0 "Normal"
   1 "Ins"
   2 "Nav"
   3 "Chrome"
   4 "VSCode"
   5 "TMUX"
   6 "Comma"
   7 "AltIns"
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
  #{1 7})

(def valid-submodes
  "Valid dsk_ins_sub_mode values when in submode-layers.
   0 = normal insert
   1 = shift-mirror oneshot
   2 = shift oneshot
   3 = delete chord
   4 = select chord
   5 = shift-pending (caps lock double-tap detection)
   6 = caps lock mode
   10 = double-tap comma pending (AltIns)
   11 = double-tap semicolon pending (AltIns)
   12 = double-tap bang pending (AltIns)"
  #{0 1 2 3 4 5 6 10 11 12})

(def return-to-layers
  "Layers where dsk_return_to_layer MUST be 0 or 1 (not -1).
   Only Label mode (13) - Grid mode (28) always returns to Normal so doesn't use return-to."
  #{13})

(def valid-return-to
  "Valid dsk_return_to_layer values when in return-to-layers (NOT -1)
   0 = Normal, 1 = Ins, 7 = AltIns"
  #{0 1 7})

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
;; State Type Hierarchy
;; ============================================================================
;;
;; States form a hierarchical ADT with three entry points:
;;
;; CompleteState (for simulation): profile → device → app → layer → leaf
;; FilterState (for conditions):   device → app → layer → leaf
;; TransitionState (for actions):  layer → leaf (submode, return-to)
;;
;; Each type is the same tree structure entered at different levels.
;; Partial states (e.g., just profile+device) match all descendants.

(def state-hierarchy
  "The canonical hierarchy of state dimensions, root to leaf"
  [:profile :device :app :layer :submode :return-to])

(def state-type-roots
  "Which dimension each state type must start with"
  {:complete :profile    ;; Must have profile if anything
   :filter :device       ;; Must have device if anything
   :transition :layer})  ;; Must have layer if anything

(defn state-type
  "Determine the type of a state map based on which fields are present.
   Returns :complete, :filter, :transition, or :empty."
  [{:keys [profile device layer]}]
  (cond
    profile :complete
    device :filter
    layer :transition
    :else :empty))

(defn validate-state-hierarchy
  "Validate that a state respects the hierarchy for its type.
   Child fields should not appear without their parent.
   Returns nil if valid, or error map if invalid."
  [{:keys [profile device app layer submode return-to] :as state}]
  (let [stype (state-type state)]
    (cond
      ;; Complete state: device requires profile
      (and device (not profile) (= stype :filter))
      nil  ;; This is a filter state, valid

      (and device (not profile) (not= stype :filter))
      {:type :hierarchy-violation
       :message "device requires profile in complete state"}

      ;; App requires device
      (and app (not device))
      {:type :hierarchy-violation
       :message "app requires device"}

      ;; Layer requires device
      (and layer (not device) (not= stype :transition))
      {:type :hierarchy-violation
       :message "layer requires device (unless transition state)"}

      ;; Submode requires layer
      (and submode (not layer))
      {:type :hierarchy-violation
       :message "submode requires layer"}

      ;; Return-to requires layer
      (and return-to (not layer))
      {:type :hierarchy-violation
       :message "return-to requires layer"}

      :else nil)))

;; ============================================================================
;; State Parsing - From Strings
;; ============================================================================

(defn parse-state-string
  "Parse a state string like 'profile=Default:device=Desktop:dsk_layer=13:dsk_return_to_layer=1'
   into a state map {:profile 'Default' :device :!apple_internal :layer 13 :submode nil :return-to 1 :app nil}

   Also accepts 'layer=1' shorthand for 'dsk_layer=1'."
  [s]
  (when (and s (string? s) (not (str/blank? s)))
    (let [parts (str/split s #":")
          pairs (map #(str/split % #"=" 2) parts)]
      (reduce
        (fn [state [k v]]
          (case k
            "profile" (assoc state :profile v)
            "device" (assoc state :device (cond
                                            (= v "Desktop") :desktop
                                            (= v "desktop") :desktop
                                            (= v "Laptop") :laptop
                                            (= v "laptop") :laptop
                                            :else (keyword v)))
            "app" (assoc state :app (keyword v))
            ("dsk_layer" "layer") (assoc state :layer (parse-long v))
            ("dsk_ins_sub_mode" "submode") (assoc state :submode (parse-long v))
            ("dsk_return_to_layer" "return-to") (assoc state :return-to (parse-long v))
            state))
        {}
        pairs))))

(defn parse-complete-state
  "Parse a state string as a CompleteState. Validates hierarchy.
   Returns {:state <map> :type :complete} or throws on invalid."
  [s]
  (let [state (parse-state-string s)
        stype (state-type state)]
    (when (and state (not= stype :empty))
      (when-not (= stype :complete)
        (throw (ex-info (str "Complete state must start with profile, got " stype " state")
                        {:state state :type stype})))
      (when-let [err (validate-state-hierarchy state)]
        (throw (ex-info (:message err) {:state state :error err})))
      {:state state :type :complete})))

(defn parse-filter-state
  "Parse a state string as a FilterState (starts with device).
   FilterState is for rule conditions: device → app → layer → leaf.
   Distinct from TransitionState which is variables-only."
  [s]
  (let [state (parse-state-string s)
        stype (state-type state)]
    (when (and state (not= stype :empty))
      (when-not (= stype :filter)
        (throw (ex-info (str "Filter state must start with device, got " stype " state")
                        {:state state :type stype})))
      {:state state :type :filter})))

(defn parse-transition-state
  "Parse a state string as a TransitionState (just layer variables)."
  [s]
  (let [state (parse-state-string s)
        stype (state-type state)]
    (when (and state (not= stype :empty))
      (when-not (= stype :transition)
        (throw (ex-info (str "Transition state must only have layer variables")
                        {:state state :type stype})))
      {:state state :type :transition})))

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
   Checks that leaf conditions (submode, return-to, app) have required parent (layer).
   Returns nil if valid, or an error map if invalid."
  [{:keys [layer submode return-to app] :as state}]
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

    ;; App condition requires app layer (0 or 10)
    (and app (not layer))
    {:type :app-without-layer
     :message "App condition requires dsk_layer condition"}

    (and app layer (not (app-layers layer)))
    {:type :app-wrong-layer
     :message (format "App condition only valid in layers 0 or 10, got %d" layer)}

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
   Each state is {:profile :device :layer :submode :return-to :app}.

   Canonical ordering (root to leaf):
   1. None profile (placeholder, no real state)
   2. Default + Laptop (no layer variables apply)
   3. Default + Desktop + each layer with valid submode/return-to/app

   State rules:
   - Layer 1 (Ins): submode must be 0-4, return-to=-1, app=nil
   - Layers 13/28 (mouse): submode=-1, return-to must be 0 or 1, app=nil
   - Layers 0/10 (app layers): submode=-1, return-to=-1, app can be Chrome/VSCode/iTerm or nil
   - Other layers: submode=-1, return-to=-1, app=nil"
  []
  (concat
    ;; 1. None profile (placeholder)
    [{:profile "None" :device nil :layer nil :submode nil :return-to nil :app nil}]

    ;; 2. Default + Laptop
    [{:profile "Default" :device :laptop :layer nil :submode nil :return-to nil :app nil}]

    ;; 3. Default + Desktop + each valid layer state
    (mapcat
      (fn [layer]
        (cond
          ;; Layer 1: submodes
          (submode-layers layer)
          (for [submode (sort valid-submodes)]
            {:profile "Default" :device :desktop :layer layer :submode submode :return-to -1 :app nil})

          ;; Layer 13/28: return-to
          (return-to-layers layer)
          (for [return-to (sort valid-return-to)]
            {:profile "Default" :device :desktop :layer layer :submode -1 :return-to return-to :app nil})

          ;; Layers 0, 10: app-specific states
          (app-layers layer)
          (concat
            (for [app (sort-by name all-apps)]
              {:profile "Default" :device :desktop :layer layer :submode -1 :return-to -1 :app app})
            [{:profile "Default" :device :desktop :layer layer :submode -1 :return-to -1 :app nil}])

          ;; Other layers
          :else
          [{:profile "Default" :device :desktop :layer layer :submode -1 :return-to -1 :app nil}]))
      (sort all-layers))))

(defn state-matches-partial?
  "Check if a complete state matches a partial state specification.
   A partial state matches if all specified fields match."
  [complete-state partial-state]
  (every? (fn [[k v]]
            (or (nil? v)  ;; nil in partial means 'any'
                (= (get complete-state k) v)))
          partial-state))

(defn expand-partial-state
  "Given a partial complete state, return all valid complete states that match.
   E.g., {:profile \"Default\" :device :desktop} returns all desktop layer states.
   E.g., {:profile \"Default\" :device :desktop :layer 1} returns all Ins submodes."
  [partial-state]
  (filter #(state-matches-partial? % partial-state) (all-valid-states)))

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
