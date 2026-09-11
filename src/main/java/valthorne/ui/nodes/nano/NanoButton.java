package valthorne.ui.nodes.nano;

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

/**
 * Focusable NanoVG button with centered text and state-specific background,
 * border, and text colors. Enter, Space, and accepted left-button releases use
 * the shared activation policy; a local callback overrides the resolved theme
 * action. Disabled, pressed, focused, and hovered paint states have that priority.
 *
 * <p>Automatic dimensions are replaced with measured text plus padding during
 * layout. Later text/font changes dirty layout but do not restore those dimensions
 * to auto. Colors and registered fonts are borrowed; styles may overwrite local
 * paint settings. Render through the root-managed NanoVG lifecycle.</p>
 *
 * @author Albert Beaupre
 */
public class NanoButton extends UINode implements NanoNode {

    /**
     * Theme background color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    /**
     * Theme hover background color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.hoverBackgroundColor", Color.class, new Color(0xFF323232));
    /**
     * Theme focused background color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.focusedBackgroundColor", Color.class, new Color(0xFF3A3A3A));
    /**
     * Theme pressed background color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> PRESSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.pressedBackgroundColor", Color.class, new Color(0xFF202020));
    /**
     * Theme disabled background color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> DISABLED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.button.disabledBackgroundColor", Color.class, new Color(0xFF242424));

    /**
     * Theme border color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.button.borderColor", Color.class, new Color(0xFF555555));
    /**
     * Theme hover border color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.button.hoverBorderColor", Color.class, new Color(0xFF777777));
    /**
     * Theme focused border color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.button.focusedBorderColor", Color.class, new Color(0xFF7AA2FF));
    /**
     * Theme pressed border color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> PRESSED_BORDER_COLOR_KEY = StyleKey.of("nano.button.pressedBorderColor", Color.class, new Color(0xFF4A4A4A));
    /**
     * Theme disabled border color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> DISABLED_BORDER_COLOR_KEY = StyleKey.of("nano.button.disabledBorderColor", Color.class, new Color(0xFF3A3A3A));

    /**
     * Theme text color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> TEXT_COLOR_KEY = StyleKey.of("nano.button.textColor", Color.class, Color.WHITE);
    /**
     * Theme hover text color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> HOVER_TEXT_COLOR_KEY = StyleKey.of("nano.button.hoverTextColor", Color.class, Color.WHITE);
    /**
     * Theme focused text color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> FOCUSED_TEXT_COLOR_KEY = StyleKey.of("nano.button.focusedTextColor", Color.class, Color.WHITE);
    /**
     * Theme pressed text color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> PRESSED_TEXT_COLOR_KEY = StyleKey.of("nano.button.pressedTextColor", Color.class, Color.WHITE);
    /**
     * Theme disabled text color used during layout; colors are retained by reference.
     */
    public static final StyleKey<Color> DISABLED_TEXT_COLOR_KEY = StyleKey.of("nano.button.disabledTextColor", Color.class, new Color(0xFF9A9A9A));

