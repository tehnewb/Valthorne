package valthorne.ui.enums;

/**
 * <p>
 * {@code LayoutUnit} defines the unit type used when specifying layout values
 * such as width, height, margin, or padding.
 * </p>
 *
 * <p>
 * This determines how the numeric value should be interpreted by the layout system.
 * </p>
 *
 * <ul>
 *     <li>{@code AUTO} — size determined automatically by layout</li>
 *     <li>{@code POINT} — fixed pixel/point value</li>
 *     <li>{@code PERCENT} — percentage relative to parent</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Layout layout = element.getLayout();
 *
 * layout.width(200, LayoutUnit.POINT);
 * layout.height(50, LayoutUnit.PERCENT);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public enum LayoutUnit {
    /**
     * Delegates dimension selection to automatic layout sizing.
     */
    AUTO,
    /**
     * Interprets the value as an absolute number of layout points.
     */
    POINT,
    /**
     * Interprets the value as a percentage of the applicable reference dimension.
     */
    PERCENT
}