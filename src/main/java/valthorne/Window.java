package valthorne;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import valthorne.event.EventTypes;
import valthorne.event.events.WindowResizeEvent;
import valthorne.event.listeners.WindowResizeListener;
import valthorne.graphics.Color;
import java.util.function.UnaryOperator;
import valthorne.graphics.ImmediateTextureRenderer;
import valthorne.graphics.texture.TextureData;
import org.joml.Matrix4f;
import valthorne.math.geometry.Dimensional;

import java.nio.IntBuffer;
import java.nio.DoubleBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import valthorne.event.events.FileDropEvent;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;
import static org.lwjgl.system.MemoryUtil.NULL;
import org.lwjgl.system.MemoryUtil;
import valthorne.event.events.WindowFocusEvent;
import valthorne.graphics.GraphicsCapabilities;

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
     * Native close callback reference that requests window closure.
     */
    private static GLFWWindowCloseCallback closeCallback;

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
     * Cached swap-interval preference, initially disabled.
     */
    private static SwapInterval swapInterval = SwapInterval.OFF;
    /**
     * Owned GLFW window handle, or zero when no window is registered.
     */
    private static long address;
    /**
     * Shared graphics capabilities state retained by Window.
     */
    private static GraphicsCapabilities graphicsCapabilities;

    /** @return actual capabilities of the initialized window, not the requested version */
    public static GraphicsCapabilities getGraphicsCapabilities() {
        if (graphicsCapabilities == null) throw new IllegalStateException("Window is not initialized");
        return graphicsCapabilities;
    }
    /**
     * Cached desktop X and Y coordinates in GLFW screen-coordinate units.
     */
    private static int x, y;
    /**
     * Cached logical content width and height, rather than a separate framebuffer pixel size.
     */
    private static int width, height;
    /*
     * Saved windowed desktop X coordinate, independent of fullscreen callbacks.
     */
    private static int windowedX;
    /*
     * Saved windowed desktop Y coordinate, independent of fullscreen callbacks.
     */
    private static int windowedY;
    /*
     * Saved windowed content width; configured width is used for fullscreen startup.
     */
    private static int windowedWidth;
    /*
     * Saved windowed content height; configured height is used for fullscreen startup.
     */
    private static int windowedHeight;
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
     * Cached undecorated window preference/state.
     */
    private static boolean borderless = false;
    /**
     * Cached resizability preference/state.
     */
    private static boolean resizable = true;

    /**
     * Prevents construction of the process-wide GLFW window utility.
     */
    private Window() {
    }

    /**
     * Initializes the application window with the given configuration parameters.
     * Configures the OpenGL context and GLFW window hints and creates the application window.
     * It sets up various callbacks for handling resize, position, focus, and other window events.
     * The method also ensures proper rendering capabilities, including enabling multisampling and
     * blending if required, based on the configuration.
     *
     * @param config the {@link JGLConfiguration} object containing all the configuration parameters
     *               required for setting up the application window, such as dimensions, fullscreen
     *               mode, OpenGL settings, and additional hints. This parameter must not be null.
     *               If null, a {@link NullPointerException} will be thrown.
     */
    public static void init(JGLConfiguration config) {
        if (config == null) throw new NullPointerException("JGLConfiguration cannot be null");
        if (address != NULL) throw new IllegalStateException("Window is already initialized");
        if (config.getClientApi() != GLFW_OPENGL_API)
            throw new UnsupportedOperationException("Valthorne currently uses desktop OpenGL bindings; OpenGL ES and no-API windows require a separate backend.");

        Window.width = config.getWidth();
        Window.height = config.getHeight();
        windowedX = 0;
        windowedY = 0;
        windowedWidth = config.getWidth();
        windowedHeight = config.getHeight();
        Window.fullscreen = config.isFullscreen();
        Window.borderless = !config.isDecorated();
        Window.resizable = config.isResizable();
        Window.swapInterval = config.getSwapInterval();

        config.applyWindowHints();

        long monitor = config.isFullscreen() ? glfwGetPrimaryMonitor() : NULL;

        if (config.isFullscreen()) {
            GLFWVidMode desktop = glfwGetVideoMode(monitor);
            if (desktop != null) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer monitorX = stack.mallocInt(1);
                    IntBuffer monitorY = stack.mallocInt(1);
                    glfwGetMonitorPos(monitor, monitorX, monitorY);
                    windowedX = monitorX.get(0) + (desktop.width() - windowedWidth) / 2;
                    windowedY = monitorY.get(0) + (desktop.height() - windowedHeight) / 2;
                }
            }
        }

        int[][] versions = config.isAutomaticContext() ? new int[][]{{4, 3}, {4, 1}, {3, 3}}
                : new int[][]{{config.getContextVersionMajor(), config.getContextVersionMinor()}};
        StringBuilder failures = new StringBuilder();
        for (int[] version : versions) {
            if (config.isAutomaticContext()) {
                glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, version[0]);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, version[1]);
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
                glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
            }
            address = glfwCreateWindow(config.getWidth(), config.getHeight(), config.getTitle(), monitor, NULL);
            if (address != NULL) break;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var description = stack.mallocPointer(1);
                int error = glfwGetError(description);
                String detail = description.get(0) == NULL ? "No native description"
                        : MemoryUtil.memUTF8(description.get(0));
                failures.append("GL ").append(version[0]).append('.').append(version[1])
                        .append(" (error ").append(error).append("): ").append(detail).append('\n');
            }
        }
        if (address == NULL) throw new IllegalStateException("Failed to create the GLFW window:\n" + failures);

        glfwMakeContextCurrent(address);
        try {
            if (glfwGetWindowAttrib(address, GLFW_CLIENT_API) != GLFW_OPENGL_API)
                throw new UnsupportedOperationException("Extra window hints selected an unsupported client API; desktop OpenGL is required.");
            GL.createCapabilities();
            graphicsCapabilities = GraphicsCapabilities.current();
            graphicsCapabilities.requireRaster();
        } catch (RuntimeException | Error failure) {
            graphicsCapabilities = null;
            glfwMakeContextCurrent(NULL);
            GL.setCapabilities(null);
            glfwDestroyWindow(address);
            address = NULL;
            throw failure;
        }
        glfwSwapInterval(config.getSwapInterval().getValue());

        if (config.getSamples() > 0) glEnable(GL_MULTISAMPLE);

        sizeCallback = GLFWWindowSizeCallback.create((win, newW, newH) -> {
            if (newW <= 0 || newH <= 0) return;

            int oldWidth = Window.width;
            int oldHeight = Window.height;

            Window.width = newW;
            Window.height = newH;

            glViewport(0, 0, newW, newH);

            updateDefaultProjectionMatrix();

            resizeEvent.setOldHeight(oldHeight);
            resizeEvent.setOldWidth(oldWidth);
            resizeEvent.setNewHeight(Window.height);
            resizeEvent.setNewWidth(Window.width);
            JGL.publish(resizeEvent);
        });
        glfwSetWindowSizeCallback(address, sizeCallback);

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

        closeCallback = GLFWWindowCloseCallback.create(win -> glfwSetWindowShouldClose(win, true));
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

        if (config.isVisible()) glfwShowWindow(address);

        if (config.isMaximized()) glfwMaximizeWindow(address);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetWindowSize(address, w, h);
            Window.width = w.get(0);
            Window.height = h.get(0);
        }

        glViewport(0, 0, Window.width, Window.height);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        updateDefaultProjectionMatrix();
    }

    /**
     * Rebuilds the engine's bottom-left-origin orthographic projection from cached
     * window dimensions, with depth limits -1 and 1, and refreshes the reusable
     * column-major upload array. Does not modify a fixed-function GL matrix stack.
     */
    private static void updateDefaultProjectionMatrix() {
        projectionMatrix.setOrtho(0f, Window.width, 0f, Window.height, -1f, 1f).get(projectionValues);
    }

    /**
     * Clears the current OpenGL framebuffer using the specified color.
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
        glClearColor(effective.r(), effective.g(), effective.b(), effective.a());
        glClear(GL_COLOR_BUFFER_BIT);
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
        if (listener == null) throw new NullPointerException("A null WindowResizeListener cannot be removed");
        JGL.unsubscribe(EventTypes.WINDOW_RESIZE, listener);
    }

    /**
     * Checks whether GLFW has requested the window be closed.
     *
     * @return true if the window should close
     */
    static boolean shouldClose() {
        return address == NULL || glfwWindowShouldClose(address);
    }

    /**
     * Sets the window title.
     *
     * <p>This delegates to {@link GLFW#glfwSetWindowTitle(long, CharSequence)}.</p>
     *
     * @param newTitle new window title
     */
    public static void setTitle(String newTitle) {
        glfwSetWindowTitle(address, newTitle);
    }

    /**
     * Sets the window size.
     *
     * <p>This requests a size change; GLFW will typically call your size callback, which updates cached
     * {@link #width} and {@link #height} and updates the OpenGL projection.</p>
     *
     * @param width  new window width (pixels)
     * @param height new window height (pixels)
     */
    public static void setSize(int width, int height) {
        glfwSetWindowSize(address, width, height);
    }

    /**
     * Returns the cached window width.
     *
     * @return window width in pixels
     */
    public static int getWidth() {
        return width;
    }

    /**
     * Returns the cached window height.
     *
     * @return window height in pixels
     */
    public static int getHeight() {
        return height;
    }

    /**
     * Returns the cached window X position.
     *
     * @return window X in screen coordinates
     */
    public static int getX() {
        return x;
    }

    /**
     * Returns the cached window Y position.
     *
     * @return window Y in screen coordinates
     */
    public static int getY() {
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
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetFramebufferSize(address, w, h);
            return new int[]{w.get(0), h.get(0)};
        }
    }

    /**
     * Returns the current swap interval.
     *
     * @return swap interval enum
     */
    public static SwapInterval getSwapInterval() {
        return swapInterval;
    }

    /**
     * Sets the swap interval (VSync).
     *
     * <p>This updates the cached {@link #swapInterval} and immediately calls {@link GLFW#glfwSwapInterval(int)}.</p>
     *
     * @param type swap interval enum (null is ignored)
     */
    public static void setSwapInterval(SwapInterval type) {
        if (type == null) return;
        swapInterval = type;
        glfwSwapInterval(type.getValue());
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
        return fullscreen;
    }

    /**
     * Sets fullscreen mode using the primary monitor.
     *
     * <p>When entering fullscreen, this sets the window monitor to the primary monitor and uses the monitor's
     * current video mode dimensions and refresh rate.</p>
     *
     * <p>When leaving fullscreen, this restores the native position and content size saved
     * immediately before entry. Repeated requests for the current mode do nothing. A window
     * initialized in fullscreen uses its configured size, centered on the primary monitor's
     * desktop at initialization, for its first windowed bounds (origin zero if unavailable).
     * Native callbacks continue to update the current bounds without altering the saved bounds.
     * Call on the window-owning thread.</p>
     *
     * @param fullscreen true for fullscreen, false for windowed
     * @throws RuntimeException if the primary monitor video mode cannot be queried
     */
    public static void setFullscreen(boolean fullscreen) {
        /*
         * Query native bounds only on entry so pending position/size callbacks cannot
         * make the snapshot stale. Separate primitives survive fullscreen callbacks
         * without allocating persistent rectangle objects.
         */
        if (address == NULL || Window.fullscreen == fullscreen) return;
        if (fullscreen) {
            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode vid = glfwGetVideoMode(monitor);
            if (vid == null) throw new RuntimeException("Failed to get video mode");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer first = stack.mallocInt(1);
                IntBuffer second = stack.mallocInt(1);
                glfwGetWindowPos(address, first, second);
                windowedX = first.get(0);
                windowedY = second.get(0);
                glfwGetWindowSize(address, first, second);
                windowedWidth = first.get(0);
                windowedHeight = second.get(0);
            }
            Window.fullscreen = true;
            glfwSetWindowMonitor(address, monitor, 0, 0, vid.width(), vid.height(), vid.refreshRate());
        } else {
            Window.fullscreen = false;
            glfwSetWindowMonitor(address, NULL, windowedX, windowedY, windowedWidth, windowedHeight, GLFW_DONT_CARE);
        }
    }

    /**
     * Returns whether the window is currently marked borderless/undecorated.
     *
     * @return true if borderless
     */
    public static boolean isBorderless() {
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
        setFullscreen(!fullscreen);
    }

    /**
     * Minimizes (iconifies) the window.
     *
     * <p>No-op if the window has not been created.</p>
     */
    public static void minimize() {
        if (address == NULL) return;
        glfwIconifyWindow(address);
    }

    /**
     * Maximizes the window.
     *
     * <p>No-op if the window has not been created.</p>
     */
    public static void maximize() {
        if (address == NULL) return;
        glfwMaximizeWindow(address);
    }

    /**
     * Restores the window from minimized/maximized state.
     *
     * <p>No-op if the window has not been created.</p>
     */
    public static void restore() {
        if (address == NULL) return;
        glfwRestoreWindow(address);
    }

    /**
     * Brings focus to the window.
     *
     * <p>No-op if the window has not been created.</p>
     */
    public static void focus() {
        if (address == NULL) return;
        glfwFocusWindow(address);
    }

    /**
     * Requests the window to close.
     *
     * <p>This sets the GLFW should-close flag. Your engine loop should check it and exit cleanly.</p>
     */
    public static void requestClose() {
        if (address == NULL) return;
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
        if (address == NULL) return;
        glfwSetWindowSizeLimits(address, minW, minH, maxW, maxH);
    }

    /**
     * Disposes of window resources.
     *
     * <p>This frees all registered GLFW callbacks and destroys the GLFW window handle.</p>
     */
    static void dispose() {
        Throwable failure = null;

        failure = appendSuppressed(failure, runSafe(ImmediateTextureRenderer::dispose));
        long handle = address;
        if (handle != NULL) {
            failure = appendSuppressed(failure, runSafe(() -> glfwSetWindowSizeCallback(handle, null)));
            failure = appendSuppressed(failure, runSafe(() -> glfwSetWindowPosCallback(handle, null)));
            failure = appendSuppressed(failure, runSafe(() -> glfwSetWindowFocusCallback(handle, null)));
            failure = appendSuppressed(failure, runSafe(() -> glfwSetWindowCloseCallback(handle, null)));
            failure = appendSuppressed(failure, runSafe(() -> glfwSetDropCallback(handle, null)));
        }

        GLFWWindowSizeCallback windowSizeCallback = sizeCallback;
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

        address = NULL;
        graphicsCapabilities = null;
        if (handle != NULL) {
            failure = appendSuppressed(failure, runSafe(() -> {
                glfwMakeContextCurrent(NULL);
                GL.setCapabilities(null);
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
        posCallback = null;
        sizeCallback = null;
        address = NULL;
        x = 0;
        y = 0;
        width = 0;
        height = 0;
        windowedX = 0;
        windowedY = 0;
        windowedWidth = 0;
        windowedHeight = 0;
        fullscreen = false;
        borderless = false;
        resizable = true;
        swapInterval = SwapInterval.OFF;
        projectionMatrix.identity().get(projectionValues);
    }

    /**
     * Returns the underlying GLFW window handle.
     *
     * @return GLFW window handle (0/NULL if not created)
     */
    public static long getAddress() {
        return address;
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
        return projectionValues;
    }

    /**
     * Replaces the engine-managed projection matrix with the supplied column-major matrix values.
     *
     * <p>This does not update OpenGL's fixed-function matrix stack directly. It exists so
     * shader-driven renderers can follow the same logical viewport/camera state as legacy
     * code while the engine transitions away from implicit OpenGL matrices.</p>
     *
     * @param matrixData projection values in column-major order
     * @throws NullPointerException     if {@code matrixData} is null
     * @throws IllegalArgumentException if {@code matrixData.length < 16}
     */
    public static void setProjectionMatrix(float[] matrixData) {
        if (matrixData == null) throw new NullPointerException("matrixData");
        if (matrixData.length < 16) throw new IllegalArgumentException("matrixData must contain at least 16 floats");
        projectionMatrix.set(matrixData).get(projectionValues);
    }

    /**
     * Copies the current engine-managed projection matrix into the supplied destination array.
     *
     * @param destination destination array that must contain room for 16 floats
     * @throws NullPointerException     if {@code destination} is null
     * @throws IllegalArgumentException if {@code destination.length < 16}
     */
    public static void copyProjectionMatrix(float[] destination) {
        if (destination == null) throw new NullPointerException("destination");
        if (destination.length < 16) throw new IllegalArgumentException("destination must contain at least 16 floats");
        projectionMatrix.get(destination);
    }

    /**
     * Returns the shared window adapter. Its getters expose a zero content origin
     * and current content dimensions, while position setters move the desktop window
     * and size setters resize it, truncating float inputs to integers.
     *
     * @return live shared dimensional adapter
     */
    public static Dimensional getDimensional() {
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
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(throwable);
    }
}
