package valthorne.ui.enums;

import org.lwjgl.util.yoga.Yoga;

/**
 * <p>
 * {@code FlexWrap} controls whether children inside a flex container
 * are allowed to wrap onto multiple lines.
 * </p>
 *
 * <p>
 * This directly maps to Yoga's {@code YGWrap} values.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Panel panel = new Panel();
 *
 * panel.getLayout()
 *      .flexWrap(FlexWrap.WRAP);
 *
 * int yogaWrap = FlexWrap.WRAP.yoga();
 * }</pre>
 *
 * <p>
 * When wrapping is enabled, children that exceed the container width
 * will flow onto a new row or column.
 * </p>
 *
 * @author Albert Beaupre
 * @since March11th, 2026
 */
public enum FlexWrap {

    NO_WRAP(Yoga.YGWrapNoWrap),
    WRAP(Yoga.YGWrapWrap),
    WRAP_REVERSE(Yoga.YGWrapReverse);

    private final int yoga;

    /**
     * Creates a flex wrap value mapped to the Yoga wrap constant.
     *
     * @param yoga the Yoga wrap constant
     */
    FlexWrap(int yoga) {
        this.yoga = yoga;
    }

    /**
     * Returns the raw Yoga constant used internally by the Yoga layout engine.
     *
     * @return the Yoga wrap constant
     */
    public int yoga() {
        return yoga;
    }
}