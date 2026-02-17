# Hammerspoon docs: hs.fs.xattr
Get and manipulate extended attributes for files and directories

This submodule provides functions for getting and setting the extended attributes for files and directories.  Access to extended attributes is provided through the Darwin xattr functions defined in the /usr/include/sys/xattr.h header. Attribute names are expected to conform to proper UTF-8 strings and values are represented as raw data -- in Lua raw data is presented as bytes in a string object but the bytes are not required to conform to proper UTF-8 byte code sequences. This module does not perform any encoding or decoding of the raw data.

## API Reference
