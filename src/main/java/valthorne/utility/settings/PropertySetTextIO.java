package valthorne.utility.settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Reads and writes the human-readable UTF-8 representation of a {@link PropertySet}.
 * Each property occupies one line in the form {@code escaped-name:type=escaped-value}
 * and may end with an optional {@code # comment} or {@code ! comment}.
 * Blank lines and lines whose first non-whitespace character is {@code #} or {@code !}
 * are ignored. Backslash escapes {@code \}, {@code :}, {@code =}, newline, carriage
 * return, tab, and comment-marker characters.
 * <p>
 * Type names preserve the same values supported by {@link PropertySetIO}. Entries are
 * written in ascending name order for stable source-control diffs. Readers and writers
 * supplied by callers remain open; path methods manage their own resources.
 *
 * @author Albert Beaupre
 * @since September 21st, 2026
 */
public final class PropertySetTextIO {

    private PropertySetTextIO() {
        // utility class
    }

    /**
     * Writes properties to a text file using UTF-8, creating or replacing the file.
     *
     * @param properties properties to write
     * @param path       destination path
     * @throws IOException if writing fails
     */
    public static void write(PropertySet properties, Path path) throws IOException {
        write(properties, path, PropertyTextOptions.none());
    }

    /**
     * Writes properties and optional comments to a UTF-8 text file.
     *
     * @param properties properties to write
     * @param path destination path
     * @param options header and entry comments
     * @throws IOException if writing fails
     */
    public static void write(PropertySet properties, Path path, PropertyTextOptions options) throws IOException {
        Objects.requireNonNull(path, "path");
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            write(properties, writer, options);
        }
    }

    /**
     * Writes properties as UTF-8 to a caller-owned stream without closing it.
     *
     * @param properties properties to write
     * @param output     destination stream
     * @throws IOException if writing fails
     */
    public static void write(PropertySet properties, OutputStream output) throws IOException {
        write(properties, output, PropertyTextOptions.none());
    }

    /**
     * Writes properties and optional comments as UTF-8 to a caller-owned stream.
     *
     * @param properties properties to write
     * @param output destination stream
     * @param options header and entry comments
     * @throws IOException if writing fails
     */
    public static void write(PropertySet properties, OutputStream output,
                             PropertyTextOptions options) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(Objects.requireNonNull(output, "output"), StandardCharsets.UTF_8);
        write(properties, writer, options);
        writer.flush();
    }

    /**
     * Writes properties to a caller-owned character stream without closing it.
     *
     * @param properties properties to write
     * @param writer     destination writer
     * @throws IOException              if writing fails
     * @throws IllegalArgumentException if a value type is unsupported
     */
    public static void write(PropertySet properties, Writer writer) throws IOException {
        write(properties, writer, PropertyTextOptions.none());
    }

    /**
     * Writes properties and optional comments to a caller-owned character stream.
     *
     * @param properties properties to write
     * @param writer destination writer
     * @param options header and entry comments
     * @throws IOException if writing fails
     * @throws IllegalArgumentException if a value type is unsupported
     */
    public static void write(PropertySet properties, Writer writer,
                             PropertyTextOptions options) throws IOException {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(options, "options");
        List<String> names = new ArrayList<>(properties.asMap().keySet());
        names.sort(String::compareTo);

        for (String header : options.headers()) {
            writer.write("# ");
            writer.write(header);
            writer.write('\n');
        }
        for (String name : names) {
            EncodedValue encoded = encode(properties.getRequired(name).value());
            writer.write(escape(name));
            writer.write(':');
            writer.write(encoded.type());
            writer.write('=');
            writer.write(escape(encoded.value()));
            String comment = options.comments().get(name);
            if (comment != null) {
                writer.write(" # ");
                writer.write(comment);
            }
            writer.write('\n');
        }
    }

    /**
     * Returns the human-readable representation of a property set.
     *
     * @param properties properties to encode
     * @return encoded text
     */
    public static String toText(PropertySet properties) {
        return toText(properties, PropertyTextOptions.none());
    }

    /**
     * Returns the human-readable representation with optional comments.
     *
     * @param properties properties to encode
     * @param options header and entry comments
     * @return encoded text
     */
    public static String toText(PropertySet properties, PropertyTextOptions options) {
        try {
            StringWriter writer = new StringWriter();
            write(properties, writer, options);
            return writer.toString();
        } catch (IOException impossible) {
            throw new AssertionError("StringWriter unexpectedly failed", impossible);
        }
    }

    /**
     * Reads a UTF-8 property text file.
     *
     * @param path source path
     * @return decoded property set
     * @throws IOException if reading fails or the text is invalid
     */
    public static PropertySet read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    /**
     * Reads UTF-8 property text from a caller-owned stream without closing it.
     *
     * @param input source stream
     * @return decoded property set
     * @throws IOException if reading fails or the text is invalid
     */
    public static PropertySet read(InputStream input) throws IOException {
        return read(new InputStreamReader(Objects.requireNonNull(input, "input"), StandardCharsets.UTF_8));
    }

    /**
     * Reads property text from a caller-owned character stream without closing it.
     *
     * @param reader source reader
     * @return decoded property set
     * @throws IOException if reading fails or the text is invalid
     */
    public static PropertySet read(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        BufferedReader lines = reader instanceof BufferedReader buffered ? buffered : new BufferedReader(reader);
        PropertySet properties = new PropertySet();
        String line;
        int lineNumber = 0;
        while ((line = lines.readLine()) != null) {
            lineNumber++;
            String trimmed = line.stripLeading();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;
            try {
                int colon = separator(line, ':', 0);
                int equals = colon < 0 ? -1 : separator(line, '=', colon + 1);
                if (colon < 1 || equals < colon + 2) throw new IllegalArgumentException("Expected name:type=value");

                String name = unescape(line.substring(0, colon));
                String type = line.substring(colon + 1, equals).trim();
                String encodedValue = line.substring(equals + 1);
                int comment = inlineComment(encodedValue);
                if (comment >= 0) encodedValue = encodedValue.substring(0, comment - 1);
                String value = unescape(encodedValue);
                if (name.isEmpty()) throw new IllegalArgumentException("Property name cannot be empty");
                if (properties.contains(name)) throw new IllegalArgumentException("Duplicate property name: " + name);
                properties.set(name, decode(type, value));
            } catch (RuntimeException error) {
                throw new IOException("Invalid property text at line " + lineNumber + ": " + error.getMessage(), error);
            }
        }
        return properties;
    }

    /**
     * Decodes properties from a string.
     *
     * @param text encoded property text
     * @return decoded property set
     * @throws IOException if the text is invalid
     */
    public static PropertySet fromText(String text) throws IOException {
        return read(new StringReader(Objects.requireNonNull(text, "text")));
    }

    private static EncodedValue encode(Object value) {
        if (value instanceof String v) return new EncodedValue("string", v);
        if (value instanceof Byte v) return new EncodedValue("byte", v.toString());
        if (value instanceof Short v) return new EncodedValue("short", v.toString());
        if (value instanceof Integer v) return new EncodedValue("integer", v.toString());
        if (value instanceof Long v) return new EncodedValue("long", v.toString());
        if (value instanceof Float v) return new EncodedValue("float", v.toString());
        if (value instanceof Double v) return new EncodedValue("double", v.toString());
        if (value instanceof Boolean v) return new EncodedValue("boolean", v.toString());
        if (value instanceof Character v) return new EncodedValue("character", v.toString());
        if (value instanceof BigInteger v) return new EncodedValue("big-integer", v.toString());
        if (value instanceof BigDecimal v) return new EncodedValue("big-decimal", v.toString());
        if (value instanceof UUID v) return new EncodedValue("uuid", v.toString());
        if (value instanceof Path v) return new EncodedValue("path", v.toString());
        if (value instanceof URI v) return new EncodedValue("uri", v.toString());
        if (value instanceof URL v) return new EncodedValue("url", v.toString());
        if (value instanceof Duration v) return new EncodedValue("duration", v.toString());
        if (value instanceof Period v) return new EncodedValue("period", v.toString());
        if (value instanceof Instant v) return new EncodedValue("instant", v.toString());
        if (value instanceof LocalDate v) return new EncodedValue("local-date", v.toString());
        if (value instanceof LocalTime v) return new EncodedValue("local-time", v.toString());
        if (value instanceof LocalDateTime v) return new EncodedValue("local-date-time", v.toString());
        if (value instanceof OffsetDateTime v) return new EncodedValue("offset-date-time", v.toString());
        if (value instanceof ZonedDateTime v) return new EncodedValue("zoned-date-time", v.toString());
        if (value instanceof Locale v) return new EncodedValue("locale", v.toLanguageTag());
        if (value instanceof Currency v) return new EncodedValue("currency", v.getCurrencyCode());
        if (value instanceof Charset v) return new EncodedValue("charset", v.name());
        throw new IllegalArgumentException("Unsupported property value type: " + value.getClass().getName());
    }

    private static Object decode(String type, String value) {
        return switch (type) {
            case "string" -> value;
            case "byte" -> Byte.valueOf(value);
            case "short" -> Short.valueOf(value);
            case "integer" -> Integer.valueOf(value);
            case "long" -> Long.valueOf(value);
            case "float" -> Float.valueOf(value);
            case "double" -> Double.valueOf(value);
            case "boolean" -> parseBoolean(value);
            case "character" -> parseCharacter(value);
            case "big-integer" -> new BigInteger(value);
            case "big-decimal" -> new BigDecimal(value);
            case "uuid" -> UUID.fromString(value);
            case "path" -> Path.of(value);
            case "uri" -> URI.create(value);
            case "url" -> toUrl(value);
            case "duration" -> Duration.parse(value);
            case "period" -> Period.parse(value);
            case "instant" -> Instant.parse(value);
            case "local-date" -> LocalDate.parse(value);
            case "local-time" -> LocalTime.parse(value);
            case "local-date-time" -> LocalDateTime.parse(value);
            case "offset-date-time" -> OffsetDateTime.parse(value);
            case "zoned-date-time" -> ZonedDateTime.parse(value);
            case "locale" -> Locale.forLanguageTag(value);
            case "currency" -> Currency.getInstance(value);
            case "charset" -> Charset.forName(value);
            default -> throw new IllegalArgumentException("Unknown property type: " + type);
        };
    }

    private static boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("Boolean must be true or false");
    }

    private static char parseCharacter(String value) {
        if (value.length() != 1) throw new IllegalArgumentException("Character must contain exactly one character");
        return value.charAt(0);
    }

    private static URL toUrl(String value) {
        try {
            return URI.create(value).toURL();
        } catch (MalformedURLException error) {
            throw new IllegalArgumentException("Invalid URL: " + value, error);
        }
    }

    private static int separator(String line, char expected, int start) {
        boolean escaped = false;
        for (int i = start; i < line.length(); i++) {
            char character = line.charAt(i);
            if (escaped) escaped = false;
            else if (character == '\\') escaped = true;
            else if (character == expected) return i;
        }
        return -1;
    }

    private static int inlineComment(String value) {
        boolean escaped = false;
        for (int i = 1; i < value.length(); i++) {
            char character = value.charAt(i);
            if (escaped) escaped = false;
            else if (character == '\\') escaped = true;
            else if ((character == '#' || character == '!') && Character.isWhitespace(value.charAt(i - 1))) return i;
        }
        return -1;
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case ':' -> result.append("\\:");
                case '=' -> result.append("\\=");
                case '#' -> result.append("\\#");
                case '!' -> result.append("\\!");
                default -> result.append(value.charAt(i));
            }
        }
        return result.toString();
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character != '\\') {
                result.append(character);
                continue;
            }
            if (++i == value.length()) throw new IllegalArgumentException("Trailing escape character");
            switch (value.charAt(i)) {
                case '\\' -> result.append('\\');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case ':' -> result.append(':');
                case '=' -> result.append('=');
                case '#' -> result.append('#');
                case '!' -> result.append('!');
                default -> throw new IllegalArgumentException("Unknown escape sequence: \\" + value.charAt(i));
            }
        }
        return result.toString();
    }

    private record EncodedValue(String type, String value) {
    }
}
