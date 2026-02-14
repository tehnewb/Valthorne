package valthorne;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import valthorne.event.events.WindowResizeEvent;
import valthorne.event.listeners.WindowResizeListener;
import valthorne.ui.Dimensional;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * The Window class serves as a utility for managing an OpenGL-based window
 * using GLFW. It provides methods for initializing, configuring, and disposing
 * of the window, as well as handling window events and states such as resizing,
 * full-screen toggling, and setting various properties.
 *
 * @author Albert Beaupre
 * @since October 17th, 2025
 */
public final class Window {

    private static final WindowResizeEvent resizeEvent = new WindowResizeEvent(0, 0, 0, 0);

    // GLFW callback handlers
    private static GLFWFramebufferSizeCallback fbCallback;
    private static GLFWWindowFocusCallback focusCallback;
    private static GLFWWindowIconifyCallback iconifyCallback;
    private static GLFWWindowMaximizeCallback maximizeCallback;
    private static GLFWWindowCloseCallback closeCallback;
    private static GLFWWindowPosCallback posCallback;
    private static GLFWWindowSizeCallback sizeCallback;
    private static GLFWWindowContentScaleCallback scaleCallback;

    // Window properties
    private static SwapInterval swapInterval = SwapInterval.OFF;
    private static long address;
    private static short x, y;
    private static short width, height;
    private static boolean fullscreen = false;
    private static boolean borderless = false;
    private static boolean resizable = true;

