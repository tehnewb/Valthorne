# Cache stores and checksums

Author: Albert Beaupre

[System manual](README.md)

## Purpose

CacheStore groups named archives, and each CacheArchive contains ordered CacheFiles. The checksum model records archive/file information for integrity and version comparisons. Use this system when packaging or exchanging structured binary asset groups rather than loading each resource as an unrelated file.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Store/archive/file hierarchy | The hierarchy groups payloads and provides stable organizational boundaries. |
| Compression identifiers | Each archive records how its payload is stored. |
| Serialization | Store operations encode and decode the supported cache format. |
| Checksums | ChecksumTable and row records describe CRC-based payload metadata and versions. |

## Getting started

1. Create the store hierarchy and assign archive/file names and payloads.
2. Choose a supported archive compression mode.
3. Save through the store API and retain version/checksum information where required.
4. Load the store, inspect checksum metadata, and retrieve the desired file payloads.

## Ownership and lifecycle

Byte arrays and archive arrays can be retained or exposed according to each constructor/accessor. Treat checksum data as a description of particular encoded contents; changing a payload requires rebuilding the relevant metadata.

## Important behavior

- CRC checksums detect accidental corruption but are not cryptographic authentication.
- Compression selection must match the serialized identifier.
- Do not assume changing an archive automatically refreshes an already captured checksum table.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`CacheArchive`](#type-cachearchive)
- [`CacheFile`](#type-cachefile)
- [`CacheStore`](#type-cachestore)
- [`ArchiveRow`](#type-archiverow)
- [`ChecksumTable`](#type-checksumtable)
- [`FileRow`](#type-filerow)

<a id="type-cachearchive"></a>

### CacheArchive

[Source](../../src/main/java/valthorne/cache/CacheArchive.java#L60)

A single named archive inside a `CacheStore`.

##### Example

```java
CacheArchive archive = new CacheArchive("ui");
archive.setCompression((byte) 1); // Example: gzip in your format.

archive.addFile(new CacheFile("button.png", buttonBytes));
archive.addFile(new CacheFile("panel.png", panelBytes));

// Store it.
CacheStore store = new CacheStore();
store.setVersion(1);
store.setArchives(archive);

// Encode for disk.
byte[] payload = CacheArchive.compress(archive);

// Decode from disk.
CacheArchive decoded = CacheArchive.decompress(payload, archive.getCompression());
CacheFile f = decoded.getFile("button.png");
```

##### Archive payload format

The (decompressed) archive payload begins with:

- `short`: file count

- `byte`: archive name byte length (UTF-8)

- `byte[]`: archive name bytes

Each file then stores:

- `int`: file data size

- `byte`: file name byte length (UTF-8)

- `byte[]`: file name bytes

- `byte[]`: file data bytes

The outer store decides whether the payload is compressed. Compression choice is stored in
`compression` and written by `CacheStore#save(java.nio.file.Path)`.

<details>
<summary>CacheArchive operation reference (12 declarations)</summary>

#### Constructor

```java
public CacheArchive(String name, CacheFile... files)
```

Creates a new archive with a name and an initial file array.

The provided `files` varargs becomes the backing array reference (no defensive copy).
If you want to prevent external mutation, pass a copied array.

- **`name`** — archive name
- **`files`** — initial files (may be empty)

#### decompress

```java
public static CacheArchive decompress(byte[] payload, byte compression)
```

Decodes an archive from payload bytes and compression id.

If `compression == NO_COMPRESSION`, `payload` is treated as already decompressed.
Otherwise, the appropriate `CompressionStrategy` is used to decompress the bytes first.

This then parses the archive payload format and constructs `CacheFile` objects for each file.
The returned archive has its `compression` field set to the provided id.

- **`payload`** — raw stored bytes (compressed or uncompressed)
- **`compression`** — compression id

**Returns:** decoded archive

#### compress

```java
public static byte[] compress(CacheArchive archive)
```

Encodes an archive into payload bytes, applying its configured compression if requested.

This always writes the decompressed payload format first (file count, archive name, files...).
If `compression` is `NO_COMPRESSION`, the raw payload bytes are returned directly.
Otherwise, the payload is compressed using the strategy returned by `getStrategy(int)`.

- **`archive`** — archive to encode

**Returns:** payload bytes (compressed or raw)

**Throws `RuntimeException`:** if the configured compression id is unsupported

#### addFile

```java
public void addFile(CacheFile file)
```

Appends a file to this archive, resizing the internal file array.

This performs an array grow-by-one and copies all existing references. The archive enforces
`Short.MAX_VALUE` because file counts are stored as a `short` in the payload.

- **`file`** — file to add

**Throws `IllegalArgumentException`:** if file count would exceed `Short.MAX_VALUE`

#### getFile

```java
public CacheFile getFile(String name)
```

Returns the first file whose name matches (case-insensitive).

This performs a linear search. If no file matches, returns `null`.

- **`name`** — file name to search

**Returns:** file if found, otherwise null

#### getFile

```java
public CacheFile getFile(int id)
```

Returns a file by numeric id (array index).

This is a direct indexed access. If the id is invalid, the JVM will throw
`ArrayIndexOutOfBoundsException`.

- **`id`** — file id (0-based)

**Returns:** file at that index

#### getFiles

```java
public CacheFile[] getFiles()
```

Returns the internal file array reference.

This is the live backing array. Modifying its contents will modify this archive. Copy it if you
need immutability.

**Returns:** file array

#### setFiles

```java
public void setFiles(CacheFile... files)
```

Replaces the file array used by this archive.

This is a direct assignment of the provided varargs array reference (no copy).
The archive enforces `Short.MAX_VALUE` because file counts are stored as a short on disk.

- **`files`** — new file array (varargs)

**Throws `IllegalArgumentException`:** if file count is >= `Short.MAX_VALUE`

#### getName

```java
public String getName()
```

Returns the archive name.

**Returns:** archive name

#### setName

```java
public void setName(String name)
```

Sets the archive name.

This validates against `Byte.MAX_VALUE` because the name length is written as a single byte
in the payload. If UTF-8 encoding could exceed this even when `name.length()` does not,
you may want to validate based on `name.getBytes(UTF_8).length` instead.

- **`name`** — new archive name

**Throws `IllegalArgumentException`:** if name length is >= `Byte.MAX_VALUE`

#### getCompression

```java
public byte getCompression()
```

Returns the compression id used for this archive when stored in a `CacheStore`.

**Returns:** compression id

#### setCompression

```java
public void setCompression(byte compression)
```

Sets the compression id for this archive.

Valid ids are currently 0..4 (NO_COMPRESSION, GZIP, BZIP2, LZMA, XZ).

- **`compression`** — compression id

**Throws `IllegalArgumentException`:** if id is outside the allowed range

</details>

<a id="type-cachefile"></a>

### CacheFile

[Source](../../src/main/java/valthorne/cache/CacheFile.java#L25)

A single named file entry inside a `CacheArchive`.

##### Example

```java
CacheFile file = new CacheFile("player.png", playerPngBytes);
String name = file.getName();
byte[] data = file.getData();

file.setName("player_idle.png");
file.setData(otherBytes);
```

##### Notes

This class is intentionally minimal and acts as a simple data holder for file name + bytes.
Higher-level logic (compression, indexing, etc.) is handled by `CacheArchive` and `CacheStore`.

<details>
<summary>CacheFile operation reference (6 declarations)</summary>

#### Constructor

```java
public CacheFile()
```

Creates an empty file entry.

This is useful for serializers or frameworks that require a no-arg constructor.

#### Constructor

```java
public CacheFile(String name, byte[] data)
```

Creates a file entry with name and data.

This stores references as-is. If you need defensive copies, copy the array before passing it in.

- **`name`** — file name
- **`data`** — file bytes

#### getName

```java
public String getName()
```

Returns the file name.

**Returns:** name

#### setName

```java
public void setName(String name)
```

Sets the file name.

- **`name`** — new name

#### getData

```java
public byte[] getData()
```

Returns the file data bytes.

This returns the internal array reference. Copy it if you need immutability.

**Returns:** data bytes

#### setData

```java
public void setData(byte[] data)
```

Sets the file data bytes.

This stores the reference as-is. Copy the array if you need defensive storage.

- **`data`** — new data bytes

</details>

<a id="type-cachestore"></a>

### CacheStore

[Source](../../src/main/java/valthorne/cache/CacheStore.java#L72)

Top-level container for a Valthorne cache on disk.

##### Example

```java
// Build a store in memory.
CacheStore store = new CacheStore();
store.setVersion(1);

CacheArchive sprites = new CacheArchive("sprites",
        new CacheFile("player.png", playerPngBytes),
        new CacheFile("enemy.png", enemyPngBytes)
);
sprites.setCompression((byte) 1); // Example: GZIP in your archive format (see CacheArchive constants).

store.setArchives(sprites);

// Save to disk.
store.save(Path.of("assets/cache.vthn"));

// Load it back.
CacheStore loaded = CacheStore.load(Path.of("assets/cache.vthn"));
CacheArchive a = loaded.getArchive("sprites");
CacheFile f = a.getFile("player.png");
byte[] bytes = f.getData();

// Modify.
loaded.removeArchive("sprites");
loaded.addArchive(new CacheArchive("ui"));
loaded.save(Path.of("assets/cache.vthn"));
```

##### File format

- `short`: store version

- `short`: archive count

Each archive entry then stores:

- `byte`: compression id

- `int`: compressed payload byte length

- `byte[]`: payload bytes (compressed or raw depending on compression id)

Archive payload encoding/decoding is handled by `CacheArchive#compress(CacheArchive)` and
`CacheArchive#decompress(byte[], byte)`.

Alongside the cache file, `save(Path)` will also write a checksum table file:

- `cache.vthn` -> main cache

- `cache.chk` -> checksum table (manifest)

<details>
<summary>CacheStore operation reference (14 declarations)</summary>

#### load

```java
public static CacheStore load(Path path)
```

Loads a cache store from a file at the specified path.

This method reads the file data and attempts to decode it into a `CacheStore` instance.
If a matching checksum file exists in the same location with a ".chk" extension, it will also parse
and associate the checksum data with the loaded store.

- **`path`** — the path to the cache store file to be loaded

**Returns:** a `CacheStore` instance initialized from the file data

**Throws `RuntimeException`:** if an `IOException` occurs during file read operations

#### load

```java
public static CacheStore load(byte[] data)
```

Loads a cache store from its raw bytes.

This validates the version and archive count, then reads each archive payload
and delegates archive decoding to `CacheArchive#decompress(byte[], byte)`.

- **`data`** — raw file bytes

**Returns:** loaded cache store

#### getVersion

```java
public int getVersion()
```

Returns the store format version read from disk or set in memory.

**Returns:** version value stored in this instance

#### setVersion

```java
public void setVersion(int version)
```

Sets the store format version that will be written by `save(Path)`.

This value is stored as a `short` in the file. Keep it within 0..65535 if you treat it as
an unsigned short on read.

- **`version`** — store version

#### removeArchive

```java
public boolean removeArchive(int id)
```

Removes an archive by numeric id (array index), compacting the internal archive array.

If the store has no archives, this returns `false`. If the id is invalid, this throws
`IndexOutOfBoundsException`. On success, the internal array shrinks by 1 and all entries
after the removed id shift left by one position.

This is a structural removal. Any external code holding on to a previously obtained archive id
must be aware that ids can change after removal.

- **`id`** — archive id (0-based)

**Returns:** true if an archive was removed, false if the store had no archives

**Throws `IndexOutOfBoundsException`:** if id is not within [0, archives.length)

#### removeArchive

```java
public boolean removeArchive(String name)
```

Removes the first archive whose name matches (case-insensitive).

If the store has no archives, this returns `false`. If the name is null, this throws
`NullPointerException`. If no matching archive exists, this returns `false`.

Internally this performs a linear search and then delegates to `removeArchive(int)`.

- **`name`** — archive name to remove

**Returns:** true if removed, false if not found or store empty

**Throws `NullPointerException`:** if name is null

#### setArchive

```java
public void setArchive(int id, CacheArchive archive)
```

Replaces an archive at the given numeric id.

This does not resize the store. It only replaces the slot. If the store has no archives,
this throws `IllegalStateException`. If the id is invalid, this throws
`IndexOutOfBoundsException`. If the archive is null, this throws `NullPointerException`.

- **`id`** — archive id (0-based)
- **`archive`** — archive instance to store

**Throws `NullPointerException`:** if archive is null

**Throws `IllegalStateException`:** if the store contains no archives

**Throws `IndexOutOfBoundsException`:** if id is not within [0, archives.length)

#### setArchive

```java
public void setArchive(String name, CacheArchive archive)
```

Replaces the first archive whose name matches (case-insensitive).

This is intended for "swap in" behavior when you already know the archive exists.
If no archive matches the name, this throws `IllegalArgumentException`.

If the provided name is null or the provided archive is null, this throws `NullPointerException`.

- **`name`** — archive name to find
- **`archive`** — new archive instance to store

**Throws `NullPointerException`:** if name or archive is null

**Throws `IllegalArgumentException`:** if no archive with that name exists

#### getArchives

```java
public CacheArchive[] getArchives()
```

Returns the internal archive array reference.

This is the live backing array. Modifying its contents will modify this store. If you need
immutability, copy the array before exposing it.

**Returns:** internal archive array (may be null)

#### setArchives

```java
public void setArchives(CacheArchive... archives)
```

Replaces the archive array used by this store.

This is a direct assignment of the provided varargs array reference (no copy).
The store enforces `Short.MAX_VALUE` because the count is stored as a short on disk.

- **`archives`** — new archives backing array (varargs)

**Throws `IllegalArgumentException`:** if archive count is >= `Short.MAX_VALUE`

#### getArchive

```java
public CacheArchive getArchive(String name)
```

Returns the first archive whose name matches (case-insensitive).

This performs a linear search. If no archive matches, returns `null`. If name is null,
throws `NullPointerException`.

- **`name`** — archive name to search

**Returns:** archive if found, otherwise null

**Throws `NullPointerException`:** if name is null

#### getArchive

```java
public CacheArchive getArchive(int id)
```

Returns an archive by numeric id (array index).

This is a direct indexed access. If the id is invalid, throws `IndexOutOfBoundsException`.

- **`id`** — archive id (0-based)

**Returns:** archive at that index

**Throws `IndexOutOfBoundsException`:** if id is not within [0, archives.length)

#### addArchive

```java
public void addArchive(CacheArchive archive)
```

Appends an archive to the end of the store, resizing the internal array.

If the store currently has no archives, this allocates an array of size 1. Otherwise, it grows
by 1 and copies all existing references.

The store enforces a maximum count of `Short.MAX_VALUE` archives because the archive count
is stored as a `short` in the on-disk format.

- **`archive`** — archive to add

**Throws `NullPointerException`:** if archive is null

**Throws `IllegalArgumentException`:** if archive count would exceed `Short.MAX_VALUE`

#### save

```java
public void save(Path path)
```

Saves the current state of the cache store to the specified file path,
writing its archives and associated data. A corresponding checksum file
with the ".chk" extension is also created alongside the main file.

- **`path`** — the file path where the cache store and its checksum data will be written

**Throws `RuntimeException`:** if an `IOException` occurs during
any file write operations

</details>

<a id="type-archiverow"></a>

### ArchiveRow

[Source](../../src/main/java/valthorne/cache/checksum/ArchiveRow.java#L31)

Represents a checksum row for a single `CacheArchive`.

##### Example

```java
// Build a row (usually created by ChecksumTable.build()).
FileRow[] files = FileRow.EMPTY;
ArchiveRow row = new ArchiveRow("textures", (byte) 1, 0x12345678, 4096, files);

// Read fields directly (records expose accessor methods).
System.out.println(row.name());
System.out.println(row.archiveCrc32());
```

##### What this record does

`ArchiveRow` is a compact container for archive-level integrity metadata:
name, compression ID, CRC32, length, and optional per-file rows.

- **`name`** — archive name (null becomes "")
- **`compression`** — compression ID used to produce the served bytes
- **`archiveCrc32`** — CRC32 of the served archive bytes
- **`archiveLength`** — length of the served archive bytes
- **`files`** — optional per-file rows (null becomes `FileRow#EMPTY`)

<details>
<summary>ArchiveRow operation reference (1 declarations)</summary>

#### Constructor

```java
public ArchiveRow(String name, byte compression, int archiveCrc32, int archiveLength, FileRow[] files)
```

Canonical constructor with null-safe normalization for name and files.

Records allow you to validate/normalize inputs inside the canonical constructor.
This implementation ensures that `name` is never null and `files` is never null.

- **`name`** — archive name (null becomes "")
- **`compression`** — compression ID used to produce the served bytes
- **`archiveCrc32`** — CRC32 of the served archive bytes
- **`archiveLength`** — length of the served archive bytes
- **`files`** — per-file rows (null becomes `FileRow#EMPTY`)

</details>

<a id="type-checksumtable"></a>

### ChecksumTable

[Source](../../src/main/java/valthorne/cache/checksum/ChecksumTable.java#L91)

Builds and verifies a CRC32-based checksum table for a `CacheStore`.

##### Example

```java
// Build a cache store (or load one).
CacheStore store = CacheStore.load(Path.of("cache.dat"));

// Build a checksum table for the current store (include file rows for per-file validation).
ChecksumTable table = ChecksumTable.build(store, true);

// Encode to bytes (store alongside your cache file).
byte[] encoded = ChecksumTable.encode(table);
Files.write(Path.of("cache.chk"), encoded);

// Later: load and verify the checksum table bytes.
byte[] chkBytes = Files.readAllBytes(Path.of("cache.chk"));
ChecksumTable loaded = ChecksumTable.decode(chkBytes); // throws if CRC mismatches

// Compare two tables to find which archives changed.
ChecksumTable newer = ChecksumTable.build(store, false); // maybe you don't need file rows
String[] changed = newer.diffChangedArchives(loaded);

// Example: if changed contains "textures", re-download or rebuild that archive.
for (String name : changed) {
    System.out.println("Changed archive: " + name);
}
```

##### What this class does

`ChecksumTable` creates a compact, serializable snapshot of the integrity-relevant properties
of a `CacheStore`. For each `CacheArchive`, it records:

- Archive name

- Compression ID

- CRC32 of the **served bytes** (the compressed archive payload produced by `CacheArchive#compress(CacheArchive)`)

- Length of those served bytes

Optionally, it can also store per-file CRC32 and length for each `CacheFile` inside an archive.
This helps you detect which file(s) changed inside an archive when you already have the decompressed file data.

##### Encoding format

The encoded output is:

```java
[u16 storeVersion]
[i32 payloadCrc32]   // CRC32 of the payload bytes that follow
[payload...]
```

The payload itself contains a u16 archive count, then for each archive:

```java
[u8 nameLen][name UTF-8 bytes...]
[u8 compression]
[i32 archiveCrc32]
[i32 archiveLength]
[u16 fileCount]
  repeated fileCount times:
    [u8 nameLen][name UTF-8 bytes...]
    [i32 fileCrc32]
    [i32 fileLength]
```

Names are stored with a 1-byte length prefix (0..255). Longer names throw an exception when encoding.

<details>
<summary>ChecksumTable operation reference (8 declarations)</summary>

#### build

```java
public static ChecksumTable build(CacheStore store, boolean includeFileRows)
```

Builds a checksum table snapshot from a `CacheStore`.

Each archive row is computed from the bytes produced by `CacheArchive#compress(CacheArchive)`.
That means the CRC and length represent the exact bytes that would be written/served for that archive.
If the archive compression method changes, the bytes change, and the row is considered changed.

If `includeFileRows` is true, per-file CRC and length rows are generated from each file's raw data.
These do **not** affect archive CRC/length (which remains based on compressed archive bytes), but are
included in the encoded payload for deeper diffing/debugging.

- **`store`** — cache store to snapshot
- **`includeFileRows`** — whether to include per-file rows

**Returns:** new checksum table

**Throws `NullPointerException`:** if store is null

#### encode

```java
public static byte[] encode(ChecksumTable table)
```

Encodes a checksum table into bytes suitable for writing to disk or sending over the network.

The encoded stream starts with the store version (u16) and the payload CRC32 (i32),
followed by the payload bytes. The payload CRC32 is computed during encoding, so it is safe
to encode tables built in-memory or tables that were previously decoded.

- **`table`** — checksum table to encode

**Returns:** encoded bytes

**Throws `NullPointerException`:** if table is null

#### decode

```java
public static ChecksumTable decode(byte[] bytes)
```

Decodes a checksum table from bytes and verifies payload integrity.

This method validates the payload CRC32 stored in the header. If the CRC mismatches,
an `IllegalStateException` is thrown and the table is not returned.

- **`bytes`** — encoded checksum table bytes

**Returns:** decoded checksum table

**Throws `NullPointerException`:** if bytes is null

**Throws `IllegalStateException`:** if payload CRC does not match the expected CRC

#### getStoreVersion

```java
public int getStoreVersion()
```

Returns the `CacheStore` version that this table corresponds to.

This is whatever `CacheStore#getVersion()` returned at build time, or what was decoded from bytes.

**Returns:** store version

#### getTableCrc32

```java
public int getTableCrc32()
```

Returns the CRC32 of the encoded payload portion of this table.

This is the value written during `encode(ChecksumTable)` and verified during `decode(byte[])`.

**Returns:** payload CRC32

#### getArchiveRows

```java
public ArchiveRow[] getArchiveRows()
```

Returns the archive rows contained in this table.

The returned array is the internal backing array. Treat it as read-only.

**Returns:** archive rows (never null)

#### findArchive

```java
public ArchiveRow findArchive(String name)
```

Finds an archive row by name using case-insensitive comparison.

If the provided name is null, this returns null. If no archive matches, this returns null.

- **`name`** — archive name to search for

**Returns:** matching archive row, or null if not found

#### diffChangedArchives

```java
public String[] diffChangedArchives(ChecksumTable oldTable)
```

Produces a list of archive names that differ between this table and an older table.

This is designed for cache invalidation and patching workflows:
if an archive's compression changes, length changes, or CRC changes, it is reported as changed.
If the old table is null or empty, all current archives are returned.

This method only compares archive-level fields (name, compression, archiveLength, archiveCrc32).
It does not require file rows and does not compare file rows.

- **`oldTable`** — previous checksum table (may be null)

**Returns:** array of changed archive names (never null)

</details>

<a id="type-filerow"></a>

### FileRow

[Source](../../src/main/java/valthorne/cache/checksum/FileRow.java#L26)

Represents a checksum row for a single `CacheFile`.

##### Example

```java
FileRow row = new FileRow("player.png", 0xDEADBEEF, 1024);
System.out.println(row.name());
System.out.println(row.fileLength());
```

##### What this record does

`FileRow` stores a file name plus CRC32 and length for the file's raw data.
It is typically used for debugging, diagnostics, or deeper diffing when archive-level
checks are not enough.

- **`name`** — file name (null becomes "")
- **`fileCrc32`** — CRC32 of the raw file bytes
- **`fileLength`** — length of the raw file bytes

<details>
<summary>FileRow operation reference (2 declarations)</summary>

#### EMPTY

```java
public static final  FileRow[] EMPTY
```

Shared empty file row array to avoid allocations.

#### Constructor

```java
public FileRow(String name, int fileCrc32, int fileLength)
```

Canonical constructor with null-safe normalization for name.

Ensures `name` is never null so consumers can safely call `name()`.

- **`name`** — file name (null becomes "")
- **`fileCrc32`** — CRC32 of the raw file bytes
- **`fileLength`** — length of the raw file bytes

</details>

## Related guides

- [Compression strategies](compression.md)
- [Binary buffers and byte order](buffers.md)
- [Classpath and filesystem utilities](files.md)
- [Asset loading and caching](assets.md)
