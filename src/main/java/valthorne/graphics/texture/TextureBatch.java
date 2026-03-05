package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.graphics.shader.Shader;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.GL_MAX_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/**
 * Instanced sprite batch that preserves draw order while supporting multiple textures per batch,
 * with optional rendering into a {@link FrameBuffer}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TextureBatch batch = new TextureBatch(5000);
 *
 * batch.begin();
 * batch.setColor(1f, 1f, 1f, 1f);
 * batch.draw(playerTex, 100, 80, 64, 64);
 * batch.drawRegion(atlasRegion, 200, 80, 64, 64);
 * batch.end();
 *
 * // Render into an FBO (same size as current viewport; it will auto-resize if needed)
 * FrameBuffer fbo = new FrameBuffer();
 * batch.begin(fbo);
 * batch.draw(uiTex, 10, 10, 32, 32, new Color(1f, 0.4f, 0.4f, 1f));
 * batch.end(); // restores previous framebuffer + viewport automatically
 *
 * batch.dispose();
 * }</pre>
 *
 * <h2>How it works</h2>
 * <ul>
 *     <li>Uses one static quad VBO for local quad vertices + UVs.</li>
 *     <li>Queues per-sprite instance data into a CPU FloatBuffer, uploads to an instance VBO on flush.</li>
 *     <li>Binds multiple textures across units 0..N-1 and stores a per-instance texture-unit index.</li>
 *     <li>Stores per-instance UV rectangles so sub-regions (atlases/fonts) are supported.</li>
 *     <li>Flushes when the instance buffer is full or when no free texture unit exists.</li>
 * </ul>
 *
 * <h2>FBO notes</h2>
 * <ul>
 *     <li>{@link #begin(FrameBuffer)} captures the currently bound framebuffer + viewport.</li>
 *     <li>If the target FBO size does not match the current viewport size, it is resized to match.</li>
 *     <li>{@link #end()} restores the previous framebuffer + viewport exactly as they were.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 8th, 2026
 */
public final class TextureBatch {

    private static final int QUAD_VERTS = 6; // Vertices per quad (two triangles).
    private static final int QUAD_FLOATS_PER_VERT = 4; // Floats per quad vertex (localX, localY, u, v).
    private static final int BYTES_PER_FLOAT = 4; // Bytes per float.

    private static final int INST_FLOATS = 13; // Floats per instance (x,y,w,h, r,g,b,a, texUnit, u0,v0,u1,v1).
    private static final int INST_STRIDE_BYTES = INST_FLOATS * BYTES_PER_FLOAT; // Byte stride per instance.

    private static final int ATTR_LOCAL = 0; // Vertex attribute index for quad-local position.
    private static final int ATTR_UV = 1; // Vertex attribute index for quad-local UV.
    private static final int ATTR_XYWH = 2; // Instance attribute index for world-space x,y,w,h.
    private static final int ATTR_COL = 3; // Instance attribute index for per-sprite color.
    private static final int ATTR_TEX = 4; // Instance attribute index for texture unit selector.
    private static final int ATTR_UVRECT = 5; // Instance attribute index for UV rectangle.

    private final FloatBuffer instanceBuffer; // CPU staging buffer for per-instance data.
    private final Color color = Color.WHITE;
    private final Shader shader; // Shader used for instanced sprite rendering.
    private final int maxSprites; // Maximum sprites queued before an auto flush.
    private final int maxTextureUnits; // Maximum simultaneous texture units used by this batch.
    private final int quadVBO; // GL buffer id for the static quad geometry.
    private final int instanceVBO; // GL buffer id for the dynamic per-instance data.
    private final int[] textureIDs; // Texture id per unit; -1 means the unit is free.
    private final int[] vp = new int[4]; // Temporary buffer for GL_VIEWPORT queries.

    private int instanceCount; // Number of queued instances since the last flush.
    private boolean drawing; // True between begin() and end().

    private boolean usingFBO; // True when begin(FrameBuffer) is active.
    private int previousFBO; // Previously bound framebuffer id captured at begin(FrameBuffer).
    private int prevViewportX, prevViewportY, prevViewportW, prevViewportH; // Previously active viewport captured at begin(FrameBuffer).

