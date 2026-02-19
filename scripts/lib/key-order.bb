#!/usr/bin/env bb
;; Canonical Physical Key Ordering
;;
;; Defines the physical layout order for RHS keys on Kinesis Advantage 360.
;; Used for sorting rules by key position (most specific modifiers first within each key).

(ns lib.key-order)

;; Physical key positions on RHS of Kinesis Advantage 360
;; Order: top-to-bottom, left-to-right within each row

(def physical-key-order
  "Ordered list of physical keys by position on keyboard"
  [;; Top row (number row)
   "6" "7" "8" "9" "0" "hyphen"
   ;; Upper row
   "y" "u" "i" "o" "p" "backslash"
   ;; Home row
   "h" "j" "k" "l" "semicolon" "quote"
   ;; Lower row
   "n" "m" "comma" "period" "slash" "right_shift"
   ;; Arrow row (below main keys)
   "up_arrow" "down_arrow" "open_bracket" "close_bracket"
   ;; Thumb cluster
   "spacebar" "return_or_enter" "page_up" "page_down" "right_command" "right_control"
   ;; Hotkeys (special Kinesis buttons)
   "hk3" "hk4"])

;; Fn layer produces F-keys. Same physical position order.
(def fn-key-order
  "Ordered list of Fn+key outputs (F-keys) by physical position"
  [;; Fn + top row -> F7-F12
   "f7" "f8" "f9" "f10" "f11" "f12"
   ;; Fn + upper row -> F15-F20
   "f15" "f16" "f17" "f18" "f19" "f20"
   ;; Fn + home row -> F21-F24, then Opt+F1, Opt+F2
   "f21" "f22" "f23" "f24" "!Of1" "!Of2"
   ;; Fn + lower row -> Opt+F3-F8 (note: right_shift has no Fn mapping)
   "!Of3" "!Of4" "!Of5" "!Of6" "!Of7" "!Of8"
   ;; Fn + arrow row -> Opt+F9-F12
   "!Of9" "!Of10" "!Of11" "!Of12"
   ;; Fn + thumb cluster -> Opt+F15-F18, plus special
   "!Of15" "!Of16" "!Of17" "!Of18"
   ;; Fn + hotkeys
   "!Of19" "!Of20"])

;; Combined order: physical keys first, then Fn-layer keys
(def all-keys-order
  "Complete ordered list of all keys by physical position"
  (vec (concat physical-key-order fn-key-order)))

;; Create index map for fast lookup
(def key-position-index
  "Map from key name to position index (lower = earlier in physical order)"
  (into {} (map-indexed (fn [idx k] [k idx]) all-keys-order)))

(defn get-key-position
  "Get the physical position index for a key. Returns 9999 for unknown keys."
  [key-name]
  (get key-position-index key-name 9999))

(defn compare-keys-by-position
  "Compare two keys by their physical position. Returns negative if a < b."
  [key-a key-b]
  (compare (get-key-position key-a) (get-key-position key-b)))
