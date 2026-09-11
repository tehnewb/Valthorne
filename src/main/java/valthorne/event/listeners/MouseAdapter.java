package valthorne.event.listeners;

import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MouseMoveEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;

/**
 * Adapter with no-op implementations for the four callbacks in {@link MouseListener}.
 *
 * <p>
 * Subclasses may override only the actions they need, then register the adapter through
 * {@link MouseListener#register(valthorne.event.EventPublisher)} or the priority overload.
 * </p>
 *
 * @author Albert Beaupre
 * @since February 7th, 2026
 */
public class MouseAdapter implements MouseListener {

    /**
     * Default no-op press callback.
     * This implementation does not inspect, retain, or consume the event. Override
     * this callback to handle the notification while leaving other callbacks inactive.
     *
     * @param event notification ignored by this default implementation
     */
    @Override
    public void mousePressed(MousePressEvent event) {
    }

    /**
     * Default no-op release callback.
     * This implementation does not inspect, retain, or consume the event. Override
     * this callback to handle the notification while leaving other callbacks inactive.
     *
     * @param event notification ignored by this default implementation
     */
    @Override
    public void mouseReleased(MouseReleaseEvent event) {
    }

    /**
     * Default no-op drag callback.
     * This implementation does not inspect, retain, or consume the event. Override
     * this callback to handle the notification while leaving other callbacks inactive.
     *
     * @param event notification ignored by this default implementation
     */
    @Override
    public void mouseDragged(MouseDragEvent event) {
    }

    /**
     * Default no-op move callback.
     * This implementation does not inspect, retain, or consume the event. Override
     * this callback to handle the notification while leaving other callbacks inactive.
     *
     * @param event notification ignored by this default implementation
     */
    @Override
    public void mouseMoved(MouseMoveEvent event) {
    }
}
