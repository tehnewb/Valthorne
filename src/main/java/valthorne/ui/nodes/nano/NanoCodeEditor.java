package valthorne.ui.nodes.nano;

import valthorne.ui.NanoText;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.TextInputEvent;
import valthorne.graphics.Color;
import valthorne.graphics.font.TextRun;
import valthorne.ui.NanoUtility;
import valthorne.ui.UIRenderContext;
import valthorne.ui.UIRoot;
import valthorne.ui.behavior.TextEditModel;
import valthorne.ui.behavior.TextEditing;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Editable multiline NanoVG text surface with line numbers and pluggable
 * syntax colors. A secondary click opens standard undo, clipboard, deletion,
 * and selection commands at the pointer while preserving an existing selection.
 * Mutations and callbacks run on the UI thread. The editor owns its edit model
 * and transient command menu; fonts belong to the theme or root. Line starts
 * are rebuilt after text changes, while painting visits only visible lines.
 *
 * <pre>{@code
 * NanoCodeEditor editor = new NanoCodeEditor().text(source);
 * editor.onSave(() -> saveDocument(editor.getText()));
 * root.add(editor);
 * }</pre>
 */
public class NanoCodeEditor extends NanoPanel {

    /**
     * Main foreground used when no syntax highlighter is installed.
     */
    public static final StyleKey<Color> TEXT_COLOR_KEY = StyleKey.of("nano.codeEditor.textColor", Color.class, new Color(0xFFE5E5E5));
    /**
     * Line-number foreground.
     */
    public static final StyleKey<Color> GUTTER_COLOR_KEY = StyleKey.of("nano.codeEditor.gutterColor", Color.class, new Color(0xFF858585));
    /**
     * Editable text selection background.
     */
    public static final StyleKey<Color> SELECTION_COLOR_KEY = StyleKey.of("nano.codeEditor.selectionColor", Color.class, new Color(0xFF747474));
    /**
     * Insertion caret foreground.
     */
    public static final StyleKey<Color> CARET_COLOR_KEY = StyleKey.of("nano.codeEditor.caretColor", Color.class, Color.WHITE);
    /**
     * Baseline distance between editor lines in UI units.
     */
    public static final StyleKey<Float> LINE_HEIGHT_KEY = StyleKey.of("nano.codeEditor.lineHeight", Float.class, 18f);
    /**
     * Font size used for source text, line numbers, hit testing and the caret.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.codeEditor.fontSize", Float.class, 12f);
    /**
     * Fixed left gutter reserved for line numbers and their spacing.
     */
    private static final float TEXT_X = 64;
    private float lineHeight = 18f; // Distance between successive editor lines in UI units.
    private float fontSize = 12f; // Current resolved source and gutter text size.
    private final TextEditModel model = new TextEditModel()
            .multiline(true); // Owned content model retaining edits, projection, or selection state.
    private final NanoPopupMenu contextMenu = new NanoPopupMenu(); // Standard text commands for a secondary click.
    private final ArrayList<Integer> starts = new ArrayList<>(); // Cached UTF-16 start offset of each source line.
    private String lastText = ""; // Last source string used to build cached line offsets.
    private Function<String, List<NanoColoredRun>> highlighter; // Optional application callback returning colored source runs.
    private Consumer<String> changed = text -> {}; // Synchronous application callback after text changes.
    private Runnable save = () -> {}; // Application action invoked by the save shortcut.
    private float blink; // Elapsed time used to alternate caret visibility.
    private int preferredColumn = -1; // Retained horizontal caret column, or minus one before vertical navigation.
    private String fontName = "default"; // Borrowed registered font name used for measurement and rendering.
    private float[] selectionRanges = new float[8]; // Reused visual selection pairs for the current shaped line.
    private int[] highlightEnds = new int[8]; // Reused exclusive display offsets for syntax-color spans.
    private Color[] highlightColors = new Color[8]; // Borrowed colors paired with the current highlighted line's spans.

    /**
     * Builds the control and its owned children from the supplied configuration.
     */
    public NanoCodeEditor() {
        setClickable(true);
        setFocusable(true);
        model.onChange(this::modelChanged);
        rebuildLines();
    }

