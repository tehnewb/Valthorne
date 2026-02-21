package valthorne.tween;

/**
 * Functional interface representing a single property setter used in tweening animations.
 * The {@code TweenSetter} interface provides the ability to apply or write a transformed value, typically during
 * an interpolation or animation process. It is primarily used in conjunction with tweening systems for updating
 * and setting values (such as position, scale, opacity, etc.) on the target object.
 * <p>
 * This interface is a core component of the tweening system and is often paired with a {@code TweenGetter}
 * for defining how to interpolate values between two states.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
@FunctionalInterface
public interface TweenSetter {

    /**
     * Sets the value for the corresponding property or target.
     * Typically used in tweening animations to apply the transformed value.
     *
     * @param value the value to set, typically representing an interpolated or calculated property
     */
    void set(float value);
}