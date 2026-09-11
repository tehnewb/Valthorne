package valthorne.event.events;

import valthorne.event.Event;
import valthorne.event.EventTypes;

/**
 * Event emitted when a window changes dimensions.
 *
 * <p>
 * The four integer dimensions are intentionally stored directly in the reusable event object.
 * The event routes through {@link EventTypes#WINDOW_RESIZE} using the same constant-time numeric
 * dispatch mechanism as all other events.
 * </p>
 *
 * <p>Dimensions are retained without validation, including zero or negative values
 * supplied by a producer. Payload changes do not resize a native window or publish
 * a notification; publication and exclusive ownership belong to the producer.</p>
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class WindowResizeEvent extends Event {

    private int oldWidth; // Previous width supplied by the producer.
    private int oldHeight; // Previous height supplied by the producer.
    private int newWidth; // Replacement width supplied by the producer.
    private int newHeight; // Replacement height supplied by the producer.

    /**
     * Creates a reusable notification on its concrete numeric route. Supplied payload
     * values are stored using the field types without additional range validation.
     *
     * @param oldWidth  previous window width
     * @param oldHeight previous window height
     * @param newWidth  new window width
     * @param newHeight new window height
     */
    public WindowResizeEvent(int oldWidth, int oldHeight, int newWidth, int newHeight) {
        super(EventTypes.WINDOW_RESIZE);
        set(oldWidth, oldHeight, newWidth, newHeight);
    }

    /**
     * Replaces all four dimensions without changing route or consumption state.
     * Does not change the native window or dispatch an event.
     *
     * @param oldWidth previous width
     * @param oldHeight previous height
     * @param newWidth replacement width
     * @param newHeight replacement height
     *
     * @return this event
     */
    public WindowResizeEvent set(int oldWidth, int oldHeight, int newWidth, int newHeight) {
        this.oldWidth = oldWidth;
        this.oldHeight = oldHeight;
        this.newWidth = newWidth;
        this.newHeight = newHeight;
        return this;
    }

    /**
     * Returns the current stored payload component without querying native input or
     * window state. Copy the value if it is needed after this reusable event is updated.
     *
     * @return width before resize
     */
    public int getOldWidth() {
        return oldWidth;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param oldWidth width before resize
     */
    public void setOldWidth(int oldWidth) {
        this.oldWidth = oldWidth;
    }

    /**
     * Returns the current stored payload component without querying native input or
     * window state. Copy the value if it is needed after this reusable event is updated.
     *
     * @return height before resize
     */
    public int getOldHeight() {
        return oldHeight;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param oldHeight height before resize
     */
    public void setOldHeight(int oldHeight) {
        this.oldHeight = oldHeight;
    }

    /**
     * Returns the current stored payload component without querying native input or
     * window state. Copy the value if it is needed after this reusable event is updated.
     *
     * @return width after resize
     */
    public int getNewWidth() {
        return newWidth;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param newWidth width after resize
     */
    public void setNewWidth(int newWidth) {
        this.newWidth = newWidth;
    }

    /**
     * Returns the current stored payload component without querying native input or
     * window state. Copy the value if it is needed after this reusable event is updated.
     *
     * @return height after resize
     */
    public int getNewHeight() {
        return newHeight;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param newHeight height after resize
     */
    public void setNewHeight(int newHeight) {
        this.newHeight = newHeight;
    }
}
