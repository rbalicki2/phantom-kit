import Cocoa

let pos = NSEvent.mouseLocation
let screenHeight = NSScreen.main?.frame.height ?? 0
// Convert from bottom-left to top-left coordinates
let cgPos = CGPoint(x: pos.x, y: screenHeight - pos.y)

let down = CGEvent(mouseEventSource: nil, mouseType: .rightMouseDown, mouseCursorPosition: cgPos, mouseButton: .right)
let up = CGEvent(mouseEventSource: nil, mouseType: .rightMouseUp, mouseCursorPosition: cgPos, mouseButton: .right)

down?.post(tap: .cghidEventTap)
usleep(10000) // 10ms
up?.post(tap: .cghidEventTap)
