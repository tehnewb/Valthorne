package valthorne.ui.nodes.nano;

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
import valthorne.ui.behavior.ActivationBehavior;

/**
 * Focusable NanoVG checkbox using the shared activation policy for Enter, Space,
 * and accepted left-button releases. Checked state lives in the inherited node
 * bit; checked and toggle additionally invoke a synchronous action on changes.
 * A local action takes precedence over the theme action. Focused paint colors
 * win over hovered colors; no separate disabled color palette is applied here.
 *
 * <p>Colors are borrowed references and resolved styles can replace paint values
 * during layout. Render through the root-managed NanoVG frame. This leaf neither
 * owns native resources nor traverses child nodes.</p>
 *
 * @author Albert Beaupre
 */
public class NanoCheckbox extends UINode implements NanoNode {

    /**
     * Theme normal background color.
     */
    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.checkbox.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    /**
     * Theme hovered background color, below focused-state precedence.
     */
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.checkbox.hoverBackgroundColor", Color.class, new Color(0xFF323232));
    /**
     * Theme focused background color, preferred over hover.
     */
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.checkbox.focusedBackgroundColor", Color.class, new Color(0xFF3A3A3A));
    /**
     * Theme normal border-stroke color.
     */
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.checkbox.borderColor", Color.class, new Color(0xFF555555));
    /**
     * Theme hovered border color, below focused-state precedence.
     */
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.checkbox.hoverBorderColor", Color.class, new Color(0xFF777777));
    /**
     * Theme focused border color, preferred over hover.
     */
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.checkbox.focusedBorderColor", Color.class, new Color(0xFF7AA2FF));
    /**
     * Theme checked-mark stroke color.
     */
    public static final StyleKey<Color> CHECKMARK_COLOR_KEY = StyleKey.of("nano.checkbox.checkmarkColor", Color.class, new Color(0xFFFFFFFF));
    /**
     * Theme rounded-box corner radius in UI units.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.checkbox.cornerRadius", Float.class, 6f);
    /**
     * Theme border stroke width in UI units; zero omits the border.
     */
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.checkbox.borderWidth", Float.class, 1f);
    /**
     * Theme checkmark fraction of box width and height, defaulting to one half.
     */
    public static final StyleKey<Float> CHECKMARK_SCALE_KEY = StyleKey.of("nano.checkbox.checkmarkScale", Float.class, 0.5f);
    /**
     * Theme checkmark stroke thickness in UI units.
     */
    public static final StyleKey<Float> CHECKMARK_THICKNESS_KEY = StyleKey.of("nano.checkbox.checkmarkThickness", Float.class, 2.5f);
    /**
     * Theme change action used only when the local callback is null.
     */
    public static final StyleKey<NodeAction<NanoCheckbox>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<NanoCheckbox>>) (Class<?>) NodeAction.class);

    private NodeAction<NanoCheckbox> action; // Optional local callback overriding the resolved theme action.

    private Color backgroundColor = new Color(0xFF2A2A2A); // Borrowed normal background color.
    private Color hoverBackgroundColor = new Color(0xFF323232); // Borrowed hovered background color.
    private Color focusedBackgroundColor = new Color(0xFF3A3A3A); // Borrowed focused background color.
    private Color borderColor = new Color(0xFF555555); // Borrowed normal border color.
    private Color hoverBorderColor = new Color(0xFF777777); // Borrowed hovered border color.
    private Color focusedBorderColor = new Color(0xFF7AA2FF); // Borrowed focused border color.
    private Color checkmarkColor = new Color(0xFFFFFFFF); // Borrowed checkmark color.

    private float cornerRadius = 6f; // Corner radius in UI units.
    private float borderWidth = 1f; // Border stroke width in UI units.
    private float checkmarkScale = 0.5f; // Checkmark width/height fraction in fractions of node dimensions.
    private float checkmarkThickness = 2.5f; // Checkmark stroke thickness in UI units.

    /**
     * Creates an unchecked node with clickable and focusable capabilities enabled.
     * Paint settings begin with local defaults and may be replaced by layout styles.
     */
    public NanoCheckbox() {
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
    }

    /**
     * Reads only the explicitly assigned local callback, without resolving a theme.
     *
     * @return local action, or null when theme lookup is used
     */
    public NodeAction<NanoCheckbox> getAction() {
        return action;
    }

    /**
     * Replaces the local change callback without invoking it. Null restores fallback
     * to the resolved theme action; it does not necessarily disable all callbacks.
     *
     * @param action local synchronous callback, or null for theme fallback
     * @return this checkbox
     */
    public NanoCheckbox action(NodeAction<NanoCheckbox> action) {
        this.action = action;
        return this;
    }

    /**
     * Reads the inherited checked-state bit used to decide whether to draw the mark.
     *
     * @return current checked state
     */
    public boolean isChecked() {
        return getBit(CHECKED_BIT);
    }

    /**
     * Allocates no resources; the root owns the NanoVG context used for painting.
     */
    @Override
    public void onCreate() {

    }

    /**
     * Performs no disposal because this widget owns no native rendering resources.
     */
    @Override
    public void onDestroy() {

    }

    /**
     * Performs no timed work or child traversal. Checked state changes through
     * explicit calls or routed activation events.
     *
     * @param delta elapsed update seconds, unused
     */
    @Override
    public void update(float delta) {

    }

    /**
     * Enters root-managed mixed-renderer dispatch for the NanoVG callback.
     *
     * @param batch active texture batch used by the UI traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }

    /**
     * Applies a changed checked value, then invokes the local action or theme fallback.
     * Repeating the existing value neither mutates state nor fires an action. This
     * programmatic operation does not check enabled state; callback failure leaves
     * the new checked bit in place.
     *
     * @param checked desired state
     * @return this checkbox
     */
    public NanoCheckbox checked(boolean checked) {
        if (isChecked() == checked)
            return this;

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

    /**
     * Inverts checked state through checked, including synchronous action resolution.
     * This programmatic operation is available even while the node is disabled.
     *
     * @return this checkbox
     */
    public NanoCheckbox toggle() {
        return checked(!isChecked());
    }

    /**
     * Retains a non-null color for the normal background without copying it.
     * Null keeps the previous value, and resolved styles may later replace it.
     *
     * @param color paint color reference, or null
     * @return this checkbox
     */
    public NanoCheckbox backgroundColor(Color color) {
        if (color != null)
            this.backgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the hovered background without copying it.
     * Null keeps the previous value, and resolved styles may later replace it.
     *
     * @param color paint color reference, or null
     * @return this checkbox
     */
    public NanoCheckbox hoverBackgroundColor(Color color) {
        if (color != null)
            this.hoverBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the focused background without copying it.
     * Null keeps the previous value, and resolved styles may later replace it.
     *
     * @param color paint color reference, or null
     * @return this checkbox
     */
    public NanoCheckbox focusedBackgroundColor(Color color) {
        if (color != null)
            this.focusedBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the normal border without copying it.
     * Null keeps the previous value, and resolved styles may later replace it.
     *
     * @param color paint color reference, or null
     * @return this checkbox
     */
    public NanoCheckbox borderColor(Color color) {
        if (color != null)
            this.borderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the hovered border without copying it.
     * Null keeps the previous value, and resolved styles may later replace it.
     *
     * @param color paint color reference, or null
     * @return this checkbox
     */
    public NanoCheckbox hoverBorderColor(Color color) {
        if (color != null)
            this.hoverBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the focused border without copying it.
     * Null keeps the previous value, and resolved styles may later replace it.
     *
     * @param color paint color reference, or null
     * @return this checkbox
     */
    public NanoCheckbox focusedBorderColor(Color color) {
        if (color != null)
            this.focusedBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the checkmark without copying it.
     * Null keeps the previous value, and resolved styles may later replace it.
     *
     * @param color paint color reference, or null
     * @return this checkbox
     */
    public NanoCheckbox checkmarkColor(Color color) {
        if (color != null)
            this.checkmarkColor = color;
        return this;
    }

    /**
     * Sets the corner radius, clamping negatives to zero.
     * There is no upper clamp; resolved layout styles can replace this value.
     *
     * @param cornerRadius finite requested value in UI units
     * @return this checkbox
     */
    public NanoCheckbox cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    /**
     * Sets the border stroke width, clamping negatives to zero.
     * There is no upper clamp; resolved layout styles can replace this value.
     *
     * @param borderWidth finite requested value in UI units
     * @return this checkbox
     */
    public NanoCheckbox borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    /**
     * Sets the checkmark width/height fraction, clamping negatives to zero.
     * There is no upper clamp; resolved layout styles can replace this value.
     *
     * @param checkmarkScale finite requested value in fractions of node dimensions
     * @return this checkbox
     */
    public NanoCheckbox checkmarkScale(float checkmarkScale) {
        this.checkmarkScale = Math.max(0f, checkmarkScale);
        return this;
    }

    /**
     * Sets the checkmark stroke thickness, clamping negatives to zero.
     * There is no upper clamp; resolved layout styles can replace this value.
     *
     * @param checkmarkThickness finite requested value in UI units
     * @return this checkbox
     */
    public NanoCheckbox checkmarkThickness(float checkmarkThickness) {
        this.checkmarkThickness = Math.max(0f, checkmarkThickness);
        return this;
    }

    /**
     * Uses shared activation to consume Enter or Space on an enabled receiver and
     * toggle it. Focus routing and key-repeat policy belong to the root; this helper
     * does not independently suppress repeats or check focus.
     *
     * @param event routed key press
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        ActivationBehavior.key(this, event, this::toggle);
    }

    /**
     * Toggles when the inherited activation-release policy accepts a left release
     * on this enabled, attached, hit-tested node. The event is consumed before the
     * action; this policy does not itself require a matching earlier press.
     *
     * @param event routed pointer release
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        ActivationBehavior.release(this, event, this::toggle);
    }

    /**
     * Retains non-null resolved colors and nonnegative paint dimensions before
     * base layout. The theme action is resolved on change instead of copied here.
     * Theme colors are borrowed and can replace explicit setter values.
     */
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

    /**
     * Paints the rounded box and optional border, then draws the scaled checkmark
     * only when checked. Focus colors take precedence over hover colors. Invisible
     * nodes and a zero handle are skipped; the root owns frame and clipping state.
     *
     * @param vg borrowed active NanoVG context, or zero to skip
     */
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

        if (!isChecked())
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
