package valthorne.event.events;

import valthorne.event.Event;

/**
 * The WindowResizeEvent class represents an event triggered when a window's size changes.
 * This event contains information about the previous dimensions of the window as well as
 * the new dimensions it has been resized to. It extends the {@code Event} class, allowing
 * it to be used in event-driven systems for handling window resizing functionality.
 * <p>
 * This class is typically used in scenarios where applications need to perform specific
 * actions or re-render elements in response to a change in the window size.
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class WindowResizeEvent extends Event {

    private short oldWidth, oldHeight;
    private short newWidth, newHeight;

    /**
     * Constructs a new {@code WindowResizeEvent} with information about the
     * window's previous dimensions and its new dimensions after resizing.
     *
     * @param oldWidth  the previous width of the window before resizing
     * @param oldHeight the previous height of the window before resizing
     * @param newWidth  the new width of the window after resizing
     * @param newHeight the new height of the window after resizing
     */
    public WindowResizeEvent(int oldWidth, int oldHeight, int newWidth, int newHeight) {
        this.oldWidth = (short) oldWidth;
        this.oldHeight = (short) oldHeight;
        this.newWidth = (short) newWidth;
        this.newHeight = (short) newHeight;
    }

    /**
     * Retrieves the previous width of the window before it was resized.
     *
     * @return the previous width of the window as an integer
     */
    public int getOldWidth() {
        return oldWidth;
    }

    /**
     * Sets the previous width of the window before resizing.
     *
     * @param oldWidth the previous width of the window, represented as a short value
     */
    public void setOldWidth(short oldWidth) {
        this.oldWidth = oldWidth;
    }

    /**
     * Retrieves the previous height of the window before it was resized.
     *
     * @return the previous height of the window as an integer
     */
    public int getOldHeight() {
        return oldHeight;
    }

    /**
     * Sets the previous height of the window before resizing.
     *
     * @param oldHeight the previous height of the window, represented as a short value
     */
    public void setOldHeight(short oldHeight) {
        this.oldHeight = oldHeight;
    }

    /**
     * Retrieves the new width of the window after it has been resized.
     *
     * @return the new width of the window as an integer
     */
    public int getNewWidth() {
        return newWidth;
    }

    /**
     * Updates the new width of the window after resizing. This method allows
     * modification of the width value that represents the current updated
     * state of the window's dimensions.
     *
     * @param newWidth the updated width of the window, represented as a short value
     */
    public void setNewWidth(short newWidth) {
        this.newWidth = newWidth;
    }

    /**
     * Retrieves the new height of the window after it has been resized.
     *
     * @return the new height of the window as an integer
     */
    public int getNewHeight() {
        return newHeight;
    }

    /**
     * Updates the new height of the window after resizing. This method allows
     * modification of the height value that represents the current updated
     * state of the window's dimensions.
     *
     * @param newHeight the updated height of the window, represented as a short value
     */
    public void setNewHeight(short newHeight) {
        this.newHeight = newHeight;
    }

}
