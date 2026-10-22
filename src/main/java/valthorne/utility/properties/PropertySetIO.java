package valthorne.utility.properties;

import valthorne.io.buffer.DynamicByteBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.BufferUnderflowException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Encodes and decodes {@link PropertySet} instances using Valthorne's
 * {@link DynamicByteBuffer}. The binary representation contains a magic number,
 * format version, entry count, and a type tag for every value. Property names are
 * written in ascending order, producing the same bytes regardless of insertion order.
 * <p>
 * Supported values are strings, primitive wrappers, {@link BigInteger},
 * {@link BigDecimal}, {@link UUID}, {@link Path}, {@link URI}, {@link java.net.URL},
 * Java time values supported by {@link PropertyValue}, {@link Locale},
 * {@link Currency}, and {@link Charset}. Unsupported types are rejected instead of
 * being silently converted to text and losing their type.
 * <p>
 * Stream methods do not close caller-owned streams. Path methods open and close their
 * own streams. This class is stateless and thread-safe.
 *
 * @author Albert Beaupre
 * @since September 21st, 2026
 */
public final class PropertySetIO {

    /**
     * "VPRP"
     */
    private static final int MAGIC = 0x56505250;
    /**
     * Binary property format version written and accepted by this codec.
     */
    private static final short VERSION = 1;
    /**
     * Maximum property entries accepted when decoding untrusted input.
     */
    private static final int MAX_ENTRIES = 1_000_000;
    /**
     * Maximum encoded text length accepted before allocating a buffer.
     */
    private static final int MAX_TEXT_BYTES = 64 * 1024 * 1024;

    /**
     * Binary type tag identifying string property values.
     */
    private static final byte STRING = 1;
    /**
     * Binary type tag identifying byte property values.
     */
    private static final byte BYTE = 2;
    /**
     * Binary type tag identifying short property values.
     */
    private static final byte SHORT = 3;
    /**
     * Binary type tag identifying integer property values.
     */
    private static final byte INTEGER = 4;
    /**
     * Binary type tag identifying long property values.
     */
    private static final byte LONG = 5;
    /**
     * Binary type tag identifying float property values.
     */
    private static final byte FLOAT = 6;
    /**
     * Binary type tag identifying double property values.
     */
    private static final byte DOUBLE = 7;
    /**
     * Binary type tag identifying boolean property values.
     */
    private static final byte BOOLEAN = 8;
    /**
     * Binary type tag identifying character property values.
     */
    private static final byte CHARACTER = 9;
    /**
     * Binary type tag identifying big integer property values.
     */
    private static final byte BIG_INTEGER = 10;
    /**
     * Binary type tag identifying big decimal property values.
     */
    private static final byte BIG_DECIMAL = 11;
    /**
     * Binary type tag identifying uuid value property values.
     */
    private static final byte UUID_VALUE = 12;
    /**
     * Binary type tag identifying path value property values.
     */
    private static final byte PATH_VALUE = 13;
    /**
     * Binary type tag identifying uri value property values.
     */
    private static final byte URI_VALUE = 14;
    /**
     * Binary type tag identifying url value property values.
     */
    private static final byte URL_VALUE = 15;
    /**
     * Binary type tag identifying duration property values.
     */
    private static final byte DURATION = 16;
    /**
     * Binary type tag identifying period property values.
     */
    private static final byte PERIOD = 17;
    /**
     * Binary type tag identifying instant property values.
     */
    private static final byte INSTANT = 18;
    /**
     * Binary type tag identifying local date property values.
     */
    private static final byte LOCAL_DATE = 19;
    /**
     * Binary type tag identifying local time property values.
     */
    private static final byte LOCAL_TIME = 20;
    /**
     * Binary type tag identifying local date time property values.
     */
    private static final byte LOCAL_DATE_TIME = 21;
    /**
     * Binary type tag identifying offset date time property values.
     */
    private static final byte OFFSET_DATE_TIME = 22;
    /**
     * Binary type tag identifying zoned date time property values.
     */
    private static final byte ZONED_DATE_TIME = 23;
    /**
     * Binary type tag identifying locale property values.
     */
    private static final byte LOCALE = 24;
    /**
     * Binary type tag identifying currency property values.
     */
    private static final byte CURRENCY = 25;
    /**
     * Binary type tag identifying charset property values.
     */
    private static final byte CHARSET = 26;

