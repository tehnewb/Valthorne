package valthorne.event.listeners;

import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MouseMoveEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;

/**
 * The {@code MouseAdapter} class is an abstract adapter class for receiving mouse events.
 * This class provides default (empty) implementations for the methods defined in the
 * {@code MouseListener} interface, allowing subclasses to selectively override only
 * the methods they are interested in.
 * <p>
 * Subclassing {@code MouseAdapter} is useful when creating a listener for mouse events
 * where not all methods from the {@code MouseListener} interface are required to be implemented.
 * <p>
 * The following methods are defined and can be overridden by subclasses:
 * - {@code mousePressed}: Triggered when a mouse button is pressed.
 * - {@code mouseReleased}: Triggered when a mouse button is released.
 * - {@code mouseDragged}: Triggered when the mouse is dragged (moved while a button is held down).
 * - {@code mouseMoved}: Triggered when the mouse is moved.
 *
 * @author Albert Beaupre
 * @since February 7th, 2026
 */
public class MouseAdapter implements MouseListener {

    @Override
    public void mousePressed(MousePressEvent event) {}

    @Override
    public void mouseReleased(MouseReleaseEvent event) {}

    @Override
    public void mouseDragged(MouseDragEvent event) {}

    @Override
    public void mouseMoved(MouseMoveEvent event) {}

}
