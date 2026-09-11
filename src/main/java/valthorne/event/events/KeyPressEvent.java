package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Keyboard press notification carrying a GLFW key code and modifier mask.
 * Uses the dedicated KEY_PRESS event route and inherits mutable event-consumption
 * state. The payload represents a key transition; Unicode text entry is handled
 * by TextInputEvent rather than inferred from this key code.
 *
 * @author Albert Beaupre
 */
public class KeyPressEvent extends KeyEvent {

    /**
     * Creates a press notification with the corresponding numeric event type.
     * Key and modifier values are retained without querying current keyboard state.
     *
     * @param key GLFW key code
     * @param modifiers GLFW modifier bit mask
     */
    public KeyPressEvent(int key, int modifiers) {
        super(EventTypes.KEY_PRESS, key, modifiers);
    }
}
