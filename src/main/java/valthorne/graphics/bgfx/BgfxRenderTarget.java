package valthorne.graphics.bgfx;

import valthorne.graphics.GraphicsResource;
import valthorne.graphics.texture.TextureFilter;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Owns a sampled render target and its color attachment, including optional sRGB
 * encoding or floating-point storage. The framebuffer transfers
 * attachment ownership to bgfx, so disposal destroys exactly one native resource
 * tree. Instances are confined to the owning window's render thread.
 */
public final class BgfxRenderTarget implements AutoCloseable, GraphicsResource {
    private final boolean depthStencil; // Whether a depth/stencil attachment is required.
    private final int format; // Retained attachment format for recreation.
    private final long samplerFlags; // Immutable attachment sampling restored with each allocation.
    private final boolean srgb; // Whether the attachment encodes linear shader output as sRGB.

    private final BgfxDesktopWindow window; // Borrowed runtime owner that must outlive this target.
    private short framebuffer = BGFX_INVALID_HANDLE; // Owned framebuffer and color attachment.
    private short texture = BGFX_INVALID_HANDLE; // Borrowed color attachment handle.
    private final int width; // Immutable attachment width in pixels.
    private final int height; // Immutable attachment height in pixels.

    /**
     * Allocates a render target suitable for sampling and readback blits.
     *
     * @param window initialized runtime owner
     * @param width positive texture width
     * @param height positive texture height
     */
    public BgfxRenderTarget(BgfxDesktopWindow window, int width, int height) {
        this(window, width, height, false);
    }

    /**
     * Allocates a sampled color target with an optional depth/stencil attachment.
     *
     * @param window initialized runtime owner
     * @param width positive texture width
     * @param height positive texture height
     * @param depthStencil whether to allocate D24S8 storage for depth and NanoVG stencils
     */
    public BgfxRenderTarget(BgfxDesktopWindow window, int width, int height, boolean depthStencil) {
        this(window, width, height, depthStencil, BGFX_TEXTURE_FORMAT_RGBA8);
    }

    /**
     * Allocates a sampled render target with an explicitly selected color format.
     *
     * @param window initialized runtime owner
     * @param width positive pixel width
     * @param height positive pixel height
     * @param depthStencil whether D24S8 storage is required
     * @param format native color format, including RGBA16F for HDR lighting
     */
    public BgfxRenderTarget(BgfxDesktopWindow window, int width, int height, boolean depthStencil, int format) {
        this(window, width, height, depthStencil, format, TextureFilter.LINEAR);
    }

    /**
     * Allocates a target with an explicit non-mipmapped attachment sampling policy.
     *
     * @param window initialized runtime owner
     * @param width positive pixel width
     * @param height positive pixel height
     * @param depthStencil whether D24S8 storage is required
     * @param format native color format
     * @param filter nearest or linear sampling, retained across device changes
     */
    public BgfxRenderTarget(BgfxDesktopWindow window, int width, int height, boolean depthStencil, int format, TextureFilter filter) {
        this(window, width, height, depthStencil, format, filter, false);
    }

    /**
     * Allocates a target with explicit color encoding. sRGB targets blend shader
     * output in linear light and decode automatically when sampled; use linear
     * shader colors. The encoding and sampling policy survive device replacement.
     * Unsupported format/encoding combinations fail before allocation.
     *
     * @param window initialized runtime owner
     * @param width positive pixel width
     * @param height positive pixel height
     * @param depthStencil whether D24S8 storage is required
     * @param format native color format
     * @param filter nearest or linear attachment sampling
     * @param srgb whether to request hardware sRGB encoding and blending
     */
    public BgfxRenderTarget(BgfxDesktopWindow window, int width, int height, boolean depthStencil, int format, TextureFilter filter, boolean srgb) {
        window.checkOwner();
        if (filter != TextureFilter.NEAREST && filter != TextureFilter.LINEAR)
            throw new IllegalArgumentException("Render targets require nearest or linear filtering");
        samplerFlags = BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP | (filter == TextureFilter.NEAREST ? BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT | BGFX_SAMPLER_MIP_POINT : 0);
        if (width < 1 || height < 1 || width > 65535 || height > 65535)
            throw new IllegalArgumentException("Render-target dimensions must be between 1 and 65535");
        int maximum = bgfx_get_caps()
                .limits()
                .maxTextureSize();
        if (width > maximum || height > maximum)
            throw new IllegalArgumentException("Render target exceeds this device's maximum size: " + maximum);
        this.window = window;
        this.width = width;
        this.height = height;
        this.depthStencil = depthStencil;
        this.format = format;
        this.srgb = srgb;
        try {
            restoreGraphicsResources();
            BgfxResources.register(this);
        } catch (RuntimeException | Error failure) {
            releaseGraphicsResources();
            throw failure;
        }
    }

