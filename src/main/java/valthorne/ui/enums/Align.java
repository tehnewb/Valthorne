package valthorne.ui.enums;

import org.lwjgl.util.yoga.Yoga;

/**
 * <p>
 * {@code Align} represents the alignment rules used by the Yoga layout engine
 * for aligning items along the cross axis of a flex container.
 * </p>
 *
 * <p>
 * These values map directly to Yoga's native {@code YGAlign} constants and are
 * used by Valthorne's UI layout system when configuring element alignment.
 * </p>
 *
 * <p>
 * Alignment affects how children are positioned relative to the container's
 * cross axis (the axis perpendicular to the main flex direction).
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Panel panel = new Panel();
 *
 * panel.getLayout()
 *      .flexDirection(FlexDirection.ROW)
 *      .alignItems(Align.CENTER);
 *
 * int yogaAlign = Align.CENTER.yoga();
 * }</pre>
 *
 * <p>
 * This example centers all children vertically when the container is laid out
 * in a horizontal row.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public enum Align {

    /**
     * Uses automatic cross-axis alignment, inheriting the applicable container policy.
     */
    AUTO(Yoga.YGAlignAuto),
    /**
     * Aligns items or lines at the cross-axis start.
     */
    FLEX_START(Yoga.YGAlignFlexStart),
    /**
     * Centers items or lines along the cross axis.
     */
    CENTER(Yoga.YGAlignCenter),
    /**
     * Aligns items or lines at the cross-axis end.
     */
    FLEX_END(Yoga.YGAlignFlexEnd),
    /**
     * Stretches eligible items or lines across available cross-axis space.
     */
    STRETCH(Yoga.YGAlignStretch),
    /**
     * Aligns items by their text baseline where supported by the layout context.
     */
    BASELINE(Yoga.YGAlignBaseline),
    /**
     * Distributes wrapped lines with free space between them and no extra outer gaps.
     */
    SPACE_BETWEEN(Yoga.YGAlignSpaceBetween),
    /**
     * Distributes wrapped lines with surrounding space and half-sized outer gaps.
     */
    SPACE_AROUND(Yoga.YGAlignSpaceAround);

    private final int yoga; // Native Yoga enum value corresponding to this layout option.

    /**
     * Creates an alignment enum value mapped to the corresponding Yoga constant.
     *
     * @param yoga the Yoga alignment constant
     */
    Align(int yoga) {
        this.yoga = yoga;
    }

    /**
     * Returns the raw Yoga alignment constant used internally by the Yoga layout engine.
     *
     * @return the Yoga alignment constant
     */
    public int yoga() {
        return yoga;
    }

}