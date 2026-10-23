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
import java.util.Objects;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Owns live-outline submission and GPU state for one {@link TextureBatch}.
 * The parent controls painter order, drawing scopes, translation and sprite
 * submissions; this component handles curve coverage, culling, packed glyph
 * records, font bindings and restoration of the sprite pipeline.
 *
 * <p>Created lazily on the first text operation. It borrows the parent's native
 * staging buffer, quad VBO and instance VBO while a glyph segment is active.
 * A second bounded native staging buffer is allocated only for cold captures;
 * both streams reuse the parent's VBO. The parent releases all owned resources.
 * Fonts and text runs are borrowed and must outlive queued submission.</p>
 *
 * <p>Positive uniform axis-aligned samples use a lazy sixteen MiB R32F atlas.
 * Cold tiles are queued and evaluated together before their first use, including
 * changing text and fractional movement. Physical baseline fractions retain
 * shader precision without phase quantization or coverage filtering. Run origins
 * and the original projection arithmetic are preserved without changing the
 * analytic sampling positions. Oversized glyphs and general transforms retain
 * direct analytic coverage. Tint and opacity remain live.</p>
 *
 * <p>Packed CPU geometry is capped at four MiB over 1024 slots. Stable runs copy
 * their instances in bulk. Recent rebuilds can reuse an identical shaped glyph
 * prefix and update only its suffix. Atlas resets and drawing-state changes
 * invalidate sample addresses. Full-context shaping establishes prefix identity;
 * no substring is independently shaped or assumed context-free.</p>
 *
 * <p>Compatible unrotated default sprites join cached text in painter order.
 * OpenGL 3.3 dual-source factors preserve straight sprite blending and
 * premultiplied text, including their distinct destination-alpha behavior.
 * Matching sprite triangulation preserves texture sampling at texel boundaries.
 * Custom shaders, custom blending, sampler overrides, depth/cull state, sRGB attachments and text
 * transforms retain independent pipeline boundaries.</p>
 *
 * <p>All calls are confined to the parent's graphics thread. Clip and tint values
 * are copied per submission; glyph loops allocate no temporary objects. Cold
 * capture suspends caller masking state and restores it before composition.
 * The parent owns the drawing scope and all resources.</p>
 */
public final class TextureBatchGlyphs {
    private final TextureBatch parent; // Owning batch for public drawing and alignment entry points.
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
    private final ByteBuffer cacheColorMask = BufferUtils.createByteBuffer(4); // Caller write mask restored after cold coverage draws.
    private ByteBuffer captures; // Lazily allocated bounded staging for cold atlas tiles.
    private int captureCount; // Cold tiles evaluated together before queued coverage is sampled.
    private boolean cachedCoverage; // Whether queued instances reference coverage samples rather than outlines.
    private int oldTexture2; // Caller texture binding on the coverage sampler unit.
    private int oldSampler2; // Caller sampler binding on the coverage sampler unit.
    private final ByteBuffer instances; // Borrowed native staging storage, shared exclusively with sprites.
    private final int quadVBO; // Borrowed static quad VBO owned by the parent batch.
    private final int instanceVBO; // Borrowed streaming VBO owned by the parent batch.
    private final int textureUnits; // Sprite sampler units whose external overrides require the original pipeline.
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
    private Shader mixedShader; // Dual-source composition preserves distinct sprite and glyph alpha rules.
    private boolean mixedTargetKnown; // Whether the current segment has checked framebuffer draw targets.
    private boolean mixedTargetSupported; // Dual-source blending requires exactly one active color output.
    private boolean mixedSprites; // Current segment includes compatible ordinary sprites.
    private int spriteTexture; // Borrowed sprite texture sampled by the mixed segment.
    private int oldTexture3; // Texture binding restored after mixed composition.
    private int oldSampler3; // Sampler binding restored after mixed composition.
    private Shader cachedShader; // Coverage-only shader omits analytic curve evaluation and its GPU register pressure.
    private int vao; // Glyph interpretation of the shared quad and instance VBOs.
    private int indexBuffer; // Analytic strip indices followed by the sprite pipeline's original triangles.
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
     * @param textureUnits sampler units addressable by the parent sprite shader
     */
    TextureBatchGlyphs(TextureBatch parent, ByteBuffer instances, int quadVBO, int instanceVBO, int capacity, int textureUnits) {
        this.parent = parent;
        this.instances = instances;
        this.quadVBO = quadVBO;
        this.instanceVBO = instanceVBO;
        this.capacity = capacity;
        this.textureUnits = textureUnits;
    }

