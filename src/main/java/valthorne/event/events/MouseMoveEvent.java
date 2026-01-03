package valthorne.event.events;

/**
 * The MouseMoveEvent class represents a mouse movement event, encapsulating
 * information about the starting and ending positions of the mouse cursor during
 * the event. It is a subclass of {@code MouseEvent} and adds functionality to
 * handle the ending coordinates of a mouse move action.
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class MouseMoveEvent extends MouseEvent {

    private short toX, toY;

    /**
     * Constructs a new MouseMoveEvent with the specified mouse button, modifier flags,
     * starting coordinates, and ending coordinates of the mouse movement.
     *
     * @param button    the mouse button involved in the event, represented as an integer
     * @param modifiers the modifier flags active during the event (e.g., Shift, Control)
     * @param fromX     the starting x-coordinate of the mouse cursor during the event
     * @param fromY     the starting y-coordinate of the mouse cursor during the event
     * @param toX       the ending x-coordinate of the mouse cursor during the event
     * @param toY       the ending y-coordinate of the mouse cursor during the event
     */
    public MouseMoveEvent(int button, int modifiers, int fromX, int fromY, int toX, int toY) {
        super(fromX, fromY, button, modifiers);
        this.toX = (short) toX;
        this.toY = (short) toY;
    }

    /**
     * Sets the ending x-coordinate of the mouse cursor for this mouse movement event.
     *
     * @param toX the new x-coordinate to set, represented as a {@code short}.
     */
    public void setToX(short toX) {
        this.toX = toX;
    }

    /**
     * Sets the ending y-coordinate of the mouse cursor for this mouse movement event.
     *
     * @param toY the new y-coordinate to set, represented as a {@code short}.
     */
    public void setToY(short toY) {
        this.toY = toY;
    }


    /**
     * @return the ending x-coordinate of the mouse cursor as an integer
     */
    public int getToX() {
        return toX;
    }

    /**
     * @return the ending y-coordinate of the mouse cursor as an integer
     */
    public int getToY() {
        return toY;
    }
}
