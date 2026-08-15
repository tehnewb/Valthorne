package valthorne.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

/**
 * High-throughput synchronous event dispatcher built around immutable route snapshots.
 *
 * <h2>Why this replaces the old publisher</h2>
 * <p>
 * The previous implementation used a {@code ConcurrentHashMap<Class<? extends Event>, ...>},
 * {@code TreeSet}, reflection-backed priority discovery, superclass traversal, and a per-listener
 * {@code canHandle(...)} call. Those features made publication flexible but placed substantial
 * general-purpose machinery directly on the hottest path.
 * </p>
 *
 * <p>
 * This implementation moves work to registration time. Publication performs a numeric array lookup
 * and iterates a dense immutable handler array. It does not perform reflection, hashing, class-tree
 * traversal, sorting, listener-map synchronization, or dispatcher-owned allocation.
 * </p>
 *
 * <h2>Publication hot path</h2>
 * <pre>{@code
 * EventHandler<Event>[] handlers = routes[event.typeId()];
 * for (int i = 0; i < handlers.length; i++) {
 *     handlers[i].handle(event);
 *     if (event.isConsumed()) {
 *         return;
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread safety and snapshot semantics</h2>
 * <p>
 * Multiple threads may call {@link #publish(Event)} concurrently. Listener registration and
 * unregistration are serialized by a private mutation lock, but publication never acquires that
 * lock. Route mutations build new immutable arrays and publish a replacement top-level table via
 * a volatile reference.
 * </p>
 *
 * <p>
 * An event already being dispatched may finish against the old snapshot after a listener is
 * unregistered. A later publication observes either the complete old snapshot or the complete new
 * snapshot, never a partially modified route.
 * </p>
 *
 * <p>
 * The publisher is thread-safe; individual mutable {@link Event} instances are not. Never publish
 * the same event object concurrently.
 * </p>
 *
 * <h2>Priorities</h2>
 * <p>
 * Priority is explicit at registration time. Higher values execute first. Equal values use a
 * monotonic registration sequence as a deterministic tie-breaker. No reflection or annotation is
 * required.
 * </p>
 *
 * <h2>Asynchronous processing</h2>
 * <p>
 * This class intentionally contains no executor or queue. If producers must be decoupled from
 * handlers, place a bounded queue/ring-buffer and sharding layer in front of one or more publishers.
 * Separating scheduling from dispatch keeps backpressure, ownership, and ordering policy explicit.
 * </p>
 *
 * @author Albert Beaupre
 * @see Event
 * @see EventType
 * @see EventHandler
 * @since August 29th, 2024
 */
@SuppressWarnings({"unchecked"})
public final class EventPublisher {

    /**
     * Shared immutable empty route used for event types with no handlers.
     */
    private static final EventHandler<Event>[] EMPTY_HANDLERS = (EventHandler<Event>[]) new EventHandler[0];

    /**
     * Cold-path sort order: highest priority first, then earliest registration first.
     */
    private static final Comparator<Registration> REGISTRATION_ORDER = Comparator.comparingInt((Registration registration) -> registration.priority).reversed().thenComparingLong(registration -> registration.sequence);

    /**
     * Serializes listener topology changes. Never touched by {@link #publish(Event)}.
     */
    private final Object mutationLock = new Object();

    /**
     * Cold-path mutable registration metadata, one list per numeric event type.
     */
    private final ArrayList<Registration>[] registrations;

    /**
     * Immutable routing snapshot visible to publishing threads.
     *
     * <p>The outer table and each inner route are never mutated in place after publication.</p>
     */
    private volatile EventHandler<Event>[][] routes;

    /**
     * Monotonic same-priority ordering sequence.
     */
    private long registrationSequence;

    /**
     * Creates a publisher sized for the built-in {@link EventTypes} registry.
     *
     * <p>
     * This is the normal constructor for the Valthorne event package. The explicit-size constructor
     * remains available for tests or applications that maintain a different compatible registry.
     * </p>
     */
    public EventPublisher() {
        this(EventTypes.COUNT);
    }

