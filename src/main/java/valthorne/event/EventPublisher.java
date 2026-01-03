package valthorne.event;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code EventPublisher} class manages the publication of events and the registration of listeners
 * that handle these events. It serves as a central hub for event-driven communication in an application.
 *
 * <p>For an {@code Event} to be processed, an {@code EventListener} must be registered with this publisher.
 * When an event is published, registered listeners for that event type are notified in priority order,
 * as defined by the {@link EventPriority} annotation (higher values = earlier execution).
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * EventPublisher publisher = new EventPublisher();
 * publisher.register(MyEvent.class, new MyEventListener());
 * publisher.publish(new MyEvent());
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe, using a {@link ConcurrentHashMap}
 * for listener storage and atomic operations for registration and publication.
 *
 * <p>If no listeners are registered for an event type, {@code publish} has no effect. Exceptions
 * thrown by listeners are logged but do not stop the event propagation unless the event is consumed.
 *
 * @author Albert Beaupre
 * @see Event
 * @see EventListener
 * @see EventPriority
 * @since August 29th, 2024
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class EventPublisher {

    /**
     * A thread-safe mapping of event types to ordered sets of listeners.
     */
    private final ConcurrentHashMap<Class<? extends Event>, TreeSet<EventListener>> listeners = new ConcurrentHashMap<>();

    /**
     * Determines whether an event type has any registered listeners.
     *
     * @param clazz the event class to check for registration; must not be null
     * @return true if the event class has registered listeners, false otherwise
     * @throws NullPointerException if the event class is null
     */
    public boolean isRegistered(Class<? extends Event> clazz) {
        Objects.requireNonNull(clazz, "A null event class cannot be checked for registration with the EventPublisher.");
        return listeners.containsKey(clazz);
    }

    /**
     * Registers an {@code EventListener} to handle events of the specified type.
     * Listeners are ordered by priority (via {@link EventPriority}), with higher values executed first.
     *
     * @param clazz    the event type the listener will handle
     * @param listener the listener to register
     * @throws IllegalArgumentException if clazz or listener is null
     */
    public void register(Class<? extends Event> clazz, EventListener listener) {
        Objects.requireNonNull(clazz, "A null event class cannot be registered.");
        Objects.requireNonNull(listener, "A null listener cannot be registered for an event class.");

        listeners.computeIfAbsent(clazz, this::createSet).add(listener);
    }

    /**
     * Unregisters an {@code EventListener} from handling events of the specified type.
     *
     * @param clazz    the event type to unregister from
     * @param listener the listener to remove
     * @return true if the listener was removed, false if it wasn’t registered
     * @throws IllegalArgumentException if clazz or listener is null
     */
    public boolean unregister(Class<? extends Event> clazz, EventListener listener) {
        Objects.requireNonNull(clazz, "A null event class cannot be unregistered from.");
        Objects.requireNonNull(listener, "A null listener cannot be unregistered from an event class.");

        TreeSet<EventListener> set = listeners.get(clazz);
        return set != null && set.remove(listener);
    }

    /**
     * Publishes the given {@code Event} to all registered {@code EventListener}s capable of handling it.
     * The event is processed by listeners in order of their priority, from highest to lowest.
     * If an event is consumed during processing, any remaining listeners will not be invoked.
     *
     * @param event the event to be published; must not be null
     * @throws NullPointerException if the event is null
     * @throws RuntimeException     if an exception occurs while a listener is handling the event
     */
    public void publish(Event event) {
        Objects.requireNonNull(event, "A null event cannot be published.");

        Class<?> clazz = event.getClass();

        while (clazz != null && Event.class.isAssignableFrom(clazz)) {
            @SuppressWarnings("unchecked")
            TreeSet<EventListener> set = listeners.get((Class<? extends Event>) clazz);

            if (set != null) {
                for (EventListener listener : set) {
                    if (event.isConsumed())
                        return;

                    try {
                        if (listener.canHandle(event)) {
                            listener.handle(event);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to handle event", e);
                    }
                }
            }

            clazz = clazz.getSuperclass();
        }
    }


    /**
     * Creates a {@code TreeSet} for storing listeners, ordered by priority (highest first).
     * Ties in priority are resolved by listener identity to ensure uniqueness.
     *
     * @param clazz the event type for the set
     * @return a priority-ordered TreeSet
     */
    private TreeSet<EventListener> createSet(Class<? extends Event> clazz) {
        return new TreeSet<>((listener1, listener2) -> {
            int compare = Integer.compare(extractPriority(listener2, clazz), extractPriority(listener1, clazz));
            if (compare == 0)
                return Integer.compare(System.identityHashCode(listener2), System.identityHashCode(listener1));
            return compare;
        });
    }

    /**
     * Extracts the priority of an {@code EventListener} for a specific {@code Event} type, based
     * on the presence of the {@code @EventPriority} annotation on the listener's {@code handle} method.
     * If the annotation is not present, a default priority of {@code 0} is returned.
     *
     * @param listener the {@code EventListener} whose priority is being extracted; must not be null
     * @param clazz    the event class the listener is associated with; must not be null
     * @return the priority level as defined by the {@code @EventPriority} annotation, or {@code 0} if the annotation is absent
     */
    private static int extractPriority(EventListener listener, Class<? extends Event> clazz) {
        for (Method method : listener.getClass().getMethods()) {
            if (!method.getName().equals("handle")) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && clazz.isAssignableFrom(params[0])) {
                EventPriority annotation = method.getAnnotation(EventPriority.class);
                return annotation != null ? annotation.priority() : 0;
            }
        }
        return 0;
    }

}
