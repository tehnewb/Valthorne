package valthorne.event;

import valthorne.WindowContext;

/**
 * Cold-created input route adapter isolating native and logical presentations.
 * Generic non-input events retain their existing routing semantics.
 */
final class ContextEventHandler implements EventHandler<Event> {
    private final EventHandler<Event> handler; // Original subscription identity, borrowed from registration.
    private final WindowContext context; // Captured presentation, or null for primary window input.

    /**
     * Captures input ownership without creating a per-publication object.
     *
     * @param handler original handler
     * @param context presentation identity, or null
     */
    ContextEventHandler(EventHandler<Event> handler, WindowContext context) { this.handler = handler; this.context = context; }

    @Override
    public void handle(Event event) {
        if (WindowContext.current() == context && (context == null || !context.isClosed())) handler.handle(event);
    }
}
