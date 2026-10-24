package valthorne.graphics.font;

/**
 * Borrowed immutable visual rectangles for a shaped UTF-16 selection. Coordinates
 * are relative to the layout's top-left origin. Bidirectional selections can
 * contain disjoint rectangles and must not be reduced to a pair of caret positions.
 */
public interface TextSelection {
    /**
     * Returns the number of visual rectangles.
     *
     * @return rectangle count
     */
    int size();

    /**
     * Returns a rectangle's left coordinate.
     *
     * @param index visual rectangle index
     * @return left coordinate in layout units
     */
    float left(int index);

    /**
     * Returns a rectangle's top coordinate.
     *
     * @param index visual rectangle index
     * @return top coordinate in layout units
     */
    float top(int index);

    /**
     * Returns a rectangle's horizontal extent.
     *
     * @param index visual rectangle index
     * @return nonnegative width
     */
    float width(int index);

    /**
     * Returns a rectangle's vertical extent.
     *
     * @param index visual rectangle index
     * @return nonnegative height
     */
    float height(int index);
}
