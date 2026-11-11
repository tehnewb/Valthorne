package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFX;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.Platform;

/**
 * Selects the bundled Windows bgfx runtime before LWJGL initializes its bindings.
 * It matches LWJGL 3.4.3's native API and corrects Direct3D 12 screenshots to read
 * the current swapchain buffer without an extra presentation, and resolves
 * Vulkan MSAA screenshots before readback. Other platforms
 * retain LWJGL's runtime. An explicit org.lwjgl.bgfx.libname override remains
 * under the application's ownership, including its native behavior.
 */
final class BgfxNativeLibrary {
    /**
     * Prevents constructing the native library selection policy.
     */
    private BgfxNativeLibrary() {
    }

    /**
     * Selects the corrected Windows library unless the application selected one.
     * Call before any bgfx binding initialization; repeated calls are harmless.
     * @throws IllegalStateException if default LWJGL bindings already loaded another runtime
     */
    static void configure() {
        /*
         * A distinct resource name avoids classpath-order collisions with LWJGL's
         * native JAR. Its ordinary loader owns extraction and process lifetime.
         */
        if (Platform.get() != Platform.WINDOWS) return;
        if (Configuration.BGFX_LIBRARY_NAME.get() == null)
            Configuration.BGFX_LIBRARY_NAME.set("valthorne-bgfx.dll");
        if ("valthorne-bgfx.dll".equals(Configuration.BGFX_LIBRARY_NAME.get()) && !BGFX.getLibrary().getName().endsWith("valthorne-bgfx.dll"))
            throw new IllegalStateException("bgfx bindings were initialized before Valthorne selected its runtime; set org.lwjgl.bgfx.libname=valthorne-bgfx.dll before the first bgfx reference");
    }
}
