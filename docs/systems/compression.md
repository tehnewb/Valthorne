# Compression strategies

Author: Albert Beaupre

[System manual](README.md)

## Purpose

CompressionStrategy exposes byte-array compression and decompression. Concrete implementations wrap gzip, deflate, ZIP, BZIP2, LZMA, and XZ behavior. Choose the format required by the consuming file or protocol; similarly named algorithms and containers are not interchangeable byte streams.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Format-specific encoding | Each strategy produces its own encoded representation. |
| Decompression | The matching decoder expands bytes into independent output. |
| In-memory operation | These APIs accumulate complete results rather than exposing an incremental stream interface. |
| Failure translation | I/O or format failures are translated according to the concrete strategy. |

## Getting started

1. Determine the exact required format before choosing a strategy.
2. Compress the complete input byte array and store the format alongside it when the surrounding container requires that.
3. Decode with the matching implementation.
4. Handle corrupt-input failures and account for the expanded output size.

## Usage example

Place these statements in your initialization or application method; imports belong at the top of the Java file.

```java
import valthorne.compression.GZIP;
import java.nio.charset.StandardCharsets;

GZIP gzip = new GZIP();
byte[] original = "Valthorne".getBytes(StandardCharsets.UTF_8);
byte[] encoded = gzip.compress(original);
byte[] decoded = gzip.decompress(encoded);
String text = new String(decoded, StandardCharsets.UTF_8);
```

## Ownership and lifecycle

Inputs are generally not modified, but output allocation can be large. The strategy API does not by itself impose a universal decompressed-size limit or provide cancellation.

## Important behavior

