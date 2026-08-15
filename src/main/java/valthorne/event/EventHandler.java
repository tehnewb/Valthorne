package valthorne.event;

/**
 * Minimal synchronous handler used by the high-throughput event dispatcher.
 *
 * <p>
 * Registration itself declares which event route a handler accepts, so this interface has no
 * {@code canHandle(...)} method. If a specific handler needs dynamic filtering, the predicate
 * belongs inside {@link #handle(Event)}. Handlers that do not need filtering therefore avoid an
 * unnecessary virtual call and conditional branch for every event they receive.
 * </p>
 *
 * <h2>Execution guarantees</h2>
 * <ul>
 *     <li>Handlers execute synchronously on the thread calling {@link EventPublisher#publish(Event)}.</li>
 *     <li>Higher numeric registration priority executes before lower priority.</li>
 *     <li>Equal-priority handlers execute in registration order.</li>
 *     <li>{@link Event#consume()} prevents all remaining handlers for that publication from running.</li>
 *     <li>Runtime exceptions propagate directly to the publisher caller.</li>
 * </ul>
 *
 * <h2>Exception policy</h2>
 * <p>
 * The publisher does not wrap every invocation in a try/catch block. A subsystem that requires
 * isolation should register a wrapper handler that applies its own logging/recovery policy. This
 * keeps the universal hot path free of work that many events do not need.
 * </p>
 *
 * @param <E> event type accepted by this handler
 * @author Albert Beaupre
 */
@FunctionalInterface
public interface EventHandler<E extends Event> {

    /**
     * Handles one synchronously dispatched event.
     *
     * @param event event being dispatched
     */
    void handle(E event);
}
