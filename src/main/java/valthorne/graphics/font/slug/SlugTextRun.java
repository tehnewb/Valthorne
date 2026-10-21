package valthorne.graphics.font.slug;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;

import java.util.Arrays;

/**
 * Reusable pre-laid-out Slug text.
 *
 * <p>Use this for fair live-rendering benchmarks and for text that is drawn repeatedly. The run
 * removes per-frame layout, newline handling, tab handling, and kerning lookups. It still renders
 * Slug coverage with its original outlines. TextureBatch may reuse exact glyph
 * coverage and packed run geometry at unchanged sampling positions; this run
 * itself owns no GPU textures or batch-specific cached state.</p>
 *
 * <pre>{@code
 * SlugTextRun run = font.createRun("The quick brown fox", 36f);
 *
 * batch.begin(); // The ordinary TextureBatch owns the live curve renderer.
 * run.draw(batch, 40f, 180f, Color.WHITE);
 * batch.end();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public final class SlugTextRun {

    private final SlugFont font; // Font that owns the glyph data.
    private SlugGlyph[] glyphs; // Drawable glyphs in draw order.
    private float[] xOffsets; // Baseline x offsets in world units.
    private float[] yOffsets; // Baseline y offsets in world units.
    private int count; // Number of drawable glyphs.
    private String text; // Source text.
    private float size; // World units per em.
    private long revision; // Increments whenever layout geometry is rebuilt.
    private float width; // Measured width.
    private float height; // Measured height.
    private float minX; // Minimum outline X relative to the run baseline.
    private float minY; // Minimum outline Y relative to the run baseline.
    private float maxX; // Maximum outline X relative to the run baseline.
    private float maxY; // Maximum outline Y relative to the run baseline.

    /**
     * Creates a reusable layout for the supplied font and immediately builds its
     * glyph positions. The font is borrowed and must remain usable while the run is
     * drawn; this object does not allocate or own font textures.
     *
     * @param font font supplying metrics and glyph data
     * @param text source text, with null treated as empty
     * @param size finite, nonnegative world units per em
     * @throws NullPointerException if font is null
     */
    SlugTextRun(SlugFont font, String text, float size) {
        if (font == null)
            throw new NullPointerException("font");
        this.font = font;
        rebuild(text, size);
    }

    /**
     * Rebuilds this run for new text or size.
     *
     * @param text text to layout
     * @param size world units per em
     */
    public void rebuild(String text, float size) {
        if (!Float.isFinite(size) || size < 0)
            throw new IllegalArgumentException("Size must be finite and nonnegative");
        String normalized = text == null ? "" : text;
        if (glyphs != null && this.size == size && normalized.equals(this.text)) return;
        revision++;
        this.text = text == null ? "" : text;
        this.size = size;

        int capacity = Math.max(8, this.text.length());
        int oldCount = count;
        if (glyphs == null) {
            glyphs = new SlugGlyph[capacity];
            xOffsets = new float[capacity];
            yOffsets = new float[capacity];
        } else ensureCapacity(capacity);
        count = 0;
        minX = minY = Float.POSITIVE_INFINITY;
        maxX = maxY = Float.NEGATIVE_INFINITY;

        SlugShape shape = font.shape(this.text);
        for (int index = 0; index < shape.glyphCount(); index++) {
            SlugGlyph glyph = font.glyphByIndex(shape.glyphIndex(index));
            if (!glyph.drawable) continue;
            ensureCapacity(count + 1);
            float offsetX = shape.x(index) * size;
            float offsetY = shape.y(index) * size;
            glyphs[count] = glyph;
            xOffsets[count] = offsetX;
            yOffsets[count] = offsetY;
            minX = Math.min(minX, offsetX + glyph.x0 * size);
            minY = Math.min(minY, offsetY + glyph.y0 * size);
            maxX = Math.max(maxX, offsetX + glyph.x1 * size);
            maxY = Math.max(maxY, offsetY + glyph.y1 * size);
            count++;
        }
        width = shape.width() * size;
        height = shape.height() * size;
        if (count < oldCount) Arrays.fill(glyphs, count, oldCount, null);
    }

    /**
     * Grows the three parallel glyph-layout arrays together while preserving entries.
     * Capacity doubles unless the requested minimum is larger; an already sufficient
     * capacity avoids allocation and leaves the drawable count unchanged.
     *
     * @param required minimum number of glyph slots
     */
    private void ensureCapacity(int required) {
        if (required <= glyphs.length) {
            return;
        }
        int next = Math.max(required, glyphs.length * 2);
        glyphs = Arrays.copyOf(glyphs, next);
        xOffsets = Arrays.copyOf(xOffsets, next);
        yOffsets = Arrays.copyOf(yOffsets, next);
    }

    /**
     * Queues this retained layout in an ordinary texture batch. Glyphs remain
     * resolution independent and inherit batch translation, clipping and opacity.
     * Warm submissions reuse layout arrays; the batch owns any coverage cache.
     *
     * @param batch active destination texture batch
     * @param x baseline X before batch translation
     * @param y baseline Y before batch translation
     * @param color copied tint, or null for the batch color
     */
    public void draw(TextureBatch batch, float x, float y, Color color) {
        if (batch == null) throw new NullPointerException("batch");
        batch.draw(this, x, y, color);
    }

    /**
     * Returns the source text.
     *
     * @return source text
     */
    public String text() {
        return text;
    }

    /**
     * Returns the layout generation used to invalidate retained drawing geometry.
     * Rebuilding unchanged text at the same size leaves this value unchanged.
     *
     * @return generation incremented whenever glyph offsets are rebuilt
     */
    public long revision() {
        return revision;
    }

    /**
     * Checks whether reusing this immediate layout would retain reasonably sized
     * staging arrays. Large-to-small replacements instead release excess capacity.
     *
     * @param characters replacement source length within the font's cache limit
     * @return true when retained glyph capacity is at most twice the requested size
     */
    boolean canReuse(int characters) {
        return glyphs.length <= Math.max(8, characters) * 2;
    }

    /**
     * Returns the run size in world units per em.
     *
     * @return size
     */
    public float size() {
        return size;
    }

    /**
     * Returns the measured width.
     *
     * @return width
     */
    public float width() {
        return width;
    }

    /**
     * Returns the measured height.
     *
     * @return height
     */
    public float height() {
        return height;
    }

    /**
     * Returns the number of drawable glyphs in this run.
     *
     * @return drawable glyph count
     */
    public int glyphCount() {
        return count;
    }

    /**
     * Returns the borrowed font whose GPU outlines must outlive queued draws.
     *
     * @return layout font
     */
    public SlugFont font() { return font; }

    /**
     * Returns one drawable glyph in retained submission order.
     *
     * @param index glyph index from zero through glyphCount minus one
     * @return immutable outline metadata
     */
    public SlugGlyph glyph(int index) { return glyphs[index]; }

    /**
     * Returns a glyph's horizontal baseline offset from the run origin.
     *
     * @param index glyph index from zero through glyphCount minus one
     * @return offset in world units
     */
    public float xOffset(int index) { return xOffsets[index]; }

    /**
     * Returns a glyph's vertical baseline offset, including line spacing.
     *
     * @param index glyph index from zero through glyphCount minus one
     * @return offset in world units
     */
    public float yOffset(int index) { return yOffsets[index]; }

    /**
     * Tests retained ink bounds against a rectangle without iterating glyphs.
     *
     * @param x translated baseline X
     * @param y translated baseline Y
     * @param left rectangle minimum X
     * @param bottom rectangle minimum Y
     * @param right rectangle maximum X
     * @param top rectangle maximum Y
     * @return whether a nonempty run overlaps the rectangle
     */
    public boolean intersects(float x, float y, float left, float bottom, float right, float top) {
        return count != 0 && x + maxX > left && y + maxY > bottom && x + minX < right && y + minY < top;
    }
}