    /**
     * Queues live Slug outlines at a baseline origin in the normal batch scope.
     * Consecutive text calls share a glyph queue; switching to sprites preserves
     * submission order. Null tint selects the batch color. Translation, logical
     * clipping and opacity apply exactly once when glyphs are submitted.
     *
     * @param font borrowed live font, kept alive through flush
     * @param text UTF-16 text; null or empty submits nothing
     * @param x baseline X in world units before batch translation
     * @param y baseline Y in world units before batch translation
     * @param size finite nonnegative world units per em
     * @param tint copied tint, or null for the batch color
     * @throws IllegalStateException if the batch is not drawing or the font is disposed
     * @throws IllegalArgumentException if size is negative or nonfinite
     */
    public void draw(SlugFont font, String text, float x, float y, float size, Color tint) {
        if (!parent.isDrawing()) throw new IllegalStateException("Call begin() before draw().");
        Objects.requireNonNull(font, "font");
        if (!Float.isFinite(size) || size < 0f)
            throw new IllegalArgumentException("Size must be finite and nonnegative.");
        if (text == null || text.isEmpty() || size == 0f || parent.getOpacityMultiplier() == 0f) return;
        Color selected = tint == null ? parent.getDefaultTint() : tint;
        if (selected.a() <= 0f) return;
        if (font.isDisposed()) throw new IllegalStateException("Slug font is disposed.");
        prepare(selected);
        draw(font, text, x + parent.getTranslationX(), y + parent.getTranslationY(), size);
    }

    /**
     * Queues a retained layout using live curves without repeating text layout.
     * The run and its font are borrowed; the font must outlive queued draws.
     *
     * @param run precomputed glyph positions and size
     * @param x baseline X before batch translation
     * @param y baseline Y before batch translation
     * @param tint copied tint, or null for the batch color
     * @throws IllegalStateException if the batch is not drawing or the font is disposed
     */
    public void draw(SlugTextRun run, float x, float y, Color tint) {
        if (!parent.isDrawing()) throw new IllegalStateException("Call begin() before draw().");
        Objects.requireNonNull(run, "run");
        if (run.glyphCount() == 0 || run.size() == 0f || parent.getOpacityMultiplier() == 0f) return;
        Color selected = tint == null ? parent.getDefaultTint() : tint;
        if (selected.a() <= 0f) return;
        SlugFont font = run.font();
        if (font.isDisposed()) throw new IllegalStateException("Slug font is disposed.");
        prepare(selected);
        draw(run, x + parent.getTranslationX(), y + parent.getTranslationY());
    }

    /**
     * Applies a rigid world transform to text, matching a caller's custom sprite
     * transform. UI scopes use this for composed node rotations. Previously queued
     * work is flushed before the transform changes; begin resets it to identity.
     *
     * @param cosine finite rotation cosine
     * @param sine finite rotation sine
     * @param x finite horizontal transform offset
     * @param y finite vertical transform offset
     * @throws IllegalStateException if the parent batch has not begun drawing
     * @throws IllegalArgumentException if any transform component is nonfinite
     */
    public void setTextTransform(float cosine, float sine, float x, float y) {
        if (!parent.isDrawing()) throw new IllegalStateException("Call begin() before setTextTransform().");
        if (!Float.isFinite(cosine) || !Float.isFinite(sine) || !Float.isFinite(x) || !Float.isFinite(y))
            throw new IllegalArgumentException("Text transform must be finite.");
        if (this.cosine == cosine && this.sine == sine && transformX == x && transformY == y) return;
        parent.flush();
        this.cosine = cosine;
        this.sine = sine;
        transformX = x;
        transformY = y;
    }

