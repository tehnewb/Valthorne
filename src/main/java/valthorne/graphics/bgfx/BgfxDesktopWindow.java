package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFXCaps;
import org.lwjgl.bgfx.BGFXInit;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import valthorne.graphics.GraphicsBackend;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.bgfx.BGFXPlatform.bgfx_render_frame;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window;

/**
 * Owns the synchronous bgfx device attached to Valthorne's persistent no-client-API
 * GLFW window. JGL owns native window creation, input, visibility and destruction;
 * this wrapper owns renderer initialization, presentation, captures and device
 * replacement. Backend changes retain the application window and replace only
 * device-dependent native state and the Windows presentation child.
 *
 * <p>All operations run on the creating graphics thread. Release dependent graphics
 * resources before closing this device and close it before JGL destroys the window.
 * Backbuffer dimensions use framebuffer pixels. Explicit backend selection disables
 * fallback, so unsupported APIs report initialization failure.</p>
 */
public final class BgfxDesktopWindow implements AutoCloseable {
    private GraphicsBackend backend; // Graphics API currently owning the native device.
    private final Thread owner = Thread.currentThread(); // Thread owning all window and rendering operations.
    private final int[] pixelWidth = new int[1]; // Reused framebuffer-size query storage.
    private final int[] pixelHeight = new int[1]; // Reused framebuffer-size query storage.
    private int resetFlags = BGFX_RESET_VSYNC; // Backbuffer reset configuration retained across resizes.
    private final long handle; // Borrowed no-API GLFW window owned by JGL.
    private boolean initialized; // Whether native bgfx shutdown is required.
    private boolean closed; // Whether this renderer wrapper has released its native resources.
    private int width; // Current positive backbuffer width in framebuffer pixels.
    private int height; // Current positive backbuffer height in framebuffer pixels.
    private BgfxCallbacks callbacks; // Owned native diagnostic/capture callbacks, retained through shutdown.
    private BgfxWindowsSurface surface; // Replaceable Windows presentation child; null on other platforms.

    /**
     * Attaches bgfx to a JGL-owned no-API window without taking GLFW ownership.
     * Close this wrapper before destroying that window.
     *
     * @param handle existing GLFW_NO_API window
     * @param vsync whether presentation waits for vertical synchronization
     */
    public BgfxDesktopWindow(long handle, boolean vsync) {
        this(handle, vsync, 0, GraphicsBackend.OPENGL);
    }

    /**
     * Attaches to a JGL-owned window with a retained multisample reset policy.
     * Requested counts round up to the next supported power of two through sixteen.
     *
     * @param handle existing GLFW_NO_API window
     * @param vsync whether to synchronize presentation
     * @param samples requested multisample count, zero through sixteen
     */
    public BgfxDesktopWindow(long handle, boolean vsync, int samples) {
        this(handle, vsync, samples, GraphicsBackend.OPENGL);
    }

    /**
     * Attaches the selected API to a platform-owned window.
     *
     * @param handle GLFW_NO_API window
     * @param vsync vertical synchronization policy
     * @param samples requested multisample count
     * @param backend explicit desktop renderer
     */
    public BgfxDesktopWindow(long handle, boolean vsync, int samples, GraphicsBackend backend) {
        BgfxPlatform.validate(backend);
        this.backend = backend;
        if (handle == 0 || glfwGetWindowAttrib(handle, GLFW_CLIENT_API) != GLFW_NO_API)
            throw new IllegalArgumentException("A GLFW no-API window is required");
        if (samples < 0 || samples > 16) throw new IllegalArgumentException("Samples must be between zero and sixteen");
        BgfxRuntime.claim(this);
        this.handle = handle;
        resetFlags = vsync ? BGFX_RESET_VSYNC : BGFX_RESET_NONE;
        resetFlags |= samples <= 1 ? BGFX_RESET_NONE : samples <= 2 ? BGFX_RESET_MSAA_X2 : samples <= 4 ? BGFX_RESET_MSAA_X4 : samples <= 8 ? BGFX_RESET_MSAA_X8 : BGFX_RESET_MSAA_X16;
        try {
            initializeRenderer();
        } catch (RuntimeException | Error failure) {
            release();
            throw failure;
        }
    }

    /**
     * Initializes the native renderer against the already-created no-API window.
     */
    private void initializeRenderer() {
        callbacks = new BgfxCallbacks();
        glfwGetFramebufferSize(handle, pixelWidth, pixelHeight);
        this.width = Math.max(1, pixelWidth[0]);
        this.height = Math.max(1, pixelHeight[0]);
        if (Platform.get() == Platform.WINDOWS)
            surface = new BgfxWindowsSurface(glfwGetWin32Window(handle), this.width, this.height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            BGFXInit init = BGFXInit.calloc(stack);
            bgfx_init_ctor(init);
            init.type(backend.rendererType());
            init.fallback(false);
            init.callback(callbacks.callback());
            BgfxPlatform.configure(handle, init.platformData());
            if (surface != null) init.platformData().nwh(surface.handle());
            init.resolution()
                .width(this.width)
                .height(this.height)
                .reset(resetFlags);
            bgfx_render_frame(0);
            if (!bgfx_init(init)) {
                callbacks.checkFailure();
                throw new IllegalStateException("bgfx initialization failed for " + backend);
            }
            initialized = true;
        }
        BGFXCaps caps = bgfx_get_caps();
        if (caps == null || caps.rendererType() != backend.rendererType())
            throw new IllegalStateException("bgfx did not select the requested renderer " + backend + "");
        if (glfwGetWindowAttrib(handle, GLFW_CLIENT_API) != GLFW_NO_API || glfwGetCurrentContext() != 0)
            throw new IllegalStateException("The bgfx desktop path must use a GLFW no-client-API window");
    }

