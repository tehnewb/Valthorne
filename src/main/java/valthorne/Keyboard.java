package valthorne;

import org.lwjgl.glfw.GLFWKeyCallback;
import valthorne.event.events.KeyEvent;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;
import valthorne.event.listeners.KeyListener;

import java.util.BitSet;

import static org.lwjgl.glfw.GLFW.*;

/**
 * <p>
 * The {@code Keyboard} class is Valthorne's global keyboard input manager. It provides
 * a centralized static API for tracking key state, modifier state, Caps Lock state,
 * and for dispatching keyboard events through the engine's event system.
 * </p>
 *
 * <p>
 * This class is intentionally non-instantiable and is designed to be used entirely
 * through static members. Internally, it installs a GLFW key callback onto the active
 * window and uses that callback to:
 * </p>
 *
 * <ul>
 *     <li>track which keys are currently down</li>
 *     <li>track current modifier flags such as Shift, Control, Alt, and Super</li>
 *     <li>track an approximate Caps Lock state</li>
 *     <li>publish {@link KeyPressEvent} and {@link KeyReleaseEvent} instances through {@link JGL}</li>
 *     <li>expose utility helpers for converting key codes into printable characters</li>
 * </ul>
 *
 * <p>
 * One of the main goals of this class is to separate low-level GLFW keyboard handling
 * from higher-level gameplay, UI, and input logic. Instead of repeatedly querying GLFW
 * directly from many different systems, the engine can rely on this class as the single
 * authority for keyboard state and keyboard event propagation.
 * </p>
 *
 * <p>
 * The callback registered by this class updates a {@link BitSet} that represents all
 * currently pressed keys. This makes it efficient to check whether a key is down at
 * any moment using {@link #isKeyDown(int)}. At the same time, modifier flags are stored
 * in a compact byte mask, allowing helpers such as {@link #isShiftDown()},
 * {@link #isCtrlDown()}, {@link #isAltDown()}, and {@link #isSuperDown()} to answer
 * state queries quickly.
 * </p>
 *
 * <p>
 * This class also exposes a large set of key constants mirroring GLFW key codes. That
 * allows engine code to reference keys through readable names such as {@link #SPACE},
 * {@link #ENTER}, {@link #LEFT_SHIFT}, {@link #F1}, and so on, without needing to use
 * raw integers throughout the codebase.
 * </p>
 *
 * <p>
 * The {@link #getKeyChar(int)} helper converts a supported key code into the printable
 * character that the current modifier state implies. It handles:
 * </p>
 *
 * <ul>
 *     <li>alphabetic keys with Shift and Caps Lock interaction</li>
 *     <li>top-row numeric keys with shifted symbol variants</li>
 *     <li>common punctuation and symbol keys</li>
 * </ul>
 *
 * <p>
 * This method is useful for lightweight text entry and UI logic where a full text-input
 * callback is not required.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Keyboard.addKeyListener(event -> {
 *     if (event instanceof KeyPressEvent && event.getKey() == Keyboard.ENTER) {
 *         System.out.println("Enter was pressed.");
 *     }
 * });
 *
 * if (Keyboard.isKeyDown(Keyboard.W)) {
 *     System.out.println("Moving forward.");
 * }
 *
 * if (Keyboard.isShiftDown()) {
 *     System.out.println("Sprint modifier active.");
 * }
 *
 * char typed = Keyboard.getKeyChar(Keyboard.A);
 * if (typed != '\0') {
 *     System.out.println("Typed character: " + typed);
 * }
 *
 * Keyboard.removeKeyListener(myListener);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete intended use of the class: listening for
 * keyboard events, polling current key state, checking modifiers, converting key
 * codes into printable characters, and unregistering listeners.
 * </p>
 *
 * @author Albert Beaupre
 * @since October 17th, 2025
 */
public final class Keyboard {

    /**
     * Unknown key constant.
     */
    public static final int UNKNOWN = -1;

    /**
     * Space key.
     */
    public static final int SPACE = 32;

    /**
     * Apostrophe key (').
     */
    public static final int APOSTROPHE = 39;

    /**
     * Comma key (,).
     */
    public static final int COMMA = 44;

    /**
     * Minus key (-).
     */
    public static final int MINUS = 45;

    /**
     * Period key (.).
     */
    public static final int PERIOD = 46;

    /**
     * Slash key (/).
     */
    public static final int SLASH = 47;

    /**
     * Number row 0 key.
     */
    public static final int KEY_0 = 48;

    /**
     * Number row 1 key.
     */
    public static final int KEY_1 = 49;

