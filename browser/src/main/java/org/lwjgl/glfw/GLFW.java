package org.lwjgl.glfw;

import java.nio.IntBuffer;
import java.nio.DoubleBuffer;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import valthorne.browser.BrowserRuntime;

/**
 * Canvas windowing and input bindings for shared engine code. Window identifiers
 * are browser-owned tokens, never native pointers. Event delivery resumes only
 * the suspended application coroutine; DOM listeners never reenter game code.
 */
public final class GLFW {
    /**
     * Engine version major value.
     */
    public static final int GLFW_VERSION_MAJOR = 3;
    /**
     * Engine version minor value.
     */
    public static final int GLFW_VERSION_MINOR = 5;
    /**
     * Engine version revision value.
     */
    public static final int GLFW_VERSION_REVISION = 1;
    /**
     * Engine true value.
     */
    public static final int GLFW_TRUE = 1;
    /**
     * Engine false value.
     */
    public static final int GLFW_FALSE = 0;
    /**
     * Engine release value.
     */
    public static final int GLFW_RELEASE = 0;
    /**
     * Engine press value.
     */
    public static final int GLFW_PRESS = 1;
    /**
     * Engine repeat value.
     */
    public static final int GLFW_REPEAT = 2;
    /**
     * Engine hat centered value.
     */
    public static final int GLFW_HAT_CENTERED = 0;
    /**
     * Engine hat up value.
     */
    public static final int GLFW_HAT_UP = 1;
    /**
     * Engine hat right value.
     */
    public static final int GLFW_HAT_RIGHT = 2;
    /**
     * Engine hat down value.
     */
    public static final int GLFW_HAT_DOWN = 4;
    /**
     * Engine hat left value.
     */
    public static final int GLFW_HAT_LEFT = 8;
    /**
     * Engine hat right up value.
     */
    public static final int GLFW_HAT_RIGHT_UP = (GLFW_HAT_RIGHT | GLFW_HAT_UP);
    /**
     * Engine hat right down value.
     */
    public static final int GLFW_HAT_RIGHT_DOWN = (GLFW_HAT_RIGHT | GLFW_HAT_DOWN);
    /**
     * Engine hat left up value.
     */
    public static final int GLFW_HAT_LEFT_UP = (GLFW_HAT_LEFT  | GLFW_HAT_UP);
    /**
     * Engine hat left down value.
     */
    public static final int GLFW_HAT_LEFT_DOWN = (GLFW_HAT_LEFT  | GLFW_HAT_DOWN);
    /**
     * Engine key unknown value.
     */
    public static final int GLFW_KEY_UNKNOWN = -1;
    /**
     * Engine key space value.
     */
    public static final int GLFW_KEY_SPACE = 32;
    /**
     * Engine key apostrophe value.
     */
    public static final int GLFW_KEY_APOSTROPHE = 39;
    /**
     * Engine key comma value.
     */
    public static final int GLFW_KEY_COMMA = 44;
    /**
     * Engine key minus value.
     */
    public static final int GLFW_KEY_MINUS = 45;
    /**
     * Engine key period value.
     */
    public static final int GLFW_KEY_PERIOD = 46;
    /**
     * Engine key slash value.
     */
    public static final int GLFW_KEY_SLASH = 47;
    /**
     * Engine key 0 value.
     */
    public static final int GLFW_KEY_0 = 48;
    /**
     * Engine key 1 value.
     */
    public static final int GLFW_KEY_1 = 49;
    /**
     * Engine key 2 value.
     */
    public static final int GLFW_KEY_2 = 50;
    /**
     * Engine key 3 value.
     */
    public static final int GLFW_KEY_3 = 51;
    /**
     * Engine key 4 value.
     */
    public static final int GLFW_KEY_4 = 52;
    /**
     * Engine key 5 value.
     */
    public static final int GLFW_KEY_5 = 53;
    /**
     * Engine key 6 value.
     */
    public static final int GLFW_KEY_6 = 54;
    /**
     * Engine key 7 value.
     */
    public static final int GLFW_KEY_7 = 55;
    /**
     * Engine key 8 value.
     */
    public static final int GLFW_KEY_8 = 56;
    /**
     * Engine key 9 value.
     */
    public static final int GLFW_KEY_9 = 57;
    /**
     * Engine key semicolon value.
     */
    public static final int GLFW_KEY_SEMICOLON = 59;
    /**
     * Engine key equal value.
     */
    public static final int GLFW_KEY_EQUAL = 61;
    /**
     * Engine key a value.
     */
    public static final int GLFW_KEY_A = 65;
    /**
     * Engine key b value.
     */
    public static final int GLFW_KEY_B = 66;
    /**
     * Engine key c value.
     */
    public static final int GLFW_KEY_C = 67;
    /**
     * Engine key d value.
     */
    public static final int GLFW_KEY_D = 68;
    /**
     * Engine key e value.
     */
    public static final int GLFW_KEY_E = 69;
    /**
     * Engine key f value.
     */
    public static final int GLFW_KEY_F = 70;
    /**
     * Engine key g value.
     */
    public static final int GLFW_KEY_G = 71;
    /**
     * Engine key h value.
     */
    public static final int GLFW_KEY_H = 72;
    /**
     * Engine key i value.
     */
    public static final int GLFW_KEY_I = 73;
    /**
     * Engine key j value.
     */
    public static final int GLFW_KEY_J = 74;
    /**
     * Engine key k value.
     */
    public static final int GLFW_KEY_K = 75;
    /**
     * Engine key l value.
     */
    public static final int GLFW_KEY_L = 76;
    /**
     * Engine key m value.
     */
    public static final int GLFW_KEY_M = 77;
    /**
     * Engine key n value.
     */
    public static final int GLFW_KEY_N = 78;
    /**
     * Engine key o value.
     */
    public static final int GLFW_KEY_O = 79;
    /**
     * Engine key p value.
     */
    public static final int GLFW_KEY_P = 80;
    /**
     * Engine key q value.
     */
    public static final int GLFW_KEY_Q = 81;
    /**
     * Engine key r value.
     */
    public static final int GLFW_KEY_R = 82;
    /**
     * Engine key s value.
     */
    public static final int GLFW_KEY_S = 83;
    /**
     * Engine key t value.
     */
    public static final int GLFW_KEY_T = 84;
    /**
     * Engine key u value.
     */
    public static final int GLFW_KEY_U = 85;
    /**
     * Engine key v value.
     */
    public static final int GLFW_KEY_V = 86;
    /**
     * Engine key w value.
     */
    public static final int GLFW_KEY_W = 87;
    /**
     * Engine key x value.
     */
    public static final int GLFW_KEY_X = 88;
    /**
     * Engine key y value.
     */
    public static final int GLFW_KEY_Y = 89;
    /**
     * Engine key z value.
     */
    public static final int GLFW_KEY_Z = 90;
    /**
     * Engine key left bracket value.
     */
    public static final int GLFW_KEY_LEFT_BRACKET = 91;
    /**
     * Engine key backslash value.
     */
    public static final int GLFW_KEY_BACKSLASH = 92;
    /**
     * Engine key right bracket value.
     */
    public static final int GLFW_KEY_RIGHT_BRACKET = 93;
    /**
     * Engine key grave accent value.
     */
    public static final int GLFW_KEY_GRAVE_ACCENT = 96;
    /**
     * Engine key world 1 value.
     */
    public static final int GLFW_KEY_WORLD_1 = 161;
    /**
     * Engine key world 2 value.
     */
    public static final int GLFW_KEY_WORLD_2 = 162;
    /**
     * Engine key escape value.
     */
    public static final int GLFW_KEY_ESCAPE = 256;
    /**
     * Engine key enter value.
     */
    public static final int GLFW_KEY_ENTER = 257;
    /**
     * Engine key tab value.
     */
    public static final int GLFW_KEY_TAB = 258;
    /**
     * Engine key backspace value.
     */
    public static final int GLFW_KEY_BACKSPACE = 259;
    /**
     * Engine key insert value.
     */
    public static final int GLFW_KEY_INSERT = 260;
    /**
     * Engine key delete value.
     */
    public static final int GLFW_KEY_DELETE = 261;
    /**
     * Engine key right value.
     */
    public static final int GLFW_KEY_RIGHT = 262;
    /**
     * Engine key left value.
     */
    public static final int GLFW_KEY_LEFT = 263;
    /**
     * Engine key down value.
     */
    public static final int GLFW_KEY_DOWN = 264;
    /**
     * Engine key up value.
     */
    public static final int GLFW_KEY_UP = 265;
    /**
     * Engine key page up value.
     */
    public static final int GLFW_KEY_PAGE_UP = 266;
    /**
     * Engine key page down value.
     */
    public static final int GLFW_KEY_PAGE_DOWN = 267;
    /**
     * Engine key home value.
     */
    public static final int GLFW_KEY_HOME = 268;
    /**
     * Engine key end value.
     */
    public static final int GLFW_KEY_END = 269;
    /**
     * Engine key caps lock value.
     */
    public static final int GLFW_KEY_CAPS_LOCK = 280;
    /**
     * Engine key scroll lock value.
     */
    public static final int GLFW_KEY_SCROLL_LOCK = 281;
    /**
     * Engine key num lock value.
     */
    public static final int GLFW_KEY_NUM_LOCK = 282;
    /**
     * Engine key print screen value.
     */
    public static final int GLFW_KEY_PRINT_SCREEN = 283;
    /**
     * Engine key pause value.
     */
    public static final int GLFW_KEY_PAUSE = 284;
    /**
     * Engine key f1 value.
     */
    public static final int GLFW_KEY_F1 = 290;
    /**
     * Engine key f2 value.
     */
    public static final int GLFW_KEY_F2 = 291;
    /**
     * Engine key f3 value.
     */
    public static final int GLFW_KEY_F3 = 292;
    /**
     * Engine key f4 value.
     */
    public static final int GLFW_KEY_F4 = 293;
    /**
     * Engine key f5 value.
     */
    public static final int GLFW_KEY_F5 = 294;
    /**
     * Engine key f6 value.
     */
    public static final int GLFW_KEY_F6 = 295;
    /**
     * Engine key f7 value.
     */
    public static final int GLFW_KEY_F7 = 296;
    /**
     * Engine key f8 value.
     */
    public static final int GLFW_KEY_F8 = 297;
    /**
     * Engine key f9 value.
     */
    public static final int GLFW_KEY_F9 = 298;
    /**
     * Engine key f10 value.
     */
    public static final int GLFW_KEY_F10 = 299;
    /**
     * Engine key f11 value.
     */
    public static final int GLFW_KEY_F11 = 300;
    /**
     * Engine key f12 value.
     */
    public static final int GLFW_KEY_F12 = 301;
    /**
     * Engine key f13 value.
     */
    public static final int GLFW_KEY_F13 = 302;
    /**
     * Engine key f14 value.
     */
    public static final int GLFW_KEY_F14 = 303;
    /**
     * Engine key f15 value.
     */
    public static final int GLFW_KEY_F15 = 304;
    /**
     * Engine key f16 value.
     */
    public static final int GLFW_KEY_F16 = 305;
    /**
     * Engine key f17 value.
     */
    public static final int GLFW_KEY_F17 = 306;
    /**
     * Engine key f18 value.
     */
    public static final int GLFW_KEY_F18 = 307;
    /**
     * Engine key f19 value.
     */
    public static final int GLFW_KEY_F19 = 308;
    /**
     * Engine key f20 value.
     */
    public static final int GLFW_KEY_F20 = 309;
    /**
     * Engine key f21 value.
     */
    public static final int GLFW_KEY_F21 = 310;
    /**
     * Engine key f22 value.
     */
    public static final int GLFW_KEY_F22 = 311;
    /**
     * Engine key f23 value.
     */
    public static final int GLFW_KEY_F23 = 312;
    /**
     * Engine key f24 value.
     */
    public static final int GLFW_KEY_F24 = 313;
    /**
     * Engine key f25 value.
     */
    public static final int GLFW_KEY_F25 = 314;
    /**
     * Engine key kp 0 value.
     */
    public static final int GLFW_KEY_KP_0 = 320;
    /**
     * Engine key kp 1 value.
     */
    public static final int GLFW_KEY_KP_1 = 321;
    /**
     * Engine key kp 2 value.
     */
    public static final int GLFW_KEY_KP_2 = 322;
    /**
     * Engine key kp 3 value.
     */
    public static final int GLFW_KEY_KP_3 = 323;
    /**
     * Engine key kp 4 value.
     */
    public static final int GLFW_KEY_KP_4 = 324;
    /**
     * Engine key kp 5 value.
     */
    public static final int GLFW_KEY_KP_5 = 325;
    /**
     * Engine key kp 6 value.
     */
    public static final int GLFW_KEY_KP_6 = 326;
    /**
     * Engine key kp 7 value.
     */
    public static final int GLFW_KEY_KP_7 = 327;
    /**
     * Engine key kp 8 value.
     */
    public static final int GLFW_KEY_KP_8 = 328;
    /**
     * Engine key kp 9 value.
     */
    public static final int GLFW_KEY_KP_9 = 329;
    /**
     * Engine key kp decimal value.
     */
    public static final int GLFW_KEY_KP_DECIMAL = 330;
    /**
     * Engine key kp divide value.
     */
    public static final int GLFW_KEY_KP_DIVIDE = 331;
    /**
     * Engine key kp multiply value.
     */
    public static final int GLFW_KEY_KP_MULTIPLY = 332;
    /**
     * Engine key kp subtract value.
     */
    public static final int GLFW_KEY_KP_SUBTRACT = 333;
    /**
     * Engine key kp add value.
     */
    public static final int GLFW_KEY_KP_ADD = 334;
    /**
     * Engine key kp enter value.
     */
    public static final int GLFW_KEY_KP_ENTER = 335;
    /**
     * Engine key kp equal value.
     */
    public static final int GLFW_KEY_KP_EQUAL = 336;
    /**
     * Engine key left shift value.
     */
    public static final int GLFW_KEY_LEFT_SHIFT = 340;
    /**
     * Engine key left control value.
     */
    public static final int GLFW_KEY_LEFT_CONTROL = 341;
    /**
     * Engine key left alt value.
     */
    public static final int GLFW_KEY_LEFT_ALT = 342;
    /**
     * Engine key left super value.
     */
    public static final int GLFW_KEY_LEFT_SUPER = 343;
    /**
     * Engine key right shift value.
     */
    public static final int GLFW_KEY_RIGHT_SHIFT = 344;
    /**
     * Engine key right control value.
     */
    public static final int GLFW_KEY_RIGHT_CONTROL = 345;
    /**
     * Engine key right alt value.
     */
    public static final int GLFW_KEY_RIGHT_ALT = 346;
    /**
     * Engine key right super value.
     */
    public static final int GLFW_KEY_RIGHT_SUPER = 347;
    /**
     * Engine key menu value.
     */
    public static final int GLFW_KEY_MENU = 348;
    /**
     * Engine key last value.
     */
    public static final int GLFW_KEY_LAST = GLFW_KEY_MENU;
    /**
     * Engine mod shift value.
     */
    public static final int GLFW_MOD_SHIFT = 0x1;
    /**
     * Engine mod control value.
     */
    public static final int GLFW_MOD_CONTROL = 0x2;
    /**
     * Engine mod alt value.
     */
    public static final int GLFW_MOD_ALT = 0x4;
    /**
     * Engine mod super value.
     */
    public static final int GLFW_MOD_SUPER = 0x8;
    /**
     * Engine mod caps lock value.
     */
    public static final int GLFW_MOD_CAPS_LOCK = 0x10;
    /**
     * Engine mod num lock value.
     */
    public static final int GLFW_MOD_NUM_LOCK = 0x20;
    /**
     * Engine mouse button 1 value.
     */
    public static final int GLFW_MOUSE_BUTTON_1 = 0;
    /**
     * Engine mouse button 2 value.
     */
    public static final int GLFW_MOUSE_BUTTON_2 = 1;
    /**
     * Engine mouse button 3 value.
     */
    public static final int GLFW_MOUSE_BUTTON_3 = 2;
    /**
     * Engine mouse button 4 value.
     */
    public static final int GLFW_MOUSE_BUTTON_4 = 3;
    /**
     * Engine mouse button 5 value.
     */
    public static final int GLFW_MOUSE_BUTTON_5 = 4;
    /**
     * Engine mouse button 6 value.
     */
    public static final int GLFW_MOUSE_BUTTON_6 = 5;
    /**
     * Engine mouse button 7 value.
     */
    public static final int GLFW_MOUSE_BUTTON_7 = 6;
    /**
     * Engine mouse button 8 value.
     */
    public static final int GLFW_MOUSE_BUTTON_8 = 7;
    /**
     * Engine mouse button last value.
     */
    public static final int GLFW_MOUSE_BUTTON_LAST = GLFW_MOUSE_BUTTON_8;
    /**
     * Engine mouse button left value.
     */
    public static final int GLFW_MOUSE_BUTTON_LEFT = GLFW_MOUSE_BUTTON_1;
    /**
     * Engine mouse button right value.
     */
    public static final int GLFW_MOUSE_BUTTON_RIGHT = GLFW_MOUSE_BUTTON_2;
    /**
     * Engine mouse button middle value.
     */
    public static final int GLFW_MOUSE_BUTTON_MIDDLE = GLFW_MOUSE_BUTTON_3;
    /**
     * Engine joystick 1 value.
     */
    public static final int GLFW_JOYSTICK_1 = 0;
    /**
     * Engine joystick 2 value.
     */
    public static final int GLFW_JOYSTICK_2 = 1;
    /**
     * Engine joystick 3 value.
     */
    public static final int GLFW_JOYSTICK_3 = 2;
    /**
     * Engine joystick 4 value.
     */
    public static final int GLFW_JOYSTICK_4 = 3;
    /**
     * Engine joystick 5 value.
     */
    public static final int GLFW_JOYSTICK_5 = 4;
    /**
     * Engine joystick 6 value.
     */
    public static final int GLFW_JOYSTICK_6 = 5;
    /**
     * Engine joystick 7 value.
     */
    public static final int GLFW_JOYSTICK_7 = 6;
    /**
     * Engine joystick 8 value.
     */
    public static final int GLFW_JOYSTICK_8 = 7;
    /**
     * Engine joystick 9 value.
     */
    public static final int GLFW_JOYSTICK_9 = 8;
    /**
     * Engine joystick 10 value.
     */
    public static final int GLFW_JOYSTICK_10 = 9;
    /**
     * Engine joystick 11 value.
     */
    public static final int GLFW_JOYSTICK_11 = 10;
    /**
     * Engine joystick 12 value.
     */
    public static final int GLFW_JOYSTICK_12 = 11;
    /**
     * Engine joystick 13 value.
     */
    public static final int GLFW_JOYSTICK_13 = 12;
    /**
     * Engine joystick 14 value.
     */
    public static final int GLFW_JOYSTICK_14 = 13;
    /**
     * Engine joystick 15 value.
     */
    public static final int GLFW_JOYSTICK_15 = 14;
    /**
     * Engine joystick 16 value.
     */
    public static final int GLFW_JOYSTICK_16 = 15;
    /**
     * Engine joystick last value.
     */
    public static final int GLFW_JOYSTICK_LAST = GLFW_JOYSTICK_16;
    /**
     * Engine gamepad button a value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_A = 0;
    /**
     * Engine gamepad button b value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_B = 1;
    /**
     * Engine gamepad button x value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_X = 2;
    /**
     * Engine gamepad button y value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_Y = 3;
    /**
     * Engine gamepad button left bumper value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_LEFT_BUMPER = 4;
    /**
     * Engine gamepad button right bumper value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER = 5;
    /**
     * Engine gamepad button back value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_BACK = 6;
    /**
     * Engine gamepad button start value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_START = 7;
    /**
     * Engine gamepad button guide value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_GUIDE = 8;
    /**
     * Engine gamepad button left thumb value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_LEFT_THUMB = 9;
    /**
     * Engine gamepad button right thumb value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_RIGHT_THUMB = 10;
    /**
     * Engine gamepad button dpad up value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_DPAD_UP = 11;
    /**
     * Engine gamepad button dpad right value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_DPAD_RIGHT = 12;
    /**
     * Engine gamepad button dpad down value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_DPAD_DOWN = 13;
    /**
     * Engine gamepad button dpad left value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_DPAD_LEFT = 14;
    /**
     * Engine gamepad button last value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_LAST = GLFW_GAMEPAD_BUTTON_DPAD_LEFT;
    /**
     * Engine gamepad button cross value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_CROSS = GLFW_GAMEPAD_BUTTON_A;
    /**
     * Engine gamepad button circle value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_CIRCLE = GLFW_GAMEPAD_BUTTON_B;
    /**
     * Engine gamepad button square value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_SQUARE = GLFW_GAMEPAD_BUTTON_X;
    /**
     * Engine gamepad button triangle value.
     */
    public static final int GLFW_GAMEPAD_BUTTON_TRIANGLE = GLFW_GAMEPAD_BUTTON_Y;
    /**
     * Engine gamepad axis left x value.
     */
    public static final int GLFW_GAMEPAD_AXIS_LEFT_X = 0;
    /**
     * Engine gamepad axis left y value.
     */
    public static final int GLFW_GAMEPAD_AXIS_LEFT_Y = 1;
    /**
     * Engine gamepad axis right x value.
     */
    public static final int GLFW_GAMEPAD_AXIS_RIGHT_X = 2;
    /**
     * Engine gamepad axis right y value.
     */
    public static final int GLFW_GAMEPAD_AXIS_RIGHT_Y = 3;
    /**
     * Engine gamepad axis left trigger value.
     */
    public static final int GLFW_GAMEPAD_AXIS_LEFT_TRIGGER = 4;
    /**
     * Engine gamepad axis right trigger value.
     */
    public static final int GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER = 5;
    /**
     * Engine gamepad axis last value.
     */
    public static final int GLFW_GAMEPAD_AXIS_LAST = GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER;
    /**
     * Engine no error value.
     */
    public static final int GLFW_NO_ERROR = 0;
    /**
     * Engine not initialized value.
     */
    public static final int GLFW_NOT_INITIALIZED = 0x10001;
    /**
     * Engine no current context value.
     */
    public static final int GLFW_NO_CURRENT_CONTEXT = 0x10002;
    /**
     * Engine invalid enum value.
     */
    public static final int GLFW_INVALID_ENUM = 0x10003;
    /**
     * Engine invalid value value.
     */
    public static final int GLFW_INVALID_VALUE = 0x10004;
    /**
     * Engine out of memory value.
     */
    public static final int GLFW_OUT_OF_MEMORY = 0x10005;
    /**
     * Engine api unavailable value.
     */
    public static final int GLFW_API_UNAVAILABLE = 0x10006;
    /**
     * Engine version unavailable value.
     */
    public static final int GLFW_VERSION_UNAVAILABLE = 0x10007;
    /**
     * Engine platform error value.
     */
    public static final int GLFW_PLATFORM_ERROR = 0x10008;
    /**
     * Engine format unavailable value.
     */
    public static final int GLFW_FORMAT_UNAVAILABLE = 0x10009;
    /**
     * Engine no window context value.
     */
    public static final int GLFW_NO_WINDOW_CONTEXT = 0x1000A;
    /**
     * Engine cursor unavailable value.
     */
    public static final int GLFW_CURSOR_UNAVAILABLE = 0x1000B;
    /**
     * Engine feature unavailable value.
     */
    public static final int GLFW_FEATURE_UNAVAILABLE = 0x1000C;
    /**
     * Engine feature unimplemented value.
     */
    public static final int GLFW_FEATURE_UNIMPLEMENTED = 0x1000D;
    /**
     * Engine platform unavailable value.
     */
    public static final int GLFW_PLATFORM_UNAVAILABLE = 0x1000E;
    /**
     * Engine focused value.
     */
    public static final int GLFW_FOCUSED = 0x20001;
    /**
     * Engine iconified value.
     */
    public static final int GLFW_ICONIFIED = 0x20002;
    /**
     * Engine resizable value.
     */
    public static final int GLFW_RESIZABLE = 0x20003;
    /**
     * Engine visible value.
     */
    public static final int GLFW_VISIBLE = 0x20004;
    /**
     * Engine decorated value.
     */
    public static final int GLFW_DECORATED = 0x20005;
    /**
     * Engine auto iconify value.
     */
    public static final int GLFW_AUTO_ICONIFY = 0x20006;
    /**
     * Engine floating value.
     */
    public static final int GLFW_FLOATING = 0x20007;
    /**
     * Engine maximized value.
     */
    public static final int GLFW_MAXIMIZED = 0x20008;
    /**
     * Engine center cursor value.
     */
    public static final int GLFW_CENTER_CURSOR = 0x20009;
    /**
     * Engine transparent framebuffer value.
     */
    public static final int GLFW_TRANSPARENT_FRAMEBUFFER = 0x2000A;
    /**
     * Engine hovered value.
     */
    public static final int GLFW_HOVERED = 0x2000B;
    /**
     * Engine focus on show value.
     */
    public static final int GLFW_FOCUS_ON_SHOW = 0x2000C;
    /**
     * Engine mouse passthrough value.
     */
    public static final int GLFW_MOUSE_PASSTHROUGH = 0x2000D;
    /**
     * Engine position x value.
     */
    public static final int GLFW_POSITION_X = 0x2000E;
    /**
     * Engine position y value.
     */
    public static final int GLFW_POSITION_Y = 0x2000F;
    /**
     * Engine soft fullscreen value.
     */
    public static final int GLFW_SOFT_FULLSCREEN = 0x20010;
    /**
     * Engine cursor value.
     */
    public static final int GLFW_CURSOR = 0x33001;
    /**
     * Engine sticky keys value.
     */
    public static final int GLFW_STICKY_KEYS = 0x33002;
    /**
     * Engine sticky mouse buttons value.
     */
    public static final int GLFW_STICKY_MOUSE_BUTTONS = 0x33003;
    /**
     * Engine lock key mods value.
     */
    public static final int GLFW_LOCK_KEY_MODS = 0x33004;
    /**
     * Engine raw mouse motion value.
     */
    public static final int GLFW_RAW_MOUSE_MOTION = 0x33005;
    /**
     * Engine unlimited mouse buttons value.
     */
    public static final int GLFW_UNLIMITED_MOUSE_BUTTONS = 0x33006;
    /**
     * Engine ime value.
     */
    public static final int GLFW_IME = 0x33007;
    /**
     * Engine cursor normal value.
     */
    public static final int GLFW_CURSOR_NORMAL = 0x34001;
    /**
     * Engine cursor hidden value.
     */
    public static final int GLFW_CURSOR_HIDDEN = 0x34002;
    /**
     * Engine cursor disabled value.
     */
    public static final int GLFW_CURSOR_DISABLED = 0x34003;
    /**
     * Engine cursor captured value.
     */
    public static final int GLFW_CURSOR_CAPTURED = 0x34004;
    /**
     * Engine arrow cursor value.
     */
    public static final int GLFW_ARROW_CURSOR = 0x36001;
    /**
     * Engine ibeam cursor value.
     */
    public static final int GLFW_IBEAM_CURSOR = 0x36002;
    /**
     * Engine crosshair cursor value.
     */
    public static final int GLFW_CROSSHAIR_CURSOR = 0x36003;
    /**
     * Engine pointing hand cursor value.
     */
    public static final int GLFW_POINTING_HAND_CURSOR = 0x36004;
    /**
     * Engine resize ew cursor value.
     */
    public static final int GLFW_RESIZE_EW_CURSOR = 0x36005;
    /**
     * Engine resize ns cursor value.
     */
    public static final int GLFW_RESIZE_NS_CURSOR = 0x36006;
    /**
     * Engine resize nwse cursor value.
     */
    public static final int GLFW_RESIZE_NWSE_CURSOR = 0x36007;
    /**
     * Engine resize nesw cursor value.
     */
    public static final int GLFW_RESIZE_NESW_CURSOR = 0x36008;
    /**
     * Engine resize all cursor value.
     */
    public static final int GLFW_RESIZE_ALL_CURSOR = 0x36009;
    /**
     * Engine not allowed cursor value.
     */
    public static final int GLFW_NOT_ALLOWED_CURSOR = 0x3600A;
    /**
     * Engine hresize cursor value.
     */
    public static final int GLFW_HRESIZE_CURSOR = GLFW_RESIZE_EW_CURSOR;
    /**
     * Engine vresize cursor value.
     */
    public static final int GLFW_VRESIZE_CURSOR = GLFW_RESIZE_NS_CURSOR;
    /**
     * Engine hand cursor value.
     */
    public static final int GLFW_HAND_CURSOR = GLFW_POINTING_HAND_CURSOR;
    /**
     * Engine connected value.
     */
    public static final int GLFW_CONNECTED = 0x40001;
    /**
     * Engine disconnected value.
     */
    public static final int GLFW_DISCONNECTED = 0x40002;
    /**
     * Engine joystick hat buttons value.
     */
    public static final int GLFW_JOYSTICK_HAT_BUTTONS = 0x50001;
    /**
     * Engine angle platform type value.
     */
    public static final int GLFW_ANGLE_PLATFORM_TYPE = 0x50002;
    /**
     * Engine any position value.
     */
    public static final int GLFW_ANY_POSITION = 0x80000000;
    /**
     * Engine platform value.
     */
    public static final int GLFW_PLATFORM = 0x50003;
    /**
     * Engine manage preedit candidate value.
     */
    public static final int GLFW_MANAGE_PREEDIT_CANDIDATE = 0x50004;
    /**
     * Engine cocoa chdir resources value.
     */
    public static final int GLFW_COCOA_CHDIR_RESOURCES = 0x51001;
    /**
     * Engine cocoa menubar value.
     */
    public static final int GLFW_COCOA_MENUBAR = 0x51002;
    /**
     * Engine x11 xcb vulkan surface value.
     */
    public static final int GLFW_X11_XCB_VULKAN_SURFACE = 0x52001;
    /**
     * Engine x11 onthespot value.
     */
    public static final int GLFW_X11_ONTHESPOT = 0x52002;
    /**
     * Engine wayland libdecor value.
     */
    public static final int GLFW_WAYLAND_LIBDECOR = 0x53001;
    /**
     * Engine any platform value.
     */
    public static final int GLFW_ANY_PLATFORM = 0x60000;
    /**
     * Engine platform win32 value.
     */
    public static final int GLFW_PLATFORM_WIN32 = 0x60001;
    /**
     * Engine platform cocoa value.
     */
    public static final int GLFW_PLATFORM_COCOA = 0x60002;
    /**
     * Engine platform wayland value.
     */
    public static final int GLFW_PLATFORM_WAYLAND = 0x60003;
    /**
     * Engine platform x11 value.
     */
    public static final int GLFW_PLATFORM_X11 = 0x60004;
    /**
     * Engine platform null value.
     */
    public static final int GLFW_PLATFORM_NULL = 0x60005;
    /**
     * Engine dont care value.
     */
    public static final int GLFW_DONT_CARE = -1;
    /**
     * Engine red bits value.
     */
    public static final int GLFW_RED_BITS = 0x21001;
    /**
     * Engine green bits value.
     */
    public static final int GLFW_GREEN_BITS = 0x21002;
    /**
     * Engine blue bits value.
     */
    public static final int GLFW_BLUE_BITS = 0x21003;
    /**
     * Engine alpha bits value.
     */
    public static final int GLFW_ALPHA_BITS = 0x21004;
    /**
     * Engine depth bits value.
     */
    public static final int GLFW_DEPTH_BITS = 0x21005;
    /**
     * Engine stencil bits value.
     */
    public static final int GLFW_STENCIL_BITS = 0x21006;
    /**
     * Engine accum red bits value.
     */
    public static final int GLFW_ACCUM_RED_BITS = 0x21007;
    /**
     * Engine accum green bits value.
     */
    public static final int GLFW_ACCUM_GREEN_BITS = 0x21008;
    /**
     * Engine accum blue bits value.
     */
    public static final int GLFW_ACCUM_BLUE_BITS = 0x21009;
    /**
     * Engine accum alpha bits value.
     */
    public static final int GLFW_ACCUM_ALPHA_BITS = 0x2100A;
    /**
     * Engine aux buffers value.
     */
    public static final int GLFW_AUX_BUFFERS = 0x2100B;
    /**
     * Engine stereo value.
     */
    public static final int GLFW_STEREO = 0x2100C;
    /**
     * Engine samples value.
     */
    public static final int GLFW_SAMPLES = 0x2100D;
    /**
     * Engine srgb capable value.
     */
    public static final int GLFW_SRGB_CAPABLE = 0x2100E;
    /**
     * Engine refresh rate value.
     */
    public static final int GLFW_REFRESH_RATE = 0x2100F;
    /**
     * Engine doublebuffer value.
     */
    public static final int GLFW_DOUBLEBUFFER = 0x21010;
    /**
     * Engine client api value.
     */
    public static final int GLFW_CLIENT_API = 0x22001;
    /**
     * Engine context version major value.
     */
    public static final int GLFW_CONTEXT_VERSION_MAJOR = 0x22002;
    /**
     * Engine context version minor value.
     */
    public static final int GLFW_CONTEXT_VERSION_MINOR = 0x22003;
    /**
     * Engine context revision value.
     */
    public static final int GLFW_CONTEXT_REVISION = 0x22004;
    /**
     * Engine context robustness value.
     */
    public static final int GLFW_CONTEXT_ROBUSTNESS = 0x22005;
    /**
     * Engine opengl forward compat value.
     */
    public static final int GLFW_OPENGL_FORWARD_COMPAT = 0x22006;
    /**
     * Engine context debug value.
     */
    public static final int GLFW_CONTEXT_DEBUG = 0x22007;
    /**
     * Engine opengl debug context value.
     */
    public static final int GLFW_OPENGL_DEBUG_CONTEXT = GLFW_CONTEXT_DEBUG;
    /**
     * Engine opengl profile value.
     */
    public static final int GLFW_OPENGL_PROFILE = 0x22008;
    /**
     * Engine context release behavior value.
     */
    public static final int GLFW_CONTEXT_RELEASE_BEHAVIOR = 0x22009;
    /**
     * Engine context no error value.
     */
    public static final int GLFW_CONTEXT_NO_ERROR = 0x2200A;
    /**
     * Engine context creation api value.
     */
    public static final int GLFW_CONTEXT_CREATION_API = 0x2200B;
    /**
     * Engine scale to monitor value.
     */
    public static final int GLFW_SCALE_TO_MONITOR = 0x2200C;
    /**
     * Engine scale framebuffer value.
     */
    public static final int GLFW_SCALE_FRAMEBUFFER = 0x2200D;
    /**
     * Engine cocoa retina framebuffer value.
     */
    public static final int GLFW_COCOA_RETINA_FRAMEBUFFER = 0x23001;
    /**
     * Engine cocoa frame name value.
     */
    public static final int GLFW_COCOA_FRAME_NAME = 0x23002;
    /**
     * Engine cocoa graphics switching value.
     */
    public static final int GLFW_COCOA_GRAPHICS_SWITCHING = 0x23003;
    /**
     * Engine x11 class name value.
     */
    public static final int GLFW_X11_CLASS_NAME = 0x24001;
    /**
     * Engine x11 instance name value.
     */
    public static final int GLFW_X11_INSTANCE_NAME = 0x24002;
    /**
     * Engine win32 keyboard menu value.
     */
    public static final int GLFW_WIN32_KEYBOARD_MENU = 0x25001;
    /**
     * Engine win32 showdefault value.
     */
    public static final int GLFW_WIN32_SHOWDEFAULT = 0x25002;
    /**
     * Engine wayland app id value.
     */
    public static final int GLFW_WAYLAND_APP_ID = 0x26001;
    /**
     * Engine no api value.
     */
    public static final int GLFW_NO_API = 0;
    /**
     * Engine opengl api value.
     */
    public static final int GLFW_OPENGL_API = 0x30001;
    /**
     * Engine opengl es api value.
     */
    public static final int GLFW_OPENGL_ES_API = 0x30002;
    /**
     * Engine no robustness value.
     */
    public static final int GLFW_NO_ROBUSTNESS = 0;
    /**
     * Engine no reset notification value.
     */
    public static final int GLFW_NO_RESET_NOTIFICATION = 0x31001;
    /**
     * Engine lose context on reset value.
     */
    public static final int GLFW_LOSE_CONTEXT_ON_RESET = 0x31002;
    /**
     * Engine opengl any profile value.
     */
    public static final int GLFW_OPENGL_ANY_PROFILE = 0;
    /**
     * Engine opengl core profile value.
     */
    public static final int GLFW_OPENGL_CORE_PROFILE = 0x32001;
    /**
     * Engine opengl compat profile value.
     */
    public static final int GLFW_OPENGL_COMPAT_PROFILE = 0x32002;
    /**
     * Engine any release behavior value.
     */
    public static final int GLFW_ANY_RELEASE_BEHAVIOR = 0;
    /**
     * Engine release behavior flush value.
     */
    public static final int GLFW_RELEASE_BEHAVIOR_FLUSH = 0x35001;
    /**
     * Engine release behavior none value.
     */
    public static final int GLFW_RELEASE_BEHAVIOR_NONE = 0x35002;
    /**
     * Engine native context api value.
     */
    public static final int GLFW_NATIVE_CONTEXT_API = 0x36001;
    /**
     * Engine egl context api value.
     */
    public static final int GLFW_EGL_CONTEXT_API = 0x36002;
    /**
     * Engine osmesa context api value.
     */
    public static final int GLFW_OSMESA_CONTEXT_API = 0x36003;
    /**
     * Engine angle platform type none value.
     */
    public static final int GLFW_ANGLE_PLATFORM_TYPE_NONE = 0x37001;
    /**
     * Engine angle platform type opengl value.
     */
    public static final int GLFW_ANGLE_PLATFORM_TYPE_OPENGL = 0x37002;
    /**
     * Engine angle platform type opengles value.
     */
    public static final int GLFW_ANGLE_PLATFORM_TYPE_OPENGLES = 0x37003;
    /**
     * Engine angle platform type d3d9 value.
     */
    public static final int GLFW_ANGLE_PLATFORM_TYPE_D3D9 = 0x37004;
    /**
     * Engine angle platform type d3d11 value.
     */
    public static final int GLFW_ANGLE_PLATFORM_TYPE_D3D11 = 0x37005;
    /**
     * Engine angle platform type vulkan value.
     */
    public static final int GLFW_ANGLE_PLATFORM_TYPE_VULKAN = 0x37007;
    /**
     * Engine angle platform type metal value.
     */
    public static final int GLFW_ANGLE_PLATFORM_TYPE_METAL = 0x37008;
    /**
     * Engine wayland prefer libdecor value.
     */
    public static final int GLFW_WAYLAND_PREFER_LIBDECOR = 0x38001;
    /**
     * Engine wayland disable libdecor value.
     */
    public static final int GLFW_WAYLAND_DISABLE_LIBDECOR = 0x38002;

