package valthorne.ui.behavior;

import org.joml.Vector2f;
import valthorne.event.events.MouseScrollEvent;

/**
 * Computes scroll offsets consistently for texture and NanoVG scroll panels.
 * Wheel deltas are scaled into content-coordinate units and subtracted from
 * the current offsets. Both results are clamped to nonnegative scroll ranges.
 * The caller applies the returned offsets to its own panel state.
 * Fractional trackpad input is read through MouseScrollEvent's precise accessors,
 * avoiding truncation from its integer compatibility getters.
 *
 * <p>An event is consumed only when the resulting X or Y offset differs from
 * the supplied value, allowing an unchanged boundary scroll to continue through
 * normal routing to an ancestor. Existing consumption is never cleared. The
 * utility has no stored state and allocates a result vector for each call.</p>
 *
 * @author Albert Beaupre
 */
public final class ScrollBehavior {
    /**
     * Prevents construction of this stateless helper. Panel offsets and axis
     * settings are supplied explicitly for each wheel calculation.
     */
    private ScrollBehavior() {
    }

    /**
     * Computes the next offsets and consumes the event if either changes.
     * Horizontal wheel input affects X when enabled. Vertical input affects Y
     * when vertical scrolling is enabled and has positive available range;
     * otherwise it falls back to X when horizontal scrolling is enabled and
     * the event has no horizontal delta.
     * The fallback depends on vertical capability and total available range,
     * not whether Y is already at its upper or lower boundary. A vertically
     * scrollable panel with positive maxY therefore does not redirect a blocked
     * vertical movement into X merely because it has reached an edge.
     *
     * <p>Both axes are clamped even when their scrolling flag is disabled, so
     * correcting an out-of-range starting offset can also consume the event.
     * Negative maximum offsets are treated as zero. Supply finite offsets,
     * extents, deltas and speed; this method does not validate numerical finiteness.</p>
     *
     * <p>Positive deltas decrease offsets with a positive speed. Zero speed
     * suppresses wheel displacement but still clamps existing offsets; negative
     * speed reverses direction. The result preserves fractional movement and is
     * independent of future event reuse. No panel layout or scroll state is
     * modified by this helper.</p>
     *
     * @param event      the wheel event to read and conditionally consume
     * @param horizontal whether horizontal wheel movement or fallback is enabled
     * @param vertical   whether vertical wheel movement is enabled
     * @param x          the current horizontal offset in content units
     * @param y          the current vertical offset in content units
     * @param maxX       the maximum horizontal offset, clamped to at least zero
     * @param maxY       the maximum vertical offset, clamped to at least zero
     * @param speed      content-coordinate units per wheel-delta unit
     * @return a newly allocated vector containing clamped X and Y offsets
     */
    public static Vector2f wheel(MouseScrollEvent event, boolean horizontal, boolean vertical, float x, float y, float maxX, float maxY, float speed) {
        float nextX = x, nextY = y;
        if (horizontal && event.preciseXOffset() != 0) nextX -= event.preciseXOffset() * speed;
        if (vertical && maxY > 0) nextY -= event.preciseYOffset() * speed;
        else if (horizontal && event.preciseXOffset() == 0) nextX -= event.preciseYOffset() * speed;
        nextX = Math.clamp(nextX, 0, Math.max(0, maxX));
        nextY = Math.clamp(nextY, 0, Math.max(0, maxY));
        if (nextX != x || nextY != y) event.consume();
        return new Vector2f(nextX, nextY);
    }
}
