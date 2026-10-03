package valthorne.graphics.texture;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugGlyph;
import valthorne.graphics.font.slug.SlugTextRun;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.shader.ShaderSources;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Owns live-outline submission and GPU state for one {@link TextureBatch}.
 * The parent controls painter order, drawing scopes, translation and sprite
 * submissions; this component handles curve coverage, culling, packed glyph
 * records, font bindings and restoration of the sprite pipeline.
 *
 * <p>Created lazily on the first text operation. It borrows the parent's native
 * staging buffer, quad VBO and instance VBO, with exclusive access while a glyph
 * segment is active. No second staging buffer or stream VBO is allocated. Shader
 * resources are initialized on the first segment and released by the parent.
 * Fonts and text runs are borrowed and must outlive queued submission.</p>
 *
 * <p>All calls are confined to the parent's graphics thread. The parent flushes
 * sprites before entering a segment and finishes the segment before drawing
 * sprites, changing transforms or ending a scope. Clip and tint values are
 * copied once per submission; glyph loops allocate no temporary objects.
 * Package visibility keeps this implementation out of the public drawing API.</p>
 */
final class TextureBatchGlyphs {
    private final ByteBuffer instances; // Borrowed native staging storage, shared exclusively with sprites.
    private final int quadVBO; // Borrowed static quad VBO owned by the parent batch.
    private final int instanceVBO; // Borrowed streaming VBO owned by the parent batch.
    private final int capacity; // Maximum glyph count fitting the shared staging and stream storage.
    private final int[] viewport = new int[4]; // Reused viewport query storage for coverage and rejection bounds.
    private int instanceCount; // Glyph records queued in the shared storage during the active segment.
    private long totalDrawCalls; // Cumulative nonempty glyph uploads, including font and capacity boundaries.
    private boolean clipEnabled; // Whether the current submission uses a logical world clip.
    private float clipX; // Logical clip minimum X in final world coordinates.
    private float clipY; // Logical clip minimum Y in final world coordinates.
    private float clipW; // Logical clip width in final world coordinates.
    private float clipH; // Logical clip height in final world coordinates.
    private Shader shader; // Lazily initialized curve-coverage shader owned by this component.
    private int vao; // Glyph interpretation of the shared quad and instance VBOs.
    private int indexBuffer; // Four indices preserving the curve shader's original triangle-strip interpolation.
    private Matrix4f projection; // Reused combined camera/text transform for coverage and culling.
    private float[] matrix; // Reusable upload storage for the composed glyph projection.
    private Matrix4f transform; // Reused rigid text transform composed with the current projection.
    private float cosine = 1f; // Text transform cosine, shared by all fonts in the current UI scope.
    private float sine; // Text transform sine, shared by all fonts in the current UI scope.
    private float transformX; // Text transform horizontal offset after batch translation.
    private float transformY; // Text transform vertical offset after batch translation.
    private SlugFont activeFont; // Borrowed font supplying textures for the queued glyph segment.
    private int packedColor; // Packed RGBA tint including opacity, copied once per text submission.
    private float projectionScale; // Uniform physical pixels per world unit, or negative for derivative coverage.
    private float padding; // World-space expansion sufficient to retain antialiased glyph edges.
    private float viewMinX; // Local-world viewport minimum X for compatible axis-aligned projections.
    private float viewMinY; // Local-world viewport minimum Y for compatible axis-aligned projections.
    private float viewMaxX; // Local-world viewport maximum X for compatible axis-aligned projections.
    private float viewMaxY; // Local-world viewport maximum Y for compatible axis-aligned projections.
    private boolean drawing; // Whether consecutive text submissions currently own the GPU state.
    private int oldProgram; // Program binding restored after a glyph segment.
    private int oldVao; // Vertex-array binding restored after a glyph segment.
    private int oldBuffer; // Array-buffer binding restored after a glyph segment.
    private int oldActiveTexture; // Active texture unit restored after a glyph segment.
    private int oldTexture0; // Texture binding on unit zero before glyph drawing.
    private int oldTexture1; // Texture binding on unit one before glyph drawing.
    private int oldSampler0; // Sampler binding on unit zero before glyph drawing.
    private int oldSampler1; // Sampler binding on unit one before glyph drawing.
    private int oldSrcRgb; // Original RGB source blend factor.
    private int oldDstRgb; // Original RGB destination blend factor.
    private int oldSrcAlpha; // Original alpha source blend factor.
    private int oldDstAlpha; // Original alpha destination blend factor.
    private int oldEquationRgb; // Original RGB blend equation.
    private int oldEquationAlpha; // Original alpha blend equation.
    private boolean oldBlend; // Original blend enablement.
    private boolean oldDepth; // Original depth-test enablement.
    private boolean oldCull; // Original face-cull enablement.
    private boolean oldDepthMask; // Original depth-write enablement.
    private boolean oldSrgb; // Framebuffer conversion policy restored after glyph drawing.

