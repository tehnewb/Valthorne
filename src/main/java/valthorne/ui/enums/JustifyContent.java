package valthorne.ui.enums;

import org.lwjgl.util.yoga.Yoga;

/**
 * <p>
 * {@code JustifyContent} controls how child elements are distributed
 * along the main axis of a flex container.
 * </p>
 *
 * <p>
 * These values correspond directly to Yoga's {@code YGJustify} constants.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Panel panel = new Panel();
 *
 * panel.getLayout()
 *      .flexDirection(FlexDirection.ROW)
 *      .justifyContent(JustifyContent.SPACE_BETWEEN);
 *
 * int yogaValue = JustifyContent.SPACE_BETWEEN.yoga();
 * }</pre>
 *
 * <p>
 * This example distributes children evenly across the container width.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public enum JustifyContent {

    FLEX_START(Yoga.YGJustifyFlexStart),
    CENTER(Yoga.YGJustifyCenter),
    FLEX_END(Yoga.YGJustifyFlexEnd),
    SPACE_BETWEEN(Yoga.YGJustifySpaceBetween),
    SPACE_AROUND(Yoga.YGJustifySpaceAround),
    SPACE_EVENLY(Yoga.YGJustifySpaceEvenly);

    private final int yoga;

    /**
     * Creates a justify-content value mapped to the Yoga constant.
     *
     * @param yoga the Yoga justify constant
     */
    JustifyContent(int yoga) {
        this.yoga = yoga;
    }

    /**
     * Returns the Yoga constant used by the layout engine.
     *
     * @return the Yoga justify constant
     */
    public int yoga() {
        return yoga;
    }

}