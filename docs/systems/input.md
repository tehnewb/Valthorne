# Keyboard, mouse, and cursor input

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Use polling for continuous actions such as movement and events for discrete actions such as clicks or text entry. `Keyboard` tracks GLFW key and modifier state; `Mouse` tracks buttons, cursor position, scrolling, and cursor appearance. UI routing adds focus and coordinate conversion on top of these global inputs.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Key polling | Tests whether a physical key is currently held; it does not produce correctly composed Unicode text. |
| Text input | Character events carry text for editors and text fields independently of physical key codes. |
| Mouse state | Button and cursor queries support continuous drags and pointer feedback. |
| Cursors and modifiers | Cursor selection changes pointer appearance; modifier masks distinguish shortcut combinations. |

## Getting started

1. Let engine initialization install its input callbacks.
2. Poll held keys in `update(delta)` and scale motion by delta seconds.
3. Subscribe to transitions when an action should occur once; retain the listener object for removal.
4. Route Unicode text to a focused editor, and convert pointer coordinates through the viewport or UI root before hit testing.

## Ownership and lifecycle

Global input callbacks live with the active window. Remove application subscriptions when a screen is disposed. Avoid replacing engine-owned callbacks casually, because doing so can break both global listeners and UI routing.

## Important behavior

- Raw cursor coordinates are not automatically a widget's local coordinates.
- Consuming an event affects routed delivery; polling still represents device state.
- Use pointer cancellation when abandoning a drag, so a control does not remain armed.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Keyboard`](#type-keyboard)
- [`Mouse`](#type-mouse)

<a id="type-keyboard"></a>

### Keyboard

[Source](../../src/main/java/valthorne/Keyboard.java#L108)

The `Keyboard` class is Valthorne's global keyboard input manager. It provides
a centralized static API for tracking key state, modifier state, Caps Lock state,
and for dispatching keyboard events through the engine's event system.

This class is intentionally non-instantiable and is designed to be used entirely
through static members. Internally, it installs a GLFW key callback onto the active
window and uses that callback to:

- track which keys are currently down

- track current modifier flags such as Shift, Control, Alt, and Super

- track an approximate Caps Lock state

- publish `KeyPressEvent` and `KeyReleaseEvent` instances through `JGL`

- expose utility helpers for converting key codes into printable characters

One of the main goals of this class is to separate low-level GLFW keyboard handling
from higher-level gameplay, UI, and input logic. Instead of repeatedly querying GLFW
directly from many different systems, the engine can rely on this class as the single
authority for keyboard state and keyboard event propagation.

The callback registered by this class updates a `BitSet` that represents all
currently pressed keys. This makes it efficient to check whether a key is down at
any moment using `isKeyDown(int)`. At the same time, modifier flags are stored
in a compact byte mask, allowing helpers such as `isShiftDown()`,
`isCtrlDown()`, `isAltDown()`, and `isSuperDown()` to answer
state queries quickly.

This class also exposes a large set of key constants mirroring GLFW key codes. That
allows engine code to reference keys through readable names such as `SPACE`,
`ENTER`, `LEFT_SHIFT`, `F1`, and so on, without needing to use
raw integers throughout the codebase.

The `getKeyChar(int)` helper converts a supported key code into the printable
character that the current modifier state implies. It handles:

- alphabetic keys with Shift and Caps Lock interaction

- top-row numeric keys with shifted symbol variants

- common punctuation and symbol keys

This method is useful for lightweight text entry and UI logic where a full text-input
callback is not required.

##### Example Usage

```java
Keyboard.addKeyListener(event -> {
    if (event instanceof KeyPressEvent && event.getKey() == Keyboard.ENTER) {
        System.out.println("Enter was pressed.");
    }
});

if (Keyboard.isKeyDown(Keyboard.W)) {
    System.out.println("Moving forward.");
}

if (Keyboard.isShiftDown()) {
    System.out.println("Sprint modifier active.");
}

char typed = Keyboard.getKeyChar(Keyboard.A);
if (typed != '\0') {
    System.out.println("Typed character: " + typed);
}

