package valthorne.tween;

/**
 * Enum representing various target dimensions that can be used in tween animations.
 * Each value corresponds to a specific property or combination of properties
 * that can be animated.
 * <p>
 * - {@code X}: Targets the x-coordinate.
 * - {@code Y}: Targets the y-coordinate.
 * - {@code WIDTH}: Targets the width.
 * - {@code HEIGHT}: Targets the height.
 * - {@code XY}: Targets both x- and y-coordinates.
 * - {@code WH}: Targets both width and height.
 * - {@code XYWH}: Targets x- and y-coordinates, and width and height.
 * <p>
 * This enum is typically used for controlling the specific aspects of an object
 * or component that are affected during an animation sequence.
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
public enum TweenDimensionalTarget {
    X,
    Y,
    WIDTH,
    HEIGHT,
    XY,
    WH,
    XYWH
}