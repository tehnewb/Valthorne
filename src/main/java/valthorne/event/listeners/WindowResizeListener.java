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

    /**
     * Delegates the handler entry point to {@link #windowResized(WindowResizeEvent)}.
     * Passes the original reference synchronously without validation or automatic
     * consumption. Callback exceptions propagate to the caller.
     *
     * @param event notification passed to the specialized callback
     */
    @Override
    default void handle(WindowResizeEvent event) {
        windowResized(event);
    }

    /**
     * Registers this listener at normal priority {@code 0}.
     * Registration retains this listener instance on the corresponding numeric route
     * or routes; call unregister when the listener's owner no longer needs delivery.
     *
     * @param publisher publisher receiving the registration
     * @throws NullPointerException if publisher is null
     */
    default void register(EventPublisher publisher) {
        publisher.register(EventTypes.WINDOW_RESIZE, this);
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
        publisher.register(EventTypes.WINDOW_RESIZE, priority, this);
    }

    /**
     * Removes this listener from the window-resize route.
     * Only registrations for this listener instance on the supported route are affected.
     *
     * @param publisher publisher from which to detach
     * @return true if a registration was removed; false if none existed
     * @throws NullPointerException if publisher is null
     */
    default boolean unregister(EventPublisher publisher) {
        return publisher.unregister(EventTypes.WINDOW_RESIZE, this);
    }

    /**
     * Receives the completed window-resize event. The event is shared with the publisher's
     * dispatch; consume it explicitly if later listeners should not receive it.
     * Implementations run synchronously and should copy needed values before retaining
     * information from reusable events.
     *
     * @param event completed window-resize event
     */
    void windowResized(WindowResizeEvent event);
}
