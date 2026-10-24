package valthorne;

import valthorne.graphics.GraphicsViewport;
import valthorne.graphics.bgfx.BgfxGraphics;

import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;

/**
 * Platform rendering and diagnostic operations shared by application launchers.
 */
public final class PlatformTools {
    /**
     * Release value used by PlatformTools.
     */
    public static final int RELEASE = 0;
    /**
     * Input press action used when replaying callbacks.
     */
    public static final int PRESS = 1;
    /**
     * Input repeat action used when replaying callbacks.
     */
    public static final int REPEAT = 2;

    /**
     * Prevents constructing the process-wide platform diagnostics facade.
     */
    private PlatformTools() {
    }

    /**
     * Sets a bottom-left physical viewport on the active graphics backend.
     *
     * @param x physical left edge
     * @param y physical bottom edge
     * @param width physical width
     * @param height physical height
     */
    public static void viewport(int x, int y, int width, int height) {
        /*
         * The shared facade converts native viewport orientation when needed.
         */
        GraphicsViewport.set(x, y, width, height);
    }

    /**
     * Completes queued GPU work without swapping the window. Finish public batches first.
     */
    public static void finish() {
        /*
         * Readback completes queued work without presenting the window.
         */
        BgfxGraphics.capture("");
    }

    /**
     * Validates the graphics device; submission failures propagate as exceptions.
     *
     * @return zero when device validation succeeds
     */
    public static int graphicsError() {
        /*
         * Device failures propagate as exceptions at submission boundaries.
         */
        BgfxGraphics.device().checkOwner();
        return 0;
    }

    /**
     * Returns the initialized graphics renderer identity.
     *
     * @return driver renderer description
     */
    public static String rendererName() {
        /*
         * Capabilities retain the initialized renderer identity.
         */
        return Window.getGraphicsCapabilities().renderer();
    }

    /**
     * Selects the sampled texture slot used by subsequent Texture.bind calls.
     *
     * @param unit zero-based texture slot
     */
    public static void textureUnit(int unit) {
        /*
         * Texture bindings are tracked in engine state for all backends.
         */
        BgfxGraphics.textureUnit(unit);
    }

    /**
     * Writes the current rendered image as PNG. bgfx captures the full physical
     * backbuffer and completes readback before returning, without presentation.
     * Finish pending batches before capture.
     *
     * @param path PNG destination path
     * @param width retained launcher parameter; capture uses the physical backbuffer width
     * @param height retained launcher parameter; capture uses the physical backbuffer height
     */
    public static void capture(String path, int width, int height) {
        /*
         * The renderer callback encodes the physical backbuffer after readback.
         */
        BgfxGraphics.capture(path);
    }

    /**
     * Replays input through the installed engine callbacks for deterministic diagnostics.
     */
    public static void injectKey(int code, int action) {
        long window = Window.getAddress();
        var callback = glfwSetKeyCallback(window, null);
        glfwSetKeyCallback(window, callback);
        if (callback != null) callback.invoke(window, code, 0, action, 0);
    }

    /**
     * Positions use the engine's bottom-left coordinate convention.
     */
    public static void injectPointer(float x, float y, int button, int action) {
        long window = Window.getAddress();
        var cursor = glfwSetCursorPosCallback(window, null);
        glfwSetCursorPosCallback(window, cursor);
        if (cursor != null) cursor.invoke(window, x, Window.getHeight() - y);
        if (button >= 0) {
            var callback = glfwSetMouseButtonCallback(window, null);
            glfwSetMouseButtonCallback(window, callback);
            if (callback != null) callback.invoke(window, button, action, 0);
        }
    }
}
