package valthorne.graphics.radiance;

import org.lwjgl.BufferUtils;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;

import java.nio.ByteBuffer;

/**
 * Wraps an existing radiance texture ID for use by the texture-rendering API.
 * The four-byte CPU placeholder supplies dimensions only and is not a full image
 * or a readback of the GPU data. Construction changes the texture's filtering.
 * When obtained from a radiance target, the wrapper borrows that target's ID:
 * do not call inherited disposal independently or use it after target recreation.
 *
 * @author Albert Beaupre
 */
public final class RadianceTexture extends Texture {

    /**
     * Associates an existing texture name with dimension metadata and applies filtering.
     * Does not allocate the GPU image; the texture must already have valid storage.
     *
     * @param textureID existing OpenGL texture name
     * @param width logical texture width in pixels
     * @param height logical texture height in pixels
     * @param linear true for linear filtering, false for nearest
     */
    public RadianceTexture(int textureID, int width, int height, boolean linear) {
        super(textureID, placeholder(width, height));
        setFilter(linear ? TextureFilter.LINEAR : TextureFilter.NEAREST);
    }

    /**
     * Allocates four bytes of placeholder storage with the supplied image dimensions.
     * The buffer is metadata support only and must not be treated as width-by-height
     * pixel data for upload or export.
     *
     * @param width reported width in pixels
     * @param height reported height in pixels
     * @return placeholder texture metadata
     */
    private static TextureData placeholder(int width, int height) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(4);
        return new TextureData(buffer, width, height);
    }
}
