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
     * Creates a new {@link Value} instance of type {@link ValueType#FILL} with a numeric value of 0.
     * This represents a value that is used to fill the available space.
     *
     * @return a {@link Value} instance representing the FILL behavior
     */
    public static Value fill() {
        return new Value(ValueType.FILL, 0);
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

}
