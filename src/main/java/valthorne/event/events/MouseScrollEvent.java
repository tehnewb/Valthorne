package valthorne.event.events;

import valthorne.event.Event;
import valthorne.event.EventTypes;

/**
 * Mouse scroll notification containing horizontal and vertical input deltas.
 * Offsets describe one scroll callback, not cursor coordinates, accumulated
 * displacement or a prescribed pixel distance. Consumers choose how to map
 * them to scrolling or zooming. The mouse producer supplies floating-point
 * offsets so fractional trackpad input remains available to precise accessors.
 *
 * <p>
 * The event is routed directly through {@link EventTypes#MOUSE_SCROLL}. Payload fields remain
 * mutable so one exclusively-owned object may be reused to reduce allocation pressure.
 * The mouse producer reuses this event between publications; listeners that need
 * a lasting value should copy the numeric offsets instead of retaining the event.
 * Do not mutate or publish the same instance concurrently. Payload setters do
 * not reset consumption; the event publisher handles that before dispatch.
 * </p>
 *
 * <p>Integer accessors truncate fractional offsets toward zero. The legacy
 * integer setters first narrow to signed 16-bit values, whereas construction
 * converts its integers directly to floats. Use {@link #setPreciseOffsets(float, float)}
 * to preserve fractional values and avoid that legacy narrowing.</p>
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class MouseScrollEvent extends Event {

    private float xOffset; // Horizontal delta for the current publication, including fractional input.
    private float yOffset; // Vertical delta for the current publication, including fractional input.

    /**
     * Creates an event on the mouse-scroll route with the supplied integer
     * deltas converted directly to floats. Large integers can lose float
     * precision; unlike legacy setters, construction does not narrow to short.
     *
     * @param xOffset the initial horizontal scroll delta
     * @param yOffset the initial vertical scroll delta
     */
    public MouseScrollEvent(int xOffset, int yOffset) {
        super(EventTypes.MOUSE_SCROLL);
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }

    /**
     * Replaces both offsets using the legacy integer conversion. Each input is
     * narrowed to a signed short before storage, so values outside that range
     * wrap rather than clamp. Routing and consumption state are unchanged.
     *
     * @param xOffset the horizontal delta to narrow and store
     * @param yOffset the vertical delta to narrow and store
     * @return this event for reuse or chaining
     */
    public MouseScrollEvent set(int xOffset, int yOffset) {
        this.xOffset = (short) xOffset;
        this.yOffset = (short) yOffset;
        return this;
    }

    /**
     * Replaces only the horizontal delta after narrowing it to a signed short.
     * Out-of-range values wrap; the vertical delta and event state are retained.
     *
     * @param xOffset the horizontal scroll delta to narrow and store
     */
    public void setXOffset(int xOffset) {
        this.xOffset = (short) xOffset;
    }

    /**
     * Replaces only the vertical delta after narrowing it to a signed short.
     * Out-of-range values wrap; the horizontal delta and event state are retained.
     *
     * @param yOffset the vertical scroll delta to narrow and store
     */
    public void setYOffset(int yOffset) {
        this.yOffset = (short) yOffset;
    }

    /**
     * Stores both finite deltas directly, preserving fractional trackpad input
     * without short narrowing. Both values are validated before either field
     * changes; a rejected update leaves the previous payload intact. This method
     * does not publish the event or reset its consumed state.
     *
     * @param x the finite horizontal scroll delta
     * @param y the finite vertical scroll delta
     * @return this event for reuse or chaining
     * @throws IllegalArgumentException if either delta is NaN or infinite
     */
    public MouseScrollEvent setPreciseOffsets(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Non-finite scroll");
        xOffset = x;
        yOffset = y;
        return this;
    }

    /**
     * Reads the stored horizontal delta without integer conversion or mutation.
     * Fractional precision depends on the producer and the setter it used.
     *
     * @return the horizontal scroll delta as a float
     */
    public float preciseXOffset() {return xOffset;}

    /**
     * Reads the stored vertical delta without integer conversion or mutation.
     * Prefer this accessor when fractional input should affect scroll or zoom.
     *
     * @return the vertical scroll delta as a float
     */
    public float preciseYOffset() {return yOffset;}

    /**
     * Provides the integer compatibility view of the horizontal delta. Java's
     * float-to-int conversion truncates toward zero and saturates values outside
     * the integer range; reading does not discard the stored fractional value.
     *
     * @return the horizontal scroll delta converted to an integer
     */
    public int xOffset() {
        return (int) xOffset;
    }

    /**
     * Provides the integer compatibility view of the vertical delta. Java's
     * float-to-int conversion truncates toward zero and saturates values outside
     * the integer range; use the precise accessor for deltas smaller than one.
     *
     * @return the vertical scroll delta converted to an integer
     */
    public int yOffset() {
        return (int) yOffset;
    }
}