Keyboard.removeKeyListener(myListener);
```

This example demonstrates the complete intended use of the class: listening for
keyboard events, polling current key state, checking modifiers, converting key
codes into printable characters, and unregistering listeners.

<details>
<summary>Keyboard operation reference (130 declarations)</summary>

#### UNKNOWN

```java
public static final  int UNKNOWN
```

Unknown key constant.

#### SPACE

```java
public static final  int SPACE
```

Space key.

#### APOSTROPHE

```java
public static final  int APOSTROPHE
```

Apostrophe key (').

#### COMMA

```java
public static final  int COMMA
```

Comma key (,).

#### MINUS

```java
public static final  int MINUS
```

Minus key (-).

#### PERIOD

```java
public static final  int PERIOD
```

Period key (.).

#### SLASH

```java
public static final  int SLASH
```

Slash key (/).

#### KEY_0

```java
public static final  int KEY_0
```

Number row 0 key.

#### KEY_1

```java
public static final  int KEY_1
```

Number row 1 key.

#### KEY_2

```java
public static final  int KEY_2
```

Number row 2 key.

#### KEY_3

```java
public static final  int KEY_3
```

Number row 3 key.

#### KEY_4

```java
public static final  int KEY_4
```

Number row 4 key.

#### KEY_5

```java
public static final  int KEY_5
```

Number row 5 key.

#### KEY_6

```java
public static final  int KEY_6
```

Number row 6 key.

#### KEY_7

```java
public static final  int KEY_7
```

Number row 7 key.

#### KEY_8

```java
public static final  int KEY_8
```

Number row 8 key.

#### KEY_9

```java
public static final  int KEY_9
```

Number row 9 key.

#### SEMICOLON

```java
public static final  int SEMICOLON
```

Semicolon key (;).

#### EQUAL

```java
public static final  int EQUAL
```

Equals key (=).

#### A

```java
public static final  int A
```

Letter A key.

#### B

```java
public static final  int B
```

Letter B key.

#### C

```java
public static final  int C
```

Letter C key.

#### D

```java
public static final  int D
```

Letter D key.

#### E

```java
public static final  int E
```

Letter E key.

#### F

```java
public static final  int F
```

Letter F key.

#### G

```java
public static final  int G
```

Letter G key.

#### H

```java
public static final  int H
```

Letter H key.

#### I

```java
public static final  int I
```

Letter I key.

#### J

```java
public static final  int J
```

Letter J key.

#### K

```java
public static final  int K
```

Letter K key.

#### L

```java
public static final  int L
```

Letter L key.

#### M

```java
public static final  int M
```

Letter M key.

#### N

```java
public static final  int N
```

Letter N key.

#### O

```java
public static final  int O
```

Letter O key.

#### P

```java
public static final  int P
```

Letter P key.

#### Q

```java
public static final  int Q
```

Letter Q key.

#### R

```java
public static final  int R
```

Letter R key.

#### S

```java
public static final  int S
```

Letter S key.

#### T

```java
public static final  int T
```

Letter T key.

#### U

```java
public static final  int U
```

Letter U key.

#### V

```java
public static final  int V
```

Letter V key.

#### W

```java
public static final  int W
```

Letter W key.

#### X

```java
public static final  int X
```

Letter X key.

#### Y

```java
public static final  int Y
```

Letter Y key.

#### Z

```java
public static final  int Z
```

Letter Z key.

#### LEFT_BRACKET

```java
public static final  int LEFT_BRACKET
```

Left bracket key ([).

#### BACKSLASH

```java
public static final  int BACKSLASH
```

Backslash key (\).

#### RIGHT_BRACKET

```java
public static final  int RIGHT_BRACKET
```

Right bracket key (]).

#### GRAVE_ACCENT

```java
public static final  int GRAVE_ACCENT
```

Grave accent key (`).

#### WORLD_1

```java
public static final  int WORLD_1
```

Non-US key #1.

#### WORLD_2

```java
public static final  int WORLD_2
```

Non-US key #2.

#### ESCAPE

```java
public static final  int ESCAPE
```

Escape key.

#### ENTER

```java
public static final  int ENTER
```

Enter key.

#### TAB

```java
public static final  int TAB
```

Tab key.

#### BACKSPACE

```java
public static final  int BACKSPACE
```

Backspace key.

#### INSERT

```java
public static final  int INSERT
```

Insert key.

#### DELETE

```java
public static final  int DELETE
```

Delete key.

#### RIGHT

```java
public static final  int RIGHT
```

Right arrow key.

#### LEFT

```java
public static final  int LEFT
```

Left arrow key.

#### DOWN

```java
public static final  int DOWN
```

Down arrow key.

#### UP

```java
public static final  int UP
```

Up arrow key.

