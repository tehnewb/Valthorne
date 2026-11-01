package valthorne.android.internal;

import android.view.Surface;

/**
 * Owns the native bgfx renderer attached to one Android surface. Creation, drawing,
 * resize and release belong to the creating Java render thread; bgfx executes
 * backend rendering on its dedicated native worker. The native side retains
 * its own ANativeWindow reference until shutdown. This internal platform boundary
 * does not load desktop LWJGL or create a browser/WebView runtime.
 */
final class AndroidNativeDevice implements AutoCloseable {
    static {
        System.loadLibrary("valthorne_android");
    }

    private final Thread owner = Thread.currentThread(); // Thread owning this device and bgfx's frame state.
    private long handle; // Native device pointer, or zero after release.

    /**
     * Creates an OpenGL ES or Vulkan device with a dedicated native render worker.
     * @param surface live Android presentation surface
     * @param vulkan true for Vulkan, false for OpenGL ES 3
     */
    AndroidNativeDevice(Surface surface, boolean vulkan) {
        if (surface == null || !surface.isValid()) throw new IllegalArgumentException("A valid Android surface is required");
        handle = create(surface, vulkan);
        if (handle == 0) throw new IllegalStateException("Could not initialize the Android bgfx renderer");
    }

    /**
     * Completes pending presentation work before Android may destroy its surface.
     * Flush frames do not present and retain the application's resource graph.
     */
    void quiesce() {
        checkOwner();
        if (handle == 0) throw new IllegalStateException("Android graphics device is closed");
        quiesceNative();
    }

    /**
     * Rejects device access from UI callbacks or unrelated worker threads.
     */
    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Android graphics belongs to its render thread");
    }

    @Override
    public void close() {
        checkOwner();
        if (handle == 0) return;
        destroy(handle);
        handle = 0;
    }

    /**
     * Retains the surface and initializes bgfx on this native calling thread.
     * @param surface live Java surface
     * @param vulkan requested graphics API
     * @return owned native device pointer, or zero on initialization failure
     */
    private static native long create(Surface surface, boolean vulkan);

    /**
     * Drains the backend's queued frame before acknowledging surface loss.
     */
    private static native void quiesceNative();

    /**
     * Shuts down bgfx before releasing the retained native window.
     * @param handle owned device pointer
     */
    private static native void destroy(long handle);
}
