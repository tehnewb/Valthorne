package valthorne.ui.styles;

import valthorne.graphics.Drawable;

/**
 * <h1>CheckboxStyle</h1>
 *
 * <p>
 * {@code CheckboxStyle} defines the visual appearance of a
 * {@link valthorne.ui.elements.Checkbox}. It stores the drawables used for the checkbox background,
 * checkmark, and interaction-specific state variations such as hover, press, focus, and disabled.
 * </p>
 *
 * <p>
 * This class exists so checkbox visuals can be configured independently from checkbox behavior. A
 * single checkbox implementation can therefore be reused with many different skins, themes, or design
 * systems simply by swapping the style object.
 * </p>
 *
 * <h2>Drawable roles</h2>
 * <ul>
 *     <li><b>Background</b> is the normal box shown when no higher-priority state overrides apply.</li>
 *     <li><b>Hovered</b> is used when the pointer is over the checkbox.</li>
 *     <li><b>Pressed</b> is used while the checkbox is actively pressed.</li>
 *     <li><b>Focused</b> is used when the checkbox has keyboard focus.</li>
 *     <li><b>Disabled</b> is used when the checkbox cannot be interacted with.</li>
 *     <li><b>Checkmark</b> is drawn on top of the background when the checkbox is checked.</li>
 * </ul>
 *
 * <h2>Fluent usage</h2>
 * <p>
 * This class uses builder-style setter methods so styles can be assembled cleanly through method
 * chaining.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * CheckboxStyle style = CheckboxStyle.of()
 *     .background(normalBoxDrawable)
 *     .hovered(hoveredBoxDrawable)
 *     .pressed(pressedBoxDrawable)
 *     .focused(focusedBoxDrawable)
 *     .disabled(disabledBoxDrawable)
 *     .checkmark(checkmarkDrawable);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class CheckboxStyle {

    private Drawable checkmark; // The drawable rendered inside the checkbox when it is checked.
    private Drawable background; // The drawable used for the normal background state.
    private Drawable hover; // The drawable used while the checkbox is hovered.
    private Drawable pressed; // The drawable used while the checkbox is pressed.
    private Drawable disabled; // The drawable used while the checkbox is disabled.
    private Drawable focused; // The drawable used while the checkbox is focused.

    /**
     * Creates a new empty checkbox style.
     *
     * <p>
     * This static factory is useful for fluent builder-style configuration.
     * </p>
     *
     * @return a new checkbox style instance
     */
    public static CheckboxStyle of() {
        return new CheckboxStyle();
    }

    /**
     * Returns the checkmark drawable.
     *
     * @return the checkmark drawable, or null if none is set
     */
    public Drawable getCheckmark() {
        return checkmark;
    }

    /**
     * Sets the checkmark drawable.
     *
     * <p>
     * The checkmark is drawn on top of the background whenever the checkbox is checked.
     * </p>
     *
     * @param checkmark the drawable to use as the checkmark
     * @return this style for chaining
     */
    public CheckboxStyle checkmark(Drawable checkmark) {
        this.checkmark = checkmark;
        return this;
    }

    /**
     * Returns the default background drawable.
     *
     * @return the default background drawable, or null if none is set
     */
    public Drawable getBackground() {
        return background;
    }

    /**
     * Sets the default background drawable.
     *
     * @param background the drawable to use for the normal background state
     * @return this style for chaining
     */
    public CheckboxStyle background(Drawable background) {
        this.background = background;
        return this;
    }

    /**
     * Returns the hovered background drawable.
     *
     * @return the hovered drawable, or null if none is set
     */
    public Drawable getHovered() {
        return hover;
    }

    /**
     * Sets the hovered background drawable.
     *
     * @param hover the drawable to use while the checkbox is hovered
     * @return this style for chaining
     */
    public CheckboxStyle hovered(Drawable hover) {
        this.hover = hover;
        return this;
    }

    /**
     * Returns the pressed background drawable.
     *
     * @return the pressed drawable, or null if none is set
     */
    public Drawable getPressed() {
        return pressed;
    }

    /**
     * Sets the pressed background drawable.
     *
     * @param pressed the drawable to use while the checkbox is pressed
     * @return this style for chaining
     */
    public CheckboxStyle pressed(Drawable pressed) {
        this.pressed = pressed;
        return this;
    }

    /**
     * Returns the disabled background drawable.
     *
     * @return the disabled drawable, or null if none is set
     */
    public Drawable getDisabled() {
        return disabled;
    }

    /**
     * Sets the disabled background drawable.
     *
     * @param disabled the drawable to use while the checkbox is disabled
     * @return this style for chaining
     */
    public CheckboxStyle disabled(Drawable disabled) {
        this.disabled = disabled;
        return this;
    }

    /**
     * Returns the focused background drawable.
     *
     * @return the focused drawable, or null if none is set
     */
    public Drawable getFocused() {
        return focused;
    }

    /**
     * Sets the focused background drawable.
     *
     * @param focused the drawable to use while the checkbox is focused
     * @return this style for chaining
     */
    public CheckboxStyle focused(Drawable focused) {
        this.focused = focused;
        return this;
    }
}