package valthorne.ui.enums;

import org.lwjgl.util.yoga.Yoga;

/**
 * <p>
 * {@code PositionType} determines how an element's position is calculated
 * within the Yoga layout system.
 * </p>
 *
 * <p>
 * Two positioning modes are supported:
 * </p>
 *
 * <ul>
 *     <li>{@code RELATIVE} — participates in normal flex layout flow</li>
 *     <li>{@code ABSOLUTE} — positioned independently of layout flow</li>
 * </ul>
 *
 * <p>
 * These values map directly to Yoga's {@code YGPositionType} constants.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Tooltip tooltip = new Tooltip();
 *
 * tooltip.getLayout()
 *        .positionType(PositionType.ABSOLUTE)
 *        .left(100)
 *        .top(50);
 *
 * int yogaValue = PositionType.ABSOLUTE.yoga();
 * }</pre>
 *
 * <p>
 * This example places the tooltip at an exact coordinate instead of allowing
 * it to flow with the layout.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public enum PositionType {

    /**
     * Keeps the node in normal flex flow, with relative position offsets applied.
     */
    RELATIVE(Yoga.YGPositionTypeRelative),
    /**
     * Removes the node from normal flex flow and positions it using its containing block.
     */
    ABSOLUTE(Yoga.YGPositionTypeAbsolute);

    private final int yoga; // Native Yoga enum value corresponding to this layout option.

    /**
     * Creates a position type mapped to the Yoga constant.
     *
     * @param yoga the Yoga position type constant
     */
    PositionType(int yoga) {
        this.yoga = yoga;
    }

    /**
     * Returns the Yoga constant used by the layout engine.
     *
     * @return the Yoga position constant
     */
    public int yoga() {
        return yoga;
    }

}