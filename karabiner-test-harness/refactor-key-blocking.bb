#!/usr/bin/env bb
;; Refactor key blocking:
;; - Remove LHS key blocking
;; - Remove Normal mode RHS key blocking
;; - Remove modal layer catch-all
;; - Add global catch-all
;; - Add Ins mode pass-through rules

(require '[clojure.string :as str])

(def content (slurp "/Users/rbalicki/code/voicemode/karabiner.edn"))
(def lines (vec (str/split-lines content)))

;; Find line ranges to delete
(defn find-section-range [lines start-pattern]
  (let [start-idx (first (keep-indexed
                           (fn [i line] (when (str/includes? line start-pattern) i))
                           lines))]
    (when start-idx
      ;; Find the closing ]} for this section
      (loop [i (inc start-idx)
             depth 1]
        (if (>= i (count lines))
          nil
          (let [line (nth lines i)
                opens (count (re-seq #"\{|\[" line))
                closes (count (re-seq #"\}|\]" line))
                new-depth (+ depth opens (- closes))]
            (if (<= new-depth 0)
              [start-idx i]
              (recur (inc i) new-depth))))))))

;; Find ranges to delete (working backwards to preserve indices)
(def lhs-range (find-section-range lines "[Desktop] Disable LHS keys and arrows"))
(def normal-disable-range (find-section-range lines "[Desktop, Layer 0] Normal - Disable unmapped RHS keys"))
(def modal-catchall-range (find-section-range lines "[Global] Block unmapped keys in all modal layers"))

(println "LHS key blocking:" lhs-range)
(println "Normal mode disable:" normal-disable-range)
(println "Modal catch-all:" modal-catchall-range)

;; Ins mode pass-through rules (to be inserted after existing Ins rules)
;; These allow unmapped keys to type normally in Ins mode
(def ins-passthrough-rules
  "
  {:des \"[Desktop, Layer 1] Ins - Key pass-through\"
   :rules [:!apple_internal
    ;; Letters (## = any modifiers)
    [:##y :y [\"dsk_layer\" 1]]
    [:##u :u [\"dsk_layer\" 1]]
    [:##i :i [\"dsk_layer\" 1]]
    [:##o :o [\"dsk_layer\" 1]]
    [:##p :p [\"dsk_layer\" 1]]
    [:##h :h [\"dsk_layer\" 1]]
    [:##j :j [\"dsk_layer\" 1]]
    [:##k :k [\"dsk_layer\" 1]]
    [:##l :l [\"dsk_layer\" 1]]
    [:##n :n [\"dsk_layer\" 1]]
    [:##m :m [\"dsk_layer\" 1]]
    ;; Punctuation
    [:##semicolon :semicolon [\"dsk_layer\" 1]]
    [:##quote :quote [\"dsk_layer\" 1]]
    [:##comma :comma [\"dsk_layer\" 1]]
    [:##period :period [\"dsk_layer\" 1]]
    [:##slash :slash [\"dsk_layer\" 1]]
    [:##hyphen :hyphen [\"dsk_layer\" 1]]
    ;; Numbers (number swap handles bare/shift; this catches Cmd+N, Ctrl+N, etc.)
    [:##6 :6 [\"dsk_layer\" 1]]
    [:##7 :7 [\"dsk_layer\" 1]]
    [:##8 :8 [\"dsk_layer\" 1]]
    [:##9 :9 [\"dsk_layer\" 1]]
    [:##0 :0 [\"dsk_layer\" 1]]
    [:##equal_sign :equal_sign [\"dsk_layer\" 1]]
    ;; Arrows
    [:##up_arrow :up_arrow [\"dsk_layer\" 1]]
    [:##down_arrow :down_arrow [\"dsk_layer\" 1]]
    ;; Thumb cluster
    [:##return_or_enter :return_or_enter [\"dsk_layer\" 1]]
    [:##spacebar :spacebar [\"dsk_layer\" 1]]
    [:##page_up :page_up [\"dsk_layer\" 1]]
    [:##page_down :page_down [\"dsk_layer\" 1]]
   ]}")

;; Global catch-all (blocks all keys not handled by earlier rules)
(def global-catchall
  "
  {:des \"[Global] Block all unmapped keys\"
   :rules [:!apple_internal
    ;; Letters
    [:##a :vk_none] [:##b :vk_none] [:##c :vk_none] [:##d :vk_none] [:##e :vk_none]
    [:##f :vk_none] [:##g :vk_none] [:##h :vk_none] [:##i :vk_none] [:##j :vk_none]
    [:##k :vk_none] [:##l :vk_none] [:##m :vk_none] [:##n :vk_none] [:##o :vk_none]
    [:##p :vk_none] [:##q :vk_none] [:##r :vk_none] [:##s :vk_none] [:##t :vk_none]
    [:##u :vk_none] [:##v :vk_none] [:##w :vk_none] [:##x :vk_none] [:##y :vk_none]
    [:##z :vk_none]
    ;; Numbers
    [:##1 :vk_none] [:##2 :vk_none] [:##3 :vk_none] [:##4 :vk_none] [:##5 :vk_none]
    [:##6 :vk_none] [:##7 :vk_none] [:##8 :vk_none] [:##9 :vk_none] [:##0 :vk_none]
    ;; Symbols and punctuation
    [:##grave_accent_and_tilde :vk_none]
    [:##hyphen :vk_none]
    [:##equal_sign :vk_none]
    [:##open_bracket :vk_none]
    [:##close_bracket :vk_none]
    [:##backslash :vk_none]
    [:##semicolon :vk_none]
    [:##quote :vk_none]
    [:##comma :vk_none]
    [:##period :vk_none]
    [:##slash :vk_none]
    [:##non_us_backslash :vk_none]
    ;; Arrows
    [:##left_arrow :vk_none]
    [:##right_arrow :vk_none]
    [:##up_arrow :vk_none]
    [:##down_arrow :vk_none]
    ;; Other
    [:##spacebar :vk_none]
    [:##tab :vk_none]
    [:##return_or_enter :vk_none]
   ]}")

;; Find where to insert Ins pass-through (after last Ins mode rule block)
(def ins-insert-point
  (first (keep-indexed
           (fn [i line]
             (when (str/includes? line "[Desktop] Disable backspace and delete keys") i))
           lines)))

(println "Ins pass-through insert point:" ins-insert-point)

;; Apply transformations (work backwards to preserve indices)
(defn delete-range [lines [start end]]
  (vec (concat (subvec lines 0 start) (subvec lines (inc end)))))

(defn insert-at [lines idx text]
  (vec (concat (subvec lines 0 idx) [text] (subvec lines idx))))

;; Process deletions from end to start
(def sorted-ranges
  (->> [["lhs" lhs-range]
        ["normal" normal-disable-range]
        ["modal" modal-catchall-range]]
       (filter second)
       (sort-by (comp first second) >)))

(println "\nWill delete in order:" (map first sorted-ranges))

;; Apply deletions
(def after-deletions
  (reduce (fn [ls [name range]]
            (println "Deleting" name "at" range)
            (delete-range ls range))
          lines
          sorted-ranges))

;; Find new insert points after deletions
;; The Ins insert point is before "[Desktop] Disable backspace"
(def new-ins-insert
  (first (keep-indexed
           (fn [i line]
             (when (str/includes? line "[Desktop] Disable backspace and delete keys") i))
           after-deletions)))

(println "New Ins insert point:" new-ins-insert)

;; Insert Ins pass-through rules
(def after-ins-insert
  (if new-ins-insert
    (insert-at after-deletions new-ins-insert ins-passthrough-rules)
    after-deletions))

;; Find end of :main (before final closing brackets)
(def end-main-idx
  (first (keep-indexed
           (fn [i line]
             (when (str/includes? line "] ;; end :main") i))
           after-ins-insert)))

(println "End main idx:" end-main-idx)

;; Insert global catch-all before end of :main
(def final-lines
  (if end-main-idx
    (insert-at after-ins-insert end-main-idx global-catchall)
    after-ins-insert))

;; Write output
(spit "/Users/rbalicki/code/voicemode/karabiner.edn" (str/join "\n" final-lines))

(println "\nDone! Changes applied:")
(println "- Removed LHS key blocking")
(println "- Removed Normal mode RHS key blocking")
(println "- Removed modal layer catch-all")
(println "- Added Ins mode pass-through rules")
(println "- Added global catch-all")
