package valthorne.ui;


/**
 * A class that manages layout properties for UI elements, providing control over positioning,
 * dimensions, padding, and margins. Each layout property is represented by a {@link Value} object
 * that defines how the property should be calculated or constrained.
 * <p>
 * Key features:
 * <ul>
 *   <li>Position control (x, y coordinates)</li>
 *   <li>Dimension management (width, height)</li>
 *   <li>Padding on all sides (left, right, top, bottom)</li>
 *   <li>Margin control on all sides (left, right, top, bottom)</li>
 * </ul>
 * <p>
 * All properties use a {@link Value} system that supports different types of measurements:
 * <ul>
 *   <li>Auto - Automatically determined based on content</li>
 *   <li>Pixels - Fixed pixel values</li>
 *   <li>Percentage - Relative to parent container</li>
 *   <li>Fill - Fills available space</li>
 * </ul>
 * <p>
 * Layout properties are constrained values, meaning once set, the element they are bound to
 * will maintain these values during layout calculations. The layout system ensures that
 * elements respect their assigned constraints while maintaining proper positioning and
 * sizing within their parent containers.
 * <p>
 * Example usage:
 * <pre>
 * Layout layout = new Layout();
 * layout.setWidth(Value.pixels(100))
 *       .setHeight(Value.percentage(50))
 *       .setPadding(Value.pixels(10))
 *       .setMargins(Value.auto());
 * </pre>
 *
 * @author Albert Beaupre
 * @since December 25th, 2025
 */
public final class Layout {

    /**
     * The default value used for all layout properties if isn't explicitly set
     */
    private static final Value Default = Value.auto();

    /**
     * Padding values for all four sides of the element
     */
    private Value leftPadding = Default;
    private Value rightPadding = Default;
    private Value topPadding = Default;
    private Value bottomPadding = Default;

    /**
     * Position coordinates and dimension values
     */
    private Value x = Default, y = Default;
    private Value width = Default, height = Default;
    private Value leftMargin = Default, rightMargin = Default, topMargin = Default, bottomMargin = Default;

    /**
     * Gets the bottom padding value of this layout.
     *
     * @return the current bottom padding as a {@link Value}
     */
    public Value getBottomPadding() {
        return bottomPadding;
    }

    /**
     * Sets the bottom padding of this layout.
     *
     * @param bottomPadding the new bottom padding value
     * @return this Layout instance for method chaining
     */
    public Layout setBottomPadding(Value bottomPadding) {
        this.bottomPadding = bottomPadding;
        return this;
    }

    /**
     * Gets the top padding value of this layout.
     *
     * @return the current top padding as a {@link Value}
     */
    public Value getTopPadding() {
        return topPadding;
    }

    /**
     * Sets the top padding of this layout.
     *
     * @param topPadding the new top padding value
     * @return this Layout instance for method chaining
     */
    public Layout setTopPadding(Value topPadding) {
        this.topPadding = topPadding;
        return this;
    }

    /**
     * Gets the right padding value of this layout.
     *
     * @return the current right padding as a {@link Value}
     */
    public Value getRightPadding() {
        return rightPadding;
    }

    /**
     * Sets the right padding of this layout.
     *
     * @param rightPadding the new right padding value
     * @return this Layout instance for method chaining
     */
    public Layout setRightPadding(Value rightPadding) {
        this.rightPadding = rightPadding;
        return this;
    }

    /**
     * Gets the left padding value of this layout.
     *
     * @return the current left padding as a {@link Value}
     */
    public Value getLeftPadding() {
        return leftPadding;
    }

    /**
     * Sets the left padding of this layout.
     *
     * @param leftPadding the new left padding value
     * @return this Layout instance for method chaining
     */
    public Layout setLeftPadding(Value leftPadding) {
        this.leftPadding = leftPadding;
        return this;
    }

    /**
     * Sets uniform padding for all sides of the layout simultaneously.
     *
     * @param padding the padding value to apply to all sides
     * @return this Layout instance for method chaining
     */
    public Layout setPadding(Value padding) {
        this.leftPadding = padding;
        this.rightPadding = padding;
        this.topPadding = padding;
        this.bottomPadding = padding;
        return this;
    }