    /**
     * Creates a batch with {@code maxSprites} and auto-detects texture units (clamped to <= 16).
     *
     * <p>This constructor queries {@code GL_MAX_TEXTURE_IMAGE_UNITS} and clamps it for safety.
     * If detection fails or returns an invalid value, a sane default is used.</p>
     *
     * @param maxSprites maximum sprites queued before flushing
     */
    public TextureBatch(int maxSprites) {
        this(maxSprites, detectTextureUnitsClamped(16));
    }

    /**
     * Creates a batch with an explicit texture-unit cap.
     *
     * <p>Initialization details:</p>
     * <ul>
     *     <li>Allocates the CPU instance buffer sized to {@code maxSprites}.</li>
     *     <li>Creates a static quad VBO (two triangles, local [0..1] space).</li>
     *     <li>Creates a dynamic instance VBO sized to {@code maxSprites} instances.</li>
     *     <li>Builds an instancing shader (GLSL 120) with a sampler if/else chain sized to {@code maxTextureUnits}.</li>
     *     <li>Binds attribute locations and relinks the shader so locations are guaranteed.</li>
     * </ul>
     *
     * @param maxSprites      maximum sprites queued before flushing
     * @param maxTextureUnits maximum simultaneously bound textures (2..16 recommended)
     * @throws IllegalArgumentException if {@code maxSprites <= 0} or {@code maxTextureUnits < 2}
     */
    public TextureBatch(int maxSprites, int maxTextureUnits) {
        if (maxSprites <= 0) throw new IllegalArgumentException("maxSprites must be > 0");
        if (maxTextureUnits < 2) throw new IllegalArgumentException("maxTextureUnits must be >= 2");

        this.maxSprites = maxSprites;
        this.maxTextureUnits = Math.min(maxTextureUnits, 16);
        this.textureIDs = new int[this.maxTextureUnits];
        Arrays.fill(textureIDs, -1);

        this.instanceBuffer = BufferUtils.createFloatBuffer(maxSprites * INST_FLOATS);

        this.shader = new Shader(VERTEX_SHADER, buildFragmentShader(this.maxTextureUnits));
        shader.bindAttribLocation(ATTR_LOCAL, "a_local");
        shader.bindAttribLocation(ATTR_UV, "a_uv");
        shader.bindAttribLocation(ATTR_XYWH, "i_xywh");
        shader.bindAttribLocation(ATTR_COL, "i_col");
        shader.bindAttribLocation(ATTR_TEX, "i_tex");
        shader.bindAttribLocation(ATTR_UVRECT, "i_uvRect");
        shader.reload();

        FloatBuffer quad = BufferUtils.createFloatBuffer(QUAD_VERTS * QUAD_FLOATS_PER_VERT);

        quad.put(0f).put(0f).put(0f).put(0f);
        quad.put(1f).put(0f).put(1f).put(0f);
        quad.put(1f).put(1f).put(1f).put(1f);

        quad.put(1f).put(1f).put(1f).put(1f);
        quad.put(0f).put(1f).put(0f).put(1f);
        quad.put(0f).put(0f).put(0f).put(0f);

        quad.flip();

        quadVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, quad, GL_STATIC_DRAW);

        instanceVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, (long) maxSprites * INST_STRIDE_BYTES, GL_STREAM_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Sets the batch tint color used when a draw call does not provide a per-sprite tint.
     *
     * <p>This copies the provided color into the internal tint instance. The reference is not retained,
     * so the caller may freely mutate their {@link Color} afterward without affecting the batch.</p>
     *
     * @param tint tint color (must be non-null)
     * @throws NullPointerException if {@code tint} is null
     */
    public void setColor(Color tint) {
        Objects.requireNonNull(tint, "Color cannot be null");
        color.set(tint);
    }