    /**
     * Theme font name used during layout; colors are retained by reference.
     */
    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.button.fontName", String.class, "default");
    /**
     * Theme font size used during layout; colors are retained by reference.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.button.fontSize", Float.class, 18f);
    /**
     * Theme padding x used during layout; colors are retained by reference.
     */
    public static final StyleKey<Float> PADDING_X_KEY = StyleKey.of("nano.button.paddingX", Float.class, 14f);
    /**
     * Theme padding y used during layout; colors are retained by reference.
     */
    public static final StyleKey<Float> PADDING_Y_KEY = StyleKey.of("nano.button.paddingY", Float.class, 8f);
    /**
     * Theme corner radius used during layout; colors are retained by reference.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.button.cornerRadius", Float.class, 6f);
    /**
     * Theme border width used during layout; colors are retained by reference.
     */
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.button.borderWidth", Float.class, 1f);
    /**
     * Theme activation callback used when no local action is assigned.
     */
    public static final StyleKey<NodeAction<NanoButton>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<NanoButton>>) (Class<?>) NodeAction.class);

    private String text = ""; // Raw non-null text used for measurement and painting.

    private Color backgroundColor = new Color(0xFF2A2A2A); // Borrowed background color used by state-aware painting.
    private Color hoverBackgroundColor = new Color(0xFF323232); // Borrowed hover background color used by state-aware painting.
    private Color focusedBackgroundColor = new Color(0xFF3A3A3A); // Borrowed focused background color used by state-aware painting.
    private Color pressedBackgroundColor = new Color(0xFF202020); // Borrowed pressed background color used by state-aware painting.
    private Color disabledBackgroundColor = new Color(0xFF242424); // Borrowed disabled background color used by state-aware painting.

    private Color borderColor = new Color(0xFF555555); // Borrowed border color used by state-aware painting.
    private Color hoverBorderColor = new Color(0xFF777777); // Borrowed hover border color used by state-aware painting.
    private Color focusedBorderColor = new Color(0xFF7AA2FF); // Borrowed focused border color used by state-aware painting.
    private Color pressedBorderColor = new Color(0xFF4A4A4A); // Borrowed pressed border color used by state-aware painting.
    private Color disabledBorderColor = new Color(0xFF3A3A3A); // Borrowed disabled border color used by state-aware painting.

    private Color textColor = Color.WHITE; // Borrowed text color used by state-aware painting.
    private Color hoverTextColor = Color.WHITE; // Borrowed hover text color used by state-aware painting.
    private Color focusedTextColor = Color.WHITE; // Borrowed focused text color used by state-aware painting.
    private Color pressedTextColor = Color.WHITE; // Borrowed pressed text color used by state-aware painting.
    private Color disabledTextColor = new Color(0xFF9A9A9A); // Borrowed disabled text color used by state-aware painting.

    private String fontName = "default"; // Borrowed NanoVG font registration name.
    private float fontSize = 18f; // Text size in UI units.
    private float paddingX = 14f; // Padding on each horizontal side in UI units.
    private float paddingY = 8f; // Padding on each vertical side in UI units.
    private float cornerRadius = 6f; // Rounded-corner radius in UI units.
    private float borderWidth = 1f; // Outer border stroke width in UI units.

    private boolean fontLoaded; // Reserved font state; not used by the current implementation.
    private NodeAction<NanoButton> action; // Optional local callback taking precedence over the theme action.

    /**
     * Creates an empty clickable, focusable button with default paint settings and
     * marks layout dirty for initial content measurement.
     */
    public NanoButton() {
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        markLayoutDirty();
    }

    /**
     * Creates a clickable, focusable button and stores text through the normal setter.
     * Font resources are expected to have been registered by the root/application.
     *
     * @param text initial text; null becomes empty
     */
    public NanoButton(String text) {
        this();
        text(text);
    }

    /**
     * Returns stored text unchanged. The current setter does not invoke the private
     * sanitizer, so control characters are not removed by this accessor or setter.
     *
     * @return non-null button text
     */
    public String getText() {
        return text;
    }

    /**
     * Stores text, converting null to empty, and marks layout dirty. Does not sanitize
     * control characters or restore previously measured numeric dimensions to auto.
     *
     * @param text replacement contents, possibly null
     * @return this button
     */
    public NanoButton text(String text) {
        this.text = text == null ? "" : text;
        markLayoutDirty();
        return this;
    }

    /**
     * Reads the explicitly assigned callback without consulting resolved styles.
     *
     * @return local callback, or null when theme fallback may apply
     */
    public NodeAction<NanoButton> getAction() {
        return action;
    }

    /**
     * Replaces the local activation callback without invoking it. Null enables theme
     * fallback rather than necessarily disabling activation behavior.
     *
     * @param action local synchronous callback, or null
     * @return this button
     */
    public NanoButton action(NodeAction<NanoButton> action) {
        this.action = action;
        return this;
    }

    /**
     * Retains a nonblank NanoVG registration name and dirties layout even if input
     * is null or blank. Does not load a font or verify that the name is registered.
     *
     * @param fontName desired registered font name
     * @return this button
     */
    public NanoButton fontName(String fontName) {
        if (fontName != null && !fontName.isBlank())
            this.fontName = fontName;
        markLayoutDirty();
        return this;
    }

    /**
     * Sets text size, clamping values below 1 to 1.
     * Marks layout dirty for measurement, without restoring fixed dimensions to auto.
     *
     * @param fontSize finite requested value in UI units
     * @return this button
     */
    public NanoButton fontSize(float fontSize) {
        this.fontSize = Math.max(1f, fontSize);
        markLayoutDirty();
        return this;
    }

    /**
     * Sets padding on each horizontal side, clamping values below 0 to 0.
     * Marks layout dirty for measurement, without restoring fixed dimensions to auto.
     *
     * @param paddingX finite requested value in UI units
     * @return this button
     */
    public NanoButton paddingX(float paddingX) {
        this.paddingX = Math.max(0f, paddingX);
        markLayoutDirty();
        return this;
    }

    /**
     * Sets padding on each vertical side, clamping values below 0 to 0.
     * Marks layout dirty for measurement, without restoring fixed dimensions to auto.
     *
     * @param paddingY finite requested value in UI units
     * @return this button
     */
    public NanoButton paddingY(float paddingY) {
        this.paddingY = Math.max(0f, paddingY);
        markLayoutDirty();
        return this;
    }

    /**
     * Sets rounded-corner radius, clamping values below 0 to 0.
     * Changes paint only; does not mark layout dirty.
     *
     * @param cornerRadius finite requested value in UI units
     * @return this button
     */
    public NanoButton cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    /**
     * Sets outer border stroke width, clamping values below 0 to 0.
     * Changes paint only; does not mark layout dirty.
     *
     * @param borderWidth finite requested value in UI units
     * @return this button
     */
    public NanoButton borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    /**
     * Retains a non-null background color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton backgroundColor(Color color) {
        if (color != null)
            this.backgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null hover background color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton hoverBackgroundColor(Color color) {
        if (color != null)
            this.hoverBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null focused background color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton focusedBackgroundColor(Color color) {
        if (color != null)
            this.focusedBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null pressed background color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton pressedBackgroundColor(Color color) {
        if (color != null)
            this.pressedBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null disabled background color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton disabledBackgroundColor(Color color) {
        if (color != null)
            this.disabledBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null border color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton borderColor(Color color) {
        if (color != null)
            this.borderColor = color;
        return this;
    }

    /**
     * Retains a non-null hover border color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton hoverBorderColor(Color color) {
        if (color != null)
            this.hoverBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null focused border color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton focusedBorderColor(Color color) {
        if (color != null)
            this.focusedBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null pressed border color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton pressedBorderColor(Color color) {
        if (color != null)
            this.pressedBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null disabled border color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton disabledBorderColor(Color color) {
        if (color != null)
            this.disabledBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null text color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton textColor(Color color) {
        if (color != null)
            this.textColor = color;
        return this;
    }

    /**
     * Retains a non-null hover text color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton hoverTextColor(Color color) {
        if (color != null)
            this.hoverTextColor = color;
        return this;
    }

    /**
     * Retains a non-null focused text color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton focusedTextColor(Color color) {
        if (color != null)
            this.focusedTextColor = color;
        return this;
    }

    /**
     * Retains a non-null pressed text color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton pressedTextColor(Color color) {
        if (color != null)
            this.pressedTextColor = color;
        return this;
    }

    /**
     * Retains a non-null disabled text color without copying it. Null leaves the current
     * reference unchanged; theme application may later replace it.
     *
     * @param color mutable paint color, or null
     * @return this button
     */
    public NanoButton disabledTextColor(Color color) {
        if (color != null)
            this.disabledTextColor = color;
        return this;
    }

    /**
     * Allocates no resources. Fonts and the NanoVG context belong to the root/application.
     */
    @Override
    public void onCreate() {

    }

    /**
     * Performs no disposal because native rendering resources are borrowed.
     */
    @Override
    public void onDestroy() {
    }

    /**
     * Performs no timed work or child traversal. Input callbacks drive activation.
     *
     * @param delta elapsed update seconds, unused
     */
    @Override
    public void update(float delta) {
    }

    /**
     * Enters the root's shared rendering path for NanoVG backend selection and clipping.
     *
     * @param batch active texture batch used by mixed UI traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }

    /**
     * Consumes Enter or Space on an enabled routed receiver, then activates it.
     * The shared policy does not independently check focus or suppress repeats.
     * Accepted events are consumed even when no action is configured.
     *
     * @param event routed key press
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        valthorne.ui.behavior.ActivationBehavior.key(this, event, this::activate);
    }

    /**
     * Activates for a left release accepted by this enabled, attached node's hit-test
     * policy. Consumption occurs first; the policy does not itself establish a
     * matching earlier press. Other releases remain untouched.
     *
     * @param event routed pointer release
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        valthorne.ui.behavior.ActivationBehavior.release(this, event, this::activate);
    }

    /**
     * Invokes the local callback when present, otherwise looks up and invokes the
     * resolved theme action. Missing actions are a no-op. Callers apply enabled and
     * event-consumption policy; callback failures propagate synchronously.
     */
    private void activate() {
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

    /**
     * Applies non-null theme paint/font/padding values, measures text plus padding,
     * and replaces dimensions still marked auto. Then delegates base layout.
     * Resolved styles may overwrite local setters; missing native context uses
     * approximate text metrics through the measurement helpers.
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

    /**
     * Paints state-selected background and border, followed by centered text when
     * nonempty. The border is centered on the outer path, not inset like NanoPanel.
     * Skips invisible nodes and a zero handle. Requires root-prepared frame state.
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

    /**
     * Measures text through the root context when available, otherwise estimates
     * half font size per UTF-16 unit. Null and empty input have zero width.
     *
     * @param value text to measure
     * @return content width before horizontal padding
     */
    private float measureTextWidth(String value) {
        UIRoot root = getRoot();
        if (root == null || value == null || value.isEmpty())
            return value == null ? 0f : value.length() * fontSize * 0.5f;

        return NanoUtility.measureTextWidth(root.getNanoVGHandle(), fontName, fontSize, value);
    }

    /**
     * Reads font line height through the root context or uses fontSize when detached
     * or when the utility has no native handle. Does not include vertical padding.
     *
     * @return content line height in UI units
     */
    private float measureTextHeight() {
        UIRoot root = getRoot();
        if (root == null)
            return fontSize;

        return NanoUtility.measureTextHeight(root.getNanoVGHandle(), fontName, fontSize);
    }

    /**
     * Builds a version with control characters replaced by spaces, avoiding an
     * extra replacement space when output already ends with one. Ordinary spaces
     * are preserved. This helper is currently not called by text assignment or drawing.
     *
     * @param value raw text, possibly null
     * @return sanitized string, or empty for null/empty input
     */
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
