package valthorne.utility;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

/**
 * A utility class for manipulating and mutating a numeric value.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class NumberUtility extends Number {

    private static final DecimalFormat df = new DecimalFormat();
    private double number; // the number to mutate

    /**
     * Constructs a new Value object with a default value of 0.
     */
    public NumberUtility() {
        this(0);
    }

    /**
     * Constructs a new Value object using a number to provide an initial value.
     *
     * @param number The initial number.
     */
    public NumberUtility(double number) {
        this.number = number;
    }

    /**
     * Sets the number of this Value to the specified number.
     *
     * @param number The number to set
     * @return This Value object after the number change.
     */
    public NumberUtility set(double number) {
        this.number = number;
        return this;
    }

    /**
     * Mutates the value by a specified percentage.
     *
     * @param input The percentage by which to adjust the value.
     * @return This Value object after the mutation.
     */
    public NumberUtility percent(double input) {
        return new NumberUtility(number * (input / 100D));
    }

    /**
     * Restricts the value to be within a specified range.
     *
     * @param min The minimum value in the range.
     * @param max The maximum value in the range.
     * @return This Value object after the restriction.
     */
    public NumberUtility clamp(double min, double max) {
        return new NumberUtility(Math.min(Math.max(number, min), max));
    }

    /**
     * Checks if this value is within the specified range.
     *
     * @param min The minimum value in the range.
     * @param max The maximum value in the range.
     * @return This Value object after the restriction.
     */
    public boolean within(double min, double max) {
        return number >= min && number <= max;
    }

    /**
     * Adds a specified value to the current value.
     *
     * @param value The value to add.
     * @return This Value object after the addition.
     */
    public NumberUtility add(double value) {
        return new NumberUtility(this.number + value);
    }

    /**
     * Subtracts a specified value from the current value.
     *
     * @param value The value to subtract.
     * @return This Value object after the subtraction.
     */
    public NumberUtility subtr(double value) {
        return new NumberUtility(this.number - value);
    }

    /**
     * Multiplies the current value by a specified factor.
     *
     * @param factor The factor by which to multiply the value.
     * @return This Value object after the multiplication.
     */
    public NumberUtility mult(double factor) {
        return new NumberUtility(this.number * factor);
    }

    /**
     * Divides the current value by a specified divisor.
     *
     * @param divisor The divisor by which to divide the value.
     * @return This Value object after the division.
     */
    public NumberUtility divide(double divisor) {
        return new NumberUtility(this.number / divisor);
    }

    /**
     * Raises the current value to a specified power.
     *
     * @param exponent The exponent to which to raise the value.
     * @return This Value object after the exponentiation.
     */
    public NumberUtility power(double exponent) {
        return new NumberUtility(Math.pow(number, exponent));
    }

    /**
     * Rounds the current value to a specified number of decimal places using the default rounding mode (HALF_UP).
     *
     * @param decimalPlaces The number of decimal places to round to.
     * @return This Value object after rounding.
     */
    public NumberUtility round(int decimalPlaces) {
        return round(decimalPlaces, RoundingMode.HALF_UP);
    }

    /**
     * Rounds the current value to a specified number of decimal places using the specified rounding mode.
     *
     * @param decimalPlaces The number of decimal places to round to.
     * @param roundingMode  The rounding mode to use.
     * @return This Value object after rounding.
     */
    public NumberUtility round(int decimalPlaces, RoundingMode roundingMode) {
        BigDecimal bd = new BigDecimal(number);
        bd = bd.setScale(decimalPlaces, roundingMode);
        return new NumberUtility(bd.doubleValue());
    }

    /**
     * Ensures that the current value is positive.
     *
     * @return This Value object with a positive value.
     */
    public NumberUtility abs() {
        return new NumberUtility(Math.abs(number));
    }

    /**
     * Negates the current value.
     *
     * @return This Value object with the negated value.
     */
    public NumberUtility negate() {
        return new NumberUtility(-number);
    }

    /**
     * Sets the current value to a random number within a specified range.
     *
     * @param min The minimum value for the random number.
     * @param max The maximum value for the random number.
     * @return This Value object with the random value.
     */
    public NumberUtility randomize(double min, double max) {
        return new NumberUtility(min + (Math.random() * (max - min)));
    }

    /**
     * Checks if the current value is equal to a specified value.
     *
     * @param otherValue The value to compare with.
     * @return true if the values are equal, false otherwise.
     */
    public boolean isEqualTo(double otherValue) {
        return Double.compare(number, otherValue) == 0;
    }

    /**
     * Checks if the current value is greater than a specified value.
     *
     * @param otherValue The value to compare with.
     * @return true if the current value is greater, false otherwise.
     */
    public boolean isGreaterThan(double otherValue) {
        return number > otherValue;
    }

    /**
     * Checks if the current value is less than a specified value.
     *
     * @param otherValue The value to compare with.
     * @return true if the current value is less, false otherwise.
     */
    public boolean isLessThan(double otherValue) {
        return number < otherValue;
    }

    /**
     * Formats the current value as a string using the specified format pattern.
     *
     * @param formatPattern The format pattern to use.
     * @return The formatted string representation of the current value.
     */
    public String format(String formatPattern) {
        df.applyPattern(formatPattern);
        return df.format(number);
    }

    /**
     * Converts the current value to an integer.
     *
     * @return The integer representation of the current value.
     */
    public int intValue() {
        return (int) number;
    }

    /**
     * Converts the current value to a long.
     *
     * @return The long representation of the current value.
     */
    public long longValue() {
        return (long) number;
    }

    /**
     * Converts the current value to a float.
     *
     * @return The float representation of the current value.
     */
    public float floatValue() {
        return (float) number;
    }

    /**
     * Converts the current value to a double.
     *
     * @return The double representation of the current value.
     */
    public double doubleValue() {
        return number;
    }

    /**
     * Gets the fractional part of the current value.
     *
     * @return The fractional part of the current value.
     */
    public double getFractPart() {
        return number - (int) number;
    }

    /**
     * Increases the current value by a specified percentage.
     *
     * @param percentage The percentage by which to increase the value.
     * @return This Value object after the increase.
     */
    public NumberUtility incrByPerc(double percentage) {
        return new NumberUtility(number * (1 + (percentage / 100)));
    }

    /**
     * Decreases the current value by a specified percentage.
     *
     * @param percentage The percentage by which to decrease the value.
     * @return This Value object after the decrease.
     */
    public NumberUtility decrByPerc(double percentage) {
        return new NumberUtility(number * (1 - (percentage / 100)));
    }

    /**
     * Truncates the decimal part of the current value.
     *
     * @return This Value object after truncation.
     */
    public NumberUtility truncate() {
        return new NumberUtility((double) ((int) number));
    }

    /**
     * Rounds the current value up to the nearest integer (ceiling).
     *
     * @return This Value object after rounding up.
     */
    public NumberUtility ceil() {
        return new NumberUtility(Math.ceil(number));
    }

    /**
     * Rounds the current value down to the nearest integer (floor).
     *
     * @return This Value object after rounding down.
     */
    public NumberUtility flr() {
        return new NumberUtility(Math.floor(number));
    }

    /**
     * Gets the signum (sign) of the current value.
     *
     * @return 1 if the value is positive, 0 if it's zero, -1 if it's negative.
     */
    public int signum() {
        return Double.compare(number, 0.0);
    }

    /**
     * Calculates the percentage difference between the current value and another value.
     *
     * @param otherValue The value to compare with.
     * @return The percentage difference between the values.
     */
    public double percDiff(double otherValue) {
        if (otherValue == 0)
            return Double.POSITIVE_INFINITY;
        return (Math.abs(number - otherValue) / Math.abs(otherValue)) * 100.0;
    }

    /**
     * Calculates the factorial of the current value (if it's a non-negative integer).
     *
     * @return This Value object after the factorial calculation.
     */
    public NumberUtility fact() {
        if (number >= 0 && number == Math.floor(number)) {
            double result = 1;
            for (int i = 1; i <= (int) number; i++) {
                result *= i;
            }
            return new NumberUtility(result);
        } else {
            throw new ArithmeticException("Invalid Factorial Value: " + number);
        }
    }

    /**
     * Calculates the exponential (e^x) of the current value.
     *
     * @return This Value object after the exponentiation.
     */
    public NumberUtility exp() {
        return new NumberUtility(Math.exp(number));
    }

    /**
     * Calculates the natural logarithm (ln) of the current value.
     *
     * @return This Value object after the logarithm calculation.
     */
    public NumberUtility log() {
        if (number > 0) {
            return new NumberUtility(Math.log(number));
        } else {
            throw new ArithmeticException("Cannot calculate the natural logarithm for: " + number);
        }
    }

    /**
     * Rounds the current value to the nearest Nth decimal place.
     *
     * @param decimalPlace The decimal place to round to.
     * @return This Value object after rounding.
     */
    public NumberUtility roundToDecimalPlace(int decimalPlace) {
        double scale = Math.pow(10, decimalPlace);
        return new NumberUtility(Math.round(number * scale) / scale);
    }

    /**
     * Calculates the sine of the current value (assuming the value is in radians).
     *
     * @return This Value object after calculating the sine.
     */
    public NumberUtility sin() {
        return new NumberUtility(Math.sin(number));
    }

    /**
     * Calculates the cosine of the current value (assuming the value is in radians).
     *
     * @return This Value object after calculating the cosine.
     */
    public NumberUtility cos() {
        return new NumberUtility(Math.cos(number));
    }

    /**
     * Calculates the tangent of the current value (assuming the value is in radians).
     *
     * @return This Value object after calculating the tangent.
     */
    public NumberUtility tan() {
        return new NumberUtility(Math.tan(number));
    }

    /**
     * Calculates the square root of the current value.
     *
     * @return This Value object after the square root calculation.
     * @throws ArithmeticException if the current value is negative.
     */
    public NumberUtility sqrt() {
        if (number >= 0) {
            return new NumberUtility(Math.sqrt(number));
        } else {
            throw new ArithmeticException("Square root of a negative number is undefined.");
        }
    }

    /**
     * Calculates the cube root of the current value.
     *
     * @return This Value object after the cube root calculation.
     */
    public NumberUtility cubeRoot() {
        return new NumberUtility(Math.cbrt(number));
    }

    /**
     * Calculates the hypotenuse of a right triangle with the current value as one of the sides.
     *
     * @param otherSide The length of the other side of the right triangle.
     * @return This Value object after the hypotenuse calculation.
     */
    public NumberUtility hypot(double otherSide) {
        return new NumberUtility(Math.hypot(number, otherSide));
    }

    /**
     * Calculates the result of raising the current value to a specified base.
     *
     * @param base The base to which the current value is raised.
     * @return This Value object after the exponentiation.
     */
    public NumberUtility pow(double base) {
        return new NumberUtility(Math.pow(base, number));
    }

    /**
     * Performs a bitwise AND operation with the current value and another integer.
     *
     * @param otherValue The integer to perform the bitwise AND operation with.
     * @return This Value object after the bitwise AND operation.
     */
    public NumberUtility bitAnd(int otherValue) {
        return new NumberUtility((int) number & otherValue);
    }

    /**
     * Performs a bitwise OR operation with the current value and another integer.
     *
     * @param otherValue The integer to perform the bitwise OR operation with.
     * @return This Value object after the bitwise OR operation.
     */
    public NumberUtility bitOr(int otherValue) {
        return new NumberUtility((int) number | otherValue);
    }

    /**
     * Performs a bitwise XOR operation with the current value and another integer.
     *
     * @param otherValue The integer to perform the bitwise XOR operation with.
     * @return This Value object after the bitwise XOR operation.
     */
    public NumberUtility bitXor(int otherValue) {
        return new NumberUtility((int) number ^ otherValue);
    }

    /**
     * Calculates the logarithm of the current value with a specified base.
     *
     * @param base The base of the logarithm.
     * @return This Value object after the logarithm calculation.
     * @throws ArithmeticException if the current value is non-positive or if the base is non-positive.
     */
    public NumberUtility log(double base) {
        if (number > 0 && base > 0) {
            return new NumberUtility(Math.log(number) / Math.log(base));
        } else {
            throw new ArithmeticException("Logarithm input must be positive.");
        }
    }

    /**
     * Checks if the current value is approximately equal to another value within a specified epsilon.
     *
     * @param otherValue The value to compare with.
     * @param epsilon    The tolerance for approximate equality.
     * @return true if the values are approximately equal within the epsilon, false otherwise.
     */
    public boolean isApproxEqualTo(double otherValue, double epsilon) {
        return Math.abs(number - otherValue) <= epsilon;
    }

    /**
     * Rounds the current value to the nearest multiple of a specified value.
     *
     * @param multiple The value to round to.
     * @return This Value object after rounding.
     */
    public NumberUtility roundToNearestMultiple(double multiple) {
        return new NumberUtility(Math.round(number / multiple) * multiple);
    }

    /**
     * Calculates the reciprocal (1/x) of the current value.
     *
     * @return This Value object after the reciprocal calculation.
     * @throws ArithmeticException if the current value is zero.
     */
    public NumberUtility recip() {
        if (number != 0) {
            return new NumberUtility(1 / number);
        } else {
            throw new ArithmeticException("Reciprocal of zero is undefined.");
        }
    }

    /**
     * Checks if the current value is even.
     *
     * @return true if the current value is even, false otherwise.
     */
    public boolean isEven() {
        return ((int) number) % 2 == 0;
    }

    /**
     * Checks if the current value is odd.
     *
     * @return true if the current value is odd, false otherwise.
     */
    public boolean isOdd() {
        return ((int) number) % 2 != 0;
    }

    /**
     * Calculates the absolute difference between the current value and another value.
     *
     * @param otherValue The value to compare with.
     * @return The absolute difference between the values.
     */
    public double absDiff(double otherValue) {
        return Math.abs(number - otherValue);
    }

    /**
     * Converts this Value to the specified TimeUnit based on the given TimeUnits.
     *
     * @param from The TimeUnit to convert from.
     * @param to   The TimeUnit to convert to.
     * @return The converted time in a long format.
     */
    public long convert(TimeUnit from, TimeUnit to) {
        return from.convert((long) number, to);
    }

    /**
     * Returns the current numeric value as a string.
     *
     * @return The string representation of the current numeric value.
     */
    @Override
    public String toString() {
        return String.valueOf(number);
    }


}
