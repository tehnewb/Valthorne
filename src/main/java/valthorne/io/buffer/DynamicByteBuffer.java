package valthorne.io.buffer;

import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A utility class for reading and writing various data types to a byte buffer with support for bit-level operations.
 * <p>
 * The {@code DynamicByteBuffer} class provides methods to serialize and deserialize primitive data types (byte, char, short,
 * int, long, float, double, boolean), strings (UTF-8 encoded with a length prefix), and individual bits to/from a byte array.
 * It maintains separate read and write positions, allowing sequential access to the buffer. The buffer supports configurable
 * byte order (big-endian or little-endian) for multibyte types, with big-endian as the default.
 * </p>
 * <p>
 * Key features include:
 * <ul>
 *     <li>Support for reading and writing primitive types with bounds checking.</li>
 *     <li>UTF-8 string handling with an integer length prefix.</li>
 *     <li>Bit-level operations for compact data encoding.</li>
 *     <li>Bulk byte array operations for efficient data transfer.</li>
 *     <li>Dynamic resizing of the buffer when write operations exceed capacity.</li>
 *     <li>Utility methods for position management, slicing, and buffer reset.</li>
 *     <li>Configurable byte order (big-endian or little-endian) for multi-byte types.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Thread Safety:</b> This class is not thread-safe. If multiple threads access a {@code DynamicByteBuffer} instance
 * concurrently, external synchronization is required to prevent data corruption or inconsistent state.
 * </p>
 * <p>
 * <b>Usage Example:</b>
 * <pre>
 * byte[] buffer = new byte[100];
 * DynamicByteBuffer dbb = new DynamicByteBuffer(buffer);
 * dbb.setByteOrder(DynamicByteBuffer.ByteOrder.LITTLE_ENDIAN)
 *    .writeInt(12345)
 *    .writeString("Hello")
 *    .writeBit(true)
 *    .flushBits();
 * dbb.setReadPosition(0);
 * System.out.println(dbb.readInt());    // Outputs: 12345
 * System.out.println(dbb.readString()); // Outputs: Hello
 * System.out.println(dbb.readBit());    // Outputs: true
 * </pre>
 * </p>
 * <p>
 * <b>Note:</b> When performing bit-level operations, ensure to call {@link #flushBits()} after writing bits to ensure all
 * buffered bits are written to the underlying byte array. Reading or writing bits after changing the read or write position
 * resets the bit buffers to maintain consistency. The byte order affects only multi-byte types (char, short, int, long,
 * float, double, string length prefix); single-byte and bit operations are unaffected.
 * </p>
 *
 * @author Albert Beaupre
 * @version 1.0
 * @see java.nio.ByteBuffer
 * @see BufferUnderflowException
 * @see java.nio.BufferOverflowException
 * @since April 17th, 2025
 */
public class DynamicByteBuffer {

    /**
     * The underlying byte array used to store data.
     */
    private byte[] buffer;
    /**
     * The current position for reading from the buffer.
     */
    private int readPosition;
    /**
     * The current position for writing to the buffer.
     */
    private int writePosition;
    /**
     * Accumulates bits for reading, holding up to one byte (8 bits) at a time.
     */
    private int bitReadBuffer;
    /**
     * Accumulates bits for writing, holding up to one byte (8 bits) before flushing to the buffer.
     */
    private int bitWriteBuffer;
    /**
     * The number of bits remaining to be read from {@code bitReadBuffer}.
     */
    private int bitReadCount;
    /**
     * The number of bits accumulated in {@code bitWriteBuffer} for writing.
     */
    private int bitWriteCount;
    /**
     * The byte order used for multibyte data types (default is big-endian).
     */
    private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;

    /**
     * Constructs a new {@code DynamicByteBuffer} with the specified byte array.
     * <p>
     * The buffer is used directly (not copied), and the read and write positions are initialized to 0.
     * The bit buffers are initialized to empty states. The byte order is set to big-endian by default.
     * </p>
     *
     * @param buffer the byte array to use as the underlying storage
     * @throws NullPointerException if {@code buffer} is {@code null}
     */
    public DynamicByteBuffer(byte[] buffer) {
        this.buffer = Objects.requireNonNull(buffer, "Buffer cannot be null");
    }

    /**
     * Sets the byte order for multi-byte data types (char, short, int, long, float, double, string length prefix).
     * <p>
     * The byte order affects how multi-byte values are read from or written to the buffer. Single-byte operations
     * (byte, boolean) and bit operations are unaffected. The default byte order is big-endian.
     * </p>
     *
     * @param order the byte order to use ({@link ByteOrder#BIG_ENDIAN} or {@link ByteOrder#LITTLE_ENDIAN})
     * @return this buffer, for method chaining
     * @throws NullPointerException if {@code order} is {@code null}
     */
    public DynamicByteBuffer setByteOrder(ByteOrder order) {
        this.byteOrder = Objects.requireNonNull(order, "Byte order cannot be null");
        return this;
    }

    /**
     * Reads a single byte from the buffer at the current read position and advances the read position.
     *
     * @return the byte value read from the buffer
     * @throws BufferUnderflowException if there are no bytes remaining in the buffer
     */
    public byte readByte() {
        checkRead(1);
        return buffer[readPosition++];
    }

    /**
     * Writes a single byte to the buffer at the current write position and advances the write position.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the byte value to write
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer writeByte(byte value) {
        checkWrite(1);
        buffer[writePosition++] = value;
        return this;
    }

    /**
     * Reads a 16-bit character (2 bytes) from the buffer in the configured byte order and advances the read position.
     *
     * @return the character read from the buffer
     * @throws BufferUnderflowException if there are fewer than 2 bytes remaining in the buffer
     */
    public char readChar() {
        checkRead(2);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return (char) ((buffer[readPosition++] & 0xFF) << 8 | (buffer[readPosition++] & 0xFF));
        } else {
            return (char) ((buffer[readPosition++] & 0xFF) | (buffer[readPosition++] & 0xFF) << 8);
        }
    }

    /**
     * Writes a 16-bit character (2 bytes) to the buffer in the configured byte order and advances the write position.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the character to write
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer writeChar(char value) {
        checkWrite(2);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            buffer[writePosition++] = (byte) (value >>> 8);
            buffer[writePosition++] = (byte) value;
        } else {
            buffer[writePosition++] = (byte) value;
            buffer[writePosition++] = (byte) (value >>> 8);
        }
        return this;
    }

    /**
     * Reads a 16-bit short integer (2 bytes) from the buffer in the configured byte order and advances the read position.
     *
     * @return the short integer read from the buffer
     * @throws BufferUnderflowException if there are fewer than 2 bytes remaining in the buffer
     */
    public short readShort() {
        checkRead(2);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return (short) ((buffer[readPosition++] & 0xFF) << 8 | (buffer[readPosition++] & 0xFF));
        } else {
            return (short) ((buffer[readPosition++] & 0xFF) | (buffer[readPosition++] & 0xFF) << 8);
        }
    }

    /**
     * Reads a 16-bit unsigned short integer (2 bytes) from the buffer in the configured byte order and advances the read position.
     *
     * @return the unsigned short integer read from the buffer as an int
     * @throws BufferUnderflowException if there are fewer than 2 bytes remaining in the buffer
     */
    public int readUnsignedShort() {
        checkRead(2);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return ((buffer[readPosition++] & 0xFF) << 8) | (buffer[readPosition++] & 0xFF);
        } else {
            return (buffer[readPosition++] & 0xFF) | ((buffer[readPosition++] & 0xFF) << 8);
        }
    }

    /**
     * Writes a 16-bit unsigned short integer (2 bytes) to the buffer in the configured byte order and advances the write position.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the unsigned short integer to write (0 to 65535)
     * @return this buffer, for method chaining
     * @throws IllegalArgumentException if {@code value} is negative or greater than 65535
     */
    public DynamicByteBuffer writeUnsignedShort(int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Unsigned short value must be between 0 and 65535");
        }
        checkWrite(2);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            buffer[writePosition++] = (byte) (value >>> 8);
            buffer[writePosition++] = (byte) value;
        } else {
            buffer[writePosition++] = (byte) value;
            buffer[writePosition++] = (byte) (value >>> 8);
        }
        return this;
    }

    /**
     * Reads a 32-bit unsigned integer (4 bytes) from the buffer in the configured byte order and advances the read position.
     *
     * @return the unsigned integer read from the buffer as a long
     * @throws BufferUnderflowException if there are fewer than 4 bytes remaining in the buffer
     */
    public long readUnsignedInt() {
        checkRead(4);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return ((long) (buffer[readPosition++] & 0xFF) << 24) |
                    ((buffer[readPosition++] & 0xFF) << 16) |
                    ((buffer[readPosition++] & 0xFF) << 8) |
                    (buffer[readPosition++] & 0xFF);
        } else {
            return (buffer[readPosition++] & 0xFF) |
                    ((buffer[readPosition++] & 0xFF) << 8) |
                    ((buffer[readPosition++] & 0xFF) << 16) |
                    ((long) (buffer[readPosition++] & 0xFF) << 24);
        }
    }

    /**
     * Writes a 32-bit unsigned integer (4 bytes) to the buffer in the configured byte order and advances the write position.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the unsigned integer to write (0 to 4294967295)
     * @return this buffer, for method chaining
     * @throws IllegalArgumentException if {@code value} is negative or greater than 4294967295
     */
    public DynamicByteBuffer writeUnsignedInt(long value) {
        if (value < 0 || value > 0xFFFFFFFFL)
            throw new IllegalArgumentException("Unsigned int value must be between 0 and 4294967295");
        checkWrite(4);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            buffer[writePosition++] = (byte) (value >>> 24);
            buffer[writePosition++] = (byte) (value >>> 16);
            buffer[writePosition++] = (byte) (value >>> 8);
            buffer[writePosition++] = (byte) value;
        } else {
            buffer[writePosition++] = (byte) value;
            buffer[writePosition++] = (byte) (value >>> 8);
            buffer[writePosition++] = (byte) (value >>> 16);
            buffer[writePosition++] = (byte) (value >>> 24);
        }
        return this;
    }

    /**
     * Writes a 16-bit short integer (2 bytes) to the buffer in the configured byte order and advances the write position.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the short integer to write
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer writeShort(short value) {
        checkWrite(2);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            buffer[writePosition++] = (byte) (value >>> 8);
            buffer[writePosition++] = (byte) value;
        } else {
            buffer[writePosition++] = (byte) value;
            buffer[writePosition++] = (byte) (value >>> 8);
        }
        return this;
    }

    /**
     * Reads a 32-bit integer (4 bytes) from the buffer in the configured byte order and advances the read position.
     *
     * @return the integer read from the buffer
     * @throws BufferUnderflowException if there are fewer than 4 bytes remaining in the buffer
     */
    public int readInt() {
        checkRead(4);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return ((buffer[readPosition++] & 0xFF) << 24) |
                    ((buffer[readPosition++] & 0xFF) << 16) |
                    ((buffer[readPosition++] & 0xFF) << 8) |
                    (buffer[readPosition++] & 0xFF);
        } else {
            return (buffer[readPosition++] & 0xFF) |
                    ((buffer[readPosition++] & 0xFF) << 8) |
                    ((buffer[readPosition++] & 0xFF) << 16) |
                    ((buffer[readPosition++] & 0xFF) << 24);
        }
    }

    /**
     * Writes a 32-bit integer (4 bytes) to the buffer in the configured byte order and advances the write position.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the integer to write
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer writeInt(int value) {
        checkWrite(4);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            buffer[writePosition++] = (byte) (value >>> 24);
            buffer[writePosition++] = (byte) (value >>> 16);
            buffer[writePosition++] = (byte) (value >>> 8);
            buffer[writePosition++] = (byte) value;
        } else {
            buffer[writePosition++] = (byte) value;
            buffer[writePosition++] = (byte) (value >>> 8);
            buffer[writePosition++] = (byte) (value >>> 16);
            buffer[writePosition++] = (byte) (value >>> 24);
        }
        return this;
    }

    /**
     * Reads a 64-bit long integer (8 bytes) from the buffer in the configured byte order and advances the read position.
     *
     * @return the long integer read from the buffer
     * @throws BufferUnderflowException if there are fewer than 8 bytes remaining in the buffer
     */
    public long readLong() {
        checkRead(8);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return ((long) (buffer[readPosition++] & 0xFF) << 56) |
                    ((long) (buffer[readPosition++] & 0xFF) << 48) |
                    ((long) (buffer[readPosition++] & 0xFF) << 40) |
                    ((long) (buffer[readPosition++] & 0xFF) << 32) |
                    ((long) (buffer[readPosition++] & 0xFF) << 24) |
                    ((long) (buffer[readPosition++] & 0xFF) << 16) |
                    ((long) (buffer[readPosition++] & 0xFF) << 8) |
                    ((long) (buffer[readPosition++] & 0xFF));
        } else {
            return ((long) (buffer[readPosition++] & 0xFF)) |
                    ((long) (buffer[readPosition++] & 0xFF) << 8) |
                    ((long) (buffer[readPosition++] & 0xFF) << 16) |
                    ((long) (buffer[readPosition++] & 0xFF) << 24) |
                    ((long) (buffer[readPosition++] & 0xFF) << 32) |
                    ((long) (buffer[readPosition++] & 0xFF) << 40) |
                    ((long) (buffer[readPosition++] & 0xFF) << 48) |
                    ((long) (buffer[readPosition++] & 0xFF) << 56);
        }
    }

    /**
     * Writes a 64-bit long integer (8 bytes) to the buffer in the configured byte order and advances the write position.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the long integer to write
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer writeLong(long value) {
        checkWrite(8);
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            buffer[writePosition++] = (byte) (value >>> 56);
            buffer[writePosition++] = (byte) (value >>> 48);
            buffer[writePosition++] = (byte) (value >>> 40);
            buffer[writePosition++] = (byte) (value >>> 32);
            buffer[writePosition++] = (byte) (value >>> 24);
            buffer[writePosition++] = (byte) (value >>> 16);
            buffer[writePosition++] = (byte) (value >>> 8);
            buffer[writePosition++] = (byte) value;
        } else {
            buffer[writePosition++] = (byte) value;
            buffer[writePosition++] = (byte) (value >>> 8);
            buffer[writePosition++] = (byte) (value >>> 16);
            buffer[writePosition++] = (byte) (value >>> 24);
            buffer[writePosition++] = (byte) (value >>> 32);
            buffer[writePosition++] = (byte) (value >>> 40);
            buffer[writePosition++] = (byte) (value >>> 48);
            buffer[writePosition++] = (byte) (value >>> 56);
        }
        return this;
    }

    /**
     * Reads a 32-bit floating-point number (4 bytes) from the buffer by reading an integer and converting it to a float.
     *
     * @return the float value read from the buffer
     * @throws BufferUnderflowException if there are fewer than 4 bytes remaining in the buffer
     * @see Float#intBitsToFloat(int)
     */
    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * Writes a 32-bit floating-point number (4 bytes) to the buffer by converting it to an integer and writing it.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the float value to write
     * @return this buffer, for method chaining
     * @see Float#floatToIntBits(float)
     */
    public DynamicByteBuffer writeFloat(float value) {
        return writeInt(Float.floatToIntBits(value));
    }

    /**
     * Reads a 64-bit double-precision floating-point number (8 bytes) from the buffer by reading a long and converting it to a double.
     *
     * @return the double value read from the buffer
     * @throws BufferUnderflowException if there are fewer than 8 bytes remaining in the buffer
     * @see Double#longBitsToDouble(long)
     */
    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Writes a 64-bit double-precision floating-point number (8 bytes) to the buffer by converting it to a long and writing it.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the double value to write
     * @return this buffer, for method chaining
     * @see Double#doubleToLongBits(double)
     */
    public DynamicByteBuffer writeDouble(double value) {
        return writeLong(Double.doubleToLongBits(value));
    }

    /**
     * Reads a boolean value from the buffer by reading a single byte (non-zero is {@code true}, zero is {@code false}).
     *
     * @return the boolean value read from the buffer
     * @throws BufferUnderflowException if there are no bytes remaining in the buffer
     */
    public boolean readBoolean() {
        checkRead(1);
        return buffer[readPosition++] != 0;
    }

    /**
     * Writes a boolean value to the buffer as a single byte (1 for {@code true}, 0 for {@code false}).
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the boolean value to write
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer writeBoolean(boolean value) {
        checkWrite(1);
        buffer[writePosition++] = (byte) (value ? 1 : 0);
        return this;
    }

    /**
     * Reads a UTF-8 encoded string from the buffer.
     * <p>
     * The string is prefixed with a 32-bit integer (in the configured byte order) indicating the number of bytes in the encoded string.
     * The method reads this length, then reads the specified number of bytes and decodes them as a UTF-8 string.
     * </p>
     *
     * @return the string read from the buffer
     * @throws BufferUnderflowException if there are not enough bytes to read the length or the string data
     * @throws IllegalArgumentException if the length is negative
     */
    public String readString() {
        int length = readShort();
        if (length < 0)
            throw new BufferUnderflowException();
        checkRead(length);
        byte[] bytes = new byte[length];
        System.arraycopy(buffer, readPosition, bytes, 0, length);
        readPosition += length;
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Writes a UTF-8 encoded string to the buffer.
     * <p>
     * The string is encoded as UTF-8 bytes, and the length of these bytes is written as a 32-bit integer (in the configured byte order)
     * before the bytes themselves. If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param value the string to write
     * @return this buffer, for method chaining
     * @throws IllegalArgumentException if {@code value} is {@code null}
     */
    public DynamicByteBuffer writeString(String value) {
        if (value == null)
            throw new IllegalArgumentException("String cannot be null");

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        checkWrite(4 + bytes.length);
        writeShort((short) bytes.length);
        for (byte b : bytes) {
            buffer[writePosition++] = b;
        }
        return this;
    }

    /**
     * Reads a specified number of bytes from the buffer into a new byte array and advances the read position.
     *
     * @param length the number of bytes to read
     * @return a new byte array containing the read bytes
     * @throws BufferUnderflowException if there are fewer than {@code length} bytes remaining in the buffer
     * @throws IllegalArgumentException if {@code length} is negative
     */
    public byte[] readBytes(int length) {
        checkRead(length);
        byte[] bytes = new byte[length];
        System.arraycopy(buffer, readPosition, bytes, 0, length);
        readPosition += length;
        return bytes;
    }

    /**
     * Writes all bytes from the specified byte array to the buffer and advances the write position.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param bytes the byte array to write
     * @return this buffer, for method chaining
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public DynamicByteBuffer writeBytes(byte[] bytes) {
        return writeBytes(bytes, 0, bytes.length);
    }

    /**
     * Writes a portion of the specified byte array to the buffer and advances the write position.
     * <p>
     * If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @param bytes  the byte array to write
     * @param offset the starting index in the byte array
     * @param length the number of bytes to write
     * @return this buffer, for method chaining
     * @throws NullPointerException     if {@code bytes} is {@code null}
     * @throws IllegalArgumentException if {@code offset} or {@code length} is negative, or if
     *                                  {@code offset + length} exceeds the array length
     */
    public DynamicByteBuffer writeBytes(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "Byte array cannot be null");
        if (offset < 0 || length < 0 || offset + length > bytes.length) {
            throw new IllegalArgumentException("Invalid offset or length");
        }
        checkWrite(length);
        System.arraycopy(bytes, offset, buffer, writePosition, length);
        writePosition += length;
        return this;
    }

    /**
     * Reads a single bit from the buffer and advances the bit position.
     * <p>
     * Bits are read from bytes in most-significant-bit-first order. When the bit buffer is empty, a new byte is read from
     * the buffer, and 8 bits are available for reading.
     * </p>
     *
     * @return {@code true} if the bit is 1, {@code false} if the bit is 0
     * @throws BufferUnderflowException if there are no bytes remaining in the buffer when a new byte is needed
     */
    public boolean readBit() {
        if (bitReadCount == 0) {
            checkRead(1);
            bitReadBuffer = buffer[readPosition++] & 0xFF;
            bitReadCount = 8;
        }
        boolean bit = ((bitReadBuffer >> (bitReadCount - 1)) & 1) == 1;
        bitReadCount--;
        return bit;
    }

    /**
     * Writes a single bit to the buffer and advances the bit position.
     * <p>
     * Bits are accumulated in the bit write buffer in the most-significant-bit-first order. When 8 bits are accumulated, they
     * are written as a byte to the buffer, and the bit buffer is cleared. If the buffer is too small, it is dynamically
     * resized to accommodate the write operation.
     * </p>
     *
     * @param bit the bit to write ({@code true} for 1, {@code false} for 0)
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer writeBit(boolean bit) {
        bitWriteBuffer = (bitWriteBuffer << 1) | (bit ? 1 : 0);
        bitWriteCount++;
        if (bitWriteCount == 8) {
            checkWrite(1);
            buffer[writePosition++] = (byte) bitWriteBuffer;
            bitWriteBuffer = 0;
            bitWriteCount = 0;
        }
        return this;
    }

    /**
     * Reads a specified number of bits from the buffer and returns them as an integer.
     * <p>
     * The bits are read in most-significant-bit-first order and assembled into the lower bits of the returned integer.
     * For example, reading 3 bits with values 1, 0, 1 returns the integer 5 (binary 101).
     * </p>
     *
     * @param numBits the number of bits to read (0 to 32)
     * @return the integer value formed by the read bits
     * @throws IllegalArgumentException if {@code numBits} is negative or greater than 32
     * @throws BufferUnderflowException if there are insufficient bytes to read the required bits
     */
    public int readBits(int numBits) {
        if (numBits < 0 || numBits > 32)
            throw new IllegalArgumentException("Number of bits must be between 0 and 32");

        int result = 0;
        for (int i = 0; i < numBits; i++)
            result = (result << 1) | (readBit() ? 1 : 0);

        return result;
    }

    /**
     * Writes a specified number of bits from an integer to the buffer.
     * <p>
     * The bits are taken from the lower {@code numBits} of the {@code value} parameter and written in
     * most-significant-bit-first order. For example, writing the value 5 (binary 101) with {@code numBits=3} writes
     * the bits 1, 0, 1.
     * </p>
     *
     * @param value   the integer containing the bits to write
     * @param numBits the number of bits to write (0 to 32)
     * @return this buffer, for method chaining
     * @throws IllegalArgumentException if {@code numBits} is negative or greater than 32
     */
    public DynamicByteBuffer writeBits(int value, int numBits) {
        if (numBits < 0 || numBits > 32)
            throw new IllegalArgumentException("Number of bits must be between 0 and 32");

        for (int i = numBits - 1; i >= 0; i--)
            writeBit(((value >>> i) & 1) == 1);

        return this;
    }

    /**
     * Flushes any remaining bits in the bit write buffer to the byte buffer, padding with zeros if necessary.
     * <p>
     * If fewer than 8 bits are in the bit write buffer, they are shifted left to align with the most significant bits of
     * a byte, and the byte is written to the buffer. This method should be called after writing bits to ensure all bits are
     * persisted. If the buffer is too small, it is dynamically resized to accommodate the write operation.
     * </p>
     *
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer flushBits() {
        if (bitWriteCount > 0) {
            checkWrite(1);
            buffer[writePosition++] = (byte) (bitWriteBuffer << (8 - bitWriteCount));
            bitWriteBuffer = 0;
            bitWriteCount = 0;
        }
        return this;
    }

    /**
     * Returns the current read position in the buffer.
     *
     * @return the current read position
     */
    public int getReadPosition() {
        return readPosition;
    }

    /**
     * Sets the read position to the specified value.
     * <p>
     * This also resets the bit read buffer to ensure consistency in bit-level operations.
     * </p>
     *
     * @param position the new read position
     * @throws IllegalArgumentException if {@code position} is negative or greater than the buffer length
     */
    public void setReadPosition(int position) {
        if (position < 0 || position > buffer.length)
            throw new IllegalArgumentException("Invalid read position");

        this.readPosition = position;
        this.bitReadBuffer = 0;
        this.bitReadCount = 0;
    }

    /**
     * Checks if there are any bytes remaining to read from the current read position to the end of the buffer.
     * <p>
     * This method returns {@code true} if the current read position is less than the buffer's length,
     * indicating that there are more bytes available to read, and {@code false} otherwise.
     * </p>
     *
     * @return {@code true} if there are remaining bytes to read, {@code false} otherwise
     * @see java.nio.ByteBuffer#hasRemaining()
     */
    public boolean hasRemaining() {
        return readPosition < buffer.length;
    }

    /**
     * Returns the current write position in the buffer.
     *
     * @return the current write position
     */
    public int getWritePosition() {
        return writePosition;
    }

    /**
     * Sets the write position to the specified value.
     * <p>
     * This also resets the bit write buffer to ensure consistency in bit-level operations.
     * </p>
     *
     * @param position the new write position
     * @throws IllegalArgumentException if {@code position} is negative or greater than the buffer length
     */
    public void setWritePosition(int position) {
        if (position < 0 || position > buffer.length)
            throw new IllegalArgumentException("Invalid write position");

        this.writePosition = position;
        this.bitWriteBuffer = 0;
        this.bitWriteCount = 0;
    }

    /**
     * Creates a new {@code DynamicByteBuffer} containing a copy of a portion of this buffer's data.
     * <p>
     * The new buffer is independent of this buffer, and its read and write positions are initialized to 0.
     * </p>
     *
     * @param start  the starting index of the slice
     * @param length the length of the slice
     * @return a new {@code DynamicByteBuffer} containing the sliced data
     * @throws IllegalArgumentException if {@code start} or {@code length} is negative, or if
     *                                  {@code start + length} exceeds the buffer length
     */
    public DynamicByteBuffer slice(int start, int length) {
        if (start < 0 || length < 0 || start + length > buffer.length)
            throw new IllegalArgumentException("Invalid slice range");

        byte[] sliceBuffer = new byte[length];
        System.arraycopy(buffer, start, sliceBuffer, 0, length);
        return new DynamicByteBuffer(sliceBuffer);
    }

    /**
     * Clears the buffer by resetting all positions and bit buffers and filling the buffer with zeros.
     *
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer clear() {
        resetPositions();
        Arrays.fill(buffer, (byte) 0);
        return this;
    }

    /**
     * Resets the read and write positions and bit buffers to their initial states without modifying the buffer's contents.
     *
     * @return this buffer, for method chaining
     */
    public DynamicByteBuffer resetPositions() {
        readPosition = 0;
        writePosition = 0;
        bitReadBuffer = 0;
        bitWriteBuffer = 0;
        bitReadCount = 0;
        bitWriteCount = 0;
        return this;
    }

    public byte[] getData() {
        return this.buffer;
    }

    /**
     * Returns the number of bytes remaining for reading from the current read position.
     *
     * @return the number of readable bytes
     */
    public int remainingRead() {
        return buffer.length - readPosition;
    }

    /**
     * Returns the number of bytes remaining for writing from the current write position.
     *
     * @return the number of writable bytes
     */
    public int remainingWrite() {
        return buffer.length - writePosition;
    }

    /**
     * Checks if there are enough bytes remaining to read the specified number of bytes.
     *
     * @param bytes the number of bytes to check
     * @throws BufferUnderflowException if there are insufficient bytes remaining
     */
    private void checkRead(int bytes) {
        if (readPosition + bytes > buffer.length) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * Ensures the buffer has enough capacity to write the specified number of bytes, resizing if necessary.
     *
     * @param bytes the number of bytes to check
     */
    private void checkWrite(int bytes) {
        if (writePosition + bytes > buffer.length) {
            int newCapacity = Math.max(buffer.length * 2, writePosition + bytes);
            byte[] newBuffer = new byte[newCapacity];
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
            buffer = newBuffer;
        }
    }

    /**
     * Compares this buffer to another object for equality.
     * <p>
     * Two {@code DynamicByteBuffer} instances are equal if they have the same read and write positions, bit buffer states,
     * and identical buffer contents.
     * </p>
     *
     * @param o the object to compare with
     * @return {@code true} if the objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicByteBuffer other)) return false;
        return readPosition == other.readPosition &&
                writePosition == other.writePosition &&
                bitReadCount == other.bitReadCount &&
                bitWriteCount == other.bitWriteCount &&
                bitReadBuffer == other.bitReadBuffer &&
                bitWriteBuffer == other.bitWriteBuffer &&
                byteOrder == other.byteOrder &&
                Arrays.equals(buffer, other.buffer);
    }

    /**
     * Computes a hash code for this buffer based on its state and contents.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(readPosition, writePosition, bitReadCount, bitWriteCount,
                bitReadBuffer, bitWriteBuffer, byteOrder, Arrays.hashCode(buffer));
    }

    /**
     * Enum representing the byte order for multi-byte data types.
     */
    public enum ByteOrder {
        /**
         * Big-endian order (the most significant byte first).
         */
        BIG_ENDIAN,
        /**
         * Little-endian order (the least significant byte first).
         */
        LITTLE_ENDIAN
    }
}