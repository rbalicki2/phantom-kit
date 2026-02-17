#!/usr/bin/env bb
;; Swap ALL Insert mode transitions to AltIns mode
;; Keeps R0060 (/ key) as legacy Insert mode
;; Preserves rules that are WITHIN layer 1 (they have dsk_layer=1 in their condition)

(require '[clojure.string :as str])

(def input-file (first *command-line-args*))

(when-not input-file
  (println "Usage: bb scripts/edit/swap-ins-to-altins-v2.bb <karabiner.edn>")
  (System/exit 1))

(def content (slurp input-file))

;; Key insight: Rules WITHIN layer 1 have "dsk_layer=1" or "dsk_layer\" 1]" in their ID/condition
;; Rules that TRANSITION TO layer 1 have "echo ins" in their shell command

;; Step 1: Replace "echo ins > /tmp/karabiner-layer" with altins version
;; But skip R0060
(def step1
  (-> content
      ;; First, temporarily mark R0060 to preserve it
      (str/replace #"(R0060[^\n]+)echo ins > /tmp/karabiner-layer"
                   "$1PRESERVE_INS_MARKER")
      ;; Replace all echo ins with echo altins + overlay
      (str/replace "echo ins > /tmp/karabiner-layer"
                   "echo altins > /tmp/karabiner-layer && /opt/homebrew/bin/hs -c 'showPermanentLayerOverlay()'")
      ;; Restore R0060
      (str/replace "PRESERVE_INS_MARKER"
                   "echo ins > /tmp/karabiner-layer")))

;; Step 2: Replace layer transitions in rules that have shell commands with altins
;; These are rules that ENTER layer 1 from other layers
;; Pattern: rules with "echo altins" should have layer 7, not layer 1
(def step2
  (str/replace step1
               #"(\[\"dsk_layer\" )1(\] \[\"dsk_ins_sub_mode\" 0\] \[\"dsk_return_to_layer\" -1\] \{:shell [^\}]+echo altins)"
               "$17$2"))

(spit input-file step2)

(println "Transformed ALL Insert mode transitions to AltIns")
(println "R0060 (/ key) preserved as legacy Insert")
(println "Rules within layer 1 unchanged")