    /**
     * Returns the owned editing model for native input-method integration.
     * Mutations belong to the UI owner thread and retain normal change listeners.
     * @return live source editing model
     */
    public TextEditModel getEditor() {
        return model;
    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();
        if (style != null) {
            Float size = style.get(FONT_SIZE_KEY);
            Float height = style.get(LINE_HEIGHT_KEY);
            float nextSize = size != null && Float.isFinite(size) && size > 0 ? size : fontSize;
            float nextHeight = height != null && Float.isFinite(height) && height > 0 ? height : lineHeight;
            if (fontSize != nextSize || lineHeight != nextHeight) {
                fontSize = nextSize;
                lineHeight = nextHeight;
                rebuildLines();
            }
        }
        super.applyLayout();
    }

    /**
     * Returns current source text without copying the editing model.
     *
     * @return current source text without copying the editing model
     */
    public String getText() {
        return model.text();
    }

    /**
     * Returns the current UTF-16 insertion offset in the source text.
     *
     * @return the current utf-16 insertion offset in the source text
     */
    public int getCaretOffset() {
        return model.caret();
    }

    /**
     * Replaces source text through the editing model and synchronizes line geometry.
     *
     * @param value replacement value applied to this control
     * @return this control for further configuration
     */
    public NanoCodeEditor text(String value) {
        model.text(value);
        return this;
    }

    /**
     * Selects a borrowed registered font name for drawing and caret measurement.
     *
     * @param name borrowed registered font or displayed command name
     * @return this control for further configuration
     */
    public NanoCodeEditor font(String name) {
        fontName = Objects.requireNonNull(name);
        return this;
    }

    /**
     * Installs the callback producing ordered colored runs for each visible source
     * line. Run text must concatenate to the unchanged input line. Adaptive text
     * is shaped once for the complete line; clusters spanning color boundaries
     * retain the color of their first source character.
     *
     * @param value replacement value applied to this control
     * @return this control for further configuration
     */
    public NanoCodeEditor highlighter(Function<String, List<NanoColoredRun>> value) {
        highlighter = Objects.requireNonNull(value);
        return this;
    }

    /**
     * Replaces the synchronous user-change callback without emitting an initial notification.
     *
     * @param listener nonnull synchronous application callback
     * @return this control for further configuration
     */
    public NanoCodeEditor onChange(Consumer<String> listener) {
        changed = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Installs the action requested by the editor save shortcut.
     *
     * @param listener nonnull synchronous application callback
     * @return this control for further configuration
     */
    public NanoCodeEditor onSave(Runnable listener) {
        save = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Restores the most recent source edit and makes it available for redo.
     */
    public void undo() {
        model.undo();
    }

    /**
     * Reapplies the most recently undone source edit.
     */
    public void redo() {
        model.redo();
    }

    /**
     * Opens the standard editing actions at a secondary pointer press.
     */
    private void showContextMenu(MousePressEvent event) {
        int offset = positionAt(event.getX(), event.getY());
        if (offset < model.start() || offset > model.end()) model.move(offset, false);
        contextMenu.items(List.of(new NanoPopupMenuItem("Undo", model::undo, model.canUndo()), new NanoPopupMenuItem("Redo", model::redo, model.canRedo()), new NanoPopupMenuItem("Cut", () -> {
            if (TextEditing.copy(model, false)) model.deleteSelection();
        }, model.hasSelection()), new NanoPopupMenuItem("Copy", () -> TextEditing.copy(model, false), model.hasSelection()), new NanoPopupMenuItem("Paste", () -> TextEditing.paste(model), true), new NanoPopupMenuItem("Delete", model::deleteSelection, model.hasSelection()), new NanoPopupMenuItem("Select All", model::selectAll, !model.text().isEmpty())))
                .showAt(this, event.getX(), event.getY(), 176);
    }

    /**
     * Rebuilds cached line starts after model changes and notifies the application.
     */
    private void modelChanged() {
        blink = 0;
        if (!lastText.equals(model.text())) {
            preferredColumn = -1;
            lastText = model.text();
            rebuildLines();
            changed.accept(lastText);
        }
        revealCaret();
    }

    /**
     * Recomputes UTF-16 line starts and content bounds after text or typography changes.
     */
    private void rebuildLines() {
        starts.clear();
        starts.add(0);
        String text = model.text();
        int longest = 0, start = 0;
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) == '\n') {
                longest = Math.max(longest, visualLength(text.substring(start, i)));
                starts.add(i + 1);
                start = i + 1;
        }
        longest = Math.max(longest, visualLength(text.substring(start)));
        getLayout()
                .width(Math.max(500, TEXT_X + 20 + longest * fontSize * 0.65f))
                .height(Math.max(1, starts.size()) * lineHeight);
        markLayoutDirty();
    }

