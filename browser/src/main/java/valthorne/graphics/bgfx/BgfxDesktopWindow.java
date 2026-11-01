package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFXCaps;
import valthorne.browser.BrowserNative;
import valthorne.browser.BrowserRuntime;
import valthorne.graphics.GraphicsBackend;

import static org.lwjgl.bgfx.BGFX.*;
import static valthorne.browser.BrowserOperation.INITIALIZE;

/**
 * Owns the browser bgfx WebAssembly device attached to the persistent canvas.
 * The retained class name preserves the shared engine's binary contract. JGL
 * owns the canvas presentation and input; this wrapper owns only graphics state.
 * Calls belong to the application's browser coroutine, including reset and close.
 */
public final class BgfxDesktopWindow implements AutoCloseable {
    private final Thread owner = Thread.currentThread(); // Application coroutine owning the graphics device.
    private final long handle; // Borrowed browser presentation token.
    private int width; // Current backbuffer width in physical pixels.
    private int height; // Current backbuffer height in physical pixels.
    private int flags; // Retained multisample and presentation policy.
    private int swapInterval; // Requested display refresh divisor, or minus one for browser-adaptive timing.
    private boolean initialized; // Whether native shutdown is required.
    private boolean closed; // Whether this wrapper has released its resources.
    private long captures; // Monotonic synchronous canvas-capture completion count.
    private String capturePath; // Most recently completed capture path.

    /**
     * Attaches the browser renderer with the default single-sample policy.
     * @param handle canvas presentation token
     * @param vsync requested presentation policy
     */
    public BgfxDesktopWindow(long handle, boolean vsync) { this(handle, vsync, 0, GraphicsBackend.WEBGL); }

    /**
     * Attaches the browser renderer with a retained multisample policy.
     * @param handle canvas presentation token
     * @param vsync requested presentation policy
     * @param samples requested sample count
     */
    public BgfxDesktopWindow(long handle, boolean vsync, int samples) { this(handle, vsync, samples, GraphicsBackend.WEBGL); }

