package valthorne.ui.enums;

import valthorne.math.Vector2f;
import valthorne.ui.Dimensional;
import valthorne.ui.Sizeable;

/**
 * @author Albert Beaupre
 * @since February 5th, 2026
 */
public enum Alignment {
    START,
    CENTER,
    END;

    public static Vector2f align(Dimensional source, Sizeable target, Alignment horizontalAlignment, Alignment verticalAlignment) {
        float x = alignHorizontally(source, target, horizontalAlignment);
        float y = alignVertically(source, target, verticalAlignment);

        return new Vector2f(x, y);
    }

    public static Vector2f align(Dimensional source, Sizeable target, Alignment alignment) {
        return align(source, target, alignment, alignment);
    }

    public static float alignHorizontally(Dimensional source, Sizeable target, Alignment alignment) {
        return switch (alignment) {
            case START -> source.getX();
            case END -> source.getX() + source.getWidth() - target.getWidth();
            case CENTER -> source.getX() + (source.getWidth() - target.getWidth()) * 0.5f;
        };
    }

    public static float alignVertically(Dimensional source, Sizeable target, Alignment alignment) {
        return switch (alignment) {
            case END -> source.getY() + source.getHeight() - target.getHeight();
            case START -> source.getY();
            case CENTER -> source.getY() + (source.getHeight() - target.getHeight()) * 0.5f;
        };
    }
}
