;; Phantom Kit - State validation and testing utilities
;;
;; Wraps the generic simulator with Phantom Kit specific knowledge:
;; - Valid state combinations
;; - Human-readable state names
;; - State validation
;;
;; Usage:
;;   (load-file "scripts/lib/phantom-kit.bb")
;;   (require '[lib.phantom-kit :as pk])
;;   (pk/validate-state state)
;;   (pk/state->string state)

(ns lib.phantom-kit
  (:require [clojure.string :as str]))

;; ============================================================================
;; State Variable Definitions
;; ============================================================================

(def layer-names
  {0 "Normal" 1 "Ins" 2 "Nav" 3 "Chrome" 4 "VSCode" 5 "TMUX"
   6 "Comma" 7 "L" 8 "Term" 9 "Admin" 10 "InApp" 11 "AppSwitcher"
   12 "WindowSwitcher" 13 "Label" 14 "L-Cmd" 15 "L-Cmd-Shift"
   16 "L-Ctrl" 17 "L-Ctrl-Shift" 18 "L-CtrlCmd" 19 "L-CtrlCmd-Shift"
   20 "L-CtrlOpt" 21 "L-CtrlOpt-Shift" 22 "L-Opt" 23 "L-Opt-Shift"
   24 "L-OptCmd" 25 "L-OptCmd-Shift" 26 "L-Hyper" 27 "L-Hyper-Shift"
   28 "Grid"})

(def submode-names
  {-1 "none"
   0 "base"
   1 "shift-mirror-oneshot"
   2 "shift-oneshot"
   3 "delete-chord"
   4 "select-chord"})

(def return-to-names
  {-1 "none"
   0 "Normal"
   1 "Ins"})

;; ============================================================================
;; State Construction
;; ============================================================================

(defn make-state
  "Create a Phantom Kit state map.

   Options:
   - :layer (default 0) - dsk_layer value
   - :submode (default -1) - dsk_ins_sub_mode value
   - :modal (default 0) - dsk_in_modal_layer value
   - :return-to (default -1) - dsk_return_to_layer value
   - :device (default :desktop) - :desktop or :laptop
   - :app (default nil) - current app keyword like :Chrome"
  [& {:keys [layer submode modal return-to device app]
      :or {layer 0 submode -1 modal 0 return-to -1 device :desktop app nil}}]
  {:device device
   :app app
   :variables {:dsk_layer layer
               :dsk_ins_sub_mode submode
               :dsk_in_modal_layer modal
               :dsk_return_to_layer return-to}})

(def normal-state (make-state :layer 0 :modal 0 :submode -1 :return-to -1))
(def ins-state (make-state :layer 1 :modal 0 :submode 0 :return-to -1))
(def ins-oneshot-mirror (make-state :layer 1 :modal 0 :submode 1 :return-to -1))
(def ins-oneshot-shift (make-state :layer 1 :modal 0 :submode 2 :return-to -1))

;; ============================================================================
;; State Validation
;; ============================================================================

(defn validate-state
  "Validate a Phantom Kit state. Returns {:valid true/false :errors [...]}"
  [state]
  (let [vars (:variables state)
        layer (get vars :dsk_layer)
        submode (get vars :dsk_ins_sub_mode)
        modal (get vars :dsk_in_modal_layer)
        return-to (get vars :dsk_return_to_layer)
        errors (cond-> []
                 ;; Layer must be valid
                 (not (contains? layer-names layer))
                 (conj (str "Invalid layer: " layer))

                 ;; Submode must be valid
                 (not (contains? submode-names submode))
                 (conj (str "Invalid submode: " submode))

                 ;; Return-to must be valid
                 (not (contains? return-to-names return-to))
                 (conj (str "Invalid return-to: " return-to))

                 ;; Modal invariant: 0 for Normal/Ins, 1 for modal layers
                 (and (< layer 2) (= modal 1))
                 (conj (str "Layer " layer " should have modal=0, got modal=1"))

                 (and (>= layer 2) (= modal 0))
                 (conj (str "Layer " layer " should have modal=1, got modal=0"))

                 ;; Submode only valid in Ins mode (layer 1)
                 (and (not= layer 1) (>= submode 0))
                 (conj (str "Submode " submode " only valid in Ins mode (layer 1), current layer: " layer))

                 ;; Return-to only valid in Label mode (layer 13)
                 (and (not= layer 13) (>= return-to 0))
                 (conj (str "Return-to " return-to " only valid in Label mode (layer 13), current layer: " layer)))]
    {:valid (empty? errors)
     :errors errors}))

;; ============================================================================
;; State Display
;; ============================================================================

(defn state->string
  "Convert state to human-readable string"
  [state]
  (let [vars (:variables state)
        layer (get vars :dsk_layer)
        submode (get vars :dsk_ins_sub_mode)
        modal (get vars :dsk_in_modal_layer)
        return-to (get vars :dsk_return_to_layer)
        parts (cond-> [(str "layer=" (get layer-names layer layer))]
                (and (= layer 1) (>= submode 0))
                (conj (str "submode=" (get submode-names submode submode)))

                (= layer 13)
                (conj (str "return-to=" (get return-to-names return-to return-to)))

                (:app state)
                (conj (str "app=" (name (:app state)))))]
    (str/join ", " parts)))

(defn key->string
  "Convert key input to human-readable string"
  [key-input]
  (let [key-map (if (keyword? key-input) {:key key-input :modifiers []} key-input)
        mods (:modifiers key-map [])
        key-name (name (:key key-map))]
    (if (empty? mods)
      key-name
      (str (str/join "+" (map name mods)) "+" key-name))))

(defn print-step
  "Print a simulation step result"
  [step]
  (println (str "  Input: " (key->string (:input step))))
  (println (str "  State before: " (state->string (:state-before step))))
  (if (:matched step)
    (do
      (println (str "  Matched rule: " (:rule-id step)))
      (println (str "  Block: " (:rule-description step)))
      (when (seq (:variable-changes step))
        (println (str "  Variable changes: " (pr-str (:variable-changes step)))))
      (when (:output step)
        (println (str "  Output: " (:output step)))))
    (println "  No rule matched - key passed through"))
  (println (str "  State after: " (state->string (:new-state step))))
  (when (seq (:shadowed-rules step))
    (println (str "  WARNING: " (count (:shadowed-rules step)) " shadowed rules:")))
  (doseq [shadowed (:shadowed-rules step)]
    (println (str "    - " (get-in shadowed [:from :id]) " in " (:description shadowed)))))

(defn print-simulation
  "Print full simulation results"
  [results]
  (println "=== Simulation Results ===")
  (doseq [[idx step] (map-indexed vector (:steps results))]
    (println (str "\nStep " (inc idx) ":"))
    (print-step step))
  (println (str "\n=== Final State: " (state->string (:final-state results)) " ===")))
