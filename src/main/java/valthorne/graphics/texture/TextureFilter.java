package valthorne.graphics.texture;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;

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
    LINEAR(GL_LINEAR, GL_LINEAR);

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
}
