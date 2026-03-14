package valthorne.ui.nodes;

import valthorne.graphics.Color;
import valthorne.graphics.font.Font;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

/**
 * <p>
 * {@code Label} is a lightweight UI node used to display text.
 * It resolves its font and optional color from the current style and sizes
 * itself to fit the text during layout application.
 * </p>
 *
 * <p>
 * This class is intended to be the basic text-rendering node in the Valthorne UI
 * system. It stores a text string and, when layout is applied, reads style-driven
 * values such as:
 * </p>
 *
 * <ul>
 *     <li>{@link #FONT_KEY} for the font used to render the text</li>
 *     <li>{@link #COLOR_KEY} for the optional text color</li>
 * </ul>
 *
 * <p>
 * If a font is available in the resolved style, the label updates its layout width
 * and height to match the measured size of its current text. That allows labels to
 * naturally participate in Yoga layout sizing based on their rendered content.
 * </p>
 *
 * <p>
 * During drawing, if a font is present, the label renders its text at its render
 * position. If a color is also present, that color is used; otherwise the font's
 * default rendering path is used.
 * </p>
 *
 * <p>
 * The class does not perform its own interaction behavior and is typically used as
 * a child inside higher-level components such as buttons, tooltips, or form controls.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Label label = new Label("Hello World");
 *
 * String text = label.getText();
 * label.text("Updated Text");
 *
 * label.update(delta);
 * label.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete usage of the class: construction with text,
 * reading text, changing text, update, and draw.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Label extends UINode {

    /**
     * Style key used to resolve the font used for rendering the label text.
     */
    public static final StyleKey<Font> FONT_KEY = StyleKey.of("font", Font.class);

    /**
     * Style key used to resolve the text color used for rendering the label.
     */
    public static final StyleKey<Color> COLOR_KEY = StyleKey.of("color", Color.class);

    private String text = ""; // Current text displayed by the label
    private Font font; // Font resolved from the label style
    private Color color; // Color resolved from the label style

    /**
     * <p>
     * Creates a new empty label.
     * </p>
     */
    public Label() {
    }

    /**
     * <p>
     * Creates a new label with the provided initial text.
     * </p>
     *
     * <p>
     * If the supplied text is {@code null}, the label stores an empty string instead.
     * </p>
     *
     * @param text the initial label text
     */
    public Label(String text) {
        this.text = text == null ? "" : text;
    }

    /**
     * <p>
     * Called when this label is created.
     * </p>
     *
     * <p>
     * This implementation currently performs no creation logic, but the method exists
     * as part of the UI node lifecycle.
     * </p>
     */
    @Override
    public void onCreate() {
    }

    /**
     * <p>
     * Called when this label is destroyed.
     * </p>
     *
     * <p>
     * This implementation currently performs no destruction logic.
     * </p>
     */
    @Override
    public void onDestroy() {
    }

    /**
     * <p>
     * Updates this label.
     * </p>
     *
     * <p>
     * This implementation currently performs no per-frame logic.
     * </p>
     *
     * @param delta the frame delta time
     */
    @Override
    public void update(float delta) {
    }

    /**
     * <p>
     * Returns the current text displayed by this label.
     * </p>
     *
     * @return the current label text
     */
    public String getText() {
        return text;
    }

    /**
     * <p>
     * Sets the text displayed by this label.
     * </p>
     *
     * <p>
     * If the supplied text is {@code null}, an empty string is stored instead.
     * Changing the text marks layout dirty so size can be recalculated during the
     * next layout pass.
     * </p>
     *
     * @param text the new label text
     * @return this label
     */
    public Label text(String text) {
        this.text = text == null ? "" : text;
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Applies layout-related updates for this label.
     * </p>
     *
     * <p>
     * The current style is resolved and used to update the label's font and color.
     * If a font is available, the label's layout width and height are set to the
     * measured size of the current text. After applying its own text-specific layout
     * data, the method delegates to the superclass implementation.
     * </p>
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Font font = style.get(FONT_KEY);
            this.font = font;

            if (font != null) {
                getLayout().width(font.getWidth(text));
                getLayout().height(font.getHeight(text));
            }

            color = style.get(COLOR_KEY);
        }

        super.applyLayout();
    }

    /**
     * <p>
     * Draws this label using the provided {@link TextureBatch}.
     * </p>
     *
     * <p>
     * If no font has been resolved, rendering is skipped. If a color is available,
     * the colored font draw overload is used. Otherwise the font is drawn using its
     * default draw path.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (font == null)
            return;

        if (color != null) {
            font.draw(batch, text, getRenderX(), getRenderY(), color);
        } else {
            font.draw(batch, text, getRenderX(), getRenderY());
        }
    }
}