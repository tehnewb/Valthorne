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
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class WindowResizeEvent extends Event {

    private int oldWidth;
    private int oldHeight;
    private int newWidth;
    private int newHeight;

    /**
     * @param oldWidth previous window width
     * @param oldHeight previous window height
     * @param newWidth new window width
     * @param newHeight new window height
     */
    public WindowResizeEvent(int oldWidth, int oldHeight, int newWidth, int newHeight) {
        super(EventTypes.WINDOW_RESIZE);
        set(oldWidth, oldHeight, newWidth, newHeight);
    }

    /**
     * Replaces the complete resize payload for reuse.
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

    /** @return width before resize */
    public int getOldWidth() {
        return oldWidth;
    }

    /** @param oldWidth width before resize */
    public void setOldWidth(int oldWidth) {
        this.oldWidth = oldWidth;
    }

    /** @return height before resize */
    public int getOldHeight() {
        return oldHeight;
    }

    /** @param oldHeight height before resize */
    public void setOldHeight(int oldHeight) {
        this.oldHeight = oldHeight;
    }

    /** @return width after resize */
    public int getNewWidth() {
        return newWidth;
    }

    /** @param newWidth width after resize */
    public void setNewWidth(int newWidth) {
        this.newWidth = newWidth;
    }

    /** @return height after resize */
    public int getNewHeight() {
        return newHeight;
    }

    /** @param newHeight height after resize */
    public void setNewHeight(int newHeight) {
        this.newHeight = newHeight;
    }
}
