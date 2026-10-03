package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.ui.NanoUtility;
import valthorne.ui.UIGradient;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * NanoVG container painting a rounded background and optional inset border before
 * mixed-renderer child traversal. Draw colors use disabled, pressed, focused,
 * then hovered priority. Colors are retained by reference and may be replaced
 * by resolved style values during layout; direct setters do not lock out themes.
 * Use through a root-managed NanoVG frame and leave context ownership to the root.
 *
 * @author Albert Beaupre
 */
public class NanoPanel extends NanoContainer {

    /*
     * Optional gradient for the panel surface. A resolved theme may supply it.
     */
    public static final StyleKey<UIGradient> BACKGROUND_GRADIENT_KEY = StyleKey.of("nano.panel.backgroundGradient", UIGradient.class, null);
    /*
     * Optional gradient for the panel outline. A resolved theme may supply it.
     */
    public static final StyleKey<UIGradient> BORDER_GRADIENT_KEY = StyleKey.of("nano.panel.borderGradient", UIGradient.class, null);

    /**
     * Theme override for the normal background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.backgroundColor", Color.class, null);
    /**
     * Theme override for the hovered background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.hoverBackgroundColor", Color.class, null);
    /**
     * Theme override for the focused background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.focusedBackgroundColor", Color.class, null);
    /**
     * Theme override for the pressed background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> PRESSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.pressedBackgroundColor", Color.class, null);
    /**
     * Theme override for the disabled background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> DISABLED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.panel.disabledBackgroundColor", Color.class, null);

    /**
     * Theme override for the normal border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.panel.borderColor", Color.class, null);
    /**
     * Theme override for the hovered border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.panel.hoverBorderColor", Color.class, null);
    /**
     * Theme override for the focused border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.panel.focusedBorderColor", Color.class, null);
    /**
     * Theme override for the pressed border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> PRESSED_BORDER_COLOR_KEY = StyleKey.of("nano.panel.pressedBorderColor", Color.class, null);
    /**
     * Theme override for the disabled border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> DISABLED_BORDER_COLOR_KEY = StyleKey.of("nano.panel.disabledBorderColor", Color.class, null);

    /**
     * Theme corner radius in UI units, defaulting to zero.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.panel.cornerRadius", Float.class, 0f);
    /**
     * Theme border stroke width in UI units, defaulting to one.
     */
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.panel.borderWidth", Float.class, 1f);

    private Color backgroundColor = new Color(0xFF242424); // Borrowed normal background fill color used when that state wins precedence.
    private Color hoverBackgroundColor; // Optional explicit state color; null retains the normal panel color.
    private Color focusedBackgroundColor; // Optional explicit state color; null retains the normal panel color.
    private Color pressedBackgroundColor; // Optional explicit state color; null retains the normal panel color.
    private Color disabledBackgroundColor; // Optional explicit state color; null retains the normal panel color.

    private Color borderColor = new Color(0xFF242424); // Borrowed normal border stroke color used when that state wins precedence.
    private Color hoverBorderColor; // Optional explicit state color; null retains the normal panel color.
    private Color focusedBorderColor; // Optional explicit state color; null retains the normal panel color.
    private Color pressedBorderColor; // Optional explicit state color; null retains the normal panel color.
    private Color disabledBorderColor; // Optional explicit state color; null retains the normal panel color.

    private float cornerRadius = 0f; // Rounded background radius in UI units.
    private float borderWidth = 1f; // Inset stroke width in UI units; zero disables the border.
    private UIGradient backgroundGradient; // Optional gradient replacing the selected background color.
    private UIGradient borderGradient; // Optional gradient replacing the selected border color.

    /**
     * Sets the background gradient, or clears it with null.
     *
     * @param gradient gradient borrowed by this panel
     * @return this panel
     */
    public NanoPanel backgroundGradient(UIGradient gradient) {
        backgroundGradient = gradient;
        return this;
    }

    /**
     * Sets the border gradient, or clears it with null.
     *
     * @param gradient gradient borrowed by this panel
     * @return this panel
     */
    public NanoPanel borderGradient(UIGradient gradient) {
        borderGradient = gradient;
        return this;
    }

