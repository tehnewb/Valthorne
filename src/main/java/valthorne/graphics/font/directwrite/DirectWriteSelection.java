package valthorne.graphics.font.directwrite;

/**
 * Immutable native hit-test rectangles for a logical selection. Bidirectional text
 * can produce multiple disjoint visible rectangles for one UTF-16 range. Coordinates
 * are relative to the layout's top-left origin in DIPs; callers must not substitute
 * a rectangle between two caret X values for these shaped visual ranges.
 */
public final class DirectWriteSelection {
    private final float[] boxes; // Packed left/top/width/height coordinates of each native selection rectangle.

    /**
     * Takes ownership of a newly allocated native-result snapshot.
     *
     * @param boxes packed four-float rectangles
     */
    DirectWriteSelection(float[] boxes) { this.boxes = boxes; }

    /**
     * Returns the number of disjoint visual ranges.
     *
     * @return native rectangle count
     */
    public int size() { return boxes.length / 4; }

    /**
     * Returns a visual range's horizontal coordinate.
     *
     * @param index rectangle index
     * @return left edge in DIPs
     */
    public float left(int index) { return boxes[index * 4]; }

    /**
     * Returns a visual range's vertical coordinate.
     *
     * @param index rectangle index
     * @return top edge in DIPs
     */
    public float top(int index) { return boxes[index * 4 + 1]; }

    /**
     * Returns a visual range's horizontal extent.
     *
     * @param index rectangle index
     * @return width in DIPs
     */
    public float width(int index) { return boxes[index * 4 + 2]; }

    /**
     * Returns a visual range's line-box height.
     *
     * @param index rectangle index
     * @return height in DIPs
     */
    public float height(int index) { return boxes[index * 4 + 3]; }
}
