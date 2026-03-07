package valthorne.utility;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * <h1>NumberUtility</h1>
 *
 * <p>
 * {@code NumberUtility} is a mutable, chainable numeric helper built around a single internal
 * {@code double} value. It is designed for situations where you want to progressively transform
 * a number through a fluent API without repeatedly reassigning temporary variables.
 * </p>
 *
 * <p>
 * This class extends {@link Number}, which means it can still be used in places where a normal
 * numeric wrapper is expected, while also exposing many convenience methods for arithmetic,
 * rounding, comparisons, percentage operations, trigonometry, logarithms, bitwise operations,
 * formatting, and unit conversion.
 * </p>
 *
 * <h2>How this class works</h2>
 * <ul>
 *     <li>The instance stores one mutable numeric value in {@link #number}.</li>
 *     <li>Most modifier methods update that value and return {@code this} for chaining.</li>
 *     <li>Query methods return derived information without replacing the stored value.</li>
 *     <li>Because the internal representation is a {@code double}, some operations are approximate by nature.</li>
 * </ul>
 *
 * <h2>When this class is useful</h2>
 * <ul>
 *     <li>Quick math pipelines in gameplay code</li>
 *     <li>Formatting numbers for UI or debugging output</li>
 *     <li>Percentage and clamping operations</li>
 *     <li>Simple rounding or conversion helpers</li>
 *     <li>Readable chained numeric transformations</li>
 * </ul>
 *
 * <h2>Important notes</h2>
 * <ul>
 *     <li>This class is mutable and not thread-safe.</li>
 *     <li>Bitwise methods cast the current value to an {@code int} before applying the operation.</li>
 *     <li>Methods such as {@link #fact()}, {@link #sqrt()}, {@link #log()}, and {@link #recip()} validate input and throw when invalid.</li>
 *     <li>Because this class stores a {@code double}, floating-point precision rules still apply.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * NumberUtility damage = new NumberUtility(125);
 *
 * double result = damage
 *         .incrByPerc(10)
 *         .clamp(0, 200)
 *         .round(2)
 *         .doubleValue();
 *
 * System.out.println(result);
 *
 * String formatted = new NumberUtility(15342.875)
 *         .divide(3)
 *         .round(2)
 *         .format("#,##0.00");
 *
 * System.out.println(formatted);
 *
 * NumberUtility movement = new NumberUtility(64);
 * movement
 *         .mult(1.5)
 *         .subtr(4)
 *         .ceil();
 *
 * System.out.println(movement.intValue());
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class NumberUtility extends Number {

    /**
     * Shared decimal formatter used by {@link #format(String)} to produce custom string output.
     *
     * <p>
     * This formatter is reused to avoid unnecessary allocation during repeated formatting calls.
     * Because {@link DecimalFormat} is mutable, access is synchronized inside the format method.
     * </p>
     */
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat();

    private double number; // The current mutable numeric value wrapped by this utility instance.

    /**
     * Creates a new utility initialized to zero.
     *
     * <p>
     * This constructor is useful when you want to create the utility first and assign or build
     * the value later through chained operations.
     * </p>
     */
    public NumberUtility() {
        this(0D);
    }

    /**
     * Creates a new utility initialized with the provided number.
     *
     * <p>
     * The given value becomes the internal mutable number used by every later operation.
     * </p>
     *
     * @param number the initial numeric value
     */
    public NumberUtility(double number) {
        this.number = number;
    }

    /**
     * Replaces the current internal value.
     *
     * <p>
     * This is useful when reusing the same instance for a new numeric workflow.
     * </p>
     *
     * @param number the new value to store
     * @return this utility instance for chaining
     */
    public NumberUtility set(double number) {
        this.number = number;
        return this;
    }

    /**
     * Replaces the current value with the requested percentage of the current value.
     *
     * <p>
     * For example, if the current value is {@code 200} and the input percentage is {@code 25},
     * the new value becomes {@code 50}.
     * </p>
     *
     * @param input the percentage to extract from the current value
     * @return this utility instance for chaining
     */
    public NumberUtility percent(double input) {
        this.number = number * (input / 100D);
        return this;
    }

    /**
     * Clamps the current value between the provided minimum and maximum.
     *
     * <p>
     * If the value is below {@code min}, it becomes {@code min}. If it is above {@code max},
     * it becomes {@code max}. Otherwise it remains unchanged.
     * </p>
     *
     * @param min the minimum allowed value
     * @param max the maximum allowed value
     * @return this utility instance for chaining
     */
    public NumberUtility clamp(double min, double max) {
        this.number = Math.min(Math.max(number, min), max);
        return this;
    }

    /**
     * Checks whether the current value lies within the provided inclusive range.
     *
     * <p>
     * Both range endpoints are included in the comparison.
     * </p>
     *
     * @param min the lower inclusive bound
     * @param max the upper inclusive bound
     * @return true if the current value is within the range, otherwise false
     */
    public boolean within(double min, double max) {
        return number >= min && number <= max;
    }

    /**
     * Adds the provided value to the current number.
     *
     * @param value the amount to add
     * @return this utility instance for chaining
     */
    public NumberUtility add(double value) {
        this.number += value;
        return this;
    }

    /**
     * Subtracts the provided value from the current number.
     *
     * @param value the amount to subtract
     * @return this utility instance for chaining
     */
    public NumberUtility subtr(double value) {
        this.number -= value;
        return this;
    }

    /**
     * Multiplies the current value by the provided factor.
     *
     * @param factor the multiplier to apply
     * @return this utility instance for chaining
     */
    public NumberUtility mult(double factor) {
        this.number *= factor;
        return this;
    }

    /**
     * Divides the current value by the provided divisor.
     *
     * <p>
     * This method follows normal Java floating-point division behavior.
     * </p>
     *
     * @param divisor the divisor to use
     * @return this utility instance for chaining
     */
    public NumberUtility divide(double divisor) {
        this.number /= divisor;
        return this;
    }

    /**
     * Raises the current value to the specified exponent.
     *
     * <p>
     * This method delegates to {@link Math#pow(double, double)} using the current number
     * as the base.
     * </p>
     *
     * @param exponent the exponent to apply
     * @return this utility instance for chaining
     */
    public NumberUtility power(double exponent) {
        this.number = Math.pow(number, exponent);
        return this;
    }

    /**
     * Rounds the current value to the given number of decimal places using
     * {@link RoundingMode#HALF_UP}.
     *
     * @param decimalPlaces the number of decimal places to retain
     * @return this utility instance for chaining
     */
    public NumberUtility round(int decimalPlaces) {
        return round(decimalPlaces, RoundingMode.HALF_UP);
    }

    /**
     * Rounds the current value to the given number of decimal places using the specified rounding mode.
     *
     * <p>
     * This method uses {@link BigDecimal} so the rounding mode is explicit and predictable.
     * </p>
     *
     * @param decimalPlaces the number of decimal places to retain
     * @param roundingMode  the rounding mode to apply
     * @return this utility instance for chaining
     * @throws NullPointerException if {@code roundingMode} is null
     */
    public NumberUtility round(int decimalPlaces, RoundingMode roundingMode) {
        Objects.requireNonNull(roundingMode, "roundingMode cannot be null");
        BigDecimal bd = BigDecimal.valueOf(number);
        bd = bd.setScale(decimalPlaces, roundingMode);
        this.number = bd.doubleValue();
        return this;
    }

    /**
     * Replaces the current value with its absolute value.
     *
     * @return this utility instance for chaining
     */
    public NumberUtility abs() {
        this.number = Math.abs(number);
        return this;
    }

    /**
     * Negates the current value.
     *
     * <p>
     * Positive values become negative, negative values become positive, and zero remains zero.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public NumberUtility negate() {
        this.number = -number;
        return this;
    }

    /**
     * Replaces the current value with a random number in the provided range.
     *
     * <p>
     * The range is generated using {@link ThreadLocalRandom}. The minimum is inclusive and the
     * maximum is exclusive in practice, following the behavior of uniform floating-point generation.
     * </p>
     *
     * @param min the lower bound of the random range
     * @param max the upper bound of the random range
     * @return this utility instance for chaining
     */
    public NumberUtility randomize(double min, double max) {
        this.number = ThreadLocalRandom.current().nextDouble(min, max);
        return this;
    }

    /**
     * Checks whether the current value is exactly equal to another value.
     *
     * <p>
     * This uses {@link Double#compare(double, double)} for an exact floating-point comparison.
     * </p>
     *
     * @param otherValue the value to compare against
     * @return true if the two values are exactly equal, otherwise false
     */
    public boolean isEqualTo(double otherValue) {
        return Double.compare(number, otherValue) == 0;
    }

    /**
     * Checks whether the current value is greater than the provided value.
     *
     * @param otherValue the comparison target
     * @return true if the current value is greater
     */
    public boolean isGreaterThan(double otherValue) {
        return number > otherValue;
    }

    /**
     * Checks whether the current value is less than the provided value.
     *
     * @param otherValue the comparison target
     * @return true if the current value is less
     */
    public boolean isLessThan(double otherValue) {
        return number < otherValue;
    }

    /**
     * Formats the current value using a custom {@link DecimalFormat} pattern.
     *
     * <p>
     * The shared formatter is synchronized because {@link DecimalFormat} is mutable and not thread-safe.
     * </p>
     *
     * @param formatPattern the decimal format pattern to apply
     * @return the formatted numeric string
     * @throws NullPointerException if {@code formatPattern} is null
     */
    public String format(String formatPattern) {
        Objects.requireNonNull(formatPattern, "formatPattern cannot be null");
        synchronized (DECIMAL_FORMAT) {
            DECIMAL_FORMAT.applyPattern(formatPattern);
            return DECIMAL_FORMAT.format(number);
        }
    }

    /**
     * Returns the current value as an {@code int}.
     *
     * <p>
     * This performs a narrowing primitive conversion using Java cast rules.
     * </p>
     *
     * @return the current value converted to int
     */
    @Override
    public int intValue() {
        return (int) number;
    }

    /**
     * Returns the current value as a {@code long}.
     *
     * <p>
     * This performs a narrowing primitive conversion using Java cast rules.
     * </p>
     *
     * @return the current value converted to long
     */
    @Override
    public long longValue() {
        return (long) number;
    }

    /**
     * Returns the current value as a {@code float}.
     *
     * <p>
     * This performs a narrowing primitive conversion using Java cast rules.
     * </p>
     *
     * @return the current value converted to float
     */
    @Override
    public float floatValue() {
        return (float) number;
    }

    /**
     * Returns the current value as a {@code double}.
     *
     * @return the current stored double value
     */
    @Override
    public double doubleValue() {
        return number;
    }

    /**
     * Returns the fractional part of the current value.
     *
     * <p>
     * This subtracts the truncated integer portion from the current number.
     * </p>
     *
     * @return the fractional component of the current value
     */
    public double getFractPart() {
        return number - (long) number;
    }

    /**
     * Increases the current value by the specified percentage.
     *
     * <p>
     * For example, increasing {@code 100} by {@code 25} results in {@code 125}.
     * </p>
     *
     * @param percentage the percentage increase to apply
     * @return this utility instance for chaining
     */
    public NumberUtility incrByPerc(double percentage) {
        this.number = number * (1 + (percentage / 100));
        return this;
    }

    /**
     * Decreases the current value by the specified percentage.
     *
     * <p>
     * For example, decreasing {@code 100} by {@code 25} results in {@code 75}.
     * </p>
     *
     * @param percentage the percentage decrease to apply
     * @return this utility instance for chaining
     */
    public NumberUtility decrByPerc(double percentage) {
        this.number = number * (1 - (percentage / 100));
        return this;
    }

    /**
     * Removes the fractional component of the current value.
     *
     * <p>
     * This behaves like numeric truncation toward zero.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public NumberUtility truncate() {
        this.number = (double) ((long) number);
        return this;
    }

    /**
     * Replaces the current value with its mathematical ceiling.
     *
     * @return this utility instance for chaining
     */
    public NumberUtility ceil() {
        this.number = Math.ceil(number);
        return this;
    }

    /**
     * Replaces the current value with its mathematical floor.
     *
     * @return this utility instance for chaining
     */
    public NumberUtility flr() {
        this.number = Math.floor(number);
        return this;
    }

    /**
     * Returns the sign of the current value.
     *
     * <p>
     * The result is {@code -1}, {@code 0}, or {@code 1} depending on whether the value is
     * negative, zero, or positive.
     * </p>
     *
     * @return the sign indicator of the current value
     */
    public int signum() {
        return Double.compare(number, 0.0);
    }

    /**
     * Computes the percentage difference between the current value and another value.
     *
     * <p>
     * The calculation is based on the absolute difference divided by the absolute value of
     * {@code otherValue}, then multiplied by {@code 100}.
     * </p>
     *
     * @param otherValue the reference value
     * @return the percentage difference relative to {@code otherValue}
     */
    public double percDiff(double otherValue) {
        if (otherValue == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return (Math.abs(number - otherValue) / Math.abs(otherValue)) * 100.0;
    }

    /**
     * Replaces the current value with its factorial.
     *
     * <p>
     * This method only accepts non-negative whole-number values. Because the result is stored back
     * into a {@code double}, very large factorials will eventually lose precision.
     * </p>
     *
     * @return this utility instance for chaining
     * @throws ArithmeticException if the current value is negative or not a whole number
     */
    public NumberUtility fact() {
        if (number >= 0 && number == Math.floor(number)) {
            double result = 1;
            for (int i = 1; i <= (int) number; i++) {
                result *= i;
            }
            this.number = result;
            return this;
        }
        throw new ArithmeticException("Invalid factorial value: " + number);
    }

    /**
     * Replaces the current value with Euler's exponential function of the current value.
     *
     * <p>
     * This method delegates to {@link Math#exp(double)}.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public NumberUtility exp() {
        this.number = Math.exp(number);
        return this;
    }

    /**
     * Replaces the current value with its natural logarithm.
     *
     * <p>
     * This method is only valid for positive values.
     * </p>
     *
     * @return this utility instance for chaining
     * @throws ArithmeticException if the current value is not positive
     */
    public NumberUtility log() {
        if (number > 0) {
            this.number = Math.log(number);
            return this;
        }
        throw new ArithmeticException("Cannot calculate the natural logarithm for: " + number);
    }

    /**
     * Rounds the current value to the requested decimal place using {@link Math#round(float)}-style scaling.
     *
     * <p>
     * This is a lightweight rounding method compared to the {@link BigDecimal}-based
     * {@link #round(int, RoundingMode)} method.
     * </p>
     *
     * @param decimalPlace the number of decimal places to retain
     * @return this utility instance for chaining
     */
    public NumberUtility roundToDecimalPlace(int decimalPlace) {
        double scale = Math.pow(10, decimalPlace);
        this.number = Math.round(number * scale) / scale;
        return this;
    }

    /**
     * Replaces the current value with its sine.
     *
     * <p>
     * The input is interpreted in radians.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public NumberUtility sin() {
        this.number = Math.sin(number);
        return this;
    }

    /**
     * Replaces the current value with its cosine.
     *
     * <p>
     * The input is interpreted in radians.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public NumberUtility cos() {
        this.number = Math.cos(number);
        return this;
    }

    /**
     * Replaces the current value with its tangent.
     *
     * <p>
     * The input is interpreted in radians.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public NumberUtility tan() {
        this.number = Math.tan(number);
        return this;
    }

    /**
     * Replaces the current value with its square root.
     *
     * <p>
     * This method is only valid for zero and positive values.
     * </p>
     *
     * @return this utility instance for chaining
     * @throws ArithmeticException if the current value is negative
     */
    public NumberUtility sqrt() {
        if (number >= 0) {
            this.number = Math.sqrt(number);
            return this;
        }
        throw new ArithmeticException("Square root of a negative number is undefined.");
    }

    /**
     * Replaces the current value with its cube root.
     *
     * <p>
     * This method delegates to {@link Math#cbrt(double)} and supports negative inputs.
     * </p>
     *
     * @return this utility instance for chaining
     */
    public NumberUtility cubeRoot() {
        this.number = Math.cbrt(number);
        return this;
    }

    /**
     * Replaces the current value with the hypotenuse of the current value and another side.
     *
     * <p>
     * This method delegates to {@link Math#hypot(double, double)}.
     * </p>
     *
     * @param otherSide the second side length
     * @return this utility instance for chaining
     */
    public NumberUtility hypot(double otherSide) {
        this.number = Math.hypot(number, otherSide);
        return this;
    }

    /**
     * Raises the provided base to the power of the current value.
     *
     * <p>
     * This is the inverse ordering of {@link #power(double)}.
     * </p>
     *
     * @param base the base to raise
     * @return this utility instance for chaining
     */
    public NumberUtility pow(double base) {
        this.number = Math.pow(base, number);
        return this;
    }

    /**
     * Applies a bitwise AND between the integer form of the current value and the provided value.
     *
     * <p>
     * The current value is first cast to {@code int}, the bitwise operation is applied, and the
     * resulting integer is stored back as a {@code double}.
     * </p>
     *
     * @param otherValue the integer value to AND with
     * @return this utility instance for chaining
     */
    public NumberUtility bitAnd(int otherValue) {
        this.number = (int) number & otherValue;
        return this;
    }

    /**
     * Applies a bitwise OR between the integer form of the current value and the provided value.
     *
     * <p>
     * The current value is first cast to {@code int}, the bitwise operation is applied, and the
     * resulting integer is stored back as a {@code double}.
     * </p>
     *
     * @param otherValue the integer value to OR with
     * @return this utility instance for chaining
     */
    public NumberUtility bitOr(int otherValue) {
        this.number = (int) number | otherValue;
        return this;
    }

    /**
     * Applies a bitwise XOR between the integer form of the current value and the provided value.
     *
     * <p>
     * The current value is first cast to {@code int}, the bitwise operation is applied, and the
     * resulting integer is stored back as a {@code double}.
     * </p>
     *
     * @param otherValue the integer value to XOR with
     * @return this utility instance for chaining
     */
    public NumberUtility bitXor(int otherValue) {
        this.number = (int) number ^ otherValue;
        return this;
    }

    /**
     * Replaces the current value with its logarithm in the specified base.
     *
     * <p>
     * Both the current value and the base must be positive, and the base must not equal {@code 1}.
     * </p>
     *
     * @param base the logarithm base
     * @return this utility instance for chaining
     * @throws ArithmeticException if the input or base is invalid
     */
    public NumberUtility log(double base) {
        if (number > 0 && base > 0 && base != 1.0) {
            this.number = Math.log(number) / Math.log(base);
            return this;
        }
        throw new ArithmeticException("Logarithm input must be positive and base must not equal 1.");
    }

    /**
     * Checks whether the current value is approximately equal to another value using a tolerance.
     *
     * @param otherValue the comparison target
     * @param epsilon    the maximum allowed absolute difference
     * @return true if the values are within {@code epsilon} of each other
     */
    public boolean isApproxEqualTo(double otherValue, double epsilon) {
        return Math.abs(number - otherValue) <= epsilon;
    }

    /**
     * Rounds the current value to the nearest multiple of the provided step.
     *
     * <p>
     * For example, rounding {@code 37} to the nearest multiple of {@code 5} produces {@code 35}.
     * </p>
     *
     * @param multiple the multiple to round toward
     * @return this utility instance for chaining
     */
    public NumberUtility roundToNearestMultiple(double multiple) {
        this.number = Math.round(number / multiple) * multiple;
        return this;
    }

    /**
     * Replaces the current value with its reciprocal.
     *
     * <p>
     * The reciprocal is {@code 1 / number}.
     * </p>
     *
     * @return this utility instance for chaining
     * @throws ArithmeticException if the current value is zero
     */
    public NumberUtility recip() {
        if (number != 0) {
            this.number = 1 / number;
            return this;
        }
        throw new ArithmeticException("Reciprocal of zero is undefined.");
    }

    /**
     * Checks whether the integer form of the current value is even.
     *
     * <p>
     * The current value is cast to {@code long} before evaluation.
     * </p>
     *
     * @return true if the integer form is even
     */
    public boolean isEven() {
        return ((long) number) % 2 == 0;
    }

    /**
     * Checks whether the integer form of the current value is odd.
     *
     * <p>
     * The current value is cast to {@code long} before evaluation.
     * </p>
     *
     * @return true if the integer form is odd
     */
    public boolean isOdd() {
        return ((long) number) % 2 != 0;
    }

    /**
     * Computes the absolute difference between the current value and another value.
     *
     * @param otherValue the comparison target
     * @return the absolute numeric difference
     */
    public double absDiff(double otherValue) {
        return Math.abs(number - otherValue);
    }

    /**
     * Converts the current value between {@link TimeUnit}s.
     *
     * <p>
     * The current number is cast to {@code long} before conversion.
     * </p>
     *
     * @param from the source time unit
     * @param to   the target time unit
     * @return the converted long value
     * @throws NullPointerException if {@code from} or {@code to} is null
     */
    public long convert(TimeUnit from, TimeUnit to) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        return to.convert((long) number, from);
    }

    /**
     * Returns the current internal number as a string.
     *
     * <p>
     * This method delegates to {@link String#valueOf(double)}.
     * </p>
     *
     * @return the current numeric value as text
     */
    @Override
    public String toString() {
        return String.valueOf(number);
    }
}