    /**
     * Borrows shared storage without creating GPU resources or reading GL state.
     *
     * @param instances shared native staging buffer
     * @param quadVBO shared static geometry VBO
     * @param instanceVBO shared streaming VBO
     * @param capacity number of instances supported by shared storage
     */
    TextureBatchGlyphs(ByteBuffer instances, int quadVBO, int instanceVBO, int capacity) {
        this.instances = instances;
        this.quadVBO = quadVBO;
        this.instanceVBO = instanceVBO;
        this.capacity = capacity;
    }

    /**
     * Reports whether this component currently owns the parent's GPU bindings.
     *
     * @return true while a glyph segment is active
     */
    boolean isDrawing() { return drawing; }

    /**
     * Returns nonempty glyph draws without querying the GPU.
     *
     * @return lifetime glyph draw-call count
     */
    long getTotalDrawCalls() { return totalDrawCalls; }

    /**
     * Resets transform state at the start of the parent's drawing scope.
     * Requires the preceding segment to have finished.
     */
    void resetTransform() {
        cosine = 1f;
        sine = 0f;
        transformX = 0f;
        transformY = 0f;
    }

    /**
     * Compares a validated transform with the retained scope transform.
     *
     * @param cosine rotation cosine
     * @param sine rotation sine
     * @param x horizontal transform offset
     * @param y vertical transform offset
     * @return true when no scope flush is necessary
     */
    boolean matchesTransform(float cosine, float sine, float x, float y) {
        return this.cosine == cosine && this.sine == sine && transformX == x && transformY == y;
    }

    /**
     * Stores a validated transform after the parent has flushed queued work.
     *
     * @param cosine rotation cosine
     * @param sine rotation sine
     * @param x horizontal transform offset
     * @param y vertical transform offset
     */
    void setTransform(float cosine, float sine, float x, float y) {
        this.cosine = cosine;
        this.sine = sine;
        transformX = x;
        transformY = y;
    }

    /**
     * Establishes a segment and copies submission tint, opacity and clip values.
     * The parent must already have flushed its sprite queue.
     *
     * @param tint selected nonnull tint
     * @param opacity parent opacity multiplier
     * @param clipped whether a logical world clip is enabled
     * @param x clip minimum X
     * @param y clip minimum Y
     * @param width clip width
     * @param height clip height
     */
    void prepare(Color tint, float opacity, boolean clipped, float x, float y, float width, float height) {
        beginGlyphSegment();
        packedColor = colorByte(tint.r()) << 24 | colorByte(tint.g()) << 16 | colorByte(tint.b()) << 8 | colorByte(tint.a() * opacity);
        clipEnabled = clipped;
        clipX = x;
        clipY = y;
        clipW = width;
        clipH = height;
    }

    /**
     * Lays out trusted UTF-16 text and queues visible live outlines.
     *
     * @param font borrowed live font already checked for disposal
     * @param text nonempty text
     * @param x translated baseline X
     * @param y translated baseline Y
     * @param size positive finite world units per em
     */
    void draw(SlugFont font, String text, float x, float y, float size) {
        float originX = x;
        float penX = originX;
        float penY = y;
        float lineAdvance = font.lineHeight() * size;
        int previous = 0;
        for (int i = 0; i < text.length(); i++) {
            char codepoint = text.charAt(i);
            if (codepoint == '\n') {
                penX = originX;
                penY -= lineAdvance;
                previous = 0;
                continue;
            }
            if (codepoint == '\t') {
                SlugGlyph space = font.glyph(' ');
                penX += (space == null ? .25f : space.advance()) * 4f * size;
                previous = 0;
                continue;
            }
            SlugGlyph glyph = font.glyph(codepoint);
            if (glyph == null) {
                penX += .25f * size;
                previous = 0;
                continue;
            }
            if (previous != 0) penX += font.kerning(previous, codepoint) * size;
            if (glyph.isDrawable()) appendGlyph(font, glyph, penX, penY, size);
            penX += glyph.advance() * size;
            previous = codepoint;
        }
    }

