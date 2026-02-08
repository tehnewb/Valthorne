package valthorne.graphics.animation;

import valthorne.graphics.Drawable;

/**
 * Represents a single frame within an animation sequence. Each frame is associated
 * with a {@link Drawable} object that can be rendered, as well as a duration
 * specifying how long the frame should be displayed before transitioning to the next.
 * <p>
 * This class is immutable and is intended to act as a lightweight data container
 * for animation frames.
 *
 * @param drawable the drawable object to render for this animation frame
 * @param duration the duration, in seconds, for which this frame should be displayed
 */
public record AnimationFrame(Drawable drawable, float duration) {

}
