package valthorne.ui.behavior;

/** Read-only, multiline selection state. Allocated only for selectable text. */
public final class TextSelection {
    private String text = "";
    private int anchor, caret;

    public void text(String value) {
        text = value == null ? "" : value;
        anchor = caret = 0;
    }
    public int start() { return Math.min(anchor, caret); }
    public int end() { return Math.max(anchor, caret); }
    public int caret() { return caret; }
    public String selectedText() { return text.substring(start(), end()); }
    public void selectAll() { anchor = 0; caret = text.length(); }
    /** Selects a word, whitespace run, or punctuation run at a UTF-16 offset. */
    public void selectWord(int offset) {
        if (text.isEmpty()) return;
        move(Math.min(offset, text.length() - 1), false);
        int category = category(text.codePointAt(caret));
        int first = caret, last = caret;
        while (first > 0 && category(text.codePointBefore(first)) == category)
            first = text.offsetByCodePoints(first, -1);
        while (last < text.length() && category(text.codePointAt(last)) == category)
            last = text.offsetByCodePoints(last, 1);
        anchor = first; caret = last;
    }
    public void selectLine(int offset) {
        move(offset, false);
        anchor = text.lastIndexOf('\n', Math.max(-1, caret - 1)) + 1;
        int next = text.indexOf('\n', caret);
        caret = next < 0 ? text.length() : next + 1;
    }
    public void lineEdge(boolean end, boolean extend) {
        int next = end ? text.indexOf('\n', caret) : text.lastIndexOf('\n', caret - 1);
        move(end ? (next < 0 ? text.length() : next) : next + 1, extend);
    }
    public void vertical(boolean down, boolean extend) {
        int start = text.lastIndexOf('\n', caret - 1) + 1, column = caret - start;
        int next = text.indexOf('\n', caret);
        if (down) {
            if (next < 0) { move(text.length(), extend); return; }
            int end = text.indexOf('\n', next + 1);
            move(Math.min(next + 1 + column, end < 0 ? text.length() : end), extend);
        } else {
            if (start == 0) { move(0, extend); return; }
            int previous = text.lastIndexOf('\n', start - 2) + 1;
            move(Math.min(previous + column, start - 1), extend);
        }
    }
    public void wordStep(boolean right, boolean extend) {
        int offset = caret;
        if (right) {
            if (offset < text.length()) {
                int kind = category(text.codePointAt(offset));
                while (offset < text.length() && category(text.codePointAt(offset)) == kind)
                    offset = text.offsetByCodePoints(offset, 1);
            }
            while (offset < text.length() && Character.isWhitespace(text.codePointAt(offset))) offset = text.offsetByCodePoints(offset, 1);
        } else {
            while (offset > 0 && Character.isWhitespace(text.codePointBefore(offset))) offset = text.offsetByCodePoints(offset, -1);
            if (offset > 0) {
                int kind = category(text.codePointBefore(offset));
                while (offset > 0 && category(text.codePointBefore(offset)) == kind) offset = text.offsetByCodePoints(offset, -1);
            }
        }
        move(offset, extend);
    }
    private static int category(int point) {
        return Character.isLetterOrDigit(point) || point == '_' || Character.getType(point) == Character.NON_SPACING_MARK ? 1
                : Character.isWhitespace(point) ? 0 : 2;
    }
    public void move(int offset, boolean extend) {
        caret = Math.max(0, Math.min(text.length(), offset));
        if (caret > 0 && caret < text.length() && Character.isLowSurrogate(text.charAt(caret))
                && Character.isHighSurrogate(text.charAt(caret - 1))) caret--;
        if (!extend) anchor = caret;
    }
    public void step(boolean right, boolean extend) {
        if (!extend && anchor != caret) { move(right ? end() : start(), false); return; }
        if (right && caret < text.length()) move(text.offsetByCodePoints(caret, 1), extend);
        else if (!right && caret > 0) move(text.offsetByCodePoints(caret, -1), extend);
    }
}