    /**
     * Queues retained glyph offsets without repeating kerning or line layout.
     *
     * @param run trusted nonempty retained layout
     * @param x translated baseline X
     * @param y translated baseline Y
     */
    void draw(SlugTextRun run, float x, float y) {
        SlugFont font = run.font();
        if (!run.intersects(x, y, viewMinX - padding, viewMinY - padding, viewMaxX + padding, viewMaxY + padding)) return;
        for (int i = 0; i < run.glyphCount(); i++)
            appendGlyph(font, run.glyph(i), x + run.xOffset(i), y + run.yOffset(i), run.size());
    }

    /**
     * Starts a segment and snaps a translated baseline for uniform axis-aligned
     * projections. General projections preserve the supplied baseline.
     *
     * @param baseline finite baseline before parent translation
     * @param translationY current parent vertical translation
     * @return baseline in the same local coordinates
     */
    float alignBaseline(float baseline, float translationY) {
        beginGlyphSegment();
        if (projectionScale <= 0f) return baseline;
        float translated = baseline + translationY;
        return viewMinY + Math.round((translated - viewMinY) * projectionScale) / projectionScale - translationY;
    }

    /**
     * Converts a normalized component into a clamped unsigned byte for GPU storage.
     *
     * @param value normalized color component
     * @return component in the range zero through 255
     */
    private static int colorByte(float value) {
        /*
         * Direct conversion matches the previous normalized-byte attributes and
         * needs no temporary color or boxed component; NaN converts to zero.
         */
        return Math.max(0, Math.min(255, (int) (value * 255f + .5f)));
    }

    /**
     * Establishes glyph shader state once per consecutive text segment. Staging,
     * geometry and the stream VBO are borrowed from sprites without allocating
     * another stream. Captures state needed to resume the parent's sprite pipeline.
     */
    private void beginGlyphSegment() {
        if (!drawing) {
            if (shader == null) initializeGlyphState();
            glGetIntegerv(GL_VIEWPORT, viewport);
            projection.set(Window.getProjectionMatrix());
            transform.identity()
                    .m00(cosine)
                    .m01(sine)
                    .m10(-sine)
                    .m11(cosine)
                    .m30(transformX)
                    .m31(transformY);
            projection.mul(transform);
            if (!projection.isFinite() || viewport[2] <= 0 || viewport[3] <= 0)
                throw new IllegalStateException("Text requires a finite projection and a nonempty viewport.");
            updateGlyphBounds();
            oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
            oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            oldBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
            oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            oldTexture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
            oldSampler0 = glGetInteger(GL_SAMPLER_BINDING);
            glActiveTexture(GL_TEXTURE0 + 1);
            oldTexture1 = glGetInteger(GL_TEXTURE_BINDING_2D);
            oldSampler1 = glGetInteger(GL_SAMPLER_BINDING);
            oldBlend = glIsEnabled(GL_BLEND);
            oldDepth = glIsEnabled(GL_DEPTH_TEST);
            oldCull = glIsEnabled(GL_CULL_FACE);
            oldDepthMask = glGetInteger(GL_DEPTH_WRITEMASK) != 0;
            oldSrgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
            oldSrcRgb = glGetInteger(GL_BLEND_SRC);
            oldDstRgb = glGetInteger(GL_BLEND_DST);
            oldSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
            oldDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
            oldEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
            oldEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
            boolean srgb = isSrgbFramebuffer();
            shader.bind();
            shader.setUniformMatrix4("u_mvp", projection.get(matrix));
            shader.setUniform4f("u_textTransform", cosine, sine, transformX, transformY);
            shader.setUniform1i("u_linearColor", srgb ? 1 : 0);
            glBindSampler(0, 0);
            glBindSampler(1, 0);
            if (srgb) glEnable(GL_FRAMEBUFFER_SRGB);
            glEnable(GL_BLEND);
            glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glDepthMask(false);
            glBindVertexArray(vao);
            instances.clear();
            activeFont = null;
            drawing = true;
        }
    }

