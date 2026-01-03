package valthorne.ui;

import valthorne.math.Vector2f;

/**
 * Defines horizontal and vertical alignment combinations used to position
 * elements relative to their container or another element.
 *
 * @author Albert Beaupre
 * @since December 24th, 2025
 */
public enum Alignment {

    CENTER_CENTER(Horizontal.CENTER, Vertical.CENTER),

    TOP_LEFT(Horizontal.LEFT, Vertical.TOP),
    TOP_CENTER(Horizontal.CENTER, Vertical.TOP),
    TOP_RIGHT(Horizontal.RIGHT, Vertical.TOP),

    CENTER_LEFT(Horizontal.LEFT, Vertical.CENTER),
    CENTER_RIGHT(Horizontal.RIGHT, Vertical.CENTER),

    BOTTOM_LEFT(Horizontal.LEFT, Vertical.BOTTOM),
    BOTTOM_CENTER(Horizontal.CENTER, Vertical.BOTTOM),
    BOTTOM_RIGHT(Horizontal.RIGHT, Vertical.BOTTOM);

    public enum Horizontal {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum Vertical {
        TOP,
        CENTER,
        BOTTOM
    }

    private final Horizontal horizontal;
    private final Vertical vertical;

    Alignment(Horizontal horizontal, Vertical vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public Horizontal getHorizontal() {
        return horizontal;
    }

    public Vertical getVertical() {
        return vertical;
    }

    public static Vector2f align(Dimensional source, Sizeable target, Alignment alignment) {
        float x = switch (alignment.horizontal) {
            case LEFT -> source.getX();
            case RIGHT -> source.getX() + source.getWidth() - target.getWidth();
            case CENTER -> source.getX() + (source.getWidth() - target.getWidth()) * 0.5f;
        };

        float y = switch (alignment.vertical) {
            case TOP -> source.getY() + source.getHeight() - target.getHeight();
            case BOTTOM -> source.getY();
            case CENTER -> source.getY() + (source.getHeight() - target.getHeight()) * 0.5f;
        };

        return new Vector2f(x, y);
    }
}
