package valthorne.tween;

/**
 * Functional interface representing an easing/tweening function that defines
 * the relationship between input normalized time and output eased values.
 * A tween function manipulates or transforms the progression of a parameter
 * over time, providing interpolated results that can be used to create smooth,
 * non-linear animations or transitions.
 * <p>
 * Typical implementations of this interface include easing functions such as
 * Linear, Quadratic, Cubic, Bounce, Back, or Elastic curves. These provide the
 * basis for creating visually appealing animations by adjusting the rate of
 * change relative to time.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
@FunctionalInterface
public interface Tween {

    /**
     * Applies the easing or tweening function to the given normalized time input.
     * This method defines how the progression of time is mapped to a transformed
     * output, often used for creating animations or smooth transitions.
     *
     * @param t the normalized time input, typically in the range [0, 1]
     * @return the transformed output value based on the function's easing logic
     */
    float apply(float t);

    /**
     * Clamps the given value to the range [0, 1].
     * If the input value is less than 0, it returns 0.
     * If the input value is greater than 1, it returns 1.
     * Otherwise, it returns the input value unchanged.
     *
     * @param t the value to be clamped
     * @return the clamped value within the range [0, 1]
     */
    static float clamp01(float t) {
        return t < 0f ? 0f : (Math.min(t, 1f));
    }
}