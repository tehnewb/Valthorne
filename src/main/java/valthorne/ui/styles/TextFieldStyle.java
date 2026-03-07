package valthorne.ui.styles;

import valthorne.graphics.Drawable;
import valthorne.graphics.font.FontData;

/**
 * <h1>TextFieldStyle</h1>
 *
 * <p>
 * {@code TextFieldStyle} stores the visual configuration used by
 * {@link valthorne.ui.elements.TextField}. It separates text field rendering assets from text
 * field logic so multiple fields can share the same look and feel.
 * </p>
 *
 * <p>
 * A style currently provides:
 * </p>
 *
 * <ul>
 *     <li>{@link FontData} used to create the field font</li>
 *     <li>A default background drawable</li>
 *     <li>A focused background drawable</li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <p>
 * Styles are configured fluently through builder-style methods and then supplied to a text field.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * TextFieldStyle style = TextFieldStyle.of()
 *     .fontData(fontData)
 *     .background(normalBackground)
 *     .focused(focusedBackground);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class TextFieldStyle {

    private FontData fontData; // The font data used to build the text field font.
    private Drawable background; // The drawable used while the field is not focused.
    private Drawable focused; // The drawable used while the field is focused.

    /**
     * Creates a new empty text field style instance.
     *
     * <p>
     * This method exists to support fluent builder-style construction.
     * </p>
     *
     * @return a new text field style
     */
    public static TextFieldStyle of() {
        return new TextFieldStyle();
    }

    /**
     * Sets the font data used by the text field.
     *
     * @param fontData the font data to assign
     * @return this style for chaining
     */
    public TextFieldStyle fontData(FontData fontData) {
        this.fontData = fontData;
        return this;
    }

    /**
     * Returns the configured font data.
     *
     * @return the font data
     */
    public FontData getFontData() {
        return fontData;
    }

    /**
     * Sets the default background drawable.
     *
     * @param background the drawable used while the field is unfocused
     * @return this style for chaining
     */
    public TextFieldStyle background(Drawable background) {
        this.background = background;
        return this;
    }

    /**
     * Returns the default background drawable.
     *
     * @return the unfocused background drawable
     */
    public Drawable getBackground() {
        return background;
    }

    /**
     * Sets the focused background drawable.
     *
     * @param focused the drawable used while the field is focused
     * @return this style for chaining
     */
    public TextFieldStyle focused(Drawable focused) {
        this.focused = focused;
        return this;
    }

    /**
     * Returns the focused background drawable.
     *
     * @return the focused drawable
     */
    public Drawable getFocused() {
        return focused;
    }
}