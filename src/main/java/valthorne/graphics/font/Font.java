package valthorne.graphics.font;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.Dimensional;

/**
 * Bitmap font renderer backed by {@link FontData} and a single atlas {@link Texture}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Font font = new Font("assets/fonts/Inter-Regular.ttf", 32)
 *         .setShadow(2f, -2f, new Color(0f, 0f, 0f, 0.75f))
 *         .setOutline(1, new Color(0f, 0f, 0f, 1f));
 *
 * font.setText("Hello\nWorld!");
 * font.setPosition(32, 64);
 * font.setScale(1f, 1f);
 *
 * font.draw();
 * font.dispose();
 * }</pre>
 *
 * <h2>Behavior</h2>
 * <ul>
 *     <li>Draws glyphs from an atlas using metrics from {@link FontData}.</li>
 *     <li>Supports {@code '\n'} newlines and {@code '\t'} tabs (4 spaces).</li>
 *     <li>Supports independent X/Y scaling.</li>
 *     <li>Supports drop shadow and geometric outline without shaders.</li>
 * </ul>
 *
 * <h2>Coordinates</h2>
 * <p>{@link #x}/{@link #y} represent the pen origin for the first line.</p>
 * <p>The baseline is computed using the font descent.</p>
 * <p>The atlas texture is scaled as {@code (1, -1)} to match atlas coordinate conventions.</p>
 *
 * <h2>Caching</h2>
 * <p>{@link #setText(String)} updates cached {@link #width}/{@link #height} immediately.</p>
 *
 * <h2>Effects</h2>
 * <p>Shadow is a single offset pass. Outline is a disk of offset passes per glyph.</p>
 *
 * @author Albert Beaupre
 * @since December 6th, 2025
 */
public class Font implements Dimensional {

    private Texture texture;                              // Atlas texture used to draw glyph regions.
    private FontData data;                                // Glyph metrics, kerning, and atlas metadata.
    private Glyph spaceGlyph;                             // Cached glyph(' ') for tab sizing.
    private final float fallbackAdvance;                  // Advance used when a glyph is missing.
    private String text = "";                             // Current text content.
    private float scaleX = 1f;                            // Horizontal scale.
    private float scaleY = 1f;                            // Vertical scale.
    private float x;                                      // Pen origin x for the first line.
    private float y;                                      // Pen origin y for the first line.
    private float width;                                  // Cached max line width.
    private float height;                                 // Cached total height.
    private float tabAdvanceBase;                         // Unscaled tab advance (4 spaces).
    private float lineAdvanceY;                           // Scaled distance between baselines.
    private float baselineDescentY;                       // Scaled descent used to place baseline.
    private float firstLineHeightY;                       // Scaled single-line height.
    private final TextureBatch batch;                     // Batched renderer.

    private boolean outlineEnabled;                       // True if outline is enabled.
    private int outlinePx = 1;                            // Outline radius in pixels.
    private Color outlineColor = new Color(0f, 0f, 0f, 1f); // Outline color.

    private boolean shadowEnabled;                        // True if shadow is enabled.
    private float shadowOffsetX = 2f;                     // Shadow x offset in pixels.
    private float shadowOffsetY = -2f;                    // Shadow y offset in pixels.
    private Color shadowColor = new Color(0f, 0f, 0f, 0.75f); // Shadow color.

    /**
     * Creates a font backed by the given {@link FontData}.
     *
     * @param data font metrics and atlas data
     * @throws NullPointerException if {@code data} is null
     */
    public Font(FontData data) {
        if (data == null) throw new NullPointerException("FontData cannot be null");
        this.data = data;
        this.texture = new Texture(data.textureData());
        this.texture.setScale(1f, -1f);
        this.spaceGlyph = data.glyph(' ');
        this.fallbackAdvance = data.lineHeight() * 0.25f;
        this.batch = new TextureBatch(4096);

        recalcScaleCaches();
        recalcTabBase();
        measureIntoCache(this.text);
    }

    /**
     * Loads {@link FontData} from disk and creates a font.
     *
     * @param path     font file path
     * @param fontSize baked pixel size
     */
    public Font(String path, int fontSize) {
        this(FontData.load(path, fontSize, 0, 254));
    }

