package valthorne.ui.nodes;

import valthorne.graphics.Color;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugTextRun;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;
import valthorne.ui.enums.Alignment;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.ui.theme.UITokens;

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
 *     <li>{@link #FONT_SIZE_KEY} for logical world units per em</li>
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
 * position. If a color is also present, that color is used; otherwise the batch
 * color is used. Live Slug curves follow camera zoom without raster caches.
 * </p>
 *
 * <p>
 * Per-line layouts are retained so drawing does not split strings or repeat kerning.
 * The font is borrowed and must outlive pending draws.
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
     * Shared Slug outline font used by regular UI text controls.
     */
    public static final StyleKey<SlugFont> FONT_KEY = StyleKey.of("font", SlugFont.class);

    /**
     * Logical em size shared with the theme's density-scaled font-size token.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = UITokens.FONT_SIZE;

    /**
     * Empty retained layout shared by labels with no drawable text.
     */
    private static final SlugTextRun[] EMPTY_LINES = new SlugTextRun[0];

    /**
     * Style key used to resolve the text color used for rendering the label.
     */
    public static final StyleKey<Color> COLOR_KEY = StyleKey.of("color", Color.class);

    /**
     * Style key used to resolve the alignment for horizontal label coordination
     */
    public static final StyleKey<Alignment> ALIGNMENT_KEY = StyleKey.of("alignment", Alignment.class, Alignment.START);

    private float measuredWidth = Float.NaN; // Last intrinsic width, used to retain explicit application dimensions.
    private float measuredHeight = Float.NaN; // Last intrinsic height, updated as text or font changes.
    private String text = ""; // Retained nonnull text, with newline-separated lines.
    private SlugFont font; // Borrowed explicit or style-resolved font, possibly null.
    private float fontSize = 16f; // Resolved world units per em, independent of the shared font state.
    private SlugTextRun[] lines = EMPTY_LINES; // Retained line layouts, rebuilt only when font, text or size changes.
    private SlugFont layoutFont; // Font used to build the current retained lines.
    private String layoutText; // Source text used to build the current retained lines.
    private float layoutSize; // Em scale used to build the current retained lines.
    private float textWidth; // Cached maximum line advance in world units.
    private float textHeight; // Cached total line-box height in world units.
    private Color color; // Borrowed draw tint, or null for the batch's default color.
    private Alignment alignment = Alignment.START; // Horizontal alignment applied independently to each text line.

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

    /**
     * Recalculates content dimensions using the currently available font when the
     * node is created. A missing font or empty text yields zero layout dimensions.
     */
    @Override
    public void onCreate() {
        recalculateSize();
    }

    /**
     * Performs no label-specific cleanup because the font and color are borrowed.
     */
    @Override
    public void onDestroy() {
    }

    /**
     * Performs no per-frame animation or input work for this text-only node.
     *
     * @param delta elapsed frame time in seconds, unused
     */
    @Override
    public void update(float delta) {
    }

    /**
     * Returns the retained text, normalized to a nonnull string by constructors
     * and the text setter.
     *
     * @return displayed text
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
        recalculateSize();
        markLayoutDirty();
        return this;
    }

    /**
     * Borrows a font reference, immediately recalculates dimensions, and marks
     * layout dirty. The explicit override takes precedence over theme fonts.
     *
     * @param font font to use, or null to restore theme or root font selection
     * @return this label
     */
    public Label font(SlugFont font) {
        this.font = font;
        setStyle(FONT_KEY, font);
        recalculateSize();
        markLayoutDirty();
        return this;
    }

    /**
     * Retrieves the font used by this label.
     *
     * @return the font currently assigned to this label
     */
    public SlugFont getFont() {
        return font;
    }

    /**
     * Overrides this label's logical em size without mutating its shared font.
     *
     * @param size finite nonnegative world units per em
     * @return this label
     * @throws IllegalArgumentException if size is negative or nonfinite
     */
    public Label fontSize(float size) {
        if (!Float.isFinite(size) || size < 0f) throw new IllegalArgumentException("Font size must be finite and nonnegative.");
        fontSize = size;
        setStyle(FONT_SIZE_KEY, size);
        recalculateSize();
        markLayoutDirty();
        return this;
    }

    /**
     * Returns the resolved em scale used for drawing and intrinsic measurement.
     *
     * @return world units per em
     */
    public float getFontSize() { return fontSize; }

    /**
     * Borrows a color reference as an explicit style override for subsequent draws.
     * Null retains the resolved theme tint, or the batch tint when none is available.
     *
     * @param color draw tint, or null
     * @return this label
     */
    public Label color(Color color) {
        this.color = color;
        setStyle(COLOR_KEY, color);
        return this;
    }

    /**
     * Returns the current borrowed tint, whether explicitly assigned or style-resolved.
     *
     * @return mutable tint reference, or null for the batch's current color
     */
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
        if (alignment != null) {
            this.alignment = alignment;
            setStyle(ALIGNMENT_KEY, alignment);
        }
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

    /**
     * Applies nonnull resolved font, color, and alignment values, preserving current
     * references when a style value is absent. Recalculates exact content width/height
     * before delegating to base Yoga style application; this updates intrinsic layout
     * automatic dimensions with measured text dimensions, retaining explicit sizes.
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            SlugFont resolvedFont = style.get(FONT_KEY);
            Color resolvedColor = style.get(COLOR_KEY);
            Alignment resolvedAlignment = style.get(ALIGNMENT_KEY);
            float resolvedSize = style.get(FONT_SIZE_KEY);
            if (!Float.isFinite(resolvedSize) || resolvedSize < 0f) throw new IllegalArgumentException("Font size must be finite and nonnegative.");
            fontSize = resolvedSize;

            if (resolvedFont != null)
                font = resolvedFont;
            if (resolvedColor != null)
                color = resolvedColor;
            if (resolvedAlignment != null)
                alignment = resolvedAlignment;
        }

        if (font == null && getRoot() != null) font = getRoot().getDefaultFont();
        recalculateSize();
        super.applyLayout();
    }

    /**
     * Updates intrinsic width and height when dimensions are automatic or still
     * match the previous measurement. Explicit application dimensions are retained.
     */
    private void recalculateSize() {
        updateTextLayout();
        float width = textWidth;
        float height = textHeight;
        var layout = getLayout();
        if (layout.getWidth().isAuto() || layout.getWidth().isPoints() && layout.getWidth().getValue() == measuredWidth) {
            layout.width(width);
            measuredWidth = width;
        }
        if (layout.getHeight().isAuto() || layout.getHeight().isPoints() && layout.getHeight().getValue() == measuredHeight) {
            layout.height(height);
            measuredHeight = height;
        }
    }

    /**
     * Reuses per-line glyph layouts until the text, font or em size changes.
     * Newline scanning occurs only during rebuilds, preserving trailing empty
     * lines without regex or temporary arrays in the drawing path.
     */
    private void updateTextLayout() {
        if (layoutFont == font && layoutSize == fontSize && text.equals(layoutText)) return;
        layoutFont = font;
        layoutText = text;
        layoutSize = fontSize;
        textWidth = 0f;
        textHeight = 0f;
        if (font == null || text.isEmpty()) {
            lines = EMPTY_LINES;
            return;
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) == '\n') count++;
        SlugTextRun[] previous = lines;
        if (lines.length != count) lines = new SlugTextRun[count];
        int start = 0;
        for (int i = 0; i < count; i++) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            String line = start == 0 && end == text.length() ? text : text.substring(start, end);
            SlugTextRun run = i < previous.length ? previous[i] : null;
            if (run == null || run.font() != font) run = font.createRun(line, fontSize);
            else run.rebuild(line, fontSize);
            lines[i] = run;
            textWidth = Math.max(textWidth, run.width());
            start = end + 1;
        }
        textHeight = count * font.lineHeight() * fontSize;
    }

    @Override
    public void draw(TextureBatch batch) {
        if (font == null || lines.length == 0 || fontSize == 0f) return;
        float x = getRenderX();
        float baseline = getRenderY() + getHeight() - font.ascent() * fontSize;
        float advance = font.lineHeight() * fontSize;
        float availableWidth = getWidth();
        for (int i = 0; i < lines.length; i++) {
            SlugTextRun line = lines[i];
            float lineX = switch (alignment) {
                case START -> x;
                case CENTER -> x + (availableWidth - line.width()) * .5f;
                case END -> x + availableWidth - line.width();
            };
            line.draw(batch, lineX, baseline - i * advance, color);
        }
    }
}
