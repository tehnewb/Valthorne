package valthorne.graphics.font.adaptive;

import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.TextFont;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.texture.TextureBatch;

import java.util.Arrays;

import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * One font API for naturally spaced, hinted small text and scalable MSDF text.
 * HarfBuzz always shapes the same design-unit font; raster choice never changes
 * advances, kerning, substitutions or line metrics. Axis-aligned text up to 48
 * physical pixels per em uses grayscale FreeType coverage with configurable
 * {@link FontHinting}; larger, rotated,
 * perspective or anisotropically scaled text uses linear MSDF atlases.
 *
 * <p>Create from CPU-only {@link AdaptiveFontData} on the graphics thread. Native
 * shaping and coverage faces are created immediately; the additional MSDF face
 * and atlas pages are lazy. The font owns these resources and borrows the batch's
 * standard shader. Borrowed retained runs need no disposal. Call {@link #dispose()}
 * after ending batches that reference the font. Access is confined to the
 * constructing thread, including measurement and disposal.</p>
 *
 * <p>Coverage pages use R8 at 256 square; MSDF pages use RGB8, or RGBA8 when required by the backend, at 512 square.
 * Oversized glyphs may use pages up to 1024 square. At most eight pages and 8192
 * glyph entries bound storage to 32 MiB of GPU texel data plus retained recovery pixels in the worst case. The
 * sparse glyph table grows on demand. Full caches flush the batch before page
 * eviction. Retained runs validate cached image references against an eviction
 * version and remain usable after eviction. A warm
 * retained draw allocates no per-glyph objects. Direct drawing caches 256 short
 * layouts; use retained runs for large documents or predictable cache reuse. Prewarming
 * is deferred until first draw and capped at 256 nominal characters so broad
 * Unicode ranges cannot cause unbounded startup work. All other glyphs load on
 * demand, including glyphs introduced by shaping. Consecutive sprites, coverage
 * text and MSDF text share the standard batch without per-label flushes.
 * Participating batch shaders retain their transforms and fragment effects.
 * MSDF drawing under a shader without distance-field support temporarily selects
 * the standard shader and restores the custom shader afterward.</p>
 *
 * <pre>{@code
 * AdaptiveFont font = AdaptiveFontData.load("Cinzel-Regular.otf", 20).asFont();
 * AdaptiveTextRun title = font.createRun("Valthorne", 96);
 * batch.begin();
 * font.draw(batch, "Inventory", 24, 100, 20, Color.WHITE);
 * title.draw(batch, 24, 200, Color.WHITE);
 * batch.end();
 * font.dispose();
 * }</pre>
 */
public final class AdaptiveFont implements TextFont {
    /**
     * Largest physical em size using grid-fitted bitmap coverage.
     */
    private static final int HINT_LIMIT = 48;
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
     * Unused MSDF border cropped only at one or more physical pixels per atlas texel.
     * At least two physical pixels of transparent antialiasing guard remain.
     */
    private static final int MSDF_QUAD_INSET = 3;
    private final Thread owner = Thread.currentThread(); // Native and graphics owner thread.
    private final AdaptiveNativeFace face; // Owned native backends and encoded source memory.
    private final FontHinting hinting; // Immutable small-text raster policy shared by all cached coverage glyphs.
    private AdaptiveTextRun scratch = new AdaptiveTextRun(this); // Owned long-text scratch; released on disposal.
    private final AdaptiveTextRun[] layouts = new AdaptiveTextRun[LAYOUT_CACHE_CAPACITY]; // Bounded cache; each run owns its source key.
    private int layoutVictim; // Round-robin victim selected within a full cache group.
    private long[] keys = new long[INITIAL_CACHE_CAPACITY]; // Growing primitive keys; zero is unused, null after disposal.
    private AtlasGlyph[] glyphs = new AtlasGlyph[INITIAL_CACHE_CAPACITY]; // Entries parallel to keys; null after disposal.
    private final FontAtlasPage[] pages = new FontAtlasPage[MAX_PAGES]; // Owned bounded GPU pages.
    private final int initialSize; // Initial logical em size for prewarming and short draw overload.
    private final int firstCharacter; // First nominal prewarm codepoint.
    private final int prewarmCount; // Bounded startup work from supplied character range.
    private final float ascent; // Normalized typographic ascent.
    private final float descent; // Normalized typographic descent, normally negative.
    private final float lineHeight; // Normalized baseline spacing.
    private final float spaceAdvance; // Normalized nominal space advance for tabs.
    private int pageCount; // Active prefix of atlas pages.
    private int entries; // Current cache occupancy.
    private long cacheVersion; // Changes whenever atlas eviction invalidates retained image references.
    private long atlasBytes; // Exact allocated atlas texel storage, excluding driver bookkeeping.
    private float pixelScaleX; // Horizontal physical pixels per logical unit for the current hinted draw.
    private float pixelScaleY; // Vertical physical pixels per logical unit for the current hinted draw.
    private float pixelOriginX; // Viewport/projection horizontal physical origin.
    private float pixelOriginY; // Viewport/projection vertical physical origin.
    private float physicalEmSize; // Current uniform axis-aligned physical em size; zero disables border trimming.
    private boolean warmed; // Whether bounded nominal prewarming completed.
    private boolean disposed; // Prevents native access after release.

    /**
     * Creates native font resources using NORMAL hinting, requiring no graphics device
     * until first drawing.
     * @param data immutable encoded asset
     */
    public AdaptiveFont(AdaptiveFontData data) {
        this(data, FontHinting.NORMAL);
    }

    /**
     * Creates native font resources with an immutable coverage hinting policy.
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
        initialSize = data.fontSize();
        firstCharacter = data.firstCharacterIndex();
        prewarmCount = Math.min(data.characterCount(), 256);
        face = new AdaptiveNativeFace(data.bytes());
        ascent = face.ascent();
        descent = face.descent();
        lineHeight = face.lineHeight();
        spaceAdvance = face.spaceAdvance();
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
     * Returns this font's immutable small-text raster policy.
     * @return selected hinting mode, NORMAL for the default constructor
     */
    public FontHinting hinting() { return hinting; }

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
     * Draws at the asset's initial size with the standard shared layout.
     * @param batch active destination
     * @param text source
     * @param x baseline X
     * @param y baseline Y
     * @param tint borrowed tint, null for white
     */
    public void draw(TextureBatch batch, String text, float x, float y, Color tint) {
        draw(batch, text, x, y, initialSize, tint);
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
        if (radians != 0 || batch.transformsText() || matrix[1] != 0 || matrix[4] != 0 || matrix[3] != 0 || matrix[7] != 0 || matrix[15] != 1 || matrix[0] <= 0 || matrix[5] <= 0) return 0;
        int width = batch.getViewportWidth();
        int height = batch.getViewportHeight();
        float sx = Math.abs(matrix[0]) * width * 0.5f;
        float sy = Math.abs(matrix[5]) * height * 0.5f;
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
        int count = run.count;
        float size = run.size;
        if (count == 0 || size == 0) return;
        int raster = rasterSize(batch, size, radians);
        if (raster != 0) {
            float screenX = (x + batch.getTranslationX()) * pixelScaleX + pixelOriginX;
            float screenY = (y + batch.getTranslationY()) * pixelScaleY + pixelOriginY;
            x += (Math.round(screenX) - screenX) / pixelScaleX;
            y += (Math.round(screenY) - screenY) / pixelScaleY;
        }
        Shader previous = batch.getShader();
        boolean override = raster == 0 && !batch.supportsDistanceFields();
        try {
            if (override) batch.setShader(batch.getDefaultShader());
            if (!warmed) {
                int warmSize = initialSize <= HINT_LIMIT ? initialSize : 0;
                for (int i = 0; i < prewarmCount; i++) {
                    int index = face.nominalGlyph(firstCharacter + i);
                    if (index != 0) glyph(batch, index, warmSize);
                }
                warmed = true;
            }
            float sin = radians == 0 ? 0 : (float) Math.sin(radians);
            float cos = radians == 0 ? 1 : (float) Math.cos(radians);
            boolean cached = run.imageRaster == raster && run.imageVersion == cacheVersion;
            if (run.images.length < count) run.images = new AtlasGlyph[run.glyphs.length];
            AtlasGlyph[] images = run.images;
            float[] xs = run.xs;
            float[] ys = run.ys;
            Color drawTint = tint == null ? Color.WHITE : tint;
            boolean trim = raster == 0 && physicalEmSize >= AdaptiveNativeFace.MSDF_EM;
            float inset = trim ? size * (MSDF_QUAD_INSET / (float) AdaptiveNativeFace.MSDF_EM) : 0;
            long version = cacheVersion;
            for (int i = 0; i < count; i++) {
                AtlasGlyph glyph = cached ? images[i] : glyph(batch, run.glyphs[i], raster);
                if (!cached) images[i] = glyph;
                if (glyph.page() == null) continue;
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
                if (trim) {
                    // Skip provably transparent border fragments without reducing the stored distance range.
                    localX += inset;
                    localY += inset;
                    batch.drawUV(glyph.page(), x + localX, y + localY, glyph.width() * size - 2 * inset, glyph.height() * size - 2 * inset, glyph.u0() + glyph.insetU(), glyph.v0() + glyph.insetV(), glyph.u1() - glyph.insetU(), glyph.v1() - glyph.insetV(), -localX, -localY, sin, cos, drawTint);
                } else batch.drawUV(glyph.page(), x + localX, y + localY, glyph.width() * size, glyph.height() * size, glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1(), -localX, -localY, sin, cos, drawTint);
            }
            run.imageRaster = raster;
            run.imageVersion = version == cacheVersion ? version : -1;
        } finally {
            if (override) batch.setShader(previous);
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
        long key = (((long) id << 6) | raster) + 1;
        int slot = cacheSlot(key) & (keys.length - 1);
        while (keys[slot] != 0) {
            if (keys[slot] == key) return glyphs[slot];
            slot = (slot + 1) & (keys.length - 1);
        }
        if (entries >= keys.length / 2) {
            if (keys.length < TABLE_SIZE) growCache();
            else {
                batch.flush();
                clearCache();
            }
            return glyph(batch, id, raster);
        }
        FontRaster image = raster == 0 ? face.msdf(id) : face.bitmap(id, raster, hinting);
        try {
            FontAtlasPage page = null;
            if (image.pixels() != null) {
                for (int i = 0; i < pageCount; i++) {
                    if ((pages[i].getDistanceFieldRange() > 0) != (raster == 0)) continue;
                    if (!pages[i].reserve(image.width(), image.height())) continue;
                    page = pages[i];
                    break;
                }
                if (page == null) {
                    if (pageCount == MAX_PAGES) {
                        batch.flush();
                        clearCache();
                        slot = cacheSlot(key) & (keys.length - 1);
                    }
                    int needed = Math.max(image.width(), image.height()) + 2;
                    if (needed > FontAtlasPage.MAX_SIDE) throw new IllegalArgumentException("Glyph exceeds atlas dimensions");
                    int side = Math.max(raster == 0 ? FontAtlasPage.DISTANCE_SIDE : FontAtlasPage.COVERAGE_SIDE, Integer.highestOneBit(needed - 1) << 1);
                    page = new FontAtlasPage(raster == 0, side);
                    pages[pageCount++] = page;
                    atlasBytes += page.storageBytes();
                    if (!page.reserve(image.width(), image.height())) throw new IllegalArgumentException("Glyph exceeds atlas dimensions");
                }
                page.upload(image);
            }
            float u = page == null ? 0 : page.imageX() / (float) page.getWidth();
            float v = page == null ? 0 : page.imageY() / (float) page.getHeight();
            AtlasGlyph glyph = new AtlasGlyph(page, image.width() / image.scale(), image.height() / image.scale(), image.left(), image.bottom(), u, v, page == null ? 0 : u + image.width() / (float) page.getWidth(), page == null ? 0 : v + image.height() / (float) page.getHeight(), page == null ? 0 : MSDF_QUAD_INSET / (float) page.getWidth(), page == null ? 0 : MSDF_QUAD_INSET / (float) page.getHeight());
            keys[slot] = key;
            glyphs[slot] = glyph;
            entries++;
            return glyph;
        } finally {
            if (image.pixels() != null) memFree(image.pixels());
        }
    }

    /**
     * Mixes glyph/raster keys or source hashes before masking a cache table.
     * @param key packed glyph/raster key or sign-extended string hash
     * @return mixed hash masked by the current table capacity at the call site
     */
    private static int cacheSlot(long key) {
        /*
         * An avalanche avoids clustering glyph size bits and similar numbered labels.
         */
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
