package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * NanoVG text leaf with explicit line breaks, expanded tab stops, and left/top
 * alignment. Fonts are selected by an already registered NanoVG name; this node
 * does not load or own font resources. Width and height are measured from the
 * root's context when available, otherwise estimated from character count.
 *
 * <p>Layout replaces auto width/height with measured numeric values. Subsequent
 * text changes mark layout dirty but do not restore those dimensions to auto;
 * set them back to auto when another content-size measurement is desired.
 * Text is normalized for measurement and drawing without modifying getText().</p>
 *
 * @author Albert Beaupre
 */
public class NanoLabel extends UINode implements NanoNode {

    /**
     * Theme font registration name, defaulting to default.
     */
    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.label.fontName", String.class, "default");
    /**
     * Theme text size in UI units, defaulting to 18.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.label.fontSize", Float.class, 18f);
    /**
     * Theme text color, defaulting to the shared white color.
     */
    public static final StyleKey<Color> COLOR_KEY = StyleKey.of("nano.label.color", Color.class, Color.WHITE);
    /**
     * Theme tab-stop spacing in character columns, defaulting to four.
     */
    public static final StyleKey<Float> TAB_SIZE_KEY = StyleKey.of("nano.label.tabSize", Float.class, 4f);
    /**
     * Theme extra line spacing in UI units, defaulting to zero.
     */
    public static final StyleKey<Float> LINE_SPACING_KEY = StyleKey.of("nano.label.lineSpacing", Float.class, 0f);

    private String text = ""; // Raw non-null text before newline and tab normalization.
    private String fontName = "default"; // Borrowed NanoVG font registration name.
    private float fontSize = 18f; // Text measurement and drawing size in UI units.
    private Color color = Color.WHITE; // Borrowed mutable text color, initially the shared white constant.
    private float tabSize = 4f; // Requested tab-stop interval rounded to integer character columns.
    private float lineSpacing; // Extra UI-unit distance between successive lines.
    private boolean fontLoaded; // Reserved font state; current implementation does not read or update it.

    /**
     * Creates an empty label using the default font name, size 18, white color,
     * four-column tab stops, and zero extra line spacing. Font registration belongs
     * to the root/application.
     */
    public NanoLabel() {
    }

    /**
     * Creates a label with the supplied raw text and default font settings.
     * Text normalization is deferred to layout and drawing.
     *
     * @param text label contents; null is stored as an empty string
     */
    public NanoLabel(String text) {
        this.text = text == null ? "" : text;
    }

    /**
     * Returns the raw stored text before newline normalization and tab expansion.
     *
     * @return non-null label contents
     */
    public String getText() {
        return text;
    }

    /**
     * Replaces raw contents, converting null to empty, and marks layout dirty.
     * Previously measured numeric dimensions are retained unless restored to auto.
     *
     * @param text new label contents, possibly null
     * @return this label
     */
    public NanoLabel text(String text) {
        this.text = text == null ? "" : text;
        markLayoutDirty();
        return this;
    }

    /**
     * Reads the name selected for NanoVG font lookup; this does not verify registration.
     *
     * @return stored font name
     */
    public String getFontName() {
        return fontName;
    }

    /**
     * Stores a nonblank font name and marks layout dirty. Null or blank input keeps
     * the old name but still dirties layout. No font resource is loaded here.
     *
     * @param fontName name of a font registered in the root's NanoVG context
     * @return this label
     */
    public NanoLabel fontName(String fontName) {
        if (fontName != null && !fontName.isBlank()) this.fontName = fontName;
        markLayoutDirty();
        return this;
    }

    /**
     * Reads the requested NanoVG text size used by measurement and drawing.
     *
     * @return font size in UI units
     */
    public float getFontSize() {
        return fontSize;
    }

    /**
     * Sets text size with a minimum of one and marks layout dirty. Supply a finite
     * value; Math.max does not reject NaN. Resolved styles can later replace it.
     *
     * @param fontSize requested size in UI units
     * @return this label
     */
    public NanoLabel fontSize(float fontSize) {
        this.fontSize = Math.max(1f, fontSize);
        markLayoutDirty();
        return this;
    }

    /**
     * Exposes the current borrowed text color. Mutating it affects rendering and
     * may also affect other objects sharing that color, including shared constants.
     *
     * @return live color reference
     */
    public Color getColor() {
        return color;
    }

    /**
     * Retains a non-null color without copying it or dirtying geometry. Null leaves
     * the current value unchanged; style application can replace the reference.
     *
     * @param color text color reference, or null to retain the current value
     * @return this label
     */
    public NanoLabel color(Color color) {
        if (color != null) this.color = color;
        return this;
    }

    /**
     * Reads the requested tab-stop interval before rounding to an integer column count.
     *
     * @return configured interval in character columns
     */
    public float getTabSize() {
        return tabSize;
    }

    /**
     * Sets tab-stop spacing with a minimum of one and marks layout dirty. Rendering
     * rounds the value to an integer and advances each tab to the next stop.
     *
     * @param tabSize finite requested column interval
     * @return this label
     */
    public NanoLabel tabSize(float tabSize) {
        this.tabSize = Math.max(1f, tabSize);
        markLayoutDirty();
        return this;
    }

    /**
     * Reads extra spacing added between measured text lines.
     *
     * @return additional interline distance in UI units
     */
    public float getLineSpacing() {
        return lineSpacing;
    }

    /**
     * Sets nonnegative extra line spacing and marks layout dirty. No spacing is
     * added after the last line; resolved styles may replace this value.
     *
     * @param lineSpacing finite requested extra distance in UI units
     * @return this label
     */
    public NanoLabel lineSpacing(float lineSpacing) {
        this.lineSpacing = Math.max(0f, lineSpacing);
        markLayoutDirty();
        return this;
    }

