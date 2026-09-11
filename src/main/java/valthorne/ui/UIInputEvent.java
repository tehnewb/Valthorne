package valthorne.ui;

import valthorne.event.Event;
import org.joml.Vector2f;

/**
 * Describes one receiver's preview or bubble callback during root input routing.
 * The target remains the original destination, while the current target identifies
 * the node whose callback is executing. Every wrapper in the route shares the
 * same underlying event and therefore the same consumption state.
 *
 * <p>Stored coordinates are screen coordinates passed by the root, not local
 * coordinates. {@link #localPosition()} converts them into top-left-local layout
 * coordinates for the current receiver on demand. Non-pointer routes can supply
 * NaN coordinates and therefore have no meaningful pointer position.</p>
 *
 * <p>Do not retain this wrapper after the callback. Its references are fixed,
 * but the event's consumption state and node layout are mutable, and underlying
 * events may be reused for later publications. Construction performs no null
 * checks, coordinate validation, or copying of the referenced objects.</p>
 *
 * @param event         the shared underlying event for this dispatch
 * @param target        the original destination of the routed input
 * @param currentTarget the receiver currently processing preview or bubble input
 * @param screenX       screen X coordinate, or NaN for a route without pointer coordinates
 * @param screenY       screen Y coordinate, or NaN for a route without pointer coordinates
 * @author Albert Beaupre
 */
public record UIInputEvent(Event event, UINode target, UINode currentTarget, float screenX, float screenY) {
    /**
     * Converts the stored screen point using the current receiver's layout and
     * viewport mapping. The result is top-left-local to that receiver and is
     * computed at call time rather than captured when the wrapper is constructed.
     *
     * @return a newly allocated local-position vector; pointerless routes may yield NaN
     * @throws NullPointerException if currentTarget is null
     */
    public Vector2f localPosition() {return currentTarget.screenToLocal(screenX, screenY);}

    /**
     * Consumes the underlying event so normal routing stops delivery to later
     * receivers. Repeated calls are harmless and do not cancel work already
     * performed by callbacks that have run.
     *
     * @throws NullPointerException if event is null
     */
    public void consume() {event.consume();}

    /**
     * Reads the shared event's current consumption state, including consumption
     * performed by another receiver or directly through the underlying event.
     *
     * @return whether further normal event propagation should stop
     * @throws NullPointerException if event is null
     */
    public boolean isConsumed() {return event.isConsumed();}
}