    /**
     * Prevents creating process-wide browser window adapters.
     */
    private GLFW() {
    }

    /**
     * Checks that the browser host has finished loading.
     * @return browser window result
     */
    public static boolean glfwInit() {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.requireHost();
        return true;
    }

    /**
     * Detaches input and ends the browser host session.
     */
    public static void glfwTerminate() {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.close();
    }

    /**
     * Reads the browser monotonic clock in seconds.
     * @return browser window result
     */
    public static double glfwGetTime() {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.time();
    }

    /**
     * Waits for the next browser frame and dispatches queued input.
     */
    public static void glfwPollEvents() {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.poll();
    }

    /**
     * Records canvas creation preferences.
     *
     * @param hint browser window argument
     * @param value browser window argument
     */
    public static void glfwWindowHint(int hint, int value) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.hint(hint, value);
    }

    /**
     * Creates the application canvas presentation.
     *
     * @param width browser window argument
     * @param height browser window argument
     * @param title browser window argument
     * @param monitor browser window argument
     * @param shared browser window argument
     * @return browser window result
     */
    public static long glfwCreateWindow(int width, int height, CharSequence title, long monitor, long shared) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.createWindow(width, height, title.toString(), monitor != 0);
        return 1L;
    }

    /**
     * Releases the canvas presentation.
     *
     * @param window browser window argument
     */
    public static void glfwDestroyWindow(long window) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.destroyWindow();
    }

    /**
     * Reads retained canvas state.
     *
     * @param window browser window argument
     * @param attribute browser window argument
     * @return browser window result
     */
    public static int glfwGetWindowAttrib(long window, int attribute) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.attribute(attribute);
    }

    /**
     * Changes a supported canvas window preference.
     *
     * @param window browser window argument
     * @param attribute browser window argument
     * @param value browser window argument
     */
    public static void glfwSetWindowAttrib(long window, int attribute, int value) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.attribute(attribute, value);
    }

    /**
     * Copies the logical canvas dimensions.
     *
     * @param window browser window argument
     * @param width browser window argument
     * @param height browser window argument
     */
    public static void glfwGetWindowSize(long window, IntBuffer width, IntBuffer height) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        width.put(width.position(), BrowserRuntime.width());
        height.put(height.position(), BrowserRuntime.height());
    }

    /**
     * Copies logical canvas dimensions into arrays.
     *
     * @param window browser window argument
     * @param width browser window argument
     * @param height browser window argument
     */
    public static void glfwGetWindowSize(long window, int[] width, int[] height) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        width[0] = BrowserRuntime.width();
        height[0] = BrowserRuntime.height();
    }

    /**
     * Copies physical canvas dimensions.
     *
     * @param window browser window argument
     * @param width browser window argument
     * @param height browser window argument
     */
    public static void glfwGetFramebufferSize(long window, IntBuffer width, IntBuffer height) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        width.put(width.position(), BrowserRuntime.pixelWidth());
        height.put(height.position(), BrowserRuntime.pixelHeight());
    }

    /**
     * Copies physical canvas dimensions into arrays.
     *
     * @param window browser window argument
     * @param width browser window argument
     * @param height browser window argument
     */
    public static void glfwGetFramebufferSize(long window, int[] width, int[] height) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        width[0] = BrowserRuntime.pixelWidth();
        height[0] = BrowserRuntime.pixelHeight();
    }

    /**
     * Requests new logical canvas dimensions.
     *
     * @param window browser window argument
     * @param width browser window argument
     * @param height browser window argument
     */
    public static void glfwSetWindowSize(long window, int width, int height) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.size(width, height);
    }

    /**
     * Updates the browser document title.
     *
     * @param window browser window argument
     * @param title browser window argument
     */
    public static void glfwSetWindowTitle(long window, CharSequence title) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.title(title.toString());
    }

    /**
     * Reads the application close request.
     *
     * @param window browser window argument
     * @return browser window result
     */
    public static boolean glfwWindowShouldClose(long window) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.shouldClose();
    }

    /**
     * Updates the retained close request.
     *
     * @param window browser window argument
     * @param close browser window argument
     */
    public static void glfwSetWindowShouldClose(long window, boolean close) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.requestClose(close);
    }

    /**
     * Shows the canvas presentation.
     *
     * @param window browser window argument
     */
    public static void glfwShowWindow(long window) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.show();
    }

    /**
     * Focuses the keyboard input canvas.
     *
     * @param window browser window argument
     */
    public static void glfwFocusWindow(long window) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.focus();
    }

    /**
     * Moves the canvas within its browser container.
     *
     * @param window browser window argument
     * @param x browser window argument
     * @param y browser window argument
     */
    public static void glfwSetWindowPos(long window, int x, int y) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.position(x, y);
    }

    /**
     * Returns the browser display token.
     * @return browser window result
     */
    public static long glfwGetPrimaryMonitor() {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return 1L;
    }

    /**
     * Snapshots available browser display dimensions.
     *
     * @param monitor browser window argument
     * @return browser window result
     */
    public static GLFWVidMode glfwGetVideoMode(long monitor) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return new GLFWVidMode(BrowserRuntime.screenWidth(), BrowserRuntime.screenHeight());
    }

    /**
     * Requests fullscreen or restores canvas sizing.
     *
     * @param window browser window argument
     * @param monitor browser window argument
     * @param x browser window argument
     * @param y browser window argument
     * @param width browser window argument
     * @param height browser window argument
     * @param refresh browser window argument
     */
    public static void glfwSetWindowMonitor(long window, long monitor, int x, int y, int width, int height, int refresh) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.fullscreen(monitor != 0);
        if (monitor == 0) BrowserRuntime.size(width, height);
    }

    /**
     * Fits the canvas to its browser viewport.
     *
     * @param window browser window argument
     */
    public static void glfwMaximizeWindow(long window) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.maximize();
    }

    /**
     * Hides the canvas presentation.
     *
     * @param window browser window argument
     */
    public static void glfwIconifyWindow(long window) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.hide();
    }

    /**
     * Restores the visible canvas presentation.
     *
     * @param window browser window argument
     */
    public static void glfwRestoreWindow(long window) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.show();
    }

    /**
     * Updates canvas opacity.
     *
     * @param window browser window argument
     * @param opacity browser window argument
     */
    public static void glfwSetWindowOpacity(long window, float opacity) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.opacity(opacity);
    }

    /**
     * Applies logical canvas sizing limits.
     *
     * @param window browser window argument
     * @param minWidth browser window argument
     * @param minHeight browser window argument
     * @param maxWidth browser window argument
     * @param maxHeight browser window argument
     */
    public static void glfwSetWindowSizeLimits(long window, int minWidth, int minHeight, int maxWidth, int maxHeight) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.limits(minWidth, minHeight, maxWidth, maxHeight);
    }

    /**
     * Copies the latest logical pointer position.
     *
     * @param window browser window argument
     * @param x browser window argument
     * @param y browser window argument
     */
    public static void glfwGetCursorPos(long window, double[] x, double[] y) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        x[0] = BrowserRuntime.cursorX();
        y[0] = BrowserRuntime.cursorY();
    }

    /**
     * Copies the latest logical pointer position into buffers.
     *
     * @param window browser window argument
     * @param x browser window argument
     * @param y browser window argument
     */
    public static void glfwGetCursorPos(long window, DoubleBuffer x, DoubleBuffer y) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        x.put(x.position(), BrowserRuntime.cursorX());
        y.put(y.position(), BrowserRuntime.cursorY());
    }

    /**
     * Updates the logical pointer position.
     *
     * @param window browser window argument
     * @param x browser window argument
     * @param y browser window argument
     */
    public static void glfwSetCursorPos(long window, double x, double y) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.cursorPosition(x, y);
    }

    /**
     * Reports browser pointer-lock movement availability.
     * @return browser window result
     */
    public static boolean glfwRawMouseMotionSupported() {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return true;
    }

    /**
     * Changes canvas pointer capture or visibility.
     *
     * @param window browser window argument
     * @param mode browser window argument
     * @param value browser window argument
     */
    public static void glfwSetInputMode(long window, int mode, int value) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.inputMode(mode, value);
    }

    /**
     * Retains the engine cursor-shape identifier.
     *
     * @param shape browser window argument
     * @return browser window result
     */
    public static long glfwCreateStandardCursor(int shape) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return shape;
    }

    /**
     * Releases a retained browser cursor descriptor.
     *
     * @param cursor browser window argument
     */
    public static void glfwDestroyCursor(long cursor) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.releaseCursor((int) cursor);
    }

    /**
     * Applies a standard or image browser cursor.
     *
     * @param window browser window argument
     * @param cursor browser window argument
     */
    public static void glfwSetCursor(long window, long cursor) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        BrowserRuntime.cursor((int) cursor);
    }

    /**
     * Creates a browser image cursor.
     *
     * @param image browser window argument
     * @param x browser window argument
     * @param y browser window argument
     * @return browser window result
     */
    public static long glfwCreateCursor(GLFWImage image, int x, int y) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.createCursor(image.pixels(), image.width(), image.height(), x, y);
    }

    /**
     * Sets the document favicon from engine image data.
     *
     * @param window browser window argument
     * @param images browser window argument
     */
    public static void glfwSetWindowIcon(long window, GLFWImageBuffer images) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        if (images.get(0) != null) {
            GLFWImage image = images.get(0);
            BrowserRuntime.icon(image.pixels(), image.width(), image.height());
        }
    }

    /**
     * Reads the last browser modifier-state report.
     * @return browser window result
     */
    public static boolean browserCapsLock() {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.capsLock();
    }

    /**
     * Resolves a browser-managed dropped-file path.
     *
     * @param names browser window argument
     * @param index browser window argument
     * @return browser window result
     */
    public static String browserDropName(long names, int index) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.dropName(Math.toIntExact(names), index);
    }

    /**
     * Replaces the retained windowfocus callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWWindowFocusCallbackI glfwSetWindowFocusCallback(long window, GLFWWindowFocusCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.windowFocus(callback);
    }

    /**
     * Replaces the retained windowclose callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWWindowCloseCallbackI glfwSetWindowCloseCallback(long window, GLFWWindowCloseCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.windowClose(callback);
    }

    /**
     * Replaces the retained drop callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWDropCallbackI glfwSetDropCallback(long window, GLFWDropCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.drop(callback);
    }

    /**
     * Replaces the retained windowpos callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWWindowPosCallbackI glfwSetWindowPosCallback(long window, GLFWWindowPosCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.windowPos(callback);
    }

    /**
     * Replaces the retained windowsize callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWWindowSizeCallbackI glfwSetWindowSizeCallback(long window, GLFWWindowSizeCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.windowSize(callback);
    }

    /**
     * Replaces the retained framebuffersize callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWFramebufferSizeCallbackI glfwSetFramebufferSizeCallback(long window, GLFWFramebufferSizeCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.framebufferSize(callback);
    }

    /**
     * Replaces the retained char callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWCharCallbackI glfwSetCharCallback(long window, GLFWCharCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.character(callback);
    }

    /**
     * Replaces the retained key callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWKeyCallbackI glfwSetKeyCallback(long window, GLFWKeyCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.key(callback);
    }

    /**
     * Replaces the retained cursorpos callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWCursorPosCallbackI glfwSetCursorPosCallback(long window, GLFWCursorPosCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.cursorPos(callback);
    }

    /**
     * Replaces the retained mousebutton callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWMouseButtonCallbackI glfwSetMouseButtonCallback(long window, GLFWMouseButtonCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.mouseButton(callback);
    }

    /**
     * Replaces the retained scroll callback.
     *
     * @param window browser window argument
     * @param callback browser window argument
     * @return browser window result
     */
    public static GLFWScrollCallbackI glfwSetScrollCallback(long window, GLFWScrollCallbackI callback) {
        /*
         * BrowserRuntime owns the canvas and confines event delivery to JGL.
         */
        return BrowserRuntime.scroll(callback);
    }

    /**
     * Restores default canvas hints before application configuration.
     */
    public static void glfwDefaultWindowHints() {
        /*
         * Hint zero clears only startup preferences, preserving active DOM state.
         */
        BrowserRuntime.hint(0, 0);
    }

    /**
     * Rejects native window-manager class names that have no browser equivalent.
     * @param hint native manager hint
     * @param value requested manager metadata
     */
    public static void glfwWindowHintString(int hint, CharSequence value) {
        /*
         * Silent application of desktop-only metadata would imply browser support.
         */
        throw new UnsupportedOperationException("Native window-manager metadata is unavailable in a browser");
    }

}
