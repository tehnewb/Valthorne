package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Event emitted when a mouse button is pressed.
 *
 * @author Albert Beaupre
 */
public class MousePressEvent extends MouseEvent {

    /**
     * @param button pressed mouse button
     * @param modifiers modifier bit mask
     * @param x cursor X coordinate
     * @param y cursor Y coordinate
     */
    public MousePressEvent(int button, int modifiers, int x, int y) {
        super(EventTypes.MOUSE_PRESS, button, modifiers, x, y);
    }
}
