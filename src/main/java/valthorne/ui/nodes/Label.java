package valthorne.ui.nodes;

import valthorne.graphics.Color;
import valthorne.graphics.font.Font;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;
import valthorne.ui.enums.Alignment;
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

    /**
     * Style key used to resolve the alignment for horizontal label coordination
     */
    public static final StyleKey<Alignment> ALIGNMENT_KEY = StyleKey.of("alignment", Alignment.class, Alignment.START);

    private String text = "";
    private Font font;
    private Color color;
    private Alignment alignment = Alignment.START;

    /**
     * Constructs a new default instance of the Label class.
     * <p>
     * This constructor creates a Label with default configurations.
     * Default properties such as text, font, color, and alignment can be set or modified using the respective methods provided in the class.
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

    @Override
    public void onCreate() {
        recalculateSize();
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void update(float delta) {
    }

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
        recalculateSize();
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
    public Label font(Font font) {
        this.font = font;
        recalculateSize();
        markLayoutDirty();
        return this;
    }

    /**
     * Retrieves the font used by this label.
     *
     * @return the font currently assigned to this label
     */
    public Font getFont() {
        return font;
    }

    /**
     * Sets the color for this label.
     *
     * @param color the new color to be applied to the label
     * @return this label, allowing for method chaining
     */
    public Label color(Color color) {
        this.color = color;
        return this;
    }

    public Color getColor() {
        return color;
    }

    /**
     * Sets the alignment for this label.
     * <p>
     * If the specified alignment is not null, it updates the label's alignment to the provided value.
     *
     * @param alignment the new alignment to be applied to the label
     * @return this label, allowing for method chaining
     */
    public Label alignment(Alignment alignment) {
        if (alignment != null)
            this.alignment = alignment;
        return this;
    }

    /**
     * Retrieves the alignment currently assigned to this label.
     *
     * @return the alignment of the label
     */
    public Alignment getAlignment() {
        return alignment;
    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Font resolvedFont = style.get(FONT_KEY);
            Color resolvedColor = style.get(COLOR_KEY);
            Alignment resolvedAlignment = style.get(ALIGNMENT_KEY);

            if (resolvedFont != null)
                font = resolvedFont;
            if (resolvedColor != null)
                color = resolvedColor;
            if (resolvedAlignment != null)
                alignment = resolvedAlignment;
        }

        recalculateSize();
        super.applyLayout();
    }

    private void recalculateSize() {
        if (font == null || text == null || text.isEmpty()) {
            getLayout().width(0f);
            getLayout().height(0f);
            return;
        }

        getLayout().width(measureTextWidth());
        getLayout().height(measureTextHeight());
    }

    private String[] getLines() {
        return text == null ? new String[]{""} : text.split("\n", -1);
    }

    private float measureTextWidth() {
        if (font == null)
            return 0f;

        float width = 0f;
        String[] lines = getLines();

        for (String line : lines)
            width = Math.max(width, font.getWidth(line));

        return width;
    }

    private float measureTextHeight() {
        if (font == null || text == null || text.isEmpty())
            return 0f;

        String[] lines = getLines();
        if (lines.length == 1)
            return font.getHeight(lines[0]);

        float singleLineHeight = getSingleLineHeight();
        float lineAdvance = getLineAdvance();
        return singleLineHeight + (lines.length - 1) * lineAdvance;
    }

    private float getSingleLineHeight() {
        return font == null ? 0f : font.getHeight("Ag");
    }

    private float getLineAdvance() {
        if (font == null)
            return 0f;

        float singleLineHeight = getSingleLineHeight();
        float twoLineHeight = font.getHeight("Ag\nAg");
        float lineAdvance = twoLineHeight - singleLineHeight;
        return lineAdvance > 0f ? lineAdvance : singleLineHeight;
    }

    @Override
    public void draw(TextureBatch batch) {
        if (font == null || text == null || text.isEmpty())
            return;

        String[] lines = getLines();
        float x = getRenderX();
        float lineAdvance = getLineAdvance();
        float topY = getRenderY() + measureTextHeight() - lineAdvance;
        float availableWidth = getWidth();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            float lineWidth = font.getWidth(line);
            float lineX = switch (alignment) {
                case START -> x;
                case CENTER -> x + (availableWidth - lineWidth) * 0.5f;
                case END -> x + availableWidth - lineWidth;
            };
            float lineY = topY - i * lineAdvance;

            if (color != null)
                font.draw(batch, line, lineX, lineY, color);
            else
                font.draw(batch, line, lineX, lineY);
        }
    }
}