package valthorne.ui.nodes;

import valthorne.graphics.Drawable;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugTextRun;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.ui.theme.UITokens;

/**
 * <p>
 * {@code Tooltip} is a lightweight floating UI node used to display a short piece
 * of text near another UI element, typically after a hover delay.
 * </p>
 *
 * <p>
 * The tooltip is styled entirely through the theme system and resolves:
 * </p>
 *
 * <ul>
 *     <li>a font used to render the tooltip text</li>
 *     <li>a background drawable for the tooltip box</li>
 *     <li>a padding value used around the text</li>
 * </ul>
 *
 * <p>
 * This node is generally controlled by higher-level UI systems such as
 * {@link valthorne.ui.UIRoot}, which determine when the tooltip should become
 * visible and where it should be positioned. The tooltip itself focuses on:
 * </p>
 *
 * <ul>
 *     <li>storing its text content</li>
 *     <li>sizing itself from the resolved font and padding</li>
 *     <li>drawing its background and text when visible</li>
 * </ul>
 *
 * <p>
 * Tooltips are intentionally non-interactive. In the constructor, they are
 * configured as invisible, non-clickable, and non-focusable by default.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Tooltip tooltip = new Tooltip("Save changes");
 *
 * String text = tooltip.getText();
 * tooltip.text("Click to save");
 *
 * tooltip.setVisible(true);
 * tooltip.update(delta);
 * tooltip.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete intended use of the class: construction,
 * text access, text updates, visibility control, update, and draw.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Tooltip extends UINode {

    /**
     * Style key used to resolve the tooltip font.
     */
    public static final StyleKey<SlugFont> FONT_STYLE_KEY = StyleKey.of("font", SlugFont.class);

    /**
     * Em scale shared by all regular UI text controls through the semantic size token.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = UITokens.FONT_SIZE;

    /**
     * Style key used to resolve the tooltip background drawable.
     */
    public static final StyleKey<Drawable> BACKGROUND_STYLE_KEY = StyleKey.of("background", Drawable.class);

    /**
     * Style key used to resolve tooltip padding, defaulting to {@code 6f}.
     */
    public static final StyleKey<Float> PADDING_STYLE_KEY = StyleKey.of("padding", Float.class, 6f);

    private String text; // Current text displayed by the tooltip
    private SlugFont font; // Resolved font used to render tooltip text
    private SlugTextRun run; // Retained multiline layout borrowing the resolved font.
    private float fontSize = 16f; // Resolved world units per em, independent of the shared font state.
    private Drawable background; // Resolved background drawable for the tooltip box
    private float padding = 6f; // Resolved padding applied around the tooltip text

    /**
     * <p>
     * Creates a new tooltip with the supplied text.
     * </p>
     *
     * <p>
     * The tooltip starts hidden and is configured to be neither clickable nor
     * focusable, since tooltips are purely informational UI elements.
     * </p>
     *
     * @param text the initial tooltip text
     */
    public Tooltip(String text) {
        this.text = text;
        setVisible(false);
        setClickable(false);
        setFocusable(false);
    }

    /**
     * <p>
     * Called when this tooltip is created.
     * </p>
     *
     * <p>
     * This implementation currently performs no creation logic.
     * </p>
     */
    @Override
    public void onCreate() {
    }

    /**
     * <p>
     * Called when this tooltip is destroyed.
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
     * Updates this tooltip.
     * </p>
     *
     * <p>
     * This implementation currently performs no per-frame behavior.
     * </p>
     *
     * @param delta the elapsed frame time in seconds
     */
    @Override
    public void update(float delta) {
    }

    /**
     * <p>
     * Applies style-driven layout for this tooltip.
     * </p>
     *
     * <p>
     * The current style is resolved and used to update the font, background, and
     * padding. If no style exists, all resolved visual references are cleared and
     * padding is reset to its default. If both a font and non-null text are available,
     * the tooltip computes its width and height from the text size plus padding.
     * </p>
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();
        font = null;
        background = null;
        padding = 6f;

        if (style == null) {
            super.applyLayout();
            return;
        }

        font = style.get(FONT_STYLE_KEY);
        if (font == null && getRoot() != null) font = getRoot().getDefaultFont();
        fontSize = style.get(FONT_SIZE_KEY);
        if (!Float.isFinite(fontSize) || fontSize < 0f) throw new IllegalArgumentException("Font size must be finite and nonnegative.");
        background = style.get(BACKGROUND_STYLE_KEY);

        Float resolvedPadding = style.get(PADDING_STYLE_KEY);
        if (resolvedPadding != null)
            padding = resolvedPadding;

        if (font == null || text == null) {
            super.applyLayout();
            return;
        }

        if (run == null || run.font() != font) run = font.createRun(text, fontSize);
        else run.rebuild(text, fontSize);
        float width = run.width() + padding * 2f;
        float height = run.height() + padding * 2f;

        getLayout()
                .width(width)
                .height(height);
        super.applyLayout();
    }

    /**
     * <p>
     * Draws this tooltip using the provided texture batch.
     * </p>
     *
     * <p>
     * Rendering is skipped if the tooltip is not visible. If visible, the background
     * drawable is rendered first across the tooltip bounds. After that, if a font
     * exists and the text is non-null and non-blank, the text is rendered inside the
     * tooltip using the resolved padding offset.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (!isVisible())
            return;

        if (background != null)
            background.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());

        if (font == null || text == null || text.isBlank())
            return;

        run.draw(batch, getRenderX() + padding, getRenderY() + getHeight() - padding - font.ascent() * fontSize, getStyle() == null ? null : getStyle().get(Label.COLOR_KEY));
    }

    /**
     * <p>
     * Sets the tooltip text.
     * </p>
     *
     * <p>
     * Changing the text marks layout dirty so the tooltip can recompute its size
     * during the next layout pass.
     * </p>
     *
     * @param text the new tooltip text
     * @return this tooltip
     */
    public Tooltip text(String text) {
        this.text = text;
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Returns the current tooltip text.
     * </p>
     *
     * @return the current tooltip text
     */
    public String getText() {
        return text;
    }
}
