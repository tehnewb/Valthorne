package valthorne.event.events;

import valthorne.event.Event;

/**
 * Represents a mouse scroll event that encapsulates the horizontal
 * and vertical scroll offsets generated during a scroll interaction.
 * This class extends the {@code Event} class and provides methods to
 * retrieve and manipulate the x and y scroll offsets for the event.
 * <p>
 * The offsets represent the amount of scrolling that has occurred.
 * Positive values indicate scrolling in one direction (e.g., down or
 * right), while negative values indicate the opposite direction
 * (e.g., up or left).
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class MouseScrollEvent extends Event {

    private short xOffset, yOffset;

    /**
     * Constructs a new MouseScrollEvent with the specified horizontal
     * and vertical scroll offsets.
     * <p>
     * The scroll offsets represent the amount of scrolling that occurred
     * along each axis, with positive values indicating scrolling in a
     * forward direction (e.g., right or down) and negative values
     * indicating scrolling in the opposite direction (e.g., left or up).
     *
     * @param xOffset the horizontal scroll offset for this event. The value is cast to a {@code short}.
     * @param yOffset the vertical scroll offset for this event. The value is cast to a {@code short}.
     */
    public MouseScrollEvent(int xOffset, int yOffset) {
        this.xOffset = (short) xOffset;
        this.yOffset = (short) yOffset;
    }

    /**
     * Sets the horizontal scroll offset for this event.
     *
     * @param xOffset the horizontal scroll offset to set, which will be cast to a {@code short}
     */
    public void setXOffset(int xOffset) {
        this.xOffset = (short) xOffset;
    }

    /**
     * Sets the vertical scroll offset for this event.
     *
     * @param yOffset the vertical scroll offset to set, which will be cast to a {@code short}
     */
    public void setYOffset(int yOffset) {
        this.yOffset = (short) yOffset;
    }

    /**
     * Retrieves the horizontal scroll offset for this event.
     * The offset represents the amount of horizontal scrolling that
     * has occurred, with positive values indicating scrolling to the right
     * and negative values indicating scrolling to the left.
     *
     * @return the horizontal scroll offset as an integer
     */
    public int xOffset() {
        return xOffset;
    }

    /**
     * Retrieves the vertical scroll offset for this event.
     * The offset represents the amount of vertical scrolling that
     * has occurred, with positive values indicating scrolling down
     * and negative values indicating scrolling up.
     *
     * @return the vertical scroll offset as an integer
     */
    public int yOffset() {
        return yOffset;
    }
}
