# Floating UI windows

Author: Albert Beaupre

`valthorne.ui.nodes.Window` is a titled container inside a `UIRoot`. Use it for tool
palettes, inspectors, movable panels, or document views. It uses the same layout,
theme, pointer capture, and focus routing as other Valthorne controls.

The engine's native desktop window remains `valthorne.Window`. Import the intended
type explicitly, or use a fully qualified name when a file uses both. Wildcard imports
of both `valthorne.*` and `valthorne.ui.nodes.*` make `Window` ambiguous.

## Create a window

```java
var inspector = new valthorne.ui.nodes.Window("Inspector")
        .bounds(40, 60, 420, 300)
        .minimumSize(240, 160)
        .maximumSize(800, 600)
        .draggable(true)
        .resizable(true);

inspector.getContentPane().getLayout().column().gap(8);
TextField name = new TextField("Object name");
name.getLayout().height(36).widthPercent(100);
Button apply = new Button("Apply").action(button -> applyChanges(name.getText()));
apply.getLayout().height(36).widthPercent(100);
inspector.getContentPane().add(name, apply);
root.add(inspector);
```

Use `ProfessionalTheme` for a frame, title bar, and highlighted resize handles.
The widget starts at `(0, 0)` with a `360 × 260` outer frame, a `160 × 100` minimum,
and both dragging and resizing enabled. The title is 32 logical units high; resize
borders occupy six units on each side. Bounds use top-left coordinates relative to
the parent and include the title and borders.

## Movement, resizing, and input

Drag the title bar to move the window. All four edges and all four corners resize
the frame; the opposite edge stays anchored when minimum or maximum size is reached.
Corners can be grabbed along the first 16 units of either adjoining six-unit border,
so a near-corner press selects diagonal resizing instead of a single-axis edge.
All four corners support growing, shrinking, or changing width and height in opposite
directions. Client content and title-bar hit areas remain separate from these border arms.

Hovering a resize area displays the matching system cursor: horizontal for left/right,
vertical for top/bottom, northwest/southeast for top-left/bottom-right, and
northeast/southwest for top-right/bottom-left. The cursor stays locked to the captured
handle during a drag, including outside the frame. Leaving the area, disabling resizing,
hiding/removing the window, or cancelling input restores the application's previous
standard or custom image cursor. Repeated hover updates reuse the native override cursor.

The root captures the pointer, so dragging remains active after leaving the handle.
Only the primary button starts a gesture. Disabling a policy mid-gesture stops that
operation at its current geometry. Root cancellation, hiding, removal, and disabled
state use the normal UI input lifecycle.

Dragging and resizing are independent:

```java
inspector.draggable(false); // fixed position, still resizable
inspector.resizable(false); // fixed size too
inspector.draggable(true);  // movable at its fixed size
```

Keyboard users can focus the title or an enabled resize handle using Tab. Arrow keys
move the title by eight units or resize the focused handle along its supported axes.
Shift increases the step to 32 units. Child text fields, buttons, and other controls
retain their own keyboard behavior. `getTitleBar()` provides a direct focus target:

```java
root.setFocusTo(inspector.getTitleBar());
```

Pointer displacement is converted to parent-local layout coordinates, so viewport
scaling does not double the movement distance. Clicking any descendant raises the
window above its siblings. `bringToFront()` performs the same operation from code.
Raising reorders existing nodes without recreating controls, losing text selection,
or cancelling pointer capture. Root modal scopes still govern input as usual.

## Geometry and limits

`bounds(x, y, width, height)` changes the frame silently and cancels any current
gesture. Positions must be finite. Requested dimensions must be at least `40 × 48`,
then clamp to the configured minimum and maximum. Minimum dimensions cannot exceed
maximum dimensions; invalid settings throw without changing the previous policy.
All size limits must be finite. The default maximum is `Float.MAX_VALUE`.

