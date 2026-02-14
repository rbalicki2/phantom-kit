#!/usr/bin/env bb

;; Insert EDN blocks into karabiner.edn at the right location
;;
;; Usage:
;;   bb insert-rules.bb <edn-file> <output-file> <rules-file-or-stdin>
;;
;; The rules will be inserted BEFORE the "[Global] Block all unmapped keys" block.
;;
;; Examples:
;;   bb gen-oneshot-clear-rules.bb | bb insert-rules.bb src/karabiner.edn /tmp/new.edn -
;;   bb insert-rules.bb src/karabiner.edn /tmp/new.edn rules.edn

(require '[clojure.string :as str])

(defn find-insert-point [lines]
  "Find the line number to insert before (the Global Block line)"
  (first (keep-indexed
          (fn [idx line]
            (when (str/includes? line "[Global] Block all unmapped keys")
              idx))
          lines)))

(defn -main [& args]
  (let [[input-file output-file rules-source] args]
    (when (or (nil? input-file) (nil? output-file) (nil? rules-source))
      (println "Usage: bb insert-rules.bb <input-edn> <output-edn> <rules-file-or-stdin>")
      (println "  Use '-' for rules-source to read from stdin")
      (System/exit 1))

    (let [input-lines (str/split-lines (slurp input-file))
          rules-content (if (= "-" rules-source)
                         (slurp *in*)
                         (slurp rules-source))
          ;; Filter out comment lines from rules (Goku doesn't support ;;)
          rules-lines (->> (str/split-lines rules-content)
                          (remove #(str/starts-with? (str/trim %) ";;"))
                          (str/join "\n"))
          insert-idx (find-insert-point input-lines)]

      (if (nil? insert-idx)
        (do
          (println "Error: Could not find '[Global] Block all unmapped keys' block")
          (System/exit 1))
        (let [before-lines (take insert-idx input-lines)
              after-lines (drop insert-idx input-lines)
              new-content (str (str/join "\n" before-lines)
                              "\n"
                              rules-lines
                              "\n"
                              (str/join "\n" after-lines))]
          (spit output-file new-content)
          (println (str "Inserted rules before line " (inc insert-idx)))
          (println (str "Output written to " output-file)))))))

(apply -main *command-line-args*)
