package valthorne.graphics;

import org.lwjgl.bgfx.BGFXCaps;
import valthorne.graphics.bgfx.BgfxGraphics;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Immutable feature report from the active graphics device. Renderer names never
 * substitute for feature checks: browser/raster devices may omit compute or HDR
 * attachments while retaining the same scene, sprite, text and UI drawing APIs.
 *
 * @param vendor device vendor identifier
 * @param renderer selected API renderer name
 * @param version rendering runtime identity
 * @param raster whether the engine's raster pipeline is supported
 * @param compute whether compute dispatch and writable resources are available
 */
public record GraphicsCapabilities(String vendor, String renderer, String version, boolean raster, boolean compute) {
    /**
     * Returns the active device's maximum texture extent without a report allocation.
     *
     * @return maximum pixels per texture axis
     */
    public static int maximumTextureSize() {
        /*
         * Native limits are authoritative across all renderer implementations.
         */
        BgfxGraphics.device();
        return bgfx_get_caps().limits().maxTextureSize();
    }

    /**
     * Captures capabilities on the render thread after graphics initialization.
     *
     * @return immutable active-device feature report
     */
    public static GraphicsCapabilities current() {
        /*
         * The renderer owns API detection; no API-specific context table is queried.
         */
        BgfxGraphics.device();
        BGFXCaps caps = bgfx_get_caps();
        return new GraphicsCapabilities(Integer.toString(Short.toUnsignedInt(caps.vendorId())), bgfx_get_renderer_name(caps.rendererType()), "bgfx", (caps.supported() & BGFX_CAPS_INSTANCING) != 0, (caps.supported() & BGFX_CAPS_COMPUTE) != 0);
    }

    /**
     * Rejects devices lacking the instance-based engine raster contract.
     */
    public void requireRaster() {
        if (!raster) throw new UnsupportedOperationException("The selected device does not support the engine raster contract: " + renderer);
    }
}
