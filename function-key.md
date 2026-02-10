# Function Key Behavior

## Current macOS Function Key Behavior

The `fn` (function/globe) key on Mac has special system-level behavior:

- **Tap**: Opens emoji picker / character viewer
- **Hold + letter**: Insert special characters (e.g., fn+e = é options)
- **Hold + number row**: F1-F12 keys (on keyboards where top row defaults to media keys)
- **Hold + arrow keys**: Page Up/Down, Home/End
- **Hold + Delete**: Forward delete
- **Double-tap**: Dictation (if enabled in System Preferences)

## Karabiner Notes

- Function key (`fn`) is tricky to remap in Karabiner
- It's handled at a lower level than most keys
- Some fn behaviors are hardcoded in macOS and can't be overridden

## Planned Changes

See pending task: "Remap: Quote → Shift, Shift → Function"

The goal is to make Shift key act as Function key, freeing up Quote to be the primary Shift.
