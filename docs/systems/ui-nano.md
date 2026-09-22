# NanoVG UI controls

Author: Albert Beaupre

[System manual](README.md)

## Purpose

NanoVG controls use vector-style painting while retaining the same UI tree, layout, focus, and behavior foundations. NanoNode defines the painting contract; NanoContainer and concrete controls provide panels, labels, buttons, images, fields, sliders, combo boxes, links, and scrollable content.

The [composite widget guide](../ui-widgets.md#nanovg-versions) also covers
`NanoBreadcrumbBar`, `NanoColorPicker`, `NanoComboBox`, `NanoDirectoryTree`,
`NanoFileChooser`, `NanoFileExplorer`, `NanoMenuBar`, `NanoNumberSpinner`,
`NanoPopupMenu`, and `NanoRadioGroup`. Their owned editors, rows, scrollbars, and
modal shells all use NanoVG. [NanoWindow](../ui-windows.md) adds movable, resizable
tool windows with matching light/dark chrome and clipped Nano content.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Vector painting | Controls draw through the root's prepared NanoVG context. |
| Shared interaction | Focus, activation, text editing, and scrolling integrate with the normal UI router. |
| Measurement | Text and automatic dimensions use the available NanoVG font context or documented fallbacks. |
| Mixed trees | The rendering context switches between texture and NanoVG work when needed. |
| Navigation controls | Combo boxes, hyperlinks, and modals manage transient interaction and display states. |

## Getting started

1. Create or configure the root's NanoVG context and register the required font resources.
2. Attach NanoVG controls to the tree and configure their layout and data.
3. Let the root begin/end rendering phases rather than opening a NanoVG frame inside each widget.
4. Use normal focus, modal, and input APIs while the control handles painting.

## Ownership and lifecycle

The root owns or adopts its NanoVG context according to its configuration. Controls borrow fonts/images and should not delete the shared context. Native drawing and measurement require the graphics thread.

## Important behavior

- A zero or missing context prevents normal vector rendering.
- Auto-size fallbacks can differ from final font measurement; relayout once the real context is available.
- Standard and NanoVG controls share behavior but can have different style keys and sizing details.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`NanoButton`](#type-nanobutton)
- [`NanoCheckbox`](#type-nanocheckbox)
- [`NanoComboBox`](#type-nanocombobox)
- [`NanoContainer`](#type-nanocontainer)
- [`NanoGrid`](#type-nanogrid)
- [`NanoHyperlink`](#type-nanohyperlink)
- [`NanoImage`](#type-nanoimage)
- [`NanoLabel`](#type-nanolabel)
- [`NanoModal`](#type-nanomodal)
- [`NanoNode`](#type-nanonode)
- [`NanoPanel`](#type-nanopanel)
- [`NanoProgressBar`](#type-nanoprogressbar)
- [`NanoScrollPanel`](#type-nanoscrollpanel)
- [`NanoSlider`](#type-nanoslider)
- [`NanoTextField`](#type-nanotextfield)

<a id="type-nanobutton"></a>

### NanoButton

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoButton.java#L29)

Focusable NanoVG button with centered text and state-specific background,
border, and text colors. Enter, Space, and accepted left-button releases use
the shared activation policy; a local callback overrides the resolved theme
action. Disabled, pressed, focused, and hovered paint states have that priority.

Automatic dimensions are replaced with measured text plus padding during
layout. Later text/font changes dirty layout but do not restore those dimensions
to auto. Colors and registered fonts are borrowed; styles may overwrite local
paint settings. Render through the root-managed NanoVG lifecycle.

<details>
<summary>NanoButton operation reference (57 declarations)</summary>

#### BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> BACKGROUND_COLOR_KEY
```

Theme background color used during layout; colors are retained by reference.

#### HOVER_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY
```

Theme hover background color used during layout; colors are retained by reference.

#### FOCUSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY
```

Theme focused background color used during layout; colors are retained by reference.

#### PRESSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> PRESSED_BACKGROUND_COLOR_KEY
```

Theme pressed background color used during layout; colors are retained by reference.

#### DISABLED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_BACKGROUND_COLOR_KEY
```

Theme disabled background color used during layout; colors are retained by reference.

#### BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> BORDER_COLOR_KEY
```

Theme border color used during layout; colors are retained by reference.

#### HOVER_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BORDER_COLOR_KEY
```

Theme hover border color used during layout; colors are retained by reference.

#### FOCUSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BORDER_COLOR_KEY
```

Theme focused border color used during layout; colors are retained by reference.

#### PRESSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> PRESSED_BORDER_COLOR_KEY
```

Theme pressed border color used during layout; colors are retained by reference.

#### DISABLED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_BORDER_COLOR_KEY
```

Theme disabled border color used during layout; colors are retained by reference.

#### TEXT_COLOR_KEY

```java
public static final  StyleKey<Color> TEXT_COLOR_KEY
```

Theme text color used during layout; colors are retained by reference.

#### HOVER_TEXT_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_TEXT_COLOR_KEY
```

Theme hover text color used during layout; colors are retained by reference.

#### FOCUSED_TEXT_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_TEXT_COLOR_KEY
```

Theme focused text color used during layout; colors are retained by reference.

#### PRESSED_TEXT_COLOR_KEY

```java
public static final  StyleKey<Color> PRESSED_TEXT_COLOR_KEY
```

Theme pressed text color used during layout; colors are retained by reference.

#### DISABLED_TEXT_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_TEXT_COLOR_KEY
```

Theme disabled text color used during layout; colors are retained by reference.

#### FONT_NAME_KEY

```java
public static final  StyleKey<String> FONT_NAME_KEY
```

Theme font name used during layout; colors are retained by reference.

#### FONT_SIZE_KEY

```java
public static final  StyleKey<Float> FONT_SIZE_KEY
```

Theme font size used during layout; colors are retained by reference.

#### PADDING_X_KEY

```java
public static final  StyleKey<Float> PADDING_X_KEY
```

Theme padding x used during layout; colors are retained by reference.

#### PADDING_Y_KEY

```java
public static final  StyleKey<Float> PADDING_Y_KEY
```

Theme padding y used during layout; colors are retained by reference.

#### CORNER_RADIUS_KEY

```java
public static final  StyleKey<Float> CORNER_RADIUS_KEY
```

Theme corner radius used during layout; colors are retained by reference.

#### BORDER_WIDTH_KEY

```java
public static final  StyleKey<Float> BORDER_WIDTH_KEY
```

Theme border width used during layout; colors are retained by reference.

#### ACTION_KEY

```java
public static final  StyleKey<NodeAction<NanoButton>> ACTION_KEY
```

Theme activation callback used when no local action is assigned.

#### Constructor

```java
public NanoButton()
```

Creates an empty clickable, focusable button with default paint settings and
marks layout dirty for initial content measurement.

#### Constructor

```java
public NanoButton(String text)
```

Creates a clickable, focusable button and stores text through the normal setter.
Font resources are expected to have been registered by the root/application.

- **`text`** — initial text; null becomes empty

#### getText

```java
public String getText()
```

Returns stored text unchanged. The current setter does not invoke the private
sanitizer, so control characters are not removed by this accessor or setter.

**Returns:** non-null button text

#### text

```java
public NanoButton text(String text)
```

Stores text, converting null to empty, and marks layout dirty. Does not sanitize
control characters or restore previously measured numeric dimensions to auto.

- **`text`** — replacement contents, possibly null

**Returns:** this button

#### getAction

```java
public NodeAction<NanoButton> getAction()
```

Reads the explicitly assigned callback without consulting resolved styles.

**Returns:** local callback, or null when theme fallback may apply

#### action

```java
public NanoButton action(NodeAction<NanoButton> action)
```

Replaces the local activation callback without invoking it. Null enables theme
fallback rather than necessarily disabling activation behavior.

- **`action`** — local synchronous callback, or null

**Returns:** this button

#### fontName

```java
public NanoButton fontName(String fontName)
```

Retains a nonblank NanoVG registration name and dirties layout even if input
is null or blank. Does not load a font or verify that the name is registered.

- **`fontName`** — desired registered font name

**Returns:** this button

#### fontSize

```java
public NanoButton fontSize(float fontSize)
```

Sets text size, clamping values below 1 to 1.
Marks layout dirty for measurement, without restoring fixed dimensions to auto.

- **`fontSize`** — finite requested value in UI units

**Returns:** this button

#### paddingX

```java
public NanoButton paddingX(float paddingX)
```

Sets padding on each horizontal side, clamping values below 0 to 0.
Marks layout dirty for measurement, without restoring fixed dimensions to auto.

- **`paddingX`** — finite requested value in UI units

**Returns:** this button

#### paddingY

```java
public NanoButton paddingY(float paddingY)
```

Sets padding on each vertical side, clamping values below 0 to 0.
Marks layout dirty for measurement, without restoring fixed dimensions to auto.

- **`paddingY`** — finite requested value in UI units

**Returns:** this button

#### cornerRadius

```java
public NanoButton cornerRadius(float cornerRadius)
```

Sets rounded-corner radius, clamping values below 0 to 0.
Changes paint only; does not mark layout dirty.

- **`cornerRadius`** — finite requested value in UI units

**Returns:** this button

#### borderWidth

```java
public NanoButton borderWidth(float borderWidth)
```

Sets outer border stroke width, clamping values below 0 to 0.
Changes paint only; does not mark layout dirty.

- **`borderWidth`** — finite requested value in UI units

**Returns:** this button

#### backgroundColor

```java
public NanoButton backgroundColor(Color color)
```

Retains a non-null background color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### hoverBackgroundColor

```java
public NanoButton hoverBackgroundColor(Color color)
```

Retains a non-null hover background color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### focusedBackgroundColor

```java
public NanoButton focusedBackgroundColor(Color color)
```

Retains a non-null focused background color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### pressedBackgroundColor

```java
public NanoButton pressedBackgroundColor(Color color)
```

Retains a non-null pressed background color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### disabledBackgroundColor

```java
public NanoButton disabledBackgroundColor(Color color)
```

Retains a non-null disabled background color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### borderColor

```java
public NanoButton borderColor(Color color)
```

Retains a non-null border color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### hoverBorderColor

```java
public NanoButton hoverBorderColor(Color color)
```

Retains a non-null hover border color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### focusedBorderColor

```java
public NanoButton focusedBorderColor(Color color)
```

Retains a non-null focused border color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### pressedBorderColor

```java
public NanoButton pressedBorderColor(Color color)
```

Retains a non-null pressed border color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### disabledBorderColor

```java
public NanoButton disabledBorderColor(Color color)
```

Retains a non-null disabled border color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### textColor

```java
public NanoButton textColor(Color color)
```

Retains a non-null text color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### hoverTextColor

```java
public NanoButton hoverTextColor(Color color)
```

Retains a non-null hover text color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### focusedTextColor

```java
public NanoButton focusedTextColor(Color color)
```

Retains a non-null focused text color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### pressedTextColor

```java
public NanoButton pressedTextColor(Color color)
```

Retains a non-null pressed text color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### disabledTextColor

```java
public NanoButton disabledTextColor(Color color)
```

Retains a non-null disabled text color without copying it. Null leaves the current
reference unchanged; theme application may later replace it.

- **`color`** — mutable paint color, or null

**Returns:** this button

#### onCreate

```java
    public void onCreate()
```

Allocates no resources. Fonts and the NanoVG context belong to the root/application.

#### onDestroy

```java
    public void onDestroy()
```

Performs no disposal because native rendering resources are borrowed.

#### update

```java
    public void update(float delta)
```

Performs no timed work or child traversal. Input callbacks drive activation.

- **`delta`** — elapsed update seconds, unused

#### draw

```java
    public void draw(TextureBatch batch)
```

Enters the root's shared rendering path for NanoVG backend selection and clipping.

- **`batch`** — active texture batch used by mixed UI traversal

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Consumes Enter or Space on an enabled routed receiver, then activates it.
The shared policy does not independently check focus or suppress repeats.
Accepted events are consumed even when no action is configured.

- **`event`** — routed key press

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Activates for a left release accepted by this enabled, attached node's hit-test
policy. Consumption occurs first; the policy does not itself establish a
matching earlier press. Other releases remain untouched.

- **`event`** — routed pointer release

#### applyLayout

```java
    protected void applyLayout()
```

Applies non-null theme paint/font/padding values, measures text plus padding,
and replaces dimensions still marked auto. Then delegates base layout.
Resolved styles may overwrite local setters; missing native context uses
approximate text metrics through the measurement helpers.

#### draw

```java
    public void draw(long vg)
```

Paints state-selected background and border, followed by centered text when
nonempty. The border is centered on the outer path, not inset like NanoPanel.
Skips invisible nodes and a zero handle. Requires root-prepared frame state.

- **`vg`** — borrowed active NanoVG context, or zero to skip

</details>

<a id="type-nanocheckbox"></a>

### NanoCheckbox

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoCheckbox.java#L28)

Focusable NanoVG checkbox using the shared activation policy for Enter, Space,
and accepted left-button releases. Checked state lives in the inherited node
bit; checked and toggle additionally invoke a synchronous action on changes.
A local action takes precedence over the theme action. Focused paint colors
win over hovered colors; no separate disabled color palette is applied here.

Colors are borrowed references and resolved styles can replace paint values
during layout. Render through the root-managed NanoVG frame. This leaf neither
owns native resources nor traverses child nodes.

<details>
<summary>NanoCheckbox operation reference (37 declarations)</summary>

#### BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> BACKGROUND_COLOR_KEY
```

Theme normal background color.

#### HOVER_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY
```

Theme hovered background color, below focused-state precedence.

#### FOCUSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY
```

Theme focused background color, preferred over hover.

#### BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> BORDER_COLOR_KEY
```

Theme normal border-stroke color.

#### HOVER_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BORDER_COLOR_KEY
```

Theme hovered border color, below focused-state precedence.

#### FOCUSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BORDER_COLOR_KEY
```

Theme focused border color, preferred over hover.

#### CHECKMARK_COLOR_KEY

```java
public static final  StyleKey<Color> CHECKMARK_COLOR_KEY
```

Theme checked-mark stroke color.

#### CORNER_RADIUS_KEY

```java
public static final  StyleKey<Float> CORNER_RADIUS_KEY
```

Theme rounded-box corner radius in UI units.

#### BORDER_WIDTH_KEY

```java
public static final  StyleKey<Float> BORDER_WIDTH_KEY
```

Theme border stroke width in UI units; zero omits the border.

#### CHECKMARK_SCALE_KEY

```java
public static final  StyleKey<Float> CHECKMARK_SCALE_KEY
```

Theme checkmark fraction of box width and height, defaulting to one half.

#### CHECKMARK_THICKNESS_KEY

```java
public static final  StyleKey<Float> CHECKMARK_THICKNESS_KEY
```

Theme checkmark stroke thickness in UI units.

#### ACTION_KEY

```java
public static final  StyleKey<NodeAction<NanoCheckbox>> ACTION_KEY
```

Theme change action used only when the local callback is null.

#### Constructor

```java
public NanoCheckbox()
```

Creates an unchecked node with clickable and focusable capabilities enabled.
Paint settings begin with local defaults and may be replaced by layout styles.

#### getAction

```java
public NodeAction<NanoCheckbox> getAction()
```

Reads only the explicitly assigned local callback, without resolving a theme.

**Returns:** local action, or null when theme lookup is used

#### action

```java
public NanoCheckbox action(NodeAction<NanoCheckbox> action)
```

Replaces the local change callback without invoking it. Null restores fallback
to the resolved theme action; it does not necessarily disable all callbacks.

- **`action`** — local synchronous callback, or null for theme fallback

**Returns:** this checkbox

#### isChecked

```java
public boolean isChecked()
```

Reads the inherited checked-state bit used to decide whether to draw the mark.

**Returns:** current checked state

#### onCreate

```java
    public void onCreate()
```

Allocates no resources; the root owns the NanoVG context used for painting.

#### onDestroy

```java
    public void onDestroy()
```

Performs no disposal because this widget owns no native rendering resources.

#### update

```java
    public void update(float delta)
```

Performs no timed work or child traversal. Checked state changes through
explicit calls or routed activation events.

- **`delta`** — elapsed update seconds, unused

#### draw

```java
    public void draw(TextureBatch batch)
```

Enters root-managed mixed-renderer dispatch for the NanoVG callback.

- **`batch`** — active texture batch used by the UI traversal

#### checked

```java
public NanoCheckbox checked(boolean checked)
```

Applies a changed checked value, then invokes the local action or theme fallback.
Repeating the existing value neither mutates state nor fires an action. This
programmatic operation does not check enabled state; callback failure leaves
the new checked bit in place.

- **`checked`** — desired state

**Returns:** this checkbox

#### toggle

```java
public NanoCheckbox toggle()
```

Inverts checked state through checked, including synchronous action resolution.
This programmatic operation is available even while the node is disabled.

**Returns:** this checkbox

#### backgroundColor

```java
public NanoCheckbox backgroundColor(Color color)
```

Retains a non-null color for the normal background without copying it.
Null keeps the previous value, and resolved styles may later replace it.

- **`color`** — paint color reference, or null

**Returns:** this checkbox

#### hoverBackgroundColor

```java
public NanoCheckbox hoverBackgroundColor(Color color)
```

Retains a non-null color for the hovered background without copying it.
Null keeps the previous value, and resolved styles may later replace it.

- **`color`** — paint color reference, or null

**Returns:** this checkbox

#### focusedBackgroundColor

```java
public NanoCheckbox focusedBackgroundColor(Color color)
```

Retains a non-null color for the focused background without copying it.
Null keeps the previous value, and resolved styles may later replace it.

- **`color`** — paint color reference, or null

**Returns:** this checkbox

#### borderColor

```java
public NanoCheckbox borderColor(Color color)
```

Retains a non-null color for the normal border without copying it.
Null keeps the previous value, and resolved styles may later replace it.

- **`color`** — paint color reference, or null

**Returns:** this checkbox

#### hoverBorderColor

```java
public NanoCheckbox hoverBorderColor(Color color)
```

Retains a non-null color for the hovered border without copying it.
Null keeps the previous value, and resolved styles may later replace it.

- **`color`** — paint color reference, or null

**Returns:** this checkbox

#### focusedBorderColor

```java
public NanoCheckbox focusedBorderColor(Color color)
```

Retains a non-null color for the focused border without copying it.
Null keeps the previous value, and resolved styles may later replace it.

- **`color`** — paint color reference, or null

**Returns:** this checkbox

#### checkmarkColor

```java
public NanoCheckbox checkmarkColor(Color color)
```

Retains a non-null color for the checkmark without copying it.
Null keeps the previous value, and resolved styles may later replace it.

- **`color`** — paint color reference, or null

**Returns:** this checkbox

#### cornerRadius

```java
public NanoCheckbox cornerRadius(float cornerRadius)
```

Sets the corner radius, clamping negatives to zero.
There is no upper clamp; resolved layout styles can replace this value.

- **`cornerRadius`** — finite requested value in UI units

**Returns:** this checkbox

#### borderWidth

```java
public NanoCheckbox borderWidth(float borderWidth)
```

Sets the border stroke width, clamping negatives to zero.
There is no upper clamp; resolved layout styles can replace this value.

- **`borderWidth`** — finite requested value in UI units

**Returns:** this checkbox

#### checkmarkScale

```java
public NanoCheckbox checkmarkScale(float checkmarkScale)
```

Sets the checkmark width/height fraction, clamping negatives to zero.
There is no upper clamp; resolved layout styles can replace this value.

- **`checkmarkScale`** — finite requested value in fractions of node dimensions

**Returns:** this checkbox

#### checkmarkThickness

```java
public NanoCheckbox checkmarkThickness(float checkmarkThickness)
```

Sets the checkmark stroke thickness, clamping negatives to zero.
There is no upper clamp; resolved layout styles can replace this value.

- **`checkmarkThickness`** — finite requested value in UI units

**Returns:** this checkbox

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Uses shared activation to consume Enter or Space on an enabled receiver and
toggle it. Focus routing and key-repeat policy belong to the root; this helper
does not independently suppress repeats or check focus.

- **`event`** — routed key press

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Toggles when the inherited activation-release policy accepts a left release
on this enabled, attached, hit-tested node. The event is consumed before the
action; this policy does not itself require a matching earlier press.

- **`event`** — routed pointer release

#### applyLayout

```java
    protected void applyLayout()
```

Retains non-null resolved colors and nonnegative paint dimensions before
base layout. The theme action is resolved on change instead of copied here.
Theme colors are borrowed and can replace explicit setter values.

#### draw

```java
    public void draw(long vg)
```

Paints the rounded box and optional border, then draws the scaled checkmark
only when checked. Focus colors take precedence over hover colors. Invisible
nodes and a zero handle are skipped; the root owns frame and clipping state.

- **`vg`** — borrowed active NanoVG context, or zero to skip

</details>

<a id="type-nanocombobox"></a>

### NanoComboBox

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoComboBox.java#L33)

Keyboard-accessible selection control with a clipped, virtualized modal overlay.
Item membership is copied on assignment, while item objects remain shared. The
popup creates rows on demand, shows at most eight row slots, and moves keyboard
focus to the highlighted option. Use on the owning UI thread.

```java
NanoComboBox<String> choice = new NanoComboBox<String>()
        .items(List.of("Low", "Medium", "High"))
        .selectedIndex(1)
        .onChange(value -> System.out.println(value));
```

Programmatic selection updates the label without invoking the change listener.
User selection closes the popup and notifies only when the selected index changes.
An attached, enabled control with nonempty items is required to open the popup.

- **`<T>`** — item value type

<details>
<summary>NanoComboBox operation reference (13 declarations)</summary>

#### Constructor

```java
public NanoComboBox()
```

Creates a focusable 200-by-38 control with a non-focusable trigger that fills its
bounds. Trigger activation opens the popup; the initial item list is empty.

#### items

```java
public NanoComboBox<T> items(List<T> items)
```

Closes any popup, copies list membership, clears selection, and refreshes the label.
Does not notify the change listener. Null validation happens after popup closure.

- **`items`** — replacement values; list and elements must be non-null

**Returns:** this control

**Throws `NullPointerException`:** if the list or any item is null

#### getItems

```java
public List<T> getItems()
```

Returns the unmodifiable membership snapshot. Item objects themselves are not
copied and may remain mutable.

**Returns:** current item list

#### getSelectedIndex

```java
public int getSelectedIndex()
```

Returns the current selected index independently of the popup highlight.

**Returns:** selected index, or -1 when no item is selected

#### getSelected

```java
public T getSelected()
```

Returns the shared selected item without applying the formatter.

**Returns:** selected item, or null when selection is empty

#### selectedIndex

```java
public NanoComboBox<T> selectedIndex(int index)
```

Sets selection and refreshes the trigger label without closing an open popup or
calling the change listener. The existing popup highlight is not synchronized here.

- **`index`** — item index, or -1 to clear selection

**Returns:** this control

**Throws `IndexOutOfBoundsException`:** if index is outside -1 through the last item

#### formatter

```java
public NanoComboBox<T> formatter(Function<? super T, String> formatter)
```

Replaces the item formatter, closes the popup, and refreshes the selected label.
The formatter runs synchronously for selected text and newly created option rows.

- **`formatter`** — item-to-label function

**Returns:** this control

**Throws `NullPointerException`:** if formatter is null

#### onChange

```java
public NanoComboBox<T> onChange(Consumer<? super T> listener)
```

Replaces the callback for user selection changes. Programmatic item or index
changes do not invoke it, and callback exceptions propagate after popup closure.

- **`listener`** — callback receiving the selected item

**Returns:** this control

**Throws `NullPointerException`:** if listener is null

#### isOpen

```java
public boolean isOpen()
```

Tests whether an overlay reference is retained. This reflects local popup state
rather than independently querying the root's modal stack.

**Returns:** true while this control retains a popup

#### open

```java
public void open()
```

Creates a root-sized modal overlay and virtualized option list if attached, enabled,
nonempty, and currently closed. Positions the list below the control or above when
space is insufficient, bounds its height to the root, and focuses the selected or
first option. Formatting and layout occur synchronously.

#### close

```java
public void close()
```

Clears local popup references and asks the owning root to hide the prior modal
when still attached. Repeated calls are harmless and do not change selection.

#### onDestroy

```java
    public void onDestroy()
```

Closes the modal overlay during node destruction so it does not remain attached
to the root after the control is removed.

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Attempts to open on Space, Enter, or Down and consumes those keys even if opening
is prevented by attachment, disabled state, or an empty list.

- **`event`** — routed control key press

</details>

<a id="type-nanocombobox-popup"></a>

### NanoComboBox.Popup — internal support type

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoComboBox.java#L244)

Root-sized modal receiver around the virtual option list. Handles dismissal and
navigation keys while option buttons perform selection. Its lifetime is controlled
by the enclosing combo box.

<details>
<summary>NanoComboBox.Popup operation reference (2 declarations)</summary>

#### onMousePress

```java
        public void onMousePress(MousePressEvent event)
```

Closes the popup and consumes a mouse press routed to the overlay. There is no
button filter in this callback; option rows have their own input routing.

- **`event`** — overlay mouse press

#### onKeyPress

```java
        public void onKeyPress(KeyPressEvent event)
```

Handles Escape dismissal and bounded Up, Down, Home, and End highlighting.
Navigation materializes and focuses the option row; recognized keys are consumed.
Other keys are left for option button activation or normal routing.

- **`event`** — routed popup key press

</details>

<a id="type-nanocontainer"></a>

### NanoContainer

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoContainer.java#L21)

Base container that participates in NanoVG rendering while delegating child
traversal to the root's shared UI render context. Children can use either
NanoVG or texture-batch painting; their normal order and backend transitions
are handled by that context rather than by directly invoking child draw methods.
This class adds no background or border painting of its own.

Layout, child ownership and input behavior come from UIContainer. Attach
the container to a UIRoot before rendering: the NanoVG callback obtains its
active render context from that root. Neither draw method creates or owns a
NanoVG frame. Subclasses that paint decorations should preserve shared child
dispatch and use the root-managed rendering lifecycle.

<details>
<summary>NanoContainer operation reference (2 declarations)</summary>

#### draw

```java
    public void draw(TextureBatch batch)
```

Enters the standard node rendering path so the active root can dispatch
this container to its NanoVG callback with shared clipping and backend state.
Child painting occurs through that callback rather than this overload.

- **`batch`** — the active texture batch used by mixed UI traversal

#### draw

```java
    public void draw(long vg)
```

Draws all children through the root render context without excluding any
child or adding container decoration. The context applies visibility and
culling rules and can switch backends for individual children. The supplied
NanoVG handle is not used directly because child dispatch belongs to the root.

- **`vg`** — the root-managed NanoVG context for this callback

**Throws `NullPointerException`:** if the container is detached from a root

</details>

<a id="type-nanogrid"></a>

### NanoGrid

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoGrid.java#L26)

Wrapping NanoVG container with uniform optional cell constraints and state-aware
background/border painting. Point-valued cell sizes determine the grid's own
fixed extent from the configured column count and child count. Percentage or
auto cell dimensions leave the corresponding grid dimension automatic, so
actual wrapping remains subject to the layout solver and parent constraints.

Non-auto cell sizes overwrite child width/height constraints and disable
grow/shrink. Switching a cell dimension back to auto skips those assignments
but does not restore each child's previous constraints. Child order is retained
and mixed-renderer painting is delegated to the root render context.

<details>
<summary>NanoGrid operation reference (39 declarations)</summary>

#### BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> BACKGROUND_COLOR_KEY
```

Theme override for the normal background fill; null preserves the local setting.

#### HOVER_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY
```

Theme override for the hovered background fill; null preserves the local setting.

#### FOCUSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY
```

Theme override for the focused background fill; null preserves the local setting.

#### PRESSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> PRESSED_BACKGROUND_COLOR_KEY
```

Theme override for the pressed background fill; null preserves the local setting.

#### DISABLED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_BACKGROUND_COLOR_KEY
```

Theme override for the disabled background fill; null preserves the local setting.

#### BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> BORDER_COLOR_KEY
```

Theme override for the normal border stroke; null preserves the local setting.

#### HOVER_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BORDER_COLOR_KEY
```

Theme override for the hovered border stroke; null preserves the local setting.

#### FOCUSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BORDER_COLOR_KEY
```

Theme override for the focused border stroke; null preserves the local setting.

#### PRESSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> PRESSED_BORDER_COLOR_KEY
```

Theme override for the pressed border stroke; null preserves the local setting.

#### DISABLED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_BORDER_COLOR_KEY
```

Theme override for the disabled border stroke; null preserves the local setting.

#### CORNER_RADIUS_KEY

```java
public static final  StyleKey<Float> CORNER_RADIUS_KEY
```

Theme corner radius in UI units, defaulting to six.

#### BORDER_WIDTH_KEY

```java
public static final  StyleKey<Float> BORDER_WIDTH_KEY
```

Theme border stroke width in UI units, defaulting to one.

#### Constructor

```java
public NanoGrid()
```

Creates a wrapping row layout with centered content, one configured column,
and automatic cell dimensions. Child geometry is assigned during layout.

#### getColumns

```java
public int getColumns()
```

Reads the column count used to calculate fixed grid extents. Actual wrapping
with automatic or percentage widths still depends on available layout space.

**Returns:** configured positive column count

#### columns

```java
public NanoGrid columns(int columns)
```

Sets the positive column count and marks layout dirty only when it changes.
Existing child order and per-cell dimensions are retained.

- **`columns`** — requested number of columns

**Returns:** this grid

**Throws `IllegalArgumentException`:** if columns is below one

#### getCellWidth

```java
public LayoutValue getCellWidth()
```

Returns the immutable cell-width specification, without resolving percentages
or automatic sizing against available parent space.

**Returns:** configured width value

#### cellWidth

```java
public NanoGrid cellWidth(LayoutValue cellWidth)
```

Stores a non-null width specification and marks layout dirty. Non-auto values
will replace child width/min/max/basis and disable child flex growth/shrink.
Auto skips future assignment without restoring old child constraints.

- **`cellWidth`** — immutable cell-width specification

**Returns:** this grid

**Throws `NullPointerException`:** if cellWidth is null

#### cellWidth

```java
public NanoGrid cellWidth(float cellWidth)
```

Stores a point-valued cell width and marks layout dirty. The next layout fixes
the grid width from occupied columns and column gaps. No numeric validation
is performed by this convenience setter.

- **`cellWidth`** — requested fixed width in UI units

**Returns:** this grid

#### getCellHeight

```java
public LayoutValue getCellHeight()
```

Returns the immutable cell-height specification used during grid layout.
Automatic sizing leaves each child's existing constraints untouched.

**Returns:** configured height value

#### cellHeight

```java
public NanoGrid cellHeight(LayoutValue cellHeight)
```

Stores a non-null height specification and marks layout dirty. Non-auto values
replace child height/min/max and disable growth/shrink; auto does not restore
constraints previously installed on the children.

- **`cellHeight`** — immutable cell-height specification

**Returns:** this grid

**Throws `NullPointerException`:** if cellHeight is null

#### cellHeight

```java
public NanoGrid cellHeight(float cellHeight)
```

Stores a point-valued cell height and marks layout dirty. The next layout fixes
grid height from row count and row gaps. No numeric validation is performed.

- **`cellHeight`** — requested fixed height in UI units

**Returns:** this grid

#### cellSize

```java
public NanoGrid cellSize(float cellWidth, float cellHeight)
```

Stores point-valued width and height together and marks layout dirty. Values
are retained without validation; nonnegative finite dimensions are expected.

- **`cellWidth`** — cell width in UI units
- **`cellHeight`** — cell height in UI units

**Returns:** this grid

#### gap

```java
public NanoGrid gap(float gap)
```

Assigns both layout gaps and marks this grid dirty for extent recalculation.

- **`gap`** — spacing between adjacent rows and columns in UI units

**Returns:** this grid

#### rowGap

```java
public NanoGrid rowGap(float rowGap)
```

Assigns vertical row spacing and marks grid layout dirty.

- **`rowGap`** — spacing between wrapped rows in UI units

**Returns:** this grid

#### columnGap

```java
public NanoGrid columnGap(float columnGap)
```

Assigns horizontal column spacing and marks grid layout dirty.

- **`columnGap`** — spacing between adjacent columns in UI units

**Returns:** this grid

#### backgroundColor

```java
public NanoGrid backgroundColor(Color color)
```

Retains a non-null color for the normal background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### hoverBackgroundColor

```java
public NanoGrid hoverBackgroundColor(Color color)
```

Retains a non-null color for the hovered background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### focusedBackgroundColor

```java
public NanoGrid focusedBackgroundColor(Color color)
```

Retains a non-null color for the focused background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### pressedBackgroundColor

```java
public NanoGrid pressedBackgroundColor(Color color)
```

Retains a non-null color for the pressed background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### disabledBackgroundColor

```java
public NanoGrid disabledBackgroundColor(Color color)
```

Retains a non-null color for the disabled background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### borderColor

```java
public NanoGrid borderColor(Color color)
```

Retains a non-null color for the normal border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### hoverBorderColor

```java
public NanoGrid hoverBorderColor(Color color)
```

Retains a non-null color for the hovered border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### focusedBorderColor

```java
public NanoGrid focusedBorderColor(Color color)
```

Retains a non-null color for the focused border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### pressedBorderColor

```java
public NanoGrid pressedBorderColor(Color color)
```

Retains a non-null color for the pressed border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### disabledBorderColor

```java
public NanoGrid disabledBorderColor(Color color)
```

Retains a non-null color for the disabled border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this grid

#### cornerRadius

```java
public NanoGrid cornerRadius(float cornerRadius)
```

Sets the background corner radius, clamping negative values to zero.
Does not mark layout dirty; a later resolved style can overwrite this value.

- **`cornerRadius`** — requested radius in UI units; supply a finite value

**Returns:** this grid

#### borderWidth

```java
public NanoGrid borderWidth(float borderWidth)
```

Sets the inset border stroke width, clamping negative values to zero.
Zero omits the border. A later resolved style can overwrite this value.

- **`borderWidth`** — requested stroke width in UI units; supply a finite value

**Returns:** this grid

#### applyLayout

```java
    protected void applyLayout()
```

Applies resolved paint settings, constrains each child for non-auto cell sizes,
and computes fixed point-based grid extents including gaps. Non-point grid
dimensions are reset to auto. Counts all children, including invisible entries,
and then delegates normal container layout.

#### draw

```java
    public void draw(long vg)
```

Paints background and optional inset border using disabled, pressed, focused,
then hovered color precedence. Draws children through root-managed mixed-backend
traversal afterward. Requires a valid context and enclosing root dispatch.

- **`vg`** — borrowed active NanoVG context

</details>

<a id="type-nanohyperlink"></a>

### NanoHyperlink

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoHyperlink.java#L38)

Focusable NanoVG text link that requests URI opening through the desktop or
platform launcher. Null text becomes empty text; null or blank destinations
do not launch anything. Visited records a successful launch request, not
confirmation that a destination loaded. Changing URL does not reset visited.

Routed Enter/Space presses and accepted left-button releases use the shared
ActivationBehavior policy. The armed flag is bookkeeping and does not gate
release activation. Rendering prioritizes disabled, pressed, focused, hovered,
visited and normal colors in that order.

Text is left-aligned and vertically centered. Automatic layout dimensions
become measured point dimensions during layout; reset them to auto when later
text changes should resize the node. Style colors and fonts are borrowed.
Use the attached root's rendering and input lifecycle on its owning thread.

<details>
<summary>NanoHyperlink operation reference (30 declarations)</summary>

#### COLOR_KEY

```java
public static final  StyleKey<Color> COLOR_KEY
```

Normal link text color; default blue, used when no higher-priority state applies.

#### HOVER_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_COLOR_KEY
```

Hovered text color, below pressed and focused state precedence.

#### FOCUSED_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_COLOR_KEY
```

Focused text color, below pressed state precedence.

#### PRESSED_COLOR_KEY

```java
public static final  StyleKey<Color> PRESSED_COLOR_KEY
```

Pressed text color, overridden only by disabled state.

#### DISABLED_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_COLOR_KEY
```

Disabled text color, taking precedence over all other appearance states.

#### VISITED_COLOR_KEY

```java
public static final  StyleKey<Color> VISITED_COLOR_KEY
```

Visited text color used when enabled and neither pressed, focused nor hovered.

#### FONT_SIZE_KEY

```java
public static final  StyleKey<Float> FONT_SIZE_KEY
```

Font size in layout units, default 18 and clamped to at least one during style refresh.

#### UNDERLINE_THICKNESS_KEY

```java
public static final  StyleKey<Float> UNDERLINE_THICKNESS_KEY
```

Underline stroke width in layout units, default 1.5 and clamped nonnegative.

#### PADDING_X_KEY

```java
public static final  StyleKey<Float> PADDING_X_KEY
```

Horizontal text padding in layout units; default zero, also included twice in automatic width.

#### PADDING_Y_KEY

```java
public static final  StyleKey<Float> PADDING_Y_KEY
```

Vertical padding in layout units; default zero, included twice in automatic height.

#### UNDERLINE_ALWAYS_KEY

```java
public static final  StyleKey<Boolean> UNDERLINE_ALWAYS_KEY
```

Whether to underline without hover, focus or press; default false.

#### FONT_NAME_KEY

```java
public static final  StyleKey<String> FONT_NAME_KEY
```

NanoVG font registration name, default 'default'; blank resolved names use that fallback.

#### Constructor

```java
public NanoHyperlink(String text, String url)
```

Creates a clickable, focusable link without parsing or opening its destination.

- **`text`** — the label, with null normalized to empty text
- **`url`** — the optional destination string, retained without validation

#### getText

```java
public String getText()
```

Returns the current normalized label without measuring or changing layout.

**Returns:** the nonnull displayed text

#### text

```java
public NanoHyperlink text(String text)
```

Replaces the label and marks layout dirty, even for equal text. Existing explicit dimensions are retained; restore automatic sizing separately if desired.

- **`text`** — the replacement label, with null normalized to empty text

**Returns:** this link for chaining

#### getUrl

```java
public String getUrl()
```

Reads the destination without URI parsing or launcher interaction.

**Returns:** the stored destination, possibly null or blank

#### url

```java
public NanoHyperlink url(String url)
```

Stores a destination without validation or resetting visited state. Parsing is deferred until activation.

- **`url`** — the replacement destination, possibly null or blank

**Returns:** this link for chaining

#### isVisited

```java
public boolean isVisited()
```

Reports local visited appearance state, which does not query browser history or confirm page loading.

**Returns:** whether this link is marked visited

#### visited

```java
public NanoHyperlink visited(boolean visited)
```

Sets visited appearance explicitly without opening the destination or changing layout.

- **`visited`** — the desired local visited flag

**Returns:** this link for chaining

#### onCreate

```java
    public void onCreate()
```

Requests layout so text can be measured with the attached root's font context. No owned rendering resource is created.

#### onDestroy

```java
    public void onDestroy()
```

Performs no resource cleanup because this node owns no native font or image. Text, URL and visited fields remain unchanged.

#### update

```java
    public void update(float delta)
```

Performs no frame-based work; state changes are driven by input and style resolution.

- **`delta`** — the frame interval, unused here

#### draw

```java
    public void draw(TextureBatch batch)
```

Delegates to shared node rendering so the root dispatches NanoVG painting with the active clipping and backend state.

- **`batch`** — the active batch for mixed UI traversal

#### onMousePress

```java
    public void onMousePress(MousePressEvent event)
```

Sets armed when enabled with a nonblank destination, otherwise clears it. The event is not inspected or consumed, and armed does not gate release activation.

- **`event`** — the routed press, unused by this implementation

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Delegates to the shared left-button release policy, which consumes accepted releases before activation. Clears armed after the helper returns; no earlier armed press is required here.

- **`event`** — the routed release to test

**Throws `NullPointerException`:** if event is null

#### onKeyPress

```java
    public void onKeyPress(valthorne.event.events.KeyPressEvent event)
```

Delegates enabled Enter/Space activation to ActivationBehavior, consuming accepted presses before the launch attempt. Focus routing and repeat suppression are not implemented here.

- **`event`** — the routed keyboard press

#### onPointerCancel

```java
    public void onPointerCancel()
```

Runs inherited pointer cancellation and clears local armed bookkeeping without changing the destination or visited flag.

#### invalidateStyleTree

```java
    protected void invalidateStyleTree()
```

Invalidates inherited resolved style state and immediately reloads this link's cached appearance values.

#### applyLayout

```java
    protected void applyLayout()
```

Refreshes styling and measures text using the root's NanoVG context when available. Without a context, estimates width from UTF-16 length times font size times 0.56 and uses font size as height. Automatic dimensions become nonnegative measured dimensions plus twice their padding, then inherited layout is applied. Explicit dimensions remain unchanged.

#### draw

```java
    public void draw(long vg)
```

Paints visible text in the prepared NanoVG context, skipping a zero handle. Refreshes style, applies state-color precedence and measures drawn bounds for underlining. Horizontal padding offsets text; vertical padding affects automatic sizing while painting centers text vertically. Temporary bounds arrays are allocated, but no frame is begun or ended here.

- **`vg`** — the prepared root-owned NanoVG context

</details>

<a id="type-nanoimage"></a>

### NanoImage

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoImage.java#L30)

UI image node that uploads borrowed TextureData to a lazily created NanoVG
image and stretches it across the node's layout rectangle. The image pattern
uses full opacity and no rotation; aspect ratio follows the assigned width
and height rather than a separate fit policy.

Construction with a texture initializes layout dimensions from its pixel
dimensions. The native image is created on the first draw and recreated when
the NanoVG context changes. Texture replacement or node destruction deletes
the owned image handle; the source TextureData is retained by reference and
is never disposed by this node.

Keep source pixel storage available for initial upload and any later context
change. In-place edits to that storage are not detected once an image exists,
and assigning the same TextureData instance is a no-op. Rendering and cleanup
belong on the graphics thread while the image's owning context remains valid.
Attach the node to a UIRoot and use its mixed-backend drawing lifecycle.

<details>
<summary>NanoImage operation reference (8 declarations)</summary>

#### Constructor

```java
public NanoImage(TextureData texture)
```

Retains optional source pixels and, when present, sets initial layout width
and height to their dimensions. No native image is created at construction.

- **`texture`** — the borrowed source data, or null for an initially empty node

#### onCreate

```java
    public void onCreate()
```

Leaves image creation deferred until drawing supplies the NanoVG context.
This lifecycle hook performs no resource allocation.

#### onDestroy

```java
    public void onDestroy()
```

Releases the owned NanoVG image through its recorded context and clears
handle bookkeeping. The borrowed source data remains retained and is not
disposed. Run before the owning NanoVG context is destroyed.

#### update

```java
    public void update(float delta)
```

Performs no per-frame work; this node has no image animation or upload
polling. Source replacement is explicit through texture(TextureData).

- **`delta`** — the frame interval supplied by UI traversal, unused here

#### draw

```java
    public void draw(TextureBatch batch)
```

Routes drawing through the shared UI rendering entry point so an attached
root can dispatch this node to NanoVG with the proper clipping and state.

- **`batch`** — the active texture batch used by mixed UI traversal

#### draw

```java
    public void draw(long vg)
```

Lazily ensures a context-local image, then fills the node's absolute layout
rectangle with its image pattern. Null source data, a zero context, failed
image creation or nonpositive layout dimensions produce no painted image.
Upload is attempted before the layout-size check. A temporary native paint
structure is freed after use; frame and context ownership remain with the root.

- **`vg`** — the prepared NanoVG context, or zero to skip drawing

#### getTexture

```java
public TextureData getTexture()
```

Returns the retained source directly, without copying pixels or exposing
the separate native image. Mutating its pixels does not refresh an existing upload.

**Returns:** the borrowed TextureData, or null when no source is assigned

#### texture

```java
public NanoImage texture(TextureData texture)
```

Replaces source data after deleting any existing native image. A nonnull
replacement sets layout dimensions from the source and enables flex grow
and shrink with automatic basis through Layout.fill. Null clears the image
source while retaining current layout settings. Changed sources mark layout
dirty; assigning the identical reference performs no work or refresh.

- **`texture`** — the new borrowed source, or null to stop painting

**Returns:** this node for chaining

</details>

<a id="type-nanolabel"></a>

### NanoLabel

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoLabel.java#L26)

NanoVG text leaf with explicit line breaks, expanded tab stops, and left/top
alignment. Fonts are selected by an already registered NanoVG name; this node
does not load or own font resources. Width and height are measured from the
root's context when available, otherwise estimated from character count.

Layout replaces auto width/height with measured numeric values. Subsequent
text changes mark layout dirty but do not restore those dimensions to auto;
set them back to auto when another content-size measurement is desired.
Text is normalized for measurement and drawing without modifying getText().

<details>
<summary>NanoLabel operation reference (25 declarations)</summary>

#### FONT_NAME_KEY

```java
public static final  StyleKey<String> FONT_NAME_KEY
```

Theme font registration name, defaulting to default.

#### FONT_SIZE_KEY

```java
public static final  StyleKey<Float> FONT_SIZE_KEY
```

Theme text size in UI units, defaulting to 18.

#### COLOR_KEY

```java
public static final  StyleKey<Color> COLOR_KEY
```

Theme text color, defaulting to the shared white color.

#### TAB_SIZE_KEY

```java
public static final  StyleKey<Float> TAB_SIZE_KEY
```

Theme tab-stop spacing in character columns, defaulting to four.

#### LINE_SPACING_KEY

```java
public static final  StyleKey<Float> LINE_SPACING_KEY
```

Theme extra line spacing in UI units, defaulting to zero.

#### Constructor

```java
public NanoLabel()
```

Creates an empty label using the default font name, size 18, white color,
four-column tab stops, and zero extra line spacing. Font registration belongs
to the root/application.

#### Constructor

```java
public NanoLabel(String text)
```

Creates a label with the supplied raw text and default font settings.
Text normalization is deferred to layout and drawing.

- **`text`** — label contents; null is stored as an empty string

#### getText

```java
public String getText()
```

Returns the raw stored text before newline normalization and tab expansion.

**Returns:** non-null label contents

#### text

```java
public NanoLabel text(String text)
```

Replaces raw contents, converting null to empty, and marks layout dirty.
Previously measured numeric dimensions are retained unless restored to auto.

- **`text`** — new label contents, possibly null

**Returns:** this label

#### getFontName

```java
public String getFontName()
```

Reads the name selected for NanoVG font lookup; this does not verify registration.

**Returns:** stored font name

#### fontName

```java
public NanoLabel fontName(String fontName)
```

Stores a nonblank font name and marks layout dirty. Null or blank input keeps
the old name but still dirties layout. No font resource is loaded here.

- **`fontName`** — name of a font registered in the root's NanoVG context

**Returns:** this label

#### getFontSize

```java
public float getFontSize()
```

Reads the requested NanoVG text size used by measurement and drawing.

**Returns:** font size in UI units

#### fontSize

```java
public NanoLabel fontSize(float fontSize)
```

Sets text size with a minimum of one and marks layout dirty. Supply a finite
value; Math.max does not reject NaN. Resolved styles can later replace it.

- **`fontSize`** — requested size in UI units

**Returns:** this label

#### getColor

```java
public Color getColor()
```

Exposes the current borrowed text color. Mutating it affects rendering and
may also affect other objects sharing that color, including shared constants.

**Returns:** live color reference

#### color

```java
public NanoLabel color(Color color)
```

Retains a non-null color without copying it or dirtying geometry. Null leaves
the current value unchanged; style application can replace the reference.

- **`color`** — text color reference, or null to retain the current value

**Returns:** this label

#### getTabSize

```java
public float getTabSize()
```

Reads the requested tab-stop interval before rounding to an integer column count.

**Returns:** configured interval in character columns

#### tabSize

```java
public NanoLabel tabSize(float tabSize)
```

Sets tab-stop spacing with a minimum of one and marks layout dirty. Rendering
rounds the value to an integer and advances each tab to the next stop.

- **`tabSize`** — finite requested column interval

**Returns:** this label

#### getLineSpacing

```java
public float getLineSpacing()
```

Reads extra spacing added between measured text lines.

**Returns:** additional interline distance in UI units

#### lineSpacing

```java
public NanoLabel lineSpacing(float lineSpacing)
```

Sets nonnegative extra line spacing and marks layout dirty. No spacing is
added after the last line; resolved styles may replace this value.

- **`lineSpacing`** — finite requested extra distance in UI units

**Returns:** this label

#### onCreate

```java
    public void onCreate()
```

Performs no resource allocation. The owning root/application is responsible
for font registration and the NanoVG context before layout or drawing.

#### onDestroy

```java
    public void onDestroy()
```

Performs no resource disposal because this leaf borrows the root's context
and registered fonts.

#### update

```java
    public void update(float delta)
```

Performs no per-frame work or child traversal; label contents change through
setters and layout processing.

- **`delta`** — elapsed update seconds, unused

#### draw

```java
    public void draw(TextureBatch batch)
```

Enters shared UI dispatch so the root can switch to the NanoVG callback
with the correct clipping and backend state.

- **`batch`** — active texture batch used by mixed-renderer traversal

#### applyLayout

```java
    protected void applyLayout()
```

Applies resolved font/color/spacing values, normalizes text, and measures
all lines. Without a root NanoVG handle, estimates width as half font size
per UTF-16 unit and line height as font size. Replaces only dimensions still
marked auto, then delegates base layout. Font and color resources are borrowed.

#### draw

```java
    public void draw(long vg)
```

Draws normalized lines at absolute node coordinates using left/top alignment.
Skips invisible nodes and a zero context handle. The root must already have
begun the NanoVG frame and prepared transforms and clipping.

- **`vg`** — borrowed active NanoVG context, or zero to skip

</details>

<a id="type-nanomodal"></a>

### NanoModal

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoModal.java#L35)

NanoVG modal overlay containing a centered dialog panel and optional content.
The constructor's parent locates the root when opening; root registration owns
focus scoping and pointer cancellation. Without a parent root, open only changes
visibility and does not establish input isolation. Content may use either renderer.

Escape dismissal is enabled by default; outside-click dismissal is opt-in
and requires non-null content. Dialog style keys customize the inner panel,
while inherited NanoPanel settings paint the surrounding overlay.

```java
NanoModal modal = new NanoModal(parentNode);
modal.getDialog().getLayout().width(360).height(200).padding(16);
modal.content(new NanoLabel("Settings"));
modal.closeOnOutsideClick(true).open();
// Dismiss through the modal API to unwind the root's focus scope:
modal.close();
```

<details>
<summary>NanoModal operation reference (31 declarations)</summary>

#### DIALOG_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_BACKGROUND_COLOR_KEY
```

Theme background color for the inner dialog, separate from overlay painting.

#### DIALOG_HOVER_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_HOVER_BACKGROUND_COLOR_KEY
```

Theme hover background color for the inner dialog, separate from overlay painting.

#### DIALOG_FOCUSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_FOCUSED_BACKGROUND_COLOR_KEY
```

Theme focused background color for the inner dialog, separate from overlay painting.

#### DIALOG_PRESSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_PRESSED_BACKGROUND_COLOR_KEY
```

Theme pressed background color for the inner dialog, separate from overlay painting.

#### DIALOG_DISABLED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_DISABLED_BACKGROUND_COLOR_KEY
```

Theme disabled background color for the inner dialog, separate from overlay painting.

#### DIALOG_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_BORDER_COLOR_KEY
```

Theme border color for the inner dialog, separate from overlay painting.

#### DIALOG_HOVER_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_HOVER_BORDER_COLOR_KEY
```

Theme hover border color for the inner dialog, separate from overlay painting.

#### DIALOG_FOCUSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_FOCUSED_BORDER_COLOR_KEY
```

Theme focused border color for the inner dialog, separate from overlay painting.

#### DIALOG_PRESSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_PRESSED_BORDER_COLOR_KEY
```

Theme pressed border color for the inner dialog, separate from overlay painting.

#### DIALOG_DISABLED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> DIALOG_DISABLED_BORDER_COLOR_KEY
```

Theme disabled border color for the inner dialog, separate from overlay painting.

#### DIALOG_CORNER_RADIUS_KEY

```java
public static final  StyleKey<Float> DIALOG_CORNER_RADIUS_KEY
```

Theme corner radius for the inner dialog, separate from overlay painting.

#### DIALOG_BORDER_WIDTH_KEY

```java
public static final  StyleKey<Float> DIALOG_BORDER_WIDTH_KEY
```

Theme border width for the inner dialog, separate from overlay painting.

#### Constructor

```java
public NanoModal(UINode parentNode)
```

Retains a parent for root lookup, creates a centered dialog, and starts hidden.
The overlay is clickable, focusable, and scrollable to participate in modal
input routing. It is not attached to the parent's ordinary children here.

- **`parentNode`** — non-null node whose root will own the opened overlay

**Throws `NullPointerException`:** if parentNode is null

#### getDialog

```java
public NanoPanel getDialog()
```

Exposes the live inner panel for size, padding, and appearance configuration.
Use content to replace the managed content node; arbitrary children are separate.

**Returns:** owned dialog panel

#### getModalParent

```java
public UINode getModalParent()
```

Returns the constructor-supplied root-lookup node, which need not be the
modal's current structural parent in the overlay hierarchy.

**Returns:** borrowed modal parent reference

#### getContent

```java
public UINode getContent()
```

Reads the content reference last assigned through content.

**Returns:** managed content, or null

#### content

```java
public NanoModal content(UINode content)
```

Removes the old managed content, stores the replacement, and adds it to the
dialog when non-null. Marks layout dirty. Removal precedes insertion, so an
insertion failure is not a transactional rollback to the old content.

- **`content`** — replacement node accepted by the dialog's child-ownership policy, or null to clear

**Returns:** this modal

#### isCloseOnEscape

```java
public boolean isCloseOnEscape()
```

Reads whether an Escape press is handled as modal dismissal.

**Returns:** configured Escape-dismissal flag

#### closeOnEscape

```java
public NanoModal closeOnEscape(boolean closeOnEscape)
```

Changes Escape handling without opening or closing the overlay.

- **`closeOnEscape`** — true to dismiss and consume Escape presses

**Returns:** this modal

#### isCloseOnOutsideClick

```java
public boolean isCloseOnOutsideClick()
```

Reads the requested outside-click policy. It only takes effect when content
is non-null and a pointer press is delivered to this handler.

**Returns:** outside-click dismissal flag

#### closeOnOutsideClick

```java
public NanoModal closeOnOutsideClick(boolean closeOnOutsideClick)
```

Changes outside-dialog press handling without altering current visibility.
The handler does not restrict dismissal to a particular pointer button.

- **`closeOnOutsideClick`** — true to enable outside-press dismissal

**Returns:** this modal

#### isOpen

```java
public boolean isOpen()
```

Reports visibility, which does not by itself guarantee root modal registration.
Detached open calls can make the overlay visible without a focus scope.

**Returns:** current visible state

#### open

```java
public NanoModal open()
```

Shows through the parent's root when available, letting it establish a modal
focus scope and cancel prior pointer state. If no root exists, only makes
this node visible. Repeated root registration is handled by the root.

**Returns:** this modal

#### close

```java
public NanoModal close()
```

Asks the current root to hide the modal and unwind overlay/focus state.
Without a root, only clears visibility. Content remains available for reopening.

**Returns:** this modal

#### toggle

```java
public NanoModal toggle()
```

Chooses close or open from current visibility. Uses the same root lookup and
focus-scope behavior as those operations.

**Returns:** this modal

#### findNodeAt

```java
    public UINode findNodeAt(float x, float y, int requiredBit)
```

Rejects invisible, disabled, or out-of-bounds queries, then searches descendants.
Falls back to the overlay itself when it satisfies the requested capability,
preventing an otherwise empty modal surface from passing that hit through.

- **`x`** — hit-test X in UI world coordinates
- **`y`** — hit-test Y in UI world coordinates
- **`requiredBit`** — node capability bit, or negative to accept any

**Returns:** matching modal descendant/overlay, or null

#### onMousePress

```java
    public void onMousePress(MousePressEvent event)
```

When enabled and content exists, converts screen coordinates to UI world space
and closes on a press outside the dialog bounds. Does not consume the event
or restrict the mouse button; root modal routing supplies input isolation.

- **`event`** — routed pointer press

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Closes and consumes Escape when dismissal is enabled. Other keys are left
untouched; root routing determines whether this handler receives the event.

- **`event`** — routed key press

#### applyLayout

```java
    protected void applyLayout()
```

Copies non-null modal-specific theme colors and dimensions into the inner
dialog, then applies inherited overlay panel styling and layout. The dialog's
own resolved styles can also participate in its later layout pass.

#### update

```java
    public void update(float delta)
```

Delegates to normal panel/container update traversal for the dialog subtree.
Visibility eligibility is supplied by the surrounding UI lifecycle.

- **`delta`** — elapsed update seconds

#### draw

```java
    public void draw(TextureBatch batch)
```

Routes the overlay through shared UI rendering so inherited NanoVG decoration
and mixed-renderer dialog children receive prepared backend state.

- **`batch`** — active texture batch used by UI traversal

</details>

<a id="type-nanonode"></a>

### NanoNode

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoNode.java#L25)

Declares the NanoVG painting capability of a `valthorne.ui.UINode`.
During `valthorne.ui.UIRoot#draw()`, the active render context detects
this interface and invokes `draw(long)` regardless of whether a parent
or child uses texture-batch painting. Implementing this interface does not
itself supply layout, input handling, or child traversal.

The owning root manages the NanoVG context and frame lifetime. Implementations
draw into that prepared context and must not begin or end frames, delete the
context, or bypass mixed-backend dispatch when drawing children. The render
context applies the shared camera, translation and clipping state before the
callback and restores saved NanoVG state afterward.

Containers can delegate traversal to `NanoContainer` or call
`valthorne.ui.UINode#render(valthorne.graphics.texture.TextureBatch)`
with the active context's batch. Drawing a NanoVG node through a detached
node's normal render entry point is unsupported; attach it to a root and use
the root's draw lifecycle.

<details>
<summary>NanoNode operation reference (1 declarations)</summary>

#### draw

```java
void draw(long nanoHandle)
```

Paints this node using the root-owned NanoVG context and the coordinate
mapping prepared by the active UI render context. The callback runs on
the graphics-context thread during a root draw; implementations may issue
NanoVG drawing commands but must leave frame and context ownership to the root.

A container implementation is responsible for rendering its children
through the shared UI dispatch in the intended painter order. Saved NanoVG
state is restored when this callback exits, including exceptional exits.
The render context restores backend state at the enclosing dispatch or
child-traversal boundary; adjacent siblings can share a NanoVG interval.

- **`nanoHandle`** — the borrowed, nonzero NanoVG context handle for this draw

</details>

<a id="type-nanopanel"></a>

### NanoPanel

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoPanel.java#L19)

NanoVG container painting a rounded background and optional inset border before
mixed-renderer child traversal. Draw colors use disabled, pressed, focused,
then hovered priority. Colors are retained by reference and may be replaced
by resolved style values during layout; direct setters do not lock out themes.
Use through a root-managed NanoVG frame and leave context ownership to the root.

<details>
<summary>NanoPanel operation reference (26 declarations)</summary>

#### BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> BACKGROUND_COLOR_KEY
```

Theme override for the normal background fill; null preserves the local setting.

#### HOVER_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY
```

Theme override for the hovered background fill; null preserves the local setting.

#### FOCUSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY
```

Theme override for the focused background fill; null preserves the local setting.

#### PRESSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> PRESSED_BACKGROUND_COLOR_KEY
```

Theme override for the pressed background fill; null preserves the local setting.

#### DISABLED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_BACKGROUND_COLOR_KEY
```

Theme override for the disabled background fill; null preserves the local setting.

#### BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> BORDER_COLOR_KEY
```

Theme override for the normal border stroke; null preserves the local setting.

#### HOVER_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BORDER_COLOR_KEY
```

Theme override for the hovered border stroke; null preserves the local setting.

#### FOCUSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BORDER_COLOR_KEY
```

Theme override for the focused border stroke; null preserves the local setting.

#### PRESSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> PRESSED_BORDER_COLOR_KEY
```

Theme override for the pressed border stroke; null preserves the local setting.

#### DISABLED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_BORDER_COLOR_KEY
```

Theme override for the disabled border stroke; null preserves the local setting.

#### CORNER_RADIUS_KEY

```java
public static final  StyleKey<Float> CORNER_RADIUS_KEY
```

Theme corner radius in UI units, defaulting to six.

#### BORDER_WIDTH_KEY

```java
public static final  StyleKey<Float> BORDER_WIDTH_KEY
```

Theme border stroke width in UI units, defaulting to one.

#### backgroundColor

```java
public NanoPanel backgroundColor(Color color)
```

Retains a non-null color for the normal background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### hoverBackgroundColor

```java
public NanoPanel hoverBackgroundColor(Color color)
```

Retains a non-null color for the hovered background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### focusedBackgroundColor

```java
public NanoPanel focusedBackgroundColor(Color color)
```

Retains a non-null color for the focused background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### pressedBackgroundColor

```java
public NanoPanel pressedBackgroundColor(Color color)
```

Retains a non-null color for the pressed background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### disabledBackgroundColor

```java
public NanoPanel disabledBackgroundColor(Color color)
```

Retains a non-null color for the disabled background fill; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### borderColor

```java
public NanoPanel borderColor(Color color)
```

Retains a non-null color for the normal border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### hoverBorderColor

```java
public NanoPanel hoverBorderColor(Color color)
```

Retains a non-null color for the hovered border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### focusedBorderColor

```java
public NanoPanel focusedBorderColor(Color color)
```

Retains a non-null color for the focused border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### pressedBorderColor

```java
public NanoPanel pressedBorderColor(Color color)
```

Retains a non-null color for the pressed border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### disabledBorderColor

```java
public NanoPanel disabledBorderColor(Color color)
```

Retains a non-null color for the disabled border stroke; null leaves it unchanged.
The color is borrowed rather than copied, and resolved styles may replace it.

- **`color`** — mutable color reference, or null to keep the current value

**Returns:** this panel

#### cornerRadius

```java
public NanoPanel cornerRadius(float cornerRadius)
```

Sets the background corner radius, clamping negative values to zero.
Does not mark layout dirty; a later resolved style can overwrite this value.

- **`cornerRadius`** — requested radius in UI units; supply a finite value

**Returns:** this panel

#### borderWidth

```java
public NanoPanel borderWidth(float borderWidth)
```

Sets the inset border stroke width, clamping negative values to zero.
Zero omits the border. A later resolved style can overwrite this value.

- **`borderWidth`** — requested stroke width in UI units; supply a finite value

**Returns:** this panel

#### applyLayout

```java
    protected void applyLayout()
```

Copies non-null resolved state colors and dimensions into local paint settings,
clamping radii and widths to nonnegative values, then runs container layout.
Theme colors are borrowed references; missing color values retain current ones.

#### draw

```java
    public void draw(long vg)
```

Paints the background, then an inset border when width is positive, selecting
state colors with disabled/pressed/focused/hovered precedence. Delegates child
painting to the root's shared context afterward. The caller supplies a valid
NanoVG frame and visibility handling through normal root dispatch.

- **`vg`** — borrowed active NanoVG context

</details>

<a id="type-nanoprogressbar"></a>

### NanoProgressBar

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoProgressBar.java#L27)

NanoVG progress indicator with a target value and a separately animated display
value. Horizontal fill grows left to right; vertical fill grows from the bottom.
The optional percentage text and finished state use displayed progress rather
than the pending target. Call update with elapsed seconds to advance animation.

Range endpoints are retained without validation; use finite ordered values.
Colors and registered fonts are borrowed, and style application can overwrite
local paint and animation settings. The progress callback runs synchronously
when displayed progress changes, rather than on every target assignment.

<details>
<summary>NanoProgressBar operation reference (38 declarations)</summary>

#### BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> BACKGROUND_COLOR_KEY
```

Theme color for the unfilled background.

#### FOREGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> FOREGROUND_COLOR_KEY
```

Theme color for the displayed filled region.

#### BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> BORDER_COLOR_KEY
```

Theme border-stroke color.

#### TEXT_COLOR_KEY

```java
public static final  StyleKey<Color> TEXT_COLOR_KEY
```

Theme percentage-text color.

#### FONT_NAME_KEY

```java
public static final  StyleKey<String> FONT_NAME_KEY
```

Theme registered font name for percentage text.

#### FONT_SIZE_KEY

```java
public static final  StyleKey<Float> FONT_SIZE_KEY
```

Theme percentage font size in UI units.

#### CORNER_RADIUS_KEY

```java
public static final  StyleKey<Float> CORNER_RADIUS_KEY
```

Theme corner radius in UI units.

#### BORDER_WIDTH_KEY

```java
public static final  StyleKey<Float> BORDER_WIDTH_KEY
```

Theme border width and fill inset in UI units.

#### TEXT_PADDING_X_KEY

```java
public static final  StyleKey<Float> TEXT_PADDING_X_KEY
```

Theme horizontal percentage-label padding in UI units.

#### ANIMATION_DURATION_KEY

```java
public static final  StyleKey<Float> ANIMATION_DURATION_KEY
```

Theme interpolation duration in seconds, defaulting to 0.25.

#### Constructor

```java
public NanoProgressBar(float min, float max)
```

Initializes target, displayed value, and animation endpoints to min. The
constructor stores range endpoints unchecked; use finite min not greater than
max. A zero-width range draws zero percent regardless of its finished state.

- **`min`** — lower progress endpoint
- **`max`** — upper progress endpoint

#### progress

```java
public NanoProgressBar progress(float progress)
```

Clamps a new target to the stored range and starts interpolation from the
current displayed value. An unchanged target does nothing. With zero duration,
snaps the display and invokes the callback immediately; otherwise notification
waits for update to change the displayed value.

- **`progress`** — requested finite target value

**Returns:** this progress bar

#### onProgress

```java
public NanoProgressBar onProgress(NodeAction<NanoProgressBar> action)
```

Replaces the synchronous displayed-value callback. It runs during animated
updates or immediate target snaps, and can read both target and displayed
values. Assigning null disables notifications.

- **`action`** — callback receiving this bar, or null

**Returns:** this progress bar

#### getProgress

```java
public float getProgress()
```

Reads the clamped target value, which may be ahead of the animated display.

**Returns:** current target in the configured range's units

#### getDisplayedProgress

```java
public float getDisplayedProgress()
```

Reads the value currently represented by fill, percentage text, and completion.

**Returns:** animated display value

#### getAnimationDuration

```java
public float getAnimationDuration()
```

Reads the configured interpolation duration, which styles may also set.

**Returns:** transition duration in seconds

#### animationDuration

```java
public NanoProgressBar animationDuration(float animationDuration)
```

Clamps negative duration to zero. Zero snaps all animation state to the current
target without invoking the progress callback. Changing a positive duration
retains elapsed time and endpoints for the next update.

- **`animationDuration`** — finite requested transition seconds

**Returns:** this progress bar

#### displayPercentage

```java
public NanoProgressBar displayPercentage(boolean displayPercentage)
```

Enables or hides centered percentage text without changing progress or layout.
Text uses displayed progress formatted with two decimal places.

- **`displayPercentage`** — whether to draw the percentage label

**Returns:** this progress bar

#### isDisplayPercentage

```java
public boolean isDisplayPercentage()
```

Reads whether the percentage label is enabled independently of node visibility.

**Returns:** configured percentage-text flag

#### isVertical

```java
public boolean isVertical()
```

Reads fill orientation without changing animation state.

**Returns:** true for bottom-to-top fill, false for left-to-right fill

#### vertical

```java
public NanoProgressBar vertical(boolean vertical)
```

Selects bottom-to-top fill when true and left-to-right fill otherwise.
Progress values, animation timing, and layout dimensions are unchanged.

- **`vertical`** — requested vertical orientation

**Returns:** this progress bar

#### horizontal

```java
public NanoProgressBar horizontal(boolean horizontal)
```

Selects left-to-right fill when true and bottom-to-top fill otherwise.
Equivalent to assigning the inverse vertical flag.

- **`horizontal`** — requested horizontal orientation

**Returns:** this progress bar

#### backgroundColor

```java
public NanoProgressBar backgroundColor(Color color)
```

Retains a non-null color for the unfilled background without copying it.
Null preserves the current reference; resolved styles may later replace it.

- **`color`** — mutable paint color, or null to keep the current value

**Returns:** this progress bar

#### foregroundColor

```java
public NanoProgressBar foregroundColor(Color color)
```

Retains a non-null color for the filled region without copying it.
Null preserves the current reference; resolved styles may later replace it.

- **`color`** — mutable paint color, or null to keep the current value

**Returns:** this progress bar

#### borderColor

```java
public NanoProgressBar borderColor(Color color)
```

Retains a non-null color for the border stroke without copying it.
Null preserves the current reference; resolved styles may later replace it.

- **`color`** — mutable paint color, or null to keep the current value

**Returns:** this progress bar

#### textColor

```java
public NanoProgressBar textColor(Color color)
```

Retains a non-null color for the percentage text without copying it.
Null preserves the current reference; resolved styles may later replace it.

- **`color`** — mutable paint color, or null to keep the current value

**Returns:** this progress bar

#### fontName

```java
public NanoProgressBar fontName(String fontName)
```

Retains a nonblank registered NanoVG font name; null or blank leaves it unchanged.
No font is loaded, and resolved styles may later replace the name.

- **`fontName`** — existing font registration name

**Returns:** this progress bar

#### fontSize

```java
public NanoProgressBar fontSize(float fontSize)
```

Sets percentage font size in UI units, clamping below 1 to 1.
This changes painting rather than measured layout; styles may override it.

- **`fontSize`** — finite requested percentage font size

**Returns:** this progress bar

#### cornerRadius

```java
public NanoProgressBar cornerRadius(float cornerRadius)
```

Sets rounded-corner radius in UI units, clamping below 0 to 0.
This changes painting rather than measured layout; styles may override it.

- **`cornerRadius`** — finite requested rounded-corner radius

**Returns:** this progress bar

#### borderWidth

```java
public NanoProgressBar borderWidth(float borderWidth)
```

Sets border width and fill inset in UI units, clamping below 0 to 0.
This changes painting rather than measured layout; styles may override it.

- **`borderWidth`** — finite requested border width and fill inset

**Returns:** this progress bar

#### textPaddingX

```java
public NanoProgressBar textPaddingX(float textPaddingX)
```

Sets horizontal percentage padding in UI units, clamping below 0 to 0.
This changes painting rather than measured layout; styles may override it.

- **`textPaddingX`** — finite requested horizontal percentage padding

**Returns:** this progress bar

#### isFinished

```java
public boolean isFinished()
```

Checks the displayed value against max. A target reaching max is insufficient
until animation arrives; equal range endpoints are finished immediately.

**Returns:** true when displayed progress is at least max

#### onCreate

```java
    public void onCreate()
```

Allocates no resources. The root manages the NanoVG context and the application
must register the font used for percentage text.

#### onDestroy

```java
    public void onDestroy()
```

Releases no native resources because the widget borrows its colors and font
registration and owns no NanoVG context.

#### update

```java
    public void update(float delta)
```

Interpolates displayed progress toward the target, clamping negative elapsed
time to zero and the animation fraction to one. Invokes the callback only
when the displayed value changes. A completed transition aligns endpoints
with the target; a clean display returns immediately.

- **`delta`** — finite elapsed seconds since the previous update

#### draw

```java
    public void draw(TextureBatch batch)
```

Routes painting through the root's mixed-renderer dispatch and prepared state.

- **`batch`** — active texture batch used by the UI traversal

#### applyLayout

```java
    protected void applyLayout()
```

Applies non-null theme colors, font settings, dimensions, and duration before
base layout. Theme duration assignment does not use the public setter's
immediate snap; the next update resolves a zero-duration transition.

#### draw

```java
    public void draw(long vg)
```

Paints background, border-inset fill, optional border, and optional percentage
text using the displayed fraction. Skips invisible nodes and a zero handle.
The root supplies the active frame, transforms, and clipping; this leaf does
not traverse children or manage NanoVG lifetime.

- **`vg`** — borrowed active NanoVG context, or zero to skip

</details>

<a id="type-nanoscrollpanel"></a>

### NanoScrollPanel

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoScrollPanel.java#L40)

Scrollable NanoVG container with one managed content node and optional horizontal
and vertical scrollbars. Offsets apply immediately; target fields retain the
same requested positions for post-layout clamping rather than driving animation.
Rendering clips and translates children through the shared root context, allowing
mixed NanoVG and texture content to scroll together.

Scrollbar metrics reuse a mutable cache. Maximum offsets include the cached
opposite scrollbar thickness, so range getters reflect the current metric cache
rather than independently solving both bar visibilities. Use the normal layout
and draw lifecycle after content/style changes. Configure the content through
setContent rather than adding unrelated children to this container.

```java
NanoScrollPanel scroll = new NanoScrollPanel();
scroll.getLayout().width(400).height(250);
NanoPanel body = new NanoPanel();
body.getLayout().width(700).height(600);
scroll.setContent(body);
// After the root has laid out content:
scroll.scroll(80, 120);
```

<details>
<summary>NanoScrollPanel operation reference (45 declarations)</summary>

#### BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> BACKGROUND_COLOR_KEY
```

Theme background color for scrollbar painting; sizes use UI units.

#### BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> BORDER_COLOR_KEY
```

Theme border color for scrollbar painting; sizes use UI units.

#### BORDER_WIDTH_KEY

```java
public static final  StyleKey<Float> BORDER_WIDTH_KEY
```

Theme border width for scrollbar painting; sizes use UI units.

#### HORIZONTAL_BAR_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> HORIZONTAL_BAR_BACKGROUND_COLOR_KEY
```

Theme horizontal bar background color for scrollbar painting; sizes use UI units.

#### HORIZONTAL_BAR_FOREGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> HORIZONTAL_BAR_FOREGROUND_COLOR_KEY
```

Theme horizontal bar foreground color for scrollbar painting; sizes use UI units.

#### HORIZONTAL_BAR_HOVER_FOREGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> HORIZONTAL_BAR_HOVER_FOREGROUND_COLOR_KEY
```

Theme horizontal bar hover foreground color for scrollbar painting; sizes use UI units.

#### HORIZONTAL_BAR_PRESSED_FOREGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> HORIZONTAL_BAR_PRESSED_FOREGROUND_COLOR_KEY
```

Theme horizontal bar pressed foreground color for scrollbar painting; sizes use UI units.

#### VERTICAL_BAR_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> VERTICAL_BAR_BACKGROUND_COLOR_KEY
```

Theme vertical bar background color for scrollbar painting; sizes use UI units.

#### VERTICAL_BAR_FOREGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> VERTICAL_BAR_FOREGROUND_COLOR_KEY
```

Theme vertical bar foreground color for scrollbar painting; sizes use UI units.

#### VERTICAL_BAR_HOVER_FOREGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> VERTICAL_BAR_HOVER_FOREGROUND_COLOR_KEY
```

Theme vertical bar hover foreground color for scrollbar painting; sizes use UI units.

#### VERTICAL_BAR_PRESSED_FOREGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> VERTICAL_BAR_PRESSED_FOREGROUND_COLOR_KEY
```

Theme vertical bar pressed foreground color for scrollbar painting; sizes use UI units.

#### HORIZONTAL_BAR_HEIGHT_KEY

```java
public static final  StyleKey<Float> HORIZONTAL_BAR_HEIGHT_KEY
```

Theme horizontal bar height for scrollbar painting; sizes use UI units.

#### VERTICAL_BAR_WIDTH_KEY

```java
public static final  StyleKey<Float> VERTICAL_BAR_WIDTH_KEY
```

Theme vertical bar width for scrollbar painting; sizes use UI units.

#### BAR_PADDING_KEY

```java
public static final  StyleKey<Float> BAR_PADDING_KEY
```

Theme bar padding for scrollbar painting; sizes use UI units.

#### MIN_THUMB_SIZE_KEY

```java
public static final  StyleKey<Float> MIN_THUMB_SIZE_KEY
```

Theme min thumb size for scrollbar painting; sizes use UI units.

#### CORNER_RADIUS_KEY

```java
public static final  StyleKey<Float> CORNER_RADIUS_KEY
```

Theme corner radius for scrollbar painting; sizes use UI units.

#### Constructor

```java
public NanoScrollPanel()
```

Creates a scrollable container with an attached empty NanoPanel as content.
Both axes and scrollbar painting start enabled; initial offsets are zero.

#### getContent

```java
public UINode getContent()
```

Returns the managed content node whose laid-out dimensions define scroll range.
The node is live and may be configured through its own layout/child APIs.

**Returns:** managed content, or null after clearing

#### setContent

```java
public void setContent(UINode child)
```

Replaces managed content after rejecting an already-parented replacement.
Assigning the same node is a no-op; null removes content. Offsets are not reset
here and are reconciled by the normal layout lifecycle.

- **`child`** — fresh unattached content, or null to clear

**Throws `IllegalStateException`:** if a different replacement already has a parent

#### scrollSpeed

```java
public NanoScrollPanel scrollSpeed(float speed)
```

Stores the wheel-to-offset multiplier without validation or moving content.
Negative values reverse wheel direction; callers should supply a finite value.

- **`speed`** — UI units per wheel offset unit

**Returns:** this panel

#### horizontal

```java
public NanoScrollPanel horizontal(boolean horizontal)
```

Enables horizontal scrolling or resets both X offsets and horizontal drag/hover
flags when disabled. Reenabling clamps existing offsets against the cached range.

- **`horizontal`** — whether X scrolling is allowed

**Returns:** this panel

#### vertical

```java
public NanoScrollPanel vertical(boolean vertical)
```

Enables vertical scrolling or resets both Y offsets and vertical drag/hover
flags when disabled. Reenabling clamps existing offsets against the cached range.

- **`vertical`** — whether Y scrolling is allowed

**Returns:** this panel

#### horizontalBar

```java
public NanoScrollPanel horizontalBar(boolean drawHorizontalBar)
```

Changes whether an overflowing enabled horizontal axis paints a scrollbar.
Does not disable horizontal scrolling or immediately refresh cached metrics.

- **`drawHorizontalBar`** — requested bar visibility policy

**Returns:** this panel

#### verticalBar

```java
public NanoScrollPanel verticalBar(boolean drawVerticalBar)
```

Changes whether an overflowing enabled vertical axis paints a scrollbar.
Does not disable vertical scrolling or immediately refresh cached metrics.

- **`drawVerticalBar`** — requested bar visibility policy

**Returns:** this panel

#### getScrollX

```java
public float getScrollX()
```

Reads the applied horizontal offset without recomputing scrollbar metrics.

**Returns:** current X offset in UI units

#### getScrollY

```java
public float getScrollY()
```

Reads the applied vertical offset without recomputing scrollbar metrics.

**Returns:** current Y offset in UI units

#### scrollX

```java
public NanoScrollPanel scrollX(float scrollX)
```

Clamps and immediately applies an X offset, or zero when horizontal scrolling
is disabled. Uses current content dimensions and cached opposite-bar thickness.

- **`scrollX`** — finite requested horizontal offset in UI units

**Returns:** this panel

#### scrollY

```java
public NanoScrollPanel scrollY(float scrollY)
```

Clamps and immediately applies a Y offset, or zero when vertical scrolling
is disabled. Uses current content dimensions and cached opposite-bar thickness.

- **`scrollY`** — finite requested vertical offset in UI units

**Returns:** this panel

#### scroll

```java
public NanoScrollPanel scroll(float scrollX, float scrollY)
```

Applies each requested axis through its clamping setter without animation.
Disabled axes resolve to zero.

- **`scrollX`** — finite requested horizontal offset
- **`scrollY`** — finite requested vertical offset

**Returns:** this panel

#### scrollBy

```java
public NanoScrollPanel scrollBy(float dx, float dy)
```

Adds displacements to target offsets on enabled axes, clamps them, and applies
the results immediately. Disabled axes are left unchanged.

- **`dx`** — finite horizontal displacement in UI units
- **`dy`** — finite vertical displacement in UI units

**Returns:** this panel

#### getMaxScrollX

```java
public float getMaxScrollX()
```

Computes nonnegative content-width overflow plus cached vertical-bar width.
Returns zero without content and does not refresh bar visibility itself.

**Returns:** maximum X offset under the current cached metrics

#### getMaxScrollY

```java
public float getMaxScrollY()
```

Computes nonnegative content-height overflow plus cached horizontal-bar height.
Returns zero without content and does not refresh bar visibility itself.

**Returns:** maximum Y offset under the current cached metrics

#### invalidateStyleTree

```java
    protected void invalidateStyleTree()
```

Invalidates inherited style state and immediately rebuilds this panel's local
paint cache from defaults and resolved values.

#### transformChildHitX

```java
    protected float transformChildHitX(float x)
```

Maps an unscrolled hit X into content coordinates by adding applied X offset.

- **`x`** — incoming UI world X

**Returns:** child-query X corrected for horizontal scrolling

#### transformChildHitY

```java
    protected float transformChildHitY(float y)
```

Maps an unscrolled hit Y into content coordinates by subtracting applied Y
offset in the root's world-coordinate convention.

- **`y`** — incoming UI world Y

**Returns:** child-query Y corrected for vertical scrolling

#### findNodeAt

```java
    public UINode findNodeAt(float x, float y, int requiredBit)
```

Rejects hidden, disabled, and out-of-panel points, then protects scrollbar
regions from child hit tests. Queries content only inside the reduced clip area
and falls back to this node when its capabilities match. Converts world Y to
top-left layout Y for scrollbar comparisons.

- **`x`** — hit-test world X
- **`y`** — hit-test world Y
- **`requiredBit`** — required capability bit, or negative for any

**Returns:** eligible descendant/panel, or null

#### onMouseMove

```java
    public void onMouseMove(MouseMoveEvent event)
```

Converts the pointer endpoint to layout coordinates and updates whole-bar hover
flags. Hover is based on the entire track, not only the thumb.

- **`event`** — routed pointer movement

#### onPointerCancel

```java
    public void onPointerCancel()
```

Runs inherited cancellation and clears both scrollbar dragging flags without
synthesizing a release or altering scroll offsets.

#### onMouseScroll

```java
    public void onMouseScroll(MouseScrollEvent event)
```

Delegates precise wheel routing and conditional consumption to ScrollBehavior,
then immediately applies the returned offsets. Enabled axes, cached ranges,
and configured speed determine whether scrolling can move.

- **`event`** — routed wheel event

#### onMousePress

```java
    public void onMousePress(MousePressEvent event)
```

Handles left presses on visible thumbs or tracks. Thumb presses begin dragging;
track presses first center the thumb on the pointer within travel bounds.
Sets pressed state for either drag and leaves event consumption to routing.

- **`event`** — routed pointer press in screen coordinates

#### onMouseDrag

```java
    public void onMouseDrag(MouseDragEvent event)
```

Moves any active visible scrollbar by mapping the pointer to the thumb center
and clamping travel. The initial grab offset is not retained; each drag centers
the thumb on the pointer. Does not independently filter mouse buttons.

- **`event`** — routed pointer drag

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Clears both scrollbar drag flags and pressed state for any delivered release.
Does not consume the event or alter offsets.

- **`event`** — routed pointer release

#### applyLayout

```java
    protected void applyLayout()
```

Refreshes local theme paint settings before normal container layout.
Post-layout offset clamping is performed separately by afterLayout.

#### draw

```java
    public void draw(long vg)
```

Refreshes styles/metrics, paints background, then clips and translates shared
child traversal to the content viewport. Translation and scissor are restored
in finally before drawing bars and border. Skips hidden nodes and zero handles;
requires root attachment when content is present.

- **`vg`** — borrowed active NanoVG context, or zero to skip

#### afterLayout

```java
    protected void afterLayout()
```

Runs inherited post-layout handling, clamps target offsets for enabled axes,
and synchronizes targets with applied positions. Uses current cached bar widths.

</details>

<a id="type-nanoscrollpanel-scrollmetrics"></a>

### NanoScrollPanel.ScrollMetrics — internal support type

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoScrollPanel.java#L876)

Reusable top-left layout-space geometry for visible scrollbar tracks and thumbs.
One instance belongs to the panel and is overwritten by each metric refresh;
all positions and dimensions use UI units. Hidden bars receive zero geometry.

<a id="type-nanoslider"></a>

### NanoSlider

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoSlider.java#L34)

Focusable NanoVG range control with mouse, wheel, and keyboard input. Values
are clamped and optionally snapped by RangeModel; horizontal values increase
rightward and vertical values increase upward. Input changes invoke a local
action or theme fallback, while programmatic value, percent, increment, and
decrement calls remain silent.

Construction sets a fixed 160-by-18 layout. Orientation changes supply
alternate dimensions only where layout is auto, so switching to vertical does
not automatically swap those fixed defaults. Track/thumb dimensions come from
resolved styles. The root owns capture routing, clipping, and NanoVG lifetime.

<details>
<summary>NanoSlider operation reference (59 declarations)</summary>

#### TRACK_COLOR_KEY

```java
public static final  StyleKey<Color> TRACK_COLOR_KEY
```

Theme track color; dimensions use UI units and colors are borrowed.

#### HOVER_TRACK_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_TRACK_COLOR_KEY
```

Theme hover track color; dimensions use UI units and colors are borrowed.

#### FOCUSED_TRACK_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_TRACK_COLOR_KEY
```

Theme focused track color; dimensions use UI units and colors are borrowed.

#### DISABLED_TRACK_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_TRACK_COLOR_KEY
```

Theme disabled track color; dimensions use UI units and colors are borrowed.

#### FILL_COLOR_KEY

```java
public static final  StyleKey<Color> FILL_COLOR_KEY
```

Theme fill color; dimensions use UI units and colors are borrowed.

#### HOVER_FILL_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_FILL_COLOR_KEY
```

Theme hover fill color; dimensions use UI units and colors are borrowed.

#### FOCUSED_FILL_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_FILL_COLOR_KEY
```

Theme focused fill color; dimensions use UI units and colors are borrowed.

#### DISABLED_FILL_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_FILL_COLOR_KEY
```

Theme disabled fill color; dimensions use UI units and colors are borrowed.

#### THUMB_COLOR_KEY

```java
public static final  StyleKey<Color> THUMB_COLOR_KEY
```

Theme thumb color; dimensions use UI units and colors are borrowed.

#### HOVER_THUMB_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_THUMB_COLOR_KEY
```

Theme hover thumb color; dimensions use UI units and colors are borrowed.

#### FOCUSED_THUMB_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_THUMB_COLOR_KEY
```

Theme focused thumb color; dimensions use UI units and colors are borrowed.

#### PRESSED_THUMB_COLOR_KEY

```java
public static final  StyleKey<Color> PRESSED_THUMB_COLOR_KEY
```

Theme pressed thumb color; dimensions use UI units and colors are borrowed.

#### DISABLED_THUMB_COLOR_KEY

```java
public static final  StyleKey<Color> DISABLED_THUMB_COLOR_KEY
```

Theme disabled thumb color; dimensions use UI units and colors are borrowed.

#### TRACK_HEIGHT_KEY

```java
public static final  StyleKey<Float> TRACK_HEIGHT_KEY
```

Theme track height; dimensions use UI units and colors are borrowed.

#### THUMB_SIZE_KEY

```java
public static final  StyleKey<Float> THUMB_SIZE_KEY
```

Theme thumb size; dimensions use UI units and colors are borrowed.

#### ACTION_KEY

```java
public static final  StyleKey<NodeAction<NanoSlider>> ACTION_KEY
```

Theme input-change callback used when the local action is null.

#### Constructor

```java
public NanoSlider()
```

Creates a horizontal [0, 1] slider at zero with no local action and default
fixed dimensions. Snapping is initially disabled.

#### Constructor

```java
public NanoSlider(float min, float max, float value)
```

Creates a horizontal slider with validated range and value, using theme action
fallback. A reversed range collapses at min and the value is clamped.

- **`min`** — finite minimum
- **`max`** — finite requested maximum
- **`value`** — finite initial value

**Throws `IllegalArgumentException`:** if an input or effective range width is non-finite

#### Constructor

```java
public NanoSlider(float min, float max, float value, NodeAction<NanoSlider> action)
```

Stores the local action, validates/clamps range state, enables pointer, keyboard,
and wheel capabilities, and assigns default dimensions. Initialization does
not invoke the action; a reversed range collapses at min.

- **`min`** — finite minimum
- **`max`** — finite requested maximum
- **`value`** — finite initial value
- **`action`** — optional local input-change callback

**Throws `IllegalArgumentException`:** if an input or effective range width is non-finite

#### action

```java
public NanoSlider action(NodeAction<NanoSlider> action)
```

Replaces the local input-change callback without invoking it. Null restores
resolved theme fallback rather than necessarily disabling notifications.

- **`action`** — synchronous callback, or null

**Returns:** this slider

#### trackColor

```java
public NanoSlider trackColor(Color color)
```

Retains a non-null track color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### hoverTrackColor

```java
public NanoSlider hoverTrackColor(Color color)
```

Retains a non-null hover track color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### focusedTrackColor

```java
public NanoSlider focusedTrackColor(Color color)
```

Retains a non-null focused track color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### disabledTrackColor

```java
public NanoSlider disabledTrackColor(Color color)
```

Retains a non-null disabled track color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### fillColor

```java
public NanoSlider fillColor(Color color)
```

Retains a non-null fill color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### hoverFillColor

```java
public NanoSlider hoverFillColor(Color color)
```

Retains a non-null hover fill color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### focusedFillColor

```java
public NanoSlider focusedFillColor(Color color)
```

Retains a non-null focused fill color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### disabledFillColor

```java
public NanoSlider disabledFillColor(Color color)
```

Retains a non-null disabled fill color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### thumbColor

```java
public NanoSlider thumbColor(Color color)
```

Retains a non-null thumb color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### hoverThumbColor

```java
public NanoSlider hoverThumbColor(Color color)
```

Retains a non-null hover thumb color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### focusedThumbColor

```java
public NanoSlider focusedThumbColor(Color color)
```

Retains a non-null focused thumb color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### pressedThumbColor

```java
public NanoSlider pressedThumbColor(Color color)
```

Retains a non-null pressed thumb color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### disabledThumbColor

```java
public NanoSlider disabledThumbColor(Color color)
```

Retains a non-null disabled thumb color without copying it. Null keeps the current
reference; resolved styles may replace the value during layout.

- **`color`** — mutable paint color, or null

**Returns:** this slider

#### getValue

```java
public float getValue()
```

Reads the model's already clamped and snapped numeric value.

**Returns:** current value within the effective endpoints

#### value

```java
public NanoSlider value(float value)
```

Sets a finite value through range clamping and optional minimum-anchored
snapping. Does not invoke the widget action, even when the value changes.

- **`value`** — requested numeric value

**Returns:** this slider

**Throws `IllegalArgumentException`:** if value is non-finite

#### getPercent

```java
public float getPercent()
```

Normalizes current value to [0, 1], returning zero for a collapsed range.

**Returns:** fractional progress, not a zero-to-one-hundred percentage

#### percent

```java
public NanoSlider percent(float percent)
```

Clamps a finite fraction to [0, 1], maps it to the range, and applies snapping.
The resulting fraction may differ from the request. No widget action is fired.

- **`percent`** — requested normalized fraction

**Returns:** this slider

**Throws `IllegalArgumentException`:** if percent is non-finite

#### stepSize

```java
public NanoSlider stepSize(float stepSize)
```

Sets the finite step and reapplies it to the current value without notification.
Negative values become zero, disabling snapping; positive values snap relative
to the minimum using ties-to-even rounding, with exact endpoints reachable.

- **`stepSize`** — requested increment in numeric range units

**Returns:** this slider

**Throws `IllegalArgumentException`:** if stepSize is non-finite

#### increment

```java
public NanoSlider increment()
```

Increases by one configured step, or one percent of the range with a minimum
increment of 0.000001 when stepping is disabled. Clamps/snaps without firing
the widget action.

**Returns:** this slider

#### decrement

```java
public NanoSlider decrement()
```

Decreases by one configured or fallback increment through model clamping and
snapping. This programmatic operation does not fire the widget action.

**Returns:** this slider

#### vertical

```java
public NanoSlider vertical(boolean vertical)
```

Changes orientation, supplies default dimensions only for axes still auto,
and marks layout dirty. Existing fixed dimensions and numeric value are retained.

- **`vertical`** — true for bottom-to-top increase

**Returns:** this slider

#### horizontal

```java
public NanoSlider horizontal(boolean horizontal)
```

Delegates to vertical with the inverse flag, retaining existing fixed dimensions.

- **`horizontal`** — true for left-to-right increase

**Returns:** this slider

#### getTrackX

```java
public float getTrackX()
```

Computes the absolute left edge, centering track thickness across a vertical node.

**Returns:** track X in UI world coordinates

#### getTrackY

```java
public float getTrackY()
```

Computes the absolute top edge, centering track thickness across a horizontal node.

**Returns:** track Y in UI world coordinates

#### getTrackWidth

```java
public float getTrackWidth()
```

Returns node width for horizontal orientation or configured thickness for vertical.

**Returns:** drawn track width in UI units

#### getTrackActualHeight

```java
public float getTrackActualHeight()
```

Returns node height for vertical orientation or configured thickness for horizontal.

**Returns:** drawn track height in UI units

#### getThumbCenterX

```java
public float getThumbCenterX()
```

Computes absolute thumb center X. Horizontal travel is clamped nonnegative
after subtracting thumb size; vertical orientation uses the node midpoint.

**Returns:** thumb center X in UI world coordinates

#### getThumbCenterY

```java
public float getThumbCenterY()
```

Computes absolute thumb center Y. Vertical travel reverses normalized value
and reserves half a thumb at each end; horizontal orientation uses node midpoint.

**Returns:** thumb center Y in UI world coordinates

#### onMousePress

```java
    public void onMousePress(MousePressEvent event)
```

Starts dragging and updates from a left press while not disabled. Other buttons
are ignored. This handler does not consume the press; root routing owns capture.

- **`event`** — routed pointer press

#### onMouseDrag

```java
    public void onMouseDrag(MouseDragEvent event)
```

Updates from the drag endpoint only for the left button while dragging is set.
Relies on root routing for current eligibility and does not consume the event.

- **`event`** — routed drag with screen-space endpoint

#### onMouseScroll

```java
    public void onMouseScroll(MouseScrollEvent event)
```

Uses precise vertical wheel offset, or horizontal when vertical is zero, as
an increment count. Consumes and notifies only if the value changes, allowing
unchanged boundary scrolls to remain available to ancestors. Enabled-state
filtering is supplied by normal routing rather than checked in this method.

- **`event`** — routed scroll event

#### onCreate

```java
    public void onCreate()
```

Allocates no native resources; the root supplies the NanoVG context.

#### onDestroy

```java
    public void onDestroy()
```

Performs no native cleanup because all rendering context resources are borrowed.

#### update

```java
    public void update(float delta)
```

Performs no timed work or child traversal; input handlers drive value changes.

- **`delta`** — elapsed update seconds, unused

#### draw

```java
    public void draw(TextureBatch batch)
```

Enters shared root dispatch for NanoVG painting and mixed-backend state handling.

- **`batch`** — active texture batch used by UI traversal

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Handles Home/End and orientation-appropriate arrows unless disabled. Recognized
keys are consumed even at an unchanged endpoint; action fires only on value
change. Other keys remain untouched.

- **`event`** — routed key press

#### applyLayout

```java
    protected void applyLayout()
```

Applies resolved state colors and nonnegative track/thumb sizes, then supplies
auto cross-axis dimensions and vertical default length before base layout.
The action remains dynamically resolved at notification time.

#### draw

```java
    public void draw(long vg)
```

Draws track, normalized fill, and circular thumb. Disabled colors win; pressed
state selects its own thumb while retaining focus/hover track and fill colors.
Assumes the root has handled visibility and provided a valid NanoVG frame.

- **`vg`** — borrowed active NanoVG context

#### onMouseRelease

```java
    public void onMouseRelease(valthorne.event.events.MouseReleaseEvent event)
```

Clears dragging for any delivered release without consuming it or changing value.
Capture lifecycle and cancellation are managed by the surrounding root/node policy.

- **`event`** — routed pointer release

</details>

<a id="type-nanotextfield"></a>

### NanoTextField

[Source](../../src/main/java/valthorne/ui/nodes/nano/NanoTextField.java#L45)

Single-line NanoVG editor backed by the shared TextEditModel for sanitized text,
grapheme-aware movement, selection, undo/redo, and validation. Renderer fields
are synchronized snapshots of that model. Enter invokes a local or theme action
only when validation succeeds; ordinary editing does not invoke the submit action.

Masking changes displayed characters and suppresses clipboard export through
shared shortcuts; raw text remains available from getText and getEditor. One
mask character is emitted per UTF-16 code unit, not per grapheme. Pointer presses
within the double-click interval select all, without a spatial proximity test.

Attach to a root before operations requiring caret measurement: substring
measurement and styled auto layout dereference the root. The root owns NanoVG
fonts and frame state. Colors are borrowed and may be replaced by theme layout.
Auto dimensions are replaced with measured numbers, so later text changes do
not automatically restore content sizing.

```java
NanoTextField field = new NanoTextField("Name");
field.getLayout().width(240).height(40);
parent.add(field); // Attach before caret/text operations requiring measurement.
field.text("Albert");
field.action(value -> System.out.println(value.getText()));
```

<details>
<summary>NanoTextField operation reference (64 declarations)</summary>

#### BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> BACKGROUND_COLOR_KEY
```

Theme background color used by text-field layout and painting.

#### HOVER_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY
```

Theme hover background color used by text-field layout and painting.

#### FOCUSED_BACKGROUND_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY
```

Theme focused background color used by text-field layout and painting.

#### BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> BORDER_COLOR_KEY
```

Theme border color used by text-field layout and painting.

#### HOVER_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> HOVER_BORDER_COLOR_KEY
```

Theme hover border color used by text-field layout and painting.

#### FOCUSED_BORDER_COLOR_KEY

```java
public static final  StyleKey<Color> FOCUSED_BORDER_COLOR_KEY
```

Theme focused border color used by text-field layout and painting.

#### TEXT_COLOR_KEY

```java
public static final  StyleKey<Color> TEXT_COLOR_KEY
```

Theme text color used by text-field layout and painting.

#### PLACEHOLDER_COLOR_KEY

```java
public static final  StyleKey<Color> PLACEHOLDER_COLOR_KEY
```

Theme placeholder color used by text-field layout and painting.

#### CARET_COLOR_KEY

```java
public static final  StyleKey<Color> CARET_COLOR_KEY
```

Theme caret color used by text-field layout and painting.

#### SELECTION_COLOR_KEY

```java
public static final  StyleKey<Color> SELECTION_COLOR_KEY
```

Theme selection color used by text-field layout and painting.

#### DEFAULT_SELECTION_COLOR_KEY

```java
public static final  StyleKey<Color> DEFAULT_SELECTION_COLOR_KEY
```

Theme default selection color used by text-field layout and painting.

#### FONT_NAME_KEY

```java
public static final  StyleKey<String> FONT_NAME_KEY
```

Theme font name used by text-field layout and painting.

#### FONT_SIZE_KEY

```java
public static final  StyleKey<Float> FONT_SIZE_KEY
```

Theme font size used by text-field layout and painting.

#### PADDING_KEY

```java
public static final  StyleKey<Float> PADDING_KEY
```

Theme padding used by text-field layout and painting.

#### CORNER_RADIUS_KEY

```java
public static final  StyleKey<Float> CORNER_RADIUS_KEY
```

Theme corner radius used by text-field layout and painting.

#### BORDER_WIDTH_KEY

```java
public static final  StyleKey<Float> BORDER_WIDTH_KEY
```

Theme border width used by text-field layout and painting.

#### CARET_WIDTH_KEY

```java
public static final  StyleKey<Float> CARET_WIDTH_KEY
```

Theme caret width used by text-field layout and painting.

#### CARET_PADDING_Y_KEY

```java
public static final  StyleKey<Float> CARET_PADDING_Y_KEY
```

Theme caret padding y used by text-field layout and painting.

#### SCISSOR_FUDGE_KEY

```java
public static final  StyleKey<Float> SCISSOR_FUDGE_KEY
```

Theme scissor fudge used by text-field layout and painting.

#### ACTION_KEY

```java
public static final  StyleKey<NodeAction<NanoTextField>> ACTION_KEY
```

Theme valid-Enter callback used when no local action is assigned.

#### Constructor

```java
public NanoTextField()
```

Creates an empty clickable, focusable field and subscribes its renderer to the
owned editor's synchronous change signal. Marks layout dirty for initial sizing.

#### Constructor

```java
public NanoTextField(String placeholder)
```

Creates an empty field with a sanitized placeholder. The placeholder is paint
metadata and does not become editable text.

- **`placeholder`** — hint text; null becomes empty

#### getText

```java
public String getText()
```

Returns the latest raw sanitized editor text, including when masking is enabled.

**Returns:** non-null text snapshot

#### text

```java
public NanoTextField text(String text)
```

Replaces text through the editor's programmatic assignment path: sanitizes,
clamps the caret to a valid boundary, collapses selection, clears history, and
notifies renderer synchronization. Does not enforce insertion-length limits
or invoke the submit action. Attach before nontrivial caret measurement.

- **`text`** — replacement text, possibly null

**Returns:** this field

#### getPlaceholder

```java
public String getPlaceholder()
```

Reads the sanitized hint displayed when actual display text is empty.

**Returns:** non-null placeholder

#### placeholder

```java
public NanoTextField placeholder(String placeholder)
```

Sanitizes hint text through the shared single-line sanitizer and dirties layout.
Does not alter editable contents, selection, or undo history.

- **`placeholder`** — replacement hint, possibly null

**Returns:** this field

#### getCaretIndex

```java
public int getCaretIndex()
```

Reads the active caret's UTF-16 offset from the synchronized editor snapshot.

**Returns:** caret offset in raw text, aligned to an editor boundary

#### caretIndex

```java
public NanoTextField caretIndex(int caretIndex)
```

Moves to a clamped editor boundary and collapses selection, synchronizing
blink and scrolling through the editor listener. The submit action is not fired.

- **`caretIndex`** — requested UTF-16 offset

**Returns:** this field

#### isMasking

```java
public boolean isMasking()
```

Reads whether display substitution and clipboard-export suppression are enabled.
Raw text remains accessible through the public editor/text APIs.

**Returns:** current masking flag

#### masking

```java
public NanoTextField masking(boolean masking)
```

Changes display substitution, invalidates its cache and layout, and updates
caret scrolling. Repeating the current flag is a no-op; raw text is unchanged.

- **`masking`** — true to display mask characters and suppress shortcut export

**Returns:** this field

#### getMaskChar

```java
public char getMaskChar()
```

Reads the single UTF-16 character repeated for masked display.

**Returns:** configured mask character

#### maskChar

```java
public NanoTextField maskChar(char maskChar)
```

Changes the unvalidated mask character and refreshes display/layout/scrolling.
Even control or surrogate characters are accepted; choose a renderable glyph.
Repeating the current value is a no-op.

- **`maskChar`** — character repeated once per UTF-16 unit

**Returns:** this field

#### getAction

```java
public NodeAction<NanoTextField> getAction()
```

Reads only the local Enter-submit callback, without resolving theme fallback.

**Returns:** local action, or null

#### action

```java
public NanoTextField action(NodeAction<NanoTextField> action)
```

Replaces the synchronous valid-Enter callback. Null allows theme fallback;
assignment and ordinary text edits do not invoke this action.

- **`action`** — local submit callback, or null

**Returns:** this field

#### fontName

```java
public NanoTextField fontName(String fontName)
```

Retains a nonblank registered font name, resets the reserved font-loaded flag,
and dirties layout even if the argument is null or blank. Does not load a font.

- **`fontName`** — NanoVG registration name

**Returns:** this field

#### fontSize

```java
public NanoTextField fontSize(float fontSize)
```

Sets font size with a minimum of 1.
Marks layout dirty without restoring numeric dimensions to auto.

- **`fontSize`** — finite requested value in UI units

**Returns:** this field

#### padding

```java
public NanoTextField padding(float padding)
```

Sets content inset on each side with a minimum of 0.
Marks layout dirty without restoring numeric dimensions to auto.

- **`padding`** — finite requested value in UI units

**Returns:** this field

#### cornerRadius

```java
public NanoTextField cornerRadius(float cornerRadius)
```

Sets rounded-box radius with a minimum of 0.
Changes painting without dirtying layout.

- **`cornerRadius`** — finite requested value in UI units

**Returns:** this field

#### borderWidth

```java
public NanoTextField borderWidth(float borderWidth)
```

Sets border stroke width with a minimum of 0.
Changes painting without dirtying layout.

- **`borderWidth`** — finite requested value in UI units

**Returns:** this field

#### backgroundColor

```java
public NanoTextField backgroundColor(Color backgroundColor)
```

Retains a non-null background color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`backgroundColor`** — mutable paint color, or null

**Returns:** this field

#### hoverBackgroundColor

```java
public NanoTextField hoverBackgroundColor(Color hoverBackgroundColor)
```

Retains a non-null hover background color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`hoverBackgroundColor`** — mutable paint color, or null

**Returns:** this field

#### focusedBackgroundColor

```java
public NanoTextField focusedBackgroundColor(Color focusedBackgroundColor)
```

Retains a non-null focused background color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`focusedBackgroundColor`** — mutable paint color, or null

**Returns:** this field

#### borderColor

```java
public NanoTextField borderColor(Color borderColor)
```

Retains a non-null border color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`borderColor`** — mutable paint color, or null

**Returns:** this field

#### hoverBorderColor

```java
public NanoTextField hoverBorderColor(Color hoverBorderColor)
```

Retains a non-null hover border color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`hoverBorderColor`** — mutable paint color, or null

**Returns:** this field

#### focusedBorderColor

```java
public NanoTextField focusedBorderColor(Color focusedBorderColor)
```

Retains a non-null focused border color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`focusedBorderColor`** — mutable paint color, or null

**Returns:** this field

#### textColor

```java
public NanoTextField textColor(Color textColor)
```

Retains a non-null text color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`textColor`** — mutable paint color, or null

**Returns:** this field

#### placeholderColor

```java
public NanoTextField placeholderColor(Color placeholderColor)
```

Retains a non-null placeholder color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`placeholderColor`** — mutable paint color, or null

**Returns:** this field

#### caretColor

```java
public NanoTextField caretColor(Color caretColor)
```

Retains a non-null caret color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`caretColor`** — mutable paint color, or null

**Returns:** this field

#### selectionColor

```java
public NanoTextField selectionColor(Color selectionColor)
```

Retains a non-null selection color without copying it. Null keeps the current
reference; resolved style application may overwrite it.

- **`selectionColor`** — mutable paint color, or null

**Returns:** this field

#### update

```java
    public void update(float delta)
```

Advances the double-click window and focused caret blink. Unfocused fields hide
the caret and reset blink time. At most one blink toggle occurs per update and
excess interval time is discarded; supply nonnegative finite elapsed time.

- **`delta`** — elapsed seconds

#### draw

```java
    public void draw(TextureBatch batch)
```

Enters root-managed mixed-renderer dispatch for the NanoVG painting callback.

- **`batch`** — active texture batch used by UI traversal

#### onMouseMove

```java
    public void onMouseMove(MouseMoveEvent event)
```

Delegates ordinary hover/movement behavior to the base node without editing text.

- **`event`** — routed pointer movement

#### onMousePress

```java
    public void onMousePress(MousePressEvent event)
```

Handles left presses as caret placement or Shift-extended selection. A second
press within the timing window selects all regardless of click distance.
Starts selection dragging for ordinary presses and resets blink/scrolling.
Enabled/focus routing is supplied by the root rather than checked here.

- **`event`** — routed pointer press in screen coordinates

#### onMouseDrag

```java
    public void onMouseDrag(MouseDragEvent event)
```

Extends selection to the pointer-derived editor boundary only while selecting.
Does not independently filter buttons; resets caret blink and keeps it visible
through horizontal scrolling.

- **`event`** — routed pointer drag

#### onMouseRelease

```java
    public void onMouseRelease(MouseReleaseEvent event)
```

Stops pointer selection for any delivered release without consuming the event
or changing selected text.

- **`event`** — routed pointer release

#### onKeyPress

```java
    public void onKeyPress(KeyPressEvent event)
```

Processes shared editing shortcuts only while focused and enabled, consuming
recognized commands even when no change occurs. Otherwise Enter checks current
validation, invokes local/theme submit action when valid, and is consumed even
when invalid or no action exists. Callback errors propagate synchronously.

- **`event`** — routed key press

#### applyLayout

```java
    protected void applyLayout()
```

Applies non-null theme paint/font settings and measures content/placeholder
when a style exists. Replaces only dimensions still auto, then delegates layout
and scrolls the caret into view. Styled measurement requires root attachment;
caret padding and scissor expansion are retained without nonnegative clamping.

#### draw

```java
    public void draw(long vg)
```

Paints state background/border and clipped text or placeholder, followed by
focused selection and blinking caret. Focus colors precede hover colors.
Masking affects measurements and display, not stored text. Saves/restores local
NanoVG scissor state on normal completion; the root owns enclosing frame state.

- **`vg`** — borrowed active NanoVG context, or zero to skip

#### setFocused

```java
    public void setFocused(boolean focused)
```

Applies base focus state, then resets blink and scrolling on an actual change.
Losing focus also stops selection dragging; update subsequently hides the caret.

- **`focused`** — requested focus state

#### onCreate

```java
    public void onCreate()
```

Allocates no native resources; the owned editor subscription is installed by
construction and fonts/context belong to the root/application.

#### onDestroy

```java
    public void onDestroy()
```

Performs no explicit disposal. The editor and its internal callback share this
field's lifetime; native NanoVG resources are borrowed.

#### getEditor

```java
public valthorne.ui.behavior.TextEditModel getEditor()
```

Exposes the live owned editing/validation/history model. Changes synchronously
update renderer snapshots, blink state, and scrolling through its listener.
Raw text remains accessible even for a masked field.

**Returns:** owned mutable editor

#### onTextInput

```java
    public void onTextInput(valthorne.event.events.TextInputEvent event)
```

Inserts routed text through the editor only while focused and enabled, then
consumes the event even if input constraints prevent a change. The editor owns
sanitization, selection replacement, length limits, and undo behavior.

- **`event`** — committed text-input event

#### onPointerCancel

```java
    public void onPointerCancel()
```

Runs base cancellation and clears selection dragging and pending double-click
recognition without changing the editor's existing selection.

</details>

## Related guides

- [UI roots, nodes, and input routing](ui-core.md)
- [UI layout and alignment](ui-layout.md)
- [Shared UI behavior and editing models](ui-behavior.md)
- [Themes, styles, and design tokens](ui-themes.md)
- [Existing ui rendering guide](../ui-rendering.md)
