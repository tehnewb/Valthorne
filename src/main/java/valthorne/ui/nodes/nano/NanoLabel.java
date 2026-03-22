package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoLabel extends UINode implements NanoNode {

    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.label.fontName", String.class, "default");
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.label.fontSize", Float.class, 18f);
    public static final StyleKey<Color> COLOR_KEY = StyleKey.of("nano.label.color", Color.class, Color.WHITE);

    private String text = "";
    private String fontName = "default";
    private float fontSize = 18f;
    private Color color = Color.WHITE;
    private boolean fontLoaded;

    public NanoLabel() {
    }

    public NanoLabel(String text) {
        this.text = text == null ? "" : text;
    }

    public String getText() {
        return text;
    }

    public NanoLabel text(String text) {
        this.text = text == null ? "" : text;
        markLayoutDirty();
        return this;
    }

    public String getFontName() {
        return fontName;
    }

    public NanoLabel fontName(String fontName) {
        if (fontName != null && !fontName.isBlank()) this.fontName = fontName;
        markLayoutDirty();
        return this;
    }

    public float getFontSize() {
        return fontSize;
    }

    public NanoLabel fontSize(float fontSize) {
        this.fontSize = Math.max(1f, fontSize);
        markLayoutDirty();
        return this;
    }

    public Color getColor() {
        return color;
    }

    public NanoLabel color(Color color) {
        if (color != null) this.color = color;
        return this;
    }

    @Override
    public void onCreate() {

    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public void draw(TextureBatch batch) {

    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            String resolvedFontName = style.get(FONT_NAME_KEY);
            Float resolvedFontSize = style.get(FONT_SIZE_KEY);
            Color resolvedColor = style.get(COLOR_KEY);

            if (resolvedFontName != null && !resolvedFontName.isBlank()) fontName = resolvedFontName;
            if (resolvedFontSize != null) fontSize = Math.max(1f, resolvedFontSize);
            if (resolvedColor != null) color = resolvedColor;
        }

        UIRoot root = getRoot();
        long vg = root != null ? root.getNanoVGHandle() : 0L;

        float measuredWidth;
        float measuredHeight;

        if (vg != 0L) {
            measuredWidth = NanoUtility.measureTextWidth(vg, fontName, fontSize, text);
            measuredHeight = NanoUtility.measureTextHeight(vg, fontName, fontSize);
        } else {
            measuredWidth = text.length() * fontSize * 0.5f;
            measuredHeight = fontSize;
        }

        if (getLayout().getWidth().isAuto()) getLayout().width(measuredWidth);

        if (getLayout().getHeight().isAuto()) getLayout().height(measuredHeight);

        super.applyLayout();
    }

    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L) return;

        nvgFontSize(vg, fontSize);
        nvgFontFace(vg, fontName);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgFillColor(vg, NanoUtility.color1(color));
        nvgText(vg, getAbsoluteX(), getAbsoluteY(), text);
    }
}