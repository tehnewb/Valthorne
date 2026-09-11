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
 * <p>Starting coordinates use the inherited integer X/Y fields; ending coordinates
 * are stored as signed shorts, so larger input values wrap during construction or
 * set. This object is reusable and must not be mutated during publication.</p>
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class MouseMoveEvent extends MouseEvent {

    private short toX; // Ending cursor X stored as a signed short.
    private short toY; // Ending cursor Y stored as a signed short.

    /**
     * Creates a normal mouse-move event.
     *
     * @param button    mouse button state/code associated with the move
     * @param modifiers modifier bit mask
     * @param fromX     starting X coordinate
     * @param fromY     starting Y coordinate
     * @param toX       ending X coordinate
     * @param toY       ending Y coordinate
     */
    public MouseMoveEvent(int button, int modifiers, int fromX, int fromY, int toX, int toY) {
        this(EventTypes.MOUSE_MOVE, button, modifiers, fromX, fromY, toX, toY);
    }

    /**
     * Stores the start point in inherited integer fields and the end point in signed
     * shorts while selecting the subclass's numeric route. No range checks occur.
     *
     * @param type concrete event route
     * @param button mouse button code
     * @param modifiers modifier mask
     * @param fromX starting cursor X
     * @param fromY starting cursor Y
     * @param toX ending cursor X, narrowed to a short
     * @param toY ending cursor Y, narrowed to a short
     */
    protected MouseMoveEvent(EventType<?> type, int button, int modifiers, int fromX, int fromY, int toX, int toY) {
        super(type, button, modifiers, fromX, fromY);
        this.toX = (short) toX;
        this.toY = (short) toY;
    }

    /**
     * Replaces the complete movement payload without changing route or consumption.
     * End coordinates are narrowed to signed shorts; start coordinates remain integers.
     *
     * @param button mouse button code
     * @param modifiers modifier mask
     * @param fromX starting cursor X
     * @param fromY starting cursor Y
     * @param toX ending cursor X, narrowed to a short
     * @param toY ending cursor Y, narrowed to a short
     *
     * @return this event
     */
    public MouseMoveEvent set(int button, int modifiers, int fromX, int fromY, int toX, int toY) {
        super.set(button, modifiers, fromX, fromY);
        this.toX = (short) toX;
        this.toY = (short) toY;
        return this;
    }

    /**
     * Returns the current stored payload component without querying native input or
     * window state. Copy the value if it is needed after this reusable event is updated.
     *
     * @return ending X coordinate
     */
    public int getToX() {
        return toX;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param toX ending X coordinate
     */
    public void setToX(short toX) {
        this.toX = toX;
    }

    /**
     * Returns the current stored payload component without querying native input or
     * window state. Copy the value if it is needed after this reusable event is updated.
     *
     * @return ending Y coordinate
     */
    public int getToY() {
        return toY;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param toY ending Y coordinate
     */
    public void setToY(short toY) {
        this.toY = toY;
    }
}
