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

    /**
     * Delegates the generic handler entry point to {@link #mouseScrolled(MouseScrollEvent)}.
     * Passes the original reference synchronously without validation or automatic
     * consumption. Callback exceptions propagate to the caller.
     *
     * @param event notification passed to the specialized callback
     */
    @Override
    default void handle(MouseScrollEvent event) {
        mouseScrolled(event);
    }

    /**
     * Registers this listener at priority {@code 0}.
     * Registration retains this listener instance on the corresponding numeric route
     * or routes; call unregister when the listener's owner no longer needs delivery.
     *
     * @param publisher publisher receiving the registration
     * @throws NullPointerException if publisher is null
     */
    default void register(EventPublisher publisher) {
        publisher.register(EventTypes.MOUSE_SCROLL, this);
    }

    /**
     * Registers this listener at an explicit priority.
     * Registration retains this listener instance on the corresponding numeric route
     * or routes; call unregister when the listener's owner no longer needs delivery.
     *
     * @param publisher publisher receiving the registration
     * @param priority  execution priority; larger values run first
     * @throws NullPointerException if publisher is null
     */
    default void register(EventPublisher publisher, int priority) {
        publisher.register(EventTypes.MOUSE_SCROLL, priority, this);
    }

    /**
     * Removes this listener from the mouse-scroll route.
     * Only registrations for this listener instance on the supported route are affected.
     *
     * @param publisher publisher from which to detach
     * @return true if a registration was removed; false if none existed
     * @throws NullPointerException if publisher is null
     */
    default boolean unregister(EventPublisher publisher) {
        return publisher.unregister(EventTypes.MOUSE_SCROLL, this);
    }

    /**
     * Receives the mouse scroll event. The event is shared with the publisher's
     * dispatch; consume it explicitly if later listeners should not receive it.
     * Implementations run synchronously and should copy needed values before retaining
     * information from reusable events.
     *
     * @param event mouse scroll event
     */
    void mouseScrolled(MouseScrollEvent event);
}
