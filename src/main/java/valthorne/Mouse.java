package valthorne;

import org.lwjgl.glfw.*;
import valthorne.event.EventTypes;
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

    /**
     * Northwest/southeast diagonal resize cursor for top-left and bottom-right corners.
     */
    public static final int CURSOR_RESIZE_NWSE = GLFW_RESIZE_NWSE_CURSOR;

    /**
     * Northeast/southwest diagonal resize cursor for top-right and bottom-left corners.
     */
    public static final int CURSOR_RESIZE_NESW = GLFW_RESIZE_NESW_CURSOR;

    /**
     * Reusable mouse press event instance. Consumers must copy values for a historical snapshot.
     */
    private static final MousePressEvent pressEvent = new MousePressEvent(0, 0, 0, 0); // Reusable mouse press event instance
    /**
     * Reusable mouse release event instance. Consumers must copy values for a historical snapshot.
     */
    private static final MouseReleaseEvent releaseEvent = new MouseReleaseEvent(0, 0, 0, 0); // Reusable mouse release event instance
    /**
     * Reusable mouse move event instance. Consumers must copy values for a historical snapshot.
     */
    private static final MouseMoveEvent moveEvent = new MouseMoveEvent(0, 0, 0, 0, 0, 0); // Reusable mouse move event instance
    /**
     * Reusable mouse drag event instance. Consumers must copy values for a historical snapshot.
     */
    private static final MouseDragEvent dragEvent = new MouseDragEvent(0, 0, 0, 0, 0, 0); // Reusable mouse drag event instance
    /**
     * Reusable mouse scroll event instance. Consumers must copy values for a historical snapshot.
     */
    private static final MouseScrollEvent scrollEvent = new MouseScrollEvent(0, 0); // Reusable mouse scroll event instance
    /**
     * GLFW callback used to track cursor movement.
     */
    private static GLFWCursorPosCallback cursorPosCallback; // GLFW callback used to track cursor movement
    /**
     * GLFW callback used to track mouse button actions.
     */
    private static GLFWMouseButtonCallback mouseButtonCallback; // GLFW callback used to track mouse button actions
    /**
     * GLFW callback used to track scroll wheel movement.
     */
    private static GLFWScrollCallback scrollCallback; // GLFW callback used to track scroll wheel movement
    /**
     * Current raw GLFW cursor X position.
     */
    private static short x; // Current raw GLFW cursor X position
    /**
     * Current raw GLFW cursor Y position.
     */
    private static short y; // Current raw GLFW cursor Y position
    /**
     * Bitmask representing currently pressed mouse buttons.
     */
    private static byte buttonState; // Bitmask representing currently pressed mouse buttons
    /**
     * Bitmask representing the current modifier key state.
     */
    private static byte modifierState; // Bitmask representing the current modifier key state
    /**
     * Latest horizontal scroll delta.
     */
    private static byte scrollX; // Latest horizontal scroll delta
    /**
     * Latest vertical scroll delta.
     */
    private static byte scrollY; // Latest vertical scroll delta

    /**
     * Native GLFW cursor handle currently assigned to the window.
     */
    private static long currentCursor = 0; // Native GLFW cursor handle currently assigned to the window

    /**
     * Application-selected standard shape, or zero when a custom image cursor is selected.
     */
    private static int currentCursorShape = CURSOR_ARROW;

    /**
     * Temporary UI cursor handle, owned independently so the application cursor survives.
     */
    private static long overrideCursor;

    /**
     * Identity token authorized to release the current temporary cursor.
     */
    private static Object cursorOwner;

    /**
     * Standard shape of the active override, used to avoid recreating it on every frame.
     */
    private static int overrideCursorShape;

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
        dispose();
        resetState();

        cursorPosCallback = GLFWCursorPosCallback.create((win, xpos, ypos) -> {
            short fromX = x;
            short fromY = y;
            x = (short) xpos;
            y = (short) ypos;

            if (buttonState != 0) {
                // Drag payloads use the same button code as press/release, not the state mask.
                // Publish each held button so a second press cannot steal an existing drag.
                int heldButtons = Byte.toUnsignedInt(buttonState);
                for (int button = 0; button <= GLFW_MOUSE_BUTTON_LAST; button++) {
                    if ((heldButtons & (1 << button)) == 0) continue;
                    dragEvent.set(button, modifierState, fromX, Window.getHeight() - fromY, x, Window.getHeight() - y);
                    JGL.publish(dragEvent);
                }
            } else {
                moveEvent.set(-1, modifierState, fromX, Window.getHeight() - fromY, x, Window.getHeight() - y);
                JGL.publish(moveEvent);
            }
        });
        glfwSetCursorPosCallback(Window.getAddress(), cursorPosCallback);

        mouseButtonCallback = GLFWMouseButtonCallback.create((win, button, action, mods) -> {
            if (button < 0 || button > GLFW_MOUSE_BUTTON_LAST) return;

            MouseEvent event = null;

            if (action == GLFW_PRESS) {
                event = pressEvent;
                buttonState |= (byte) (1 << button);
            } else if (action == GLFW_RELEASE) {
                event = releaseEvent;
                buttonState &= (byte) ~(1 << button);
            }

            modifierState = (byte) mods;
            if (event != null) {
                event.setX(x);
                event.setY((short) (Window.getHeight() - y));
                event.setButton(button);
                event.setModifiers(modifierState);
                JGL.publish(event);
            }

        });
        glfwSetMouseButtonCallback(Window.getAddress(), mouseButtonCallback);

        scrollCallback = GLFWScrollCallback.create((win, xoff, yoff) -> {
            scrollX = (byte) xoff;
            scrollY = (byte) yoff;

            scrollEvent.setPreciseOffsets((float) xoff, (float) yoff);

            JGL.publish(scrollEvent);
        });
        glfwSetScrollCallback(Window.getAddress(), scrollCallback);
    }

    /**
     * <p>
     * Sets the mouse cursor to one of GLFW's standard system cursor shapes.
     * </p>
     *
     * <p>
     * Replaces the application's previously selected cursor. A temporary UI override
     * remains visible until its owner releases it, at which point this new choice is
     * restored. If the window address is invalid, the method returns without changes.
     * </p>
     *
     * @param shape one of the supported {@code CURSOR_*} shape constants
     */
    public static void setCursor(int shape) {
        long win = Window.getAddress();
        if (win == 0) return;

        if (currentCursor != 0) glfwDestroyCursor(currentCursor);

        currentCursor = glfwCreateStandardCursor(shape);
        currentCursorShape = shape;
        glfwSetCursor(win, overrideCursor != 0 ? overrideCursor : currentCursor);
    }

    /**
     * Temporarily displays a standard cursor while retaining the application's selected
     * standard or custom image cursor. Identical shapes reuse the native cursor; changing
     * owner transfers release responsibility without destroying the application cursor.
     * @param owner nonnull identity token used later to release this override
     * @param shape standard cursor shape such as CURSOR_HRESIZE or CURSOR_RESIZE_NWSE
     */
    public static void overrideCursor(Object owner, int shape) {
        java.util.Objects.requireNonNull(owner);
        long win = Window.getAddress(); if (win == 0) return;
        if (overrideCursor != 0 && overrideCursorShape == shape) { cursorOwner = owner; return; }
        long next = glfwCreateStandardCursor(shape);
        if (next == 0) return;
        glfwSetCursor(win, next);
        if (overrideCursor != 0) glfwDestroyCursor(overrideCursor);
        overrideCursor = next; overrideCursorShape = shape; cursorOwner = owner;
    }

    /**
     * Restores the application's latest cursor only when the supplied owner still owns
     * the override. Stale releases cannot clear a newer UI owner's cursor.
     * @param owner identity token supplied to overrideCursor
     */
    public static void clearCursorOverride(Object owner) {
        if (cursorOwner != owner || overrideCursor == 0) return;
        long win = Window.getAddress(); if (win != 0) glfwSetCursor(win, currentCursor);
        glfwDestroyCursor(overrideCursor); overrideCursor = 0; cursorOwner = null; overrideCursorShape = 0;
    }

    /**
     * Reads the selected standard cursor, including any temporary UI override.
     * @return standard CURSOR_* shape, or zero for an application custom image cursor
     */
    public static int getCursorShape() { return overrideCursor != 0 ? overrideCursorShape : currentCursorShape; }

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
     * The previous application cursor is destroyed before the new one is assigned.
     * A temporary UI override remains visible and retains this image cursor for restoration.
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

        currentCursorShape = 0;
        glfwSetCursor(win, overrideCursor != 0 ? overrideCursor : currentCursor);
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
     * Requests unaccelerated relative mouse motion on supported desktop platforms.
     * GLFW applies this setting while the cursor is {@link #CURSOR_DISABLED}.
     * Call on the window thread after selecting the desired cursor mode. Missing
     * windows and unsupported backends ignore the request; browser relative input
     * remains controlled by the browser's pointer-lock implementation.
     *
     * @param enabled whether raw desktop motion should be enabled
     */
    public static void setRawMouseMotion(boolean enabled) {
        long win = Window.getAddress();
        if (win != 0 && glfwRawMouseMotionSupported())
            glfwSetInputMode(win, GLFW_RAW_MOUSE_MOTION, enabled ? GLFW_TRUE : GLFW_FALSE);
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
     * The listener is subscribed to the concrete mouse action routes and will therefore
     * receive mouse press, release, move, and drag events published through the engine
     * event system.
     * </p>
     *
     * @param listener the listener to register
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public static void addMouseListener(MouseListener listener) {
        if (listener == null) throw new NullPointerException("A null MouseListener cannot be added");
        JGL.subscribe(EventTypes.MOUSE_MOVE, listener);
        JGL.subscribe(EventTypes.MOUSE_DRAG, listener);
        JGL.subscribe(EventTypes.MOUSE_PRESS, listener);
        JGL.subscribe(EventTypes.MOUSE_RELEASE, listener);
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
        JGL.unsubscribe(EventTypes.MOUSE_MOVE, listener);
        JGL.unsubscribe(EventTypes.MOUSE_DRAG, listener);
        JGL.unsubscribe(EventTypes.MOUSE_PRESS, listener);
        JGL.unsubscribe(EventTypes.MOUSE_RELEASE, listener);
    }

    /**
     * <p>
     * Registers a {@link MouseScrollListener} to receive scroll wheel events.
     * </p>
     *
     * <p>
     * The listener is subscribed to {@link EventTypes#MOUSE_SCROLL} notifications.
     * </p>
     *
     * @param listener the scroll listener to register
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public static void addScrollListener(MouseScrollListener listener) {
        if (listener == null) throw new NullPointerException("A null MouseScrollListener cannot be added");
        JGL.subscribe(EventTypes.MOUSE_SCROLL, listener);
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
        JGL.unsubscribe(EventTypes.MOUSE_SCROLL, listener);
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
        if (overrideCursor != 0) {
            glfwDestroyCursor(overrideCursor); overrideCursor = 0; cursorOwner = null; overrideCursorShape = 0;
        }
        if (cursorPosCallback != null) {
            if (Window.getAddress() != 0) glfwSetCursorPosCallback(Window.getAddress(), null);
            cursorPosCallback.free();
            cursorPosCallback = null;
        }
        if (mouseButtonCallback != null) {
            if (Window.getAddress() != 0) glfwSetMouseButtonCallback(Window.getAddress(), null);
            mouseButtonCallback.free();
            mouseButtonCallback = null;
        }
        if (scrollCallback != null) {
            if (Window.getAddress() != 0) glfwSetScrollCallback(Window.getAddress(), null);
            scrollCallback.free();
            scrollCallback = null;
        }
        if (currentCursor != 0) {
            glfwDestroyCursor(currentCursor);
            currentCursor = 0;
        }
        resetState();
        currentCursorShape = CURSOR_ARROW;
    }

    /**
     * Cancel held input while preserving cursor position across window activation changes.
     */
    static void cancelButtons() {
        buttonState = 0;
        modifierState = 0;
        scrollX = scrollY = 0;
    }

    /**
     * Clears cached cursor coordinates, pressed-button/modifier masks, and scroll
     * deltas without publishing events. Native callbacks and cursor resources are
     * managed separately by disposal.
     */
    static void resetState() {
        x = 0;
        y = 0;
        buttonState = 0;
        modifierState = 0;
        scrollX = 0;
        scrollY = 0;
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
