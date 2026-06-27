#!/usr/bin/env bb
;; Ensure ~/.config/karabiner/karabiner.json is a SYMLINK to the generated copy
;; checked into this repo (generated/karabiner.json), which is the single real
;; artifact Karabiner-Elements reads.
;;
;; goku + the spit-based post-processors normally write THROUGH the symlink, so
;; the link survives a sync untouched. But if goku ever replaces the path with a
;; real file (atomic rename, etc.), this step repairs it: it copies that fresh
;; real file back into the repo first (so we never lose the just-generated
;; output), then restores the symlink. Idempotent — safe to run every sync.
;;
;; Run as the LAST step of `npm run sync`, after goku + post-processing.

(require '[clojure.java.io :as io])
(import '[java.nio.file Files Path Paths LinkOption]
        '[java.nio.file.attribute FileAttribute])

(def home (System/getenv "HOME"))
(def repo-json (str home "/code/voicemode/generated/karabiner.json"))
(def deployed  (str home "/.config/karabiner/karabiner.json"))

(defn ->path ^Path [s] (Paths/get s (into-array String [])))
(def no-follow (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))

(defn symlink? [s]
  (Files/isSymbolicLink (->path s)))

(defn symlink-target [s]
  (when (symlink? s)
    (str (Files/readSymbolicLink (->path s)))))

(defn -main []
  (let [dep (->path deployed)
        repo (->path repo-json)]
    ;; Make sure the parent dir exists.
    (Files/createDirectories (.getParent dep) (into-array FileAttribute []))
    (cond
      ;; Already the correct symlink — nothing to do.
      (= (symlink-target deployed) repo-json)
      (println "relink-karabiner-json: symlink already correct")

      :else
      (do
        ;; If the deployed path is a REAL file (goku replaced the link), it holds
        ;; the freshly generated output — preserve it into the repo before we
        ;; blow it away. If it's a stale/wrong symlink, just drop it.
        (when (and (Files/exists dep no-follow) (not (symlink? deployed)))
          (println "relink-karabiner-json: deployed path is a real file — copying into repo")
          (io/copy (io/file deployed) (io/file repo-json)))
        ;; Remove whatever is at the deployed path and recreate the symlink.
        (Files/deleteIfExists dep)
        (when-not (Files/exists repo no-follow)
          (throw (ex-info (str "repo json missing: " repo-json
                               " — run goku first") {})))
        (Files/createSymbolicLink dep repo (into-array FileAttribute []))
        (println (str "relink-karabiner-json: linked " deployed " -> " repo-json))))))

(-main)
