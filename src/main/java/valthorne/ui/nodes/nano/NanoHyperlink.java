package valthorne.ui.nodes.nano;

import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Focusable NanoVG text link that requests URI opening through the desktop or
 * platform launcher. Null text becomes empty text; null or blank destinations
 * do not launch anything. Visited records a successful launch request, not
 * confirmation that a destination loaded. Changing URL does not reset visited.
 *
 * <p>Routed Enter/Space presses and accepted left-button releases use the shared
 * ActivationBehavior policy. The armed flag is bookkeeping and does not gate
 * release activation. Rendering prioritizes disabled, pressed, focused, hovered,
 * visited and normal colors in that order.</p>
 *
 * <p>Text is left-aligned and vertically centered. Automatic layout dimensions
 * become measured point dimensions during layout; reset them to auto when later
 * text changes should resize the node. Style colors and fonts are borrowed.
 * Use the attached root's rendering and input lifecycle on its owning thread.</p>
 *
 * @author Albert Beaupre
 */
public class NanoHyperlink extends UINode implements NanoNode {

    /**
     * Normal link text color; default blue, used when no higher-priority state applies.
     */
    public static final StyleKey<Color> COLOR_KEY = StyleKey.of("nano.hyperlink.color", Color.class, new Color(0xFF4DA3FF));
    /**
     * Hovered text color, below pressed and focused state precedence.
     */
    public static final StyleKey<Color> HOVER_COLOR_KEY = StyleKey.of("nano.hyperlink.hoverColor", Color.class, new Color(0xFF7FBEFF));
    /**
     * Focused text color, below pressed state precedence.
     */
    public static final StyleKey<Color> FOCUSED_COLOR_KEY = StyleKey.of("nano.hyperlink.focusedColor", Color.class, new Color(0xFFA9D1FF));
    /**
     * Pressed text color, overridden only by disabled state.
     */
    public static final StyleKey<Color> PRESSED_COLOR_KEY = StyleKey.of("nano.hyperlink.pressedColor", Color.class, new Color(0xFF2E7FD1));
    /**
     * Disabled text color, taking precedence over all other appearance states.
     */
    public static final StyleKey<Color> DISABLED_COLOR_KEY = StyleKey.of("nano.hyperlink.disabledColor", Color.class, new Color(0xFF6E6E6E));
    /**
     * Visited text color used when enabled and neither pressed, focused nor hovered.
     */
    public static final StyleKey<Color> VISITED_COLOR_KEY = StyleKey.of("nano.hyperlink.visitedColor", Color.class, new Color(0xFFC08CFF));

    /**
     * Font size in layout units, default 18 and clamped to at least one during style refresh.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.hyperlink.fontSize", Float.class, 18f);
    /**
     * Underline stroke width in layout units, default 1.5 and clamped nonnegative.
     */
    public static final StyleKey<Float> UNDERLINE_THICKNESS_KEY = StyleKey.of("nano.hyperlink.underlineThickness", Float.class, 1.5f);
    /**
     * Horizontal text padding in layout units; default zero, also included twice in automatic width.
     */
    public static final StyleKey<Float> PADDING_X_KEY = StyleKey.of("nano.hyperlink.paddingX", Float.class, 0f);
    /**
     * Vertical padding in layout units; default zero, included twice in automatic height.
     */
    public static final StyleKey<Float> PADDING_Y_KEY = StyleKey.of("nano.hyperlink.paddingY", Float.class, 0f);
    /**
     * Whether to underline without hover, focus or press; default false.
     */
    public static final StyleKey<Boolean> UNDERLINE_ALWAYS_KEY = StyleKey.of("nano.hyperlink.underlineAlways", Boolean.class, false);
    /**
     * NanoVG font registration name, default 'default'; blank resolved names use that fallback.
     */
    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.hyperlink.fontName", String.class, "default");

    private String text; // Normalized label text, never null through public setters.
    private String url; // Unvalidated destination string; null and blank suppress launch attempts.

