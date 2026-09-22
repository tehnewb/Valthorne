package valthorne.graphics.map.ldtk;

import java.util.List;

/**
 * Represents one LDtk custom field while retaining its declared type and decoded,
 * JSON-compatible value. Conversion helpers deliberately return caller-provided
 * fallbacks instead of coercing incompatible scalar types.
 *
 * @param identifier field identifier, normalized to an empty string
 * @param type LDtk field-type descriptor, normalized to an empty string
 * @param value decoded scalar, immutable list or map, or {@code null}
 */
public record LdtkField(String identifier, String type, Object value) {
    /**
     * Normalizes nullable metadata without altering the decoded value.
     *
     * @param identifier field identifier; {@code null} becomes empty
     * @param type field-type descriptor; {@code null} becomes empty
     * @param value decoded JSON-compatible value
     */
    public LdtkField {
        identifier = identifier == null ? "" : identifier;
        type = type == null ? "" : type;
    }

    /**
     * Reports whether the decoded value is an array-like list.
     *
     * @return {@code true} only for list values
     */
    public boolean isArray() {
        return value instanceof List<?>;
    }

    /**
     * Returns the textual representation of the decoded value.
     *
     * @return {@code null} for a null value, otherwise {@link String#valueOf(Object)}
     */
    public String asString() {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Reads the value as an integer when it is numeric.
     *
     * @param fallback value returned for nonnumeric or null data
     * @return the numeric value narrowed with {@link Number#intValue()}, or fallback
     */
    public int asInt(int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    /**
     * Reads the value as a double when it is numeric.
     *
     * @param fallback value returned for nonnumeric or null data
     * @return the numeric value as a double, or fallback
     */
    public double asDouble(double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    /**
     * Reads the value as a boolean without string or numeric coercion.
     *
     * @param fallback value returned for nonboolean or null data
     * @return the stored boolean, or fallback
     */
    public boolean asBoolean(boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }
}
