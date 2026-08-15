package valthorne.event.listeners;

import valthorne.event.EventHandler;
import valthorne.event.EventPublisher;
import valthorne.event.EventTypes;
import valthorne.event.events.KeyEvent;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;

/**
 * Convenience handler for both key-press and key-release routes.
 *
 * <p>
 * The old publisher delivered subclass events to a listener registered for {@code KeyEvent.class}
 * by walking the event's superclass chain. The high-throughput publisher intentionally does not do
 * that. This interface therefore provides {@link #register(EventPublisher, int)} and
 * {@link #unregister(EventPublisher)} helpers that explicitly attach the same handler to the two
 * concrete numeric routes.
 * </p>
 *
 * <p>
 * Once registered, the publisher still performs only one route-array lookup per publication.
 * The small subtype switch below is executed only by this convenience handler, not by every event
 * in the global dispatcher.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 16th, 2025
 */
public interface KeyListener extends EventHandler<KeyEvent> {

    /** Dispatches the concrete key event to its specialized callback. */
    @Override
    default void handle(KeyEvent event) {
        switch (event) {
            case KeyPressEvent e -> keyPressed(e);
            case KeyReleaseEvent e -> keyReleased(e);
            default -> throw new IllegalStateException("Unexpected key event: " + event);
        }
    }

    /**
     * Registers this listener for key press and key release at normal priority {@code 0}.
     *
     * @param publisher target publisher
     */
    default void register(EventPublisher publisher) {
        register(publisher, 0);
    }

    /**
     * Registers this exact listener instance for both concrete key routes.
     *
     * @param publisher target publisher
     * @param priority explicit execution priority
     */
    default void register(EventPublisher publisher, int priority) {
        publisher.register(EventTypes.KEY_PRESS, priority, this);
        publisher.register(EventTypes.KEY_RELEASE, priority, this);
    }

    /**
     * Removes this listener from both concrete key routes.
     *
     * @param publisher target publisher
     * @return {@code true} if at least one route registration was removed
     */
    default boolean unregister(EventPublisher publisher) {
        boolean press = publisher.unregister(EventTypes.KEY_PRESS, this);
        boolean release = publisher.unregister(EventTypes.KEY_RELEASE, this);
        return press || release;
    }

    /** @param event key press event */
    void keyPressed(KeyPressEvent event);

    /** @param event key release event */
    void keyReleased(KeyReleaseEvent event);
}
