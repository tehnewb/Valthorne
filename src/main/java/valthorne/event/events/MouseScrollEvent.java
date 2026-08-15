package valthorne.event.events;

import valthorne.event.Event;
import valthorne.event.EventTypes;

/**
 * Mouse scroll event containing horizontal and vertical scroll offsets.
 *
 * <p>
 * The event is routed directly through {@link EventTypes#MOUSE_SCROLL}. Payload fields remain
 * mutable so one exclusively-owned object may be reused to reduce allocation pressure.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class MouseScrollEvent extends Event {

    private short xOffset;
    private short yOffset;

    /**
     * @param xOffset horizontal scroll offset
     * @param yOffset vertical scroll offset
     */
    public MouseScrollEvent(int xOffset, int yOffset) {
        super(EventTypes.MOUSE_SCROLL);
        this.xOffset = (short) xOffset;
        this.yOffset = (short) yOffset;
    }

    /**
     * Replaces both offsets for object reuse.
     *
     * @return this event
     */
    public MouseScrollEvent set(int xOffset, int yOffset) {
        this.xOffset = (short) xOffset;
        this.yOffset = (short) yOffset;
        return this;
    }

    /** @param xOffset horizontal scroll offset */
    public void setXOffset(int xOffset) {
        this.xOffset = (short) xOffset;
    }

    /** @param yOffset vertical scroll offset */
    public void setYOffset(int yOffset) {
        this.yOffset = (short) yOffset;
    }

    /** @return horizontal scroll offset */
    public int xOffset() {
        return xOffset;
    }

    /** @return vertical scroll offset */
    public int yOffset() {
        return yOffset;
    }
}
