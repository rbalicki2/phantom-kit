#!/usr/bin/env bb

;; Add catch-all rules for oneshot modes
;;
;; When in oneshot mode (dsk_ins_sub_mode=1 or 2), ANY key press should
;; clear the oneshot. This script adds rules for keys not already handled.
;;
;; Usage:
;;   bb add-oneshot-catchall.bb <input-edn> <output-edn>

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

;; Keys that need catch-all rules (not already handled by letter/number oneshot rules)
(def catchall-keys
  ;; Navigation/editing keys
  [:spacebar :return_or_enter :tab :escape
   :delete_or_backspace :delete_forward
   :up_arrow :down_arrow :left_arrow :right_arrow
   :page_up :page_down :home :end
   ;; Symbol keys
   :open_bracket :close_bracket
   :hyphen :equal_sign
   :quote :grave_accent_and_tilde :backslash
   ;; Function keys (Fn+keys that aren't already mirrored letters/numbers)
   :f1 :f2 :f3 :f4 :f5 :f6 :f10 :f11 :f12 :f13 :f14])

(defn key-name [k]
  "Human-readable key name"
  (case k
    :spacebar "Space"
    :return_or_enter "Enter"
    :delete_or_backspace "Backspace"
    :delete_forward "Delete"
    :up_arrow "↑"
    :down_arrow "↓"
    :left_arrow "←"
    :right_arrow "→"
    :page_up "PgUp"
    :page_down "PgDn"
    :open_bracket "["
    :close_bracket "]"
    :equal_sign "="
    :grave_accent_and_tilde "`"
    :backslash "bksl"
    (name k)))

(defn generate-catchall-rule [key-kw submode rule-num]
  "Generate a catch-all rule for a key in a submode"
  (let [state-str (str "profile=Default:device=Desktop:dsk_ins_sub_mode=" submode)
        key-display (key-name key-kw)
        description (str key-display " → " key-display " (oneshot clear)")]
    [{:key key-kw
      :modi {:optional [:any]}
      :id (str "R" (format "%04d" rule-num) " [" state-str "] " description)}
     [key-kw ["dsk_ins_sub_mode" 0]]
     [["dsk_ins_sub_mode" submode]]]))

(defn generate-catchall-block [submode start-rule-num]
  "Generate a block of catch-all rules for a submode"
  (let [rules (map-indexed
               (fn [idx key-kw]
                 (generate-catchall-rule key-kw submode (+ start-rule-num idx)))
               catchall-keys)]
    {:des (str "[Desktop, Layer 1, Submode " submode "] Oneshot catch-all (any other key clears)")
     :rules (vec (cons :!apple_internal rules))}))

(defn find-insertion-point [content submode]
  "Find where to insert the catch-all block (after the existing submode blocks)"
  ;; Look for the end of the last submode-specific block
  (let [pattern (re-pattern (str "\\{:des \"\\[Desktop, Layer 1, Submode " submode "\\].*?Clear oneshot on modified keys\""))]
    (when-let [match (re-find pattern content)]
      ;; Find the closing of that block
      (let [start-idx (.indexOf content match)]
        (when (pos? start-idx)
          ;; Find the matching closing brace
          (loop [idx (+ start-idx (count match))
                 depth 1]
            (when (< idx (count content))
              (case (nth content idx)
                \{ (recur (inc idx) (inc depth))
                \} (if (= depth 1)
                     (inc idx) ;; Found the closing brace
                     (recur (inc idx) (dec depth)))
                (recur (inc idx) depth)))))))))

(defn -main [& args]
  (let [[input-file output-file] args]
    (when (or (nil? input-file) (nil? output-file))
      (println "Usage: bb add-oneshot-catchall.bb <input-edn> <output-edn>")
      (System/exit 1))

    (let [content (slurp input-file)
          ;; Find the highest rule number to continue from
          rule-nums (re-seq #"R(\d{4})" content)
          max-rule-num (if (seq rule-nums)
                         (apply max (map #(parse-long (second %)) rule-nums))
                         0)

          ;; Generate catch-all blocks
          block1 (generate-catchall-block 1 (+ max-rule-num 1))
          block2 (generate-catchall-block 2 (+ max-rule-num 1 (count catchall-keys)))]

      (println "Max existing rule number:" max-rule-num)
      (println "Generating" (count catchall-keys) "catch-all rules per submode")
      (println "\nBlock 1 (submode 1):")
      (println "  " (:des block1))
      (println "\nBlock 2 (submode 2):")
      (println "  " (:des block2))

      ;; Find insertion points
      (let [insert1 (find-insertion-point content 1)
            insert2 (find-insertion-point content 2)]

        (println "\nInsertion point for submode 1:" insert1)
        (println "Insertion point for submode 2:" insert2)

        (if (and insert1 insert2)
          (let [;; Insert in reverse order (submode 2 first since it's later in file)
                ;; This preserves the insertion points
                with-block2 (str (subs content 0 insert2)
                                 "\n"
                                 (pr-str block2)
                                 (subs content insert2))
                ;; Recalculate insert1 since content length changed
                insert1-adjusted insert1
                with-both (str (subs with-block2 0 insert1-adjusted)
                               "\n"
                               (pr-str block1)
                               (subs with-block2 insert1-adjusted))]
            (spit output-file with-both)
            (println "\nWritten to" output-file))
          (do
            (println "\nCould not find insertion points. Outputting blocks for manual insertion:")
            (println "\n--- Submode 1 block ---")
            (println (pr-str block1))
            (println "\n--- Submode 2 block ---")
            (println (pr-str block2))))))))

(apply -main *command-line-args*)
