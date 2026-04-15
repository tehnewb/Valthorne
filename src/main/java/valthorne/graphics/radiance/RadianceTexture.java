package valthorne.graphics.radiance;

import org.lwjgl.BufferUtils;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;

import java.nio.ByteBuffer;

public final class RadianceTexture extends Texture {

    private static TextureData placeholder(int width, int height) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(4);
        return new TextureData(buffer, width, height);
    }

    public RadianceTexture(int textureID, int width, int height, boolean linear) {
        super(textureID, placeholder(width, height));
        setFilter(linear ? TextureFilter.LINEAR : TextureFilter.NEAREST);
    }
}
