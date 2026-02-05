package valthorne.ui;

import valthorne.math.Vector2f;

/**
 * Defines combined horizontal and vertical alignment modes used to position one element
 * relative to another element or container.
 *
 * <p>This enum is conceptually similar to CSS alignment rules. Each constant represents
 * a pairing of a {@link HorizontalAlignment} and a {@link VerticalAlignment}.</p>
 *
 * <p>The primary utility provided by this enum is {@link #align(Dimensional, Sizeable, Alignment)},
 * which computes the top-left position needed to place a target element inside a source
 * element according to the requested alignment.</p>
 *
 * <h2>Coordinate assumptions</h2>
 * <ul>
 *     <li>X increases to the right</li>
 *     <li>Y increases upward</li>
 *     <li>{@code source.getX()/getY()} refers to the bottom-left of the source</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Center a button inside a panel
 * Vector2f pos = Alignment.align(panel, button, Alignment.CENTER_CENTER);
 * button.setPosition(pos.getX(), pos.getY());
 *
 * // Align text to the top-left of a container
 * Vector2f textPos = Alignment.align(container, font, Alignment.TOP_LEFT);
 * font.setPosition(textPos.getX(), textPos.getY());
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 5th, 2026
 */
public enum Alignment {

    /**
     * Centers the target both horizontally and vertically within the source.
     */
    CENTER_CENTER(HorizontalAlignment.CENTER, VerticalAlignment.CENTER),

    /**
     * Aligns the target to the top-left corner of the source.
     */
    TOP_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.TOP),

    /**
     * Aligns the target to the top-center of the source.
     */
    TOP_CENTER(HorizontalAlignment.CENTER, VerticalAlignment.TOP),

    /**
     * Aligns the target to the top-right corner of the source.
     */
    TOP_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.TOP),

    /**
     * Aligns the target to the center-left of the source.
     */
    CENTER_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.CENTER),

    /**
     * Aligns the target to the center-right of the source.
     */
    CENTER_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER),

    /**
     * Aligns the target to the bottom-left corner of the source.
     */
    BOTTOM_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM),

    /**
     * Aligns the target to the bottom-center of the source.
     */
    BOTTOM_CENTER(HorizontalAlignment.CENTER, VerticalAlignment.BOTTOM),

    /**
     * Aligns the target to the bottom-right corner of the source.
     */
    BOTTOM_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.BOTTOM);

    private final HorizontalAlignment horizontalAlignment; // Horizontal alignment component (LEFT, CENTER, RIGHT).
    private final VerticalAlignment verticalAlignment;     // Vertical alignment component (TOP, CENTER, BOTTOM).

    /**
     * Creates a combined alignment from horizontal and vertical components.
     *
     * @param horizontalAlignment horizontal alignment component
     * @param verticalAlignment   vertical alignment component
     */
    Alignment(HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
        this.horizontalAlignment = horizontalAlignment;
        this.verticalAlignment = verticalAlignment;
    }

    /**
     * Computes the top-left position required to align a target inside a source.
     *
     * <p>The returned position represents where the target should be placed so that
     * it satisfies the specified {@link Alignment} relative to the source.</p>
     *
     * <p>Rules applied:</p>
     * <ul>
     *     <li>LEFT / RIGHT / CENTER determine horizontal offset</li>
     *     <li>TOP / BOTTOM / CENTER determine vertical offset</li>
     *     <li>Target size is subtracted where needed to keep it inside the source</li>
     * </ul>
     *
     * <p>This method does not mutate either object.</p>
     *
     * @param source     the container or reference element providing position and size
     * @param target     the element being positioned inside the source
     * @param alignment  the desired alignment mode
     * @return a {@link Vector2f} containing the computed x/y position for the target
     */
    public static Vector2f align(Dimensional source, Sizeable target, Alignment alignment) {
        float x = switch (alignment.horizontalAlignment) {
            case LEFT -> source.getX();
            case RIGHT -> source.getX() + source.getWidth() - target.getWidth();
            case CENTER -> source.getX() + (source.getWidth() - target.getWidth()) * 0.5f;
        };

        float y = switch (alignment.verticalAlignment) {
            case TOP -> source.getY() + source.getHeight() - target.getHeight();
            case BOTTOM -> source.getY();
            case CENTER -> source.getY() + (source.getHeight() - target.getHeight()) * 0.5f;
        };

        return new Vector2f(x, y);
    }

    /**
     * Returns the horizontal alignment component of this alignment.
     *
     * @return horizontal alignment (LEFT, CENTER, RIGHT)
     */
    public HorizontalAlignment getHorizontal() {
        return horizontalAlignment;
    }

    /**
     * Returns the vertical alignment component of this alignment.
     *
     * @return vertical alignment (TOP, CENTER, BOTTOM)
     */
    public VerticalAlignment getVertical() {
        return verticalAlignment;
    }
}
