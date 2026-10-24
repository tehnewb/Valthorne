package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import org.lwjgl.bgfx.BGFXMemory;
import valthorne.graphics.GraphicsResource;
import valthorne.graphics.texture.TextureFilter;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Owns one sampled texture uploaded through bgfx-owned memory. This
 * initial desktop API accepts RGBA8, RGBA32F and RG32U data with tightly packed
 * rows. An owned CPU copy, including optional mip levels, restores the texture
 * after device replacement. Mip storage can be replaced without changing the
 * engine binding ID, preserving queued draws and bound texture slots. Caller
 * staging may be freed immediately; close frees the native texture and its
 * retained upload bytes. Allocation failures retain the previous usable storage.
 */
public final class BgfxTexture2D implements AutoCloseable, GraphicsResource, BgfxTextureReference {
    private final int width; // Retained pixel width for device replacement.
    private final int height; // Retained pixel height for device replacement.
    private final int format; // Immutable sampled texture format.
    private boolean mipmaps; // Whether the retained upload includes a mip chain.
    private ByteBuffer retainedPixels; // Owned immutable recovery upload including mip levels.
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
     * @param format RGBA8, RGBA32F or RG32U bgfx texture format
     * @param pixels tightly packed source bytes, copied without changing position
     */
    public BgfxTexture2D(BgfxDesktopWindow window, int width, int height, int format, ByteBuffer pixels) {
        this(window, width, height, format, pixels, false);
    }

    /**
     * Uploads an image with optional CPU-generated RGBA8 box-filtered mip levels.
     * Generation writes directly into the retained upload buffer during resource
     * configuration; drawing never allocates intermediate images or filters pixels.
     *
     * @param window initialized runtime owner
     * @param width positive image width
     * @param height positive image height
     * @param format RGBA8, RGBA32F or RG32U format
     * @param pixels tightly packed base-level bytes
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
        else if (format == BGFX_TEXTURE_FORMAT_RGBA32F) stride = 16;
        else if (format == BGFX_TEXTURE_FORMAT_RG32U) stride = 8;
        else throw new IllegalArgumentException("Unsupported desktop texture format");
        long size = (long) width * height * stride;
        if (size > Integer.MAX_VALUE || pixels.remaining() != size)
            throw new IllegalArgumentException("Pixel byte count does not match texture dimensions");
        if (!bgfx_is_texture_valid(0, false, 1, format, BGFX_TEXTURE_NONE))
            throw new UnsupportedOperationException("The device cannot sample the requested texture format");
        if (mipmaps && format != BGFX_TEXTURE_FORMAT_RGBA8)
            throw new IllegalArgumentException("Automatic mipmaps require RGBA8 pixels");
        this.window = window;
        this.width = width;
        this.height = height;
        this.format = format;
        this.mipmaps = mipmaps;
        retainedPixels = copyPixels(pixels, width, height, mipmaps);
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
     * Copies base pixels and optionally appends RGBA8 mip levels in one allocation.
     *
     * @param pixels validated tightly packed base pixels
     * @param width base pixel width
     * @param height base pixel height
     * @param mipmaps whether to generate the RGBA8 mip chain
     * @return owned upload buffer positioned at its beginning
     */
    private static ByteBuffer copyPixels(ByteBuffer pixels, int width, int height, boolean mipmaps) {
        /*
         * Size the complete upload before allocation; mip generation reads each
         * preceding level in place and never needs a second scratch image.
         */
        long total = pixels.remaining();
        if (mipmaps) {
            for (int w = width, h = height; w > 1 || h > 1; ) {
                w = Math.max(1, w / 2);
                h = Math.max(1, h / 2);
                total += (long) w * h * 4;
            }
        }
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("Mip chain exceeds upload limits");
        ByteBuffer upload = memAlloc((int) total);
        try {
            upload.put(pixels.duplicate());
            if (mipmaps) generateMipmaps(upload, width, height);
            return upload.clear();
        } catch (RuntimeException | Error failure) {
            memFree(upload);
            throw failure;
        }
    }

    /**
     * Creates a native allocation from a retained upload using default sampling.
     *
     * @param pixels complete base and optional mip bytes
     * @param mipmaps whether the upload includes mip levels
     * @return owned valid native texture handle
     */
    private short createTexture(ByteBuffer pixels, boolean mipmaps) {
        BGFXMemory memory = bgfx_copy(pixels);
        if (memory == null) throw new IllegalStateException("Could not allocate texture upload memory");
        short texture = bgfx_create_texture_2d(width, height, mipmaps, 1, format, BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT | BGFX_SAMPLER_MIP_POINT | BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP, memory, 0);
        if (texture == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not create bgfx texture");
        return texture;
    }

    /**
     * Appends tightly packed mip levels, including every texel of odd source rows.
     *
     * @param upload native upload containing the complete base level
     * @param width base width
     * @param height base height
     */
    private static void generateMipmaps(ByteBuffer upload, int width, int height) {
        /*
         * Integer area partitions account for odd dimensions without an extra
         * scratch image; previous levels already reside in the upload allocation.
         */
        int sourceOffset = 0;
        int destinationOffset = width * height * 4;
        while (width > 1 || height > 1) {
            int nextWidth = Math.max(1, width / 2);
            int nextHeight = Math.max(1, height / 2);
            for (int y = 0; y < nextHeight; y++) {
                int startY = y * height / nextHeight;
                int endY = (y + 1) * height / nextHeight;
                for (int x = 0; x < nextWidth; x++) {
                    int startX = x * width / nextWidth;
                    int endX = (x + 1) * width / nextWidth;
                    int samples = (endX - startX) * (endY - startY);
                    for (int channel = 0; channel < 4; channel++) {
                        int sum = 0;
                        for (int sy = startY; sy < endY; sy++)
                            for (int sx = startX; sx < endX; sx++)
                                sum += upload.get(sourceOffset + (sy * width + sx) * 4 + channel) & 255;
                        upload.put(destinationOffset + (y * nextWidth + x) * 4 + channel, (byte) ((sum + samples / 2) / samples));
                    }
                }
            }
            sourceOffset = destinationOffset;
            destinationOffset += nextWidth * nextHeight * 4;
            width = nextWidth;
            height = nextHeight;
        }
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
        ByteBuffer replacement = copyPixels(base, width, height, mipmaps);
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