    /**
     * Gets the x-coordinate value of this layout.
     *
     * @return the current x position as a {@link Value}
     */
    public Value getX() {
        return x;
    }

    /**
     * Sets the x-coordinate of this layout.
     *
     * @param x the new x position value
     * @return this Layout instance for method chaining
     */
    public Layout setX(Value x) {
        this.x = x;
        return this;
    }

    /**
     * Gets the y-coordinate value of this layout.
     *
     * @return the current y position as a {@link Value}
     */
    public Value getY() {
        return y;
    }

    /**
     * Sets the y-coordinate of this layout.
     *
     * @param y the new y position value
     * @return this Layout instance for method chaining
     */
    public Layout setY(Value y) {
        this.y = y;
        return this;
    }

    /**
     * Gets the width value of this layout.
     *
     * @return the current width as a {@link Value}
     */
    public Value getWidth() {
        return width;
    }

    /**
     * Sets the width of this layout.
     *
     * @param width the new width value
     * @return this Layout instance for method chaining
     */
    public Layout setWidth(Value width) {
        this.width = width;
        return this;
    }

    /**
     * Gets the height value of this layout.
     *
     * @return the current height as a {@link Value}
     */
    public Value getHeight() {
        return height;
    }

    /**
     * Sets the height of this layout.
     *
     * @param height the new height value
     * @return this Layout instance for method chaining
     */
    public Layout setHeight(Value height) {
        this.height = height;
        return this;
    }

    /**
     * Sets the margins of this layout for the left, top, right, and bottom sides.
     *
     * @param left   the margin value for the left side
     * @param top    the margin value for the top side
     * @param right  the margin value for the right side
     * @param bottom the margin value for the bottom side
     * @return this Layout instance for method chaining
     */
    public Layout setMargins(Value left, Value top, Value right, Value bottom) {
        this.leftMargin = left;
        this.topMargin = top;
        this.rightMargin = right;
        this.bottomMargin = bottom;
        return this;
    }

    /**
     * Sets uniform margins for all sides of the layout (left, top, right, and bottom)
     * using the specified margin value.
     *
     * @param margin the margin value to apply uniformly to all sides
     * @return this Layout instance for method chaining
     */
    public Layout setMargins(Value margin) {
        this.leftMargin = margin;
        this.topMargin = margin;
        this.rightMargin = margin;
        this.bottomMargin = margin;
        return this;
    }

    /**
     * Retrieves the left margin value of this layout.
     *
     * @return the current left margin as a {@link Value}
     */
    public Value getLeftMargin() {
        return leftMargin;
    }

    /**
     * Sets the left margin of this layout to the specified value.
     *
     * @param leftMargin the new left margin value as a {@link Value}
     * @return this Layout instance for method chaining
     */
    public Layout setLeftMargin(Value leftMargin) {
        this.leftMargin = leftMargin;
        return this;
    }

    /**
     * Retrieves the top margin value of this layout.
     *
     * @return the current top margin as a {@link Value}
     */
    public Value getTopMargin() {
        return topMargin;
    }

    /**
     * Sets the top margin of this layout to the specified value.
     *
     * @param topMargin the new top margin value as a {@link Value}
     * @return this Layout instance for method chaining
     */
    public Layout setTopMargin(Value topMargin) {
        this.topMargin = topMargin;
        return this;
    }

    /**
     * Retrieves the right margin value of this layout.
     *
     * @return the current right margin as a {@link Value}
     */
    public Value getRightMargin() {
        return rightMargin;
    }

    /**
     * Sets the right margin of this layout to the specified value.
     *
     * @param rightMargin the new right margin value as a {@link Value}
     * @return this Layout instance for method chaining
     */
    public Layout setRightMargin(Value rightMargin) {
        this.rightMargin = rightMargin;
        return this;
    }

    /**
     * Retrieves the bottom margin value of this layout.
     *
     * @return the current bottom margin as a {@link Value}
     */
    public Value getBottomMargin() {
        return bottomMargin;
    }

    /**
     * Sets the bottom margin of this layout to the specified value.
     *
     * @param bottomMargin the new bottom margin value as a {@link Value}
     * @return this Layout instance for method chaining
     */
    public Layout setBottomMargin(Value bottomMargin) {
        this.bottomMargin = bottomMargin;
        return this;
    }
}