    /**
     * Sets the batch tint color used when a draw call does not provide a per-sprite tint.
     *
     * <p>This is a convenience overload that avoids allocating a {@link Color} at call sites.</p>
     *
     * @param r red   (0..1)
     * @param g green (0..1)
     * @param b blue  (0..1)
     * @param a alpha (0..1)
     */
    public void setColor(float r, float g, float b, float a) {
        color.r(r);
        color.g(g);
        color.b(b);
        color.a(a);
    }

    /**
     * Begins a drawing session to the currently bound framebuffer.
     *
     * <p>This is equivalent to {@link #begin(FrameBuffer)} with {@code null}, meaning:
     * it does not change the framebuffer binding or viewport, it only sets up batching state.</p>
     *
     * @throws IllegalStateException if already drawing
     */
    public void begin() {
        begin(null);
    }

    /**
     * Begins a drawing session, optionally targeting a {@link FrameBuffer}.
     *
     * <p>If {@code fbo} is non-null:</p>
     * <ul>
     *     <li>Captures the currently bound framebuffer id.</li>
     *     <li>Captures the current viewport (GL_VIEWPORT).</li>
     *     <li>Resizes the target FBO to match the captured viewport size (so output matches screen size).</li>
     *     <li>Binds the FBO and sets viewport to {@code 0,0,fboW,fboH}.</li>
     * </ul>
     *
     * <p>Regardless of target:</p>
     * <ul>
     *     <li>Resets internal counters and clears the instance buffer.</li>
     *     <li>Clears texture-unit bindings and rebinds textures on demand.</li>
     *     <li>Binds the shader, uploads sampler uniforms, and sets up instanced attribute pointers/divisors.</li>
     * </ul>
     *
     * @param fbo optional render target (null means draw to current framebuffer)
     * @throws IllegalStateException if already drawing
     * @throws IllegalStateException if viewport query returns invalid dimensions while using an FBO
     */
    public void begin(FrameBuffer fbo) {
        if (drawing) throw new IllegalStateException("Already drawing.");
        drawing = true;

        usingFBO = (fbo != null);

        if (usingFBO) {
            previousFBO = glGetInteger(GL_FRAMEBUFFER_BINDING);

            glGetIntegerv(GL_VIEWPORT, vp);
            prevViewportX = vp[0];
            prevViewportY = vp[1];
            prevViewportW = vp[2];
            prevViewportH = vp[3];

            if (prevViewportW <= 0 || prevViewportH <= 0) {
                throw new IllegalStateException("GL_VIEWPORT returned invalid size: " + prevViewportW + "x" + prevViewportH);
            }

            if (fbo.getWidth() != prevViewportW || fbo.getHeight() != prevViewportH) {
                fbo.resize(prevViewportW, prevViewportH);
            }

            glBindFramebuffer(GL_FRAMEBUFFER, fbo.getFrameBufferId());
            glViewport(0, 0, fbo.getWidth(), fbo.getHeight());
        }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        instanceCount = 0;
        instanceBuffer.clear();

        for (int i = 0; i < maxTextureUnits; i++) textureIDs[i] = -1;

        shader.bind();
        for (int i = 0; i < maxTextureUnits; i++) shader.setUniform1i("u_tex" + i, i);

        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);

        int quadStride = QUAD_FLOATS_PER_VERT * BYTES_PER_FLOAT;

        glEnableVertexAttribArray(ATTR_LOCAL);
        glVertexAttribPointer(ATTR_LOCAL, 2, GL_FLOAT, false, quadStride, 0L);

