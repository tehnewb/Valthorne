package valthorne.ui.behavior;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Single-line editing independent of fonts, rendering and native input.
 * Indices are UTF-16 offsets snapped to extended grapheme boundaries. Maximum length
 * is measured in Unicode code points, while movement and deletion preserve graphemes.
 * Validation reports acceptability but does not veto edits. Changes are synchronous
 * and unsynchronized; use the model on the owning UI thread.
 *
 * <pre>{@code
 * TextEditModel model = new TextEditModel();
 * model.maxLength(40);
 * model.text("Hello");
 * model.move(model.text().length(), false);
 * model.insert(" world");
 * model.undo();
 * }</pre>
 *
 * <p>Programmatic text replacement sanitizes content, collapses selection, and clears
 * history. Insertion and deletion retain up to 100 undo states. A lowered maximum
 * does not truncate existing text and still permits edits that do not increase an
 * already-over-limit code-point count. Notifications can report text, selection, or
 * validator changes and are not limited to text modifications.</p>
 *
 * @author Albert Beaupre
 */
public final class TextEditModel {
    /**
     * Extended grapheme matcher used to build UTF-16 cursor boundaries for non-ASCII text.
     */
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private final ArrayDeque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>(); // Snapshots used by undo and redo, newest at the tail.
    private final ChangeSignal changes = new ChangeSignal(); // Shared synchronous text/selection/validation notifications.
    private String text = ""; // Current sanitized single-line text.
    private int anchor, caret; // Selection anchor and active end as UTF-16 offsets.
    private int[] boundaries; // Reusable non-ASCII grapheme-boundary storage.
    private int boundaryCount; // Number of valid entries in the boundary array.
    private boolean ascii = true; // Enables direct index movement when all characters are ASCII.
    private int maxLength = Integer.MAX_VALUE; // Code-point limit applied to growth during insertion.
    private Predicate<String> validator = value -> true; // Validity query predicate; does not reject edits.

