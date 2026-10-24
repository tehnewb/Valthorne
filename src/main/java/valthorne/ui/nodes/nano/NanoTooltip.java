package valthorne.ui.nodes.nano;

import valthorne.ui.NanoText;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.UIConstants;
import valthorne.ui.nodes.Tooltip;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * NanoVG tooltip with themed surface, border, text, padding, and typography.
 * Font and padding setters are local style overrides; clearing them restores the current
 * theme or key defaults. Text measurement uses the same shared adaptive font bridge as drawing.
 * Attach through a root or assign with UINode.setTooltip; the root owns graphics resources.
 */
public class NanoTooltip extends Tooltip implements NanoNode {
    /**
     * Theme key controlling background color.
     */
    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.tooltip.backgroundColor", Color.class, new Color(0xEE202124));
    /**
     * Theme key controlling border color.
     */
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.tooltip.borderColor", Color.class, new Color(0xFF55575C));
    /**
     * Theme key controlling text color.
     */
    public static final StyleKey<Color> TEXT_COLOR_KEY = StyleKey.of("nano.tooltip.textColor", Color.class, new Color(0xFFF1F3F4));
    /**
     * Theme key controlling padding.
     */
    public static final StyleKey<Float> PADDING_KEY = StyleKey.of("nano.tooltip.padding", Float.class, 6f);
    /**
     * Theme key controlling font size.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.tooltip.fontSize", Float.class, 14f);
    /**
     * Theme key controlling corner radius.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.tooltip.cornerRadius", Float.class, 0f);

    /**
     * Theme font registration name, defaulting to the root's proportional font.
     */
    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.tooltip.fontName", String.class, "default");

    private Color background = new Color(0xEE202124); // Current borrowed surface tint.
    private Color border = new Color(0xFF55575C); // Current borrowed outline tint.
    private Color foreground = new Color(0xFFF1F3F4); // Current borrowed text tint.
    private float padding = 6; // Inner text inset in logical UI units.
    private float fontSize = 14; // Current logical text em size.
    private float cornerRadius = 0f; // Surface rounding in logical UI units.
    private String fontName = "default"; // Registered NanoVG font name.

    /**
     * Creates a tooltip with initial text and unoverridden appearance defaults.
     *
     * @param text tooltip contents, possibly null
     */
    public NanoTooltip(String text) {
        super(text);
    }

    /**
     * Resolves Nano appearance, measures its text, and submits those dimensions to Yoga.
     * Skips the standard tooltip's independent text measurement to avoid alternating sizes.
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyleOrDefaults();
        background = style.get(BACKGROUND_COLOR_KEY);
        border = style.get(BORDER_COLOR_KEY);
        foreground = style.get(TEXT_COLOR_KEY);
        padding = style.get(PADDING_KEY);
        fontSize = style.get(FONT_SIZE_KEY);
        cornerRadius = style.get(CORNER_RADIUS_KEY);
        String resolvedName = style.get(FONT_NAME_KEY);
        if (resolvedName != null && !resolvedName.isBlank()) fontName = resolvedName;
        String text = getText();
        if (text != null) {
            long vg = getRoot() == null ? 0 : getRoot().getNanoVGHandle();
            getLayout().width(NanoText.measureTextWidth(this, vg, fontName, fontSize, text) + padding * 2)
                    .height(NanoText.measureTextHeight(this, vg, fontName, fontSize) + padding * 2);
        }
        UIConstants.applyLayout(getYogaMemoryAddress(), getLayout());
    }

    /**
     * Stores a nonblank font name as a local style override; null or blank retains it.
     *
     * @param value requested appearance value
     * @return this tooltip
     */
    public NanoTooltip fontName(String value) {
        if (value != null && !value.isBlank()) {
            fontName = value;
            setStyle(FONT_NAME_KEY, value);
            markLayoutDirty();
        }
        return this;
    }

    /**
     * Stores a positive finite font size as a local style override.
     *
     * @param value requested appearance value
     * @return this tooltip
     * @throws IllegalArgumentException if the value is outside its supported range
     */
    public NanoTooltip fontSize(float value) {
        if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid fontSize");
        fontSize = value;
        setStyle(FONT_SIZE_KEY, value);
        markLayoutDirty();
        return this;
    }

    /**
     * Stores finite nonnegative padding as a local style override.
     *
     * @param value requested appearance value
     * @return this tooltip
     * @throws IllegalArgumentException if the value is outside its supported range
     */
    public NanoTooltip padding(float value) {
        if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid padding");
        padding = value;
        setStyle(PADDING_KEY, value);
        markLayoutDirty();
        return this;
    }

    /**
     * Replaces tooltip text and requests measurement without changing appearance overrides.
     *
     * @param value replacement text, possibly null
     * @return this tooltip
     */
    @Override
    public NanoTooltip text(String value) {
        super.text(value);
        return this;
    }

    /**
     * Routes mixed-renderer dispatch through the root's active render context.
     *
     * @param batch current UI batch
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }
    /**
     * Paints the current surface, outline, and text in root-managed NanoVG coordinates.
     *
     * @param vg active NanoVG context, or zero to skip drawing
     */
    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0) return;
        float x = getRenderX(), y = getRenderY();
        nvgBeginPath(vg); nvgRoundedRect(vg, x, y, getWidth(), getHeight(), cornerRadius);
        nvgFillColor(vg, NanoUtility.color1(background)); nvgFill(vg);
        nvgStrokeColor(vg, NanoUtility.color2(border)); nvgStrokeWidth(vg, 1); nvgStroke(vg);
        String text = getText();
        if (text == null || text.isBlank()) return;
        nvgFontFace(vg, fontName); nvgFontSize(vg, fontSize); nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, NanoUtility.color3(foreground));
        NanoText.draw(this, vg, fontName, x + padding, y + getHeight() * .5f, text, fontSize, foreground, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }
}
