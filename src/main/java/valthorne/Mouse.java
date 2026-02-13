package valthorne;

import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import valthorne.event.events.*;
import valthorne.event.listeners.MouseListener;
import valthorne.event.listeners.MouseScrollListener;
import valthorne.graphics.texture.TextureData;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWImage.malloc;

/**
 * The {@code Mouse} class provides static utilities for handling mouse input within
 * a GLFW-based application. It tracks cursor movement, button interactions, scroll
 * wheel input, and modifier states (Shift, Ctrl, Alt, Super).
 *
 * <p>This class contains only static behavior and cannot be instantiated, serving
 * as a global mouse manager used throughout the JGL framework.</p>
 *
 * <p>Primary responsibilities include:</p>
 * <ul>
 *     <li>Tracking the current mouse cursor position.</li>
 *     <li>Tracking which mouse buttons are currently held down.</li>
 *     <li>Receiving scroll wheel input on both X and Y axes.</li>
 *     <li>Publishing corresponding mouse events through the JGL event system.</li>
 *     <li>Updating modifier key states during mouse interactions.</li>
 * </ul>
 *
 * <p>To use this system, call {@link #init()} during window initialization and
 * {@link #dispose()} during shutdown.</p>
 *
 * @author Albert Beaupre
 * @since February 16th, 2025
 */
public final class Mouse {

    /**
     * Left mouse button (button index 0).
     */
    public static final int LEFT = 0;

    /**
     * Right mouse button (button index 1).
     */
    public static final int RIGHT = 1;

    /**
     * Middle mouse button (button index 2), usually the scroll wheel click.
     */
    public static final int MIDDLE = 2;

    /**
     * Extra mouse button #3 (typically side/back button).
     */
    public static final int BUTTON_3 = 3;

    /**
     * Extra mouse button #4 (typically side/forward button).
     */
    public static final int BUTTON_4 = 4;

    /**
     * Extra mouse button #5.
     */
    public static final int BUTTON_5 = 5;

    /**
     * Extra mouse button #6.
     */
    public static final int BUTTON_6 = 6;

    /**
     * Extra mouse button #7.
     */
    public static final int BUTTON_7 = 7;

    /**
     * Cursor mode: normal cursor behavior (visible and not captured).
     */
    public static final int CURSOR_NORMAL = GLFW_CURSOR_NORMAL;

    /**
     * Cursor mode: cursor is hidden when over the window.
     */
    public static final int CURSOR_HIDDEN = GLFW_CURSOR_HIDDEN;

    /**
     * Cursor mode: cursor is disabled and captured (useful for FPS camera).
     */
    public static final int CURSOR_DISABLED = GLFW_CURSOR_DISABLED;


    /**
     * Standard arrow cursor shape for general use.
     */
    public static final int CURSOR_ARROW = GLFW_ARROW_CURSOR;

    /**
     * I-beam cursor shape typically used for text editing.
     */
    public static final int CURSOR_IBEAM = GLFW_IBEAM_CURSOR;

    /**
     * Crosshair cursor shape for precise selection.
     */
    public static final int CURSOR_CROSSHAIR = GLFW_CROSSHAIR_CURSOR;

    /**
     * Hand cursor shape indicating clickable elements.
     */
    public static final int CURSOR_HAND = GLFW_HAND_CURSOR;

    /**
     * Horizontal resize cursor shape for width adjustment.
     */
    public static final int CURSOR_HRESIZE = GLFW_HRESIZE_CURSOR;

    /**
     * Vertical resize cursor shape for height adjustment.
     */
    public static final int CURSOR_VRESIZE = GLFW_VRESIZE_CURSOR;
    