    private Color color = new Color(0xFF4DA3FF); // Normal text color, replaced by borrowed resolved style values.
    private Color hoverColor = new Color(0xFF7FBEFF); // Hovered text color, possibly borrowed from resolved style.
    private Color focusedColor = new Color(0xFFA9D1FF); // Focused text color, possibly borrowed from resolved style.
    private Color pressedColor = new Color(0xFF2E7FD1); // Pressed text color, possibly borrowed from resolved style.
    private Color disabledColor = new Color(0xFF6E6E6E); // Disabled text color with highest drawing precedence.
    private Color visitedColor = new Color(0xFFC08CFF); // Visited text color used in the resting enabled state.

    private float fontSize = 18f; // Cached text size in layout units.
    private float underlineThickness = 1.5f; // Cached underline stroke width in layout units.
    private float paddingX; // Horizontal inset and automatic-width padding.
    private float paddingY; // Padding used in automatic-height measurement.
    private boolean underlineAlways; // Whether resting text also receives an underline.
    private String fontName = "default"; // NanoVG font registration name used for measuring and painting.

    private boolean visited; // Local visited appearance flag, set after a successful launch request.
    private boolean armed; // Press bookkeeping cleared on release/cancel; not used to gate activation.
    private float measuredTextWidth; // Latest measured or estimated text width.
    private float measuredTextHeight; // Latest measured or estimated text height.

    /**
     * Creates a clickable, focusable link without parsing or opening its destination.
     *
     * @param text the label, with null normalized to empty text
     * @param url  the optional destination string, retained without validation
     */
    public NanoHyperlink(String text, String url) {
        this.text = text == null ? "" : text;
        this.url = url;
        setClickable(true);
        setFocusable(true);
    }

    /**
     * Returns the current normalized label without measuring or changing layout.
     *
     * @return the nonnull displayed text
     */
    public String getText() {
        return text;
    }

    /**
     * Replaces the label and marks layout dirty, even for equal text. Existing explicit dimensions are retained; restore automatic sizing separately if desired.
     *
     * @param text the replacement label, with null normalized to empty text
     * @return this link for chaining
     */
    public NanoHyperlink text(String text) {
        this.text = text == null ? "" : text;
        markLayoutDirty();
        return this;
    }

    /**
     * Reads the destination without URI parsing or launcher interaction.
     *
     * @return the stored destination, possibly null or blank
     */
    public String getUrl() {
        return url;
    }

    /**
     * Stores a destination without validation or resetting visited state. Parsing is deferred until activation.
     *
     * @param url the replacement destination, possibly null or blank
     * @return this link for chaining
     */
    public NanoHyperlink url(String url) {
        this.url = url;
        return this;
    }

    /**
     * Reports local visited appearance state, which does not query browser history or confirm page loading.
     *
     * @return whether this link is marked visited
     */
    public boolean isVisited() {
        return visited;
    }

    /**
     * Sets visited appearance explicitly without opening the destination or changing layout.
     *
     * @param visited the desired local visited flag
     * @return this link for chaining
     */
    public NanoHyperlink visited(boolean visited) {
        this.visited = visited;
        return this;
    }

    /**
     * Requests layout so text can be measured with the attached root's font context. No owned rendering resource is created.
     */
    @Override
    public void onCreate() {
        markLayoutDirty();
    }

    /**
     * Performs no resource cleanup because this node owns no native font or image. Text, URL and visited fields remain unchanged.
     */
    @Override
    public void onDestroy() {
    }

    /**
     * Performs no frame-based work; state changes are driven by input and style resolution.
     *
     * @param delta the frame interval, unused here
     */
    @Override
    public void update(float delta) {
    }

    /**
     * Delegates to shared node rendering so the root dispatches NanoVG painting with the active clipping and backend state.
     *
     * @param batch the active batch for mixed UI traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }

    /**
     * Sets armed when enabled with a nonblank destination, otherwise clears it. The event is not inspected or consumed, and armed does not gate release activation.
     *
     * @param event the routed press, unused by this implementation
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (!isEnabled() || url == null || url.isBlank()) {
            armed = false;
            return;
        }
        armed = true;
    }

    /**
     * Delegates to the shared left-button release policy, which consumes accepted releases before activation. Clears armed after the helper returns; no earlier armed press is required here.
     *
     * @param event the routed release to test
     * @throws NullPointerException if event is null
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        valthorne.ui.behavior.ActivationBehavior.release(this, event, this::activate);
        armed = false;
    }

    /**
     * Attempts to open a nonblank destination and marks visited when launch succeeds. Missing destinations or a false launch result preserve existing visited state.
     */
    private void activate() {
        if (url != null && !url.isBlank() && openInBrowser(url)) visited = true;
    }

