package valthorne.graphics.shader;

import valthorne.graphics.GraphicsCapabilities;
import valthorne.graphics.ImageAccess;
import valthorne.graphics.TextureFormat;
import valthorne.graphics.bgfx.BgfxComputeImage;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxShader;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Owns a packaged compute program on the active device. Compute is optional;
 * check {@link #isComputeSupported()} before construction. Stage identifiers
 * resolve to binaries for the selected backend. Dispatch creates an ordered pass
 * so subsequent draws see completed writes without waiting on the CPU.
 *
 * <p>Storage buffers contain uint32 elements with four-byte-aligned byte offsets.
 * Image formats must match shader declarations. Buffers and images remain
 * caller-owned and must be released before device shutdown. All operations are
 * confined to the rendering thread; disposal is idempotent.</p>
 *
 * <pre>{@code
 * ComputeShader shader = new ComputeShader("app/update.comp");
 * int image = ComputeShader.createImage2D(128, 128, TextureFormat.RGBA8);
 * ComputeBuffer parameters = new ComputeBuffer(16, ImageAccess.READ);
 * parameters.update(0, initialValues);
 * parameters.bind(1, ImageAccess.READ);
 * ComputeShader.bindImage2D(image, 0, ImageAccess.WRITE, TextureFormat.RGBA8);
 * shader.dispatch(16, 16, 1);
 * }</pre>
 */
public final class ComputeShader {
    private final BgfxShader program; // Owned program and retained uniform storage.

    /**
     * Loads a compiled stage for the active backend.
     *
     * @param stage packaged stage identifier without the binary suffix
     */
    public ComputeShader(String stage) {
        if (!isComputeSupported()) throw new UnsupportedOperationException("The selected device does not support compute");
        if (stage == null) throw new NullPointerException("stage");
        if (stage.startsWith("bgfx:")) stage = stage.substring(5);
        if (stage.isBlank() || stage.contains("..") || stage.indexOf('\n') >= 0 || stage.indexOf('\r') >= 0)
            throw new IllegalArgumentException("A packaged compute stage identifier is required");
        program = new BgfxShader(stage);
    }

    /**
     * Reports compute support on the initialized device.
     *
     * @return whether compute dispatch is available
     */
    public static boolean isComputeSupported() {
        /*
         * Driver capabilities establish support more precisely than renderer names.
         */
        BgfxGraphics.device();
        return (bgfx_get_caps().supported() & BGFX_CAPS_COMPUTE) != 0;
    }

    /**
     * Starts an ordered pass for subsequent dependent work without a CPU wait.
     */
    public static void memoryBarrierAll() {
        /*
         * bgfx performs resource transitions at ordered submission boundaries.
         */
        BgfxGraphics.invalidateView();
    }

    /**
     * Starts an ordered pass for subsequent image consumers without a CPU wait.
     */
    public static void memoryBarrierImage() {
        /*
         * Images follow the same ordered submission boundary as other resources.
         */
        BgfxGraphics.invalidateView();
    }

    /**
     * Binds mip level zero for the next dispatch without transferring ownership.
     *
     * @param image image handle
     * @param unit shader binding index
     * @param access shader access mode
     * @param format allocated image format
     */
    public static void bindImage2D(int image, int unit, ImageAccess access, TextureFormat format) {
        /*
         * Image bindings are independent of sampled texture bindings.
         */
        BgfxGraphics.device();
        if (unit < 0 || unit >= bgfx_get_caps().limits().maxComputeBindings())
            throw new IllegalArgumentException("Image binding exceeds device limits");
        bgfx_set_image(unit, (short) BgfxGraphics.textureHandle(image), 0, access.nativeAccess(), format.nativeFormat());
    }

    /**
     * Allocates an uninitialized sampled image with compute write access.
     *
     * @param width positive width in pixels
     * @param height positive height in pixels
     * @param format shader-compatible storage format
     * @return caller-owned texture handle
     */
    public static int createImage2D(int width, int height, TextureFormat format) {
        /*
         * Write access is an allocation property; validate it before allocating.
         */
        if (!isComputeSupported()) throw new UnsupportedOperationException("Compute images are unavailable");
        int maximum = GraphicsCapabilities.maximumTextureSize();
        if (width < 1 || height < 1 || width > maximum || height > maximum)
            throw new IllegalArgumentException("Compute image dimensions exceed device limits");
        if (!bgfx_is_texture_valid(0, false, 1, format.nativeFormat(), BGFX_TEXTURE_COMPUTE_WRITE))
            throw new UnsupportedOperationException("Compute image format is unavailable on this device");
        return new BgfxComputeImage(width, height, format).id();
    }

    /**
     * Releases a caller-owned image and clears its managed sampler bindings.
     *
     * @param image live texture handle
     */
    public static void deleteImage2D(int image) {
        /*
         * The engine identifier owns its image and follows native handle replacement.
         */
        BgfxComputeImage.dispose(image);
    }

    /**
     * Validates lifetime; dispatch selects the program explicitly.
     */
    public void bind() {
        program.handle();
    }

    /**
     * Ends the caller's binding scope without changing raster program selection.
     */
    public void unbind() {
        program.handle();
    }

    /**
     * Submits workgroups in a dedicated ordered view.
     *
     * @param groupsX X workgroup count from one through 65535
     * @param groupsY Y workgroup count from one through 65535
     * @param groupsZ Z workgroup count from one through 65535
     */
    public void dispatch(int groupsX, int groupsY, int groupsZ) {
        if (groupsX < 1 || groupsY < 1 || groupsZ < 1 || groupsX > 65535 || groupsY > 65535 || groupsZ > 65535)
            throw new IllegalArgumentException("Workgroup counts must be between one and 65535");
        program.apply(BgfxGraphics.textures(), 16);
        BgfxGraphics.invalidateView();
        bgfx_dispatch(BgfxGraphics.view(), program.handle(), groupsX, groupsY, groupsZ, BGFX_DISCARD_ALL);
        BgfxGraphics.invalidateView();
    }

    /**
     * Releases the program; images and buffers remain caller-owned.
     */
    public void dispose() {
        program.close();
    }

    /**
     * Retains an integer scalar uniform for subsequent dispatches.
     *
     * @param name uniform name
     * @param value scalar value
     */
    public void setUniform1i(String name, int value) {
        program.integer(name, value);
    }

    /**
     * Retains a scalar uniform for subsequent dispatches.
     *
     * @param name uniform name
     * @param value scalar value
     */
    public void setUniform1f(String name, float value) {
        program.vector(name, value, 0, 0, 0);
    }

    /**
     * Retains two uniform components for subsequent dispatches.
     *
     * @param name uniform name
     * @param x first component
     * @param y second component
     */
    public void setUniform2f(String name, float x, float y) {
        program.vector(name, x, y, 0, 0);
    }

    /**
     * Retains three uniform components for subsequent dispatches.
     *
     * @param name uniform name
     * @param x first component
     * @param y second component
     * @param z third component
     */
    public void setUniform3f(String name, float x, float y, float z) {
        program.vector(name, x, y, z, 0);
    }

    /**
     * Retains four uniform components for subsequent dispatches.
     *
     * @param name uniform name
     * @param x first component
     * @param y second component
     * @param z third component
     * @param w fourth component
     */
    public void setUniform4f(String name, float x, float y, float z, float w) {
        program.vector(name, x, y, z, w);
    }

    /**
     * Retains integer-valued components in native vector storage.
     *
     * @param name uniform name
     * @param x first component
     * @param y second component
     */
    public void setUniform2i(String name, int x, int y) {
        program.vector(name, x, y, 0, 0);
    }
}
