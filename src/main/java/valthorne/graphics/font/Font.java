package valthorne.graphics.font;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.ui.Dimensional;

/**
 * A Font class that handles text rendering using texture-based glyphs.
 * This class manages the positioning, scaling, and drawing of text using
 * a texture atlas containing font glyphs. It supports basic text operations
 * like newlines and tabs, and provides methods for text measurement.
 *
 * @author Albert Beaupre
 * @since December 6th, 2025
 */
public class Font implements Dimensional {

    private final Texture texture;
    private final FontData data;

    private float scaleX = 1f;
    private float scaleY = 1f;

    private String text = "";

    private float x;
    private float y;

    private float width;
    private float height;

    /**
     * Creates a new Font instance using the specified font data.
     * The font data contains glyph information and texture data required for rendering.
     *
     * @param data The FontData containing glyph metrics and texture information
     * @throws NullPointerException if the provided FontData is null
     */
    public Font(FontData data) {
        if (data == null) throw new NullPointerException("FontData cannot be null");
        this.data = data;

        this.texture = new Texture(data.textureData());

        this.texture.setScale(1f, -1f);
    }

    /**
     * Draws the current text string using the font's texture atlas.
     * Handles special characters like newlines ('\n') and tabs ('\t').
     * The text is rendered at the current position using the current scale factors.
     */
    public void draw() {
        if (text == null || text.isEmpty()) return;

        float penX = x;

        float baselineY = y - (data.descent() * scaleY);

        final float lineAdvance = data.lineHeight() * scaleY;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                penX = x;
                baselineY -= lineAdvance;
                continue;
            }

            if (c == '\t') {
                Glyph space = data.glyph(' ');
                float adv = (space != null ? space.xAdvance() : (data.lineHeight() * 0.25f));
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

            texture.setRegion(g.x0(), g.y0(), g.x1(), g.y1());

            float drawX = penX + (g.xOffset() * scaleX);
            float drawY = baselineY - (g.yOffset() * scaleY);

            texture.setPosition(drawX, drawY);
            texture.setSize(gw * scaleX, gh * scaleY);
            texture.draw();

            penX += g.xAdvance() * scaleX;
        }
    }

    /**
     * Retrieves the width of the current text rendered by the font.
     *
     * @return The width of the rendered text in pixels.
     */
    public float getWidth() {
        return width;
    }

    /**
     * Calculates the width of the specified text string when rendered.
     * Takes into account the current horizontal scale factor and special characters.
     *
     * @param text The text string to measure
     * @return The width of the text in pixels
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
                float adv = (space != null ? space.xAdvance() : (data.lineHeight() * 0.25f));
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
     * Retrieves the height of the currently rendered text.
     *
     * @return The height of the rendered text in pixels.
     */
    public float getHeight() {
        return height;
    }

    /**
     * Calculates the height of the specified text string when rendered.
     * Takes into account the current vertical scale factor and line breaks.
     *
     * @param text The text string to measure
     * @return The height of the text in pixels
     */
    public float getHeight(String text) {
        if (text == null || text.isEmpty()) return 0f;

        int lines = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;

        float firstLine = (data.ascent() - data.descent()) * scaleY; // descent is negative
        if (lines == 1) return firstLine;

        return firstLine + (lines - 1) * data.lineHeight() * scaleY;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = (text == null ? "" : text);
        this.width = getWidth(this.text);
        this.height = getHeight(this.text);
    }

    /**
     * Sets the position where the text will be rendered.
     *
     * @param x The x-coordinate of the text position
     * @param y The y-coordinate of the text position
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Sets the horizontal and vertical scale factors for text rendering.
     * Updates the cached width and height measurements.
     *
     * @param sx The horizontal scale factor
     * @param sy The vertical scale factor
     */
    public void setScale(float sx, float sy) {
        this.scaleX = sx;
        this.scaleY = sy;

        this.width = getWidth(this.text);
        this.height = getHeight(this.text);
    }

    /**
     * Retrieves the horizontal scale factor for text rendering.
     *
     * @return The current horizontal scale factor.
     */
    public float getScaleX() {return scaleX;}

    /**
     * Retrieves the vertical scale factor for text rendering.
     *
     * @return The current vertical scale factor.
     */
    public float getScaleY() {return scaleY;}

    /**
     * Retrieves the current x-coordinate position where the text is rendered.
     *
     * @return The current x-coordinate of the text position.
     */
    public float getX() {return x;}

    /**
     * Sets the x-coordinate for the text position.
     *
     * @param x The x-coordinate to set
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Retrieves the current y-coordinate position where the text is rendered.
     *
     * @return The current y-coordinate of the text position.
     */
    public float getY() {
        return y;
    }

    /**
     * Sets the y-coordinate for the text position.
     *
     * @param y The y-coordinate to set
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Retrieves the current color associated with the font's texture.
     *
     * @return The color of the font's texture.
     */
    public Color getColor() {
        return this.texture.getColor();
    }

    /**
     * Sets the color used by the font's texture for rendering text.
     *
     * @param color The color to set for the font's texture. This determines the rendering color of the text.
     */
    public void setColor(Color color) {
        this.texture.setColor(color);
    }

    /**
     * Releases resources associated with the font instance.
     * <p>
     * This method sets the current text string to {@code null} and disposes of the
     * texture used by the font. It is essential to invoke this method when the font
     * instance is no longer needed to free up related memory, especially GPU resources.
     * <p>
     * After calling this method, attempts to use the font's texture or interact with
     * related resources may result in undefined behavior.
     */
    public void dispose() {
        text = null;
        texture.dispose();
    }
}
