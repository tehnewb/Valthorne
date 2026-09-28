# Persistent property sets

[System manual](README.md)

`PropertySet` stores named typed values for application settings. `PropertyValue`
wraps each value and its type. Choose binary persistence when type fidelity and
compact storage matter; choose text persistence when a person needs to inspect
or edit the settings.

## Binary format

`PropertySet.write(...)`, `toBytes()`, `read(...)`, and `fromBytes(...)` delegate
to `PropertySetIO`. The format includes a magic number, version, entry count,
and a type tag per value. Names are sorted before writing, so insertion order
does not change the bytes. Unsupported value types fail instead of being
silently converted to strings. A `DynamicByteBuffer` must use the same byte
order for writing and reading. Stream operations leave caller-owned streams
open; path operations own the streams they open.

## Text format

`writeText(...)`, `toText(...)`, `readText(...)`, and `fromText(...)` use
`PropertySetTextIO`. `PropertyTextOptions` controls text output. Use the
type-aware parser for round trips instead of treating the text as ordinary
`java.util.Properties` content. Validate untrusted files at the load boundary
and handle the reported I/O or format error.
