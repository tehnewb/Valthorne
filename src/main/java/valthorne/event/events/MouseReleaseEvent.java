package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Mouse-button release notification carrying button, modifier, and cursor values.
 * Uses the dedicated MOUSE_RELEASE route and inherited event-consumption state.
 * Coordinates are captured by the producer; construction does not convert them
 * to a UI node's local coordinates or query the current cursor.
 *
 * @author Albert Beaupre
 */
public class MouseReleaseEvent extends MouseEvent {

    /**
     * Creates a button release notification from the supplied cursor snapshot.
     * The values are retained without validation or coordinate conversion.
     *
     * @param button GLFW mouse-button code
     * @param modifiers modifier bit mask
     * @param x cursor horizontal coordinate supplied by the producer
     * @param y cursor vertical coordinate supplied by the producer
     */
    public MouseReleaseEvent(int button, int modifiers, int x, int y) {
        super(EventTypes.MOUSE_RELEASE, button, modifiers, x, y);
    }
}