`getFrame()` returns immutable `Frame(x, y, width, height)` data. Immediately after a
setter it reports requested geometry; after layout it reflects computed geometry.
Use `bounds` for predictable frame updates. If you change `getLayout()` directly,
the new frame becomes observable after the next root layout pass.

Interactive changes stay within the parent by default. This keeps the title and
resize targets reachable. If the parent is smaller than the configured minimum,
minimum size wins and the oversized frame anchors at zero. Parent resizing does not
automatically move an idle window. Programmatic bounds can be outside the parent.
Use `keepWithinParent(false)` to allow user gestures outside the parent as well.

## Titles, content, and clipping

`title(text)` changes the displayed caption without moving the frame. `getTitle()`
reads it back. Empty titles are permitted; null titles are rejected. Long captions
clip inside the title bar, leaving room for the top-right X button.

Add controls to `getContentPane()`; use its normal layout settings to arrange them.
The panel fills the client area and has eight units of padding by default. The title
and borders remain separate from that area. Both rendering and pointer hit testing
clip overflowing controls to the body. Add a `ScrollPanel` inside the content pane
when the application needs scrollable content.

`content(node)` replaces all existing client controls with one unattached node,
using normal child-removal ownership rules. The new node retains its own layout.
Do not add application content directly to the outer window: its direct children
are the owned title, close button, body viewport, and resize targets.

## Closing and reopening

Click the **X** at the top-right to close the UI window. It remains available when
dragging or resizing is disabled. Keyboard users can Tab to the X and press Enter
or Space. Pressing it and releasing outside the button cancels the close action.
The button follows the right edge as the window resizes, independently of title dragging.

```java
inspector.onClose(() -> saveInspectorSettings());
inspector.close(); // Same close behavior as clicking X.
inspector.open();  // Restore the existing window and raise it above siblings.
```

Closing hides the widget while retaining its frame, content, and control values.
It immediately cancels pointer capture, focus, hover, and tooltips belonging to the
window, including an active move or resize gesture. Other controls retain their input
state. Closing this widget leaves the native application window running.

`onClose` runs synchronously after hiding and clearing input, once per visible-to-closed
transition. Repeated `close()` calls and closing a disabled window do nothing. The
listener may remove the widget from its parent or reopen it; callback exceptions
propagate to the caller. `open()` shows and raises the window, then focuses its title
when attached and enabled. Direct `setVisible` changes do not emit close notifications.

Use `getCloseButton()` to customize the owned button's style or focus it explicitly.
Replacing its action also replaces the default closing behavior.

## Notifications and lifecycle

```java
inspector.onChange(frame -> rememberInspectorBounds(frame));
```

User movement and resizing notify synchronously once per changed frame. The callback
sees committed geometry and receives an immutable value; duplicate bounds do not
emit another callback. Programmatic setters and layout-only changes are silent.
Application exceptions propagate. Use this listener to persist positions or update
related views without treating silent initialization as user input.

The widget follows normal `UINode` visibility, enabled state, and container ownership.
Use `close()` and `open()` for the lifecycle described above. Remove it through
its parent when its screen is discarded. Dispose the root before the theme resources
used by its descendants. All window operations belong on the UI thread.

## Styling and verification

The named styles are `window-frame` on the outer panel, `window-title` on its title
button, `window-close` on the X, and `window-grip` on resize buttons. The X highlights
red on hover. `ProfessionalTheme` supplies light/dark
variants with focused, hovered, and pressed handle feedback. Fonts and drawable
resources remain owned by the active theme.

Run `./gradlew verifyWidgets` for configuration, pointer, keyboard, stacking,
cancellation, closing/reopening, clipping, and scaled-viewport tests. `./gradlew verifyUI` runs the wider
UI regression suite. Rendered light/dark evidence is generated under
`build/reports/widget-visuals/windows-light.png` and `windows-dark.png`.

The sibling examples project's `runWidgetShowcase` task opens an interactive window
demo with title editing and independent Dragging/Resizing toggles. See the
[widget guide](ui-widgets.md) for the surrounding controls.