    /**
     * Counts display columns with tab expansion for fixed-width source layout.
     *
     * @param line zero-based source line index
     * @return counts display columns with tab expansion for fixed-width source layout
     */
    private static int visualLength(String line) {
        int length = 0;
        for (int i = 0; i < line.length(); i++) length += line.charAt(i) == '\t' ? 4 : 1;
        return length;
    }

    /**
     * Finds the source line containing a UTF-16 offset using cached line starts.
     *
     * @param offset UTF-16 insertion offset within the current source text
     * @return finds the source line containing a utf-16 offset using cached line starts
     */
    private int lineAt(int offset) {
        int low = 0, high = starts.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (starts.get(middle) <= offset) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    /**
     * Returns the exclusive content end of a source line, excluding its newline.
     *
     * @param line zero-based source line index
     * @return the exclusive content end of a source line, excluding its newline
     */
    private int lineEnd(int line) {
        return line + 1 < starts.size() ? starts.get(line + 1) - 1 : model.text().length();
    }

    /**
     * Copies one source line without its terminating newline for display.
     *
     * @param line zero-based source line index
     * @return copies one source line without its terminating newline for display
     */
    private String lineText(int line) {
        return model.text()
                .substring(starts.get(line), lineEnd(line));
    }

    /**
     * Expands tab characters into spaces for aligned source display.
     *
     * @param text nonnull text to display or process
     * @return expands tab characters into spaces for aligned source display
     */
    private static String visible(String text) {
        return text.replace("\t", "    ");
    }

    /**
     * Finds the enclosing scroll viewport, or returns null for an unscrolled editor.
     *
     * @return finds the enclosing scroll viewport, or returns null for an unscrolled editor
     */
    private NanoScrollPanel scroll() {
        for (var node = getParent(); node != null; node = node.getParent())
            if (node instanceof NanoScrollPanel panel) return panel;
        return null;
    }

    /**
     * Measures source text using the same font and size as rendering.
     *
     * @param text nonnull text to display or process
     * @return measures source text using the same font and size as rendering
     */
    private float width(String text) {
        return NanoText.measureTextWidth(this, getRoot() == null ? 0 : getRoot().getNanoVGHandle(), fontName, fontSize, visible(text));
    }

    /**
     * Maps a source insertion offset into the tab-expanded display line.
     *
     * @param value complete source line
     * @param index source UTF-16 insertion offset
     * @return corresponding display UTF-16 offset
     */
    private int displayOffset(String value, int index) {
        int result = Math.clamp(index, 0, value.length());
        for (int i = 0; i < index && i < value.length(); i++)
            if (value.charAt(i) == '\t') result += 3;
        return result;
    }

    /**
     * Resolves the primary shaped caret within a complete tab-expanded line.
     * Custom NanoVG faces retain their existing prefix measurement path.
     *
     * @param value complete source line
     * @param index source UTF-16 insertion boundary
     * @return visual horizontal offset from the line origin
     */
    private float caretPosition(String value, int index) {
        TextRun layout = NanoText.layout(this, fontName, fontSize, visible(value));
        return layout == null ? width(value.substring(0, index)) : layout.caretX(displayOffset(value, index));
    }

    /**
     * Adjusts scrolling only as needed to keep the insertion caret visible.
     */
    private void revealCaret() {
        NanoScrollPanel panel = scroll();
        if (panel == null) return;
        int line = lineAt(model.caret());
        float top = line * lineHeight, bottom = top + lineHeight;
        if (top < panel.getScrollY()) panel.scrollY(top);
        else if (bottom > panel.getScrollY() + panel.getHeight()) panel.scrollY(bottom - panel.getHeight());
        float x = TEXT_X + caretPosition(lineText(line), model.caret() - starts.get(line));
        if (x < panel.getScrollX() + TEXT_X) panel.scrollX(Math.max(0, x - TEXT_X));
        else if (x > panel.getScrollX() + panel.getWidth() - 12) panel.scrollX(x - panel.getWidth() + 12);
    }

    /**
     * Converts a pointer position into a clamped UTF-16 insertion offset.
     *
     * @param screenX pointer X in window screen coordinates
     * @param screenY pointer Y in bottom-origin window coordinates
     * @return converts a pointer position into a clamped utf-16 insertion offset
     */
    private int positionAt(float screenX, float screenY) {
        var point = screenToLayout(screenX, screenY);
        int line = Math.clamp((int) ((point.y() - getAbsoluteY()) / lineHeight), 0, starts.size() - 1);
        String value = lineText(line);
        float x = point.x() - getAbsoluteX() - TEXT_X;
        TextRun layout = NanoText.layout(this, fontName, fontSize, visible(value));
        if (layout != null) {
            int display = layout.hitTest(x);
            int source = 0;
            int offset = 0;
            while (source < value.length() && offset < display) {
                int length = value.charAt(source) == '\t' ? 4 : 1;
                if (offset + length > display && display - offset < length * 0.5f) break;
                offset += length;
                source++;
            }
            return model.boundary(starts.get(line) + source);
        }
        if (x <= 0) return starts.get(line);
        int index = 0;
        while (index < value.length()) {
            int next = value.offsetByCodePoints(index, 1);
            float midpoint = (width(value.substring(0, index)) + width(value.substring(0, next))) * 0.5f;
            if (x < midpoint) break;
            index = next;
        }
        return starts.get(line) + index;
    }

    @Override
    public void onMousePress(MousePressEvent event) {
        if (event.getButton() == Mouse.RIGHT && !isDisabled()) {
            if (getRoot() != null) getRoot()
                    .setFocusTo(this);
            showContextMenu(event);
            event.consume();
            return;
        }
        if (event.getButton() != Mouse.LEFT || isDisabled()) return;
        if (getRoot() != null) getRoot()
                .setFocusTo(this);
        model.move(positionAt(event.getX(), event.getY()), event.isShiftDown());
        preferredColumn = -1;
        event.consume();
    }

    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (event.getButton() != Mouse.LEFT || isDisabled()) return;
        model.move(positionAt(event.getToX(), event.getToY()), true);
        event.consume();
    }

