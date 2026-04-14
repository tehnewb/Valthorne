package valthorne.graphics.lighting;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Off-screen buffer the user renders into each frame to describe the scene for
 * standalone radiance-cascade lighting. The encoding is:
 * <ul>
 *   <li>RGB – emissive colour at each pixel (glowing tiles, lit surfaces, etc.)</li>
 *   <li>A   – transmittance: 1.0 = fully open air, 0.0 = solid occluder</li>
 * </ul>
 * Call {@link #begin()} before rendering scene geometry, then {@link #end()}.
 * Pass this buffer to {@link RadianceCascades#update(GISceneBuffer)} or
 * {@link RadianceCascadeRenderer#render(GISceneBuffer)}.
 */
public final class GISceneBuffer {

    private int fboId;
    private LightTexture texture;
    private int width;
    private int height;

    public GISceneBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        create(width, height);
    }

    private void create(int w, int h) {
        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        texture = new LightTexture(w, h);
        // NEAREST filter is required: bilinear interpolation at wall edges (A=0)
        // bleeds into adjacent air (A=1), making walls semi-transparent to GI rays.
        texture.bind();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture.getTextureID(), 0);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("GISceneBuffer FBO incomplete. Status: " + status);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Binds the FBO and clears it to the default (no emission, full transmittance).
     * Render scene geometry before calling {@link #end()}.
     */
    public void begin() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, width, height);
        // Default: RGB=0 (no emission), A=1 (air – fully transmits light)
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    /** Unbinds the FBO. */
    public void end() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void resize(int newWidth, int newHeight) {
        if (this.width == newWidth && this.height == newHeight) return;
        this.width = newWidth;
        this.height = newHeight;
        if (texture == null) {
            create(newWidth, newHeight);
            return;
        }

        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        texture.resize(newWidth, newHeight);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture.getTextureID(), 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public int getTextureID() {
        return texture.getTextureID();
    }

    public int getTextureId() {
        return getTextureID();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void dispose() {
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
        if (fboId != 0) {
            glDeleteFramebuffers(fboId);
            fboId = 0;
        }
    }
}