    /**
     * Initializes the GLFW window with the specified parameters.
     *
     * @param title  Window title
     * @param width  Initial window width
     * @param height Initial window height
     */
    public static void init(String title, int width, int height) {
        Window.width = (short) width;
        Window.height = (short) height;

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_SAMPLES, 4);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DOUBLEBUFFER, GLFW_TRUE);

        address = glfwCreateWindow(width, height, title, NULL, NULL);

        if (address == NULL) throw new RuntimeException("Failed to create the GLFW window");

        glfwMakeContextCurrent(address);
        glfwSwapInterval(swapInterval.getInterval());
        GL.createCapabilities();

        fbCallback = glfwSetFramebufferSizeCallback(address, (win, newW, newH) -> {
        });
        sizeCallback = glfwSetWindowSizeCallback(address, (win, newW, newH) -> {
            if (newW <= 0 || newH <= 0) return;

            short oldWidth = Window.width;
            short oldHeight = Window.height;

            Window.width = (short) newW;
            Window.height = (short) newH;

            // Update GL viewport + projection to match new window size.
            glViewport(0, 0, newW, newH);

            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, newW, 0, newH, -1, 1);

            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            resizeEvent.setOldHeight(oldHeight);
            resizeEvent.setOldWidth(oldWidth);
            resizeEvent.setNewHeight(Window.height);
            resizeEvent.setNewWidth(Window.width);
            JGL.publish(resizeEvent);
        });

        posCallback = glfwSetWindowPosCallback(address, (win, newX, newY) -> {
            Window.x = (short) newX;
            Window.y = (short) newY;
        });
        focusCallback = glfwSetWindowFocusCallback(address, (win, focused) -> {});
        iconifyCallback = glfwSetWindowIconifyCallback(address, (win, iconified) -> {});
        maximizeCallback = glfwSetWindowMaximizeCallback(address, (win, maximized) -> {});
        scaleCallback = glfwSetWindowContentScaleCallback(address, (win, xs, ys) -> {});
        closeCallback = glfwSetWindowCloseCallback(address, (win) -> glfwSetWindowShouldClose(win, true));

        // Center window on screen
        GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vid != null) {
            int centerX = (vid.width() - width) / 2;
            int centerY = (vid.height() - height) / 2;
            glfwSetWindowPos(address, centerX, centerY);
        }

        glfwShowWindow(address);
        glViewport(0, 0, width, height);

        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glEnable(GL_TEXTURE_2D);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, Window.getWidth(), 0, Window.getHeight(), -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    /**
     * Adds a window resize event listener.
     *
     * @param listener The listener to add
     * @throws NullPointerException if listener is null
     */
    public static void addWindowResizeListener(WindowResizeListener listener) {
        if (listener == null) throw new NullPointerException("A null WindowResizeListener cannot be added");
        JGL.subscribe(WindowResizeEvent.class, listener);
    }

    /**
     * Removes a window resize event listener.
     *
     * @param listener The listener to remove
     * @throws NullPointerException if the listener is null
     */
    public static void removeWindowResizeListener(WindowResizeListener listener) {
        if (listener == null) throw new NullPointerException("A null WindowResizeListener cannot be removed");
        JGL.unsubscribe(WindowResizeEvent.class, listener);
    }

    /**
     * @return true if window should close, false otherwise
     */
    static boolean shouldClose() {
        return glfwWindowShouldClose(address);
    }

    /**
     * Sets the window title.
     *
     * @param newTitle The new window title
     */
    public static void setTitle(String newTitle) {
        glfwSetWindowTitle(address, newTitle);
    }

    /**
     * Sets whether the window is resizable.
     * <p>
     * This affects the window immediately after creation.
     * GLFW allows changing this at runtime via window attributes.
     *
     * @param resizable true to allow resizing, false to lock the window size
     */
    public static void setResizable(boolean resizable) {
        if (address == NULL)
            return;
        Window.resizable = resizable;
        glfwSetWindowAttrib(address, GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
    }

    /**
     * Sets whether the window is borderless (undecorated).
     * <p>
     * A borderless window has no title bar or borders.
     * This can be changed at runtime.
     *
     * @param borderless true for borderless, false for normal window decorations
     */
    public static void setBorderless(boolean borderless) {
        if (address == NULL)
            return;
        Window.borderless = borderless;
        glfwSetWindowAttrib(address, GLFW_DECORATED, borderless ? GLFW_FALSE : GLFW_TRUE);
    }

    /**
     * Sets the window fullscreen state.
     *
     * @param fullscreen true for fullscreen, false for windowed
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
     * @param width  New window width
     * @param height New window height
     */
    public static void setSize(int width, int height) {
        glfwSetWindowSize(address, width, height);
    }

    /**
     * @return Window width
     */
    public static int getWidth() {
        return width;
    }

    /**
     * @return Window height
     */
    public static int getHeight() {
        return height;
    }

    /**
     * @return Window X coordinate
     */
    public static int getX() {
        return x;
    }

    /**
     * @return Window Y coordinate
     */
    public static int getY() {
        return y;
    }

    /**
     * Gets the framebuffer size.
     *
     * @return Array containing framebuffer width and height
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
     * @return Current SwapInterval
     */
    public static SwapInterval getSwapInterval() {
        return swapInterval;
    }

    /**
     * Sets the swap interval (VSync).
     *
     * @param type The swap interval type
     */
    public static void setSwapInterval(SwapInterval type) {
        if (type == null) return;
        swapInterval = type;
        glfwSwapInterval(type.getInterval());
    }

    public static boolean isFullscreen() {
        return fullscreen;
    }

    public static boolean isBorderless() {
        return borderless;
    }

    public static boolean isResizable() {
        return resizable;
    }

    public static void toggleFullscreen() {
        setFullscreen(!fullscreen);
        fullscreen = !fullscreen;
    }

    public static void minimize() {
        if (address == NULL) return;
        glfwIconifyWindow(address);
    }

    public static void maximize() {
        if (address == NULL) return;
        glfwMaximizeWindow(address);
    }

    public static void restore() {
        if (address == NULL) return;
        glfwRestoreWindow(address);
    }

    public static void focus() {
        if (address == NULL) return;
        glfwFocusWindow(address);
    }

    public static void requestClose() {
        if (address == NULL) return;
        glfwSetWindowShouldClose(address, true);
    }

    public static void setAlwaysOnTop(boolean value) {
        if (address == NULL) return;
        glfwSetWindowAttrib(address, GLFW_FLOATING, value ? GLFW_TRUE : GLFW_FALSE);
    }

    public static void setOpacity(float opacity) {
        if (address == NULL) return;
        opacity = Math.max(0f, Math.min(1f, opacity));
        glfwSetWindowOpacity(address, opacity);
    }

    public static void center() {
        if (address == NULL) return;

        GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vid == null) return;

        int cx = (vid.width() - getWidth()) / 2;
        int cy = (vid.height() - getHeight()) / 2;
        glfwSetWindowPos(address, cx, cy);
    }

    public static void setSizeLimits(int minW, int minH, int maxW, int maxH) {
        if (address == NULL) return;
        glfwSetWindowSizeLimits(address, minW, minH, maxW, maxH);
    }

    /**
     * Disposes of window resources.
     */
    static void dispose() {
        if (fbCallback != null) fbCallback.free();
        if (sizeCallback != null) sizeCallback.free();
        if (posCallback != null) posCallback.free();
        if (focusCallback != null) focusCallback.free();
        if (iconifyCallback != null) iconifyCallback.free();
        if (maximizeCallback != null) maximizeCallback.free();
        if (scaleCallback != null) scaleCallback.free();
        if (closeCallback != null) closeCallback.free();
        if (address != NULL) glfwDestroyWindow(address);
    }

    /**
     * @return GLFW window handle
     */
    public static long getAddress() {
        return address;
    }

    /**
     * A static instance of the {@link Dimensional} interface that provides access to the window's
     * positional and dimensional properties. This particular implementation fetches the width
     * and height dynamically from the {@link Window} class and provides default values of `0`
     * for the X and Y coordinates.
     *
     * <ul>
     * <li>{@code getX()} - Returns a constant value of `0`.</li>
     * <li>{@code getY()} - Returns a constant value of `0`.</li>
     * <li>{@code getWidth()} - Dynamically retrieves the current width of the {@link Window}
     * using {@code Window.getWidth()}.</li>
     * <li>{@code getHeight()} - Dynamically retrieves the current height of the {@link Window}
     * using {@code Window.getHeight()}.</li>
     * </ul>
     * <p>
     * This implementation is particularly useful for accessing or representing window dimensions
     * and is synchronized with the runtime properties of the {@link Window} class.
     */
    private static final Dimensional dimensional = new Dimensional() {
        @Override
        public float getX() {
            return 0;
        }

        @Override
        public float getY() {
            return 0;
        }

        @Override
        public float getWidth() {
            return Window.getWidth();
        }

        @Override
        public float getHeight() {
            return Window.getHeight();
        }
    };

    /**
     * Retrieves the current {@link Dimensional} instance associated with the window.
     *
     * @return the current Dimensional instance representing the size and location of the window
     */
    public static Dimensional getDimensional() {
        return dimensional;
    }
}