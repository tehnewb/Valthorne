package valthorne.ui;

/**
 * Represents a layout configuration that defines padding, position, and size properties for an element.
 * <p>
 * The Layout class allows customization of padding for all sides (left, right, top, bottom),
 * as well as position (x, y) and dimensions (width, height). Each property is represented by a {@link Value}.
 * Default values are applied when properties are not explicitly specified.
 * <p>
 * This class is designed to use a fluent API style, enabling method chaining for property configuration.
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
}