package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.nodes.Tooltip;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

/** NanoVG-rendered tooltip compatible with {@link valthorne.ui.UINode#setTooltip(Tooltip)}. */
public class NanoTooltip extends Tooltip implements NanoNode {
    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.tooltip.backgroundColor", Color.class, new Color(0xEE202124));
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.tooltip.borderColor", Color.class, new Color(0xFF55575C));
    public static final StyleKey<Color> TEXT_COLOR_KEY = StyleKey.of("nano.tooltip.textColor", Color.class, new Color(0xFFF1F3F4));
    public static final StyleKey<Float> PADDING_KEY = StyleKey.of("nano.tooltip.padding", Float.class, 6f);
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.tooltip.fontSize", Float.class, 14f);
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.tooltip.cornerRadius", Float.class, 4f);

    private Color background = new Color(0xEE202124), border = new Color(0xFF55575C), foreground = new Color(0xFFF1F3F4);
    private float padding = 6, fontSize = 14, cornerRadius = 4;
    private String fontName = "default";

    public NanoTooltip(String text) { super(text); }

    @Override protected void applyLayout() {
        super.applyLayout();
        ResolvedStyle style = getStyle();
        if (style != null) {
            background = style.get(BACKGROUND_COLOR_KEY); border = style.get(BORDER_COLOR_KEY);
            foreground = style.get(TEXT_COLOR_KEY); padding = style.get(PADDING_KEY);
            fontSize = style.get(FONT_SIZE_KEY); cornerRadius = style.get(CORNER_RADIUS_KEY);
        }
        String text = getText();
        if (text != null) {
            long vg = getRoot() == null ? 0 : getRoot().getNanoVGHandle();
            getLayout().width(NanoUtility.measureTextWidth(vg, fontName, fontSize, text) + padding * 2)
                    .height(NanoUtility.measureTextHeight(vg, fontName, fontSize) + padding * 2);
        }
    }

    public NanoTooltip fontName(String value) { if (value != null && !value.isBlank()) { fontName = value; markLayoutDirty(); } return this; }
    public NanoTooltip fontSize(float value) { if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid font size"); fontSize = value; markLayoutDirty(); return this; }
    public NanoTooltip padding(float value) { if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid padding"); padding = value; markLayoutDirty(); return this; }
    @Override public NanoTooltip text(String value) { super.text(value); return this; }

    @Override public void draw(TextureBatch batch) { render(batch); }
    @Override public void draw(long vg) {
        if (!isVisible() || vg == 0) return;
        float x = getRenderX(), y = getRenderY();
        nvgBeginPath(vg); nvgRoundedRect(vg, x, y, getWidth(), getHeight(), cornerRadius);
        nvgFillColor(vg, NanoUtility.color1(background)); nvgFill(vg);
        nvgStrokeColor(vg, NanoUtility.color2(border)); nvgStrokeWidth(vg, 1); nvgStroke(vg);
        String text = getText();
        if (text == null || text.isBlank()) return;
        nvgFontFace(vg, fontName); nvgFontSize(vg, fontSize); nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, NanoUtility.color3(foreground));
        nvgText(vg, x + padding, y + getHeight() * .5f, text);
    }
}
