package valthorne.graphics.font;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.Dimensional;

import java.util.Arrays;

/**
 * Renders cached bitmap glyphs from a baked {@link FontData} atlas through a {@link TextureBatch}.
 *
 * <p>
 * This class is designed for fast repeated text rendering in places like UI, debug overlays,
 * HUD labels, dialog boxes, and other 2D text-heavy systems. A {@link Font} instance owns one
 * atlas {@link Texture} and converts the current text into cached quads ahead of time so the
 * draw step only needs to iterate the prepared glyph entries and submit them to the batch.
 * </p>
 *
 * <h2>How this class works</h2>
 * <p>
 * The supplied {@link FontData} contains baked glyph metrics and atlas texture information.
 * When text is assigned through {@link #setText(String)}, this class walks the string once,
 * computes every drawable glyph's position and UV coordinates, and stores the result inside
 * a reusable quad cache. That cache is then used by {@link #draw(TextureBatch)} for rendering.
 * This avoids recalculating line positions, glyph rectangles, and UV coordinates every frame.
 * </p>
 *
 * <h2>What gets cached</h2>
 * <ul>
 *     <li>Measured width of the current text block</li>
 *     <li>Measured height of the current text block</li>
 *     <li>Per-glyph local position</li>
 *     <li>Per-glyph local size</li>
 *     <li>Per-glyph atlas UV coordinates</li>
 *     <li>Per-glyph metadata for runtime stylers</li>
 * </ul>
 *
 * <h2>Rendering passes</h2>
 * <p>
 * Rendering can happen in up to three passes:
 * </p>
 * <ol>
 *     <li>Shadow pass</li>
 *     <li>Outline pass</li>
 *     <li>Main glyph pass</li>
 * </ol>
 *
 * <p>
 * Each pass uses the same cached glyph geometry. Only offsets, colors, and optional runtime
 * style adjustments differ between passes.
 * </p>
 *
 * <h2>Tabs, missing glyphs, and newlines</h2>
 * <p>
 * This class supports newline and tab processing when measuring and building cached layout data.
 * Newlines start a new visual line. Tabs advance by a width derived from a configurable number
 * of spaces. Missing glyphs do not crash rendering; they use a fallback horizontal advance so
 * layout can continue in a stable way.
 * </p>
 *
 * <h2>Runtime styling</h2>
 * <p>
 * A {@link FontStyler} may be attached to modify each glyph as it is rendered. This allows effects
 * like shaking, waving, color cycling, visibility toggles, or per-letter scaling without rebuilding
 * the cached layout itself.
 * </p>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * Font font = new Font("assets/fonts/inter.ttf", 32);
 * font.setText("Inventory");
 * font.setPosition(24f, 680f);
 * font.setColor(new Color(1f, 1f, 1f, 1f));
 * font.setShadow(2f, -2f, new Color(0f, 0f, 0f, 0.65f));
 * font.setOutline(1, new Color(0f, 0f, 0f, 1f));
 *
 * TextureBatch batch = new TextureBatch(4096);
 *
 * batch.begin();
 * font.draw(batch);
 * batch.end();
 *
 * font.dispose();
 * batch.dispose();
 * }</pre>
 *
 * <h2>Important ownership note</h2>
 * <p>
 * This class owns the atlas {@link Texture} it creates from the provided {@link FontData}. When the
 * font is no longer needed, call {@link #dispose()} to release the texture and clear internal references.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 6th, 2025
 */
public class Font implements Dimensional {

    /**
     * Number of spaces a tab character expands to when width and layout are calculated.
     */
    private static final int TAB_SPACES = 4;

    private final float fallbackAdvance; // Fallback horizontal advance used when a glyph is missing from the font.