    /**
     * Pre-allocated mouse event instances for reuse.
     */
    private static final MousePressEvent pressEvent = new MousePressEvent(0, 0, 0, 0);
    private static final MouseReleaseEvent releaseEvent = new MouseReleaseEvent(0, 0, 0, 0);
    private static final MouseMoveEvent moveEvent = new MouseMoveEvent(0, 0, 0, 0, 0, 0);
    private static final MouseDragEvent dragEvent = new MouseDragEvent(0, 0, 0, 0, 0, 0);
    private static final MouseScrollEvent scrollEvent = new MouseScrollEvent(0, 0);
    /**
     * GLFW callback for handling mouse movement. This callback updates cursor
     * coordinates and publishes a {@link MouseMoveEvent} whenever the user moves
     * the mouse cursor.
     */
    private static GLFWCursorPosCallback cursorPosCallback;
    /**
     * GLFW callback for handling mouse button interactions. This callback receives
     * button press and release actions and publishes {@link MousePressEvent} or
     * {@link MouseReleaseEvent} as appropriate.
     */
    private static GLFWMouseButtonCallback mouseButtonCallback;
    /**
     * GLFW callback for handling scroll wheel input. Publishes {@link MouseScrollEvent}
     * whenever vertical or horizontal scrolling occurs.
     */
    private static GLFWScrollCallback scrollCallback;
    /**
     * The current X position of the mouse cursor relative to the window.
     */
    private static short x;
    /**
     * The current Y position of the mouse cursor relative to the window.
     */
    private static short y;
    /**
     * Bitmask of mouse button states, where each bit corresponds to a mouse button.
     */
    private static byte buttonState;
    /**
     * Bitmask representing the current state of modifier keys (Shift, Control, Alt, Super).
     */
    private static byte modifierState;
    /**
     * Represents the horizontal scroll offset of the mouse wheel.
     */
    private static byte scrollX;
    private static byte scrollY;

    /**
     * Tracks the current cursor type being used in the system.
     */
    private static long currentCursor = 0;

    /**
     * Private constructor prevents instantiation. This class is purely static.
     */
    private Mouse() {
        // Inaccessible
    }

    /**
     * Initializes all GLFW mouse callbacks for movement, button input, and scroll wheel
     * actions. These callbacks publish the appropriate JGL mouse events as input is detected.
     *
     * <p>This method should be called once during window or engine initialization.</p>
     */
    static void init() {
        cursorPosCallback = glfwSetCursorPosCallback(Window.getAddress(), (win, xpos, ypos) -> {
            short fromX = x;
            short fromY = y;
            x = (short) xpos;
            y = (short) ypos;

            MouseMoveEvent event = moveEvent;

            if (buttonState > 0) {
                event = dragEvent;
                event.setX(fromX);
                event.setY(fromY);
                event.setToX(x);
                event.setToY((short) (Window.getHeight() - y));
            } else {
                event.setX(x);
                event.setY((short) (Window.getHeight() - y));
            }
            event.setButton(buttonState);
            event.setModifiers(modifierState);

            JGL.publish(event);
        });

        mouseButtonCallback = glfwSetMouseButtonCallback(Window.getAddress(), (win, button, action, mods) -> {
            if (button < 0 || button > GLFW_MOUSE_BUTTON_LAST) return;

            MouseEvent event = null;

            if (action == GLFW_PRESS) {
                event = pressEvent;
                buttonState |= (byte) (1 << button);
            } else if (action == GLFW_RELEASE) {
                event = releaseEvent;
                buttonState &= (byte) ~(1 << button);
            }

            if (event != null) {
                event.setX(x);
                event.setY((short) (Window.getHeight() - y));
                event.setButton(button);
                event.setModifiers(modifierState);
                JGL.publish(event);
            }

            modifierState = (byte) mods;
        });

        scrollCallback = glfwSetScrollCallback(Window.getAddress(), (win, xoff, yoff) -> {
            scrollX = (byte) xoff;
            scrollY = (byte) yoff;

            scrollEvent.setXOffset(scrollX);
            scrollEvent.setYOffset(scrollY);

            JGL.publish(scrollEvent);
        });
    }

    /**
     * Sets the mouse cursor to a standard system cursor.
     *
     * @param shape One of the CURSOR_* constants
     */
    public static void setCursor(int shape) {
        long win = Window.getAddress();
        if (win == 0) return;

        if (currentCursor != 0)
            glfwDestroyCursor(currentCursor);

        currentCursor = glfwCreateStandardCursor(shape);
        glfwSetCursor(win, currentCursor);
    }

