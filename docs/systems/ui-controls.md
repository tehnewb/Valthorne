# Standard UI controls

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Standard controls render through the texture-oriented UI path. Choose controls by interaction rather than appearance alone: buttons activate, checkboxes toggle, sliders select a range value, and text fields edit text. Containers such as panels, grids, tabs, split panes, and modals organize other controls.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Actions and values | Button, checkbox, slider, and progress controls expose different interaction/value contracts. |
| Text and images | Label, Image, and DrawableNode display borrowed visual resources. |
| Containers | Panel, Grid, ScrollPanel, SplitPane, and TabbedPane arrange or reveal content. |
| Transient UI | Modal and Tooltip provide temporary or contextual information. |
| Collapsible content | CollapsibleSection hides or reveals a related group without replacing the application screen. |
| Live curve labels | SlugLabel draws Slug outlines with retained layout and updates measured dimensions when text or em size changes. |

## Getting started

1. Attach a control to a live UIRoot or container.
2. Assign required fonts/textures and configure layout, text, values, and callbacks.
3. Use theme keys or explicit setters to establish appearance.
4. Handle user actions through the control's callback/model rather than duplicating its input logic.

## Ownership and lifecycle

Controls commonly retain fonts, textures, and colors by reference. Keep those resources alive while the control draws. Removing a control should terminate its interactions; disposing a screen should unregister its root routing.

## Important behavior

- Programmatic setters do not all fire user-action callbacks; read each setter's contract.
- A displayed range value and a normalized thumb position are not interchangeable units.
- Use virtualized controls for large datasets rather than attaching one ordinary widget per row.
- SlugLabel borrows its SlugFont, SlugBatch, and mutable tint. Keep the shared batch idle on entry to label drawing and dispose shared GPU resources only after all dependent labels are gone.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Button`](#type-button)
- [`Checkbox`](#type-checkbox)
- [`CollapsibleSection`](#type-collapsiblesection)
- [`DrawableNode`](#type-drawablenode)
- [`Grid`](#type-grid)
- [`Image`](#type-image)
- [`Label`](#type-label)
- [`Modal`](#type-modal)
- [`Panel`](#type-panel)
- [`ProgressBar`](#type-progressbar)
- [`ScrollPanel`](#type-scrollpanel)
- [`Slider`](#type-slider)
- [`SlugLabel`](#type-sluglabel)
- [`SplitPane`](#type-splitpane)
- [`TabbedPane`](#type-tabbedpane)
- [`TextField`](#type-textfield)
- [`Tooltip`](#type-tooltip)

<a id="type-button"></a>

### Button

[Source](../../src/main/java/valthorne/ui/nodes/Button.java#L23)

A themed panel with a centered label and primary-button/keyboard activation.
Release activation uses the root's left-button hit-test policy; keyboard activation
accepts Enter or Space on an enabled receiver. Input routing supplies focus and
capture behavior; these callbacks do not independently require an earlier press.

All children, including NanoVG nodes, use the panel's shared rendering path.
Background styling is inherited from Panel.

Actions run synchronously after event consumption, and a null action disables
only the callback, not the button's normal input handling.

<details>
<summary>Button operation reference (10 declarations)</summary>

#### BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> BACKGROUND_KEY
```

Alias of the panel background key, allowing button rules to use the shared
drawable property without registering a separate key.

#### Constructor

```java
public Button()
```

Creates a clickable, focusable button with a centered non-clickable label.
The label does not grow or shrink and is attached through the panel lifecycle.

#### Constructor

```java
public Button(String text)
```

Creates a default button and supplies its initial label text.
Text handling and measurement are delegated to the child label.

- **`text`** — initial label text

#### getLabel

```java
public Label getLabel()
```

Returns the live child label for typography and layout customization.
The button hierarchy retains ownership of this child.

**Returns:** the owned label

#### getText

```java
public String getText()
```

Reads the current text from the child label without creating a text snapshot
or changing layout.

**Returns:** current label text

#### text

```java
public Button text(String text)
```

Replaces the label text through the label's normal text and measurement path.
The action and focus state are preserved.

- **`text`** — replacement label text

**Returns:** this button

#### action

```java
public Button action(NodeAction<Button> action)
```

Replaces the callback invoked for accepted activations. The callback receives
this button and runs synchronously; exceptions propagate to input dispatch.

- **`action`** — replacement callback, or null to clear it

**Returns:** this button

#### getAction

```java
public NodeAction<Button> getAction()
```

Returns the configured activation callback without invoking it.

**Returns:** current callback, or null when unset

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Applies the shared release-activation policy, consuming accepted releases
before invoking the optional action. Rejected releases have no effect here.

- **`event`** — routed mouse release

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Consumes Enter or Space on an enabled button and invokes the optional action.
Focus routing and repeat filtering are not performed by this callback itself.

- **`event`** — routed key press

</details>

<a id="type-checkbox"></a>

### Checkbox