    /**
     * Initializes an explicitly selected browser device without desktop fallback.
     * @param handle browser presentation token
     * @param vsync requested presentation policy
     * @param samples requested sample count, zero through sixteen
     * @param backend selected API, which must be WEBGL
     */
    public BgfxDesktopWindow(long handle, boolean vsync, int samples, GraphicsBackend backend) {
        BgfxPlatform.validate(backend);
        if (handle == 0) throw new IllegalArgumentException("A browser canvas is required");
        if (samples < 0 || samples > 16) throw new IllegalArgumentException("Samples must be between zero and sixteen");
        this.handle = handle;
        flags = vsync ? BGFX_RESET_VSYNC : BGFX_RESET_NONE;
        swapInterval = vsync ? 1 : 0;
        flags |= samples <= 1 ? 0 : samples <= 2 ? BGFX_RESET_MSAA_X2 : samples <= 4 ? BGFX_RESET_MSAA_X4 : samples <= 8 ? BGFX_RESET_MSAA_X8 : BGFX_RESET_MSAA_X16;
        BgfxRuntime.claim(this);
        try {
            initialize();
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /**
     * Creates the native WebGL 2 renderer against the already-created canvas.
     */
    private void initialize() {
        width = Math.max(1, BrowserRuntime.pixelWidth());
        height = Math.max(1, BrowserRuntime.pixelHeight());
        BrowserNative.word(0, width);
        BrowserNative.word(1, height);
        BrowserNative.word(2, flags);
        if (BrowserNative.callString(INITIALIZE, BrowserRuntime.canvasSelector()) == 0) throw new IllegalStateException("bgfx could not initialize WebGL 2");
        initialized = true;
        BGFXCaps.invalidate();
    }

    /**
     * Returns the actual browser rendering backend.
     * @return WEBGL
     */
    public GraphicsBackend backend() { return GraphicsBackend.WEBGL; }

    /**
     * Recreates the browser device after releasing all dependent GPU resources.
     * @param backend requested browser backend
     */
    void replaceBackend(GraphicsBackend backend) {
        checkOwner();
        BgfxPlatform.validate(backend);
        if (initialized) bgfx_shutdown();
        initialized = false;
        initialize();
    }

    /**
     * Rejects operations outside the application coroutine or after disposal.
     */
    public void checkOwner() {
        if (closed) throw new IllegalStateException("Browser graphics device is closed");
        if (Thread.currentThread() != owner) throw new IllegalStateException("Use the application graphics coroutine");
    }

    /**
     * Resets the native backbuffer when the canvas's physical dimensions change.
     * @return whether the canvas has a positive drawing area
     */
    public boolean refreshSize() {
        checkOwner();
        int nextWidth = BrowserRuntime.pixelWidth();
        int nextHeight = BrowserRuntime.pixelHeight();
        if (nextWidth < 1 || nextHeight < 1) return false;
        if (width != nextWidth || height != nextHeight) {
            width = nextWidth;
            height = nextHeight;
            bgfx_reset(width, height, flags, BGFX_TEXTURE_FORMAT_COUNT);
        }
        return true;
    }

    /**
     * Updates retained presentation policy; the browser controls actual frame timing.
     * @param enabled requested vertical synchronization policy
     */
    public void setVsync(boolean enabled) {
        setSwapInterval(enabled ? 1 : 0);
    }

    /**
     * Selects a display refresh divisor; adaptive mode follows browser scheduling.
     * @param interval zero, one through three, or minus one for adaptive presentation
     */
    public void setSwapInterval(int interval) {
        checkOwner();
        if (interval < -1 || interval > 3) throw new IllegalArgumentException("Unsupported swap interval: " + interval);
        swapInterval = interval;
        int next = interval != 0 ? flags | BGFX_RESET_VSYNC : flags & ~BGFX_RESET_VSYNC;
        if (next == flags) return;
        flags = next;
        bgfx_reset(width, height, flags, BGFX_TEXTURE_FORMAT_COUNT);
    }

    /**
     * Executes the queued bgfx frame before yielding presentation to the browser.
     * @return completed native frame number
     */
    public int present() {
        checkOwner();
        int frame = bgfx_frame(BGFX_FRAME_NONE);
        if (swapInterval > 1) BrowserRuntime.waitRefreshes(swapInterval);
        return frame;
    }

    /**
     * Reports whether multisample coverage was requested.
     * @return whether the retained multisample policy is enabled
     */
    public boolean isMultisampled() { return (flags & BGFX_RESET_MSAA_MASK) != 0; }

    /**
     * Executes pending commands before a synchronous canvas readback.
     */
    void flush() {
        checkOwner();
        bgfx_frame(BGFX_FRAME_FLUSH);
    }

    /**
     * Completes pending rendering and captures the canvas as PNG data.
     * @param path browser capture name, or empty for synchronization
     * @return prior capture generation
     */
    long requestScreenshot(String path) {
        checkOwner();
        long before = captures;
        flush();
        if (!path.isEmpty()) BrowserRuntime.capture(path);
        capturePath = path;
        captures++;
        return before;
    }

    /**
     * Reports completion of the requested synchronous browser capture.
     * @param path requested capture name
     * @param before generation before capture
     * @return whether the matching capture completed
     */
    boolean screenshotCompleted(String path, long before) { return captures > before && path.equals(capturePath); }

    /**
     * Returns the borrowed presentation token.
     * @return canvas token
     */
    public long handle() { checkOwner(); return handle; }

    /**
     * Returns current physical backbuffer width.
     * @return width in pixels
     */
    public int width() { return width; }

    /**
     * Returns current physical backbuffer height.
     * @return height in pixels
     */
    public int height() { return height; }

    @Override
    public void close() {
        if (closed) return;
        checkOwner();
        if (initialized) bgfx_shutdown();
        initialized = false;
        BgfxRuntime.release(this);
        BGFXCaps.invalidate();
        closed = true;
    }
}
