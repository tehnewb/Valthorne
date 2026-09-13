package valthorne.graphics.font.slug;

import valthorne.graphics.Color;

import java.util.Arrays;

/**
 * Reusable pre-laid-out Slug text.
 *
 * <p>Use this for fair live-rendering benchmarks and for text that is drawn repeatedly. The run
 * removes per-frame layout, newline handling, tab handling, and kerning lookups. It still renders
 * live Slug curves every frame; it does not cache text into a texture.</p>
 *
 * <pre>{@code
 * SlugTextRun run = font.createRun("The quick brown fox", 36f);
 *
 * batch.begin(projection, width, height);
 * run.draw(batch, 40f, 180f, Color.WHITE);
 * batch.end();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public final class SlugTextRun {

    private SlugFont font; // Font that owns the glyph data.
    private SlugGlyph[] glyphs; // Drawable glyphs in draw order.
    private float[] xOffsets; // Baseline x offsets in world units.
    private float[] yOffsets; // Baseline y offsets in world units.
    private int count; // Number of drawable glyphs.
    private String text; // Source text.
    private float size; // World units per em.
    private float width; // Measured width.
    private float height; // Measured height.
    private float minX, minY, maxX, maxY; // Drawable outline bounds relative to the run baseline, used for whole-run rejection.

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
        if (font == null) {
            throw new NullPointerException("font");
        }
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
        if (!Float.isFinite(size) || size < 0) throw new IllegalArgumentException("Size must be finite and nonnegative");
        String normalized = text == null ? "" : text;
        if (glyphs != null && this.size == size && normalized.equals(this.text)) return;
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

        float penX = 0f;
        float penY = 0f;
        float lineWidth = 0f;
        float maxWidth = 0f;
        int previous = 0;
        int lines = 1;

        for (int i = 0; i < this.text.length(); i++) {
            char c = this.text.charAt(i);

            if (c == '\n') {
                maxWidth = Math.max(maxWidth, lineWidth);
                lineWidth = 0f;
                penX = 0f;
                penY -= font.lineHeight() * size;
                previous = 0;
                lines++;
                continue;
            }

            if (c == '\t') {
                SlugGlyph space = font.glyph(' ');
                float tab = (space != null ? space.advance : 0.25f) * 4f * size;
                penX += tab;
                lineWidth += tab;
                previous = 0;
                continue;
            }

            SlugGlyph glyph = font.glyph(c);
            if (glyph == null) {
                float fallback = 0.25f * size;
                penX += fallback;
                lineWidth += fallback;
                previous = 0;
                continue;
            }

            if (previous != 0) {
                float kern = font.kerning(previous, c) * size;
                penX += kern;
                lineWidth += kern;
            }

            if (glyph.drawable) {
                ensureCapacity(count + 1);
                glyphs[count] = glyph;
                xOffsets[count] = penX;
                yOffsets[count] = penY;
                minX = Math.min(minX, penX + glyph.x0 * size);
                minY = Math.min(minY, penY + glyph.y0 * size);
                maxX = Math.max(maxX, penX + glyph.x1 * size);
                maxY = Math.max(maxY, penY + glyph.y1 * size);
                count++;
            }

            float advance = glyph.advance * size;
            penX += advance;
            lineWidth += advance;
            previous = c;
        }

        width = Math.max(maxWidth, lineWidth);
        height = this.text.isEmpty() ? 0 : lines * font.lineHeight() * size;
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
     * Draws this pre-laid-out run.
     *
     * @param batch batch that receives glyph instances
     * @param x     baseline x position
     * @param y     baseline y position
     * @param color text color
     */
    public void draw(SlugBatch batch, float x, float y, Color color) {
        if (batch == null) {
            throw new NullPointerException("batch");
        }
        if (color == null || color.a() <= 0f || size == 0f) {
            return;
        }
        batch.requireDrawing();
        if (font.curveTexture() == 0) throw new IllegalStateException("Slug font is disposed");
        if (count == 0 || !batch.intersects(x + minX, y + minY, x + maxX, y + maxY)) return;
        for (int i = 0; i < count; i++) {
            batch.drawGlyph(font, glyphs[i], x + xOffsets[i], y + yOffsets[i], size, color);
        }
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
}
