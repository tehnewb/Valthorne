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
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/**
 * Instanced sprite batch that preserves draw order while supporting multiple textures per batch.
 *
 * <h2>How it works</h2>
 * <ul>
 *     <li>Binds up to {@code maxTextureUnits} textures to texture units 0..N-1.</li>
 *     <li>Each sprite instance stores {@code i_tex} which selects which unit to sample from.</li>
 *     <li>Each sprite instance stores {@code i_uvRect = (u0,v0,u1,v1)} to support sub-regions (atlases/fonts).</li>
 *     <li>Sprites are drawn in the order {@link #draw(Texture, float, float, float, float)} is called.</li>
 *     <li>The batch flushes when the instance buffer is full, or when a new texture is requested and no unit is free.</li>
 * </ul>
 *
 * <h2>Pipeline notes</h2>
 * <ul>
 *     <li>Uses GLSL 120 and {@code gl_ModelViewProjectionMatrix} so it matches your fixed-function matrix pipeline.</li>
 *     <li>Draw call is {@code glDrawArraysInstanced(GL_TRIANGLES, 0, 6, instanceCount)} (two triangles per quad).</li>
 *     <li>Texture sampling is implemented via an if/else chain (GLSL 120 has no sampler arrays).</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TextureBatch batch = new TextureBatch(5000);
 *
 * batch.begin();
 * batch.setColor(1f, 1f, 1f, 1f);
 *
 * batch.draw(playerTex, 100, 80, 64, 64);
 * batch.draw(uiTex,     10,  10, 32, 32, new Color(1f, 0.4f, 0.4f, 1f));
 *
 * // Draw an atlas region (TextureRegion) at its own position/size:
 * batch.drawRegion(iconRegion);
 *
 * // Draw an atlas region (TextureRegion) scaled:
 * batch.drawRegion(iconRegion, 10, 10, 48, 48);
 *
 * batch.end();
 *
 * batch.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 8th, 2026
 */
public final class TextureBatch {

    private static final int QUAD_VERTS = 6;                                                     // Vertices per quad (two triangles).
    private static final int QUAD_FLOATS_PER_VERT = 4;                                           // localX, localY, u, v.
    private static final int BYTES_PER_FLOAT = 4;                                                // Bytes per float.

    private static final int INST_FLOATS = 13;                                                   // Instance floats: x,y,w,h, r,g,b,a, texUnit, u0,v0,u1,v1.
    private static final int INST_STRIDE_BYTES = INST_FLOATS * BYTES_PER_FLOAT;                  // Instance stride in bytes.

    private static final int ATTR_LOCAL = 0;                                                     // Attribute index: vec2 a_local.
    private static final int ATTR_UV = 1;                                                        // Attribute index: vec2 a_uv.
    private static final int ATTR_XYWH = 2;                                                      // Attribute index: vec4 i_xywh.
    private static final int ATTR_COL = 3;                                                       // Attribute index: vec4 i_col.
    private static final int ATTR_TEX = 4;                                                       // Attribute index: float i_tex.
    private static final int ATTR_UVRECT = 5;                                                    // Attribute index: vec4 i_uvRect.

    private final FloatBuffer instanceBuffer;                                                    // CPU staging buffer for per-instance data.
    private final Color color = new Color(1f, 1f, 1f, 1f);                                       // Current batch tint color.
    private final Shader shader;                                                                 // Shader used for instanced rendering.
    private final int maxSprites;                                                                // Maximum sprites per batch before flush.
    private final int maxTextureUnits;                                                           // Maximum texture units supported by this batch.
    private final int quadVBO;                                                                   // Static quad vertex buffer (local + uv).
    private final int instanceVBO;                                                               // Dynamic per-instance buffer.
    private final int[] textureIDs;                                                              // Texture id per unit; -1 means free.

    private int instanceCount;                                                                   // Current number of queued instances.
    private boolean drawing;                                                                     // True between begin() and end().

