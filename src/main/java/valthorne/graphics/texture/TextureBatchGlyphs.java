package valthorne.graphics.texture;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugGlyph;
import valthorne.graphics.font.slug.SlugTextRun;
import valthorne.graphics.shader.Shader;
import valthorne.io.file.ValthorneFiles;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.lang.ref.WeakReference;

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
 * <p>Repeated axis-aligned glyph samples use a bounded, lazily allocated sixteen
 * MiB R32F coverage atlas. Exact physical size and subpixel baseline phase are
 * retained; tint, opacity and blending stay live. Three distinct drawing
 * scopes establish reuse before a cold capture is admitted. Moving samples,
 * oversized glyphs and general transforms retain analytic coverage. The batch
 * owns and disposes the atlas independently of borrowed font resources.</p>
 *
 * <p>Stable runs additionally reuse packed CPU geometry, capped at four MiB over
 * 1024 submission slots. Atlas resets, layout changes and drawing-state changes
 * invalidate these records. A hot run copies its instances in bulk instead of
 * repeating per-glyph bounds, phase calculations and cache lookups.</p>
 *
 * <p>All calls are confined to the parent's graphics thread. The parent flushes
 * sprites before entering a segment and finishes the segment before drawing
 * sprites, changing transforms or ending a scope. Clip and tint values are
 * copied once per submission; glyph loops allocate no temporary objects.
 * Package visibility keeps this implementation out of the public drawing API.</p>
 */
