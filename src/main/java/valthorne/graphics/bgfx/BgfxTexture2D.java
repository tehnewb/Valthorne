package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import org.lwjgl.bgfx.BGFXMemory;
import valthorne.graphics.GraphicsResource;
import valthorne.graphics.texture.TextureFilter;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memCalloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Owns one sampled texture uploaded through bgfx-owned memory. This
 * initial desktop API accepts RGBA8, RGBA32F and RG32U data with tightly packed
 * rows. An owned CPU copy of the base level restores the texture
 * after device replacement. Mip storage can be replaced without changing the
 * engine binding ID, preserving queued draws and bound texture slots. Caller
 * staging may be freed immediately; close frees the native texture and its
 * retained upload bytes. Allocation failures retain the previous usable storage.
 */
public final class BgfxTexture2D implements AutoCloseable, GraphicsResource, BgfxTextureReference {
    private final int width; // Retained pixel width for device replacement.
    private final int height; // Retained pixel height for device replacement.
    private final int format; // Immutable sampled texture format.
    private final boolean mutable; // Whether region uploads require mutable native storage.
    private final int pixelBytes; // Packed bytes per texel for validated region uploads.
    private boolean mipmaps; // Whether the retained upload includes a mip chain.
    private ByteBuffer retainedPixels; // Owned immutable base image used for recovery and mip regeneration.
    private int id = -1; // Stable engine binding ID, or negative for standalone allocations.
    private TextureFilter filter; // Optional sampler override restored with this allocation.

    private final BgfxDesktopWindow window; // Borrowed runtime owner.
    private short handle = BGFX_INVALID_HANDLE; // Owned native texture, invalid after close.

    /**
     * Copies validated image bytes into an immutable nearest-sampled texture.
     *
     * @param window initialized runtime owner
     * @param width positive image width
     * @param height positive image height
     * @param format R8, RGB8, RGBA8, RGBA32F or RG32U bgfx texture format
     * @param pixels tightly packed source bytes, or null for a zeroed allocation
     */
    public BgfxTexture2D(BgfxDesktopWindow window, int width, int height, int format, ByteBuffer pixels) {
        this(window, width, height, format, pixels, false);
    }

    /**
     * Uploads an image with optional GPU-generated RGBA8 mip levels. Generation
     * uses the active backend's native filter; only the base pixels are retained
     * for device replacement, and ordinary drawing performs no filtering work.
     *
     * @param window initialized runtime owner
     * @param width positive image width
     * @param height positive image height
     * @param format R8, RGB8, RGBA8, RGBA32F or RG32U format
     * @param pixels tightly packed base-level bytes, or null for a zeroed allocation
     * @param mipmaps whether RGBA8 minification levels should be generated
     */
    public BgfxTexture2D(BgfxDesktopWindow window, int width, int height, int format, ByteBuffer pixels, boolean mipmaps) {
        window.checkOwner();
        if (width < 1 || height < 1 || width > 65535 || height > 65535)
            throw new IllegalArgumentException("Texture dimensions must be between 1 and 65535");
        int maximum = bgfx_get_caps()
                .limits()
                .maxTextureSize();
        if (width > maximum || height > maximum)
            throw new IllegalArgumentException("Texture exceeds this device's maximum size: " + maximum);
        int stride;
        if (format == BGFX_TEXTURE_FORMAT_RGBA8) stride = 4;
        else if (format == BGFX_TEXTURE_FORMAT_R8) stride = 1;
        else if (format == BGFX_TEXTURE_FORMAT_RGB8) stride = 3;
        else if (format == BGFX_TEXTURE_FORMAT_RGBA32F) stride = 16;
        else if (format == BGFX_TEXTURE_FORMAT_RG32U) stride = 8;
        else throw new IllegalArgumentException("Unsupported desktop texture format");
        long size = (long) width * height * stride;
        if (size > Integer.MAX_VALUE || pixels != null && pixels.remaining() != size)
            throw new IllegalArgumentException("Pixel byte count does not match texture dimensions");
        if (!bgfx_is_texture_valid(0, false, 1, format, BGFX_TEXTURE_NONE))
            throw new UnsupportedOperationException("The device cannot sample the requested texture format");
        if (mipmaps && format != BGFX_TEXTURE_FORMAT_RGBA8)
            throw new IllegalArgumentException("Automatic mipmaps require RGBA8 pixels");
        this.window = window;
        this.width = width;
        this.height = height;
        this.format = format;
        this.pixelBytes = stride;
        this.mutable = pixels == null;
        this.mipmaps = mipmaps;
        retainedPixels = pixels == null ? memCalloc((int) size) : copyPixels(pixels);
        try {
            handle = createTexture(retainedPixels, mipmaps);
            if (BgfxGraphics.isActive()) {
                BgfxGraphics.resetTextureState(Short.toUnsignedInt(handle));
                id = BgfxTextureHandles.register(this);
            }
            BgfxResources.register(this);
        } catch (RuntimeException | Error failure) {
            releaseGraphicsResources();
            BgfxTextureHandles.unregister(id);
            memFree(retainedPixels);
            retainedPixels = null;
            throw failure;
        }
    }

