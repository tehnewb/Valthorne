package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Keyboard press notification carrying a GLFW key code and modifier mask.
 * Uses the dedicated KEY_PRESS event route and inherits mutable event-consumption
 * state. The payload represents a key transition; Unicode text entry is handled
 * by TextInputEvent rather than inferred from this key code.
 * Initial presses and native repeats both retain the KEY_PRESS route for compatibility.
 * Command handlers can ignore {@link #isRepeat()} notifications, while editors can
 * deliberately process them. Two-argument construction represents an initial press.
 * Keyboard reuses the payload; copy values during synchronous delivery. Native scancodes
 * are not retained, and Keyboard drops unknown GLFW key codes before publication.
 *
 * @author Albert Beaupre
 */
public class KeyPressEvent extends KeyEvent {

    private boolean repeat; // Whether the producer marked this notification as a native repeat.

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
    /**
     * Returns whether this publication represents a native key repeat.
     *
     * @return true for repeat callbacks; false for initial presses
     */
    public boolean isRepeat() {
        return repeat;
    }

    /**
     * Replaces repeat metadata without changing key, modifiers, route, or consumption.
     * Producers reusing this event must set it for every initial press and repeat.
     *
     * @param repeat whether this notification represents a native repeat
     */
    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }
}
