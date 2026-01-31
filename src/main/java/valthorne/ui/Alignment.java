package valthorne.ui;

import valthorne.math.Vector2f;

/**
 * Defines horizontalAlignment and verticalAlignment alignment combinations used to position
 * elements relative to their container or another element.
 *
 * @author Albert Beaupre
 * @since December 24th, 2025
 */
public enum Alignment {

    /**
     * Represents an alignment configuration where both horizontal and vertical alignments
     * are centered. Specifically, the horizontal alignment is set to {@code HorizontalAlignment.CENTER}
     * and the vertical alignment is set to {@code VerticalAlignment.CENTER}.
     * <p>
     * This alignment is commonly used to position elements in the exact center
     * of a container or a specific area.
     */
    CENTER_CENTER(HorizontalAlignment.CENTER, VerticalAlignment.CENTER),

    /**
     * Represents an alignment configuration where horizontal alignment is set to {@code HorizontalAlignment.LEFT}
     * and vertical alignment is set to {@code VerticalAlignment.TOP}.
     * <p>
     * This alignment is typically used to position elements at the top-left corner of a container or another element.
     */
    TOP_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.TOP),
    TOP_CENTER(HorizontalAlignment.CENTER, VerticalAlignment.TOP),
    TOP_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.TOP),

    /**
     * Represents an alignment configuration where horizontal alignment is set to {@code HorizontalAlignment.LEFT}
     * and vertical alignment is set to {@code VerticalAlignment.CENTER}.
     * <p>
     * This alignment is typically used to position elements along the center of a container vertically,
     * while aligning them to the left horizontally.
     */
    CENTER_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.CENTER),
    CENTER_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER),

    /**
     * Represents an alignment configuration where the horizontal alignment is set to the leftmost position
     * and the vertical alignment is set to the bottommost position.
     * <p>
     * This combination is useful when aligning elements towards the bottom-left corner
     * relative to a specified reference.
     */
    BOTTOM_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM),
    BOTTOM_CENTER(HorizontalAlignment.CENTER, VerticalAlignment.BOTTOM),
    BOTTOM_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.BOTTOM);


    private final HorizontalAlignment horizontalAlignment;
    private final VerticalAlignment verticalAlignment;

    Alignment(HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
        this.horizontalAlignment = horizontalAlignment;
        this.verticalAlignment = verticalAlignment;
    }

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

    public HorizontalAlignment getHorizontal() {
        return horizontalAlignment;
    }

    public VerticalAlignment getVertical() {
        return verticalAlignment;
    }
}
