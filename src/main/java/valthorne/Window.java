package valthorne;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import valthorne.event.EventTypes;
import valthorne.event.events.WindowResizeEvent;
import valthorne.event.listeners.WindowResizeListener;
import valthorne.graphics.Color;
import valthorne.graphics.ImmediateTextureRenderer;
import valthorne.graphics.texture.TextureData;
import valthorne.math.Matrix4f;
import valthorne.ui.Dimensional;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;
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
 *
 * @author Albert Beaupre
 * @since October 17th, 2025
 */
public final class Window {

    private static final Matrix4f projectionMatrix = new Matrix4f();                     // Engine-managed projection used by modern shader paths.

    private static final WindowResizeEvent resizeEvent = new WindowResizeEvent(0, 0, 0, 0); // Reused resize event instance to avoid allocations.

    private static GLFWFramebufferSizeCallback fbCallback;                                   // Framebuffer resize callback handle (free() on dispose).
    private static GLFWWindowFocusCallback focusCallback;                                    // Focus callback handle (free() on dispose).
    private static GLFWWindowIconifyCallback iconifyCallback;                                // Minimize/iconify callback handle (free() on dispose).
    private static GLFWWindowMaximizeCallback maximizeCallback;                              // Maximize callback handle (free() on dispose).
    private static GLFWWindowCloseCallback closeCallback;                                    // Close callback handle (free() on dispose).
    private static GLFWWindowPosCallback posCallback;                                        // Position callback handle (free() on dispose).
    private static GLFWWindowSizeCallback sizeCallback;                                      // Window size callback handle (free() on dispose).
    private static GLFWWindowContentScaleCallback scaleCallback;                             // Content scale callback handle (free() on dispose).

    private static SwapInterval swapInterval = SwapInterval.OFF;                             // Current swap interval (vsync mode) cached for init.
    private static long address;                                                             // GLFW window handle (0/NULL means not created).
    private static int x, y;                                                               // Cached window position in screen coordinates.
    private static int width, height;                                                      // Cached window size in screen coordinates.
    private static boolean fullscreen = false;                                               // Cached fullscreen state.
    private static boolean borderless = false;                                               // Cached borderless/undecorated state.
    private static boolean resizable = true;                                                 // Cached resizable state.

    private static final Dimensional dimensional = new Dimensional() {                       // Dimensional adapter for treating the window as a UI rectangle.
        @Override
        public float getX() {
            return 0;
        }

        @Override
        public float getY() {
            return 0;
        }

        @Override
        public void setPosition(float x, float y) {
            Window.setPosition((int) x, (int) y);
        }

        @Override
        public void setX(float x) {
            Window.setPosition((int) x, Window.getY());
        }

        @Override
        public void setY(float y) {
            Window.setPosition(Window.getX(), (int) y);
        }

        @Override
        public float getWidth() {
            return Window.getWidth();
        }

        @Override
        public float getHeight() {
            return Window.getHeight();
        }

        @Override
        public void setSize(float width, float height) {
            Window.setSize((int) width, (int) height);
        }

        @Override
        public void setWidth(float width) {
            Window.setSize((int) width, Window.getHeight());
        }

        @Override
        public void setHeight(float height) {
            Window.setSize(Window.getWidth(), (int) height);
        }
    };

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

        Window.width = config.getWidth();
        Window.height = config.getHeight();
        Window.fullscreen = config.isFullscreen();
        Window.borderless = !config.isDecorated();
        Window.resizable = config.isResizable();
        Window.swapInterval = config.getSwapInterval();

        config.applyWindowHints();

        long monitor = config.isFullscreen() ? glfwGetPrimaryMonitor() : NULL;

        address = glfwCreateWindow(config.getWidth(), config.getHeight(), config.getTitle(), monitor, NULL);
        if (address == NULL) throw new RuntimeException("Failed to create the GLFW window");

        glfwMakeContextCurrent(address);
        GL.createCapabilities();
        glfwSwapInterval(config.getSwapInterval().getValue());

        if (config.getSamples() > 0) glEnable(GL_MULTISAMPLE);

        fbCallback = glfwSetFramebufferSizeCallback(address, (win, newW, newH) -> {
        });

