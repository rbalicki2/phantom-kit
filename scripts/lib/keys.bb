;; Shared key mappings and utilities for Karabiner scripts
;;
;; Usage: (require '[lib.keys :as keys])

(ns lib.keys
  (:require [clojure.string :as str]))

;; === Kinesis Fn layer mapping ===
;; What the Kinesis keyboard sends when Fn+key is pressed

(def fn-to-physical
  "Map from F-key code (what Karabiner sees) to physical Fn+key"
  {;; Plain F-keys (Fn + top/middle row)
   "f15" "Fn+Y" "f16" "Fn+U" "f17" "Fn+I" "f18" "Fn+O" "f19" "Fn+P"
   "f20" "Fn+bksl" "f21" "Fn+hk4" "f22" "Fn+H" "f23" "Fn+J" "f24" "Fn+K"
   ;; Alt+F-keys (Fn + lower row, sends LOpt+F-key)
   "!Of1" "Fn+L" "!Of2" "Fn+;" "!Of3" "Fn+'" "!Of4" "Fn+N" "!Of5" "Fn+M"
   "!Of6" "Fn+," "!Of7" "Fn+." "!Of8" "Fn+/" "!Of9" "Fn+↑" "!Of10" "Fn+↓"
   "!Of11" "Fn+[" "!Of12" "Fn+]"
   ;; Alt+F-keys (Fn + thumb cluster)
   "!Of15" "Fn+PgUp" "!Of16" "Fn+PgDn" "!Of17" "Fn+Enter" "!Of18" "Fn+Space"
   ;; Hotkeys (special Kinesis buttons)
   "!Of19" "hk3" "!Of20" "hk4" "!Of22" "Fn+hk4"
   ;; Shift variants
   "!SOf19" "Shift+hk3" "!TOSf19" "Ctrl+Opt+Shift+F19"})

(def physical-to-fn
  "Reverse map: physical key to F-key code"
  (into {} (map (fn [[k v]] [v k]) fn-to-physical)))

;; === Mirror letter mapping ===
;; RHS key → LHS mirrored letter (for one-handed typing)

(def mirror-letters
  "RHS key → LHS mirrored letter"
  {"y" "t" "u" "r" "i" "e" "o" "w" "p" "q"
   "h" "g" "j" "f" "k" "d" "l" "s" "semicolon" "a"
   "n" "b" "m" "v" "comma" "c" "period" "x" "slash" "z"})

(def mirror-numbers
  "RHS number → LHS mirrored number"
  {"6" "5" "7" "4" "8" "3" "9" "2" "0" "1"})

(def mirror-all
  "All mirror mappings combined"
  (merge mirror-letters mirror-numbers))

;; === Key name translations ===

(def key-aliases
  "Long key names → short readable names"
  {"return_or_enter" "Enter"
   "delete_or_backspace" "Backspace"
   "delete_forward" "Delete"
   "spacebar" "Space"
   "grave_accent_and_tilde" "`"
   "open_bracket" "["
   "close_bracket" "]"
   "left_arrow" "←"
   "right_arrow" "→"
   "up_arrow" "↑"
   "down_arrow" "↓"
   "page_down" "PgDn"
   "page_up" "PgUp"
   "right_control" "RCtrl"
   "right_command" "RCmd"
   "left_option" "LOpt"
   "left_control" "LCtrl"
   "left_shift" "LShift"
   "right_shift" "RShift"
   "non_us_backslash" "ISO-bksl"
   "backslash" "bksl"
   "hyphen" "-"
   "equal_sign" "="
   "quote" "'"
   "vk_none" "blocked"})

(def modifier-aliases
  "Modifier shorthand → readable"
  {"!S" "Shift+" "!R" "RShift+" "!C" "Cmd+" "!Q" "RCmd+"
   "!T" "Ctrl+" "!O" "LOpt+" "!E" "ROpt+"
   "!CS" "Cmd+Shift+" "!SC" "Shift+Cmd+"
   "!SO" "Shift+LOpt+" "!OS" "LOpt+Shift+"
   "!CO" "Cmd+LOpt+" "!TO" "Ctrl+LOpt+"
   "!TC" "Ctrl+Cmd+" "!TOS" "Ctrl+LOpt+Shift+"
   "##" ""})

;; === Translation functions ===

(defn translate-key
  "Translate a key code to human-readable form"
  [key-code]
  (let [key-str (if (keyword? key-code) (name key-code) (str key-code))]
    (or
     ;; Check if it's an Fn-layer F-key
     (get fn-to-physical key-str)
     ;; Check aliases
     (get key-aliases key-str)
     ;; Handle modifier prefixes
     (when-let [[_ mods base] (re-matches #"(![A-Z]+)(.+)" key-str)]
       (str (get modifier-aliases mods mods) (translate-key base)))
     ;; Handle ## prefix (optional any)
     (when (str/starts-with? key-str "##")
       (translate-key (subs key-str 2)))
     ;; Default: return as-is
     key-str)))

(defn translate-output
  "Translate an output key, expanding modifiers"
  [key-code]
  (let [key-str (if (keyword? key-code) (name key-code) (str key-code))]
    (or
     (get key-aliases key-str)
     ;; Handle modifier prefixes for output
     (when-let [[_ mods base] (re-matches #"(![A-Z]+)(.+)" key-str)]
       (str (get modifier-aliases mods mods) (translate-output base)))
     key-str)))

;; === Layer names ===

(def layer-names
  {0 "Normal" 1 "Ins" 2 "Nav" 3 "Chrome" 4 "VSCode" 5 "TMUX"
   6 "Comma" 7 "L" 8 "Term" 9 "Admin" 10 "InApp" 11 "AppSwitcher"
   12 "WindowSwitcher" 13 "Label" 14 "L-Cmd" 15 "L-Cmd-Shift"
   16 "L-Ctrl" 17 "L-Ctrl-Shift" 18 "L-CtrlCmd" 19 "L-CtrlCmd-Shift"
   20 "L-CtrlOpt" 21 "L-CtrlOpt-Shift" 22 "L-Opt" 23 "L-Opt-Shift"
   24 "L-OptCmd" 25 "L-OptCmd-Shift" 26 "L-Hyper" 27 "L-Hyper-Shift"
   28 "Grid"})

(def submode-names
  {0 "base" 1 "shift-mirror-oneshot" 2 "shift-oneshot"
   3 "delete-chord" 4 "select-chord"})
