package valthorne;

import org.lwjgl.glfw.GLFWKeyCallback;
import valthorne.event.events.KeyEvent;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;
import valthorne.event.listeners.KeyListener;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The {@code Keyboard} class provides static utilities for managing keyboard input
 * using the GLFW library. It tracks the current key state, modifier state, and
 * dispatches key events through the JGL event system.
 *
 * <p>This class is non-instantiable and acts as a global keyboard input manager.
 * It stores a GLFW key callback and exposes helper methods to query whether
 * specific modifier keys or key codes are currently active.</p>
 *
 * <p>Usage typically involves:</p>
 * <ul>
 *     <li>Calling {@link #init()} during window initialization to register the GLFW callback.</li>
 *     <li>Using {@link #dispose()} when shutting down to free the callback.</li>
 *     <li>Polling {@link #isKeyDown(int)} or modifier checks during gameplay or UI input.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 16th, 2025
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
    // 0–9 digit keys
    public static final int KEY_0 = 48;
    public static final int KEY_1 = 49;
    public static final int KEY_2 = 50;
    public static final int KEY_3 = 51;
    public static final int KEY_4 = 52;
    public static final int KEY_5 = 53;
    public static final int KEY_6 = 54;
    public static final int KEY_7 = 55;
    public static final int KEY_8 = 56;
    public static final int KEY_9 = 57;
    /**
     * Semicolon key (;).
     */
    public static final int SEMICOLON = 59;
    /**
     * Equals key (=).
     */
    public static final int EQUAL = 61;
    // A–Z letter keys
    public static final int A = 65;
    public static final int B = 66;
    public static final int C = 67;
    public static final int D = 68;
    public static final int E = 69;
    public static final int F = 70;
    public static final int G = 71;
    public static final int H = 72;
    public static final int I = 73;
    public static final int J = 74;
    public static final int K = 75;
    public static final int L = 76;
    public static final int M = 77;
    public static final int N = 78;
    public static final int O = 79;
    public static final int P = 80;
    public static final int Q = 81;
    public static final int R = 82;
    public static final int S = 83;
    public static final int T = 84;
    public static final int U = 85;
    public static final int V = 86;
    public static final int W = 87;
    public static final int X = 88;
    public static final int Y = 89;
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
    // Function / control keys
    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int INSERT = 260;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;
    public static final int CAPS_LOCK = 280;
    public static final int SCROLL_LOCK = 281;
    public static final int NUM_LOCK = 282;
    public static final int PRINT_SCREEN = 283;
    public static final int PAUSE = 284;
    // Function keys F1–F25
    public static final int F1 = 290;
    public static final int F2 = 291;
    public static final int F3 = 292;
    public static final int F4 = 293;
    public static final int F5 = 294;
    public static final int F6 = 295;
    public static final int F7 = 296;
    public static final int F8 = 297;
    public static final int F9 = 298;
    public static final int F10 = 299;
    public static final int F11 = 300;
    public static final int F12 = 301;
    public static final int F13 = 302;
    public static final int F14 = 303;
    public static final int F15 = 304;
    public static final int F16 = 305;
    public static final int F17 = 306;
    public static final int F18 = 307;
    public static final int F19 = 308;
    public static final int F20 = 309;
    public static final int F21 = 310;
    public static final int F22 = 311;
    public static final int F23 = 312;
    public static final int F24 = 313;
    public static final int F25 = 314;
    // Keypad keys
    public static final int KP_0 = 320;
    public static final int KP_1 = 321;
    public static final int KP_2 = 322;
    public static final int KP_3 = 323;
    public static final int KP_4 = 324;
    public static final int KP_5 = 325;
    public static final int KP_6 = 326;
    public static final int KP_7 = 327;
    public static final int KP_8 = 328;
    public static final int KP_9 = 329;
    public static final int KP_DECIMAL = 330;
    public static final int KP_DIVIDE = 331;
    public static final int KP_MULTIPLY = 332;
    public static final int KP_SUBTRACT = 333;
    public static final int KP_ADD = 334;
    public static final int KP_ENTER = 335;
    public static final int KP_EQUAL = 336;
    // Modifier keys
    public static final int LEFT_SHIFT = 340;
    public static final int LEFT_CONTROL = 341;
    public static final int LEFT_ALT = 342;
    public static final int LEFT_SUPER = 343;
    public static final int RIGHT_SHIFT = 344;
    public static final int RIGHT_CONTROL = 345;
    public static final int RIGHT_ALT = 346;
    public static final int RIGHT_SUPER = 347;
    public static final int MENU = 348;
    private static final KeyPressEvent pressEvent = new KeyPressEvent(0, 0);
    private static final KeyReleaseEvent releaseEvent = new KeyReleaseEvent(0, 0);
    /**
     * The GLFW key callback responsible for handling key press, release, and repeat
     * events. GLFW invokes this callback whenever a keyboard action occurs in the
     * active window. Inside the callback, modifier keys are updated and JGL events
     * are published accordingly.
     *
     * <p>This callback is installed in {@link #init()} and should be freed using
     * {@link #dispose()} to avoid memory leaks.</p>
     */
    private static GLFWKeyCallback keyCallback;
    /**
     * Stores the most recently pressed key. If no key is pressed, this value becomes -1.
     * This value is updated whenever GLFW detects a key press or repeat event.
     */
    private static short currentKey;
    /**
     * Tracks the current state of modifier keys (Shift, Control, Alt, Super).
     * This is a bitwise mask composed of GLFW modifier constants.
     */
    private static byte modifierState;
    /**
     * Private constructor to prevent instantiation. This class is designed to be
     * accessed statically.
     */
    private Keyboard() {
        // Inaccessible
    }

    /**
     * Initializes keyboard input by registering a GLFW key callback on the active window.
     * This callback listens for key presses, releases, and repeat actions.
     *
     * <p>Inside the callback:</p>
     * <ul>
     *     <li>Updates the {@code currentKey}</li>
     *     <li>Updates the {@code modifierState}</li>
     *     <li>Publishes {@link KeyPressEvent} or {@link KeyReleaseEvent} accordingly</li>
     * </ul>
     */
    static void init() {
        keyCallback = glfwSetKeyCallback(Window.getAddress(), (win, key, scancode, action, mods) -> {
            if (key < 0 || key > GLFW_KEY_LAST)
                return;

            currentKey = (short) key;
            modifierState = (byte) mods;

            KeyEvent event = null;

            switch (action) {
                case GLFW_PRESS, GLFW_REPEAT -> event = pressEvent;
                case GLFW_RELEASE -> {
                    event = releaseEvent;
                    currentKey = -1;
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
     * Registers a {@code KeyListener} to receive keyboard event notifications.
     * The listener will be invoked for specific key events, such as key presses
     * and key releases.
     * <p>
     * This method ensures that a non-null {@code KeyListener} is provided. If
     * a {@code null} listener is passed, a {@code NullPointerException} is thrown.
     *
     * @param listener the {@code KeyListener} to add; must not be {@code null}
     * @throws NullPointerException if the provided {@code listener} is {@code null}
     */
    public static void addKeyListener(KeyListener listener) {
        if (listener == null)
            throw new NullPointerException("A null KeyListener cannot be added");
        JGL.subscribe(KeyEvent.class, listener);
    }

    /**
     * Removes a previously registered {@code KeyListener} from receiving keyboard
     * event notifications. The listener will no longer receive key press and key
     * release events after removal.
     *
     * @param listener the {@code KeyListener} to be removed; must not be {@code null}
     * @throws NullPointerException if the provided {@code listener} is {@code null}
     */
    public static void removeKeyListener(KeyListener listener) {
        if (listener == null)
            throw new NullPointerException("A null KeyListener cannot be removed");
        JGL.unsubscribe(KeyEvent.class, listener);
    }

    /**
     * Frees the GLFW key callback, releasing native resources. This should be
     * called when shutting down the application to avoid memory leaks.
     */
    static void dispose() {
        if (keyCallback != null)
            keyCallback.free();
    }

    /**
     * Checks whether the given key is currently pressed.
     *
     * @param key the GLFW key code
     * @return {@code true} if the key is the current active key, otherwise {@code false}
     */
    public static boolean isKeyDown(int key) {
        return key == currentKey;
    }

    /**
     * Determines whether either Shift key is currently held down.
     *
     * @return {@code true} if Shift is pressed
     */
    public static boolean isShiftDown() {
        return (modifierState & GLFW_MOD_SHIFT) != 0;
    }

    /**
     * Determines whether either Control key is currently held down.
     *
     * @return {@code true} if Control is pressed
     */
    public static boolean isCtrlDown() {
        return (modifierState & GLFW_MOD_CONTROL) != 0;
    }

    /**
     * Determines whether either Alt key is currently held down.
     *
     * @return {@code true} if Alt is pressed
     */
    public static boolean isAltDown() {
        return (modifierState & GLFW_MOD_ALT) != 0;
    }

    /**
     * Determines whether either Super key (Windows key / Command key) is held down.
     *
     * @return {@code true} if Super is pressed
     */
    public static boolean isSuperDown() {
        return (modifierState & GLFW_MOD_SUPER) != 0;
    }

    /**
     * Converts a GLFW key ID to the corresponding printable character.
     * Returns '\0' if the key does not produce a character.
     *
     * @param key the GLFW key code
     * @return the character for this key, or '\0' if not printable
     */
    public static char getKeyChar(int key) {
        boolean shift = (modifierState & GLFW_MOD_SHIFT) != 0;

        // Letters A–Z
        if (key >= A && key <= Z) {
            char base = (char) ('a' + (key - A));
            return shift ? Character.toUpperCase(base) : base;
        }

        // Numbers 0–9 (top row)
        if (key >= KEY_0 && key <= KEY_9) {
            if (!shift) {
                return (char) key; // '0'–'9'
            }

            // Shifted number row symbols
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

        // Punctuation & shift variants
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

            default -> '\0'; // Non-printable keys return null character
        };
    }

}
