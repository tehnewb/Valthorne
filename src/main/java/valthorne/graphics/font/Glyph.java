package valthorne.graphics.font;

/**
 * Represents a glyph in a font, containing positional and offset information.
 * <p>
 * This class is a record that stores data about a glyph's character, its
 * bounding box coordinates, and its positional offsets.
 * It is used to describe glyphs for rendering text in graphical applications.
 */
public record Glyph(char character, int x0, int y0, int x1, int y1, float xOffset, float yOffset, float xOffset2, float yOffset2, float xAdvance) {

    /**
     * Calculates the width of the glyph's bounding box.
     *
     * @return the width of the bounding box, computed as the difference between x1 and x0
     */
    public int width() {
        return x1 - x0;
    }

    /**
     * Calculates the height of the glyph's bounding box.
     *
     * @return the height of the bounding box, computed as the difference between y1 and y0
     */
    public int height() {
        return y1 - y0;
    }
}