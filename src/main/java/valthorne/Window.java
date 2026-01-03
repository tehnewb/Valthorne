package valthorne;

import valthorne.event.events.WindowResizeEvent;
import valthorne.event.listeners.WindowResizeListener;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

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

        if (address == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        glfwMakeContextCurrent(address);
        glfwSwapInterval(swapInterval.getInterval());
        GL.createCapabilities();

        // Set up all window callbacks
        fbCallback = glfwSetFramebufferSizeCallback(address, (win, newW, newH) -> {
        });
        sizeCallback = glfwSetWindowSizeCallback(address, (win, newW, newH) -> {
            short oldWidth = Window.width;
            short oldHeight = Window.height;
            Window.width = (short) newW;
            Window.height = (short) newH;

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
        focusCallback = glfwSetWindowFocusCallback(address, (win, focused) -> {
        });
        iconifyCallback = glfwSetWindowIconifyCallback(address, (win, iconified) -> {
        });
        maximizeCallback = glfwSetWindowMaximizeCallback(address, (win, maximized) -> {
        });
        scaleCallback = glfwSetWindowContentScaleCallback(address, (win, xs, ys) -> {
        });
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
        if (listener == null)
            throw new NullPointerException("A null WindowResizeListener cannot be added");
        JGL.subscribe(WindowResizeEvent.class, listener);
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
     * Sets the window fullscreen state.
     *
     * @param fullscreen true for fullscreen, false for windowed
     */
    public static void setFullscreen(boolean fullscreen) {
        if (address == NULL) return;

        GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vid == null)
            throw new RuntimeException("Failed to get video mode");

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
     * Sets the swap interval (VSync).
     *
     * @param type The swap interval type
     */
    public static void setSwapInterval(SwapInterval type) {
        if (type == null) return;
        swapInterval = type;
        glfwSwapInterval(type.getInterval());
    }

    /**
     * @return Current SwapInterval
     */
    public static SwapInterval getSwapInterval() {
        return swapInterval;
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
}