package valthorne.graphics.font;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.ui.Dimensional;

/**
 * A lightweight bitmap-font text renderer built on {@link FontData} + a single {@link Texture} atlas.
 *
 * <p>This class is responsible for:</p>
 * <ul>
 *     <li>Storing the current text string ({@link #text}) and caching its measured width/height</li>
 *     <li>Rendering glyphs from a font atlas using glyph metrics from {@link FontData}</li>
 *     <li>Applying kerning via {@link FontData#getKerningAdvance(char, char)}</li>
 *     <li>Handling special characters:
 *         <ul>
 *             <li>{@code '\n'}: starts a new line</li>
 *             <li>{@code '\t'}: advances by a tab width (defaults to 4 spaces)</li>
 *         </ul>
 *     </li>
 *     <li>Supporting independent X/Y scaling</li>
 * </ul>
 *
 * <h2>Coordinate behavior</h2>
 * <p>This renderer treats {@link #x}/{@link #y} as the "pen origin" for the first line. Internally it computes
 * a baseline position using the font descent so glyph offsets land where you expect.</p>
 *
 * <p>Because the underlying {@link Texture} is scaled using {@code (1, -1)} in the constructor, the atlas is
 * drawn upside-down relative to the typical OpenGL UV origin, which matches your font atlas coordinates.</p>
 *
 * <h2>Caching</h2>
 * <p>{@link #setText(String)} updates cached width/height immediately via {@link #measureIntoCache(String)}.
 * {@link #getWidth()} and {@link #getHeight()} return these cached values.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * FontData data = ...;                     // Loaded/constructed elsewhere (atlas + metrics)
 * Font font = new Font(data);
 *
 * font.setText("Hello,\nValthorne!");
 * font.setPosition(40, 300);
 * font.setScale(1.0f, 1.0f);
 * font.setColor(new Color(1f, 1f, 1f, 1f));
 *
 * // Inside your render loop:
 * font.draw();
 *
 * // Measuring:
 * float w = font.getWidth();
 * float h = font.getHeight();
 *
 * // Don't forget to do this after you're done using the font
 * font.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 6th, 2025
 */
public class Font implements Dimensional {

    private Texture texture;                  // Underlying texture atlas used to draw glyph regions.
    private FontData data;                    // Glyph metrics/kerning and atlas metadata.
    private Glyph spaceGlyph;                 // Cached data.glyph(' ') to compute tab width efficiently.
    private final float fallbackAdvance;            // Advance used when glyph is missing from FontData.
    private String text = "";                       // Current text content for rendering and measurement.
    private float scaleX = 1f;                      // Horizontal scale factor applied to glyph sizing/advance.
    private float scaleY = 1f;                      // Vertical scale factor applied to glyph sizing/line spacing.
    private float x;                                // X position (pen origin) for rendering the first line.
    private float y;                                // Y position (pen origin) for rendering the first line.
    private float width;                            // Cached measured max line width of {@link #text}.
    private float height;                           // Cached measured total height of {@link #text}.
    private float tabAdvanceBase;                   // Base (unscaled) tab advance, computed from space glyph.
    private float lineAdvanceY;                     // Scaled vertical distance between baselines for new lines.
    private float baselineDescentY;                 // Scaled descent used to place baseline relative to {@link #y}.
    private float firstLineHeightY;                 // Scaled height of a single-line string (ascent - descent).

    /**
     * Creates a new {@link Font} instance backed by the given {@link FontData}.
     *
     * <p>Initialization details:</p>
     * <ul>
     *     <li>Creates a {@link Texture} from {@link FontData#textureData()}</li>
     *     <li>Applies {@code texture.setScale(1f, -1f)} so glyph atlas coordinates match your rendering orientation</li>
     *     <li>Caches the space glyph (if present) to compute a consistent tab width</li>
     *     <li>Computes fallback and scale-dependent cached metrics</li>
     * </ul>
     *
     * @param data the font metadata containing glyph metrics, kerning, and texture information
     * @throws NullPointerException if {@code data} is null
     */
    public Font(FontData data) {
        if (data == null) throw new NullPointerException("FontData cannot be null");
        this.data = data;
        this.texture = new Texture(data.textureData());
        this.texture.setScale(1f, -1f);
        this.spaceGlyph = data.glyph(' ');
        this.fallbackAdvance = data.lineHeight() * 0.25f;

        recalcScaleCaches();
        recalcTabBase();
    }