#### PAGE_UP

```java
public static final  int PAGE_UP
```

Page Up key.

#### PAGE_DOWN

```java
public static final  int PAGE_DOWN
```

Page Down key.

#### HOME

```java
public static final  int HOME
```

Home key.

#### END

```java
public static final  int END
```

End key.

#### CAPS_LOCK

```java
public static final  int CAPS_LOCK
```

Caps Lock key.

#### SCROLL_LOCK

```java
public static final  int SCROLL_LOCK
```

Scroll Lock key.

#### NUM_LOCK

```java
public static final  int NUM_LOCK
```

Num Lock key.

#### PRINT_SCREEN

```java
public static final  int PRINT_SCREEN
```

Print Screen key.

#### PAUSE

```java
public static final  int PAUSE
```

Pause key.

#### F1

```java
public static final  int F1
```

Function key F1.

#### F2

```java
public static final  int F2
```

Function key F2.

#### F3

```java
public static final  int F3
```

Function key F3.

#### F4

```java
public static final  int F4
```

Function key F4.

#### F5

```java
public static final  int F5
```

Function key F5.

#### F6

```java
public static final  int F6
```

Function key F6.

#### F7

```java
public static final  int F7
```

Function key F7.

#### F8

```java
public static final  int F8
```

Function key F8.

#### F9

```java
public static final  int F9
```

Function key F9.

#### F10

```java
public static final  int F10
```

Function key F10.

#### F11

```java
public static final  int F11
```

Function key F11.

#### F12

```java
public static final  int F12
```

Function key F12.

#### F13

```java
public static final  int F13
```

Function key F13.

#### F14

```java
public static final  int F14
```

Function key F14.

#### F15

```java
public static final  int F15
```

Function key F15.

#### F16

```java
public static final  int F16
```

Function key F16.

#### F17

```java
public static final  int F17
```

Function key F17.

#### F18

```java
public static final  int F18
```

Function key F18.

#### F19

```java
public static final  int F19
```

Function key F19.

#### F20

```java
public static final  int F20
```

Function key F20.

#### F21

```java
public static final  int F21
```

Function key F21.

#### F22

```java
public static final  int F22
```

Function key F22.

#### F23

```java
public static final  int F23
```

Function key F23.

#### F24

```java
public static final  int F24
```

Function key F24.

#### F25

```java
public static final  int F25
```

Function key F25.

#### KP_0

```java
public static final  int KP_0
```

Keypad 0 key.

#### KP_1

```java
public static final  int KP_1
```

Keypad 1 key.

#### KP_2

```java
public static final  int KP_2
```

Keypad 2 key.

#### KP_3

```java
public static final  int KP_3
```

Keypad 3 key.

#### KP_4

```java
public static final  int KP_4
```

Keypad 4 key.

#### KP_5

```java
public static final  int KP_5
```

Keypad 5 key.

#### KP_6

```java
public static final  int KP_6
```

Keypad 6 key.

#### KP_7

```java
public static final  int KP_7
```

Keypad 7 key.

#### KP_8

```java
public static final  int KP_8
```

Keypad 8 key.

#### KP_9

```java
public static final  int KP_9
```

Keypad 9 key.

#### KP_DECIMAL

```java
public static final  int KP_DECIMAL
```

Keypad decimal key.

#### KP_DIVIDE

```java
public static final  int KP_DIVIDE
```

Keypad divide key.

#### KP_MULTIPLY

```java
public static final  int KP_MULTIPLY
```

Keypad multiply key.

#### KP_SUBTRACT

```java
public static final  int KP_SUBTRACT
```

Keypad subtract key.

#### KP_ADD

```java
public static final  int KP_ADD
```

Keypad add key.

#### KP_ENTER

```java
public static final  int KP_ENTER
```

Keypad enter key.

#### KP_EQUAL

```java
public static final  int KP_EQUAL
```

Keypad equals key.

#### LEFT_SHIFT

```java
public static final  int LEFT_SHIFT
```

Left Shift key.

#### LEFT_CONTROL

```java
public static final  int LEFT_CONTROL
```

Left Control key.

#### LEFT_ALT

```java
public static final  int LEFT_ALT
```

Left Alt key.

#### LEFT_SUPER

```java
public static final  int LEFT_SUPER
```

Left Super key.

#### RIGHT_SHIFT

```java
public static final  int RIGHT_SHIFT
```

Right Shift key.

