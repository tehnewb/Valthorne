package valthorne.ui.styles;

import valthorne.graphics.font.FontData;

/**
 * <h1>LabelStyle</h1>
 *
 * <p>
 * {@code LabelStyle} defines the visual configuration for a
 * {@link valthorne.ui.elements.Label}. It primarily stores the
 * {@link FontData} used to construct the {@link valthorne.graphics.font.Font}
 * responsible for rendering the label text.
 * </p>
 *
 * <p>
 * Separating styling from UI elements allows the same label logic to be reused
 * across many different themes or UI skins. A label only needs to know how to
 * render text, while the style determines what font configuration should be
 * used.
 * </p>
 *
 * <h2>FontData role</h2>
 *
 * <p>
 * {@link FontData} contains all information required to construct a font,
 * including glyph atlas textures, character metrics, spacing information,
 * and other rendering properties. When a {@link valthorne.ui.elements.Label}
 * is created, it typically uses the {@code FontData} provided by this style
 * to construct its internal {@link valthorne.graphics.font.Font}.
 * </p>
 *
 * <p>
 * Because {@code FontData} is shared data rather than a runtime font instance,
 * multiple labels can reuse the same font data safely without duplicating
 * texture resources.
 * </p>
 *
 * <h2>Fluent configuration</h2>
 *
 * <p>
 * This class follows a builder-style pattern so styles can be constructed
 * concisely using method chaining.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * FontData uiFont = new FontData("assets/fonts/ui.ttf", 24);
 *
 * LabelStyle style = LabelStyle.of()
 *     .fontData(uiFont);
 *
 * Label label = new Label("Hello World", style);
 * }</pre>
 *
 * <p>
 * In this example, the label will construct its internal font using the
 * provided {@code FontData}.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class LabelStyle {

    private FontData fontData; // The font data used to construct the label's rendering font.

    /**
     * Creates a new empty label style.
     *
     * <p>
     * This static factory method exists primarily for fluent builder-style
     * configuration.
     * </p>
     *
     * @return a new label style instance
     */
    public static LabelStyle of() {
        return new LabelStyle();
    }

    /**
     * Returns the font data used by labels configured with this style.
     *
     * @return the font data instance, or null if none has been assigned
     */
    public FontData getFontData() {
        return fontData;
    }

    /**
     * Sets the font data used by labels that use this style.
     *
     * <p>
     * The supplied {@link FontData} will be used by labels to construct
     * their internal {@link valthorne.graphics.font.Font}.
     * </p>
     *
     * @param fontData the font data to assign
     * @return this style for chaining
     */
    public LabelStyle fontData(FontData fontData) {
        this.fontData = fontData;
        return this;
    }

}