    /**
     * Delegates enabled Enter/Space activation to ActivationBehavior, consuming accepted presses before the launch attempt. Focus routing and repeat suppression are not implemented here.
     *
     * @param event the routed keyboard press
     */
    @Override
    public void onKeyPress(valthorne.event.events.KeyPressEvent event) {
        valthorne.ui.behavior.ActivationBehavior.key(this, event, this::activate);
    }

    /**
     * Runs inherited pointer cancellation and clears local armed bookkeeping without changing the destination or visited flag.
     */
    @Override
    public void onPointerCancel() {
        super.onPointerCancel();
        armed = false;
    }

    /**
     * Invalidates inherited resolved style state and immediately reloads this link's cached appearance values.
     */
    @Override
    protected void invalidateStyleTree() {
        super.invalidateStyleTree();
        refreshStyleCache();
    }

    /**
     * Refreshes styling and measures text using the root's NanoVG context when available. Without a context, estimates width from UTF-16 length times font size times 0.56 and uses font size as height. Automatic dimensions become nonnegative measured dimensions plus twice their padding, then inherited layout is applied. Explicit dimensions remain unchanged.
     */
    @Override
    protected void applyLayout() {
        refreshStyleCache();

        UIRoot root = getRoot();
        long vg = root != null ? root.getNanoVGHandle() : 0L;

        if (vg != 0L) {
            nvgFontSize(vg, fontSize);
            nvgFontFace(vg, fontName);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);

            float[] bounds = new float[4];
            nvgTextBounds(vg, 0f, 0f, text == null ? "" : text, bounds);
            measuredTextWidth = Math.max(0f, bounds[2] - bounds[0]);
            measuredTextHeight = Math.max(0f, bounds[3] - bounds[1]);
        } else {
            measuredTextWidth = Math.max(0f, (text == null ? 0 : text.length()) * fontSize * 0.56f);
            measuredTextHeight = fontSize;
        }

        if (getLayout().getWidth().isAuto()) {
            getLayout().width(Math.max(0f, measuredTextWidth + paddingX * 2f));
        }

        if (getLayout().getHeight().isAuto()) {
            getLayout().height(Math.max(0f, measuredTextHeight + paddingY * 2f));
        }