    private Texture texture; // Atlas texture that contains all baked glyph pixels for this font.
    private FontData data; // Font metadata and glyph metrics used for measurement and rendering.
    private Glyph spaceGlyph; // Cached space glyph used to derive tab width efficiently.
    private String text = ""; // Current text content assigned to this font instance.
    private float x; // World-space x position of the text origin.
    private float y; // World-space y position of the text origin.
    private float width; // Cached measured width of the current text block.
    private float height; // Cached measured height of the current text block.
    private float scaleX = 1f; // Horizontal glyph scale applied during layout and drawing.
    private float scaleY = 1f; // Vertical glyph scale applied during layout and drawing.
    private float tabAdvanceBase; // Unscaled tab advance derived from the width of multiple spaces.
    private float lineAdvanceY; // Cached scaled vertical advance between lines.
    private float baselineDescentY; // Cached scaled descent value used to place the baseline correctly.
    private float firstLineHeightY; // Cached scaled height used for single-line text measurement.
    private float atlasInvW; // Cached inverse atlas width used to convert glyph coordinates into UVs.
    private float atlasInvH; // Cached inverse atlas height used to convert glyph coordinates into UVs.
    private FontStyler styler; // Optional runtime styler used to modify glyph output while drawing.
    private boolean outlineEnabled; // True when outline rendering is enabled.
    private int outlinePx = 1; // Outline radius in pixels.
    private final Color outlineColor = new Color(0f, 0f, 0f, 1f); // Color used during the outline pass.
    private boolean shadowEnabled; // True when shadow rendering is enabled.
    private float shadowOffsetX = 2f; // Horizontal offset applied to the shadow pass.
    private float shadowOffsetY = -2f; // Vertical offset applied to the shadow pass.
    private final Color shadowColor = new Color(0f, 0f, 0f, 0.75f); // Color used during the shadow pass.
    private CachedQuad[] cachedQuads = new CachedQuad[0]; // Cached glyph quads built from the current text layout.
    private int cachedQuadCount; // Number of valid cached quads currently stored in the quad cache.
    private OutlineOffset[] outlineOffsets = new OutlineOffset[0]; // Cached offsets used to draw outline samples around glyphs.
    private int outlineOffsetCount; // Number of valid outline offsets currently stored in the outline cache.
    private int outlineOffsetsForRadius = -1; // Radius value that the current outline offset cache was built for.
    private final GlyphStyle tempStyle = new GlyphStyle(); // Reusable temporary style object used during runtime styling.
    private final GlyphContext tempCtx = new GlyphContext(); // Reusable temporary glyph context used during runtime styling.
    private final Color tempColor = new Color(1f, 1f, 1f, 1f); // Reusable temporary color used during outline rendering.
    private final Color tempGlyphColor = new Color(1f, 1f, 1f, 1f); // Reusable temporary color used when the styler overrides glyph color.

    /**
     * Creates a new font renderer from existing baked {@link FontData}.
     *
     * <p>
     * This constructor creates the atlas {@link Texture}, caches commonly used metric values,
     * prepares tab width behavior, and builds the initial layout cache for the current text,
     * which starts as an empty string.
     * </p>
     *
     * <p>
     * The supplied {@link FontData} must not be null.
     * </p>
     *
     * @param data baked font data containing glyph metrics and atlas texture data
     * @throws NullPointerException if {@code data} is null
     */
    public Font(FontData data) {
        if (data == null) {
            throw new NullPointerException("FontData cannot be null");
        }

        this.data = data;
        this.texture = new Texture(data.textureData());
        this.spaceGlyph = data.glyph(' ');
        this.fallbackAdvance = data.lineHeight() * 0.25f;

        refreshAtlasInv();
        recalcScaleCaches();
        recalcTabBase();
        rebuildLayoutAndMeasureCache(this.text);
    }

    /**
     * Loads font data from disk and creates a font renderer from it.
     *
     * <p>
     * This constructor is a convenience overload that calls {@link FontData#load(String, int, int, int)}
     * using the character range {@code 0..254}.
     * </p>
     *
     * @param path     path to the font file
     * @param fontSize target baked font size
     */
    public Font(String path, int fontSize) {
        this(FontData.load(path, fontSize, 0, 254));
    }

    /**
     * Replaces the current text and rebuilds the cached glyph layout if the text changed.
     *
     * <p>
     * Null input is converted into an empty string. If the incoming text is equal to the current text,
     * the method returns immediately without rebuilding caches.
     * </p>
     *
     * <p>
     * Rebuilding the layout updates:
     * </p>
     * <ul>
     *     <li>cached glyph quads</li>
     *     <li>cached width</li>
     *     <li>cached height</li>
     * </ul>
     *
     * @param text new text to render
     * @return this font instance for chaining
     */
    public Font setText(String text) {
        String newText = (text == null) ? "" : text;
        if (this.text.equals(newText)) {
            return this;
        }

        this.text = newText;
        rebuildLayoutAndMeasureCache(this.text);
        return this;
    }

