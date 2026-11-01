package valthorne.android;

import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import valthorne.android.internal.AndroidRuntime;

/**
 * Delivers committed Android IME text to the engine editing model. Composition
 * stays in this connection until committed, preventing duplicate intermediate
 * characters. The engine retains its text, selection, validation and undo state.
 */
final class AndroidTextConnection extends BaseInputConnection {
    private final AndroidSurface view; // Native view receiving hardware-equivalent key events.
    private String composing = ""; // Uncommitted IME text, cleared on commit or finish.

    /**
     * Creates an input connection for the current engine text focus.
     * @param view native input endpoint
     */
    AndroidTextConnection(AndroidSurface view) { super(view, false); this.view = view; }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        composing = "";
        AndroidRuntime.emit(11, 0, 0, 0, 0, 0, 0, text.toString());
        return true;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) { composing = text.toString(); return true; }

    @Override
    public boolean finishComposingText() {
        if (!composing.isEmpty()) { AndroidRuntime.emit(11, 0, 0, 0, 0, 0, 0, composing); composing = ""; }
        return true;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        for (int i = 0; i < beforeLength; i++) stroke(KeyEvent.KEYCODE_DEL);
        for (int i = 0; i < afterLength; i++) stroke(KeyEvent.KEYCODE_FORWARD_DEL);
        return true;
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) { return deleteSurroundingText(beforeLength, afterLength); }

    @Override
    public boolean sendKeyEvent(KeyEvent event) { return view.dispatchKey(event); }

    @Override
    public boolean performEditorAction(int actionCode) {
        if (actionCode != EditorInfo.IME_ACTION_DONE && actionCode != EditorInfo.IME_ACTION_GO) return false;
        stroke(KeyEvent.KEYCODE_ENTER);
        return true;
    }

    /**
     * Emits one key press and release without retaining Android event objects.
     * @param code Android key code
     */
    private void stroke(int code) { view.dispatchKey(new KeyEvent(KeyEvent.ACTION_DOWN, code)); view.dispatchKey(new KeyEvent(KeyEvent.ACTION_UP, code)); }
}
