package valthorne.ui.elements;

import valthorne.graphics.font.Font;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.Vector2f;
import valthorne.ui.Element;
import valthorne.ui.enums.Alignment;
import valthorne.ui.styles.LabelStyle;

/**
 * <h1>Label</h1>
 *
 * <p>
 * {@code Label} is a UI element used to render text inside the UI system. It wraps a
 * {@link Font} object and integrates it with the layout and positioning behavior of
 * {@link Element}. This allows text to participate in the same UI layout system used
 * by buttons, sliders, images, and other elements.
 * </p>
 *
 * <p>
 * The label manages three main responsibilities:
 * </p>
 *
 * <ul>
 *     <li>Holding the displayed text.</li>
 *     <li>Rendering that text using a {@link Font}.</li>
 *     <li>Aligning the text inside the element bounds using {@link Alignment}.</li>
 * </ul>
 *
 * <h2>Text layout behavior</h2>
 *
 * <p>
 * The label itself represents a rectangular region in UI space. The actual text is rendered
 * by the internal {@link Font} instance. Whenever the element's position or size changes,
 * the label re-aligns the font inside its bounds using {@link Alignment#align}.
 * </p>
 *
 * <p>
 * This allows the text to be aligned in multiple ways such as:
 * </p>
 *
 * <ul>
 *     <li>{@code START}</li>
 *     <li>{@code CENTER}</li>
 *     <li>{@code END}</li>
 * </ul>
 *
 * <p>
 * By default, labels use {@link Alignment#CENTER}, which centers the text horizontally
 * and vertically inside the element.
 * </p>
 *
 * <h2>Font ownership</h2>
 *
 * <p>
 * The label creates its own {@link Font} instance using the font data supplied by the
 * {@link LabelStyle}. The font is responsible for measuring and rendering glyphs.
 * </p>
 *
 * <p>
 * Changing the text updates the internal font immediately so the label size and rendering
 * remain correct.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Label title = new Label("Valthorne");
 *
 * title.setPosition(100f, 400f);
 * title.setSize(300f, 80f);
 * title.setTextAlignment(Alignment.CENTER);
 *
 * ui.add(title);
 * }</pre>
 *
 * <p>
 * This will render the text centered inside the label's bounds.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class Label extends Element {

    private String text; // The text currently displayed by the label.
    private LabelStyle style; // The visual style used to configure font data and other text properties.
    private Font font; // The font instance responsible for rendering and measuring the label text.
    private Alignment textAlignment; // The alignment used to position the text within the label bounds.

    /**
     * Creates a new label using the provided text and style.
     *
     * <p>
     * A new {@link Font} instance is created using the style's font data. The label size
     * is automatically initialized to the natural size of the rendered text.
     * </p>
     *
     * @param text  the text displayed by the label
     * @param style the style that defines font data and visual properties
     */
    public Label(String text, LabelStyle style) {
        this.text = text;
        this.style = style;
        this.textAlignment = Alignment.CENTER;

        this.font = new Font(style.getFontData());
        this.font.setText(text);

        this.setSize(font.getWidth(), font.getHeight());
    }

    /**
     * Creates a new label using the given text and a default {@link LabelStyle}.
     *
     * @param text the text displayed by the label
     */
    public Label(String text) {
        this(text, new LabelStyle());
    }

    /**
     * Updates the label element.
     *
     * <p>
     * The label currently has no time-based behavior or animation. This method exists
     * to satisfy the UI element lifecycle and to allow future extensions if animated
     * or dynamic text behavior is introduced.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous update
     */
    @Override
    public void update(float delta) {

    }

    /**
     * Draws the label text using the internal {@link Font}.
     *
     * <p>
     * The font handles all glyph rendering internally. The label only delegates
     * the draw call to the font using the provided {@link TextureBatch}.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        font.draw(batch);
    }

    /**
     * Sets the position of the label element.
     *
     * <p>
     * After updating the element position, the font is re-aligned inside the label
     * bounds according to the current {@link Alignment}.
     * </p>
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     */
    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        updateFontAlignment();
    }

    /**
     * Sets the size of the label element.
     *
     * <p>
     * After resizing the element, the font is re-aligned within the new bounds
     * according to the current {@link Alignment}.
     * </p>
     *
     * @param width  the new width
     * @param height the new height
     */
    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        updateFontAlignment();
    }

    /**
     * Updates the displayed text.
     *
     * <p>
     * The internal font text is updated immediately. After the new text is applied,
     * the font position is recalculated so the text remains properly aligned inside
     * the label bounds.
     * </p>
     *
     * @param text the new text content
     */
    public void setText(String text) {
        this.text = text;
        this.font.setText(text);
        updateFontAlignment();
    }

    /**
     * Returns the text currently displayed by the label.
     *
     * @return the label text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the style used by the label.
     *
     * <p>
     * This updates the stored style reference but does not recreate the font.
     * If the font data changes, the font should be replaced manually using
     * {@link #setFont(Font)}.
     * </p>
     *
     * @param style the new label style
     * @return this label for chaining
     */
    public Label setStyle(LabelStyle style) {
        this.style = style;
        return this;
    }

    /**
     * Sets the alignment used to position the text within the label bounds.
     *
     * <p>
     * Changing the alignment immediately recalculates the font position so the
     * text appears in the correct location.
     * </p>
     *
     * @param alignment the alignment to apply
     * @return this label for chaining
     */
    public Label setTextAlignment(Alignment alignment) {
        this.textAlignment = alignment;
        updateFontAlignment();
        return this;
    }

    /**
     * Returns the alignment used for the label text.
     *
     * @return the current text alignment
     */
    public Alignment getTextAlignment() {
        return textAlignment;
    }

    /**
     * Replaces the font used by this label.
     *
     * <p>
     * The new font will be used for all future rendering. The font position
     * is immediately recalculated to ensure correct alignment.
     * </p>
     *
     * @param font the new font instance
     * @return this label for chaining
     */
    public Label setFont(Font font) {
        this.font = font;
        updateFontAlignment();
        return this;
    }

    /**
     * Returns the font currently used to render the label text.
     *
     * @return the font instance used by this label
     */
    public Font getFont() {
        return font;
    }

    /**
     * Recalculates the font position based on the label bounds and alignment.
     *
     * <p>
     * This method uses {@link Alignment#align} to determine where the font
     * should be placed inside the element's rectangle. The result is then
     * applied directly to the font position.
     * </p>
     */
    private void updateFontAlignment() {
        Vector2f fontPosition = Alignment.align(this, font, textAlignment);
        font.setPosition(fontPosition.getX(), fontPosition.getY());
    }
}