    private PropertySetIO() {
        // utility class
    }

    /**
     * Writes a property set to a growable buffer at its current write position.
     * The buffer's byte order is respected and must also be used when reading.
     *
     * @param properties properties to encode
     * @param buffer     destination buffer
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if a value has an unsupported type
     */
    public static void write(PropertySet properties, DynamicByteBuffer buffer) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(buffer, "buffer");

        List<String> names = new ArrayList<>(properties.asMap().keySet());
        names.sort(String::compareTo);
        buffer.writeInt(MAGIC).writeShort(VERSION).writeInt(names.size());
        for (String name : names) {
            writeText(buffer, name);
            writeValue(buffer, properties.getRequired(name).value());
        }
    }

    /**
     * Reads one property set from the buffer's current read position.
     *
     * @param buffer source buffer
     * @return decoded property set
     * @throws NullPointerException if buffer is null
     * @throws IOException          if the header, counts, type tags, or values are invalid
     */
    public static PropertySet read(DynamicByteBuffer buffer) throws IOException {
        Objects.requireNonNull(buffer, "buffer");
        try {
            if (buffer.readInt() != MAGIC) throw new IOException("Invalid property-set file signature");
            int version = Short.toUnsignedInt(buffer.readShort());
            if (version != VERSION) throw new IOException("Unsupported property-set format version: " + version);
            int count = buffer.readInt();
            if (count < 0 || count > MAX_ENTRIES) throw new IOException("Invalid property count: " + count);

            PropertySet properties = new PropertySet();
            for (int i = 0; i < count; i++) {
                String name = readText(buffer);
                if (properties.contains(name)) throw new IOException("Duplicate property name: " + name);
                properties.set(name, readValue(buffer));
            }
            return properties;
        } catch (BufferUnderflowException | IllegalArgumentException error) {
            throw new IOException("Invalid or truncated property-set data", error);
        }
    }

    /**
     * Encodes a property set into a newly allocated byte array.
     *
     * @param properties properties to encode
     * @return encoded bytes
     */
    public static byte[] toBytes(PropertySet properties) {
        DynamicByteBuffer buffer = new DynamicByteBuffer();
        write(properties, buffer);
        return buffer.toTrimmedWriteArray();
    }

    /**
     * Decodes a property set from a byte array.
     *
     * @param bytes encoded bytes
     * @return decoded property set
     * @throws IOException if the data is invalid or truncated
     */
    public static PropertySet fromBytes(byte[] bytes) throws IOException {
        return read(new DynamicByteBuffer(Objects.requireNonNull(bytes, "bytes")));
    }

    /**
     * Writes a property set to a file, creating or replacing it after encoding succeeds.
     * Unsupported values leave existing content intact. File-system write failures
     * may still leave a partially written file.
     *
     * @param properties properties to encode
     * @param path       destination path
     * @throws IOException if the file cannot be written
     */
    public static void write(PropertySet properties, Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        /*
         * Encode before opening the destination so validation cannot truncate an
         * existing file. Reuse the byte array already required by stream writing.
         */
        byte[] encoded = toBytes(properties);
        Files.write(path, encoded);
    }

    /**
     * Reads a property set from a file.
     *
     * @param path source path
     * @return decoded property set
     * @throws IOException if the file cannot be read or is invalid
     */
    public static PropertySet read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return read(input);
        }
    }

    /**
     * Writes a property set to a caller-owned stream without closing it.
     *
     * @param properties properties to encode
     * @param output     destination stream
     * @throws IOException if writing fails
     */
    public static void write(PropertySet properties, OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output").write(toBytes(properties));
    }

    /**
     * Reads a property set from a caller-owned stream without closing it.
     *
     * @param input source stream
     * @return decoded property set
     * @throws IOException if reading fails or the data is invalid
     */
    public static PropertySet read(InputStream input) throws IOException {
        return fromBytes(Objects.requireNonNull(input, "input").readAllBytes());
    }

    private static void writeValue(DynamicByteBuffer buffer, Object value) {
        switch (value) {
            case String v -> taggedText(buffer, STRING, v);
            case Byte v -> buffer.writeByte(BYTE).writeByte(v);
            case Short v -> buffer.writeByte(SHORT).writeShort(v);
            case Integer v -> buffer.writeByte(INTEGER).writeInt(v);
            case Long v -> buffer.writeByte(LONG).writeLong(v);
            case Float v -> buffer.writeByte(FLOAT).writeFloat(v);
            case Double v -> buffer.writeByte(DOUBLE).writeDouble(v);
            case Boolean v -> buffer.writeByte(BOOLEAN).writeBoolean(v);
            case Character v -> buffer.writeByte(CHARACTER).writeChar(v);
            case BigInteger v -> taggedText(buffer, BIG_INTEGER, v.toString());
            case BigDecimal v -> taggedText(buffer, BIG_DECIMAL, v.toString());
            case UUID v -> taggedText(buffer, UUID_VALUE, v.toString());
            case Path v -> taggedText(buffer, PATH_VALUE, v.toString());
            case URI v -> taggedText(buffer, URI_VALUE, v.toString());
            case URL v -> taggedText(buffer, URL_VALUE, v.toString());
            case Duration v -> taggedText(buffer, DURATION, v.toString());
            case Period v -> taggedText(buffer, PERIOD, v.toString());
            case Instant v -> taggedText(buffer, INSTANT, v.toString());
            case LocalDate v -> taggedText(buffer, LOCAL_DATE, v.toString());
            case LocalTime v -> taggedText(buffer, LOCAL_TIME, v.toString());
            case LocalDateTime v -> taggedText(buffer, LOCAL_DATE_TIME, v.toString());
            case OffsetDateTime v -> taggedText(buffer, OFFSET_DATE_TIME, v.toString());
            case ZonedDateTime v -> taggedText(buffer, ZONED_DATE_TIME, v.toString());
            case Locale v -> taggedText(buffer, LOCALE, v.toLanguageTag());
            case Currency v -> taggedText(buffer, CURRENCY, v.getCurrencyCode());
            case Charset v -> taggedText(buffer, CHARSET, v.name());
            case null, default -> throw new IllegalArgumentException("Unsupported property value type: " + value.getClass().getName());
        }
    }

    private static Object readValue(DynamicByteBuffer buffer) throws IOException {
        byte type = buffer.readByte();
        return switch (type) {
            case STRING -> readText(buffer);
            case BYTE -> buffer.readByte();
            case SHORT -> buffer.readShort();
            case INTEGER -> buffer.readInt();
            case LONG -> buffer.readLong();
            case FLOAT -> buffer.readFloat();
            case DOUBLE -> buffer.readDouble();
            case BOOLEAN -> buffer.readBoolean();
            case CHARACTER -> buffer.readChar();
            case BIG_INTEGER -> new BigInteger(readText(buffer));
            case BIG_DECIMAL -> new BigDecimal(readText(buffer));
            case UUID_VALUE -> UUID.fromString(readText(buffer));
            case PATH_VALUE -> Path.of(readText(buffer));
            case URI_VALUE -> URI.create(readText(buffer));
            case URL_VALUE -> URI.create(readText(buffer)).toURL();
            case DURATION -> Duration.parse(readText(buffer));
            case PERIOD -> Period.parse(readText(buffer));
            case INSTANT -> Instant.parse(readText(buffer));
            case LOCAL_DATE -> LocalDate.parse(readText(buffer));
            case LOCAL_TIME -> LocalTime.parse(readText(buffer));
            case LOCAL_DATE_TIME -> LocalDateTime.parse(readText(buffer));
            case OFFSET_DATE_TIME -> OffsetDateTime.parse(readText(buffer));
            case ZONED_DATE_TIME -> ZonedDateTime.parse(readText(buffer));
            case LOCALE -> Locale.forLanguageTag(readText(buffer));
            case CURRENCY -> Currency.getInstance(readText(buffer));
            case CHARSET -> Charset.forName(readText(buffer));
            default -> throw new IOException("Unknown property value type tag: " + Byte.toUnsignedInt(type));
        };
    }

    private static void taggedText(DynamicByteBuffer buffer, byte type, String value) {
        buffer.writeByte(type);
        writeText(buffer, value);
    }

    private static void writeText(DynamicByteBuffer buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) throw new IllegalArgumentException("Property text is too large");
        buffer.writeInt(bytes.length).writeBytes(bytes);
    }

    private static String readText(DynamicByteBuffer buffer) throws IOException {
        int length = buffer.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES || length > buffer.remainingRead()) {
            throw new IOException("Invalid property text length: " + length);
        }
        return new String(buffer.readBytes(length), StandardCharsets.UTF_8);
    }
}
