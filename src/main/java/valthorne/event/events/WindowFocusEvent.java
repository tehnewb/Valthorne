package valthorne.event.events;

import valthorne.event.Event;
import valthorne.event.EventTypes;

/**
 * Reports a native window focus transition on {@link EventTypes#WINDOW_FOCUS}.
 * The payload describes whether the window gained or lost focus, rather than
 * which UI node owns keyboard focus. UI roots use focus loss to cancel input
 * state that could otherwise remain captured after switching applications.
 *
 * <p>Constructing this event does not activate a window or change UI focus.
 * The boolean payload is fixed, while inherited consumption state belongs to
 * the current dispatch. A single instance must not be published concurrently.</p>
 *
 * @author Albert Beaupre
 */
public final class WindowFocusEvent extends Event {
    private final boolean focused; // True for native focus gain; false for native focus loss.

    /**
     * Creates a focus notification without publishing it or modifying window
     * state. The value is a snapshot of the reported native transition.
     *
     * @param focused true if the window gained focus, false if it lost focus
     */
    public WindowFocusEvent(boolean focused) {
        super(EventTypes.WINDOW_FOCUS);
        this.focused = focused;
    }

    /**
     * Returns the focus state captured by this notification. This does not
     * query the window's current state, which may have changed since publication.
     *
     * @return whether this notification reports native window focus gain
     */
    public boolean isFocused() {return focused;}
}
