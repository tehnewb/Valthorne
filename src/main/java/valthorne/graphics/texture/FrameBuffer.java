package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.*;

/**
 * Simple 2D framebuffer (render target) you can draw to, then draw its color texture like a sprite.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Create a framebuffer matching your initial window size.
 * FrameBuffer fbo = new FrameBuffer(1280, 720, true);
 *
 * // 1) Render scene into the FBO.
 * fbo.begin();
 * glClearColor(0f, 0f, 0f, 1f);
 * glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
 * // draw your world/batches here...
 * fbo.end();
 *
 * // 2) Present the FBO's color texture to the currently bound framebuffer (usually the screen).
 * glClear(GL_COLOR_BUFFER_BIT);
 * fbo.draw(0f, 0f, 1280f, 720f);
 *
 * // If your output is upside-down, use:
 * // fbo.drawFlippedY(0f, 0f, 1280f, 720f);
 *
 * // Cleanup.
 * fbo.dispose();
 * }</pre>
 *
 * <h2>What this class does</h2>
 * <ul>
 *     <li>Creates an OpenGL framebuffer object (FBO) with a color texture attachment.</li>
 *     <li>Optionally creates a depth renderbuffer attachment.</li>
 *     <li>Captures and restores the previously bound framebuffer and viewport around {@link #begin()} / {@link #end()}.</li>
 *     <li>Provides {@link #draw(float, float, float, float)} and {@link #drawFlippedY(float, float, float, float)} using fixed-function vertex pointers.</li>
 * </ul>
 *
 * <h2>Important notes</h2>
 * <ul>
 *     <li>This uses {@code GL_QUADS}. If you ever move fully to core profiles, you will want to replace this with two triangles.</li>
 *     <li>{@link #draw(float, float, float, float)} and {@link #drawFlippedY(float, float, float, float)} assume your caller has already enabled
 *     {@code GL_TEXTURE_2D} and client states as needed (matching your existing fixed-function pipeline).</li>
 *     <li>{@link #resize(int, int)} recreates the underlying OpenGL objects. Call it when your window/viewport size changes.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 22nd, 2026
 */
public final class FrameBuffer {

    private int fboID; // OpenGL framebuffer object id.
    private int colorTextureID; // OpenGL texture id used as the color attachment.
    private int depthRBOID; // OpenGL renderbuffer id used as the optional depth attachment.

    private int width; // Current framebuffer width in pixels.
    private int height; // Current framebuffer height in pixels.

    private final boolean hasDepth; // True to allocate and attach a depth renderbuffer.

    private int previousFBO; // Previously bound framebuffer id (captured by begin()).
    private int prevViewportX, prevViewportY, prevViewportW, prevViewportH; // Previously active viewport (captured by begin()).

    private final FloatBuffer quadVerts = BufferUtils.createFloatBuffer(8); // Quad vertex positions (x,y) * 4 in draw order.
    private final FloatBuffer quadUvs = BufferUtils.createFloatBuffer(8); // Quad UV coordinates (u,v) * 4 matching quadVerts.
    private final int[] vp = new int[4]; // Temporary buffer for GL_VIEWPORT queries.

    /**
     * Creates a framebuffer with a color texture attachment and optional depth attachment.
     *
     * <p>This allocates the OpenGL objects immediately and prepares internal quad buffers used by draw methods.</p>
     *
     * @param width    framebuffer width in pixels (must be > 0)
     * @param height   framebuffer height in pixels (must be > 0)
     * @param hasDepth true to attach a depth renderbuffer, false for color-only
     * @throws IllegalArgumentException if width or height are <= 0
     * @throws IllegalStateException    if OpenGL reports the framebuffer is incomplete
     */
    public FrameBuffer(int width, int height, boolean hasDepth) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");

        this.width = width;
        this.height = height;
        this.hasDepth = hasDepth;

