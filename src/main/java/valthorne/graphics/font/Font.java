package valthorne.graphics.font;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.ui.Dimensional;

/**
 * The Font class represents a renderable font, enabling textual content
 * to be drawn using predefined font metrics and glyph data from a
 * FontData instance. The class also provides scaling, positioning, and
 * color customization options for the rendered text.
 * <p>
 * This class relies on a Texture for rendering glyphs and requires
 * a non-null FontData to define the font metrics and glyph metadata.
 *
 * @author Albert Beaupre
 * @since December 6th, 2025
 */
public class Font implements Dimensional {

    private final Texture texture;
    private final FontData data;

    private float scaleX = 1f;
    private float scaleY = 1f;
    private String text;
    private float x;
    private float y;

    private float width, height;

    /**
     * Constructs a new {@code Font} object using the specified {@code FontData}.
     *
     * @param data the {@code FontData} containing font metrics and texture information;
     *             must not be {@code null}
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public Font(FontData data) {
        if (data == null)
            throw new NullPointerException("FontData cannot be null");
        this.data = data;
        this.texture = new Texture(data.textureData());
        this.texture.setScale(1f, -1f);
    }

    /**
     * Renders text using the defined font data and texture settings.
     * <p>
     * This method draws each character in the current {@code text} string
     * at the specified position ({@code x}, {@code y}), applying the corresponding
     * glyph metadata from the {@code FontData} instance. Characters are rendered
     * sequentially, starting from the given position, and advancing horizontally
     * or vertically depending on newline ('\n') and tab ('\t') characters.
     * If a glyph for a character is missing, it skips the character or applies
     * a default spacing.
     * <p>
     * Behavior:
     * - Handles newline ('\n') by moving to a new line based on line height.
     * - Handles tab ('\t') by adding four times the advance width of a space glyph.
     * - For each character, retrieves associated glyph data to determine position,
     * size, offsets, and texture region to render.
     * - If the glyph for a character is not found, a default spacing is added based
     * on the font's line height.
     * - Skips rendering for glyphs with a width or height of zero.
     */
    public void draw() {
        if (text == null || text.isEmpty()) return;

        float penX = x;
        float baselineY = y;

        float lineAdvance = data.lineHeight() * scaleY;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                penX = x;
                baselineY -= lineAdvance;
                continue;
            }

            if (c == '\t') {
                Glyph space = data.glyph(' ');
                float adv = (space != null ? space.xAdvance() : data.lineHeight() * 0.25f);
                penX += (adv * 4f) * scaleX;
                continue;
            }

            Glyph g = data.glyph(c);
            if (g == null) {
                penX += (data.lineHeight() * 0.25f) * scaleX;
                continue;
            }

            int gw = g.width();
            int gh = g.height();

            if (gw <= 0 || gh <= 0) {
                penX += g.xAdvance() * scaleX;
                continue;
            }

            float left = g.x0();
            float top = g.y0();
            float right = g.x1();
            float bottom = g.y1();

            texture.setRegion(left, top, right, bottom);

            float drawX = penX + (g.xOffset() * scaleX);
            float drawY = baselineY - g.yOffset() * scaleY;

            texture.setSize(gw * scaleX, gh * scaleY);
            texture.setPosition(drawX, drawY);
            texture.draw();

