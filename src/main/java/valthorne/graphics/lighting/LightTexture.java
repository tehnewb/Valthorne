package valthorne.graphics.lighting;

import org.lwjgl.BufferUtils;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;

/**
 * Adapts an RGBA16F lighting texture to the normal texture API with explicit native
 * ownership. The size-only constructor allocates storage; the ID constructor wraps
 * existing storage and records linear filtering metadata without applying parameters.
 * Placeholder CPU bytes are not a full image and must not be used as a pixel readback.
 *
 * <p>All native operations require the owning graphics context. Resize mutates even
 * borrowed texture storage. Dispose once: the native ID is not zeroed, and repeated
 * disposal of an owning wrapper is not guarded.</p>
 *
 * @author Albert Beaupre
 */
public final class LightTexture extends Texture {

    private final boolean ownsGlTexture; // Whether this wrapper deletes the native texture on disposal.

    /**
     * Allocates an owned RGBA16F texture with linear filtering and clamp-to-edge wrapping.
     * Leaves the texture bound; dimension validity is delegated to the texture/GPU path.
     *
     * @param width  texture width in pixels
     * @param height texture height in pixels
     */
    public LightTexture(int width, int height) {
        this(glGenTextures(), width, height, true);
        bind();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, TextureFilter.LINEAR.minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, TextureFilter.LINEAR.magFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, 0L);
    }

    /**
     * Wraps an existing texture with dimension metadata and a one-byte placeholder.
     * Does not allocate image storage or apply the recorded linear filter.
     *
     * @param textureID     existing OpenGL texture name
     * @param width         reported texture width
     * @param height        reported texture height
     * @param ownsGlTexture whether disposal deletes the native texture
     */
    public LightTexture(int textureID, int width, int height, boolean ownsGlTexture) {
        super(textureID, new TextureData(BufferUtils.createByteBuffer(1), width, height));
        this.ownsGlTexture = ownsGlTexture;
        this.filter = TextureFilter.LINEAR;
    }

    /**
     * Reallocates RGBA16F storage and replaces dimension metadata, discarding contents.
     * Keeps the same native ID and placeholder buffer, including for borrowed textures.
     *
     * @param width  replacement pixel width
     * @param height replacement pixel height
     */
    public void resize(int width, int height) {
        bind();
        this.data = new TextureData(this.data.buffer(), width, height);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, 0L);
    }

    /**
     * Deletes the native texture only when ownership was requested, then clears data
     * and filter references. The stored texture ID is retained; dispose only once.
     */
    @Override
    public void dispose() {
        if (ownsGlTexture) {
            glDeleteTextures(textureID);
        }
        this.data = null;
        this.filter = null;
    }
}
