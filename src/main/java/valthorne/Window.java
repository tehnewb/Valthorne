package valthorne;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import org.joml.Matrix4f;
import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;
import valthorne.event.EventTypes;
import valthorne.event.events.FileDropEvent;
import valthorne.event.events.WindowFocusEvent;
import valthorne.event.events.WindowResizeEvent;
import valthorne.event.events.FramebufferResizeEvent;
import valthorne.event.listeners.WindowResizeListener;
import valthorne.graphics.Color;
import valthorne.graphics.GraphicsBackend;
import valthorne.graphics.GraphicsCapabilities;
import valthorne.graphics.GraphicsRecoveryException;
import valthorne.graphics.ImmediateTextureRenderer;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxResources;
import valthorne.graphics.texture.TextureData;
import valthorne.math.geometry.Dimensional;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GLFW window wrapper for Valthorne.
 *
 * <h2>Example</h2>
 * <pre>{@code
 *
 * // These are called inside your Application.init() method
 * Window.setSwapInterval(SwapInterval.ON); // vsync
 * Window.setResizable(true);
 * Window.center();
 *
 * Window.addWindowResizeListener(evt -> {
 *     System.out.println("Resize: " + evt.getOldWidth() + "x" + evt.getOldHeight()
 *             + " -> " + evt.getNewWidth() + "x" + evt.getNewHeight());
 * });
 * }</pre>
 *
 * <p>This class is a static utility. It owns the GLFW window handle, registers GLFW callbacks,
 * manages cached window state (size/position/fullscreen/etc.), and publishes engine events such as
 * {@link WindowResizeEvent} through {@link JGL}.</p>
 * <p>Logical window dimensions drive layout and projection. The OpenGL viewport uses
 * framebuffer pixels. Subscribe to {@link EventTypes#FRAMEBUFFER_RESIZE} for pixel-size
 * changes, including monitor scaling without a logical resize. Events are reused;
 * copy dimensions during delivery. A zero-sized framebuffer sets a zero-area viewport;
 * render-target owners should defer allocation and rendering until both sizes are positive.</p>
 *
 * @author Albert Beaupre
 * @since October 17th, 2025
 */
public final class Window {

    /**
     * Optional render-thread clear-color policy, borrowed until restored or window shutdown.
     */
    private static UnaryOperator<Color> clearColorFilter;
    /**
     * Engine-managed projection matrix used by shader rendering paths.
     */
    private static final Matrix4f projectionMatrix = new Matrix4f();
    /**
     * Reusable column-major projection values refreshed whenever the engine matrix changes.
     */
    private static final float[] projectionValues = projectionMatrix.get(new float[16]);
    /**
     * Reused resize event; listeners must copy values instead of retaining it as a historical snapshot.
     */
    private static final WindowResizeEvent resizeEvent = new WindowResizeEvent(0, 0, 0, 0);
    /**
     * Native focus callback reference; focus loss clears keyboard and pointer state.
     */
    private static GLFWWindowFocusCallback focusCallback;
    /**
     * Native close callback reference that accepts or defers primary-window closure.
     */
    private static GLFWWindowCloseCallback closeCallback;
    /**
     * Optional primary-window close policy, borrowed until replaced or shutdown.
     */
    private static Runnable closeRequestHandler;
    /**
     * Coalesced native close intent awaiting dispatch outside GLFW callbacks.
     */
    private static boolean nativeCloseRequested;
    /**
     * Accepted primary closure; visibility across threads preserves explicit requests.
     */
    private static volatile boolean closeConfirmed;
    /**
     * Owned native path-drop callback, released with the window.
     */
    private static GLFWDropCallback dropCallback;
    /**
     * Native position callback reference that refreshes cached desktop coordinates.
     */
    private static GLFWWindowPosCallback posCallback;
    /**
     * Native logical-size callback reference that updates projection and publishes resize events.
     */
    private static GLFWWindowSizeCallback sizeCallback;
    /**
     * Owned pixel-size callback, independent of logical window layout changes.
     */
    private static GLFWFramebufferSizeCallback framebufferCallback;
    /**
     * Last framebuffer pixel width; zero when minimized or uninitialized.
     */
    private static int framebufferWidth;
    /**
     * Last framebuffer pixel height; zero when minimized or uninitialized.
     */
    private static int framebufferHeight;
    /**
     * Reused pixel-size notification; listeners must copy its values during delivery.
     */
    private static final FramebufferResizeEvent framebufferResizeEvent = new FramebufferResizeEvent();
    /**
     * Cached swap-interval preference, initially disabled.
     */
    private static SwapInterval swapInterval = SwapInterval.OFF;
    /**
     * Owned GLFW window handle, or zero when no window is registered.
     */
    private static long address;
    /**
     * Thread owning the primary native window and scoped render presentations.
     */
    private static volatile Thread renderThread;
    /**
     * Shared graphics capabilities state retained by Window.
     */
    private static GraphicsCapabilities graphicsCapabilities;

    /**
     * Cached desktop X and Y coordinates in GLFW screen-coordinate units.
     */
    private static int x;
    /**
     * Cached vertical window position in desktop screen coordinates.
     */
    private static int y;
    /**
     * Cached logical content width and height, rather than a separate framebuffer pixel size.
     */
    private static int width;
    /**
     * Cached logical window content height in screen-coordinate units.
     */
    private static int height;
    /**
     * Shared content-rectangle adapter; position setters move the window while origin getters return zero.
     */
    private static final Dimensional dimensional = new Dimensional() {
        /**
         * Returns zero for the adapter's content-space origin, rather than the native
         * window's desktop X position.
         *
         * @return content origin X, always zero
         */
        @Override
        public float getX() {
            return 0;
        }

        /**
         * Moves the native window horizontally, retaining its cached desktop Y coordinate.
         * Truncates the supplied coordinate to an integer.
         *
         * @param x desktop X position
         */
        @Override
        public void setX(float x) {
            Window.setPosition((int) x, Window.getY());
        }

        /**
         * Returns zero for the adapter's content-space origin, rather than the native
         * window's desktop Y position.
         *
         * @return content origin Y, always zero
         */
        @Override
        public float getY() {
            return 0;
        }

        /**
         * Moves the native window vertically, retaining its cached desktop X coordinate.
         * Truncates the supplied coordinate to an integer.
         *
         * @param y desktop Y position
         */
        @Override
        public void setY(float y) {
            Window.setPosition(Window.getX(), (int) y);
        }

        /**
         * Moves the native window using integer-truncated desktop coordinates. The
         * adapter's position getters still represent the zero content origin.
         *
         * @param x desktop X position
         * @param y desktop Y position
         */
        @Override
        public void setPosition(float x, float y) {
            Window.setPosition((int) x, (int) y);
        }

        /**
         * Returns the cached window content width in screen-coordinate units.
         *
         * @return current content width
         */
        @Override
        public float getWidth() {
            return Window.getWidth();
        }

        /**
         * Requests an integer-truncated window width while retaining cached height.
         *
         * @param width requested content width
         */
        @Override
        public void setWidth(float width) {
            Window.setSize((int) width, Window.getHeight());
        }

        /**
         * Returns the cached window content height in screen-coordinate units.
         *
         * @return current content height
         */
        @Override
        public float getHeight() {
            return Window.getHeight();
        }

        /**
         * Requests an integer-truncated window height while retaining cached width.
         *
         * @param height requested content height
         */
        @Override
        public void setHeight(float height) {
            Window.setSize(Window.getWidth(), (int) height);
        }

        /**
         * Requests integer-truncated native window dimensions through the static window
         * size API.
         *
         * @param width requested content width
         * @param height requested content height
         */
        @Override
        public void setSize(float width, float height) {
            Window.setSize((int) width, (int) height);
        }
    };
    /**
     * Cached fullscreen preference/state.
     */
    private static boolean fullscreen = false;
    /**
     * Horizontal position restored when leaving fullscreen.
     */
    private static int windowedX;
    /**
     * Vertical position restored when leaving fullscreen.
     */
    private static int windowedY;
    /**
     * Content width retained independently of fullscreen resize callbacks.
     */
    private static int windowedWidth;
    /**
     * Content height retained independently of fullscreen resize callbacks.
     */
    private static int windowedHeight;
    /**
     * Cached undecorated window preference/state.
     */
    private static boolean borderless = false;
    /**
     * Cached resizability preference/state.
     */
    private static boolean resizable = true;
    /**
     * Pending render-thread requests cancelled if the application closes first.
     */
    private static final ArrayList<CompletableFuture<GraphicsBackend>> backendChanges = new ArrayList<>();
    /**
     * Prevents completion callbacks from queuing changes during window disposal.
     */
    private static boolean acceptsBackendChanges;

    /**
     * Prevents construction of the process-wide GLFW window utility.
     */
    private Window() {
    }

    /**
     * Returns capabilities of the current device, refreshed after backend changes.
     *
     * @return actual capabilities of the initialized renderer
     */
    public static GraphicsCapabilities getGraphicsCapabilities() {
        /*
         * Reuse the current device snapshot without querying native caps per call.
         */
        if (graphicsCapabilities == null) throw new IllegalStateException("Window is not initialized");
        return graphicsCapabilities;
    }

    /**
     * Requests a desktop renderer change at the next main-loop frame boundary.
     * Call on the rendering thread and finish ordinary drawing scopes normally.
     * The window, application, input, audio and CPU scene state remain alive.
     * Engine assets restore automatically; render targets and compute outputs
     * must be repainted. Borrowed native handles must be fetched again afterward.
     *
     * <p>The future completes on the rendering thread after restoration. Do not
     * block that thread waiting for it. Unsupported targets and recoverable
     * failures complete exceptionally while retaining/restoring the old backend.</p>
     * <p>If both replacement and recovery fail, the future receives a
     * {@link GraphicsRecoveryException} and JGL closes through normal disposal.</p>
     *
     * <pre>{@code
     * Window.switchGraphicsBackend(GraphicsBackend.VULKAN)
     *         .thenRun(this::regenerateComputeOutput);
     * }</pre>
     *
     * @param backend requested desktop rendering API
     * @return completion of the frame-boundary transition
     */
    public static CompletableFuture<GraphicsBackend> switchGraphicsBackend(GraphicsBackend backend) {
        /*
         * Queue work through the existing loop rather than destroying a device
         * while input callbacks or an active scene draw are still using it.
         */
        BgfxGraphics.device();
        if (!acceptsBackendChanges) throw new IllegalStateException("The window is shutting down");
        if (backend == null) throw new NullPointerException("backend");
        CompletableFuture<GraphicsBackend> completion = new CompletableFuture<>();
        backendChanges.add(completion);
        JGL.runTask(() -> {
            if (!acceptsBackendChanges || shouldClose()) return;
            if (completion.isDone()) {
                backendChanges.remove(completion);
                return;
            }
            try {
                BgfxResources.switchBackend(backend);
                graphicsCapabilities = GraphicsCapabilities.current();
                backendChanges.remove(completion);
                completion.complete(backend);
            } catch (RuntimeException | Error failure) {
                if (failure instanceof GraphicsRecoveryException) {
                    acceptsBackendChanges = false;
                    requestClose();
                } else {
                    try {
                        graphicsCapabilities = GraphicsCapabilities.current();
                    } catch (RuntimeException | Error capabilityFailure) {
                        failure.addSuppressed(capabilityFailure);
                    }
                }
                backendChanges.remove(completion);
                completion.completeExceptionally(failure);
            }
        });
        return completion;
    }

    /**
     * Returns the active renderer rather than the startup configuration value.
     *
     * @return current graphics API
     */
    public static GraphicsBackend getGraphicsBackend() {
        /*
         * Runtime selection belongs to the live device, not its initial config.
         */
        return BgfxGraphics.device().backend();
    }

    /**
     * Creates a native window and initializes the configured bgfx device, projection
     * and engine event callbacks. Graphics initialization failure destroys the window.
     *
     * @param config nonnull application window and graphics configuration
     */
    public static void init(JGLConfiguration config) {
        /*
         * Keep lifecycle initialization on the calling engine thread so native handles and callbacks share one owner.
         */
        if (config == null) throw new NullPointerException("JGLConfiguration cannot be null");
        if (address != NULL) throw new IllegalStateException("Window is already initialized");
        /*
         * Every renderer owns its graphics device. GLFW supplies only the native
         * window, so context hints cannot alter the selected rendering backend.
         */
        Window.width = config.getWidth();
        Window.height = config.getHeight();
        Window.fullscreen = config.isFullscreen();
        windowedWidth = width;
        windowedHeight = height;
        windowedX = 0;
        windowedY = 0;
        if (fullscreen) {
            GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (mode != null) {
                windowedX = (mode.width() - width) / 2;
                windowedY = (mode.height() - height) / 2;
            }
        }
        Window.borderless = !config.isDecorated();
        Window.resizable = config.isResizable();
        Window.swapInterval = config.getSwapInterval();
        renderThread = Thread.currentThread();
        config.applyWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        long monitor = config.isFullscreen() ? glfwGetPrimaryMonitor() : NULL;
        address = glfwCreateWindow(width, height, config.getTitle(), monitor, NULL);
        if (address == NULL) throw new IllegalStateException("Failed to create the GLFW window");
        try {
            BgfxGraphics.initialize(address, swapInterval.getValue() != 0, config.getSamples(), config.getGraphicsBackend());
            BgfxGraphics.device().setSwapInterval(swapInterval.getValue());
            graphicsCapabilities = GraphicsCapabilities.current();
            graphicsCapabilities.requireRaster();
            acceptsBackendChanges = true;
        } catch (RuntimeException | Error failure) {
            BgfxGraphics.dispose();
            glfwDestroyWindow(address);
            address = NULL;
            graphicsCapabilities = null;
            throw failure;
        }

        sizeCallback = GLFWWindowSizeCallback.create((win, newW, newH) -> {
            if (newW <= 0 || newH <= 0) return;

            int oldWidth = Window.width;
            int oldHeight = Window.height;

            Window.width = newW;
            Window.height = newH;

            BgfxGraphics.setViewport(0, 0, newW, newH);

            updateDefaultProjectionMatrix();

            resizeEvent.setOldHeight(oldHeight);
            resizeEvent.setOldWidth(oldWidth);
            resizeEvent.setNewHeight(Window.height);
            resizeEvent.setNewWidth(Window.width);
            JGL.publish(resizeEvent);
        });
        glfwSetWindowSizeCallback(address, sizeCallback);
        framebufferCallback = GLFWFramebufferSizeCallback.create((win, newWidth, newHeight) -> {
            int oldWidth = framebufferWidth;
            int oldHeight = framebufferHeight;
            framebufferWidth = newWidth;
            framebufferHeight = newHeight;
            BgfxGraphics.setViewport(0, 0, newWidth, newHeight);
            framebufferResizeEvent.set(oldWidth, oldHeight, newWidth, newHeight);
            JGL.publish(framebufferResizeEvent);
        });
        glfwSetFramebufferSizeCallback(address, framebufferCallback);

        posCallback = GLFWWindowPosCallback.create((win, newX, newY) -> {
            Window.x = newX;
            Window.y = newY;
        });
        glfwSetWindowPosCallback(address, posCallback);

        focusCallback = GLFWWindowFocusCallback.create((win, focused) -> {
            if (!focused) {
                Mouse.cancelButtons();
                Keyboard.resetState();
            }
            JGL.publish(new WindowFocusEvent(focused));
        });
        glfwSetWindowFocusCallback(address, focusCallback);

        closeCallback = GLFWWindowCloseCallback.create(win -> {
            if (closeRequestHandler == null || closeConfirmed) return;
            // GLFW sets this flag before invoking the callback; clear it before returning.
            glfwSetWindowShouldClose(win, false);
            nativeCloseRequested = true;
        });
        glfwSetWindowCloseCallback(address, closeCallback);
        dropCallback = GLFWDropCallback.create((win, count, names) -> {
            ArrayList<Path> paths = new ArrayList<>(count);
            for (int index = 0; index < count; index++) paths.add(Path.of(GLFWDropCallback.getName(names, index)));
            try (MemoryStack stack = MemoryStack.stackPush()) {
                DoubleBuffer cursorX = stack.mallocDouble(1);
                DoubleBuffer cursorY = stack.mallocDouble(1);
                glfwGetCursorPos(win, cursorX, cursorY);
                JGL.publish(new FileDropEvent(paths, (float) cursorX.get(0), Window.height - (float) cursorY.get(0)));
            }
        });
        glfwSetDropCallback(address, dropCallback);

        if (!config.isFullscreen() && !config.isMaximized()) {
            GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vid != null) {
                int centerX = (vid.width() - config.getWidth()) / 2;
                int centerY = (vid.height() - config.getHeight()) / 2;
                glfwSetWindowPos(address, centerX, centerY);
                Window.x = centerX;
                Window.y = centerY;
            }
        }

        if (config.isMaximized()) glfwMaximizeWindow(address);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetWindowSize(address, w, h);
            Window.width = w.get(0);
            Window.height = h.get(0);
            glfwGetFramebufferSize(address, w, h);
            framebufferWidth = w.get(0);
            framebufferHeight = h.get(0);
        }

        BgfxGraphics.setViewport(0, 0, framebufferWidth, framebufferHeight);

        updateDefaultProjectionMatrix();

        if (config.isVisible()) {
            // Initial presentation honors both focus preferences; subsequent shows retain their own policy.
            glfwSetWindowAttrib(address, GLFW_FOCUS_ON_SHOW, config.isFocused() && config.isFocusOnShow() ? GLFW_TRUE : GLFW_FALSE);
            glfwShowWindow(address);
            glfwSetWindowAttrib(address, GLFW_FOCUS_ON_SHOW, config.isFocusOnShow() ? GLFW_TRUE : GLFW_FALSE);
        }
    }

    /**
     * Rebuilds the engine's bottom-left-origin orthographic projection from cached
     * window dimensions, with depth limits -1 and 1, and refreshes the reusable
     * column-major upload array. Does not submit any drawing.
     */
    private static void updateDefaultProjectionMatrix() {
        /*
         * Update the retained matrix and shader array together so resize does not allocate projection objects.
         */
        projectionMatrix.setOrtho(0f, Window.width, 0f, Window.height, -1f, 1f)
                .get(projectionValues);
    }

    /**
     * Clears the current render target using the specified color.
     * <p>
     * This sets the clear color to the RGBA values of the provided {@code Color} object
     * and clears the color buffer bit, effectively resetting the frame for new drawing.
     *
     * @param color the color to use for clearing the framebuffer; must not be null
     */
    public static void clear(Color color) {
        /*
         * Resolve an optional scoped policy before clearing without allocating colors.
         */
        Color effective = clearColorFilter == null ? color : clearColorFilter.apply(color);
        BgfxGraphics.clear(effective);
    }

    /**
     * Installs a render-thread policy applied to subsequent framebuffer clears.
     * Callers must restore the returned policy in a finally block, in reverse
     * nesting order. The policy and colors remain borrowed; no resource is owned.
     * Window shutdown releases the installed policy so a later runtime starts without it.
     *
     * @param filter color policy, or null to use the supplied color directly
     * @return previous policy, or null when none was installed
     */
    public static UnaryOperator<Color> setClearColorFilter(UnaryOperator<Color> filter) {
        /*
         * Return the prior reference so nested passes restore policy without wrappers.
         */
        UnaryOperator<Color> previous = clearColorFilter;
        clearColorFilter = filter;
        return previous;
    }

    /**
     * Adds a window resize listener.
     *
     * <p>This subscribes the listener directly to {@link EventTypes#WINDOW_RESIZE} via {@link JGL}.</p>
     *
     * @param listener resize listener (must be non-null)
     * @throws NullPointerException if {@code listener} is null
     */
    public static void addWindowResizeListener(WindowResizeListener listener) {
        /*
         * Forward listener identity through the shared event registry so subscription lifetime remains explicit.
         */
        addWindowResizeListener(listener, 0);
    }

    /**
     * Adds a window resize listener with an explicit execution priority.
     *
     * @param listener resize listener (must be non-null)
     * @param priority execution priority; higher values execute earlier
     * @throws NullPointerException if {@code listener} is null
     */
    public static void addWindowResizeListener(WindowResizeListener listener, int priority) {
        /*
         * Forward listener identity through the shared event registry so subscription lifetime remains explicit.
         */
        if (listener == null) throw new NullPointerException("A null WindowResizeListener cannot be added");
        JGL.subscribe(EventTypes.WINDOW_RESIZE, priority, listener);
    }

    /**
     * Removes a window resize listener.
     *
     * <p>This unsubscribes the listener from {@link EventTypes#WINDOW_RESIZE} via {@link JGL}.</p>
     *
     * @param listener resize listener (must be non-null)
     * @throws NullPointerException if {@code listener} is null
     */
    public static void removeWindowResizeListener(WindowResizeListener listener) {
        /*
         * Forward listener identity through the shared event registry so subscription lifetime remains explicit.
         */
        if (listener == null) throw new NullPointerException("A null WindowResizeListener cannot be removed");
        JGL.unsubscribe(EventTypes.WINDOW_RESIZE, listener);
    }

    /**
     * Checks whether GLFW has requested the window be closed.
     *
     * @return true if the window should close
     */
    static boolean shouldClose() {
        /*
         * An explicit accepted close wins even if another native request arrives
         * before the loop observes it and clears GLFW's provisional flag.
         */
        return address == NULL || closeConfirmed || glfwWindowShouldClose(address);
    }

    /**
     * Dispatches one pending primary close request after native event polling.
     * Application work therefore executes outside GLFW callbacks and follows the
     * normal runtime failure and cleanup path. Temporarily enters the primary
     * logical scope even if the event originated during a scoped presentation.
     */
    static void dispatchCloseRequest() {
        /*
         * Consume intent before invoking user code so reentrant polling cannot
         * replay this request. No task is captured in an embedded game context.
         */
        if (!nativeCloseRequested) return;
        nativeCloseRequested = false;
        if (closeConfirmed) return;
        WindowContext previous = WindowContext.use(null);
        try {
            Runnable handler = closeRequestHandler;
            if (handler == null) requestClose();
            else handler.run();
        } finally {
            WindowContext.use(previous);
        }
    }

    /**
     * Sets the window title.
     *
     * <p>This delegates to {@link GLFW#glfwSetWindowTitle(long, CharSequence)}.</p>
     *
     * @param newTitle new window title
     */
    public static void setTitle(String newTitle) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        long handle = getAddress();
        if (handle != NULL) glfwSetWindowTitle(handle, newTitle);
    }

    /**
     * Sets the window size.
     *
     * <p>This requests a size change; GLFW will typically call your size callback, which updates cached
     * {@link #width} and {@link #height} and updates the engine projection.</p>
     *
     * @param width  new window width (pixels)
     * @param height new window height (pixels)
     */
    public static void setSize(int width, int height) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        WindowContext context = WindowContext.current();
        if (context != null) {
            if (context.nativeWindow() != NULL) glfwSetWindowSize(context.nativeWindow(), width, height);
            return;
        }
        glfwSetWindowSize(address, width, height);
    }

    /**
     * Returns the cached window width.
     *
     * @return window width in pixels
     */
    public static int getWidth() {
        /*
         * Use the active logical presentation dimensions before falling back to primary-window state.
         */
        WindowContext context = WindowContext.current();
        return context == null ? width : context.width();
    }

    /**
     * Returns the cached window height.
     *
     * @return window height in pixels
     */
    public static int getHeight() {
        /*
         * Use the active logical presentation dimensions before falling back to primary-window state.
         */
        WindowContext context = WindowContext.current();
        return context == null ? height : context.height();
    }

    /**
     * Returns the cached window X position.
     *
     * @return window X in screen coordinates
     */
    public static int getX() {
        /*
         * Return cached coordinates directly instead of polling native state during each caller query.
         */
        return x;
    }

    /**
     * Returns the cached window Y position.
     *
     * @return window Y in screen coordinates
     */
    public static int getY() {
        /*
         * Return cached coordinates directly instead of polling native state during each caller query.
         */
        return y;
    }

    /**
     * Queries the framebuffer size.
     *
     * <p>On HiDPI displays, the framebuffer size can differ from {@link #getWidth()}/{@link #getHeight()}.</p>
     *
     * @return array {framebufferWidth, framebufferHeight}
     */
    public static int[] getFramebufferSize() {
        /*
         * Use stack buffers for the native query; only the returned dimension pair escapes this call.
         */
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetFramebufferSize(address, w, h);
            return new int[]{w.get(0), h.get(0)};
        }
    }

    /**
     * Returns the last native framebuffer width without allocating or querying GLFW.
     *
     * @return width in pixels, possibly zero when minimized; zero before initialization
     */
    public static int getFramebufferWidth() {
        /*
         * Callback-maintained primitive state allows allocation-free render-target sizing.
         */
        return framebufferWidth;
    }

    /**
     * Returns the last native framebuffer height without allocating or querying GLFW.
     *
     * @return height in pixels, possibly zero when minimized; zero before initialization
     */
    public static int getFramebufferHeight() {
        /*
         * Dimensions become current when GLFW delivers framebuffer callbacks.
         */
        return framebufferHeight;
    }

    /**
     * Returns the current swap interval.
     *
     * @return swap interval enum
     */
    public static SwapInterval getSwapInterval() {
        /*
         * Read retained configuration or native state directly without constructing a temporary settings object.
         */
        return swapInterval;
    }

    /**
     * Sets the swap interval (VSync).
     *
     * <p>This updates the cached {@link #swapInterval} and updates the graphics device presentation policy.</p>
     *
     * @param type swap interval enum (null is ignored)
     */
    public static void setSwapInterval(SwapInterval type) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (type == null) return;
        swapInterval = type;
        if (BgfxGraphics.isActive()) BgfxGraphics.device().setSwapInterval(type.getValue());
    }

    /**
     * Sets the window position (top-left corner) in screen coordinates.
     *
     * <p>This also updates the cached {@link #x}/{@link #y} immediately so callers see the new values
     * even before the GLFW position callback runs.</p>
     *
     * @param x new window X
     * @param y new window Y
     */
    public static void setPosition(int x, int y) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        glfwSetWindowPos(address, x, y);
        Window.x = x;
        Window.y = y;
    }

    /**
     * Returns whether the window is currently marked fullscreen.
     *
     * @return true if fullscreen
     */
    public static boolean isFullscreen() {
        /*
         * Read retained configuration or native state directly without constructing a temporary settings object.
         */
        return fullscreen;
    }

    /**
     * Sets fullscreen mode using the primary monitor.
     *
     * <p>When entering fullscreen, this sets the window monitor to the primary monitor and uses the monitor's
     * current video mode dimensions and refresh rate.</p>
     *
     * <p>When leaving fullscreen, this restores the position and size saved before entering fullscreen.
     * Applications started fullscreen use their configured windowed dimensions. Repeating the
     * current mode leaves the saved bounds unchanged.</p>
     *
     * @param fullscreen true for fullscreen, false for windowed
     * @throws RuntimeException if the primary monitor video mode cannot be queried
     */
    public static void setFullscreen(boolean fullscreen) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL || Window.fullscreen == fullscreen) return;

        GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vid == null) throw new RuntimeException("Failed to get video mode");

        if (fullscreen) {
            windowedX = x;
            windowedY = y;
            windowedWidth = width;
            windowedHeight = height;
        }
        Window.fullscreen = fullscreen;

        if (fullscreen) {
            glfwSetWindowMonitor(address, glfwGetPrimaryMonitor(), 0, 0, vid.width(), vid.height(), vid.refreshRate());
        } else {
            glfwSetWindowMonitor(address, NULL, windowedX, windowedY, windowedWidth, windowedHeight, GLFW_DONT_CARE);
        }
    }

    /**
     * Returns whether the window is currently marked borderless/undecorated.
     *
     * @return true if borderless
     */
    public static boolean isBorderless() {
        /*
         * Read retained configuration or native state directly without constructing a temporary settings object.
         */
        return borderless;
    }

    /**
     * Sets whether the window is borderless (undecorated).
     *
     * <p>This updates both the cached {@link #borderless} flag and the GLFW window attribute.</p>
     *
     * @param borderless true for borderless, false for normal window decorations
     */
    public static void setBorderless(boolean borderless) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        Window.borderless = borderless;
        glfwSetWindowAttrib(address, GLFW_DECORATED, borderless ? GLFW_FALSE : GLFW_TRUE);
    }

    /**
     * Returns whether the window is currently marked resizable.
     *
     * @return true if resizable
     */
    public static boolean isResizable() {
        /*
         * Read retained configuration or native state directly without constructing a temporary settings object.
         */
        return resizable;
    }

    /**
     * Sets whether the window is resizable.
     *
     * <p>This updates both the cached {@link #resizable} flag and the GLFW window attribute.</p>
     *
     * @param resizable true to allow resizing, false to lock the window size
     */
    public static void setResizable(boolean resizable) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        Window.resizable = resizable;
        glfwSetWindowAttrib(address, GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
    }

    /**
     * Toggles fullscreen mode.
     *
     * <p>This calls {@link #setFullscreen(boolean)}. Note that {@link #setFullscreen(boolean)}
     * already updates {@link #fullscreen}.</p>
     */
    public static void toggleFullscreen() {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        setFullscreen(!fullscreen);
    }

    /**
     * Minimizes (iconifies) the window.
     *
     * <p>No-op if the window has not been created.</p>
     */
    public static void minimize() {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        glfwIconifyWindow(address);
    }

    /**
     * Maximizes the window.
     *
     * <p>No-op if the window has not been created.</p>
     */
    public static void maximize() {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        glfwMaximizeWindow(address);
    }

    /**
     * Restores the window from minimized/maximized state.
     *
     * <p>No-op if the window has not been created.</p>
     */
    public static void restore() {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        glfwRestoreWindow(address);
    }

    /**
     * Brings focus to the window.
     *
     * <p>No-op if the window has not been created.</p>
     */
    public static void focus() {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        glfwFocusWindow(address);
    }

    /**
     * Replaces the primary operating-system close policy.
     *
     * <p>A nonnull handler receives coalesced title-bar and Alt+F4 requests on the
     * render thread after native event polling. The window stays open until the
     * handler eventually calls {@link #requestClose()}, allowing asynchronous
     * save decisions. Returning without closing keeps the application running.
     * Exceptions propagate through the normal JGL lifecycle. A null handler
     * restores immediate native closure. Explicit close requests bypass this policy.</p>
     *
     * <p>Call only on the initialized primary window's render thread, outside a
     * {@link WindowContext}. The handler is borrowed and released on shutdown.</p>
     *
     * @param handler close policy, or null for default behavior
     * @return previous borrowed handler, or null
     * @throws IllegalStateException if called outside the primary render scope
     */
    public static Runnable setCloseRequestHandler(Runnable handler) {
        /*
         * Presentation-scoped scripts must not replace the host's close policy.
         * Retain one callback rather than allocating a task for each native event.
         */
        if (!isRenderThread() || WindowContext.current() != null)
            throw new IllegalStateException("Close policy requires the primary window render thread.");
        Runnable previous = closeRequestHandler;
        closeRequestHandler = handler;
        return previous;
    }

    /**
     * Accepts closure of the active presentation, bypassing any native close policy.
     *
     * <p>For the primary window this sets the GLFW should-close flag and allows the
     * engine loop to exit cleanly. Inside a {@link WindowContext}, only that logical
     * presentation closes. Does nothing when no primary window exists.</p>
     */
    public static void requestClose() {
        /*
         * A completed save workflow must not prompt again. Keep scoped game exits
         * independent of the host editor's accepted primary close state.
         */
        WindowContext context = WindowContext.current();
        if (context != null) {
            context.requestClose();
            return;
        }
        if (address == NULL) return;
        closeConfirmed = true;
        glfwSetWindowShouldClose(address, true);
    }

    /**
     * Sets whether the window should stay above other windows.
     *
     * <p>This uses the GLFW floating attribute.</p>
     *
     * @param value true to keep on top, false to allow normal z-ordering
     */
    public static void setAlwaysOnTop(boolean value) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        glfwSetWindowAttrib(address, GLFW_FLOATING, value ? GLFW_TRUE : GLFW_FALSE);
    }

    /**
     * Sets the window opacity (where supported).
     *
     * <p>Values are clamped to [0, 1]. On platforms that do not support opacity, GLFW may ignore it.</p>
     *
     * @param opacity opacity in [0..1]
     */
    public static void setOpacity(float opacity) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        opacity = Math.max(0f, Math.min(1f, opacity));
        glfwSetWindowOpacity(address, opacity);
    }

    /**
     * Sets the icon of the window using the provided texture data.
     *
     * @param texture the texture data that defines the icon. It cannot be null.
     *                The data should include the width, height, and pixel buffer
     *                of the image to be used as the window's icon.
     * @throws NullPointerException if the texture parameter is null.
     */
    public static void setIcon(TextureData texture) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        if (texture == null) throw new NullPointerException("TextureData cannot be null");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage glfwImage = GLFWImage.malloc(stack);
            glfwImage.set(texture.width(), texture.height(), texture.buffer());
            GLFWImage.Buffer images = GLFWImage.malloc(1, stack);
            images.put(0, glfwImage);

            glfwSetWindowIcon(address, images);
        }
    }

    /**
     * Centers the window on the primary monitor.
     *
     * <p>This uses the current cached window size to compute the center position.</p>
     */
    public static void center() {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;

        GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vid == null) return;

        int cx = (vid.width() - getWidth()) / 2;
        int cy = (vid.height() - getHeight()) / 2;
        glfwSetWindowPos(address, cx, cy);
        x = cx;
        y = cy;
    }

    /**
     * Sets minimum and maximum window size limits.
     *
     * <p>GLFW uses {@code GLFW_DONT_CARE} if you want to disable a bound.</p>
     *
     * @param minW minimum width
     * @param minH minimum height
     * @param maxW maximum width
     * @param maxH maximum height
     */
    public static void setSizeLimits(int minW, int minH, int maxW, int maxH) {
        /*
         * Apply the requested native or cached state change directly, retaining lifecycle ownership in the engine.
         */
        if (address == NULL) return;
        glfwSetWindowSizeLimits(address, minW, minH, maxW, maxH);
    }

    /**
     * Disposes of window resources.
     *
     * <p>This frees all registered GLFW callbacks and destroys the GLFW window handle.</p>
     */
    static void dispose() {
        /*
         * Finish pending requests while callbacks can still inspect the live window,
         * and reject new transitions before releasing native resources.
         */
        Throwable failure = null;
        acceptsBackendChanges = false;
        while (!backendChanges.isEmpty()) {
            CompletableFuture<GraphicsBackend> completion = backendChanges.remove(backendChanges.size() - 1);
            completion.completeExceptionally(new CancellationException("The application closed before the backend change"));
        }

        failure = appendSuppressed(failure, runSafe(ImmediateTextureRenderer::dispose));
        long handle = address;
        if (handle != NULL) {
            failure = appendSuppressed(failure, runSafe(() -> glfwSetFramebufferSizeCallback(handle, null)));
            failure = appendSuppressed(failure, runSafe(() -> glfwSetWindowSizeCallback(handle, null)));
            failure = appendSuppressed(failure, runSafe(() -> glfwSetWindowPosCallback(handle, null)));
            failure = appendSuppressed(failure, runSafe(() -> glfwSetWindowFocusCallback(handle, null)));
            failure = appendSuppressed(failure, runSafe(() -> glfwSetWindowCloseCallback(handle, null)));
            failure = appendSuppressed(failure, runSafe(() -> glfwSetDropCallback(handle, null)));
        }

        GLFWWindowSizeCallback windowSizeCallback = sizeCallback;
        GLFWFramebufferSizeCallback pixelCallback = framebufferCallback;
        framebufferCallback = null;
        if (pixelCallback != null) failure = appendSuppressed(failure, runSafe(pixelCallback::free));
        sizeCallback = null;
        if (windowSizeCallback != null) {
            failure = appendSuppressed(failure, runSafe(windowSizeCallback::free));
        }

        GLFWWindowPosCallback windowPosCallback = posCallback;
        posCallback = null;
        if (windowPosCallback != null) {
            failure = appendSuppressed(failure, runSafe(windowPosCallback::free));
        }

        GLFWWindowFocusCallback windowFocusCallback = focusCallback;
        focusCallback = null;
        if (windowFocusCallback != null) {
            failure = appendSuppressed(failure, runSafe(windowFocusCallback::free));
        }

        GLFWWindowCloseCallback windowCloseCallback = closeCallback;
        closeCallback = null;
        if (windowCloseCallback != null) {
            failure = appendSuppressed(failure, runSafe(windowCloseCallback::free));
        }
        GLFWDropCallback windowDropCallback = dropCallback;
        dropCallback = null;
        if (windowDropCallback != null) failure = appendSuppressed(failure, runSafe(windowDropCallback::free));

        failure = appendSuppressed(failure, runSafe(BgfxGraphics::dispose));
        address = NULL;
        graphicsCapabilities = null;
        if (handle != NULL) {
            failure = appendSuppressed(failure, runSafe(() -> {
                glfwDestroyWindow(handle);
            }));
        }

        resetState();

        if (failure != null) {
            rethrowUnchecked(failure);
        }
    }

    /**
     * Clears cached window and callback references and restores default flags,
     * swap interval, clear-color policy, and identity projection. Performs no native cleanup itself;
     * call only after owned resources have been released or during safe reset.
     */
    static void resetState() {
        /*
         * Release borrowed application callbacks so completed runtimes cannot retain
         * their owners or alter framebuffer clears during the next initialization.
         */
        clearColorFilter = null;
        focusCallback = null;
        closeCallback = null;
        closeRequestHandler = null;
        nativeCloseRequested = false;
        closeConfirmed = false;
        posCallback = null;
        sizeCallback = null;
        address = NULL;
        framebufferCallback = null;
        framebufferWidth = 0;
        framebufferHeight = 0;
        x = 0;
        y = 0;
        width = 0;
        height = 0;
        fullscreen = false;
        windowedX = 0;
        windowedY = 0;
        windowedWidth = 0;
        windowedHeight = 0;
        borderless = false;
        resizable = true;
        swapInterval = SwapInterval.OFF;
        projectionMatrix.identity()
                .get(projectionValues);
    }

    /**
     * Returns the underlying GLFW window handle.
     *
     * @return GLFW window handle (0/NULL if not created)
     */
    public static long getAddress() {
        /*
         * Resolve the current presentation handle while preserving the primary handle for unscoped callers.
         */
        WindowContext context = WindowContext.current();
        return context == null ? address : context.nativeWindow();
    }

    /**
     * Returns the engine-managed projection matrix values used by shader-based render paths.
     *
     * <p>The returned array is a reused snapshot of the current projection state.
     * Callers should treat it as read-only.</p>
     *
     * @return the current projection matrix values in column-major order
     */
    public static float[] getProjectionMatrix() {
        /*
         * Borrow the retained projection array for the current presentation without copying it.
         */
        WindowContext context = WindowContext.current();
        return context == null ? projectionValues : context.projection();
    }

    /**
     * Replaces the engine's column-major projection. Renderers copy this value at
     * submission, so subsequent camera changes cannot alter already queued draws.
     *
     * @param matrixData at least sixteen column-major values
     * @throws NullPointerException if the matrix is null
     * @throws IllegalArgumentException if fewer than sixteen values are provided
     */
    public static void setProjectionMatrix(float[] matrixData) {
        /*
         * Keep the retained matrix and packed shader values synchronized for the selected presentation.
         */
        if (matrixData == null) throw new NullPointerException("matrixData");
        if (matrixData.length < 16) throw new IllegalArgumentException("matrixData must contain at least 16 floats");
        WindowContext context = WindowContext.current();
        if (context != null) {
            context.projection(matrixData);
            return;
        }
        projectionMatrix.set(matrixData)
                .get(projectionValues);
    }

    /**
     * Copies the current engine-managed projection matrix into the supplied destination array.
     *
     * @param destination destination array that must contain room for 16 floats
     * @throws NullPointerException     if {@code destination} is null
     * @throws IllegalArgumentException if {@code destination.length < 16}
     */
    public static void copyProjectionMatrix(float[] destination) {
        /*
         * Copy into caller-owned storage so rendering can retain its own projection without exposing mutable engine state.
         */
        if (destination == null) throw new NullPointerException("destination");
        if (destination.length < 16) throw new IllegalArgumentException("destination must contain at least 16 floats");
        System.arraycopy(getProjectionMatrix(), 0, destination, 0, 16);
    }

    /**
     * Returns the shared window adapter. Its getters expose a zero content origin
     * and current content dimensions, while position setters move the desktop window
     * and size setters resize it, truncating float inputs to integers.
     *
     * @return live shared dimensional adapter
     */
    public static Dimensional getDimensional() {
        /*
         * Reuse the shared dimensional adapter rather than allocating a wrapper for each query.
         */
        return dimensional;
    }

    /**
     * Runs one cleanup action and captures any thrown failure so later cleanup
     * actions can still be attempted.
     *
     * @param action cleanup operation
     * @return thrown failure, or null on success
     */
    private static Throwable runSafe(Runnable action) {
        /*
         * Retain the original cleanup failure instead of interrupting the remaining release sequence.
         */
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    /**
     * Keeps the first cleanup failure and attaches a later nonnull failure as
     * suppressed. Callers must avoid passing the same throwable as both arguments.
     *
     * @param primary earlier failure, possibly null
     * @param next later failure, possibly null
     * @return first available failure
     */
    private static Throwable appendSuppressed(Throwable primary, Throwable next) {
        /*
         * Preserve the first failure as the primary cause and attach later cleanup failures in encounter order.
         */
        if (next == null) {
            return primary;
        }
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }

    /**
     * Rethrows runtime exceptions and errors unchanged, wrapping other throwable
     * types so cleanup can report them without a checked throws declaration.
     *
     * @param throwable nonnull failure to propagate
     */
    private static void rethrowUnchecked(Throwable throwable) {
        /*
         * Preserve runtime and error identities; wrap only checked cleanup failures that cannot escape this API directly.
         */
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(throwable);
    }

    /**
     * Checks ownership of native rendering and presentation scope changes.
     *
     * @return whether the calling thread owns the primary window
     */
    public static boolean isRenderThread() {
        /*
         * The volatile owner is read only at context boundaries, not by hot input polling.
         */
        return address != NULL && renderThread == Thread.currentThread();
    }

}
