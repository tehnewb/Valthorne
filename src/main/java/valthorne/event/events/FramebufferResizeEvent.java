package valthorne.event.events;

import valthorne.event.Event;
import valthorne.event.EventTypes;

/**
 * Pixel-size notification dispatched separately from logical window resizing.
 * Window reuses this payload and publishes it synchronously after updating the viewport
 * and cached framebuffer dimensions. Listeners must copy values rather than retain the
 * event. Zero dimensions represent a non-drawable framebuffer; defer rendering and
 * render-target allocation until both dimensions are positive. Subscribe through
 * {@code JGL.subscribe(EventTypes.FRAMEBUFFER_RESIZE, listener)} on the window thread.
 * Updating a payload alone does not resize a framebuffer or publish a notification.
 */
public final class FramebufferResizeEvent extends Event {

    private int oldWidth; // Previous framebuffer width in pixels.
    private int oldHeight; // Previous framebuffer height in pixels.
    private int newWidth; // Replacement framebuffer width in pixels.
    private int newHeight; // Replacement framebuffer height in pixels.

    /**
     * Creates an initially zero-sized payload on the framebuffer resize route.
     */
    public FramebufferResizeEvent() {
        super(EventTypes.FRAMEBUFFER_RESIZE);
    }

    /**
     * Replaces the pixel dimensions without dispatching or modifying native state.
     *
     * @param oldWidth previous pixel width
     * @param oldHeight previous pixel height
     * @param newWidth replacement pixel width
     * @param newHeight replacement pixel height
     * @return this reusable payload
     */
    public FramebufferResizeEvent set(int oldWidth, int oldHeight, int newWidth, int newHeight) {
        this.oldWidth = oldWidth;
        this.oldHeight = oldHeight;
        this.newWidth = newWidth;
        this.newHeight = newHeight;
        return this;
    }

    /**
     * Returns the width before the change.
     *
     * @return previous width in pixels
     */
    public int getOldWidth() {
        return oldWidth;
    }

    /**
     * Returns the height before the change.
     *
     * @return previous height in pixels
     */
    public int getOldHeight() {
        return oldHeight;
    }

    /**
     * Returns the width after the change.
     *
     * @return replacement width in pixels, including zero when non-drawable
     */
    public int getNewWidth() {
        return newWidth;
    }

    /**
     * Returns the height after the change.
     *
     * @return replacement height in pixels, including zero when non-drawable
     */
    public int getNewHeight() {
        return newHeight;
    }
}
