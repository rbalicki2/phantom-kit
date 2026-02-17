# Hammerspoon docs: hs.axuielement
This module allows you to access the accessibility objects of running applications, their windows, menus, and other user interface elements that support the OS X accessibility API.

This module works through the use of axuielementObjects, which is the Hammerspoon representation for an accessibility object.  An accessibility object represents any object or component of an OS X application which can be manipulated through the OS X Accessibility API -- it can be an application, a window, a button, selected text, etc.  As such, it can only support those features and objects within an application that the application developers make available through the Accessibility API.

## API Reference