        sizeCallback = glfwSetWindowSizeCallback(address, (win, newW, newH) -> {
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

        posCallback = glfwSetWindowPosCallback(address, (win, newX, newY) -> {
            Window.x = newX;
            Window.y = newY;
        });

        focusCallback = glfwSetWindowFocusCallback(address, (win, focused) -> {
        });

        iconifyCallback = glfwSetWindowIconifyCallback(address, (win, iconified) -> {
        });

        maximizeCallback = glfwSetWindowMaximizeCallback(address, (win, maximized) -> {
        });

        scaleCallback = glfwSetWindowContentScaleCallback(address, (win, xs, ys) -> {
        });

        closeCallback = glfwSetWindowCloseCallback(address, (win) -> glfwSetWindowShouldClose(win, true));

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

    private static void updateDefaultProjectionMatrix() {
        projectionMatrix.ortho(0f, Window.width, 0f, Window.height, -1f, 1f);
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
        glClearColor(color.r(), color.g(), color.b(), color.a());
        glClear(GL_COLOR_BUFFER_BIT);
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
     * Sets fullscreen mode using the primary monitor.
     *
     * <p>When entering fullscreen, this sets the window monitor to the primary monitor and uses the monitor's
     * current video mode dimensions and refresh rate.</p>
     *
     * <p>When leaving fullscreen, this restores the window to its cached position and size.</p>
     *
     * @param fullscreen true for fullscreen, false for windowed
     * @throws RuntimeException if the primary monitor video mode cannot be queried
     */
    public static void setFullscreen(boolean fullscreen) {
        if (address == NULL) return;

        GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vid == null) throw new RuntimeException("Failed to get video mode");

        Window.fullscreen = fullscreen;

        if (fullscreen) {
            glfwSetWindowMonitor(address, glfwGetPrimaryMonitor(), 0, 0, vid.width(), vid.height(), vid.refreshRate());
        } else {
            glfwSetWindowMonitor(address, NULL, getX(), getY(), getWidth(), getHeight(), vid.refreshRate());
        }
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
     * Returns whether the window is currently marked borderless/undecorated.
     *
     * @return true if borderless
     */
    public static boolean isBorderless() {
        return borderless;
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
        GLFWFramebufferSizeCallback framebufferCallback = fbCallback;
        fbCallback = null;
        if (framebufferCallback != null) {
            failure = appendSuppressed(failure, runSafe(framebufferCallback::free));
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

        GLFWWindowIconifyCallback windowIconifyCallback = iconifyCallback;
        iconifyCallback = null;
        if (windowIconifyCallback != null) {
            failure = appendSuppressed(failure, runSafe(windowIconifyCallback::free));
        }

        GLFWWindowMaximizeCallback windowMaximizeCallback = maximizeCallback;
        maximizeCallback = null;
        if (windowMaximizeCallback != null) {
            failure = appendSuppressed(failure, runSafe(windowMaximizeCallback::free));
        }

        GLFWWindowContentScaleCallback windowScaleCallback = scaleCallback;
        scaleCallback = null;
        if (windowScaleCallback != null) {
            failure = appendSuppressed(failure, runSafe(windowScaleCallback::free));
        }

        GLFWWindowCloseCallback windowCloseCallback = closeCallback;
        closeCallback = null;
        if (windowCloseCallback != null) {
            failure = appendSuppressed(failure, runSafe(windowCloseCallback::free));
        }

        long handle = address;
        address = NULL;
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

    static void resetState() {
        fbCallback = null;
        focusCallback = null;
        iconifyCallback = null;
        maximizeCallback = null;
        closeCallback = null;
        posCallback = null;
        sizeCallback = null;
        scaleCallback = null;
        address = NULL;
        x = 0;
        y = 0;
        width = 0;
        height = 0;
        fullscreen = false;
        borderless = false;
        resizable = true;
        swapInterval = SwapInterval.OFF;
        projectionMatrix.identity();
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
     * <p>The returned array is the live backing storage for the current projection state.
     * Callers should treat it as read-only.</p>
     *
     * @return the current projection matrix values in column-major order
     */
    public static float[] getProjectionMatrix() {
        return projectionMatrix.get();
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
        System.arraycopy(matrixData, 0, projectionMatrix.m, 0, 16);
    }

    /**
     * Retrieves a {@link Dimensional} adapter representing the window.
     *
     * <p>This is useful for treating the window like a UI rectangle (size + movable position).</p>
     *
     * @return dimensional adapter bound to the window
     */
    public static Dimensional getDimensional() {
        return dimensional;
    }

    private static Throwable runSafe(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

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