    @Override
    public void onTextInput(TextInputEvent event) {
        if (!isFocused() || isDisabled()) return;
        model.insert(event.getText());
        preferredColumn = -1;
        event.consume();
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (!isFocused() || isDisabled()) return;
        int key = event.getKey();
        boolean control = event.isCtrlDown() || event.isSuperDown();
        boolean shift = event.isShiftDown();
        if (control && key == Keyboard.S) {
            save.run();
            event.consume();
            return;
        }
        if (control && key == Keyboard.Z) {
            if (shift) redo();
            else undo();
            preferredColumn = -1;
            event.consume();
            return;
        }
        if (control && key == Keyboard.Y) {
            redo();
            preferredColumn = -1;
            event.consume();
            return;
        }
        if (key == Keyboard.UP || key == Keyboard.DOWN || key == Keyboard.PAGE_UP || key == Keyboard.PAGE_DOWN) {
            int line = lineAt(model.caret());
            if (preferredColumn < 0) preferredColumn = model.caret() - starts.get(line);
            int step = key == Keyboard.PAGE_UP || key == Keyboard.PAGE_DOWN ? 20 : 1;
            int next = Math.clamp(line + (key == Keyboard.UP || key == Keyboard.PAGE_UP ? -step : step), 0, starts.size() - 1);
            model.move(Math.min(starts.get(next) + preferredColumn, lineEnd(next)), shift);
        } else if (key == Keyboard.HOME || key == Keyboard.END) {
            int line = lineAt(model.caret());
            model.move(control ? key == Keyboard.HOME ? 0 : model.text().length() : key == Keyboard.HOME ? starts.get(line) : lineEnd(line), shift);
            preferredColumn = -1;
        } else if (key == Keyboard.ENTER || key == Keyboard.KP_ENTER) {
            model.insert("\n");
            preferredColumn = -1;
        } else if (key == Keyboard.TAB) {
            model.insert("\t");
            preferredColumn = -1;
        } else if (TextEditing.key(model, event, false)) {
            preferredColumn = -1;
        } else return;
        event.consume();
    }

    @Override
    public void update(float delta) {
        super.update(delta);
        blink += delta;
    }

    @Override
    public void onDestroy() {
        contextMenu.close();
        super.onDestroy();
    }

