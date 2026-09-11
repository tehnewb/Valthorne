package valthorne.event.listeners;

import valthorne.event.EventHandler;
import valthorne.event.EventPublisher;
import valthorne.event.EventTypes;
import valthorne.event.events.*;

/**
 * Convenience handler spanning the four primary mouse action routes.
 *
 * <p>
 * Superclass-based routing has been removed from {@link EventPublisher}. Calling
 * {@link #register(EventPublisher, int)} explicitly registers this same handler instance for
 * {@link EventTypes#MOUSE_MOVE}, {@link EventTypes#MOUSE_DRAG}, {@link EventTypes#MOUSE_PRESS},
 * and {@link EventTypes#MOUSE_RELEASE}. Publication itself remains a single numeric array lookup.
 * </p>
 *
 * <p>
 * Scroll events intentionally remain separate and are handled by {@link MouseScrollListener},
 * matching the structure of the original API.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 16th, 2025
 */
public interface MouseListener extends EventHandler<MouseEvent> {

    /**
     * Dispatches a routed concrete mouse event to its specialized callback.
     * The callback executes immediately and receives the original event reference.
     * This entry point neither copies the event nor consumes it automatically.
     *
     * @param event concrete event supported by this listener
     * @throws IllegalStateException if the event has an unsupported subtype
     * @throws NullPointerException  if event is null
     */
    @Override
    default void handle(MouseEvent event) {
        switch (event) {
            case MouseDragEvent e -> mouseDragged(e);
            case MouseMoveEvent e -> mouseMoved(e);
            case MousePressEvent e -> mousePressed(e);
            case MouseReleaseEvent e -> mouseReleased(e);
            default -> throw new IllegalStateException("Unexpected mouse event: " + event);
        }
    }

    /**
     * Registers all four supported mouse routes at priority {@code 0}.
     * Registration retains this listener instance on the corresponding numeric route
     * or routes; call unregister when the listener's owner no longer needs delivery.
     *
     * @param publisher publisher receiving the registration
     * @throws NullPointerException if publisher is null
     */
    default void register(EventPublisher publisher) {
        register(publisher, 0);
    }

    /**
     * Registers all four concrete mouse routes with one explicit priority.
     *
     * @param publisher target publisher
     * @param priority  execution priority; larger values run first
     */
    default void register(EventPublisher publisher, int priority) {
        publisher.register(EventTypes.MOUSE_MOVE, priority, this);
        publisher.register(EventTypes.MOUSE_DRAG, priority, this);
        publisher.register(EventTypes.MOUSE_PRESS, priority, this);
        publisher.register(EventTypes.MOUSE_RELEASE, priority, this);
    }

    /**
     * Removes this listener from all four routes. Each route is checked even if
     * an earlier removal succeeds; registrations on other routes are preserved.
     *
     * @param publisher publisher from which to detach
     * @return {@code true} if at least one registration was removed
     */
    default boolean unregister(EventPublisher publisher) {
        boolean removed = publisher.unregister(EventTypes.MOUSE_MOVE, this);
        removed |= publisher.unregister(EventTypes.MOUSE_DRAG, this);
        removed |= publisher.unregister(EventTypes.MOUSE_PRESS, this);
        removed |= publisher.unregister(EventTypes.MOUSE_RELEASE, this);
        return removed;
    }

    /**
     * Receives the mouse-button press event. The event is shared with the publisher's
     * dispatch; consume it explicitly if later listeners should not receive it.
     * Implementations run synchronously and should copy needed values before retaining
     * information from reusable events.
     *
     * @param event mouse-button press event
     */
    void mousePressed(MousePressEvent event);

    /**
     * Receives the mouse-button release event. The event is shared with the publisher's
     * dispatch; consume it explicitly if later listeners should not receive it.
     * Implementations run synchronously and should copy needed values before retaining
     * information from reusable events.
     *
     * @param event mouse-button release event
     */
    void mouseReleased(MouseReleaseEvent event);

    /**
     * Receives the mouse drag event. The event is shared with the publisher's
     * dispatch; consume it explicitly if later listeners should not receive it.
     * Implementations run synchronously and should copy needed values before retaining
     * information from reusable events.
     *
     * @param event mouse drag event
     */
    void mouseDragged(MouseDragEvent event);

    /**
     * Receives the mouse movement event. The event is shared with the publisher's
     * dispatch; consume it explicitly if later listeners should not receive it.
     * Implementations run synchronously and should copy needed values before retaining
     * information from reusable events.
     *
     * @param event mouse movement event
     */
    void mouseMoved(MouseMoveEvent event);
}
