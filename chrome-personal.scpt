tell application "System Events"
    tell process "Google Chrome"
        set windowNames to name of every window
        repeat with i from 1 to count of windowNames
            if item i of windowNames contains "(Personal)" then
                perform action "AXRaise" of window i
                set frontmost to true
                exit repeat
            end if
        end repeat
    end tell
end tell
