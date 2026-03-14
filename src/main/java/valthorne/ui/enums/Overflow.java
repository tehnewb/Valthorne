package valthorne.ui.enums;

import org.lwjgl.util.yoga.Yoga;

/**
 * <p>
 * {@code Overflow} controls how content is handled when it exceeds
 * the bounds of its container.
 * </p>
 *
 * <p>
 * These values map directly to Yoga's {@code YGOverflow} constants.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Panel panel = new Panel();
 *
 * panel.getLayout()
 *      .overflow(Overflow.HIDDEN);
 *
 * int yogaOverflow = Overflow.HIDDEN.yoga();
 * }</pre>
 *
 * <p>
 * In this example, child content that exceeds the panel bounds will be clipped.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public enum Overflow {

    VISIBLE(Yoga.YGOverflowVisible),
    HIDDEN(Yoga.YGOverflowHidden),
    SCROLL(Yoga.YGOverflowScroll);

    private final int yoga;

    /**
     * Creates an overflow mode mapped to the Yoga constant.
     *
     * @param yoga the Yoga overflow constant
     */
    Overflow(int yoga) {
        this.yoga = yoga;
    }

    /**
     * Returns the Yoga constant used internally by the layout engine.
     *
     * @return the Yoga overflow constant
     */
    public int yoga() {
        return yoga;
    }

}