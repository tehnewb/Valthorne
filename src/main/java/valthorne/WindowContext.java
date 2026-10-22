package valthorne;

import org.joml.Matrix4f;
import java.util.BitSet;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Logical window, projection, and input state for one render-thread presentation.
 * Use scopes explicitly around callbacks and offscreen rendering; the primary
 * window remains unchanged. Other threads see no active context. A context owns
 * no GLFW handle or GPU resource. Native input subscriptions capture its identity,
 * allowing an embedded or secondary presentation to publish input independently.
 * Scope changes allocate nothing. Close invalidates late queued work and input.
 */
public final class WindowContext implements AutoCloseable {
    /**
     * Active render-thread view. Only the window thread may replace this reference.
     */
    private static WindowContext active;
    private final Thread owner = Thread.currentThread(); // Thread permitted to enter this context.
    private final BitSet keys = new BitSet(GLFW_KEY_LAST + 1); // Presentation-owned held keys.
    private final Matrix4f matrix = new Matrix4f(); // Presentation-owned projection scratch.
    private final float[] projection = new float[16]; // Cached column-major shader values.
    private int width; // Logical presentation width.
    private int height; // Logical presentation height.
    private long nativeWindow; // Associated secondary handle, or zero for an offscreen presentation.
    private int mouseX; // Cursor X in logical bottom-left coordinates.
    private int mouseY; // Cursor Y in logical bottom-left coordinates.
    private int buttons; // Held mouse-button mask.
    private int modifiers; // Current GLFW modifier mask.
    private double scrollX; // Unconsumed horizontal wheel movement.
    private double scrollY; // Unconsumed vertical wheel movement.
    private boolean focused; // Whether the presentation owns user input.
    private boolean capsLock; // Presentation Caps Lock state.
    private boolean closeRequested; // Logical game-close request, independent of the primary window.
    private volatile boolean closed; // Invalidates queued tasks after host disposal.

    /**
     * Creates logical presentation state without changing the primary window.
     *
     * @param width positive logical width
     * @param height positive logical height
     */
    public WindowContext(int width, int height) { resize(width, height); }

    /**
     * Returns this thread's active presentation, or null for ordinary window work.
     *
     * @return borrowed context, or null
     */
    public static WindowContext current() {
        /*
         * Every installed context belongs to the single window thread. A final
         * owner check makes background workers observe no override without TLS lookup.
         */
        WindowContext context = active;
        return context != null && context.owner == Thread.currentThread() ? context : null;
    }

    /**
     * Replaces render-thread state and returns the previous scope for finally restoration.
     *
     * @param context presentation, or null for the primary window
     * @return preceding context
     */
    public static WindowContext use(WindowContext context) {
        /*
         * Validate only at scope boundaries; high-frequency input getters remain
         * direct primitive reads with no allocation or synchronization.
         */
        if (!Window.isRenderThread()) throw new IllegalStateException("Window contexts require the window/render thread");
        if (context != null && (context.owner != Thread.currentThread() || context.closed)) throw new IllegalStateException("Invalid or closed window context");
        WindowContext previous = active;
        active = context;
        return previous;
    }

    /**
     * Updates dimensions and the default projection before host resize notification.
     *
     * @param width logical width in the supported signed-short input range
     * @param height logical height in the supported signed-short input range
     */
    public void resize(int width, int height) {
        if (width < 1 || height < 1 || width > Short.MAX_VALUE || height > Short.MAX_VALUE) throw new IllegalArgumentException("Invalid presentation size");
        this.width = width; this.height = height;
        matrix.setOrtho(0, width, 0, height, -1, 1).get(projection);
    }

