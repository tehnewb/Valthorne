package valthorne.graphics.animation;

import valthorne.graphics.Drawable;

/**
 * Represents a single frame within an animation sequence. Each frame is associated
 * with a {@link Drawable} object that can be rendered, as well as a duration
 * specifying how long the frame should be displayed before transitioning to the next.
 * <p>
 * The record keeps fixed component references but does not copy or own the drawable.
 * It performs no duration or null validation; {@link Animation} treats null drawables
 * as non-rendering content and clamps negative frame durations for timing.
 *
 * @param drawable the drawable object to render for this animation frame
 * @param duration the duration, in seconds, for which this frame should be displayed
 * @author Albert Beaupre
 */
public record AnimationFrame(Drawable drawable, float duration) {

}