- ZIP containers and raw deflate/gzip payloads require their matching readers.
- A small encoded input can expand into much larger output.
- Use the detailed class contract for headers, entries, and exception wrapping.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`BZIP2Strategy`](#type-bzip2strategy)
- [`CompressionStrategy`](#type-compressionstrategy)
- [`Deflate`](#type-deflate)
- [`GZIP`](#type-gzip)
- [`LZMAStrategy`](#type-lzmastrategy)
- [`XZStrategy`](#type-xzstrategy)
- [`ZIP`](#type-zip)

<a id="type-bzip2strategy"></a>

### BZIP2Strategy

[Source](../../src/main/java/valthorne/compression/BZIP2Strategy.java#L30)

The BZIP2Strategy class implements the CompressionStrategy interface, providing
methods for compressing and decompressing data using the BZIP2 compression format.

BZIP2 is a high-compression algorithm effective for compressing large datasets, offering
higher compression ratios compared to some other algorithms like GZIP, though it may
require more computational resources. This class uses Apache Commons Compress libraries
to handle BZIP2 compression (BZip2CompressorOutputStream) and decompression
(BZip2CompressorInputStream).

The class is designed to process data as byte arrays, which makes it suitable for scenarios
where in-memory compression and decompression are required for storage or transmission.

It handles potential IOException cases during the compression and decompression processes
by wrapping them in a RuntimeException, providing a simpler interface while ensuring robust
error handling.

<details>
<summary>BZIP2Strategy operation reference (2 declarations)</summary>

#### compress

```java
    public byte[] compress(byte[] data)
```

Encodes the complete input as a BZIP2 stream using the compressor's default
settings. The output stream is finalized before its bytes are returned. Input
contents remain unchanged, and each call uses independent temporary buffers.

- **`data`** — non-null uncompressed bytes

**Returns:** newly allocated, finalized BZIP2 payload

**Throws `RuntimeException`:** if the compressor reports an I/O failure; the cause is retained

#### decompress

```java
    public byte[] decompress(byte[] data)
```

Expands a BZIP2 payload into a new in-memory byte array. Bytes are copied
until the decoder reports end of input, with no configured output-size limit.
The supplied encoded array is not modified.

- **`data`** — non-null BZIP2 bytes

**Returns:** newly allocated decompressed contents

**Throws `RuntimeException`:** if malformed data or another decoder I/O failure prevents decoding

</details>

<a id="type-compressionstrategy"></a>

### CompressionStrategy

[Source](../../src/main/java/valthorne/compression/CompressionStrategy.java#L16)

The `CompressionStrategy` interface defines the contract for classes that implement various compression
and decompression algorithms. Classes implementing this interface should provide methods to encode (compress)
and decode (decompress) data, typically represented as byte arrays.

Implementations of this interface can be used to compress and decompress data in a consistent manner,
allowing for flexibility in choosing different compression algorithms while maintaining a common interface.
This is particularly useful when dealing with various data storage or transmission scenarios where different
compression techniques may be more suitable.

<details>
<summary>CompressionStrategy operation reference (8 declarations)</summary>

#### LZMA

```java
 LZMAStrategy LZMA
```

A predefined instance of the `LZMAStrategy` class, which implements the `CompressionStrategy`
interface for performing data compression and decompression using the LZMA (Lempel\u2013Ziv\u2013Markov chain algorithm) algorithm.

#### XZ

```java
 XZStrategy XZ
```

A predefined instance of the `XZStrategy` class, which implements the `CompressionStrategy`
interface for performing data compression and decompression using the XZ algorithm.

#### BZIP2

```java
 BZIP2Strategy BZIP2
```

A predefined instance of the `BZIP2Strategy` class, which implements the `CompressionStrategy`
interface for performing data compression and decompression using the BZIP2 algorithm.

#### GZIP

```java
 GZIP GZIP
```

A predefined instance of the GZIP class, which implements the CompressionStrategy
interface for performing compression and decompression using the GZIP algorithm.

#### DEFLATE

```java
 Deflate DEFLATE
```

A predefined instance of the Deflate class, which implements the CompressionStrategy
interface for performing data compression and decompression using the Deflate algorithm.

#### ZIP

```java
static ZIP ZIP(String entryName)
```

Creates a new instance of the ZIP class representing a compression strategy
that uses the ZIP format. The provided entry name is used as the identifier
for the compressed data entry.

- **`entryName`** — The name of the entry to be used in the ZIP-compressed data.

**Returns:** A new ZIP instance configured with the specified entry name.

#### compress

```java
byte[] compress(byte[] data)
```

Compresses the input data represented as a byte array.

- **`data`** — The input data to be compressed.

**Returns:** A byte array containing the compressed data.

#### decompress

```java
byte[] decompress(byte[] data)
```

Decompresses the input data represented as a byte array.

- **`data`** — The input data to be decompressed.

**Returns:** A byte array containing the decompressed data.

</details>

<a id="type-deflate"></a>

### Deflate

[Source](../../src/main/java/valthorne/compression/Deflate.java#L22)

The Deflate class implements the CompressionStrategy interface and provides methods
for compressing and decompressing data using the Deflate compression algorithm.

Deflate is a widely used lossless data compression algorithm supported by
various file formats and protocols, including ZIP files and HTTP compression. This class
uses the java.util.zip.Deflater and java.util.zip.Inflater classes to perform compression
and decompression, respectively.

<details>
<summary>Deflate operation reference (2 declarations)</summary>

#### compress

```java
@Override
    public byte[] compress(byte[] data)
```

Compresses the given byte array using the Deflate compression algorithm.

- **`data`** — The input data to be compressed.

**Returns:** The compressed data as a byte array.

**Throws `RuntimeException`:** If an error occurs during compression.

#### decompress

```java
@Override
    public byte[] decompress(byte[] data)
```

Decompresses the given byte array using the Deflate decompression algorithm.

- **`data`** — The compressed data to be decompressed.

**Returns:** The decompressed data as a byte array.

**Throws `RuntimeException`:** If an error occurs during decompression.

</details>

<a id="type-gzip"></a>

### GZIP

[Source](../../src/main/java/valthorne/compression/GZIP.java#L24)

The GZIP class implements the CompressionStrategy interface and provides methods
for compressing and decompressing data using the GZIP (GNU ZIP) compression algorithm.

GZIP is a widely used file compression format commonly found in various
file formats and protocols, including HTTP compression and compressed archive formats
like .gz and .tar.gz. This class uses Java's GZIPOutputStream and GZIPInputStream classes
to perform compression and decompression, respectively.

<details>
<summary>GZIP operation reference (2 declarations)</summary>

#### compress

```java
@Override
    public byte[] compress(byte[] data)
```

Compresses the given byte array using the GZIP compression algorithm.

- **`data`** — The input data to be compressed.

**Returns:** The compressed data as a byte array.

**Throws `RuntimeException`:** If an error occurs during compression.

#### decompress

```java
@Override
    public byte[] decompress(byte[] data)
```

Decompresses the given byte array using the GZIP decompression algorithm.

- **`data`** — The compressed data to be decompressed.

**Returns:** The decompressed data as a byte array.

**Throws `RuntimeException`:** If an error occurs during decompression.

</details>

<a id="type-lzmastrategy"></a>

### LZMAStrategy

[Source](../../src/main/java/valthorne/compression/LZMAStrategy.java#L28)

The LZMAStrategy class implements the CompressionStrategy interface and provides
methods for compressing and decompressing data using the LZMA (Lempel\u2013Ziv\u2013Markov chain algorithm) compression algorithm.

This implementation leverages the Apache Commons Compress library to handle LZMA compression and decompression.
LZMA is known for its high compression ratio and is commonly used for large-scale data compression needs
such as software distribution and archiving.

Compression is performed via an LZMACompressorOutputStream, whereas decompression utilizes an LZMACompressorInputStream.

This class is designed to process data represented as byte arrays and is suitable for scenarios where
efficient lossless compression and decompression operations are required.

<details>
<summary>LZMAStrategy operation reference (2 declarations)</summary>

#### compress

```java
    public byte[] compress(byte[] data)
```

Encodes the complete input as a LZMA stream using the compressor's default
settings. The output stream is finalized before its bytes are returned. Input
contents remain unchanged, and each call uses independent temporary buffers.

- **`data`** — non-null uncompressed bytes

**Returns:** newly allocated, finalized LZMA payload

**Throws `RuntimeException`:** if the compressor reports an I/O failure; the cause is retained

#### decompress

```java
    public byte[] decompress(byte[] data)
```

Expands a LZMA payload into a new in-memory byte array. Bytes are copied
until the decoder reports end of input, with no configured output-size limit.
The supplied encoded array is not modified.

- **`data`** — non-null LZMA bytes

**Returns:** newly allocated decompressed contents

**Throws `RuntimeException`:** if malformed data or another decoder I/O failure prevents decoding

</details>

<a id="type-xzstrategy"></a>

### XZStrategy

[Source](../../src/main/java/valthorne/compression/XZStrategy.java#L35)

The XZStrategy class implements the CompressionStrategy interface and provides methods
for compressing and decompressing data using the XZ compression algorithm.

XZ compression is an advanced general-purpose data compression algorithm
that provides high compression ratios. It is commonly used for compressing large files
and supports various compression levels. This implementation leverages Java's
XZCompressorOutputStream and XZCompressorInputStream classes to perform the operations.

This class is particularly useful for scenarios that require efficient compression
and decompression of byte array data in memory.

This class follows the typical flow of CompressionStrategy and allows easy
integration into systems that require multiple compression formats.

Note: Errors during compression or decompression are wrapped into RuntimeExceptions
for simplicity, but they are caused by underlying IOExceptions.

See also:
- org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
- org.apache.commons.compress.compressors.xz.XZCompressorInputStream

<details>
<summary>XZStrategy operation reference (2 declarations)</summary>

#### compress

```java
    public byte[] compress(byte[] data)
```

Encodes the complete input as a XZ stream using the compressor's default
settings. The output stream is finalized before its bytes are returned. Input
contents remain unchanged, and each call uses independent temporary buffers.

- **`data`** — non-null uncompressed bytes

**Returns:** newly allocated, finalized XZ payload

**Throws `RuntimeException`:** if the compressor reports an I/O failure; the cause is retained

#### decompress

```java
    public byte[] decompress(byte[] data)
```

Expands a XZ payload into a new in-memory byte array. Bytes are copied
until the decoder reports end of input, with no configured output-size limit.
The supplied encoded array is not modified.

- **`data`** — non-null XZ bytes

**Returns:** newly allocated decompressed contents

**Throws `RuntimeException`:** if malformed data or another decoder I/O failure prevents decoding

</details>

<a id="type-zip"></a>

### ZIP

[Source](../../src/main/java/valthorne/compression/ZIP.java#L31)

The ZIP class implements the CompressionStrategy interface and provides methods
for compressing and decompressing data using the ZIP compression format.

This implementation uses Java's ZipOutputStream and ZipInputStream classes to
perform compression and decompression, respectively. The ZIP format supports
archiving and compression and is commonly used for packaging multiple files
into a single compressed archive. However, this class focuses on single-entry
compression where data is stored under a specific entry name.

The class is designed to compress and decompress data in memory and is particularly
useful for scenarios where data needs to be compressed for storage or transmission.
The entry name provided when creating an instance of the class is used as the
identifier for the compressed data in the ZIP format.

<details>
<summary>ZIP operation reference (2 declarations)</summary>

#### compress

```java
@Override
    public byte[] compress(byte[] data)
```

Compresses the given byte array using the ZIP compression format.
The data is stored as a single ZIP entry named "data".

- **`data`** — The input data to be compressed.

**Returns:** The compressed data as a byte array in ZIP format.

**Throws `RuntimeException`:** If an error occurs during compression.

#### decompress

```java
@Override
    public byte[] decompress(byte[] data)
```

Decompresses the given ZIP-compressed byte array and returns the first entry's content.
Assumes the ZIP archive contains a single entry named "data".

- **`data`** — The compressed data in ZIP format.

**Returns:** The decompressed data as a byte array.

**Throws `RuntimeException`:** If an error occurs during decompression or if the entry is not found.

</details>

## Related guides

- [Binary buffers and byte order](buffers.md)
- [Classpath and filesystem utilities](files.md)