    /**
     * Creates a batch with {@code maxSprites} and auto-detects texture units (clamped to {@code <= 16}).
     *
     * <p>This uses {@link #detectTextureUnitsClamped(int)} to choose a safe maximum for GLSL 120.</p>
     *
     * @param maxSprites maximum sprites buffered before flushing
     * @throws IllegalArgumentException if {@code maxSprites <= 0}
     */
    public TextureBatch(int maxSprites) {
        this(maxSprites, detectTextureUnitsClamped(16));
    }

    /**
     * Creates a batch.
     *
     * <p>This allocates:</p>
     * <ul>
     *     <li>A static quad VBO containing local quad coordinates + base UVs.</li>
     *     <li>A dynamic instance VBO sized for {@code maxSprites} instances.</li>
     *     <li>A CPU {@link FloatBuffer} staging area sized for {@code maxSprites} instances.</li>
     * </ul>
     *
     * @param maxSprites      max sprites buffered before flushing
     * @param maxTextureUnits max simultaneously bound textures (2..16 recommended)
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
     * Sets the default tint color applied to sprites drawn without a per-sprite tint.
     *
     * <p>When {@link #draw(Texture, float, float, float, float)} (and other overloads) are called with {@code tint=null},
     * the current batch color is used.</p>
     *
     * @param tint tint color (must be non-null)
     * @throws NullPointerException if {@code tint} is null
     */
    public void setColor(Color tint) {
        Objects.requireNonNull(tint, "tint");
        color.set(tint);
    }

