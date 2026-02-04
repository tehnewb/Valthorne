package valthorne.ui;

/**
 * Represents a value with a specific type and associated numeric value.
 * <p>
 * A Value instance is defined by:
 * - A {@link ValueType} that specifies the type of the value (e.g. percentage, pixels, etc.).
 * - A numeric value, represented as a float, which corresponds to the amount or measurement.
 * <p>
 * This class is immutable and is typically used to handle measurements or
 * size specifications in a uniform manner.
 *
 * @param type   the {@link ValueType} representing the type of the value
 * @param number the numeric value associated with the type
 * @author Albert Beaupre
 */
public record Value(ValueType type, float number) {

    /**
     * Creates a new {@link Value} instance of type {@link ValueType#FILL} with a numeric value of 1.
     * This represents a value that is used to fill the available space.
     *
     * @return a {@link Value} instance representing the FILL behavior
     */
    public static Value fill() {
        return new Value(ValueType.FILL, 1);
    }

    /**
     * Creates a new {@link Value} instance of type {@link ValueType#AUTO} with a numeric value of 0.
     * This represents a value that is automatically determined based on context or fallback logic.
     *
     * @return a {@link Value} instance representing the AUTO behavior
     */
    public static Value auto() {
        return new Value(ValueType.AUTO, 0);
    }

    /**
     * Creates a {@link Value} instance representing a percentage value.
     *
     * @param number the percentage value as a float
     * @return a {@link Value} instance of type {@link ValueType#PERCENTAGE} containing the given percentage value
     */
    public static Value percentage(float number) {
        return new Value(ValueType.PERCENTAGE, number);
    }

    /**
     * Creates a {@link Value} instance representing an absolute pixel value.
     *
     * @param number the numeric value in pixels
     * @return a {@link Value} instance of type {@link ValueType#PIXELS} containing the given pixel value
     */
    public static Value pixels(float number) {
        return new Value(ValueType.PIXELS, number);
    }

    /**
     * Resolves the final value based on the type of the value, its numeric value,
     * and contextual parameters such as origin, size, and fallback.
     *
     * @param origin   the origin offset (e.g., x or y coordinate) to calculate the value against
     * @param size     the reference size (e.g., width or height) to determine proportional or relative values
     * @param fallback the fallback value to use if the resolution cannot be determined from the type or number
     * @return the resolved float value based on the inputs and the value's type
     */
    public float resolve(float origin, float size, float fallback) {
        return type.resolve(number, origin, size, fallback);
    }

    /**
     * Checks if the {@link ValueType} of this instance matches the specified type.
     *
     * @param type the {@link ValueType} to compare against
     * @return true if the type of this instance matches the specified {@link ValueType}, false otherwise
     */
    public boolean is(ValueType type) {
        return this.type == type;
    }
}
