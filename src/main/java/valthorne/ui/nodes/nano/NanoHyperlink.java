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

public class NanoHyperlink extends UINode implements NanoNode {

    public static final StyleKey<Color> COLOR_KEY = StyleKey.of("nano.hyperlink.color", Color.class, new Color(0xFF4DA3FF));
    public static final StyleKey<Color> HOVER_COLOR_KEY = StyleKey.of("nano.hyperlink.hoverColor", Color.class, new Color(0xFF7FBEFF));
    public static final StyleKey<Color> FOCUSED_COLOR_KEY = StyleKey.of("nano.hyperlink.focusedColor", Color.class, new Color(0xFFA9D1FF));
    public static final StyleKey<Color> PRESSED_COLOR_KEY = StyleKey.of("nano.hyperlink.pressedColor", Color.class, new Color(0xFF2E7FD1));
    public static final StyleKey<Color> DISABLED_COLOR_KEY = StyleKey.of("nano.hyperlink.disabledColor", Color.class, new Color(0xFF6E6E6E));
    public static final StyleKey<Color> VISITED_COLOR_KEY = StyleKey.of("nano.hyperlink.visitedColor", Color.class, new Color(0xFFC08CFF));

    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.hyperlink.fontSize", Float.class, 18f);
    public static final StyleKey<Float> UNDERLINE_THICKNESS_KEY = StyleKey.of("nano.hyperlink.underlineThickness", Float.class, 1.5f);
    public static final StyleKey<Float> PADDING_X_KEY = StyleKey.of("nano.hyperlink.paddingX", Float.class, 0f);
    public static final StyleKey<Float> PADDING_Y_KEY = StyleKey.of("nano.hyperlink.paddingY", Float.class, 0f);
    public static final StyleKey<Boolean> UNDERLINE_ALWAYS_KEY = StyleKey.of("nano.hyperlink.underlineAlways", Boolean.class, false);
    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.hyperlink.fontName", String.class, "default");

    private String text;
    private String url;

    private Color color = new Color(0xFF4DA3FF);
    private Color hoverColor = new Color(0xFF7FBEFF);
    private Color focusedColor = new Color(0xFFA9D1FF);
    private Color pressedColor = new Color(0xFF2E7FD1);
    private Color disabledColor = new Color(0xFF6E6E6E);
    private Color visitedColor = new Color(0xFFC08CFF);

    private float fontSize = 18f;
    private float underlineThickness = 1.5f;
    private float paddingX;
    private float paddingY;
    private boolean underlineAlways;
    private String fontName = "default";

    private boolean visited;
    private boolean armed;
    private float measuredTextWidth;
    private float measuredTextHeight;

    public NanoHyperlink(String text, String url) {
        this.text = text == null ? "" : text;
        this.url = url;
        setClickable(true);
        setFocusable(true);
    }

    public String getText() {
        return text;
    }

    public NanoHyperlink text(String text) {
        this.text = text == null ? "" : text;
        markLayoutDirty();
        return this;
    }

    public String getUrl() {
        return url;
    }

    public NanoHyperlink url(String url) {
        this.url = url;
        return this;
    }

    public boolean isVisited() {
        return visited;
    }

    public NanoHyperlink visited(boolean visited) {
        this.visited = visited;
        return this;
    }

    @Override
    public void onCreate() {
        markLayoutDirty();
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
    public void onMousePress(MousePressEvent event) {
        if (!isEnabled() || url == null || url.isBlank()) {
            armed = false;
            return;
        }
        armed = true;
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (!isEnabled()) {
            armed = false;
            return;
        }

        boolean shouldOpen = armed && isHovered() && url != null && !url.isBlank();
        armed = false;

        if (shouldOpen && openInBrowser(url)) {
            visited = true;
        }
    }

    @Override
    protected void invalidateStyleTree() {
        super.invalidateStyleTree();
        refreshStyleCache();
    }

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