    /**
     * Flushes preceding sprites, starts a segment and snaps a translated baseline for uniform axis-aligned
     * projections. General projections preserve the supplied baseline.
     *
     * @param baseline finite baseline before parent translation
     * @return baseline in the same local coordinates
     * @throws IllegalStateException if the parent batch has not begun drawing
     * @throws IllegalArgumentException if baseline is not finite
     */
    public float alignTextBaseline(float baseline) {
        if (!parent.isDrawing()) throw new IllegalStateException("Call begin() before alignTextBaseline().");
        if (!Float.isFinite(baseline)) throw new IllegalArgumentException("Baseline must be finite.");
        if (!drawing) parent.flush();
        beginGlyphSegment();
        if (projectionScale <= 0f) return baseline;
        float translationY = parent.getTranslationY();
        float translated = baseline + translationY;
        return viewMinY + Math.round((translated - viewMinY) * projectionScale) / projectionScale - translationY;
    }

    /**
     * Aligns a text line's horizontal origin to the nearest physical pixel
     * boundary when the combined camera/text projection is axis-aligned and
     * uniformly scaled. Glyph advances and kerning retain their fractional
     * precision. Flushes preceding sprites and begins a glyph segment so the
     * subsequent text reuses the same projection and viewport snapshot; other
     * projections preserve the origin. Uses the owning batch's live translation.
     *
     * @param x finite horizontal pen coordinate before parent translation
     * @return origin in the same local coordinates
     * @throws IllegalStateException if the parent batch has not begun drawing
     * @throws IllegalArgumentException if x is not finite
     */
    public float alignTextOrigin(float x) {
        if (!parent.isDrawing()) throw new IllegalStateException("Call begin() before alignTextOrigin().");
        if (!Float.isFinite(x)) throw new IllegalArgumentException("Text origin must be finite.");
        if (!drawing) parent.flush();
        beginGlyphSegment();
        if (projectionScale <= 0f) return x;
        float translationX = parent.getTranslationX();
        float translated = x + translationX;
        return viewMinX + Math.round((translated - viewMinX) * projectionScale) / projectionScale - translationX;
    }

