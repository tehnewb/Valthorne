package valthorne.event;

/**
 * An {@code EventListener} listens for and handles events of a specific type, as defined by the generic parameter {@code E}.
 * Listeners must be registered with an {@code EventPublisher} to receive events.
 *
 * <p>
 * The {@code handle} method is invoked when an event of the matching type is published.
 * Listeners can be prioritized using the {@code @EventPriority} annotation, where higher values indicate earlier execution.
 *
 * <p>
 * Implementations should be thread-safe if used in a concurrent environment.
 *
 * @param <E> the type of {@code Event} this listener handles
 * @author Albert Beaupre
 * @since August 27th, 2024
 */
public interface EventListener<E extends Event> {

    /**
     * Handles the specified event when published by an {@code EventPublisher}.
     *
     * <p>
     * If the event is consumed within this method (via {@code Event.consume()}), no subsequent
     * listeners will process it. Exceptions thrown here may disrupt the event handling chain,
     * depending on the publisher's implementation.
     *
     * @param event the event to handle
     */
    void handle(E event);

    /**
     * Determines if this listener should handle the given event. Defaults to {@code true}.
     *
     * <p>
     * Override this method to implement dynamic filtering logic (e.g., based on event state).
     *
     * @param event the event to evaluate
     * @return {@code true} if the event should be handled; {@code false} otherwise
     */
    default boolean canHandle(E event) {
        return true;
    }
}