[Source](../../src/main/java/valthorne/ui/nodes/Checkbox.java#L80)

`Checkbox` is a toggleable UI control built on top of `Panel`.
It supports checked and unchecked states, optional action callbacks, keyboard
activation, mouse activation, and theme-driven background and checkmark visuals.

This class is designed to be a simple reusable boolean input control. It stores
a local checked flag and mirrors that flag into the inherited node checked state
through `setChecked(boolean)` whenever the value changes.

Visuals are entirely style-driven:

- `BACKGROUND_KEY` resolves the checkbox background drawable

- `CHECKMARK_KEY` resolves the drawable shown when checked

- `ACTION_KEY` optionally resolves a style-provided action

The checkbox may have an explicitly assigned action through
`action(NodeAction)`. If no explicit action is assigned, it will attempt
to resolve one from the current style when the checked state changes.

Interaction behavior is as follows:

- Space or Enter toggles the checkbox when focused

- Mouse release toggles the checkbox when clicked

- Changing the checked state triggers the resolved action if one exists

##### Example Usage

```java
Checkbox checkbox = new Checkbox();

checkbox.getLayout()
        .width(24)
        .height(24);

checkbox.action(c -> {
    System.out.println("Checked: " + c.isChecked());
});

checkbox.checked(true);
checkbox.toggle();

boolean checked = checkbox.isChecked();
NodeAction<Checkbox> action = checkbox.getAction();

checkbox.draw(batch);
```

This example demonstrates the complete usage of the class: construction,
layout sizing, action assignment, state changes, querying state, and drawing.

<details>
<summary>Checkbox operation reference (12 declarations)</summary>

#### BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> BACKGROUND_KEY
```

Style key used to resolve the checkbox background drawable.

#### CHECKMARK_KEY

```java
public static final  StyleKey<Drawable> CHECKMARK_KEY
```

Style key used to resolve the checkbox checkmark drawable.

#### ACTION_KEY

```java
public static final  StyleKey<NodeAction<Checkbox>> ACTION_KEY
```

Style key used to resolve a fallback checkbox action from the current style.

#### Constructor

```java
public Checkbox()
```

Creates a new checkbox.

The checkbox is configured as clickable and focusable so it can be interacted
with using both mouse and keyboard input.

#### getAction

```java
public NodeAction<Checkbox> getAction()
```

Returns the explicitly assigned action for this checkbox.

**Returns:** the explicit action, or `null` if none exists

#### action

```java
public Checkbox action(NodeAction<Checkbox> action)
```

Assigns an explicit action to this checkbox.

This action takes priority over any action resolved from the current style.

- **`action`** — the action to assign

**Returns:** this checkbox

#### isChecked

```java
public boolean isChecked()
```

Returns whether this checkbox is currently checked.

**Returns:** `true` if checked

#### checked

```java
public Checkbox checked(boolean checked)
```

Sets the checked state of this checkbox.

If the state is unchanged, the method returns immediately. Otherwise the local
checked flag is updated, the inherited node checked state is synchronized, and
a resolved action is executed if one is available.

Action resolution order is:

- use the explicitly assigned action if present

- otherwise try to resolve `ACTION_KEY` from the current style

- **`checked`** — the new checked state

**Returns:** this checkbox

#### toggle

```java
public Checkbox toggle()
```

Toggles the checked state of this checkbox.

**Returns:** this checkbox

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Handles keyboard activation for this checkbox.

Pressing Space or Enter toggles the checked state.

- **`event`** — the key press event

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Handles mouse activation for this checkbox.

Releasing the mouse over the checkbox toggles the checked state.

- **`event`** — the mouse release event

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws the checkbox using the provided `TextureBatch`.

If the checkbox is not visible, rendering is skipped. Otherwise the current
style is resolved and the background drawable is rendered first. If the
checkbox is checked, the checkmark drawable is then rendered centered inside
the checkbox bounds at half the checkbox's width and height.

- **`batch`** — the batch used for rendering

</details>

<a id="type-collapsiblesection"></a>

### CollapsibleSection

[Source](../../src/main/java/valthorne/ui/nodes/CollapsibleSection.java#L18)

Retained disclosure widget with a fixed header and one content subtree.
Starts expanded; collapsing hides the body, assigns it zero height, and skips
its updates. Content can use either renderer. Focus or pointer capture within
a collapsing body is moved/cancelled before the body becomes hidden.
The header remains available for pointer activation and Left/Right navigation.

<details>
<summary>CollapsibleSection operation reference (6 declarations)</summary>

#### Constructor

```java
public CollapsibleSection(String title, UINode content)
```

Builds an expanded vertical section with a 36-unit header and full-width
content. The supplied node becomes a child of the internal body, and its
layout width is set to 100 percent.

- **`title`** — non-null persistent header text
- **`content`** — non-null unattached content node

**Throws `NullPointerException`:** if title or content is null

**Throws `IllegalArgumentException`:** if content already has a parent

#### isExpanded

```java
public boolean isExpanded()
```

Reads the requested disclosure state, which also controls body update calls.

**Returns:** true while the body is expanded

#### getHeader

```java
public Button getHeader()
```

Exposes the live header for focus, styling, or application configuration.
Its text is rewritten by later disclosure changes to include the state marker.

**Returns:** internally owned header button

#### onChange

```java
public AutoCloseable onChange(Runnable listener)
```

Subscribes synchronously to actual expansion changes. No event is emitted
when setting the current state; the listener sees updated visibility and text.

- **`listener`** — non-null callback

**Returns:** handle whose close removes this subscription

**Throws `NullPointerException`:** if listener is null

#### expanded

```java
public CollapsibleSection expanded(boolean value)
```

Applies a new disclosure state and fires change listeners after visibility,
height, and header text are updated. On collapse, focus within the body moves
to the header; body capture triggers root input cancellation first. Repeating
the existing value has no effect and emits no event.

- **`value`** — true to show the body, false to collapse it

**Returns:** this section

#### update

```java
@Override public void update(float delta)
```

Updates the header every call and the body only while expanded. Does not
invoke the superclass traversal, so arbitrary extra structural children are
not part of this update path.

- **`delta`** — elapsed update seconds

</details>

<a id="type-drawablenode"></a>

### DrawableNode

[Source](../../src/main/java/valthorne/ui/nodes/DrawableNode.java#L15)

Adapts a borrowed drawable to a UI node's current render position and dimensions.
Drawing stretches the drawable across the node bounds; layout and drawable
resource lifetime remain the caller's responsibility. The draw and update overrides
do not traverse children, so use this adapter as a leaf in the UI hierarchy.

<details>
<summary>DrawableNode operation reference (7 declarations)</summary>

#### Constructor

```java
public DrawableNode(Drawable drawable)
```

Stores drawing content without copying it or allocating rendering resources.
Null is accepted here but will fail if draw is called before replacement.

- **`drawable`** — content to render using this node's bounds

#### onCreate

```java
    public void onCreate()
```

Performs no creation work; the supplied drawable must already be ready for
use when the node is drawn.

#### onDestroy

```java
    public void onDestroy()
```

Leaves the borrowed drawable untouched. Its owner remains responsible for
releasing any associated resources after dependent nodes are finished.

#### update

```java
    public void update(float delta)
```

Performs no animation or child updates. Animate the drawable externally if
it needs time-dependent state.

- **`delta`** — elapsed seconds, unused by this leaf adapter

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws the current content at the node's render coordinates with its layout
width and height. Batch setup is supplied by the caller; children are not drawn.

- **`batch`** — active texture batch used by the drawable

**Throws `NullPointerException`:** if no drawable has been supplied

#### drawable

```java
public DrawableNode drawable(Drawable drawable)
```

Replaces the borrowed drawable without disposing the previous one or changing
layout dimensions. Null defers failure until drawing.

- **`drawable`** — replacement content

**Returns:** this node

#### getDrawable

```java
public Drawable getDrawable()
```

Returns the current drawable reference; this accessor does not transfer
resource ownership to or from the node.

**Returns:** borrowed drawable, possibly null

</details>

<a id="type-grid"></a>

### Grid

[Source](../../src/main/java/valthorne/ui/nodes/Grid.java#L84)

`Grid` is a layout-oriented container that arranges its children in a
wrapped row-based grid using the underlying Yoga flex layout system.

This node is designed to make it easy to place UI children into evenly sized
cells while still relying on the engine's standard layout pipeline. Internally,
the grid configures itself as a row-based wrapping container and then, during
layout application, pushes width and height constraints into each child based
on the configured cell size rules.

The grid supports:

- a configurable column count

- optional fixed cell width

- optional fixed cell height

- uniform gap configuration

- separate row gap and column gap configuration

- automatic container width and height calculation when cell sizes are point-based

When a fixed point-based cell width is used, the grid computes its total width
from the number of active columns and the configured horizontal gap. Likewise,
when a fixed point-based cell height is used, the grid computes its total height
from the number of rows and the configured vertical gap.

If cell width or height is set to auto, the grid leaves that dimension to the
underlying layout system instead of forcing a fixed measurement.

This class is especially useful for icon grids, inventory slots, gallery layouts,
menu tiles, or any other UI where children should be laid out in a consistent
multi-column arrangement.

##### Example Usage

```java
Grid grid = new Grid()
        .columns(4)
        .cellSize(64, 64)
        .gap(8);

grid.add(new Button("One"));
grid.add(new Button("Two"));
grid.add(new Button("Three"));
grid.add(new Button("Four"));
grid.add(new Button("Five"));

int columns = grid.getColumns();
LayoutValue width = grid.getCellWidth();
LayoutValue height = grid.getCellHeight();

grid.update(delta);
grid.draw(batch);
```

This example demonstrates full usage of the class by configuring columns,
assigning cell size and gaps, adding children, querying layout values, and
participating in the standard update and draw cycle.

<details>
<summary>Grid operation reference (16 declarations)</summary>

#### Constructor

```java
public Grid()
```

Creates a new grid container.

The grid is initialized as a row-based wrapping layout with centered content.
This gives it grid-like behavior while still using Yoga flexbox rules.

#### getColumns

```java
public int getColumns()
```

Returns the configured number of columns.

**Returns:** the column count

#### columns

```java
public Grid columns(int columns)
```

Sets the number of columns used by the grid.

The value must be at least `1`. If the new value matches the current
value, the method returns immediately. Otherwise, layout is marked dirty so
the grid can recompute its layout constraints.

- **`columns`** — the new column count

**Returns:** this grid

**Throws `IllegalArgumentException`:** if `columns` is less than `1`

#### getCellWidth

```java
public LayoutValue getCellWidth()
```

Returns the configured cell width rule.

**Returns:** the cell width layout value

#### cellWidth

```java
public Grid cellWidth(LayoutValue cellWidth)
```

Sets the cell width rule using a `LayoutValue`.

A non-null value is required. This affects how child widths are constrained
during layout application.

- **`cellWidth`** — the new cell width rule

**Returns:** this grid

**Throws `NullPointerException`:** if `cellWidth` is `null`

#### cellWidth

```java
public Grid cellWidth(float cellWidth)
```

Sets the cell width rule using a fixed point value.

- **`cellWidth`** — the fixed cell width in points

**Returns:** this grid

#### getCellHeight

```java
public LayoutValue getCellHeight()
```

Returns the configured cell height rule.

**Returns:** the cell height layout value

#### cellHeight

```java
public Grid cellHeight(LayoutValue cellHeight)
```

Sets the cell height rule using a `LayoutValue`.

A non-null value is required. This affects how child heights are constrained
during layout application.

- **`cellHeight`** — the new cell height rule

**Returns:** this grid

**Throws `NullPointerException`:** if `cellHeight` is `null`

#### cellHeight

```java
public Grid cellHeight(float cellHeight)
```

Sets the cell height rule using a fixed point value.

- **`cellHeight`** — the fixed cell height in points

**Returns:** this grid

#### cellSize

```java
public Grid cellSize(float cellWidth, float cellHeight)
```

Sets both cell width and cell height using fixed point values.

- **`cellWidth`** — the fixed cell width in points
- **`cellHeight`** — the fixed cell height in points

**Returns:** this grid

#### gap

```java
public Grid gap(float gap)
```

Sets both row and column gap to the same value.

- **`gap`** — the uniform gap value

**Returns:** this grid

#### rowGap

```java
public Grid rowGap(float rowGap)
```

Sets the vertical gap between rows.

- **`rowGap`** — the row gap value

**Returns:** this grid

#### columnGap

```java
public Grid columnGap(float columnGap)
```

Sets the horizontal gap between columns.

- **`columnGap`** — the column gap value

**Returns:** this grid

#### applyLayout

```java
    protected void applyLayout()
```

Applies layout constraints for the grid and its children.

This method calculates the effective number of used columns and rows based on
the current child count and configured column limit. It then applies width and
height constraints to each child when cell size rules are not auto.

If the grid is using point-based fixed cell sizes, it also computes and applies
explicit width and height constraints for the grid itself based on the number
of active columns, rows, and configured gaps. If either dimension is auto,
the corresponding size is left automatic.

#### update

```java
    public void update(float delta)
```

Updates the grid and all of its children.

This implementation delegates directly to the superclass update method.

- **`delta`** — the frame delta time

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws the grid and all of its children.

This implementation delegates directly to the superclass draw method.

- **`batch`** — the batch used for rendering

</details>

<a id="type-image"></a>

### Image

[Source](../../src/main/java/valthorne/ui/nodes/Image.java#L54)

`Image` is a simple UI node that renders a `Texture`.
It is intended for displaying static or dynamically swapped images inside
the UI hierarchy.

This node stores a single texture reference and draws it to its current
render bounds using a `TextureBatch`. During creation, it initializes
its layout size to the texture's native width and height and marks itself
as fill-enabled through the layout configuration.

The class is intentionally minimal. It does not manage color tinting, region
selection, scaling policies, or interaction behavior on its own. Instead, it
serves as a lightweight textured UI element that can participate in the
broader layout and node lifecycle system.

##### Example Usage

```java
Texture logo = new Texture("assets/ui/logo.png");

Image image = new Image(logo);
image.getLayout()
     .width(128)
     .height(128);

Texture current = image.getTexture();
image.texture(new Texture("assets/ui/other.png"));

image.update(delta);
image.draw(batch);
```

This example demonstrates the complete usage of the class: construction,
layout sizing, texture access, texture replacement, update, and draw.

<details>
<summary>Image operation reference (9 declarations)</summary>

#### Constructor

```java
public Image(Texture texture)
```

Creates a new image node using the provided texture.

- **`texture`** — the texture to display

#### onCreate

```java
    public void onCreate()
```

Called when this image node is created.

The node's layout is initialized to the texture's native width and height
and then configured to fill according to the layout system.

#### onDestroy

```java
    public void onDestroy()
```

Called when this image node is destroyed.

This implementation currently performs no additional destruction logic, but
the method exists to fulfill the node lifecycle contract.

#### update

```java
    public void update(float delta)
```

Updates this image node.

This implementation currently performs no per-frame logic.

- **`delta`** — the frame delta time

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws the image using the provided `TextureBatch`.

If no texture is assigned, drawing is skipped. Otherwise the texture is drawn
using the node's current render position and size.

- **`batch`** — the batch used for rendering

#### getTexture

```java
public Texture getTexture()
```

Returns the texture currently assigned to this image node.

**Returns:** the current texture

#### texture

```java
public Image texture(Texture texture)
```

Assigns a new texture to this image node.

This method stores the new texture reference and returns this image for
fluent configuration.

- **`texture`** — the new texture to display

**Returns:** this image node

#### color

```java
public Image color(Color color)
```

Sets the color of this image node.

- **`color`** — the color to apply to this image node

**Returns:** this image node, allowing for fluent configuration

#### getColor

```java
public Color getColor()
```

Returns the current color assigned to this image node.

**Returns:** the current color

</details>

<a id="type-label"></a>

### Label

[Source](../../src/main/java/valthorne/ui/nodes/Label.java#L66)

`Label` is a lightweight UI node used to display text.
It resolves its font and optional color from the current style and sizes
itself to fit the text during layout application.

This class is intended to be the basic text-rendering node in the Valthorne UI
system. It stores a text string and, when layout is applied, reads style-driven
values such as:

- `FONT_KEY` for the font used to render the text

- `COLOR_KEY` for the optional text color

If a font is available in the resolved style, the label updates its layout width
and height to match the measured size of its current text. That allows labels to
naturally participate in Yoga layout sizing based on their rendered content.

During drawing, if a font is present, the label renders its text at its render
position. If a color is also present, that color is used; otherwise the font's
default rendering path is used.

The class does not perform its own interaction behavior and is typically used as
a child inside higher-level components such as buttons, tooltips, or form controls.

##### Example Usage

```java
Label label = new Label("Hello World");

String text = label.getText();
label.text("Updated Text");

label.update(delta);
label.draw(batch);
```

This example demonstrates the complete usage of the class: construction with text,
reading text, changing text, update, and draw.

<details>
<summary>Label operation reference (18 declarations)</summary>

#### FONT_KEY

```java
public static final  StyleKey<Font> FONT_KEY
```

Style key used to resolve the font used for rendering the label text.

#### COLOR_KEY

```java
public static final  StyleKey<Color> COLOR_KEY
```

Style key used to resolve the text color used for rendering the label.

#### ALIGNMENT_KEY

```java
public static final  StyleKey<Alignment> ALIGNMENT_KEY
```

Style key used to resolve the alignment for horizontal label coordination

#### Constructor

```java
public Label()
```

Constructs a new default instance of the Label class.

This constructor creates a Label with default configurations.
Default properties such as text, font, color, and alignment can be set or modified using the respective methods provided in the class.

#### Constructor

```java
public Label(String text)
```

Creates a new label with the provided initial text.

If the supplied text is `null`, the label stores an empty string instead.

- **`text`** — the initial label text

#### onCreate

```java
    public void onCreate()
```

Recalculates content dimensions using the currently available font when the
node is created. A missing font or empty text yields zero layout dimensions.

#### onDestroy

```java
    public void onDestroy()
```

Performs no label-specific cleanup because the font and color are borrowed.

#### update

```java
    public void update(float delta)
```

Performs no per-frame animation or input work for this text-only node.

- **`delta`** — elapsed frame time in seconds, unused

#### getText

```java
public String getText()
```

Returns the retained text, normalized to a nonnull string by constructors
and the text setter.

**Returns:** displayed text

#### text

```java
public Label text(String text)
```

Sets the text displayed by this label.

If the supplied text is `null`, an empty string is stored instead.
Changing the text marks layout dirty so size can be recalculated during the
next layout pass.

- **`text`** — the new label text

**Returns:** this label

#### font

```java
public Label font(Font font)
```

Borrows a font reference, immediately recalculates dimensions, and marks
layout dirty. A later resolved style font may replace this reference.

- **`font`** — font to use, or null to produce no text geometry

**Returns:** this label

#### getFont

```java
public Font getFont()
```

Retrieves the font used by this label.

**Returns:** the font currently assigned to this label

#### color

```java
public Label color(Color color)
```

Borrows a color reference for subsequent draws. Null selects the font's default
color path; a later resolved style color can replace this reference.

- **`color`** — draw tint, or null

**Returns:** this label

#### getColor

```java
public Color getColor()
```

Returns the current borrowed tint, whether explicitly assigned or style-resolved.

**Returns:** mutable tint reference, or null for the font's default color

#### alignment

```java
public Label alignment(Alignment alignment)
```

Sets the alignment for this label.

If the specified alignment is not null, it updates the label's alignment to the provided value.

- **`alignment`** — the new alignment to be applied to the label

**Returns:** this label, allowing for method chaining

#### getAlignment

```java
public Alignment getAlignment()
```

Retrieves the alignment currently assigned to this label.

**Returns:** the alignment of the label

#### applyLayout

```java
    protected void applyLayout()
```

Applies nonnull resolved font, color, and alignment values, preserving current
references when a style value is absent. Recalculates exact content width/height
before delegating to base Yoga style application; this replaces explicit layout
dimensions with measured text dimensions.

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws each newline-separated line with independent start/center/end alignment
inside the computed node width, advancing downward from the top text line.
Uses the borrowed tint when present and the font's default draw path otherwise.
Does nothing for a missing font or empty text.

- **`batch`** — prepared texture batch receiving font glyphs

</details>

<a id="type-modal"></a>

### Modal

[Source](../../src/main/java/valthorne/ui/nodes/Modal.java#L90)

`Modal` is a top-level overlay dialog container built on top of
`Panel`. It is designed to display a centered dialog panel above the
normal UI while optionally blocking or intercepting interaction outside the
dialog area.

This class provides modal-style behavior by rendering itself in the UI root's
overlay layer and by managing an internal `Panel` called `dialog`
that holds the actual modal content. The modal itself fills the available
render space, while the dialog panel is centered inside it.

The modal supports:

- a full-screen or full-root modal backdrop

- a dedicated dialog container for child content

- optional closing with Escape

- optional closing when clicking outside the dialog

- theme-resolved backdrop and dialog background drawables

- focus transfer when opened

- overlay-layer integration when attached to a `UIRoot`

Visual appearance is theme-driven:

- `BACKGROUND_KEY` resolves the modal backdrop drawable

- `DIALOG_BACKGROUND_KEY` resolves the dialog panel background drawable

When the modal is open and attached to a root, it is shown through the root's
overlay mechanism. This ensures it renders above the normal UI tree. The modal
may also be used without a root, in which case opening and closing only affect
its visibility flag.

##### Example Usage

```java
Modal modal = new Modal(parentNode);

Label message = new Label("Are you sure?");
modal.content(message)
     .closeOnEscape(true)
     .closeOnOutsideClick(true);

Panel dialog = modal.getDialog();
UINode content = modal.getContent();
UINode owner = modal.getModalParent();

modal.open();

boolean open = modal.isOpen();

modal.toggle();
modal.close();

modal.draw(batch);
```

This example demonstrates the full usage of the class: creation, content
assignment, behavior flags, dialog access, modal lifecycle, and drawing.

<details>
<summary>Modal operation reference (20 declarations)</summary>

#### BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> BACKGROUND_KEY
```

Style key used to resolve the full modal backdrop drawable.

#### DIALOG_BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> DIALOG_BACKGROUND_KEY
```

Style key used to resolve the dialog panel background drawable.

#### Constructor

```java
public Modal(UINode parentNode)
```

Creates a new modal attached to the given parent node.

The supplied parent node is required so the modal can locate its root and
participate in overlay-based behavior when opened. The modal is configured to
be clickable, focusable, and scrollable, and it is laid out to fill its
available space while centering the inner dialog panel.

The dialog panel is configured not to grow or shrink and is added as the sole
child of the modal. The modal starts hidden.

- **`parentNode`** — the node whose UI context owns this modal

**Throws `NullPointerException`:** if `parentNode` is `null`

#### getDialog

```java
public Panel getDialog()
```

Returns the inner dialog panel used to host modal content.

**Returns:** the dialog panel

#### getModalParent

```java
public UINode getModalParent()
```

Returns the parent node associated with this modal.

**Returns:** the owning parent node

#### getContent

```java
public UINode getContent()
```

Returns the content currently attached to the dialog panel.

**Returns:** the current dialog content, or `null` if none exists

#### content

```java
public Modal content(UINode content)
```

Sets the content displayed inside the dialog panel.

If existing content is present, it is removed first. The new content is then
added to the dialog panel if non-null. Layout is marked dirty afterward so the
modal can be relaid out to reflect the new content.

- **`content`** — the new dialog content

**Returns:** this modal

#### isCloseOnEscape

```java
public boolean isCloseOnEscape()
```

Returns whether this modal closes when Escape is pressed.

**Returns:** `true` if Escape closes the modal

#### closeOnEscape

```java
public Modal closeOnEscape(boolean closeOnEscape)
```

Sets whether this modal should close when Escape is pressed.

- **`closeOnEscape`** — whether Escape closes the modal

**Returns:** this modal

#### isCloseOnOutsideClick

```java
public boolean isCloseOnOutsideClick()
```

Returns whether this modal closes when the user clicks outside the dialog.

**Returns:** `true` if outside clicks close the modal

#### closeOnOutsideClick

```java
public Modal closeOnOutsideClick(boolean closeOnOutsideClick)
```

Sets whether this modal should close when the user clicks outside the dialog.

- **`closeOnOutsideClick`** — whether outside clicks close the modal

**Returns:** this modal

#### isOpen

```java
public boolean isOpen()
```

Returns whether this modal is currently open.

The modal is considered open whenever it is visible.

**Returns:** `true` if the modal is open

#### open

```java
public Modal open()
```

Opens this modal.

If the owning parent has a `UIRoot`, the modal is shown through the
root overlay layer and focus is transferred to the modal itself. Otherwise the
modal is simply made visible.

**Returns:** this modal

#### close

```java
public Modal close()
```

Closes this modal.

If the modal is attached to a `UIRoot`, it is removed from the overlay
layer and focus is cleared if the modal currently owns it. Otherwise the modal
is simply made invisible.

**Returns:** this modal

#### toggle

```java
public Modal toggle()
```

Toggles the modal open state.

If the modal is open, it is closed. Otherwise it is opened.

**Returns:** this modal

#### findNodeAt

```java
    public UINode findNodeAt(float x, float y, int requiredBit)
```

Finds the top-most matching node inside this modal at the given coordinates.

If the modal is not visible or is disabled, the search immediately returns
`null`. If the coordinates lie outside the modal bounds, the search also
returns `null`. Otherwise the method delegates to the superclass search.
If no child matches, the modal itself is returned when the required bit is
satisfied, allowing the backdrop to participate in input handling.

- **`x`** — the X coordinate
- **`y`** — the Y coordinate
- **`requiredBit`** — the required interaction bit

**Returns:** the matched node, or `null` if none is found

#### onMousePress

```java
    public void onMousePress(MousePressEvent event)
```

Handles mouse press events for this modal.

If closing on outside click is disabled or no content is currently attached,
nothing happens. Otherwise the event position is optionally converted through
the root viewport when one exists, and the modal closes if the click occurred
outside the dialog panel bounds.

- **`event`** — the mouse press event

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Handles key press events for this modal.

If closing on Escape is enabled and the pressed key is
`Keyboard#ESCAPE`, the modal is closed.

- **`event`** — the key press event

#### applyLayout

```java
    protected void applyLayout()
```

Applies style-driven layout and drawable resolution for this modal.

The current style is resolved and used to update the modal backdrop drawable
and the dialog background drawable. If no style exists, both drawables are
cleared. After resolving visuals, the superclass layout application continues.

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws this modal using the provided `TextureBatch`.

If the modal is not visible, rendering is skipped. Otherwise the modal
backdrop is drawn first, then the dialog background, and finally the dialog
panel itself and its contents.

- **`batch`** — the batch used for rendering

</details>

<a id="type-panel"></a>

### Panel

[Source](../../src/main/java/valthorne/ui/nodes/Panel.java#L73)

`Panel` is the basic drawable container node in the Valthorne UI system.
It extends `UIContainer`, which means it can hold and manage child
`valthorne.ui.UINode` instances while also optionally rendering a styled
background behind them.

This class is intentionally simple and acts as a foundational building block
for many other UI components. A panel does not define any custom input behavior
on its own. Instead, it provides a themed rectangular surface that can contain
child nodes and participate in the layout, update, and draw lifecycle shared by
all UI nodes.

The panel's main visual feature is its optional background drawable, resolved
from the panel's style using `BACKGROUND_KEY`. If a theme and resolved
style are available, the background is drawn to the panel's current render
bounds before any children are rendered.
The drawable is borrowed from the resolved style and is neither copied nor
disposed by the panel. A missing background leaves the container transparent;
children still render. Child traversal uses the root's shared backend dispatch
when attached, allowing NanoVG and texture-based children to coexist.

Because `Panel` extends `UIContainer`, it is commonly used as:

- a generic grouping container

- a background surface for other controls

- a base class for more specialized UI widgets

- a layout wrapper for content blocks

##### Example Usage

```java
Panel panel = new Panel();

panel.getLayout()
     .width(300)
     .height(200)
     .padding(12);

panel.add(new Label("Settings"));
panel.add(new Button("Apply"));

root.add(panel);
// Let the root's normal layout, update and draw lifecycle visit the panel.
```

This example demonstrates the complete intended use of the class: creating a
panel, assigning layout values, adding child nodes and attaching it for root
traversal. Render on the graphics thread with the root's active batch and context.

<details>
<summary>Panel operation reference (3 declarations)</summary>

#### BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> BACKGROUND_KEY
```

Style key used to resolve the panel background drawable.
Registered globally as "background" with no default. A null resolved value
paints no panel surface. The drawable receives the full render rectangle,
including the area behind layout padding, and remains owned by its provider.

#### update

```java
    public void update(float delta)
```

Updates this panel and all of its children.

This implementation delegates directly to the superclass update logic so
child nodes continue to receive their normal update calls.
This panel adds no animation or background-specific update work. Structural
changes to children should follow the inherited container lifecycle policy.

- **`delta`** — the elapsed frame time in seconds

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws this panel and all of its children.

If the panel has an assigned theme and a resolved style, this method first
attempts to resolve a background drawable using `BACKGROUND_KEY`.
If one exists, it is drawn across the panel's full render bounds. After that,
the container's children are drawn through the superclass implementation.
Padding does not inset the background. Rendering does not resize the panel
from drawable dimensions or add a clip of its own. Root traversal supplies
visibility checks and rendering scopes; callers invoking draw directly must
provide a prepared batch. A background failure propagates before children
are visited, with no resource disposal by this method.

- **`batch`** — the texture batch used for rendering

</details>

<a id="type-progressbar"></a>

### ProgressBar

[Source](../../src/main/java/valthorne/ui/nodes/ProgressBar.java#L84)

`ProgressBar` is a visual UI component used to display progress between
a configured minimum and maximum value. It supports smooth animated transitions
between progress values, optional percentage text display, and both horizontal
and vertical fill directions.

The control is theme-driven and resolves its visuals from the active style.
It supports:

- a background drawable for the full bar area

- a foreground drawable for the filled portion

- a font used to render percentage text when enabled

The progress bar stores two progress values:

- `progress`, which is the actual target progress value

- `displayedProgress`, which is the animated visual value

Each frame, the displayed value interpolates toward the target value using
`MathUtils#lerp(float, float, float)`, producing a smooth visual fill
transition instead of an immediate jump.

When percentage display is enabled and a font is available, the bar caches
a percentage string based on the animated value and draws it explicitly.
Disabling percentage display clears the borrowed font's legacy text property.
Drawable and font resources are borrowed from resolved style and are not disposed
here. Although this class extends Panel, its update and draw overrides do not
traverse children.

##### Example Usage

```java
ProgressBar bar = new ProgressBar(0f, 100f);

bar.getLayout()
   .width(240)
   .height(28);

bar.progress(45f)
   .displayPercentage(true)
   .horizontal(true);

float current = bar.getProgress();
boolean vertical = bar.isVertical();
boolean showingPercent = bar.isDisplayPercentage();
Font font = bar.getFont();

root.add(bar);
// The root's normal update and draw lifecycle visits the progress bar.
```

This example demonstrates the complete intended use of the class: construction,
sizing, progress updates, percentage display, orientation control, state queries,
update, and draw.

<details>
<summary>ProgressBar operation reference (15 declarations)</summary>

#### BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> BACKGROUND_KEY
```

Style key used to resolve the background drawable for the full progress bar area.

#### FOREGROUND_KEY

```java
public static final  StyleKey<Drawable> FOREGROUND_KEY
```

Style key used to resolve the foreground drawable for the filled portion of the bar.

#### FONT_KEY

```java
public static final  StyleKey<Font> FONT_KEY
```

Style key used to resolve the font used for percentage text rendering.

#### Constructor

```java
public ProgressBar(float min, float max)
```

Creates a new progress bar with the supplied minimum and maximum bounds.

The initial target and displayed progress values are both set to the minimum,
meaning the bar starts completely empty relative to the configured range.
Bounds are stored without validation or reordering; supply finite min and max
with max greater than min for ordinary progress. Equal bounds always display
zero percent. Initial orientation is horizontal and percentage text is disabled.

- **`min`** — the minimum allowed progress value
- **`max`** — the maximum allowed progress value

#### update

```java
    public void update(float delta)
```

Updates the visual state of this progress bar.

The displayed progress is smoothly interpolated toward the target progress.
Interpolation uses the factor 1 - exp(-max(0, delta) * 20), giving exponential
smoothing for finite frame intervals. Negative delta produces no movement;
NaN is not rejected. If percentage display and a font are available, the
cached string is refreshed to two decimal places using the default locale.
This override does not call the inherited child-update implementation.

- **`delta`** — the elapsed frame time in seconds

#### progress

```java
public ProgressBar progress(float progress)
```

Sets the target progress value for this bar.

The value is clamped into the configured `[min, max]` range. If percentage
display is enabled and a font is available, the displayed text is also updated.
The animated value does not jump to the new target, so that text still
reflects the current displayed value until subsequent updates advance it.

- **`progress`** — the new target progress value

**Returns:** this progress bar

#### getProgress

```java
public float getProgress()
```

Returns the current target progress value.

**Returns:** the target progress value

#### displayPercentage

```java
public ProgressBar displayPercentage(boolean displayPercentage)
```

Enables or disables percentage text display.

With a font available, enabling refreshes the cached percentage string.
Disabling instead clears the font's legacy text property, which can affect
another user of that shared Font. No layout invalidation is performed.

- **`displayPercentage`** — whether percentage text should be shown

**Returns:** this progress bar

#### isDisplayPercentage

```java
public boolean isDisplayPercentage()
```

Returns whether percentage text display is enabled.

**Returns:** `true` if percentage text should be shown

#### isVertical

```java
public boolean isVertical()
```

Returns whether this progress bar is currently configured for vertical filling.

**Returns:** `true` if the bar fills vertically

#### vertical

```java
public ProgressBar vertical(boolean vertical)
```

Sets whether this progress bar should fill vertically.

- **`vertical`** — `true` for vertical fill, `false` for horizontal fill

**Returns:** this progress bar

#### horizontal

```java
public ProgressBar horizontal(boolean horizontal)
```

Sets whether this progress bar should fill horizontally.

Internally this is implemented by storing the inverse into the
`vertical` flag.

- **`horizontal`** — `true` for horizontal fill, `false` for vertical fill

**Returns:** this progress bar

#### getFont

```java
public Font getFont()
```

Returns the font currently resolved for this progress bar.
This is a borrowed reference refreshed during layout, not an owned copy.

**Returns:** the resolved font, or `null` if none is available

#### applyLayout

```java
    protected void applyLayout()
```

Applies style-driven layout data and visual resources for this progress bar.

The current style is resolved and used to update the background drawable,
foreground drawable, and optional font. With percentage display enabled,
the cached string is refreshed; otherwise a present font's legacy text is
cleared. If no style exists,
all resolved visual references are cleared.

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws this progress bar using the provided texture batch.

The background is drawn first over the full bounds. The foreground is then
drawn using the current fill percentage, either horizontally or vertically.
If percentage display is enabled and a font is available, centered text is
rendered on top of the bar.
Horizontal fill grows from the left; vertical fill grows from the bottom
in render coordinates. Label.COLOR_KEY supplies text color when present,
otherwise white is used. The cached percentage is passed directly to Font.draw.
This method does not call Panel.draw or render children, and it relies on
the root for visibility checks and a prepared batch.

- **`batch`** — the texture batch used for rendering

</details>

<a id="type-scrollpanel"></a>

### ScrollPanel

[Source](../../src/main/java/valthorne/ui/nodes/ScrollPanel.java#L101)

`ScrollPanel` is a container node that provides scrollable viewing of a single
content node. It supports horizontal scrolling, vertical scrolling, optional
scrollbar rendering, draggable scrollbar thumbs, mouse wheel scrolling, scissored
content rendering, and style-driven visuals for the panel background and both
scrollbars.

This class is intended to act as a clipped viewport over another `UINode`.
A content node is stored internally and rendered inside a scissor region while the
panel applies a translation based on the current scroll amounts. If the content is
larger than the visible area, horizontal and/or vertical scrollbar metrics are
calculated and scrollbar visuals can be drawn.

The panel supports:

- horizontal scrolling

- vertical scrolling

- enabling or disabling either scroll direction independently

- showing or hiding horizontal and vertical scrollbars independently

- mouse wheel scrolling

- dragging scrollbar thumbs

- viewport-aware mouse coordinate conversion

- automatic scroll clamping after layout

- style-driven backgrounds and scrollbar visuals

The content node defaults to a `Panel`, but callers may replace it using
`setContent(UINode)`. Only a single content node is managed directly by this
class. Hit detection for children is adjusted using the current scroll values so
interaction can still target the proper child positions even though rendering is
translated.

Scrollbar geometry is computed lazily through an internal `ScrollMetrics`
structure. This includes bar bounds, thumb bounds, visibility flags, and the space
consumed by active scrollbars.

##### Example Usage

```java
ScrollPanel panel = new ScrollPanel()
        .horizontal(true)
        .vertical(true)
        .horizontalBar(true)
        .verticalBar(true)
        .scrollSpeed(48f);

Grid content = new Grid()
        .columns(4)
        .cellSize(64, 64)
        .gap(8);

panel.setContent(content);

panel.scroll(0f, 0f);
panel.scrollBy(24f, 48f);

float x = panel.getScrollX();
float y = panel.getScrollY();
float maxX = panel.getMaxScrollX();
float maxY = panel.getMaxScrollY();

panel.update(delta);
panel.draw(batch);
```

This example demonstrates the complete usage of the class: creating the panel,
configuring scroll directions and bars, assigning content, reading scroll values,
adjusting scroll positions, and drawing the result.

<details>
<summary>ScrollPanel operation reference (40 declarations)</summary>

#### BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> BACKGROUND_KEY
```

Style key used to resolve the panel background drawable.

#### HORIZONTAL_BAR_BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> HORIZONTAL_BAR_BACKGROUND_KEY
```

Style key used to resolve the horizontal scrollbar background drawable.

#### HORIZONTAL_BAR_FOREGROUND_KEY

```java
public static final  StyleKey<Drawable> HORIZONTAL_BAR_FOREGROUND_KEY
```

Style key used to resolve the horizontal scrollbar thumb drawable.

#### VERTICAL_BAR_BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> VERTICAL_BAR_BACKGROUND_KEY
```

Style key used to resolve the vertical scrollbar background drawable.

#### VERTICAL_BAR_FOREGROUND_KEY

```java
public static final  StyleKey<Drawable> VERTICAL_BAR_FOREGROUND_KEY
```

Style key used to resolve the vertical scrollbar thumb drawable.

#### HORIZONTAL_BAR_HEIGHT_KEY

```java
public static final  StyleKey<Float> HORIZONTAL_BAR_HEIGHT_KEY
```

Style key used to resolve the height of the horizontal scrollbar.

#### VERTICAL_BAR_WIDTH_KEY

```java
public static final  StyleKey<Float> VERTICAL_BAR_WIDTH_KEY
```

Style key used to resolve the width of the vertical scrollbar.

#### BAR_PADDING_KEY

```java
public static final  StyleKey<Float> BAR_PADDING_KEY
```

Style key used to resolve padding around scrollbar bars.

#### MIN_THUMB_SIZE_KEY

```java
public static final  StyleKey<Float> MIN_THUMB_SIZE_KEY
```

Style key used to resolve the minimum size of scrollbar thumbs.

#### Constructor

```java
public ScrollPanel()
```

Creates a new scroll panel with both scroll directions enabled, both scrollbars
allowed to render, a default content panel, and scrollable interaction enabled.

The constructor also initializes the style cache immediately and adds the
default content node as a child.

#### getContent

```java
public UINode getContent()
```

Returns the current content node displayed inside this scroll panel.

**Returns:** the current content node

#### setContent

```java
public void setContent(UINode child)
```

Replaces the current content node with the supplied child node.

The existing content node is removed first. If the supplied child is
`null`, the method returns after removing the old content. Otherwise the
new child becomes the active content node and is added as a child of the panel.

- **`child`** — the new content node

#### scrollSpeed

```java
public ScrollPanel scrollSpeed(float speed)
```

Sets the scroll speed multiplier used by mouse wheel scrolling.

- **`speed`** — the new scroll speed multiplier

**Returns:** this scroll panel

#### isHorizontalEnabled

```java
public boolean isHorizontalEnabled()
```

Returns whether horizontal scrolling is currently enabled.

**Returns:** `true` if horizontal scrolling is enabled

#### horizontal

```java
public ScrollPanel horizontal(boolean horizontal)
```

Enables or disables horizontal scrolling.

Disabling horizontal scrolling resets horizontal scroll positions and cancels
active horizontal thumb dragging. Enabling it reclamps the current and target
horizontal scroll values to the valid range.

- **`horizontal`** — whether horizontal scrolling should be enabled

**Returns:** this scroll panel

#### isVerticalEnabled

```java
public boolean isVerticalEnabled()
```

Returns whether vertical scrolling is currently enabled.

**Returns:** `true` if vertical scrolling is enabled

#### vertical

```java
public ScrollPanel vertical(boolean vertical)
```

Enables or disables vertical scrolling.

Disabling vertical scrolling resets vertical scroll positions and cancels active
vertical thumb dragging. Enabling it reclamps the current and target vertical
scroll values to the valid range.

- **`vertical`** — whether vertical scrolling should be enabled

**Returns:** this scroll panel

#### isHorizontalBarVisible

```java
public boolean isHorizontalBarVisible()
```

Returns whether horizontal scrollbar drawing is enabled.

**Returns:** `true` if horizontal scrollbar drawing is enabled

#### horizontalBar

```java
public ScrollPanel horizontalBar(boolean drawHorizontalBar)
```

Enables or disables horizontal scrollbar drawing.

- **`drawHorizontalBar`** — whether the horizontal scrollbar should be drawn when needed

**Returns:** this scroll panel

#### isVerticalBarVisible

```java
public boolean isVerticalBarVisible()
```

Returns whether vertical scrollbar drawing is enabled.

**Returns:** `true` if vertical scrollbar drawing is enabled

#### verticalBar

```java
public ScrollPanel verticalBar(boolean drawVerticalBar)
```

Enables or disables vertical scrollbar drawing.

- **`drawVerticalBar`** — whether the vertical scrollbar should be drawn when needed

**Returns:** this scroll panel

#### getScrollX

```java
public float getScrollX()
```

Returns the current horizontal scroll offset.

**Returns:** the current horizontal scroll offset

#### scrollX

```java
public ScrollPanel scrollX(float scrollX)
```

Sets the horizontal scroll offset immediately.

If horizontal scrolling is disabled, the value is forced to `0`. Otherwise
the value is clamped into the valid horizontal scroll range and applied to both
the current and target horizontal scroll positions.

- **`scrollX`** — the new horizontal scroll offset

**Returns:** this scroll panel

#### getScrollY

```java
public float getScrollY()
```

Returns the current vertical scroll offset.

**Returns:** the current vertical scroll offset

#### scrollY

```java
public ScrollPanel scrollY(float scrollY)
```

Sets the vertical scroll offset immediately.

If vertical scrolling is disabled, the value is forced to `0`. Otherwise
the value is clamped into the valid vertical scroll range and applied to both
the current and target vertical scroll positions.

- **`scrollY`** — the new vertical scroll offset

**Returns:** this scroll panel

#### scroll

```java
public ScrollPanel scroll(float scrollX, float scrollY)
```

Sets both horizontal and vertical scroll offsets immediately.

- **`scrollX`** — the new horizontal scroll offset
- **`scrollY`** — the new vertical scroll offset

**Returns:** this scroll panel

#### scrollBy

```java
public ScrollPanel scrollBy(float dx, float dy)
```

Adjusts the current scroll offsets by the supplied deltas.

Only enabled directions are affected. New values are clamped to their valid
ranges and applied to both current and target scroll values.

- **`dx`** — horizontal scroll delta
- **`dy`** — vertical scroll delta

**Returns:** this scroll panel

#### getMaxScrollX

```java
public float getMaxScrollX()
```

Returns the maximum horizontal scroll offset based on content width, panel width,
and active vertical scrollbar width contribution.

**Returns:** the maximum horizontal scroll amount

#### getMaxScrollY

```java
public float getMaxScrollY()
```

Returns the maximum vertical scroll offset based on content height, panel height,
and active horizontal scrollbar height contribution.

**Returns:** the maximum vertical scroll amount

#### invalidateStyleTree

```java
    protected void invalidateStyleTree()
```

Invalidates this node's style tree and refreshes cached style-driven values.

This ensures scrollbar and background drawables, sizes, and padding are updated
whenever styles are invalidated.

#### transformChildHitX

```java
    protected float transformChildHitX(float x)
```

Transforms child hit-test X coordinates by the current horizontal scroll offset.

This allows hit testing against children to line up with the translated content.

- **`x`** — the incoming hit-test X coordinate

**Returns:** the transformed child hit-test X coordinate

#### transformChildHitY

```java
    protected float transformChildHitY(float y)
```

Transforms child hit-test Y coordinates by the current vertical scroll offset.

This allows hit testing against children to line up with the translated content.

- **`y`** — the incoming hit-test Y coordinate

**Returns:** the transformed child hit-test Y coordinate

#### findNodeAt

```java
    public UINode findNodeAt(float x, float y, int requiredBit)
```

Finds the top-most node at the supplied coordinates that satisfies the required bit.

The method first rejects invisible or disabled states, then rejects coordinates
outside the panel bounds. Scrollbar regions are checked before delegating to
normal child hit testing so the panel itself can intercept input on visible
scrollbar bars and thumbs.

- **`x`** — the X coordinate to test
- **`y`** — the Y coordinate to test
- **`requiredBit`** — the required interaction bit

**Returns:** the matched node, or `null` if no match exists

#### onPointerCancel

```java
    public void onPointerCancel()
```

Cancel scrollbar dragging without a synthetic release.

#### onMouseScroll

```java
    public void onMouseScroll(MouseScrollEvent event)
```

Computes clamped offsets using the enabled axes, scroll ranges, and speed,
then applies them to the panel. The shared wheel policy consumes events only
when an offset changes, allowing boundary scrolling to propagate to ancestors.

- **`event`** — routed wheel event with precise fractional deltas

#### onMousePress

```java
    public void onMousePress(MousePressEvent event)
```

Handles mouse press interaction for scrollbar dragging.

The method optionally converts mouse coordinates through the root viewport,
resets both dragging flags, and then starts dragging the appropriate scrollbar
if the mouse press occurred inside a visible horizontal or vertical scrollbar.

- **`event`** — the mouse press event

#### onMouseDrag

```java
    public void onMouseDrag(MouseDragEvent event)
```

Handles dragging of horizontal and vertical scrollbar thumbs.

Mouse movement is measured either in world space or raw coordinates depending on
whether a viewport is active. That movement is then converted into scroll delta
based on the ratio between maximum content scroll and maximum thumb travel.

- **`event`** — the mouse drag event

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Handles mouse release by ending any active scrollbar dragging.

- **`event`** — the mouse release event

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws the scroll panel, clipped content, and any visible scrollbars.

The panel background is drawn first if present. Then the content is rendered
inside a scissor region while translated by the current scroll amounts.
Horizontal and vertical scrollbars are then drawn on top if needed and enabled.

- **`batch`** — the texture batch used for rendering

#### afterLayout

```java
    protected void afterLayout()
```

Performs post-layout scroll clamping.

After layout changes, both current and target scroll values are clamped into
their valid ranges, or reset to zero when the corresponding direction is disabled.

</details>

<a id="type-scrollpanel-scrollmetrics"></a>

### ScrollPanel.ScrollMetrics — internal support type

[Source](../../src/main/java/valthorne/ui/nodes/ScrollPanel.java#L919)

`ScrollMetrics` is a small reusable data holder that stores the computed
geometry and visibility state of the horizontal and vertical scrollbars.

It is intentionally private and static because it only exists to support the
internal layout and rendering calculations of `ScrollPanel`.

##### Example Usage

```java
// Internal to ScrollPanel:
ScrollMetrics metrics = getScrollMetrics();

if (metrics.showHorizontalBar) {
    // draw horizontal track and thumb
}

if (metrics.showVerticalBar) {
    // draw vertical track and thumb
}
```

<a id="type-slider"></a>

### Slider

[Source](../../src/main/java/valthorne/ui/nodes/Slider.java#L106)

`Slider` is an interactive UI control used to select a numeric value within
a configurable range. It supports mouse dragging, mouse wheel adjustment,
keyboard adjustment, horizontal and vertical orientation, optional snapping
through step size, and theme-driven rendering for the track, fill, and thumb.

This control is built on top of `Panel`, which means it participates in
the standard Valthorne UI node lifecycle, theming system, layout system, and
rendering pipeline. The slider stores a minimum value, maximum value, current
value, and an optional step size that determines how values are snapped during
changes.

Rendering is entirely style-driven. The slider resolves:

- a track drawable used as the full background of the slider path

- a fill drawable used to show the selected portion of the slider

- a thumb drawable used for the draggable handle

- track and thumb sizing values from style keys

- an optional action from style when no explicit action is assigned

Interaction behavior includes:

- mouse press begins dragging

- mouse drag moves the value along the track

- mouse release stops dragging

- mouse scroll increments or decrements the value

- arrow keys, Home, and End adjust the value from the keyboard

The slider can operate either horizontally or vertically. In horizontal mode,
the value increases from left to right. In vertical mode, the value increases
from bottom to top based on the current implementation of thumb position and
drag delta handling.

A slider may optionally perform a `NodeAction` whenever its value changes.
The action resolution order is:

- use the explicitly assigned action if one exists

- otherwise resolve `ACTION_KEY` from the current style

##### Example Usage

```java
Slider slider = new Slider(0f, 100f, 25f);

slider.getLayout()
      .width(220)
      .height(24);

slider.stepSize(5f)
      .horizontal(true)
      .action(s -> {
          System.out.println("Slider value: " + s.getValue());
      });

slider.increment();
slider.decrement();
slider.percent(0.5f);

float value = slider.getValue();
float percent = slider.getPercent();
boolean dragging = slider.isDragging();

slider.update(delta);
slider.draw(batch);
```

This example demonstrates the complete use of the class: construction, layout,
range management, stepping, orientation, action binding, querying state,
update, and draw.

<details>
<summary>Slider operation reference (54 declarations)</summary>

#### TRACK_KEY

```java
public static final  StyleKey<Drawable> TRACK_KEY
```

Style key used to resolve the slider track drawable.

#### FILL_KEY

```java
public static final  StyleKey<Drawable> FILL_KEY
```

Style key used to resolve the slider fill drawable.

#### THUMB_KEY

```java
public static final  StyleKey<Drawable> THUMB_KEY
```

Style key used to resolve the slider thumb drawable.

#### TRACK_HEIGHT_KEY

```java
public static final  StyleKey<Float> TRACK_HEIGHT_KEY
```

Style key used to resolve the slider track thickness.

#### THUMB_WIDTH_KEY

```java
public static final  StyleKey<Float> THUMB_WIDTH_KEY
```

Style key used to resolve the slider thumb width.

#### THUMB_HEIGHT_KEY

```java
public static final  StyleKey<Float> THUMB_HEIGHT_KEY
```

Style key used to resolve the slider thumb height.

#### THUMB_OFFSET_Y_KEY

```java
public static final  StyleKey<Float> THUMB_OFFSET_Y_KEY
```

Style key used to resolve the vertical thumb offset in horizontal mode.

#### ACTION_KEY

```java
public static final  StyleKey<NodeAction<Slider>> ACTION_KEY
```

Style key used to resolve a fallback slider action when no explicit action is assigned.

#### Constructor

```java
public Slider()
```

Creates a new slider with a default range of `0` to `1`
and an initial value of `0`.

#### Constructor

```java
public Slider(float min, float max, float value)
```

Creates a new slider with the supplied range and initial value.

No explicit action is assigned.

- **`min`** — the minimum value
- **`max`** — the maximum value
- **`value`** — the initial value

#### Constructor

```java
public Slider(float min, float max, float value, NodeAction<Slider> action)
```

Creates a new slider with the supplied range, initial value, and action.

The maximum is clamped so it is never lower than the minimum. The initial
value is clamped into the valid range. The slider is configured as clickable,
focusable, draggable-capable, and scrollable. A default layout size is also set.

- **`min`** — the minimum value
- **`max`** — the maximum value
- **`value`** — the initial value
- **`action`** — the explicit action to perform when the value changes

#### action

```java
public Slider action(NodeAction<Slider> action)
```

Assigns an explicit action to this slider.

- **`action`** — the action to perform when the value changes

**Returns:** this slider

#### getAction

```java
public NodeAction<Slider> getAction()
```

Returns the explicitly assigned action.

**Returns:** the explicit action, or `null` if none is assigned

#### getMin

```java
public float getMin()
```

Returns the minimum allowed value.

**Returns:** the minimum value

#### min

```java
public Slider min(float min)
```

Sets the minimum allowed value.

If the current maximum becomes invalid, it is raised to match the new minimum.
The current slider value is then clamped into the adjusted range.

- **`min`** — the new minimum value

**Returns:** this slider

#### getMax

```java
public float getMax()
```

Returns the maximum allowed value.

**Returns:** the maximum value

#### max

```java
public Slider max(float max)
```

Sets the maximum allowed value.

The maximum is never allowed to fall below the current minimum. The current
value is clamped into the resulting range.

- **`max`** — the new maximum value

**Returns:** this slider

#### range

```java
public Slider range(float min, float max)
```

Sets both the minimum and maximum values at once.

The maximum is forced to be at least as large as the minimum, and the current
value is clamped into the new range.

- **`min`** — the new minimum value
- **`max`** — the new maximum value

**Returns:** this slider

#### getValue

```java
public float getValue()
```

Returns the current slider value.

**Returns:** the current value

#### value

```java
public Slider value(float value)
```

Sets the current slider value.

The value is clamped into the valid range and then passed through
`snap(float)` so step-based snapping is applied when enabled.

- **`value`** — the new value

**Returns:** this slider

#### getStepSize

```java
public float getStepSize()
```

Returns the configured step size.

**Returns:** the step size, or `0` when free movement is enabled

#### stepSize

```java
public Slider stepSize(float stepSize)
```

Sets the step size used for snapping.

Negative values are treated as `0`. After the new step size is applied,
the current value is re-snapped to ensure consistency.

- **`stepSize`** — the new step size

**Returns:** this slider

#### increment

```java
public Slider increment()
```

Increments the slider value by one logical step.

If a step size is configured, that step is used. Otherwise, a fallback increment
equal to one hundredth of the range is used.

**Returns:** this slider

#### decrement

```java
public Slider decrement()
```

Decrements the slider value by one logical step.

If a step size is configured, that step is used. Otherwise, a fallback decrement
equal to one hundredth of the range is used.

**Returns:** this slider

#### isVertical

```java
public boolean isVertical()
```

Returns whether the slider is currently vertical.

**Returns:** `true` if vertical

#### vertical

```java
public Slider vertical(boolean vertical)
```

Sets whether the slider should use vertical orientation.

When switching to vertical mode, default auto layout sizing is adjusted so the
width matches the larger of the track height and thumb width, while height
defaults to `160`. In horizontal mode, auto sizing defaults back to a
width of `160` and a height based on the larger of the track height and
thumb height.

- **`vertical`** — whether the slider should be vertical

**Returns:** this slider

#### horizontal

```java
public Slider horizontal(boolean horizontal)
```

Sets whether the slider should use horizontal orientation.

- **`horizontal`** — whether the slider should be horizontal

**Returns:** this slider

#### getTrackHeight

```java
public float getTrackHeight()
```

Returns the current track thickness.

**Returns:** the track height value

#### trackHeight

```java
public Slider trackHeight(float trackHeight)
```

Sets the track thickness.

Negative values are clamped to `0`. Layout is then marked dirty.

- **`trackHeight`** — the new track thickness

**Returns:** this slider

#### getThumbWidth

```java
public float getThumbWidth()
```

Returns the current thumb width.

**Returns:** the thumb width

#### thumbWidth

```java
public Slider thumbWidth(float thumbWidth)
```

Sets the thumb width.

Negative values are clamped to `0`. Layout is then marked dirty.

- **`thumbWidth`** — the new thumb width

**Returns:** this slider

#### getThumbHeight

```java
public float getThumbHeight()
```

Returns the current thumb height.

**Returns:** the thumb height

#### thumbHeight

```java
public Slider thumbHeight(float thumbHeight)
```

Sets the thumb height.

Negative values are clamped to `0`. Layout is then marked dirty.

- **`thumbHeight`** — the new thumb height

**Returns:** this slider

#### thumbSize

```java
public Slider thumbSize(float width, float height)
```

Sets both thumb width and thumb height at once.

Negative values are clamped to `0`. Layout is then marked dirty.

- **`width`** — the new thumb width
- **`height`** — the new thumb height

**Returns:** this slider

#### getThumbOffsetY

```java
public float getThumbOffsetY()
```

Returns the current vertical thumb offset used in horizontal mode.

**Returns:** the thumb Y offset

#### thumbOffsetY

```java
public Slider thumbOffsetY(float thumbOffsetY)
```

Sets the vertical thumb offset used in horizontal mode.

- **`thumbOffsetY`** — the new thumb Y offset

**Returns:** this slider

#### getPercent

```java
public float getPercent()
```

Returns the current value as a normalized percentage in the range `[0, 1]`.

If the slider range is zero or negative, the method returns `0`.

**Returns:** the normalized percentage

#### percent

```java
public Slider percent(float percent)
```

Sets the slider value using a normalized percentage in the range `[0, 1]`.

- **`percent`** — the normalized percentage

**Returns:** this slider

#### isDragging

```java
public boolean isDragging()
```

Returns whether the slider is currently being dragged.

**Returns:** `true` if dragging is active

#### getTrackX

```java
public float getTrackX()
```

Returns the X position of the rendered track.

In vertical mode, the track is centered horizontally within the full slider
width. In horizontal mode, the track begins at the slider's render X.

**Returns:** the track X position

#### getTrackY

```java
public float getTrackY()
```

Returns the Y position of the rendered track.

In vertical mode, the track begins at the slider's render Y. In horizontal
mode, the track is vertically centered within the slider bounds.

**Returns:** the track Y position

#### getTrackWidth

```java
public float getTrackWidth()
```

Returns the rendered track width.

In vertical mode, this is the track thickness. In horizontal mode, it is the
full slider width.

**Returns:** the track width

#### getTrackActualHeight

```java
public float getTrackActualHeight()
```

Returns the rendered track height.

In vertical mode, this is the full slider height. In horizontal mode, it is
the configured track thickness.

**Returns:** the track height

#### getThumbX

```java
public float getThumbX()
```

Returns the current X position of the thumb.

In vertical mode, the thumb is horizontally centered. In horizontal mode,
the thumb position is computed from the current value percentage and the
available track width minus thumb width.

**Returns:** the thumb X position

#### getThumbY

```java
public float getThumbY()
```

Returns the current Y position of the thumb.

In vertical mode, the thumb position is computed from the current value
percentage and the available track height minus thumb height. In horizontal
mode, the thumb is vertically centered with the configured offset applied.

**Returns:** the thumb Y position

#### getFillWidth

```java
public float getFillWidth()
```

Returns the width of the filled portion of the slider in horizontal mode.

In vertical mode, this simply returns the track width.

**Returns:** the fill width

#### getFillHeight

```java
public float getFillHeight()
```

Returns the height of the filled portion of the slider in vertical mode.

In horizontal mode, this simply returns the track height.

**Returns:** the fill height

#### onMousePress

```java
    public void onMousePress(MousePressEvent event)
```

Handles the start of slider dragging.

Dragging state and pressed state are enabled. The starting mouse position is
recorded either in raw screen space or viewport world space depending on
whether a viewport is active.

- **`event`** — the mouse press event

#### onMouseDrag

```java
    public void onMouseDrag(MouseDragEvent event)
```

Handles slider dragging while the mouse moves.

If dragging is not active, the method returns immediately. Otherwise the mouse
movement delta is computed either in world space or screen space. The slider
value is then changed proportionally along the relevant axis based on track
size and range span. If the value changes, the resolved action is fired.

- **`event`** — the mouse drag event

#### onMouseScroll

```java
    public void onMouseScroll(MouseScrollEvent event)
```

Handles mouse wheel adjustment for this slider.

The value is increased or decreased by one logical step. In vertical mode,
the Y scroll offset is used. In horizontal mode, Y is preferred, with X used
as a fallback when Y is zero. If the value changes, the resolved action is fired.

- **`event`** — the mouse scroll event

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Handles the end of slider dragging.

Dragging state and pressed state are cleared.

- **`event`** — the mouse release event

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Handles keyboard interaction for this slider.

If the slider is disabled, no action is taken. Otherwise arrow keys adjust the
value according to orientation, and Home/End jump directly to the minimum or
maximum value. If the value changes, the resolved action is fired.

- **`event`** — the key press event

#### applyLayout

```java
    protected void applyLayout()
```

Applies layout and style-driven visual configuration for this slider.

The current style is resolved and used to update the track height, thumb width,
thumb height, and thumb offset values when present. Auto layout defaults are
then applied based on the current orientation. Finally, the superclass layout
application continues.

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws the slider using the provided batch.

The current style is resolved for the track, fill, and thumb drawables.
The slider then computes current geometry for each visual component and
renders them in order: track first, fill second, and thumb last.

- **`batch`** — the texture batch used for rendering

</details>

<a id="type-sluglabel"></a>

### SlugLabel

[Source](../../src/main/java/valthorne/ui/nodes/SlugLabel.java#L29)

Retained curve-rendered text for regular or NanoVG UI containers. Text and size changes
refresh a reusable glyph layout and the node's measured dimensions; each draw renders
live curves through a shared Slug batch. The font and renderer are borrowed and must
outlive all labels using them. GPU operations require their owning GL context thread.

```java
SlugLabel label = new SlugLabel(font, slugBatch, "Score: 0", 24f);
container.add(label);
label.text("Score: 100").color(Color.WHITE);
```

The shared batch must be idle when this node draws. Container clipping is forwarded
for glyph rejection and CPU quad cropping in the text's world-coordinate space.

<details>
<summary>SlugLabel operation reference (10 declarations)</summary>

#### Constructor

```java
public SlugLabel(SlugFont font, SlugBatch renderer, String text, float size)
```

Creates retained text and initializes measured node dimensions from its layout.

- **`font`** — borrowed font supplying live curve data and metrics
- **`renderer`** — borrowed batch, idle whenever this label draws
- **`text`** — initial text; null is normalized to empty
- **`size`** — finite nonnegative world units per em

**Throws `NullPointerException`:** if font or renderer is null

**Throws `IllegalArgumentException`:** if size is negative or nonfinite

#### text

```java
public SlugLabel text(String text)
```

Changes text and updates layout dimensions; unchanged content reuses glyph arrays.

- **`text`** — replacement text, with null treated as empty

**Returns:** this label for configuration chaining

#### text

```java
public String text()
```

Reads the normalized source string retained by the glyph layout.

**Returns:** current text, never null

#### size

```java
public SlugLabel size(float size)
```

Changes the em scale, rebuilds glyph positions when necessary, and updates dimensions.

- **`size`** — finite nonnegative world units per em

**Returns:** this label

**Throws `IllegalArgumentException`:** if size is negative or nonfinite

#### size

```java
public float size()
```

Reads the retained font scale, which is independent of the node's layout height.

**Returns:** world units per em

#### color

```java
public SlugLabel color(Color color)
```

Borrows a tint object without copying it; later mutations affect subsequent draws.

- **`color`** — nonnull text tint

**Returns:** this label

**Throws `NullPointerException`:** if color is null

#### onCreate

```java
@Override public void onCreate()
```

Requires no additional resources because construction already created the CPU run.

#### onDestroy

```java
@Override public void onDestroy()
```

Leaves the borrowed font and renderer alive for other labels; their owner disposes them.

#### update

```java
@Override public void update(float delta)
```

Performs no timed updates; text layout changes only through explicit setters.

- **`delta`** — elapsed update time supplied by the UI lifecycle

#### draw

```java
@Override public void draw(TextureBatch batch)
```

Flushes preceding texture geometry, renders retained curves using the window projection
and current viewport, then restores the Slug pass state. Applies batch translation
and forwards its clip rectangle. Empty, zero-sized, and fully transparent labels skip
work. A finally block cancels unfinished submission and clears the shared CPU clip.

- **`batch`** — active UI texture batch whose pending geometry must precede this label

</details>

<a id="type-splitpane"></a>

### SplitPane

[Source](../../src/main/java/valthorne/ui/nodes/SplitPane.java#L31)

Two mixed-renderer panes separated by a captured, keyboard-accessible divider.
Vertical means top/bottom; horizontal means left/right. When minimum sizes cannot
fit, available space is shared proportionally. Requested ratio survives resizing.
Manage pane contents through first()/second(), not inherited structural methods.
Initial layout is horizontal with ratio 0.5, divider thickness eight layout units,
and zero minimum sizes. Ratio notifications run synchronously on the UI thread;
geometry changes take effect through a subsequent layout pass.

```java
SplitPane split = new SplitPane(new Panel(), new Panel());
split.ratio(0.3f).minimumSizes(120, 200).dividerSize(8);
split.getLayout().width(800).height(600);
```

<details>
<summary>SplitPane operation reference (12 declarations)</summary>

#### Constructor

```java
public SplitPane(UINode first, UINode second)
```

Attaches distinct, unattached nodes to owned pane containers and sizes their
layouts to fill those containers. The supplied nodes become part of this pane's
lifecycle; later content management should use first() and second().

- **`first`** — initial content of the first pane
- **`second`** — initial content of the second pane

**Throws `NullPointerException`:** if either node is null

**Throws `IllegalArgumentException`:** if nodes are identical or already attached

#### first

```java
public Panel first()
```

Returns the owned first-pane container for content management. It occupies the
left side in horizontal mode or the top in vertical mode.

**Returns:** live first-pane container

#### second

```java
public Panel second()
```

Returns the owned second-pane container for content management. It occupies the
right side in horizontal mode or the bottom in vertical mode.

**Returns:** live second-pane container

#### getDivider

```java
public Button getDivider()
```

Returns the live divider for styling and enabled-state customization. Its input
callbacks implement split resizing rather than ordinary button activation.

**Returns:** owned divider control

#### getRatio

```java
public float getRatio()
```

Returns the requested first-pane fraction, independent of minimum-size constraints
and current layout. This value survives container resizing.

**Returns:** requested fraction in the inclusive range zero through one

#### getEffectiveRatio

```java
public float getEffectiveRatio()
```

Returns the first-pane fraction computed by the last layout, excluding divider
thickness. May differ from the requested ratio when minimum sizes constrain it.

**Returns:** actual first-pane fraction, or zero when no content space is available

#### onChange

```java
public AutoCloseable onChange(Runnable listener)
```

Registers a synchronous callback for changes to the requested ratio. Geometry
may not have been laid out when the callback runs; orientation and size changes
alone do not fire this signal.

- **`listener`** — callback to subscribe

**Returns:** handle whose close operation removes the subscription

#### ratio

```java
public SplitPane ratio(float value)
```

Clamps a finite requested fraction to zero through one. A changed value invalidates
geometry and immediately notifies listeners; an unchanged value does neither.
Minimum sizes constrain the effective geometry without replacing this request.

- **`value`** — desired fraction of content space assigned to the first pane

**Returns:** this split pane

**Throws `IllegalArgumentException`:** if value is non-finite

#### vertical

```java
public SplitPane vertical(boolean value)
```

Changes orientation and clears divider dragging when the value changes. Marks
geometry dirty while preserving ratio and minimum sizes; does not fire a ratio
change notification.

- **`value`** — true for top/bottom, false for left/right

**Returns:** this split pane

#### dividerSize

```java
public SplitPane dividerSize(float pixels)
```

Stores a finite divider extent of at least one layout unit and invalidates changed
geometry. Layout caps the actual divider to the available container extent.

- **`pixels`** — divider thickness in layout units

**Returns:** this split pane

**Throws `IllegalArgumentException`:** if pixels is non-finite or less than one

#### minimumSizes

```java
public SplitPane minimumSizes(float first, float second)
```

Sets finite nonnegative minimum extents along the split axis. If their sum exceeds
available content space, layout distributes that space proportionally instead.
Changed minima invalidate geometry without changing the requested ratio.

- **`first`** — minimum first-pane extent in layout units
- **`second`** — minimum second-pane extent in layout units

**Returns:** this split pane

**Throws `IllegalArgumentException`:** if either extent is negative or non-finite

#### afterLayout

```java
    protected void afterLayout()
```

Repositions the two containers and divider after normal panel layout. Unchanged
cached dimensions skip computation. Divider space is removed before applying
the requested ratio and minima; insufficient space is shared proportionally.

</details>

<a id="type-splitpane-divider"></a>

### SplitPane.Divider — internal support type

[Source](../../src/main/java/valthorne/ui/nodes/SplitPane.java#L285)

Implements pointer dragging and axis-aware keyboard resizing for its enclosing
split pane. The root provides input routing and capture; this control retains
dragging state while the enclosing pane stores the grab offset.

<details>
<summary>SplitPane.Divider operation reference (5 declarations)</summary>

#### onMousePress

```java
        public void onMousePress(MousePressEvent event)
```

Begins dragging for an enabled left-button press, preserving the pointer's offset
from the first-pane boundary. Consumes accepted presses; other presses are ignored.

- **`event`** — routed mouse press

#### onMouseDrag

```java
        public void onMouseDrag(MouseDragEvent event)
```

Moves the divider from a left-button drag while dragging is active. Applies the
stored grab offset and minimum constraints, then consumes the event.

- **`event`** — routed drag with current destination coordinates

#### onMouseRelease

```java
        public void onMouseRelease(MouseReleaseEvent event)
```

Ends dragging and clears pressed state for a left-button release, consuming it
even if no drag is currently active. Other buttons are ignored.

- **`event`** — routed mouse release

#### onPointerCancel

```java
        public void onPointerCancel()
```

Clears dragging after routed pointer cancellation. Leaves the current split ratio
and geometry intact.

#### onKeyPress

```java
        public void onKeyPress(KeyPressEvent event)
```

Resizes an enabled divider with the orientation's arrow keys, Home, or End.
Arrow steps are eight layout units, or 32 with Shift. Home and End request the
minimum and maximum permitted positions. Recognized keys are consumed even
when constraints prevent movement; other keys are ignored.

- **`event`** — routed divider key press

</details>

<a id="type-tabbedpane"></a>

### TabbedPane

[Source](../../src/main/java/valthorne/ui/nodes/TabbedPane.java#L31)

Retains lazily constructed tab pages with headers that support pointer and keyboard
selection. Pages may use either rendering backend. Only the selected page is visible
and updated; instantiated inactive pages retain state until removed.

```java
TabbedPane tabs = new TabbedPane();
tabs.addTab("Overview", () -> new Panel());
tabs.addTab("Details", () -> new Panel());
tabs.select(1);
```

Use addTab and removeTab to maintain the header/page bookkeeping, rather than
inherited structural methods. Factories must return fresh unattached nodes. The
first added tab is selected immediately, which invokes its factory during addTab.
Changes and factories run synchronously on the UI thread. Selection moves focus to
the next header when necessary and cancels capture held by the outgoing page.

<details>
<summary>TabbedPane operation reference (10 declarations)</summary>

#### Constructor

```java
public TabbedPane()
```

Creates a vertical tab layout with an eight-unit gap, a 36-unit header strip,
and a growing page deck. No tab or page is initially selected.

#### getTabCount

```java
public int getTabCount()
```

Returns the number of registered tabs, including tabs whose pages have not
yet been instantiated.

**Returns:** current tab count

#### getSelectedIndex

```java
public int getSelectedIndex()
```

Returns the selected tab's current index. Removing an earlier tab can change
this index while leaving the same page selected.

**Returns:** selected index, or -1 when no tab is selected

#### getPage

```java
public UINode getPage(int index)
```

Returns an existing page without invoking its factory. The returned node belongs
to the deck; callers should not reparent it independently.

- **`index`** — zero-based tab index

**Returns:** live page, or null if never successfully selected

**Throws `IndexOutOfBoundsException`:** if index is outside the tab list

#### getHeader

```java
public Button getHeader(int index)
```

Returns the live header for label, style, or disabled-state customization.
Ownership stays with the header strip.

- **`index`** — zero-based tab index

**Returns:** tab header button

**Throws `IndexOutOfBoundsException`:** if index is outside the tab list

#### onChange

```java
public AutoCloseable onChange(Runnable listener)
```

Subscribes to synchronous selection and removal notifications. Registration does
not immediately report the current selection; close the subscription to detach.

- **`listener`** — callback invoked after state changes

**Returns:** subscription handle

#### addTab

```java
public TabbedPane addTab(String title, Supplier<? extends UINode> factory)
```

Adds a header and retains its page factory. If no tab is selected, selects index
zero immediately. Other page factories are deferred until selection; failures
propagate, and adding a tab is not rolled back if automatic selection fails.

- **`title`** — header label
- **`factory`** — supplier of a fresh unattached page

**Returns:** this pane

**Throws `NullPointerException`:** if title or factory is null

#### select

```java
public void select(int index)
```

Selects an enabled tab, creating and attaching its page if needed. The page is
absolutely sized to fill the deck. A disabled or already-selected tab is ignored.
Hides the previous page, updates selected header state, cancels outgoing page
capture if present, and notifies listeners. Factory failures propagate before the
selection changes; a later retry may invoke the factory again.

- **`index`** — zero-based tab to select

**Throws `IndexOutOfBoundsException`:** if index is invalid

**Throws `NullPointerException`:** if the factory returns null

**Throws `IllegalArgumentException`:** if the page is attached or is this pane

#### removeTab

```java
public void removeTab(int index)
```

Removes a header and any instantiated page through normal node removal. If the
active tab is removed, searches cyclically from a nearby index for an enabled
replacement; otherwise adjusts the selected index as needed. Fires a change
notification and restores header focus when focus belonged to removed content.

- **`index`** — zero-based tab to remove

**Throws `IndexOutOfBoundsException`:** if index is invalid

#### update

```java
@Override public void update(float delta)
```

Updates the header strip and only the selected page. Inactive pages retain state
without receiving updates; the deck's generic update traversal is bypassed.

- **`delta`** — elapsed time in seconds

</details>

<a id="type-tabbedpane-tab"></a>

### TabbedPane.Tab — internal support type

[Source](../../src/main/java/valthorne/ui/nodes/TabbedPane.java#L217)

Retains one header, its deferred factory, and its instantiated page reference.
The enclosing pane controls attachment, selection, and disposal through its
header and deck containers.

<a id="type-textfield"></a>

### TextField

[Source](../../src/main/java/valthorne/ui/nodes/TextField.java#L120)

`TextField` is an interactive single-line text input control built on top of
`Panel`. It supports typing, caret movement, text selection, clipboard
operations, placeholder text, optional masking, horizontal scrolling, blinking
caret rendering, and style-driven visuals.

This class is designed to be the primary editable text input component in the
Valthorne UI system. It integrates with keyboard input, mouse interaction,
viewport-aware coordinate conversion, and theme resolution. The control manages
both the raw logical text and a derived display string used when masking is enabled.

The text field supports the following editing behaviors:

- typing printable characters

- caret movement with arrow keys, Home, End, and Ctrl+word navigation

- selection through mouse drag and Shift+keyboard movement

- double-click select-all behavior

- Backspace and Delete removal

- clipboard copy, cut, and paste with Ctrl+C, Ctrl+X, and Ctrl+V

- Ctrl+A select all

- Enter-triggered action callbacks

Rendering is style-driven and can resolve:

- background drawables for normal, hovered, and focused states

- a font for text rendering

- colors for text, placeholder text, caret, and selection

- padding and caret dimensions

- selection fallback color

- an optional action callback from style data

The text field keeps track of:

- the raw input text

- the placeholder text

- the current caret position

- selection range

- whether masking is enabled

- the visual horizontal text scroll offset

- caret blink state

- double-click timing state

Because the control is single-line, pasted and assigned text is sanitized so line
breaks, tabs, and other control characters are converted into spaces rather than
being inserted directly into the field.

##### Example Usage

```java
TextField field = new TextField("Enter your name");

field.getLayout()
     .width(240)
     .height(36);

field.text("Albert")
     .caretIndex(3)
     .masking(false)
     .action(textField -> {
         System.out.println("Submitted: " + textField.getText());
     });

String value = field.getText();
String placeholder = field.getPlaceholder();
int caret = field.getCaretIndex();
boolean masked = field.isMasking();
char mask = field.getMaskChar();
NodeAction<TextField> action = field.getAction();

field.update(delta);
field.draw(batch);
```

This example demonstrates the complete intended use of the class: construction,
sizing, text assignment, placeholder usage, caret positioning, masking,
action binding, querying state, updating, and drawing.

<details>
<summary>TextField operation reference (40 declarations)</summary>

#### BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> BACKGROUND_KEY
```

Style key used to resolve the normal background drawable.

#### HOVER_BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> HOVER_BACKGROUND_KEY
```

Style key used to resolve the hovered background drawable.

#### FOCUSED_BACKGROUND_KEY

```java
public static final  StyleKey<Drawable> FOCUSED_BACKGROUND_KEY
```

Style key used to resolve the focused background drawable.

#### FONT_KEY

```java
public static final  StyleKey<Font> FONT_KEY
```

Style key used to resolve the text field font.

#### COLOR_KEY

```java
public static final  StyleKey<Color> COLOR_KEY
```

Style key used to resolve the normal text color.

#### PLACEHOLDER_COLOR_KEY

```java
public static final  StyleKey<Color> PLACEHOLDER_COLOR_KEY
```

Style key used to resolve the placeholder text color.

#### CARET_COLOR_KEY

```java
public static final  StyleKey<Color> CARET_COLOR_KEY
```

Style key used to resolve the caret color.

#### SELECTION_COLOR_KEY

```java
public static final  StyleKey<Color> SELECTION_COLOR_KEY
```

Style key used to resolve the selection highlight color.

#### PADDING_KEY

```java
public static final  StyleKey<Float> PADDING_KEY
```

Style key used to resolve horizontal text padding.

#### CARET_WIDTH_KEY

```java
public static final  StyleKey<Float> CARET_WIDTH_KEY
```

Style key used to resolve caret width.

#### CARET_PADDING_Y_KEY

```java
public static final  StyleKey<Float> CARET_PADDING_Y_KEY
```

Style key used to resolve vertical caret padding.

#### SCISSOR_FUDGE_KEY

```java
public static final  StyleKey<Float> SCISSOR_FUDGE_KEY
```

Style key used to resolve a small scissor expansion value.

#### ACTION_KEY

```java
public static final  StyleKey<NodeAction<TextField>> ACTION_KEY
```

Style key used to resolve a fallback action callback.

#### DEFAULT_SELECTION_COLOR_KEY

```java
public static final  StyleKey<Color> DEFAULT_SELECTION_COLOR_KEY
```

Style key used to resolve the default selection color when no explicit selection color exists.

#### Constructor

```java
public TextField()
```

Creates a new empty text field.

The field is configured as clickable and focusable and immediately marks
its layout dirty so it can size itself once styled.

#### Constructor

```java
public TextField(String placeholder)
```

Creates a new text field with the provided placeholder text.

- **`placeholder`** — the placeholder text to display when the field is empty

#### getText

```java
public String getText()
```

Returns the raw logical text stored by this field.

**Returns:** the current text

#### text

```java
public TextField text(String text)
```

Sets the raw logical text stored by this field.

The supplied text is sanitized so newlines, tabs, carriage returns, and other
ISO control characters are converted into spaces. Consecutive control-derived
spaces are collapsed so multiple adjacent control characters do not generate
repeated spaces unnecessarily.

After assignment, the caret is clamped into the valid range, selection is
cleared, display text is marked dirty, layout is marked dirty, and horizontal
scrolling is updated.

- **`text`** — the new logical text

**Returns:** this text field

#### getPlaceholder

```java
public String getPlaceholder()
```

Returns the current placeholder text.

**Returns:** the placeholder text

#### placeholder

```java
public TextField placeholder(String placeholder)
```

Sets the placeholder text shown when the field is empty.

- **`placeholder`** — the new placeholder text

**Returns:** this text field

#### getCaretIndex

```java
public int getCaretIndex()
```

Returns the current caret index.

**Returns:** the caret insertion index

#### caretIndex

```java
public TextField caretIndex(int caretIndex)
```

Sets the caret index.

The value is clamped into the valid range of the current text, selection is
cleared, cursor blinking is reset, and horizontal scrolling is updated.

- **`caretIndex`** — the new caret index

**Returns:** this text field

#### isMasking

```java
public boolean isMasking()
```

Returns whether masking is currently enabled.

**Returns:** `true` if masking is enabled

#### masking

```java
public TextField masking(boolean masking)
```

Enables or disables visual masking of the field text.

When masking is enabled, the logical text remains unchanged but the displayed
string is replaced with repeated instances of `maskChar`. Changing this
setting rebuilds display text and updates layout and scrolling.

- **`masking`** — whether masking should be enabled

**Returns:** this text field

#### getMaskChar

```java
public char getMaskChar()
```

Returns the current masking character.

**Returns:** the mask character

#### maskChar

```java
public TextField maskChar(char maskChar)
```

Sets the masking character used when masking is enabled.

- **`maskChar`** — the new masking character

**Returns:** this text field

#### getAction

```java
public NodeAction<TextField> getAction()
```

Returns the explicitly assigned Enter action.

**Returns:** the explicit action, or `null` if none is assigned

#### action

```java
public TextField action(NodeAction<TextField> action)
```

Assigns an explicit Enter action to this field.

- **`action`** — the action to perform when Enter is pressed

**Returns:** this text field

#### update

```java
    public void update(float delta)
```

Updates this text field.

The method advances double-click timing when a click is pending, then updates
caret blinking while the field is focused. When not focused, the caret is forced
invisible and the blink timer is reset.

- **`delta`** — the frame delta time in seconds

#### onMouseMove

```java
    public void onMouseMove(MouseMoveEvent event)
```

Handles mouse movement over the text field.

This implementation currently delegates directly to the superclass.

- **`event`** — the mouse move event

#### onMousePress

```java
    public void onMousePress(MousePressEvent event)
```

Handles mouse press interaction for caret placement and selection behavior.

A second click within the double-click window triggers select-all behavior.
Otherwise a new selection begins at the character index nearest the pressed
mouse position. Caret blinking is reset and scrolling is updated afterward.

- **`event`** — the mouse press event

#### onMouseDrag

```java
    public void onMouseDrag(MouseDragEvent event)
```

Handles mouse drag selection updates.

While a selection drag is active, the caret and selection end are moved to the
character nearest the dragged mouse position.

- **`event`** — the mouse drag event

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Handles mouse release by ending active selection dragging.

- **`event`** — the mouse release event

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Handles keyboard editing and command shortcuts.

If the field is not focused, no action is taken. When focused, the caret blink
state is reset and the event is interpreted according to modifier state:

- Ctrl+A selects all

- Ctrl+C copies selection

- Ctrl+X cuts selection

- Ctrl+V pastes clipboard text

- Ctrl+Left and Ctrl+Right move by words

- Backspace and Delete remove text

- Arrow keys, Home, and End move the caret

- Enter performs the explicit or style-resolved action

- Printable characters are inserted at the caret

- **`event`** — the key press event

#### applyLayout

```java
    protected void applyLayout()
```

Applies style-driven layout configuration and resolved rendering resources.

This method resolves drawables, colors, font, padding, caret dimensions,
scissor fudge, and default selection color from the current style. If a font
exists, the field sizes itself automatically when width or height are set to
auto. Layout is then delegated to the superclass.

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws the text field.

The currently resolved background is drawn first. If no font is available,
rendering stops there. Otherwise a scissor region is established for the inner
text area, selection is drawn if present, then text and caret are rendered,
and finally scissoring is ended.

- **`batch`** — the texture batch used for rendering

#### setFocused

```java
    public void setFocused(boolean focused)
```

Updates focus state for this field.

When focus changes, cursor blinking is reset, active selection dragging is
canceled if focus is lost, and horizontal scrolling is recalculated.

- **`focused`** — the new focus state

#### getEditor

```java
public valthorne.ui.behavior.TextEditModel getEditor()
```

Editing, validation and undo/redo API shared by both field families.

#### onTextInput

```java
    public void onTextInput(valthorne.event.events.TextInputEvent event)
```

Inserts routed text through the editing model only when focused and enabled.
Accepted routing consumes the event even if model validation rejects the edit;
model listeners synchronize rendering state when an edit succeeds.

- **`event`** — text-input event to insert and consume

#### onPointerCancel

```java
    public void onPointerCancel()
```

Clears inherited press/drag state and stops local selection and pending-click
handling. Current text, caret, and selection contents are preserved.

</details>

<a id="type-tooltip"></a>

### Tooltip

[Source](../../src/main/java/valthorne/ui/nodes/Tooltip.java#L64)

`Tooltip` is a lightweight floating UI node used to display a short piece
of text near another UI element, typically after a hover delay.

The tooltip is styled entirely through the theme system and resolves:

- a font used to render the tooltip text

- a background drawable for the tooltip box

- a padding value used around the text

This node is generally controlled by higher-level UI systems such as
`valthorne.ui.UIRoot`, which determine when the tooltip should become
visible and where it should be positioned. The tooltip itself focuses on:

- storing its text content

- sizing itself from the resolved font and padding

- drawing its background and text when visible

Tooltips are intentionally non-interactive. In the constructor, they are
configured as invisible, non-clickable, and non-focusable by default.

##### Example Usage

```java
Tooltip tooltip = new Tooltip("Save changes");

String text = tooltip.getText();
tooltip.text("Click to save");

tooltip.setVisible(true);
tooltip.update(delta);
tooltip.draw(batch);
```

This example demonstrates the complete intended use of the class: construction,
text access, text updates, visibility control, update, and draw.

<details>
<summary>Tooltip operation reference (11 declarations)</summary>

#### FONT_STYLE_KEY

```java
public static final  StyleKey<Font> FONT_STYLE_KEY
```

Style key used to resolve the tooltip font.

#### BACKGROUND_STYLE_KEY

```java
public static final  StyleKey<Drawable> BACKGROUND_STYLE_KEY
```

Style key used to resolve the tooltip background drawable.

#### PADDING_STYLE_KEY

```java
public static final  StyleKey<Float> PADDING_STYLE_KEY
```

Style key used to resolve tooltip padding, defaulting to `6f`.

#### Constructor

```java
public Tooltip(String text)
```

Creates a new tooltip with the supplied text.

The tooltip starts hidden and is configured to be neither clickable nor
focusable, since tooltips are purely informational UI elements.

- **`text`** — the initial tooltip text

#### onCreate

```java
@Override
    public void onCreate()
```

Called when this tooltip is created.

This implementation currently performs no creation logic.

#### onDestroy

```java
@Override
    public void onDestroy()
```

Called when this tooltip is destroyed.

This implementation currently performs no destruction logic.

#### update

```java
@Override
    public void update(float delta)
```

Updates this tooltip.

This implementation currently performs no per-frame behavior.

- **`delta`** — the elapsed frame time in seconds

#### applyLayout

```java
@Override
    protected void applyLayout()
```

Applies style-driven layout for this tooltip.

The current style is resolved and used to update the font, background, and
padding. If no style exists, all resolved visual references are cleared and
padding is reset to its default. If both a font and non-null text are available,
the tooltip computes its width and height from the text size plus padding.

#### draw

```java
@Override
    public void draw(TextureBatch batch)
```

Draws this tooltip using the provided texture batch.

Rendering is skipped if the tooltip is not visible. If visible, the background
drawable is rendered first across the tooltip bounds. After that, if a font
exists and the text is non-null and non-blank, the text is rendered inside the
tooltip using the resolved padding offset.

- **`batch`** — the texture batch used for rendering

#### text

```java
public Tooltip text(String text)
```

Sets the tooltip text.

Changing the text marks layout dirty so the tooltip can recompute its size
during the next layout pass.

- **`text`** — the new tooltip text

**Returns:** this tooltip

#### getText

```java
public String getText()
```

Returns the current tooltip text.

**Returns:** the current tooltip text

</details>

## Related guides

- [UI roots, nodes, and input routing](ui-core.md)
- [UI layout and alignment](ui-layout.md)
- [Themes, styles, and design tokens](ui-themes.md)
- [Virtual lists, tables, and selection](ui-data.md)
- [Shared UI behavior and editing models](ui-behavior.md)
- [Slug vector fonts](slug-fonts.md)
