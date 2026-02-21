package valthorne.tween;

/**
 * Represents a functional interface for setting colors.
 * The {@code ColorSetter} interface defines a single abstract method
 * which accepts four float parameters representing the red, green,
 * blue, and alpha (transparency) components of a color.
 * <p>
 * This interface is often used wherever a method is required to define
 * how a specific color must be applied or processed.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
@FunctionalInterface
public interface ColorSetter {

    /**
     * Sets the color components.
     *
     * @param r the red component of the color, typically in the range [0.0, 1.0]
     * @param g the green component of the color, typically in the range [0.0, 1.0]
     * @param b the blue component of the color, typically in the range [0.0, 1.0]
     * @param a the alpha (transparency) component of the color, typically in the range [0.0, 1.0]
     */
    void set(float r, float g, float b, float a);
}