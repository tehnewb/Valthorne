package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import org.lwjgl.bgfx.BGFXMemory;
import valthorne.browser.BrowserNative;

import static org.lwjgl.bgfx.BGFX.*;
import static valthorne.browser.BrowserOperation.MIPMAP_FRAMEBUFFER;

/**
 * Creates RGBA8 mip chains with WebGL's native generator through a bgfx resolve.
 * The base image is uploaded once and retained by the shared texture owner for
 * context restoration. No CPU mip images or per-pixel Java work are required.
 * Creation preserves the current target, viewport, textures and clipping.
 */
final class BgfxMipmaps {
    /**
     * Prevents constructing stateless native upload operations.
     */
    private BgfxMipmaps() {
    }

    /**
     * Uploads a base image and queues mip generation before later sampling.
     *
     * @param window owning initialized browser device
     * @param pixels tightly packed RGBA8 base pixels; cursor is preserved
     * @param width positive base width
     * @param height positive base height
     * @return owned texture with a complete GPU-generated mip chain
     */
    static short create(BgfxDesktopWindow window, ByteBuffer pixels, int width, int height) {
        /*
         * A resolve-only view uses the same native operation as desktop bgfx,
         * preserving dependencies without copying generated pixels back to Java.
         */
        window.checkOwner();
        BgfxGraphics.reserveUploadViews(2);
        short texture = bgfx_create_texture_2d(width, height, true, 1, BGFX_TEXTURE_FORMAT_RGBA8, BGFX_TEXTURE_RT | BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT | BGFX_SAMPLER_MIP_POINT | BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP, null, 0);
        if (texture == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not allocate mipmapped texture");
        short framebuffer = BGFX_INVALID_HANDLE;
        BgfxRenderState saved = new BgfxRenderState();
        try {
            BGFXMemory upload = bgfx_copy(pixels);
            if (upload == null) throw new IllegalStateException("Could not allocate base texture upload");
            bgfx_update_texture_2d(texture, 0, 0, 0, 0, width, height, upload, 0xffff);
            BrowserNative.word(0, Short.toUnsignedInt(texture));
            framebuffer = (short) BrowserNative.call(MIPMAP_FRAMEBUFFER);
            if (framebuffer == BGFX_INVALID_HANDLE) {
                BgfxGraphics.flushUploads();
                BrowserNative.word(0, Short.toUnsignedInt(texture));
                framebuffer = (short) BrowserNative.call(MIPMAP_FRAMEBUFFER);
            }
            if (framebuffer == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not create mip-generation framebuffer");
            saved.store();
            BgfxGraphics.setTarget(Short.toUnsignedInt(framebuffer), width, height);
            BgfxGraphics.setViewport(0, 0, width, height);
            BgfxGraphics.setScissor(0, 0, 0, 0, false);
            bgfx_touch(BgfxGraphics.view());
            return texture;
        } catch (RuntimeException | Error failure) {
            bgfx_destroy_texture(texture);
            throw failure;
        } finally {
            saved.restore();
            if (framebuffer != BGFX_INVALID_HANDLE) bgfx_destroy_frame_buffer(framebuffer);
        }
    }
}
