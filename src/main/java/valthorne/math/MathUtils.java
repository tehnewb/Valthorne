package valthorne.math;

import java.util.Arrays;
import java.util.Random;

/**
 * A collection of static math utilities extending {@link Math}.
 * Provides clamping, interpolation, randomness, angle helpers,
 * easing functions, numeric utilities, and bit tricks.
 *
 * <p>This class cannot be instantiated.</p>
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

    private static final Random RANDOM = new Random();

    private MathUtils() {
        throw new AssertionError("No MathUtils instances allowed.");
    }

    /**
     * Clamps an int into a range.
     */
    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Clamps a long into a range.
     */
    public static long clamp(long value, long min, long max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Clamps a float into a range.
     */
    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Clamps a double into a range.
     */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Linear interpolation.
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Linear interpolation.
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Normalizes a value inside a range to [0,1].
     */
    public static float norm(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    /**
     * Normalizes a value inside a range to [0,1].
     */
    public static double norm(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    /**
     * Maps a float from one range to another.
     */
    public static float map(float value, float inMin, float inMax, float outMin, float outMax) {
        return lerp(outMin, outMax, norm(value, inMin, inMax));
    }

    /**
     * Maps a double from one range to another.
     */
    public static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return lerp(outMin, outMax, norm(value, inMin, inMax));
    }

    /**
     * Rounds a double to nearest int.
     */
    public static int round(double value) {
        return (int) Math.round(value);
    }

    /**
     * Floors a double to int.
     */
    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    /**
     * Ceils a double to int.
     */
    public static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    /**
     * Rounds a double to long.
     */
    public static long roundToLong(double value) {
        return Math.round(value);
    }

    /**
     * Snaps an int to the nearest multiple.
     */
    public static int nearestMultiple(int value, int multiple) {
        return multiple * Math.round((float) value / multiple);
    }

    /**
     * Snaps a long to the nearest multiple.
     */
    public static long nearestMultiple(long value, long multiple) {
        return multiple * Math.round((double) value / multiple);
    }

    /**
     * Sine in degrees.
     */
    public static double sinDeg(double degrees) {
        return Math.sin(degrees * DEG_TO_RAD);
    }

    /**
     * Cosine in degrees.
     */
    public static double cosDeg(double degrees) {
        return Math.cos(degrees * DEG_TO_RAD);
    }

    /**
     * Tangent in degrees.
     */
    public static double tanDeg(double degrees) {
        return Math.tan(degrees * DEG_TO_RAD);
    }

    /**
     * Arc-sine in degrees.
     */
    public static double asinDeg(double sin) {
        return Math.asin(sin) * RAD_TO_DEG;
    }

    /**
     * Arc-cosine in degrees.
     */
    public static double acosDeg(double cos) {
        return Math.acos(cos) * RAD_TO_DEG;
    }

    /**
     * Arc-tangent in degrees.
     */
    public static double atanDeg(double tan) {
        return Math.atan(tan) * RAD_TO_DEG;
    }

    /**
     * Arc-tangent2 in degrees.
     */
    public static double atan2Deg(double y, double x) {
        return Math.atan2(y, x) * RAD_TO_DEG;
    }

    /**
     * Fast inverse square root (approximate).
     */
    public static float fastInvSqrt(float x) {
        float xhalf = 0.5f * x;
        int i = Float.floatToIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        return x * (1.5f - xhalf * x * x);
    }

    /**
     * Returns a random int in [min,max].
     */
    public static int randomInt(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }

    /**
     * Returns a random float in [min,max].
     */
    public static float randomFloat(float min, float max) {
        return min + RANDOM.nextFloat() * (max - min);
    }

    /**
     * Returns a random double in [min,max].
     */
    public static double randomDouble(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    /**
     * Returns a random boolean.
     */
    public static boolean randomBoolean() {
        return RANDOM.nextBoolean();
    }

    /**
     * Gaussian-distributed random float.
     */
    public static float randomGaussian(float mean, float deviation) {
        return mean + (float) RANDOM.nextGaussian() * deviation;
    }

    /**
     * Minimal angle difference in radians.
     */
    public static double angleDiff(double a, double b) {
        double diff = b - a;
        while (diff <= -Math.PI) diff += TAU;
        while (diff > Math.PI) diff -= TAU;
        return diff;
    }

    /**
     * Interpolates between angles in radians.
     */
    public static double lerpAngle(double a, double b, double t) {
        return a + angleDiff(a, b) * t;
    }

    /**
     * Interpolates between angles in degrees.
     */
    public static double lerpAngleDeg(double a, double b, double t) {
        double diff = ((b - a) + 180) % 360 - 180;
        return a + diff * t;
    }

    /**
     * Returns true if n is a power of two.
     */
    public static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    /**
     * Returns next power of two ≥ n.
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
     * Logistic sigmoid function.
     */
    public static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * Smoothstep interpolation.
     */
    public static double smoothStep(double edge0, double edge1, double x) {
        x = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return x * x * (3 - 2 * x);
    }

    /**
     * Smootherstep interpolation.
     */
    public static double smootherStep(double edge0, double edge1, double x) {
        x = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    /**
     * Safe integer division (returns 0 when dividing by zero).
     */
    public static int safeDiv(int a, int b) {
        return b == 0 ? 0 : a / b;
    }

    /**
     * Safe long division (returns 0 when dividing by zero).
     */
    public static long safeDiv(long a, long b) {
        return b == 0 ? 0 : a / b;
    }

    /**
     * Max of multiple ints.
     */
    public static int max(int a, int b, int... values) {
        int m = Math.max(a, b);
        for (int v : values) m = Math.max(m, v);
        return m;
    }

    /**
     * Max of multiple longs.
     */
    public static long max(long a, long b, long... values) {
        long m = Math.max(a, b);
        for (long v : values) m = Math.max(m, v);
        return m;
    }

    /**
     * Max of multiple floats.
     */
    public static float max(float a, float b, float... values) {
        float m = Math.max(a, b);
        for (float v : values) m = Math.max(m, v);
        return m;
    }

    /**
     * Max of multiple doubles.
     */
    public static double max(double a, double b, double... values) {
        double m = Math.max(a, b);
        for (double v : values) m = Math.max(m, v);
        return m;
    }

    /**
     * Min of multiple ints.
     */
    public static int min(int a, int b, int... values) {
        int m = Math.min(a, b);
        for (int v : values) m = Math.min(m, v);
        return m;
    }

    /**
     * Min of multiple longs.
     */
    public static long min(long a, long b, long... values) {
        long m = Math.min(a, b);
        for (long v : values) m = Math.min(m, v);
        return m;
    }

    /**
     * Min of multiple floats.
     */
    public static float min(float a, float b, float... values) {
        float m = Math.min(a, b);
        for (float v : values) m = Math.min(m, v);
        return m;
    }

    /**
     * Min of multiple doubles.
     */
    public static double min(double a, double b, double... values) {
        double m = Math.min(a, b);
        for (double v : values) m = Math.min(m, v);
        return m;
    }

    /**
     * Average of bytes.
     */
    public static double average(byte... values) {
        if (values.length == 0) return 0.0;
        long sum = 0;
        for (byte v : values) sum += v;
        return sum / (double) values.length;
    }

    /**
     * Average of shorts.
     */
    public static double average(short... values) {
        if (values.length == 0) return 0.0;
        long sum = 0;
        for (short v : values) sum += v;
        return sum / (double) values.length;
    }

    /**
     * Average of ints.
     */
    public static double average(int... values) {
        if (values.length == 0) return 0.0;
        long sum = 0;
        for (int v : values) sum += v;
        return sum / (double) values.length;
    }

    /**
     * Average of longs.
     */
    public static double average(long... values) {
        if (values.length == 0) return 0.0;
        long sum = 0;
        for (long v : values) sum += v;
        return sum / (double) values.length;
    }

    /**
     * Average of floats.
     */
    public static double average(float... values) {
        if (values.length == 0) return 0.0;
        double sum = 0;
        for (float v : values) sum += v;
        return sum / values.length;
    }

    /**
     * Average of doubles.
     */
    public static double average(double... values) {
        if (values.length == 0) return 0.0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    /**
     * Median of ints.
     */
    public static double median(int... values) {
        if (values.length == 0) return 0.0;
        int[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length >> 1;
        return (copy.length & 1) == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    /**
     * Median of longs.
     */
    public static double median(long... values) {
        if (values.length == 0) return 0.0;
        long[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length >> 1;
        return (copy.length & 1) == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    /**
     * Median of floats.
     */
    public static double median(float... values) {
        if (values.length == 0) return 0.0;
        float[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length >> 1;
        return (copy.length & 1) == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    /**
     * Median of doubles.
     */
    public static double median(double... values) {
        if (values.length == 0) return 0.0;
        double[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length >> 1;
        return (copy.length & 1) == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    /**
     * Returns true if value is within inclusive range.
     */
    public static boolean between(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * Returns true if value is within inclusive range.
     */
    public static boolean between(long value, long min, long max) {
        return value >= min && value <= max;
    }

    /**
     * Returns true if value is within inclusive range.
     */
    public static boolean between(float value, float min, float max) {
        return value >= min && value <= max;
    }

    /**
     * Returns true if value is within inclusive range.
     */
    public static boolean between(double value, double min, double max) {
        return value >= min && value <= max;
    }

    /**
     * Returns the sign of an integer.
     */
    public static int sign(int x) {
        return Integer.compare(x, 0);
    }

    /**
     * Returns the sign of a long.
     */
    public static int sign(long x) {
        return Long.compare(x, 0);
    }

    /**
     * Returns the sign of a float.
     */
    public static int sign(float x) {
        return Float.compare(x, 0.0f);
    }

    /**
     * Returns the sign of a double.
     */
    public static int sign(double x) {
        return Double.compare(x, 0.0);
    }

    /**
     * Fast absolute value for ints.
     */
    public static int abs(int x) {
        return Math.abs(x);
    }

    /**
     * Fast absolute value for longs.
     */
    public static long abs(long x) {
        return Math.abs(x);
    }

    /**
     * Fast absolute value for floats.
     */
    public static float abs(float x) {
        return Math.abs(x);
    }

    /**
     * Fast absolute value for doubles.
     */
    public static double abs(double x) {
        return Math.abs(x);
    }

    /**
     * Fast floor for positive floats.
     */
    public static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /**
     * Fast floor for positive doubles.
     */
    public static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /**
     * Euclidean distance (double).
     */
    public static double distance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Euclidean distance (float).
     */
    public static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Squared distance (double).
     */
    public static double distanceSq(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    /**
     * Squared distance (float).
     */
    public static float distanceSq(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    /**
     * Fast square root approximation.
     */
    public static float fastSqrt(float x) {
        return 1.0f / fastInvSqrt(x);
    }

    /**
     * Fast distance approximation.
     */
    public static float fastDistance(float x1, float y1, float x2, float y2) {
        return fastSqrt(distanceSq(x1, y1, x2, y2));
    }

    /**
     * Ease-in quadratic curve.
     */
    public static double easeInQuad(double t) {
        return t * t;
    }

    /**
     * Ease-out quadratic curve.
     */
    public static double easeOutQuad(double t) {
        return t * (2 - t);
    }

    /**
     * Ease-in-out quadratic curve.
     */
    public static double easeInOutQuad(double t) {
        return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
    }

    /**
     * Ease-in cubic curve.
     */
    public static double easeInCubic(double t) {
        return t * t * t;
    }

    /**
     * Ease-out cubic curve.
     */
    public static double easeOutCubic(double t) {
        return (--t) * t * t + 1;
    }

    /**
     * Ease-in-out cubic curve.
     */
    public static double easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
    }

    /**
     * Elastic ease-out curve.
     */
    public static double easeOutElastic(double t) {
        double p = 0.3;
        return Math.pow(2, -10 * t) * Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1;
    }

    /**
     * Bounce ease-out curve.
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
     * Computes GCD of two ints.
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
     * Computes GCD of two longs.
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
     * Computes LCM of two ints.
     */
    public static int lcm(int a, int b) {
        return a / gcd(a, b) * b;
    }

    /**
     * True if n is even.
     */
    public static boolean isEven(int n) {
        return (n & 1) == 0;
    }

    /**
     * True if n is odd.
     */
    public static boolean isOdd(int n) {
        return (n & 1) != 0;
    }

    /**
     * Reverses bit order of an int.
     */
    public static int reverseBits(int n) {
        n = ((n >>> 1) & 0x55555555) | ((n & 0x55555555) << 1);
        n = ((n >>> 2) & 0x33333333) | ((n & 0x33333333) << 2);
        n = ((n >>> 4) & 0x0F0F0F0F) | ((n & 0x0F0F0F0F) << 4);
        n = ((n >>> 8) & 0x00FF00FF) | ((n & 0x00FF00FF) << 8);
        return (n >>> 16) | (n << 16);
    }

    /**
     * Compares doubles with small epsilon.
     */
    public static boolean equalsApprox(double a, double b) {
        return Math.abs(a - b) <= 1e-9;
    }

    /**
     * Compares floats with small epsilon.
     */
    public static boolean equalsApprox(float a, float b) {
        return Math.abs(a - b) <= 1e-6f;
    }

    /**
     * True if double is close to zero.
     */
    public static boolean isZero(double x) {
        return Math.abs(x) < 1e-12;
    }

    /**
     * True if float is close to zero.
     */
    public static boolean isZero(float x) {
        return Math.abs(x) < 1e-6f;
    }

    /**
     * Log base 2.
     */
    public static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    /**
     * Log base 10.
     */
    public static double log10(double x) {
        return Math.log10(x);
    }

    /**
     * Wraps an int inside a range.
     */
    public static int wrap(int value, int min, int max) {
        int range = max - min + 1;
        return ((value - min) % range + range) % range + min;
    }

    /**
     * Wraps a double inside a range.
     */
    public static double wrap(double value, double min, double max) {
        double range = max - min;
        return value - (range * Math.floor((value - min) / range));
    }

    /**
     * Moves a value toward a target by delta.
     */
    public static float approach(float current, float target, float delta) {
        return current < target ? Math.min(current + delta, target)
                : Math.max(current - delta, target);
    }

    /**
     * Moves a value toward a target by delta.
     */
    public static double approach(double current, double target, double delta) {
        return current < target ? Math.min(current + delta, target)
                : Math.max(current - delta, target);
    }

    /**
     * Applies a deadzone to joystick-style input.
     */
    public static float applyDeadzone(float value, float deadzone) {
        return Math.abs(value) < deadzone ? 0.0f
                : value > 0 ? value - deadzone : value + deadzone;
    }
}
