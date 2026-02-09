import Cocoa

let pos = NSEvent.mouseLocation
let screenHeight = NSScreen.main?.frame.height ?? 0
// Convert from bottom-left to top-left coordinates
let cgPos = CGPoint(x: pos.x, y: screenHeight - pos.y)

let down = CGEvent(mouseEventSource: nil, mouseType: .leftMouseDown, mouseCursorPosition: cgPos, mouseButton: .left)
let up = CGEvent(mouseEventSource: nil, mouseType: .leftMouseUp, mouseCursorPosition: cgPos, mouseButton: .left)

down?.post(tap: .cghidEventTap)
usleep(10000) // 10ms
up?.post(tap: .cghidEventTap)
