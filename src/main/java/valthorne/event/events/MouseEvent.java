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
 * @author Albert Beaupre
 * @since December 18th, 2025
 */
public class MouseEvent extends Event {

    private int x;
    private int y;
    private byte button;
    private byte modifiers;

    /**
     * Creates a raw mouse event routed as {@link EventTypes#MOUSE}.
     *
     * @param button mouse button code
     * @param modifiers modifier bit mask
     * @param x cursor X coordinate
     * @param y cursor Y coordinate
     */
    public MouseEvent(int button, int modifiers, int x, int y) {
        this(EventTypes.MOUSE, button, modifiers, x, y);
    }

    /**
     * Constructor used by subclasses to choose a concrete numeric route.
     *
     * @param type concrete event type
     * @param button mouse button code
     * @param modifiers modifier bit mask
     * @param x cursor X coordinate
     * @param y cursor Y coordinate
     */
    protected MouseEvent(EventType<?> type, int button, int modifiers, int x, int y) {
        super(type);
        this.x = x;
        this.y = y;
        this.button = (byte) button;
        this.modifiers = (byte) modifiers;
    }

    /**
     * Replaces the complete base mouse payload for reuse.
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

    /** @param modifiers modifier bit mask */
    public void setModifiers(int modifiers) {
        this.modifiers = (byte) modifiers;
    }

    /** @return cursor X coordinate */
    public int getX() {
        return x;
    }

    /** @param x new cursor X coordinate */
    public void setX(int x) {
        this.x = x;
    }

    /** @return cursor Y coordinate */
    public int getY() {
        return y;
    }

    /** @param y new cursor Y coordinate */
    public void setY(int y) {
        this.y = y;
    }

    /** @return mouse button code */
    public int getButton() {
        return button;
    }

    /** @param button mouse button code */
    public void setButton(int button) {
        this.button = (byte) button;
    }

    /** @return whether Shift was active */
    public boolean isShiftDown() {
        return (modifiers & GLFW_MOD_SHIFT) != 0;
    }

    /** @return whether Control was active */
    public boolean isCtrlDown() {
        return (modifiers & GLFW_MOD_CONTROL) != 0;
    }

    /** @return whether Alt was active */
    public boolean isAltDown() {
        return (modifiers & GLFW_MOD_ALT) != 0;
    }

    /** @return whether Super/Command/Windows was active */
    public boolean isSuperDown() {
        return (modifiers & GLFW_MOD_SUPER) != 0;
    }
}