    /**
     * Renders the current {@link #text} using the font atlas and glyph metrics.
     *
     * <p>Rendering rules:</p>
     * <ul>
     *     <li>If {@link #text} is null/empty, this is a no-op.</li>
     *     <li>{@code '\n'} resets pen X to {@link #x} and moves baseline down by {@link #lineAdvanceY}.</li>
     *     <li>{@code '\t'} advances pen X by a tab width (4 spaces by default).</li>
     *     <li>Kerning is applied between adjacent glyphs via {@link FontData#getKerningAdvance(char, char)}.</li>
     *     <li>Missing glyphs advance by {@link #fallbackAdvance} (scaled) and render nothing.</li>
     *     <li>Zero-sized glyphs (width/height <= 0) do not render but still advance.</li>
     * </ul>
     *
     * <p>Positioning:</p>
     * <ul>
     *     <li>The baseline starts at {@code y - baselineDescentY} so descent is accounted for.</li>
     *     <li>Per-glyph offsets ({@link Glyph#xOffset()}, {@link Glyph#yOffset()}) are applied with scaling.</li>
     * </ul>
     */
    public void draw() {
        final String s = this.text;
        if (s == null || s.isEmpty()) return;

        final float sx = this.scaleX;
        final float sy = this.scaleY;

        float penX = x;
        float baselineY = y - baselineDescentY;
        final float lineAdvance = lineAdvanceY;

        final float tabAdv = tabAdvanceBase * sx;
        final float fallbackAdv = fallbackAdvance * sx;

        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);

