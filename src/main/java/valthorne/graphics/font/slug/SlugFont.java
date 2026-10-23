package valthorne.graphics.font.slug;

import valthorne.graphics.Color;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxSlugFont;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.geometry.Dimensional;

/**
 * Owns GPU outline data and font metrics for a contiguous character range,
 * using Slug-style banded curve evaluation. Loading compiles glyph outlines into
 * quadratic segments and horizontal/vertical curve lookup bands. Kerning pairs
 * are cached on demand. Normal construction uploads RGBA32F curve and RG32UI
 * band textures on the graphics thread; deferred construction retains CPU staging
 * until the first binding and supports measurement without a live device.
 * <p>
 * Text drawing and measurement iterate UTF-16 characters, recognize newline and
 * tab, and use a quarter-em advance for characters outside the compiled range.
 * They do not perform script shaping or surrogate-pair composition. Cubic outlines
 * are approximated by short line sequences; small segments are omitted according
 * to the configured thresholds.
 * </p>
 * <p>
 * The font retains backing bytes for STB fallback kerning queries and owns its two
 * GPU textures. Dispose uploaded resources on the graphics thread after drawing
 * completes. Device replacement restores uploaded textures from retained resource
 * data. Measurement data remains stored after disposal, but drawing has no valid
 * texture resources. Optional retained text, baseline position, size, and color
 * support Dimensional usage; explicit width/height setters only override measured
 * metadata and do not scale rendered glyphs.
 * </p>
 * <p>
 * Draw directly into an active TextureBatch for live curve coverage at any zoom.
 * That batch owns the glyph renderer and preserves mixed sprite/text order; callers
 * only manage their existing TextureBatch. No bitmap atlases are baked or retained.
 * Reusable SlugTextRun layouts avoid repeated layout work while retaining live curves.
 * </p>
 * <pre>{@code
 * SlugFont font = SlugData.load("assets/font.ttf").asFont();
 * try {
 *     font.setText("Hello").setSize(32);
 *     font.setPosition(20, 60);
 *     font.draw(batch); // Inside the caller's TextureBatch drawing lifecycle.
 * } finally {
 *     font.dispose();
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public final class SlugFont implements Dimensional {
    private SlugOutlines pendingOutlines; // CPU outline staging retained until the first device upload.
    private boolean disposed; // Explicit lifetime state, including fonts that never reached the GPU.
    private BgfxSlugFont bgfxFont; // Owned bgfx curve/band textures.

    private SlugTextRun retainedRun; // Reusable convenience-draw layout; separate runs are required for independent retained text.

    private final SlugMetrics metrics; // Shared layout state without retaining temporary texture upload buffers.
    private final float ascent; // Font ascent in em units.
    private final float descent; // Font descent in em units.
    private final float lineHeight; // Line height in em units.
    private final float fallbackAdvance; // Advance used when a glyph is not present.
    private final Color color = new Color(1f, 1f, 1f, 1f); // Optional retained color for Font-like usage.
    private String text = ""; // Optional retained text for Font-like usage.
    private float x; // Optional retained x position for Font-like usage.
    private float y; // Optional retained y position for Font-like usage.
    private float size = 32f; // Optional retained size in world units per em.
    private float width; // Cached measured width of retained text.
    private float height; // Cached measured height of retained text.

    /**
     * Creates a font from immutable encoded data on the thread owning the current
     * graphics device. Copies bytes into retained direct storage, initializes STB,
     * compiles glyphs, initializes the lazy kerning cache, and uploads two owned
     * data textures. The encoded data remains reusable.
     *
     * @param data immutable encoded font and character-range metadata
     * @throws NullPointerException if data is null
     * @throws RuntimeException if STB cannot initialize the font
     */
    public SlugFont(SlugData data) {
        this(data, false);
    }

    /**
     * Compiles reusable CPU metrics and optionally defers graphics allocation until
     * the first binding. Deferred measurement and disposal need no live device.
     *
     * @param data immutable encoded font and character-range metadata
     * @param deferUpload whether to delay device allocation until first binding
     */
    public SlugFont(SlugData data, boolean deferUpload) {
        pendingOutlines = new SlugOutlines(data);
        metrics = pendingOutlines.metrics();
        ascent = metrics.ascent();
        descent = metrics.descent();
        lineHeight = metrics.lineHeight();
        fallbackAdvance = 0.25f;
        if (!deferUpload) ensureTextures();
        recalcSize();
    }

    /**
     * Uploads pending outlines once, keeping staging available if allocation fails.
     */
    private void ensureTextures() {
        if (disposed) throw new IllegalStateException("Font is disposed");
        if (bgfxFont != null) return;
        bgfxFont = new BgfxSlugFont(BgfxGraphics.device(), pendingOutlines);
        pendingOutlines = null;
    }

    /**
     * Submits live outlines through an active texture batch. Camera zoom affects
     * curve evaluation at the actual framebuffer resolution, without raster caches.
     * The batch supplies clipping, translation, opacity and submission ordering.
     *
     * @param batch active destination batch
     * @param text UTF-16 text; null or empty submits nothing
     * @param x baseline X before batch translation
     * @param y baseline Y before batch translation
     * @param size finite nonnegative world units per em
     * @param tint copied tint, or null for the batch color
     */
    public void draw(TextureBatch batch, String text, float x, float y, float size, Color tint) {
        if (batch == null) throw new NullPointerException("batch");
        batch.getGlyphs()
                .draw(this, text, x, y, size, tint);
    }

    /**
     * Submits retained text with its configured baseline, size and tint. The CPU
     * layout is reused until text or size changes; glyph coverage remains live.
     *
     * @param batch active destination texture batch
     */
    public void draw(TextureBatch batch) {
        if (retainedRun == null) retainedRun = createRun(text, size);
        else retainedRun.rebuild(text, size);
        retainedRun.draw(batch, x, y, color);
    }

    /**
     * Looks up a compiled glyph without substituting another character.
     *
     * @param codepoint requested Unicode character
     * @return glyph metadata, or null outside the compiled range
     */
    public SlugGlyph glyph(int codepoint) {
        return metrics.glyph(codepoint);
    }

    /**
     * Returns a pair advance adjustment using the shared CPU kerning data.
     *
     * @param previousCodepoint preceding character
     * @param codepoint current character
     * @return advance adjustment in em units
     */
    public float kerning(int previousCodepoint, int codepoint) {
        return metrics.kerning(previousCodepoint, codepoint);
    }

    /**
     * Creates a reusable layout tied to this font, avoiding repeated glyph and kerning
     * layout during drawing. Keep the font's GPU resources alive while drawing the run.
     *
     * @param text text to lay out
     * @param size world units per em
     * @return new reusable run
     */
    public SlugTextRun createRun(String text, float size) {
        return new SlugTextRun(this, text, size);
    }

    /**
     * Reports whether the owned GPU outline textures have been released.
     *
     * @return true after disposal; measurement remains available
     */
    public boolean isDisposed() { return disposed; }

    /**
     * Binds outline data to the two units reserved by TextureBatch's glyph shader.
     * Uploads deferred data if needed. Requires the graphics context; the batch restores previous bindings when
     * its glyph segment finishes. This does not transfer texture ownership.
     *
     * @throws IllegalStateException if this font has been disposed
     */
    public void bindTextures() {
        ensureTextures();
        BgfxGraphics.bindTexture(0, Short.toUnsignedInt(bgfxFont.curves()));
        BgfxGraphics.bindTexture(1, Short.toUnsignedInt(bgfxFont.bands()));
    }

    /**
     * Replaces retained text, normalizing null to empty, and recalculates measured
     * width and height at the retained font size.
     *
     * @param text new retained text
     * @return this font
     */
    public SlugFont setText(String text) {
        this.text = text == null ? "" : text;
        recalcSize();
        return this;
    }

    /**
     * Sets the retained baseline origin without changing layout or measurements.
     *
     * @param x baseline X
     * @param y baseline Y
     */
    @Override
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the retained baseline X coordinate.
     *
     * @return baseline X
     */
    @Override
    public float getX() {
        return x;
    }

    /**
     * Sets retained baseline X without changing measured width.
     *
     * @param x baseline X
     */
    @Override
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Returns the retained baseline Y coordinate.
     *
     * @return baseline Y
     */
    @Override
    public float getY() {
        return y;
    }

    /**
     * Sets retained baseline Y without changing measured height.
     *
     * @param y baseline Y
     */
    @Override
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Sets retained world units per em and recomputes measured dimensions.
     * The value is stored without sign or finiteness validation.
     *
     * @param size new font scale
     * @return this font
     */
    public SlugFont setSize(float size) {
        this.size = size;
        recalcSize();
        return this;
    }

    /**
     * Replaces cached retained dimensions with current text measurements. Overrides
     * any width/height values previously set through the Dimensional interface.
     */
    private void recalcSize() {
        width = getWidth(text, size);
        height = getHeight(text, size);
    }

    /**
     * Measures the maximum line advance using the same UTF-16 kerning, tab, and
     * fallback rules as draw. This measures pen advances rather than tight ink bounds.
     * Does not change retained state; null or empty text returns zero.
     *
     * @param text text to measure
     * @param size world units per em
     * @return maximum line advance in world units
     */
    public float getWidth(String text, float size) {
        return getWidth(text, 0, text == null ? 0 : text.length(), size);
    }

    /**
     * Measures a UTF-16 range without allocating a substring. Range endpoints
     * are clamped to the string, and kerning starts fresh at the first character.
     * Newline, tab and unsupported-character advances match live rendering.
     *
     * @param text source text; null or empty yields zero
     * @param startInclusive inclusive start index, clamped to the source
     * @param endExclusive exclusive end index, clamped to the source
     * @param size finite nonnegative world units per em
     * @return maximum line advance in the requested range
     * @throws IllegalArgumentException if size is negative or nonfinite
     */
    public float getWidth(String text, int startInclusive, int endExclusive, float size) {
        if (!Float.isFinite(size) || size < 0f) throw new IllegalArgumentException("Size must be finite and nonnegative.");
        if (text == null || text.isEmpty() || size == 0f) return 0f;
        int start = Math.max(0, startInclusive);
        int end = Math.min(text.length(), endExclusive);
        if (start >= end) return 0f;

        float lineWidth = 0f;
        float maxWidth = 0f;
        int previous = 0;

        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                maxWidth = Math.max(maxWidth, lineWidth);
                lineWidth = 0f;
                previous = 0;
                continue;
            }
            if (c == '\t') {
                SlugGlyph space = glyph(' ');
                lineWidth += (space != null ? space.advance : fallbackAdvance) * 4f * size;
                previous = 0;
                continue;
            }
            SlugGlyph glyph = glyph(c);
            if (glyph == null) {
                lineWidth += fallbackAdvance * size;
                previous = 0;
                continue;
            }
            if (previous != 0) {
                lineWidth += kerning(previous, c) * size;
            }
            lineWidth += glyph.advance * size;
            previous = c;
        }

        return Math.max(maxWidth, lineWidth);
    }

    /**
     * Measures line-box height as newline count plus one times font line height and
     * size. A trailing newline adds an empty line; null or empty text returns zero.
     * This is not a tight glyph-ink measurement.
     *
     * @param text text to measure
     * @param size world units per em
     * @return total line-box height
     */
    public float getHeight(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines * lineHeight * size;
    }

    /**
     * Returns cached retained width, either measured or explicitly overridden.
     *
     * @return retained width metadata
     */
    @Override
    public float getWidth() {
        return width;
    }

    /**
     * Overrides cached width metadata without scaling glyphs or changing font size.
     * A later text or single-argument size change recomputes it.
     *
     * @param width width metadata to store
     */
    @Override
    public void setWidth(float width) {
        this.width = width;
    }

    /**
     * Returns cached retained height, either measured or explicitly overridden.
     *
     * @return retained height metadata
     */
    @Override
    public float getHeight() {
        return height;
    }

    /**
     * Overrides cached height metadata without scaling glyphs or changing line spacing.
     * A later text or single-argument size change recomputes it.
     *
     * @param height height metadata to store
     */
    @Override
    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Overrides both cached dimensions without changing the font scale used to draw.
     * Use the single-argument font-size setter to resize glyphs and recalculate bounds.
     *
     * @param width  width metadata
     * @param height height metadata
     */
    @Override
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Returns the retained drawing scale, independent of overridden width/height metadata.
     *
     * @return world units per em
     */
    public float getFontSize() {
        return size;
    }

    /**
     * Returns the live retained tint. Direct mutations affect subsequent retained draws.
     *
     * @return mutable font-owned color
     */
    public Color getColor() {
        return color;
    }

    /**
     * Copies a supplied color into the retained tint, leaving it unchanged for null.
     *
     * @param color tint to copy, or null
     * @return this font
     */
    public SlugFont setColor(Color color) {
        if (color != null) {
            this.color.set(color);
        }
        return this;
    }

    /**
     * Returns STB ascent converted from font units to em units.
     *
     * @return baseline-to-ascent metric in em units
     */
    public float ascent() {
        return ascent;
    }

    /**
     * Returns STB descent in em units, normally a negative baseline-relative value.
     *
     * @return descent metric in em units
     */
    public float descent() {
        return descent;
    }

    /**
     * Returns ascent minus descent plus line gap, used for baseline spacing.
     *
     * @return line height in em units
     */
    public float lineHeight() {
        return lineHeight;
    }

    /**
     * Deletes owned curve and band textures with the graphics device current, replacing
     * their IDs with zero. Repeated calls are harmless. Retains CPU glyph, kerning,
     * and font-byte data, so measurement still works while drawing is no longer valid.
     * An unuploaded deferred font needs no current context for disposal.
     */
    public void dispose() {
        disposed = true;
        pendingOutlines = null;
        if (bgfxFont != null) bgfxFont.close();
        bgfxFont = null;
    }

}
