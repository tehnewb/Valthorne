package valthorne.event;

/**
 * Base class for every event dispatched by {@link EventPublisher}.
 *
 * <h2>Routing model</h2>
 * <p>
 * Every event instance permanently stores a compact integer type identifier. The identifier is
 * supplied by an {@link EventType} when the event is constructed and is used directly as an array
 * index by {@link EventPublisher}. Publication therefore does not require a {@code Class} lookup,
 * a hash operation, reflection, annotation inspection, or a superclass walk.
 * </p>
 *
 * <h2>Consumption</h2>
 * <p>
 * A handler may call {@link #consume()} to stop propagation of the current event. The publisher
 * resets the consumed state immediately before every dispatch, so the same event object may be
 * reused safely by the same owner for many sequential publications.
 * </p>
 *
 * <h2>Thread ownership</h2>
 * <p>
 * Event instances are deliberately <strong>not thread-safe</strong>. In particular, the consumed
 * flag is a plain {@code boolean}, not a volatile or atomic value. A single event instance must be
 * owned by one producer/dispatch thread at a time and must never be published concurrently.
 * This avoids synchronization costs on every handler invocation and is essential for the intended
 * high-throughput use case.
 * </p>
 *
 * <p>
 * Multiple different event instances may of course be published concurrently through the same
 * {@link EventPublisher}; the publisher itself is designed for that usage.
 * </p>
 *
 * @author Albert Beaupre
 * @see EventType
 * @see EventHandler
 * @see EventPublisher
 * @since August 29th, 2024
 */
public abstract class Event {

    /** Numeric routing identifier used by {@link EventPublisher}. */
    private final int typeId;

    /** Dispatch-local propagation state. Intentionally non-volatile. */
    private boolean consumed;

    /**
     * Creates an event permanently associated with the supplied event type.
     *
     * @param type type descriptor whose ID will be stored by this event
     * @throws NullPointerException if {@code type} is {@code null}
     */
    protected Event(EventType<?> type) {
        if (type == null) {
            throw new NullPointerException("Event type cannot be null.");
        }
        this.typeId = type.id();
    }

    /**
     * Returns the dense numeric route ID for this event.
     *
     * <p>The value never changes during the lifetime of the event instance.</p>
     *
     * @return zero-based event type identifier
     */
    public final int typeId() {
        return typeId;
    }

    /**
     * Returns whether a handler has consumed the current publication.
     *
     * @return {@code true} when propagation should stop
     */
    public final boolean isConsumed() {
        return consumed;
    }

    /**
     * Stops propagation to any handlers that have not yet executed.
     *
     * <p>
     * Calling this method repeatedly is harmless. The old event system threw when an event was
     * consumed twice; the high-throughput system intentionally avoids that exceptional branch in
     * normal handler code.
     * </p>
     */
    public final void consume() {
        consumed = true;
    }

    /**
     * Explicitly clears the consumed state.
     *
     * <p>
     * Application code normally does not need to call this because {@link EventPublisher#publish(Event)}
     * resets the state automatically. The method is retained as a convenient compatibility utility.
     * </p>
     */
    public final void unconsume() {
        consumed = false;
    }

    /**
     * Resets dispatch-local state immediately before a publication.
     *
     * <p>Package-private so only event infrastructure controls the dispatch lifecycle.</p>
     */
    final void prepareForDispatch() {
        consumed = false;
    }

    /**
     * Returns a lightweight diagnostic representation.
     *
     * @return class name, type ID, and consumed state
     */
    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "[typeId=" + typeId
                + ", consumed=" + consumed
                + ']';
    }
}
