package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoProgressBar extends UINode implements NanoNode {

    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.progressbar.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    public static final StyleKey<Color> FOREGROUND_COLOR_KEY = StyleKey.of("nano.progressbar.foregroundColor", Color.class, new Color(0xFF7AA2FF));
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.progressbar.borderColor", Color.class, new Color(0xFF555555));
    public static final StyleKey<Color> TEXT_COLOR_KEY = StyleKey.of("nano.progressbar.textColor", Color.class, Color.WHITE);

    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.progressbar.fontName", String.class, "default");
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.progressbar.fontSize", Float.class, 16f);
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.progressbar.cornerRadius", Float.class, 6f);
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.progressbar.borderWidth", Float.class, 1f);
    public static final StyleKey<Float> TEXT_PADDING_X_KEY = StyleKey.of("nano.progressbar.textPaddingX", Float.class, 8f);

    private final float min;
    private final float max;

    private float progress;
    private float displayedProgress;
    private boolean displayPercentage;
    private boolean vertical;

    private Color backgroundColor = new Color(0xFF2A2A2A);
    private Color foregroundColor = new Color(0xFF7AA2FF);
    private Color borderColor = new Color(0xFF555555);
    private Color textColor = Color.WHITE;

    private String fontName = "default";
    private float fontSize = 16f;
    private float cornerRadius = 6f;
    private float borderWidth = 1f;
    private float textPaddingX = 8f;

    public NanoProgressBar(float min, float max) {
        this.min = min;
        this.max = max;
        this.progress = min;
        this.displayedProgress = min;
    }

    public NanoProgressBar progress(float progress) {
        this.progress = MathUtils.clamp(progress, min, max);
        return this;
    }

    public float getProgress() {
        return progress;
    }

    public NanoProgressBar displayPercentage(boolean displayPercentage) {
        this.displayPercentage = displayPercentage;
        return this;
    }

    public boolean isDisplayPercentage() {
        return displayPercentage;
    }

    public boolean isVertical() {
        return vertical;
    }

    public NanoProgressBar vertical(boolean vertical) {
        this.vertical = vertical;
        return this;
    }

    public NanoProgressBar horizontal(boolean horizontal) {
        this.vertical = !horizontal;
        return this;
    }

    public NanoProgressBar backgroundColor(Color color) {
        if (color != null)
            this.backgroundColor = color;
        return this;
    }

    public NanoProgressBar foregroundColor(Color color) {
        if (color != null)
            this.foregroundColor = color;
        return this;
    }

    public NanoProgressBar borderColor(Color color) {
        if (color != null)
            this.borderColor = color;
        return this;
    }

    public NanoProgressBar textColor(Color color) {
        if (color != null)
            this.textColor = color;
        return this;
    }

    public NanoProgressBar fontName(String fontName) {
        if (fontName != null && !fontName.isBlank())
            this.fontName = fontName;
        return this;
    }

    public NanoProgressBar fontSize(float fontSize) {
        this.fontSize = Math.max(1f, fontSize);
        return this;
    }

    public NanoProgressBar cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    public NanoProgressBar borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    public NanoProgressBar textPaddingX(float textPaddingX) {
        this.textPaddingX = Math.max(0f, textPaddingX);
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
        displayedProgress = MathUtils.lerp(displayedProgress, progress, delta * 20f);
    }

    @Override
    public void draw(TextureBatch batch) {

    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Color resolvedBackgroundColor = style.get(BACKGROUND_COLOR_KEY);
            Color resolvedForegroundColor = style.get(FOREGROUND_COLOR_KEY);
            Color resolvedBorderColor = style.get(BORDER_COLOR_KEY);
            Color resolvedTextColor = style.get(TEXT_COLOR_KEY);

            String resolvedFontName = style.get(FONT_NAME_KEY);
            Float resolvedFontSize = style.get(FONT_SIZE_KEY);
            Float resolvedCornerRadius = style.get(CORNER_RADIUS_KEY);
            Float resolvedBorderWidth = style.get(BORDER_WIDTH_KEY);
            Float resolvedTextPaddingX = style.get(TEXT_PADDING_X_KEY);

            if (resolvedBackgroundColor != null)
                backgroundColor = resolvedBackgroundColor;
            if (resolvedForegroundColor != null)
                foregroundColor = resolvedForegroundColor;
            if (resolvedBorderColor != null)
                borderColor = resolvedBorderColor;
            if (resolvedTextColor != null)
                textColor = resolvedTextColor;

            if (resolvedFontName != null && !resolvedFontName.isBlank())
                fontName = resolvedFontName;
            if (resolvedFontSize != null)
                fontSize = Math.max(1f, resolvedFontSize);
            if (resolvedCornerRadius != null)
                cornerRadius = Math.max(0f, resolvedCornerRadius);
            if (resolvedBorderWidth != null)
                borderWidth = Math.max(0f, resolvedBorderWidth);
            if (resolvedTextPaddingX != null)
                textPaddingX = Math.max(0f, resolvedTextPaddingX);
        }

        super.applyLayout();
    }

    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L)
            return;

        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float width = getWidth();
        float height = getHeight();
        float percentage = getPercentage();

        nvgBeginPath(vg);
        nvgFillColor(vg, NanoUtility.color1(backgroundColor));
        nvgRoundedRect(vg, x, y, width, height, cornerRadius);
        nvgFill(vg);

        if (percentage > 0f) {
            if (vertical) {
                float filledHeight = height * percentage;
                float filledY = y + (height - filledHeight);

                nvgBeginPath(vg);
                nvgFillColor(vg, NanoUtility.color1(foregroundColor));
                nvgRoundedRect(vg, x + borderWidth, filledY + borderWidth, width - borderWidth * 2, filledHeight - borderWidth * 2, cornerRadius);
                nvgFill(vg);
            } else {
                float filledWidth = width * percentage;
                nvgBeginPath(vg);
                nvgFillColor(vg, NanoUtility.color1(foregroundColor));
                nvgRoundedRect(vg, x + borderWidth, y + borderWidth, filledWidth - borderWidth * 2, height - borderWidth * 2, cornerRadius);
                nvgFill(vg);
            }
        }

        if (borderWidth > 0f) {
            nvgBeginPath(vg);
            nvgStrokeWidth(vg, borderWidth);
            nvgStrokeColor(vg, NanoUtility.color1(borderColor));
            nvgRoundedRect(vg, x + borderWidth * 0.5f, y + borderWidth * 0.5f, width - borderWidth, height - borderWidth, Math.max(0f, cornerRadius - borderWidth * 0.5f));
            nvgStroke(vg);
        }

        if (displayPercentage) {
            String text = String.format("%.2f%%", percentage * 100f);
            float textWidth = NanoUtility.measureTextWidth(vg, fontName, fontSize, text);
            float textY = NanoUtility.getTextCenterY(vg, fontName, fontSize, y, height);

            float textX = x + (width - textWidth) * 0.5f;
            textX = Math.max(x + textPaddingX, textX);
            textX = Math.min(x + width - textPaddingX - textWidth, textX);

            nvgFontSize(vg, fontSize);
            nvgFontFace(vg, fontName);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_BASELINE);
            nvgFillColor(vg, NanoUtility.color1(textColor));
            nvgText(vg, textX, textY, text);
        }
    }

    private float getPercentage() {
        float range = max - min;
        if (range == 0f)
            return 0f;
        return MathUtils.clamp((displayedProgress - min) / range, 0f, 1f);
    }
}