    /**
     * Sets a custom cursor for the current window using the provided texture data and hotspot.
     *
     *
     * @param data The texture data used to create the custom cursor. Must not be {@code null},
     *             and its buffer must not be {@code null}.
     * @param hotX The x-coordinate of the cursor's hotspot in pixels, relative to the top-left of the image.
     *             The value will be clamped to the image's bounds.
     * @param hotY The y-coordinate of the cursor's hotspot in pixels, relative to the top-left of the image.
     *             The value will be clamped to the image's bounds.
     * @throws NullPointerException  If the provided {@code data} is null.
     * @throws IllegalStateException If {@code data.buffer()} is null.
     * @throws RuntimeException      If cursor creation fails due to a problem with the provided texture data.
     */
    public static void setCursor(TextureData data, int hotX, int hotY) {
        long win = Window.getAddress();
        if (win == 0) return;
        if (data == null) throw new NullPointerException("data");
        if (data.buffer() == null) throw new IllegalStateException("TextureData.buffer() is null");

        if (currentCursor != 0) {
            glfwDestroyCursor(currentCursor);
            currentCursor = 0;
        }

        GLFWImage img = malloc();
        img.width(data.width());
        img.height(data.height());
        img.pixels(data.buffer());

        hotY = data.height() - hotY - 1; // I'm making the y coordinate how my system is

        if (hotX < 0) hotX = 0;
        if (hotY < 0) hotY = 0;
        if (hotX >= data.width()) hotX = data.width() - 1;
        if (hotY >= data.height()) hotY = data.height() - 1;

        currentCursor = glfwCreateCursor(img, hotX, hotY);
        img.free();

        if (currentCursor == 0)
            throw new RuntimeException("Failed to create GLFW cursor from TextureData");

        glfwSetCursor(win, currentCursor);
    }

    /**
     * Sets the cursor mode for the current window.
     *
     * <p>Valid values are:</p>
     * <ul>
     *     <li>{@link #CURSOR_NORMAL}</li>
     *     <li>{@link #CURSOR_HIDDEN}</li>
     *     <li>{@link #CURSOR_DISABLED}</li>
     * </ul>
     *
     * @param mode the GLFW cursor mode constant
     */
    public static void setCursorMode(int mode) {
        long win = Window.getAddress();
        if (win == 0) return;

        if (mode != GLFW_CURSOR_NORMAL && mode != GLFW_CURSOR_HIDDEN && mode != GLFW_CURSOR_DISABLED)
            throw new IllegalArgumentException("Invalid cursor mode: " + mode);

        glfwSetInputMode(win, GLFW_CURSOR, mode);
    }

    /**
     * Sets the cursor position in window coordinates.
     *
     * <p>This sets GLFW's internal cursor position, which will trigger the cursor
     * position callback (if installed). Coordinates are in GLFW window space
     * (origin at top-left).</p>
     *
     * @param x the X position in window coordinates
     * @param y the Y position in window coordinates
     */
    public static void setCursorPosition(double x, double y) {
        long win = Window.getAddress();
        if (win == 0) return;
        glfwSetCursorPos(win, x, y);
    }

    /**
     * Adds a {@code MouseListener} to the system to handle mouse-related events.
     * The listener will be subscribed to receive notifications for various mouse events,
     * including movement, button presses/releases, and dragging.
     *
     * @param listener the {@code MouseListener} to be added
     *                 (must not be {@code null})
     * @throws NullPointerException if the provided listener is {@code null}
     */
    public static void addMouseListener(MouseListener listener) {
        if (listener == null) throw new NullPointerException("A null MouseListener cannot be added");
        JGL.subscribe(MouseEvent.class, listener);
    }

