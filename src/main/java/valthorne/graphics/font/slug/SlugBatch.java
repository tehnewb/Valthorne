package valthorne.graphics.font.slug;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL33.*;

/**
 * Fast 2D instanced batch renderer for {@link SlugFont} glyphs.
 *
 * <p>This path is optimized for live font rendering in a normal orthographic 2D pass. It removes
 * the expensive Slug reference vertex dilation math and applies a small CPU-side pad to every glyph
 * quad instead. That keeps the fragment shader from clipping antialiasing while making the vertex
 * shader much cheaper. This pass also sends the pixels-per-em value as a flat instance
 * attribute so the fragment shader does not need to call fwidth() for ordinary 2D text.</p>
 * <p>Use one graphics context thread. Begin captures the GL state changed by this
 * batch; end flushes pending instances and restores it, while cancel restores it
 * without drawing pending work. Clip and viewport bounds reject outside glyphs
 * and crop intersecting glyph quads on the CPU without changing hardware scissor state.</p>
 * <pre>{@code
 * SlugTextRun run = font.createRun("Ready", 24f);
 * batch.begin(projection, viewportWidth, viewportHeight);
 * try {
 *     run.draw(batch, 20f, 40f, Color.WHITE);
 *     batch.end();
 * } finally {
 *     batch.cancel();
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public final class SlugBatch {

    /**
     * Byte width of each floating-point instance component.
     */
    private static final int BYTES_PER_FLOAT = 4;
    /**
     * Byte width of a packed unsigned-integer glyph component.
     */
    private static final int BYTES_PER_INT = 4;
    /**
     * Byte width of four normalized RGBA color components.
     */
    private static final int BYTES_PER_COLOR = 4;

    /**
     * Byte stride of static two-float unit-quad corners.
     */
    private static final int CORNER_STRIDE = 2 * BYTES_PER_FLOAT;

    /**
     * Byte offset of the world rectangle's four floats.
     */
    private static final int RECT_OFFSET = 0;
    /**
     * Byte offset of the em-space outline rectangle.
     */
    private static final int TEX_RECT_OFFSET = RECT_OFFSET + 4 * BYTES_PER_FLOAT;
    /**
     * Byte offset of two packed unsigned glyph metadata integers.
     */
    private static final int GLYPH_OFFSET = TEX_RECT_OFFSET + 4 * BYTES_PER_FLOAT;
    /**
     * Byte offset of band scale and offset vectors.
     */
    private static final int BAND_OFFSET = GLYPH_OFFSET + 2 * BYTES_PER_INT;
    /**
     * Byte offset of the antialiasing scale float.
     */
    private static final int PIXELS_PER_EM_OFFSET = BAND_OFFSET + 4 * BYTES_PER_FLOAT;
    /**
     * Byte offset of normalized RGBA color bytes.
     */
    private static final int COLOR_OFFSET = PIXELS_PER_EM_OFFSET + BYTES_PER_FLOAT;
    /**
     * Total byte stride of one interleaved glyph instance.
     */
    private static final int INSTANCE_STRIDE = COLOR_OFFSET + BYTES_PER_COLOR;

    private final int maxGlyphs; // Maximum queued glyph instances before an automatic flush.
    private final ByteBuffer instanceBuffer; // CPU staging buffer for streamed glyph instances.
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16); // Temporary matrix upload buffer.
    private final IntBuffer stateBuffer = BufferUtils.createIntBuffer(1); // Reusable direct storage for scalar GL state queries.

    /**
     * Reads one integer GL state value into reusable direct scratch on the context thread.
     * @param name scalar OpenGL state selector
     * @return captured integer value
     */
    private int integerState(int name) {
        glGetIntegerv(name, stateBuffer);
        return stateBuffer.get(0);
    }
    private final SlugShader shader; // GLSL Slug shader.
    private final int vao; // Vertex array object.
    private final int cornerVbo; // Static four-corner VBO.
    private final int instanceVbo; // Streaming instance VBO.

    private SlugFont activeFont; // Font whose curve/band textures are bound for the current queue.
    private int instanceCount; // Number of queued glyph instances.
    private boolean drawing; // True between begin and end.
    private boolean disposed, oldDepth, oldCull, oldDepthMask; // Disposal flag and saved depth-test, cull, and depth-write state.
    private int oldProgram, oldVao, oldBuffer, oldActive, oldTexture0, oldTexture1, oldSampler0, oldSampler1; // Saved program, vertex/buffer bindings, active unit, textures, and samplers.
    private int oldSrcAlpha, oldDstAlpha, oldEquationRgb, oldEquationAlpha; // Saved alpha blend factors and separate RGB/alpha blend equations.
    private float projectionScale = 1, effectivePadding; // Pixels-per-world-unit estimate and conservative world-space antialiasing padding.
    private float viewMinX, viewMinY, viewMaxX, viewMaxY; // World-space viewport rejection bounds derived from compatible projections.
    private float clipMinX = Float.NEGATIVE_INFINITY, clipMinY = Float.NEGATIVE_INFINITY; // Optional CPU clip minimum coordinates; negative infinity disables the lower limits.
    private float clipMaxX = Float.POSITIVE_INFINITY, clipMaxY = Float.POSITIVE_INFINITY; // Optional CPU clip maximum coordinates; positive infinity disables the upper limits.
    private int glyphsSubmitted, drawCalls; // Accepted glyph and actual draw counters reset at each begin.

    /**
     * Restricts subsequently submitted glyphs to intersection with a world-space rectangle.
     * Outside glyphs are rejected; intersecting quads and their em-space coordinates
     * are cropped before upload. Hardware scissor state and existing queued glyphs
     * are unchanged. The rectangle follows world axes, including under rotated projections.
     * @param x rectangle left coordinate in glyph world units
     * @param y rectangle lower coordinate in glyph world units
     * @param width finite nonnegative rectangle width
     * @param height finite nonnegative rectangle height
     * @throws IllegalArgumentException if any component is nonfinite or a dimension is negative
     */
    public void setClip(float x, float y, float width, float height) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0)
            throw new IllegalArgumentException("Clip must be finite with nonnegative dimensions");
        clipMinX = x; clipMinY = y; clipMaxX = x + width; clipMaxY = y + height;
    }
    /**
     * Removes the optional CPU clip rectangle for subsequent submissions. Viewport
     * rejection still applies when begin can derive bounds from the projection.
     */
    public void clearClip() {
        clipMinX = clipMinY = Float.NEGATIVE_INFINITY;
        clipMaxX = clipMaxY = Float.POSITIVE_INFINITY;
    }
    /**
     * Reads accepted glyph submissions since the latest begin, including flushed glyphs.
     * @return accepted instance count for the current or most recently completed pass
     */
    public int getGlyphsSubmitted() { return glyphsSubmitted; }
    /**
     * Reads actual nonempty batch draws since the latest begin; font changes and capacity
     * flushes can make this exceed one even during a single text pass.
     * @return draw-call count for the current or most recently completed pass
     */
    public int getDrawCalls() { return drawCalls; }
    /**
     * Rejects submission unless this batch is alive and inside a begin/end interval.
     * @throws IllegalStateException if disposed or not drawing
     */
    void requireDrawing() { if (!drawing || disposed) throw new IllegalStateException("SlugBatch must be alive and drawing"); }
    /**
     * Tests a world-space glyph or run rectangle against viewport and optional clip bounds,
     * expanding it by the effective antialiasing padding. Intersection does not crop pixels.
     * @param x0 minimum x
     * @param y0 minimum y
     * @param x1 maximum x
     * @param y1 maximum y
     * @return whether submission may contribute visible coverage
     */
    boolean intersects(float x0, float y0, float x1, float y1) {
        return x1 + effectivePadding > Math.max(viewMinX, clipMinX) && y1 + effectivePadding > Math.max(viewMinY, clipMinY)
                && x0 - effectivePadding < Math.min(viewMaxX, clipMaxX) && y0 - effectivePadding < Math.min(viewMaxY, clipMaxY);
    }
    private boolean blendEnabledBeforeBegin; // Blend state captured at begin().
    private int blendSrcBeforeBegin; // Blend source factor captured at begin().
    private int blendDstBeforeBegin; // Blend destination factor captured at begin().
    private float quadPadding = 0.5f; // CPU-side pad in world units for a normal pixel-space 2D pass.
    private boolean orphanOnFlush = true; // Orphan stream buffer before uploading to avoid GPU/CPU sync stalls.

    /**
     * Creates a Slug batch with a default capacity of 4096 glyphs.
     */
    public SlugBatch() {
        this(4096);
    }

    /**
     * Creates a Slug batch with a custom glyph capacity.
     *
     * @param maxGlyphs maximum glyph instances queued before flush
     */
    public SlugBatch(int maxGlyphs) {
        if (maxGlyphs <= 0) {
            throw new IllegalArgumentException("maxGlyphs must be > 0.");
        }

        this.maxGlyphs = maxGlyphs;
        this.instanceBuffer = BufferUtils.createByteBuffer(Math.multiplyExact(this.maxGlyphs, INSTANCE_STRIDE));
        int previousProgram = integerState(GL_CURRENT_PROGRAM), previousVao = integerState(GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = integerState(GL_ARRAY_BUFFER_BINDING);
        this.shader = new SlugShader();

        this.vao = glGenVertexArrays();
        this.cornerVbo = glGenBuffers();
        this.instanceVbo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, cornerVbo);
        FloatBuffer corners = BufferUtils.createFloatBuffer(8);
        corners.put(0f).put(0f);
        corners.put(1f).put(0f);
        corners.put(0f).put(1f);
        corners.put(1f).put(1f);
        corners.flip();
        glBufferData(GL_ARRAY_BUFFER, corners, GL_STATIC_DRAW);

        glEnableVertexAttribArray(SlugShader.ATTR_CORNER);
        glVertexAttribPointer(SlugShader.ATTR_CORNER, 2, GL_FLOAT, false, CORNER_STRIDE, 0L);

        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) this.maxGlyphs * INSTANCE_STRIDE, GL_STREAM_DRAW);

        glEnableVertexAttribArray(SlugShader.ATTR_RECT);
        glVertexAttribPointer(SlugShader.ATTR_RECT, 4, GL_FLOAT, false, INSTANCE_STRIDE, RECT_OFFSET);
        glVertexAttribDivisor(SlugShader.ATTR_RECT, 1);

        glEnableVertexAttribArray(SlugShader.ATTR_TEX_RECT);
        glVertexAttribPointer(SlugShader.ATTR_TEX_RECT, 4, GL_FLOAT, false, INSTANCE_STRIDE, TEX_RECT_OFFSET);
        glVertexAttribDivisor(SlugShader.ATTR_TEX_RECT, 1);

        glEnableVertexAttribArray(SlugShader.ATTR_GLYPH);
        glVertexAttribIPointer(SlugShader.ATTR_GLYPH, 2, GL_UNSIGNED_INT, INSTANCE_STRIDE, GLYPH_OFFSET);
        glVertexAttribDivisor(SlugShader.ATTR_GLYPH, 1);

        glEnableVertexAttribArray(SlugShader.ATTR_BAND);
        glVertexAttribPointer(SlugShader.ATTR_BAND, 4, GL_FLOAT, false, INSTANCE_STRIDE, BAND_OFFSET);
        glVertexAttribDivisor(SlugShader.ATTR_BAND, 1);

        glEnableVertexAttribArray(SlugShader.ATTR_PIXELS_PER_EM);
        glVertexAttribPointer(SlugShader.ATTR_PIXELS_PER_EM, 1, GL_FLOAT, false, INSTANCE_STRIDE, PIXELS_PER_EM_OFFSET);
        glVertexAttribDivisor(SlugShader.ATTR_PIXELS_PER_EM, 1);

        glEnableVertexAttribArray(SlugShader.ATTR_COLOR);
        glVertexAttribPointer(SlugShader.ATTR_COLOR, 4, GL_UNSIGNED_BYTE, true, INSTANCE_STRIDE, COLOR_OFFSET);
        glVertexAttribDivisor(SlugShader.ATTR_COLOR, 1);

        glBindVertexArray(previousVao);
        glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
        glUseProgram(previousProgram);
    }

    /**
     * Rounds a normalized float color component to an integer byte and clamps the
     * result to [0,255]. Java numeric conversion maps NaN to zero.
     *
     * @param value color component
     * @return unsigned byte value stored as an int
     */
    private static int toByte(float value) {
        int v = (int) (value * 255.0f + 0.5f);
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    /**
     * Begins collecting Slug glyph draw calls.
     *
     * <p>Viewport dimensions must be the active framebuffer viewport's pixel dimensions.
     * Axis-aligned projections enable CPU culling and pixel-correct coverage. Other
     * projections use derivative-based coverage without viewport CPU culling.
     * This is an overlay renderer: depth testing/writes and face culling are disabled
     * temporarily. Program, bindings, samplers, and blend/depth/cull state are restored
     * by end or cancel. Framebuffer, viewport, scissor, and stencil state are untouched.</p>
     *
     * @param mvp       model-view-projection matrix used to transform glyph world positions
     * @param viewportW current viewport width in pixels
     * @param viewportH current viewport height in pixels
     */
    public void begin(Matrix4f mvp, float viewportW, float viewportH) {
        if (disposed) throw new IllegalStateException("SlugBatch is disposed");
        if (drawing) {
            throw new IllegalStateException("SlugBatch is already drawing.");
        }
        if (mvp == null) {
            throw new NullPointerException("mvp");
        }
        if (!mvp.isFinite() || !Float.isFinite(viewportW) || !Float.isFinite(viewportH) || viewportW <= 0 || viewportH <= 0)
            throw new IllegalArgumentException("Projection and viewport must be finite and valid");
        viewMinX = viewMinY = Float.NEGATIVE_INFINITY;
        viewMaxX = viewMaxY = Float.POSITIVE_INFINITY;
        projectionScale = -1;
        effectivePadding = quadPadding;
        if (mvp.m01() == 0 && mvp.m10() == 0 && mvp.m03() == 0 && mvp.m13() == 0 && mvp.m33() > 0 && mvp.m00() != 0 && mvp.m11() != 0) {
            float x0 = (-mvp.m33() - mvp.m30()) / mvp.m00(), x1 = (mvp.m33() - mvp.m30()) / mvp.m00();
            float y0 = (-mvp.m33() - mvp.m31()) / mvp.m11(), y1 = (mvp.m33() - mvp.m31()) / mvp.m11();
            viewMinX = Math.min(x0,x1); viewMaxX = Math.max(x0,x1);
            viewMinY = Math.min(y0,y1); viewMaxY = Math.max(y0,y1);
            float sx = Math.abs(mvp.m00()) * viewportW / (2 * mvp.m33());
            float sy = Math.abs(mvp.m11()) * viewportH / (2 * mvp.m33());
            if (Math.abs(sx-sy) < Math.min(sx,sy) * .0001f) projectionScale = sx;
            effectivePadding = Math.max(quadPadding, .5f / Math.min(sx,sy));
        }

        drawing = true;
        instanceCount = 0;
        activeFont = null;
        instanceBuffer.clear();
        glyphsSubmitted = drawCalls = 0;
        oldProgram = integerState(GL_CURRENT_PROGRAM); oldVao = integerState(GL_VERTEX_ARRAY_BINDING);
        oldBuffer = integerState(GL_ARRAY_BUFFER_BINDING); oldActive = integerState(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0); oldTexture0 = integerState(GL_TEXTURE_BINDING_2D); oldSampler0 = integerState(GL_SAMPLER_BINDING);
        glActiveTexture(GL_TEXTURE0+1); oldTexture1 = integerState(GL_TEXTURE_BINDING_2D); oldSampler1 = integerState(GL_SAMPLER_BINDING);
        glBindSampler(0,0); glBindSampler(1,0);
        oldDepth = glIsEnabled(GL_DEPTH_TEST); oldCull = glIsEnabled(GL_CULL_FACE); oldDepthMask = integerState(GL_DEPTH_WRITEMASK) != 0;
        oldSrcAlpha = integerState(GL_BLEND_SRC_ALPHA); oldDstAlpha = integerState(GL_BLEND_DST_ALPHA);
        oldEquationRgb = integerState(GL_BLEND_EQUATION_RGB); oldEquationAlpha = integerState(GL_BLEND_EQUATION_ALPHA);

        blendEnabledBeforeBegin = glIsEnabled(GL_BLEND);
        blendSrcBeforeBegin = integerState(GL_BLEND_SRC);
        blendDstBeforeBegin = integerState(GL_BLEND_DST);

        shader.bind();
        matrixBuffer.clear();
        mvp.get(matrixBuffer);
        glUniformMatrix4fv(shader.mvpLocation(), false, matrixBuffer);

        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
        glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDepthMask(false);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
    }

    /**
     * Ends the current batch and flushes remaining glyphs.
     */
    public void end() {
        if (!drawing) {
            throw new IllegalStateException("Call begin() before end().");
        }

        try { flush(); } finally { restore(); }
    }

    /**
     * Abandons unflushed glyphs and restores captured GL state if a pass is active.
     * Already flushed draws remain visible. Safe to call after end or from a finally block.
     */
    public void cancel() { if (drawing) restore(); }

    /**
     * Restores the program, vertex/buffer bindings, texture and sampler units zero and one,
     * active texture unit, depth/cull state, depth mask, and separate blend state captured
     * by begin. Clears pending instances and ends drawing without issuing another draw.
     */
    private void restore() {
        glBindBuffer(GL_ARRAY_BUFFER, oldBuffer);
        glBindVertexArray(oldVao);
        glUseProgram(oldProgram);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,oldTexture0); glBindSampler(0,oldSampler0);
        glActiveTexture(GL_TEXTURE0+1); glBindTexture(GL_TEXTURE_2D,oldTexture1); glBindSampler(1,oldSampler1);
        glActiveTexture(oldActive);
        if(oldDepth) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
        if(oldCull) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
        glDepthMask(oldDepthMask);

        if (blendEnabledBeforeBegin) {
            glEnable(GL_BLEND);
        } else {
            glDisable(GL_BLEND);
        }
        glBlendFuncSeparate(blendSrcBeforeBegin, blendDstBeforeBegin, oldSrcAlpha, oldDstAlpha);
        glBlendEquationSeparate(oldEquationRgb, oldEquationAlpha);

        drawing = false;
        activeFont = null;
        instanceBuffer.clear();
        instanceCount = 0;
    }

    /**
     * Queues a padded glyph quad, flushing when the font changes or capacity is
     * reached. Skips absent/nondrawable glyphs, null or nonpositive-alpha colors, and
     * zero size. Converts world padding back into em coordinates so the outline
     * lookup covers the enlarged quad.
     *
     * @param font borrowed live font supplying data textures
     * @param glyph compiled glyph metadata
     * @param baselineX world baseline X
     * @param baselineY world baseline Y
     * @param size world units per em
     * @param color copied draw color
     * @throws IllegalStateException if begin has not started a drawing scope
     */
    void drawGlyph(SlugFont font, SlugGlyph glyph, float baselineX, float baselineY, float size, Color color) {
        if (!drawing) {
            throw new IllegalStateException("Call begin() before drawing Slug text.");
        }
        if (font == null || glyph == null || !glyph.drawable || color == null || color.a() <= 0f || size == 0f) {
            return;
        }
        if (font.curveTexture() == 0) throw new IllegalStateException("Slug font is disposed");
        if (!Float.isFinite(size) || size < 0) throw new IllegalArgumentException("Size must be finite and nonnegative");
        if (!intersects(baselineX + glyph.x0 * size, baselineY + glyph.y0 * size, baselineX + glyph.x1 * size, baselineY + glyph.y1 * size)) return;

        if (activeFont != font) {
            flush();
            activeFont = font;
        }
        if (instanceCount + 1 > maxGlyphs) {
            flush();
        }

        float pad = effectivePadding;
        float invSize = 1.0f / size;

        float x0 = baselineX + glyph.x0 * size - pad;
        float y0 = baselineY + glyph.y0 * size - pad;
        float x1 = baselineX + glyph.x1 * size + pad;
        float y1 = baselineY + glyph.y1 * size + pad;
        float cx0 = Math.max(x0, Math.max(viewMinX,clipMinX)), cy0 = Math.max(y0, Math.max(viewMinY,clipMinY));
        float cx1 = Math.min(x1, Math.min(viewMaxX,clipMaxX)), cy1 = Math.min(y1, Math.min(viewMaxY,clipMaxY));
        if (cx0 >= cx1 || cy0 >= cy1) return;

        putInstance(
                cx0, cy0, cx1, cy1,
                (cx0 - baselineX) * invSize, (cy0 - baselineY) * invSize, (cx1 - baselineX) * invSize, (cy1 - baselineY) * invSize,
                glyph.glyphPack, glyph.glyphInfoPack,
                glyph.bandScaleX, glyph.bandScaleY, glyph.bandOffsetX, glyph.bandOffsetY,
                projectionScale > 0 ? size * projectionScale : -1, color.r(), color.g(), color.b(), color.a()
        );
    }

    /**
     * Appends one complete glyph instance to preallocated staging storage and
     * increments the queue count. The caller ensures capacity first. Colors are
     * rounded and clamped to normalized byte attributes.
     *
     * @param x0 world rectangle minimum X
     * @param y0 world rectangle minimum Y
     * @param x1 world rectangle maximum X
     * @param y1 world rectangle maximum Y
     * @param tx0 em-space rectangle minimum X
     * @param ty0 em-space rectangle minimum Y
     * @param tx1 em-space rectangle maximum X
     * @param ty1 em-space rectangle maximum Y
     * @param glyphPack packed band-texture start address
     * @param glyphInfoPack packed band counts and fill rule
     * @param bandScaleX em-to-vertical-band scale
     * @param bandScaleY em-to-horizontal-band scale
     * @param bandOffsetX vertical-band coordinate offset
     * @param bandOffsetY horizontal-band coordinate offset
     * @param pixelsPerEm absolute drawing scale for antialiasing
     * @param r red component
     * @param g green component
     * @param b blue component
     * @param a alpha component
     */
    private void putInstance(float x0, float y0, float x1, float y1,
                             float tx0, float ty0, float tx1, float ty1,
                             int glyphPack, int glyphInfoPack,
                             float bandScaleX, float bandScaleY, float bandOffsetX, float bandOffsetY,
                             float pixelsPerEm,
                             float r, float g, float b, float a) {
        instanceBuffer.putFloat(x0).putFloat(y0).putFloat(x1).putFloat(y1);
        instanceBuffer.putFloat(tx0).putFloat(ty0).putFloat(tx1).putFloat(ty1);
        instanceBuffer.putInt(glyphPack).putInt(glyphInfoPack);
        instanceBuffer.putFloat(bandScaleX).putFloat(bandScaleY).putFloat(bandOffsetX).putFloat(bandOffsetY);
        instanceBuffer.putFloat(pixelsPerEm);
        instanceBuffer.put((byte) toByte(r)).put((byte) toByte(g)).put((byte) toByte(b)).put((byte) toByte(a));
        instanceCount++;
        glyphsSubmitted++;
    }

    /**
     * Flushes queued glyphs to the GPU.
     */
    public void flush() {
        if (instanceCount <= 0 || activeFont == null) {
            instanceBuffer.clear();
            instanceCount = 0;
            return;
        }

        if (activeFont.curveTexture() == 0) throw new IllegalStateException("Slug font is disposed");
        instanceBuffer.flip();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, activeFont.curveTexture());
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, activeFont.bandTexture());

        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        if (orphanOnFlush) {
            glBufferData(GL_ARRAY_BUFFER, (long) maxGlyphs * INSTANCE_STRIDE, GL_STREAM_DRAW);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0L, instanceBuffer);

        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount);
        drawCalls++;

        instanceBuffer.clear();
        instanceCount = 0;
    }

    /**
     * Returns the CPU-side quad padding in world units.
     *
     * @return quad padding
     */
    public float getQuadPadding() {
        return quadPadding;
    }

    /**
     * Sets CPU-side quad padding in world units.
     *
     * <p>The default is 0.5 world units. Axis-aligned projections automatically raise
     * this to at least half a screen pixel. For other projections choose enough
     * world-space padding to cover antialiasing at the smallest visible scale.</p>
     *
     * @param quadPadding quad padding in world units
     */
    public void setQuadPadding(float quadPadding) {
        if (!Float.isFinite(quadPadding) || quadPadding < 0f) {
            throw new IllegalArgumentException("quadPadding cannot be negative.");
        }
        this.quadPadding = quadPadding;
    }

    /**
     * Returns whether the instance buffer is orphaned before each flush.
     *
     * @return true if buffer orphaning is enabled
     */
    public boolean isOrphanOnFlush() {
        return orphanOnFlush;
    }

    /**
     * Enables or disables stream-buffer orphaning before each flush.
     *
     * <p>Leaving this enabled is usually faster for dynamic text because it prevents the driver from
     * waiting on the previous frame's instance buffer. Disable only if profiling shows your driver is
     * faster without it.</p>
     *
     * @param orphanOnFlush true to orphan the instance buffer before uploading queued glyphs
     */
    public void setOrphanOnFlush(boolean orphanOnFlush) {
        this.orphanOnFlush = orphanOnFlush;
    }

    /**
     * Releases the shader and OpenGL buffers owned by this batch.
     */
    public void dispose() {
        if (disposed) return;
        cancel();
        disposed = true;
        glDeleteBuffers(instanceVbo);
        glDeleteBuffers(cornerVbo);
        glDeleteVertexArrays(vao);
        shader.dispose();
    }

    /**
     * Returns the maximum glyph capacity of this batch.
     *
     * @return maximum glyphs before flush
     */
    public int getMaxGlyphs() {
        return maxGlyphs;
    }
}
