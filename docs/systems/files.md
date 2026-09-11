# Classpath and filesystem utilities

Author: Albert Beaupre

[System manual](README.md)

## Purpose

ValthorneFiles reads classpath resources; FileUtility provides broader file-related convenience operations. Choose the namespace based on where the data lives. A resource inside a JAR is not necessarily a normal filesystem file, so native libraries that require filenames may need extraction.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Classpath reads | Read bytes or decoded text through the defining class loader. |
| Resource streams | Open a stream when the caller needs incremental access. |
| Temporary extraction | Copy a packaged resource to a temporary filesystem path for path-only APIs. |
| Filesystem helpers | FileUtility handles its documented file operations and conversions. |
| Exceptions | Dedicated file exceptions preserve lookup or I/O failure context. |

## Getting started

1. Place packaged assets under the project's resource layout and use a classpath-relative name.
2. Use `readBytes` or `readString` for complete resources.
3. Use `openResource` with try-with-resources when retaining a stream.
4. Extract to a temporary file only when the receiving API needs a filesystem path.

## Ownership and lifecycle

Streams returned by openResource belong to the caller. Convenience reads close their streams. Temporary extraction creates an actual file and follows the documented deletion lifecycle; do not confuse its path with the original resource key.

## Important behavior

- ValthorneFiles trims and removes leading slashes but does not canonicalize all path syntax.
- An existence check does not guarantee a later read succeeds.
- Character decoding requires the correct charset; UTF-8 is the convenience default.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`ValthorneFileException`](#type-valthornefileexception)
- [`ValthorneFileNotFoundException`](#type-valthornefilenotfoundexception)
- [`ValthorneFiles`](#type-valthornefiles)
- [`FileUtility`](#type-fileutility)

<a id="type-valthornefileexception"></a>

### ValthorneFileException

[Source](../../src/main/java/valthorne/io/file/ValthorneFileException.java#L10)

Base runtime exception for Valthorne file operations.
Wraps file access or decoding failures without requiring checked exception
declarations. A supplied cause retains the underlying failure for diagnostics.

<details>
<summary>ValthorneFileException operation reference (2 declarations)</summary>

#### Constructor

```java
public ValthorneFileException(String message)
```

Creates a failure with a descriptive message and no explicit cause.

- **`message`** — explanation of the failed operation; may be null

#### Constructor

```java
public ValthorneFileException(String message, Throwable cause)
```

Creates a failure that preserves the underlying exception.

- **`message`** — explanation of the failed operation; may be null
- **`cause`** — underlying failure; may be null

</details>

<a id="type-valthornefilenotfoundexception"></a>

### ValthorneFileNotFoundException

[Source](../../src/main/java/valthorne/io/file/ValthorneFileNotFoundException.java#L10)

Thrown when a classpath resource cannot be found.
Allows callers to distinguish a missing resource from other file failures while
retaining the unchecked `ValthorneFileException` contract.

<details>
<summary>ValthorneFileNotFoundException operation reference (1 declarations)</summary>

#### Constructor

```java
public ValthorneFileNotFoundException(String message)
```

Creates a missing-resource failure with the supplied diagnostic text.
The constructor only records the message; it does not attempt another lookup.

- **`message`** — resource identifier and failure details; may be null

</details>

<a id="type-valthornefiles"></a>

### ValthorneFiles

[Source](../../src/main/java/valthorne/io/file/ValthorneFiles.java#L22)

Utility class for handling file operations related to classpath resources.
Provides methods for reading, checking existence, and extracting files
from the classpath into usable formats or temporary files.

All methods are static and primarily handle InputStream operations,
byte array conversions, String encoding, and temporary file creation.

<details>
<summary>ValthorneFiles operation reference (7 declarations)</summary>

#### readBytes

```java
public static byte[] readBytes(String resourcePath)
```

Reads the complete classpath resource into memory and closes its stream.
The returned array is independent of the stream and can be modified by the caller.
No size limit is imposed on resource contents.

- **`resourcePath`** — resource name relative to the classpath root

**Returns:** newly allocated resource bytes

**Throws `ValthorneFileException`:** if reading fails or the resource cannot be found

#### readString

```java
public static String readString(String resourcePath)
```

Reads the entire resource as UTF-8 text and closes its stream. Decoding follows
String's replacement behavior for malformed byte sequences.

- **`resourcePath`** — resource name relative to the classpath root

**Returns:** decoded resource contents

**Throws `ValthorneFileException`:** if reading fails or the resource cannot be found

#### readString

```java
public static String readString(String resourcePath, Charset charset)
```

Reads all resource bytes and decodes them with the supplied character set.
The source stream is closed by readBytes before decoding, and malformed byte
sequences follow String's replacement behavior.

- **`resourcePath`** — resource name relative to the classpath root
- **`charset`** — character set for decoding

**Returns:** decoded resource contents

**Throws `NullPointerException`:** if charset is null

**Throws `ValthorneFileException`:** if reading fails or the resource cannot be found

#### extractToTempPath

```java
public static String extractToTempPath(String resourcePath)
```

Extracts a classpath resource to a temp file and returns its absolute filesystem path.
Use this if an API only accepts a String path and cannot read from streams/bytes directly.

The temp file is marked deleteOnExit().

- **`resourcePath`** — path relative to classpath root (e.g. "data/test.json")

**Returns:** absolute filesystem path to the extracted temp file

#### extractToTempFile

```java
public static Path extractToTempFile(String resourcePath)
```

Extracts a classpath resource to a temp file and returns the Path.
The temp file is marked deleteOnExit().

- **`resourcePath`** — path relative to classpath root (e.g. "data/test.json")

#### exists

```java
public static boolean exists(String resourcePath)
```

Checks for a resource using this class's defining class loader without opening
its stream. A positive result indicates lookup success at this instant and does
not guarantee that a later read will succeed.

- **`resourcePath`** — resource name relative to the classpath root

**Returns:** whether class-loader lookup returns a resource URL

**Throws `IllegalArgumentException`:** if the resource name is null or empty

#### openResource

```java
public static InputStream openResource(String resourcePath)
```

Opens a classpath resource with this class's defining class loader.
The returned stream belongs to the caller and must be closed, preferably using
try-with-resources. Leading slashes and surrounding whitespace are normalized.

- **`resourcePath`** — resource name relative to the classpath root

**Returns:** newly opened resource stream

**Throws `IllegalArgumentException`:** if the resource name is null or empty

**Throws `ValthorneFileNotFoundException`:** if lookup finds no resource

</details>

<a id="type-fileutility"></a>

### FileUtility

[Source](../../src/main/java/valthorne/utility/FileUtility.java#L89)

##### FileUtility

`FileUtility` is a focused collection of static helpers for common file-system tasks that
tend to show up repeatedly in tools, launchers, editors, asset pipelines, cache systems,
packagers, and engine-side utilities. The class is intentionally centered around operations that
are usually more verbose when written directly with the standard JDK APIs, such as recursive
traversal, safe ZIP extraction, checksum generation, extension manipulation, and directory tree
copying or deletion.

This utility does **not** try to replace `Files`, `Path`, or other core NIO APIs.
Instead, it complements them. For simple direct tasks such as reading bytes, writing strings,
checking existence, or querying size and timestamps, the normal JDK APIs are still the best
choice. This class is meant for the slightly more structured and repeatable cases where you want
a single reusable method instead of rewriting the same traversal or archive logic each time.

##### What this class is good for

- Extracting or replacing file extensions

- Creating directories on demand

- Recursively deleting or copying directory trees

- Listing files recursively or filtering by extension

- Computing stable checksums such as SHA-256 for caching or validation

- Creating ZIP archives and extracting them safely

- Creating a `WatchService` for directory monitoring

##### Design notes

- This is a pure static utility class and cannot be instantiated.

- Methods validate important inputs with `Objects#requireNonNull(Object, String)` where appropriate.

- Recursive directory operations use `Files#walkFileTree(Path, java.nio.file.FileVisitor)` for reliability.

- ZIP extraction includes a path-normalization safety check to prevent ZIP Slip attacks.

##### Example

```java
Path assets = Paths.get("assets");
Path backupZip = Paths.get("backup/assets.zip");
Path extracted = Paths.get("temp/extracted-assets");

// Ensure destination directory exists.
FileUtility.ensureDirectoryExists(backupZip.getParent());

// Create an archive.
FileUtility.zipDirectory(assets, backupZip);

// Extract it safely.
FileUtility.unzip(backupZip, extracted);

// Find all PNG files.
List<Path> pngFiles = FileUtility.findFilesByExtension(extracted, "png");

// Compute a checksum for one of them.
if (!pngFiles.isEmpty()) {
    String sha256 = FileUtility.computeChecksum(pngFiles.get(0), "SHA-256");
    System.out.println("Checksum: " + sha256);
}

// Copy the extracted directory somewhere else.
FileUtility.copyDirectory(extracted, Paths.get("build/copied-assets"), true);
```

<details>
<summary>FileUtility operation reference (20 declarations)</summary>

#### getExtension

```java
public static String getExtension(String fileName)
```

Returns the extension of a file name without the leading dot.

This method works on the provided string exactly as given. It does not require the input to
be a real file on disk. The extension is determined from the last dot in the name.

The method returns an empty string when:

- The input is `null`

- The input is empty

- No dot is present

- The dot is the first character, such as `".gitignore"`

- The dot is the last character

- **`fileName`** — the raw file name or path string

**Returns:** the extension without the dot, or an empty string when no usable extension exists

#### getExtension

```java
public static String getExtension(File file)
```

Returns the extension of a `File`'s name without the leading dot.

This overload delegates to `getExtension(String)` using `File#getName()`.
Only the file name portion is examined.

- **`file`** — the file whose name should be inspected

**Returns:** the extension without the dot, or an empty string when no usable extension exists

**Throws `NullPointerException`:** if `file` is null

#### getExtension

```java
public static String getExtension(Path path)
```

Returns the extension of a `Path`'s file name without the leading dot.

This overload uses `Path#getFileName()` and delegates to `getExtension(String)`.
Only the last name element is inspected.

- **`path`** — the path whose file name should be inspected

**Returns:** the extension without the dot, or an empty string when no usable extension exists

**Throws `NullPointerException`:** if `path` is null

#### getBaseName

```java
public static String getBaseName(String fileName)
```

Returns the base name of a file name using the last dot as the extension separator.

For example, `"archive.tar.gz"` becomes `"archive.tar"`.
If no dot exists, the input is returned unchanged.

- **`fileName`** — the file name to inspect

**Returns:** the name up to the last dot, the original value when no dot exists, or `null` when input is null

#### removeExtension

```java
public static String removeExtension(String fileName)
```

Removes everything after the first dot in a file name.

This differs from `getBaseName(String)`. It removes **all** extension-like suffixes
after the first dot. For example, `"archive.tar.gz"` becomes `"archive"`.

- **`fileName`** — the file name to inspect

**Returns:** the name before the first dot, the original value when no dot exists, or `null` when input is null

#### changeExtension

```java
public static String changeExtension(String fileName, String newExt)
```

Replaces the extension of a file name.

The replacement extension may be provided with or without a leading dot. If the new
extension is `null` or empty, the returned value is simply the base name produced by
`getBaseName(String)`.

- **`fileName`** — the original file name
- **`newExt`** — the new extension, with or without a leading dot

**Returns:** the updated file name, or `null` when `fileName` is null

#### getParentPath

```java
public static String getParentPath(String filePath)
```

Returns the parent path string of a raw file path.

If the provided path has no parent, an empty string is returned. If the input itself is
`null`, this method returns `null`.

- **`filePath`** — the raw file path string

**Returns:** the parent path, an empty string when no parent exists, or `null` when input is null

#### ensureDirectoryExists

```java
public static void ensureDirectoryExists(Path dir) throws IOException
```

Ensures that a directory exists, creating it and all missing parents when necessary.

If the directory already exists, this method does nothing. This method is ideal before file
creation, ZIP extraction, tree copying, or any staged build output.

- **`dir`** — the directory to create if needed

**Throws `IOException`:** if the directory cannot be created

**Throws `NullPointerException`:** if `dir` is null

#### deleteQuietly

```java
public static boolean deleteQuietly(Path path)
```

Deletes a file or directory quietly.

This method attempts to delete the given path using `Files#deleteIfExists(Path)`.
It returns `true` when the path was deleted successfully or when it did not exist.
It returns `false` when an `IOException` occurs.

This is intended for best-effort cleanup operations where failure should not abort the caller.
It does not delete directory contents recursively.

- **`path`** — the path to delete

**Returns:** true when the path was deleted or did not exist, false when deletion failed

#### deleteRecursively

```java
public static void deleteRecursively(Path path) throws IOException
```

Recursively deletes an entire directory tree or a single file.

If the path does not exist, this method simply returns. For a directory, every nested file
is deleted first, followed by each directory on the way back out.

- **`path`** — the root path to delete

**Throws `IOException`:** if any delete operation fails

**Throws `NullPointerException`:** if `path` is null

#### listFiles

```java
public static List<Path> listFiles(Path dir) throws IOException
```

Lists only the regular files directly inside a directory.

This method is non-recursive. Subdirectories are ignored. The returned list is newly created
and may be empty when no files are present.

- **`dir`** — the directory to inspect

**Returns:** a list containing the regular files directly under the directory

**Throws `IOException`:** if the directory cannot be read

**Throws `NullPointerException`:** if `dir` is null

#### listFilesRecursively

```java
public static List<Path> listFilesRecursively(Path dir) throws IOException
```

Recursively lists every regular file under a directory tree.

The returned list contains files only. Directories themselves are not included.

- **`dir`** — the root directory to traverse

**Returns:** a list of all regular files found in the tree

**Throws `IOException`:** if traversal fails

**Throws `NullPointerException`:** if `dir` is null

#### findFilesByExtension

```java
public static List<Path> findFilesByExtension(Path dir, String ext) throws IOException
```

Recursively finds files whose extension matches the requested value.

Matching is case-insensitive. The requested extension may be provided with or without a
leading dot.

- **`dir`** — the root directory to traverse
- **`ext`** — the extension to match, with or without a leading dot

**Returns:** a list of matching files

**Throws `IOException`:** if traversal fails

**Throws `NullPointerException`:** if `dir` or `ext` is null

#### copyFile

```java
public static void copyFile(Path src, Path dest, boolean overwrite) throws IOException
```

Copies a single file to a destination path.

When `overwrite` is true, an existing destination file is replaced. When it is false,
the copy fails if the destination already exists.

- **`src`** — the source file
- **`dest`** — the destination file
- **`overwrite`** — true to replace an existing destination file

**Throws `IOException`:** if the copy fails

**Throws `NullPointerException`:** if `src` or `dest` is null

#### copyDirectory

```java
public static void copyDirectory(Path src, Path dest, boolean overwrite) throws IOException
```

Recursively copies an entire directory tree to a destination directory.

Directories are created as needed before nested files are copied. Destination file overwrite
behavior is controlled by the `overwrite` flag.

- **`src`** — the source root directory
- **`dest`** — the destination root directory
- **`overwrite`** — true to replace existing files

**Throws `IOException`:** if any part of the copy fails

**Throws `NullPointerException`:** if `src` or `dest` is null

#### move

```java
public static void move(Path src, Path target, boolean atomic) throws IOException
```

Moves or renames a file or directory.

When `atomic` is true, the method requests an atomic move through
`StandardCopyOption#ATOMIC_MOVE`. Whether that is supported depends on the file system.

- **`src`** — the source path
- **`target`** — the destination path
- **`atomic`** — true to request an atomic move

**Throws `IOException`:** if the move fails

**Throws `NullPointerException`:** if `src` or `target` is null

#### computeChecksum

```java
public static String computeChecksum(Path file, String algorithm) throws IOException, NoSuchAlgorithmException
```

Computes a message-digest checksum for a file and returns it as lowercase hexadecimal text.

The file is streamed through a `DigestInputStream`, so the full file does not need to
be loaded into memory at once. This makes the method suitable for larger files.

Common algorithm values include:

- `"MD5"`

- `"SHA-1"`

- `"SHA-256"`

- `"SHA-512"`

- **`file`** — the file whose checksum should be computed
- **`algorithm`** — the digest algorithm name

**Returns:** the checksum as lowercase hexadecimal text

**Throws `IOException`:** if the file cannot be read

**Throws `NoSuchAlgorithmException`:** if the requested digest algorithm is not available

**Throws `NullPointerException`:** if `file` or `algorithm` is null

#### zipDirectory

```java
public static void zipDirectory(Path srcDir, Path zipFile) throws IOException
```

Creates a ZIP archive from the full contents of a directory tree.

Only regular files are written as ZIP entries. Each entry is stored using a path relative to
the source directory root, which preserves the directory structure inside the archive.

- **`srcDir`** — the source directory to archive
- **`zipFile`** — the destination ZIP file path

**Throws `IOException`:** if archive creation fails

**Throws `NullPointerException`:** if `srcDir` or `zipFile` is null

#### unzip

```java
public static void unzip(Path zipFile, Path targetDir) throws IOException
```

Extracts a ZIP archive into a target directory.

This method creates directories as needed and replaces existing files when a ZIP entry
targets the same path.

For safety, each extracted path is normalized and verified to remain inside the requested
destination directory. This prevents ZIP Slip path traversal issues.

- **`zipFile`** — the ZIP archive to extract
- **`targetDir`** — the destination directory

**Throws `IOException`:** if extraction fails

**Throws `NullPointerException`:** if `zipFile` or `targetDir` is null

#### watchDirectory

```java
public static WatchService watchDirectory(Path dir, WatchEvent.Kind<?>... events) throws IOException
```

Creates and registers a `WatchService` for a directory.

The returned watcher is already registered for the provided event kinds. The caller is
responsible for:

- Polling or blocking on the watcher

- Resetting received keys after processing

- Closing the watcher when finished

- **`dir`** — the directory to watch
- **`events`** — the event kinds to register, such as create, delete, or modify

**Returns:** a ready-to-use watch service

**Throws `IOException`:** if the watch service cannot be created or registered

**Throws `NullPointerException`:** if `dir` is null

</details>

## Related guides

- [Asset loading and caching](assets.md)
- [Tiled maps and tilesets](tiled-maps.md)
- [Binary buffers and byte order](buffers.md)
