package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.WNDCLASSEX;
import org.lwjgl.system.windows.WindowProc;

import static org.lwjgl.system.windows.User32.*;
import static org.lwjgl.system.windows.WinBase.GetModuleHandle;

/**
 * Owns a replaceable Windows presentation surface inside the persistent GLFW
 * application window. Drivers can retain window presentation associations across
 * different APIs, so each device lifetime receives a fresh child HWND. The child
 * is disabled for input; the parent retains keyboard, pointer, focus and window
 * callbacks. All operations belong to the GLFW application thread.
 */
final class BgfxWindowsSurface implements AutoCloseable {
    /**
     * Private window class used only while the single bgfx runtime owns a surface.
     */
    private static final String CLASS_NAME = "Valthorne.Bgfx.Presentation";
    /**
     * Dedicated device context required by desktop graphics drivers.
     */
    private static final int OWN_DEVICE_CONTEXT = 0x0020;

    private final long instance; // Process module owning the registered window class.
    private final WindowProc procedure; // Callback retained until the child window is destroyed.
    private boolean registered; // Whether this object owns a native class registration.
    private long handle; // Child presentation HWND, or zero after destruction.
    private boolean closed; // Whether the callback and native class have been released.

    /**
     * Creates an input-transparent child matching the parent's content pixels.
     *
     * @param parent persistent application HWND
     * @param width positive framebuffer width
     * @param height positive framebuffer height
     */
    BgfxWindowsSurface(long parent, int width, int height) {
        instance = GetModuleHandle(null, (ByteBuffer) null);
        procedure = WindowProc.create((window, message, wParam, lParam) -> DefWindowProc(window, message, wParam, lParam));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.callocInt(1);
            WNDCLASSEX type = WNDCLASSEX.calloc(stack)
                    .cbSize(WNDCLASSEX.SIZEOF)
                    .style(OWN_DEVICE_CONTEXT)
                    .lpfnWndProc(procedure)
                    .hInstance(instance)
                    .lpszClassName(stack.UTF16(CLASS_NAME));
            if (RegisterClassEx(error, type) == 0)
                throw new IllegalStateException("Could not register the presentation surface: " + error.get(0));
            registered = true;
            handle = CreateWindowEx(error, WS_EX_NOPARENTNOTIFY, CLASS_NAME, "", WS_CHILD | WS_VISIBLE | WS_DISABLED, 0, 0, width, height, parent, 0, instance, 0);
            if (handle == 0) throw new IllegalStateException("Could not create the presentation surface: " + error.get(0));
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Returns the HWND borrowed by bgfx until renderer shutdown.
     *
     * @return live presentation HWND
     */
    long handle() {
        return handle;
    }

    /**
     * Keeps the child aligned with the persistent parent's content area.
     *
     * @param width positive framebuffer width
     * @param height positive framebuffer height
     */
    void resize(int width, int height) {
        if (!MoveWindow(null, handle, 0, 0, width, height, false))
            throw new IllegalStateException("Could not resize the presentation surface");
    }

    @Override
    public void close() {
        if (closed) return;
        if (handle != 0) {
            if (!DestroyWindow(null, handle)) throw new IllegalStateException("Could not destroy the presentation surface");
            handle = 0;
        }
        if (registered) {
            if (!UnregisterClass(null, CLASS_NAME, instance)) throw new IllegalStateException("Could not unregister the presentation surface");
            registered = false;
        }
        procedure.free();
        closed = true;
    }
}