    /**
     * Enables outline rendering and configures outline parameters.
     *
     * @param outlinePx outline radius in pixels (clamped to {@code >= 1})
     * @param color     outline color (null keeps current)
     * @return this for chaining
     */
    public Font setOutline(int outlinePx, Color color) {
        this.outlineEnabled = true;
        this.outlinePx = Math.max(1, outlinePx);
        if (color != null) this.outlineColor = color;
        return this;
    }

    /**
     * Disables outline rendering.
     */
    public void disableOutline() {
        this.outlineEnabled = false;
    }

    /**
     * @return true if outline is enabled
     */
    public boolean isOutlineEnabled() {
        return outlineEnabled;
    }

    /**
     * @return outline radius in pixels
     */
    public int getOutlinePx() {
        return outlinePx;
    }

    /**
     * @return outline color reference
     */
    public Color getOutlineColor() {
        return outlineColor;
    }

    /**
     * Enables shadow rendering and configures shadow parameters.
     *
     * @param offsetX shadow x offset in pixels
     * @param offsetY shadow y offset in pixels
     * @param color   shadow color (null keeps current)
     * @return this for chaining
     */
    public Font setShadow(float offsetX, float offsetY, Color color) {
        this.shadowEnabled = true;
        this.shadowOffsetX = offsetX;
        this.shadowOffsetY = offsetY;
        if (color != null) this.shadowColor = color;
        return this;
    }

    /**
     * Disables shadow rendering.
     */
    public void disableShadow() {
        this.shadowEnabled = false;
    }

    /**
     * @return true if shadow is enabled
     */
    public boolean isShadowEnabled() {
        return shadowEnabled;
    }

    /**
     * @return shadow x offset in pixels
     */
    public float getShadowOffsetX() {
        return shadowOffsetX;
    }

    /**
     * @return shadow y offset in pixels
     */
    public float getShadowOffsetY() {
        return shadowOffsetY;
    }

    /**
     * @return shadow color reference
     */
    public Color getShadowColor() {
        return shadowColor;
    }

    /**
     * Draws the current {@link #text}.
     *
     * <p>Order: shadow (optional), outline (optional), fill (always).</p>
     */
    public void draw() {
        final String s = this.text;
        if (s == null || s.isEmpty()) return;

        final float texW = texture.getWidth();
        final float texH = texture.getHeight();
        if (texW <= 0f || texH <= 0f) return;

        final float invW = 1f / texW;
        final float invH = 1f / texH;

        batch.begin();

        if (shadowEnabled && shadowColor != null && shadowColor.a() > 0f)
            drawStringPass(s, invW, invH, shadowOffsetX, shadowOffsetY, shadowColor, false, 0);

        if (outlineEnabled && outlinePx > 0 && outlineColor != null && outlineColor.a() > 0f)
            drawStringPass(s, invW, invH, 0f, 0f, outlineColor, true, outlinePx);

        drawStringPass(s, invW, invH, 0f, 0f, texture.getColor(), false, 0);

        batch.end();
    }

