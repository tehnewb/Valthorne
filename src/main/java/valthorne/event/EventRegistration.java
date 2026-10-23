package valthorne.event;

import valthorne.WindowContext;

/**
 * Cold-path subscription metadata preserving identity separately from input routing.
 * Immutable handler snapshots contain only invokers, so scope metadata is not walked
 * or allocated by the publisher's hot dispatch loop.
 */
final class EventRegistration {
    final EventHandler<Event> handler; // Original caller identity for exact unregistration.
    final EventHandler<Event> invoker; // Direct handler or presentation-scoped input adapter.
    final WindowContext context; // Owning presentation for bulk disposal, or null for primary/global subscriptions.
    final int priority; // Explicit route priority.
    final long sequence; // Registration order among equal priorities.

    /**
     * Stores immutable cold-path subscription metadata.
     *
     * @param handler original identity
     * @param invoker route invocation
     * @param context captured presentation
     * @param priority handler priority
     * @param sequence tie-break sequence
     */
    EventRegistration(EventHandler<Event> handler, EventHandler<Event> invoker, WindowContext context, int priority, long sequence) {
        this.handler = handler;
        this.invoker = invoker;
        this.context = context;
        this.priority = priority;
        this.sequence = sequence;
    }
}
