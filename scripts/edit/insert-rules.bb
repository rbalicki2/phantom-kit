#!/usr/bin/env bb

;; Insert EDN blocks into karabiner.edn
;;
;; Usage:
;;   bb insert-rules.bb <edn-file> <output-file> <rules-file-or-stdin>
;;
;; The rules will be appended and then automatically reordered by state.
;;
;; Examples:
;;   echo '{:des "temp" :rules [...]}' | bb insert-rules.bb src/karabiner.edn src/karabiner.edn -
;;   bb insert-rules.bb src/karabiner.edn src/karabiner.edn rules.edn

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

(defn parse-rules-input [content]
  "Parse input content - can be a single block or multiple blocks"
  (let [trimmed (str/trim content)]
    (if (str/starts-with? trimmed "{")
      ;; Single block
      [(edn/read-string trimmed)]
      ;; Multiple blocks or raw EDN content - try to parse
      (let [parsed (edn/read-string (str "[" trimmed "]"))]
        (if (every? map? parsed)
          parsed
          ;; Might be a vector of blocks already
          (if (and (= 1 (count parsed)) (vector? (first parsed)))
            (first parsed)
            parsed))))))

(defn -main [& args]
  (let [[input-file output-file rules-source] args]
    (when (or (nil? input-file) (nil? output-file) (nil? rules-source))
      (println "Usage: bb insert-rules.bb <input-edn> <output-edn> <rules-file-or-stdin>")
      (println "  Use '-' for rules-source to read from stdin")
      (System/exit 1))

    (let [;; Read existing config
          config (edn/read-string (slurp input-file))

          ;; Read new rules
          rules-content (if (= "-" rules-source)
                          (slurp *in*)
                          (slurp rules-source))
          ;; Filter out comment lines
          filtered-content (->> (str/split-lines rules-content)
                               (remove #(str/starts-with? (str/trim %) ";;"))
                               (str/join "\n"))

          new-blocks (parse-rules-input filtered-content)

          ;; Append to main
          updated-main (vec (concat (:main config) new-blocks))
          updated-config (assoc config :main updated-main)]

      ;; Write the updated config
      (spit output-file (pr-str updated-config))
      (println (str "Appended " (count new-blocks) " block(s) to " output-file))

      ;; Now run the reorder script to put everything in the right place
      (println "Running reorder-by-state.bb...")
      (let [result (clojure.java.shell/sh
                     "bb"
                     (str (System/getProperty "user.dir") "/scripts/edit/reorder-by-state.bb")
                     output-file
                     output-file)]
        (print (:out result))
        (when (not= 0 (:exit result))
          (print (:err result))
          (System/exit 1)))

      (println "Done!"))))

(apply -main *command-line-args*)