        create();
        updateQuad(0f, 0f, width, height, false);
    }

    /**
     * Begins rendering into this framebuffer.
     *
     * <p>This method:</p>
     * <ul>
     *     <li>Captures the currently bound framebuffer id so it can be restored later.</li>
     *     <li>Captures the current viewport so it can be restored later.</li>
     *     <li>Binds this framebuffer and sets the viewport to {@code 0,0,width,height}.</li>
     * </ul>
     *
     * <p>Typical usage is {@code begin(); ...draw scene...; end();}.</p>
     */
    public void begin() {
        previousFBO = glGetInteger(GL_FRAMEBUFFER_BINDING);

        glGetIntegerv(GL_VIEWPORT, vp);
        prevViewportX = vp[0];
        prevViewportY = vp[1];
        prevViewportW = vp[2];
        prevViewportH = vp[3];

        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glViewport(0, 0, width, height);
    }

    /**
     * Ends rendering into this framebuffer and restores the previous framebuffer and viewport.
     *
     * <p>This method assumes {@link #begin()} was called earlier in the same render flow.
     * It restores exactly what was captured in {@link #begin()}.</p>
     */
    public void end() {
        glBindFramebuffer(GL_FRAMEBUFFER, previousFBO);
        glViewport(prevViewportX, prevViewportY, prevViewportW, prevViewportH);
    }

    /**
     * Convenience wrapper that runs render logic inside {@link #begin()} / {@link #end()} safely.
     *
     * <p>This guarantees {@link #end()} is called even if {@code drawCalls} throws.</p>
     *
     * @param drawCalls render logic to execute while this framebuffer is bound
     * @throws NullPointerException if {@code drawCalls} is null
     */
    public void render(Runnable drawCalls) {
        if (drawCalls == null) throw new NullPointerException("drawCalls");

        begin();
        try {
            drawCalls.run();
        } finally {
            end();
        }
    }

    /**
     * Resizes the framebuffer by recreating its underlying OpenGL attachments.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>If the size is unchanged, this is a no-op.</li>
     *     <li>If the size changes, this deletes the old OpenGL objects and allocates new ones.</li>
     *     <li>This does not automatically update any projection matrices in your engine; it only changes the render target size.</li>
     * </ul>
     *
     * @param newWidth  new framebuffer width in pixels (must be > 0)
     * @param newHeight new framebuffer height in pixels (must be > 0)
     * @throws IllegalArgumentException if newWidth or newHeight are <= 0
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0) throw new IllegalArgumentException("newWidth must be > 0");
        if (newHeight <= 0) throw new IllegalArgumentException("newHeight must be > 0");
        if (newWidth == width && newHeight == height) return;

        this.width = newWidth;
        this.height = newHeight;

        disposeGL();
        create();
    }

    /**
     * Draws the framebuffer's color texture to the currently bound framebuffer (usually the screen).
     *
     * <p>This uses fixed-function pointers ({@code glVertexPointer}/{@code glTexCoordPointer}) like your Texture pipeline.
     * UVs are set so the texture is sampled normally (0,0) bottom-left and (1,1) top-right.</p>
     *
     * <p>Important: This does not bind shaders or change matrices. It assumes your caller has already configured
     * projection/modelview state the way your engine expects.</p>
     *
     * @param x bottom-left x in world/screen space
     * @param y bottom-left y in world/screen space
     * @param w width in world/screen space
     * @param h height in world/screen space
     */
    public void draw(float x, float y, float w, float h) {
        updateQuad(x, y, w, h, true);

        glBindTexture(GL_TEXTURE_2D, colorTextureID);

        glVertexPointer(2, GL_FLOAT, 0, quadVerts);
        glTexCoordPointer(2, GL_FLOAT, 0, quadUvs);

        glDrawArrays(GL_QUADS, 0, 4);
    }

    /**
     * Draws the framebuffer's color texture flipped vertically.
     *
     * <p>This is useful when your FBO output appears upside down due to coordinate conventions.
     * The flip is done by swapping V coordinates in the quad UVs (no extra rendering passes).</p>
     *
     * @param x bottom-left x in world/screen space
     * @param y bottom-left y in world/screen space
     * @param w width in world/screen space
     * @param h height in world/screen space
     */
    public void drawFlippedY(float x, float y, float w, float h) {
        updateQuad(x, y, w, h, false);

        glBindTexture(GL_TEXTURE_2D, colorTextureID);

        glVertexPointer(2, GL_FLOAT, 0, quadVerts);
        glTexCoordPointer(2, GL_FLOAT, 0, quadUvs);

        glDrawArrays(GL_QUADS, 0, 4);
    }

    /**
     * Disposes all OpenGL resources owned by this framebuffer.
     *
     * <p>Safe to call multiple times. After disposal, the framebuffer is no longer usable
     * unless you recreate it by constructing a new {@link FrameBuffer}.</p>
     */
    public void dispose() {
        disposeGL();
    }

    /**
     * Creates the OpenGL framebuffer + attachments using the current {@link #width} and {@link #height}.
     *
     * <p>Creation steps:</p>
     * <ul>
     *     <li>Create and bind the framebuffer object.</li>
     *     <li>Create a 2D RGBA8 texture and attach it as {@code GL_COLOR_ATTACHMENT0}.</li>
     *     <li>If {@link #hasDepth} is true, create a depth renderbuffer and attach it as {@code GL_DEPTH_ATTACHMENT}.</li>
     *     <li>Validate using {@code glCheckFramebufferStatus} and throw if incomplete.</li>
     *     <li>Unbind framebuffer/texture/renderbuffer to leave GL state clean.</li>
     * </ul>
     *
     * @throws IllegalStateException if the framebuffer is not complete
     */
    private void create() {
        fboID = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboID);

        colorTextureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTextureID);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTextureID, 0);

        if (hasDepth) {
            depthRBOID = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, depthRBOID);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRBOID);
        } else {
            depthRBOID = 0;
        }

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            throw new IllegalStateException("Framebuffer incomplete: 0x" + Integer.toHexString(status));
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
    }

    /**
     * Deletes the OpenGL objects owned by this framebuffer (FBO, color texture, depth renderbuffer).
     *
     * <p>This method performs id checks and sets ids back to 0 after deletion.</p>
     */
    private void disposeGL() {
        if (depthRBOID != 0) {
            glDeleteRenderbuffers(depthRBOID);
            depthRBOID = 0;
        }
        if (colorTextureID != 0) {
            glDeleteTextures(colorTextureID);
            colorTextureID = 0;
        }
        if (fboID != 0) {
            glDeleteFramebuffers(fboID);
            fboID = 0;
        }
    }

    /**
     * Updates the internal quad vertex and UV buffers for a destination rectangle.
     *
     * <p>Vertex order is:</p>
     * <ul>
     *     <li>(x, y)</li>
     *     <li>(x+w, y)</li>
     *     <li>(x+w, y+h)</li>
     *     <li>(x, y+h)</li>
     * </ul>
     *
     * <p>UV mapping:</p>
     * <ul>
     *     <li>If {@code normalUv == true}: (0,0) bottom-left and (1,1) top-right.</li>
     *     <li>If {@code normalUv == false}: flips Y so (0,1) bottom-left and (1,0) top-right.</li>
     * </ul>
     *
     * @param x        bottom-left x
     * @param y        bottom-left y
     * @param w        width
     * @param h        height
     * @param normalUv true for normal UV orientation, false to flip Y
     */
    private void updateQuad(float x, float y, float w, float h, boolean normalUv) {
        quadVerts.put(0, x);
        quadVerts.put(1, y);

        quadVerts.put(2, x + w);
        quadVerts.put(3, y);

        quadVerts.put(4, x + w);
        quadVerts.put(5, y + h);

        quadVerts.put(6, x);
        quadVerts.put(7, y + h);

        if (normalUv) {
            quadUvs.put(0, 0f);
            quadUvs.put(1, 0f);
            quadUvs.put(2, 1f);
            quadUvs.put(3, 0f);
            quadUvs.put(4, 1f);
            quadUvs.put(5, 1f);
            quadUvs.put(6, 0f);
            quadUvs.put(7, 1f);
        } else {
            quadUvs.put(0, 0f);
            quadUvs.put(1, 1f);
            quadUvs.put(2, 1f);
            quadUvs.put(3, 1f);
            quadUvs.put(4, 1f);
            quadUvs.put(5, 0f);
            quadUvs.put(6, 0f);
            quadUvs.put(7, 0f);
        }
    }

    /**
     * Returns the current framebuffer width in pixels.
     *
     * @return width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the current framebuffer height in pixels.
     *
     * @return height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the OpenGL framebuffer object id.
     *
     * <p>This is useful when you need to bind the framebuffer yourself or integrate with other rendering code.</p>
     *
     * @return framebuffer object id
     */
    public int getFrameBufferId() {
        return fboID;
    }

    /**
     * Returns the OpenGL texture id for the color attachment.
     *
     * <p>You can bind this texture and sample it in shaders, or draw it using fixed-function like a normal texture.</p>
     *
     * @return color texture id
     */
    public int getColorTextureId() {
        return colorTextureID;
    }
}