    /**
     * Creates only the glyph shader and its interpretation of the existing VBOs.
     * Explicit shader attribute locations avoid redundant relinking. Creation
     * restores bindings before the first glyph segment captures caller state.
     */
    private void initializeGlyphState() {
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        try {
            projection = new Matrix4f();
            matrix = new float[16];
            transform = new Matrix4f();
            shader = new Shader(ShaderSources.load("font/slug.vert"), ShaderSources.load("font/slug.frag"));
            shader.bind();
            shader.setUniform1i("u_curveTexture", 0);
            shader.setUniform1i("u_bandTexture", 1);
            vao = glGenVertexArrays();
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 2, GL_FLOAT, false, TextureBatchContract.QUAD_FLOATS_PER_VERT * Float.BYTES, 0L);
            indexBuffer = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            IntBuffer indices = BufferUtils.createIntBuffer(4);
            indices.put(0).put(1).put(4).put(2).flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
            int stride = TextureBatchContract.GLYPH_STRIDE_BYTES;
            // Locations and offsets match the packed glyph record and slug.vert.
            glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 0L);
            glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 16L);
            glVertexAttribIPointer(3, 2, GL_UNSIGNED_INT, stride, 32L);
            glVertexAttribPointer(4, 4, GL_FLOAT, false, stride, 40L);
            glVertexAttribPointer(5, 4, GL_UNSIGNED_BYTE, true, stride, 60L);
            glVertexAttribPointer(6, 1, GL_FLOAT, false, stride, 56L);
            glVertexAttribPointer(7, 4, GL_FLOAT, false, stride, 64L);
            glVertexAttribPointer(8, 1, GL_FLOAT, false, stride, 80L);
            for (int attribute = 1; attribute <= 8; attribute++) {
                glEnableVertexAttribArray(attribute);
                glVertexAttribDivisor(attribute, 1);
            }
        } finally {
            glBindVertexArray(previousVao);
            glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
            glUseProgram(previousProgram);
        }
    }

    /**
     * Derives viewport rejection bounds and physical coverage scale from the
     * combined projection. General transforms retain derivative coverage and
     * conservative visibility rather than assuming an axis-aligned viewport.
     */
    private void updateGlyphBounds() {
        viewMinX = Float.NEGATIVE_INFINITY;
        viewMinY = Float.NEGATIVE_INFINITY;
        viewMaxX = Float.POSITIVE_INFINITY;
        viewMaxY = Float.POSITIVE_INFINITY;
        projectionScale = -1f;
        padding = .5f;
        Matrix4f mvp = projection;
        if (mvp.m01() != 0f || mvp.m10() != 0f || mvp.m03() != 0f || mvp.m13() != 0f || mvp.m33() <= 0f || mvp.m00() == 0f || mvp.m11() == 0f) return;
        float x0 = (-mvp.m33() - mvp.m30()) / mvp.m00();
        float x1 = (mvp.m33() - mvp.m30()) / mvp.m00();
        float y0 = (-mvp.m33() - mvp.m31()) / mvp.m11();
        float y1 = (mvp.m33() - mvp.m31()) / mvp.m11();
        viewMinX = Math.min(x0, x1);
        viewMaxX = Math.max(x0, x1);
        viewMinY = Math.min(y0, y1);
        viewMaxY = Math.max(y0, y1);
        float sx = Math.abs(mvp.m00()) * viewport[2] / (2f * mvp.m33());
        float sy = Math.abs(mvp.m11()) * viewport[3] / (2f * mvp.m33());
        if (Math.abs(sx - sy) < Math.min(sx, sy) * .0001f) projectionScale = sx;
        padding = Math.max(.5f, .5f / Math.min(sx, sy));
    }

    /**
     * Reads the current draw attachment's color encoding without changing targets.
     *
     * @return true when glyphs should be blended in linear light
     */
    private static boolean isSrgbFramebuffer() {
        /*
         * Default framebuffer attachment names differ from FBO color attachments.
         * Query the active draw buffer so both offscreen and window text agree.
         */
        int attachment = glGetInteger(GL_DRAW_BUFFER0);
        if (attachment == GL_NONE) return false;
        if (glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0) {
            if (attachment == GL_BACK) attachment = GL_BACK_LEFT;
            else if (attachment == GL_FRONT) attachment = GL_FRONT_LEFT;
        }
        return glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, attachment, GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING) == GL_SRGB;
    }

    /**
     * Appends one visible, padded glyph to the shared staging buffer. Font changes
     * and capacity flush the current glyph segment while retaining shader state.
     * CPU clipping is safe for identity text transforms; rotated UI clips are
     * evaluated against transformed world coordinates by the fragment shader.
     *
     * @param font borrowed font already checked for disposal
     * @param glyph trusted drawable glyph metadata
     * @param x translated baseline X
     * @param y translated baseline Y
     * @param size positive finite world units per em
     */
    private void appendGlyph(SlugFont font, SlugGlyph glyph, float x, float y, float size) {
        float x0 = x + glyph.minX() * size - padding;
        float y0 = y + glyph.minY() * size - padding;
        float x1 = x + glyph.maxX() * size + padding;
        float y1 = y + glyph.maxY() * size + padding;
        x0 = Math.max(x0, viewMinX);
        y0 = Math.max(y0, viewMinY);
        x1 = Math.min(x1, viewMaxX);
        y1 = Math.min(y1, viewMaxY);
        boolean cpuClip = cosine == 1f && sine == 0f && transformX == 0f && transformY == 0f;
        if (clipEnabled && cpuClip) {
            x0 = Math.max(x0, clipX);
            y0 = Math.max(y0, clipY);
            x1 = Math.min(x1, clipX + clipW);
            y1 = Math.min(y1, clipY + clipH);
        }
        if (x0 >= x1 || y0 >= y1) return;
        if (activeFont != font) {
            flushGlyphs();
            activeFont = font;
        } else if (instanceCount == capacity) flushGlyphs();
        float inverseSize = 1f / size;
        glyph.writeInstance(instances, x0, y0, x1, y1, (x0 - x) * inverseSize, (y0 - y) * inverseSize, (x1 - x) * inverseSize, (y1 - y) * inverseSize, projectionScale > 0f ? size * projectionScale : -1f, packedColor);
        instances.putFloat(clipX).putFloat(clipY).putFloat(clipW).putFloat(clipH).putFloat(clipEnabled && !cpuClip ? 1f : 0f);
        instanceCount++;
    }

    /**
     * Uploads the shared byte storage as packed glyphs and draws the existing
     * quad through four strip indices. Keeps shader/coverage state active for
     * subsequent text and preserves the original antialiasing interpolation.
     */
    private void flushGlyphs() {
        if (instanceCount == 0) return;
        activeFont.bindTextures();
        instances.flip();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, (long) capacity * TextureBatchContract.INST_STRIDE_BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, instances);
        glDrawElementsInstanced(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_INT, 0L, instanceCount);
        totalDrawCalls++;
        instances.clear();
        instanceCount = 0;
    }

    /**
     * Finishes a text segment and restores the captured sprite pipeline, including
     * texture and sampler bindings. Also accounts for glyph capacity/font flushes.
     * The flag is cleared even if a disposed queued font causes submission to fail.
     */
    void finish() {
        if (!drawing) return;
        try {
            flushGlyphs();
        } finally {
            restoreState();
        }
    }

    /**
     * Restores captured state and discards the glyph queue, including after a
     * disposed font causes flushing to fail. The sprite view then owns staging.
     */
    private void restoreState() {
        if (oldSrgb) glEnable(GL_FRAMEBUFFER_SRGB);
        else glDisable(GL_FRAMEBUFFER_SRGB);
        glBindBuffer(GL_ARRAY_BUFFER, oldBuffer);
        glBindVertexArray(oldVao);
        glUseProgram(oldProgram);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, oldTexture0);
        glBindSampler(0, oldSampler0);
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, oldTexture1);
        glBindSampler(1, oldSampler1);
        glActiveTexture(oldActiveTexture);
        if (oldDepth) glEnable(GL_DEPTH_TEST);
        else glDisable(GL_DEPTH_TEST);
        if (oldCull) glEnable(GL_CULL_FACE);
        else glDisable(GL_CULL_FACE);
        glDepthMask(oldDepthMask);
        if (oldBlend) glEnable(GL_BLEND);
        else glDisable(GL_BLEND);
        glBlendFuncSeparate(oldSrcRgb, oldDstRgb, oldSrcAlpha, oldDstAlpha);
        glBlendEquationSeparate(oldEquationRgb, oldEquationAlpha);
        drawing = false;
        activeFont = null;
        instanceCount = 0;
        instances.clear();
    }

    /**
     * Discards pending glyphs, restores borrowed bindings and frees owned shader
     * resources. Shared VBOs and borrowed fonts remain owned by their callers.
     */
    void dispose() {
        if (drawing) restoreState();
        if (shader == null) return;
        shader.dispose();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(indexBuffer);
    }
}
