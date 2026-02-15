#!/usr/bin/env bb

;; Simulate key sequences against the Karabiner config
;;
;; Usage:
;;   bb simulate-keys.bb <edn-file> --state <state-spec> --keys <key-spec>
;;
;; State spec examples:
;;   ins                      - Ins mode (layer 1, submode 0)
;;   ins:oneshot              - Ins mode with shift-mirror-oneshot (submode 1)
;;   ins:shift-oneshot        - Ins mode with shift-oneshot (submode 2)
;;   normal                   - Normal mode (layer 0)
;;   layer=1:submode=1        - Explicit variable values
;;
;; Key spec examples:
;;   l                        - Single key
;;   shift+l                  - Key with modifier
;;   "shift+l,l"              - Sequence of keys (comma-separated)
;;
;; Example:
;;   bb simulate-keys.bb src/karabiner.edn --state ins:oneshot --keys "shift+l,l"

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; Load libraries
(load-file "scripts/lib/simulator.bb")
(load-file "scripts/lib/phantom-kit.bb")
(require '[lib.simulator :as sim]
         '[lib.phantom-kit :as pk])

;; ============================================================================
;; State Parsing
;; ============================================================================

(defn parse-state-spec [spec]
  "Parse a state specification string into a state map"
  (case spec
    "normal" pk/normal-state
    "ins" pk/ins-state
    "ins:oneshot" pk/ins-oneshot-mirror
    "ins:mirror-oneshot" pk/ins-oneshot-mirror
    "ins:shift-oneshot" pk/ins-oneshot-shift
    ;; Parse explicit format: layer=N:submode=M:...
    (let [parts (str/split spec #":")
          parsed (into {} (for [part parts
                                :let [[k v] (str/split part #"=")]
                                :when (and k v)]
                            [(keyword k) (parse-long v)]))]
      (pk/make-state :layer (get parsed :layer 0)
                     :submode (get parsed :submode -1)
                     :modal (get parsed :modal (if (>= (get parsed :layer 0) 2) 1 0))
                     :return-to (get parsed :return-to -1)
                     :device (if (= (get parsed :device) 1) :laptop :desktop)))))

;; ============================================================================
;; Key Parsing (supports both Goku format and human-readable format)
;; ============================================================================

;; Goku modifier shorthand
(def goku-modifier-map
  {\S :left_shift
   \R :right_shift
   \C :left_command
   \Q :right_command
   \T :left_control
   \O :left_option
   \E :right_option})

;; Human-readable modifier aliases
(def modifier-aliases
  {"shift" :shift
   "left_shift" :left_shift
   "right_shift" :right_shift
   "lshift" :left_shift
   "rshift" :right_shift
   "cmd" :left_command
   "command" :left_command
   "left_command" :left_command
   "right_command" :right_command
   "lcmd" :left_command
   "rcmd" :right_command
   "ctrl" :left_control
   "control" :left_control
   "left_control" :left_control
   "right_control" :right_control
   "lctrl" :left_control
   "rctrl" :right_control
   "opt" :left_option
   "option" :left_option
   "alt" :left_option
   "left_option" :left_option
   "right_option" :right_option
   "lopt" :left_option
   "ropt" :right_option})

(defn parse-goku-key-spec [spec]
  "Parse Goku format like !Rl or !CSj into {:key :l :modifiers [...]}"
  (when (str/starts-with? spec "!")
    (let [;; Extract modifier chars (uppercase letters after !)
          mod-match (re-find #"^!([A-Z]+)(.+)$" spec)]
      (when mod-match
        (let [[_ mod-chars key-part] mod-match
              modifiers (vec (keep #(get goku-modifier-map %) mod-chars))]
          {:key (keyword key-part)
           :modifiers modifiers})))))

(defn parse-human-key-spec [spec]
  "Parse human format like 'shift+l' into {:key :l :modifiers [:shift]}"
  (let [parts (str/split (str/lower-case spec) #"\+")
        key-part (last parts)
        mod-parts (butlast parts)
        modifiers (mapv #(get modifier-aliases % (keyword %)) mod-parts)
        key-kw (keyword key-part)]
    {:key key-kw :modifiers modifiers}))

(defn parse-key-spec [spec]
  "Parse a key specification (supports both Goku and human-readable formats)

   Goku format: !Sl (Shift+l), !Rl (RShift+l), !CSj (Cmd+Shift+j)
   Human format: shift+l, right_shift+l, cmd+shift+j"
  (or (parse-goku-key-spec spec)
      (parse-human-key-spec spec)))

(defn parse-key-sequence [spec]
  "Parse a comma-separated key sequence"
  (let [keys-str (str/split spec #",")]
    (mapv #(parse-key-spec (str/trim %)) keys-str)))

;; ============================================================================
;; Main
;; ============================================================================

(defn -main [& args]
  (let [opts (apply hash-map (rest args))
        edn-file (first args)
        state-spec (get opts "--state" "normal")
        keys-spec (get opts "--keys")]

    (when (or (nil? edn-file) (nil? keys-spec))
      (println "Usage: bb simulate-keys.bb <edn-file> --state <state-spec> --keys <key-spec>")
      (println)
      (println "State specs:")
      (println "  normal              - Normal mode (layer 0)")
      (println "  ins                 - Ins mode (layer 1)")
      (println "  ins:oneshot         - Ins mode with shift-mirror-oneshot (submode 1)")
      (println "  ins:shift-oneshot   - Ins mode with shift-oneshot (submode 2)")
      (println "  layer=N:submode=M   - Explicit values")
      (println)
      (println "Key specs:")
      (println "  l                   - Single key")
      (println "  shift+l             - Key with modifier")
      (println "  \"shift+l,l\"         - Sequence (comma-separated)")
      (System/exit 1))

    (println (str "Loading: " edn-file))
    (let [config (edn/read-string (slurp edn-file))
          initial-state (parse-state-spec state-spec)
          key-sequence (parse-key-sequence keys-spec)]

      ;; Validate initial state
      (println (str "\nInitial state: " (pk/state->string initial-state)))
      (let [validation (pk/validate-state initial-state)]
        (if (:valid validation)
          (println "State is valid ✓")
          (do
            (println "State is INVALID:")
            (doseq [err (:errors validation)]
              (println (str "  - " err)))
            (System/exit 1))))

      ;; Run simulation
      (println (str "\nSimulating " (count key-sequence) " key(s): "
                    (str/join ", " (map pk/key->string key-sequence))))
      (println)

      (let [results (sim/simulate-sequence config initial-state key-sequence)]
        (pk/print-simulation results)

        ;; Validate final state
        (let [final-validation (pk/validate-state (:final-state results))]
          (println)
          (if (:valid final-validation)
            (println "Final state is valid ✓")
            (do
              (println "Final state is INVALID:")
              (doseq [err (:errors final-validation)]
                (println (str "  - " err))))))))))

(apply -main *command-line-args*)
