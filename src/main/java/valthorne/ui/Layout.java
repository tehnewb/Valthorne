package valthorne.ui;

/**
 * Stores CSS-like layout constraints for a UI {@link Element}.
 *
 * <p>This class is a simple, chainable data container. It does not perform layout itself.
 * Your layout engine (ex: {@link Element#layout()} and container layouts like Grid/FlexBox)
 * reads these values and resolves them against parent sizes, content sizes, and other rules.</p>
 *
 * <h2>What this controls</h2>
 * <ul>
 *   <li><b>Position</b>: {@link #x}, {@link #y} (optional constraints)</li>
 *   <li><b>Size</b>: {@link #width}, {@link #height} (pixels/percent/auto/fill)</li>
 *   <li><b>Self alignment</b>: optional override for how this element aligns in a parent container</li>
 * </ul>
 *
 * <h2>Value system</h2>
 * <p>All properties are represented by {@link Value}, which can express:</p>
 * <ul>
 *   <li><b>AUTO</b>: resolved by content or parent rules</li>
 *   <li><b>PIXELS</b>: fixed size</li>
 *   <li><b>PERCENTAGE</b>: relative to parent container size</li>
 *   <li><b>FILL</b>: consume remaining space (container-dependent behavior)</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Layout layout = new Layout()
 *         .setX(Value.alignment(Alignment.CENTER)
 *         .setY(Value.alignment(Alignment.CENTER)
 *         .setWidth(Value.pixels(160))
 *         .setHeight(Value.percentage(50))
 *         .setPadding(Value.pixels(10));
 *
 * Element button = new Button("Play");
 * button.setLayout(layout);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 25th, 2025
 */
public final class Layout {

    private static final Value DEFAULT = Value.auto(); // Default Value assigned to properties that are not explicitly configured.
    private Value leftPadding = DEFAULT;               // Left padding inside the element (space between content and left edge).
    private Value rightPadding = DEFAULT;              // Right padding inside the element (space between content and right edge).
    private Value topPadding = DEFAULT;                // Top padding inside the element (space between content and top edge).
    private Value bottomPadding = DEFAULT;             // Bottom padding inside the element (space between content and bottom edge).
    private Value x = DEFAULT;                         // Optional X-position constraint (layout engine may ignore depending on container).
    private Value y = DEFAULT;                         // Optional Y-position constraint (layout engine may ignore depending on container).
    private Value width = DEFAULT;                     // Width constraint for the element (AUTO/PIXELS/PERCENT/FILL).
    private Value height = DEFAULT;                    // Height constraint for the element (AUTO/PIXELS/PERCENT/FILL).

    /**
     * Returns the bottom padding value.
     *
     * <p>This value represents the internal spacing between the element's content
     * and its bottom edge. Containers typically subtract padding from the available
     * content rectangle before placing children or drawing text.</p>
     *
     * @return the bottom padding {@link Value} (never null unless you set it that way)
     */
    public Value getBottomPadding() {
        return bottomPadding;
    }

    /**
     * Sets the bottom padding value.
     *
     * <p>This does not immediately change the element’s geometry. The layout engine
     * reads this value during the element/container layout pass and resolves it
     * against the element’s current size.</p>
     *
     * @param bottomPadding the new bottom padding {@link Value}
     * @return this layout for chaining
     */
    public Layout setBottomPadding(Value bottomPadding) {
        this.bottomPadding = bottomPadding;
        return this;
    }

    /**
     * Returns the top padding value.
     *
     * <p>This value represents the internal spacing between the element's content
     * and its top edge.</p>
     *
     * @return the top padding {@link Value}
     */
    public Value getTopPadding() {
        return topPadding;
    }

    /**
     * Sets the top padding value.
     *
     * <p>This does not immediately change the element’s geometry. The layout engine
     * resolves this value during layout.</p>
     *
     * @param topPadding the new top padding {@link Value}
     * @return this layout for chaining
     */
    public Layout setTopPadding(Value topPadding) {
        this.topPadding = topPadding;
        return this;
    }