    /**
     * Number row 2 key.
     */
    public static final int KEY_2 = 50;

    /**
     * Number row 3 key.
     */
    public static final int KEY_3 = 51;

    /**
     * Number row 4 key.
     */
    public static final int KEY_4 = 52;

    /**
     * Number row 5 key.
     */
    public static final int KEY_5 = 53;

    /**
     * Number row 6 key.
     */
    public static final int KEY_6 = 54;

    /**
     * Number row 7 key.
     */
    public static final int KEY_7 = 55;

    /**
     * Number row 8 key.
     */
    public static final int KEY_8 = 56;

    /**
     * Number row 9 key.
     */
    public static final int KEY_9 = 57;

    /**
     * Semicolon key (;).
     */
    public static final int SEMICOLON = 59;

    /**
     * Equals key (=).
     */
    public static final int EQUAL = 61;

    /**
     * Letter A key.
     */
    public static final int A = 65;

    /**
     * Letter B key.
     */
    public static final int B = 66;

    /**
     * Letter C key.
     */
    public static final int C = 67;

    /**
     * Letter D key.
     */
    public static final int D = 68;

    /**
     * Letter E key.
     */
    public static final int E = 69;

    /**
     * Letter F key.
     */
    public static final int F = 70;

    /**
     * Letter G key.
     */
    public static final int G = 71;

    /**
     * Letter H key.
     */
    public static final int H = 72;

    /**
     * Letter I key.
     */
    public static final int I = 73;

    /**
     * Letter J key.
     */
    public static final int J = 74;

    /**
     * Letter K key.
     */
    public static final int K = 75;

    /**
     * Letter L key.
     */
    public static final int L = 76;

    /**
     * Letter M key.
     */
    public static final int M = 77;

    /**
     * Letter N key.
     */
    public static final int N = 78;

    /**
     * Letter O key.
     */
    public static final int O = 79;

    /**
     * Letter P key.
     */
    public static final int P = 80;

    /**
     * Letter Q key.
     */
    public static final int Q = 81;

    /**
     * Letter R key.
     */
    public static final int R = 82;

    /**
     * Letter S key.
     */
    public static final int S = 83;

    /**
     * Letter T key.
     */
    public static final int T = 84;

    /**
     * Letter U key.
     */
    public static final int U = 85;

    /**
     * Letter V key.
     */
    public static final int V = 86;

    /**
     * Letter W key.
     */
    public static final int W = 87;

    /**
     * Letter X key.
     */
    public static final int X = 88;

    /**
     * Letter Y key.
     */
    public static final int Y = 89;

    /**
     * Letter Z key.
     */
    public static final int Z = 90;

    /**
     * Left bracket key ([).
     */
    public static final int LEFT_BRACKET = 91;

    /**
     * Backslash key (\).
     */
    public static final int BACKSLASH = 92;

    /**
     * Right bracket key (]).
     */
    public static final int RIGHT_BRACKET = 93;

    /**
     * Grave accent key (`).
     */
    public static final int GRAVE_ACCENT = 96;

    /**
     * Non-US key #1.
     */
    public static final int WORLD_1 = 161;

    /**
     * Non-US key #2.
     */
    public static final int WORLD_2 = 162;

    /**
     * Escape key.
     */
    public static final int ESCAPE = 256;

    /**
     * Enter key.
     */
    public static final int ENTER = 257;

    /**
     * Tab key.
     */
    public static final int TAB = 258;

    /**
     * Backspace key.
     */
    public static final int BACKSPACE = 259;

    /**
     * Insert key.
     */
    public static final int INSERT = 260;

    /**
     * Delete key.
     */
    public static final int DELETE = 261;

    /**
     * Right arrow key.
     */
    public static final int RIGHT = 262;

    /**
     * Left arrow key.
     */
    public static final int LEFT = 263;

    /**
     * Down arrow key.
     */
    public static final int DOWN = 264;

    /**
     * Up arrow key.
     */
    public static final int UP = 265;

    /**
     * Page Up key.
     */
    public static final int PAGE_UP = 266;

    /**
     * Page Down key.
     */
    public static final int PAGE_DOWN = 267;

    /**
     * Home key.
     */
    public static final int HOME = 268;

    /**
     * End key.
     */
    public static final int END = 269;

    /**
     * Caps Lock key.
     */
    public static final int CAPS_LOCK = 280;

    /**
     * Scroll Lock key.
     */
    public static final int SCROLL_LOCK = 281;

    /**
     * Num Lock key.
     */
    public static final int NUM_LOCK = 282;