    /**
     * Returns the explicit API owning this window's graphics resources.
     *
     * @return selected graphics backend
     */
    public GraphicsBackend backend() {
        return backend;
    }

    /**
     * Replaces only the graphics device, retaining the native window and input.
     * All dependent native resources must already be released by the registry.
     *
     * @param backend replacement desktop renderer
     */
    void replaceBackend(GraphicsBackend backend) {
        checkOwner();
        BgfxPlatform.validate(backend);
        shutdownRenderer();
        this.backend = backend;
        try {
            initializeRenderer();
        } catch (RuntimeException | Error failure) {
            shutdownRenderer();
            throw failure;
        }
    }

    /**
     * Releases the native device and callbacks while preserving GLFW ownership.
     */
    private void shutdownRenderer() {
        if (initialized) {
            bgfx_shutdown();
            initialized = false;
        }
        if (callbacks != null) {
            callbacks.close();
            callbacks = null;
        }
        if (surface != null) {
            surface.close();
            surface = null;
        }
    }

    /**
     * Checks lifetime and thread ownership at rendering API boundaries.
     */
    public void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Use the creating render thread");
        if (closed) throw new IllegalStateException("The bgfx window is closed");
    }

    /**
     * Refreshes physical backbuffer dimensions after the application's event poll.
     *
     * @return false for a minimized zero-area framebuffer
     */
    public boolean refreshSize() {
        checkOwner();
        glfwGetFramebufferSize(handle, pixelWidth, pixelHeight);
        if (pixelWidth[0] < 1 || pixelHeight[0] < 1) {
            glfwWaitEventsTimeout(0.05);
            return false;
        }
        if (width != pixelWidth[0] || height != pixelHeight[0]) {
            width = pixelWidth[0];
            height = pixelHeight[0];
            if (surface != null) surface.resize(width, height);
            bgfx_reset(width, height, resetFlags, BGFX_TEXTURE_FORMAT_COUNT);
        }
        return true;
    }

    /**
     * Changes vertical synchronization without changing the rendering backend.
     *
     * @param enabled whether to wait for presentation synchronization
     */
    public void setVsync(boolean enabled) {
        checkOwner();
        resetFlags = (resetFlags & ~BGFX_RESET_VSYNC) | (enabled ? BGFX_RESET_VSYNC : BGFX_RESET_NONE);
        bgfx_reset(width, height, resetFlags, BGFX_TEXTURE_FORMAT_COUNT);
    }

    /**
     * Executes queued commands and presents through bgfx.
     *
     * @return bgfx frame number for asynchronous readback completion
     */
    public int present() {
        checkOwner();
        int frame = bgfx_frame(BGFX_FRAME_NONE);
        callbacks.checkFailure();
        return frame;
    }

    /**
     * Reports whether the native window backbuffer uses multisample coverage.
     *
     * @return whether at least two samples were requested
     */
    public boolean isMultisampled() {
        return (resetFlags & BGFX_RESET_MSAA_MASK) != 0;
    }

    /**
     * Executes queued commands without swapping the native backbuffer.
     */
    void flush() {
        checkOwner();
        bgfx_frame(BGFX_FRAME_FLUSH);
        callbacks.checkFailure();
    }

    /**
     * Schedules a PNG capture, or a readback synchronization when the path is empty.
     *
     * @param path PNG output path or empty synchronization request
     * @return completion generation before scheduling
     */
    long requestScreenshot(String path) {
        checkOwner();
        long before = callbacks.screenshots();
        bgfx_request_screen_shot(BGFX_INVALID_HANDLE, path.isEmpty() ? BgfxCallbacks.SYNCHRONIZE_PATH : path);
        return before;
    }

    /**
     * Checks completion of one scheduled native backbuffer capture.
     *
     * @param path scheduled path
     * @param before completion generation before scheduling
     * @return whether the named request completed
     */
    boolean screenshotCompleted(String path, long before) {
        callbacks.checkFailure();
        return callbacks.completed(path, before);
    }

    /**
     * Returns the native window for input integration, without transferring ownership.
     *
     * @return GLFW window handle
     */
    public long handle() {
        checkOwner();
        return handle;
    }

    /**
     * Returns the current backbuffer width.
     *
     * @return framebuffer width in pixels
     */
    public int width() {
        return width;
    }

    /**
     * Returns the current backbuffer height.
     *
     * @return framebuffer height in pixels
     */
    public int height() {
        return height;
    }

    /**
     * Releases initialized native systems in reverse ownership order.
     */
    private void release() {
        shutdownRenderer();
        BgfxRuntime.release(this);
        closed = true;
    }

    @Override
    public void close() {
        if (closed) return;
        checkOwner();
        release();
    }
}
