package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.NodeAction;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoCheckbox extends UINode implements NanoNode {

    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.checkbox.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.checkbox.hoverBackgroundColor", Color.class, new Color(0xFF323232));
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.checkbox.focusedBackgroundColor", Color.class, new Color(0xFF3A3A3A));
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.checkbox.borderColor", Color.class, new Color(0xFF555555));
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.checkbox.hoverBorderColor", Color.class, new Color(0xFF777777));
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.checkbox.focusedBorderColor", Color.class, new Color(0xFF7AA2FF));
    public static final StyleKey<Color> CHECKMARK_COLOR_KEY = StyleKey.of("nano.checkbox.checkmarkColor", Color.class, new Color(0xFFFFFFFF));
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.checkbox.cornerRadius", Float.class, 6f);
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.checkbox.borderWidth", Float.class, 1f);
    public static final StyleKey<Float> CHECKMARK_SCALE_KEY = StyleKey.of("nano.checkbox.checkmarkScale", Float.class, 0.5f);
    public static final StyleKey<Float> CHECKMARK_THICKNESS_KEY = StyleKey.of("nano.checkbox.checkmarkThickness", Float.class, 2.5f);
    public static final StyleKey<NodeAction<NanoCheckbox>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<NanoCheckbox>>) (Class<?>) NodeAction.class);

    private NodeAction<NanoCheckbox> action;
    private boolean checked;

    private Color backgroundColor = new Color(0xFF2A2A2A);
    private Color hoverBackgroundColor = new Color(0xFF323232);
    private Color focusedBackgroundColor = new Color(0xFF3A3A3A);
    private Color borderColor = new Color(0xFF555555);
    private Color hoverBorderColor = new Color(0xFF777777);
    private Color focusedBorderColor = new Color(0xFF7AA2FF);
    private Color checkmarkColor = new Color(0xFFFFFFFF);

    private float cornerRadius = 6f;
    private float borderWidth = 1f;
    private float checkmarkScale = 0.5f;
    private float checkmarkThickness = 2.5f;

    public NanoCheckbox() {
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
    }

    public NodeAction<NanoCheckbox> getAction() {
        return action;
    }

    public NanoCheckbox action(NodeAction<NanoCheckbox> action) {
        this.action = action;
        return this;
    }

    public boolean isChecked() {
        return checked;
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

    public NanoCheckbox checked(boolean checked) {
        if (this.checked == checked)
            return this;

        this.checked = checked;
        setChecked(checked);

        NodeAction<NanoCheckbox> resolvedAction = action;
        if (resolvedAction == null) {
            ResolvedStyle style = getStyle();
            if (style != null)
                resolvedAction = style.get(ACTION_KEY);
        }

        if (resolvedAction != null)
            resolvedAction.perform(this);

        return this;
    }

    public NanoCheckbox toggle() {
        return checked(!checked);
    }

    public NanoCheckbox backgroundColor(Color color) {
        if (color != null)
            this.backgroundColor = color;
        return this;
    }

    public NanoCheckbox hoverBackgroundColor(Color color) {
        if (color != null)
            this.hoverBackgroundColor = color;
        return this;
    }

    public NanoCheckbox focusedBackgroundColor(Color color) {
        if (color != null)
            this.focusedBackgroundColor = color;
        return this;
    }

    public NanoCheckbox borderColor(Color color) {
        if (color != null)
            this.borderColor = color;
        return this;
    }

    public NanoCheckbox hoverBorderColor(Color color) {
        if (color != null)
            this.hoverBorderColor = color;
        return this;
    }

    public NanoCheckbox focusedBorderColor(Color color) {
        if (color != null)
            this.focusedBorderColor = color;
        return this;
    }

    public NanoCheckbox checkmarkColor(Color color) {
        if (color != null)
            this.checkmarkColor = color;
        return this;
    }

    public NanoCheckbox cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    public NanoCheckbox borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    public NanoCheckbox checkmarkScale(float checkmarkScale) {
        this.checkmarkScale = Math.max(0f, checkmarkScale);
        return this;
    }

    public NanoCheckbox checkmarkThickness(float checkmarkThickness) {
        this.checkmarkThickness = Math.max(0f, checkmarkThickness);
        return this;
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
        int key = event.getKey();
        if (key == Keyboard.SPACE || key == Keyboard.ENTER)
            toggle();
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        toggle();
    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Color resolvedBackgroundColor = style.get(BACKGROUND_COLOR_KEY);
            Color resolvedHoverBackgroundColor = style.get(HOVER_BACKGROUND_COLOR_KEY);
            Color resolvedFocusedBackgroundColor = style.get(FOCUSED_BACKGROUND_COLOR_KEY);
            Color resolvedBorderColor = style.get(BORDER_COLOR_KEY);
            Color resolvedHoverBorderColor = style.get(HOVER_BORDER_COLOR_KEY);
            Color resolvedFocusedBorderColor = style.get(FOCUSED_BORDER_COLOR_KEY);
            Color resolvedCheckmarkColor = style.get(CHECKMARK_COLOR_KEY);
            Float resolvedCornerRadius = style.get(CORNER_RADIUS_KEY);
            Float resolvedBorderWidth = style.get(BORDER_WIDTH_KEY);
            Float resolvedCheckmarkScale = style.get(CHECKMARK_SCALE_KEY);
            Float resolvedCheckmarkThickness = style.get(CHECKMARK_THICKNESS_KEY);

            if (resolvedBackgroundColor != null)
                backgroundColor = resolvedBackgroundColor;
            if (resolvedHoverBackgroundColor != null)
                hoverBackgroundColor = resolvedHoverBackgroundColor;
            if (resolvedFocusedBackgroundColor != null)
                focusedBackgroundColor = resolvedFocusedBackgroundColor;
            if (resolvedBorderColor != null)
                borderColor = resolvedBorderColor;
            if (resolvedHoverBorderColor != null)
                hoverBorderColor = resolvedHoverBorderColor;
            if (resolvedFocusedBorderColor != null)
                focusedBorderColor = resolvedFocusedBorderColor;
            if (resolvedCheckmarkColor != null)
                checkmarkColor = resolvedCheckmarkColor;
            if (resolvedCornerRadius != null)
                cornerRadius = Math.max(0f, resolvedCornerRadius);
            if (resolvedBorderWidth != null)
                borderWidth = Math.max(0f, resolvedBorderWidth);
            if (resolvedCheckmarkScale != null)
                checkmarkScale = Math.max(0f, resolvedCheckmarkScale);
            if (resolvedCheckmarkThickness != null)
                checkmarkThickness = Math.max(0f, resolvedCheckmarkThickness);
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

        Color drawBackgroundColor = backgroundColor;
        Color drawBorderColor = borderColor;

        if (isFocused()) {
            drawBackgroundColor = focusedBackgroundColor;
            drawBorderColor = focusedBorderColor;
        } else if (isHovered()) {
            drawBackgroundColor = hoverBackgroundColor;
            drawBorderColor = hoverBorderColor;
        }

        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, width, height, cornerRadius);
        nvgFillColor(vg, NanoUtility.color1(drawBackgroundColor));
        nvgFill(vg);

        if (borderWidth > 0f) {
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x + borderWidth * 0.5f, y + borderWidth * 0.5f, width - borderWidth, height - borderWidth, cornerRadius);
            nvgStrokeWidth(vg, borderWidth);
            nvgStrokeColor(vg, NanoUtility.color1(drawBorderColor));
            nvgStroke(vg);
        }

        if (!checked)
            return;

        float markWidth = width * checkmarkScale;
        float markHeight = height * checkmarkScale;
        float markX = x + (width - markWidth) * 0.5f;
        float markY = y + (height - markHeight) * 0.5f;

        float x1 = markX;
        float y1 = markY + markHeight * 0.45f;
        float x2 = markX + markWidth * 0.38f;
        float y2 = markY + markHeight;
        float x3 = markX + markWidth;
        float y3 = markY;

        nvgBeginPath(vg);
        nvgMoveTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        nvgLineTo(vg, x3, y3);
        nvgStrokeWidth(vg, checkmarkThickness);
        nvgStrokeColor(vg, NanoUtility.color1(checkmarkColor));
        nvgLineCap(vg, NVG_ROUND);
        nvgLineJoin(vg, NVG_ROUND);
        nvgStroke(vg);
    }
}