    /**
     * Performs no resource allocation. The owning root/application is responsible
     * for font registration and the NanoVG context before layout or drawing.
     */
    @Override
    public void onCreate() {
    }

    /**
     * Performs no resource disposal because this leaf borrows the root's context
     * and registered fonts.
     */
    @Override
    public void onDestroy() {
    }

    /**
     * Performs no per-frame work or child traversal; label contents change through
     * setters and layout processing.
     *
     * @param delta elapsed update seconds, unused
     */
    @Override
    public void update(float delta) {
    }

    /**
     * Enters shared UI dispatch so the root can switch to the NanoVG callback
     * with the correct clipping and backend state.
     *
     * @param batch active texture batch used by mixed-renderer traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }

    /**
     * Applies resolved font/color/spacing values, normalizes text, and measures
     * all lines. Without a root NanoVG handle, estimates width as half font size
     * per UTF-16 unit and line height as font size. Replaces only dimensions still
     * marked auto, then delegates base layout. Font and color resources are borrowed.
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            String resolvedFontName = style.get(FONT_NAME_KEY);
            Float resolvedFontSize = style.get(FONT_SIZE_KEY);
            Color resolvedColor = style.get(COLOR_KEY);
            Float resolvedTabSize = style.get(TAB_SIZE_KEY);
            Float resolvedLineSpacing = style.get(LINE_SPACING_KEY);

            if (resolvedFontName != null && !resolvedFontName.isBlank()) fontName = resolvedFontName;
            if (resolvedFontSize != null) fontSize = Math.max(1f, resolvedFontSize);
            if (resolvedColor != null) color = resolvedColor;
            if (resolvedTabSize != null) tabSize = Math.max(1f, resolvedTabSize);
            if (resolvedLineSpacing != null) lineSpacing = Math.max(0f, resolvedLineSpacing);
        }

        String normalized = normalizeText(text);
        String[] lines = splitLines(normalized);

        UIRoot root = getRoot();
        long vg = root != null ? root.getNanoVGHandle() : 0L;

        float measuredWidth = 0f;
        float measuredHeight;

        if (vg != 0L) {
            nvgFontSize(vg, fontSize);
            nvgFontFace(vg, fontName);

            float lineHeight = NanoUtility.measureTextHeight(vg, fontName, fontSize);

            for (String line : lines) {
                measuredWidth = Math.max(measuredWidth, NanoUtility.measureTextWidth(vg, fontName, fontSize, line));
            }

            measuredHeight = lines.length == 0 ? lineHeight : (lines.length * lineHeight) + Math.max(0, lines.length - 1) * lineSpacing;
        } else {
            float estimatedLineHeight = fontSize;
            for (String line : lines) {
                measuredWidth = Math.max(measuredWidth, line.length() * fontSize * 0.5f);
            }
            measuredHeight = lines.length == 0 ? estimatedLineHeight : (lines.length * estimatedLineHeight) + Math.max(0, lines.length - 1) * lineSpacing;
        }

        if (getLayout().getWidth().isAuto()) getLayout().width(measuredWidth);
        if (getLayout().getHeight().isAuto()) getLayout().height(measuredHeight);

        super.applyLayout();
    }

    /**
     * Draws normalized lines at absolute node coordinates using left/top alignment.
     * Skips invisible nodes and a zero context handle. The root must already have
     * begun the NanoVG frame and prepared transforms and clipping.
     *
     * @param vg borrowed active NanoVG context, or zero to skip
     */
    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L) return;

        String normalized = normalizeText(text);
        String[] lines = splitLines(normalized);

        nvgFontSize(vg, fontSize);
        nvgFontFace(vg, fontName);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgFillColor(vg, NanoUtility.color1(color));

        float lineHeight = NanoUtility.measureTextHeight(vg, fontName, fontSize);
        float x = getAbsoluteX();
        float y = getAbsoluteY();

        for (int i = 0; i < lines.length; i++) {
            nvgText(vg, x, y + i * (lineHeight + lineSpacing), lines[i]);
        }
    }

    /**
     * Normalizes CRLF, CR, form feed, and vertical tab to newline, then expands tabs.
     * Does not modify stored text; null or empty input returns an empty string.
     *
     * @param value raw label text
     * @return normalized text with tabs expanded to spaces
     */
    private String normalizeText(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').replace('\f', '\n').replace('\u000B', '\n');
        return expandTabs(normalized, Math.max(1, Math.round(tabSize)));
    }

    /**
     * Expands tabs to the next fixed character-column stop, restarting columns at
     * newlines. Columns count UTF-16 units rather than visual glyph widths, so
     * proportional fonts do not produce pixel-aligned tab stops.
     *
     * @param value normalized non-null text
     * @param tabWidth positive tab interval in character columns
     * @return newly built text with tabs replaced by spaces
     */
    private String expandTabs(String value, int tabWidth) {
        StringBuilder builder = new StringBuilder(value.length());
        int column = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '\n') {
                builder.append('\n');
                column = 0;
                continue;
            }

            if (c == '\t') {
                int spaces = tabWidth - (column % tabWidth);
                builder.append(" ".repeat(Math.max(0, spaces)));
                column += spaces;
                continue;
            }

            builder.append(c);
            column++;
        }

        return builder.toString();
    }

    /**
     * Splits normalized text at newlines while preserving trailing empty lines.
     * Empty input therefore yields one empty line and retains a line-height measure.
     *
     * @param value non-null normalized text
     * @return lines including empty leading, interior, and trailing entries
     */
    private String[] splitLines(String value) {
        return value.split("\n", -1);
    }
}
