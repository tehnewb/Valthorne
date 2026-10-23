package valthorne.graphics.bgfx;

import valthorne.graphics.font.slug.SlugMetrics;
import valthorne.graphics.font.slug.SlugOutlines;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Uploads the shared CPU Slug compiler's live curve and band data to bgfx.
 * No bitmap glyphs are generated. RGBA32F curves preserve compiler precision and
 * RG32U bands preserve integer addresses. The font owns two textures; its CPU
 * metrics are borrowed and may be shared with another font or rendering backend.
 * Close this object before its window, on the creating render thread.
 */
public final class BgfxSlugFont implements AutoCloseable {
    private final SlugMetrics metrics; // Shared layout state without retaining upload buffers.
    private BgfxTexture2D curves; // Owned quadratic control-point texture.
    private BgfxTexture2D bands; // Owned unsigned band-index texture.

    /**
     * Uploads row-padded compiler output with no OpenGL calls.
     *
     * @param window initialized runtime owner
     * @param outlines CPU-compiled font; only its metrics are retained after upload
     */
    public BgfxSlugFont(BgfxDesktopWindow window, SlugOutlines outlines) {
        window.checkOwner();
        if (outlines == null) throw new NullPointerException("outlines");
        metrics = outlines.metrics();
        FloatBuffer sourceCurves = outlines.curves();
        IntBuffer sourceBands = outlines.bands();
        ByteBuffer staging = memAlloc(Math.max(sourceCurves.remaining() * Float.BYTES, sourceBands.remaining() * Integer.BYTES));
        try {
            int curveBytes = sourceCurves.remaining() * Float.BYTES;
            staging.asFloatBuffer()
                    .put(sourceCurves);
            staging.limit(curveBytes);
            curves = new BgfxTexture2D(window, SlugOutlines.TEXTURE_WIDTH, curveBytes / (SlugOutlines.TEXTURE_WIDTH * 16), BGFX_TEXTURE_FORMAT_RGBA32F, staging);
            staging.clear();
            int bandBytes = sourceBands.remaining() * Integer.BYTES;
            staging.asIntBuffer()
                    .put(sourceBands);
            staging.limit(bandBytes);
            bands = new BgfxTexture2D(window, SlugOutlines.TEXTURE_WIDTH, bandBytes / (SlugOutlines.TEXTURE_WIDTH * 8), BGFX_TEXTURE_FORMAT_RG32U, staging);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        } finally {
            memFree(staging);
        }
    }

    /**
     * Returns shared CPU layout information.
     *
     * @return immutable compiler output
     */
    public SlugMetrics metrics() {
        return metrics;
    }

    /**
     * Borrows the native curve texture for a text batch.
     *
     * @return control-point texture handle
     */
    public short curves() {
        if (curves == null) throw new IllegalStateException("Font is closed");
        return curves.handle();
    }

    /**
     * Borrows the native band texture for a text batch.
     *
     * @return unsigned band-data texture handle
     */
    public short bands() {
        if (bands == null) throw new IllegalStateException("Font is closed");
        return bands.handle();
    }

    @Override
    public void close() {
        if (bands != null) bands.close();
        if (curves != null) curves.close();
        bands = curves = null;
    }
}