    /**
     * Print Screen key.
     */
    public static final int PRINT_SCREEN = 283;

    /**
     * Pause key.
     */
    public static final int PAUSE = 284;

    /**
     * Function key F1.
     */
    public static final int F1 = 290;

    /**
     * Function key F2.
     */
    public static final int F2 = 291;

    /**
     * Function key F3.
     */
    public static final int F3 = 292;

    /**
     * Function key F4.
     */
    public static final int F4 = 293;

    /**
     * Function key F5.
     */
    public static final int F5 = 294;

    /**
     * Function key F6.
     */
    public static final int F6 = 295;

    /**
     * Function key F7.
     */
    public static final int F7 = 296;

    /**
     * Function key F8.
     */
    public static final int F8 = 297;

    /**
     * Function key F9.
     */
    public static final int F9 = 298;

    /**
     * Function key F10.
     */
    public static final int F10 = 299;

    /**
     * Function key F11.
     */
    public static final int F11 = 300;

    /**
     * Function key F12.
     */
    public static final int F12 = 301;

    /**
     * Function key F13.
     */
    public static final int F13 = 302;

    /**
     * Function key F14.
     */
    public static final int F14 = 303;

    /**
     * Function key F15.
     */
    public static final int F15 = 304;

    /**
     * Function key F16.
     */
    public static final int F16 = 305;

    /**
     * Function key F17.
     */
    public static final int F17 = 306;

    /**
     * Function key F18.
     */
    public static final int F18 = 307;

    /**
     * Function key F19.
     */
    public static final int F19 = 308;

    /**
     * Function key F20.
     */
    public static final int F20 = 309;

    /**
     * Function key F21.
     */
    public static final int F21 = 310;

    /**
     * Function key F22.
     */
    public static final int F22 = 311;

    /**
     * Function key F23.
     */
    public static final int F23 = 312;

    /**
     * Function key F24.
     */
    public static final int F24 = 313;

    /**
     * Function key F25.
     */
    public static final int F25 = 314;

    /**
     * Keypad 0 key.
     */
    public static final int KP_0 = 320;

    /**
     * Keypad 1 key.
     */
    public static final int KP_1 = 321;

    /**
     * Keypad 2 key.
     */
    public static final int KP_2 = 322;

    /**
     * Keypad 3 key.
     */
    public static final int KP_3 = 323;

    /**
     * Keypad 4 key.
     */
    public static final int KP_4 = 324;

    /**
     * Keypad 5 key.
     */
    public static final int KP_5 = 325;

    /**
     * Keypad 6 key.
     */
    public static final int KP_6 = 326;

    /**
     * Keypad 7 key.
     */
    public static final int KP_7 = 327;

    /**
     * Keypad 8 key.
     */
    public static final int KP_8 = 328;

    /**
     * Keypad 9 key.
     */
    public static final int KP_9 = 329;

    /**
     * Keypad decimal key.
     */
    public static final int KP_DECIMAL = 330;

    /**
     * Keypad divide key.
     */
    public static final int KP_DIVIDE = 331;

    /**
     * Keypad multiply key.
     */
    public static final int KP_MULTIPLY = 332;

    /**
     * Keypad subtract key.
     */
    public static final int KP_SUBTRACT = 333;

    /**
     * Keypad add key.
     */
    public static final int KP_ADD = 334;

    /**
     * Keypad enter key.
     */
    public static final int KP_ENTER = 335;

    /**
     * Keypad equals key.
     */
    public static final int KP_EQUAL = 336;

    /**
     * Left Shift key.
     */
    public static final int LEFT_SHIFT = 340;

    /**
     * Left Control key.
     */
    public static final int LEFT_CONTROL = 341;

    /**
     * Left Alt key.
     */
    public static final int LEFT_ALT = 342;

    /**
     * Left Super key.
     */
    public static final int LEFT_SUPER = 343;

    /**
     * Right Shift key.
     */
    public static final int RIGHT_SHIFT = 344;

    /**
     * Right Control key.
     */
    public static final int RIGHT_CONTROL = 345;

    /**
     * Right Alt key.
     */
    public static final int RIGHT_ALT = 346;

    /**
     * Right Super key.
     */
    public static final int RIGHT_SUPER = 347;

    /**
     * Menu key.
     */
    public static final int MENU = 348;

    /**
     * Reusable key press event instance updated and republished on key press and repeat.
     */
    private static final KeyPressEvent pressEvent = new KeyPressEvent(0, 0);

    /**
     * Reusable key release event instance updated and republished on key release.
     */
    private static final KeyReleaseEvent releaseEvent = new KeyReleaseEvent(0, 0);

