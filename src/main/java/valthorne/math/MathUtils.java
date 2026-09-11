package valthorne.math;

import java.util.Arrays;
import java.util.Random;

/**
 * Static numeric helpers for game calculations: interpolation, angles, random
 * sampling, statistics, easing, distances, and integer bit operations. Methods
 * preserve their documented arithmetic behavior rather than universally validating
 * ranges, clamping inputs, or detecting overflow. Read individual contracts for
 * degenerate ranges and nonfinite values.
 * <p>Random helpers share one pseudorandom generator. Statistical helpers preserve
 * input arrays; median calculations sort copies. Fast square-root helpers trade
 * accuracy and special-value behavior for a compact approximation.
 * <pre>{@code
 * float progress = MathUtils.clamp(elapsed / duration, 0f, 1f);
 * float x = MathUtils.lerp(startX, endX, progress);
 * double mean = MathUtils.average(10, 20, 30);
 * int wrappedFrame = MathUtils.wrap(frame, 0, frameCount - 1);
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class MathUtils {

    /**
     * TAU constant (2π).
     */
    public static final double TAU = Math.PI * 2.0;

    /**
     * TAU constant (2π) as a float.
     */
    public static final float TAU_F = (float) (Math.PI * 2.0);

    /**
     * Degrees to radians multiplier.
     */
    public static final double DEG_TO_RAD = Math.PI / 180.0;

    /**
     * Radians to degrees multiplier.
     */
    public static final double RAD_TO_DEG = 180.0 / Math.PI;

    /**
     * Degrees to radians multiplier as a float.
     */
    public static final float DEG_TO_RAD_F = (float) (Math.PI / 180.0);

    /**
     * Radians to degrees multiplier as a float.
     */
    public static final float RAD_TO_DEG_F = (float) (180.0 / Math.PI);

    /**
     * Shared random generator used by the utility's convenience sampling methods.
     */
    private static final Random RANDOM = new Random();

    /**
     * Rejects construction of this static utility, including reflective invocation.
     *
     * @throws AssertionError whenever invoked
     */
    private MathUtils() {
        throw new AssertionError("No MathUtils instances allowed.");
    }

    /**
     * Limits a value to inclusive bounds using ordered comparisons. Bounds are
     * not reordered or validated.
     *
     * @param value value to constrain
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return value limited by the supplied bounds
     */
    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Limits a value to inclusive bounds using ordered comparisons. Bounds are
     * not reordered or validated.
     *
     * @param value value to constrain
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return value limited by the supplied bounds
     */
    public static long clamp(long value, long min, long max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Limits a value to inclusive bounds using ordered comparisons. Bounds are
     * not reordered or validated. A NaN value passes through unchanged.
     *
     * @param value value to constrain
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return value limited by the supplied bounds
     */
    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Limits a value to inclusive bounds using ordered comparisons. Bounds are
     * not reordered or validated. A NaN value passes through unchanged.
     *
     * @param value value to constrain
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return value limited by the supplied bounds
     */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Computes a + (b - a) * t without clamping the interpolation factor.
     * Factors outside zero through one extrapolate beyond the endpoints.
     *
     * @param a value at factor zero
     * @param b value at factor one
     * @param t interpolation factor
     * @return linearly interpolated or extrapolated value
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Computes a + (b - a) * t without clamping the interpolation factor.
     * Factors outside zero through one extrapolate beyond the endpoints.
     *
     * @param a value at factor zero
     * @param b value at factor one
     * @param t interpolation factor
     * @return linearly interpolated or extrapolated value
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Computes the fractional position within the supplied range without clamping.
     * Equal bounds invoke floating-point division by zero and can yield NaN or infinity.
     *
     * @param value value to normalize
     * @param min range start
     * @param max range end
     * @return fraction (value - min) / (max - min)
     */
    public static float norm(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    /**
     * Computes the fractional position within the supplied range without clamping.
     * Equal bounds invoke floating-point division by zero and can yield NaN or infinity.
     *
     * @param value value to normalize
     * @param min range start
     * @param max range end
     * @return fraction (value - min) / (max - min)
     */
    public static double norm(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    /**
     * Normalizes in the source interval and interpolates into the destination interval.
     * Neither interval is reordered; values outside the source range extrapolate.
     *
     * @param value source value
     * @param inMin source interval start
     * @param inMax source interval end
     * @param outMin destination interval start
     * @param outMax destination interval end
     * @return mapped value, potentially NaN or infinite for a degenerate source interval
     */
    public static float map(float value, float inMin, float inMax, float outMin, float outMax) {
        return lerp(outMin, outMax, norm(value, inMin, inMax));
    }

    /**
     * Normalizes in the source interval and interpolates into the destination interval.
     * Neither interval is reordered; values outside the source range extrapolate.
     *
     * @param value source value
     * @param inMin source interval start
     * @param inMax source interval end
     * @param outMin destination interval start
     * @param outMax destination interval end
     * @return mapped value, potentially NaN or infinite for a degenerate source interval
     */
    public static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return lerp(outMin, outMax, norm(value, inMin, inMax));
    }

    /**
     * Rounds through Math.round and narrows the resulting long to int.
     * Half ties round toward positive infinity; narrowing out-of-range results can wrap.
     *
     * @param value value to round
     * @return rounded long narrowed to int
     */
    public static int round(double value) {
        return (int) Math.round(value);
    }

    /**
     * Applies Math.floor and converts its result to int using Java narrowing.
     * NaN converts to zero and values outside the int range saturate to an endpoint.
     *
     * @param value value to round
     * @return downward-rounded integer, subject to narrowing
     */
    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    /**
     * Applies Math.ceil and converts its result to int using Java narrowing.
     * NaN converts to zero and values outside the int range saturate to an endpoint.
     *
     * @param value value to round
     * @return upward-rounded integer, subject to narrowing
     */
    public static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    /**
     * Delegates to Math.round: half ties round toward positive infinity, NaN yields
     * zero, and values beyond the long range saturate.
     *
     * @param value value to round
     * @return nearest long under Math.round rules
     */
    public static long roundToLong(double value) {
        return Math.round(value);
    }

    /**
     * Divides in float precision, rounds the quotient, and multiplies back.
     * Use a positive nonzero spacing. Large inputs can lose precision or overflow;
     * spacing is not validated.
     *
     * @param value value to snap
     * @param multiple positive nonzero spacing
     * @return rounded multiple under the implementation's arithmetic
     */
    public static int nearestMultiple(int value, int multiple) {
        return multiple * Math.round((float) value / multiple);
    }

    /**
     * Divides in double precision, rounds the quotient, and multiplies back.
     * Use a positive nonzero spacing. Large inputs can lose precision or overflow;
     * spacing is not validated.
     *
     * @param value value to snap
     * @param multiple positive nonzero spacing
     * @return rounded multiple under the implementation's arithmetic
     */
    public static long nearestMultiple(long value, long multiple) {
        return multiple * Math.round((double) value / multiple);
    }

    /**
     * Converts degrees to radians and evaluates Math.sin.
     * NaN and infinite angles produce NaN; no angle normalization is performed.
     *
     * @param degrees angle in degrees
     * @return sin of the supplied angle
     */
    public static double sinDeg(double degrees) {
        return Math.sin(degrees * DEG_TO_RAD);
    }

    /**
     * Converts degrees to radians and evaluates Math.cos.
     * NaN and infinite angles produce NaN; no angle normalization is performed.
     *
     * @param degrees angle in degrees
     * @return cos of the supplied angle
     */
    public static double cosDeg(double degrees) {
        return Math.cos(degrees * DEG_TO_RAD);
    }

    /**
     * Converts degrees to radians and evaluates Math.tan.
     * NaN and infinite angles produce NaN; no angle normalization is performed.
     *
     * @param degrees angle in degrees
     * @return tan of the supplied angle
     */
    public static double tanDeg(double degrees) {
        return Math.tan(degrees * DEG_TO_RAD);
    }

    /**
     * Evaluates Math.asin and converts its principal result to degrees.
     * Inputs outside minus one through one produce NaN.
     *
     * @param sin trigonometric ratio
     * @return principal inverse angle in degrees
     */
    public static double asinDeg(double sin) {
        return Math.asin(sin) * RAD_TO_DEG;
    }

    /**
     * Evaluates Math.acos and converts its principal result to degrees.
     * Inputs outside minus one through one produce NaN.
     *
     * @param cos trigonometric ratio
     * @return principal inverse angle in degrees
     */
    public static double acosDeg(double cos) {
        return Math.acos(cos) * RAD_TO_DEG;
    }

    /**
     * Evaluates Math.atan and converts its principal result to degrees.
     * Infinite inputs produce the corresponding signed right angle.
     *
     * @param tan trigonometric ratio
     * @return principal inverse angle in degrees
     */
    public static double atanDeg(double tan) {
        return Math.atan(tan) * RAD_TO_DEG;
    }

    /**
     * Computes the quadrant-aware angle from the positive x axis and converts it to
     * degrees. Signed zero and infinities follow Math.atan2 behavior.
     *
     * @param y vertical component
     * @param x horizontal component
     * @return principal angle in degrees
     */
    public static double atan2Deg(double y, double x) {
        return Math.atan2(y, x) * RAD_TO_DEG;
    }

    /**
     * Approximates inverse square root using an IEEE-754 bit estimate and one Newton
     * refinement. Intended for positive finite input; zero, negatives, infinities, and
     * NaN do not follow Math.sqrt's special-value guarantees.
     *
     * @param x positive finite radicand
     * @return approximation to one divided by square root of x
     */
    public static float fastInvSqrt(float x) {
        float xhalf = 0.5f * x;
        int i = Float.floatToIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        return x * (1.5f - xhalf * x * x);
    }

    /**
     * Samples uniformly from zero through max using the shared generator.
     * The inclusive bound is implemented as max + 1, which must remain positive.
     *
     * @param max inclusive upper bound from zero through Integer.MAX_VALUE minus one
     * @return sampled integer
     * @throws IllegalArgumentException if max + 1 is not positive
     */
    public static int randomInt(int max) {
        return RANDOM.nextInt(max + 1);
    }

    /**
     * Samples uniformly from an inclusive integer interval using the shared generator.
     * The computed width max - min + 1 must be positive without integer overflow.
     *
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return sampled integer
     * @throws IllegalArgumentException if the computed interval width is not positive
     */
    public static int randomInt(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }

    /**
     * Scales a shared-generator sample from zero-inclusive, one-exclusive into the
     * supplied interval. Bounds are not validated; floating-point rounding can reach
     * the upper endpoint, and reversed bounds reverse the mapping.
     *
     * @param min interval start
     * @param max interval end
     * @return scaled pseudorandom sample
     */
    public static float randomFloat(float min, float max) {
        return min + RANDOM.nextFloat() * (max - min);
    }

    /**
     * Scales a shared-generator sample from zero-inclusive, one-exclusive into the
     * supplied interval. Bounds are not validated; floating-point rounding can reach
     * the upper endpoint, and reversed bounds reverse the mapping.
     *
     * @param min interval start
     * @param max interval end
     * @return scaled pseudorandom sample
     */
    public static double randomDouble(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    /**
     * Draws one pseudorandom Boolean from the shared Random instance.
     * Successive calls advance the same generator used by the other random helpers.
     *
     * @return true or false with equal probability
     */
    public static boolean randomBoolean() {
        return RANDOM.nextBoolean();
    }

    /**
     * Scales a standard normal sample and shifts it by the requested mean.
     * The deviation is passed through unchanged; zero collapses to the mean and negative
     * values reflect the sample. This does not constrain the output to an interval.
     *
     * @param mean distribution center
     * @param deviation standard-deviation multiplier
     * @return Gaussian-distributed float
     */
    public static float randomGaussian(float mean, float deviation) {
        return mean + (float) RANDOM.nextGaussian() * deviation;
    }

    /**
     * Wraps b - a by repeated full turns into the interval above minus pi through pi.
     * Supply finite angles with a reasonably bounded difference: infinite differences
     * or magnitudes too large to change by one turn can prevent termination.
     *
     * @param a starting angle in radians
     * @param b ending angle in radians
     * @return signed wrapped difference in radians
     */
    public static double angleDiff(double a, double b) {
        double diff = b - a;
        while (diff <= -Math.PI) diff += TAU;
        while (diff > Math.PI) diff -= TAU;
        return diff;
    }

    /**
     * Adds a fraction of angleDiff's wrapped radian difference to the starting angle.
     * The factor and resulting angle are not clamped or normalized; angleDiff's finite
     * input requirements apply.
     *
     * @param a starting radians
     * @param b ending radians
     * @param t interpolation factor
     * @return interpolated angle in radians
     */
    public static double lerpAngle(double a, double b, double t) {
        return a + angleDiff(a, b) * t;
    }

    /**
     * Interpolates using ((b - a) + 180) % 360 - 180 as the degree difference.
     * Java's signed remainder means large negative differences are not always mapped
     * to the shortest arc. Neither factor nor output is clamped.
     *
     * @param a starting degrees
     * @param b ending degrees
     * @param t interpolation factor
     * @return degree interpolation under the signed-remainder formula
     */
    public static double lerpAngleDeg(double a, double b, double t) {
        double diff = ((b - a) + 180) % 360 - 180;
        return a + diff * t;
    }

    /**
     * Tests for exactly one set bit in a positive integer. Zero and negative values
     * return false, even when their raw bit pattern contains a single set bit.
     *
     * @param n integer to test
     * @return whether n is a positive power of two
     */
    public static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    /**
     * Propagates the highest set bit and rounds upward to a power of two.
     * Nonpositive input returns one; values above 2^30 overflow to Integer.MIN_VALUE.
     *
     * @param n requested minimum capacity
     * @return rounded power of two, subject to signed overflow
     */
    public static int nextPowerOfTwo(int n) {
        if (n <= 0) return 1;
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }

    /**
     * Evaluates the logistic function 1 / (1 + exp(-x)). Extreme values approach
     * zero or one under floating-point arithmetic; NaN propagates.
     *
     * @param x logistic input
     * @return logistic value
     */
    public static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * Normalizes x between the edges, clamps the fraction, and applies a cubic
     * smoothing polynomial. Equal edges can produce NaN; reversed edges invert the
     * transition rather than being rejected.
     *
     * @param edge0 transition start
     * @param edge1 transition end
     * @param x input value
     * @return smoothed fraction
     */
    public static double smoothStep(double edge0, double edge1, double x) {
        x = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return x * x * (3 - 2 * x);
    }

    /**
     * Normalizes x between the edges, clamps the fraction, and applies a quintic
     * smoothing polynomial. Equal edges can produce NaN; reversed edges invert the
     * transition rather than being rejected.
     *
     * @param edge0 transition start
     * @param edge1 transition end
     * @param x input value
     * @return smoothed fraction
     */
    public static double smootherStep(double edge0, double edge1, double x) {
        x = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    /**
     * Divides with truncation toward zero, returning zero when the divisor is zero.
     * Other Java integer behavior remains, including minimum-value divided by minus one.
     *
     * @param a dividend
     * @param b divisor
     * @return integer quotient, or zero for a zero divisor
     */
    public static int safeDiv(int a, int b) {
        return b == 0 ? 0 : a / b;
    }

    /**
     * Divides with truncation toward zero, returning zero when the divisor is zero.
     * Other Java integer behavior remains, including minimum-value divided by minus one.
     *
     * @param a dividend
     * @param b divisor
     * @return integer quotient, or zero for a zero divisor
     */
    public static long safeDiv(long a, long b) {
        return b == 0 ? 0 : a / b;
    }

    /**
     * Finds the largest value among two required values and additional inputs.
     * The array is traversed without modification.
     *
     * @param a first required value
     * @param b second required value
     * @param values additional values, possibly empty
     * @return selected extreme value
     * @throws NullPointerException if values is null
     */
    public static int max(int a, int b, int... values) {
        int m = Math.max(a, b);
        for (int v : values) m = Math.max(m, v);
        return m;
    }

    /**
     * Finds the largest value among two required values and additional inputs.
     * The array is traversed without modification.
     *
     * @param a first required value
     * @param b second required value
     * @param values additional values, possibly empty
     * @return selected extreme value
     * @throws NullPointerException if values is null
     */
    public static long max(long a, long b, long... values) {
        long m = Math.max(a, b);
        for (long v : values) m = Math.max(m, v);
        return m;
    }

    /**
     * Finds the largest value among two required values and additional inputs.
     * The array is traversed without modification. Math's NaN and signed-zero rules apply.
     *
     * @param a first required value
     * @param b second required value
     * @param values additional values, possibly empty
     * @return selected extreme value
     * @throws NullPointerException if values is null
     */
    public static float max(float a, float b, float... values) {
        float m = Math.max(a, b);
        for (float v : values) m = Math.max(m, v);
        return m;
    }

    /**
     * Finds the largest value among two required values and additional inputs.
     * The array is traversed without modification. Math's NaN and signed-zero rules apply.
     *
     * @param a first required value
     * @param b second required value
     * @param values additional values, possibly empty
     * @return selected extreme value
     * @throws NullPointerException if values is null
     */
    public static double max(double a, double b, double... values) {
        double m = Math.max(a, b);
        for (double v : values) m = Math.max(m, v);
        return m;
    }

    /**
     * Finds the smallest value among two required values and additional inputs.
     * The array is traversed without modification.
     *
     * @param a first required value
     * @param b second required value
     * @param values additional values, possibly empty
     * @return selected extreme value
     * @throws NullPointerException if values is null
     */
    public static int min(int a, int b, int... values) {
        int m = Math.min(a, b);
        for (int v : values) m = Math.min(m, v);
        return m;
    }

    /**
     * Finds the smallest value among two required values and additional inputs.
     * The array is traversed without modification.
     *
     * @param a first required value
     * @param b second required value
     * @param values additional values, possibly empty
     * @return selected extreme value
     * @throws NullPointerException if values is null
     */
    public static long min(long a, long b, long... values) {
        long m = Math.min(a, b);
        for (long v : values) m = Math.min(m, v);
        return m;
    }

    /**
     * Finds the smallest value among two required values and additional inputs.
     * The array is traversed without modification. Math's NaN and signed-zero rules apply.
     *
     * @param a first required value
     * @param b second required value
     * @param values additional values, possibly empty
     * @return selected extreme value
     * @throws NullPointerException if values is null
     */
    public static float min(float a, float b, float... values) {
        float m = Math.min(a, b);
        for (float v : values) m = Math.min(m, v);
        return m;
    }

    /**
     * Finds the smallest value among two required values and additional inputs.
     * The array is traversed without modification. Math's NaN and signed-zero rules apply.
     *
     * @param a first required value
     * @param b second required value
     * @param values additional values, possibly empty
     * @return selected extreme value
     * @throws NullPointerException if values is null
     */
    public static double min(double a, double b, double... values) {
        double m = Math.min(a, b);
        for (double v : values) m = Math.min(m, v);
        return m;
    }

    /**
     * Computes an arithmetic mean without modifying the input. Empty input returns
     * zero. Accumulation uses long arithmetic before division as a double.
     *
     * @param values values to average, possibly empty
     * @return arithmetic mean, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double average(byte... values) {
        if (values.length == 0) return 0.0;
        long sum = 0;
        for (byte v : values) sum += v;
        return sum / (double) values.length;
    }

    /**
     * Computes an arithmetic mean without modifying the input. Empty input returns
     * zero. Accumulation uses long arithmetic before division as a double.
     *
     * @param values values to average, possibly empty
     * @return arithmetic mean, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double average(short... values) {
        if (values.length == 0) return 0.0;
        long sum = 0;
        for (short v : values) sum += v;
        return sum / (double) values.length;
    }

    /**
     * Computes an arithmetic mean without modifying the input. Empty input returns
     * zero. Accumulation uses long arithmetic before division as a double.
     *
     * @param values values to average, possibly empty
     * @return arithmetic mean, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double average(int... values) {
        if (values.length == 0) return 0.0;
        long sum = 0;
        for (int v : values) sum += v;
        return sum / (double) values.length;
    }

    /**
     * Computes an arithmetic mean without modifying the input. Empty input returns
     * zero. Accumulation uses long arithmetic, so the sum can overflow before conversion to double.
     *
     * @param values values to average, possibly empty
     * @return arithmetic mean, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double average(long... values) {
        if (values.length == 0) return 0.0;
        long sum = 0;
        for (long v : values) sum += v;
        return sum / (double) values.length;
    }

    /**
     * Computes an arithmetic mean without modifying the input. Empty input returns
     * zero. Accumulation uses double arithmetic and ordinary rounding; NaN propagates.
     *
     * @param values values to average, possibly empty
     * @return arithmetic mean, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double average(float... values) {
        if (values.length == 0) return 0.0;
        double sum = 0;
        for (float v : values) sum += v;
        return sum / values.length;
    }

    /**
     * Computes an arithmetic mean without modifying the input. Empty input returns
     * zero. Accumulation uses double arithmetic and ordinary rounding; NaN propagates.
     *
     * @param values values to average, possibly empty
     * @return arithmetic mean, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double average(double... values) {
        if (values.length == 0) return 0.0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    /**
     * Sorts a copy and selects the middle value, averaging the middle pair for even
     * lengths. Empty input returns zero. Pair addition occurs in the input's arithmetic
     * type before division and can overflow; input order is preserved.
     *
     * @param values values whose median is requested
     * @return median, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double median(int... values) {
        if (values.length == 0) return 0.0;
        int[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length >> 1;
        return (copy.length & 1) == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    /**
     * Sorts a copy and selects the middle value, averaging the middle pair for even
     * lengths. Empty input returns zero. Pair addition occurs in the input's arithmetic
     * type before division and can overflow; input order is preserved.
     *
     * @param values values whose median is requested
     * @return median, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double median(long... values) {
        if (values.length == 0) return 0.0;
        long[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length >> 1;
        return (copy.length & 1) == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    /**
     * Sorts a copy and selects the middle value, averaging the middle pair for even
     * lengths. Empty input returns zero. Pair addition occurs in the input's arithmetic
     * type before division and can overflow; input order is preserved.
     *
     * @param values values whose median is requested
     * @return median, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double median(float... values) {
        if (values.length == 0) return 0.0;
        float[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length >> 1;
        return (copy.length & 1) == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    /**
     * Sorts a copy and selects the middle value, averaging the middle pair for even
     * lengths. Empty input returns zero. Pair addition occurs in the input's arithmetic
     * type before division and can overflow; input order is preserved.
     *
     * @param values values whose median is requested
     * @return median, or zero for empty input
     * @throws NullPointerException if values is null
     */
    public static double median(double... values) {
        if (values.length == 0) return 0.0;
        double[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length >> 1;
        return (copy.length & 1) == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    /**
     * Tests both inclusive boundaries without reordering them. Reversed bounds
     * produce false.
     *
     * @param value value to test
     * @param min lower boundary
     * @param max upper boundary
     * @return whether value lies in the closed interval
     */
    public static boolean between(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * Tests both inclusive boundaries without reordering them. Reversed bounds
     * produce false.
     *
     * @param value value to test
     * @param min lower boundary
     * @param max upper boundary
     * @return whether value lies in the closed interval
     */
    public static boolean between(long value, long min, long max) {
        return value >= min && value <= max;
    }

    /**
     * Tests both inclusive boundaries without reordering them. Reversed bounds
     * produce false. Any NaN operand also produces false.
     *
     * @param value value to test
     * @param min lower boundary
     * @param max upper boundary
     * @return whether value lies in the closed interval
     */
    public static boolean between(float value, float min, float max) {
        return value >= min && value <= max;
    }

    /**
     * Tests both inclusive boundaries without reordering them. Reversed bounds
     * produce false. Any NaN operand also produces false.
     *
     * @param value value to test
     * @param min lower boundary
     * @param max upper boundary
     * @return whether value lies in the closed interval
     */
    public static boolean between(double value, double min, double max) {
        return value >= min && value <= max;
    }

    /**
     * Compares the value with positive zero and returns an integer sign.
     * The comparison avoids subtraction and its overflow risk.
     *
     * @param x value to classify
     * @return minus one, zero, or one according to the comparison
     */
    public static int sign(int x) {
        return Integer.compare(x, 0);
    }

    /**
     * Compares the value with positive zero and returns an integer sign.
     * The comparison avoids subtraction and its overflow risk.
     *
     * @param x value to classify
     * @return minus one, zero, or one according to the comparison
     */
    public static int sign(long x) {
        return Long.compare(x, 0);
    }

    /**
     * Compares the value with positive zero and returns an integer sign.
     * Negative zero compares below positive zero; NaN compares above it.
     *
     * @param x value to classify
     * @return minus one, zero, or one according to the comparison
     */
    public static int sign(float x) {
        return Float.compare(x, 0.0f);
    }

    /**
     * Compares the value with positive zero and returns an integer sign.
     * Negative zero compares below positive zero; NaN compares above it.
     *
     * @param x value to classify
     * @return minus one, zero, or one according to the comparison
     */
    public static int sign(double x) {
        return Double.compare(x, 0.0);
    }

    /**
     * Delegates to Math.abs. The minimum representable integer remains negative
     * because its positive magnitude does not fit in this type.
     *
     * @param x value whose magnitude is requested
     * @return absolute value under Java arithmetic rules
     */
    public static int abs(int x) {
        return Math.abs(x);
    }

    /**
     * Delegates to Math.abs. The minimum representable integer remains negative
     * because its positive magnitude does not fit in this type.
     *
     * @param x value whose magnitude is requested
     * @return absolute value under Java arithmetic rules
     */
    public static long abs(long x) {
        return Math.abs(x);
    }

    /**
     * Delegates to Math.abs. Negative zero becomes positive zero and NaN
     * remains NaN.
     *
     * @param x value whose magnitude is requested
     * @return absolute value under Java arithmetic rules
     */
    public static float abs(float x) {
        return Math.abs(x);
    }

    /**
     * Delegates to Math.abs. Negative zero becomes positive zero and NaN
     * remains NaN.
     *
     * @param x value whose magnitude is requested
     * @return absolute value under Java arithmetic rules
     */
    public static double abs(double x) {
        return Math.abs(x);
    }

    /**
     * Computes floor using an integer cast and a correction for negative fractions.
     * Use finite values within the int range; values outside it can saturate or overflow
     * and this helper does not validate that precondition.
     *
     * @param x finite value representable within the int range
     * @return greatest integer no larger than x for supported input
     */
    public static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /**
     * Computes floor using an integer cast and a correction for negative fractions.
     * Use finite values within the int range; values outside it can saturate or overflow
     * and this helper does not validate that precondition.
     *
     * @param x finite value representable within the int range
     * @return greatest integer no larger than x for supported input
     */
    public static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /**
     * Computes Euclidean distance using direct coordinate differences.
     * Intermediate squares can overflow for large coordinates; no scaled hypot
     * algorithm or finiteness validation is used.
     *
     * @param x1 first point's horizontal coordinate
     * @param y1 first point's vertical coordinate
     * @param x2 second point's horizontal coordinate
     * @param y2 second point's vertical coordinate
     * @return distance in coordinate units
     */
    public static double distance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Computes Euclidean distance using direct coordinate differences.
     * Intermediate squares can overflow for large coordinates; no scaled hypot
     * algorithm or finiteness validation is used.
     *
     * @param x1 first point's horizontal coordinate
     * @param y1 first point's vertical coordinate
     * @param x2 second point's horizontal coordinate
     * @param y2 second point's vertical coordinate
     * @return distance in coordinate units
     */
    public static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Computes squared Euclidean distance using direct coordinate differences.
     * Intermediate squares can overflow for large coordinates; no scaled hypot
     * algorithm or finiteness validation is used.
     *
     * @param x1 first point's horizontal coordinate
     * @param y1 first point's vertical coordinate
     * @param x2 second point's horizontal coordinate
     * @param y2 second point's vertical coordinate
     * @return distance squared in squared coordinate units
     */
    public static double distanceSq(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    /**
     * Computes squared Euclidean distance using direct coordinate differences.
     * Intermediate squares can overflow for large coordinates; no scaled hypot
     * algorithm or finiteness validation is used.
     *
     * @param x1 first point's horizontal coordinate
     * @param y1 first point's vertical coordinate
     * @param x2 second point's horizontal coordinate
     * @param y2 second point's vertical coordinate
     * @return distance squared in squared coordinate units
     */
    public static float distanceSq(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    /**
     * Takes the reciprocal of fastInvSqrt's approximation. Intended for positive finite
     * input; it is neither correctly rounded nor guaranteed to return exact zero at zero.
     *
     * @param x positive finite radicand
     * @return approximate square root
     */
    public static float fastSqrt(float x) {
        return 1.0f / fastInvSqrt(x);
    }

    /**
     * Applies fastSqrt to direct squared coordinate distance. This is approximate;
     * coincident points need not yield exact zero and large differences can overflow.
     *
     * @param x1 first point x
     * @param y1 first point y
     * @param x2 second point x
     * @param y2 second point y
     * @return approximate distance in coordinate units
     */
    public static float fastDistance(float x1, float y1, float x2, float y2) {
        return fastSqrt(distanceSq(x1, y1, x2, y2));
    }

    /**
     * Evaluates quadratic acceleration for animation progress.
     * Input is intended for zero through one but is not clamped.
     *
     * @param t normalized animation progress
     * @return eased progress, potentially outside zero through one
     */
    public static double easeInQuad(double t) {
        return t * t;
    }

    /**
     * Evaluates quadratic deceleration for animation progress.
     * Input is intended for zero through one but is not clamped.
     *
     * @param t normalized animation progress
     * @return eased progress, potentially outside zero through one
     */
    public static double easeOutQuad(double t) {
        return t * (2 - t);
    }

    /**
     * Evaluates piecewise quadratic acceleration and deceleration for animation progress.
     * Input is intended for zero through one but is not clamped.
     *
     * @param t normalized animation progress
     * @return eased progress, potentially outside zero through one
     */
    public static double easeInOutQuad(double t) {
        return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
    }

    /**
     * Evaluates cubic acceleration for animation progress.
     * Input is intended for zero through one but is not clamped.
     *
     * @param t normalized animation progress
     * @return eased progress, potentially outside zero through one
     */
    public static double easeInCubic(double t) {
        return t * t * t;
    }

    /**
     * Evaluates cubic deceleration for animation progress.
     * Input is intended for zero through one but is not clamped.
     *
     * @param t normalized animation progress
     * @return eased progress, potentially outside zero through one
     */
    public static double easeOutCubic(double t) {
        return (--t) * t * t + 1;
    }

    /**
     * Evaluates piecewise cubic acceleration and deceleration for animation progress.
     * Input is intended for zero through one but is not clamped.
     *
     * @param t normalized animation progress
     * @return eased progress, potentially outside zero through one
     */
    public static double easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
    }

    /**
     * Evaluates exponentially decaying oscillation for animation progress.
     * Input is intended for zero through one but is not clamped. Endpoint values
     * are not explicitly snapped, and oscillation may overshoot one.
     *
     * @param t normalized animation progress
     * @return eased progress, potentially outside zero through one
     */
    public static double easeOutElastic(double t) {
        double p = 0.3;
        return Math.pow(2, -10 * t) * Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1;
    }

    /**
     * Evaluates piecewise parabolic bounce for animation progress.
     * Input is intended for zero through one but is not clamped.
     *
     * @param t normalized animation progress
     * @return eased progress, potentially outside zero through one
     */
    public static double easeOutBounce(double t) {
        if (t < 1 / 2.75) return 7.5625 * t * t;
        if (t < 2 / 2.75) {
            t -= 1.5 / 2.75;
            return 7.5625 * t * t + 0.75;
        }
        if (t < 2.5 / 2.75) {
            t -= 2.25 / 2.75;
            return 7.5625 * t * t + 0.9375;
        }
        t -= 2.625 / 2.75;
        return 7.5625 * t * t + 0.984375;
    }

    /**
     * Uses Euclid's remainder algorithm and normalizes the resulting sign.
     * Both-zero input returns zero. A minimum-value magnitude that cannot be represented
     * remains negative after the final negation.
     *
     * @param a first integer
     * @param b second integer
     * @return greatest common divisor, subject to signed-type overflow
     */
    public static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a < 0 ? -a : a;
    }

    /**
     * Uses Euclid's remainder algorithm and normalizes the resulting sign.
     * Both-zero input returns zero. A minimum-value magnitude that cannot be represented
     * remains negative after the final negation.
     *
     * @param a first integer
     * @param b second integer
     * @return greatest common divisor, subject to signed-type overflow
     */
    public static long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a < 0 ? -a : a;
    }

    /**
     * Computes a / gcd(a, b) * b using int arithmetic. The result preserves the
     * product's sign rather than forcing a positive magnitude, and multiplication may
     * overflow. Both-zero input causes division by zero.
     *
     * @param a first integer
     * @param b second integer
     * @return computed common multiple
     * @throws ArithmeticException if both arguments are zero
     */
    public static int lcm(int a, int b) {
        return a / gcd(a, b) * b;
    }

    /**
     * Tests the least significant bit, which also classifies negative integers.
     * No absolute-value conversion or division is required.
     *
     * @param n integer to classify
     * @return whether n is even
     */
    public static boolean isEven(int n) {
        return (n & 1) == 0;
    }

    /**
     * Tests the least significant bit, which also classifies negative integers.
     * No absolute-value conversion or division is required.
     *
     * @param n integer to classify
     * @return whether n is odd
     */
    public static boolean isOdd(int n) {
        return (n & 1) != 0;
    }

    /**
     * Reverses all 32 bits by progressively exchanging adjacent bit groups.
     * The sign bit participates as ordinary data; this is bit reversal, not byte swapping.
     *
     * @param n input bit pattern
     * @return bit-reversed pattern
     */
    public static int reverseBits(int n) {
        n = ((n >>> 1) & 0x55555555) | ((n & 0x55555555) << 1);
        n = ((n >>> 2) & 0x33333333) | ((n & 0x33333333) << 2);
        n = ((n >>> 4) & 0x0F0F0F0F) | ((n & 0x0F0F0F0F) << 4);
        n = ((n >>> 8) & 0x00FF00FF) | ((n & 0x00FF00FF) << 8);
        return (n >>> 16) | (n << 16);
    }

    /**
     * Tests absolute difference against an inclusive 1e-9 tolerance.
     * This is not a relative-error comparison; NaN and equal infinities return false.
     *
     * @param a first value
     * @param b second value
     * @return whether absolute difference is within the fixed tolerance
     */
    public static boolean equalsApprox(double a, double b) {
        return Math.abs(a - b) <= 1e-9;
    }

    /**
     * Tests absolute difference against an inclusive 1e-6 tolerance.
     * This is not a relative-error comparison; NaN and equal infinities return false.
     *
     * @param a first value
     * @param b second value
     * @return whether absolute difference is within the fixed tolerance
     */
    public static boolean equalsApprox(float a, float b) {
        return Math.abs(a - b) <= 1e-6f;
    }

    /**
     * Tests magnitude against a strict 1e-12 threshold. Signed zeros pass;
     * NaN and infinities fail. This does not scale tolerance with input magnitude.
     *
     * @param x value to test
     * @return whether the value is sufficiently close to zero
     */
    public static boolean isZero(double x) {
        return Math.abs(x) < 1e-12;
    }

    /**
     * Tests magnitude against a strict 1e-6 threshold. Signed zeros pass;
     * NaN and infinities fail. This does not scale tolerance with input magnitude.
     *
     * @param x value to test
     * @return whether the value is sufficiently close to zero
     */
    public static boolean isZero(float x) {
        return Math.abs(x) < 1e-6f;
    }

    /**
     * Computes logarithm to base two. Zero yields negative infinity, negative
     * input yields NaN, and positive infinity remains infinite.
     *
     * @param x logarithm argument
     * @return logarithmic value
     */
    public static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    /**
     * Computes logarithm to base ten. Zero yields negative infinity, negative
     * input yields NaN, and positive infinity remains infinite.
     *
     * @param x logarithm argument
     * @return logarithmic value
     */
    public static double log10(double x) {
        return Math.log10(x);
    }

    /**
     * Wraps into an inclusive integer interval using a double remainder adjustment.
     * Use ordered bounds with representable width and intermediate arithmetic; this
     * implementation does not detect overflow.
     *
     * @param value value to wrap
     * @param min inclusive lower bound
     * @param max inclusive upper bound
     * @return wrapped value for valid arithmetic
     * @throws ArithmeticException if the computed interval width is zero
     */
    public static int wrap(int value, int min, int max) {
        int range = max - min + 1;
        return ((value - min) % range + range) % range + min;
    }

    /**
     * Wraps a finite value into a lower-inclusive, upper-exclusive interval using floor.
     * Use finite ordered bounds with positive width; degenerate or nonfinite inputs
     * can produce NaN and rounding may affect values near boundaries.
     *
     * @param value value to wrap
     * @param min inclusive lower bound
     * @param max exclusive upper bound
     * @return wrapped value
     */
    public static double wrap(double value, double min, double max) {
        double range = max - min;
        return value - (range * Math.floor((value - min) / range));
    }

    /**
     * Moves toward the target by at most a nonnegative delta without overshooting.
     * Delta is not validated; a negative delta can move away from the target.
     *
     * @param current current value
     * @param target desired value
     * @param delta nonnegative maximum change per call
     * @return updated value limited to the target in the direction of travel
     */
    public static float approach(float current, float target, float delta) {
        return current < target ? Math.min(current + delta, target)
                : Math.max(current - delta, target);
    }

    /**
     * Moves toward the target by at most a nonnegative delta without overshooting.
     * Delta is not validated; a negative delta can move away from the target.
     *
     * @param current current value
     * @param target desired value
     * @param delta nonnegative maximum change per call
     * @return updated value limited to the target in the direction of travel
     */
    public static double approach(double current, double target, double delta) {
        return current < target ? Math.min(current + delta, target)
                : Math.max(current - delta, target);
    }

    /**
     * Returns zero inside a strict magnitude threshold and subtracts the threshold
     * from surviving magnitudes. The remaining range is not rescaled to full strength;
     * use a nonnegative deadzone, since negative values are not rejected.
     *
     * @param value signed input
     * @param deadzone nonnegative magnitude threshold
     * @return thresholded input with reduced surviving magnitude
     */
    public static float applyDeadzone(float value, float deadzone) {
        return Math.abs(value) < deadzone ? 0.0f
                : value > 0 ? value - deadzone : value + deadzone;
    }
}
