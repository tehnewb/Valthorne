package valthorne.event.events;

import valthorne.event.Event;
import valthorne.event.EventType;
import valthorne.event.EventTypes;

import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER;

/**
 * Shared payload superclass for mouse-related events.
 *
 * <p>
 * Java inheritance here is strictly for payload/API reuse; publication routing is controlled by
 * the numeric {@link EventType} passed to {@link Event}. A directly-created {@code MouseEvent}
 * routes through {@link EventTypes#MOUSE}, while subclasses use their own concrete descriptors.
 * </p>
 *
 * <p>
 * The payload is mutable to allow object reuse. An instance must have exclusive ownership while it
 * is being published.
 * </p>
 *
 * <p>Button codes and modifier masks are narrowed to bytes without validation; X/Y
 * remain integers in the producer's cursor coordinate space. Setters change payload
 * only and preserve the event's route and consumption state.</p>
 *
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class MouseEvent extends Event {

    private int x; // Starting or current cursor X coordinate from the producer.
    private int y; // Starting or current cursor Y coordinate from the producer.
    private byte button; // Button code narrowed to signed-byte storage.
    private byte modifiers; // Modifier bits narrowed to byte storage.

    /**
     * Creates a raw mouse event routed as {@link EventTypes#MOUSE}.
     *
     * @param button    mouse button code
     * @param modifiers modifier bit mask
     * @param x         cursor X coordinate
     * @param y         cursor Y coordinate
     */
    public MouseEvent(int button, int modifiers, int x, int y) {
        this(EventTypes.MOUSE, button, modifiers, x, y);
    }

    /**
     * Constructor used by subclasses to choose a concrete numeric route.
     *
     * @param type      concrete event type
     * @param button    mouse button code
     * @param modifiers modifier bit mask
     * @param x         cursor X coordinate
     * @param y         cursor Y coordinate
     */
    protected MouseEvent(EventType<?> type, int button, int modifiers, int x, int y) {
        super(type);
        this.x = x;
        this.y = y;
        this.button = (byte) button;
        this.modifiers = (byte) modifiers;
    }

    /**
     * Replaces the complete base mouse payload for reuse without resetting consumption.
     * Button and modifier values are narrowed to bytes; coordinates remain integers.
     *
     * @param button mouse button code
     * @param modifiers modifier mask
     * @param x cursor X coordinate
     * @param y cursor Y coordinate
     *
     * @return this event
     */
    public MouseEvent set(int button, int modifiers, int x, int y) {
        this.button = (byte) button;
        this.modifiers = (byte) modifiers;
        this.x = x;
        this.y = y;
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
     * @return cursor X coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param x new cursor X coordinate
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * Returns the current stored payload component without querying native input or
     * window state. Copy the value if it is needed after this reusable event is updated.
     *
     * @return cursor Y coordinate
     */
    public int getY() {
        return y;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param y new cursor Y coordinate
     */
    public void setY(int y) {
        this.y = y;
    }

    /**
     * Returns the current stored payload component without querying native input or
     * window state. Copy the value if it is needed after this reusable event is updated.
     *
     * @return mouse button code
     */
    public int getButton() {
        return button;
    }

    /**
     * Replaces this payload component without changing other values, the numeric route,
     * or consumption state. Storage uses the declared field type without range validation.
     *
     * @param button mouse button code
     */
    public void setButton(int button) {
        this.button = (byte) button;
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