final class TextureBatchGlyphs {
    /**
     * Fixed set-associative table for repeatedly submitted run geometry.
     */
    private static final int GEOMETRY_CACHE_SLOTS = 1024;
    /**
     * Associativity keeps colliding label placements from repeatedly evicting each other.
     */
    private static final int GEOMETRY_CACHE_WAYS = 4;
    /**
     * Combined retained CPU geometry budget for one batch.
     */
    private static final int MAX_GEOMETRY_BYTES = 4 * 1024 * 1024;
    /**
     * Per-run ceiling prevents a single label monopolizing the geometry budget.
     */
    private static final int MAX_RUN_GEOMETRY_BYTES = 64 * 1024;
    private final SlugRunGeometry[] geometryCache = new SlugRunGeometry[GEOMETRY_CACHE_SLOTS]; // Bounded reusable submissions indexed by layout and placement.
    private int geometryBytes; // Combined retained packed-instance bytes.
    private long geometryScope; // Drawing scope generation used for CPU-cache admission.
    private final SlugCoverageCache coverageCache = new SlugCoverageCache(); // Bounded raw Slug coverage reused at identical physical sampling positions.
    private final int[] cacheScissor = new int[4]; // Caller scissor box retained during cold coverage draws.
    private final int[] cachePolygonMode = new int[2]; // Caller polygon rasterization mode retained during cold coverage draws.
    private final float[] clearCoverage = new float[4]; // Zero clear value for previously occupied atlas regions.
    private final ByteBuffer cacheColorMask = BufferUtils.createByteBuffer(4); // Caller write mask restored after cold coverage draws.
    private boolean cachedCoverage; // Whether queued instances reference coverage samples rather than outlines.
    private int oldTexture2; // Caller texture binding on the coverage sampler unit.
    private int oldSampler2; // Caller sampler binding on the coverage sampler unit.
    private final ByteBuffer instances; // Borrowed native staging storage, shared exclusively with sprites.
    private final int quadVBO; // Borrowed static quad VBO owned by the parent batch.
    private final int instanceVBO; // Borrowed streaming VBO owned by the parent batch.
    private final int capacity; // Maximum glyph count fitting the shared staging and stream storage.
    private final int[] viewport = new int[4]; // Reused viewport query storage for coverage and rejection bounds.
    private final int[] query = new int[1]; // Reused scalar GL query storage avoids per-segment NIO wrappers.
    private int instanceCount; // Glyph records queued in the shared storage during the active segment.
    private long totalDrawCalls; // Cumulative nonempty glyph uploads, including font and capacity boundaries.
    private boolean clipEnabled; // Whether the current submission uses a logical world clip.
    private float clipX; // Logical clip minimum X in final world coordinates.
    private float clipY; // Logical clip minimum Y in final world coordinates.
    private float clipW; // Logical clip width in final world coordinates.
    private float clipH; // Logical clip height in final world coordinates.
    private Shader shader; // Lazily initialized curve-coverage shader owned by this component.
    private Shader cachedShader; // Coverage-only shader omits analytic curve evaluation and its GPU register pressure.
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
    private boolean srgbTarget; // Active attachment encoding used by scalable world-text blending.

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
        coverageCache.begin();
        geometryScope++;
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
        draw(font.cachedRun(text, size), x, y);
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
        SlugRunGeometry retained = null;
        boolean repeated = false;
        if (run.glyphCount() <= capacity && run.glyphCount() <= MAX_RUN_GEOMETRY_BYTES / TextureBatchContract.GLYPH_STRIDE_BYTES) {
            int hash = System.identityHashCode(run);
            hash = 31 * hash + Float.floatToIntBits(x);
            hash = 31 * hash + Float.floatToIntBits(y);
            hash ^= hash >>> 16;
            retained = findGeometry(hash, run, x, y);
            if (matchesGeometry(retained, run, x, y)) {
                repeated = retained.scope != geometryScope;
                if (retained.bytes != null) {
                    if (!cachedCoverage || activeFont != font || instanceCount + retained.count > capacity) flushGlyphs();
                    cachedCoverage = true;
                    activeFont = font;
                    instances.put(retained.bytes);
                    instanceCount += retained.count;
                    retained.scope = geometryScope;
                    return;
                }
            } else if (retained.bytes != null) {
                geometryBytes -= retained.bytes.length;
                retained.bytes = null;
            }
            retainGeometryKey(retained, run, x, y);
        }
        int start = instances.position();
        int count = instanceCount;
        long draws = totalDrawCalls;
        long generation = coverageCache.generation();
        for (int i = 0; i < run.glyphCount(); i++)
            appendGlyph(font, run.glyph(i), x + run.xOffset(i), y + run.yOffset(i), run.size());
        int bytes = instances.position() - start;
        if (retained != null && repeated && cachedCoverage && totalDrawCalls == draws && coverageCache.generation() == generation && bytes > 0 && geometryBytes + bytes <= MAX_GEOMETRY_BYTES) {
            byte[] packed = new byte[bytes];
            WeakReference<SlugTextRun> identity = new WeakReference<>(run);
            instances.get(start, packed);
            retained.run = identity;
            retained.bytes = packed;
            retained.count = instanceCount - count;
            geometryBytes += bytes;
        }
    }

    /**
     * Finds a matching submission or replaces the oldest entry in its small set.
     * Associativity prevents integer-grid label positions from fighting for one
     * direct-mapped slot while keeping lookup and retained memory bounded.
     *
     * @param hash mixed layout and placement fingerprint
     * @param run current layout
     * @param x world baseline X
     * @param y world baseline Y
     * @return reusable matching or replacement entry
     */
    private SlugRunGeometry findGeometry(int hash, SlugTextRun run, float x, float y) {
        int start = (hash & (GEOMETRY_CACHE_SLOTS / GEOMETRY_CACHE_WAYS - 1)) * GEOMETRY_CACHE_WAYS;
        int chosen = start;
        long oldest = Long.MAX_VALUE;
        boolean empty = false;
        for (int i = start; i < start + GEOMETRY_CACHE_WAYS; i++) {
            SlugRunGeometry candidate = geometryCache[i];
            if (candidate == null) {
                if (!empty) chosen = i;
                empty = true;
            } else {
                if (matchesGeometry(candidate, run, x, y)) return candidate;
                if (!empty && candidate.scope < oldest) {
                    oldest = candidate.scope;
                    chosen = i;
                }
            }
        }
        if (geometryCache[chosen] == null) geometryCache[chosen] = new SlugRunGeometry();
        return geometryCache[chosen];
    }

    /**
     * Compares every input that can affect packed glyph coordinates or color.
     *
     * @param entry retained submission slot
     * @param run current layout
     * @param x world baseline X
     * @param y world baseline Y
     * @return true when retained instances remain valid
     */
    private boolean matchesGeometry(SlugRunGeometry entry, SlugTextRun run, float x, float y) {
        return (entry.bytes == null ? entry.identity == System.identityHashCode(run) : entry.run.get() == run) && entry.revision == run.revision() && entry.size == run.size() && entry.x == x && entry.y == y && entry.color == packedColor && entry.clipped == clipEnabled
                && entry.clipX == clipX && entry.clipY == clipY && entry.clipW == clipW && entry.clipH == clipH
                && entry.cosine == cosine && entry.sine == sine && entry.transformX == transformX && entry.transformY == transformY
                && entry.viewportX == viewport[0] && entry.viewportY == viewport[1] && entry.viewportW == viewport[2] && entry.viewportH == viewport[3]
                && entry.generation == coverageCache.generation() && entry.projection.equals(projection);
    }

    /**
     * Records a pending submission key without allocating geometry bytes.
     *
     * @param entry reusable destination slot
     * @param run current layout
     * @param x world baseline X
     * @param y world baseline Y
     */
    private void retainGeometryKey(SlugRunGeometry entry, SlugTextRun run, float x, float y) {
        entry.run = null;
        entry.identity = System.identityHashCode(run);
        entry.revision = run.revision();
        entry.size = run.size();
        entry.x = x;
        entry.y = y;
        entry.color = packedColor;
        entry.clipped = clipEnabled;
        entry.clipX = clipX;
        entry.clipY = clipY;
        entry.clipW = clipW;
        entry.clipH = clipH;
        entry.cosine = cosine;
        entry.sine = sine;
        entry.transformX = transformX;
        entry.transformY = transformY;
        entry.viewportX = viewport[0];
        entry.viewportY = viewport[1];
        entry.viewportW = viewport[2];
        entry.viewportH = viewport[3];
        entry.generation = coverageCache.generation();
        entry.scope = geometryScope;
        entry.projection.set(projection);
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
            oldProgram = readInteger(GL_CURRENT_PROGRAM);
            oldVao = readInteger(GL_VERTEX_ARRAY_BINDING);
            oldBuffer = readInteger(GL_ARRAY_BUFFER_BINDING);
            oldActiveTexture = readInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            oldTexture0 = readInteger(GL_TEXTURE_BINDING_2D);
            oldSampler0 = readInteger(GL_SAMPLER_BINDING);
            glActiveTexture(GL_TEXTURE0 + 1);
            oldTexture1 = readInteger(GL_TEXTURE_BINDING_2D);
            oldSampler1 = readInteger(GL_SAMPLER_BINDING);
            glActiveTexture(GL_TEXTURE2);
            oldTexture2 = readInteger(GL_TEXTURE_BINDING_2D);
            oldSampler2 = readInteger(GL_SAMPLER_BINDING);
            oldBlend = glIsEnabled(GL_BLEND);
            oldDepth = glIsEnabled(GL_DEPTH_TEST);
            oldCull = glIsEnabled(GL_CULL_FACE);
            oldDepthMask = readInteger(GL_DEPTH_WRITEMASK) != 0;
            oldSrgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
            oldSrcRgb = readInteger(GL_BLEND_SRC);
            oldDstRgb = readInteger(GL_BLEND_DST);
            oldSrcAlpha = readInteger(GL_BLEND_SRC_ALPHA);
            oldDstAlpha = readInteger(GL_BLEND_DST_ALPHA);
            oldEquationRgb = readInteger(GL_BLEND_EQUATION_RGB);
            oldEquationAlpha = readInteger(GL_BLEND_EQUATION_ALPHA);
            boolean srgb = isSrgbFramebuffer();
            srgbTarget = srgb;
            shader.bind();
            shader.setUniformMatrix4("u_mvp", projection.get(matrix));
            shader.setUniform4f("u_textTransform", cosine, sine, transformX, transformY);
            shader.setUniform1i("u_linearColor", srgb ? 1 : 0);
            glBindSampler(0, 0);
            glBindSampler(1, 0);
            glBindSampler(2, 0);
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
     * Creates coverage shaders and their shared interpretation of the existing VBOs.
     * Explicit shader attribute locations avoid redundant relinking. Creation
     * restores bindings before the first glyph segment captures caller state.
     */
    private void initializeGlyphState() {
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        boolean initialized = false;
        try {
            projection = new Matrix4f();
            matrix = new float[16];
            transform = new Matrix4f();
            String vertexSource = ValthorneFiles.readString("valthorne/shaders/font/slug.vert");
            String fragmentSource = ValthorneFiles.readString("valthorne/shaders/font/slug.frag");
            cachedShader = new Shader(vertexSource, fragmentSource.replace("#version 330 core", "#version 330 core\n#define SLUG_CACHED_COVERAGE"));
            cachedShader.bind();
            cachedShader.setUniform1i("u_coverageTexture", 2);
            shader = new Shader(vertexSource, fragmentSource);
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
            initialized = true;
        } finally {
            if (!initialized) {
                if (shader != null) shader.dispose();
                if (cachedShader != null) cachedShader.dispose();
                if (vao != 0) glDeleteVertexArrays(vao);
                if (indexBuffer != 0) glDeleteBuffers(indexBuffer);
                shader = null;
                cachedShader = null;
                vao = indexBuffer = 0;
            }
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
     * Reads one scalar GL state value into reusable primitive storage. LWJGL's
     * convenience return overload creates a temporary buffer view per query.
     *
     * @param name scalar state selector
     * @return selected state value
     */
    private int readInteger(int name) {
        glGetIntegerv(name, query);
        return query[0];
    }

    /**
     * Reads the current draw attachment's color encoding without changing targets.
     *
     * @return true when glyphs should be blended in linear light
     */
    private boolean isSrgbFramebuffer() {
        int attachment = readInteger(GL_DRAW_BUFFER0);
        if (attachment == GL_NONE) return false;
        if (readInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0) {
            if (attachment == GL_BACK) attachment = GL_BACK_LEFT;
            else if (attachment == GL_FRONT) attachment = GL_FRONT_LEFT;
        }
        glGetFramebufferAttachmentParameteriv(GL_DRAW_FRAMEBUFFER, attachment, GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING, query);
        return query[0] == GL_SRGB;
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
        if (projectionScale > 0f && projection.m00() > 0f && projection.m11() > 0f && appendCachedGlyph(font, glyph, x, y, size, x0, y0, x1, y1, cpuClip)) return;
        if (cachedCoverage) {
            flushGlyphs();
            cachedCoverage = false;
        }
        if (activeFont != font) {
            flushGlyphs();
            activeFont = font;
        } else if (instanceCount == capacity) flushGlyphs();
        float inverseSize = 1f / size;
        if (projectionScale > 0f && projection.m00() > 0f && projection.m11() > 0f) {
            float px = ((projection.m00() * x + projection.m30()) / projection.m33() + 1f) * .5f * viewport[2];
            float py = ((projection.m11() * y + projection.m31()) / projection.m33() + 1f) * .5f * viewport[3];
            float ix = (float) Math.floor(px);
            float iy = (float) Math.floor(py);
            glyph.writeInstance(instances, x0, y0, x1, y1, ix + viewport[0], iy + viewport[1], px - ix, py - iy, size * projectionScale, packedColor);
        } else {
            glyph.writeInstance(instances, x0, y0, x1, y1, (x0 - x) * inverseSize, (y0 - y) * inverseSize, (x1 - x) * inverseSize, (y1 - y) * inverseSize, -1f, packedColor);
        }
        instances.putFloat(clipX).putFloat(clipY).putFloat(clipW).putFloat(clipH).putFloat(clipEnabled && !cpuClip ? 1f : 0f);
        instanceCount++;
    }

    /**
     * Reuses raw coverage only for positive uniform axis-aligned projections.
     * Exact subpixel phases and em sizes form the key; no phase quantization,
     * image filtering or foreground-specific coverage is cached.
     *
     * @param font borrowed font supplying the outline
     * @param glyph immutable outline identity
     * @param x baseline world X
     * @param y baseline world Y
     * @param size world em size
     * @param x0 clipped world minimum X
     * @param y0 clipped world minimum Y
     * @param x1 clipped world maximum X
     * @param y1 clipped world maximum Y
     * @param cpuClip whether clipping has already been applied
     * @return true when coverage instances were queued
     */
    private boolean appendCachedGlyph(SlugFont font, SlugGlyph glyph, float x, float y, float size, float x0, float y0, float x1, float y1, boolean cpuClip) {
        float ppem = size * projectionScale;
        float px = ((projection.m00() * x + projection.m30()) / projection.m33() + 1f) * .5f * viewport[2];
        float py = ((projection.m11() * y + projection.m31()) / projection.m33() + 1f) * .5f * viewport[3];
        float phaseX = px - (float) Math.floor(px);
        float phaseY = py - (float) Math.floor(py);
        int left = (int) Math.floor(phaseX + glyph.minX() * ppem - .5f);
        int bottom = (int) Math.floor(phaseY + glyph.minY() * ppem - .5f);
        int width = (int) Math.ceil(phaseX + glyph.maxX() * ppem + .5f) - left;
        int height = (int) Math.ceil(phaseY + glyph.maxY() * ppem + .5f) - bottom;
        if (width <= 0 || height <= 0 || width > SlugCoverageCache.MAX_GLYPH_EXTENT || height > SlugCoverageCache.MAX_GLYPH_EXTENT) return false;
        if (coverageCache.full()) {
            flushGlyphs();
            coverageCache.reset();
        }
        int slot = coverageCache.find(glyph, ppem, phaseX, phaseY);
        if (!coverageCache.contains(slot)) {
            if (!coverageCache.admit(glyph, ppem, phaseX, phaseY)) return false;
            flushGlyphs();
            if (coverageCache.needsReset(width, height)) {
                coverageCache.reset();
                slot = coverageCache.find(glyph, ppem, phaseX, phaseY);
            }
            captureCoverage(font, glyph, slot, ppem, phaseX, phaseY, left, bottom, width, height);
        }
        if (!cachedCoverage || activeFont != font || instanceCount == capacity) flushGlyphs();
        cachedCoverage = true;
        activeFont = font;
        float originX = x + (left - phaseX) / projectionScale;
        float originY = y + (bottom - phaseY) / projectionScale;
        x0 = Math.max(x0, originX);
        y0 = Math.max(y0, originY);
        x1 = Math.min(x1, originX + width / projectionScale);
        y1 = Math.min(y1, originY + height / projectionScale);
        glyph.writeInstance(instances, x0, y0, x1, y1, coverageCache.x(slot) + (x0 - originX) * projectionScale, coverageCache.y(slot) + (y0 - originY) * projectionScale, coverageCache.x(slot) + (x1 - originX) * projectionScale, coverageCache.y(slot) + (y1 - originY) * projectionScale, ppem, packedColor);
        instances.putFloat(clipX).putFloat(clipY).putFloat(clipW).putFloat(clipH).putFloat(clipEnabled && !cpuClip ? 1f : 0f);
        instanceCount++;
        return true;
    }

    /**
     * Evaluates one cold glyph with the original analytic shader into R32F.
     * An independent tile projection avoids baking camera/frustum clipping into
     * reusable glyph samples. The complete rectangle includes zero coverage. Caller draw
     * bindings and sampling uniforms are restored before queued text resumes.
     *
     * @param font source outline owner
     * @param glyph source glyph metadata
     * @param slot vacant coverage entry
     * @param ppem physical pixels per em
     * @param phaseX horizontal baseline fraction
     * @param phaseY vertical baseline fraction
     * @param left first sample column relative to integer baseline
     * @param bottom first sample row relative to integer baseline
     * @param width physical sample width
     * @param height physical sample height
     */
    private void captureCoverage(SlugFont font, SlugGlyph glyph, int slot, float ppem, float phaseX, float phaseY, int left, int bottom, int width, int height) {
        int target = readInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        boolean srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
        boolean stencil = glIsEnabled(GL_STENCIL_TEST);
        boolean discard = glIsEnabled(GL_RASTERIZER_DISCARD);
        boolean sampleMask = glIsEnabled(GL_SAMPLE_MASK);
        boolean sampleCoverage = glIsEnabled(GL_SAMPLE_COVERAGE);
        boolean alphaCoverage = glIsEnabled(GL_SAMPLE_ALPHA_TO_COVERAGE);
        glGetIntegerv(GL_SCISSOR_BOX, cacheScissor);
        glGetBooleanv(GL_COLOR_WRITEMASK, cacheColorMask);
        glGetIntegerv(GL_POLYGON_MODE, cachePolygonMode);
        try {
            glActiveTexture(GL_TEXTURE2);
            coverageCache.insert(slot, glyph, ppem, phaseX, phaseY, width, height);
            glBindTexture(GL_TEXTURE_2D, 0);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, coverageCache.framebuffer());
            glViewport(coverageCache.x(slot), coverageCache.y(slot), width, height);
            glEnable(GL_SCISSOR_TEST);
            glScissor(coverageCache.x(slot), coverageCache.y(slot), width, height);
            glColorMask(true, true, true, true);
            glClearBufferfv(GL_COLOR, 0, clearCoverage);
            glDisable(GL_BLEND);
            glDisable(GL_FRAMEBUFFER_SRGB);
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_RASTERIZER_DISCARD);
            glDisable(GL_SAMPLE_MASK);
            glDisable(GL_SAMPLE_COVERAGE);
            glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            shader.bind();
            shader.setUniformMatrix4("u_mvp", transform.identity().ortho2D(0, width, 0, height).get(matrix));
            shader.setUniform4f("u_textTransform", 1, 0, 0, 0);
            shader.setUniform1i("u_writeCoverage", 1);
            font.bindTextures();
            glyph.writeInstance(instances, 0, 0, width, height, coverageCache.x(slot) - left, coverageCache.y(slot) - bottom, phaseX, phaseY, ppem, -1);
            instances.putFloat(0).putFloat(0).putFloat(0).putFloat(0).putFloat(0).flip();
            glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
            glBufferData(GL_ARRAY_BUFFER, (long) capacity * TextureBatchContract.INST_STRIDE_BYTES, GL_STREAM_DRAW);
            glBufferSubData(GL_ARRAY_BUFFER, 0L, instances);
            glDrawElementsInstanced(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_INT, 0L, 1);
            totalDrawCalls++;
        } catch (RuntimeException | Error failure) {
            coverageCache.dispose();
            throw failure;
        } finally {
            instances.clear();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glScissor(cacheScissor[0], cacheScissor[1], cacheScissor[2], cacheScissor[3]);
            if (scissor) glEnable(GL_SCISSOR_TEST);
            else glDisable(GL_SCISSOR_TEST);
            glColorMask(cacheColorMask.get(0) != 0, cacheColorMask.get(1) != 0, cacheColorMask.get(2) != 0, cacheColorMask.get(3) != 0);
            if (srgb) glEnable(GL_FRAMEBUFFER_SRGB);
            if (stencil) glEnable(GL_STENCIL_TEST);
            if (discard) glEnable(GL_RASTERIZER_DISCARD);
            if (sampleMask) glEnable(GL_SAMPLE_MASK);
            if (sampleCoverage) glEnable(GL_SAMPLE_COVERAGE);
            if (alphaCoverage) glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);
            glPolygonMode(GL_FRONT_AND_BACK, cachePolygonMode[0]);
            glEnable(GL_BLEND);
            shader.setUniformMatrix4("u_mvp", projection.get(matrix));
            shader.setUniform4f("u_textTransform", cosine, sine, transformX, transformY);
            shader.setUniform1i("u_writeCoverage", 0);
        }
    }

    /**
     * Uploads the shared byte storage as packed glyphs and draws the existing
     * quad through four strip indices. Keeps shader/coverage state active for
     * subsequent text and preserves the original antialiasing interpolation.
     */
    private void flushGlyphs() {
        if (instanceCount == 0) return;
        if (cachedCoverage) {
            if (activeFont.isDisposed()) throw new IllegalStateException("Slug font is disposed.");
        } else activeFont.bindTextures();
        boolean linear = srgbTarget && !activeFont.isHinted();
        Shader current = cachedCoverage ? cachedShader : shader;
        current.bind();
        current.setUniformMatrix4("u_mvp", projection.get(matrix));
        current.setUniform4f("u_textTransform", cosine, sine, transformX, transformY);
        current.setUniform1i("u_linearColor", linear ? 1 : 0);
        current.setUniform1i("u_hintedUi", activeFont.isHinted() ? 1 : 0);
        if (!cachedCoverage) current.setUniform1i("u_writeCoverage", 0);
        if (cachedCoverage) {
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, coverageCache.texture());
        }
        if (linear) glEnable(GL_FRAMEBUFFER_SRGB);
        else glDisable(GL_FRAMEBUFFER_SRGB);
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
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, oldTexture2);
        glBindSampler(2, oldSampler2);
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
        coverageCache.dispose();
        if (cachedShader != null) cachedShader.dispose();
        for (int i = 0; i < geometryCache.length; i++) geometryCache[i] = null;
        geometryBytes = 0;
        if (shader == null) return;
        shader.dispose();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(indexBuffer);
    }
}
