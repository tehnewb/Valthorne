package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.NodeAction;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoButton extends UINode implements NanoNode {

    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.hoverBackgroundColor", Color.class, new Color(0xFF323232));
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.focusedBackgroundColor", Color.class, new Color(0xFF3A3A3A));
    public static final StyleKey<Color> PRESSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.pressedBackgroundColor", Color.class, new Color(0xFF202020));
    public static final StyleKey<Color> DISABLED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.disabledBackgroundColor", Color.class, new Color(0xFF242424));

    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.button.borderColor", Color.class, new Color(0xFF555555));
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.button.hoverBorderColor", Color.class, new Color(0xFF777777));
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.button.focusedBorderColor", Color.class, new Color(0xFF7AA2FF));
    public static final StyleKey<Color> PRESSED_BORDER_COLOR_KEY = StyleKey.of("nano.button.pressedBorderColor", Color.class, new Color(0xFF4A4A4A));
    public static final StyleKey<Color> DISABLED_BORDER_COLOR_KEY = StyleKey.of("nano.button.disabledBorderColor", Color.class, new Color(0xFF3A3A3A));

    public static final StyleKey<Color> TEXT_COLOR_KEY = StyleKey.of("nano.button.textColor", Color.class, Color.WHITE);
    public static final StyleKey<Color> HOVER_TEXT_COLOR_KEY = StyleKey.of("nano.button.hoverTextColor", Color.class, Color.WHITE);
    public static final StyleKey<Color> FOCUSED_TEXT_COLOR_KEY = StyleKey.of("nano.button.focusedTextColor", Color.class, Color.WHITE);
    public static final StyleKey<Color> PRESSED_TEXT_COLOR_KEY = StyleKey.of("nano.button.pressedTextColor", Color.class, Color.WHITE);
    public static final StyleKey<Color> DISABLED_TEXT_COLOR_KEY = StyleKey.of("nano.button.disabledTextColor", Color.class, new Color(0xFF9A9A9A));

    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.button.fontName", String.class, "default");
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.button.fontSize", Float.class, 18f);
    public static final StyleKey<Float> PADDING_X_KEY = StyleKey.of("nano.button.paddingX", Float.class, 14f);
    public static final StyleKey<Float> PADDING_Y_KEY = StyleKey.of("nano.button.paddingY", Float.class, 8f);
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.button.cornerRadius", Float.class, 6f);
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.button.borderWidth", Float.class, 1f);
    public static final StyleKey<NodeAction<NanoButton>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<NanoButton>>) (Class<?>) NodeAction.class);

    private String text = "";

    private Color backgroundColor = new Color(0xFF2A2A2A);
    private Color hoverBackgroundColor = new Color(0xFF323232);
    private Color focusedBackgroundColor = new Color(0xFF3A3A3A);
    private Color pressedBackgroundColor = new Color(0xFF202020);
    private Color disabledBackgroundColor = new Color(0xFF242424);

    private Color borderColor = new Color(0xFF555555);
    private Color hoverBorderColor = new Color(0xFF777777);
    private Color focusedBorderColor = new Color(0xFF7AA2FF);
    private Color pressedBorderColor = new Color(0xFF4A4A4A);
    private Color disabledBorderColor = new Color(0xFF3A3A3A);

    private Color textColor = Color.WHITE;
    private Color hoverTextColor = Color.WHITE;
    private Color focusedTextColor = Color.WHITE;
    private Color pressedTextColor = Color.WHITE;
    private Color disabledTextColor = new Color(0xFF9A9A9A);

    private String fontName = "default";
    private float fontSize = 18f;
    private float paddingX = 14f;
    private float paddingY = 8f;
    private float cornerRadius = 6f;
    private float borderWidth = 1f;

    private boolean fontLoaded;
    private NodeAction<NanoButton> action;

    public NanoButton() {
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        markLayoutDirty();
    }

    public NanoButton(String text) {
        this();
        text(text);
    }

    public String getText() {
        return text;
    }

    public NanoButton text(String text) {
        this.text = text == null ? "" : text;
        markLayoutDirty();
        return this;
    }

    public NodeAction<NanoButton> getAction() {
        return action;
    }

    public NanoButton action(NodeAction<NanoButton> action) {
        this.action = action;
        return this;
    }

    public NanoButton fontName(String fontName) {
        if (fontName != null && !fontName.isBlank())
            this.fontName = fontName;
        markLayoutDirty();
        return this;
    }

    public NanoButton fontSize(float fontSize) {
        this.fontSize = Math.max(1f, fontSize);
        markLayoutDirty();
        return this;
    }

    public NanoButton paddingX(float paddingX) {
        this.paddingX = Math.max(0f, paddingX);
        markLayoutDirty();
        return this;
    }

    public NanoButton paddingY(float paddingY) {
        this.paddingY = Math.max(0f, paddingY);
        markLayoutDirty();
        return this;
    }

    public NanoButton cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    public NanoButton borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    public NanoButton backgroundColor(Color color) {
        if (color != null)
            this.backgroundColor = color;
        return this;
    }

    public NanoButton hoverBackgroundColor(Color color) {
        if (color != null)
            this.hoverBackgroundColor = color;
        return this;
    }

    public NanoButton focusedBackgroundColor(Color color) {
        if (color != null)
            this.focusedBackgroundColor = color;
        return this;
    }

    public NanoButton pressedBackgroundColor(Color color) {
        if (color != null)
            this.pressedBackgroundColor = color;
        return this;
    }

    public NanoButton disabledBackgroundColor(Color color) {
        if (color != null)
            this.disabledBackgroundColor = color;
        return this;
    }

    public NanoButton borderColor(Color color) {
        if (color != null)
            this.borderColor = color;
        return this;
    }

    public NanoButton hoverBorderColor(Color color) {
        if (color != null)
            this.hoverBorderColor = color;
        return this;
    }

    public NanoButton focusedBorderColor(Color color) {
        if (color != null)
            this.focusedBorderColor = color;
        return this;
    }

    public NanoButton pressedBorderColor(Color color) {
        if (color != null)
            this.pressedBorderColor = color;
        return this;
    }

    public NanoButton disabledBorderColor(Color color) {
        if (color != null)
            this.disabledBorderColor = color;
        return this;
    }

    public NanoButton textColor(Color color) {
        if (color != null)
            this.textColor = color;
        return this;
    }

    public NanoButton hoverTextColor(Color color) {
        if (color != null)
            this.hoverTextColor = color;
        return this;
    }

    public NanoButton focusedTextColor(Color color) {
        if (color != null)
            this.focusedTextColor = color;
        return this;
    }

    public NanoButton pressedTextColor(Color color) {
        if (color != null)
            this.pressedTextColor = color;
        return this;
    }

    public NanoButton disabledTextColor(Color color) {
        if (color != null)
            this.disabledTextColor = color;
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
    public void onKeyPress(KeyPressEvent event) {
        if (action == null)
            return;

        if (event.getKey() == Keyboard.ENTER)
            action.perform(this);
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (action != null) {
            action.perform(this);
            return;
        }

        ResolvedStyle style = getStyle();
        if (style != null) {
            NodeAction<NanoButton> resolved = style.get(ACTION_KEY);
            if (resolved != null)
                resolved.perform(this);
        }
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

            Color resolvedTextColor = style.get(TEXT_COLOR_KEY);
            Color resolvedHoverTextColor = style.get(HOVER_TEXT_COLOR_KEY);
            Color resolvedFocusedTextColor = style.get(FOCUSED_TEXT_COLOR_KEY);
            Color resolvedPressedTextColor = style.get(PRESSED_TEXT_COLOR_KEY);
            Color resolvedDisabledTextColor = style.get(DISABLED_TEXT_COLOR_KEY);

            String resolvedFontName = style.get(FONT_NAME_KEY);
            Float resolvedFontSize = style.get(FONT_SIZE_KEY);
            Float resolvedPaddingX = style.get(PADDING_X_KEY);
            Float resolvedPaddingY = style.get(PADDING_Y_KEY);
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

            if (resolvedTextColor != null)
                textColor = resolvedTextColor;
            if (resolvedHoverTextColor != null)
                hoverTextColor = resolvedHoverTextColor;
            if (resolvedFocusedTextColor != null)
                focusedTextColor = resolvedFocusedTextColor;
            if (resolvedPressedTextColor != null)
                pressedTextColor = resolvedPressedTextColor;
            if (resolvedDisabledTextColor != null)
                disabledTextColor = resolvedDisabledTextColor;

            if (resolvedFontName != null && !resolvedFontName.isBlank())
                fontName = resolvedFontName;
            if (resolvedFontSize != null)
                fontSize = Math.max(1f, resolvedFontSize);
            if (resolvedPaddingX != null)
                paddingX = Math.max(0f, resolvedPaddingX);
            if (resolvedPaddingY != null)
                paddingY = Math.max(0f, resolvedPaddingY);
            if (resolvedCornerRadius != null)
                cornerRadius = Math.max(0f, resolvedCornerRadius);
            if (resolvedBorderWidth != null)
                borderWidth = Math.max(0f, resolvedBorderWidth);
        }

        float measuredWidth = measureTextWidth(text) + paddingX * 2f;
        float measuredHeight = measureTextHeight() + paddingY * 2f;

        if (getLayout().getWidth().isAuto())
            getLayout().width(measuredWidth);

        if (getLayout().getHeight().isAuto())
            getLayout().height(measuredHeight);

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

        Color drawBackground = backgroundColor;
        Color drawBorder = borderColor;
        Color drawText = textColor;

        if (!isEnabled()) {
            drawBackground = disabledBackgroundColor;
            drawBorder = disabledBorderColor;
            drawText = disabledTextColor;
        } else if (isPressed()) {
            drawBackground = pressedBackgroundColor;
            drawBorder = pressedBorderColor;
            drawText = pressedTextColor;
        } else if (isFocused()) {
            drawBackground = focusedBackgroundColor;
            drawBorder = focusedBorderColor;
            drawText = focusedTextColor;
        } else if (isHovered()) {
            drawBackground = hoverBackgroundColor;
            drawBorder = hoverBorderColor;
            drawText = hoverTextColor;
        }

        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, width, height, cornerRadius);
        nvgFillColor(vg, NanoUtility.color1(drawBackground));
        nvgFill(vg);

        if (borderWidth > 0f) {
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, width, height, cornerRadius);
            nvgStrokeWidth(vg, borderWidth);
            nvgStrokeColor(vg, NanoUtility.color1(drawBorder));
            nvgStroke(vg);
        }

        if (text != null && !text.isEmpty()) {
            nvgFontSize(vg, fontSize);
            nvgFontFace(vg, fontName);
            nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            nvgFillColor(vg, NanoUtility.color1(drawText));
            nvgText(vg, x + width * 0.5f, y + height * 0.5f, text);
        }
    }

    private float measureTextWidth(String value) {
        UIRoot root = getRoot();
        if (root == null || value == null || value.isEmpty())
            return value == null ? 0f : value.length() * fontSize * 0.5f;

        return NanoUtility.measureTextWidth(root.getNanoVGHandle(), fontName, fontSize, value);
    }

    private float measureTextHeight() {
        UIRoot root = getRoot();
        if (root == null)
            return fontSize;

        return NanoUtility.measureTextHeight(root.getNanoVGHandle(), fontName, fontSize);
    }

    private String sanitize(String value) {
        if (value == null || value.isEmpty())
            return "";

        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t' || Character.isISOControl(c)) {
                if (builder.isEmpty() || builder.charAt(builder.length() - 1) != ' ')
                    builder.append(' ');
            } else {
                builder.append(c);
            }
        }

        return builder.toString();
    }
}