package valthorne.graphics.bgfx;

import valthorne.graphics.GraphicsResource;
import valthorne.graphics.TextureFormat;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Owns a compute-writable sampled image behind a stable engine texture ID.
 * Device replacement recreates empty storage; the application's compute pass
 * regenerates its contents on the next frame. The identifier remains valid until
 * disposal, while the underlying native handle may change.
 */
public final class BgfxComputeImage implements GraphicsResource, BgfxTextureReference, AutoCloseable {
    private final int width; // Immutable pixel width.
    private final int height; // Immutable pixel height.
    private final TextureFormat format; // Shader-compatible storage format.
    private final int id; // Stable engine texture identifier.
    private short handle = BGFX_INVALID_HANDLE; // Owned native image allocation.
    private boolean closed; // Whether the engine identifier has been released.

    /**
     * Allocates a writable sampled image on the active device.
     *
     * @param width validated positive pixel width
     * @param height validated positive pixel height
     * @param format compatible image format
     */
    public BgfxComputeImage(int width, int height, TextureFormat format) {
        BgfxGraphics.device();
        if (format == null) throw new NullPointerException("format");
        if (width < 1 || height < 1 || width > 65535 || height > 65535)
            throw new IllegalArgumentException("Image dimensions must be between 1 and 65535");
        this.width = width;
        this.height = height;
        this.format = format;
        restoreGraphicsResources();
        int allocatedId = -1;
        try {
            allocatedId = BgfxTextureHandles.register(this);
            BgfxResources.register(this);
        } catch (RuntimeException | Error failure) {
            releaseGraphicsResources();
            BgfxTextureHandles.unregister(allocatedId);
            throw failure;
        }
        id = allocatedId;
    }

    /**
     * Returns the stable engine ID for sampled or compute-image binding.
     *
     * @return engine texture identifier
     */
    public int id() {
        if (closed) throw new IllegalStateException("Compute image is closed");
        return id;
    }

    /**
     * Disposes an image created by the public ComputeShader image API.
     *
     * @param id allocated compute image identifier
     */
    public static void dispose(int id) {
        /*
         * Resolve ownership before destruction, rejecting sampled asset textures.
         */
        BgfxTextureReference texture = BgfxTextureHandles.reference(id);
        if (!(texture instanceof BgfxComputeImage image)) throw new IllegalArgumentException("Not a compute image");
        image.close();
    }

    @Override
    public short handle() {
        BgfxGraphics.device();
        if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Compute image is unavailable");
        return handle;
    }

    @Override
    public void releaseGraphicsResources() {
        if (handle == BGFX_INVALID_HANDLE) return;
        BgfxGraphics.resetTextureState(Short.toUnsignedInt(handle));
        bgfx_destroy_texture(handle);
        handle = BGFX_INVALID_HANDLE;
    }

    @Override
    public void restoreGraphicsResources() {
        BgfxGraphics.device();
        int maximum = bgfx_get_caps().limits().maxTextureSize();
        if (width > maximum || height > maximum) throw new UnsupportedOperationException("Compute image exceeds the selected device size limit");
        if ((bgfx_get_caps().supported() & BGFX_CAPS_COMPUTE) == 0 || !bgfx_is_texture_valid(0, false, 1, format.nativeFormat(), BGFX_TEXTURE_COMPUTE_WRITE))
            throw new UnsupportedOperationException("Compute image format is unavailable on the selected device");
        handle = bgfx_create_texture_2d(width, height, false, 1, format.nativeFormat(), BGFX_TEXTURE_COMPUTE_WRITE | BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP, null, 0);
        if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not restore compute image");
        BgfxGraphics.resetTextureState(Short.toUnsignedInt(handle));
    }

    @Override
    public void close() {
        if (closed) return;
        BgfxResources.unregister(this);
        releaseGraphicsResources();
        BgfxTextureHandles.unregister(id);
        closed = true;
    }
}