#### RIGHT_CONTROL

```java
public static final  int RIGHT_CONTROL
```

Right Control key.

#### RIGHT_ALT

```java
public static final  int RIGHT_ALT
```

Right Alt key.

#### RIGHT_SUPER

```java
public static final  int RIGHT_SUPER
```

Right Super key.

#### MENU

```java
public static final  int MENU
```

Menu key.

#### addKeyListener

```java
public static void addKeyListener(KeyListener listener)
```

Registers a `KeyListener` so it can receive keyboard events.

The listener is subscribed through `JGL` for both concrete key routes:
`EventTypes#KEY_PRESS` and `EventTypes#KEY_RELEASE`.

- **`listener`** — the key listener to add

**Throws `NullPointerException`:** if `listener` is `null`

#### removeKeyListener

```java
public static void removeKeyListener(KeyListener listener)
```

Removes a previously registered `KeyListener`.

After removal, the listener will no longer receive keyboard events published
through `JGL` for the concrete key event routes.

- **`listener`** — the key listener to remove

**Throws `NullPointerException`:** if `listener` is `null`

#### isKeyDown

```java
public static boolean isKeyDown(int key)
```

Returns whether the given GLFW key code is currently pressed.

The key state is resolved from the internal `BitSet` that is updated by
the GLFW callback. Invalid key codes outside the GLFW key range always return
`false`.

- **`key`** — the GLFW key code to test

**Returns:** `true` if the key is currently down, otherwise `false`

#### isShiftDown

```java
public static boolean isShiftDown()
```

Returns whether either Shift key is currently active.

This is determined from the current GLFW modifier bit mask.

**Returns:** `true` if Shift is currently down

#### isCtrlDown

```java
public static boolean isCtrlDown()
```

Returns whether either Control key is currently active.

This is determined from the current GLFW modifier bit mask.

**Returns:** `true` if Control is currently down

#### isAltDown

```java
public static boolean isAltDown()
```

Returns whether either Alt key is currently active.

This is determined from the current GLFW modifier bit mask.

**Returns:** `true` if Alt is currently down

#### isSuperDown

```java
public static boolean isSuperDown()
```

Returns whether either Super key is currently active.

This is determined from the current GLFW modifier bit mask.
The Super key usually corresponds to the Windows key on Windows or the
Command key on macOS.

**Returns:** `true` if Super is currently down

#### isCapsLockOn

```java
public static boolean isCapsLockOn()
```

Returns whether Caps Lock is currently considered enabled.

This value is initialized from the operating system when possible and then kept
in sync by toggling the cached state whenever the Caps Lock key is pressed.

**Returns:** `true` if Caps Lock is currently on

#### getKeyChar

```java
public static char getKeyChar(int key)
```

Converts a supported GLFW key code into its printable character representation.

The conversion takes the current Shift and Caps Lock state into account for
alphabetic keys, applies shifted symbol mappings for the top number row,
and resolves common punctuation keys. If the supplied key does not correspond
to a printable character, the null character `'\0'` is returned.

This method is useful for lightweight text input scenarios where the engine
wants a quick key-to-character conversion without using a full character
callback pipeline.

- **`key`** — the GLFW key code to convert

**Returns:** the printable character for the given key, or `'\0'` if none exists

</details>

<a id="type-mouse"></a>

### Mouse

