package valthorne.event;

import java.util.Objects;

/**
 * Immutable, strongly typed descriptor for one numeric event route.
 *
 * <p>
 * The old event system used {@code Class<? extends Event>} keys. This replacement moves routing
 * information into a dense integer ID so publication can use direct array indexing. The generic
 * parameter preserves compile-time type safety when registering {@link EventHandler}s.
 * </p>
 *
 * <h2>ID rules</h2>
 * <ul>
 *     <li>IDs must be non-negative.</li>
 *     <li>IDs should be dense and zero-based for cache- and memory-efficient routing tables.</li>
 *     <li>An ID used with a publisher must be lower than the publisher's configured type count.</li>
 *     <li>IDs should remain stable if other code persists or otherwise depends on them.</li>
 * </ul>
 *
 * <p>
 * This class intentionally does not maintain a global registry or allocate IDs dynamically.
 * {@link EventTypes} is the explicit source of truth for this package.
 * </p>
 *
 * @param <E> concrete event type represented by this descriptor
 * @author Albert Beaupre
 * @see EventTypes
 * @see EventPublisher
 */
public record EventType<E extends Event>(int id, String name) {

    /**
     * Creates an event type descriptor.
     *
     * @param id   zero-based numeric routing ID
     * @param name stable human-readable diagnostic name
     * @throws IllegalArgumentException if {@code id} is negative
     * @throws NullPointerException     if {@code name} is {@code null}
     */
    public EventType(int id, String name) {
        if (id < 0) {
            throw new IllegalArgumentException("Event type ID cannot be negative.");
        }
        this.id = id;
        this.name = Objects.requireNonNull(name, "Event type name cannot be null.");
    }

    /**
     * Returns the numeric route ID.
     *
     * @return event route index
     */
    @Override
    public int id() {
        return id;
    }

    /**
     * Returns the diagnostic name.
     *
     * @return stable event type name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Returns a compact diagnostic representation such as {@code key-press[1]}.
     *
     * @return type name and ID
     */
    @Override
    public String toString() {
        return name + '[' + id + ']';
    }
}
