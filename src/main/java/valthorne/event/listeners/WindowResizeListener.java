package valthorne.event.listeners;

import valthorne.event.EventListener;
import valthorne.event.events.WindowResizeEvent;

/**
 * The {@code WindowResizeListener} interface defines a specialized {@code EventListener}
 * for handling {@code WindowResizeEvent} instances, which are triggered when the size
 * of a window changes.
 * <p>
 * This interface provides a default implementation for handling window resize events by
 * overriding the {@code handle} method and forwarding the event to the {@code windowResized}
 * method, which subclasses must implement.
 * <p>
 * Implementors of this interface should define the specific behavior for handling these
 * resize events in their {@code windowResized} method.
 *
 * @author Albert Beaupre
 * @since December 25th, 2025
 */
public interface WindowResizeListener extends EventListener<WindowResizeEvent> {

    @Override
    default void handle(WindowResizeEvent event) {
        this.windowResized(event);
    }

    /**
     * Handles a window resize event triggered when the size of a window changes.
     * Implementors should define the behavior that should occur when this event is detected.
     *
     * @param event the window resize event to handle, containing details such as
     *              the old dimensions (oldWidth, oldHeight) and the new dimensions
     *              (newWidth, newHeight) of the resized window.
     */
    void windowResized(WindowResizeEvent event);
}