    /**
     * Draws one pass of text.
     *
     * @param s         text to draw
     * @param invW      1/atlasWidth
     * @param invH      1/atlasHeight
     * @param passOffX  extra offset applied to all glyphs (x)
     * @param passOffY  extra offset applied to all glyphs (y)
     * @param tint      pass tint
     * @param doOutline true to draw an outline disk per glyph
     * @param outlineR  outline radius in pixels
     */
    private void drawStringPass(String s, float invW, float invH, float passOffX, float passOffY, Color tint, boolean doOutline, int outlineR) {

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

            float baseX = penX + (g.xOffset() * sx) + passOffX;
            float baseY = baselineY - (g.yOffset() * sy) + passOffY;

            float w = gw * sx;
            float h = -(gh * sy);

            float u0 = g.x0() * invW;
            float u1 = g.x1() * invW;
            float v0 = g.y0() * invH;
            float v1 = g.y1() * invH;

            if (!doOutline) {
                batch.drawUV(texture, baseX, baseY, w, h, u0, v0, u1, v1, tint);
            } else {
                final int r = Math.max(1, outlineR);
                final int r2 = r * r;

                // Reuse a single temp color per glyph to avoid allocating per offset.
                final Color tc = new Color(tint.r(), tint.g(), tint.b(), tint.a());

                for (int oy = -r; oy <= r; oy++) {
                    for (int ox = -r; ox <= r; ox++) {
                        if (ox == 0 && oy == 0) continue;

                        int d2 = ox * ox + oy * oy;
                        if (d2 > r2) continue;

                        float aMul;
                        if (r <= 1) {
                            aMul = 1f;
                        } else {
                            float d = (float) Math.sqrt(d2);
                            float t = d / (float) r;

                            float featherBand = 1f / (float) r;
                            aMul = 1f - smoothstep(1f - featherBand, 1f, t);

                            if (t <= 1f - featherBand) aMul = 1f;
                            if (aMul < 0.35f) aMul = 0.35f;
                        }

                        tc.a(tint.a() * aMul);

                        batch.drawUV(texture, baseX + ox, baseY + oy, w, h, u0, v0, u1, v1, tc);
                    }
                }
            }

            penX += g.xAdvance() * sx;
        }
    }

    /**
     * Smooth Hermite interpolation between 0 and 1.
     *
     * @param a low edge
     * @param b high edge
     * @param x sample value
     * @return smoothed value in the range 0..1
     */
    private static float smoothstep(float a, float b, float x) {
        if (x <= a) return 0f;
        if (x >= b) return 1f;
        x = (x - a) / (b - a);
        return x * x * (3f - 2f * x);
    }

    /**
     * @return cached width of the current {@link #text}
     */
    @Override
    public float getWidth() {
        return width;
    }

    /**
     * Measures width of a string without mutating {@link #text}.
     *
     * @param text string to measure
     * @return max line width
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
     * @return cached height of the current {@link #text}
     */
    @Override
    public float getHeight() {
        return height;
    }

    /**
     * Measures height of a string without mutating {@link #text}.
     *
     * @param text string to measure
     * @return total height across lines
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
     * @return current text (never null after construction)
     */
    public String getText() {
        return text;
    }

    /**
     * Sets text and updates cached width/height.
     *
     * @param text new text (null becomes empty)
     */
    public Font setText(String text) {
        this.text = (text == null ? "" : text);
        measureIntoCache(this.text);
        return this;
    }

    /**
     * Sets the pen origin used by {@link #draw()}.
     *
     * @param x pen origin x
     * @param y pen origin y
     */
    public Font setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Sets X/Y scale and refreshes cached vertical metrics and measurement cache.
     *
     * @param sx x scale
     * @param sy y scale
     */
    public Font setScale(float sx, float sy) {
        this.scaleX = sx;
        this.scaleY = sy;

        recalcScaleCaches();
        measureIntoCache(this.text);
        return this;
    }

    /**
     * @return current x scale
     */
    public float getScaleX() {
        return scaleX;
    }

    /**
     * @return current y scale
     */
    public float getScaleY() {
        return scaleY;
    }

    /**
     * @return pen origin x
     */
    public float getX() {
        return x;
    }

    /**
     * Sets pen origin x.
     *
     * @param x pen origin x
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * @return pen origin y
     */
    public float getY() {
        return y;
    }

    /**
     * Sets pen origin y.
     *
     * @param y pen origin y
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * @return fill color reference stored on the backing {@link Texture}
     */
    public Color getColor() {
        return this.texture.getColor();
    }

    /**
     * Sets the fill color for the main pass.
     *
     * @param color new fill color
     */
    public void setColor(Color color) {
        this.texture.setColor(color);
    }

    /**
     * Disposes owned GPU resources.
     *
     * <p>This disposes the atlas {@link Texture}.</p>
     */
    public void dispose() {
        this.data = null;
        this.spaceGlyph = null;
        this.text = null;

        this.texture.dispose();
        this.texture = null;

        // batch.dispose();
    }

    /**
     * Recomputes scaled vertical metrics derived from {@link FontData}.
     */
    private void recalcScaleCaches() {
        lineAdvanceY = data.lineHeight() * scaleY;
        baselineDescentY = data.descent() * scaleY;
        firstLineHeightY = (data.ascent() - data.descent()) * scaleY;
    }

    /**
     * Recomputes base tab width using four spaces.
     */
    private void recalcTabBase() {
        float adv = (spaceGlyph != null) ? spaceGlyph.xAdvance() : fallbackAdvance;
        tabAdvanceBase = adv * 4f;
    }

    /**
     * Measures {@code s} into {@link #width} and {@link #height}.
     *
     * @param s text to measure
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
        this.height = (lines == 1)
                ? firstLineHeightY
                : (firstLineHeightY + (lines - 1) * lineAdvanceY);
    }

    /**
     * @return backing atlas texture
     */
    public Texture getTexture() {
        return texture;
    }
}