#!/usr/bin/env bb
;; Generate GraphViz DOT representation of the layer state machine
;;
;; Parses karabiner.edn and extracts state transitions to create a DAG
;; visualization of layer navigation.
;;
;; Usage:
;;   bb to-graphviz.bb <edn-file> [output.dot]
;;   bb to-graphviz.bb ../karabiner.edn | dot -Tpng -o layers.png

(ns to-graphviz
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

;; ============================================================================
;; Layer Definitions
;; ============================================================================

(def layer-names
  {0  "Normal"
   1  "Ins"
   2  "Nav"
   3  "Chrome"
   4  "VSCode"
   5  "TMUX"
   6  "Comma"
   7  "L"
   8  "Term"
   9  "Admin"
   10 "InApp"
   11 "AppSwitcher"
   12 "WindowSwitcher"
   13 "Label"
   14 "L-Cmd"
   15 "L-Cmd-Shift"
   16 "L-Ctrl"
   17 "L-Ctrl-Shift"
   18 "L-CtrlCmd"
   19 "L-CtrlCmd-Shift"
   20 "L-CtrlAlt"
   21 "L-CtrlAlt-Shift"
   22 "L-Alt"
   23 "L-Alt-Shift"
   24 "L-AltCmd"
   25 "L-AltCmd-Shift"
   26 "L-Hyper"
   27 "L-Hyper-Shift"
   28 "Grid"})

(def layer-colors
  "Colors for different layer types"
  {0  "#e8e8e8"  ; Normal - gray
   1  "#b3e5fc"  ; Ins - light blue
   2  "#c8e6c9"  ; Nav - light green
   3  "#fff9c4"  ; Chrome - light yellow
   4  "#e1bee7"  ; VSCode - light purple
   5  "#ffccbc"  ; TMUX - light orange
   6  "#d1c4e9"  ; Comma - light violet
   7  "#f0f4c3"  ; L - light lime
   8  "#ffe0b2"  ; Term - peach
   9  "#f8bbd9"  ; Admin - light pink
   10 "#b2dfdb"  ; InApp - teal
   11 "#ffcdd2"  ; AppSwitcher - light red
   12 "#ffcdd2"  ; WindowSwitcher - light red
   13 "#dcedc8"  ; Label - light green
   28 "#dcedc8"  ; Grid - light green (mouse mode)
   })

(defn layer-color [layer]
  (get layer-colors layer
       (cond
         (<= 14 layer 27) "#f0f4c3"  ; L sub-layers - light lime
         :else "#ffffff")))

;; ============================================================================
;; Rule Parsing
;; ============================================================================

(defn extract-variable-set [item]
  "Extract a variable set like [\"dsk_layer\" 5] from an item"
  (when (and (vector? item)
             (= 2 (count item))
             (string? (first item)))
    [(keyword (first item)) (second item)]))

(defn extract-condition [cond-item]
  "Extract condition from various formats"
  (cond
    ;; Single condition: ["dsk_layer" 0]
    (and (vector? cond-item)
         (= 2 (count cond-item))
         (string? (first cond-item)))
    {(keyword (first cond-item)) (second cond-item)}

    ;; Nested conditions: [["dsk_layer" 0] ["dsk_in_modal_layer" 0]]
    (and (vector? cond-item)
         (every? vector? cond-item))
    (into {} (map extract-variable-set cond-item))

    :else nil))

(defn extract-actions [action]
  "Extract all variable sets from an action"
  (when (vector? action)
    (->> action
         (map extract-variable-set)
         (remove nil?)
         (into {}))))

(defn normalize-key [from-key]
  "Convert various from-key formats to a display string"
  (cond
    (keyword? from-key)
    (name from-key)

    (map? from-key)
    (let [key-name (or (:key from-key) (:pkey from-key))
          modi (:modi from-key)]
      (str (when modi
             (str (cond
                    (map? modi) (str/join "+" (map name (or (:mandatory modi) [])))
                    (vector? modi) (str/join "+" (map name modi))
                    :else "")
                  "+"))
           (if (keyword? key-name) (name key-name) (str key-name))))

    :else (str from-key)))

(defn parse-rule [rule block-desc]
  "Parse a single rule and extract transition info"
  (when (and (vector? rule) (>= (count rule) 2))
    (let [from-raw (first rule)
          to (second rule)
          condition (when (>= (count rule) 3) (nth rule 2))

          ;; Extract rule ID if present
          rule-id (when (map? from-raw) (:id from-raw))
          from-key (if (map? from-raw)
                     (normalize-key from-raw)
                     (normalize-key from-raw))

          ;; Parse condition to find source layer
          cond-map (extract-condition condition)
          source-layer (get cond-map :dsk_layer)

          ;; Parse actions to find target layer
          actions (extract-actions to)
          target-layer (get actions :dsk_layer)]

      (when (and source-layer target-layer (not= source-layer target-layer))
        {:from-layer source-layer
         :to-layer target-layer
         :trigger from-key
         :block block-desc
         :rule-id rule-id}))))

(defn extract-rules-from-block [block]
  "Extract all rules from a block"
  (let [desc (:des block)
        rules-vec (:rules block)]
    (when (vector? rules-vec)
      (->> rules-vec
           (drop-while keyword?)  ; Skip :!apple_internal, :Chrome, etc.
           (filter vector?)
           (map #(parse-rule % desc))
           (remove nil?)))))

;; ============================================================================
;; GraphViz Generation
;; ============================================================================

(defn escape-label [s]
  "Escape special characters for GraphViz labels"
  (-> s
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "<" "\\<")
      (str/replace ">" "\\>")))

(defn group-transitions [transitions]
  "Group transitions by from/to pair, combining triggers"
  (->> transitions
       (group-by (juxt :from-layer :to-layer))
       (map (fn [[[from to] trans]]
              {:from from
               :to to
               :triggers (distinct (map :trigger trans))}))))

(defn generate-dot [transitions]
  "Generate GraphViz DOT output"
  (let [grouped (group-transitions transitions)
        layers-used (->> grouped
                         (mapcat (juxt :from :to))
                         distinct
                         sort)]

    (str
      "digraph LayerStateMachine {\n"
      "  rankdir=TB;\n"
      "  node [shape=box, style=\"rounded,filled\", fontname=\"Helvetica\"];\n"
      "  edge [fontname=\"Helvetica\", fontsize=10];\n"
      "\n"
      "  // Layer nodes\n"
      (str/join "\n"
        (for [layer layers-used]
          (format "  layer_%d [label=\"%s\\n(layer %d)\", fillcolor=\"%s\"];"
                  layer
                  (get layer-names layer (str "Layer " layer))
                  layer
                  (layer-color layer))))
      "\n\n"
      "  // Transitions\n"
      (str/join "\n"
        (for [{:keys [from to triggers]} grouped]
          (format "  layer_%d -> layer_%d [label=\"%s\"];"
                  from to
                  (escape-label (str/join "\\n" (take 5 triggers))))))
      "\n"
      "}\n")))

(defn generate-stats [transitions]
  "Generate statistics about the state machine"
  (let [grouped (group-transitions transitions)
        layers-used (->> grouped (mapcat (juxt :from :to)) distinct sort)]
    (str
      "// Statistics:\n"
      "// - Total layers: " (count layers-used) "\n"
      "// - Total transitions: " (count grouped) "\n"
      "// - Layers: " (str/join ", " (map #(str % "=" (get layer-names % "?")) layers-used)) "\n")))

;; ============================================================================
;; CLI Interface
;; ============================================================================

(defn -main [& args]
  (when (empty? args)
    (println "Usage: bb to-graphviz.bb <edn-file> [output.dot]")
    (println "")
    (println "Generates GraphViz DOT representation of the layer state machine.")
    (println "")
    (println "Examples:")
    (println "  bb to-graphviz.bb ../karabiner.edn > layers.dot")
    (println "  bb to-graphviz.bb ../karabiner.edn | dot -Tpng -o layers.png")
    (println "  bb to-graphviz.bb ../karabiner.edn | dot -Tsvg -o layers.svg")
    (System/exit 1))

  (let [edn-file (first args)
        output-file (second args)
        config (edn/read-string (slurp edn-file))

        ;; Extract all transitions
        transitions (->> (:main config)
                         (mapcat extract-rules-from-block)
                         (remove nil?))

        dot-output (str (generate-stats transitions) "\n" (generate-dot transitions))]

    (if output-file
      (spit output-file dot-output)
      (println dot-output))))

;; Run main if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