    /**
     * Retains base pixels; generated mip levels remain on the graphics device.
     *
     * @param pixels validated tightly packed base pixels
     * @return owned upload buffer positioned at its beginning
     */
    private static ByteBuffer copyPixels(ByteBuffer pixels) {
        /*
         * Generated levels can always be reconstructed from the immutable base,
         * avoiding a second CPU mip chain and preserving the driver's filtering.
         */
        ByteBuffer upload = memAlloc(pixels.remaining());
        try {
            upload.put(pixels.duplicate());
            return upload.clear();
        } catch (RuntimeException | Error failure) {
            memFree(upload);
            throw failure;
        }
    }

    /**
     * Creates a native allocation from a retained upload using default sampling.
     *
     * @param pixels retained base-level pixels
     * @param mipmaps whether the upload includes mip levels
     * @return owned valid native texture handle
     */
    private short createTexture(ByteBuffer pixels, boolean mipmaps) {
        if (mipmaps) return BgfxMipmaps.create(window, pixels, width, height);
        BGFXMemory memory = mutable ? null : bgfx_copy(pixels);
        if (!mutable && memory == null) throw new IllegalStateException("Could not allocate texture upload memory");
        short texture = bgfx_create_texture_2d(width, height, mipmaps, 1, format, BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT | BGFX_SAMPLER_MIP_POINT | BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP, memory, 0);
        if (texture == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not create bgfx texture");
        if (mutable) {
            BGFXMemory initial = bgfx_copy(pixels);
            if (initial == null) {
                bgfx_destroy_texture(texture);
                throw new IllegalStateException("Could not allocate mutable texture initialization");
            }
            bgfx_update_texture_2d(texture, 0, 0, 0, 0, width, height, initial, 0xffff);
        }
        return texture;
    }

    /**
     * Borrows the texture handle for the current runtime.
     *
     * @return valid bgfx texture handle
     */
    public short handle() {
        window.checkOwner();
        if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Texture is closed");
        return handle;
    }

    @Override
    public void close() {
        window.checkOwner();
        if (retainedPixels == null) return;
        BgfxResources.unregister(this);
        releaseGraphicsResources();
        BgfxTextureHandles.unregister(id);
        memFree(retainedPixels);
        retainedPixels = null;
    }

    /**
     * Returns a stable engine binding identifier until this texture is closed.
     *
     * @return engine texture ID, or native handle in standalone mode
     */
    public int id() {
        if (retainedPixels == null) throw new IllegalStateException("Texture is closed");
        return id < 0 ? Short.toUnsignedInt(handle()) : id;
    }

    /**
     * Applies and retains a sampler policy for later device restoration.
     *
     * @param filter sampled minification and magnification policy
     */
    public void setFilter(TextureFilter filter) {
        BgfxGraphics.setTextureFilter(Short.toUnsignedInt(handle()), filter);
        this.filter = filter;
    }

    /**
     * Updates a tightly packed rectangle and its retained recovery pixels.
     * @param x destination column
     * @param y destination row in stored texture order
     * @param width rectangle width
     * @param height rectangle height
     * @param pixels borrowed packed pixels; cursor is preserved
     */
    public void update(int x, int y, int width, int height, ByteBuffer pixels) {
        handle();
        if (!mutable || mipmaps) throw new IllegalStateException("Region updates require mutable texture storage without mipmaps");
        if (x < 0 || y < 0 || width < 1 || height < 1 || width > this.width - x || height > this.height - y || pixels == null || pixels.remaining() != (long) width * height * pixelBytes)
            throw new IllegalArgumentException("Invalid packed texture update rectangle");
        BGFXMemory upload = bgfx_copy(pixels);
        if (upload == null) throw new IllegalStateException("Could not allocate texture update");
        int rowBytes = width * pixelBytes;
        for (int row = 0; row < height; row++)
            retainedPixels.put(((y + row) * this.width + x) * pixelBytes, pixels, pixels.position() + row * rowBytes, rowBytes);
        bgfx_update_texture_2d(handle, 0, 0, x, y, width, height, upload, 0xffff);
    }

    /**
     * Replaces mip storage while retaining this resource and its engine binding ID.
     * Already submitted commands retain their native allocation through bgfx's
     * deferred destruction; pending engine batches resolve the replacement allocation.
     * This configuration operation allocates only when the mip policy changes.
     * Native handles borrowed by standalone callers must be fetched again afterward.
     *
     * @param mipmaps whether to retain generated RGBA8 mip levels
     * @throws IllegalArgumentException if mipmaps are requested for another format
     * @throws IllegalStateException if this texture is closed or allocation fails
     */
    public void setMipmaps(boolean mipmaps) {
        handle();
        if (this.mipmaps == mipmaps) return;
        if (format != BGFX_TEXTURE_FORMAT_RGBA8) throw new IllegalArgumentException("Automatic mipmaps require RGBA8 pixels");
        ByteBuffer base = retainedPixels.duplicate()
                .limit(width * height * 4);
        ByteBuffer replacement = copyPixels(base);
        short replacementHandle = BGFX_INVALID_HANDLE;
        try {
            replacementHandle = createTexture(replacement, mipmaps);
            if (BgfxGraphics.isActive()) {
                BgfxGraphics.resetTextureState(Short.toUnsignedInt(replacementHandle));
                if (filter != null) BgfxGraphics.setTextureFilter(Short.toUnsignedInt(replacementHandle), filter);
            }
        } catch (RuntimeException | Error failure) {
            if (replacementHandle != BGFX_INVALID_HANDLE) bgfx_destroy_texture(replacementHandle);
            memFree(replacement);
            throw failure;
        }
        releaseGraphicsResources();
        memFree(retainedPixels);
        retainedPixels = replacement;
        handle = replacementHandle;
        this.mipmaps = mipmaps;
    }

    @Override
    public void releaseGraphicsResources() {
        if (handle == BGFX_INVALID_HANDLE) return;
        if (BgfxGraphics.isActive()) BgfxGraphics.resetTextureState(Short.toUnsignedInt(handle));
        bgfx_destroy_texture(handle);
        handle = BGFX_INVALID_HANDLE;
    }

    @Override
    public void restoreGraphicsResources() {
        int maximum = bgfx_get_caps().limits().maxTextureSize();
        if (width > maximum || height > maximum || !bgfx_is_texture_valid(0, false, 1, format, BGFX_TEXTURE_NONE))
            throw new UnsupportedOperationException("The replacement device cannot restore this sampled texture");
        handle = createTexture(retainedPixels, mipmaps);
        BgfxGraphics.resetTextureState(Short.toUnsignedInt(handle));
        if (filter != null) BgfxGraphics.setTextureFilter(Short.toUnsignedInt(handle), filter);
    }

}
