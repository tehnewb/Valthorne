package valthorne.ui;

import valthorne.ui.enums.LayoutUnit;

/**
 * Represents a value used for layout calculations. The value is expressed in
 * a specific unit, which can be either auto, points, or percent.
 * <p>
 * Instances of this class are immutable.
 */
public final class LayoutValue {

    /**
     * A predefined instance of {@link LayoutValue} representing an "auto" layout value.
     * <p>
     * The "auto" value indicates that the layout should automatically determine its size
     * or position based on the context or parent constraints.
     * <p>
     * This constant has a unit of {@link LayoutUnit#AUTO} and a value of {@code Float.NaN}.
     * It is immutable and can be accessed globally for consistency when working
     * with layout calculations.
     */
    private static final LayoutValue AUTO = new LayoutValue(LayoutUnit.AUTO, Float.NaN);

    private final LayoutUnit unit;
    private final float value;


    private LayoutValue(LayoutUnit unit, float value) {
        this.unit = unit;
        this.value = value;
    }

    /**
     * Returns the predefined "auto" layout value.
     * The "auto" value indicates that the layout should automatically determine its size
     * or position based on the context or parent constraints.
     *
     * @return a {@code LayoutValue} instance representing the "auto" layout value.
     */
    public static LayoutValue auto() {
        return AUTO;
    }

    /**
     * Creates a {@code LayoutValue} instance representing a measurement in points.
     * The "points" unit is typically used for specifying fixed sizes or positions
     * in layout calculations.
     *
     * @param value the measurement value in points.
     * @return a {@code LayoutValue} instance with the unit set to {@code LayoutUnit.POINT}
     * and the specified value.
     */
    public static LayoutValue points(float value) {
        return new LayoutValue(LayoutUnit.POINT, value);
    }

    /**
     * Creates a {@code LayoutValue} instance representing a measurement in percent.
     * The "percent" unit is typically used for specifying relative sizes
     * or positions in layout calculations based on a reference value or context.
     *
     * @param value the percentage measurement value.
     * @return a {@code LayoutValue} instance with the unit set to {@code LayoutUnit.PERCENT}
     * and the specified value.
     */
    public static LayoutValue percent(float value) {
        return new LayoutValue(LayoutUnit.PERCENT, value);
    }

    /**
     * Retrieves the unit of measurement associated with this layout value.
     * The unit indicates whether the value is expressed in points, percent, or is set to 'auto'.
     *
     * @return the {@code LayoutUnit} representing the unit of this layout value.
     */
    public LayoutUnit getUnit() {
        return unit;
    }

    /**
     * Retrieves the numeric value of this layout value. The meaning of the value
     * depends on the associated unit of measurement ({@code LayoutUnit}).
     *
     * @return the numeric value associated with this layout value.
     */
    public float getValue() {
        return value;
    }

    /**
     * Determines if this layout value has the predefined "auto" unit.
     * The "auto" unit indicates that the layout should automatically calculate
     * its size or position based on contextual or parent constraints.
     *
     * @return {@code true} if the layout value is set to {@code LayoutUnit.AUTO},
     * otherwise {@code false}.
     */
    public boolean isAuto() {
        return unit == LayoutUnit.AUTO;
    }

    /**
     * Determines if this layout value is measured in points.
     * The "points" unit is typically used for specifying fixed sizes or positions
     * in layout calculations.
     *
     * @return {@code true} if the layout value is set to {@code LayoutUnit.POINT},
     * otherwise {@code false}.
     */
    public boolean isPoints() {
        return unit == LayoutUnit.POINT;
    }

    /**
     * Determines if the layout value is measured in percent.
     * The "percent" unit is generally used to define relative sizes or positions
     * based on a reference value or context.
     *
     * @return {@code true} if the layout value is set to {@code LayoutUnit.PERCENT},
     * otherwise {@code false}.
     */
    public boolean isPercent() {
        return unit == LayoutUnit.PERCENT;
    }
}