    /**
     * Returns the attachment for sampling without transferring ownership.
     *
     * @return valid color texture handle
     */
    public short texture() {
        checkOpen();
        return texture;
    }

    /**
     * Returns the framebuffer for assigning a view's output.
     *
     * @return valid framebuffer handle
     */
    public short framebuffer() {
        checkOpen();
        return framebuffer;
    }

    /**
     * Returns the immutable attachment width.
     *
     * @return pixels per row
     */
    public int width() {
        return width;
    }

    /**
     * Returns the immutable attachment height.
     *
     * @return number of rows
     */
    public int height() {
        return height;
    }

    /**
     * Rejects resource access after close or from another rendering thread.
     */
    private void checkOpen() {
        window.checkOwner();
        if (framebuffer == BGFX_INVALID_HANDLE) throw new IllegalStateException("Render target is closed");
    }

    @Override
    public void close() {
        window.checkOwner();
        BgfxResources.unregister(this);
        releaseGraphicsResources();
    }

    @Override
    public void releaseGraphicsResources() {
        if (framebuffer == BGFX_INVALID_HANDLE) return;
        if (BgfxGraphics.isActive()) {
            BgfxGraphics.resetTextureState(Short.toUnsignedInt(texture));
            BgfxGraphics.setLinearTarget(framebuffer, false);
        }
        bgfx_destroy_frame_buffer(framebuffer);
        framebuffer = texture = BGFX_INVALID_HANDLE;
    }

    @Override
    public void restoreGraphicsResources() {
        int maximum = bgfx_get_caps().limits().maxTextureSize();
        if (width > maximum || height > maximum) throw new UnsupportedOperationException("The replacement device cannot restore this render target size");
        long flags = BGFX_TEXTURE_RT | samplerFlags | (srgb ? BGFX_TEXTURE_SRGB : 0);
        if (!bgfx_is_texture_valid(0, false, 1, format, flags))
            throw new UnsupportedOperationException("The device cannot render to the requested color format");
        if (depthStencil) {
            short color = BGFX_INVALID_HANDLE;
            short depth = BGFX_INVALID_HANDLE;
            try {
                color = bgfx_create_texture_2d(width, height, false, 1, format, flags, null, 0);
                int depthFormat = bgfx_is_texture_valid(0, false, 1, BGFX_TEXTURE_FORMAT_D24S8, BGFX_TEXTURE_RT_WRITE_ONLY) ? BGFX_TEXTURE_FORMAT_D24S8 : BGFX_TEXTURE_FORMAT_D32FS8;
                if (!bgfx_is_texture_valid(0, false, 1, depthFormat, BGFX_TEXTURE_RT_WRITE_ONLY))
                    throw new UnsupportedOperationException("The device cannot allocate a depth/stencil attachment");
                depth = bgfx_create_texture_2d(width, height, false, 1, depthFormat, BGFX_TEXTURE_RT_WRITE_ONLY, null, 0);
                if (color == BGFX_INVALID_HANDLE || depth == BGFX_INVALID_HANDLE)
                    throw new IllegalStateException("Could not allocate render-target attachments");
                framebuffer = bgfx_create_frame_buffer_from_handles(new short[]{color, depth}, true);
            } finally {
                if (framebuffer == BGFX_INVALID_HANDLE) {
                    if (color != BGFX_INVALID_HANDLE) bgfx_destroy_texture(color);
                    if (depth != BGFX_INVALID_HANDLE) bgfx_destroy_texture(depth);
                }
            }
        } else {
            framebuffer = bgfx_create_frame_buffer(width, height, format, flags);
        }
        if (framebuffer == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not allocate render target");
        texture = bgfx_get_texture(framebuffer, 0);
        if (texture == BGFX_INVALID_HANDLE) {
            releaseGraphicsResources();
            throw new IllegalStateException("Render target has no color attachment");
        }
        if (BgfxGraphics.isActive()) {
            BgfxGraphics.resetTextureState(Short.toUnsignedInt(texture));
            boolean linear = srgb || switch (format) {
                case BGFX_TEXTURE_FORMAT_R16F, BGFX_TEXTURE_FORMAT_R32F, BGFX_TEXTURE_FORMAT_RG16F, BGFX_TEXTURE_FORMAT_RG32F,
                     BGFX_TEXTURE_FORMAT_RGBA16F, BGFX_TEXTURE_FORMAT_RGBA32F, BGFX_TEXTURE_FORMAT_RGB9E5F, BGFX_TEXTURE_FORMAT_RG11B10F -> true;
                default -> false;
            };
            BgfxGraphics.setLinearTarget(framebuffer, linear);
        }
    }

}
