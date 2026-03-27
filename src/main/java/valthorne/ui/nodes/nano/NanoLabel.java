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
    public static final StyleKey<Float> TAB_SIZE_KEY = StyleKey.of("nano.label.tabSize", Float.class, 4f);
    public static final StyleKey<Float> LINE_SPACING_KEY = StyleKey.of("nano.label.lineSpacing", Float.class, 0f);

    private String text = "";
    private String fontName = "default";
    private float fontSize = 18f;
    private Color color = Color.WHITE;
    private float tabSize = 4f;
    private float lineSpacing;
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

    public float getTabSize() {
        return tabSize;
    }

    public NanoLabel tabSize(float tabSize) {
        this.tabSize = Math.max(1f, tabSize);
        markLayoutDirty();
        return this;
    }

    public float getLineSpacing() {
        return lineSpacing;
    }

    public NanoLabel lineSpacing(float lineSpacing) {
        this.lineSpacing = Math.max(0f, lineSpacing);
        markLayoutDirty();
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
            Float resolvedTabSize = style.get(TAB_SIZE_KEY);
            Float resolvedLineSpacing = style.get(LINE_SPACING_KEY);

            if (resolvedFontName != null && !resolvedFontName.isBlank()) fontName = resolvedFontName;
            if (resolvedFontSize != null) fontSize = Math.max(1f, resolvedFontSize);
            if (resolvedColor != null) color = resolvedColor;
            if (resolvedTabSize != null) tabSize = Math.max(1f, resolvedTabSize);
            if (resolvedLineSpacing != null) lineSpacing = Math.max(0f, resolvedLineSpacing);
        }

        String normalized = normalizeText(text);
        String[] lines = splitLines(normalized);

        UIRoot root = getRoot();
        long vg = root != null ? root.getNanoVGHandle() : 0L;

        float measuredWidth = 0f;
        float measuredHeight;

        if (vg != 0L) {
            nvgFontSize(vg, fontSize);
            nvgFontFace(vg, fontName);

            float lineHeight = NanoUtility.measureTextHeight(vg, fontName, fontSize);

            for (String line : lines) {
                measuredWidth = Math.max(measuredWidth, NanoUtility.measureTextWidth(vg, fontName, fontSize, line));
            }

            measuredHeight = lines.length == 0 ? lineHeight : (lines.length * lineHeight) + Math.max(0, lines.length - 1) * lineSpacing;
        } else {
            float estimatedLineHeight = fontSize;
            for (String line : lines) {
                measuredWidth = Math.max(measuredWidth, line.length() * fontSize * 0.5f);
            }
            measuredHeight = lines.length == 0 ? estimatedLineHeight : (lines.length * estimatedLineHeight) + Math.max(0, lines.length - 1) * lineSpacing;
        }

        if (getLayout().getWidth().isAuto()) getLayout().width(measuredWidth);
        if (getLayout().getHeight().isAuto()) getLayout().height(measuredHeight);

        super.applyLayout();
    }

    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L) return;

        String normalized = normalizeText(text);
        String[] lines = splitLines(normalized);

        nvgFontSize(vg, fontSize);
        nvgFontFace(vg, fontName);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgFillColor(vg, NanoUtility.color1(color));

        float lineHeight = NanoUtility.measureTextHeight(vg, fontName, fontSize);
        float x = getAbsoluteX();
        float y = getAbsoluteY();

        for (int i = 0; i < lines.length; i++) {
            nvgText(vg, x, y + i * (lineHeight + lineSpacing), lines[i]);
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').replace('\f', '\n').replace('\u000B', '\n');
        return expandTabs(normalized, Math.max(1, Math.round(tabSize)));
    }

    private String expandTabs(String value, int tabWidth) {
        StringBuilder builder = new StringBuilder(value.length());
        int column = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '\n') {
                builder.append('\n');
                column = 0;
                continue;
            }

            if (c == '\t') {
                int spaces = tabWidth - (column % tabWidth);
                builder.append(" ".repeat(Math.max(0, spaces)));
                column += spaces;
                continue;
            }

            builder.append(c);
            column++;
        }

        return builder.toString();
    }

    private String[] splitLines(String value) {
        return value.split("\n", -1);
    }
}