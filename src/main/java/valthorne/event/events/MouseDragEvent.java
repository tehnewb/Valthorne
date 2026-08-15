package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Mouse movement event emitted while a button is being dragged.
 *
 * <p>
 * Although this class extends {@link MouseMoveEvent} to reuse movement payload fields, it has its
 * own numeric route ({@link EventTypes#MOUSE_DRAG}). No superclass traversal is required during
 * publication.
 * </p>
 *
 * @author Albert Beaupre
 */
public class MouseDragEvent extends MouseMoveEvent {

    /**
     * @param button dragged mouse button
     * @param modifiers modifier bit mask
     * @param fromX starting X coordinate
     * @param fromY starting Y coordinate
     * @param toX ending X coordinate
     * @param toY ending Y coordinate
     */
    public MouseDragEvent(int button, int modifiers, int fromX, int fromY, int toX, int toY) {
        super(EventTypes.MOUSE_DRAG, button, modifiers, fromX, fromY, toX, toY);
    }

    /** @return horizontal movement delta */
    public int getDeltaX() {
        return getToX() - getX();
    }

    /** @return vertical movement delta */
    public int getDeltaY() {
        return getToY() - getY();
    }
}
