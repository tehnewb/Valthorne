package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFXPlatformData;
import org.lwjgl.system.Platform;
import valthorne.graphics.GraphicsBackend;

import static org.lwjgl.bgfx.BGFX.BGFX_NATIVE_WINDOW_HANDLE_TYPE_WAYLAND;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window;
import static org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow;
import static org.lwjgl.glfw.GLFWNativeX11.*;
import static org.lwjgl.glfw.GLFWNativeWayland.*;

/**
 * Supplies desktop window handles without creating or borrowing API contexts.
 * Window-system selection is independent of graphics API selection. The browser
 * target requires a separate canvas platform integration.
 */
final class BgfxPlatform {
    /**
     * Prevents constructing the native window-handle adapter.
     */
    private BgfxPlatform() {
    }

    /**
     * Validates platform compatibility before native initialization.
     *
     * @param backend explicitly requested graphics API
     */
    static void validate(GraphicsBackend backend) {
        /*
         * Desktop OpenGL ES is not a browser WebGL runtime. Reject incompatible
         * targets without silently selecting a different renderer.
         */
        if (backend == null) throw new NullPointerException("backend");
        if (backend == GraphicsBackend.WEBGL)
            throw new UnsupportedOperationException("WebGL requires the browser platform runtime; a desktop JVM cannot create a browser canvas");
        if (backend == GraphicsBackend.METAL && Platform.get() != Platform.MACOSX)
            throw new UnsupportedOperationException("Metal requires macOS");
        if ((backend == GraphicsBackend.DIRECTX || backend == GraphicsBackend.DIRECTX12) && Platform.get() != Platform.WINDOWS)
            throw new UnsupportedOperationException("DirectX requires Windows");
    }

    /**
     * Copies native window-system handles into bgfx's platform initialization.
     *
     * @param window live GLFW_NO_API window
     * @param data writable initialization storage
     */
    static void configure(long window, BGFXPlatformData data) {
        /*
         * bgfx creates its own device/context from these native platform handles.
         */
        switch (Platform.get()) {
            case WINDOWS -> data.nwh(glfwGetWin32Window(window));
            case MACOSX -> data.nwh(glfwGetCocoaWindow(window));
            case LINUX -> {
                if (glfwGetPlatform() == GLFW_PLATFORM_WAYLAND)
                    data.ndt(glfwGetWaylandDisplay()).nwh(glfwGetWaylandWindow(window)).type(BGFX_NATIVE_WINDOW_HANDLE_TYPE_WAYLAND);
                else if (glfwGetPlatform() == GLFW_PLATFORM_X11)
                    data.ndt(glfwGetX11Display()).nwh(glfwGetX11Window(window));
                else throw new UnsupportedOperationException("Unsupported Linux window system");
            }
        }
    }
}
