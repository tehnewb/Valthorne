package valthorne.ui.behavior;

/**
 * Stores the finite numeric state shared by texture and NanoVG sliders.
 * Values are clamped to an inclusive range and can be snapped to increments
 * measured from its minimum. This model handles numeric and input calculations;
 * it does not draw, manage focus, or notify widget action listeners.
 *
 * <h2>Range and Stepping</h2>
 * <p>A maximum below the minimum collapses the range at the minimum. A zero
 * step disables snapping; negative steps are converted to zero. Positive steps
 * use nearest-integer rounding with ties to even through {@link Math#rint(double)}.
 * Both endpoints remain directly reachable even when the step does not divide
 * the range evenly. Changing the range or step reapplies clamping and snapping
 * to the current value.</p>
 *
 * <pre>{@code
 * RangeModel range = new RangeModel(10, 97, 20);
 * range.step(5);
 * range.percent(1); // Exact maximum 97, even though it is off the step grid.
 * range.increment(-1); // Applies one negative step and snaps within the range.
 * }</pre>
 *
 * <p>Pointer coordinates are top-left-local track coordinates. Horizontal values
 * increase to the right; vertical values increase upward. Instances are mutable
 * and intended to be owned by the UI dispatch thread.</p>
 *
 * @author Albert Beaupre
 */
public final class RangeModel {
    private float min, max, value, step; // Inclusive endpoints, current snapped value, and step size (zero disables snapping).

    /**
     * Initializes the inclusive range and clamps the initial value into it.
     * Snapping starts disabled. A reversed range collapses at its minimum.
     *
     * @param min   the finite lower endpoint
     * @param max   the finite requested upper endpoint
     * @param value the finite initial value before clamping
     * @throws IllegalArgumentException if an argument or the resulting range width is non-finite
     */
    public RangeModel(float min, float max, float value) {
        range(min, max);
        value(value);
    }

