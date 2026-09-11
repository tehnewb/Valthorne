# UI roots, nodes, and input routing

Author: Albert Beaupre

[System manual](README.md)

## Purpose

UIRoot coordinates a tree of UINodes: layout, drawing, focus, pointer capture, keyboard/text routing, overlays, and modal scopes. UIContainer owns child relationships. Start here before using individual controls so the tree receives a coherent lifecycle and coordinate system.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Tree lifecycle | Attachment, creation, update, drawing, removal, and disposal define when a node participates. |
| Focus and routing | Preview, target handling, and bubbling allow controls and ancestors to share an event. |
| Pointer capture | A drag can remain attached to a control after the cursor leaves its bounds. |
| Overlays and modals | Transient content draws above ordinary children; modal scopes restrict eligible input. |
| Mixed rendering | UIRenderContext coordinates texture and NanoVG phases. |

## Getting started

1. Create a UIRoot after graphics initialization and assign the intended viewport.
2. Attach containers and controls, then set layout inputs.
3. Update the root and draw it once in your UI phase.
4. Use focus/capture/modal APIs instead of manually routing every global event to every control.
5. Dispose the root and release any separately owned resources it borrowed.

## Ownership and lifecycle

The root owns native layout and rendering infrastructure according to its constructor/configuration. Adding a borrowed font or texture does not automatically transfer its lifetime. Input eligibility depends on attachment, visibility, enabled state, focus, and scopes.

## Important behavior

