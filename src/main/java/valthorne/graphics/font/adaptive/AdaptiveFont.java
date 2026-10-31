package valthorne.graphics.font.adaptive;

import org.lwjgl.system.MemoryStack;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.TextFont;
import valthorne.graphics.font.FontStyler;
import valthorne.graphics.font.GlyphContext;
import valthorne.graphics.font.GlyphStyle;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.texture.TextureBatch;

import java.nio.IntBuffer;
import java.util.Arrays;

import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

/**
 * One font API for naturally spaced, hinted small text and scalable MSDF text.
 * HarfBuzz always shapes the same design-unit font; raster choice never changes
 * advances, kerning, substitutions or line metrics. Axis-aligned text up to 48
 * physical pixels per em uses grayscale FreeType coverage with configurable
 * {@link FontHinting}; larger, rotated,
 * perspective or anisotropically scaled text uses linear MSDF atlases.
 * Size is not part of the encoded asset. {@link #setSize(float)} changes the
 * default for size-free drawing and measurement, initially 16 logical units per
 * em. Explicit-size calls and retained layouts keep independent sizes. Change
 * {@link #setHinting(FontHinting)} at runtime to update coverage rendering for
 * all subsequent draws, including previously created retained layouts. Cached
 * coverage variants coexist within the existing bounded atlas; MSDF images and
 * shaping metrics are shared across hinting modes.
 * {@link #setStyler(FontStyler)} applies live per-glyph color, visibility, scale
 * and offsets to direct and retained drawing, including borrowing UI controls.
 * Styling does not change advances, wrapping or caret geometry. Context indices
 * use original UTF-16 cluster starts, visual glyph order and source line order;
 * ligatures share their first source character. Scratch context and style objects
 * are reused and must not be retained. Fractional offsets remain smooth; scaled
 * glyphs request MSDF automatically. Callbacks run on the owner thread during
 * every draw and must not recursively draw this font or mutate its resources.
 * Initial and stable small text rasterizes at its requested physical resolution,
 * so visible text never waits behind speculative background work to become sharp.
 * Small-size cache misses generate inexpensive coverage immediately rather than
 * displaying enlarged lower-resolution glyphs. Larger text can reuse cached MSDF.
 * MSDF preparation runs off-thread for visible glyphs; speculative prewarming
 * only prepares inexpensive default-size coverage. Large-text fallback coverage
 * uses the displayed physical resolution up to 128 pixels per em. GPU uploads
 * are admitted in small, spaced groups without changing shaping metrics.
 *
 * <p>Create from CPU-only {@link AdaptiveFontData} on the graphics thread. Native
 * shaping and provisional-coverage faces are created immediately; workers own
 * independent preparation faces, and GPU pages are lazy. The font owns these
 * resources and borrows the batch's standard shader. Borrowed retained runs need
 * no disposal. Call {@link #dispose()}
 * after ending batches that reference the font. Access is confined to the
 * constructing thread, including measurement and disposal.</p>
 *
 * <p>Coverage pages use R8 at 256 square; MSDF pages use RGB8 at 512 square.
 * Oversized glyphs may use pages up to 1024 square. At most eight pages and 8192
 * glyph entries bound storage to 24 MiB of texel data in the worst case. The
 * sparse glyph table grows on demand. Full caches flush the batch before page
 * eviction. Retained runs validate cached image references against an eviction
 * version and remain usable after eviction. A warm
 * retained draw allocates no per-glyph objects. Direct drawing caches 256 short
 * layouts; use retained runs for large documents or predictable cache reuse. Prewarming
 * is asynchronous and admits at most 256 pending images per font so broad
 * Unicode ranges cannot cause unbounded startup work. All other glyphs load on
 * demand, including glyphs introduced by shaping. Consecutive sprites, coverage
 * text and MSDF text share the standard batch without per-label flushes.
 * Optional outlines replay the shaped run at cached integer offsets in a round disk,
 * behind its fill. The radius is in logical units (1 through 16), follows batch
 * scaling/rotation and uses the same coverage/MSDF path, clipping and shaders.
 * Outline settings are shared by all users of this font, including retained runs
 * and NanoLabel theme text; use a separate font for independent styles. Layout,
 * advances and selection bounds do not expand. Effects may be clipped by UI bounds.
 * The disabled path adds one branch; enabled drawing submits one extra quad per
 * drawable glyph per disk offset, with no new per-frame layout or color objects.
 * Large radii cost proportionally more submissions and should be used sparingly.
 * Glyph styling callbacks run for each outline pass as well as the fill, using
 * the unshifted fill geometry; keep callbacks deterministic and side-effect free.
 * Participating batch shaders retain their transforms and fragment effects.
 * Each MSDF image, including a provisional small-text fallback, temporarily uses
 * the standard shader when the selected shader lacks distance-field support.
 * Coverage images retain the custom shader, which is restored after drawing.</p>
 *
 * <pre>{@code
 * AdaptiveFont font = AdaptiveFontData.load("Cinzel-Regular.otf")
 *         .asFont();
 * font.setSize(20)
 *         .setHinting(FontHinting.LIGHT);
 * font.setOutline(1, Color.BLACK);
 * AdaptiveTextRun title = font.createRun("Valthorne", 96);
 * batch.begin();
 * font.draw(batch, "Inventory", 24, 100, 20, Color.WHITE);
 * title.draw(batch, 24, 200, Color.WHITE);
 * batch.end();
 * font.disableOutline(); // Existing retained runs immediately use fill-only rendering.
 * font.dispose();
 * }</pre>
 *
 * <p>For standard UI labels, configure the borrowed font on the graphics thread
 * before assigning it to the UI theme:</p>
 * <pre>{@code
 * AdaptiveFont uiFont = AdaptiveFontData.load("ui.ttf").asFont();
 * uiFont.setOutline(1, Color.BLACK);
 * ui.getTheme().setToken(NanoText.FONT, uiFont);
 * ui.add(new NanoLabel("Loading..."));
 * // Dispose uiFont on its owner thread after UI drawing has stopped.
 * }</pre>
 */
