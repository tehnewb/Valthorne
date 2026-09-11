package valthorne.event.events;

import valthorne.Keyboard;
import valthorne.event.Event;
import valthorne.event.EventType;
import valthorne.event.EventTypes;

import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER;

/**
 * Keyboard event payload containing a key code and modifier bit mask.
 *
 * <p>
 * This class remains the shared payload superclass for press and release events, but inheritance no
 * longer controls listener routing. A directly-created {@code KeyEvent} uses {@link EventTypes#KEY};
 * {@link KeyPressEvent} and {@link KeyReleaseEvent} pass their own concrete type descriptors through
 * the protected constructor.
 * </p>
 *
 * <p>
 * Instances are mutable to support reuse. They must not be modified while dispatch is in progress
 * and must not be shared concurrently between publishing threads.
 * </p>
 *
 * <p>Key codes are narrowed to signed shorts and modifier masks to bytes without range
 * validation. Payload setters do not reset consumption or change the numeric route.
 * Character lookup is a key-code mapping, not Unicode text-input composition.</p>
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class KeyEvent extends Event {

    private short key; // Key code narrowed to signed-short storage.
    private byte modifiers; // Modifier bits narrowed to byte storage.

    /**
     * Creates a raw key event routed as {@link EventTypes#KEY}.
     *
     * @param key       GLFW key code
     * @param modifiers GLFW modifier bit mask
     */
    public KeyEvent(int key, int modifiers) {
        this(EventTypes.KEY, key, modifiers);
    }

    /**
     * Constructor used by concrete key-event subclasses to select their own numeric route.
     *
     * @param type      concrete event type
     * @param key       GLFW key code
     * @param modifiers GLFW modifier bit mask
     */
    protected KeyEvent(EventType<?> type, int key, int modifiers) {
        super(type);
        this.key = (short) key;
        this.modifiers = (byte) modifiers;
    }

    /**
     * Replaces both payload fields for object reuse.
     *
     * @param key       GLFW key code
     * @param modifiers modifier mask
     * @return this event
     */
    public KeyEvent set(int key, int modifiers) {
        this.key = (short) key;
        this.modifiers = (byte) modifiers;
        return this;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param modifiers modifier bit mask
     */
    public void setModifiers(int modifiers) {
        this.modifiers = (byte) modifiers;
    }

    /**
     * Returns the current stored payload component without querying native input or
     * window state. Copy the value if it is needed after this reusable event is updated.
     *
     * @return key code
     */
    public short getKey() {
        return key;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param key key code
     */
    public void setKey(int key) {
        this.key = (short) key;
    }

    /**
     * Resolves the current key code to the application's character representation.
     *
     * @return character corresponding to the current key code
     */
    public char getChar() {
        return Keyboard.getKeyChar(key);
    }

    /**
     * Tests the stored modifier bit without polling current keyboard state.
     * The result describes this event's payload at the time it is read.
     *
     * @return whether Shift was active
     */
    public boolean isShiftDown() {
        return (modifiers & GLFW_MOD_SHIFT) != 0;
    }

    /**
     * Tests the stored modifier bit without polling current keyboard state.
     * The result describes this event's payload at the time it is read.
     *
     * @return whether Control was active
     */
    public boolean isCtrlDown() {
        return (modifiers & GLFW_MOD_CONTROL) != 0;
    }

    /**
     * Tests the stored modifier bit without polling current keyboard state.
     * The result describes this event's payload at the time it is read.
     *
     * @return whether Alt was active
     */
    public boolean isAltDown() {
        return (modifiers & GLFW_MOD_ALT) != 0;
    }

    /**
     * Tests the stored modifier bit without polling current keyboard state.
     * The result describes this event's payload at the time it is read.
     *
     * @return whether Super/Command/Windows was active
     */
    public boolean isSuperDown() {
        return (modifiers & GLFW_MOD_SUPER) != 0;
    }
}