[Source](../../src/main/java/valthorne/Mouse.java#L104)

The `Mouse` class is Valthorne's global static mouse input manager for GLFW-based
applications. It centralizes cursor position tracking, mouse button state, scroll wheel
input, cursor mode changes, cursor shape changes, custom cursor creation, and event
publishing through the engine event system.

This class is intentionally non-instantiable and operates entirely through static state.
It is designed to be initialized once for the active window and then queried or listened
to from anywhere in the engine or game code. Internally, it installs GLFW callbacks for:

- cursor movement

- mouse button press and release actions

- scroll wheel movement

These callbacks update the cached mouse state and publish reusable event objects such as:

- `MouseMoveEvent`

- `MouseDragEvent`

- `MousePressEvent`

- `MouseReleaseEvent`

- `MouseScrollEvent`

The class also exposes helper methods for:

- checking whether a mouse button is currently down

- reading the current cursor position

- reading current scroll deltas

- switching between GLFW cursor modes

- setting a standard system cursor

- creating a custom cursor from `TextureData`

- registering and unregistering listeners

One important detail of this class is that it stores the raw GLFW Y coordinate internally
and converts it to Valthorne's bottom-left style coordinate system when reporting public
mouse positions and events. This keeps mouse behavior aligned with the rest of the engine's
rendering conventions.

##### Example Usage

```java
Mouse.init();

Mouse.addMouseListener(event -> {
    if (event instanceof MousePressEvent press && press.getButton() == Mouse.LEFT) {
        System.out.println("Left click at: " + press.getX() + ", " + press.getY());
    }
});

Mouse.addScrollListener(event -> {
    System.out.println("Scroll: " + event.getXOffset() + ", " + event.getYOffset());
});

Mouse.setCursor(Mouse.CURSOR_HAND);
Mouse.setCursorMode(Mouse.CURSOR_NORMAL);

if (Mouse.isButtonDown(Mouse.LEFT)) {
    System.out.println("Holding left mouse button");
}

short mouseX = Mouse.getX();
short mouseY = Mouse.getY();

Mouse.resetScroll();
Mouse.dispose();
```

This example demonstrates the full intended use of the class: initialization,
listener registration, cursor changes, polling button state and position, clearing
scroll state, and shutdown cleanup.

<details>
<summary>Mouse operation reference (34 declarations)</summary>

#### LEFT

```java
public static final  int LEFT
```

Left mouse button (button index 0).

#### RIGHT

```java
public static final  int RIGHT
```

Right mouse button (button index 1).

#### MIDDLE

```java
public static final  int MIDDLE
```

Middle mouse button (button index 2), usually the scroll wheel click.

#### BUTTON_3

```java
public static final  int BUTTON_3
```

Extra mouse button #3 (typically side/back button).

#### BUTTON_4

```java
public static final  int BUTTON_4
```

Extra mouse button #4 (typically side/forward button).

#### BUTTON_5

```java
public static final  int BUTTON_5
```

Extra mouse button #5.

#### BUTTON_6

```java
public static final  int BUTTON_6
```

Extra mouse button #6.

#### BUTTON_7

```java
public static final  int BUTTON_7
```

Extra mouse button #7.

#### CURSOR_NORMAL

```java
public static final  int CURSOR_NORMAL
```

Cursor mode: normal cursor behavior (visible and not captured).

#### CURSOR_HIDDEN

```java
public static final  int CURSOR_HIDDEN
```

Cursor mode: cursor is hidden when over the window.

#### CURSOR_DISABLED

```java
public static final  int CURSOR_DISABLED
```

Cursor mode: cursor is disabled and captured (useful for FPS camera).

#### CURSOR_ARROW

```java
public static final  int CURSOR_ARROW
```

Standard arrow cursor shape for general use.

#### CURSOR_IBEAM

```java
public static final  int CURSOR_IBEAM
```

I-beam cursor shape typically used for text editing.

#### CURSOR_CROSSHAIR

```java
public static final  int CURSOR_CROSSHAIR
```

Crosshair cursor shape for precise selection.

#### CURSOR_HAND

```java
public static final  int CURSOR_HAND
```

Hand cursor shape indicating clickable elements.

#### CURSOR_HRESIZE

```java
public static final  int CURSOR_HRESIZE
```

Horizontal resize cursor shape for width adjustment.

#### CURSOR_VRESIZE

```java
public static final  int CURSOR_VRESIZE
```

Vertical resize cursor shape for height adjustment.

#### setCursor

```java
public static void setCursor(int shape)
```

Sets the mouse cursor to one of GLFW's standard system cursor shapes.

If a custom or previously created cursor is already active, it is destroyed
before the new cursor is created and assigned. If the window address is invalid,
the method returns immediately without doing anything.

- **`shape`** — one of the supported `CURSOR_*` shape constants

#### setCursor

```java
public static void setCursor(TextureData data, int hotX, int hotY)
```

Creates and assigns a custom cursor from the provided `TextureData`.

The supplied image data is wrapped in a temporary `GLFWImage`, then used
to create a GLFW cursor. The hotspot coordinates are clamped to the image bounds.
The Y hotspot is converted to match this engine's coordinate convention before
the cursor is created.

Any previously active cursor created by this class is destroyed before the new
one is assigned.

- **`data`** — the texture data used as the cursor image
- **`hotX`** — the hotspot X coordinate relative to the image
- **`hotY`** — the hotspot Y coordinate relative to the image

**Throws `NullPointerException`:** if `data` is `null`

**Throws `IllegalStateException`:** if `data.buffer()` is `null`

**Throws `RuntimeException`:** if GLFW fails to create the cursor from the provided image

#### setCursorMode

```java
public static void setCursorMode(int mode)
```

Sets the current GLFW cursor mode for the active window.

Supported values are:

- `CURSOR_NORMAL`

- `CURSOR_HIDDEN`

- `CURSOR_DISABLED`

If the window address is invalid, the method returns immediately. Any unsupported
value causes an `IllegalArgumentException`.

- **`mode`** — the GLFW cursor mode constant

**Throws `IllegalArgumentException`:** if the provided mode is not a valid GLFW cursor mode

#### setCursorPosition

```java
public static void setCursorPosition(double x, double y)
```

Sets the cursor position in GLFW window coordinates.

This delegates directly to `GLFW#glfwSetCursorPos(long, double, double)`.
The coordinates use GLFW's window-space convention, where the origin is at the
top-left. Calling this may trigger the installed cursor position callback.

- **`x`** — the X position in GLFW window coordinates
- **`y`** — the Y position in GLFW window coordinates

#### addMouseListener

```java
public static void addMouseListener(MouseListener listener)
```

Registers a `MouseListener` to receive mouse event notifications.

The listener is subscribed to the concrete mouse action routes and will therefore
receive mouse press, release, move, and drag events published through the engine
event system.

- **`listener`** — the listener to register

**Throws `NullPointerException`:** if `listener` is `null`

#### removeMouseListener

```java
public static void removeMouseListener(MouseListener listener)
```

Unregisters a previously added `MouseListener`.

After removal, the listener will no longer receive published mouse events.

- **`listener`** — the listener to remove

**Throws `NullPointerException`:** if `listener` is `null`

#### addScrollListener

```java
public static void addScrollListener(MouseScrollListener listener)
```

Registers a `MouseScrollListener` to receive scroll wheel events.

The listener is subscribed to `EventTypes#MOUSE_SCROLL` notifications.

- **`listener`** — the scroll listener to register

**Throws `NullPointerException`:** if `listener` is `null`

#### removeScrollListener

```java
public static void removeScrollListener(MouseScrollListener listener)
```

Unregisters a previously added `MouseScrollListener`.

- **`listener`** — the scroll listener to remove

**Throws `NullPointerException`:** if `listener` is `null`

#### getX

```java
public static short getX()
```

Returns the current raw X cursor position.

This value is stored directly from GLFW cursor callbacks.

**Returns:** the current cursor X position

#### getY

```java
public static short getY()
```

Returns the current cursor Y position converted into Valthorne's coordinate system.

Internally, GLFW reports Y using a top-left origin. This method converts it to a
bottom-left origin by subtracting the internal raw Y value from the current window
height.

**Returns:** the converted cursor Y position

#### getScrollX

```java
public static byte getScrollX()
```

Returns the most recent horizontal scroll delta.

**Returns:** the horizontal scroll amount

#### getScrollY

```java
public static byte getScrollY()
```

Returns the most recent vertical scroll delta.

**Returns:** the vertical scroll amount

#### isButtonDown

```java
public static boolean isButtonDown(int button)
```

Returns whether the specified mouse button is currently held down.

Button state is tracked using a bitmask where each bit corresponds to a button
index. The method checks whether the requested bit is currently set.

- **`button`** — the mouse button index

**Returns:** `true` if the specified button is currently down

#### isShiftDown

```java
public boolean isShiftDown()
```

Returns whether the Shift modifier key is currently active during mouse interaction.

This method checks the cached modifier bitmask updated by GLFW mouse callbacks.

**Returns:** `true` if Shift is active

#### isCtrlDown

```java
public boolean isCtrlDown()
```

Returns whether the Control modifier key is currently active during mouse interaction.

**Returns:** `true` if Control is active

#### isAltDown

```java
public boolean isAltDown()
```

Returns whether the Alt modifier key is currently active during mouse interaction.

**Returns:** `true` if Alt is active

#### isSuperDown

```java
public boolean isSuperDown()
```

Returns whether the Super modifier key is currently active during mouse interaction.

On most systems, this corresponds to the Windows key or Command key.

**Returns:** `true` if Super is active

</details>

## Related guides

- [Events and listeners](events.md)
- [UI roots, nodes, and input routing](ui-core.md)
- [Viewport scaling and coordinate conversion](viewports.md)