    /**
     * Returns the right padding value.
     *
     * <p>This value represents the internal spacing between the element's content
     * and its right edge.</p>
     *
     * @return the right padding {@link Value}
     */
    public Value getRightPadding() {
        return rightPadding;
    }

    /**
     * Sets the right padding value.
     *
     * <p>This does not immediately change the element’s geometry. The layout engine
     * resolves this value during layout.</p>
     *
     * @param rightPadding the new right padding {@link Value}
     * @return this layout for chaining
     */
    public Layout setRightPadding(Value rightPadding) {
        this.rightPadding = rightPadding;
        return this;
    }

    /**
     * Returns the left padding value.
     *
     * <p>This value represents the internal spacing between the element's content
     * and its left edge.</p>
     *
     * @return the left padding {@link Value}
     */
    public Value getLeftPadding() {
        return leftPadding;
    }

    /**
     * Sets the left padding value.
     *
     * <p>This does not immediately change the element’s geometry. The layout engine
     * resolves this value during layout.</p>
     *
     * @param leftPadding the new left padding {@link Value}
     * @return this layout for chaining
     */
    public Layout setLeftPadding(Value leftPadding) {
        this.leftPadding = leftPadding;
        return this;
    }

    /**
     * Sets uniform padding for all four sides.
     *
     * <p>This is equivalent to calling:</p>
     * <pre>{@code
     * setLeftPadding(p);
     * setRightPadding(p);
     * setTopPadding(p);
     * setBottomPadding(p);
     * }</pre>
     *
     * @param padding the padding {@link Value} to apply to every side
     * @return this layout for chaining
     */
    public Layout setPadding(Value padding) {
        this.leftPadding = padding;
        this.rightPadding = padding;
        this.topPadding = padding;
        this.bottomPadding = padding;
        return this;
    }

    /**
     * Returns the X-position constraint value.
     *
     * <p>Whether this is used depends on the container/layout policy.
     * Absolute-positioning containers may respect it; flow containers may ignore it.</p>
     *
     * @return x constraint {@link Value}
     */
    public Value getX() {
        return x;
    }

    /**
     * Sets the X-position constraint value.
     *
     * <p>This does not move anything immediately. A layout pass must read and apply it.</p>
     *
     * @param x the new x constraint {@link Value}
     * @return this layout for chaining
     */
    public Layout setX(Value x) {
        this.x = x;
        return this;
    }

    /**
     * Returns the Y-position constraint value.
     *
     * <p>Whether this is used depends on the container/layout policy.</p>
     *
     * @return y constraint {@link Value}
     */
    public Value getY() {
        return y;
    }

    /**
     * Sets the Y-position constraint value.
     *
     * <p>This does not move anything immediately. A layout pass must read and apply it.</p>
     *
     * @param y the new y constraint {@link Value}
     * @return this layout for chaining
     */
    public Layout setY(Value y) {
        this.y = y;
        return this;
    }

    /**
     * Returns the width constraint value.
     *
     * <p>{@link ValueType#AUTO} typically means "size to content" unless overridden by a container.
     * {@link ValueType#FILL} typically means "take remaining space" (container-dependent).</p>
     *
     * @return width constraint {@link Value}
     */
    public Value getWidth() {
        return width;
    }

    /**
     * Sets the width constraint value.
     *
     * <p>This does not resize anything immediately. A layout pass must resolve and apply it.</p>
     *
     * @param width the new width constraint {@link Value}
     * @return this layout for chaining
     */
    public Layout setWidth(Value width) {
        this.width = width;
        return this;
    }

    /**
     * Returns the height constraint value.
     *
     * <p>{@link ValueType#AUTO} typically means "size to content" unless overridden by a container.
     * {@link ValueType#FILL} typically means "take remaining space" (container-dependent).</p>
     *
     * @return height constraint {@link Value}
     */
    public Value getHeight() {
        return height;
    }

    /**
     * Sets the height constraint value.
     *
     * <p>This does not resize anything immediately. A layout pass must resolve and apply it.</p>
     *
     * @param height the new height constraint {@link Value}
     * @return this layout for chaining
     */
    public Layout setHeight(Value height) {
        this.height = height;
        return this;
    }
}
