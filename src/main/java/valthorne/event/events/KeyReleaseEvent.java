package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Keyboard release notification carrying a GLFW key code and modifier mask.
 * Uses the dedicated KEY_RELEASE event route and inherits mutable event-consumption
 * state. The payload represents a key transition; Unicode text entry is handled
 * by TextInputEvent rather than inferred from this key code.
 *
 * @author Albert Beaupre
 */
public class KeyReleaseEvent extends KeyEvent {

    /**
     * Creates a release notification with the corresponding numeric event type.
     * Key and modifier values are retained without querying current keyboard state.
     *
     * @param key GLFW key code
     * @param modifiers GLFW modifier bit mask
     */
    public KeyReleaseEvent(int key, int modifiers) {
        super(EventTypes.KEY_RELEASE, key, modifiers);
    }
}
