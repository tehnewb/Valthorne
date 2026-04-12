package valthorne.graphics.lighting;

import org.lwjgl.BufferUtils;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;

public class LightTexture extends Texture {

    private final boolean ownsGlTexture;

    public LightTexture(int width, int height) {
        this(glGenTextures(), width, height, true);
        bind();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, TextureFilter.LINEAR.minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, TextureFilter.LINEAR.magFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, 0L);
    }

    public LightTexture(int textureID, int width, int height, boolean ownsGlTexture) {
        super(textureID, new TextureData(BufferUtils.createByteBuffer(1), width, height));
        this.ownsGlTexture = ownsGlTexture;
        this.filter = TextureFilter.LINEAR;
    }

    public void upload(ByteBuffer buffer, int width, int height) {
        bind();
        this.data = new TextureData(this.data.buffer(), width, height);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, buffer);
    }

    public void resize(int width, int height) {
        bind();
        this.data = new TextureData(this.data.buffer(), width, height);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, 0L);
    }

    @Override
    public void dispose() {
        if (ownsGlTexture) {
            glDeleteTextures(textureID);
        }
        this.data = null;
        this.filter = null;
    }
}