    /**
     * Rejects NaN and either infinity before a numeric setting is applied.
     * Valid finite values, including signed zero, are returned unchanged.
     *
     * @param value the candidate numeric input
     * @return the same finite value
     * @throws IllegalArgumentException if value is non-finite
     */
    private static float finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Range values must be finite");
        return value;
    }

    /**
     * Returns the inclusive lower endpoint, retained when a requested maximum
     * lies below it. Reading this value does not change the model.
     *
     * @return the finite minimum in the slider's numeric units
     */
    public float min() {return min;}

    /**
     * Returns the effective inclusive upper endpoint after reversed-range
     * normalization. It is always greater than or equal to the minimum.
     *
     * @return the finite effective maximum in the slider's numeric units
     */
    public float max() {return max;}

    /**
     * Returns the current value after the most recent clamping and optional
     * snapping operation. Reading the value does not perform another snap.
     *
     * @return the current finite value within the inclusive endpoints
     */
    public float value() {return value;}

    /**
     * Returns the configured step size used for snapping and incremental input.
     * Zero disables snapping and selects the fallback increment size.
     *
     * @return the nonnegative step in the slider's numeric units
     */
    public float step() {return step;}

    /**
     * Replaces the range and reapplies the current value to it. A maximum below
     * the minimum is raised to the minimum rather than swapping endpoints.
     * Validation occurs before the endpoints are modified.
     *
     * @param min the finite lower endpoint
     * @param max the finite requested upper endpoint
     * @throws IllegalArgumentException if either endpoint or the effective width is non-finite
     */
    public void range(float min, float max) {
        finite(min);
        finite(max);
        if (!Float.isFinite(Math.max(min, max) - min)) throw new IllegalArgumentException("Range is too large");
        this.min = min;
        this.max = Math.max(min, max);
        value(value);
    }

    /**
     * Clamps a finite candidate, optionally snaps interior values to the step
     * grid anchored at the minimum, and clamps the result again. Exact endpoints
     * bypass snapping. Halfway grid positions use ties-to-even rounding.
     *
     * @param next the finite requested value in the range's numeric units
     * @throws IllegalArgumentException if next is NaN or infinite
     */
    public void value(float next) {
        next = Math.clamp(finite(next), min, max);
        if (step > 0 && next != min && next != max)
            next = (float) (min + Math.rint(((double) next - min) / step) * step);
        value = Math.clamp(next, min, max);
    }

    /**
     * Sets the snapping increment and reapplies it to the current value.
     * Negative finite inputs become zero, disabling snapping. A positive step
     * may exceed the range width; endpoint clamping still applies.
     *
     * @param step the finite requested increment in numeric units
     * @throws IllegalArgumentException if step is NaN or infinite
     */
    public void step(float step) {
        this.step = Math.max(0, finite(step));
        value(value);
    }

    /**
     * Converts the current value to a normalized fraction of the effective range.
     * A collapsed range returns zero to avoid division by zero.
     *
     * @return a fraction from zero to one, or zero when both endpoints match
     */
    public float percent() {return max == min ? 0 : (value - min) / (max - min);}

    /**
     * Clamps a finite fraction to zero through one, maps it into the numeric
     * range, then applies normal value snapping. The resulting fraction may
     * differ from the request when stepping is enabled.
     *
     * @param percent the normalized requested fraction, not a zero-to-one-hundred percentage
     * @throws IllegalArgumentException if percent is NaN or infinite
     */
    public void percent(float percent) {value(min + Math.clamp(finite(percent), 0, 1) * (max - min));}

    /**
     * Moves by a multiple of the configured step, or a fallback increment when
     * stepping is disabled. The fallback is one hundredth of the range width,
     * with a minimum of {@code 0.000001f}. The candidate is calculated in double
     * precision, clamped, then passed through normal value snapping.
     *
     * @param amount the signed number of increments; fractional counts are supported
     * @throws IllegalArgumentException if amount is NaN; infinite amounts clamp to an endpoint
     */
    public void increment(float amount) {
        double next = value + (double) amount * (step > 0 ? step : Math.max((max - min) / 100f, 0.000001f));
        value((float) Math.clamp(next, min, max));
    }

    /**
     * Maps a pointer coordinate to a value using the travel available to the
     * thumb center. Subtracts half the thumb length from the coordinate and uses
     * {@code length - thumb} as the usable travel. Nonpositive travel leaves the
     * value unchanged. Positions outside usable travel clamp to an endpoint.
     *
     * @param coordinate pointer position along the track in top-left-local units
     * @param length     full track length in the same units
     * @param thumb      thumb length along the track in the same units
     * @param vertical   whether to reverse the fraction so upward movement increases value
     * @throws IllegalArgumentException if the calculated fraction is NaN
     */
    public void pointer(float coordinate, float length, float thumb, boolean vertical) {
        float usable = length - thumb;
        if (usable <= 0) return;
        float p = Math.clamp((coordinate - thumb * .5f) / usable, 0, 1);
        percent(vertical ? 1 - p : p);
    }

    /**
     * Applies a supported keyboard command to this range. Home and End select
     * the exact endpoints. Right/Left increment or decrement horizontal ranges;
     * Up/Down do so for vertical ranges. Other keys leave the value unchanged.
     *
     * @param key      the {@link valthorne.Keyboard} key code
     * @param vertical whether the range uses vertical arrow-key navigation
     * @return true for a recognized key, even if clamping leaves the value unchanged
     */
    public boolean key(int key, boolean vertical) {
        if (key == valthorne.Keyboard.HOME) value(min);
        else if (key == valthorne.Keyboard.END) value(max);
        else if (key == (vertical ? valthorne.Keyboard.UP : valthorne.Keyboard.RIGHT)) increment(1);
        else if (key == (vertical ? valthorne.Keyboard.DOWN : valthorne.Keyboard.LEFT)) increment(-1);
        else return false;
        return true;
    }
}