            penX += g.xAdvance() * scaleX;
        }
    }

    /**
     * Retrieves the width of the font, which represents the predefined maximum
     * width available for rendering text using this font instance.
     *
     * @return the width of the font as a float
     */
    public float getWidth() {
        return width;
    }

    /**
     * Calculates the maximum width of the given text string when rendered using the font's glyph data.
     * The width is determined based on the horizontal advances of each character in the string,
     * accounting for newline ('\n') and tab ('\t') characters. Newline resets the current line's width,
     * and tab adds a spacing equivalent to four times the advance width of a space character.
     *
     * @param text the text string for which the width is to be calculated; if null or empty, the method returns 0
     * @return the maximum width of the text string as a float, considering the font's scale and glyph data
     */
    public float getWidth(String text) {
        if (text == null || text.isEmpty()) return 0f;

        float penX = 0f;
        float maxX = 0f;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                if (penX > maxX) maxX = penX;
                penX = 0f;
                continue;
            }

            if (c == '\t') {
                Glyph space = data.glyph(' ');
                float adv = (space != null ? space.xAdvance() : data.lineHeight() * 0.25f);
                penX += (adv * 4f) * scaleX;
                continue;
            }

            Glyph g = data.glyph(c);
            if (g == null) {
                penX += (data.lineHeight() * 0.25f) * scaleX;
                continue;
            }

            penX += g.xAdvance() * scaleX;
        }

        if (penX > maxX) maxX = penX;
        return maxX;
    }

    /**
     * Retrieves the height of the font, which represents the predefined maximum
     * height available for rendering text using this font instance.
     *
     * @return the height of the font as a float
     */
    public float getHeight() {
        return height;
    }

    /**
     * Calculates the height required to render the specified text string, based on
     * the font's ascent and vertical scale. The method multiplies the font's ascent
     * with the number of lines in the text and applies the vertical scale factor.
     * If the text is null or empty, the method returns 0.
     *
     * @param text the input text string for which the height is to be calculated;
     *             if null or empty, the method returns 0
     * @return the calculated height of the text as a float
     */
    public float getHeight(String text) {
        if (text == null || text.isEmpty()) return 0f;

        return data.ascent() * text.lines().count() * scaleY;
    }

    public String getText() {
        return text;
    }

    /**
     * Sets the text to be rendered by the font and calculates its dimensions (width and height)
     * based on the font's glyph data and scale. The width is calculated as the maximum
     * horizontal advance of all lines in the text, while the height is based on the number of lines
     * and the font's ascent and vertical scale. The new text string updates the rendering logic,
     * and dimensions are updated accordingly.
     *
     * @param text the new text to be used for rendering; can be null or empty. If null, the
     *             text is treated as empty, and the dimensions are set to zero.
     */
    public void setText(String text) {
        this.text = text;
        this.width = getWidth(text);
        this.height = getHeight(text);
    }

    /**
     * Sets the position of the font to the specified coordinates.
     *
     * @param x the x-coordinate of the position
     * @param y the y-coordinate of the position
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Sets the scale factors for rendering fonts. The scale factors determine the horizontal
     * and vertical scaling applied to the font's dimensions when rendered. These values are
     * applied globally, affecting all text rendered using this font instance.
     *
     * @param sx the horizontal scaling factor
     * @param sy the vertical scaling factor
     */
    public void setScale(float sx, float sy) {
        this.scaleX = sx;
        this.scaleY = sy;
    }

    /**
     * Retrieves the horizontal scaling factor of the font. The scale factor is used to
     * determine how the width of the font is adjusted during rendering.
     *
     * @return the horizontal scale factor as a float
     */
    public float getScaleX() {return scaleX;}

    /**
     * Retrieves the vertical scaling factor of the font. The scale factor determines
     * how the height of the font is adjusted during rendering.
     *
     * @return the vertical scale factor as a float
     */
    public float getScaleY() {return scaleY;}

    /**
     * Retrieves the x-coordinate of the font's position. The x-coordinate determines
     * the horizontal placement of the font for rendering.
     *
     * @return the x-coordinate as a float
     */
    public float getX() {return x;}

    /**
     * Sets the x-coordinate of the font's position.
     *
     * @param x the new x-coordinate value
     */
    public void setX(float x) {this.x = x;}

    /**
     * Retrieves the y-coordinate of the font's position. The y-coordinate determines
     * the vertical placement of the font for rendering.
     *
     * @return the y-coordinate as a float
     */
    public float getY() {return y;}

    /**
     * Sets the y-coordinate of the font's position.
     *
     * @param y the new y-coordinate value
     */
    public void setY(float y) {this.y = y;}

    /**
     * Retrieves the color associated with the font's texture, which determines
     * how the font will be rendered in terms of its appearance or visual style.
     *
     * @return the {@code Color} object used for rendering the font
     */
    public Color getColor() {
        return this.texture.getColor();
    }

    /**
     * Sets the color used to render the font. This color is applied to the font's texture,
     * modifying its appearance based on the specified color values (e.g., red, green, blue, alpha).
     *
     * @param color the {@code Color} object defining the desired rendering color;
     *              must not be {@code null}
     * @throws NullPointerException if {@code color} is {@code null}
     */
    public void setColor(Color color) {
        this.texture.setColor(color);
    }

    /**
     * Releases resources associated with this font instance.
     * <p>
     * This method disposes of the texture used by the font, ensuring that the
     * associated GPU resources are properly released. After calling this method,
     * the font's texture becomes unusable, and any attempts to render text using
     * this font may result in undefined behavior or errors.
     * <p>
     * It is essential to call this method when the font is no longer needed to
     * avoid memory leaks or excessive GPU resource usage.
     */
    public void dispose() {
        text = null;
        texture.dispose();
    }
}
