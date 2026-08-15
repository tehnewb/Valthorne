package valthorne.event.listeners;

import valthorne.event.EventHandler;
import valthorne.event.EventPublisher;
import valthorne.event.EventTypes;
import valthorne.event.events.WindowResizeEvent;

/**
 * Specialized high-throughput handler for {@link WindowResizeEvent}.
 *
 * <p>
 * The listener registers directly against {@link EventTypes#WINDOW_RESIZE}; no reflective method
 * inspection or class-based listener lookup is involved.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 25th, 2025
 */
public interface WindowResizeListener extends EventHandler<WindowResizeEvent> {

    /** Delegates the handler entry point to {@link #windowResized(WindowResizeEvent)}. */
    @Override
    default void handle(WindowResizeEvent event) {
        windowResized(event);
    }

    /** Registers this listener at normal priority {@code 0}. */
    default void register(EventPublisher publisher) {
        publisher.register(EventTypes.WINDOW_RESIZE, this);
    }

    /** Registers this listener at an explicit priority. */
    default void register(EventPublisher publisher, int priority) {
        publisher.register(EventTypes.WINDOW_RESIZE, priority, this);
    }

    /** Removes this listener from the window-resize route. */
    default boolean unregister(EventPublisher publisher) {
        return publisher.unregister(EventTypes.WINDOW_RESIZE, this);
    }

    /** @param event completed window-resize event */
    void windowResized(WindowResizeEvent event);
}
