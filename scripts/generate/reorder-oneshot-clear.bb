#!/usr/bin/env bb

;; Reorder oneshot clear rules to come before bare letter rules
;;
;; The "Clear oneshot on modified keys" blocks need to come BEFORE the
;; "Shift+mirror oneshot letters/numbers" blocks so that Shift+L matches
;; the clear rule first.
;;
;; Usage:
;;   bb reorder-oneshot-clear.bb <input-edn> <output-edn>

(require '[clojure.string :as str]
         '[clojure.edn :as edn])

(defn find-block-bounds [content des-pattern]
  "Find the start and end positions of a block with matching :des"
  (let [;; Find the start of the block (the {:des "..." line)
        pattern (re-pattern (str "\\{:des \"" (java.util.regex.Pattern/quote des-pattern) "\""))
        start-match (re-find pattern content)]
    (when start-match
      (let [start-idx (.indexOf content start-match)]
        (when (>= start-idx 0)
          ;; Find the end of this block - it ends at the next {:des or end of :main
          ;; We need to find the matching closing brace
          (loop [idx (+ start-idx (count start-match))
                 depth 1]
            (when (< idx (count content))
              (let [ch (nth content idx)]
                (cond
                  (= ch \{) (recur (inc idx) (inc depth))
                  (= ch \}) (if (= depth 1)
                              {:start start-idx :end (inc idx)}
                              (recur (inc idx) (dec depth)))
                  :else (recur (inc idx) depth))))))))))

(defn extract-block [content des-pattern]
  "Extract a block with its content"
  (when-let [{:keys [start end]} (find-block-bounds content des-pattern)]
    {:start start
     :end end
     :text (subs content start end)}))

(defn -main [& args]
  (let [[input-file output-file] args]
    (when (or (nil? input-file) (nil? output-file))
      (println "Usage: bb reorder-oneshot-clear.bb <input-edn> <output-edn>")
      (System/exit 1))

    (let [content (slurp input-file)

          ;; Find the blocks we need to move
          clear-block-1 (extract-block content "[Desktop, Layer 1, Submode 1] Clear oneshot on modified keys")
          clear-block-2 (extract-block content "[Desktop, Layer 1, Submode 2] Clear oneshot on modified keys")

          ;; Find the blocks we need to insert before
          ;; For submode 1, insert before NUMBERS (which comes before letters)
          ;; For submode 2, insert before the shift oneshot block
          numbers-block-1 (extract-block content "[Desktop, Layer 1, Submode 1] Shift+mirror oneshot numbers")
          letters-block-2 (extract-block content "[Desktop, Layer 1, Submode 2] Shift oneshot (Fn+Space then letter = Shift+letter)")]

      (println "Found blocks:")
      (println (str "  Clear Submode 1: " (when clear-block-1 (str "lines " (:start clear-block-1) "-" (:end clear-block-1)))))
      (println (str "  Clear Submode 2: " (when clear-block-2 (str "lines " (:start clear-block-2) "-" (:end clear-block-2)))))
      (println (str "  Numbers Submode 1: " (when numbers-block-1 (str "lines " (:start numbers-block-1) "-" (:end numbers-block-1)))))
      (println (str "  Letters Submode 2: " (when letters-block-2 (str "lines " (:start letters-block-2) "-" (:end letters-block-2)))))

      (if (and clear-block-1 clear-block-2 numbers-block-1 letters-block-2)
        (let [;; Work backwards to preserve indices
              ;; First remove both clear blocks (later one first)
              content-after-remove-2 (str (subs content 0 (:start clear-block-2))
                                          (subs content (:end clear-block-2)))

              ;; Adjust clear-block-1 end if it was before clear-block-2
              clear-block-1-adjusted (if (< (:start clear-block-1) (:start clear-block-2))
                                       clear-block-1
                                       (update clear-block-1 :start #(- % (- (:end clear-block-2) (:start clear-block-2)))))

              content-after-remove-1 (str (subs content-after-remove-2 0 (:start clear-block-1-adjusted))
                                          (subs content-after-remove-2 (:end clear-block-1-adjusted)))

              ;; Now find insertion points in the modified content
              numbers-1-in-modified (extract-block content-after-remove-1
                                                    "[Desktop, Layer 1, Submode 1] Shift+mirror oneshot numbers")
              letters-2-in-modified (extract-block content-after-remove-1
                                                    "[Desktop, Layer 1, Submode 2] Shift oneshot (Fn+Space then letter = Shift+letter)")]

          (if (and numbers-1-in-modified letters-2-in-modified)
            (let [;; Insert clear blocks before their respective key blocks
                  ;; Do submode 2 first (it's later in the file)
                  content-with-clear-2 (str (subs content-after-remove-1 0 (:start letters-2-in-modified))
                                            (:text clear-block-2)
                                            "\n"
                                            (subs content-after-remove-1 (:start letters-2-in-modified)))

                  ;; Find numbers-1 again in modified content
                  numbers-1-final (extract-block content-with-clear-2
                                                  "[Desktop, Layer 1, Submode 1] Shift+mirror oneshot numbers")

                  content-final (str (subs content-with-clear-2 0 (:start numbers-1-final))
                                     (:text clear-block-1)
                                     "\n"
                                     (subs content-with-clear-2 (:start numbers-1-final)))]

              (spit output-file content-final)
              (println "\nBlocks reordered successfully!")
              (println (str "Written to " output-file)))
            (do
              (println "\nError: Could not find letter blocks in modified content")
              (System/exit 1))))
        (do
          (println "\nError: Could not find all required blocks")
          (System/exit 1))))))

(apply -main *command-line-args*)
