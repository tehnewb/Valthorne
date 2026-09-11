package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.ui.NanoUtility;
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
     * Theme corner radius in UI units, defaulting to six.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.panel.cornerRadius", Float.class, 6f);
    /**
     * Theme border stroke width in UI units, defaulting to one.
     */
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.panel.borderWidth", Float.class, 1f);

    private Color backgroundColor = new Color(0xFF242424); // Borrowed normal background fill color used when that state wins precedence.
    private Color hoverBackgroundColor = new Color(0xFF242424); // Borrowed hovered background fill color used when that state wins precedence.
    private Color focusedBackgroundColor = new Color(0xFF242424); // Borrowed focused background fill color used when that state wins precedence.
    private Color pressedBackgroundColor = new Color(0xFF242424); // Borrowed pressed background fill color used when that state wins precedence.
    private Color disabledBackgroundColor = new Color(0xFF242424); // Borrowed disabled background fill color used when that state wins precedence.

    private Color borderColor = new Color(0xFF242424); // Borrowed normal border stroke color used when that state wins precedence.
    private Color hoverBorderColor = new Color(0xFF242424); // Borrowed hovered border stroke color used when that state wins precedence.
    private Color focusedBorderColor = new Color(0xFF242424); // Borrowed focused border stroke color used when that state wins precedence.
    private Color pressedBorderColor = new Color(0xFF242424); // Borrowed pressed border stroke color used when that state wins precedence.
    private Color disabledBorderColor = new Color(0xFF242424); // Borrowed disabled border stroke color used when that state wins precedence.

    private float cornerRadius = 6f; // Rounded background radius in UI units.
    private float borderWidth = 1f; // Inset stroke width in UI units; zero disables the border.

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
     * painting to the root's shared context afterward. The caller supplies a valid
     * NanoVG frame and visibility handling through normal root dispatch.
     *
     * @param vg borrowed active NanoVG context
     */
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