    /**
     * Removes unpaired surrogate code points and replaces ISO control characters with
     * spaces, avoiding an extra space when the current output already ends in one.
     * Ordinary spaces are otherwise preserved. Valid unchanged input is returned directly.
     *
     * @param value input text, or null for an empty result
     * @return sanitized single-line text
     */
    public static String sanitize(String value) {
        if (value == null) return "";
        StringBuilder result = null;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            boolean invalid = cp >= 0xD800 && cp <= 0xDFFF;
            boolean control = Character.isISOControl(cp);
            if ((invalid || control) && result == null) {
                result = new StringBuilder(value.length());
                result.append(value, 0, i);
            }
            i += Character.charCount(cp);
            if (invalid) continue;
            if (control) {
                if (result.isEmpty() || result.charAt(result.length() - 1) != ' ') result.append(' ');
            } else if (result != null) result.appendCodePoint(cp);
        }
        return result == null ? value : result.toString();
    }

    /**
     * Returns the current immutable string without copying or validating it.
     *
     * @return sanitized text
     */
    public String text() {return text;}

    /**
     * Returns the active selection end as a UTF-16 offset on a grapheme boundary.
     *
     * @return current caret offset
     */
    public int caret() {return caret;}

    /**
     * Returns the fixed selection end as a UTF-16 offset. It can lie before or after
     * the caret depending on selection direction.
     *
     * @return current anchor offset
     */
    public int anchor() {return anchor;}

    /**
     * Returns the smaller selection endpoint without changing selection direction.
     *
     * @return inclusive selection start
     */
    public int start() {return Math.min(anchor, caret);}

    /**
     * Returns the larger selection endpoint without changing selection direction.
     *
     * @return exclusive selection end
     */
    public int end() {return Math.max(anchor, caret);}

    /**
     * Tests whether anchor and caret differ. A collapsed selection has no selected text.
     *
     * @return true when a nonempty range is selected
     */
    public boolean hasSelection() {return anchor != caret;}

    /**
     * Extracts the text between the ordered endpoints. Returns an empty string when
     * selection is collapsed, without changing the caret or history.
     *
     * @return selected substring
     */
    public String selectedText() {return text.substring(start(), end());}

    /**
     * Runs the current validator against the current text each time it is called.
     * Validation exceptions propagate; edits are not rejected by this predicate.
     *
     * @return predicate result for current text
     */
    public boolean isValid() {return validator.test(text);}

    /**
     * Replaces the validity predicate and notifies listeners immediately without changing
     * text, selection, or history. The predicate is evaluated by isValid, not here.
     *
     * @param validator replacement predicate
     * @throws NullPointerException if validator is null
     */
    public void validator(Predicate<String> validator) {
        this.validator = Objects.requireNonNull(validator);
        notifyListeners();
    }

    /**
     * Sets the insertion growth limit in code points. Does not truncate existing text,
     * clear history, or notify listeners. Programmatic text replacement bypasses this limit.
     *
     * @param length nonnegative maximum code-point count
     * @throws IllegalArgumentException if length is negative
     */
    public void maxLength(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative length");
        maxLength = length;
    }

    /**
     * Adds a listener alongside existing widget synchronization listeners. Notifications
     * run synchronously; registration itself does not fire the callback.
     *
     * @param listener change callback
     * @return subscription handle that removes the listener when closed
     */
    public AutoCloseable onChange(Runnable listener) {
        return changes.subscribe(listener);
    }

    /**
     * Synchronously emits a change signal for current model state. Does not distinguish
     * text changes from selection or validation changes.
     */
    private void notifyListeners() {changes.fire();}

    /**
     * Replaces text after sanitization, rebuilds grapheme boundaries, clamps and snaps the
     * old caret, collapses selection there, clears both histories, and notifies listeners.
     * Maximum length and validator do not prevent this assignment.
     *
     * @param value replacement text, or null for empty text
     */
    public void text(String value) {
        text = sanitize(value);
        rebuild();
        caret = boundary(Math.min(caret, text.length()));
        anchor = caret;
        clearHistory();
        notifyListeners();
    }

    /**
     * Drops all undo and redo snapshots without modifying text or selection and without
     * notifying listeners.
     */
    public void clearHistory() {
        undo.clear();
        redo.clear();
    }

    /**
     * Reports whether a prior edit snapshot is available without consuming it.
     *
     * @return true when undo history is nonempty
     */
    public boolean canUndo() {return !undo.isEmpty();}

    /**
     * Reports whether an undone snapshot can be restored without consuming it.
     *
     * @return true when redo history is nonempty
     */
    public boolean canRedo() {return !redo.isEmpty();}

    /**
     * Captures text and both selection endpoints for history. The immutable string can
     * be safely shared without copying its contents.
     *
     * @return new history snapshot
     */
    private State state() {return new State(text, anchor, caret);}

    /**
     * Restores a history snapshot, rebuilds grapheme boundaries, and notifies listeners.
     * Does not enforce the current length limit or validator and does not alter stacks.
     *
     * @param state previously captured text and endpoints
     */
    private void restore(State state) {
        text = state.text;
        anchor = state.anchor;
        caret = state.caret;
        rebuild();
        notifyListeners();
    }

    /**
     * Moves the current snapshot to redo and restores the most recent undo state.
     * Empty undo history is a no-op with no notification.
     */
    public void undo() {
        if (!undo.isEmpty()) {
            redo.addLast(state());
            restore(undo.removeLast());
        }
    }

    /**
     * Moves the current snapshot to undo and restores the most recent redo state.
     * Empty redo history is a no-op with no notification.
     */
    public void redo() {
        if (!redo.isEmpty()) {
            undo.addLast(state());
            restore(redo.removeLast());
        }
    }

    /**
     * Replaces the selection with sanitized input, recording current endpoints for undo.
     * Rejects unchanged text and growth beyond the code-point limit; validator results
     * do not prevent insertion. Successful edits collapse selection and notify listeners.
     *
     * @param value replacement text, or null for empty input
     * @return true if text changed
     */
    public boolean insert(String value) {
        return insert(value, anchor, caret);
    }

    /**
     * Performs replacement while recording caller-supplied pre-edit selection endpoints.
     * Allows non-growing edits to text already above the limit. Successful changes retain
     * at most 100 undo snapshots, clear redo, rebuild boundaries, and snap the new caret
     * backward to a grapheme boundary.
     *
     * @param value text to sanitize and insert
     * @param undoAnchor anchor saved before a temporary deletion selection
     * @param undoCaret caret saved before a temporary deletion selection
     * @return whether text changed
     */
    private boolean insert(String value, int undoAnchor, int undoCaret) {
        value = sanitize(value);
        String next = text.substring(0, start()) + value + text.substring(end());
        int nextLength = next.codePointCount(0, next.length());
        if (next.equals(text) || (nextLength > maxLength && nextLength > text.codePointCount(0, text.length())))
            return false;
        undo.addLast(new State(text, undoAnchor, undoCaret));
        if (undo.size() > 100) undo.removeFirst();
        redo.clear();
        int nextCaret = start() + value.length();
        text = next;
        rebuild();
        caret = boundary(nextCaret);
        anchor = caret;
        notifyListeners();
        return true;
    }

    /**
     * Deletes the selection, or extends a temporary selection backward by one grapheme
     * or word segment before deleting. Saves original endpoints for undo; a boundary
     * no-op does not create history or notify listeners.
     *
     * @param word true to use word navigation instead of one grapheme
     */
    public void deleteBackward(boolean word) {
        int undoAnchor = anchor, undoCaret = caret;
        if (!hasSelection()) anchor = word ? previousWord(caret) : previous(caret);
        insert("", undoAnchor, undoCaret);
    }

    /**
     * Deletes the selection, or selects the next grapheme or word segment for deletion.
     * Original selection endpoints are restored by undo after a successful change.
     *
     * @param word true to use word navigation instead of one grapheme
     */
    public void deleteForward(boolean word) {
        int undoAnchor = anchor, undoCaret = caret;
        if (!hasSelection()) anchor = word ? nextWord(caret) : next(caret);
        insert("", undoAnchor, undoCaret);
    }

    /**
     * Deletes a nonempty selection through the normal insertion/history path.
     * A collapsed selection is a no-op.
     */
    public void deleteSelection() {if (hasSelection()) insert("");}

    /**
     * Sets anchor to zero and caret to the text end, then notifies listeners even if
     * the same range was already selected. Does not alter edit history.
     */
    public void selectAll() {
        anchor = 0;
        caret = text.length();
        notifyListeners();
    }

    /**
     * Clamps a UTF-16 offset to text bounds and snaps it backward to a grapheme boundary.
     * Moves the caret there, preserves anchor only for extension, and always notifies.
     *
     * @param offset requested UTF-16 caret offset
     * @param extend whether to retain the existing selection anchor
     */
    public void move(int offset, boolean extend) {
        caret = boundary(offset);
        if (!extend) anchor = caret;
        notifyListeners();
    }

    /**
     * Moves by a grapheme or word boundary. Without extension or word movement, an
     * existing selection collapses to the endpoint in the requested direction. Other
     * moves start from the active caret and follow the normal move notification path.
     *
     * @param right true to move forward, false backward
     * @param extend whether to preserve the selection anchor
     * @param word whether to use word segmentation
     */
    public void moveHorizontal(boolean right, boolean extend, boolean word) {
        if (!extend && hasSelection() && !word) {
            move(right ? end() : start(), false);
            return;
        }
        move(right ? (word ? nextWord(caret) : next(caret)) : (word ? previousWord(caret) : previous(caret)), extend);
    }

    /**
     * Clamps an offset and returns the nearest grapheme boundary at or before it.
     * ASCII text uses every code-unit boundary; non-ASCII text uses the cached matcher result.
     *
     * @param index requested UTF-16 offset
     * @return clamped preceding boundary
     */
    public int boundary(int index) {
        index = Math.clamp(index, 0, text.length());
        if (ascii) return index;
        int at = java.util.Arrays.binarySearch(boundaries, 0, boundaryCount, index);
        return boundaries[at >= 0 ? at : Math.max(0, -at - 2)];
    }

    /**
     * Returns the boundary at or before index minus one, clamped to text bounds.
     * Offsets at or below zero return zero without reading text.
     *
     * @param index UTF-16 offset from which to step backward
     * @return preceding clamped boundary
     */
    public int previous(int index) {return index <= 0 ? 0 : boundary(index - 1);}

    /**
     * Returns the first boundary strictly after an in-range offset. Negative offsets
     * return zero; offsets at or beyond the end return the text length.
     *
     * @param index UTF-16 offset from which to step forward
     * @return next clamped boundary
     */
    public int next(int index) {
        if (index < 0) return 0;
        if (index >= text.length()) return text.length();
        if (ascii) return index + 1;
        int at = java.util.Arrays.binarySearch(boundaries, 0, boundaryCount, index);
        return boundaries[at >= 0 ? at + 1 : -at - 1];
    }

    /**
     * Snaps backward, skips preceding whitespace, then consumes a run with the same
     * letter-or-digit classification. Punctuation forms a separate run; this is a
     * navigation heuristic rather than locale-sensitive word analysis.
     *
     * @param index starting UTF-16 offset
     * @return preceding word-segment boundary
     */
    public int previousWord(int index) {
        int i = boundary(index);
        while (i > 0 && Character.isWhitespace(text.codePointAt(previous(i)))) i = previous(i);
        if (i == 0) return 0;
        boolean word = Character.isLetterOrDigit(text.codePointAt(previous(i)));
        do {i = previous(i);}
        while (i > 0 && !Character.isWhitespace(text.codePointAt(previous(i)))
                && Character.isLetterOrDigit(text.codePointAt(previous(i))) == word);
        return i;
    }

    /**
     * Snaps the starting offset, advances through matching letter-or-digit classification
     * until whitespace or a class change, then skips following whitespace. Traversal
     * uses grapheme boundaries and is not locale-sensitive word analysis.
     *
     * @param index starting UTF-16 offset
     * @return following word-segment boundary
     */
    public int nextWord(int index) {
        int i = boundary(index);
        if (i == text.length()) return i;
        boolean word = Character.isLetterOrDigit(text.codePointAt(i));
        do {i = next(i);}
        while (i < text.length() && !Character.isWhitespace(text.codePointAt(i))
                && Character.isLetterOrDigit(text.codePointAt(i)) == word);
        while (i < text.length() && Character.isWhitespace(text.codePointAt(i))) i = next(i);
        return i;
    }

    /**
     * Detects ASCII text for direct indexing; otherwise repopulates reusable grapheme
     * end offsets beginning at zero. Grows boundary storage as needed without shrinking
     * it when later text becomes shorter or ASCII.
     */
    private void rebuild() {
        ascii = true;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 128) {
                ascii = false;
                break;
            }
        }
        if (ascii) {
            boundaryCount = 0;
            return;
        }
        if (boundaries == null) boundaries = new int[32];
        boundaryCount = 1;
        boundaries[0] = 0;
        var matcher = GRAPHEME.matcher(text);
        while (matcher.find()) {
            if (boundaryCount == boundaries.length)
                boundaries = java.util.Arrays.copyOf(boundaries, boundaries.length * 2);
            boundaries[boundaryCount++] = matcher.end();
        }
    }

    /**
     * Immutable edit-history snapshot sharing the immutable text string. Endpoints
     * represent the selection before an edit and are restored without revalidation.
     *
     * <p>Snapshots carry text and selection together so undo and redo restore a coherent editing
     * position. They contain no widget rendering state, clipboard handle, or callback.</p>
     *
     * @param text text at snapshot time
     * @param anchor fixed selection endpoint in UTF-16 units
     * @param caret active selection endpoint in UTF-16 units
     * @author Albert Beaupre
     */
    private record State(String text, int anchor, int caret) {
    }
}
