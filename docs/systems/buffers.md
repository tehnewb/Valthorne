# Binary buffers and byte order

Author: Albert Beaupre

[System manual](README.md)

## Purpose

DynamicByteBuffer provides growable byte storage with independent read/write positions and support for typed values and bit operations. ByteOrder selects multibyte ordering. This is useful for custom binary formats where fields must be encoded predictably.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Read/write cursors | Reading and writing advance different positions within shared storage. |
| Primitive values | Typed operations encode numbers using the configured byte order. |
| Bit packing | Bit buffers accumulate partial-byte reads and writes. |
| Capacity and extraction | Growth and output-copy operations separate valid data from unused capacity. |

## Getting started

1. Define the binary format's byte order and field sequence before writing.
2. Write fields through typed or bit APIs, completing partial-bit state as the contract requires.
3. Extract only the written payload for storage or transmission.
4. Set up the correct read position and decode fields in the same order and units.

## Ownership and lifecycle

The buffer owns its storage but can expose live arrays depending on the accessor. Reusing the object requires deliberate cursor and bit-state reset; changing byte order halfway through a format changes interpretation.

## Important behavior

- Capacity is not the number of written bytes.
- Mixing byte and bit operations requires attention to buffered partial bytes.
- Bounds and string encodings follow individual method contracts, not an implicit external protocol.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`ByteOrder`](#type-byteorder)
- [`DynamicByteBuffer`](#type-dynamicbytebuffer)

<a id="type-byteorder"></a>

### ByteOrder

[Source](../../src/main/java/valthorne/io/buffer/ByteOrder.java#L10)

Selects the ordering of bytes in multi-byte values read or written by
`DynamicByteBuffer`. The choice changes byte significance within a value;
it does not reverse the sequence of values or affect single-byte operations.

<details>
<summary>ByteOrder operation reference (2 declarations)</summary>

#### BIG_ENDIAN

```java
public static final  ByteOrder BIG_ENDIAN
```

Big-endian order (the most significant byte first).

#### LITTLE_ENDIAN

```java
public static final  ByteOrder LITTLE_ENDIAN
```

Little-endian order (the least significant byte first).

</details>

<a id="type-dynamicbytebuffer"></a>

### DynamicByteBuffer

[Source](../../src/main/java/valthorne/io/buffer/DynamicByteBuffer.java#L62)

A utility class for reading and writing various data types to a byte buffer with support for bit-level operations.

The `DynamicByteBuffer` class provides methods to serialize and deserialize primitive data types (byte, char, short,
int, long, float, double, boolean), strings (UTF-8 encoded with a length prefix), and individual bits to/from a byte array.
It maintains separate read and write positions, allowing sequential access to the buffer. The buffer supports configurable
byte order (big-endian or little-endian) for multibyte types, with big-endian as the default.

Key features include:

- Support for reading and writing primitive types with bounds checking.

- UTF-8 string handling with an integer length prefix.

- Bit-level operations for compact data encoding.

- Bulk byte array operations for efficient data transfer.

- Dynamic resizing of the buffer when write operations exceed capacity.

- Utility methods for position management, slicing, and buffer reset.

- Configurable byte order (big-endian or little-endian) for multi-byte types.

**Thread Safety:** This class is not thread-safe. If multiple threads access a `DynamicByteBuffer` instance
concurrently, external synchronization is required to prevent data corruption or inconsistent state.

**Usage Example:**

```java
byte[] buffer = new byte[100];
DynamicByteBuffer dbb = new DynamicByteBuffer(buffer);
dbb.setByteOrder(DynamicByteBuffer.ByteOrder.LITTLE_ENDIAN)
   .writeInt(12345)
   .writeString("Hello")
   .writeBit(true)
   .flushBits();
dbb.setReadPosition(0);
System.out.println(dbb.readInt());    // Outputs: 12345
System.out.println(dbb.readString()); // Outputs: Hello
System.out.println(dbb.readBit());    // Outputs: true
```

**Note:** When performing bit-level operations, ensure to call `flushBits()` after writing bits to ensure all
buffered bits are written to the underlying byte array. Reading or writing bits after changing the read or write position
resets the bit buffers to maintain consistency. The byte order affects only multi-byte types (char, short, int, long,
float, double, string length prefix); single-byte and bit operations are unaffected.

<details>
<summary>DynamicByteBuffer operation reference (50 declarations)</summary>

#### Constructor

```java
public DynamicByteBuffer()
```

Constructs a new instance of DynamicByteBuffer with an initial capacity of 16 bytes.
This constructor initializes the internal buffer to a default size, allowing for dynamic resizing
as new data is added.

#### Constructor

```java
public DynamicByteBuffer(int capacity)
```

Constructs a new DynamicByteBuffer with the specified initial capacity.

- **`capacity`** — the initial capacity of the byte buffer

#### Constructor

```java
public DynamicByteBuffer(byte[] buffer)
```

Constructs a new `DynamicByteBuffer` with the specified byte array.

The buffer is used directly (not copied), and the read and write positions are initialized to 0.
The bit buffers are initialized to empty states. The byte order is set to big-endian by default.

- **`buffer`** — the byte array to use as the underlying storage

**Throws `NullPointerException`:** if `buffer` is `null`

#### setByteOrder

```java
public DynamicByteBuffer setByteOrder(ByteOrder order)
```

Sets the byte order for multi-byte data types (char, short, int, long, float, double, string length prefix).

The byte order affects how multi-byte values are read from or written to the buffer. Single-byte operations
(byte, boolean) and bit operations are unaffected. The default byte order is big-endian.

- **`order`** — the byte order to use (`ByteOrder#BIG_ENDIAN` or `ByteOrder#LITTLE_ENDIAN`)

**Returns:** this buffer, for method chaining

**Throws `NullPointerException`:** if `order` is `null`

#### readByte

```java
public byte readByte()
```

Reads a single byte from the buffer at the current read position and advances the read position.

**Returns:** the byte value read from the buffer

**Throws `BufferUnderflowException`:** if there are no bytes remaining in the buffer

#### writeByte

```java
public DynamicByteBuffer writeByte(byte value)
```

Writes a single byte to the buffer at the current write position and advances the write position.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the byte value to write

**Returns:** this buffer, for method chaining

#### readChar

```java
public char readChar()
```

Reads a 16-bit character (2 bytes) from the buffer in the configured byte order and advances the read position.

**Returns:** the character read from the buffer

**Throws `BufferUnderflowException`:** if there are fewer than 2 bytes remaining in the buffer

#### writeChar

```java
public DynamicByteBuffer writeChar(char value)
```

Writes a 16-bit character (2 bytes) to the buffer in the configured byte order and advances the write position.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the character to write

**Returns:** this buffer, for method chaining

#### readShort

```java
public short readShort()
```

Reads a 16-bit short integer (2 bytes) from the buffer in the configured byte order and advances the read position.

**Returns:** the short integer read from the buffer

**Throws `BufferUnderflowException`:** if there are fewer than 2 bytes remaining in the buffer

#### readUnsignedShort

```java
public int readUnsignedShort()
```

Reads a 16-bit unsigned short integer (2 bytes) from the buffer in the configured byte order and advances the read position.

**Returns:** the unsigned short integer read from the buffer as an int

**Throws `BufferUnderflowException`:** if there are fewer than 2 bytes remaining in the buffer

#### writeUnsignedShort

```java
public DynamicByteBuffer writeUnsignedShort(int value)
```

Writes a 16-bit unsigned short integer (2 bytes) to the buffer in the configured byte order and advances the write position.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the unsigned short integer to write (0 to 65535)

**Returns:** this buffer, for method chaining

**Throws `IllegalArgumentException`:** if `value` is negative or greater than 65535

#### readUnsignedInt

```java
public long readUnsignedInt()
```

Reads a 32-bit unsigned integer (4 bytes) from the buffer in the configured byte order and advances the read position.

**Returns:** the unsigned integer read from the buffer as a long

**Throws `BufferUnderflowException`:** if there are fewer than 4 bytes remaining in the buffer

#### writeUnsignedInt

```java
public DynamicByteBuffer writeUnsignedInt(long value)
```

Writes a 32-bit unsigned integer (4 bytes) to the buffer in the configured byte order and advances the write position.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the unsigned integer to write (0 to 4294967295)

**Returns:** this buffer, for method chaining

**Throws `IllegalArgumentException`:** if `value` is negative or greater than 4294967295

#### writeShort

```java
public DynamicByteBuffer writeShort(short value)
```

Writes a 16-bit short integer (2 bytes) to the buffer in the configured byte order and advances the write position.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the short integer to write

**Returns:** this buffer, for method chaining

#### readInt

```java
public int readInt()
```

Reads a 32-bit integer (4 bytes) from the buffer in the configured byte order and advances the read position.

**Returns:** the integer read from the buffer

**Throws `BufferUnderflowException`:** if there are fewer than 4 bytes remaining in the buffer

#### writeInt

```java
public DynamicByteBuffer writeInt(int value)
```

Writes a 32-bit integer (4 bytes) to the buffer in the configured byte order and advances the write position.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the integer to write

**Returns:** this buffer, for method chaining

#### readLong

```java
public long readLong()
```

Reads a 64-bit long integer (8 bytes) from the buffer in the configured byte order and advances the read position.

**Returns:** the long integer read from the buffer

**Throws `BufferUnderflowException`:** if there are fewer than 8 bytes remaining in the buffer

#### writeLong

```java
public DynamicByteBuffer writeLong(long value)
```

Writes a 64-bit long integer (8 bytes) to the buffer in the configured byte order and advances the write position.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the long integer to write

**Returns:** this buffer, for method chaining

#### readFloat

```java
public float readFloat()
```

Reads a 32-bit floating-point number (4 bytes) from the buffer by reading an integer and converting it to a float.

**Returns:** the float value read from the buffer

**Throws `BufferUnderflowException`:** if there are fewer than 4 bytes remaining in the buffer

#### writeFloat

```java
public DynamicByteBuffer writeFloat(float value)
```

Writes a 32-bit floating-point number (4 bytes) to the buffer by converting it to an integer and writing it.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the float value to write

**Returns:** this buffer, for method chaining

#### readDouble

```java
public double readDouble()
```

Reads a 64-bit double-precision floating-point number (8 bytes) from the buffer by reading a long and converting it to a double.

**Returns:** the double value read from the buffer

**Throws `BufferUnderflowException`:** if there are fewer than 8 bytes remaining in the buffer

#### writeDouble

```java
public DynamicByteBuffer writeDouble(double value)
```

Writes a 64-bit double-precision floating-point number (8 bytes) to the buffer by converting it to a long and writing it.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the double value to write

**Returns:** this buffer, for method chaining

#### readBoolean

```java
public boolean readBoolean()
```

Reads a boolean value from the buffer by reading a single byte (non-zero is `true`, zero is `false`).

**Returns:** the boolean value read from the buffer

**Throws `BufferUnderflowException`:** if there are no bytes remaining in the buffer

#### writeBoolean

```java
public DynamicByteBuffer writeBoolean(boolean value)
```

Writes a boolean value to the buffer as a single byte (1 for `true`, 0 for `false`).

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the boolean value to write

**Returns:** this buffer, for method chaining

#### readString

```java
public String readString(int length)
```

Reads a UTF-8 encoded string from the buffer.

The string is prefixed with a 32-bit integer (in the configured byte order) indicating the number of bytes in the encoded string.
The method reads this length, then reads the specified number of bytes and decodes them as a UTF-8 string.

**Returns:** the string read from the buffer

**Throws `BufferUnderflowException`:** if there are not enough bytes to read the length or the string data

**Throws `IllegalArgumentException`:** if the length is negative

#### writeString

```java
public DynamicByteBuffer writeString(String value)
```

Writes a UTF-8 encoded string to the buffer.

The string is encoded as UTF-8 bytes, and the length of these bytes is written as a 32-bit integer (in the configured byte order)
before the bytes themselves. If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`value`** — the string to write

**Returns:** this buffer, for method chaining

**Throws `IllegalArgumentException`:** if `value` is `null`

#### readBytes

```java
public byte[] readBytes(int length)
```

Reads a specified number of bytes from the buffer into a new byte array and advances the read position.

- **`length`** — the number of bytes to read

**Returns:** a new byte array containing the read bytes

**Throws `BufferUnderflowException`:** if there are fewer than `length` bytes remaining in the buffer

**Throws `IllegalArgumentException`:** if `length` is negative

#### writeBytes

```java
public DynamicByteBuffer writeBytes(byte[] bytes)
```

Writes all bytes from the specified byte array to the buffer and advances the write position.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`bytes`** — the byte array to write

**Returns:** this buffer, for method chaining

**Throws `NullPointerException`:** if `bytes` is `null`

#### writeBytes

```java
public DynamicByteBuffer writeBytes(byte[] bytes, int offset, int length)
```

Writes a portion of the specified byte array to the buffer and advances the write position.

If the buffer is too small, it is dynamically resized to accommodate the write operation.

- **`bytes`** — the byte array to write
- **`offset`** — the starting index in the byte array
- **`length`** — the number of bytes to write

**Returns:** this buffer, for method chaining

**Throws `NullPointerException`:** if `bytes` is `null`

**Throws `IllegalArgumentException`:** if `offset` or `length` is negative, or if
`offset + length` exceeds the array length

#### readBit

```java
public boolean readBit()
```

Reads a single bit from the buffer and advances the bit position.

Bits are read from bytes in most-significant-bit-first order. When the bit buffer is empty, a new byte is read from
the buffer, and 8 bits are available for reading.

**Returns:** `true` if the bit is 1, `false` if the bit is 0

**Throws `BufferUnderflowException`:** if there are no bytes remaining in the buffer when a new byte is needed

#### writeBit

```java
public DynamicByteBuffer writeBit(boolean bit)
```

Writes a single bit to the buffer and advances the bit position.

Bits are accumulated in the bit write buffer in the most-significant-bit-first order. When 8 bits are accumulated, they
are written as a byte to the buffer, and the bit buffer is cleared. If the buffer is too small, it is dynamically
resized to accommodate the write operation.

- **`bit`** — the bit to write (`true` for 1, `false` for 0)

**Returns:** this buffer, for method chaining

#### readBits

```java
public int readBits(int numBits)
```

Reads a specified number of bits from the buffer and returns them as an integer.

The bits are read in most-significant-bit-first order and assembled into the lower bits of the returned integer.
For example, reading 3 bits with values 1, 0, 1 returns the integer 5 (binary 101).

- **`numBits`** — the number of bits to read (0 to 32)

**Returns:** the integer value formed by the read bits

**Throws `IllegalArgumentException`:** if `numBits` is negative or greater than 32

**Throws `BufferUnderflowException`:** if there are insufficient bytes to read the required bits

#### writeBits

```java
public DynamicByteBuffer writeBits(int value, int numBits)
```

Writes a specified number of bits from an integer to the buffer.

The bits are taken from the lower `numBits` of the `value` parameter and written in
most-significant-bit-first order. For example, writing the value 5 (binary 101) with `numBits=3` writes
the bits 1, 0, 1.

- **`value`** — the integer containing the bits to write
- **`numBits`** — the number of bits to write (0 to 32)

**Returns:** this buffer, for method chaining

**Throws `IllegalArgumentException`:** if `numBits` is negative or greater than 32

#### flushBits

```java
public DynamicByteBuffer flushBits()
```

Flushes any remaining bits in the bit write buffer to the byte buffer, padding with zeros if necessary.

If fewer than 8 bits are in the bit write buffer, they are shifted left to align with the most significant bits of
a byte, and the byte is written to the buffer. This method should be called after writing bits to ensure all bits are
persisted. If the buffer is too small, it is dynamically resized to accommodate the write operation.

**Returns:** this buffer, for method chaining

#### getReadPosition

```java
public int getReadPosition()
```

Returns the current read position in the buffer.

**Returns:** the current read position

#### setReadPosition

```java
public void setReadPosition(int position)
```

Sets the read position to the specified value.

This also resets the bit read buffer to ensure consistency in bit-level operations.

- **`position`** — the new read position

**Throws `IllegalArgumentException`:** if `position` is negative or greater than the buffer length

#### hasRemaining

```java
public boolean hasRemaining()
```

Checks if there are any bytes remaining to read from the current read position to the end of the buffer.

This method returns `true` if the current read position is less than the buffer's length,
indicating that there are more bytes available to read, and `false` otherwise.

**Returns:** `true` if there are remaining bytes to read, `false` otherwise

#### getWritePosition

```java
public int getWritePosition()
```

Returns the current write position in the buffer.

**Returns:** the current write position

#### setWritePosition

```java
public void setWritePosition(int position)
```

Sets the write position to the specified value.

This also resets the bit write buffer to ensure consistency in bit-level operations.

- **`position`** — the new write position

**Throws `IllegalArgumentException`:** if `position` is negative or greater than the buffer length

#### slice

```java
public DynamicByteBuffer slice(int start, int length)
```

Creates a new `DynamicByteBuffer` containing a copy of a portion of this buffer's data.

The new buffer is independent of this buffer, and its read and write positions are initialized to 0.

- **`start`** — the starting index of the slice
- **`length`** — the length of the slice

**Returns:** a new `DynamicByteBuffer` containing the sliced data

**Throws `IllegalArgumentException`:** if `start` or `length` is negative, or if
`start + length` exceeds the buffer length

#### clear

```java
public DynamicByteBuffer clear()
```

Clears the buffer by resetting all positions and bit buffers and filling the buffer with zeros.

**Returns:** this buffer, for method chaining

#### resetPositions

```java
public DynamicByteBuffer resetPositions()
```

Resets the read and write positions and bit buffers to their initial states without modifying the buffer's contents.

**Returns:** this buffer, for method chaining

#### getData

```java
public byte[] getData()
```

Retrieves the data stored in the buffer.

**Returns:** A byte array representing the data contained in the buffer.

#### toTrimmedWriteArray

```java
public byte[] toTrimmedWriteArray()
```

Returns a trimmed copy of the internal buffer, up to the current write position.

This is the method you want after writing. It flushes any pending bits first so the
returned byte array includes all data.

**Returns:** a new byte array containing bytes [0, writePosition)

#### toTrimmedReadArray

```java
public byte[] toTrimmedReadArray()
```

Returns a trimmed copy of the internal buffer, up to the current read position.

This is useful if you want "what has been consumed so far" during reading.

**Returns:** a new byte array containing bytes [0, readPosition)

#### toTrimmedArray

```java
public byte[] toTrimmedArray()
```

Returns a trimmed copy of the internal buffer using whichever position is farther.

Useful when you don't care whether the buffer was primarily used for reading or writing.
If you wrote then read, this returns the larger of the two.

**Returns:** a new byte array containing bytes [0, max(readPosition, writePosition))

#### remainingRead

```java
public int remainingRead()
```

Returns the number of bytes remaining for reading from the current read position.

**Returns:** the number of readable bytes

#### remainingWrite

```java
public int remainingWrite()
```

Returns the number of bytes remaining for writing from the current write position.

**Returns:** the number of writable bytes

#### equals

```java
@Override
    public boolean equals(Object o)
```

Compares this buffer to another object for equality.

Two `DynamicByteBuffer` instances are equal if they have the same read and write positions, bit buffer states,
and identical buffer contents.

- **`o`** — the object to compare with

**Returns:** `true` if the objects are equal, `false` otherwise

#### hashCode

```java
@Override
    public int hashCode()
```

Computes a hash code for this buffer based on its state and contents.

**Returns:** the hash code

</details>

## Related guides

- [Bit fields and flags](bits.md)
- [Cache stores and checksums](cache.md)
- [Compression strategies](compression.md)
- [Classpath and filesystem utilities](files.md)