    /**
     * Returns logical width.
     * @return width
     */
    public int width() { return width; }
    /**
     * Returns logical height.
     * @return height
     */
    public int height() { return height; }
    /**
     * Returns the associated native presentation handle.
     * @return handle, or zero for offscreen rendering
     */
    public long nativeWindow() { return nativeWindow; }
    /**
     * Associates a shared-context secondary window without transferring ownership.
     * @param handle handle, or zero when embedded
     */
    public void nativeWindow(long handle) { nativeWindow = handle; }
    /**
     * Returns borrowed shader projection values.
     * @return read-only column-major values
     */
    public float[] projection() { return projection; }
    /**
     * Copies a projection into this presentation's cached shader state.
     * @param values at least sixteen column-major values
     */
    public void projection(float[] values) { matrix.set(values).get(projection); }
    /**
     * Returns logical cursor X.
     * @return bottom-left X
     */
    public int mouseX() { return mouseX; }
    /**
     * Returns logical cursor Y.
     * @return bottom-left Y
     */
    public int mouseY() { return mouseY; }
    /**
     * Updates mapped cursor coordinates.
     * @param x logical X
     * @param y logical Y
     */
    public void pointer(int x, int y) { mouseX = x; mouseY = y; }
    /**
     * Updates held button and modifiers before event publication.
     * @param button valid GLFW mouse button
     * @param down pressed state
     * @param modifiers GLFW modifiers
     */
    public void button(int button, boolean down, int modifiers) {
        if (button < 0 || button > GLFW_MOUSE_BUTTON_LAST) return;
        buttons = down ? buttons | 1 << button : buttons & ~(1 << button);
        this.modifiers = modifiers;
    }
    /**
     * Checks one held mouse button.
     * @param button GLFW button
     * @return held state
     */
    public boolean buttonDown(int button) { return button >= 0 && button <= GLFW_MOUSE_BUTTON_LAST && (buttons & 1 << button) != 0; }
    /**
     * Returns the current mouse-button mask.
     * @return held button bits
     */
    public int buttons() { return buttons; }
    /**
     * Updates held key and modifier state before event publication.
     * @param key GLFW key code
     * @param down held state
     * @param modifiers GLFW modifier bits
     */
    public void key(int key, boolean down, int modifiers) {
        if (key < 0 || key > GLFW_KEY_LAST) return;
        if (key == GLFW_KEY_CAPS_LOCK && down && !keys.get(key)) capsLock = !capsLock;
        keys.set(key, down); this.modifiers = modifiers;
    }
    /**
     * Checks one held key.
     * @param key GLFW key code
     * @return held state
     */
    public boolean keyDown(int key) { return key >= 0 && key <= GLFW_KEY_LAST && keys.get(key); }
    /**
     * Returns current GLFW modifiers.
     * @return modifier mask
     */
    public int modifiers() { return modifiers; }
    /**
     * Returns Caps Lock state for key-to-character helpers.
     * @return Caps Lock state
     */
    public boolean capsLock() { return capsLock; }
    /**
     * Initializes Caps Lock from the host's actual input state.
     * @param enabled host state
     */
    public void capsLock(boolean enabled) { capsLock = enabled; }
    /**
     * Accumulates precise scroll movement.
     * @param x horizontal movement
     * @param y vertical movement
     */
    public void scroll(double x, double y) { scrollX += x; scrollY += y; }
    /**
     * Returns accumulated horizontal scrolling.
     * @return wheel movement
     */
    public double scrollX() { return scrollX; }
    /**
     * Returns accumulated vertical scrolling.
     * @return wheel movement
     */
    public double scrollY() { return scrollY; }
    /**
     * Clears consumed frame scrolling.
     */
    public void resetScroll() { scrollX = 0; scrollY = 0; }
    /**
     * Returns input focus ownership.
     * @return focus state
     */
    public boolean focused() { return focused; }
    /**
     * Changes focus and clears stale held input on loss.
     * @param focused input ownership
     */
    public void focus(boolean focused) { this.focused = focused; if (!focused) clearInput(); }
    /**
     * Clears held input without changing operating-system input state.
     */
    public void clearInput() { keys.clear(); buttons = 0; modifiers = 0; resetScroll(); }
    /**
     * Clears presentation pointer capture state.
     */
    public void cancelButtons() { buttons = 0; resetScroll(); }
    /**
     * Requests game closure without closing the primary editor window.
     */
    public void requestClose() { closeRequested = true; }
    /**
     * Reports a logical game-close request.
     * @return whether the presentation should end
     */
    public boolean closeRequested() { return closeRequested; }
    /**
     * Reports disposal for late queued task guards.
     * @return whether this scope is closed
     */
    public boolean isClosed() { return closed; }

    @Override
    public void close() { clearInput(); focused = false; nativeWindow = 0; closed = true; }
}
