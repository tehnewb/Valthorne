package valthorne.event.listeners;

import valthorne.event.EventHandler;
import valthorne.event.EventPublisher;
import valthorne.event.EventTypes;
import valthorne.event.events.MouseScrollEvent;

/**
 * Specialized high-throughput handler for {@link MouseScrollEvent}.
 *
 * <p>
 * This interface is itself an {@link EventHandler}, so it may be registered directly with
 * {@link EventTypes#MOUSE_SCROLL}. Convenience registration methods are supplied for symmetry with
 * the multi-route listener interfaces.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 16th, 2025
 */
public interface MouseScrollListener extends EventHandler<MouseScrollEvent> {

    /** Delegates the generic handler entry point to {@link #mouseScrolled(MouseScrollEvent)}. */
    @Override
    default void handle(MouseScrollEvent event) {
        mouseScrolled(event);
    }

    /** Registers this listener at priority {@code 0}. */
    default void register(EventPublisher publisher) {
        publisher.register(EventTypes.MOUSE_SCROLL, this);
    }

    /** Registers this listener at an explicit priority. */
    default void register(EventPublisher publisher, int priority) {
        publisher.register(EventTypes.MOUSE_SCROLL, priority, this);
    }

    /** Removes this listener from the mouse-scroll route. */
    default boolean unregister(EventPublisher publisher) {
        return publisher.unregister(EventTypes.MOUSE_SCROLL, this);
    }

    /** @param event mouse scroll event */
    void mouseScrolled(MouseScrollEvent event);
}