    /**
     * Removes a {@code MouseListener} from the system to stop handling mouse-related events.
     * The listener will no longer receive notifications for various mouse events, including
     * movement, button presses/releases, and dragging.
     *
     * @param listener the {@code MouseListener} to be removed.
     *                 Must not be {@code null}.
     * @throws NullPointerException if the provided listener is {@code null}.
     */
    public static void removeMouseListener(MouseListener listener) {
        if (listener == null) throw new NullPointerException("A null MouseListener cannot be removed");
        JGL.unsubscribe(MouseEvent.class, listener);
    }

    /**
     * Adds a {@code MouseScrollListener} to the system to handle mouse scroll events.
     * The listener will be subscribed to receive notifications for scroll actions
     * detected by the mouse.
     *
     * @param listener the {@code MouseScrollListener} to be added (must not be {@code null})
     * @throws NullPointerException if the provided listener is {@code null}
     */
    public static void addScrollListener(MouseScrollListener listener) {
        if (listener == null) throw new NullPointerException("A null MouseScrollListener cannot be added");
        JGL.subscribe(MouseScrollEvent.class, listener);
    }

    /**
     * Frees all GLFW mouse-related callbacks. This should be called upon closing
     * the window or shutting down the engine to avoid native memory leaks.
     */
    static void dispose() {
        if (cursorPosCallback != null) cursorPosCallback.free();
        if (mouseButtonCallback != null) mouseButtonCallback.free();
        if (scrollCallback != null) scrollCallback.free();
        if (currentCursor != 0) {
            glfwDestroyCursor(currentCursor);
            currentCursor = 0;
        }
    }

    /**
     * Returns the current x-coordinate of the mouse cursor.
     *
     * @return the cursor's x position
     */
    public static short getX() {
        return x;
    }

    /**
     * Returns the current y-coordinate of the mouse cursor.
     *
     * @return the cursor's y position
     */
    public static short getY() {
        return (short) (Window.getHeight() - y);
    }

    /**
     * Retrieves the current horizontal scroll value captured by the mouse's scroll wheel.
     * The value is typically used to represent horizontal scrolling activity detected
     * by the mouse.
     *
     * @return the horizontal scroll value as a byte
     */
    public static byte getScrollX() {
        return scrollX;
    }

    /**
     * Retrieves the current vertical scroll value captured by the mouse's scroll wheel.
     * The value is typically used to represent vertical scrolling activity detected
     * by the mouse.
     *
     * @return the vertical scroll value as a byte
     */
    public static byte getScrollY() {
        return scrollY;
    }

    /**
     * Resets the mouse scroll values to their default states. This method sets
     * both the horizontal and vertical scroll values to zero. It is typically
     * used to clear any accumulated scroll buffer to prepare for new scroll input.
     */
    static void resetScroll() {
        scrollX = 0;
        scrollY = 0;
    }

    /**
     * Determines whether a specific mouse button is currently pressed.
     *
     * @param button the button index (e.g., {@link #LEFT}, {@link #RIGHT})
     * @return {@code true} if the button is currently held down
     */
    public static boolean isButtonDown(int button) {
        return (buttonState & (1 << button)) != 0;
    }

    /**
     * Returns whether the Shift modifier key is active during mouse interaction.
     *
     * @return {@code true} if Shift is pressed
     */
    public boolean isShiftDown() {
        return (modifierState & GLFW_MOD_SHIFT) != 0;
    }

    /**
     * Returns whether the Control modifier key is active during mouse interaction.
     *
     * @return {@code true} if Control is pressed
     */
    public boolean isCtrlDown() {
        return (modifierState & GLFW_MOD_CONTROL) != 0;
    }

    /**
     * Returns whether the Alt modifier key is active during mouse interaction.
     *
     * @return {@code true} if Alt is pressed
     */
    public boolean isAltDown() {
        return (modifierState & GLFW_MOD_ALT) != 0;
    }

    /**
     * Returns whether the Super modifier key (Windows key / Command key) is active.
     *
     * @return {@code true} if Super is pressed
     */
    public boolean isSuperDown() {
        return (modifierState & GLFW_MOD_SUPER) != 0;
    }
}