    /**
     * BitSet tracking which GLFW key codes are currently pressed.
     */
    private static BitSet keyDown = new BitSet(GLFW_KEY_LAST + 1);

    private static GLFWKeyCallback keyCallback; // GLFW callback instance installed on the active window
    private static short currentKey; // Most recently pressed key code, or -1 when no active key is tracked
    private static byte modifierState; // Current GLFW modifier mask containing Shift, Control, Alt, and Super state
    private static boolean capsLockOn; // Cached Caps Lock state tracked through toolkit initialization and key toggles

    /**
     * <p>
     * Private constructor to prevent instantiation.
     * </p>
     *
     * <p>
     * {@code Keyboard} is intended to function purely as a static global input service.
     * </p>
     */
    private Keyboard() {
    }

    /**
     * <p>
     * Initializes keyboard handling by installing the GLFW key callback on the active window.
     * </p>
     *
     * <p>
     * During initialization, the method first attempts to query the operating system's
     * current Caps Lock state using AWT. If that fails for any reason, Caps Lock tracking
     * falls back to {@code false} until toggled by keyboard input events.
     * </p>
     *
     * <p>
     * The installed callback performs the following responsibilities:
     * </p>
     *
     * <ul>
     *     <li>rejects invalid key codes outside GLFW's supported key range</li>
     *     <li>updates the current key state and modifier mask</li>
     *     <li>updates the {@link #keyDown} BitSet for press and release events</li>
     *     <li>toggles the cached Caps Lock state when the Caps Lock key is pressed</li>
     *     <li>publishes reusable {@link KeyPressEvent} and {@link KeyReleaseEvent} instances through {@link JGL}</li>
     * </ul>
     *
     * <p>
     * This method is package-private because it is intended to be called by engine
     * initialization code rather than by arbitrary user code.
     * </p>
     */
    static void init() {
        try {
            capsLockOn = java.awt.Toolkit.getDefaultToolkit().getLockingKeyState(java.awt.event.KeyEvent.VK_CAPS_LOCK);
        } catch (Throwable ignored) {
            capsLockOn = false;
        }

        keyCallback = glfwSetKeyCallback(Window.getAddress(), (win, key, scancode, action, mods) -> {
            if (key < 0 || key > GLFW_KEY_LAST) return;

            KeyEvent event = null;

            switch (action) {
                case GLFW_PRESS, GLFW_REPEAT -> {
                    currentKey = (short) key;
                    modifierState = (byte) mods;
                    event = pressEvent;
                    keyDown.set(key, true);

                    if (key == CAPS_LOCK) capsLockOn = !capsLockOn;
                }
                case GLFW_RELEASE -> {
                    modifierState = (byte) mods;
                    event = releaseEvent;
                    currentKey = -1;
                    keyDown.clear(key);
                }
            }

            if (event != null) {
                event.setKey(key);
                event.setModifiers(modifierState);
                JGL.publish(event);
            }
        });
    }

    /**
     * <p>
     * Registers a {@link KeyListener} so it can receive keyboard events.
     * </p>
     *
     * <p>
     * The listener is subscribed through {@link JGL} for all {@link KeyEvent} instances,
     * which includes both {@link KeyPressEvent} and {@link KeyReleaseEvent}.
     * </p>
     *
     * @param listener the key listener to add
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public static void addKeyListener(KeyListener listener) {
        if (listener == null) throw new NullPointerException("A null KeyListener cannot be added");
        JGL.subscribe(KeyEvent.class, listener);
    }

    /**
     * <p>
     * Removes a previously registered {@link KeyListener}.
     * </p>
     *
     * <p>
     * After removal, the listener will no longer receive keyboard events published
     * through {@link JGL} for the {@link KeyEvent} type.
     * </p>
     *
     * @param listener the key listener to remove
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    public static void removeKeyListener(KeyListener listener) {
        if (listener == null) throw new NullPointerException("A null KeyListener cannot be removed");
        JGL.unsubscribe(KeyEvent.class, listener);
    }

    /**
     * <p>
     * Disposes the installed GLFW key callback.
     * </p>
     *
     * <p>
     * This releases the native resources associated with the callback and should be
     * called during engine shutdown. If no callback has been installed, the method
     * safely does nothing.
     * </p>
     */
    static void dispose() {
        if (keyCallback != null) keyCallback.free();
    }

