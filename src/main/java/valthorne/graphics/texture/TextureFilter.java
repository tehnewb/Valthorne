package valthorne.graphics.texture;

import static org.lwjgl.opengl.GL11.*;

/**
 * Represents the filtering mode applied to an OpenGL texture.
 *
 * <p>This enum abstracts over the raw OpenGL constants and provides
 * descriptive names for common filtering behaviors. These modes determine
 * how OpenGL samples texture pixels when the texture is scaled.</p>
 *
 * <ul>
 *   <li>{@link #NEAREST} — crisp, blocky, pixel-art style</li>
 *   <li>{@link #LINEAR} — smooth, blended scaling</li>
 * </ul>
 *
 * <p>The values stored here are passed directly into
 * {@code glTexParameteri(GL_TEXTURE_2D, ...)}.</p>
 *
 * @author Albert Beaupre
 * @since November 26th, 2025
 */
public enum TextureFilter {

    /**
     * Nearest-neighbor filtering.
     *
     * <p>Uses the closest pixel when scaling the texture. Produces a sharp,
     * pixelated look. Ideal for retro art, voxel textures, or crisp UI.</p>
     */
    NEAREST(GL_NEAREST, GL_NEAREST),

    /**
     * Linear filtering.
     *
     * <p>Interpolates between adjacent pixels, producing a smoother image.
     * Best for modern graphics, high-res textures, and UI widgets.</p>
     */
    LINEAR(GL_LINEAR, GL_LINEAR),

    /**
     * Nearest filtering with mipmaps.
     *
     * <p>Uses nearest neighbor sampling and nearest mipmap level.
     * Reduces aliasing at distance while keeping a pixel-art look.</p>
     */
    NEAREST_MIPMAP_NEAREST(GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST),

    /**
     * Nearest filtering with linear mipmap blending.
     *
     * <p>Sharp pixels with smoother transitions between mip levels.</p>
     */
    NEAREST_MIPMAP_LINEAR(GL_NEAREST_MIPMAP_LINEAR, GL_NEAREST),

    /**
     * Linear filtering with nearest mipmap selection.
     */
    LINEAR_MIPMAP_NEAREST(GL_LINEAR_MIPMAP_NEAREST, GL_LINEAR),

    /**
     * Linear filtering with linear mipmap blending.
     *
     * <p>The highest quality standard OpenGL filtering.</p>
     */
    LINEAR_MIPMAP_LINEAR(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR);

    /**
     * Minification filter parameter.
     */
    public final int minFilter;

    /**
     * Magnification filter parameter.
     */
    public final int magFilter;

    TextureFilter(int minFilter, int magFilter) {
        this.minFilter = minFilter;
        this.magFilter = magFilter;
    }

    /**
     * @return true if this filter requires mipmaps
     */
    public boolean usesMipmaps() {
        return minFilter == GL_NEAREST_MIPMAP_NEAREST
                || minFilter == GL_NEAREST_MIPMAP_LINEAR
                || minFilter == GL_LINEAR_MIPMAP_NEAREST
                || minFilter == GL_LINEAR_MIPMAP_LINEAR;
    }

    /**
     * @return true if this filter is pixel-perfect
     */
    public boolean isPixelPerfect() {
        return magFilter == GL_NEAREST;
    }

}
