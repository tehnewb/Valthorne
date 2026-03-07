package valthorne.ui.enums;

import valthorne.math.Vector2f;
import valthorne.ui.Dimensional;
import valthorne.ui.Sizeable;

/**
 * Defines simple horizontal and vertical alignment modes used to position one object relative
 * to another object.
 *
 * <p>
 * This enum is primarily used by UI and rendering code that needs to align a target object
 * inside or against a source object. The source provides a world-space position and dimensions
 * through {@link Dimensional}, while the target provides its size through {@link Sizeable}.
 * The alignment methods then compute the final x and y coordinates where the target should be
 * placed.
 * </p>
 *
 * <h2>Supported alignment modes</h2>
 * <ul>
 *     <li>{@link #START} aligns to the left edge horizontally or bottom edge vertically</li>
 *     <li>{@link #CENTER} aligns to the center along the requested axis</li>
 *     <li>{@link #END} aligns to the right edge horizontally or top edge vertically</li>
 * </ul>
 *
 * <h2>How this works</h2>
 * <p>
 * The alignment methods do not move either object directly. They only calculate coordinates.
 * This is useful when you want to position text, textures, buttons, icons, or other UI content
 * inside another element without duplicating alignment math all over the codebase.
 * </p>
 *
 * <p>
 * For example, if a button is 200 pixels wide and the text inside it is 80 pixels wide:
 * </p>
 * <ul>
 *     <li>{@code START} places the text at the button's x position</li>
 *     <li>{@code CENTER} places the text so equal space appears on both sides</li>
 *     <li>{@code END} places the text so its right edge touches the button's right edge</li>
 * </ul>
 *
 * <h2>Reusable vector note</h2>
 * <p>
 * The combined {@link #align(Dimensional, Sizeable, Alignment, Alignment)} methods return a reused
 * internal {@link Vector2f} instance for performance. That means the returned vector should be used
 * immediately and not stored long-term if another alignment call may happen later.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Element panel = new Element();
 * panel.setPosition(100f, 50f);
 * panel.setSize(300f, 120f);
 *
 * Font label = new Font(fontData);
 * label.setText("Play");
 *
 * Vector2f pos = Alignment.align(panel, label, Alignment.CENTER, Alignment.CENTER);
 * label.setPosition(pos.getX(), pos.getY());
 *
 * Vector2f bottomLeft = Alignment.align(panel, label, Alignment.START, Alignment.START);
 * label.setPosition(bottomLeft.getX(), bottomLeft.getY());
 *
 * float rightX = Alignment.alignHorizontally(panel, label, Alignment.END);
 * float centerY = Alignment.alignVertically(panel, label, Alignment.CENTER);
 * label.setPosition(rightX, centerY);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public enum Alignment {
    START, CENTER, END;

    private static final Vector2f reused = new Vector2f(); // Reused result vector to avoid creating a new object for every alignment call.

    /**
     * Computes a full 2D alignment position for a target relative to a source using separate
     * horizontal and vertical alignment modes.
     *
     * <p>
     * This method combines {@link #alignHorizontally(Dimensional, Sizeable, Alignment)} and
     * {@link #alignVertically(Dimensional, Sizeable, Alignment)} into a single call. The resulting
     * x and y values are written into a reused internal {@link Vector2f} and then returned.
     * </p>
     *
     * <p>
     * Because the returned vector is reused internally, callers should not assume the returned
     * object remains stable after future alignment calls.
     * </p>
     *
     * @param source              the object that provides the base position and available size
     * @param target              the object being aligned inside or against the source
     * @param horizontalAlignment the horizontal alignment mode to apply
     * @param verticalAlignment   the vertical alignment mode to apply
     * @return a reused vector containing the aligned x and y position
     * @throws NullPointerException if any argument is null
     */
    public static Vector2f align(Dimensional source, Sizeable target, Alignment horizontalAlignment, Alignment verticalAlignment) {
        float x = alignHorizontally(source, target, horizontalAlignment);
        float y = alignVertically(source, target, verticalAlignment);

        reused.set(x, y);
        return reused;
    }

    /**
     * Computes a full 2D alignment position for a target relative to a source using the same
     * alignment mode on both axes.
     *
     * <p>
     * This is a convenience overload for cases such as centering on both axes or aligning to
     * the start or end on both axes at once.
     * </p>
     *
     * @param source    the object that provides the base position and available size
     * @param target    the object being aligned inside or against the source
     * @param alignment the alignment mode to apply to both the horizontal and vertical axes
     * @return a reused vector containing the aligned x and y position
     * @throws NullPointerException if any argument is null
     */
    public static Vector2f align(Dimensional source, Sizeable target, Alignment alignment) {
        return align(source, target, alignment, alignment);
    }

    /**
     * Computes the horizontal aligned x position for a target relative to a source.
     *
     * <p>
     * The horizontal rules are:
     * </p>
     * <ul>
     *     <li>{@link #START}: the target's left edge matches the source's x position</li>
     *     <li>{@link #CENTER}: the target is centered within the source's width</li>
     *     <li>{@link #END}: the target's right edge matches the source's right edge</li>
     * </ul>
     *
     * @param source    the object that provides the base x position and width
     * @param target    the object whose width is being aligned
     * @param alignment the horizontal alignment mode
     * @return the computed world-space x position for the target
     * @throws NullPointerException if any argument is null
     */
    public static float alignHorizontally(Dimensional source, Sizeable target, Alignment alignment) {
        return switch (alignment) {
            case START -> source.getX();
            case END -> source.getX() + source.getWidth() - target.getWidth();
            case CENTER -> source.getX() + (source.getWidth() - target.getWidth()) * 0.5f;
        };
    }

    /**
     * Computes the vertical aligned y position for a target relative to a source.
     *
     * <p>
     * The vertical rules are:
     * </p>
     * <ul>
     *     <li>{@link #START}: the target's bottom edge matches the source's y position</li>
     *     <li>{@link #CENTER}: the target is centered within the source's height</li>
     *     <li>{@link #END}: the target's top edge matches the source's top edge</li>
     * </ul>
     *
     * <p>
     * This follows a bottom-left coordinate system, which matches the rest of your rendering
     * setup and OpenGL-style positioning.
     * </p>
     *
     * @param source    the object that provides the base y position and height
     * @param target    the object whose height is being aligned
     * @param alignment the vertical alignment mode
     * @return the computed world-space y position for the target
     * @throws NullPointerException if any argument is null
     */
    public static float alignVertically(Dimensional source, Sizeable target, Alignment alignment) {
        return switch (alignment) {
            case END -> source.getY() + source.getHeight() - target.getHeight();
            case START -> source.getY();
            case CENTER -> source.getY() + (source.getHeight() - target.getHeight()) * 0.5f;
        };
    }
}