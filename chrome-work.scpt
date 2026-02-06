tell application "System Events"
    tell process "Google Chrome"
        set windowNames to name of every window
        repeat with i from 1 to count of windowNames
            set winName to item i of windowNames
            if winName contains "Robert" and winName does not contain "(Personal)" then
                perform action "AXRaise" of window i
                set frontmost to true
                exit repeat
            end if
        end repeat
    end tell
end tell