public final class AdaptiveFont implements TextFont {
    /**
     * Largest physical em size using grid-fitted bitmap coverage.
     */
    private static final int HINT_LIMIT = 48;
    /**
     * Bits reserved for physical coverage sizes, including the zero MSDF sentinel.
     */
    private static final int HINTING_SHIFT = 6;
    /**
     * Logical em size used until the caller selects another default size.
     */
    private static final float DEFAULT_SIZE = 16f;
    /**
     * Reserved six-bit raster value for size-independent provisional coverage.
     */
    private static final int FALLBACK_RASTER = 63;
    /**
     * Inexpensive fallback resolution used while final glyphs prepare off-thread.
     */
    private static final int FALLBACK_SIZE = 24;
    /**
     * Highest provisional coverage resolution before background MSDF is ready.
     */
    private static final int MAX_FALLBACK_SIZE = 128;
    /**
     * Maximum ready glyph uploads admitted by one preparation drain.
     */
    private static final int MAX_UPLOADS = 4;
    /**
     * Minimum nanoseconds between bounded upload groups during preparation.
     */
    private static final long UPLOAD_INTERVAL = 1_000_000L;
    /**
     * CPU time budget checked between ready-image uploads in one group.
     */
    private static final long UPLOAD_BUDGET = 200_000L;
    /**
     * Maximum simultaneously owned atlas pages.
     */
    private static final int MAX_PAGES = 8;
    /**
     * Open-addressed table size, twice the maximum populated entry count.
     */
    private static final int TABLE_SIZE = 16384;
    /**
     * Initial sparse table capacity; ordinary ASCII fonts grow only to 256 slots.
     */
    private static final int INITIAL_CACHE_CAPACITY = 64;
    /**
     * Bounded short-layout cache, sufficient for a typical panel of distinct labels.
     */
    private static final int LAYOUT_CACHE_CAPACITY = 256;
    /**
     * Small fixed probe group balancing label reuse against lookup work.
     */
    private static final int LAYOUT_CACHE_WAYS = 8;
    /**
     * Maximum disk radius, bounding configuration storage and extra quad submissions.
     */
    private static final int MAX_OUTLINE_WIDTH = 16;
    private final Color outlineColor = new Color(0f, 0f, 0f, 1f); // Owned outline tint, copied at configuration time.
    private final Color outlineTint = new Color(0f, 0f, 0f, 1f); // Reused submission color with fill alpha applied.
    private int[] outlineOffsets; // Cached disk offset pairs; allocated only when configuring a new radius.
    private int outlineWidth = 1; // Configured radius in logical drawing units.
    private boolean outlineEnabled; // Whether every run draw includes the cached outline passes.
    private final Thread owner = Thread.currentThread(); // Native and OpenGL owner thread.
    private final AdaptiveNativeFace face; // Owner-thread shaper and quick provisional coverage rasterizer.
    private final FontGlyphPreparation preparation; // Bounded background preparation with an independent native face.
    private FontHinting hinting; // Mutable small-text policy; coverage caches distinguish each mode.
    private int hintingKey; // Current policy ordinal packed above the six physical raster-size bits.
    private AdaptiveTextRun scratch = new AdaptiveTextRun(this); // Owned long-text scratch; released on disposal.
    private final AdaptiveTextRun[] layouts = new AdaptiveTextRun[LAYOUT_CACHE_CAPACITY]; // Bounded cache; each run owns its source key.
    private int layoutVictim; // Round-robin victim selected within a full cache group.
    private long[] keys = new long[INITIAL_CACHE_CAPACITY]; // Growing primitive keys; zero is unused, null after disposal.
    private AtlasGlyph[] glyphs = new AtlasGlyph[INITIAL_CACHE_CAPACITY]; // Entries parallel to keys; null after disposal.
    private final FontAtlasPage[] pages = new FontAtlasPage[MAX_PAGES]; // Owned bounded GPU pages.
    private float size = DEFAULT_SIZE; // Mutable logical em size used by overloads without an explicit size.
    private final float ascent; // Normalized typographic ascent.
    private final float descent; // Normalized typographic descent, normally negative.
    private final float lineHeight; // Normalized baseline spacing.
    private final float spaceAdvance; // Normalized nominal space advance for tabs.
    private int pageCount; // Active prefix of atlas pages.
    private int entries; // Current cache occupancy.
    private long cacheVersion; // Changes whenever atlas eviction invalidates retained image references.
    private long atlasBytes; // Exact allocated R8/RGB8 texel storage, excluding driver bookkeeping.
    private float pixelScaleX; // Horizontal physical pixels per logical unit for the current hinted draw.
    private float pixelScaleY; // Vertical physical pixels per logical unit for the current hinted draw.
    private float pixelOriginX; // Viewport/projection horizontal physical origin.
    private float pixelOriginY; // Viewport/projection vertical physical origin.
    private float physicalEmSize; // Current uniform physical em size used for provisional raster resolution.
    private boolean preparing; // Whether completed CPU images may need owner-thread upload.
    private long nextUpload = System.nanoTime(); // Earliest monotonic time for another bounded upload group.
    private boolean disposed; // Prevents native access after release.
    private FontStyler styler; // Optional borrowed callback evaluated for every shaped glyph during drawing.
    private GlyphStyle glyphStyle; // Lazily allocated reusable callback output, reset before each glyph.
    private GlyphContext glyphContext; // Lazily allocated reusable original-layout context.