    /**
     * Creates a publisher capable of routing IDs in {@code [0, eventTypeCount)}.
     *
     * @param eventTypeCount number of route slots to allocate
     * @throws IllegalArgumentException if {@code eventTypeCount <= 0}
     */
    public EventPublisher(int eventTypeCount) {
        if (eventTypeCount <= 0) {
            throw new IllegalArgumentException("eventTypeCount must be greater than zero.");
        }

        registrations = new ArrayList[eventTypeCount];

        EventHandler<Event>[][] initial = (EventHandler<Event>[][]) new EventHandler[eventTypeCount][];

        for (int i = 0; i < eventTypeCount; i++) {
            initial[i] = EMPTY_HANDLERS;
        }

        routes = initial;
    }

    /**
     * Registers a handler at an explicit priority.
     *
     * <p>
     * Registration identity uses {@code ==}, not {@link Object#equals(Object)}. The exact same
     * handler object cannot be inserted twice on one route, while unrelated handler objects remain
     * independent even if they implement unusual equality semantics.
     * </p>
     *
     * @param type     event route to receive
     * @param priority execution priority; larger values execute first
     * @param handler  handler instance
     * @param <E>      concrete event type
     * @return {@code true} if added, {@code false} if the same instance was already registered
     * @throws NullPointerException     if {@code type} or {@code handler} is null
     * @throws IllegalArgumentException if the type ID is outside this publisher's route table
     */
    public <E extends Event> boolean register(EventType<E> type, int priority, EventHandler<? super E> handler) {
        Objects.requireNonNull(type, "Event type cannot be null.");
        Objects.requireNonNull(handler, "Event handler cannot be null.");

        int typeId = type.id();
        validateTypeId(typeId);

        synchronized (mutationLock) {
            ArrayList<Registration> list = registrations[typeId];

            if (list == null) {
                list = new ArrayList<>(4);
                registrations[typeId] = list;
            }

            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).handler == handler) {
                    return false;
                }
            }

            EventHandler<Event> rawHandler = (EventHandler<Event>) (EventHandler<?>) handler;

            list.add(new Registration(rawHandler, priority, registrationSequence++));
            rebuildRoute(typeId, list);
            return true;
        }
    }

    /**
     * Registers a handler at normal priority {@code 0}.
     *
     * @param type    event route
     * @param handler handler instance
     * @param <E>     concrete event type
     * @return {@code true} if the handler was added
     */
    public <E extends Event> boolean register(EventType<E> type, EventHandler<? super E> handler) {
        return register(type, 0, handler);
    }

    /**
     * Removes one exact handler instance from one event route.
     *
     * <p>
     * An in-flight publication that already captured the previous immutable route may still invoke
     * the handler once. Publications observing the new snapshot will not.
     * </p>
     *
     * @param type    event route
     * @param handler exact handler instance to remove
     * @param <E>     concrete event type
     * @return {@code true} if a registration was removed
     */
    public <E extends Event> boolean unregister(EventType<E> type, EventHandler<? super E> handler) {
        Objects.requireNonNull(type, "Event type cannot be null.");
        Objects.requireNonNull(handler, "Event handler cannot be null.");

        int typeId = type.id();
        validateTypeId(typeId);

        synchronized (mutationLock) {
            ArrayList<Registration> list = registrations[typeId];

            if (list == null) {
                return false;
            }

            for (int i = 0, size = list.size(); i < size; i++) {
                if (list.get(i).handler == handler) {
                    list.remove(i);

                    if (list.isEmpty()) {
                        registrations[typeId] = null;
                        publishRoute(typeId, EMPTY_HANDLERS);
                    } else {
                        rebuildRoute(typeId, list);
                    }

                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Publishes an event synchronously.
     *
     * <p>
     * This method is the performance-critical path. It resets consumption, captures one immutable
     * route-table snapshot, directly indexes the route by {@link Event#typeId()}, and invokes the
     * already-ordered handler array.
     * </p>
     *
     * <p>
     * No dispatcher-owned object is allocated during a successful publication. Runtime exceptions
     * from handlers are allowed to propagate directly to the caller.
     * </p>
     *
     * @param event event to dispatch
     * @throws NullPointerException           if {@code event} is null
     * @throws ArrayIndexOutOfBoundsException if the event's type ID is not valid for this publisher
     * @throws RuntimeException               if a handler throws one
     */
    public void publish(Event event) {
        Objects.requireNonNull(event, "Event cannot be null.");
        event.prepareForDispatch();

        EventHandler<Event>[][] currentRoutes = routes;
        EventHandler<Event>[] handlers = currentRoutes[event.typeId()];

        for (int i = 0, length = handlers.length; i < length; i++) {
            handlers[i].handle(event);

            if (event.isConsumed()) {
                return;
            }
        }
    }

    /**
     * Trusted internal variant of {@link #publish(Event)} without the public null check.
     *
     * <p>
     * Keep use of this method inside event infrastructure. In most applications the JVM optimizes
     * the checked method sufficiently that this variant is unnecessary until profiling proves
     * otherwise.
     * </p>
     *
     * @param event non-null event whose type ID is valid for this publisher
     */
    void publishUnchecked(Event event) {
        event.prepareForDispatch();
        EventHandler<Event>[] handlers = routes[event.typeId()];

        for (int i = 0, length = handlers.length; i < length; i++) {
            handlers[i].handle(event);

            if (event.isConsumed()) {
                return;
            }
        }
    }

    /**
     * Returns whether a route currently has at least one handler.
     *
     * @param type event route
     * @return {@code true} if one or more handlers are registered
     */
    public boolean isRegistered(EventType<?> type) {
        Objects.requireNonNull(type, "Event type cannot be null.");
        int typeId = type.id();
        validateTypeId(typeId);
        return routes[typeId].length != 0;
    }

    /**
     * Returns the currently visible handler count for one route.
     *
     * @param type event route
     * @return number of handlers in the immutable route snapshot
     */
    public int listenerCount(EventType<?> type) {
        Objects.requireNonNull(type, "Event type cannot be null.");
        int typeId = type.id();
        validateTypeId(typeId);
        return routes[typeId].length;
    }

    /**
     * Removes all listener registrations.
     *
     * <p>
     * Existing in-flight publications may finish using snapshots they captured before the clear.
     * New publications observe an entirely empty route table.
     * </p>
     */
    public void clear() {
        synchronized (mutationLock) {
            for (int i = 0; i < registrations.length; i++) {
                registrations[i] = null;
            }

            EventHandler<Event>[][] empty = (EventHandler<Event>[][]) new EventHandler[registrations.length][];

            for (int i = 0; i < empty.length; i++) {
                empty[i] = EMPTY_HANDLERS;
            }

            registrationSequence = 0L;
            routes = empty;
        }
    }

    /**
     * Sorts cold-path metadata and builds the dense immutable handler array consumed by publish.
     */
    private void rebuildRoute(int typeId, ArrayList<Registration> list) {
        list.sort(REGISTRATION_ORDER);

        EventHandler<Event>[] handlers = (EventHandler<Event>[]) new EventHandler[list.size()];

        for (int i = 0, size = list.size(); i < size; i++) {
            handlers[i] = list.get(i).handler;
        }

        publishRoute(typeId, handlers);
    }

    /**
     * Replaces one route using copy-on-write and a single volatile publication of the new table.
     */
    private void publishRoute(int typeId, EventHandler<Event>[] handlers) {
        EventHandler<Event>[][] current = routes;
        EventHandler<Event>[][] updated = current.clone();
        updated[typeId] = handlers;
        routes = updated;
    }

    /**
     * Validates type IDs used by cold-path public operations.
     */
    private void validateTypeId(int typeId) {
        if (typeId < 0 || typeId >= registrations.length) {
            throw new IllegalArgumentException("Invalid event type ID " + typeId + "; valid range is [0, " + registrations.length + ").");
        }
    }

    /**
     * Cold-path registration metadata. These objects are never traversed by {@link #publish(Event)}.
     */
    private record Registration(EventHandler<Event> handler, int priority, long sequence) {

    }
}
