#!/usr/bin/env bb
;; Swap Insert mode entries to AltIns mode
;; Keeps R0060 (/ key) as legacy Insert mode

(require '[clojure.string :as str])

(def input-file (first *command-line-args*))

(when-not input-file
  (println "Usage: bb scripts/edit/swap-ins-to-altins.bb <karabiner.edn>")
  (System/exit 1))

(def content (slurp input-file))

;; Process line by line
(def lines (str/split-lines content))

(defn transform-line [line]
  ;; Skip R0060 - this stays as legacy Insert
  (if (str/includes? line "R0060")
    line
    ;; Transform lines that transition TO Insert mode
    (if (and (str/includes? line "→ Ins")
             (str/includes? line "[\"dsk_layer\" 1]"))
      (-> line
          ;; Change layer 1 to layer 7
          (str/replace "[\"dsk_layer\" 1]" "[\"dsk_layer\" 7]")
          ;; Change SwiftBar indicator
          (str/replace "echo ins > /tmp/karabiner-layer"
                       "echo altins > /tmp/karabiner-layer && /opt/homebrew/bin/hs -c 'showPermanentLayerOverlay()'"))
      line)))

(def transformed-lines (map transform-line lines))
(def output (str/join "\n" transformed-lines))

(spit input-file output)

(println "Transformed Insert mode entries to AltIns mode")
(println "R0060 (/ key) preserved as legacy Insert mode")
