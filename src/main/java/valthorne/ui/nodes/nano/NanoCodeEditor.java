package valthorne.ui.nodes.nano;

import valthorne.ui.NanoText;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.TextInputEvent;
import valthorne.graphics.Color;
import valthorne.ui.NanoUtility;
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
     * Line height value used by NanoCodeEditor.
     */
    private static final int LINE_HEIGHT = 20;
    /**
     * Font size value used by NanoCodeEditor.
     */
    private static final float FONT_SIZE = 14, TEXT_X = 64;
    private final TextEditModel model = new TextEditModel().multiline(true);
    private final NanoPopupMenu contextMenu = new NanoPopupMenu(); // Standard text commands for a secondary click.
    private final ArrayList<Integer> starts = new ArrayList<>();
    private String lastText = "";
    private Function<String, List<NanoColoredRun>> highlighter;
    private Consumer<String> changed = text -> {};
    private Runnable save = () -> {};
    private float blink;
    private int preferredColumn = -1;
    private String fontName = "default";

    public NanoCodeEditor() {
        setClickable(true);
        setFocusable(true);
        model.onChange(this::modelChanged);
        rebuildLines();
    }

    public String getText() {return model.text();}

    public int getCaretOffset() {return model.caret();}

    public NanoCodeEditor text(String value) {
        model.text(value);
        return this;
    }

    public NanoCodeEditor font(String name) {
        fontName = Objects.requireNonNull(name);
        return this;
    }

    public NanoCodeEditor highlighter(Function<String, List<NanoColoredRun>> value) {
        highlighter = Objects.requireNonNull(value);
        return this;
    }

    public NanoCodeEditor onChange(Consumer<String> listener) {
        changed = Objects.requireNonNull(listener);
        return this;
    }

    public NanoCodeEditor onSave(Runnable listener) {
        save = Objects.requireNonNull(listener);
        return this;
    }

    /** Restores the most recent source edit and makes it available for redo. */
    public void undo() {model.undo();}

    /** Reapplies the most recently undone source edit. */
    public void redo() {model.redo();}

    /** Opens the standard editing actions at a secondary pointer press. */
    private void showContextMenu(MousePressEvent event) {
        int offset = positionAt(event.getX(), event.getY());
        if (offset < model.start() || offset > model.end()) model.move(offset, false);
        contextMenu.items(List.of(new NanoPopupMenu.Item("Undo", model::undo, model.canUndo()), new NanoPopupMenu.Item("Redo", model::redo, model.canRedo()), new NanoPopupMenu.Item("Cut", () -> {
            if (TextEditing.copy(model, false)) model.deleteSelection();
        }, model.hasSelection()), new NanoPopupMenu.Item("Copy", () -> TextEditing.copy(model, false), model.hasSelection()), new NanoPopupMenu.Item("Paste", () -> TextEditing.paste(model), true), new NanoPopupMenu.Item("Delete", model::deleteSelection, model.hasSelection()), new NanoPopupMenu.Item("Select All", model::selectAll, !model.text().isEmpty()))).showAt(this, event.getX(), event.getY(), 176);
    }

    private void modelChanged() {
        blink = 0;
        if (!lastText.equals(model.text())) {
            lastText = model.text();
            rebuildLines();
            changed.accept(lastText);
        }
        revealCaret();
    }

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
        getLayout().width(Math.max(500, TEXT_X + 20 + longest * 9f)).height(Math.max(1, starts.size()) * LINE_HEIGHT);
        markLayoutDirty();
    }

    private static int visualLength(String line) {
        int length = 0;
        for (int i = 0; i < line.length(); i++) length += line.charAt(i) == '\t' ? 4 : 1;
        return length;
    }

    private int lineAt(int offset) {
        int low = 0, high = starts.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (starts.get(middle) <= offset) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private int lineEnd(int line) {
        return line + 1 < starts.size() ? starts.get(line + 1) - 1 : model.text().length();
    }

    private String lineText(int line) {return model.text().substring(starts.get(line), lineEnd(line));}

    private static String visible(String text) {return text.replace("\t", "    ");}

    private NanoScrollPanel scroll() {
        for (var node = getParent(); node != null; node = node.getParent())
            if (node instanceof NanoScrollPanel panel) return panel;
        return null;
    }

    private float width(String text) {
        return NanoText.measureTextWidth(this, getRoot() == null ? 0 : getRoot().getNanoVGHandle(), fontName, FONT_SIZE, visible(text));
    }

    private void revealCaret() {
        NanoScrollPanel panel = scroll();
        if (panel == null) return;
        int line = lineAt(model.caret());
        float top = line * LINE_HEIGHT, bottom = top + LINE_HEIGHT;
        if (top < panel.getScrollY()) panel.scrollY(top);
        else if (bottom > panel.getScrollY() + panel.getHeight()) panel.scrollY(bottom - panel.getHeight());
        float x = TEXT_X + width(model.text().substring(starts.get(line), model.caret()));
        if (x < panel.getScrollX() + TEXT_X) panel.scrollX(Math.max(0, x - TEXT_X));
        else if (x > panel.getScrollX() + panel.getWidth() - 12) panel.scrollX(x - panel.getWidth() + 12);
    }

    private int positionAt(float screenX, float screenY) {
        var point = screenToLayout(screenX, screenY);
        int line = Math.clamp((int) ((point.y() - getAbsoluteY()) / LINE_HEIGHT), 0, starts.size() - 1);
        String value = lineText(line);
        float x = point.x() - getAbsoluteX() - TEXT_X;
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
            if (getRoot() != null) getRoot().setFocusTo(this);
            showContextMenu(event);
            event.consume();
            return;
        }
        if (event.getButton() != Mouse.LEFT || isDisabled()) return;
        if (getRoot() != null) getRoot().setFocusTo(this);
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
        int first = Math.max(0, (int) ((panel == null ? 0 : panel.getScrollY()) / LINE_HEIGHT) - 1);
        int last = Math.min(starts.size(), (int) (((panel == null ? getHeight() : panel.getScrollY() + panel.getHeight())) / LINE_HEIGHT) + 2);
        float x = getAbsoluteX(), y = getAbsoluteY();
        nvgSave(vg);
        nvgFontFace(vg, fontName);
        nvgFontSize(vg, FONT_SIZE);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        for (int line = first; line < last; line++) {
            float top = y + line * LINE_HEIGHT + 2;
            String value = lineText(line);
            int begin = Math.max(model.start(), starts.get(line));
            int end = Math.min(model.end(), lineEnd(line));
            if (begin < end) {
                float left = x + TEXT_X + width(model.text().substring(starts.get(line), begin));
                float right = x + TEXT_X + width(model.text().substring(starts.get(line), end));
                nvgBeginPath(vg);
                nvgRect(vg, left, top - 1, Math.max(1, right - left), LINE_HEIGHT);
                nvgFillColor(vg, NanoUtility.color1(selectionColor));
                nvgFill(vg);
            }
            nvgFillColor(vg, NanoUtility.color1(gutterColor));
            NanoText.draw(this, vg, x + 8, top, Integer.toString(line + 1), FONT_SIZE, gutterColor, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            if (highlighter == null) {
                nvgFillColor(vg, NanoUtility.color1(textColor));
                NanoText.draw(this, vg, x + TEXT_X, top, visible(value), FONT_SIZE, textColor, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            } else {
                float cursor = x + TEXT_X;
                for (NanoColoredRun run : highlighter.apply(value)) {
                    nvgFillColor(vg, NanoUtility.color1(run.color()));
                    cursor = NanoText.draw(this, vg, cursor, top, visible(run.text()), FONT_SIZE, run.color(), NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
                }
            }
        }
        if (isFocused() && (blink % 1f) < 0.5f) {
            int line = lineAt(model.caret());
            if (line >= first && line < last) {
                float cursor = x + TEXT_X + width(model.text().substring(starts.get(line), model.caret()));
                nvgBeginPath(vg);
                nvgRect(vg, cursor, y + line * LINE_HEIGHT + 2, 1.5f, FONT_SIZE + 3);
                nvgFillColor(vg, NanoUtility.color1(caretColor));
                nvgFill(vg);
            }
        }
        nvgRestore(vg);
    }
}
