package valthorne.event.events;

import valthorne.event.EventType;
import valthorne.event.EventTypes;

/**
 * Mouse movement event containing starting and ending cursor coordinates.
 *
 * <p>
 * A normal instance routes through {@link EventTypes#MOUSE_MOVE}. The protected constructor allows
 * {@link MouseDragEvent} to reuse this payload implementation while selecting
 * {@link EventTypes#MOUSE_DRAG} as its actual route.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class MouseMoveEvent extends MouseEvent {

    private short toX;
    private short toY;

    /**
     * Creates a normal mouse-move event.
     *
     * @param button mouse button state/code associated with the move
     * @param modifiers modifier bit mask
     * @param fromX starting X coordinate
     * @param fromY starting Y coordinate
     * @param toX ending X coordinate
     * @param toY ending Y coordinate
     */
    public MouseMoveEvent(int button, int modifiers, int fromX, int fromY, int toX, int toY) {
        this(EventTypes.MOUSE_MOVE, button, modifiers, fromX, fromY, toX, toY);
    }

    /**
     * Constructor used by movement subclasses to supply their concrete route type.
     */
    protected MouseMoveEvent(
            EventType<?> type,
            int button,
            int modifiers,
            int fromX,
            int fromY,
            int toX,
            int toY
    ) {
        super(type, button, modifiers, fromX, fromY);
        this.toX = (short) toX;
        this.toY = (short) toY;
    }

    /**
     * Replaces the complete movement payload for reuse.
     *
     * @return this event
     */
    public MouseMoveEvent set(
            int button,
            int modifiers,
            int fromX,
            int fromY,
            int toX,
            int toY
    ) {
        super.set(button, modifiers, fromX, fromY);
        this.toX = (short) toX;
        this.toY = (short) toY;
        return this;
    }

    /** @return ending X coordinate */
    public int getToX() {
        return toX;
    }

    /** @param toX ending X coordinate */
    public void setToX(short toX) {
        this.toX = toX;
    }

    /** @return ending Y coordinate */
    public int getToY() {
        return toY;
    }

    /** @param toY ending Y coordinate */
    public void setToY(short toY) {
        this.toY = toY;
    }
}