    @Override
    public void draw(long vg) {
        super.draw(vg);
        ResolvedStyle style = getStyle();
        Color textColor = style == null ? TEXT_COLOR_KEY.getDefaultValue() : style.get(TEXT_COLOR_KEY);
        Color gutterColor = style == null ? GUTTER_COLOR_KEY.getDefaultValue() : style.get(GUTTER_COLOR_KEY);
        Color selectionColor = style == null ? SELECTION_COLOR_KEY.getDefaultValue() : style.get(SELECTION_COLOR_KEY);
        Color caretColor = style == null ? CARET_COLOR_KEY.getDefaultValue() : style.get(CARET_COLOR_KEY);
        NanoScrollPanel panel = scroll();
        int first = Math.max(0, (int) ((panel == null ? 0 : panel.getScrollY()) / lineHeight) - 1);
        int last = Math.min(starts.size(), (int) (((panel == null ? getHeight() : panel.getScrollY() + panel.getHeight())) / lineHeight) + 2);
        float x = getAbsoluteX(), y = getAbsoluteY();
        nvgSave(vg);
        nvgFontFace(vg, fontName);
        nvgFontSize(vg, fontSize);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        for (int line = first; line < last; line++) {
            float top = y + line * lineHeight + 2;
            String value = lineText(line);
            int begin = Math.max(model.start(), starts.get(line));
            int end = Math.min(model.end(), lineEnd(line));
            if (begin < end) {
                nvgBeginPath(vg);
                TextRun layout = NanoText.layout(this, fontName, fontSize, visible(value));
                if (layout == null) {
                    float left = width(value.substring(0, begin - starts.get(line)));
                    float right = width(value.substring(0, end - starts.get(line)));
                    nvgRect(vg, x + TEXT_X + left, top - 1, Math.max(1, right - left), lineHeight);
                } else {
                    int ranges = layout.selectionRanges(displayOffset(value, begin - starts.get(line)), displayOffset(value, end - starts.get(line)), selectionRanges);
                    if (ranges * 2 > selectionRanges.length) {
                        selectionRanges = new float[ranges * 2];
                        layout.selectionRanges(displayOffset(value, begin - starts.get(line)), displayOffset(value, end - starts.get(line)), selectionRanges);
                    }
                    for (int i = 0; i < ranges; i++) {
                        float left = selectionRanges[i * 2];
                        float right = selectionRanges[i * 2 + 1];
                        nvgRect(vg, x + TEXT_X + left, top - 1, right - left, lineHeight);
                    }
                }
                nvgFillColor(vg, NanoUtility.color1(selectionColor));
                nvgFill(vg);
            }
            nvgFillColor(vg, NanoUtility.color1(gutterColor));
            NanoText.draw(this, vg, fontName, x + 8, top, Integer.toString(line + 1), fontSize, gutterColor, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            if (highlighter == null) {
                nvgFillColor(vg, NanoUtility.color1(textColor));
                NanoText.draw(this, vg, fontName, x + TEXT_X, top, visible(value), fontSize, textColor, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            } else {
                List<NanoColoredRun> runs = highlighter.apply(value);
                TextRun layout = NanoText.layout(this, fontName, fontSize, visible(value));
                UIRoot root = getRoot();
                UIRenderContext context = root == null ? null : root.getRenderContext();
                if (layout != null && context != null) {
                    if (runs.size() > highlightEnds.length) {
                        highlightEnds = new int[runs.size()];
                        highlightColors = new Color[runs.size()];
                    }
                    int offset = 0;
                    for (int i = 0; i < runs.size(); i++) {
                        NanoColoredRun run = runs.get(i);
                        offset += visible(run.text()).length();
                        highlightEnds[i] = offset;
                        highlightColors[i] = run.color();
                    }
                    context.drawNanoTextStyled(this, layout, highlightEnds, highlightColors, runs.size(), x + TEXT_X, top + layout.font().ascent() * fontSize);
                } else {
                    float cursor = x + TEXT_X;
                    for (NanoColoredRun run : runs) {
                        nvgFillColor(vg, NanoUtility.color1(run.color()));
                        cursor = NanoText.draw(this, vg, fontName, cursor, top, visible(run.text()), fontSize, run.color(), NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
                    }
                }
            }
        }
        if (isFocused() && (blink % 1f) < 0.5f) {
            int line = lineAt(model.caret());
            if (line >= first && line < last) {
                float cursor = x + TEXT_X + caretPosition(lineText(line), model.caret() - starts.get(line));
                nvgBeginPath(vg);
                nvgRect(vg, cursor, y + line * lineHeight + 2, 1.5f, fontSize + 3);
                nvgFillColor(vg, NanoUtility.color1(caretColor));
                nvgFill(vg);
            }
        }
        nvgRestore(vg);
    }
}