    /**
     * Sets the default tint color applied to sprites drawn without a per-sprite tint.
     *
     * <p>This writes into the internal {@link #color} instance and does not allocate.</p>
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
     * Begins a drawing session.
     *
     * <p>This method:</p>
     * <ul>
     *     <li>Enables blending and sets {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}.</li>
     *     <li>Clears instance state and resets texture bindings.</li>
     *     <li>Binds the shader and uploads sampler uniforms {@code u_tex0..u_texN}.</li>
     *     <li>Binds VBOs and sets attribute pointers and instancing divisors.</li>
     * </ul>
     *
     * @throws IllegalStateException if already drawing
     */
    public void begin() {
        if (drawing) throw new IllegalStateException("Already drawing.");
        drawing = true;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        instanceCount = 0;
        instanceBuffer.clear();

        for (int i = 0; i < maxTextureUnits; i++)
            textureIDs[i] = -1;

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
     * Draws a {@link Texture} using its own stored position and size.
     *
     * <p>This is a convenience overload that calls {@link #draw(Texture, Color)} with {@code color=null}.</p>
     *
     * @param tex texture to draw (must be non-null)
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code tex} is null
     */
    public void draw(Texture tex) {
        draw(tex, null);
    }

    /**
     * Draws a {@link Texture} using its own stored position and size, with an optional tint override.
     *
     * <p>This overload assumes the full texture region (u/v = 0..1).</p>
     *
     * @param tex   texture to draw (must be non-null)
     * @param tint  optional per-sprite tint (null uses current batch tint)
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code tex} is null
     */
    public void draw(Texture tex, Color tint) {
        Objects.requireNonNull(tex, "tex");
        drawUV(tex, tex.getX(), tex.getY(), tex.getWidth(), tex.getHeight(), 0f, 0f, 1f, 1f, tint);
    }

    /**
     * Queues a sprite for drawing using the current batch tint color.
     *
     * <p>This draws the full texture (u/v = 0..1) with the provided destination rectangle.</p>
     *
     * @param tex texture to draw (must be non-null)
     * @param x   world-space x
     * @param y   world-space y
     * @param w   width
     * @param h   height
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code tex} is null
     */
    public void draw(Texture tex, float x, float y, float w, float h) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, null);
    }

    /**
     * Queues a sprite for drawing using either the provided tint or the current batch tint.
     *
     * <p>If the batch is full, this flushes first. If the texture cannot be assigned a unit because all
     * units are occupied, this flushes and retries once.</p>
     *
     * @param tex  texture to draw (must be non-null)
     * @param x    world-space x
     * @param y    world-space y
     * @param w    width
     * @param h    height
     * @param tint optional per-sprite tint (null uses current batch tint)
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code tex} is null
     */
    public void draw(Texture tex, float x, float y, float w, float h, Color tint) {
        drawUV(tex, x, y, w, h, 0f, 0f, 1f, 1f, tint);
    }

    /**
     * Queues a sprite for drawing using a custom UV rectangle.
     *
     * <p>This is useful for drawing sub-regions (atlases/fonts) without creating a {@link TextureRegion} object.</p>
     *
     * @param tex texture to draw (must be non-null)
     * @param x   world-space x
     * @param y   world-space y
     * @param w   width
     * @param h   height
     * @param u0  left u (0..1)
     * @param v0  top v (0..1)
     * @param u1  right u (0..1)
     * @param v1  bottom v (0..1)
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code tex} is null
     */
    public void draw(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        drawUV(tex, x, y, w, h, u0, v0, u1, v1, null);
    }

    /**
     * Queues a sprite for drawing using a custom UV rectangle and optional tint.
     *
     * <p>If the batch is full, this flushes first. If the texture cannot be assigned a unit because all
     * units are occupied, this flushes and retries once.</p>
     *
     * @param tex  texture to draw (must be non-null)
     * @param x    world-space x
     * @param y    world-space y
     * @param w    width
     * @param h    height
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
        Objects.requireNonNull(tex, "tex");

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
     * Queues a sub-region of a texture for drawing using region coordinates in pixels.
     *
     * <p>This method converts pixel region coordinates into normalized UVs using the backing texture size.</p>
     *
     * @param tex          texture to draw (must be non-null)
     * @param x            world-space x position
     * @param y            world-space y position
     * @param w            destination width
     * @param h            destination height
     * @param regionX      x offset of the region within the texture (pixels)
     * @param regionY      y offset of the region within the texture (pixels)
     * @param regionWidth  width of the region within the texture (pixels)
     * @param regionHeight height of the region within the texture (pixels)
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code tex} is null
     */
    public void drawRegion(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight) {
        drawRegion(tex, x, y, w, h, regionX, regionY, regionWidth, regionHeight, null);
    }

    /**
     * Queues a {@link TextureRegion} for drawing using the region's stored destination rectangle.
     *
     * <p>This uses the region's backing texture, region pixel rectangle, and the region's own x/y/width/height.</p>
     *
     * @param region region to draw (must be non-null)
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code region} is null
     */
    public void drawRegion(TextureRegion region) {
        Objects.requireNonNull(region, "region");
        drawRegion(
                region.getTexture(),
                region.getX(), region.getY(),
                region.getWidth(), region.getHeight(),
                region.getRegionX(), region.getRegionY(),
                region.getRegionWidth(), region.getRegionHeight(),
                null
        );
    }

    /**
     * Queues a {@link TextureRegion} for drawing at a custom destination rectangle.
     *
     * <p>This keeps the same source region (pixel rectangle inside the atlas) but allows scaling/positioning.</p>
     *
     * @param region region to draw (must be non-null)
     * @param x      world-space x position
     * @param y      world-space y position
     * @param w      destination width
     * @param h      destination height
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code region} is null
     */
    public void drawRegion(TextureRegion region, float x, float y, float w, float h) {
        Objects.requireNonNull(region, "region");
        drawRegion(
                region.getTexture(),
                x, y, w, h,
                region.getRegionX(), region.getRegionY(),
                region.getRegionWidth(), region.getRegionHeight(),
                null
        );
    }

    /**
     * Queues a sub-region of a texture for drawing with optional tint.
     *
     * <p>The specified region rectangle inside the texture is mapped to the given destination rectangle.</p>
     *
     * @param tex          the texture to draw (must be non-null)
     * @param x            world-space x position
     * @param y            world-space y position
     * @param w            destination width
     * @param h            destination height
     * @param regionX      x offset of the region within the texture (pixels)
     * @param regionY      y offset of the region within the texture (pixels)
     * @param regionWidth  width of the region within the texture (pixels)
     * @param regionHeight height of the region within the texture (pixels)
     * @param tint         optional per-sprite tint (null uses current batch tint)
     * @throws IllegalStateException if begin() has not been called
     * @throws NullPointerException  if {@code tex} is null
     */
    public void drawRegion(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight, Color tint) {
        Objects.requireNonNull(tex, "tex");

        float texW = tex.getWidth();
        float texH = tex.getHeight();

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
     * Ends a drawing session and flushes any remaining sprites.
     *
     * <p>This resets attribute divisors to 0, disables all enabled attribute arrays, and unbinds the shader.</p>
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
    }

    /**
     * Flushes the current instance buffer and draws all queued sprites.
     *
     * <p>This method:</p>
     * <ul>
     *     <li>Uploads {@link #instanceBuffer} into {@link #instanceVBO}.</li>
     *     <li>Issues a single {@code glDrawArraysInstanced} call for all queued instances.</li>
     *     <li>Clears the CPU buffer and resets {@link #instanceCount}.</li>
     * </ul>
     *
     * <p>If no instances are queued, this method does nothing.</p>
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
     * <p>This deletes:</p>
     * <ul>
     *     <li>The internal shader program</li>
     *     <li>The quad VBO</li>
     *     <li>The instance VBO</li>
     * </ul>
     *
     * <p>Do not use the batch after calling this.</p>
     */
    public void dispose() {
        shader.dispose();
        glDeleteBuffers(quadVBO);
        glDeleteBuffers(instanceVBO);
    }

    /**
     * Returns a texture unit already bound to {@code textureId}, or binds it to a free unit.
     *
     * <p>Binding rules:</p>
     * <ul>
     *     <li>If the texture is already bound, returns that unit index immediately.</li>
     *     <li>If a free unit exists (textureIDs[i] == -1), binds and returns that index.</li>
     *     <li>If no free unit exists, returns -1 (caller typically flushes and retries).</li>
     * </ul>
     *
     * @param textureId OpenGL texture id
     * @return unit index, or -1 if no free units exist
     */
    private int getOrBindTextureUnit(int textureId) {
        for (int i = 0; i < maxTextureUnits; i++) {
            if (textureIDs[i] == textureId) return i;
        }

        for (int i = 0; i < maxTextureUnits; i++) {
            if (textureIDs[i] == -1) {
                textureIDs[i] = textureId;

                glActiveTexture(GL_TEXTURE0 + i);
                glBindTexture(GL_TEXTURE_2D, textureId);
                glActiveTexture(GL_TEXTURE0);
                return i;
            }
        }

        return -1;
    }

    /**
     * Detects {@code GL_MAX_TEXTURE_IMAGE_UNITS} and clamps it to {@code clampTo}.
     *
     * <p>This is used to size the fragment shader sampler list.</p>
     *
     * @param clampTo upper bound for returned units
     * @return detected units (falls back to 8 if query fails), clamped to {@code clampTo}
     */
    private static int detectTextureUnitsClamped(int clampTo) {
        int units = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
        if (units <= 0) units = 8;
        return Math.min(units, clampTo);
    }

    /**
     * Vertex shader source (GLSL 120) for instanced sprites.
     *
     * <p>This transforms a unit quad into world-space using {@code i_xywh} and maps base UVs into a per-instance
     * UV rectangle using {@code mix(i_uvRect.xy, i_uvRect.zw, a_uv)}.</p>
     */
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
            """;

    /**
     * Builds a GLSL 120 fragment shader that selects among {@code units} samplers using an if/else chain.
     *
     * <p>GLSL 120 does not support sampler arrays, so we bind {@code u_tex0..u_texN} and select the sampler
     * using comparisons against {@code v_tex}.</p>
     *
     * @param units number of texture units/samplers to support
     * @return fragment shader source string
     */
    private static String buildFragmentShader(int units) {
        StringBuilder sb = new StringBuilder();
        sb.append("#version 120\n");
        for (int i = 0; i < units; i++) {
            sb.append("uniform sampler2D u_tex").append(i).append(";\n");
        }
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