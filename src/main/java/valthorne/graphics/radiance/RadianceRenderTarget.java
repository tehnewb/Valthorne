package valthorne.graphics.radiance;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL11.glViewport;

final class RadianceRenderTarget {

    private final boolean renderable;
    private final boolean linear;
    private int width;
    private int height;
    private int textureID;
    private int framebufferID;
    private RadianceTexture texture;
    private int previousFramebuffer;
    private final int[] previousViewport = new int[4];

    RadianceRenderTarget(int width, int height, boolean renderable, boolean linear) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        this.width = width;
        this.height = height;
        this.renderable = renderable;
        this.linear = linear;
        create();
    }

    private void create() {
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
        texture = new RadianceTexture(textureID, width, height, linear);
        if (renderable) {
            framebufferID = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebufferID);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureID, 0);
            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                dispose();
                throw new IllegalStateException("Incomplete framebuffer for radiance target: " + status);
            }
        }
    }

    void begin() {
        if (!renderable) throw new IllegalStateException("Target is not renderable");
        previousFramebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING);
        glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, previousViewport);
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferID);
        glViewport(0, 0, width, height);
    }

    void end() {
        if (!renderable) throw new IllegalStateException("Target is not renderable");
        glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer);
        glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
    }

    void clear(float r, float g, float b, float a) {
        if (renderable) {
            begin();
            glClearColor(r, g, b, a);
            glClear(GL_COLOR_BUFFER_BIT);
            end();
        }
    }

    void resize(int width, int height) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        if (this.width == width && this.height == height) return;
        dispose();
        this.width = width;
        this.height = height;
        create();
    }

    void dispose() {
        if (framebufferID != 0) {
            glDeleteFramebuffers(framebufferID);
            framebufferID = 0;
        }
        if (textureID != 0) {
            glDeleteTextures(textureID);
            textureID = 0;
        }
        texture = null;
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    int getTextureID() {
        return textureID;
    }

    int getFramebufferID() {
        return framebufferID;
    }

    RadianceTexture getTexture() {
        return texture;
    }
}
