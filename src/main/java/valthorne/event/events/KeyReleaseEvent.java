package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Event emitted when a keyboard key is released.
 *
 * <p>This concrete class routes directly through {@link EventTypes#KEY_RELEASE}.</p>
 *
 * @author Albert Beaupre
 */
public class KeyReleaseEvent extends KeyEvent {

    /**
     * @param key GLFW key code
     * @param modifiers GLFW modifier bit mask
     */
    public KeyReleaseEvent(int key, int modifiers) {
        super(EventTypes.KEY_RELEASE, key, modifiers);
    }
}