- A node's computed bounds update during layout; setting a layout input is not immediate geometry replacement.
- Convert through root/node helpers for local pointer coordinates.
- Cancel interrupted interactions instead of leaving pressed or dragging state active.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`NanoUtility`](#type-nanoutility)
- [`NodeAction`](#type-nodeaction)
- [`UIConstants`](#type-uiconstants)
- [`UIContainer`](#type-uicontainer)
- [`UIInputEvent`](#type-uiinputevent)
- [`UINode`](#type-uinode)
- [`UIRenderContext`](#type-uirendercontext)
- [`UIRenderContext.Scope`](#type-uirendercontext-scope)
- [`UIRoot`](#type-uiroot)

<a id="type-nanoutility"></a>

### NanoUtility

[Source](../../src/main/java/valthorne/ui/NanoUtility.java#L26)

Shared NanoVG color conversion and text measurement helpers. Four per-thread
color slots and per-thread metric arrays avoid repeated scratch allocation.
Color-slot results are temporary aliases: another call to the same numbered
slot on that thread overwrites the returned struct. They must not be freed
or retained as independent color values.

Nonzero NanoVG handles must be valid on the calling render thread. Metric
calls select a font face and size without restoring previous text state.
A zero handle uses documented estimates so layout can run before a context
is available. Thread-local scratch does not make a shared native context safe
for concurrent access.

<details>
<summary>NanoUtility operation reference (17 declarations)</summary>

#### color1

```java
public static NVGColor color1(Color color)
```

Copies the source into this thread's reusable color slot 1. The returned
struct is overwritten by the next color1 call on this thread; use distinct
slots when several colors must coexist during a native call.

- **`color`** — source color, or null for opaque white

**Returns:** borrowed slot 1 struct; do not free it

#### color2

```java
public static NVGColor color2(Color color)
```

Copies the source into this thread's reusable color slot 2. The returned
struct is overwritten by the next color2 call on this thread; use distinct
slots when several colors must coexist during a native call.

- **`color`** — source color, or null for opaque white

**Returns:** borrowed slot 2 struct; do not free it

#### color3

```java
public static NVGColor color3(Color color)
```

Copies the source into this thread's reusable color slot 3. The returned
struct is overwritten by the next color3 call on this thread; use distinct
slots when several colors must coexist during a native call.

- **`color`** — source color, or null for opaque white

**Returns:** borrowed slot 3 struct; do not free it

#### color4

```java
public static NVGColor color4(Color color)
```

Copies the source into this thread's reusable color slot 4. The returned
struct is overwritten by the next color4 call on this thread; use distinct
slots when several colors must coexist during a native call.

- **`color`** — source color, or null for opaque white

**Returns:** borrowed slot 4 struct; do not free it

#### color1

```java
public static NVGColor color1(int rgba)
```

Decodes packed ARGB into this thread's reusable color slot 1. The result
aliases any previous color1 result on the same thread.

- **`rgba`** — packed 0xAARRGGBB color

**Returns:** borrowed slot 1 struct; do not free it

#### color2

```java
public static NVGColor color2(int rgba)
```

Decodes packed ARGB into this thread's reusable color slot 2. The result
aliases any previous color2 result on the same thread.

- **`rgba`** — packed 0xAARRGGBB color

**Returns:** borrowed slot 2 struct; do not free it

#### color3

```java
public static NVGColor color3(int rgba)
```

Decodes packed ARGB into this thread's reusable color slot 3. The result
aliases any previous color3 result on the same thread.

- **`rgba`** — packed 0xAARRGGBB color

**Returns:** borrowed slot 3 struct; do not free it

#### color4

```java
public static NVGColor color4(int rgba)
```

Decodes packed ARGB into this thread's reusable color slot 4. The result
aliases any previous color4 result on the same thread.

- **`rgba`** — packed 0xAARRGGBB color

**Returns:** borrowed slot 4 struct; do not free it

#### toNano

```java
public static NVGColor toNano(Color color, NVGColor target)
```

Copies color components into caller-provided NanoVG storage without clamping.
Null input writes opaque white. The target remains caller-owned.

- **`color`** — source color, or null for opaque white
- **`target`** — non-null writable destination struct

**Returns:** the same target struct

**Throws `NullPointerException`:** if target is null

#### toNano

```java
public static NVGColor toNano(int rgba, NVGColor target)
```

Decodes packed ARGB consistently with Color(int), despite the parameter's
rgba name. Each unsigned byte is divided by 255 and written to the target.

- **`rgba`** — packed 0xAARRGGBB value
- **`target`** — non-null writable destination struct

**Returns:** the same target struct

**Throws `NullPointerException`:** if target is null

#### measureTextWidth

```java
public static float measureTextWidth(long vg, String fontName, float fontSize, String text)
```

Measures glyph bounds width at origin using the selected face and size.
Null or empty text returns zero before touching context state. A zero handle
estimates half font size per UTF-16 unit. Does not normalize tabs or newlines.

- **`vg`** — valid NanoVG context, or zero for estimation
- **`fontName`** — registered font face for native measurement
- **`fontSize`** — requested text size in UI units
- **`text`** — text to measure, possibly null

**Returns:** bounds width, or estimated width without a context

#### measureTextWidth

```java
public static float measureTextWidth(long vg, String fontName, float fontSize, String text, int start, int end)
```

Measures a clamped UTF-16 substring range. Start is clamped to string bounds;
end is clamped no earlier than start, so reversed ranges are empty. Native
measurement creates a substring and changes font state; a zero handle uses
half font size per selected code unit.

- **`vg`** — valid NanoVG context, or zero for estimation
- **`fontName`** — registered font face
- **`fontSize`** — requested text size in UI units
- **`text`** — source string, possibly null
- **`start`** — requested inclusive UTF-16 start index
- **`end`** — requested exclusive UTF-16 end index

**Returns:** width of the selected range, or zero for null/empty text or range

#### measureTextHeight

```java
public static float measureTextHeight(long vg, String fontName, float fontSize)
```

Reads the selected font's line-height metric, including its line spacing,
rather than measuring a specific glyph string. Changes font face/size state.

- **`vg`** — valid NanoVG context, or zero to return fontSize
- **`fontName`** — registered font face
- **`fontSize`** — requested text size in UI units

**Returns:** font line height, or fontSize without a context

#### getLineHeight

```java
public static float getLineHeight(long vg, String fontName, float fontSize)
```

Delegates to measureTextHeight with identical font-state and fallback behavior.

- **`vg`** — valid NanoVG context, or zero for estimation
- **`fontName`** — registered font face
- **`fontSize`** — requested text size in UI units

**Returns:** line-height metric, or fontSize without a context

#### getAscender

```java
public static float getAscender(long vg, String fontName, float fontSize)
```

Reads the ascender metric above the text baseline after selecting the font.

- **`vg`** — valid NanoVG context, or zero to return fontSize
- **`fontName`** — registered font face
- **`fontSize`** — requested text size in UI units

**Returns:** ascender metric, or fontSize without a context

#### getDescender

```java
public static float getDescender(long vg, String fontName, float fontSize)
```

Reads the signed descender metric after selecting the font; NanoVG normally
reports a negative distance for glyphs below the baseline.

- **`vg`** — valid NanoVG context, or zero to return zero
- **`fontName`** — registered font face
- **`fontSize`** — requested text size in UI units

**Returns:** signed descender metric, or zero without a context

#### getTextCenterY

```java
public static float getTextCenterY(long vg, String fontName, float fontSize, float y, float height)
```

Computes a baseline that centers ascender-minus-descender height inside a
vertical interval. It is for baseline-aligned text, not top-aligned text, and
changes native font state through metric calls.

- **`vg`** — valid NanoVG context, or zero for approximate metrics
- **`fontName`** — registered font face
- **`fontSize`** — requested text size in UI units
- **`y`** — top of the containing interval
- **`height`** — containing interval height

**Returns:** baseline Y for vertically centered text

</details>

<a id="type-nodeaction"></a>

### NodeAction

[Source](../../src/main/java/valthorne/ui/NodeAction.java#L11)

Represents an action that can be performed on a given node of type `N`.
Implementations receive the node itself and may update its state. This contract
supplies no scheduling, exception handling, or automatic event consumption.

- **`<N>`** — the type of node that this action can be performed on, which extends `UINode`

<details>
<summary>NodeAction operation reference (1 declarations)</summary>

#### perform

```java
void perform(N node)
```

Performs an action on the specified node.
The caller chooses when and on which thread to invoke the action; implementations
that mutate UI state should follow the owning root's threading requirements.

- **`node`** — the node on which the action is performed must be of a type that extends `UINode`

</details>

<a id="type-uiconstants"></a>

### UIConstants

[Source](../../src/main/java/valthorne/ui/UIConstants.java#L16)

##### UIConstants

`UIConstants` is a utility class responsible for translating the engine's
`Layout` and `LayoutValue` system into Yoga layout instructions.

<details>
<summary>UIConstants operation reference (2 declarations)</summary>

#### NULL

```java
public static final  long NULL
```

Constant used to represent a null Yoga node memory address.

Yoga nodes are stored as native memory pointers represented by `long`.
A value of `0` indicates that a node has not yet been created or has
already been destroyed.

#### applyLayout

```java
public static void applyLayout(long node, Layout layout)
```

Applies all layout rules from a `Layout` object onto a Yoga node.

This method is the main entry point used during layout synchronization.
It maps layout values from the engine abstraction into Yoga style calls.

The following layout categories are applied:

- Size rules (width, height, min/max)

- Position rules

- Margin rules

- Padding rules

- Flexbox rules

- **`node`** — the Yoga node memory address
- **`layout`** — the layout configuration to apply

</details>

<a id="type-uicontainer"></a>

### UIContainer

[Source](../../src/main/java/valthorne/ui/UIContainer.java#L78)

##### UIContainer

`UIContainer` is a specialized `UINode` that can contain and manage
other `UINode` instances as children. It forms the backbone of the UI
hierarchy system used throughout the Valthorne UI framework.

A container maintains an ordered list of children and is responsible for:

- managing parent/child relationships

- propagating the UI root to descendants

- propagating style invalidation through the UI tree

- updating and rendering child nodes

- handling Yoga node attachment and detachment

- performing hit testing for input handling

Containers can be nested, forming a full UI tree structure where each node
can optionally hold children. Layout and rendering operations traverse this
tree recursively.

##### Yoga Layout Integration

Each container participates in the Yoga layout tree. When children are added
or removed, the corresponding Yoga nodes are inserted or removed to ensure
that layout computation remains synchronized with the UI hierarchy.

Layout updates propagate through the tree and are eventually resolved by
`UIRoot#layout()`.

##### Example

```java
UIContainer panel = new Panel();

Label label = new Label();
Button button = new Button();

panel.add(label);
panel.add(button);

root.add(panel);

// Update loop
panel.update(delta);

// Rendering
panel.draw(batch);
```

Containers themselves usually do not draw anything. Instead they delegate
drawing to their children.

<details>
<summary>UIContainer operation reference (18 declarations)</summary>

#### findNodeAt

```java
public UINode findNodeAt(float x, float y, int requiredBit)
```

Performs hit testing within the container hierarchy.

This method recursively searches the container tree from top-most child
to bottom-most child to determine which node is located at the provided
coordinates.

The search respects visibility and optional bit requirements. If a
`requiredBit` is specified, the node must have that bit enabled.

Children are checked in reverse order to ensure nodes drawn later
(higher z-order) receive input priority.

- **`x`** — the x coordinate in world space
- **`y`** — the y coordinate in world space
- **`requiredBit`** — an optional bit requirement or -1 to ignore

**Returns:** the node found at the location or null if none matches

#### transformChildHitX

```java
protected float transformChildHitX(float x)
```

Transforms the input x coordinate before hit testing children.

Containers can override this method to apply coordinate transformations
such as scroll offsets or clipping translations before hit detection.

- **`x`** — the original x coordinate

**Returns:** the transformed x coordinate

#### transformChildHitY

```java
protected float transformChildHitY(float y)
```

Transforms the input y coordinate before hit testing children.

Containers can override this method to apply coordinate transformations
such as scroll offsets or clipping translations before hit detection.

- **`y`** — the original y coordinate

**Returns:** the transformed y coordinate

#### invalidateStyleTree

```java
    protected void invalidateStyleTree()
```

Invalidates style state for this container and all descendants.

When a theme or style property changes, the style cache must be cleared
so that nodes can recompute their resolved styles.

#### onCreate

```java
    public void onCreate()
```

Provides an empty container creation hook. Tree/Yoga attachment is handled
by the node lifecycle; subclasses may initialize their own resources here.

#### onDestroy

```java
    public void onDestroy()
```

Provides an empty container destruction hook. Child native-node detachment
is handled separately by onNodeWillDestroy; subclasses release owned resources here.

#### update

```java
    public void update(float delta)
```

Updates children in their stored order, including hidden children. Layout
synchronization is coordinated by the root around tree updates; this method
only delegates update calls.

- **`delta`** — elapsed frame time in seconds

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws all visible children in order.

- **`batch`** — the texture batch used for rendering

#### add

```java
public void add(UINode child)
```

Appends an unattached child, propagates this root through its subtree, attaches
Yoga nodes when this container is live, and invalidates style/layout. Rejects
cycles and already-parented nodes; remove a node from its old parent first.

- **`child`** — nonnull child to attach

**Throws `NullPointerException`:** if child is null

**Throws `IllegalArgumentException`:** if attachment would create a cycle

**Throws `IllegalStateException`:** if child already has a parent

#### add

```java
public void add(UINode... children)
```

Adds children sequentially using single-child attachment. Earlier additions
remain if a later child fails validation; this operation is not transactional.

- **`children`** — nonnull array of nonnull unattached children

**Throws `NullPointerException`:** if the array or an entry is null

**Throws `IllegalArgumentException`:** if an entry would create a cycle

**Throws `IllegalStateException`:** if an entry already has a parent

#### remove

```java
public void remove(UINode child)
```

Detaches an immediate child and its native Yoga subtree, clears propagated
root/parent references, and compacts child order. Notifies the root first so
capture, focus, hover, and tooltips in the subtree can be cleared. Null or absent
children have no effect; descendant relationships within the detached subtree remain.

- **`child`** — immediate child to detach

#### remove

```java
public void remove(UINode... children)
```

Removes one or more child nodes from this container.

This method iterates through the specified child nodes, ignoring any that
are null, and removes them from the container. If a child is already not
present in the container, it will have no effect.

- **`children`** — the array of child nodes to be removed

#### clear

```java
public void clear()
```

Removes all children from this container.

#### size

```java
public final int size()
```

Returns the number of child nodes.

**Returns:** the number of children

#### get

```java
public final UINode get(int index)
```

Returns the live child at an insertion-order index.

- **`index`** — index from zero through size minus one

**Returns:** child reference

**Throws `IndexOutOfBoundsException`:** if index is outside the current child range

#### getChildren

```java
public final List<UINode> getChildren()
```

Returns a cached, read-only live view of the children. UI-thread-only;
structural edits during iteration are not supported. Use List.copyOf for a snapshot.

**Returns:** the children list

#### onNodeCreated

```java
    protected void onNodeCreated(long yogaNode)
```

After base node initialization, attaches each existing child to the root's Yoga
configuration and inserts its native node in child order.

- **`yogaNode`** — newly created native container node

#### onNodeWillDestroy

```java
    protected void onNodeWillDestroy(long yogaNode)
```

Detaches every child subtree before invoking base native-node destruction.
Java child membership is retained for a future attachment.

- **`yogaNode`** — native container node about to be destroyed

</details>

<a id="type-uicontainer-childview"></a>

### UIContainer.ChildView — internal support type

[Source](../../src/main/java/valthorne/ui/UIContainer.java#L482)

Read-only list adapter over the enclosing container's current child storage.
Membership is live rather than copied, and random indexed access delegates to
the container. UI-thread use is required; structural edits during iteration
are unsupported.

<details>
<summary>UIContainer.ChildView operation reference (2 declarations)</summary>

#### get

```java
        public UINode get(int index)
```

Returns the enclosing container's current child at the requested index.

- **`index`** — current child index

**Returns:** live child reference

**Throws `IndexOutOfBoundsException`:** if index is outside the current size

#### size

```java
        public int size()
```

Returns the enclosing container's current child count.

**Returns:** live list size

</details>

<a id="type-uiinputevent"></a>

### UIInputEvent

[Source](../../src/main/java/valthorne/ui/UIInputEvent.java#L29)

Describes one receiver's preview or bubble callback during root input routing.
The target remains the original destination, while the current target identifies
the node whose callback is executing. Every wrapper in the route shares the
same underlying event and therefore the same consumption state.

Stored coordinates are screen coordinates passed by the root, not local
coordinates. `localPosition()` converts them into top-left-local layout
coordinates for the current receiver on demand. Non-pointer routes can supply
NaN coordinates and therefore have no meaningful pointer position.

Do not retain this wrapper after the callback. Its references are fixed,
but the event's consumption state and node layout are mutable, and underlying
events may be reused for later publications. Construction performs no null
checks, coordinate validation, or copying of the referenced objects.

- **`event`** — the shared underlying event for this dispatch
- **`target`** — the original destination of the routed input
- **`currentTarget`** — the receiver currently processing preview or bubble input
- **`screenX`** — screen X coordinate, or NaN for a route without pointer coordinates
- **`screenY`** — screen Y coordinate, or NaN for a route without pointer coordinates

<details>
<summary>UIInputEvent operation reference (3 declarations)</summary>

#### localPosition

```java
public Vector2f localPosition()
```

Converts the stored screen point using the current receiver's layout and
viewport mapping. The result is top-left-local to that receiver and is
computed at call time rather than captured when the wrapper is constructed.

**Returns:** a newly allocated local-position vector; pointerless routes may yield NaN

**Throws `NullPointerException`:** if currentTarget is null

#### consume

```java
public void consume()
```

Consumes the underlying event so normal routing stops delivery to later
receivers. Repeated calls are harmless and do not cancel work already
performed by callbacks that have run.

**Throws `NullPointerException`:** if event is null

#### isConsumed

```java
public boolean isConsumed()
```

Reads the shared event's current consumption state, including consumption
performed by another receiver or directly through the underlying event.

**Returns:** whether further normal event propagation should stop

**Throws `NullPointerException`:** if event is null

</details>

<a id="type-uinode"></a>

### UINode

[Source](../../src/main/java/valthorne/ui/UINode.java#L116)

##### UINode

`UINode` is the abstract foundation for every UI object in the Valthorne UI system.
It is responsible for holding layout data, storing interaction state, resolving theme styles,
managing Yoga layout nodes, and providing the event hooks needed for interactive controls.
A node may represent a simple visual element, a container, or a specialized interactive
control such as a button, checkbox, text field, slider, tooltip host, or any future custom
component you create.

The class is designed around a few core responsibilities:

- **Layout** through a `Layout` object and a native Yoga node.

- **State management** through a compact `ShortBits` bitset.

- **Theme/style resolution** through `ThemeData`, `StyleMap`, and `ResolvedStyle`.

- **Bounds tracking** through a cached `Rectangle` updated from Yoga layout results.

- **Input hooks** through overridable mouse, keyboard, and window-resize methods.

Nodes keep their state extremely compact by storing booleans such as visible, enabled,
hovered, focused, pressed, checked, and dragging as bits inside a `ShortBits` instance.
This lets the node expose a large set of UI states without requiring a separate field for each
one. These state flags are also used when resolving the current style state for theming.

Layout is handled through Yoga. Each node may attach a native Yoga node when it becomes part
of a UI tree. Once Yoga performs layout calculations, this class reads the computed position
and size values and caches them into its `bounds` rectangle. Rendering code can then
use these cached values directly.

Styling is lazy and cached. The first time `getStyle()` is requested, the current theme,
style name, style overrides, and state bits are combined to produce a `ResolvedStyle`.
Whenever something changes that may affect appearance, such as hover state or style overrides,
the cached style is invalidated so it can be rebuilt later.

This class does not render anything by itself. Instead, subclasses implement
`draw(TextureBatch)` and define their own visuals. Likewise, subclasses decide how to
react to input by overriding whichever event methods they care about.

##### Usage flow

- Create a subclass of `UINode`.

- Override lifecycle methods such as `onCreate()`, `onDestroy()`, `update(float)`, and `draw(TextureBatch)`.

- Optionally override input handlers such as `onMousePress(MousePressEvent)` or `onKeyPress(KeyPressEvent)`.

- Configure layout through `getLayout()`.

- Read render-space values through `getRenderX()`, `getRenderY()`, `getWidth()`, and `getHeight()`.

##### Example

```java
public class MyNode extends UINode {

    @Override
    public void onCreate() {
        getLayout().width(Value.pixels(120));
        getLayout().height(Value.pixels(40));
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public void draw(TextureBatch batch) {
        ResolvedStyle style = getStyle();
        if (style != null) {
            Drawable background = style.get(Panel.BACKGROUND_KEY);
            if (background != null) {
                background.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());
            }
        }
    }
}
```

In the example above, the custom node uses Yoga-driven layout, resolves theme styling through
`getStyle()`, and responds to mouse input by changing its pressed state, which can then
affect the resolved style.

<details>
<summary>UINode operation reference (105 declarations)</summary>

#### VISIBLE_BIT

```java
public static final  int VISIBLE_BIT
```

Bit index used to mark whether the node is visible.

#### ENABLED_BIT

```java
public static final  int ENABLED_BIT
```

Bit index used to mark whether the node is enabled.

#### LAYOUT_DIRTY_BIT

```java
public static final  int LAYOUT_DIRTY_BIT
```

Bit index used to mark whether the node's layout is dirty.

#### HOVERED_BIT

```java
public static final  int HOVERED_BIT
```

Bit index used to mark whether the node is currently hovered.

#### CLICKABLE_BIT

```java
public static final  int CLICKABLE_BIT
```

Bit index used to mark whether the node can be clicked.

#### FOCUSABLE_BIT

```java
public static final  int FOCUSABLE_BIT
```

Bit index used to mark whether the node can receive focus.

#### SCROLLABLE_BIT

```java
public static final  int SCROLLABLE_BIT
```

Bit index used to mark whether the node can respond to scroll input.

#### PRESSED_BIT

```java
public static final  int PRESSED_BIT
```

Bit index used to mark whether the node is currently pressed.

#### FOCUSED_BIT

```java
public static final  int FOCUSED_BIT
```

Bit index used to mark whether the node is currently focused.

#### SELECTED_BIT

```java
public static final  int SELECTED_BIT
```

Bit index used to mark whether the node is selected.

#### CHECKED_BIT

```java
public static final  int CHECKED_BIT
```

Bit index used to mark whether the node is checked.

#### DRAGGING_BIT

```java
public static final  int DRAGGING_BIT
```

Bit index used to mark whether the node is currently being dragged.

#### Constructor

```java
protected UINode()
```

Creates a new UI node with its default state initialized.

By default, a newly created node starts as visible, enabled, layout-dirty,
and clickable. These defaults make the node ready to participate in layout
and interaction immediately after construction.

#### onKeyPress

```java
public void onKeyPress(KeyPressEvent event)
```

Called when a key is pressed while this node is the active receiver of keyboard input.

The base implementation does nothing. Subclasses override this method when they need
keyboard behavior such as confirming actions, moving focus internally, typing text,
toggling values, or reacting to shortcuts.

- **`event`** — the key press event containing the pressed key and modifier state

#### onInputPreview

```java
public void onInputPreview(UIInputEvent event)
```

Root-to-target filter, before the control's normal input handler.

#### onInputBubble

```java
public void onInputBubble(UIInputEvent event)
```

Target-to-root notification. Consume to stop parent propagation.

#### onPointerCancel

```java
public void onPointerCancel()
```

Ends interaction without activating the control.

#### screenToLocal

```java
public final Vector2f screenToLocal(float x, float y)
```

Screen pixels (bottom-left) to this node's local top-left coordinates.

#### onKeyRelease

```java
public void onKeyRelease(KeyReleaseEvent event)
```

Called when a key is released while this node is the active receiver of keyboard input.

The base implementation does nothing. Subclasses may override this method when they need
release-specific behavior, such as ending a held state or confirming a key interaction only
when the key is released.

- **`event`** — the key release event containing the released key and modifier state

#### onTextInput

```java
public void onTextInput(valthorne.event.events.TextInputEvent event)
```

Receives routed Unicode text input for controls that accept text. The base
implementation performs no editing and does not consume the event; subclasses
may update their editing model and consume accepted input.

- **`event`** — routed text-input event

#### onMousePress

```java
public void onMousePress(MousePressEvent event)
```

Called when a mouse button is pressed on this node.

The base implementation does nothing. Subclasses typically override this method to begin
press state handling, activate dragging, capture input, or trigger visual state changes.

- **`event`** — the mouse press event containing button and pointer position data

#### onMouseRelease

```java
public void onMouseRelease(MouseReleaseEvent event)
```

Called when a mouse button is released on this node.

The base implementation does nothing. Subclasses usually override this method to end press
handling, commit clicks, stop dragging, or restore state after interaction.

- **`event`** — the mouse release event containing button and pointer position data

#### onMouseDrag

```java
public void onMouseDrag(MouseDragEvent event)
```

Called while the mouse is dragged across this node.

The base implementation does nothing. Subclasses override this method for behaviors such as
slider dragging, selection box updates, custom repositioning, or scroll thumb movement.

- **`event`** — the mouse drag event containing drag coordinates and button information

#### onMouseMove

```java
public void onMouseMove(MouseMoveEvent event)
```

Called when the mouse moves over or across this node.

The base implementation does nothing. Subclasses may override this method for hover effects,
tooltips, live previews, pointer tracking, or other movement-based interaction logic.

- **`event`** — the mouse move event containing pointer movement information

#### onMouseScroll

```java
public void onMouseScroll(MouseScrollEvent event)
```

Called when the mouse wheel or scroll input is used while this node is the target.

The base implementation does nothing. Subclasses override this method for things such as
scrolling content, zooming, stepping sliders, or changing selected values.

- **`event`** — the mouse scroll event containing scroll offsets

#### onWindowResize

```java
public void onWindowResize(WindowResizeEvent event)
```

Called when the window is resized.

The base implementation does nothing. Subclasses may override this method to respond to
changes in available space, update cached render data, or refresh state that depends on
window dimensions.

- **`event`** — the resize event describing the new window size

#### getLayout

```java
public final Layout getLayout()
```

Returns the layout object owned by this node.

The returned `Layout` stores the node's desired Yoga layout configuration, including
dimensions, margins, padding, alignment, flex behavior, and related settings. Changes to this
layout normally require the node tree to be relaid out before updated values appear in the
node's computed bounds.

**Returns:** the node's layout object

#### getBits

```java
public final ShortBits getBits()
```

Returns the internal state bitset used by this node.

This can be used for direct low-level inspection or advanced integrations where you need
direct access to the packed state bits rather than using the convenience state methods.

**Returns:** the internal `ShortBits` state storage

#### getBit

```java
public final boolean getBit(int index)
```

Returns the boolean value of a specific bit index in the node state bitset.

This is a convenience method for quickly checking one of the node's packed flags.

- **`index`** — the bit index to inspect

**Returns:** true if the bit is set, otherwise false

#### setBit

```java
public final void setBit(int index, boolean value)
```

Sets the value of a specific bit in the node state bitset.

If the bit value actually changes, the node invalidates its cached style because the new
state may affect visual appearance. If the changed bit is related to visibility or layout
dirtiness, the node also marks layout as dirty.

- **`index`** — the bit index to modify
- **`value`** — the value to store in that bit

#### isVisible

```java
public final boolean isVisible()
```

Returns whether this node is visible.

Visibility is stored as a bit flag. Invisible nodes may still exist in the UI tree,
but whether they participate in rendering or hit testing depends on the surrounding UI logic.

**Returns:** true if the node is visible

#### setVisible

```java
public final void setVisible(boolean visible)
```

Sets whether this node is visible.

Changing visibility invalidates the cached style and marks layout dirty so containers and
layout calculations can react to the new state.

- **`visible`** — true to make the node visible, false to hide it

#### isDisabled

```java
public boolean isDisabled()
```

Returns whether this node is disabled.

Internally, disabled is represented by the inverse of the enabled bit.

**Returns:** true if the node is disabled

#### disable

```java
public void disable()
```

Disables the functionality associated with this method.
This method sets a specific bit to false to indicate
the disabled state and triggers a style update.

#### enable

```java
public void enable()
```

Enables the component by setting the appropriate bit flag to true
and invalidating the style for a visual update.

The method modifies an internal bit field to reflect the enabled
status of the component and ensures the style is refreshed to stay
in sync with the state change.

#### isEnabled

```java
public boolean isEnabled()
```

Returns whether this node is enabled.

**Returns:** true if the node is enabled

#### setEnabled

```java
public void setEnabled(boolean enabled)
```

Sets whether this node is enabled.

Changing enabled state invalidates the current style because enabled and disabled states often
resolve to different visuals.

- **`enabled`** — true to enable the node, false to disable it

#### isLayoutDirty

```java
public boolean isLayoutDirty()
```

Returns whether this node is marked as layout dirty.

A layout-dirty node needs its Yoga layout reapplied or recalculated before its bounds can be
considered current.

**Returns:** true if the layout is dirty

#### isHovered

```java
public boolean isHovered()
```

Returns whether this node is currently hovered.

**Returns:** true if the node is hovered

#### setHovered

```java
public void setHovered(boolean hovered)
```

Sets whether this node is currently hovered.

Changing hover state invalidates the cached style so hover-specific theme values can be resolved.

- **`hovered`** — true if the node should be considered hovered

#### isClickable

```java
public boolean isClickable()
```

Returns whether this node is clickable.

**Returns:** true if the node can receive click interactions

#### setClickable

```java
public void setClickable(boolean clickable)
```

Sets whether this node can receive click interactions.

Changing clickability invalidates the current style because clickable and non-clickable states
may be styled differently by the theme.

- **`clickable`** — true to make the node clickable

#### isFocusable

```java
public boolean isFocusable()
```

Returns whether this node can receive focus.

**Returns:** true if the node is focusable

#### setFocusable

```java
public void setFocusable(boolean focusable)
```

Sets whether this node can receive focus.

Changing focusability invalidates the current style because focusable controls are often styled
differently from non-focusable ones.

- **`focusable`** — true to allow this node to receive focus

#### isScrollable

```java
public boolean isScrollable()
```

Returns whether this node is scrollable.

**Returns:** true if this node responds to scroll interaction

#### setScrollable

```java
public void setScrollable(boolean scrollable)
```

Sets whether this node is scrollable.

Changing this flag invalidates style because scrollable and non-scrollable elements may resolve
differently in some themes.

- **`scrollable`** — true to make the node scrollable

#### isPressed

```java
public boolean isPressed()
```

Returns whether this node is currently pressed.

**Returns:** true if the node is pressed

#### setPressed

```java
public void setPressed(boolean pressed)
```

Sets whether this node is currently pressed.

Press state is commonly used by interactive controls such as buttons, sliders, and checkboxes.
When the state changes, cached styling is invalidated.

- **`pressed`** — true to mark the node as pressed

#### isFocused

```java
public boolean isFocused()
```

Returns whether this node is currently focused.

**Returns:** true if the node is focused

#### setFocused

```java
public void setFocused(boolean focused)
```

Sets whether this node is currently focused.

Focus state affects keyboard routing and usually visual appearance, so style is invalidated
when this value changes.

- **`focused`** — true to mark the node as focused

#### isSelected

```java
public boolean isSelected()
```

Returns whether this node is selected.

**Returns:** true if the node is selected

#### setSelected

```java
public void setSelected(boolean selected)
```

Sets whether this node is selected.

Selection is useful for list items, tabs, menus, and similar components.
Style is invalidated when the state changes.

- **`selected`** — true to mark the node as selected

#### isChecked

```java
public boolean isChecked()
```

Returns whether this node is checked.

**Returns:** true if the node is checked

#### setChecked

```java
public void setChecked(boolean checked)
```

Sets whether this node is checked.

Checked state is typically used by controls such as checkboxes, toggles, or custom on/off
controls. Cached style is invalidated when the state changes.

- **`checked`** — true to mark the node as checked

#### isDragging

```java
public boolean isDragging()
```

Returns whether this node is currently being dragged.

**Returns:** true if the node is dragging

#### setDragging

```java
public void setDragging(boolean dragging)
```

Sets whether this node is currently being dragged.

Dragging state can affect both behavior and style, so cached styling is invalidated when it changes.

- **`dragging`** — true to mark the node as dragging

#### getStyle

```java
protected final ResolvedStyle getStyle()
```

Resolves and returns the current style for this node.

The result is cached after the first resolution. The cache is invalidated whenever state,
theme, style name, or overrides change. Local overrides also work without a theme.

**Returns:** the resolved style, or null when neither a theme nor local overrides exist

#### getStyleState

```java
protected StyleState getStyleState()
```

Builds and returns the style state describing the current interactive state of this node.

This method maps the node's state bits into a `StyleState` object which is then used
during theme resolution. Subclasses may override this if they need to inject custom style-state
behavior on top of the default flags.

**Returns:** a style state representing the current node state

#### setStyle

```java
public final <T> void setStyle(StyleKey<T> key, T value)
```

Applies a style override for this node.

Overrides are stored locally in a `StyleMap` and layered on top of the resolved theme
values. Setting an override invalidates the cached style so the new value can be used.

- **`key`** — the style key to override
- **`value`** — the value to store for that key
- **`<T>`** — the value type associated with the style key

#### clearStyle

```java
public final void clearStyle(StyleKey<?> key)
```

Removes a previously applied local style override.

If no override map exists, the method does nothing.

- **`key`** — the style key whose override should be removed

#### clearStyles

```java
public final void clearStyles()
```

Removes all local style overrides from this node.

If no overrides exist, the method does nothing. Clearing overrides invalidates the cached style
so theme values can be re-resolved without local replacements.

#### getTheme

```java
public final ThemeData getTheme()
```

Returns the effective theme used by this node.

If a local theme has been assigned directly to this node, that theme is returned.
Otherwise, the method asks the parent container for its effective theme, allowing theme
inheritance through the UI tree.

**Returns:** the effective theme, or null if no theme is available

#### setTheme

```java
public final void setTheme(ThemeData theme)
```

Assigns a local theme to this node.

Changing the theme invalidates style resolution for this node and its descendants, and also
marks layout dirty so any theme-dependent layout values can be reapplied.

- **`theme`** — the theme to assign locally to this node

#### getLocalTheme

```java
public final ThemeData getLocalTheme()
```

Returns the local theme assigned directly to this node.

Unlike `getTheme()`, this method does not inherit from the parent.

**Returns:** the local theme assigned to this node, or null if none is assigned

#### getStyleName

```java
public final String getStyleName()
```

Returns the optional named style used when resolving theme rules for this node.

**Returns:** the style name, or null if none is set

#### setStyleName

```java
public final void setStyleName(String styleName)
```

Sets the optional named style used during theme resolution.

Changing the style name invalidates the cached style and marks layout dirty.

- **`styleName`** — the style name to assign to this node

#### invalidateStyle

```java
protected void invalidateStyle()
```

Discards this node's resolved style and marks style application as pending.
Does not immediately perform layout or recursively invalidate descendants;
callers use the tree invalidation path when descendant styles are affected.

#### invalidateStyleTree

```java
protected void invalidateStyleTree()
```

Invalidates cached styles for this node and, in subclasses, potentially for descendant nodes.

The base implementation only invalidates this node. Containers may override this method
to propagate invalidation to children.

#### onNodeCreated

```java
protected void onNodeCreated(long yogaNode)
```

Called immediately after the Yoga node has been created for this UI node.

The default implementation simply calls `onCreate()`. Subclasses may override this
method if they need direct access to the Yoga node pointer during creation.

- **`yogaNode`** — the native Yoga node pointer that was created

#### onNodeWillDestroy

```java
protected void onNodeWillDestroy(long yogaNode)
```

Called immediately before the Yoga node owned by this UI node is destroyed.

The default implementation simply calls `onDestroy()`. Subclasses may override this
method if they need direct access to the Yoga node pointer during destruction.

- **`yogaNode`** — the native Yoga node pointer that is about to be destroyed

#### getParent

```java
public final UIContainer getParent()
```

Returns the parent container of this node.

**Returns:** the parent container, or null if this node is not attached to one

#### getRoot

```java
public UIRoot getRoot()
```

Returns the UI root that owns this node.

**Returns:** the root UI, or null if this node is not attached to a root

#### getYogaMemoryAddress

```java
public final long getYogaMemoryAddress()
```

Returns the native Yoga node memory address associated with this UI node.

**Returns:** the native Yoga node pointer, or `UIConstants#NULL` if none exists

#### hasYogaNode

```java
public final boolean hasYogaNode()
```

Returns whether this node currently has a native Yoga node attached.

**Returns:** true if a Yoga node exists for this UI node

#### markLayoutDirty

```java
public void markLayoutDirty()
```

Marks this node's layout as dirty.

This causes the node to be considered out of date with respect to layout. The dirty state is
also propagated upward to the parent so ancestor containers know they must participate in a
future layout pass.

#### getRenderX

```java
public final float getRenderX()
```

Returns the render-space X position of this node.

This value is based on the node's absolute X position in the UI tree.

**Returns:** the render-space X coordinate

#### getRenderY

```java
public final float getRenderY()
```

Returns the render-space Y position of this node.

Yoga layout uses a top-based coordinate system, while rendering in the engine uses a bottom-left
style coordinate system. This method converts the node's absolute Yoga position into render space.

**Returns:** the render-space Y coordinate

#### getX

```java
public final float getX()
```

Returns the node's local X position from Yoga layout results.

**Returns:** the local X position relative to the parent

#### setX

```java
    public void setX(float x)
```

Sets the layout's left offset in layout units. Computed position changes during
a subsequent layout pass; this does not directly rewrite cached render bounds.

- **`x`** — requested left offset

#### getY

```java
public final float getY()
```

Returns the node's local Y position from Yoga layout results.

**Returns:** the local Y position relative to the parent

#### setY

```java
    public void setY(float y)
```

Sets the layout top offset to y minus the node's current computed height.
This preserves this setter's bottom-edge convention; it is not a direct assignment
to the top offset returned by getY. Updated bounds require a layout pass.

- **`y`** — requested vertical position before subtracting current height

#### getWidth

```java
public final float getWidth()
```

Returns the computed width of this node.

This value is read from the cached bounds, which are updated from Yoga layout results.

**Returns:** the node width

#### setWidth

```java
    public void setWidth(float width)
```

Sets the requested layout width in layout units. The computed width returned
by getWidth is refreshed by layout rather than changed immediately here.

- **`width`** — requested layout width

#### getHeight

```java
public final float getHeight()
```

Returns the computed height of this node.

This value is read from the cached bounds, which are updated from Yoga layout results.

**Returns:** the node height

#### setHeight

```java
    public void setHeight(float height)
```

Sets the requested layout height in layout units. The computed height returned
by getHeight is refreshed by layout rather than changed immediately here.

- **`height`** — requested layout height

#### getAbsoluteX

```java
public final float getAbsoluteX()
```

Returns the absolute X position of this node in the UI tree.

This is cached during the parent-first layout traversal.

**Returns:** the absolute X position

#### getAbsoluteY

```java
public final float getAbsoluteY()
```

Returns the absolute Y position of this node in the UI tree.

This is converted from cached render bounds without walking ancestors or calling Yoga.

**Returns:** the absolute Y position

#### setPosition

```java
    public void setPosition(float x, float y)
```

Updates horizontal and vertical layout offsets through setX and setY.
The vertical setter subtracts the current computed height. This schedules layout
inputs without directly updating cached bounds.

- **`x`** — requested left offset
- **`y`** — vertical position before the current-height adjustment

#### setSize

```java
    public void setSize(float width, float height)
```

Updates requested layout width and height through the individual setters.
Computed bounds remain unchanged until layout applies the new inputs.

- **`width`** — requested width in layout units
- **`height`** — requested height in layout units

#### contains

```java
public boolean contains(float px, float py)
```

Returns whether the given point lies inside this node's cached bounds.

The coordinates are expected to already be in render space.

- **`px`** — the point X coordinate
- **`py`** — the point Y coordinate

**Returns:** true if the point lies inside the node bounds

#### getRenderSpaceHeight

```java
protected float getRenderSpaceHeight()
```

Returns the render-space height used for converting Yoga coordinates into drawing coordinates.

If a root with a viewport exists, the viewport's world height is used. Otherwise the root height
is used if it is greater than zero. If neither is available, the method falls back to the current
window height.

**Returns:** the height of the render space

#### screenToWorld

```java
public Vector2f screenToWorld(float x, float y)
```

Converts screen coordinates to world coordinates based on the current viewport.

- **`x`** — The x-coordinate in screen space.
- **`y`** — The y-coordinate in screen space.

**Returns:** A Vector2f representing the coordinates in world space. If no viewport is available,
returns a vector with the input screen coordinates adjusted.

#### screenToContent

```java
public final Vector2f screenToContent(float x, float y)
```

Converts a pointer to this node's unscrolled bottom-left content space.

#### screenToLayout

```java
public final Vector2f screenToLayout(float x, float y)
```

Same pointer conversion, expressed in Yoga/NanoVG's top-left coordinates.

#### isActivationRelease

```java
public final boolean isActivationRelease(valthorne.event.events.MouseReleaseEvent event)
```

True for a primary-button release over this control's visible hit target.

#### onCreate

```java
public abstract void onCreate()
```

Called when this node is created or when its Yoga node is attached.

Subclasses implement this method to perform initialization that depends on the node being
fully created and ready to enter the UI tree.

#### onDestroy

```java
public abstract void onDestroy()
```

Called when this node is about to be destroyed or detached from its Yoga node.

Subclasses implement this method to release owned resources, unregister temporary state,
or perform cleanup before the node is removed.

#### update

```java
public abstract void update(float delta)
```

Updates this node for the current frame.

Subclasses implement this method to perform time-based behavior such as animations,
cursor blinking, hover timers, interpolation, or custom logic.

- **`delta`** — the elapsed time in seconds since the previous update

#### draw

```java
public abstract void draw(TextureBatch batch)
```

Draws this node using the supplied texture batch.

Subclasses implement this method to render their current visual appearance using the
node's computed render position and size.

- **`batch`** — the texture batch used for drawing

#### render

```java
public final void render(TextureBatch batch)
```

Renders this node using its backend within the root's shared UI frame.

#### applyLayout

```java
protected void applyLayout()
```

Applies this node's `Layout` data to its native Yoga node.

This method delegates to `UIConstants#applyLayout(long, Layout)` and is typically used
during layout passes to push the node's desired layout properties into Yoga.

#### afterLayout

```java
protected void afterLayout()
```

Called after layout has been applied and computed.

The base implementation does nothing. Subclasses may override this method when they need to
react after layout results are available, such as caching positions, refreshing visual data,
or updating child-dependent measurements.

#### getTooltip

```java
public Tooltip getTooltip()
```

Returns the tooltip assigned to this node.

**Returns:** the tooltip, or null if none is assigned

#### setTooltip

```java
public void setTooltip(Tooltip tooltip)
```

Assigns a tooltip object directly to this node.

- **`tooltip`** — the tooltip to assign, or null to remove it

#### setTooltip

```java
public void setTooltip(String text)
```

Assigns tooltip text to this node.

If the provided text is null or blank, the tooltip is removed. Otherwise a new
`Tooltip` is created if necessary, or the existing tooltip text is updated.

- **`text`** — the tooltip text to assign

</details>

<a id="type-uirendercontext"></a>

### UIRenderContext

[Source](../../src/main/java/valthorne/ui/UIRenderContext.java#L56)

Coordinates texture-batch and NanoVG painting within one UI root draw.
Containers retain responsibility for child traversal; this context selects
the backend for each node and preserves the caller's backend across nested
drawing. Flushing at transitions keeps mixed children in painter order.

##### Custom Container Integration

During a container's draw callback, render each child through its normal
dispatch entry point. For example, given a child belonging to the active root:

```java
UIRenderContext context = getRoot().getRenderContext();
TextureBatch activeBatch = context.getBatch();
child.render(activeBatch);
```

A container can use `drawChildren(UIContainer, UINode)` to retain
compatible sibling painting state without changing traversal order. Scoped
translations and clips can be balanced with try-with-resources:

```java
try (UIRenderContext.Scope offset = context.translate(12, 8)) {
    context.drawChildren(container, null);
}
```

##### Shared Coordinates and Drawing State

The batch's translation and clip scopes are the common spatial state in
bottom-left world coordinates. Before a NanoVG node draws, the context resets
the NanoVG transform and scissor, applies the captured camera offset and zoom,
converts the clip rectangle using the render-space height, and applies the
batch translation with its Y component negated. NanoVG state is saved before
the callback and restored afterward, including when that callback throws.

##### Lifecycle

`UIRoot#draw()` creates a fresh context after beginning its batch and
initializing its NanoVG frame. The root owns those resources; this context
neither allocates nor disposes them. Widgets must not begin or end NanoVG
frames themselves. Backend transitions flush pending work, and returning to
texture painting restores the batch's graphics state.

Use this context only during its owning root's draw on the graphics-context
thread. Camera values are captured at construction, while batch translation
and clipping are read for each NanoVG node. Custom containers should use
`UINode#render(TextureBatch)` with `getBatch()` so nested nodes
participate in this dispatch and preserve mixed-backend ordering.

<details>
<summary>UIRenderContext operation reference (8 declarations)</summary>

#### getNodesDrawn

```java
public int getNodesDrawn()
```

Returns visible-node dispatch attempts so far. Null and hidden nodes are
excluded; a visible node is counted before inspection and callback execution,
so a failing callback still contributes. Repeated dispatches count separately.

**Returns:** the current draw's visible-node attempt count

#### getBackendSwitches

```java
public int getBackendSwitches()
```

Returns attempted changes between painting backends. Selecting the current
backend adds nothing. A transition increments the counter before flushing,
so a failed transition can still be included.

**Returns:** backend transitions attempted during this root draw

#### getNanoFlushes

```java
public int getNanoFlushes()
```

Returns NanoVG end-frame submissions made when returning to texture painting.
The count increments after the native end-frame call, before restoring batch
bindings. It is a submission count rather than a GPU draw-call count.

**Returns:** NanoVG submissions completed by this context so far

#### getBatch

```java
public TextureBatch getBatch()
```

Returns the active batch shared by all nodes in this draw. This is the
root-owned instance, not a copy: its translation and clip scopes also
control subsequent NanoVG node dispatch. Callers must balance any scopes
they change and leave batch lifetime management to the root.

**Returns:** the borrowed batch to pass to `UINode#render(TextureBatch)`

#### draw

```java
public void draw(UINode node)
```

Dispatches one visible node to its supported painting backend. Null or
invisible nodes produce no drawing or backend transitions. Nodes implementing
`NanoNode` receive the NanoVG handle; all other nodes receive the batch.
Child traversal remains the node's responsibility.

Each callback runs with its backend selected. NanoVG callbacks additionally
receive the shared clip and translation state mapped into NanoVG coordinates.
A `finally` block restores the previous backend, allowing a container
to paint its foreground after children that use the other backend. NanoVG
callbacks also restore saved NanoVG state. Callback exceptions propagate
after this cleanup; already submitted drawing is not rolled back.

- **`node`** — the node to paint, or `null` to do nothing

**Throws `IllegalStateException`:** if a visible NanoVG node is drawn without an
available NanoVG context

#### drawChildren

```java
public void drawChildren(UIContainer container, UINode excluded)
```

Draws children in their existing order, skipping one optional child by
reference identity. Null and invisible children are ignored by dispatch.
NanoVG siblings may retain their backend between callbacks, reducing
transitions while preserving painter order.

The parent's entry backend is restored in a finally block, allowing the
parent to paint foreground content afterward. Do not structurally modify
the child collection while it is being traversed. This method does not draw
the container itself or establish translation and clip scopes for it.

- **`container`** — the container whose children should be traversed
- **`excluded`** — the child to skip, or null when no nonnull child is excluded

**Throws `NullPointerException`:** if container is null

#### translate

```java
public Scope translate(float x, float y)
```

Pushes an additional translation in top-left layout units. Positive X moves
right and positive Y moves down; Y is negated when stored in the batch's
bottom-left coordinate system. Both painting backends read this shared state.

- **`x`** — additional horizontal displacement in layout units
- **`y`** — additional downward displacement in layout units

**Returns:** a scope that pops this translation when closed; close scopes in reverse order

**Throws `IllegalStateException`:** if the batch is not drawing or its translation stack is full

#### clip

```java
public Scope clip(float x, float y, float width, float clipHeight)
```

Pushes a clip rectangle expressed in top-left render-space coordinates.
Converts its Y origin using the captured render-space height and passes it
to the batch, where nested scissors intersect. Coordinates must already
include any desired translation; this method does not add the batch's
translation to the rectangle. Prefer nonnegative finite dimensions.

- **`x`** — the left edge in render-space layout units
- **`y`** — the top edge in render-space layout units
- **`width`** — the requested clip width
- **`clipHeight`** — the requested clip height

**Returns:** a scope restoring the previous clip when closed, in reverse nesting order

**Throws `IllegalStateException`:** if the batch is not drawing or its clip stack is full

</details>

<a id="type-uirendercontext-scope"></a>

### UIRenderContext.Scope

[Source](../../src/main/java/valthorne/ui/UIRenderContext.java#L331)

Owns one pending restoration action for a translation or clipping scope.
Use with try-with-resources inside the root draw and close nested scopes in
reverse order. Closing is idempotent, but the scope does not validate nesting
order or extend the lifetime of the batch it restores.

The restoration reference is cleared before invocation, so an action that
throws is not retried on another close. Instances are not synchronized.

<details>
<summary>UIRenderContext.Scope operation reference (1 declarations)</summary>

#### close

```java
        public void close()
```

Runs the pending restoration once and marks this scope closed before
invoking it. Later calls do nothing, including after restoration throws.
Batch state failures propagate to the caller.

**Throws `IllegalStateException`:** if the underlying batch restoration rejects
the current drawing or stack state

</details>

<a id="type-uiroot"></a>

### UIRoot

[Source](../../src/main/java/valthorne/ui/UIRoot.java#L144)

`UIRoot` is the top-level root container for Valthorne's UI system.
It acts as the central coordinator for layout, rendering, focus management,
mouse interaction, keyboard interaction, scroll handling, tooltip display,
overlay rendering, and window resize propagation.

As the root of the UI tree, this class is responsible for owning the Yoga
configuration used for layout calculation, maintaining a dedicated
`TextureBatch` for UI rendering, and providing a special overlay layer
that is drawn above normal UI content. This overlay layer is primarily used
for transient or top-level UI elements such as tooltips, popups, and other
floating interface components that should render above the standard widget tree.

In addition to layout and rendering, `UIRoot` also serves as the global
input router for the UI hierarchy. It listens to:

- `KeyPressEvent` and `KeyReleaseEvent`

- `MousePressEvent`, `MouseReleaseEvent`, `MouseMoveEvent`, and `MouseDragEvent`

- `MouseScrollEvent`

- `WindowResizeEvent`

These events are processed centrally and then forwarded to the appropriate
`UINode` based on visibility, focusability, clickability, scrollability,
and hit testing rules.

The root also manages:

- the currently focused node

- the currently pressed node

- the currently hovered node

- tooltip activation timing and placement

- tab focus traversal

- viewport-aware input coordinate conversion

If a `Viewport` is assigned, the root converts screen coordinates to world
coordinates before hit testing and rendering logic is applied. This allows the
UI to operate in either raw window space or viewport-controlled world space.

The normal lifecycle of a root UI container is:

- Create the root

- Add child nodes

- Call `layout()` whenever layout needs recalculation

- Call `update(float)` each frame

- Call `draw()` each frame

- Call `dispose()` on shutdown

##### Example Usage

```java
UIRoot root = new UIRoot();

Panel panel = new Panel();
panel.getLayout()
     .width(300)
     .height(200)
     .left(20)
     .top(20);

root.add(panel);
root.layout();

// Game loop
root.update(delta);
root.draw();

// Focus management
root.focusNext();
UINode focused = root.getFocused();

// Optional viewport support
root.setViewport(viewport);

// Overlay usage
Tooltip tooltip = new Tooltip();
root.showOverlay(tooltip);
root.hideOverlay(tooltip);

// Shutdown
root.dispose();
```

This example demonstrates the complete usage of the class: creation, child
attachment, layout, per-frame update and draw, focus traversal, viewport usage,
overlay control, and final disposal.

<details>
<summary>UIRoot operation reference (30 declarations)</summary>

#### Constructor

```java
public UIRoot()
```

Creates a new `UIRoot`, initializes Yoga configuration, attaches the
root to the Yoga system, configures the overlay layer, sizes the root to the
current window, and registers all required global input and window listeners.

The overlay layer is configured as an absolute-positioned full-size panel that
does not participate in interaction and exists purely for top-level floating UI.

#### getInspector

```java
public UIInspector getInspector()
```

Returns the live root-owned inspector used to capture optional drawing
diagnostics. Its lifetime follows this root.

**Returns:** mutable inspection controller

#### getFrameStats

```java
public UIFrameStats getFrameStats()
```

Returns the last recorded frame's immutable statistics, initially all zero.
Layout counters accumulate until a drawing context is recorded.

**Returns:** latest frame statistics

#### getYogaConfig

```java
public long getYogaConfig()
```

Returns the Yoga configuration handle owned by this root.

This configuration is shared by the UI hierarchy attached to the root and is
typically only needed by lower-level layout code.

**Returns:** the Yoga configuration handle

#### getBatch

```java
public TextureBatch getBatch()
```

Returns the `TextureBatch` used by this UI root for rendering.

**Returns:** the UI render batch

#### getViewport

```java
public Viewport getViewport()
```

Returns the viewport currently assigned to this root, or `null` if none
is being used.

**Returns:** the active viewport, or `null`

#### setViewport

```java
public void setViewport(Viewport viewport)
```

Assigns a viewport to this root.

When a viewport is set, it is immediately updated to match the current window
size. If the viewport has a camera, the camera is centered in the viewport's
world dimensions. The root size is then updated to match the viewport world
dimensions and the UI is laid out again.

- **`viewport`** — the new viewport, or `null` to disable viewport usage

#### setSize

```java
public void setSize(float width, float height)
```

Sets the root UI size.

This updates the Yoga style width and height of the root node, resizes the
overlay layer to match, and marks layout as dirty so a future layout pass
will recompute positions and sizes.

- **`width`** — the new root width
- **`height`** — the new root height

#### layout

```java
public void layout()
```

Performs a full layout pass on the UI tree.

This method first synchronizes layout properties from nodes into Yoga by
calling `syncTree(UINode, boolean)`, then asks Yoga to calculate layout for the
entire hierarchy, and finally applies the results back into the node tree by
calling `updateLayoutTree()`.

#### showOverlay

```java
public void showOverlay(UINode node)
```

Shows the supplied node in the overlay layer.

If the node currently belongs to another parent, it is removed from that parent
and reattached to the overlay layer. The node is then made visible and a new
layout pass is triggered.

- **`node`** — the node to show in the overlay layer

#### hideOverlay

```java
public void hideOverlay(UINode node)
```

Hides the supplied node from the overlay layer.

The node is marked invisible, removed from the overlay layer if it is currently
attached there, and the UI is relaid out afterward.

- **`node`** — the overlay node to hide

#### update

```java
    public void update(float delta)
```

Updates the full UI tree and tooltip state.

This first updates all child nodes through the superclass update chain. It then
manages tooltip timing for the currently hovered node. If no hovered node exists,
or the hovered node has no tooltip, the active tooltip is hidden and hover timing
is reset. If a tooltip exists and the hover delay has elapsed, the tooltip is
shown and positioned near the mouse.

- **`delta`** — the frame delta time in seconds

#### getRoot

```java
    public UIRoot getRoot()
```

Returns this instance as the root of the UI tree.

**Returns:** this root

#### draw

```java
public void draw()
```

Draws the UI using this root's internal `TextureBatch`.

If a viewport is assigned, the viewport is bound before drawing and unbound
afterward. Otherwise drawing is performed directly without viewport wrapping.
In both cases, the batch is begun, the tree is drawn, and the batch is ended.

#### draw

```java
    public void draw(TextureBatch batch)
```

Draws normal visible children while excluding the overlay layer, which the
root's no-argument draw method renders afterward. Uses the active mixed context
when present; otherwise delegates each child directly to the supplied batch.
Does not begin/end the batch or create a NanoVG frame.

- **`batch`** — prepared destination batch

#### getRenderContext

```java
public UIRenderContext getRenderContext()
```

Returns the borrowed mixed-backend context while the root is drawing.
The reference is cleared when the drawing scope exits and must not be retained
as a reusable context.

**Returns:** active context, or null outside draw

#### dispose

```java
public void dispose()
```

Disposes this root and releases all resources and listeners it owns.

This hides any active tooltip, unregisters input and window listeners, detaches
the UI tree from Yoga, frees the Yoga configuration, and disposes the render batch.
After this method is called, the root should no longer be used.

#### getFocused

```java
public UINode getFocused()
```

Returns the node currently holding keyboard focus.

**Returns:** the focused node, or `null` if no node is focused

#### setFocusTo

```java
public void setFocusTo(UINode next)
```

Transfers focus to the provided node if it is currently focusable.

The previously focused node is unfocused first. If the next node is not focusable
under current runtime conditions, focus is cleared instead. Any active tooltip is
hidden when focus changes.

- **`next`** — the node to focus, or `null` to clear focus

#### focusNext

```java
public void focusNext()
```

Moves focus to the next focusable node in traversal order.

If no current focus exists, the first focusable node is selected. If traversal
reaches the end of the tree, focus wraps back to the first focusable node.

#### findNodeAt

```java
    public UINode findNodeAt(float x, float y, int requiredBit)
```

Finds the top-most matching node at the provided coordinates.

If a viewport is active, the input coordinates are first converted from screen
space into world space. The overlay layer is checked first, allowing overlay
elements to take precedence over normal UI nodes. If no overlay node matches,
the normal container search is used.

- **`x`** — the X coordinate
- **`y`** — the Y coordinate
- **`requiredBit`** — the required node bit flag

**Returns:** the matching node, or `null` if none was found

#### applyLayout

```java
    protected void applyLayout()
```

Applies layout behavior for the root itself.

The root does not apply additional layout work in this override because layout
is handled at a higher level through Yoga and tree synchronization.

#### focusPrevious

```java
public void focusPrevious()
```

Move backwards through the active focus scope, wrapping at its start.

#### showModal

```java
public void showModal(UINode node)
```

Moves a nonnull node into the overlay layer, remembers prior focus, and pushes
a modal scope. Cancels pointer capture, clears focus, and selects the first
eligible node in the scope. Reopening an already registered scope has no effect.

- **`node`** — nonnull modal subtree

#### hideModal

```java
public void hideModal(UINode node)
```

Hides/removes a modal through hideOverlay. Removing the top scope restores
its saved focus when eligible, otherwise traversal chooses a replacement.

- **`node`** — modal node to hide; null has no effect

#### cancelInput

```java
public void cancelInput()
```

Clear captured gestures and focus without synthesizing a release/click.

#### getCaptured

```java
public UINode getCaptured()
```

Returns the node currently captured by a mouse press, independently of current
pointer location.

**Returns:** captured node, or null

#### getHovered

```java
public UINode getHovered()
```

Returns the last node selected by pointer hover handling.

**Returns:** hovered node, or null

#### getNanoVGHandle

```java
public long getNanoVGHandle()
```

Returns the stored NanoVG handle. It is zero when creation failed or disposal
cleared it; callers borrow the context and must coordinate with root rendering.

**Returns:** current native NanoVG context handle

#### setNanoVGHandle

```java
public void setNanoVGHandle(long nanoVGHandle)
```

Replaces the stored NanoVG handle without deleting the old one or registering
fonts on the replacement. The root will delete the stored nonzero handle during
disposal, so callers must arrange ownership of both contexts.

- **`nanoVGHandle`** — replacement handle, or zero to disable NanoVG rendering

</details>

<a id="type-uiroot-focusscope"></a>

### UIRoot.FocusScope — internal support type

[Source](../../src/main/java/valthorne/ui/UIRoot.java#L1392)

One modal input boundary and the focus target that preceded it. Both nodes
are borrowed references; eligibility is rechecked when restoring focus.

The stack of these entries constrains routing to the active modal subtree. Removing
a scope may restore its previous focus only if that node is still attached and eligible.

- **`node`** — modal subtree root
- **`previous`** — focus target to restore, possibly null

<a id="type-uiroot-rootkeylistener"></a>

### UIRoot.RootKeyListener — internal support type

[Source](../../src/main/java/valthorne/ui/UIRoot.java#L1404)

Persistent global keyboard adapter owned by this root. Forwards press and
release events into scoped root routing and is unregistered during disposal.

The adapter retains its enclosing root and forwards the original event objects, allowing
consumption to remain visible to subsequent routing. Keyboard policy stays in the
root handlers rather than in the adapter.

<details>
<summary>UIRoot.RootKeyListener operation reference (2 declarations)</summary>

#### keyPressed

```java
        public void keyPressed(KeyPressEvent event)
```

Forwards a key press event to `UIRoot#handleKeyPressed(KeyPressEvent)`.

- **`event`** — the key press event

#### keyReleased

```java
        public void keyReleased(KeyReleaseEvent event)
```

Forwards a key release event to `UIRoot#handleKeyReleased(KeyReleaseEvent)`.

- **`event`** — the key release event

</details>

<a id="type-uiroot-rootmouselistener"></a>

### UIRoot.RootMouseListener — internal support type

[Source](../../src/main/java/valthorne/ui/UIRoot.java#L1440)

Persistent pointer adapter forwarding presses, releases, drags, and movement
to the owning root's capture and hover logic.

The original event object is passed through so capture, hit testing, and consumption
share one routing decision. Registration and removal follow the enclosing root's
lifecycle; the adapter owns no native cursor resources.

<details>
<summary>UIRoot.RootMouseListener operation reference (4 declarations)</summary>

#### mousePressed

```java
        public void mousePressed(MousePressEvent event)
```

Forwards a mouse press event to `UIRoot#handleMousePressed(MousePressEvent)`.

- **`event`** — the mouse press event

#### mouseReleased

```java
        public void mouseReleased(MouseReleaseEvent event)
```

Forwards a mouse release event to `UIRoot#handleMouseReleased(MouseReleaseEvent)`.

- **`event`** — the mouse release event

#### mouseDragged

```java
        public void mouseDragged(MouseDragEvent event)
```

Forwards a mouse drag event to `UIRoot#handleMouseDragged(MouseDragEvent)`.

- **`event`** — the mouse drag event

#### mouseMoved

```java
        public void mouseMoved(MouseMoveEvent event)
```

Forwards a mouse move event to `UIRoot#handleMouseMoved(MouseMoveEvent)`.

- **`event`** — the mouse move event

</details>

<a id="type-uiroot-rootscrolllistener"></a>

### UIRoot.RootScrollListener — internal support type

[Source](../../src/main/java/valthorne/ui/UIRoot.java#L1500)

Persistent global scroll adapter forwarding events into the owning root's
hit testing and bubbling path.

It forwards the same event object so a consuming scroll target can stop further
propagation. Scroll offsets belong to controls, while this adapter only connects
global delivery to root routing.

<details>
<summary>UIRoot.RootScrollListener operation reference (1 declarations)</summary>

#### mouseScrolled

```java
        public void mouseScrolled(MouseScrollEvent event)
```

Forwards a mouse scroll event to `UIRoot#handleMouseScrolled(MouseScrollEvent)`.

- **`event`** — the mouse scroll event

</details>

<a id="type-uiroot-rootwindowlistener"></a>

### UIRoot.RootWindowListener — internal support type

[Source](../../src/main/java/valthorne/ui/UIRoot.java#L1524)

Persistent resize adapter updating the root and optional viewport when the
window dimensions change. Unregistered with the root lifecycle.

The resize event is forwarded to the root's sizing policy instead of directly mutating
individual children. Layout and viewport decisions remain centralized in the owning
root, and this listener allocates no window resources.

<details>
<summary>UIRoot.RootWindowListener operation reference (1 declarations)</summary>

#### windowResized

```java
        public void windowResized(WindowResizeEvent event)
```

Forwards a window resize event to `UIRoot#handleWindowResized(WindowResizeEvent)`.

- **`event`** — the resize event

</details>

## Related guides

- [UI layout and alignment](ui-layout.md)
- [Standard UI controls](ui-controls.md)
- [NanoVG UI controls](ui-nano.md)
- [Themes, styles, and design tokens](ui-themes.md)
- [Performance overlays and UI inspection](diagnostics.md)
- [Existing ui system guide](../ui-system.md)
- [Existing ui rendering guide](../ui-rendering.md)