            if (c == '\n') {
                penX = x;
                baselineY -= lineAdvance;
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

            int gw = g.width();
            int gh = g.height();

            if (gw <= 0 || gh <= 0) {
                penX += g.xAdvance() * sx;
                continue;
            }

            texture.setRegion(g.x0(), g.y0(), g.x1(), g.y1());

            float drawX = penX + (g.xOffset() * sx);
            float drawY = baselineY - (g.yOffset() * sy);

            texture.setPosition(drawX, drawY);
            texture.setSize(gw * sx, gh * sy);
            texture.draw();

            penX += g.xAdvance() * sx;
        }
    }

    /**
     * Returns the cached width of the current {@link #text}.
     *
     * <p>This is updated when calling {@link #setText(String)} or {@link #setScale(float, float)}.</p>
     *
     * @return cached width in pixels
     */
    public float getWidth() {
        return width;
    }

    /**
     * Measures the width (max line width) of an arbitrary string using the current font configuration.
     *
     * <p>Measurement rules:</p>
     * <ul>
     *     <li>Returns 0 for null/empty input</li>
     *     <li>Tracks max line width across newlines</li>
     *     <li>Applies tab advancement</li>
     *     <li>Uses glyph {@link Glyph#xAdvance()} and {@link #fallbackAdvance} for missing glyphs</li>
     * </ul>
     *
     * <p>Note: This method does not apply kerning (mirrors your original behavior).</p>
     *
     * @param text the string to measure
     * @return width in pixels
     */
    public float getWidth(String text) {
        if (text == null || text.isEmpty()) return 0f;

        final float sx = this.scaleX;
        final float tabAdv = tabAdvanceBase * sx;
        final float fallbackAdv = fallbackAdvance * sx;

        float penX = 0f;
        float maxX = 0f;

        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                if (penX > maxX) maxX = penX;
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

        if (penX > maxX) maxX = penX;
        return maxX;
    }

    /**
     * Returns the cached height of the current {@link #text}.
     *
     * <p>This is updated when calling {@link #setText(String)} or {@link #setScale(float, float)}.</p>
     *
     * @return cached height in pixels
     */
    public float getHeight() {
        return height;
    }

    /**
     * Measures the height of an arbitrary string using the current font configuration.
     *
     * <p>Rules:</p>
     * <ul>
     *     <li>Returns 0 for null/empty input</li>
     *     <li>Counts newline characters to determine line count</li>
     *     <li>Returns {@link #firstLineHeightY} for a single line</li>
     *     <li>For multiple lines: {@code firstLineHeightY + (lines - 1) * lineAdvanceY}</li>
     * </ul>
     *
     * @param text the string to measure
     * @return height in pixels
     */
    public float getHeight(String text) {
        if (text == null || text.isEmpty()) return 0f;

        int lines = 1;
        for (int i = 0, n = text.length(); i < n; i++) {
            if (text.charAt(i) == '\n') lines++;
        }

        if (lines == 1) return firstLineHeightY;
        return firstLineHeightY + (lines - 1) * lineAdvanceY;
    }

    /**
     * Returns the currently assigned text string.
     *
     * @return current text (never null unless {@link #dispose()} has been called)
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the text content rendered by this font and updates cached width/height.
     *
     * <p>Passing null results in an empty string.</p>
     *
     * @param text new text value (null becomes "")
     */
    public void setText(String text) {
        this.text = (text == null ? "" : text);
        measureIntoCache(this.text);
    }

    /**
     * Sets the pen origin position for rendering.
     *
     * <p>This does not re-measure text. It only affects where the text is drawn.</p>
     *
     * @param x new x position
     * @param y new y position
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Sets the horizontal and vertical scale factors for rendering and measurement.
     *
     * <p>This recomputes scale-dependent caches (line height, baseline offsets) and then re-measures
     * the current {@link #text} into {@link #width}/{@link #height}.</p>
     *
     * @param sx horizontal scale factor
     * @param sy vertical scale factor
     */
    public void setScale(float sx, float sy) {
        this.scaleX = sx;
        this.scaleY = sy;

        recalcScaleCaches();
        measureIntoCache(this.text);
    }

    /**
     * Returns the current horizontal scale.
     *
     * @return x scale factor
     */
    public float getScaleX() {return scaleX;}

    /**
     * Returns the current vertical scale.
     *
     * @return y scale factor
     */
    public float getScaleY() {return scaleY;}

    /**
     * Returns the current pen origin X position.
     *
     * @return x coordinate
     */
    public float getX() {return x;}

    /**
     * Sets the pen origin X position.
     *
     * @param x new x coordinate
     */
    public void setX(float x) {this.x = x;}

    /**
     * Returns the current pen origin Y position.
     *
     * @return y coordinate
     */
    public float getY() {return y;}

    /**
     * Sets the pen origin Y position.
     *
     * @param y new y coordinate
     */
    public void setY(float y) {this.y = y;}

    /**
     * Returns the current font color used for rendering.
     *
     * <p>This color is stored on the underlying {@link Texture}.</p>
     *
     * @return current color
     */
    public Color getColor() {
        return this.texture.getColor();
    }

    /**
     * Sets the font color used for rendering.
     *
     * <p>This forwards to {@link Texture#setColor(Color)}.</p>
     *
     * @param color new color (may be null depending on Texture implementation)
     */
    public void setColor(Color color) {
        this.texture.setColor(color);
    }

    /**
     * Disposes of GPU/native resources associated with this font.
     *
     * <p>After calling this, the font should no longer be used. This method:</p>
     * <ul>
     *     <li>Nulls {@link #text} to help catch accidental reuse</li>
     *     <li>Disposes the underlying {@link Texture}</li>
     * </ul>
     */
    public void dispose() {
        this.data = null;
        this.spaceGlyph = null;
        this.text = null;
        this.texture.dispose();
        this.texture = null;
    }

    /**
     * Recalculates scale-dependent cached metrics derived from {@link FontData}.
     *
     * <p>Cached values:</p>
     * <ul>
     *     <li>{@link #lineAdvanceY}: baseline-to-baseline spacing</li>
     *     <li>{@link #baselineDescentY}: descent offset used for baseline placement</li>
     *     <li>{@link #firstLineHeightY}: ascent - descent (descent is typically negative)</li>
     * </ul>
     */
    private void recalcScaleCaches() {
        lineAdvanceY = data.lineHeight() * scaleY;
        baselineDescentY = data.descent() * scaleY;
        firstLineHeightY = (data.ascent() - data.descent()) * scaleY; // descent negative
    }

    /**
     * Recomputes the base tab width in "font units" (before X scaling is applied).
     *
     * <p>Tab width defaults to 4 spaces. If a space glyph is missing, this falls back to
     * {@link #fallbackAdvance}.</p>
     */
    private void recalcTabBase() {
        float adv = (spaceGlyph != null) ? spaceGlyph.xAdvance() : fallbackAdvance;
        tabAdvanceBase = adv * 4f;
    }

    /**
     * Measures {@code s} using the current font configuration and updates {@link #width} and {@link #height}.
     *
     * <p>Rules:</p>
     * <ul>
     *     <li>Null/empty: width/height become 0</li>
     *     <li>Width: maximum pen X across lines (split by '\n')</li>
     *     <li>Height: first line height + (lines - 1) * lineAdvanceY</li>
     *     <li>Tabs: advance by tab width (4 spaces)</li>
     *     <li>Missing glyphs: advance by {@link #fallbackAdvance}</li>
     * </ul>
     *
     * <p>Note: Like {@link #getWidth(String)}, this method does not apply kerning.</p>
     *
     * @param s the text to measure
     */
    private void measureIntoCache(String s) {
        if (s == null || s.isEmpty()) {
            this.width = 0f;
            this.height = 0f;
            return;
        }

        final float sx = this.scaleX;
        final float tabAdv = tabAdvanceBase * sx;
        final float fallbackAdv = fallbackAdvance * sx;

        float penX = 0f;
        float maxX = 0f;

        int lines = 1;

        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);

            if (c == '\n') {
                if (penX > maxX) maxX = penX;
                penX = 0f;
                lines++;
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

        if (penX > maxX) maxX = penX;

        this.width = maxX;

        if (lines == 1) this.height = firstLineHeightY;
        else this.height = firstLineHeightY + (lines - 1) * lineAdvanceY;
    }
}
