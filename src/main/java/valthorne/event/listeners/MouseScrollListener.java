package valthorne.event.listeners;

import valthorne.event.EventListener;
import valthorne.event.events.MouseScrollEvent;

/**
 * The {@code MouseScrollListener} interface represents a specialized listener for handling
 * {@code MouseScrollEvent} instances, which are triggered when a mouse scroll action is detected.
 * <p>
 * This interface provides default behavior for processing {@code MouseScrollEvent} by invoking
 * the {@code mouseScrolled} method. Implementations must define the specific behavior that should
 * occur when a mouse scroll event is detected.
 * <p>
 * The {@code handle} method from the {@code EventListener} is overridden to directly delegate
 * the event to the {@code mouseScrolled} method.
 *
 * @author Albert Beaupre
 * @since December 16th, 2025
 */
public interface MouseScrollListener extends EventListener<MouseScrollEvent> {

    @Override
    default void handle(MouseScrollEvent event) {
        this.mouseScrolled(event);
    }

    /**
     * Handles a mouse scroll event triggered when the scroll wheel moves.
     * Implementors should define the specific behavior that should occur
     * when a scroll action is detected.
     *
     * @param event the mouse scroll event to handle, containing details
     *              about the horizontal and vertical scroll offsets
     *              (such as xOffset and yOffset).
     */
    void mouseScrolled(MouseScrollEvent event);
}
