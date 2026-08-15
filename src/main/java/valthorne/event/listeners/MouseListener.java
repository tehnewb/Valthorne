package valthorne.event.listeners;

import valthorne.event.EventHandler;
import valthorne.event.EventPublisher;
import valthorne.event.EventTypes;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MouseEvent;
import valthorne.event.events.MouseMoveEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;

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

    /** Dispatches a routed concrete mouse event to its specialized callback. */
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

    /** Registers all four supported mouse routes at priority {@code 0}. */
    default void register(EventPublisher publisher) {
        register(publisher, 0);
    }

    /**
     * Registers all four concrete mouse routes with one explicit priority.
     *
     * @param publisher target publisher
     * @param priority execution priority
     */
    default void register(EventPublisher publisher, int priority) {
        publisher.register(EventTypes.MOUSE_MOVE, priority, this);
        publisher.register(EventTypes.MOUSE_DRAG, priority, this);
        publisher.register(EventTypes.MOUSE_PRESS, priority, this);
        publisher.register(EventTypes.MOUSE_RELEASE, priority, this);
    }

    /**
     * Removes this listener from all four routes.
     *
     * @return {@code true} if at least one registration was removed
     */
    default boolean unregister(EventPublisher publisher) {
        boolean removed = publisher.unregister(EventTypes.MOUSE_MOVE, this);
        removed |= publisher.unregister(EventTypes.MOUSE_DRAG, this);
        removed |= publisher.unregister(EventTypes.MOUSE_PRESS, this);
        removed |= publisher.unregister(EventTypes.MOUSE_RELEASE, this);
        return removed;
    }

    /** @param event mouse-button press event */
    void mousePressed(MousePressEvent event);

    /** @param event mouse-button release event */
    void mouseReleased(MouseReleaseEvent event);

    /** @param event mouse drag event */
    void mouseDragged(MouseDragEvent event);

    /** @param event mouse movement event */
    void mouseMoved(MouseMoveEvent event);
}