    /**
     * Returns the current text assigned to this font instance.
     *
     * @return current text
     */
    public String getText() {
        return text;
    }

    /**
     * Draws the currently cached text through the supplied {@link TextureBatch}.
     *
     * <p>
     * Rendering is skipped if there are no cached glyphs or if the atlas texture reports
     * invalid dimensions.
     * </p>
     *
     * <p>
     * Pass order is:
     * </p>
     * <ol>
     *     <li>shadow pass, if enabled</li>
     *     <li>outline pass, if enabled</li>
     *     <li>main pass</li>
     * </ol>
     *
     * <p>
     * The method expects the caller to already have started the batch.
     * </p>
     *
     * @param batch batch used to submit glyph quads
     */
    public void draw(TextureBatch batch) {
        if (cachedQuadCount <= 0) {
            return;
        }

        final float texW = texture.getWidth();
        final float texH = texture.getHeight();
        if (texW <= 0f || texH <= 0f) {
            return;
        }

        if (atlasInvW == 0f || atlasInvH == 0f) {
            refreshAtlasInv();
        }

        if (shadowEnabled && shadowColor.a() > 0f) {
            drawPass(batch, shadowOffsetX, shadowOffsetY, shadowColor);
        }

        if (outlineEnabled && outlinePx > 0 && outlineColor.a() > 0f) {
            rebuildOutlineOffsetsIfNeeded();
            drawOutlinePass(batch, outlineColor);
        }

        drawPass(batch, 0f, 0f, texture.getColor());
    }

    /**
     * Releases owned resources and clears references used by this font instance.
     *
     * <p>
     * This disposes the owned atlas {@link Texture}, then nulls other references so the font
     * is no longer usable for rendering.
     * </p>
     */
    public void dispose() {
        data = null;
        spaceGlyph = null;
        text = null;
        styler = null;

        texture.dispose();
        texture = null;
    }

    /**
     * Assigns a runtime {@link FontStyler} that may modify glyph output during drawing.
     *
     * <p>
     * The styler is consulted per glyph during normal, shadow, and outline rendering paths.
     * It can hide glyphs, recolor them, offset them, or apply scale changes without forcing
     * a layout rebuild.
     * </p>
     *
     * @param styler styler to use, or null to disable styling
     * @return this font instance for chaining
     */
    public Font setStyler(FontStyler styler) {
        this.styler = styler;
        return this;
    }

    /**
     * Removes any runtime styler currently assigned to this font.
     *
     * @return this font instance for chaining
     */
    public Font clearStyler() {
        this.styler = null;
        return this;
    }

    /**
     * Returns the styler currently assigned to this font.
     *
     * @return active styler, or null if none is assigned
     */
    public FontStyler getStyler() {
        return styler;
    }

    /**
     * Enables outline rendering and updates outline settings.
     *
     * <p>
     * The outline radius is clamped to at least {@code 1}. When a non-null color is supplied,
     * the existing outline color is replaced. The outline offset cache is also refreshed so the
     * next draw uses the correct radius data.
     * </p>
     *
     * @param outlinePx outline radius in pixels
     * @param color     outline color, or null to keep the current color
     * @return this font instance for chaining
     */
    public Font setOutline(int outlinePx, Color color) {
        this.outlineEnabled = true;
        this.outlinePx = Math.max(1, outlinePx);

        if (color != null) {
            this.outlineColor.set(color);
        }

        rebuildOutlineOffsetsIfNeeded();
        return this;
    }

    /**
     * Disables outline rendering.
     */
    public void disableOutline() {
        this.outlineEnabled = false;
    }

    /**
     * Returns whether outline rendering is currently enabled.
     *
     * @return true if outline rendering is enabled
     */
    public boolean isOutlineEnabled() {
        return outlineEnabled;
    }

    /**
     * Returns the current outline radius in pixels.
     *
     * @return outline radius
     */
    public int getOutlinePx() {
        return outlinePx;
    }

