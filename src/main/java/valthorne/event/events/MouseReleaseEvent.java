package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Event emitted when a mouse button is released.
 *
 * @author Albert Beaupre
 */
public class MouseReleaseEvent extends MouseEvent {

    /**
     * @param button released mouse button
     * @param modifiers modifier bit mask
     * @param x cursor X coordinate
     * @param y cursor Y coordinate
     */
    public MouseReleaseEvent(int button, int modifiers, int x, int y) {
        super(EventTypes.MOUSE_RELEASE, button, modifiers, x, y);
    }
}
