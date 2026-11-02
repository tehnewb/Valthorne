package valthorne.android;

import android.text.InputType;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import valthorne.android.internal.AndroidRuntime;

/**
 * Native SurfaceView that forwards copied touch, mouse, hardware-keyboard and IME
 * events to Valthorne's render thread. Primary touch uses the existing mouse API;
 * Android retains ownership of MotionEvent and KeyEvent instances.
 */
public final class AndroidSurface extends SurfaceView implements SurfaceHolder.Callback {
    private final ValthorneActivity owner; // Activity owning this view and its input-method connection.
    private boolean editing; // Whether the engine's focused node accepts text.
    private boolean masked; // Whether the focused field requests a password keyboard.

    /**
     * Creates an Android presentation endpoint before the render thread starts.
     * @param owner native Valthorne activity
     */
    AndroidSurface(ValthorneActivity owner) {
        super(owner);
        this.owner = owner;
        setFocusable(true);
        setFocusableInTouchMode(true);
        getHolder().addCallback(this);
        requestFocus();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        AndroidRuntime.surface(holder.getSurface(), Math.max(1, getWidth()), Math.max(1, getHeight()));
        owner.startApplication();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { AndroidRuntime.surface(holder.getSurface(), width, height); }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) { AndroidRuntime.loseSurface(); }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) return true;
        double x = event.getX() * AndroidRuntime.width() / Math.max(1, getWidth());
        double y = event.getY() * AndroidRuntime.height() / Math.max(1, getHeight());
        AndroidRuntime.emit(2, 0, 0, 0, 0, x, y, null);
        if (action == MotionEvent.ACTION_DOWN) { requestFocus(); AndroidRuntime.emit(3, 0, 1, 0, 0, 0, 0, null); }
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) AndroidRuntime.emit(3, 0, 0, 0, 0, 0, 0, null);
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) return super.onGenericMotionEvent(event);
        AndroidRuntime.emit(4, 0, 0, 0, 0, event.getAxisValue(MotionEvent.AXIS_HSCROLL), event.getAxisValue(MotionEvent.AXIS_VSCROLL), null);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) { return dispatchKey(event) || super.onKeyDown(keyCode, event); }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) { return dispatchKey(event) || super.onKeyUp(keyCode, event); }

    /**
     * Maps an Android hardware key into the existing GLFW-compatible key values.
     * @param event copied synchronously before Android reuses it
     * @return whether the engine handled the key
     */
    boolean dispatchKey(KeyEvent event) {
        int key = switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_ENTER -> 257;
            case KeyEvent.KEYCODE_DEL -> 259;
            case KeyEvent.KEYCODE_FORWARD_DEL -> 261;
            case KeyEvent.KEYCODE_TAB -> 258;
            case KeyEvent.KEYCODE_ESCAPE -> 256;
            case KeyEvent.KEYCODE_DPAD_LEFT -> 263;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> 262;
            case KeyEvent.KEYCODE_DPAD_UP -> 265;
            case KeyEvent.KEYCODE_DPAD_DOWN -> 264;
            case KeyEvent.KEYCODE_MOVE_HOME -> 268;
            case KeyEvent.KEYCODE_MOVE_END -> 269;
            case KeyEvent.KEYCODE_SHIFT_LEFT -> 340;
            case KeyEvent.KEYCODE_SHIFT_RIGHT -> 344;
            case KeyEvent.KEYCODE_CTRL_LEFT -> 341;
            case KeyEvent.KEYCODE_CTRL_RIGHT -> 345;
            case KeyEvent.KEYCODE_ALT_LEFT -> 342;
            case KeyEvent.KEYCODE_ALT_RIGHT -> 346;
            case KeyEvent.KEYCODE_SPACE -> 32;
            default -> event.getKeyCode() >= KeyEvent.KEYCODE_A && event.getKeyCode() <= KeyEvent.KEYCODE_Z ? 65 + event.getKeyCode() - KeyEvent.KEYCODE_A
                    : event.getKeyCode() >= KeyEvent.KEYCODE_0 && event.getKeyCode() <= KeyEvent.KEYCODE_9 ? 48 + event.getKeyCode() - KeyEvent.KEYCODE_0 : -1;
        };
        int mods = (event.isShiftPressed() ? 1 : 0) | (event.isCtrlPressed() ? 2 : 0) | (event.isAltPressed() ? 4 : 0) | (event.isMetaPressed() ? 8 : 0);
        if (key >= 0) AndroidRuntime.emit(0, key, event.getScanCode(), event.getAction() == KeyEvent.ACTION_UP ? 0 : event.getRepeatCount() > 0 ? 2 : 1, mods, 0, 0, null);
        if (event.getAction() == KeyEvent.ACTION_DOWN && !event.isCtrlPressed() && !event.isMetaPressed() && key != 257 && key != 258) {
            int character = event.getUnicodeChar();
            if (character > 0 && (character & KeyCharacterMap.COMBINING_ACCENT) == 0) AndroidRuntime.emit(1, character, 0, 0, 0, 0, 0, null);
        }
        return key >= 0;
    }

    /**
     * Synchronizes Android's software keyboard with the focused engine control.
     * @param enabled whether text editing owns engine focus
     * @param secret whether the field masks entered text
     */
    public void textFocus(boolean enabled, boolean secret) {
        if (editing == enabled && masked == secret) return;
        editing = enabled;
        masked = secret;
        InputMethodManager input = owner.getSystemService(InputMethodManager.class);
        if (enabled) { requestFocus(); input.restartInput(this); input.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT); }
        else input.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    @Override
    public boolean onCheckIsTextEditor() { return editing; }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo info) {
        if (!editing) return null;
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | (masked ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        info.imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return new AndroidTextConnection(this);
    }
}
