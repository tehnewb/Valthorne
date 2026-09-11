package valthorne.ui;

import valthorne.ui.enums.LayoutUnit;

/**
 * Represents a value used for layout calculations. The value is expressed in
 * a specific unit, which can be either auto, points, or percent.
 * <p>
 * Instances are immutable and may be safely shared between layout configurations.
 * Point values express fixed layout units, while percentages use 100 for the full
 * reference extent, not 1. The layout property and solver determine the reference
 * size and the interpretation of auto; this value object performs no resolution.
 *
 * <p>Factories retain numeric inputs without range or finiteness validation.
 * Negative values, infinities and NaN can therefore exist in point or percentage
 * values, and callers must honor the receiving property's contract. Auto stores
 * NaN as its numeric payload and is identified by its unit, not by that payload.</p>
 *
 * <p>Auto, positive zero points and 100 percent reuse cached instances. Other
 * factory calls allocate values. Equality compares the unit and Float.compare
 * semantics: signed zeros differ, while NaN payloads compare equal. Use value
 * equality rather than object identity for layout-change detection.</p>
 *
 * @author Albert Beaupre
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
    /**
     * Shared positive-zero point value. Negative zero is not mapped to this
     * instance because it is distinct under this class's float equality policy.
     */
    private static final LayoutValue ZERO = new LayoutValue(LayoutUnit.POINT, 0);
    /**
     * Shared 100-percent value representing the full reference extent chosen by
     * the receiving layout property.
     */
    private static final LayoutValue FULL = new LayoutValue(LayoutUnit.PERCENT, 100);
    private final LayoutUnit unit; // Fixed interpretation of the numeric payload: auto, points or percent.
    private final float value; // Original numeric payload; automatic values use NaN.

    /**
     * Stores a factory-supplied unit and scalar without validation or normalization.
     * Public factories supply the unit and decide whether to reuse a cached value.
     *
     * @param unit  the layout measurement category
     * @param value the numeric payload, including NaN for automatic values
     */
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
     * Positive zero reuses a shared instance; negative zero and every other value
     * create a new instance. No range or finiteness checks are performed, and the
     * numeric value is retained exactly as supplied.
     *
     * @param value the measurement value in points.
     * @return a {@code LayoutValue} instance with the unit set to {@code LayoutUnit.POINT}
     * and the specified value.
     */
    public static LayoutValue points(float value) {
        return Float.floatToRawIntBits(value) == 0 ? ZERO : new LayoutValue(LayoutUnit.POINT, value);
    }

    /**
     * Creates a {@code LayoutValue} instance representing a measurement in percent.
     * The "percent" unit is typically used for specifying relative sizes
     * or positions in layout calculations based on a reference value or context.
     * Supply 100 for the full reference extent. Exactly 100 reuses a shared
     * instance; all other values allocate a new one. Inputs are not clamped to
     * 0-100 or checked for finiteness.
     *
     * @param value the percentage measurement value.
     * @return a {@code LayoutValue} instance with the unit set to {@code LayoutUnit.PERCENT}
     * and the specified value.
     */
    public static LayoutValue percent(float value) {
        return value == 100 ? FULL : new LayoutValue(LayoutUnit.PERCENT, value);
    }

    /**
     * Compares unit and numeric value without converting between units. NaN values
     * compare equal within the same unit, while positive and negative zero differ.
     * Null and objects of other types do not match.
     *
     * @param other the candidate value
     * @return whether both layout unit and float value match
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof LayoutValue layout && unit == layout.unit && Float.compare(value, layout.value) == 0;
    }

    /**
     * Combines the unit hash with the float hash, preserving the signed-zero and
     * canonical-NaN distinctions used by equals. The result remains stable for
     * this immutable instance and is intended for in-process collections.
     *
     * @return the hash consistent with value equality
     */
    @Override
    public int hashCode() {return 31 * unit.hashCode() + Float.hashCode(value);}

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
     * Auto returns NaN. This accessor returns the stored payload without resolving
     * percentages against a parent or converting point values into screen pixels.
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
