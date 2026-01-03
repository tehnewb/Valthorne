package valthorne.event.listeners;

import valthorne.event.EventListener;
import valthorne.event.events.*;

/**
 * The {@code MouseListener} interface represents a specialized listener for handling
 * {@code MouseEvent} instances, including mouse movement, press, release, and drag events.
 * <p>
 * This interface provides default behavior for processing different mouse events by overriding
 * the {@code handle} method and dispatching them to their respective abstract methods, which
 * implementations must define.
 * <p>
 * It is designed to handle the following types of events:
 * <p>
 * - {@code MouseMoveEvent}: Triggered when the mouse is moved.
 * - {@code MousePressEvent}: Triggered when a mouse button is pressed.
 * - {@code MouseReleaseEvent}: Triggered when a mouse button is released.
 * - {@code MouseDragEvent}: Triggered when the mouse is dragged.
 *
 * @author Albert Beaupre
 * @since December 16th, 2025
 */
public interface MouseListener extends EventListener<MouseEvent> {

    @Override
    default void handle(MouseEvent event) {
        switch (event) {
            case MouseDragEvent e -> mouseDragged(e);
            case MouseMoveEvent e -> mouseMoved(e);
            case MousePressEvent e -> mousePressed(e);
            case MouseReleaseEvent e -> mouseReleased(e);
            default -> throw new IllegalStateException("Unexpected value: " + event);
        }
    }

    /**
     * Handles a mouse press event triggered when a mouse button is pressed.
     * Implementors should define the specific behavior that should occur
     * upon detecting this event.
     *
     * @param event the mouse press event to handle, containing details such as
     *              the x and y coordinates of the mouse pointer, the button
     *              pressed, and any associated modifier states (e.g., Shift,
     *              Ctrl, Alt, or Super).
     */
    void mousePressed(MousePressEvent event);

    /**
     * Handles a mouse release event triggered when a mouse button is released.
     * Implementors should define the specific behavior that should occur upon detecting this event.
     *
     * @param event the mouse release event to handle, containing details such as
     *              the x and y coordinates of the mouse pointer, the button released,
     *              and any associated modifier states (e.g., Shift, Ctrl, Alt, or Super).
     */
    void mouseReleased(MouseReleaseEvent event);

    /**
     * Handles a mouse drag event triggered when the mouse is moved while a button is held down.
     * Implementors should define the specific behavior that should occur during a drag operation.
     *
     * @param event the mouse drag event to handle, containing details such as the starting and
     *              ending coordinates of the drag operation (fromX, fromY, toX, toY), the button
     *              involved in the drag, and any associated modifier states (e.g., Shift, Ctrl,
     *              Alt, or Super).
     */
    void mouseDragged(MouseDragEvent event);

    /**
     * Handles a mouse move event triggered when the mouse pointer is moved
     * across the screen. Implementors should define the specific behavior
     * that should occur when a mouse movement is detected.
     *
     * @param event the mouse move event to handle, containing details about the
     *              starting and ending coordinates of the mouse pointer (fromX,
     *              fromY, toX, toY), as well as any associated button and modifier
     *              states (if applicable).
     */
    void mouseMoved(MouseMoveEvent event);

}
