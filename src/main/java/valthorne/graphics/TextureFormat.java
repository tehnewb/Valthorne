package valthorne.graphics;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Portable texture storage formats used by raster and compute resources. The
 * device validates each format's requested usage before allocating native storage.
 */
public enum TextureFormat {
    /**
     * Four normalized eight-bit color channels.
     */
    RGBA8(BGFX_TEXTURE_FORMAT_RGBA8),
    /**
     * Four half-precision floating-point channels.
     */
    RGBA16F(BGFX_TEXTURE_FORMAT_RGBA16F),
    /**
     * Four single-precision floating-point channels.
     */
    RGBA32F(BGFX_TEXTURE_FORMAT_RGBA32F),
    /**
     * One single-precision floating-point channel.
     */
    R32F(BGFX_TEXTURE_FORMAT_R32F),
    /**
     * One unsigned thirty-two-bit integer channel.
     */
    R32_UINT(BGFX_TEXTURE_FORMAT_R32U);

    private final int nativeFormat; // Renderer-independent bgfx texture format.

    /**
     * Stores the portable native storage identifier.
     *
     * @param nativeFormat bgfx format shared by all API implementations
     */
    TextureFormat(int nativeFormat) {
        this.nativeFormat = nativeFormat;
    }

    /**
     * Returns the format consumed by native resource allocation.
     *
     * @return portable native format identifier
     */
    public int nativeFormat() {
        return nativeFormat;
    }
}