    /**
     * Creates native font resources using NORMAL hinting, requiring no OpenGL
     * until first drawing.
     * @param data immutable encoded asset
     */
    public AdaptiveFont(AdaptiveFontData data) {
        this(data, FontHinting.NORMAL);
    }

    /**
     * Creates native font resources with an initial coverage hinting policy.
     * NONE retains grayscale coverage without grid fitting. The selected mode
     * does not change design-unit metrics, shaping or the MSDF rendering path.
     * @param data immutable encoded asset
     * @param hinting non-null small-text hinting mode
     * @throws NullPointerException if data or hinting is null
     */
    public AdaptiveFont(AdaptiveFontData data, FontHinting hinting) {
        if (data == null) throw new NullPointerException("data");
        if (hinting == null) throw new NullPointerException("hinting");
        this.hinting = hinting;
        hintingKey = hinting.ordinal() << HINTING_SHIFT;
        int firstCharacter = data.firstCharacterIndex();
        int prewarmCount = Math.min(data.characterCount(), 256);
        byte[] source = data.bytes();
        face = new AdaptiveNativeFace(source);
        preparation = new FontGlyphPreparation(source);
        float inverseEm = 1f / face.face().units_per_EM();
        ascent = face.face().ascender() * inverseEm;
        descent = face.face().descender() * inverseEm;
        lineHeight = Math.max(ascent - descent, face.face().height() * inverseEm);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer out = stack.callocInt(1);
            hb_font_get_nominal_glyph(face.font(), ' ', out);
            spaceAdvance = hb_font_get_glyph_h_advance(face.font(), out.get(0)) * inverseEm;
            for (int i = 0; i < prewarmCount; i++) {
                if (!hb_font_get_nominal_glyph(face.font(), firstCharacter + i, out)) continue;
                int glyph = out.get(0);
                preparing |= preparation.request(glyphKey(glyph, (int) DEFAULT_SIZE), glyph, (int) DEFAULT_SIZE, hinting, false);
            }
        }
    }

    /**
     * Enables a round outline shared by this font's immediate and retained draws.
     * Radius changes rebuild a bounded disk-offset cache; color-only changes reuse it.
     * The supplied color is copied. Outline alpha multiplies the corresponding fill
     * alpha and batch opacity, so transparent text remains transparent.
     *
     * @param width outline radius in logical units, from 1 through 16
     * @param color outline color, or null to retain the current color
     * @return this font
     * @throws IllegalArgumentException if width is outside the supported range
     * @throws IllegalStateException if disposed or accessed from another thread
     */
    @Override
    public AdaptiveFont setOutline(int width, Color color) {
        checkOwner();
        if (width < 1 || width > MAX_OUTLINE_WIDTH)
            throw new IllegalArgumentException("Outline width must be between 1 and " + MAX_OUTLINE_WIDTH);
        if (outlineOffsets == null || width != outlineWidth) {
            int count = 0;
            for (int y = -width; y <= width; y++)
                for (int x = -width; x <= width; x++)
                    if ((x != 0 || y != 0) && x * x + y * y <= width * width) count++;
            int[] offsets = new int[count * 2];
            int index = 0;
            for (int y = -width; y <= width; y++) {
                for (int x = -width; x <= width; x++) {
                    if ((x == 0 && y == 0) || x * x + y * y > width * width) continue;
                    offsets[index++] = x;
                    offsets[index++] = y;
                }
            }
            outlineOffsets = offsets;
        }
        outlineWidth = width;
        if (color != null) outlineColor.set(color);
        outlineEnabled = true;
        return this;
    }

    @Override
    public void disableOutline() {
        checkOwner();
        outlineEnabled = false;
    }

    @Override
    public boolean isOutlineEnabled() {
        checkOwner();
        return outlineEnabled;
    }

    @Override
    public int getOutlineWidth() {
        checkOwner();
        return outlineWidth;
    }

    /**
     * Checks ownership and native lifetime at the public API boundary.
     */
    void checkOwner() {
        if (owner != Thread.currentThread()) throw new IllegalStateException("Font accessed outside its owner thread");
        if (disposed) throw new IllegalStateException("Font is disposed");
    }

    /**
     * Borrows the native shaper from owner-thread layout code.
     * @return live native face
     */
    AdaptiveNativeFace nativeFace() { return face; }

    /**
     * Returns the normalized nominal space advance used by tab layout.
     * @return advance in em units
     */
    float spaceAdvance() { return spaceAdvance; }

    /**
     * Returns this font's current small-text raster policy.
     * @return selected hinting mode, NORMAL for the default constructor
     */
    public FontHinting hinting() { return hinting; }

    /**
     * Selects coverage hinting for subsequent draws, including existing retained
     * runs and UI controls borrowing this font. Layout metrics and MSDF images
     * remain unchanged. Coverage images are cached per policy within the same
     * bounded atlas, so changing modes neither flushes nor invalidates queued
     * text. Missing small-size coverage generates immediately on first use;
     * expensive MSDF preparation remains asynchronous.
     * @param hinting non-null coverage hinting mode
     * @return this font for fluent configuration
     * @throws NullPointerException if hinting is null
     * @throws IllegalStateException if disposed or called outside the owner thread
     */
    public AdaptiveFont setHinting(FontHinting hinting) {
        checkOwner();
        if (hinting == null) throw new NullPointerException("hinting");
        this.hinting = hinting;
        hintingKey = hinting.ordinal() << HINTING_SHIFT;
        return this;
    }

    /**
     * Returns the logical em size used by overloads without an explicit size.
     * @return current default size, initially 16
     */
    public float getSize() { return size; }

    /**
     * Changes the default logical em size without rebuilding native faces or
     * clearing glyph caches. Explicit-size drawing and retained runs keep their
     * own size; resize a run with its rebuild method. Zero hides default drawing.
     * @param size finite nonnegative logical units per em
     * @return this font for fluent configuration
     * @throws IllegalArgumentException if size is negative or nonfinite
     * @throws IllegalStateException if disposed or called outside the owner thread
     */
    public AdaptiveFont setSize(float size) {
        checkOwner();
        if (!Float.isFinite(size) || size < 0) throw new IllegalArgumentException("Size must be finite and nonnegative");
        this.size = size;
        return this;
    }

    /**
     * Returns the current glyph callback without copying or allocating.
     * @return borrowed callback, or null when per-glyph styling is disabled
     */
    public FontStyler getStyler() {
        checkOwner();
        return styler;
    }

    /**
     * Assigns a live drawing callback, preserving existing layouts and images.
     * Explicit callback colors replace span colors or the supplied tint; offsets
     * and scaling affect glyph quads only. Positive Y offsets follow the font's
     * upward baseline coordinates and rotate with the run. Callback exceptions
     * propagate to the caller. Scratch objects are borrowed only for the callback.
     * Source mappings are allocated once per styled run, then reused on rebuilds.
     * @param styler callback or null to restore the ordinary drawing fast path
     * @return this font for fluent configuration
     */
    public AdaptiveFont setStyler(FontStyler styler) {
        checkOwner();
        if (styler != null && glyphStyle == null) {
            glyphStyle = new GlyphStyle();
            glyphContext = new GlyphContext();
        }
        this.styler = styler;
        return this;
    }

    /**
     * Measures text at the current default size.
     * @param text source text, null for empty
     * @return logical advance width
     */
    public float getWidth(String text) { return getWidth(text, size); }

    /**
     * Measures text height at the current default size.
     * @param text source text, null for empty
     * @return logical line-box height
     */
    public float getHeight(String text) { return getHeight(text, size); }

    /**
     * Creates a retained layout at the current default size. The run owns its
     * size; later default-size changes do not alter an existing run.
     * @param text source text, null for empty
     * @return independently mutable retained layout borrowing this font
     */
    public AdaptiveTextRun createRun(String text) { return createRun(text, size); }

    /**
     * Reports exact allocated atlas texel bytes without querying the driver.
     * CPU staging, native font objects and driver bookkeeping are excluded.
     * @return currently owned R8/RGB8 texture storage in bytes
     */
    public long atlasBytes() { return atlasBytes; }

    @Override
    public float ascent() { return ascent; }

    @Override
    public float descent() { return descent; }

    @Override
    public float lineHeight() { return lineHeight; }

    @Override
    public float getWidth(String text, float size) {
        return layout(text, size).width();
    }

    @Override
    public float getWidth(String text, int startInclusive, int endExclusive, float size) {
        if (text == null) throw new NullPointerException("text");
        if (startInclusive == 0 && endExclusive == text.length()) return getWidth(text, size);
        return getWidth(text.substring(startInclusive, endExclusive), size);
    }

    @Override
    public float getHeight(String text, float size) {
        return layout(text, size).height();
    }

    @Override
    public AdaptiveTextRun createRun(String text, float size) {
        checkOwner();
        AdaptiveTextRun run = new AdaptiveTextRun(this);
        run.rebuild(text, size);
        return run;
    }

    @Override
    public void draw(TextureBatch batch, String text, float x, float y, float size, Color tint) {
        layout(text, size).draw(batch, x, y, tint);
    }

    /**
     * Draws at the font's current default size with the standard shared layout.
     * @param batch active destination
     * @param text source
     * @param x baseline X
     * @param y baseline Y
     * @param tint borrowed tint, null for white
     */
    public void draw(TextureBatch batch, String text, float x, float y, Color tint) {
        draw(batch, text, x, y, size, tint);
    }

    /**
     * Draws a rotated run with automatic MSDF selection around its baseline origin.
     * @param batch active destination
     * @param text source
     * @param x baseline X
     * @param y baseline Y
     * @param size logical em size
     * @param radians counterclockwise rotation
     * @param tint borrowed tint, null for white
     */
    public void draw(TextureBatch batch, String text, float x, float y, float size, float radians, Color tint) {
        layout(text, size).draw(batch, x, y, radians, tint);
    }

    @Override
    public AdaptiveTextRun layout(String text, float size) {
        checkOwner();
        if (!Float.isFinite(size) || size < 0) throw new IllegalArgumentException("Size must be finite and nonnegative");
        String normalized = text == null ? "" : text;
        if (normalized.length() > 1024) {
            scratch.rebuild(normalized, size);
            return scratch;
        }
        int hash = cacheSlot(normalized.hashCode());
        int start = (hash & (LAYOUT_CACHE_CAPACITY / LAYOUT_CACHE_WAYS - 1)) * LAYOUT_CACHE_WAYS;
        int vacant = -1;
        for (int i = start; i < start + LAYOUT_CACHE_WAYS; i++) {
            AdaptiveTextRun cached = layouts[i];
            if (cached != null && normalized.equals(cached.text)) {
                cached.size = size;
                return cached;
            }
            if (cached == null) vacant = i;
        }
        int slot = vacant >= 0 ? vacant : start + (layoutVictim++ & (LAYOUT_CACHE_WAYS - 1));
        AdaptiveTextRun run = layouts[slot];
        if (run == null) run = new AdaptiveTextRun(this);
        run.rebuild(normalized, size);
        layouts[slot] = run;
        return run;
    }

    /**
     * Selects a raster size from the actual viewport and projection. Perspective,
     * rotation, reflections and nonuniform scale use MSDF instead of grid fitting.
     * @param batch active destination supplying a per-scope viewport snapshot
     * @param size logical em size
     * @param radians explicit run rotation
     * @return hinted integer physical size, or zero for MSDF
     */
    private int rasterSize(TextureBatch batch, float size, float radians) {
        physicalEmSize = 0;
        float[] matrix = Window.getProjectionMatrix();
        int width = batch.getViewportWidth();
        int height = batch.getViewportHeight();
        float sx = Math.abs(matrix[0]) * width * 0.5f;
        float sy = Math.abs(matrix[5]) * height * 0.5f;
        physicalEmSize = size * Math.max(sx, sy);
        if (radians != 0 || batch.transformsText() || matrix[1] != 0 || matrix[4] != 0 || matrix[3] != 0 || matrix[7] != 0 || matrix[15] != 1 || matrix[0] <= 0 || matrix[5] <= 0) {
            if (matrix[1] != 0 || matrix[4] != 0) {
                double horizontal = Math.hypot(matrix[0] * width, matrix[1] * height);
                double vertical = Math.hypot(matrix[4] * width, matrix[5] * height);
                physicalEmSize = (float) (size * Math.max(horizontal, vertical) * 0.5);
            }
            return 0;
        }
        if (Math.abs(sx - sy) > Math.max(sx, sy) * 0.0001f) return 0;
        pixelScaleX = sx;
        pixelScaleY = sy;
        pixelOriginX = batch.getViewportX() + (matrix[12] * 0.5f + 0.5f) * width;
        pixelOriginY = batch.getViewportY() + (matrix[13] * 0.5f + 0.5f) * height;
        float pixels = size * sy;
        physicalEmSize = pixels;
        return pixels > 0 && pixels <= HINT_LIMIT ? Math.max(1, Math.round(pixels)) : 0;
    }

    /**
     * Paints glyphs in shape order, flushing only for shader changes or eviction.
     * Projection and shader restoration are handled without taking batch ownership.
     * @param batch active borrowed batch
     * @param run trusted retained indices, offsets and evictable image references
     * @param x baseline X
     * @param y baseline Y
     * @param radians run rotation
     * @param tint borrowed tint
     * @param ends trusted logical span boundaries, or null for uniform tint
     * @param colors borrowed span colors, or null for uniform tint
     * @param spanCount used prefix of the trusted style arrays
     */
    void drawRun(TextureBatch batch, AdaptiveTextRun run, float x, float y, float radians, Color tint, int[] ends, Color[] colors, int spanCount) {
        checkOwner();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(radians)) throw new IllegalArgumentException("Position and rotation must be finite");
        if (run.count == 0 || run.size == 0f) return;
        if (styler != null) run.ensureStyleContext(true);
        if (preparing) uploadPrepared(batch);
        if (outlineEnabled && outlineColor.a() > 0f) {
            float sin = radians == 0f ? 0f : (float) Math.sin(radians);
            float cos = radians == 0f ? 1f : (float) Math.cos(radians);
            for (int i = 0; i < outlineOffsets.length; i += 2) {
                float dx = outlineOffsets[i];
                float dy = outlineOffsets[i + 1];
                drawRunPass(batch, run, x, y, radians, tint, ends, colors, spanCount, true, dx * cos - dy * sin, dx * sin + dy * cos);
            }
        }
        drawRunPass(batch, run, x, y, radians, tint, ends, colors, spanCount, false, 0f, 0f);
    }

    /**
     * Submits one cached shaped run with its normal fill or the reusable outline tint.
     * Outline copies share glyph images, shader restoration and raster selection with
     * the fill; styles contribute alpha without replacing the configured outline RGB.
     *
     * @param batch active destination
     * @param run borrowed retained layout
     * @param x baseline X
     * @param y baseline Y
     * @param radians counterclockwise rotation
     * @param tint fill tint, or null for white
     * @param ends optional UTF-16 style boundaries
     * @param colors optional borrowed fill colors
     * @param spanCount used style count
     * @param outline whether this pass submits outline copies
     * @param offsetX world X offset applied after glyph styling
     * @param offsetY world Y offset applied after glyph styling
     */
    private void drawRunPass(TextureBatch batch, AdaptiveTextRun run, float x, float y, float radians, Color tint, int[] ends, Color[] colors, int spanCount, boolean outline, float offsetX, float offsetY) {
        int count = run.count;
        float size = run.size;
        FontStyler callback = styler;
        int raster = rasterSize(batch, size, radians);
        float pixelOffsetX = batch.getTranslationX() * pixelScaleX + pixelOriginX;
        float pixelOffsetY = batch.getTranslationY() * pixelScaleY + pixelOriginY;
        float inversePixelScaleX = raster == 0 ? 0 : 1 / pixelScaleX;
        float inversePixelScaleY = raster == 0 ? 0 : 1 / pixelScaleY;
        Shader previous = batch.getShader();
        boolean override = !batch.supportsDistanceFields();
        try {
            float sin = radians == 0 ? 0 : (float) Math.sin(radians);
            float cos = radians == 0 ? 1 : (float) Math.cos(radians);
            int imageKey = raster == 0 ? 0 : raster | hintingKey;
            boolean cached = run.imageRaster == imageKey && run.imageVersion == cacheVersion;
            if (run.images.length < count) run.images = new AtlasGlyph[run.glyphs.length];
            AtlasGlyph[] images = run.images;
            float[] xs = run.xs;
            float[] ys = run.ys;
            long version = cacheVersion;
            for (int i = 0; i < count; i++) {
                AtlasGlyph glyph = cached ? images[i] : glyph(batch, run.glyphs[i], raster);
                if (!cached) images[i] = glyph;
                Color drawTint = tint == null ? Color.WHITE : tint;
                if (ends != null) {
                    int unit = run.clusters[i];
                    int span = 0;
                    int limit = spanCount;
                    while (span < limit) {
                        int middle = (span + limit) >>> 1;
                        if (ends[middle] <= unit) span = middle + 1;
                        else limit = middle;
                    }
                    drawTint = colors[span] == null ? Color.WHITE : colors[span];
                }
                float localX = (xs[i] + glyph.left()) * size;
                float localY = (ys[i] + glyph.bottom()) * size;
                float drawX = x + localX;
                float drawY = y + localY;
                if (raster != 0) {
                    // Hinted images already contain pixel coverage. Fractional glyph origins
                    // filter that coverage a second time and blur even fully covered stems.
                    drawX = (Math.round(drawX * pixelScaleX + pixelOffsetX) - pixelOffsetX) * inversePixelScaleX;
                    drawY = (Math.round(drawY * pixelScaleY + pixelOffsetY) - pixelOffsetY) * inversePixelScaleY;
                }
                float width = glyph.width() * size;
                float height = glyph.height() * size;
                if (callback != null) {
                    int character = run.clusters[i];
                    glyphStyle.reset();
                    glyphContext.set(run.text.charAt(character), character, i, run.sourceLines[i], localX, localY, width, height, drawX, drawY);
                    callback.style(glyphStyle, glyphContext);
                    if (!glyphStyle.isVisible()) continue;
                    float scaleX = glyphStyle.getScaleX();
                    float scaleY = glyphStyle.getScaleY();
                    if (scaleX != 1 || scaleY != 1) {
                        if (raster != 0) glyph = glyph(batch, run.glyphs[i], 0);
                        localX = (xs[i] + glyph.left()) * size;
                        localY = (ys[i] + glyph.bottom()) * size;
                        drawX = x + localX;
                        drawY = y + localY;
                        width = glyph.width() * size * scaleX;
                        height = glyph.height() * size * scaleY;
                    }
                    drawX += glyphStyle.getOffsetX();
                    drawY += glyphStyle.getOffsetY();
                    if (glyphStyle.hasColor()) drawTint = glyphStyle.getColor();
                }
                if (glyph.page() == null) continue;
                if (override) {
                    // A styled scale or provisional image can change the atlas format.
                    Shader selected = glyph.page().getDistanceFieldRange() <= 0 ? previous : batch.getDefaultShader();
                    if (batch.getShader() != selected) batch.setShader(selected);
                }
                Color submissionTint = drawTint;
                if (outline) {
                    outlineTint.set(outlineColor);
                    outlineTint.a(outlineColor.a() * drawTint.a());
                    submissionTint = outlineTint;
                }
                batch.drawUV(glyph.page(), drawX + offsetX, drawY + offsetY, width, height, glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1(), x - drawX, y - drawY, sin, cos, submissionTint);
            }
            run.imageRaster = imageKey;
            run.imageVersion = version == cacheVersion ? version : -1;
        } finally {
            if (override && batch.getShader() != previous) batch.setShader(previous);
        }
    }

    /**
     * Looks up or uploads one glyph in a bounded primitive-key cache. Zero raster
     * size identifies the shared MSDF variant; positive sizes identify hinted ones.
     * @param batch active batch to flush before eviction
     * @param id shaped glyph index
     * @param raster physical hinted em size, zero for MSDF
     * @return cached atlas image or empty-outline entry
     */
    private AtlasGlyph glyph(TextureBatch batch, int id, int raster) {
        long key = glyphKey(id, raster);
        AtlasGlyph cached = findGlyph(key);
        if ((cached == null || cached.provisional()) && raster != 0) {
            ensureCacheSpace(batch, 1);
            FontRaster image = face.bitmap(id, raster, hinting);
            try {
                AtlasGlyph glyph = uploadGlyph(batch, image, false, false);
                putGlyph(key, glyph);
                if (cached != null) cacheVersion++;
                if (glyph.page() != null) requestGlyph(id, 0);
                return glyph;
            } finally {
                if (image.pixels() != null) memFree(image.pixels());
            }
        }
        if (cached != null) {
            if (cached.provisional()) requestGlyph(id, raster);
            return cached;
        }
        ensureCacheSpace(batch, 2);
        AtlasGlyph fallback = fallbackGlyph(batch, id);
        putGlyph(key, fallback);
        if (fallback.page() != null) requestGlyph(id, 0);
        return fallback;
    }

    /**
     * Returns coverage at a useful displayed resolution while MSDF prepares.
     * Its reserved key is shared across resizing; native work is bounded by
     * MAX_FALLBACK_SIZE rather than enlarging a fixed low-resolution image.
     * @param batch active destination
     * @param id shaped glyph index
     * @return cached provisional coverage or empty-outline entry
     */
    private AtlasGlyph fallbackGlyph(TextureBatch batch, int id) {
        long key = (((long) id << 32) | FALLBACK_RASTER) + 1;
        AtlasGlyph cached = findGlyph(key);
        if (cached != null) return cached;
        int pixels = physicalEmSize > 0 ? Math.max(FALLBACK_SIZE, Math.min(MAX_FALLBACK_SIZE, Math.round(physicalEmSize))) : FALLBACK_SIZE;
        FontRaster image = face.bitmap(id, pixels, hinting);
        try {
            AtlasGlyph glyph = uploadGlyph(batch, image, false, image.pixels() != null);
            putGlyph(key, glyph);
            return glyph;
        } finally {
            if (image.pixels() != null) memFree(image.pixels());
        }
    }

    /**
     * Uploads a small group of ready images without waiting for CPU generation.
     * Changed cache versions invalidate retained provisional image references.
     * @param batch active destination to flush safely if atlas eviction is needed
     */
    private void uploadPrepared(TextureBatch batch) {
        long now = System.nanoTime();
        if (now - nextUpload < 0) return;
        nextUpload = now + UPLOAD_INTERVAL;
        for (int i = 0; i < MAX_UPLOADS; i++) {
            FontGlyphResult result = preparation.poll();
            if (result == null) break;
            FontRaster image = result.image();
            try {
                ensureCacheSpace(batch, 1);
                AtlasGlyph glyph = uploadGlyph(batch, image, (int) (result.key() - 1) == 0, false);
                putGlyph(result.key(), glyph);
                cacheVersion++;
            } finally {
                if (image.pixels() != null) memFree(image.pixels());
            }
            if (System.nanoTime() - now >= UPLOAD_BUDGET) break;
        }
        preparing = preparation.hasPending();
    }

    /**
     * Requests a missing prepared variant without duplicating admitted work.
     * @param id shaped glyph index
     * @param raster requested coverage size or zero for MSDF
     */
    private void requestGlyph(int id, int raster) {
        long key = glyphKey(id, raster);
        AtlasGlyph cached = findGlyph(key);
        if (cached != null && !cached.provisional()) return;
        preparing |= preparation.request(key, id, raster, hinting, true);
    }

    /**
     * Packs a glyph and its size/policy variant without colliding with MSDF keys.
     * @param id shaped glyph index
     * @param raster physical coverage size or zero for MSDF
     * @return nonzero cache key
     */
    private long glyphKey(int id, int raster) {
        int imageKey = raster == 0 ? 0 : raster | hintingKey;
        return (((long) id << 32) | imageKey) + 1;
    }

    /**
     * Finds a prepared or provisional atlas entry without allocation.
     * @param key nonzero packed cache key
     * @return borrowed entry, or null if absent
     */
    private AtlasGlyph findGlyph(long key) {
        int slot = cacheSlot(key) & (keys.length - 1);
        while (keys[slot] != 0) {
            if (keys[slot] == key) return glyphs[slot];
            slot = (slot + 1) & (keys.length - 1);
        }
        return null;
    }

    /**
     * Installs an entry after capacity and image ownership have been established.
     * @param key nonzero packed cache key
     * @param glyph immutable atlas entry
     */
    private void putGlyph(long key, AtlasGlyph glyph) {
        int slot = cacheSlot(key) & (keys.length - 1);
        while (keys[slot] != 0 && keys[slot] != key) slot = (slot + 1) & (keys.length - 1);
        if (keys[slot] == 0) entries++;
        keys[slot] = key;
        glyphs[slot] = glyph;
    }

    /**
     * Reserves primitive table space before uploads can create borrowed entries.
     * @param batch active destination to flush before clearing GPU pages
     * @param count maximum additional entries required by the operation
     */
    private void ensureCacheSpace(TextureBatch batch, int count) {
        if (entries + count <= keys.length / 2) return;
        if (keys.length < TABLE_SIZE) growCache();
        else {
            batch.flush();
            clearCache();
        }
    }

    /**
     * Places an already generated image on an atlas; the caller frees its pixels.
     * @param batch active destination to flush before page eviction
     * @param image prepared CPU image
     * @param distance whether pixels encode an MSDF
     * @param provisional whether this entry is a temporary scaled coverage image
     * @return immutable atlas entry
     */
    private AtlasGlyph uploadGlyph(TextureBatch batch, FontRaster image, boolean distance, boolean provisional) {
        FontAtlasPage page = null;
        if (image.pixels() != null) {
            for (int i = 0; i < pageCount; i++) {
                if ((pages[i].getDistanceFieldRange() > 0) != distance) continue;
                if (!pages[i].reserve(image.width(), image.height())) continue;
                page = pages[i];
                break;
            }
            if (page == null) {
                if (pageCount == MAX_PAGES) {
                    batch.flush();
                    clearCache();
                }
                int needed = Math.max(image.width(), image.height()) + 2;
                if (needed > FontAtlasPage.MAX_SIDE) throw new IllegalArgumentException("Glyph exceeds atlas dimensions");
                int side = Math.max(distance ? FontAtlasPage.DISTANCE_SIDE : FontAtlasPage.COVERAGE_SIDE, Integer.highestOneBit(needed - 1) << 1);
                page = new FontAtlasPage(distance, side);
                pages[pageCount++] = page;
                atlasBytes += page.storageBytes();
                if (!page.reserve(image.width(), image.height())) throw new IllegalArgumentException("Glyph exceeds atlas dimensions");
            }
            page.upload(image);
        }
        float u = page == null ? 0 : page.imageX() / (float) page.getWidth();
        float v = page == null ? 0 : page.imageY() / (float) page.getHeight();
        return new AtlasGlyph(page, image.width() / image.scale(), image.height() / image.scale(), image.left(), image.bottom(), u, v, page == null ? 0 : u + image.width() / (float) page.getWidth(), page == null ? 0 : v + image.height() / (float) page.getHeight(), provisional);
    }
    /**
     * Mixes glyph/raster keys or source hashes before masking a cache table.
     * @param key packed glyph/raster key or sign-extended string hash
     * @return mixed hash masked by the current table capacity at the call site
     */
    private static int cacheSlot(long key) {
        key = (key ^ (key >>> 33)) * 0xff51afd7ed558ccdL;
        key = (key ^ (key >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return (int) (key ^ (key >>> 33));
    }

    /**
     * Doubles the sparse glyph table only as Unicode and size variants demand it.
     * Existing entries and retained image references remain valid during rehashing.
     */
    private void growCache() {
        long[] oldKeys = keys;
        AtlasGlyph[] oldGlyphs = glyphs;
        int capacity = oldKeys.length * 2;
        keys = new long[capacity];
        glyphs = new AtlasGlyph[capacity];
        for (int i = 0; i < oldKeys.length; i++) {
            long key = oldKeys[i];
            if (key == 0) continue;
            int slot = cacheSlot(key) & (capacity - 1);
            while (keys[slot] != 0) slot = (slot + 1) & (capacity - 1);
            keys[slot] = key;
            glyphs[slot] = oldGlyphs[i];
        }
    }

    /**
     * Releases evictable pages and entries after their queued drawing is flushed.
     */
    private void clearCache() {
        for (int i = 0; i < pageCount; i++) {
            pages[i].dispose();
            pages[i] = null;
        }
        pageCount = 0;
        atlasBytes = 0;
        cacheVersion++;
        entries = 0;
        Arrays.fill(keys, 0);
        Arrays.fill(glyphs, null);
    }

    @Override
    public boolean isDisposed() { return disposed; }

    @Override
    public void dispose() {
        if (disposed) return;
        checkOwner();
        clearCache();
        try {
            preparation.close();
        } finally {
            try {
                face.close();
            } finally {
                Arrays.fill(layouts, null);
                scratch = null;
                keys = null;
                glyphs = null;
                disposed = true;
            }
        }
    }
}