    /**
     * Retains a non-null color for the normal background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel backgroundColor(Color color) {
        if (color != null)
            this.backgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the hovered background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel hoverBackgroundColor(Color color) {
        if (color != null)
            this.hoverBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the focused background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel focusedBackgroundColor(Color color) {
        if (color != null)
            this.focusedBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the pressed background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel pressedBackgroundColor(Color color) {
        if (color != null)
            this.pressedBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the disabled background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel disabledBackgroundColor(Color color) {
        if (color != null)
            this.disabledBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the normal border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel borderColor(Color color) {
        if (color != null)
            this.borderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the hovered border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel hoverBorderColor(Color color) {
        if (color != null)
            this.hoverBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the focused border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel focusedBorderColor(Color color) {
        if (color != null)
            this.focusedBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the pressed border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel pressedBorderColor(Color color) {
        if (color != null)
            this.pressedBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the disabled border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this panel
     */
    public NanoPanel disabledBorderColor(Color color) {
        if (color != null)
            this.disabledBorderColor = color;
        return this;
    }

    /**
     * Sets the background corner radius, clamping negative values to zero.
     * Does not mark layout dirty; a later resolved style can overwrite this value.
     *
     * @param cornerRadius requested radius in UI units; supply a finite value
     * @return this panel
     */
    public NanoPanel cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    /**
     * Sets the inset border stroke width, clamping negative values to zero.
     * Zero omits the border. A later resolved style can overwrite this value.
     *
     * @param borderWidth requested stroke width in UI units; supply a finite value
     * @return this panel
     */
    public NanoPanel borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    /**
     * Copies non-null resolved state colors and dimensions into local paint settings,
     * clamping radii and widths to nonnegative values, then runs container layout.
     * Theme colors are borrowed references; missing color values retain current ones.
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            UIGradient resolvedBackgroundGradient = style.get(BACKGROUND_GRADIENT_KEY);
            UIGradient resolvedBorderGradient = style.get(BORDER_GRADIENT_KEY);
            if (resolvedBackgroundGradient != null) backgroundGradient = resolvedBackgroundGradient;
            if (resolvedBorderGradient != null) borderGradient = resolvedBorderGradient;
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

    /**
     * Paints the background, then an inset border when width is positive, selecting
     * state colors with disabled/pressed/focused/hovered precedence. Delegates child
     * painting to the root's shared context afterward. A dragged panel paints only
     * its children. The caller supplies a valid NanoVG frame and visibility handling
     * through normal root dispatch.
     *
     * @param vg borrowed active NanoVG context
     */
    @Override
    public void draw(long vg) {
        drawAt(vg, getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight(), 1f);
        super.draw(vg);
    }

    /**
     * Paints only this panel's live surface at externally projected bounds.
     * Child traversal remains the caller's responsibility, allowing scene
     * editors to draw a panel without reentering its runtime UI root.
     *
     * @param vg active NanoVG frame
     * @param x projected left edge
     * @param y projected top edge
     * @param width projected width
     * @param height projected height
     * @param scale projection scale applied to corners and borders
     */
    public void drawAt(long vg, float x, float y, float width, float height, float scale) {
        if (isDragging()) {
            return;
        }

        Color drawBackground = backgroundColor;
        Color drawBorder = borderColor;

        if (!isEnabled()) {
            drawBackground = disabledBackgroundColor == null ? backgroundColor : disabledBackgroundColor;
            drawBorder = disabledBorderColor == null ? borderColor : disabledBorderColor;
        } else if (isPressed()) {
            drawBackground = pressedBackgroundColor == null ? backgroundColor : pressedBackgroundColor;
            drawBorder = pressedBorderColor == null ? borderColor : pressedBorderColor;
        } else if (isFocused()) {
            drawBackground = focusedBackgroundColor == null ? backgroundColor : focusedBackgroundColor;
            drawBorder = focusedBorderColor == null ? borderColor : focusedBorderColor;
        } else if (isHovered()) {
            drawBackground = hoverBackgroundColor == null ? backgroundColor : hoverBackgroundColor;
            drawBorder = hoverBorderColor == null ? borderColor : hoverBorderColor;
        }

        nvgBeginPath(vg);
        if (backgroundGradient == null) nvgFillColor(vg, NanoUtility.color1(drawBackground));
        nvgRoundedRect(vg, x, y, width, height, cornerRadius * scale);
        if (backgroundGradient == null) nvgFill(vg);
        else backgroundGradient.fill(vg, x, y, width, height);

        if (borderWidth > 0f) {
            float stroke = borderWidth * scale;
            float inset = stroke * 0.5f;
            nvgBeginPath(vg);
            nvgStrokeWidth(vg, stroke);
            if (borderGradient == null) nvgStrokeColor(vg, NanoUtility.color2(drawBorder));
            nvgRoundedRect(vg, x + inset, y + inset, Math.max(0f, width - stroke), Math.max(0f, height - stroke), Math.max(0f, cornerRadius * scale - inset));
            if (borderGradient == null) nvgStroke(vg);
            else borderGradient.stroke(vg, x, y, width, height);
        }
    }
}