    /**
     * Flushes preceding sprites and copies the parent's current submission state.
     * Consecutive glyph submissions retain exclusive use of shared storage.
     *
     * @param tint validated nonnull text tint
     */
    private void prepare(Color tint) {
        if (!drawing) parent.flush();
        beginGlyphSegment();
        packedColor = colorByte(tint.r()) << 24 | colorByte(tint.g()) << 16 | colorByte(tint.b()) << 8 | colorByte(tint.a() * parent.getOpacityMultiplier());
        clipEnabled = parent.isClipEnabled();
        clipX = parent.getClipX();
        clipY = parent.getClipY();
        clipW = parent.getClipWidth();
        clipH = parent.getClipHeight();
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
     * Appends an unrotated default sprite to a compatible coverage segment.
     * Dual-source blending retains the sprite pipeline's squared source alpha
     * while glyphs retain ordinary source-over alpha. Custom blending, sRGB
     * targets and text transforms keep the independent renderer boundary.
     *
     * @param texture borrowed sprite texture name
     * @param x translated minimum X
     * @param y translated minimum Y
     * @param width sprite width
     * @param height sprite height
     * @param u0 minimum texture U
     * @param v0 minimum texture V
     * @param u1 maximum texture U
     * @param v1 maximum texture V
     * @param tint full precision sprite color
     * @param opacity parent opacity
     * @param clipped whether a world clip is enabled
     * @param cx clip minimum X
     * @param cy clip minimum Y
     * @param cw clip width
     * @param ch clip height
     * @return true when the sprite joined the glyph queue
     */
    boolean appendSprite(int texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, Color tint, float opacity, boolean clipped, float cx, float cy, float cw, float ch) {
        if (texture == 0 || !drawing || !cachedCoverage || activeFont == null || srgbTarget || oldDepth || oldCull || cosine != 1f || sine != 0f || transformX != 0f || transformY != 0f) return false;
        if (oldSrcRgb != GL_SRC_ALPHA || oldDstRgb != GL_ONE_MINUS_SRC_ALPHA || oldSrcAlpha != GL_SRC_ALPHA || oldDstAlpha != GL_ONE_MINUS_SRC_ALPHA || oldEquationRgb != GL_FUNC_ADD || oldEquationAlpha != GL_FUNC_ADD) return false;
        if (!supportsMixedTarget()) return false;
        if (mixedShader == null) {
            String vertex = ValthorneFiles.readString("valthorne/shaders/font/slug.vert");
            String fragment = ValthorneFiles.readString("valthorne/shaders/font/slug.frag");
            mixedShader = new Shader(vertex.replace("#version 330 core", "#version 330 core\n#define SLUG_MIXED_SPRITES"), fragment.replace("#version 330 core", "#version 330 core\n#define SLUG_CACHED_COVERAGE\n#define SLUG_MIXED_SPRITES"));
            mixedShader.bind();
            mixedShader.setUniform1i("u_coverageTexture", 2);
            mixedShader.setUniform1i("u_spriteTexture", 3);
        }
        if (!mixedSprites) {
            glActiveTexture(GL_TEXTURE3);
            oldTexture3 = readInteger(GL_TEXTURE_BINDING_2D);
            oldSampler3 = readInteger(GL_SAMPLER_BINDING);
            mixedSprites = true;
        }
        if (instanceCount == capacity || spriteTexture != texture && spriteTexture != 0) flushGlyphs();
        spriteTexture = texture;
        instances.putFloat(x).putFloat(y).putFloat(x + width).putFloat(y + height);
        instances.putFloat(u0).putFloat(v0).putFloat(u1).putFloat(v1);
        instances.putInt(0).putInt(0);
        instances.putFloat(tint.r()).putFloat(tint.g()).putFloat(tint.b()).putFloat(tint.a() * opacity);
        instances.putFloat(-2f).putInt(0);
        instances.putFloat(cx).putFloat(cy).putFloat(cw).putFloat(ch).putFloat(clipped ? 1f : 0f);
        instanceCount++;
        return true;
    }

    /**
     * Checks the destination only when sprites first try to join a text segment.
     * Sampler overrides retain the parent's texture filtering and unit selection.
     * Additional active color targets retain independent blending, including
     * sparse configurations with an unused target between active attachments.
     * Pure text submissions incur no extra framebuffer queries.
     *
     * @return true when dual-source blending can target the current framebuffer
     */
    private boolean supportsMixedTarget() {
        if (mixedTargetKnown) return mixedTargetSupported;
        mixedTargetKnown = true;
        mixedTargetSupported = false;
        if (oldSampler0 != 0 || textureUnits > 1 && oldSampler1 != 0 || textureUnits > 2 && oldSampler2 != 0) return false;
        int active = readInteger(GL_ACTIVE_TEXTURE);
        try {
            for (int unit = 3; unit < textureUnits; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit);
                if (readInteger(GL_SAMPLER_BINDING) != 0) return false;
            }
        } finally {
            glActiveTexture(active);
        }
        mixedTargetSupported = true;
        int targets = readInteger(GL_MAX_DRAW_BUFFERS);
        for (int target = 1; target < targets; target++) {
            if (readInteger(GL_DRAW_BUFFER0 + target) != GL_NONE) {
                mixedTargetSupported = false;
                break;
            }
        }
        return mixedTargetSupported;
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
        byte[] reusable = null;
        WeakReference<SlugTextRun> retainedIdentity = null;
        int prefix = 0;
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
                    if (retained.revision == run.revision()) {
                        if (!cachedCoverage || activeFont != font || instanceCount + retained.count > capacity) flushGlyphs();
                        cachedCoverage = true;
                        activeFont = font;
                        instances.put(retained.bytes);
                        instanceCount += retained.count;
                        retained.scope = geometryScope;
                        return;
                    }
                    if (retained.complete) prefix = Math.min(retained.count, run.unchangedGlyphPrefix(retained.revision));
                    reusable = retained.bytes;
                    retainedIdentity = retained.run;
                }
            }
            if (retained.bytes != null) {
                geometryBytes -= retained.bytes.length;
                retained.bytes = null;
            }
            retainGeometryKey(retained, run, x, y);
        }
        if (prefix > 0) {
            if (!cachedCoverage || activeFont != font || instanceCount + run.glyphCount() > capacity) flushGlyphs();
            cachedCoverage = true;
            activeFont = font;
        }
        int start = instances.position();
        int count = instanceCount;
        long draws = totalDrawCalls;
        long generation = coverageCache.generation();
        if (prefix > 0) {
            instances.put(reusable, 0, prefix * TextureBatchContract.GLYPH_STRIDE_BYTES);
            instanceCount += prefix;
        }
        for (int i = prefix; i < run.glyphCount(); i++)
            appendGlyph(font, run.glyph(i), x + run.xOffset(i), y + run.yOffset(i), run.size());
        int bytes = instances.position() - start;
        if (retained != null && repeated && cachedCoverage && totalDrawCalls == draws && coverageCache.generation() == generation && bytes > 0 && geometryBytes + bytes <= MAX_GEOMETRY_BYTES) {
            byte[] packed = reusable != null && reusable.length == bytes ? reusable : new byte[bytes];
            WeakReference<SlugTextRun> identity = retainedIdentity != null ? retainedIdentity : new WeakReference<>(run);
            instances.get(start, packed);
            retained.run = identity;
            retained.bytes = packed;
            retained.count = instanceCount - count;
            retained.complete = retained.count == run.glyphCount();
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
     * Compares submission state independently of layout revision. The caller
     * validates the revision or an explicitly preserved shaped prefix before reuse.
     *
     * @param entry retained submission slot
     * @param run current layout
     * @param x world baseline X
     * @param y world baseline Y
     * @return true when retained instances remain valid
     */
    private boolean matchesGeometry(SlugRunGeometry entry, SlugTextRun run, float x, float y) {
        return (entry.bytes == null ? entry.identity == System.identityHashCode(run) : entry.run.get() == run) && entry.size == run.size() && entry.x == x && entry.y == y && entry.color == packedColor && entry.clipped == clipEnabled
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
            mixedTargetKnown = false;
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
            IntBuffer indices = BufferUtils.createIntBuffer(10);
            indices.put(0).put(1).put(4).put(2);
            indices.put(0).put(1).put(2).put(2).put(4).put(0).flip();
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
        float px = 0;
        float py = 0;
        if (projectionScale > 0f && projection.m00() > 0f && projection.m11() > 0f) {
            px = ((projection.m00() * x + projection.m30()) / projection.m33() + 1f) * .5f * viewport[2];
            py = ((projection.m11() * y + projection.m31()) / projection.m33() + 1f) * .5f * viewport[3];
        }
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
        if (projectionScale > 0f && projection.m00() > 0f && projection.m11() > 0f && appendCachedGlyph(font, glyph, x, y, size, x0, y0, x1, y1, cpuClip, px, py)) return;
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
            double ix = Math.floor(px);
            double iy = Math.floor(py);
            glyph.writeInstance(instances, x0, y0, x1, y1, (float) ix + viewport[0], (float) iy + viewport[1], (float) (px - ix), (float) (py - iy), size * projectionScale, packedColor);
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
     * @param px original projected physical baseline X
     * @param py original projected physical baseline Y
     * @return true when coverage instances were queued
     */
    private boolean appendCachedGlyph(SlugFont font, SlugGlyph glyph, float x, float y, float size, float x0, float y0, float x1, float y1, boolean cpuClip, float px, float py) {
        float ppem = size * projectionScale;
        float phaseX = px - (float) Math.floor(px);
        float phaseY = py - (float) Math.floor(py);
        int slot = coverageCache.find(glyph, ppem, phaseX, phaseY);
        int left;
        int bottom;
        int width;
        int height;
        if (coverageCache.contains(slot)) {
            left = coverageCache.left(slot);
            bottom = coverageCache.bottom(slot);
            width = coverageCache.width(slot);
            height = coverageCache.height(slot);
        } else {
            left = (int) Math.floor(phaseX + glyph.minX() * ppem - .5f);
            bottom = (int) Math.floor(phaseY + glyph.minY() * ppem - .5f);
            width = (int) Math.ceil(phaseX + glyph.maxX() * ppem + .5f) - left;
            height = (int) Math.ceil(phaseY + glyph.maxY() * ppem + .5f) - bottom;
            if (width <= 0 || height <= 0 || width > SlugCoverageCache.MAX_GLYPH_EXTENT || height > SlugCoverageCache.MAX_GLYPH_EXTENT) return false;
            if (activeFont != font || captureCount == capacity) flushGlyphs();
            activeFont = font;
            if (coverageCache.needsReset(width, height)) {
                flushGlyphs();
                coverageCache.reset();
                slot = coverageCache.find(glyph, ppem, phaseX, phaseY);
            }
            queueCoverage(glyph, slot, ppem, phaseX, phaseY, left, bottom, width, height);
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
        instances.putFloat(x0).putFloat(y0).putFloat(x1).putFloat(y1);
        instances.putFloat(coverageCache.x(slot) + (x0 - originX) * projectionScale).putFloat(coverageCache.y(slot) + (y0 - originY) * projectionScale);
        instances.putFloat(coverageCache.x(slot) + (x1 - originX) * projectionScale).putFloat(coverageCache.y(slot) + (y1 - originY) * projectionScale);
        // Cached shaders never read outline addresses or band transforms.
        instances.position(instances.position() + 24);
        instances.putFloat(ppem);
        instances.put((byte) (packedColor >>> 24)).put((byte) (packedColor >>> 16)).put((byte) (packedColor >>> 8)).put((byte) packedColor);
        if (clipEnabled && !cpuClip) instances.putFloat(clipX).putFloat(clipY).putFloat(clipW).putFloat(clipH).putFloat(1);
        else {
            instances.position(instances.position() + 16);
            instances.putFloat(0);
        }
        instanceCount++;
        return true;
    }

    /**
     * Reserves and queues an exact cold sample without interrupting painter order.
     *
     * @param glyph immutable outline metadata
     * @param slot vacant atlas key
     * @param ppem physical em size
     * @param phaseX exact horizontal baseline fraction
     * @param phaseY exact vertical baseline fraction
     * @param left sample minimum relative to integer baseline
     * @param bottom sample minimum relative to integer baseline
     * @param width sample width in pixels
     * @param height sample height in pixels
     */
    private void queueCoverage(SlugGlyph glyph, int slot, float ppem, float phaseX, float phaseY, int left, int bottom, int width, int height) {
        if (captures == null) captures = memAlloc(capacity * TextureBatchContract.GLYPH_STRIDE_BYTES);
        int target = coverageCache.texture() == 0 ? readInteger(GL_DRAW_FRAMEBUFFER_BINDING) : -1;
        if (target != -1) {
            glActiveTexture(GL_TEXTURE2);
            try {
                coverageCache.insert(slot, glyph, ppem, phaseX, phaseY, left, bottom, width, height);
            } catch (RuntimeException | Error failure) {
                coverageCache.dispose();
                throw failure;
            } finally {
                glBindTexture(GL_TEXTURE_2D, 0);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target);
            }
        } else coverageCache.insert(slot, glyph, ppem, phaseX, phaseY, left, bottom, width, height);
        int x = coverageCache.x(slot);
        int y = coverageCache.y(slot);
        glyph.writeInstance(captures, x, y, x + width, y + height, x - left, y - bottom, phaseX, phaseY, ppem, -1);
        captures.putFloat(0).putFloat(0).putFloat(0).putFloat(0).putFloat(0);
        captureCount++;
    }

    /**
     * Evaluates queued cold tiles in one analytic draw before their first use.
     * Each tile writes its complete rectangle, including zero coverage, so no
     * per-glyph clears or framebuffer switches are needed. Caller masking state
     * is suspended during capture and restored before destination composition.
     */
    private void captureCoverage() {
        if (captureCount == 0) return;
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
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, coverageCache.framebuffer());
            glViewport(0, 0, SlugCoverageCache.EXTENT, coverageCache.height());
            glDisable(GL_SCISSOR_TEST);
            glColorMask(true, true, true, true);
            glDisable(GL_BLEND);
            glDisable(GL_FRAMEBUFFER_SRGB);
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_RASTERIZER_DISCARD);
            glDisable(GL_SAMPLE_MASK);
            glDisable(GL_SAMPLE_COVERAGE);
            glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            shader.bind();
            shader.setUniformMatrix4("u_mvp", transform.identity().ortho2D(0, SlugCoverageCache.EXTENT, 0, coverageCache.height()).get(matrix));
            shader.setUniform4f("u_textTransform", 1, 0, 0, 0);
            shader.setUniform1i("u_writeCoverage", 1);
            activeFont.bindTextures();
            captures.flip();
            glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
            glBufferData(GL_ARRAY_BUFFER, (long) capacity * TextureBatchContract.INST_STRIDE_BYTES, GL_STREAM_DRAW);
            glBufferSubData(GL_ARRAY_BUFFER, 0L, captures);
            glDrawElementsInstanced(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_INT, 0L, captureCount);
            totalDrawCalls++;
        } catch (RuntimeException | Error failure) {
            coverageCache.dispose();
            throw failure;
        } finally {
            captures.clear();
            captureCount = 0;
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
     * quad through analytic strip or compatible sprite triangle indices. Keeps state active for
     * subsequent text and preserves the original antialiasing interpolation.
     */
    private void flushGlyphs() {
        captureCoverage();
        if (instanceCount == 0) return;
        if (cachedCoverage) {
            if (activeFont.isDisposed()) throw new IllegalStateException("Slug font is disposed.");
        } else activeFont.bindTextures();
        boolean linear = srgbTarget && !activeFont.isHinted();
        Shader current = cachedCoverage ? mixedSprites ? mixedShader : cachedShader : shader;
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
        if (cachedCoverage && mixedSprites) {
            glActiveTexture(GL_TEXTURE3);
            glBindTexture(GL_TEXTURE_2D, spriteTexture);
            glBindSampler(3, 0);
            glBlendFuncSeparate(GL_SRC1_COLOR, GL_ONE_MINUS_SRC_ALPHA, GL_SRC1_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        } else glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        if (linear) glEnable(GL_FRAMEBUFFER_SRGB);
        else glDisable(GL_FRAMEBUFFER_SRGB);
        instances.flip();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, (long) capacity * TextureBatchContract.INST_STRIDE_BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0L, instances);
        if (cachedCoverage && mixedSprites) glDrawElementsInstanced(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 4L * Integer.BYTES, instanceCount);
        else glDrawElementsInstanced(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_INT, 0L, instanceCount);
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
        if (mixedSprites) {
            glActiveTexture(GL_TEXTURE3);
            glBindTexture(GL_TEXTURE_2D, oldTexture3);
            glBindSampler(3, oldSampler3);
            mixedSprites = false;
            spriteTexture = 0;
        }
        glActiveTexture(oldActiveTexture);
        captureCount = 0;
        if (captures != null) captures.clear();
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
        if (captures != null) memFree(captures);
        captures = null;
        if (cachedShader != null) cachedShader.dispose();
        if (mixedShader != null) mixedShader.dispose();
        for (int i = 0; i < geometryCache.length; i++) geometryCache[i] = null;
        geometryBytes = 0;
        if (shader == null) return;
        shader.dispose();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(indexBuffer);
    }
}
