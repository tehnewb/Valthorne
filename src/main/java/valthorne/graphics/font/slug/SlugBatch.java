package valthorne.graphics.font.slug;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.math.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/**
 * Fast 2D instanced batch renderer for {@link SlugFont} glyphs.
 *
 * <p>This path is optimized for live font rendering in a normal orthographic 2D pass. It removes
 * the expensive Slug reference vertex dilation math and applies a small CPU-side pad to every glyph
 * quad instead. That keeps the fragment shader from clipping antialiasing while making the vertex
 * shader much cheaper. This pass also sends the pixels-per-em value as a flat instance
 * attribute so the fragment shader does not need to call fwidth() for ordinary 2D text.</p>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public final class SlugBatch {

    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_INT = 4;
    private static final int BYTES_PER_COLOR = 4;

    private static final int CORNER_STRIDE = 2 * BYTES_PER_FLOAT;

    private static final int RECT_OFFSET = 0;
    private static final int TEX_RECT_OFFSET = RECT_OFFSET + 4 * BYTES_PER_FLOAT;
    private static final int GLYPH_OFFSET = TEX_RECT_OFFSET + 4 * BYTES_PER_FLOAT;
    private static final int BAND_OFFSET = GLYPH_OFFSET + 2 * BYTES_PER_INT;
    private static final int PIXELS_PER_EM_OFFSET = BAND_OFFSET + 4 * BYTES_PER_FLOAT;
    private static final int COLOR_OFFSET = PIXELS_PER_EM_OFFSET + BYTES_PER_FLOAT;
    private static final int INSTANCE_STRIDE = COLOR_OFFSET + BYTES_PER_COLOR;

    private final int maxGlyphs; // Maximum queued glyph instances before an automatic flush.
    private final ByteBuffer instanceBuffer; // CPU staging buffer for streamed glyph instances.
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16); // Temporary matrix upload buffer.
    private final SlugShader shader; // GLSL Slug shader.
    private final int vao; // Vertex array object.
    private final int cornerVbo; // Static four-corner VBO.
    private final int instanceVbo; // Streaming instance VBO.

    private SlugFont activeFont; // Font whose curve/band textures are bound for the current queue.
    private int instanceCount; // Number of queued glyph instances.
    private boolean drawing; // True between begin and end.
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
        this.instanceBuffer = BufferUtils.createByteBuffer(this.maxGlyphs * INSTANCE_STRIDE);
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

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Begins collecting Slug glyph draw calls.
     *
     * <p>The viewport arguments are retained for API compatibility with the reference-style batch.
     * This fast 2D path does not need them because quad expansion is performed on the CPU.</p>
     *
     * @param mvp       model-view-projection matrix used to transform glyph world positions
     * @param viewportW current viewport width in pixels
     * @param viewportH current viewport height in pixels
     */
    public void begin(Matrix4f mvp, float viewportW, float viewportH) {
        if (drawing) {
            throw new IllegalStateException("SlugBatch is already drawing.");
        }
        if (mvp == null) {
            throw new NullPointerException("mvp");
        }

        drawing = true;
        instanceCount = 0;
        activeFont = null;
        instanceBuffer.clear();

        blendEnabledBeforeBegin = glIsEnabled(GL_BLEND);
        blendSrcBeforeBegin = glGetInteger(GL_BLEND_SRC);
        blendDstBeforeBegin = glGetInteger(GL_BLEND_DST);

        shader.bind();
        matrixBuffer.clear();
        matrixBuffer.put(mvp.get()).flip();
        glUniformMatrix4fv(shader.mvpLocation(), false, matrixBuffer);

        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

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

        flush();
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        shader.unbind();

        if (blendEnabledBeforeBegin) {
            glEnable(GL_BLEND);
        } else {
            glDisable(GL_BLEND);
        }
        glBlendFunc(blendSrcBeforeBegin, blendDstBeforeBegin);

        drawing = false;
        activeFont = null;
        instanceBuffer.clear();
    }

    void drawGlyph(SlugFont font, SlugGlyph glyph, float baselineX, float baselineY, float size, Color color) {
        if (!drawing) {
            throw new IllegalStateException("Call begin() before drawing Slug text.");
        }
        if (font == null || glyph == null || !glyph.drawable || color == null || color.a() <= 0f || size == 0f) {
            return;
        }

        if (activeFont != font) {
            flush();
            activeFont = font;
        }
        if (instanceCount + 1 > maxGlyphs) {
            flush();
        }

        float pad = quadPadding;
        float invSize = 1.0f / size;
        float padEm = pad * invSize;

        float x0 = baselineX + glyph.x0 * size - pad;
        float y0 = baselineY + glyph.y0 * size - pad;
        float x1 = baselineX + glyph.x1 * size + pad;
        float y1 = baselineY + glyph.y1 * size + pad;

        putInstance(
                x0, y0, x1, y1,
                glyph.x0 - padEm, glyph.y0 - padEm, glyph.x1 + padEm, glyph.y1 + padEm,
                glyph.glyphPack, glyph.glyphInfoPack,
                glyph.bandScaleX, glyph.bandScaleY, glyph.bandOffsetX, glyph.bandOffsetY,
                Math.abs(size), color.r(), color.g(), color.b(), color.a()
        );
    }

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
    }

    private static int toByte(float value) {
        int v = (int) (value * 255.0f + 0.5f);
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
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

        instanceBuffer.clear();
        instanceCount = 0;
    }

    /**
     * Sets CPU-side quad padding in world units.
     *
     * <p>For pixel-space orthographic UI/text rendering, leave this at the default 0.75. If you use
     * a world-space camera where one world unit is not one pixel, set this to the world-size of about
     * 0.75 screen pixels.</p>
     *
     * @param quadPadding quad padding in world units
     */
    public void setQuadPadding(float quadPadding) {
        if (quadPadding < 0f) {
            throw new IllegalArgumentException("quadPadding cannot be negative.");
        }
        this.quadPadding = quadPadding;
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
     * Returns whether the instance buffer is orphaned before each flush.
     *
     * @return true if buffer orphaning is enabled
     */
    public boolean isOrphanOnFlush() {
        return orphanOnFlush;
    }

    /**
     * Releases the shader and OpenGL buffers owned by this batch.
     */
    public void dispose() {
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
