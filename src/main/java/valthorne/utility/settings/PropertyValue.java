package valthorne.utility.settings;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Wraps a non-null property value and exposes convenient typed representations.
 * Existing objects of a requested type are returned directly where possible; textual
 * conversions use the trimmed result of {@link Object#toString()}. Numeric values are
 * converted with the corresponding {@link Number} method, so narrowing conversions
 * follow the standard Java numeric conversion rules.
 *
 * @param value the wrapped, non-null value
 * @param <T>   the declared value type
 * @author Albert Beaupre
 * @since September 21st, 2026
 */
public record PropertyValue<T>(T value) {

    /**
     * Creates a property wrapper.
     *
     * @param value value to wrap
     * @throws NullPointerException if value is null
     */
    public PropertyValue {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Returns the wrapped value without conversion.
     *
     * @return wrapped value
     */
    public T asObject() {
        return value;
    }

    /**
     * Returns the value's textual representation without trimming it.
     *
     * @return result of the wrapped value's {@code toString()} method
     */
    public String asString() {
        return value.toString();
    }

    /**
     * Converts this property to a byte.
     *
     * @return value converted to a byte
     * @throws NumberFormatException if textual conversion fails
     */
    public byte asByte() {
        return value instanceof Number number ? number.byteValue() : Byte.parseByte(text());
    }

    /**
     * Converts this property to a short.
     *
     * @return value converted to a short
     * @throws NumberFormatException if textual conversion fails
     */
    public short asShort() {
        return value instanceof Number number ? number.shortValue() : Short.parseShort(text());
    }

    /**
     * Converts this property to an integer.
     *
     * @return value converted to an integer
     * @throws NumberFormatException if textual conversion fails
     */
    public int asInt() {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(text());
    }

    /**
     * Converts this property to a long.
     *
     * @return value converted to a long
     * @throws NumberFormatException if textual conversion fails
     */
    public long asLong() {
        return value instanceof Number number ? number.longValue() : Long.parseLong(text());
    }

    /**
     * Converts this property to a float.
     *
     * @return value converted to a float
     * @throws NumberFormatException if textual conversion fails
     */
    public float asFloat() {
        return value instanceof Number number ? number.floatValue() : Float.parseFloat(text());
    }

    /**
     * Converts this property to a double.
     *
     * @return value converted to a double
     * @throws NumberFormatException if textual conversion fails
     */
    public double asDouble() {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(text());
    }

    /**
     * Converts the value to a boolean. Boolean values are returned directly, numeric
     * zero is false and every other numeric value is true. Text accepts, without case
     * sensitivity, {@code true/false}, {@code yes/no}, {@code on/off}, and {@code 1/0}.
     *
     * @return converted boolean
     * @throws IllegalArgumentException if a textual value is not a recognized boolean
     */
    public boolean asBoolean() {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.doubleValue() != 0.0d;

        String text = text();
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes") || text.equalsIgnoreCase("on") || text.equals("1"))
            return true;
        if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no") || text.equalsIgnoreCase("off") || text.equals("0"))
            return false;
        throw new IllegalArgumentException("Property value is not a boolean: " + text);
    }

    /**
     * Converts the value to a character.
     *
     * @return the wrapped character or sole character in its textual representation
     * @throws IllegalArgumentException if the textual representation is not one character long
     */
    public char asChar() {
        if (value instanceof Character character) return character;
        String text = asString();
        if (text.length() != 1) {
            throw new IllegalArgumentException("Property value is not a single character: " + text);
        }
        return text.charAt(0);
    }

    /**
     * Converts the value to an arbitrary-precision integer. Integral wrapper values
     * convert exactly, and decimal values must have no fractional component.
     *
     * @return converted integer
     * @throws ArithmeticException   if a decimal value has a nonzero fractional component
     * @throws NumberFormatException if textual conversion fails
     */
    public BigInteger asBigInteger() {
        if (value instanceof BigInteger integer) return integer;
        if (value instanceof BigDecimal decimal) return decimal.toBigIntegerExact();
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        return new BigInteger(text());
    }

    /**
     * Converts the value to an arbitrary-precision decimal. Integral values retain
     * their exact value; other {@link Number} implementations convert through double.
     *
     * @return converted decimal
     * @throws NumberFormatException if textual conversion fails
     */
    public BigDecimal asBigDecimal() {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof BigInteger integer) return new BigDecimal(integer);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(text());
    }

    /**
     * Converts the value to an enum constant by matching its name without regard to case.
     *
     * @param enumType requested enum class
     * @param <E>      enum type
     * @return matching enum constant
     * @throws NullPointerException     if enumType is null
     * @throws IllegalArgumentException if no constant matches
     */
    public <E extends Enum<E>> E asEnum(Class<E> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        if (enumType.isInstance(value)) return enumType.cast(value);
        String name = text();
        for (E constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) return constant;
        }
        throw new IllegalArgumentException("No " + enumType.getSimpleName() + " constant matches: " + name);
    }

    /**
     * Converts this property to a universally unique identifier.
     *
     * @return value converted to a UUID
     * @throws IllegalArgumentException if conversion fails
     */
    public UUID asUuid() {
        return value instanceof UUID uuid ? uuid : UUID.fromString(text());
    }

    /**
     * Converts this property to a filesystem path.
     *
     * @return value converted to a path
     * @throws java.nio.file.InvalidPathException if conversion fails
     */
    public Path asPath() {
        return value instanceof Path path ? path : Path.of(text());
    }

    /**
     * Converts this property to a uniform resource identifier.
     *
     * @return value converted to a URI
     * @throws IllegalArgumentException if conversion fails
     */
    public URI asUri() {
        return value instanceof URI uri ? uri : URI.create(text());
    }

    /**
     * Converts this property to a uniform resource locator.
     *
     * @return value converted to a URL
     * @throws IllegalArgumentException if conversion fails
     */
    public URL asUrl() {
        if (value instanceof URL url) return url;
        try {
            return asUri().toURL();
        } catch (MalformedURLException error) {
            throw new IllegalArgumentException("Property value is not a valid URL: " + value, error);
        }
    }

    /**
     * Converts this property to an ISO-8601 duration.
     *
     * @return value converted to a duration
     * @throws java.time.format.DateTimeParseException if conversion fails
     */
    public Duration asDuration() {
        return value instanceof Duration duration ? duration : Duration.parse(text());
    }

    /**
     * Converts this property to an ISO-8601 period.
     *
     * @return value converted to a period
     * @throws java.time.format.DateTimeParseException if conversion fails
     */
    public Period asPeriod() {
        return value instanceof Period period ? period : Period.parse(text());
    }

    /**
     * Converts this property to an ISO-8601 instant.
     *
     * @return value converted to an instant
     * @throws java.time.format.DateTimeParseException if conversion fails
     */
    public Instant asInstant() {
        return value instanceof Instant instant ? instant : Instant.parse(text());
    }

    /**
     * Converts this property to an ISO-8601 local date.
     *
     * @return value converted to a local date
     * @throws java.time.format.DateTimeParseException if conversion fails
     */
    public LocalDate asLocalDate() {
        return value instanceof LocalDate date ? date : LocalDate.parse(text());
    }

    /**
     * Converts this property to an ISO-8601 local time.
     *
     * @return value converted to a local time
     * @throws java.time.format.DateTimeParseException if conversion fails
     */
    public LocalTime asLocalTime() {
        return value instanceof LocalTime time ? time : LocalTime.parse(text());
    }

    /**
     * Converts this property to an ISO-8601 local date-time.
     *
     * @return value converted to a local date-time
     * @throws java.time.format.DateTimeParseException if conversion fails
     */
    public LocalDateTime asLocalDateTime() {
        return value instanceof LocalDateTime dateTime ? dateTime : LocalDateTime.parse(text());
    }

    /**
     * Converts this property to an ISO-8601 offset date-time.
     *
     * @return value converted to an offset date-time
     * @throws java.time.format.DateTimeParseException if conversion fails
     */
    public OffsetDateTime asOffsetDateTime() {
        return value instanceof OffsetDateTime dateTime ? dateTime : OffsetDateTime.parse(text());
    }

    /**
     * Converts this property to an ISO-8601 zoned date-time.
     *
     * @return value converted to a zoned date-time
     * @throws java.time.format.DateTimeParseException if conversion fails
     */
    public ZonedDateTime asZonedDateTime() {
        return value instanceof ZonedDateTime dateTime ? dateTime : ZonedDateTime.parse(text());
    }

    /**
     * Converts a language tag to a locale, accepting either hyphens or underscores.
     *
     * @return converted locale
     */
    public Locale asLocale() {
        return value instanceof Locale locale ? locale : Locale.forLanguageTag(text().replace('_', '-'));
    }

    /**
     * Converts this property to an ISO 4217 currency.
     *
     * @return value converted to a currency
     * @throws IllegalArgumentException if conversion fails
     */
    public Currency asCurrency() {
        return value instanceof Currency currency ? currency : Currency.getInstance(text().toUpperCase(Locale.ROOT));
    }

    /**
     * Converts this property to a named character set.
     *
     * @return value converted to a character set
     * @throws java.nio.charset.IllegalCharsetNameException if the name is illegal
     * @throws java.nio.charset.UnsupportedCharsetException if the charset is unavailable
     */
    public Charset asCharset() {
        return value instanceof Charset charset ? charset : Charset.forName(text());
    }

    /**
     * Casts the wrapped value without conversion.
     *
     * @param type requested class
     * @param <R>  requested type
     * @return wrapped value cast to the requested type
     * @throws NullPointerException if type is null
     * @throws ClassCastException   if the value is not an instance of type
     */
    public <R> R as(Class<R> type) {
        return Objects.requireNonNull(type, "type").cast(value);
    }

    /**
     * Tests whether the wrapped value is an instance of a class.
     *
     * @param type class to test
     * @return whether type accepts the wrapped value
     * @throws NullPointerException if type is null
     */
    public boolean is(Class<?> type) {
        return Objects.requireNonNull(type, "type").isInstance(value);
    }

    private String text() {
        return asString().trim();
    }
}
