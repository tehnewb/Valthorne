package valthorne.ui.enums;

import org.lwjgl.util.yoga.Yoga;

/**
 * <p>
 * {@code FlexDirection} defines the primary axis used by the Yoga flex layout
 * engine when arranging child elements.
 * </p>
 *
 * <p>
 * The direction determines whether children are placed horizontally or vertically
 * and whether the order is normal or reversed.
 * </p>
 *
 * <p>
 * These values map directly to Yoga's {@code YGFlexDirection} constants.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Panel panel = new Panel();
 *
 * panel.getLayout()
 *      .flexDirection(FlexDirection.COLUMN);
 *
 * int yogaDirection = FlexDirection.COLUMN.yoga();
 * }</pre>
 *
 * <p>
 * This example arranges children vertically from top to bottom.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public enum FlexDirection {

    ROW(Yoga.YGFlexDirectionRow),
    COLUMN(Yoga.YGFlexDirectionColumn),
    ROW_REVERSE(Yoga.YGFlexDirectionRowReverse),
    COLUMN_REVERSE(Yoga.YGFlexDirectionColumnReverse);

    private final int yoga;

    /**
     * Creates a flex direction mapped to the corresponding Yoga constant.
     *
     * @param yoga the Yoga flex direction constant
     */
    FlexDirection(int yoga) {
        this.yoga = yoga;
    }

    /**
     * Returns the raw Yoga constant used internally by the Yoga layout engine.
     *
     * @return the Yoga flex direction constant
     */
    public int yoga() {
        return yoga;
    }

}