    /**
     * <p>
     * Returns whether the given GLFW key code is currently pressed.
     * </p>
     *
     * <p>
     * The key state is resolved from the internal {@link BitSet} that is updated by
     * the GLFW callback. Invalid key codes outside the GLFW key range always return
     * {@code false}.
     * </p>
     *
     * @param key the GLFW key code to test
     * @return {@code true} if the key is currently down, otherwise {@code false}
     */
    public static boolean isKeyDown(int key) {
        return key >= 0 && key <= GLFW_KEY_LAST && keyDown.get(key);
    }

    /**
     * <p>
     * Returns whether either Shift key is currently active.
     * </p>
     *
     * <p>
     * This is determined from the current GLFW modifier bit mask.
     * </p>
     *
     * @return {@code true} if Shift is currently down
     */
    public static boolean isShiftDown() {
        return (modifierState & GLFW_MOD_SHIFT) != 0;
    }

    /**
     * <p>
     * Returns whether either Control key is currently active.
     * </p>
     *
     * <p>
     * This is determined from the current GLFW modifier bit mask.
     * </p>
     *
     * @return {@code true} if Control is currently down
     */
    public static boolean isCtrlDown() {
        return (modifierState & GLFW_MOD_CONTROL) != 0;
    }

    /**
     * <p>
     * Returns whether either Alt key is currently active.
     * </p>
     *
     * <p>
     * This is determined from the current GLFW modifier bit mask.
     * </p>
     *
     * @return {@code true} if Alt is currently down
     */
    public static boolean isAltDown() {
        return (modifierState & GLFW_MOD_ALT) != 0;
    }

    /**
     * <p>
     * Returns whether either Super key is currently active.
     * </p>
     *
     * <p>
     * This is determined from the current GLFW modifier bit mask.
     * The Super key usually corresponds to the Windows key on Windows or the
     * Command key on macOS.
     * </p>
     *
     * @return {@code true} if Super is currently down
     */
    public static boolean isSuperDown() {
        return (modifierState & GLFW_MOD_SUPER) != 0;
    }

    /**
     * <p>
     * Returns whether Caps Lock is currently considered enabled.
     * </p>
     *
     * <p>
     * This value is initialized from the operating system when possible and then kept
     * in sync by toggling the cached state whenever the Caps Lock key is pressed.
     * </p>
     *
     * @return {@code true} if Caps Lock is currently on
     */
    public static boolean isCapsLockOn() {
        return capsLockOn;
    }

    /**
     * <p>
     * Converts a supported GLFW key code into its printable character representation.
     * </p>
     *
     * <p>
     * The conversion takes the current Shift and Caps Lock state into account for
     * alphabetic keys, applies shifted symbol mappings for the top number row,
     * and resolves common punctuation keys. If the supplied key does not correspond
     * to a printable character, the null character {@code '\0'} is returned.
     * </p>
     *
     * <p>
     * This method is useful for lightweight text input scenarios where the engine
     * wants a quick key-to-character conversion without using a full character
     * callback pipeline.
     * </p>
     *
     * @param key the GLFW key code to convert
     * @return the printable character for the given key, or {@code '\0'} if none exists
     */
    public static char getKeyChar(int key) {
        boolean shift = isShiftDown();
        boolean caps = isCapsLockOn();

        if (key >= A && key <= Z) {
            char base = (char) ('a' + (key - A));
            return (shift ^ caps) ? Character.toUpperCase(base) : base;
        }

        if (key >= KEY_0 && key <= KEY_9) {
            if (!shift) {
                return (char) key;
            }

            return switch (key) {
                case KEY_1 -> '!';
                case KEY_2 -> '@';
                case KEY_3 -> '#';
                case KEY_4 -> '$';
                case KEY_5 -> '%';
                case KEY_6 -> '^';
                case KEY_7 -> '&';
                case KEY_8 -> '*';
                case KEY_9 -> '(';
                case KEY_0 -> ')';
                default -> '\0';
            };
        }

        return switch (key) {
            case SPACE -> ' ';

            case MINUS -> shift ? '_' : '-';
            case EQUAL -> shift ? '+' : '=';

            case LEFT_BRACKET -> shift ? '{' : '[';
            case RIGHT_BRACKET -> shift ? '}' : ']';

            case BACKSLASH -> shift ? '|' : '\\';

            case SEMICOLON -> shift ? ':' : ';';
            case APOSTROPHE -> shift ? '"' : '\'';

            case COMMA -> shift ? '<' : ',';
            case PERIOD -> shift ? '>' : '.';
            case SLASH -> shift ? '?' : '/';

            case GRAVE_ACCENT -> shift ? '~' : '`';

            default -> '\0';
        };
    }

}