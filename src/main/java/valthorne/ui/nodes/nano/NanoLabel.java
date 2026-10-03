package valthorne.ui.nodes.nano;

import valthorne.ui.NanoText;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.enums.Alignment;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.*;
import valthorne.ui.behavior.TextSelection;
import valthorne.ui.behavior.TextEditing;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * NanoVG text leaf with explicit line breaks, expanded tab stops, configurable
 * horizontal and vertical alignment. Each line is aligned within the resolved
 * label width; the complete text block is aligned within its height. In NanoVG's
 * top-left coordinates, vertical START means top and END means bottom. Selection
 * and projected previews use the same offsets. Fonts are selected by
 * an already registered NanoVG name; this node
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

    /*
     * Optional theme override for horizontal alignment of each text line.
     */
    public static final StyleKey<Alignment> HORIZONTAL_ALIGNMENT_KEY = StyleKey.of("nano.label.horizontalAlignment", Alignment.class);

    /*
     * Optional theme override for vertical alignment of the complete text block.
     */
    public static final StyleKey<Alignment> VERTICAL_ALIGNMENT_KEY = StyleKey.of("nano.label.verticalAlignment", Alignment.class);

    private String text = ""; // Raw non-null text before newline and tab normalization.
    private String fontName = "default"; // Borrowed NanoVG font registration name.
    private float fontSize = 18f; // Text measurement and drawing size in UI units.
    private Color color = Color.WHITE; // Borrowed mutable text color, initially the shared white constant.
    private float tabSize = 4f; // Requested tab-stop interval rounded to integer character columns.
    private float lineSpacing; // Extra UI-unit distance between successive lines.
    private Alignment horizontalAlignment = Alignment.START; // Horizontal placement of each line within the resolved label width.
    private Alignment verticalAlignment = Alignment.START; // Vertical placement of the complete text block; START is top.
    private boolean fontLoaded; // Reserved font state; current implementation does not read or update it.
    private TextSelection selection;
    private boolean selecting;
    private boolean documentSelection;
    private long lastPressTime;
    private float lastPressX, lastPressY;
    private int clickCount;
    private static final Color SELECTION_COLOR = new Color(0x995298CE);

    /** Opts into read-only pointer selection and Ctrl/Cmd+C. Disabled by default. */
    public NanoLabel selectable(boolean enabled) {
        if (enabled && selection == null) {
            selection = new TextSelection();
            selection.text(normalizeText(text));
        } else if (!enabled) selection = null;
        selecting = false;
        setFocusable(enabled);
        return this;
    }

    public boolean isSelectable() { return selection != null; }
    public String getSelectedText() { return selection == null ? "" : selection.selectedText(); }
    public void selectAll() { if (selection != null) selection.selectAll(); }

    /** Sets a root-managed range for selection spanning multiple labels. */
    public void documentSelection(int start, int end) {
        if (selection == null) return;
        selection.move(start, false);
        selection.move(end, true);
        documentSelection = start != end;
    }

    /** Hit-tests a caret using the same layout coordinates as text drawing. */
    public int selectionIndexAt(float screenX, float screenY) { return indexAt(screenX, screenY); }
    public int selectionTextLength() { return normalizeText(text).length(); }

    @Override public void onPointerCancel() { selecting = false; super.onPointerCancel(); }
    @Override public void onMousePress(MousePressEvent event) {
        if (selection == null || isDisabled() || event.getButton() != Mouse.LEFT) return;
        selecting = true;
        long now = System.nanoTime();
        float dx = event.getX() - lastPressX, dy = event.getY() - lastPressY;
        clickCount = now - lastPressTime < 450_000_000L && dx * dx + dy * dy < 25 ? clickCount % 3 + 1 : 1;
        lastPressTime = now; lastPressX = event.getX(); lastPressY = event.getY();
        int index = indexAt(event.getX(), event.getY());
        if (clickCount == 2) selection.selectWord(index);
        else if (clickCount == 3) selection.selectLine(index);
        else selection.move(index, event.isShiftDown());
        event.consume();
    }
    @Override public void onMouseDrag(MouseDragEvent event) {
        if (!selecting || selection == null) return;
        selection.move(indexAt(event.getToX(), event.getToY()), true);
        event.consume();
    }
    @Override public void onMouseRelease(MouseReleaseEvent event) { selecting = false; }
    @Override public void onKeyPress(KeyPressEvent event) {
        if (selection == null || !isFocused() || isDisabled()) return;
        boolean command = event.isCtrlDown() || event.isSuperDown();
        if (command && event.getKey() == Keyboard.C) TextEditing.copyText(getSelectedText());
        else if (command && event.getKey() == Keyboard.A) selectAll();
        else if (event.getKey() == Keyboard.LEFT) { if (command) selection.wordStep(false, event.isShiftDown()); else selection.step(false, event.isShiftDown()); }
        else if (event.getKey() == Keyboard.RIGHT) { if (command) selection.wordStep(true, event.isShiftDown()); else selection.step(true, event.isShiftDown()); }
        else if (event.getKey() == Keyboard.UP) selection.vertical(false, event.isShiftDown());
        else if (event.getKey() == Keyboard.DOWN) selection.vertical(true, event.isShiftDown());
        else if (event.getKey() == Keyboard.HOME) { if (command) selection.move(0, event.isShiftDown()); else selection.lineEdge(false, event.isShiftDown()); }
        else if (event.getKey() == Keyboard.END) { if (command) selection.move(normalizeText(text).length(), event.isShiftDown()); else selection.lineEdge(true, event.isShiftDown()); }
        else return;
        event.consume();
    }

    private int indexAt(float screenX, float screenY) {
        // NanoVG draws in top-left layout space, including ancestor scroll offsets.
        var point = screenToLayout(screenX, screenY);
        if (point.y() < getAbsoluteY()) return 0;
        if (point.y() >= getAbsoluteY() + getHeight()) return normalizeText(text).length();
        long vg = getRoot() == null ? 0 : getRoot().getNanoVGHandle();
        String[] lines = splitLines(normalizeText(text));
        float height = vg == 0 ? fontSize : NanoText.measureTextHeight(this, vg, fontName, fontSize);
        float textY = getAbsoluteY() + blockOffset(lines.length, height, lineSpacing, getHeight());
        int line = Math.max(0, Math.min(lines.length - 1,
                (int) Math.floor((point.y() - textY) / (height + lineSpacing))));
        int offset = 0;
        for (int i = 0; i < line; i++) offset += lines[i].length() + 1;
        float target = point.x() - getAbsoluteX() - lineOffset(vg, lines[line], fontSize, getWidth());
        float previous = 0;
        String value = lines[line];
        for (int i = 0; i < value.length();) {
            int next = value.offsetByCodePoints(i, 1);
            float edge = measure(vg, value.substring(0, next));
            if (target < (previous + edge) * .5f) return offset + i;
            previous = edge;
            i = next;
        }
        return offset + value.length();
    }

    private float measure(long vg, String value) {
        return vg == 0 ? value.length() * fontSize * .5f
                : NanoText.measureTextWidth(this, vg, fontName, fontSize, value);
    }

    /**
     * Computes a line's left offset using the same metrics as rendering. Start
     * alignment avoids measurement; overflowing text retains its requested alignment.
     *
     * @param vg active context, or zero to estimate character widths
     * @param line normalized text line
     * @param size rendered font size in UI units
     * @param width available label width in the same units
     * @return horizontal offset from the label's left edge
     */
    private float lineOffset(long vg, String line, float size, float width) {
        if (horizontalAlignment == Alignment.START) return 0f;
        float textWidth = vg == 0L ? line.length() * size * 0.5f : NanoText.measureTextWidth(this, vg, fontName, size, line);
        float remaining = width - textWidth;
        return horizontalAlignment == Alignment.CENTER ? remaining * 0.5f : remaining;
    }

    /**
     * Positions the complete multiline block, excluding spacing after its last
     * line. Overflow retains the requested alignment rather than being clamped.
     *
     * @param lines number of normalized text lines
     * @param lineHeight rendered height of one line
     * @param spacing distance between lines
     * @param height available label height in the same units
     * @return offset from the top edge
     */
    private float blockOffset(int lines, float lineHeight, float spacing, float height) {
        if (verticalAlignment == Alignment.START) return 0f;
        float remaining = height - lines * lineHeight - Math.max(0, lines - 1) * spacing;
        return verticalAlignment == Alignment.CENTER ? remaining * 0.5f : remaining;
    }

    /**
     * Sets independent horizontal and vertical text alignment. Horizontal alignment
     * applies to each line; vertical alignment applies to the complete text block.
     * Explicit dimensions provide space for alignment beyond intrinsic text size.
     * Resolved theme values may override either axis during layout.
     *
     * @param horizontal horizontal alignment, or null to retain its current value
     * @param vertical vertical alignment, or null to retain its current value
     * @return this label
     */
    public NanoLabel alignment(Alignment horizontal, Alignment vertical) {
        if (horizontal != null) horizontalAlignment = horizontal;
        if (vertical != null) verticalAlignment = vertical;
        return this;
    }

    /**
     * Reads the horizontal alignment applied independently to each line.
     *
     * @return current horizontal alignment
     */
    public Alignment getHorizontalAlignment() {
        return horizontalAlignment;
    }

    /**
     * Sets horizontal text alignment while retaining vertical alignment.
     *
     * @param horizontal alignment, or null to retain the current value
     * @return this label
     */
    public NanoLabel horizontalAlignment(Alignment horizontal) {
        if (horizontal != null) horizontalAlignment = horizontal;
        return this;
    }

    /**
     * Reads vertical alignment of the complete text block.
     *
     * @return current vertical alignment; START is top and END is bottom
     */
    public Alignment getVerticalAlignment() {
        return verticalAlignment;
    }

    /**
     * Sets vertical text alignment while retaining horizontal alignment.
     *
     * @param vertical alignment, or null to retain the current value; START is top
     * @return this label
     */
    public NanoLabel verticalAlignment(Alignment vertical) {
        if (vertical != null) verticalAlignment = vertical;
        return this;
    }

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
        if (selection != null) selection.text(normalizeText(this.text));
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
            Alignment resolvedHorizontalAlignment = style.get(HORIZONTAL_ALIGNMENT_KEY);
            Alignment resolvedVerticalAlignment = style.get(VERTICAL_ALIGNMENT_KEY);

            if (resolvedFontName != null && !resolvedFontName.isBlank()) fontName = resolvedFontName;
            if (resolvedFontSize != null) fontSize = Math.max(1f, resolvedFontSize);
            if (resolvedColor != null) color = resolvedColor;
            if (resolvedTabSize != null) tabSize = Math.max(1f, resolvedTabSize);
            if (resolvedLineSpacing != null) lineSpacing = Math.max(0f, resolvedLineSpacing);
            if (resolvedHorizontalAlignment != null) horizontalAlignment = resolvedHorizontalAlignment;
            if (resolvedVerticalAlignment != null) verticalAlignment = resolvedVerticalAlignment;
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

            float lineHeight = NanoText.measureTextHeight(this, vg, fontName, fontSize);

            for (String line : lines) {
                measuredWidth = Math.max(measuredWidth, NanoText.measureTextWidth(this, vg, fontName, fontSize, line));
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
     * Draws normalized lines with both alignment axes within the node's bounds.
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

        float lineHeight = NanoText.measureTextHeight(this, vg, fontName, fontSize);
        float x = getAbsoluteX();
        float y = getAbsoluteY() + blockOffset(lines.length, lineHeight, lineSpacing, getHeight());

        int offset = 0;
        for (int i = 0; i < lines.length; i++) {
            float lineX = x + lineOffset(vg, lines[i], fontSize, getWidth());
            if (selection != null && (isFocused() || documentSelection)) {
                int start = Math.max(0, selection.start() - offset);
                int end = Math.min(lines[i].length(), selection.end() - offset);
                if (end > start) {
                    float left = measure(vg, lines[i].substring(0, start));
                    float right = measure(vg, lines[i].substring(0, end));
                    nvgBeginPath(vg);
                    nvgRect(vg, lineX + left, y + i * (lineHeight + lineSpacing), right - left, lineHeight);
                    nvgFillColor(vg, NanoUtility.color1(SELECTION_COLOR));
                    nvgFill(vg);
                    nvgFillColor(vg, NanoUtility.color1(color));
                }
            }
            NanoText.draw(this, vg, lineX, y + i * (lineHeight + lineSpacing), lines[i], fontSize, color, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            offset += lines[i].length() + 1;
        }
    }

    /**
     * Draws this label's live text at a projected position without its runtime
     * selection decoration. The caller supplies an active NanoVG frame and owns
     * clipping, making this suitable for an editor scene preview.
     *
     * @param vg active NanoVG frame
     * @param x projected left edge
     * @param y projected top edge
     * @param scale projected text scale
     */
    public void drawAt(long vg, float x, float y, float scale) {
        if (!isVisible() || vg == 0L) return;
        String[] lines = splitLines(normalizeText(text));
        float projectedSize = fontSize * scale;
        nvgFontSize(vg, projectedSize);
        nvgFontFace(vg, fontName);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgFillColor(vg, NanoUtility.color1(color));
        float lineHeight = NanoText.measureTextHeight(this, vg, fontName, projectedSize);
        float textY = y + blockOffset(lines.length, lineHeight, lineSpacing * scale, getHeight() * scale);
        for (int index = 0; index < lines.length; index++) {
            float lineX = x + lineOffset(vg, lines[index], projectedSize, getWidth() * scale);
            NanoText.draw(this, vg, lineX, textY + index * (lineHeight + lineSpacing * scale), lines[index], projectedSize, color, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
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
