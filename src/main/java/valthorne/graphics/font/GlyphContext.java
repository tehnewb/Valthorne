package valthorne.graphics.font;

/**
 * Represents context information for a glyph in the text rendering pipeline.
 * <p>
 * This class encapsulates metadata about a glyph, including its character,
 * position within the text layout, and layout bounding properties. It is used
 * to manage and retrieve glyph-specific information during rendering and styling.
 *
 * @author Albert Beaupre
 * @since March 5th, 2026
 */
public class GlyphContext {
    private char character; // The character represented by this glyph
    private int charIndex;  // The index of the character in the original text
    private int glyphIndex; // The index of the glyph in the rendered sequence
    private int lineIndex;  // The index of the line in the text layout
    private float baseX;    // The base x-coordinate of the glyph in layout space
    private float baseY;    // The base y-coordinate of the glyph in layout space
    private float baseW;    // The width of the glyph's bounding box in layout space
    private float baseH;    // The height of the glyph's bounding box in layout space
    private float drawX;    // The x-coordinate of the glyph in drawing space
    private float drawY;    // The y-coordinate of the glyph in drawing space

    /**
     * Retrieves the character represented by this glyph.
     *
     * @return the character.
     */
    public char character() {return character;}

    /**
     * Retrieves the index of the character within the original text.
     *
     * @return the character index.
     */
    public int charIndex() {return charIndex;}

    /**
     * Retrieves the index of the glyph in the rendered sequence.
     *
     * @return the glyph index.
     */
    public int glyphIndex() {return glyphIndex;}

    /**
     * Retrieves the index of the line in the text layout where this glyph is located.
     *
     * @return the line index.
     */
    public int lineIndex() {return lineIndex;}

    /**
     * Retrieves the base x-coordinate of the glyph in layout space.
     *
     * @return the base x-coordinate.
     */
    public float baseX() {return baseX;}

    /**
     * Retrieves the base y-coordinate of the glyph in layout space.
     *
     * @return the base y-coordinate.
     */
    public float baseY() {return baseY;}

    /**
     * Retrieves the width of the glyph's bounding box in layout space.
     *
     * @return the base width.
     */
    public float baseW() {return baseW;}

    /**
     * Retrieves the height of the glyph's bounding box in layout space.
     *
     * @return the base height.
     */
    public float baseH() {return baseH;}

    /**
     * Retrieves the x-coordinate of the glyph in drawing space.
     *
     * @return the draw x-coordinate.
     */
    public float drawX() {return drawX;}

    /**
     * Retrieves the y-coordinate of the glyph in drawing space.
     *
     * @return the draw y-coordinate.
     */
    public float drawY() {return drawY;}

    /**
     * Updates all properties of the glyph context with the provided values.
     *
     * @param c          the character represented by the glyph.
     * @param charIndex  the index of the character in the original text.
     * @param glyphIndex the index of the glyph in the rendered sequence.
     * @param lineIndex  the index of the line in the text layout.
     * @param baseX      the base x-coordinate in layout space.
     * @param baseY      the base y-coordinate in layout space.
     * @param baseW      the width of the glyph's bounding box in layout space.
     * @param baseH      the height of the glyph's bounding box in layout space.
     * @param drawX      the x-coordinate in drawing space.
     * @param drawY      the y-coordinate in drawing space.
     */
    public void set(char c, int charIndex, int glyphIndex, int lineIndex, float baseX, float baseY, float baseW, float baseH, float drawX, float drawY) {
        this.character = c;
        this.charIndex = charIndex;
        this.glyphIndex = glyphIndex;
        this.lineIndex = lineIndex;
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseW = baseW;
        this.baseH = baseH;
        this.drawX = drawX;
        this.drawY = drawY;
    }
}