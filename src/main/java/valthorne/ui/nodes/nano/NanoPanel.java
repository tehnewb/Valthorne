package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.UIContainer;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoPanel extends NanoContainer {

    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.backgroundColor", Color.class, null);
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.hoverBackgroundColor", Color.class, null);
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.focusedBackgroundColor", Color.class, null);
    public static final StyleKey<Color> PRESSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.pressedBackgroundColor", Color.class, null);
    public static final StyleKey<Color> DISABLED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.disabledBackgroundColor", Color.class, null);

    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.panel.borderColor", Color.class, null);
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.panel.hoverBorderColor", Color.class, null);
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.panel.focusedBorderColor", Color.class, null);
    public static final StyleKey<Color> PRESSED_BORDER_COLOR_KEY = StyleKey.of("nano.panel.pressedBorderColor", Color.class, null);
    public static final StyleKey<Color> DISABLED_BORDER_COLOR_KEY = StyleKey.of("nano.panel.disabledBorderColor", Color.class, null);

    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.panel.cornerRadius", Float.class, 6f);
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.panel.borderWidth", Float.class, 1f);

    private Color backgroundColor = new Color(0xFF242424);
    private Color hoverBackgroundColor = new Color(0xFF242424);
    private Color focusedBackgroundColor = new Color(0xFF242424);
    private Color pressedBackgroundColor = new Color(0xFF242424);
    private Color disabledBackgroundColor = new Color(0xFF242424);

    private Color borderColor = new Color(0xFF242424);
    private Color hoverBorderColor = new Color(0xFF242424);
    private Color focusedBorderColor = new Color(0xFF242424);
    private Color pressedBorderColor = new Color(0xFF242424);
    private Color disabledBorderColor = new Color(0xFF242424);

    private float cornerRadius = 6f;
    private float borderWidth = 1f;

    public NanoPanel backgroundColor(Color color) {
        if (color != null)
            this.backgroundColor = color;
        return this;
    }

    public NanoPanel hoverBackgroundColor(Color color) {
        if (color != null)
            this.hoverBackgroundColor = color;
        return this;
    }

    public NanoPanel focusedBackgroundColor(Color color) {
        if (color != null)
            this.focusedBackgroundColor = color;
        return this;
    }

    public NanoPanel pressedBackgroundColor(Color color) {
        if (color != null)
            this.pressedBackgroundColor = color;
        return this;
    }

    public NanoPanel disabledBackgroundColor(Color color) {
        if (color != null)
            this.disabledBackgroundColor = color;
        return this;
    }

    public NanoPanel borderColor(Color color) {
        if (color != null)
            this.borderColor = color;
        return this;
    }

    public NanoPanel hoverBorderColor(Color color) {
        if (color != null)
            this.hoverBorderColor = color;
        return this;
    }

    public NanoPanel focusedBorderColor(Color color) {
        if (color != null)
            this.focusedBorderColor = color;
        return this;
    }

    public NanoPanel pressedBorderColor(Color color) {
        if (color != null)
            this.pressedBorderColor = color;
        return this;
    }

    public NanoPanel disabledBorderColor(Color color) {
        if (color != null)
            this.disabledBorderColor = color;
        return this;
    }

    public NanoPanel cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    public NanoPanel borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Color resolvedBackgroundColor = style.get(BACKGROUND_COLOR_KEY);
            Color resolvedHoverBackgroundColor = style.get(HOVER_BACKGROUND_COLOR_KEY);
            Color resolvedFocusedBackgroundColor = style.get(FOCUSED_BACKGROUND_COLOR_KEY);
            Color resolvedPressedBackgroundColor = style.get(PRESSED_BACKGROUND_COLOR_KEY);
            Color resolvedDisabledBackgroundColor = style.get(DISABLED_BACKGROUND_COLOR_KEY);

            Color resolvedBorderColor = style.get(BORDER_COLOR_KEY);
            Color resolvedHoverBorderColor = style.get(HOVER_BORDER_COLOR_KEY);
            Color resolvedFocusedBorderColor = style.get(FOCUSED_BORDER_COLOR_KEY);
            Color resolvedPressedBorderColor = style.get(PRESSED_BORDER_COLOR_KEY);
            Color resolvedDisabledBorderColor = style.get(DISABLED_BORDER_COLOR_KEY);

            Float resolvedCornerRadius = style.get(CORNER_RADIUS_KEY);
            Float resolvedBorderWidth = style.get(BORDER_WIDTH_KEY);

            if (resolvedBackgroundColor != null)
                backgroundColor = resolvedBackgroundColor;
            if (resolvedHoverBackgroundColor != null)
                hoverBackgroundColor = resolvedHoverBackgroundColor;
            if (resolvedFocusedBackgroundColor != null)
                focusedBackgroundColor = resolvedFocusedBackgroundColor;
            if (resolvedPressedBackgroundColor != null)
                pressedBackgroundColor = resolvedPressedBackgroundColor;
            if (resolvedDisabledBackgroundColor != null)
                disabledBackgroundColor = resolvedDisabledBackgroundColor;

            if (resolvedBorderColor != null)
                borderColor = resolvedBorderColor;
            if (resolvedHoverBorderColor != null)
                hoverBorderColor = resolvedHoverBorderColor;
            if (resolvedFocusedBorderColor != null)
                focusedBorderColor = resolvedFocusedBorderColor;
            if (resolvedPressedBorderColor != null)
                pressedBorderColor = resolvedPressedBorderColor;
            if (resolvedDisabledBorderColor != null)
                disabledBorderColor = resolvedDisabledBorderColor;

            if (resolvedCornerRadius != null)
                cornerRadius = Math.max(0f, resolvedCornerRadius);
            if (resolvedBorderWidth != null)
                borderWidth = Math.max(0f, resolvedBorderWidth);
        }

        super.applyLayout();
    }

    @Override
    public void draw(long vg) {
        Color drawBackground = backgroundColor;
        Color drawBorder = borderColor;

        if (!isEnabled()) {
            drawBackground = disabledBackgroundColor;
            drawBorder = disabledBorderColor;
        } else if (isPressed()) {
            drawBackground = pressedBackgroundColor;
            drawBorder = pressedBorderColor;
        } else if (isFocused()) {
            drawBackground = focusedBackgroundColor;
            drawBorder = focusedBorderColor;
        } else if (isHovered()) {
            drawBackground = hoverBackgroundColor;
            drawBorder = hoverBorderColor;
        }

        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float width = getWidth();
        float height = getHeight();

        nvgBeginPath(vg);
        nvgFillColor(vg, NanoUtility.color1(drawBackground));
        nvgRoundedRect(vg, x, y, width, height, cornerRadius);
        nvgFill(vg);

        if (borderWidth > 0f) {
            float inset = borderWidth * 0.5f;
            nvgBeginPath(vg);
            nvgStrokeWidth(vg, borderWidth);
            nvgStrokeColor(vg, NanoUtility.color2(drawBorder));
            nvgRoundedRect(vg, x + inset, y + inset, Math.max(0f, width - borderWidth), Math.max(0f, height - borderWidth), Math.max(0f, cornerRadius - inset));
            nvgStroke(vg);
        }

        super.draw(vg);
    }
}