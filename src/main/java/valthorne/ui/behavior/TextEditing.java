package valthorne.ui.behavior;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

/**
 * Shared keyboard-shortcut and system-clipboard adapter for texture and NanoVG
 * text fields backed by TextEditModel. Navigation and edits delegate to the model;
 * this utility does not own text, focus, enabled state or event routing.
 *
 * <p>Ctrl and Super are treated equivalently for shortcuts. Shift extends cursor
 * movement selections and changes Ctrl/Super+Z to redo. Secret fields suppress
 * copying and cutting through this adapter, while pasting and ordinary editing
 * remain available. This is an interaction policy, not secure text storage.</p>
 *
 * <p>Keyboard dispatch returns whether it recognized a command, not whether text
 * changed. It does not consume the event; the receiving widget does so. Clipboard
 * operations use the platform clipboard synchronously and tolerate common access
 * failures. Run on the owning UI thread and let the model enforce insertion rules.</p>
 *
 * @author Albert Beaupre
 */
public final class TextEditing {
    /**
     * Prevents instances of this stateless adapter. Each operation receives the
     * model and event or clipboard policy explicitly.
     */
    private TextEditing() {}

    /**
     * Dispatches supported editing keys to the model without consuming the event.
     * Ctrl/Super supports A, C, X, V, Z, Y, word-wise Left/Right and word deletion
     * with Backspace/Delete. Without those modifiers, Left/Right move by the model's
     * character boundaries, Home/End move to text endpoints and deletion is ordinary.
     * Shift extends navigation selections and selects redo for Ctrl/Super+Z.
     *
     * <p>Copy and cut are suppressed for secret fields; cut deletes only after a
     * successful clipboard write. Recognized commands return true even when no
     * edit occurs or clipboard access fails. Ctrl/Super+Home/End are not handled
     * by this switch. Focus, enabled-state and key-repeat policies belong to callers.</p>
     *
     * @param model  the target editing model
     * @param event  the nonnull key press whose key and modifiers are inspected
     * @param secret whether clipboard export must be suppressed
     * @return true for a recognized shortcut; false for an unhandled key combination
     * @throws NullPointerException if event is null, or a handled operation dereferences a null model
     */
    public static boolean key(TextEditModel model, KeyPressEvent event, boolean secret) {
        boolean shift = event.isShiftDown();
        boolean ctrl = event.isCtrlDown() || event.isSuperDown();
        int key = event.getKey();
        if (ctrl) {
            switch (key) {
                case Keyboard.A -> model.selectAll();
                case Keyboard.C -> copy(model, secret);
                case Keyboard.X -> {if (!secret && copy(model, false)) model.deleteSelection();}
                case Keyboard.V -> paste(model);
                case Keyboard.Z -> {
                    if (shift) model.redo();
                    else model.undo();
                }
                case Keyboard.Y -> model.redo();
                case Keyboard.LEFT -> model.moveHorizontal(false, shift, true);
                case Keyboard.RIGHT -> model.moveHorizontal(true, shift, true);
                case Keyboard.BACKSPACE -> model.deleteBackward(true);
                case Keyboard.DELETE -> model.deleteForward(true);
                default -> {return false;}
            }
        } else {
            switch (key) {
                case Keyboard.LEFT -> model.moveHorizontal(false, shift, false);
                case Keyboard.RIGHT -> model.moveHorizontal(true, shift, false);
                case Keyboard.HOME -> model.move(0, shift);
                case Keyboard.END -> model.move(model.text().length(), shift);
                case Keyboard.BACKSPACE -> model.deleteBackward(false);
                case Keyboard.DELETE -> model.deleteForward(false);
                default -> {return false;}
            }
        }
        return true;
    }

    /**
     * Writes selected text to the system clipboard without modifying the model.
     * Secret mode returns immediately, as does an empty selection. Clipboard busy,
     * headless-environment and security failures return false instead of interrupting
     * editing. A successful result confirms clipboard assignment, not later availability.
     *
     * @param model  the model supplying selected text
     * @param secret whether export is forbidden
     * @return whether selected text was successfully assigned to the clipboard
     * @throws NullPointerException if model is null and secret is false
     */
    public static boolean copy(TextEditModel model, boolean secret) {
        if (secret || !model.hasSelection()) return false;
        return copyText(model.selectedText());
    }

    /** Copies read-only selected text, preserving line breaks. */
    public static boolean copyText(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            return true;
        } catch (IllegalStateException | java.awt.HeadlessException | SecurityException unavailable) {return false;}
    }

    /**
     * Requests string-flavor clipboard data and passes a returned String to the
     * model's insertion operation. The model handles selection replacement and
     * input validation; this adapter does not sanitize or force acceptance.
     * Unsupported data, I/O failures, clipboard contention, headless operation
     * and security failures are ignored. No event is consumed and no success
     * status is returned. Other failures may propagate.
     *
     * @param model the model that receives available clipboard text
     * @throws NullPointerException if model is null when a String is retrieved
     */
    public static void paste(TextEditModel model) {
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (value instanceof String text) model.insert(text);
        } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException
                 | IllegalStateException | java.awt.HeadlessException | SecurityException unavailable) {
            // Clipboard access can be temporarily unavailable; editing remains usable.
        }
    }
}