    /**
     * Returns the mutable color object used by the outline pass.
     *
     * @return outline color
     */
    public Color getOutlineColor() {
        return outlineColor;
    }

    /**
     * Enables shadow rendering and updates shadow settings.
     *
     * <p>
     * The supplied offsets are stored directly. When a non-null color is provided,
     * the existing shadow color is replaced.
     * </p>
     *
     * @param offsetX horizontal shadow offset
     * @param offsetY vertical shadow offset
     * @param color   shadow color, or null to keep the current color
     * @return this font instance for chaining
     */
    public Font setShadow(float offsetX, float offsetY, Color color) {
        this.shadowEnabled = true;
        this.shadowOffsetX = offsetX;
        this.shadowOffsetY = offsetY;

        if (color != null) {
            this.shadowColor.set(color);
        }

        return this;
    }

    /**
     * Disables shadow rendering.
     */
    public void disableShadow() {
        this.shadowEnabled = false;
    }

    /**
     * Returns whether shadow rendering is currently enabled.
     *
     * @return true if shadow rendering is enabled
     */
    public boolean isShadowEnabled() {
        return shadowEnabled;
    }

    /**
     * Returns the horizontal shadow offset.
     *
     * @return shadow x offset
     */
    public float getShadowOffsetX() {
        return shadowOffsetX;
    }

    /**
     * Returns the vertical shadow offset.
     *
     * @return shadow y offset
     */
    public float getShadowOffsetY() {
        return shadowOffsetY;
    }

    /**
     * Returns the mutable color object used by the shadow pass.
     *
     * @return shadow color
     */
    public Color getShadowColor() {
        return shadowColor;
    }

    /**
     * Sets the glyph scale and rebuilds cached layout data.
     *
     * <p>
     * Scale affects glyph positions, glyph size, line spacing, measured width, and measured height.
     * Because of that, changing either scale value triggers recalculation of metric caches and a full
     * rebuild of the current text layout.
     * </p>
     *
     * @param sx horizontal scale
     * @param sy vertical scale
     * @return this font instance for chaining
     */
    public Font setScale(float sx, float sy) {
        this.scaleX = sx;
        this.scaleY = sy;
        recalcScaleCaches();
        rebuildLayoutAndMeasureCache(this.text);
        return this;
    }

    /**
     * Returns the current horizontal scale.
     *
     * @return horizontal scale
     */
    public float getScaleX() {
        return scaleX;
    }

    /**
     * Returns the current vertical scale.
     *
     * @return vertical scale
     */
    public float getScaleY() {
        return scaleY;
    }

    /**
     * Sets the drawing origin for this font.
     *
     * <p>
     * The x and y values define the world-space origin used when cached local glyph positions
     * are submitted during rendering.
     * </p>
     *
     * @param x world-space x position
     * @param y world-space y position
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the current world-space x position of this font.
     *
     * @return x position
     */
    public float getX() {
        return x;
    }

    /**
     * Sets the world-space x position of this font.
     *
     * @param x new x position
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Returns the current world-space y position of this font.
     *
     * @return y position
     */
    public float getY() {
        return y;
    }

    /**
     * Sets the world-space y position of this font.
     *
     * @param y new y position
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Returns the cached width of the currently assigned text.
     *
     * <p>
     * This value is updated whenever cached layout data is rebuilt.
     * </p>
     *
     * @return cached text width
     */
    @Override
    public float getWidth() {
        return width;
    }

