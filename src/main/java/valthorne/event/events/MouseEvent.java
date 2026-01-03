package valthorne.event.events;

import valthorne.event.Event;

import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER;

/**
 * The MouseEvent class represents a mouse-related event and contains information
 * about the mouse button pressed, the location of the event, and any modifier keys
 * that were active during the event. It extends from the {@code Event} class and provides
 * methods to retrieve and manipulate the state of the mouse interaction.
 * <p>
 * This class can serve as a base class for specific types of mouse events,
 * such as mouse press, release, move, or drag events. Subclasses can further
 * specify or differentiate the behavior for specific types of mouse interactions.
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class MouseEvent extends Event {

    private short x, y;
    private byte button;
    private byte modifiers;

    /**
     * Constructs a new MouseEvent with the specified mouse button, modifier flags, and
     * coordinates of the event.
     *
     * @param button    the mouse button that was pressed or released, represented as an integer
     * @param modifiers the modifier flags active during the event (e.g., Shift, Control)
     * @param x         the x-coordinate of the mouse cursor during the event
     * @param y         the y-coordinate of the mouse cursor during the event
     */
    public MouseEvent(int button, int modifiers, int x, int y) {
        this.x = (short) x;
        this.y = (short) y;
        this.button = (byte) button;
        this.modifiers = (byte) modifiers;
    }

    /**
     * Sets the mouse button associated with this event.
     *
     * @param button the mouse button to set, represented as an integer.
     *               This value will be stored internally as a byte.
     */
    public void setButton(int button) {
        this.button = (byte) button;
    }

    /**
     * Updates the x-coordinate of this mouse event.
     *
     * @param x the new x-coordinate to be set, represented as a {@code short} value
     */
    public void setX(short x) {
        this.x = x;
    }

    /**
     * Updates the y-coordinate of this mouse event.
     *
     * @param y the new y-coordinate to be set, represented as a {@code short} value
     */
    public void setY(short y) {
        this.y = y;
    }

    /**
     * Sets the modifier flags for this mouse event. Modifier flags indicate which
     * modifier keys (e.g., Shift, Control, Alt, Super) were active during the event.
     *
     * @param modifiers the modifier flags to set, represented as an integer.
     *                  This value will be stored internally as a byte.
     */
    public void setModifiers(int modifiers) {
        this.modifiers = (byte) modifiers;
    }

    /**
     * Retrieves the x-coordinate of the mouse cursor during the event.
     *
     * @return the x-coordinate value as an integer
     */
    public int getX() {
        return x;
    }

    /**
     * Retrieves the y-coordinate of the mouse cursor during the event.
     *
     * @return the y-coordinate value as an integer
     */
    public int getY() {
        return y;
    }

    /**
     * Retrieves the mouse button associated with this event.
     * The button is represented as an integer value, where different
     * integers may correspond to different buttons on the mouse.
     *
     * @return the integer value of the mouse button associated with the event
     */
    public int getButton() {
        return button;
    }

    /**
     * Determines whether the Shift key was active at the time of this event.
     *
     * @return true if the Shift key was pressed, false otherwise
     */
    public boolean isShiftDown() {
        return (modifiers & GLFW_MOD_SHIFT) != 0;
    }

    /**
     * Checks whether the Control (Ctrl) key was active during the event.
     *
     * @return true if the Control key is pressed, false otherwise
     */
    public boolean isCtrlDown() {
        return (modifiers & GLFW_MOD_CONTROL) != 0;
    }

    /**
     * Determines whether the Alt key was active during the event.
     *
     * @return true if the Alt key is pressed, false otherwise.
     */
    public boolean isAltDown() {
        return (modifiers & GLFW_MOD_ALT) != 0;
    }

    /**
     * Determines whether the Super (Command/Windows) key was active during this mouse event.
     *
     * @return true if the Super key is pressed, false otherwise
     */
    public boolean isSuperDown() {
        return (modifiers & GLFW_MOD_SUPER) != 0;
    }

}
