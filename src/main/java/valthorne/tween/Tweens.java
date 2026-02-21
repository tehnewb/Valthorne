package valthorne.tween;

/**
 * Static easing factory.
 *
 * <p>
 * All parameterless tweens are exposed as {@code public static final} singletons to avoid allocations.
 * Parameterized easings (Back/Elastic) are produced via factory methods that return a new {@link Tween}
 * capturing the provided parameters.
 * </p>
 *
 * <p>
 * Input {@code t} is expected to be normalized in {@code [0,1]}.
 * Output is typically in {@code [0,1]}, but some easings (Back/Elastic/Bounce) can overshoot.
 * </p>
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
public final class Tweens {

    private Tweens() {
        // Inaccessible
    }

    /**
     * Linear easing.
     *
     * <p>
     * Returns {@code t} unchanged. This produces constant-speed interpolation with no acceleration
     * or deceleration.
     * </p>
     *
     * <p>
     * Mathematical form: {@code f(t) = t}
     * </p>
     */
    public static final Tween LINEAR = t -> t;

    /**
     * Quadratic ease-in.
     *
     * <p>
     * Starts slow and accelerates toward the end.
     * </p>
     *
     * <p>
     * Mathematical form: {@code f(t) = t^2}
     * </p>
     */
    public static final Tween QUAD_IN = t -> t * t;

    /**
     * Quadratic ease-out.
     *
     * <p>
     * Starts fast and decelerates toward the end.
     * </p>
     *
     * <p>
     * Mathematical form: {@code f(t) = 1 - (1 - t)^2}
     * </p>
     */
    public static final Tween QUAD_OUT = t -> {
        float u = 1f - t;
        return 1f - (u * u);
    };

    /**
     * Quadratic ease-in-out.
     *
     * <p>
     * Accelerates during the first half and decelerates during the second half.
     * This is often a good default when you want "smooth" motion without dramatic curvature.
     * </p>
     *
     * <p>
     * Behavior:
     * </p>
     * <ul>
     *     <li>For {@code t < 0.5}: behaves like a scaled {@link #QUAD_IN}.</li>
     *     <li>For {@code t >= 0.5}: behaves like a scaled {@link #QUAD_OUT}.</li>
     * </ul>
     */
    public static final Tween QUAD_IN_OUT = t -> {
        if (t < 0.5f) return 2f * t * t;
        float u = (-2f * t) + 2f;
        return 1f - (u * u) / 2f;
    };

    /**
     * Cubic ease-in.
     *
     * <p>
     * Stronger acceleration than {@link #QUAD_IN}.
     * </p>
     *
     * <p>
     * Mathematical form: {@code f(t) = t^3}
     * </p>
     */
    public static final Tween CUBIC_IN = t -> t * t * t;

    /**
     * Cubic ease-out.
     *
     * <p>
     * Stronger deceleration than {@link #QUAD_OUT}.
     * </p>
     *
     * <p>
     * Mathematical form: {@code f(t) = 1 - (1 - t)^3}
     * </p>
     */
    public static final Tween CUBIC_OUT = t -> {
        float u = 1f - t;
        return 1f - (u * u * u);
    };

    /**
     * Cubic ease-in-out.
     *
     * <p>
     * Strong symmetric acceleration/deceleration with a more pronounced curve than
     * {@link #QUAD_IN_OUT}.
     * </p>
     */
    public static final Tween CUBIC_IN_OUT = t -> {
        if (t < 0.5f) return 4f * t * t * t;
        float u = (-2f * t) + 2f;
        return 1f - (u * u * u) / 2f;
    };

    /**
     * Quartic ease-in.
     *
     * <p>
     * Very strong acceleration curve. Useful when you want motion that "hangs" near the start
     * and then ramps quickly.
     * </p>
     */
    public static final Tween QUART_IN = t -> t * t * t * t;

    /**
     * Quartic ease-out.
     *
     * <p>
     * Very strong deceleration curve. Useful when you want motion that arrives quickly and then
     * gently settles into the final value.
     * </p>
     */
    public static final Tween QUART_OUT = t -> {
        float u = 1f - t;
        return 1f - (u * u * u * u);
    };

    /**
     * Quartic ease-in-out.
     *
     * <p>
     * Strong symmetric acceleration and deceleration. This feels "snappier" than cubic.
     * </p>
     */
    public static final Tween QUART_IN_OUT = t -> {
        if (t < 0.5f) return 8f * t * t * t * t;
        float u = (-2f * t) + 2f;
        return 1f - (u * u * u * u) / 2f;
    };

    /**
     * Quintic ease-in.
     *
     * <p>
     * Extremely strong acceleration. This is quite aggressive and is best used sparingly
     * (short UI pops, stylized animations).
     * </p>
     */
    public static final Tween QUINT_IN = t -> t * t * t * t * t;

    /**
     * Quintic ease-out.
     *
     * <p>
     * Extremely strong deceleration. Best used for stylized "slam then settle" motion.
     * </p>
     */
    public static final Tween QUINT_OUT = t -> {
        float u = 1f - t;
        return 1f - (u * u * u * u * u);
    };

    /**
     * Quintic ease-in-out.
     *
     * <p>
     * Very sharp symmetric acceleration/deceleration. Compared to quartic/cubic, this is the
     * most dramatic of the polynomial in/out curves in this class.
     * </p>
     */
    public static final Tween QUINT_IN_OUT = t -> {
        if (t < 0.5f) return 16f * t * t * t * t * t;
        float u = (-2f * t) + 2f;
        return 1f - (u * u * u * u * u) / 2f;
    };

    /**
     * Sine ease-in.
     *
     * <p>
     * Smooth acceleration using a cosine curve. This tends to feel very natural and is great for
     * subtle UI transitions.
     * </p>
     */
    public static final Tween SINE_IN = t -> (float) (1.0 - Math.cos((t * Math.PI) / 2.0));

    /**
     * Sine ease-out.
     *
     * <p>
     * Smooth deceleration using a sine curve. Often paired with {@link #SINE_IN} for in/out motion.
     * </p>
     */
    public static final Tween SINE_OUT = t -> (float) Math.sin((t * Math.PI) / 2.0);

    /**
     * Sine ease-in-out.
     *
     * <p>
     * Symmetric sine/cosine based easing. This is a safe, "pleasant" default when you want a smooth
     * transition without overshoot or harsh curvature.
     * </p>
     */
    public static final Tween SINE_IN_OUT = t -> (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);

    /**
     * Exponential ease-in.
     *
     * <p>
     * Starts extremely slow and accelerates rapidly. This curve has a very strong ramp near the end.
     * </p>
     *
     * <p>
     * Special case: returns {@code 0} exactly when {@code t == 0} to avoid tiny floating-point values.
     * </p>
     */
    public static final Tween EXPO_IN = t ->
            t == 0f ? 0f : (float) Math.pow(2.0, 10.0 * (t - 1.0));

    /**
     * Exponential ease-out.
     *
     * <p>
     * Starts extremely fast and decelerates sharply. This curve approaches {@code 1} quickly, then
     * eases into the final value.
     * </p>
     *
     * <p>
     * Special case: returns {@code 1} exactly when {@code t == 1} to avoid tiny floating-point error.
     * </p>
     */
    public static final Tween EXPO_OUT = t ->
            t == 1f ? 1f : (float) (1.0 - Math.pow(2.0, -10.0 * t));

    /**
     * Exponential ease-in-out.
     *
     * <p>
     * Combines exponential acceleration and deceleration. This is a very dramatic curve.
     * </p>
     *
     * <p>
     * Special cases:
     * </p>
     * <ul>
     *     <li>{@code t == 0} returns {@code 0} exactly.</li>
     *     <li>{@code t == 1} returns {@code 1} exactly.</li>
     * </ul>
     */
    public static final Tween EXPO_IN_OUT = t -> {
        if (t == 0f) return 0f;
        if (t == 1f) return 1f;
        if (t < 0.5f) return (float) (Math.pow(2.0, 20.0 * t - 10.0) / 2.0);
        return (float) ((2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0);
    };

    /**
     * Circular ease-in.
     *
     * <p>
     * Simulates motion along a circular arc, producing a gentle start that accelerates smoothly.
     * </p>
     */
    public static final Tween CIRC_IN = t ->
            (float) (1.0 - Math.sqrt(1.0 - (t * t)));

    /**
     * Circular ease-out.
     *
     * <p>
     * Circular deceleration curve. It feels like following a quarter circle into the endpoint.
     * </p>
     */
    public static final Tween CIRC_OUT = t ->
            (float) Math.sqrt(1.0 - Math.pow(t - 1f, 2));

    /**
     * Circular ease-in-out.
     *
     * <p>
     * Symmetric circular acceleration and deceleration.
     * </p>
     */
    public static final Tween CIRC_IN_OUT = t -> {
        if (t < 0.5f)
            return (float) ((1.0 - Math.sqrt(1.0 - Math.pow(2.0 * t, 2.0))) / 2.0);
        return (float) ((Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2.0)) + 1.0) / 2.0);
    };

    /**
     * Bounce ease-out implementation.
     *
     * <p>
     * Uses a classic piecewise polynomial to simulate repeated impacts that diminish over time.
     * This easing is discontinuous in acceleration at the impact points, which is what creates
     * the "bounce" impression.
     * </p>
     *
     * <p>
     * Note: this is the canonical "bounce out" curve. {@link #BOUNCE_IN} and {@link #BOUNCE_IN_OUT}
     * are derived from it.
     * </p>
     */
    public static final Tween BOUNCE_OUT = t -> {
        final float n1 = 7.5625f;
        final float d1 = 2.75f;

        if (t < 1f / d1) return n1 * t * t;
        if (t < 2f / d1) return n1 * (t -= 1.5f / d1) * t + 0.75f;
        if (t < 2.5f / d1) return n1 * (t -= 2.25f / d1) * t + 0.9375f;
        return n1 * (t -= 2.625f / d1) * t + 0.984375f;
    };

    /**
     * Bounce ease-in.
     *
     * <p>
     * Defined as the time-reversal of {@link #BOUNCE_OUT}. This produces bounces at the beginning
     * instead of at the end.
     * </p>
     *
     * <p>
     * Mathematical relationship:
     * {@code bounceIn(t) = 1 - bounceOut(1 - t)}
     * </p>
     */
    public static final Tween BOUNCE_IN = t -> 1f - BOUNCE_OUT.apply(1f - t);

    /**
     * Bounce ease-in-out.
     *
     * <p>
     * Applies bounce-in for the first half and bounce-out for the second half, with scaling so that
     * the output remains normalized over the whole interval.
     * </p>
     */
    public static final Tween BOUNCE_IN_OUT =
            t -> t < 0.5f
                    ? (1f - BOUNCE_OUT.apply(1f - 2f * t)) * 0.5f
                    : (1f + BOUNCE_OUT.apply(2f * t - 1f)) * 0.5f;

    /**
     * Creates a Back ease-in tween.
     *
     * <p>
     * Back easings overshoot in the opposite direction before moving toward the target.
     * This factory produces an ease-in curve that initially moves "backwards" and then accelerates forward.
     * </p>
     *
     * <p>
     * The {@code overshoot} parameter controls how far the curve pulls back.
     * Typical values are in the range {@code [1.0 .. 3.0]} depending on how dramatic you want the effect.
     * </p>
     *
     * @param overshoot overshoot strength (higher = more pullback)
     * @return a new {@link Tween} implementing back ease-in with the provided overshoot
     */
    public static Tween backIn(float overshoot) {
        final float s = overshoot;
        return t -> t * t * ((s + 1f) * t - s);
    }

    /**
     * Creates a Back ease-out tween.
     *
     * <p>
     * Back ease-out overshoots past the end value and then settles back to {@code 1}.
     * This is great for UI "pop" effects, button presses, and item pickup motion.
     * </p>
     *
     * <p>
     * The {@code overshoot} parameter controls the magnitude of the overshoot.
     * </p>
     *
     * @param overshoot overshoot strength (higher = more overshoot)
     * @return a new {@link Tween} implementing back ease-out with the provided overshoot
     */
    public static Tween backOut(float overshoot) {
        final float s = overshoot;
        return t -> {
            float u = t - 1f;
            return 1f + (u * u * ((s + 1f) * u + s));
        };
    }

    /**
     * Creates a Back ease-in-out tween.
     *
     * <p>
     * Combines back-in (first half) and back-out (second half). The curve overshoots slightly near
     * both ends of the motion depending on {@code overshoot}.
     * </p>
     *
     * <p>
     * The implementation follows the common convention of scaling the overshoot by {@code 1.525}
     * for in/out behavior, which tends to produce visually pleasing symmetry.
     * </p>
     *
     * @param overshoot overshoot strength (higher = more overshoot/pullback)
     * @return a new {@link Tween} implementing back ease-in-out with the provided overshoot
     */
    public static Tween backInOut(float overshoot) {
        final float s = overshoot * 1.525f;
        return t -> {
            if (t < 0.5f) {
                float u = 2f * t;
                return (u * u * ((s + 1f) * u - s)) / 2f;
            }
            float u = 2f * t - 2f;
            return (u * u * ((s + 1f) * u + s) + 2f) / 2f;
        };
    }

    /**
     * Creates an Elastic ease-in tween.
     *
     * <p>
     * Elastic easings oscillate around the curve and can overshoot significantly.
     * This ease-in version starts with oscillations that grow as the motion accelerates.
     * </p>
     *
     * <p>
     * Parameters:
     * </p>
     * <ul>
     *     <li>{@code period}: controls the oscillation frequency (lower = faster oscillations).</li>
     *     <li>{@code amplitude}: controls how large the oscillations are (higher = more overshoot).</li>
     * </ul>
     *
     * <p>
     * Notes:
     * </p>
     * <ul>
     *     <li>If {@code t == 0} returns {@code 0} exactly.</li>
     *     <li>If {@code t == 1} returns {@code 1} exactly.</li>
     * </ul>
     *
     * @param period oscillation period (must be > 0 for meaningful output)
     * @param amplitude oscillation amplitude (typically around 1..2)
     * @return a new {@link Tween} implementing elastic ease-in
     */
    public static Tween elasticIn(float period, float amplitude) {
        return t -> {
            if (t == 0f) return 0f;
            if (t == 1f) return 1f;

            double s = period / 4.0;
            t -= 1f;

            return (float) (-(amplitude * Math.pow(2.0, 10.0 * t)
                    * Math.sin(((t - s) * (2.0 * Math.PI)) / period)));
        };
    }

    /**
     * Creates an Elastic ease-out tween.
     *
     * <p>
     * Elastic ease-out starts with overshoot/oscillation and settles into {@code 1}.
     * </p>
     *
     * <p>
     * Parameters match {@link #elasticIn(float, float)}:
     * </p>
     * <ul>
     *     <li>{@code period} controls oscillation frequency.</li>
     *     <li>{@code amplitude} controls oscillation magnitude.</li>
     * </ul>
     *
     * @param period oscillation period (must be > 0 for meaningful output)
     * @param amplitude oscillation amplitude (typically around 1..2)
     * @return a new {@link Tween} implementing elastic ease-out
     */
    public static Tween elasticOut(float period, float amplitude) {
        return t -> {
            if (t == 0f) return 0f;
            if (t == 1f) return 1f;

            double s = period / 4.0;

            return (float) (amplitude * Math.pow(2.0, -10.0 * t)
                    * Math.sin(((t - s) * (2.0 * Math.PI)) / period) + 1.0);
        };
    }

    /**
     * Creates an Elastic ease-in-out tween.
     *
     * <p>
     * Elastic in-out oscillates at both ends. It behaves like elastic-in for the first half
     * and elastic-out for the second half, with scaling so the curve maps to {@code [0,1]} overall.
     * </p>
     *
     * <p>
     * This easing is visually loud. Use it for stylized UI moments or strong game-feel beats,
     * not for subtle transitions.
     * </p>
     *
     * @param period oscillation period (must be > 0 for meaningful output)
     * @param amplitude oscillation amplitude (typically around 1..2)
     * @return a new {@link Tween} implementing elastic ease-in-out
     */
    public static Tween elasticInOut(float period, float amplitude) {
        return t -> {
            if (t == 0f) return 0f;
            if (t == 1f) return 1f;

            double s = period / 4.0;

            if (t < 0.5f) {
                double u = (2.0 * t) - 1.0;
                return (float) (-0.5 * (amplitude * Math.pow(2.0, 10.0 * u)
                        * Math.sin(((u - s) * (2.0 * Math.PI)) / period)));
            }

            double u = (2.0 * t) - 1.0;
            return (float) (amplitude * Math.pow(2.0, -10.0 * u)
                    * Math.sin(((u - s) * (2.0 * Math.PI)) / period) * 0.5 + 1.0);
        };
    }
}