package valthorne.ui.styles;

import valthorne.graphics.Drawable;
import valthorne.graphics.font.FontData;

/**
 * Represents a configurable style for a button, including the visual
 * appearance for various states and the font data used for rendering text.
 * <p>
 * This class provides methods to access and modify the drawables for the
 * button's default, hover, pressed, and disabled states, as well as the font
 * data used for rendering text. Each setter method allows method chaining for
 * convenience.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public class ButtonStyle {

    private FontData fontData;  // Font data for rendering button text
    private Drawable background;    // Drawable for button's default state
    private Drawable hover;     // Drawable for button's hover state
    private Drawable pressed;   // Drawable for button's pressed state
    private Drawable disabled;  // Drawable for button's disabled state
    private Drawable focused;  // Drawable for button's disabled state

    /**
     * Creates and returns a new instance of the ButtonStyle class with default settings.
     *
     * @return A new instance of ButtonStyle
     */
    public static ButtonStyle of() {
        return new ButtonStyle();
    }

    /**
     * Gets the font data used for rendering button text.
     *
     * @return The button's font data
     */
    public FontData getFontData() {
        return fontData;
    }

    /**
     * Sets the font for rendering button text.
     *
     * @param fontData The font data to use
     * @return This ButtonStyle instance for method chaining
     */
    public ButtonStyle fontData(FontData fontData) {
        this.fontData = fontData;
        return this;
    }

    /**
     * Returns the drawable associated with the button's default state.
     *
     * @return The drawable for the button's default state
     */
    public Drawable getBackground() {
        return background;
    }

    /**
     * Sets the drawable used for the button's default (background) state.
     *
     * @param background The drawable to use for the button's background state
     * @return This ButtonStyle instance for method chaining
     */
    public ButtonStyle background(Drawable background) {
        this.background = background;
        return this;
    }

    /**
     * Gets the drawable for the button's hover state.
     *
     * @return The hover state drawable
     */
    public Drawable getHovered() {
        return hover;
    }

    /**
     * Sets the drawable for when the mouse hovers over the button.
     *
     * @param hover The drawable to use for the hover state
     * @return This ButtonStyle instance for method chaining
     */
    public ButtonStyle hovered(Drawable hover) {
        this.hover = hover;
        return this;
    }

    /**
     * Gets the drawable for the button's pressed state.
     *
     * @return The pressed state drawable
     */
    public Drawable getPressed() {
        return pressed;
    }

    /**
     * Sets the drawable for when the button is being pressed.
     *
     * @param pressed The drawable to use for the pressed state
     * @return This ButtonStyle instance for method chaining
     */
    public ButtonStyle pressed(Drawable pressed) {
        this.pressed = pressed;
        return this;
    }

    /**
     * Gets the drawable for the button's disabled state.
     *
     * @return The disabled state drawable
     */
    public Drawable getDisabled() {
        return disabled;
    }

    /**
     * Sets the drawable for when the button is disabled.
     *
     * @param disabled The drawable to use for the disabled state
     * @return This ButtonStyle instance for method chaining
     */
    public ButtonStyle disabled(Drawable disabled) {
        this.disabled = disabled;
        return this;
    }

    /**
     * Retrieves the drawable associated with the button's focused state.
     *
     * @return The drawable for the button's focused state
     */
    public Drawable getFocused() {
        return focused;
    }

    /**
     * Sets the drawable to be used for the button's focused state.
     *
     * @param focused The drawable to use when the button is focused
     * @return This ButtonStyle instance for method chaining
     */
    public ButtonStyle focused(Drawable focused) {
        this.focused = focused;
        return this;
    }

}