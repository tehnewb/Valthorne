package valthorne;

import org.lwjgl.glfw.*;
import valthorne.event.events.*;
import valthorne.event.listeners.MouseListener;
import valthorne.event.listeners.MouseScrollListener;
import valthorne.graphics.texture.TextureData;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWImage.malloc;

/**
 * <p>
 * The {@code Mouse} class is Valthorne's global static mouse input manager for GLFW-based
 * applications. It centralizes cursor position tracking, mouse button state, scroll wheel
 * input, cursor mode changes, cursor shape changes, custom cursor creation, and event
 * publishing through the engine event system.
 * </p>
 *
 * <p>
 * This class is intentionally non-instantiable and operates entirely through static state.
 * It is designed to be initialized once for the active window and then queried or listened
 * to from anywhere in the engine or game code. Internally, it installs GLFW callbacks for:
 * </p>
 *
 * <ul>
 *     <li>cursor movement</li>
 *     <li>mouse button press and release actions</li>
 *     <li>scroll wheel movement</li>
 * </ul>
 *
 * <p>
 * These callbacks update the cached mouse state and publish reusable event objects such as:
 * </p>
 *
 * <ul>
 *     <li>{@link MouseMoveEvent}</li>
 *     <li>{@link MouseDragEvent}</li>
 *     <li>{@link MousePressEvent}</li>
 *     <li>{@link MouseReleaseEvent}</li>
 *     <li>{@link MouseScrollEvent}</li>
 * </ul>
 *
 * <p>
 * The class also exposes helper methods for:
 * </p>
 *
 * <ul>
 *     <li>checking whether a mouse button is currently down</li>
 *     <li>reading the current cursor position</li>
 *     <li>reading current scroll deltas</li>
 *     <li>switching between GLFW cursor modes</li>
 *     <li>setting a standard system cursor</li>
 *     <li>creating a custom cursor from {@link TextureData}</li>
 *     <li>registering and unregistering listeners</li>
 * </ul>
 *
 * <p>
 * One important detail of this class is that it stores the raw GLFW Y coordinate internally
 * and converts it to Valthorne's bottom-left style coordinate system when reporting public
 * mouse positions and events. This keeps mouse behavior aligned with the rest of the engine's
 * rendering conventions.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Mouse.init();
 *
 * Mouse.addMouseListener(event -> {
 *     if (event instanceof MousePressEvent press && press.getButton() == Mouse.LEFT) {
 *         System.out.println("Left click at: " + press.getX() + ", " + press.getY());
 *     }
 * });
 *
 * Mouse.addScrollListener(event -> {
 *     System.out.println("Scroll: " + event.getXOffset() + ", " + event.getYOffset());
 * });
 *
 * Mouse.setCursor(Mouse.CURSOR_HAND);
 * Mouse.setCursorMode(Mouse.CURSOR_NORMAL);
 *
 * if (Mouse.isButtonDown(Mouse.LEFT)) {
 *     System.out.println("Holding left mouse button");
 * }
 *
 * short mouseX = Mouse.getX();
 * short mouseY = Mouse.getY();
 *
 * Mouse.resetScroll();
 * Mouse.dispose();
 * }</pre>
 *
 * <p>
 * This example demonstrates the full intended use of the class: initialization,
 * listener registration, cursor changes, polling button state and position, clearing
 * scroll state, and shutdown cleanup.
 * </p>
 *
 * @author Albert Beaupre
 * @since October 17th, 2025
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

    private static final MousePressEvent pressEvent = new MousePressEvent(0, 0, 0, 0); // Reusable mouse press event instance
    private static final MouseReleaseEvent releaseEvent = new MouseReleaseEvent(0, 0, 0, 0); // Reusable mouse release event instance
    private static final MouseMoveEvent moveEvent = new MouseMoveEvent(0, 0, 0, 0, 0, 0); // Reusable mouse move event instance
    private static final MouseDragEvent dragEvent = new MouseDragEvent(0, 0, 0, 0, 0, 0); // Reusable mouse drag event instance
    private static final MouseScrollEvent scrollEvent = new MouseScrollEvent(0, 0); // Reusable mouse scroll event instance
    private static GLFWCursorPosCallback cursorPosCallback; // GLFW callback used to track cursor movement
    private static GLFWMouseButtonCallback mouseButtonCallback; // GLFW callback used to track mouse button actions
    private static GLFWScrollCallback scrollCallback; // GLFW callback used to track scroll wheel movement
    private static short x; // Current raw GLFW cursor X position
    private static short y; // Current raw GLFW cursor Y position
    private static byte buttonState; // Bitmask representing currently pressed mouse buttons
    private static byte modifierState; // Bitmask representing the current modifier key state
    private static byte scrollX; // Latest horizontal scroll delta
    private static byte scrollY; // Latest vertical scroll delta

    private static long currentCursor = 0; // Native GLFW cursor handle currently assigned to the window

    /**
     * <p>
     * Private constructor to prevent instantiation.
     * </p>
     *
     * <p>
     * This class is designed to be accessed entirely through static methods and fields.
     * </p>
     */
    private Mouse() {
        // Inaccessible
    }

    /**
     * <p>
     * Initializes GLFW mouse callbacks for cursor movement, mouse button input, and
     * scroll wheel input.
     * </p>
     *
     * <p>
     * Once initialized, this method installs three callbacks on the active window:
     * </p>
     *
     * <ul>
     *     <li>a cursor position callback that publishes {@link MouseMoveEvent} or
     *     {@link MouseDragEvent}</li>
     *     <li>a mouse button callback that publishes {@link MousePressEvent} and
     *     {@link MouseReleaseEvent}</li>
     *     <li>a scroll callback that publishes {@link MouseScrollEvent}</li>
     * </ul>
     *
     * <p>
     * Cursor movement is tracked in GLFW window coordinates internally, but published
     * event Y values are converted into Valthorne's coordinate system by subtracting
     * the raw GLFW Y coordinate from {@link Window#getHeight()}.
     * </p>
     *
     * <p>
     * This method is intended to be called once during engine or window initialization.
     * </p>
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
                event.setY((short) (Window.getHeight() - fromY));
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
     * <p>
     * Sets the mouse cursor to one of GLFW's standard system cursor shapes.
     * </p>
     *
     * <p>
     * If a custom or previously created cursor is already active, it is destroyed
     * before the new cursor is created and assigned. If the window address is invalid,
     * the method returns immediately without doing anything.
     * </p>
     *
     * @param shape one of the supported {@code CURSOR_*} shape constants
     */
    public static void setCursor(int shape) {
        long win = Window.getAddress();
        if (win == 0) return;

        if (currentCursor != 0) glfwDestroyCursor(currentCursor);

        currentCursor = glfwCreateStandardCursor(shape);
        glfwSetCursor(win, currentCursor);
    }

    /**
     * <p>
     * Creates and assigns a custom cursor from the provided {@link TextureData}.
     * </p>
     *
     * <p>
     * The supplied image data is wrapped in a temporary {@link GLFWImage}, then used
     * to create a GLFW cursor. The hotspot coordinates are clamped to the image bounds.
     * The Y hotspot is converted to match this engine's coordinate convention before
     * the cursor is created.
     * </p>
     *
     * <p>
     * Any previously active cursor created by this class is destroyed before the new
     * one is assigned.
     * </p>
     *
     * @param data the texture data used as the cursor image
     * @param hotX the hotspot X coordinate relative to the image
     * @param hotY the hotspot Y coordinate relative to the image
     * @throws NullPointerException  if {@code data} is {@code null}
     * @throws IllegalStateException if {@code data.buffer()} is {@code null}
     * @throws RuntimeException      if GLFW fails to create the cursor from the provided image
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

        hotY = data.height() - hotY - 1;

        if (hotX < 0) hotX = 0;
        if (hotY < 0) hotY = 0;
        if (hotX >= data.width()) hotX = data.width() - 1;
        if (hotY >= data.height()) hotY = data.height() - 1;

        currentCursor = glfwCreateCursor(img, hotX, hotY);
        img.free();

        if (currentCursor == 0) throw new RuntimeException("Failed to create GLFW cursor from TextureData");

        glfwSetCursor(win, currentCursor);
    }

    /**
     * <p>
     * Sets the current GLFW cursor mode for the active window.
     * </p>
     *
     * <p>
     * Supported values are:
     * </p>
     *
     * <ul>
     *     <li>{@link #CURSOR_NORMAL}</li>
     *     <li>{@link #CURSOR_HIDDEN}</li>
     *     <li>{@link #CURSOR_DISABLED}</li>
     * </ul>
     *
     * <p>
     * If the window address is invalid, the method returns immediately. Any unsupported
     * value causes an {@link IllegalArgumentException}.
     * </p>
     *
     * @param mode the GLFW cursor mode constant
     * @throws IllegalArgumentException if the provided mode is not a valid GLFW cursor mode
     */
    public static void setCursorMode(int mode) {
        long win = Window.getAddress();
        if (win == 0) return;

        if (mode != GLFW_CURSOR_NORMAL && mode != GLFW_CURSOR_HIDDEN && mode != GLFW_CURSOR_DISABLED)
            throw new IllegalArgumentException("Invalid cursor mode: " + mode);

        glfwSetInputMode(win, GLFW_CURSOR, mode);
    }

    /**
     * <p>
     * Sets the cursor position in GLFW window coordinates.
     * </p>
     *
     * <p>
     * This delegates directly to {@link GLFW#glfwSetCursorPos(long, double, double)}.
     * The coordinates use GLFW's window-space convention, where the origin is at the
     * top-left. Calling this may trigger the installed cursor position callback.
     * </p>
     *
     * @param x the X position in GLFW window coordinates
     * @param y the Y position in GLFW window coordinates
     */
    public static void setCursorPosition(double x, double y) {
        long win = Window.getAddress();
        if (win == 0) return;
        glfwSetCursorPos(win, x, y);
    }

    /**
     * <p>
     * Registers a {@link MouseListener} to receive mouse event notifications.
     * </p>
     *
     * <p>
     * The listener is subscribed to {@link MouseEvent} and will therefore receive
     * applicable mouse press, release, move, and drag events published through the
     * engine event system.
     * </p>
     *
     * @param listener the listener to register
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public static void addMouseListener(MouseListener listener) {
        if (listener == null) throw new NullPointerException("A null MouseListener cannot be added");
        JGL.subscribe(MouseEvent.class, listener);
    }

    /**
     * <p>
     * Unregisters a previously added {@link MouseListener}.
     * </p>
     *
     * <p>
     * After removal, the listener will no longer receive published mouse events.
     * </p>
     *
     * @param listener the listener to remove
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public static void removeMouseListener(MouseListener listener) {
        if (listener == null) throw new NullPointerException("A null MouseListener cannot be removed");
        JGL.unsubscribe(MouseEvent.class, listener);
    }

    /**
     * <p>
     * Registers a {@link MouseScrollListener} to receive scroll wheel events.
     * </p>
     *
     * <p>
     * The listener is subscribed to {@link MouseScrollEvent} notifications.
     * </p>
     *
     * @param listener the scroll listener to register
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public static void addScrollListener(MouseScrollListener listener) {
        if (listener == null) throw new NullPointerException("A null MouseScrollListener cannot be added");
        JGL.subscribe(MouseScrollEvent.class, listener);
    }

    /**
     * <p>
     * Unregisters a previously added {@link MouseScrollListener}.
     * </p>
     *
     * @param listener the scroll listener to remove
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public static void removeScrollListener(MouseScrollListener listener) {
        if (listener == null) throw new NullPointerException("A null MouseScrollListener cannot be removed");
        JGL.unsubscribe(MouseScrollEvent.class, listener);
    }

    /**
     * <p>
     * Frees all GLFW mouse callbacks and destroys any custom or standard cursor created
     * through this class.
     * </p>
     *
     * <p>
     * This method should be called during engine or window shutdown to avoid leaking
     * native callback or cursor resources.
     * </p>
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
     * <p>
     * Returns the current raw X cursor position.
     * </p>
     *
     * <p>
     * This value is stored directly from GLFW cursor callbacks.
     * </p>
     *
     * @return the current cursor X position
     */
    public static short getX() {
        return x;
    }

    /**
     * <p>
     * Returns the current cursor Y position converted into Valthorne's coordinate system.
     * </p>
     *
     * <p>
     * Internally, GLFW reports Y using a top-left origin. This method converts it to a
     * bottom-left origin by subtracting the internal raw Y value from the current window
     * height.
     * </p>
     *
     * @return the converted cursor Y position
     */
    public static short getY() {
        return (short) (Window.getHeight() - y);
    }

    /**
     * <p>
     * Returns the most recent horizontal scroll delta.
     * </p>
     *
     * @return the horizontal scroll amount
     */
    public static byte getScrollX() {
        return scrollX;
    }

    /**
     * <p>
     * Returns the most recent vertical scroll delta.
     * </p>
     *
     * @return the vertical scroll amount
     */
    public static byte getScrollY() {
        return scrollY;
    }

    /**
     * <p>
     * Resets the cached scroll deltas back to zero.
     * </p>
     *
     * <p>
     * This is useful when scroll input is being consumed per frame and should not
     * accumulate beyond the current processing step.
     * </p>
     */
    static void resetScroll() {
        scrollX = 0;
        scrollY = 0;
    }

    /**
     * <p>
     * Returns whether the specified mouse button is currently held down.
     * </p>
     *
     * <p>
     * Button state is tracked using a bitmask where each bit corresponds to a button
     * index. The method checks whether the requested bit is currently set.
     * </p>
     *
     * @param button the mouse button index
     * @return {@code true} if the specified button is currently down
     */
    public static boolean isButtonDown(int button) {
        return (buttonState & (1 << button)) != 0;
    }

    /**
     * <p>
     * Returns whether the Shift modifier key is currently active during mouse interaction.
     * </p>
     *
     * <p>
     * This method checks the cached modifier bitmask updated by GLFW mouse callbacks.
     * </p>
     *
     * @return {@code true} if Shift is active
     */
    public boolean isShiftDown() {
        return (modifierState & GLFW_MOD_SHIFT) != 0;
    }

    /**
     * <p>
     * Returns whether the Control modifier key is currently active during mouse interaction.
     * </p>
     *
     * @return {@code true} if Control is active
     */
    public boolean isCtrlDown() {
        return (modifierState & GLFW_MOD_CONTROL) != 0;
    }

    /**
     * <p>
     * Returns whether the Alt modifier key is currently active during mouse interaction.
     * </p>
     *
     * @return {@code true} if Alt is active
     */
    public boolean isAltDown() {
        return (modifierState & GLFW_MOD_ALT) != 0;
    }

    /**
     * <p>
     * Returns whether the Super modifier key is currently active during mouse interaction.
     * </p>
     *
     * <p>
     * On most systems, this corresponds to the Windows key or Command key.
     * </p>
     *
     * @return {@code true} if Super is active
     */
    public boolean isSuperDown() {
        return (modifierState & GLFW_MOD_SUPER) != 0;
    }
}