        glEnableVertexAttribArray(ATTR_UV);
        glVertexAttribPointer(ATTR_UV, 2, GL_FLOAT, false, quadStride, 2L * BYTES_PER_FLOAT);

        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);

        glEnableVertexAttribArray(ATTR_XYWH);
        glVertexAttribPointer(ATTR_XYWH, 4, GL_FLOAT, false, INST_STRIDE_BYTES, 0L);
        glVertexAttribDivisor(ATTR_XYWH, 1);

        glEnableVertexAttribArray(ATTR_COL);
        glVertexAttribPointer(ATTR_COL, 4, GL_FLOAT, false, INST_STRIDE_BYTES, 4L * BYTES_PER_FLOAT);
        glVertexAttribDivisor(ATTR_COL, 1);

        glEnableVertexAttribArray(ATTR_TEX);
        glVertexAttribPointer(ATTR_TEX, 1, GL_FLOAT, false, INST_STRIDE_BYTES, 8L * BYTES_PER_FLOAT);
        glVertexAttribDivisor(ATTR_TEX, 1);

        glEnableVertexAttribArray(ATTR_UVRECT);
        glVertexAttribPointer(ATTR_UVRECT, 4, GL_FLOAT, false, INST_STRIDE_BYTES, 9L * BYTES_PER_FLOAT);
        glVertexAttribDivisor(ATTR_UVRECT, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Queues the given texture using its own stored position and size.
     *
     * <p>This calls {@link #draw(Texture, Color)} with a null tint, which means the current batch tint is used.</p>
     *
     * @param tex texture to draw
     */
    public void draw(Texture tex) {
        draw(tex, null);
    }

    /**
     * Queues the given texture using its own stored position and size, optionally tinted.
     *
     * <p>This is a convenience call that pulls {@code x/y/width/height} from your {@link Texture} class.</p>
     *
     * @param tex  texture to draw (must be non-null)
     * @param tint optional per-sprite tint (null uses current batch tint)
     * @throws NullPointerException if {@code tex} is null
     */
    public void draw(Texture tex, Color tint) {
        Objects.requireNonNull(tex, "Texture cannot be null");
        drawUV(tex, tex.getX(), tex.getY(), tex.getWidth(), tex.getHeight(), tex.leftRegion, tex.topRegion, tex.rightRegion, tex.bottomRegion, tint);
    }

    /**
     * Queues a sprite for drawing using the full texture and the current batch tint.
     *
     * <p>This maps UVs to {@code 0..1} and delegates to {@link #drawUV(Texture, float, float, float, float, float, float, float, float, Color)}.</p>
     *
     * @param tex texture to draw (must be non-null)
     * @param x   world-space x
     * @param y   world-space y
     * @param w   world-space width
     * @param h   world-space height
     */
    public void draw(Texture tex, float x, float y, float w, float h) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, null);
    }

    /**
     * Queues a sprite for drawing using the full texture, optionally tinted.
     *
     * <p>This maps UVs to {@code 0..1} and delegates to {@link #drawUV(Texture, float, float, float, float, float, float, float, float, Color)}.</p>
     *
     * @param tex  texture to draw (must be non-null)
     * @param x    world-space x
     * @param y    world-space y
     * @param w    world-space width
     * @param h    world-space height
     * @param tint optional per-sprite tint (null uses current batch tint)
     */
    public void draw(Texture tex, float x, float y, float w, float h, Color tint) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, tint);
    }

    /**
     * Queues a sprite for drawing using custom UV coordinates.
     *
     * <p>This is typically used for atlases or partial draws when you already have normalized UVs.</p>
     *
     * @param tex texture to draw (must be non-null)
     * @param x   world-space x
     * @param y   world-space y
     * @param w   world-space width
     * @param h   world-space height
     * @param u0  left u (0..1)
     * @param v0  top v (0..1)
     * @param u1  right u (0..1)
     * @param v1  bottom v (0..1)
     */
    public void draw(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        drawUV(tex, x, y, w, h, u0, v0, u1, v1, null);
    }

    /**
     * Queues a sub-region of a texture for drawing at the specified world-space coordinates with a custom size.
     *
     * <p>This overload draws a region in pixel coordinates inside the texture and converts it to normalized UVs.</p>
     *
     * @param tex          texture to draw (must be non-null)
     * @param x            world-space x position
     * @param y            world-space y position
     * @param w            world-space width
     * @param h            world-space height
     * @param regionX      x offset of the region within the texture (pixels)
     * @param regionY      y offset of the region within the texture (pixels)
     * @param regionWidth  width of the region within the texture (pixels)
     * @param regionHeight height of the region within the texture (pixels)
     */
    public void drawRegion(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight) {
        drawRegion(tex, x, y, w, h, regionX, regionY, regionWidth, regionHeight, null);
    }

    /**
     * Queues a sub-region of a texture for drawing with optional tint.
     *
     * <p>Conversion details:</p>
     * <ul>
     *     <li>Computes {@code u0/v0/u1/v1} by dividing region pixel coordinates by the texture's pixel size.</li>
     *     <li>If the texture reports 0 width/height, this falls back to full UVs.</li>
     * </ul>
     *
     * @param tex          texture to draw (must be non-null)
     * @param x            world-space x position
     * @param y            world-space y position
     * @param w            world-space width
     * @param h            world-space height
     * @param regionX      x offset of the region within the texture (pixels)
     * @param regionY      y offset of the region within the texture (pixels)
     * @param regionWidth  width of the region within the texture (pixels)
     * @param regionHeight height of the region within the texture (pixels)
     * @param tint         optional per-sprite tint (null uses current batch tint)
     * @throws NullPointerException if {@code tex} is null
     */
    public void drawRegion(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight, Color tint) {
        Objects.requireNonNull(tex, "Texture cannot be null");

        TextureData d = tex.getData();
        if (d == null) {
            drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, tint);
            return;
        }

        float texW = d.width();
        float texH = d.height();

        if (texW == 0f || texH == 0f) {
            drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, tint);
            return;
        }

        float u0 = regionX / texW;
        float v0 = regionY / texH;
        float u1 = (regionX + regionWidth) / texW;
        float v1 = (regionY + regionHeight) / texH;

        drawUV(tex, x, y, w, h, u0, v0, u1, v1, tint);
    }

    /**
     * Queues a {@link TextureRegion} using the region's stored position and size.
     *
     * <p>This is a convenience for regions that already know their source rectangle.</p>
     *
     * @param region region to draw (must be non-null)
     * @throws NullPointerException if {@code region} is null
     */
    public void drawRegion(TextureRegion region) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region, region.getX(), region.getY(), region.getWidth(), region.getHeight(), region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), null);
    }

    /**
     * Queues a {@link TextureRegion} drawn at a custom destination rectangle.
     *
     * <p>The source region comes from the {@link TextureRegion} while the destination is caller-defined.</p>
     *
     * @param region region to draw (must be non-null)
     * @param x      world-space x
     * @param y      world-space y
     * @param w      world-space width
     * @param h      world-space height
     * @throws NullPointerException if {@code region} is null
     */
    public void drawRegion(TextureRegion region, float x, float y, float w, float h) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region, x, y, w, h, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), null);
    }

    /**
     * Queues a {@link TextureRegion} drawn at a custom destination rectangle, optionally tinted.
     *
     * <p>This keeps region source UV mapping while allowing per-draw color overrides.</p>
     *
     * @param region region to draw (must be non-null)
     * @param x      world-space x
     * @param y      world-space y
     * @param w      world-space width
     * @param h      world-space height
     * @param tint   optional per-sprite tint (null uses current batch tint)
     * @throws NullPointerException if {@code region} is null
     */
    public void drawRegion(TextureRegion region, float x, float y, float w, float h, Color tint) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        drawRegion(region, x, y, w, h, region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(), tint);
    }

    /**
     * Core enqueue method that writes one sprite instance into the instance buffer.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>Requires {@link #begin()} or {@link #begin(FrameBuffer)} to have been called.</li>
     *     <li>Auto-flushes if the instance buffer is full.</li>
     *     <li>Attempts to reuse an already-bound texture unit; otherwise binds into a free unit.</li>
     *     <li>If no unit is available, flushes once and retries binding.</li>
     *     <li>Writes instance data as: XYWH, RGBA, texUnit, UVRect.</li>
     * </ul>
     *
     * @param tex  texture to draw (must be non-null)
     * @param x    world-space x
     * @param y    world-space y
     * @param w    world-space width
     * @param h    world-space height
     * @param u0   left u (0..1)
     * @param v0   top v (0..1)
     * @param u1   right u (0..1)
     * @param v1   bottom v (0..1)
     * @param tint optional per-sprite tint (null uses current batch tint)
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code tex} is null
     */
    public void drawUV(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color tint) {
        if (!drawing) throw new IllegalStateException("Call begin() before draw().");
        Objects.requireNonNull(tex, "Texture cannot be null");

        if (instanceCount >= maxSprites) flush();

        int texUnit = getOrBindTextureUnit(tex.getTextureID());
        if (texUnit < 0) {
            flush();
            texUnit = getOrBindTextureUnit(tex.getTextureID());
            if (texUnit < 0) return;
        }

        float r = color.r(), g = color.g(), b = color.b(), a = color.a();
        if (tint != null) {
            r = tint.r();
            g = tint.g();
            b = tint.b();
            a = tint.a();
        }

        instanceBuffer.put(x).put(y).put(w).put(h);
        instanceBuffer.put(r).put(g).put(b).put(a);
        instanceBuffer.put((float) texUnit);
        instanceBuffer.put(u0).put(v0).put(u1).put(v1);

        instanceCount++;
    }

    /**
     * Ends a drawing session and flushes any remaining sprites.
     *
     * <p>Cleanup performed:</p>
     * <ul>
     *     <li>Flushes queued instances.</li>
     *     <li>Resets attribute divisors back to 0 (important if other code uses the same VAO-less state).</li>
     *     <li>Disables all enabled vertex attrib arrays used by this batch.</li>
     *     <li>Unbinds the shader program.</li>
     *     <li>Unbinds textures from all units used by the batch (sets them to 0).</li>
     *     <li>If drawing into an FBO, restores the previous framebuffer binding and viewport.</li>
     * </ul>
     *
     * @throws IllegalStateException if begin() has not been called
     */
    public void end() {
        if (!drawing) throw new IllegalStateException("Call begin() before end().");

        flush();

        glVertexAttribDivisor(ATTR_XYWH, 0);
        glVertexAttribDivisor(ATTR_COL, 0);
        glVertexAttribDivisor(ATTR_TEX, 0);
        glVertexAttribDivisor(ATTR_UVRECT, 0);

        glDisableVertexAttribArray(ATTR_LOCAL);
        glDisableVertexAttribArray(ATTR_UV);
        glDisableVertexAttribArray(ATTR_XYWH);
        glDisableVertexAttribArray(ATTR_COL);
        glDisableVertexAttribArray(ATTR_TEX);
        glDisableVertexAttribArray(ATTR_UVRECT);

        shader.unbind();
        drawing = false;

        for (int i = 0; i < maxTextureUnits; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        glActiveTexture(GL_TEXTURE0);

        if (usingFBO) {
            glBindFramebuffer(GL_FRAMEBUFFER, previousFBO);
            glViewport(prevViewportX, prevViewportY, prevViewportW, prevViewportH);
            usingFBO = false;
        }
    }

    /**
     * Flushes queued sprites in one instanced draw call.
     *
     * <p>This method:</p>
     * <ul>
     *     <li>Uploads the CPU instance buffer into the instance VBO.</li>
     *     <li>Issues {@code glDrawArraysInstanced(GL_TRIANGLES, 0, 6, instanceCount)}.</li>
     *     <li>Clears the CPU buffer and resets {@link #instanceCount} to 0.</li>
     * </ul>
     *
     * <p>It is safe to call this at any time; if nothing is queued, it does nothing.</p>
     */
    public void flush() {
        if (instanceCount == 0) return;

        instanceBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, (long) maxSprites * INST_STRIDE_BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glDrawArraysInstanced(GL_TRIANGLES, 0, QUAD_VERTS, instanceCount);

        instanceBuffer.clear();
        instanceCount = 0;
    }

    /**
     * Disposes GPU resources owned by this batch.
     *
     * <p>This deletes the shader program and both VBOs. After calling this, the batch must not be used again.</p>
     */
    public void dispose() {
        shader.dispose();
        glDeleteBuffers(quadVBO);
        glDeleteBuffers(instanceVBO);
    }

    /**
     * Returns a texture unit already bound to {@code textureID}, or binds it to a free unit.
     *
     * <p>Binding rules:</p>
     * <ul>
     *     <li>If the texture id is already in {@link #textureIDs}, returns that unit index.</li>
     *     <li>Otherwise finds the first free slot (-1), binds the texture there, and returns the unit index.</li>
     *     <li>If no free unit exists, returns -1 so the caller can flush and retry.</li>
     * </ul>
     *
     * @param textureID OpenGL texture id
     * @return unit index in [0..maxTextureUnits-1], or -1 if no free units exist
     */
    private int getOrBindTextureUnit(int textureID) {
        for (int i = 0; i < maxTextureUnits; i++) {
            if (textureIDs[i] == textureID) return i;
        }

        for (int i = 0; i < maxTextureUnits; i++) {
            if (textureIDs[i] == -1) {
                textureIDs[i] = textureID;

                glActiveTexture(GL_TEXTURE0 + i);
                glBindTexture(GL_TEXTURE_2D, textureID);
                glActiveTexture(GL_TEXTURE0);
                return i;
            }
        }

        return -1;
    }

    /**
     * Detects {@code GL_MAX_TEXTURE_IMAGE_UNITS} and clamps it to {@code clampTo}.
     *
     * <p>If OpenGL returns an invalid or non-positive value, this method uses a conservative default of 8.</p>
     *
     * @param clampTo upper bound for returned units
     * @return detected units clamped to {@code clampTo}
     */
    private static int detectTextureUnitsClamped(int clampTo) {
        int units = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
        if (units <= 0) units = 8;
        return Math.min(units, clampTo);
    }

    private static final String VERTEX_SHADER = """
            #version 120
            
            attribute vec2 a_local;
            attribute vec2 a_uv;
            
            attribute vec4 i_xywh;
            attribute vec4 i_col;
            attribute float i_tex;
            attribute vec4 i_uvRect;
            
            varying vec2 v_uv;
            varying vec4 v_col;
            varying float v_tex;
            
            void main() {
                vec2 world = i_xywh.xy + (a_local * i_xywh.zw);
                gl_Position = gl_ModelViewProjectionMatrix * vec4(world.xy, 0.0, 1.0);
                v_uv = mix(i_uvRect.xy, i_uvRect.zw, a_uv);
                v_col = i_col;
                v_tex = i_tex;
            }
            """; // GLSL 120 vertex shader for instanced quads.

    /**
     * Builds a GLSL 120 fragment shader for a fixed number of sampler2D uniforms.
     *
     * <p>GLSL 120 does not support sampler arrays, so this function generates an if/else chain that selects
     * among {@code u_tex0..u_texN} based on the per-instance {@code v_tex} value.</p>
     *
     * <p>The selection convention is:</p>
     * <ul>
     *     <li>{@code v_tex < 0.5} selects {@code u_tex0}</li>
     *     <li>{@code v_tex < 1.5} selects {@code u_tex1}</li>
     *     <li>...</li>
     * </ul>
     *
     * @param units number of texture units/samplers to support
     * @return fragment shader source string
     */
    private static String buildFragmentShader(int units) {
        StringBuilder sb = new StringBuilder();
        sb.append("#version 120\n");
        for (int i = 0; i < units; i++) sb.append("uniform sampler2D u_tex").append(i).append(";\n");
        sb.append("""
                varying vec2 v_uv;
                varying vec4 v_col;
                varying float v_tex;
                
                void main() {
                    vec4 c;
                """);

        sb.append("    float t = v_tex;\n");
        sb.append("    if (t < 0.5) c = texture2D(u_tex0, v_uv);\n");
        for (int i = 1; i < units; i++) {
            sb.append("    else if (t < ").append(i).append(".5) c = texture2D(u_tex").append(i).append(", v_uv);\n");
        }
        sb.append("""
                    else c = texture2D(u_tex0, v_uv);
                    gl_FragColor = c * v_col;
                }
                """);
        return sb.toString();
    }
}