    /**
     * Measures the width of the supplied text.
     *
     * <p>
     * This overload measures the full string from start to finish. It respects tab expansion,
     * missing-glyph fallback advance, and newline resets.
     * </p>
     *
     * @param text text to measure
     * @return measured width
     */
    public float getWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        return measureWidth(text, 0, text.length());
    }

    /**
     * Measures the width of the supplied text from index {@code 0} to {@code endExclusive}.
     *
     * <p>
     * This is useful when measuring partial visible ranges, caret positions, or selections.
     * </p>
     *
     * @param text         text to measure
     * @param endExclusive exclusive end index
     * @return measured width of the requested range
     */
    public float getWidth(String text, int endExclusive) {
        if (text == null || text.isEmpty() || endExclusive <= 0) {
            return 0f;
        }
        return measureWidth(text, 0, endExclusive);
    }

    /**
     * Measures the width of the supplied text within a specific range.
     *
     * <p>
     * The range is clamped internally so invalid index combinations do not throw.
     * </p>
     *
     * @param text           text to measure
     * @param startInclusive inclusive start index
     * @param endExclusive   exclusive end index
     * @return measured width of the requested range
     */
    public float getWidth(String text, int startInclusive, int endExclusive) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        return measureWidth(text, startInclusive, endExclusive);
    }

    /**
     * Returns the cached height of the currently assigned text.
     *
     * @return cached text height
     */
    @Override
    public float getHeight() {
        return height;
    }

    /**
     * Measures the height of the supplied text.
     *
     * <p>
     * Height is derived from line count. Single-line text uses the cached first-line height.
     * Multi-line text adds scaled line advance for each additional line.
     * </p>
     *
     * @param text text to measure
     * @return measured height
     */
    public float getHeight(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }

        int lines = 1;
        for (int i = 0, n = text.length(); i < n; i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }

        return (lines == 1) ? firstLineHeightY : firstLineHeightY + (lines - 1) * lineAdvanceY;
    }

    /**
     * Directly sets the cached width and height values.
     *
     * <p>
     * This does not rebuild glyph layout. It only replaces the stored dimension fields.
     * </p>
     *
     * @param width  new width value
     * @param height new height value
     */
    @Override
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Directly sets the cached width value.
     *
     * <p>
     * This method does not rebuild text layout.
     * </p>
     *
     * @param width new width value
     */
    @Override
    public void setWidth(float width) {
        this.width = width;
    }

    /**
     * Directly sets the cached height value.
     *
     * <p>
     * This method does not rebuild text layout.
     * </p>
     *
     * @param height new height value
     */
    @Override
    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Returns the mutable color object used for the main glyph pass.
     *
     * @return font color
     */
    public Color getColor() {
        return texture.getColor();
    }

    /**
     * Sets the main glyph color by forwarding the value to the atlas texture.
     *
     * @param color new main color
     */
    public void setColor(Color color) {
        texture.setColor(color);
    }

    /**
     * Returns the atlas texture used by this font.
     *
     * @return atlas texture
     */
    public Texture getTexture() {
        return texture;
    }

    /**
     * Draws a single rendering pass using cached glyph quads.
     *
     * <p>
     * This method is used for both shadow and main glyph rendering. For each cached quad it:
     * </p>
     * <ul>
     *     <li>computes final world position</li>
     *     <li>optionally applies runtime style changes</li>
     *     <li>submits the glyph to the batch with cached UVs</li>
     * </ul>
     *
     * @param batch    target batch
     * @param passOffX horizontal pass offset
     * @param passOffY vertical pass offset
     * @param tint     base tint for this pass
     */
    private void drawPass(TextureBatch batch, float passOffX, float passOffY, Color tint) {
        final float baseX = this.x + passOffX;
        final float baseY = this.y + passOffY;

        final CachedQuad[] quads = this.cachedQuads;
        final FontStyler styler = this.styler;

        for (int i = 0, n = this.cachedQuadCount; i < n; i++) {
            CachedQuad q = quads[i];

            float dx = baseX + q.x;
            float dy = baseY + q.y;
            float dw = q.w;
            float dh = q.h;

            Color drawColor = tint;

            if (styler != null) {
                tempStyle.reset();
                tempCtx.set(q.c, q.charIndex, q.glyphIndex, q.lineIndex, q.x, q.y, q.w, q.h, dx, dy);
                styler.style(tempStyle, tempCtx);

                if (!tempStyle.isVisible()) {
                    continue;
                }

                dx += tempStyle.getOffsetX();
                dy += tempStyle.getOffsetY();
                dw *= tempStyle.getScaleX();
                dh *= tempStyle.getScaleY();

                if (tempStyle.hasColor()) {
                    tempGlyphColor.r(tempStyle.r());
                    tempGlyphColor.g(tempStyle.g());
                    tempGlyphColor.b(tempStyle.b());
                    tempGlyphColor.a(tempStyle.a());
                    drawColor = tempGlyphColor;
                }
            }

            batch.drawUV(texture, dx, dy, dw, dh, q.u0, q.v0, q.u1, q.v1, drawColor);
        }
    }

    /**
     * Draws the outline pass using the cached outline offset set.
     *
     * <p>
     * Each glyph is drawn multiple times at neighboring offsets. The final alpha for each offset
     * is multiplied by the precomputed offset alpha multiplier to produce a softer outline edge.
     * Runtime styling is still respected.
     * </p>
     *
     * @param batch target batch
     * @param tint  outline tint
     */
    private void drawOutlinePass(TextureBatch batch, Color tint) {
        if (outlineOffsetCount <= 0) {
            return;
        }

        final float baseX = this.x;
        final float baseY = this.y;
        final float alpha = tint.a();

        final CachedQuad[] quads = this.cachedQuads;
        final OutlineOffset[] offsets = this.outlineOffsets;
        final FontStyler styler = this.styler;

        tempColor.r(tint.r());
        tempColor.g(tint.g());
        tempColor.b(tint.b());

        for (int i = 0, n = this.cachedQuadCount; i < n; i++) {
            CachedQuad q = quads[i];

            float qx = baseX + q.x;
            float qy = baseY + q.y;
            float qw = q.w;
            float qh = q.h;

            if (styler != null) {
                tempStyle.reset();
                tempCtx.set(q.c, q.charIndex, q.glyphIndex, q.lineIndex, q.x, q.y, q.w, q.h, qx, qy);
                styler.style(tempStyle, tempCtx);

                if (!tempStyle.isVisible()) {
                    continue;
                }

                qx += tempStyle.getOffsetX();
                qy += tempStyle.getOffsetY();
                qw *= tempStyle.getScaleX();
                qh *= tempStyle.getScaleY();
            }

            for (int k = 0, m = this.outlineOffsetCount; k < m; k++) {
                OutlineOffset offset = offsets[k];
                tempColor.a(alpha * offset.aMul);
                batch.drawUV(texture, qx + offset.ox, qy + offset.oy, qw, qh, q.u0, q.v0, q.u1, q.v1, tempColor);
            }
        }
    }

    /**
     * Recalculates cached metric values that depend on scale.
     *
     * <p>
     * This updates line advance, scaled descent, and the single-line measured height.
     * The values are reused by measurement and layout building.
     * </p>
     */
    private void recalcScaleCaches() {
        lineAdvanceY = data.lineHeight() * scaleY;
        baselineDescentY = data.descent() * scaleY;
        firstLineHeightY = (data.ascent() - data.descent()) * scaleY;
    }

    /**
     * Recalculates the unscaled tab advance.
     *
     * <p>
     * Tabs are measured as a fixed number of spaces. If the space glyph is unavailable,
     * the fallback advance is used instead.
     * </p>
     */
    private void recalcTabBase() {
        float advance = (spaceGlyph != null) ? spaceGlyph.xAdvance() : fallbackAdvance;
        tabAdvanceBase = advance * TAB_SPACES;
    }

    /**
     * Refreshes cached inverse atlas dimensions.
     *
     * <p>
     * These inverse values are used to convert glyph atlas coordinates into normalized UV values.
     * If the texture dimensions are invalid, both values are reset to zero.
     * </p>
     */
    private void refreshAtlasInv() {
        final float texW = texture.getWidth();
        final float texH = texture.getHeight();

        if (texW > 0f && texH > 0f) {
            atlasInvW = 1f / texW;
            atlasInvH = 1f / texH;
        } else {
            atlasInvW = 0f;
            atlasInvH = 0f;
        }
    }

    /**
     * Measures the width of a text range.
     *
     * <p>
     * This method clamps the requested range to valid bounds and then walks the characters,
     * applying newline behavior, tab expansion, glyph advances, and missing-glyph fallback advance.
     * </p>
     *
     * @param text           source text
     * @param startInclusive inclusive start index
     * @param endExclusive   exclusive end index
     * @return measured width of the requested range
     */
    private float measureWidth(String text, int startInclusive, int endExclusive) {
        int length = text.length();
        int start = Math.max(0, startInclusive);
        int end = Math.min(length, endExclusive);

        if (start >= end) {
            return 0f;
        }

        final float sx = this.scaleX;
        final float tabAdv = tabAdvanceBase * sx;
        final float fallbackAdv = fallbackAdvance * sx;

        float penX = 0f;
        float maxX = 0f;

        for (int i = start; i < end; i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                if (penX > maxX) {
                    maxX = penX;
                }
                penX = 0f;
                continue;
            }

            if (c == '\t') {
                penX += tabAdv;
                continue;
            }

            Glyph g = data.glyph(c);
            if (g == null) {
                penX += fallbackAdv;
                continue;
            }

            penX += g.xAdvance() * sx;
        }

        if (penX > maxX) {
            maxX = penX;
        }

        return maxX;
    }

    /**
     * Rebuilds the cached glyph layout for the current text and updates cached dimensions.
     *
     * <p>
     * This is the core layout-building method. It walks the entire string, handles newlines and tabs,
     * resolves glyph metrics, computes destination rectangles, computes atlas UVs, and stores the result
     * in the reusable quad cache.
     * </p>
     *
     * <p>
     * When the input is null or empty, cached dimensions are reset and the quad count becomes zero.
     * </p>
     *
     * @param s source text to cache
     */
    private void rebuildLayoutAndMeasureCache(String s) {
        if (s == null || s.isEmpty()) {
            this.width = 0f;
            this.height = 0f;
            this.cachedQuadCount = 0;
            return;
        }

        if (atlasInvW == 0f || atlasInvH == 0f) {
            refreshAtlasInv();
        }

        final float sx = this.scaleX;
        final float sy = this.scaleY;
        final float tabAdv = tabAdvanceBase * sx;
        final float fallbackAdv = fallbackAdvance * sx;
        final float lineAdvance = lineAdvanceY;

        float penX = 0f;
        float baselineY = -baselineDescentY;
        float maxX = 0f;

        int lines = 1;
        int lineIndex = 0;
        int glyphIndex = 0;

        ensureCachedQuadCapacity(s.length());
        cachedQuadCount = 0;

        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);

            if (c == '\n') {
                if (penX > maxX) {
                    maxX = penX;
                }
                penX = 0f;
                baselineY -= lineAdvance;
                lines++;
                lineIndex++;
                continue;
            }

            if (c == '\t') {
                penX += tabAdv;
                continue;
            }

            Glyph g = data.glyph(c);
            if (g == null) {
                penX += fallbackAdv;
                continue;
            }

            final int gw = g.width();
            final int gh = g.height();
            final float adv = g.xAdvance() * sx;

            if (gw <= 0 || gh <= 0) {
                penX += adv;
                continue;
            }

            final float baseX = penX + (g.xOffset() * sx);
            final float baseY = baselineY - (g.yOffset() * sy);
            final float baseW = gw * sx;
            final float baseH = -(gh * sy);

            final float u0 = g.x0() * atlasInvW;
            final float u1 = g.x1() * atlasInvW;
            final float v0 = g.y0() * atlasInvH;
            final float v1 = g.y1() * atlasInvH;

            CachedQuad quad = cachedQuads[cachedQuadCount];
            if (quad == null) {
                quad = new CachedQuad();
                cachedQuads[cachedQuadCount] = quad;
            }

            quad.x = baseX;
            quad.y = baseY;
            quad.w = baseW;
            quad.h = baseH;
            quad.u0 = u0;
            quad.v0 = v0;
            quad.u1 = u1;
            quad.v1 = v1;
            quad.c = c;
            quad.charIndex = i;
            quad.glyphIndex = glyphIndex;
            quad.lineIndex = lineIndex;

            cachedQuadCount++;
            penX += adv;
            glyphIndex++;
        }

        if (penX > maxX) {
            maxX = penX;
        }

        this.width = maxX;
        this.height = (lines == 1) ? firstLineHeightY : firstLineHeightY + (lines - 1) * lineAdvanceY;
    }

    /**
     * Ensures the cached quad array can hold at least the requested number of entries.
     *
     * <p>
     * Capacity grows geometrically so repeated text changes do not cause excessive allocations.
     * Existing cached entries are preserved.
     * </p>
     *
     * @param required minimum number of entries needed
     */
    private void ensureCachedQuadCapacity(int required) {
        if (cachedQuads.length >= required) {
            return;
        }

        int newCapacity = Math.max(required, cachedQuads.length * 2 + 8);
        cachedQuads = Arrays.copyOf(cachedQuads, newCapacity);
    }

    /**
     * Rebuilds the outline offset cache if the configured outline radius changed.
     *
     * <p>
     * The resulting offsets form a disk-shaped set around the origin. Each offset also stores an
     * alpha multiplier so outer samples can fade more softly when the outline radius is larger.
     * </p>
     */
    private void rebuildOutlineOffsetsIfNeeded() {
        final int radius = Math.max(1, this.outlinePx);
        if (outlineOffsetsForRadius == radius && outlineOffsetCount > 0) {
            return;
        }

        int max = (2 * radius + 1);
        max = max * max - 1;

        if (outlineOffsets.length < max) {
            outlineOffsets = Arrays.copyOf(outlineOffsets, max);
        }

        final int radiusSquared = radius * radius;
        final float featherBand = 1f / (float) radius;

        int count = 0;

        for (int oy = -radius; oy <= radius; oy++) {
            for (int ox = -radius; ox <= radius; ox++) {
                if (ox == 0 && oy == 0) {
                    continue;
                }

                int distanceSquared = ox * ox + oy * oy;
                if (distanceSquared > radiusSquared) {
                    continue;
                }

                float alphaMultiplier;

                if (radius <= 1) {
                    alphaMultiplier = 1f;
                } else {
                    float distance = (float) Math.sqrt(distanceSquared);
                    float normalized = distance / (float) radius;

                    alphaMultiplier = 1f - smoothstep(1f - featherBand, 1f, normalized);

                    if (normalized <= 1f - featherBand) {
                        alphaMultiplier = 1f;
                    }
                    if (alphaMultiplier < 0.35f) {
                        alphaMultiplier = 0.35f;
                    }
                }

                OutlineOffset offset = outlineOffsets[count];
                if (offset == null) {
                    offset = new OutlineOffset();
                    outlineOffsets[count] = offset;
                }

                offset.ox = ox;
                offset.oy = oy;
                offset.aMul = alphaMultiplier;
                count++;
            }
        }

        outlineOffsetCount = count;
        outlineOffsetsForRadius = radius;
    }

    /**
     * Evaluates a smoothstep interpolation.
     *
     * <p>
     * This helper is used while building outline alpha falloff values.
     * </p>
     *
     * @param a lower bound
     * @param b upper bound
     * @param x input value
     * @return smooth interpolated value in the range {@code 0..1}
     */
    private static float smoothstep(float a, float b, float x) {
        if (x <= a) return 0f;
        if (x >= b) return 1f;
        x = (x - a) / (b - a);
        return x * x * (3f - 2f * x);
    }

    /**
     * Cached render data for one drawable glyph.
     *
     * <p>
     * Each cached quad stores the glyph's local position, size, UVs, and metadata
     * used by runtime styling.
     * </p>
     *
     * @author Albert Beaupre
     * @since March 7th, 2026
     */
    private static final class CachedQuad {

        float x; // Local x position of the cached glyph quad.
        float y; // Local y position of the cached glyph quad.
        float w; // Cached width of the glyph quad.
        float h; // Cached height of the glyph quad.
        float u0; // Left atlas UV coordinate.
        float v0; // Top atlas UV coordinate.
        float u1; // Right atlas UV coordinate.
        float v1; // Bottom atlas UV coordinate.
        char c; // Character represented by this cached glyph quad.
        int charIndex; // Original character index in the source string.
        int glyphIndex; // Renderable glyph index within the text.
        int lineIndex; // Line index this glyph belongs to.
    }

    /**
     * Cached offset data used for outline rendering.
     *
     * <p>
     * Each offset stores a pixel offset from the glyph origin and an alpha multiplier
     * used to soften the outline edge.
     * </p>
     *
     * @author Albert Beaupre
     * @since March 7th, 2026
     */
    private static final class OutlineOffset {

        int ox; // Horizontal offset for one outline sample.
        int oy; // Vertical offset for one outline sample.
        float aMul; // Alpha multiplier for this outline sample.
    }
}