package valthorne.event.events;

import valthorne.Keyboard;
import valthorne.event.Event;

import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER;

/**
 * The KeyEvent class represents a keyboard event and contains information
 * about the key that was pressed or released and any modifier keys that
 * were active during the event. It extends from the {@code Event} class
 * and provides methods to retrieve and manipulate the state of the key
 * and modifiers associated with the event.
 * <p>
 * This class can be used to represent both key press and key release events
 * in a keyboard input handling system. Subclasses can further specify or
 * differentiate the behavior for specific types of keyboard events.
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class KeyEvent extends Event {

    private short key;
    private byte modifiers;

    /**
     * Constructs a new KeyEvent with the specified key code and modifier flags.
     *
     * @param key       the key code associated with this event
     * @param modifiers the modifier flags active during the event (e.g., shift, control)
     */
    public KeyEvent(int key, int modifiers) {
        this.key = (short) key;
        this.modifiers = (byte) modifiers;
    }

    /**
     * Sets the key code for this event.
     *
     * @param key the key code to set, which will be stored as a {@code short} value
     */
    public void setKey(int key) {
        this.key = (short) key;
    }

    /**
     * Sets the modifier flags for this event. Modifier flags indicate which
     * modifier keys (e.g., shift, control, alt, super) were active during the event.
     *
     * @param modifiers the modifier flags to set, represented as an integer.
     *                  This value will be stored internally as a byte.
     */
    public void setModifiers(int modifiers) {
        this.modifiers = (byte) modifiers;
    }

    /**
     * Returns the key code associated with this event. The key code is a
     * numeric representation of the key that was pressed, stored as a
     * {@code short} value.
     *
     * @return the key code for this event
     */
    public short getKey() {
        return key;
    }

    /**
     * Retrieves the character representation of the key associated with this event.
     * The character is determined based on the current keyboard state and the key
     * code stored in this event.
     *
     * @return the character corresponding to the key code for this event
     */
    public char getChar() {
        return Keyboard.getKeyChar(key);
    }

    /**
     * Checks whether the Shift key was active during the event.
     *
     * @return {@code true} if the Shift key is pressed, {@code false} otherwise
     */
    public boolean isShiftDown() {
        return (modifiers & GLFW_MOD_SHIFT) != 0;
    }

    /**
     * Checks whether the Control (Ctrl) key was active during the event.
     *
     * @return {@code true} if the Control key is pressed, {@code false} otherwise.
     */
    public boolean isCtrlDown() {
        return (modifiers & GLFW_MOD_CONTROL) != 0;
    }

    /**
     * Checks whether the Alt key was active during the event.
     *
     * @return {@code true} if the Alt key is pressed, {@code false} otherwise.
     */
    public boolean isAltDown() {
        return (modifiers & GLFW_MOD_ALT) != 0;
    }

    /**
     * Checks whether the Super (Command/Windows) key was active during the event.
     *
     * @return {@code true} if the Super key is pressed, {@code false} otherwise.
     */
    public boolean isSuperDown() {
        return (modifiers & GLFW_MOD_SUPER) != 0;
    }

}
