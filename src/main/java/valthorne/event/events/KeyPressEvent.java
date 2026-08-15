package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Event emitted when a keyboard key is pressed.
 *
 * <p>This concrete class routes directly through {@link EventTypes#KEY_PRESS}.</p>
 *
 * @author Albert Beaupre
 */
public class KeyPressEvent extends KeyEvent {

    /**
     * @param key GLFW key code
     * @param modifiers GLFW modifier bit mask
     */
    public KeyPressEvent(int key, int modifiers) {
        super(EventTypes.KEY_PRESS, key, modifiers);
    }
}
