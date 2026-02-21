package valthorne.tween;

/**
 * Represents a functional interface for applying an operation using a provided eased
 * time value, typically within the range [0..1].
 * <p>
 * The {@code apply} method is intended to be implemented to perform an operation
 * based on the normalized eased time {@code easedT}, which corresponds to
 * interpolated progress in animations, transitions, or other temporal tasks.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
@FunctionalInterface
public interface Applier {

    /**
     * Applies an operation using a provided eased time value.
     * The eased time value represents a normalized progress, typically in the range [0..1],
     * which is often used in animation or transition calculations.
     *
     * @param easedT the normalized eased time in the range [0..1]
     */
    void apply(float easedT);
}