        super.applyLayout();
    }

    /**
     * Paints visible text in the prepared NanoVG context, skipping a zero handle. Refreshes style, applies state-color precedence and measures drawn bounds for underlining. Horizontal padding offsets text; vertical padding affects automatic sizing while painting centers text vertically. Temporary bounds arrays are allocated, but no frame is begun or ended here.
     *
     * @param vg the prepared root-owned NanoVG context
     */
    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L) {
            return;
        }

        refreshStyleCache();

        Color drawColor;
        if (!isEnabled()) {
            drawColor = disabledColor;
        } else if (isPressed()) {
            drawColor = pressedColor;
        } else if (isFocused()) {
            drawColor = focusedColor;
        } else if (isHovered()) {
            drawColor = hoverColor;
        } else if (visited) {
            drawColor = visitedColor;
        } else {
            drawColor = color;
        }

        float x = getAbsoluteX() + paddingX;
        float y = getAbsoluteY();
        float height = getHeight();
        float textY = y + height * 0.5f;

        nvgFontSize(vg, fontSize);
        nvgFontFace(vg, fontName);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, NanoUtility.color1(drawColor));
        nvgText(vg, x, textY, text);

        float[] bounds = new float[4];
        nvgTextBounds(vg, x, textY, text == null ? "" : text, bounds);
        measuredTextWidth = Math.max(0f, bounds[2] - bounds[0]);
        measuredTextHeight = Math.max(0f, bounds[3] - bounds[1]);

        if ((underlineAlways || isHovered() || isFocused() || isPressed()) && measuredTextWidth > 0f) {
            float underlineY = bounds[3] + Math.max(1f, underlineThickness * 0.5f);
            nvgBeginPath(vg);
            nvgStrokeWidth(vg, underlineThickness);
            nvgStrokeColor(vg, NanoUtility.color2(drawColor));
            nvgMoveTo(vg, bounds[0], underlineY);
            nvgLineTo(vg, bounds[2], underlineY);
            nvgStroke(vg);
        }
    }

    /**
     * Restores hardcoded appearance defaults, then overlays nonnull resolved style values. Colors are retained by reference; numeric sizes have lower bounds but no explicit finiteness validation. Blank font names keep the default. Called during drawing, layout and style invalidation.
     */
    private void refreshStyleCache() {
        ResolvedStyle style = getStyle();

        color = new Color(0xFF4DA3FF);
        hoverColor = new Color(0xFF7FBEFF);
        focusedColor = new Color(0xFFA9D1FF);
        pressedColor = new Color(0xFF2E7FD1);
        disabledColor = new Color(0xFF6E6E6E);
        visitedColor = new Color(0xFFC08CFF);

        fontSize = 18f;
        underlineThickness = 1.5f;
        paddingX = 0f;
        paddingY = 0f;
        underlineAlways = false;
        fontName = "default";

        if (style == null)
            return;

        Color resolvedColor = style.get(COLOR_KEY);
        Color resolvedHoverColor = style.get(HOVER_COLOR_KEY);
        Color resolvedFocusedColor = style.get(FOCUSED_COLOR_KEY);
        Color resolvedPressedColor = style.get(PRESSED_COLOR_KEY);
        Color resolvedDisabledColor = style.get(DISABLED_COLOR_KEY);
        Color resolvedVisitedColor = style.get(VISITED_COLOR_KEY);

        Float resolvedFontSize = style.get(FONT_SIZE_KEY);
        Float resolvedUnderlineThickness = style.get(UNDERLINE_THICKNESS_KEY);
        Float resolvedPaddingX = style.get(PADDING_X_KEY);
        Float resolvedPaddingY = style.get(PADDING_Y_KEY);
        Boolean resolvedUnderlineAlways = style.get(UNDERLINE_ALWAYS_KEY);
        String resolvedFontName = style.get(FONT_NAME_KEY);

        if (resolvedColor != null) color = resolvedColor;
        if (resolvedHoverColor != null) hoverColor = resolvedHoverColor;
        if (resolvedFocusedColor != null) focusedColor = resolvedFocusedColor;
        if (resolvedPressedColor != null) pressedColor = resolvedPressedColor;
        if (resolvedDisabledColor != null) disabledColor = resolvedDisabledColor;
        if (resolvedVisitedColor != null) visitedColor = resolvedVisitedColor;

        if (resolvedFontSize != null) fontSize = Math.max(1f, resolvedFontSize);
        if (resolvedUnderlineThickness != null) underlineThickness = Math.max(0f, resolvedUnderlineThickness);
        if (resolvedPaddingX != null) paddingX = Math.max(0f, resolvedPaddingX);
        if (resolvedPaddingY != null) paddingY = Math.max(0f, resolvedPaddingY);
        if (resolvedUnderlineAlways != null) underlineAlways = resolvedUnderlineAlways;
        if (resolvedFontName != null && !resolvedFontName.isBlank()) fontName = resolvedFontName;
    }

    /**
     * Parses a URI and requests Desktop browsing when supported, otherwise starts the platform launcher (rundll32, open or xdg-open). Returns immediately after a successful request without waiting for process exit or destination loading. IOException and URI syntax failures return false; other runtime failures propagate. No scheme restriction is applied.
     *
     * @param link the destination string to parse and launch
     * @return whether a browse request or launcher start succeeded
     */
    private boolean openInBrowser(String link) {
        try {
            URI uri = new URI(link);

            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(uri);
                    return true;
                }
            }

            String os = System.getProperty("os.name", "").toLowerCase();

            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString()).start();
                return true;
            }

            if (os.contains("mac")) {
                new ProcessBuilder("open", uri.toString()).start();
                return true;
            }

            new ProcessBuilder("xdg-open", uri.toString()).start();
            return true;
        } catch (IOException | URISyntaxException ignored) {
            return false;
        }
    }
}
