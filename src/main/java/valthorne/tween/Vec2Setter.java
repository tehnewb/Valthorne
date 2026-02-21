package valthorne.tween;

/**
 * A functional interface for setting the two components of a 2D vector.
 * The primary purpose of this interface is to provide a lambda-friendly
 * way to assign values to a vector with two float components (x, y).
 * <p>
 * Implementations of this interface can be used, for example, to update
 * position, scale, or other properties represented using 2D coordinates.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
@FunctionalInterface
public interface Vec2Setter {

    /**
     * Sets the two components of the 2D vector using the given float values.
     *
     * @param x the value to set for the x-axis component
     * @param y the value to set for the y-axis component
     */
    void set(float x, float y);
}