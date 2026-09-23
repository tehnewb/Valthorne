# UI layout and alignment

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Layout wraps Yoga's flexbox inputs and exposes computed node geometry through the UI tree. Use LayoutValue and LayoutUnit to distinguish automatic, absolute-point, and percentage values. Layout is a constraint calculation, so a requested dimension and a computed dimension are different states.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Flex flow | Direction, wrapping, grow/shrink behavior, and basis determine how children occupy space. |
| Alignment | Main-axis justification and cross-axis alignment position children and wrapped lines. |
| Spacing | Margins, padding, and gaps affect different boundaries. |
| Constraints | Minimum/maximum sizes and position mode constrain or remove items from normal flow. |
| Geometry interfaces | Locatable, Sizeable, and Dimensional expose position/size contracts used by layout and alignment tools. |

## Getting started

1. Choose a container direction and define which dimensions are fixed, percentage-based, or automatic.
2. Set spacing and child grow/shrink/alignment rules.
3. Request or allow the root's layout pass after changing inputs.
4. Read computed bounds after layout and use the matching coordinate conversion for interaction.

## Ownership and lifecycle

Layout objects and native Yoga nodes belong to the UI lifecycle. A setter can mark work pending rather than synchronously calculating the entire tree. Reused alignment vectors are temporary results and should be copied when retained.

## Important behavior

- UINode.setY applies its documented height adjustment; it is not a direct top-offset assignment.
- Overflow layout settings alone do not implement scrolling behavior.
- Avoid feedback loops that continuously change layout inputs during measurement.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Dimensional`](#type-dimensional)
- [`Align`](#type-align)
- [`Alignment`](#type-alignment)
- [`FlexDirection`](#type-flexdirection)
- [`FlexWrap`](#type-flexwrap)
- [`JustifyContent`](#type-justifycontent)
- [`LayoutUnit`](#type-layoutunit)
- [`Overflow`](#type-overflow)
- [`PositionType`](#type-positiontype)
- [`Layout`](#type-layout)
- [`LayoutValue`](#type-layoutvalue)
- [`Locatable`](#type-locatable)
- [`Sizeable`](#type-sizeable)

<a id="type-dimensional"></a>

### Dimensional

[Source](../../src/main/java/valthorne/math/geometry/Dimensional.java#L13)

The Dimensional interface combines the behaviors of both Sizeable and Locatable interfaces.
Implementing this interface indicates that an object possesses both size and location properties.

This interface does not define any additional methods or properties,
but serves as a marker for objects that are both sizeable and locatable.

<a id="type-align"></a>

### Align

[Source](../../src/main/java/valthorne/ui/enums/Align.java#L41)

`Align` represents the alignment rules used by the Yoga layout engine
for aligning items along the cross axis of a flex container.

These values map directly to Yoga's native `YGAlign` constants and are
used by Valthorne's UI layout system when configuring element alignment.

Alignment affects how children are positioned relative to the container's
cross axis (the axis perpendicular to the main flex direction).

##### Example Usage

```java
Panel panel = new Panel();

panel.getLayout()
     .flexDirection(FlexDirection.ROW)
     .alignItems(Align.CENTER);

int yogaAlign = Align.CENTER.yoga();
```

This example centers all children vertically when the container is laid out
in a horizontal row.

<details>
<summary>Align operation reference (9 declarations)</summary>

#### AUTO

```java
public static final  Align AUTO
```

Uses automatic cross-axis alignment, inheriting the applicable container policy.

#### FLEX_START

```java
public static final  Align FLEX_START
```

Aligns items or lines at the cross-axis start.

#### CENTER

```java
public static final  Align CENTER
```

Centers items or lines along the cross axis.

#### FLEX_END

```java
public static final  Align FLEX_END
```

Aligns items or lines at the cross-axis end.

#### STRETCH

```java
public static final  Align STRETCH
```

Stretches eligible items or lines across available cross-axis space.

#### BASELINE

```java
public static final  Align BASELINE
```

Aligns items by their text baseline where supported by the layout context.

#### SPACE_BETWEEN

```java
public static final  Align SPACE_BETWEEN
```

Distributes wrapped lines with free space between them and no extra outer gaps.

#### SPACE_AROUND

```java
public static final  Align SPACE_AROUND
```

Distributes wrapped lines with surrounding space and half-sized outer gaps.

#### yoga

```java
public int yoga()
```

Returns the raw Yoga alignment constant used internally by the Yoga layout engine.

**Returns:** the Yoga alignment constant

</details>

<a id="type-alignment"></a>

### Alignment

[Source](../../src/main/java/valthorne/ui/enums/Alignment.java#L72)

Defines simple horizontal and vertical alignment modes used to position one object relative
to another object.

This enum is primarily used by UI and rendering code that needs to align a target object
inside or against a source object. The source provides a world-space position and dimensions
through `Dimensional`, while the target provides its size through `Sizeable`.
The alignment methods then compute the final x and y coordinates where the target should be
placed.

##### Supported alignment modes

- `START` aligns to the left edge horizontally or bottom edge vertically

- `CENTER` aligns to the center along the requested axis

- `END` aligns to the right edge horizontally or top edge vertically

##### How this works

The alignment methods do not move either object directly. They only calculate coordinates.
This is useful when you want to position text, textures, buttons, icons, or other UI content
inside another element without duplicating alignment math all over the codebase.

For example, if a button is 200 pixels wide and the text inside it is 80 pixels wide:

- `START` places the text at the button's x position

- `CENTER` places the text so equal space appears on both sides

- `END` places the text so its right edge touches the button's right edge

##### Reusable vector note

The combined `align(Dimensional, Sizeable, Alignment, Alignment)` methods return a reused
internal `Vector2f` instance for performance. That means the returned vector should be used
immediately and not stored long-term if another alignment call may happen later.

##### Example

```java
Element panel = new Element();
panel.setPosition(100f, 50f);
panel.setSize(300f, 120f);

Font label = new Font(fontData);
label.setText("Play");

Vector2f pos = Alignment.align(panel, label, Alignment.CENTER, Alignment.CENTER);
label.setPosition(pos.x(), pos.y());

Vector2f bottomLeft = Alignment.align(panel, label, Alignment.START, Alignment.START);
label.setPosition(bottomLeft.x(), bottomLeft.y());

float rightX = Alignment.alignHorizontally(panel, label, Alignment.END);
float centerY = Alignment.alignVertically(panel, label, Alignment.CENTER);
label.setPosition(rightX, centerY);
```

<details>
<summary>Alignment operation reference (7 declarations)</summary>

#### START

```java
public static final  Alignment START
```

Places the object at the beginning of the available alignment span.

#### CENTER

```java
public static final  Alignment CENTER
```

Centers the object within the available alignment span.

#### END

```java
public static final  Alignment END
```

Places the object at the end of the available alignment span.

#### align

```java
public static Vector2f align(Dimensional source, Sizeable target, Alignment horizontalAlignment, Alignment verticalAlignment)
```

Computes a full 2D alignment position for a target relative to a source using separate
horizontal and vertical alignment modes.

This method combines `alignHorizontally(Dimensional, Sizeable, Alignment)` and
`alignVertically(Dimensional, Sizeable, Alignment)` into a single call. The resulting
x and y values are written into a reused internal `Vector2f` and then returned.

Because the returned vector is reused internally, callers should not assume the returned
object remains stable after future alignment calls.

- **`source`** — the object that provides the base position and available size
- **`target`** — the object being aligned inside or against the source
- **`horizontalAlignment`** — the horizontal alignment mode to apply
- **`verticalAlignment`** — the vertical alignment mode to apply

**Returns:** a reused vector containing the aligned x and y position

**Throws `NullPointerException`:** if any argument is null

#### align

```java
public static Vector2f align(Dimensional source, Sizeable target, Alignment alignment)
```

Computes a full 2D alignment position for a target relative to a source using the same
alignment mode on both axes.

This is a convenience overload for cases such as centering on both axes or aligning to
the start or end on both axes at once.

- **`source`** — the object that provides the base position and available size
- **`target`** — the object being aligned inside or against the source
- **`alignment`** — the alignment mode to apply to both the horizontal and vertical axes

**Returns:** a reused vector containing the aligned x and y position

**Throws `NullPointerException`:** if any argument is null

#### alignHorizontally

```java
public static float alignHorizontally(Dimensional source, Sizeable target, Alignment alignment)
```

Computes the horizontal aligned x position for a target relative to a source.

The horizontal rules are:

- `START`: the target's left edge matches the source's x position

- `CENTER`: the target is centered within the source's width

- `END`: the target's right edge matches the source's right edge

- **`source`** — the object that provides the base x position and width
- **`target`** — the object whose width is being aligned
- **`alignment`** — the horizontal alignment mode

**Returns:** the computed world-space x position for the target

**Throws `NullPointerException`:** if any argument is null

#### alignVertically

```java
public static float alignVertically(Dimensional source, Sizeable target, Alignment alignment)
```

Computes the vertical aligned y position for a target relative to a source.

The vertical rules are:

- `START`: the target's bottom edge matches the source's y position

- `CENTER`: the target is centered within the source's height

- `END`: the target's top edge matches the source's top edge

This follows a bottom-left coordinate system, which matches the rest of your rendering
setup and OpenGL-style positioning.

- **`source`** — the object that provides the base y position and height
- **`target`** — the object whose height is being aligned
- **`alignment`** — the vertical alignment mode

**Returns:** the computed world-space y position for the target

**Throws `NullPointerException`:** if any argument is null

</details>

<a id="type-flexdirection"></a>

### FlexDirection

[Source](../../src/main/java/valthorne/ui/enums/FlexDirection.java#L38)

`FlexDirection` defines the primary axis used by the Yoga flex layout
engine when arranging child elements.

The direction determines whether children are placed horizontally or vertically
and whether the order is normal or reversed.

These values map directly to Yoga's `YGFlexDirection` constants.

##### Example Usage

```java
Panel panel = new Panel();

panel.getLayout()
     .flexDirection(FlexDirection.COLUMN);

int yogaDirection = FlexDirection.COLUMN.yoga();
```

This example arranges children vertically from top to bottom.

<details>
<summary>FlexDirection operation reference (5 declarations)</summary>

#### ROW

```java
public static final  FlexDirection ROW
```

Lays children along the horizontal main axis in the layout direction.

#### COLUMN

```java
public static final  FlexDirection COLUMN
```

Lays children along the vertical main axis from top to bottom.

#### ROW_REVERSE

```java
public static final  FlexDirection ROW_REVERSE
```

Lays children horizontally with main-axis order reversed.

#### COLUMN_REVERSE

```java
public static final  FlexDirection COLUMN_REVERSE
```

Lays children vertically with main-axis order reversed.

#### yoga

```java
public int yoga()
```

Returns the raw Yoga constant used internally by the Yoga layout engine.

**Returns:** the Yoga flex direction constant

</details>

<a id="type-flexwrap"></a>

### FlexWrap

[Source](../../src/main/java/valthorne/ui/enums/FlexWrap.java#L34)

`FlexWrap` controls whether children inside a flex container
are allowed to wrap onto multiple lines.

This directly maps to Yoga's `YGWrap` values.

##### Example Usage

```java
Panel panel = new Panel();

panel.getLayout()
     .flexWrap(FlexWrap.WRAP);

int yogaWrap = FlexWrap.WRAP.yoga();
```

When wrapping is enabled, children that exceed the container width
will flow onto a new row or column.

<details>
<summary>FlexWrap operation reference (4 declarations)</summary>

#### NO_WRAP

```java
public static final  FlexWrap NO_WRAP
```

Keeps children on one flex line even when their requested sizes exceed available space.

#### WRAP

```java
public static final  FlexWrap WRAP
```

Allows children to flow onto additional flex lines.

#### WRAP_REVERSE

```java
public static final  FlexWrap WRAP_REVERSE
```

Allows wrapping while reversing the cross-axis order of flex lines.

#### yoga

```java
public int yoga()
```

Returns the raw Yoga constant used internally by the Yoga layout engine.

**Returns:** the Yoga wrap constant

</details>

<a id="type-justifycontent"></a>

### JustifyContent

[Source](../../src/main/java/valthorne/ui/enums/JustifyContent.java#L34)

`JustifyContent` controls how child elements are distributed
along the main axis of a flex container.

These values correspond directly to Yoga's `YGJustify` constants.

##### Example Usage

```java
Panel panel = new Panel();

panel.getLayout()
     .flexDirection(FlexDirection.ROW)
     .justifyContent(JustifyContent.SPACE_BETWEEN);

int yogaValue = JustifyContent.SPACE_BETWEEN.yoga();
```

This example distributes children evenly across the container width.

<details>
<summary>JustifyContent operation reference (7 declarations)</summary>

#### FLEX_START

```java
public static final  JustifyContent FLEX_START
```

Packs children at the main-axis start.

#### CENTER

```java
public static final  JustifyContent CENTER
```

Centers the group of children along the main axis.

#### FLEX_END

```java
public static final  JustifyContent FLEX_END
```

Packs children at the main-axis end.

#### SPACE_BETWEEN

```java
public static final  JustifyContent SPACE_BETWEEN
```

Places available main-axis space between children with no extra outer gaps.

#### SPACE_AROUND

```java
public static final  JustifyContent SPACE_AROUND
```

Places space around each child, leaving half-sized gaps at the outer edges.

#### SPACE_EVENLY

```java
public static final  JustifyContent SPACE_EVENLY
```

Uses equal gaps between children and at both main-axis edges.

#### yoga

```java
public int yoga()
```

Returns the Yoga constant used by the layout engine.

**Returns:** the Yoga justify constant

</details>

<a id="type-layoutunit"></a>

### LayoutUnit

[Source](../../src/main/java/valthorne/ui/enums/LayoutUnit.java#L31)

`LayoutUnit` defines the unit type used when specifying layout values
such as width, height, margin, or padding.

This determines how the numeric value should be interpreted by the layout system.

- `AUTO` \u2014 size determined automatically by layout

- `POINT` \u2014 fixed pixel/point value

- `PERCENT` \u2014 percentage relative to parent

##### Example Usage

```java
Layout layout = element.getLayout();

layout.width(200, LayoutUnit.POINT);
layout.height(50, LayoutUnit.PERCENT);
```

<details>
<summary>LayoutUnit operation reference (3 declarations)</summary>

#### AUTO

```java
public static final  LayoutUnit AUTO
```

Delegates dimension selection to automatic layout sizing.

#### POINT

```java
public static final  LayoutUnit POINT
```

Interprets the value as an absolute number of layout points.

#### PERCENT

```java
public static final  LayoutUnit PERCENT
```

Interprets the value as a percentage of the applicable reference dimension.

</details>

<a id="type-overflow"></a>

### Overflow

[Source](../../src/main/java/valthorne/ui/enums/Overflow.java#L33)

`Overflow` controls how content is handled when it exceeds
the bounds of its container.

These values map directly to Yoga's `YGOverflow` constants.

##### Example Usage

```java
Panel panel = new Panel();

panel.getLayout()
     .overflow(Overflow.HIDDEN);

int yogaOverflow = Overflow.HIDDEN.yoga();
```

In this example, child content that exceeds the panel bounds will be clipped.

<details>
<summary>Overflow operation reference (4 declarations)</summary>

#### VISIBLE

```java
public static final  Overflow VISIBLE
```

Allows overflowing content to remain visible under the applicable rendering policy.

#### HIDDEN

```java
public static final  Overflow HIDDEN
```

Requests hidden overflow; UI rendering must apply the corresponding clipping policy.

#### SCROLL

```java
public static final  Overflow SCROLL
```

Marks content as scrollable for layout; a scroll control supplies offsets and interaction.

#### yoga

```java
public int yoga()
```

Returns the Yoga constant used internally by the layout engine.

**Returns:** the Yoga overflow constant

</details>

<a id="type-positiontype"></a>

### PositionType

[Source](../../src/main/java/valthorne/ui/enums/PositionType.java#L45)

`PositionType` determines how an element's position is calculated
within the Yoga layout system.

Two positioning modes are supported:

- `RELATIVE` \u2014 participates in normal flex layout flow

- `ABSOLUTE` \u2014 positioned independently of layout flow

These values map directly to Yoga's `YGPositionType` constants.

##### Example Usage

```java
Tooltip tooltip = new Tooltip();

tooltip.getLayout()
       .positionType(PositionType.ABSOLUTE)
       .left(100)
       .top(50);

int yogaValue = PositionType.ABSOLUTE.yoga();
```

This example places the tooltip at an exact coordinate instead of allowing
it to flow with the layout.

<details>
<summary>PositionType operation reference (3 declarations)</summary>

#### RELATIVE

```java
public static final  PositionType RELATIVE
```

Keeps the node in normal flex flow, with relative position offsets applied.

#### ABSOLUTE

```java
public static final  PositionType ABSOLUTE
```

Removes the node from normal flex flow and positions it using its containing block.

#### yoga

```java
public int yoga()
```

Returns the Yoga constant used by the layout engine.

**Returns:** the Yoga position constant

</details>

<a id="type-layout"></a>

### Layout

[Source](../../src/main/java/valthorne/ui/Layout.java#L38)

Mutable layout configuration containing dimensions, edge offsets, spacing, and
flexbox policy for a UINode. This object stores values; the root's layout solver
resolves percentages, auto sizing, positioning, and flex distribution later.
It starts as a relative nonwrapping column with stretched children, zero gaps,
zero grow/shrink factors, and automatic size/position constraints.

Most setters notify the supplied callback synchronously only when stored
values differ. Numeric values are retained without range/finiteness checks.
Composite helpers may notify several times and are not transactional. Single-value
margin and padding helpers assign all four sides without calling the callback;
when using those on an already laid-out node, request layout invalidation explicitly.
Callback failures leave earlier assignments in place.

Percent values use 100 for the whole solver-selected reference extent.
LayoutValue objects are immutable and shared safely; this Layout is mutable
and intended for the UI thread. A widthFill or heightFill request sets flex
growth on the parent's main axis rather than a percentage on the named axis.

```java
Panel panel = new Panel();
panel.getLayout().column().width(400).height(300).gap(8);
panel.getLayout().padding(12);
panel.markLayoutDirty(); // Uniform padding does not notify the callback.
Button button = new Button("Apply");
button.getLayout().fillWidth().height(36).noShrink();
panel.add(button);
```

<details>
<summary>Layout operation reference (211 declarations)</summary>

#### Constructor

```java
public Layout()
```

Creates default layout state with a no-op change callback. Mutations are retained
but do not themselves notify an owning node.

#### Constructor

```java
public Layout(Runnable onChange)
```

Creates default layout state with a retained synchronous change callback.
Construction does not invoke the callback. Mutators document any notification
exceptions, including uniform margin/padding assignment.

- **`onChange`** — non-null action used to invalidate the owning layout

**Throws `NullPointerException`:** if onChange is null

#### getWidth

```java
public LayoutValue getWidth()
```

Reads the stored preferred width without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current preferred width specification

#### width

```java
public Layout width(LayoutValue width)
```

Assigns the preferred width when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`width`** — non-null preferred width specification

**Returns:** this layout

**Throws `NullPointerException`:** if width is null

#### width

```java
public Layout width(float width)
```

Sets the preferred width in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`width`** — requested preferred width in points

**Returns:** this layout

#### widthPercent

```java
public Layout widthPercent(float width)
```

Sets the preferred width as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`width`** — requested percentage value

**Returns:** this layout

#### widthAuto

```java
public Layout widthAuto()
```

Restores automatic interpretation of the preferred width and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### fillWidth

```java
public Layout fillWidth()
```

Sets preferred width to 100 percent through widthPercent. Does not change
flex growth or height.

**Returns:** this layout

#### widthFill

```java
public Layout widthFill()
```

Sets flex growth to one without changing width. Growth follows the parent's
main axis, so this is not an alias for fillWidth.

**Returns:** this layout

#### getHeight

```java
public LayoutValue getHeight()
```

Reads the stored preferred height without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current preferred height specification

#### height

```java
public Layout height(LayoutValue height)
```

Assigns the preferred height when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`height`** — non-null preferred height specification

**Returns:** this layout

**Throws `NullPointerException`:** if height is null

#### height

```java
public Layout height(float height)
```

Sets the preferred height in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`height`** — requested preferred height in points

**Returns:** this layout

#### heightPercent

```java
public Layout heightPercent(float height)
```

Sets the preferred height as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`height`** — requested percentage value

**Returns:** this layout

#### heightAuto

```java
public Layout heightAuto()
```

Restores automatic interpretation of the preferred height and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### fillHeight

```java
public Layout fillHeight()
```

Sets preferred height to 100 percent through heightPercent. Does not change
flex growth or width.

**Returns:** this layout

#### heightFill

```java
public Layout heightFill()
```

Sets flex growth to one without changing height. Growth follows the parent's
main axis, so this is not an alias for fillHeight.

**Returns:** this layout

#### getMinWidth

```java
public LayoutValue getMinWidth()
```

Reads the stored minimum width constraint without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current minimum width constraint specification

#### minWidth

```java
public Layout minWidth(LayoutValue minWidth)
```

Assigns the minimum width constraint when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`minWidth`** — non-null minimum width constraint specification

**Returns:** this layout

**Throws `NullPointerException`:** if minWidth is null

#### minWidth

```java
public Layout minWidth(float minWidth)
```

Sets the minimum width constraint in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`minWidth`** — requested minimum width constraint in points

**Returns:** this layout

#### minWidthPercent

```java
public Layout minWidthPercent(float minWidth)
```

Sets the minimum width constraint as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`minWidth`** — requested percentage value

**Returns:** this layout

#### minWidthAuto

```java
public Layout minWidthAuto()
```

Restores automatic interpretation of the minimum width constraint and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getMinHeight

```java
public LayoutValue getMinHeight()
```

Reads the stored minimum height constraint without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current minimum height constraint specification

#### minHeight

```java
public Layout minHeight(LayoutValue minHeight)
```

Assigns the minimum height constraint when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`minHeight`** — non-null minimum height constraint specification

**Returns:** this layout

**Throws `NullPointerException`:** if minHeight is null

#### minHeight

```java
public Layout minHeight(float minHeight)
```

Sets the minimum height constraint in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`minHeight`** — requested minimum height constraint in points

**Returns:** this layout

#### minHeightPercent

```java
public Layout minHeightPercent(float minHeight)
```

Sets the minimum height constraint as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`minHeight`** — requested percentage value

**Returns:** this layout

#### minHeightAuto

```java
public Layout minHeightAuto()
```

Restores automatic interpretation of the minimum height constraint and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getMaxWidth

```java
public LayoutValue getMaxWidth()
```

Reads the stored maximum width constraint without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current maximum width constraint specification

#### maxWidth

```java
public Layout maxWidth(LayoutValue maxWidth)
```

Assigns the maximum width constraint when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`maxWidth`** — non-null maximum width constraint specification

**Returns:** this layout

**Throws `NullPointerException`:** if maxWidth is null

#### maxWidth

```java
public Layout maxWidth(float maxWidth)
```

Sets the maximum width constraint in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`maxWidth`** — requested maximum width constraint in points

**Returns:** this layout

#### maxWidthPercent

```java
public Layout maxWidthPercent(float maxWidth)
```

Sets the maximum width constraint as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`maxWidth`** — requested percentage value

**Returns:** this layout

#### maxWidthAuto

```java
public Layout maxWidthAuto()
```

Restores automatic interpretation of the maximum width constraint and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getMaxHeight

```java
public LayoutValue getMaxHeight()
```

Reads the stored maximum height constraint without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current maximum height constraint specification

#### maxHeight

```java
public Layout maxHeight(LayoutValue maxHeight)
```

Assigns the maximum height constraint when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`maxHeight`** — non-null maximum height constraint specification

**Returns:** this layout

**Throws `NullPointerException`:** if maxHeight is null

#### maxHeight

```java
public Layout maxHeight(float maxHeight)
```

Sets the maximum height constraint in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`maxHeight`** — requested maximum height constraint in points

**Returns:** this layout

#### maxHeightPercent

```java
public Layout maxHeightPercent(float maxHeight)
```

Sets the maximum height constraint as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`maxHeight`** — requested percentage value

**Returns:** this layout

#### maxHeightAuto

```java
public Layout maxHeightAuto()
```

Restores automatic interpretation of the maximum height constraint and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getLeft

```java
public LayoutValue getLeft()
```

Reads the stored left position offset without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current left position offset specification

#### left

```java
public Layout left(LayoutValue left)
```

Assigns the left position offset when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`left`** — non-null left position offset specification

**Returns:** this layout

**Throws `NullPointerException`:** if left is null

#### left

```java
public Layout left(float left)
```

Sets the left position offset in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`left`** — requested left position offset in points

**Returns:** this layout

#### leftPercent

```java
public Layout leftPercent(float left)
```

Sets the left position offset as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`left`** — requested percentage value

**Returns:** this layout

#### leftAuto

```java
public Layout leftAuto()
```

Restores automatic interpretation of the left position offset and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getTop

```java
public LayoutValue getTop()
```

Reads the stored top position offset without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current top position offset specification

#### top

```java
public Layout top(LayoutValue top)
```

Assigns the top position offset when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`top`** — non-null top position offset specification

**Returns:** this layout

**Throws `NullPointerException`:** if top is null

#### top

```java
public Layout top(float top)
```

Sets the top position offset in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`top`** — requested top position offset in points

**Returns:** this layout

#### topPercent

```java
public Layout topPercent(float top)
```

Sets the top position offset as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`top`** — requested percentage value

**Returns:** this layout

#### topAuto

```java
public Layout topAuto()
```

Restores automatic interpretation of the top position offset and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getRight

```java
public LayoutValue getRight()
```

Reads the stored right position offset without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current right position offset specification

#### right

```java
public Layout right(LayoutValue right)
```

Assigns the right position offset when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`right`** — non-null right position offset specification

**Returns:** this layout

**Throws `NullPointerException`:** if right is null

#### right

```java
public Layout right(float right)
```

Sets the right position offset in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`right`** — requested right position offset in points

**Returns:** this layout

#### rightPercent

```java
public Layout rightPercent(float right)
```

Sets the right position offset as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`right`** — requested percentage value

**Returns:** this layout

#### rightAuto

```java
public Layout rightAuto()
```

Restores automatic interpretation of the right position offset and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getBottom

```java
public LayoutValue getBottom()
```

Reads the stored bottom position offset without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current bottom position offset specification

#### bottom

```java
public Layout bottom(LayoutValue bottom)
```

Assigns the bottom position offset when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`bottom`** — non-null bottom position offset specification

**Returns:** this layout

**Throws `NullPointerException`:** if bottom is null

#### bottom

```java
public Layout bottom(float bottom)
```

Sets the bottom position offset in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`bottom`** — requested bottom position offset in points

**Returns:** this layout

#### bottomPercent

```java
public Layout bottomPercent(float bottom)
```

Sets the bottom position offset as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`bottom`** — requested percentage value

**Returns:** this layout

#### bottomAuto

```java
public Layout bottomAuto()
```

Restores automatic interpretation of the bottom position offset and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### inset

```java
public Layout inset(float inset)
```

Sets all four position offsets from one value.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`inset`** — position offsets value in UI points

**Returns:** this layout

#### inset

```java
public Layout inset(float horizontal, float vertical)
```

Sets all four position offsets from the supplied side values.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`horizontal`** — value for left and right in UI points
- **`vertical`** — value for top and bottom in UI points

**Returns:** this layout

#### inset

```java
public Layout inset(float left, float top, float right, float bottom)
```

Sets all four position offsets from the supplied side values.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`left`** — left side value in UI points
- **`top`** — top side value in UI points
- **`right`** — right side value in UI points
- **`bottom`** — bottom side value in UI points

**Returns:** this layout

#### insetPercent

```java
public Layout insetPercent(float inset)
```

Sets all four position offsets from one value.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`inset`** — position offsets value in percent (100 denotes the full reference extent)

**Returns:** this layout

#### insetPercent

```java
public Layout insetPercent(float horizontal, float vertical)
```

Sets all four position offsets from the supplied side values.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`horizontal`** — value for left and right in percent (100 denotes the full reference extent)
- **`vertical`** — value for top and bottom in percent (100 denotes the full reference extent)

**Returns:** this layout

#### size

```java
public Layout size(LayoutValue width, LayoutValue height)
```

Sets preferred width and height through their individual setters.
Values retain their independent units and immutable identities.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`width`** — non-null immutable specification
- **`height`** — non-null immutable specification

**Returns:** this layout

**Throws `NullPointerException`:** if a supplied LayoutValue is null

#### size

```java
public Layout size(float width, float height)
```

Sets preferred width and height through their individual setters.
Inputs use UI points with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`width`** — width in UI points
- **`height`** — height in UI points

**Returns:** this layout

#### sizePercent

```java
public Layout sizePercent(float width, float height)
```

Sets preferred width and height through their individual setters.
Inputs use percent (100 denotes the full reference extent) with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`width`** — width in percent (100 denotes the full reference extent)
- **`height`** — height in percent (100 denotes the full reference extent)

**Returns:** this layout

#### sizeAuto

```java
public Layout sizeAuto()
```

Restores automatic preferred width and height without altering minimum or
maximum constraints. Each changed dimension can notify separately.

**Returns:** this layout

#### minSize

```java
public Layout minSize(LayoutValue width, LayoutValue height)
```

Sets minimum width and height constraints through their individual setters.
Values retain their independent units and immutable identities.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`width`** — non-null immutable specification
- **`height`** — non-null immutable specification

**Returns:** this layout

**Throws `NullPointerException`:** if a supplied LayoutValue is null

#### minSize

```java
public Layout minSize(float width, float height)
```

Sets minimum width and height constraints through their individual setters.
Inputs use UI points with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`width`** — width in UI points
- **`height`** — height in UI points

**Returns:** this layout

#### minSizePercent

```java
public Layout minSizePercent(float width, float height)
```

Sets minimum width and height constraints through their individual setters.
Inputs use percent (100 denotes the full reference extent) with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`width`** — width in percent (100 denotes the full reference extent)
- **`height`** — height in percent (100 denotes the full reference extent)

**Returns:** this layout

#### maxSize

```java
public Layout maxSize(LayoutValue width, LayoutValue height)
```

Sets maximum width and height constraints through their individual setters.
Values retain their independent units and immutable identities.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`width`** — non-null immutable specification
- **`height`** — non-null immutable specification

**Returns:** this layout

**Throws `NullPointerException`:** if a supplied LayoutValue is null

#### maxSize

```java
public Layout maxSize(float width, float height)
```

Sets maximum width and height constraints through their individual setters.
Inputs use UI points with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`width`** — width in UI points
- **`height`** — height in UI points

**Returns:** this layout

#### maxSizePercent

```java
public Layout maxSizePercent(float width, float height)
```

Sets maximum width and height constraints through their individual setters.
Inputs use percent (100 denotes the full reference extent) with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`width`** — width in percent (100 denotes the full reference extent)
- **`height`** — height in percent (100 denotes the full reference extent)

**Returns:** this layout

#### widthRange

```java
public Layout widthRange(float minWidth, float maxWidth)
```

Sets minimum and maximum width constraints through their individual setters.
Inputs use UI points with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`minWidth`** — min width in UI points
- **`maxWidth`** — max width in UI points

**Returns:** this layout

#### heightRange

```java
public Layout heightRange(float minHeight, float maxHeight)
```

Sets minimum and maximum height constraints through their individual setters.
Inputs use UI points with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`minHeight`** — min height in UI points
- **`maxHeight`** — max height in UI points

**Returns:** this layout

#### position

```java
public Layout position(float left, float top)
```

Sets left and top position offsets through their individual setters.
Inputs use UI points with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`left`** — left in UI points
- **`top`** — top in UI points

**Returns:** this layout

#### positionPercent

```java
public Layout positionPercent(float left, float top)
```

Sets left and top position offsets through their individual setters.
Inputs use percent (100 denotes the full reference extent) with no numeric validation or range ordering checks.
Each changed component can notify separately; an exception after the first
assignment does not roll it back. Unrelated layout properties are retained.

- **`left`** — left in percent (100 denotes the full reference extent)
- **`top`** — top in percent (100 denotes the full reference extent)

**Returns:** this layout

#### edgesAuto

```java
public Layout edgesAuto()
```

Restores all four position offsets to auto without changing positioning mode.
Each changed offset can invoke the callback separately.

**Returns:** this layout

#### getMarginLeft

```java
public LayoutValue getMarginLeft()
```

Reads the stored left outer margin without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current left outer margin specification

#### marginLeft

```java
public Layout marginLeft(LayoutValue marginLeft)
```

Assigns the left outer margin when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`marginLeft`** — non-null left outer margin specification

**Returns:** this layout

**Throws `NullPointerException`:** if marginLeft is null

#### marginLeft

```java
public Layout marginLeft(float marginLeft)
```

Sets the left outer margin in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`marginLeft`** — requested left outer margin in points

**Returns:** this layout

#### marginLeftPercent

```java
public Layout marginLeftPercent(float marginLeft)
```

Sets the left outer margin as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`marginLeft`** — requested percentage value

**Returns:** this layout

#### marginLeftAuto

```java
public Layout marginLeftAuto()
```

Restores automatic interpretation of the left outer margin and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getMarginTop

```java
public LayoutValue getMarginTop()
```

Reads the stored top outer margin without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current top outer margin specification

#### marginTop

```java
public Layout marginTop(LayoutValue marginTop)
```

Assigns the top outer margin when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`marginTop`** — non-null top outer margin specification

**Returns:** this layout

**Throws `NullPointerException`:** if marginTop is null

#### marginTop

```java
public Layout marginTop(float marginTop)
```

Sets the top outer margin in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`marginTop`** — requested top outer margin in points

**Returns:** this layout

#### marginTopPercent

```java
public Layout marginTopPercent(float marginTop)
```

Sets the top outer margin as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`marginTop`** — requested percentage value

**Returns:** this layout

#### marginTopAuto

```java
public Layout marginTopAuto()
```

Restores automatic interpretation of the top outer margin and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getMarginRight

```java
public LayoutValue getMarginRight()
```

Reads the stored right outer margin without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current right outer margin specification

#### marginRight

```java
public Layout marginRight(LayoutValue marginRight)
```

Assigns the right outer margin when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`marginRight`** — non-null right outer margin specification

**Returns:** this layout

**Throws `NullPointerException`:** if marginRight is null

#### marginRight

```java
public Layout marginRight(float marginRight)
```

Sets the right outer margin in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`marginRight`** — requested right outer margin in points

**Returns:** this layout

#### marginRightPercent

```java
public Layout marginRightPercent(float marginRight)
```

Sets the right outer margin as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`marginRight`** — requested percentage value

**Returns:** this layout

#### marginRightAuto

```java
public Layout marginRightAuto()
```

Restores automatic interpretation of the right outer margin and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### getMarginBottom

```java
public LayoutValue getMarginBottom()
```

Reads the stored bottom outer margin without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current bottom outer margin specification

#### marginBottom

```java
public Layout marginBottom(LayoutValue marginBottom)
```

Assigns the bottom outer margin when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`marginBottom`** — non-null bottom outer margin specification

**Returns:** this layout

**Throws `NullPointerException`:** if marginBottom is null

#### marginBottom

```java
public Layout marginBottom(float marginBottom)
```

Sets the bottom outer margin in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`marginBottom`** — requested bottom outer margin in points

**Returns:** this layout

#### marginBottomPercent

```java
public Layout marginBottomPercent(float marginBottom)
```

Sets the bottom outer margin as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`marginBottom`** — requested percentage value

**Returns:** this layout

#### marginBottomAuto

```java
public Layout marginBottomAuto()
```

Restores automatic interpretation of the bottom outer margin and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### margin

```java
public Layout margin(LayoutValue margin)
```

Sets all four outer margins from one value.
Retains the immutable unit-bearing value without resolving it.
Assigns all sides directly without invoking the change callback; explicitly
invalidate the owning node if this change requires a new layout.

- **`margin`** — non-null immutable specification

**Returns:** this layout

**Throws `NullPointerException`:** if a supplied LayoutValue is null

#### margin

```java
public Layout margin(float margin)
```

Sets all four outer margins from one value.
Inputs use UI points and are retained without validation.
Assigns all sides directly without invoking the change callback; explicitly
invalidate the owning node if this change requires a new layout.

- **`margin`** — outer margins value in UI points

**Returns:** this layout

#### marginPercent

```java
public Layout marginPercent(float margin)
```

Sets all four outer margins from one value.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Assigns all sides directly without invoking the change callback; explicitly
invalidate the owning node if this change requires a new layout.

- **`margin`** — outer margins value in percent (100 denotes the full reference extent)

**Returns:** this layout

#### margin

```java
public Layout margin(float horizontal, float vertical)
```

Sets all four outer margins from the supplied side values.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`horizontal`** — value for left and right in UI points
- **`vertical`** — value for top and bottom in UI points

**Returns:** this layout

#### margin

```java
public Layout margin(float left, float top, float right, float bottom)
```

Sets all four outer margins from the supplied side values.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`left`** — left side value in UI points
- **`top`** — top side value in UI points
- **`right`** — right side value in UI points
- **`bottom`** — bottom side value in UI points

**Returns:** this layout

#### marginPercent

```java
public Layout marginPercent(float horizontal, float vertical)
```

Sets all four outer margins from the supplied side values.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`horizontal`** — value for left and right in percent (100 denotes the full reference extent)
- **`vertical`** — value for top and bottom in percent (100 denotes the full reference extent)

**Returns:** this layout

#### marginPercent

```java
public Layout marginPercent(float left, float top, float right, float bottom)
```

Sets all four outer margins from the supplied side values.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`left`** — left side value in percent (100 denotes the full reference extent)
- **`top`** — top side value in percent (100 denotes the full reference extent)
- **`right`** — right side value in percent (100 denotes the full reference extent)
- **`bottom`** — bottom side value in percent (100 denotes the full reference extent)

**Returns:** this layout

#### marginHorizontal

```java
public Layout marginHorizontal(float margin)
```

Sets left and right outer margins from one value.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`margin`** — outer margins value in UI points

**Returns:** this layout

#### marginVertical

```java
public Layout marginVertical(float margin)
```

Sets top and bottom outer margins from one value.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`margin`** — outer margins value in UI points

**Returns:** this layout

#### marginHorizontalPercent

```java
public Layout marginHorizontalPercent(float margin)
```

Sets left and right outer margins from one value.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`margin`** — outer margins value in percent (100 denotes the full reference extent)

**Returns:** this layout

#### marginVerticalPercent

```java
public Layout marginVerticalPercent(float margin)
```

Sets top and bottom outer margins from one value.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`margin`** — outer margins value in percent (100 denotes the full reference extent)

**Returns:** this layout

#### marginAuto

```java
public Layout marginAuto()
```

Sets all four outer margins to auto through their individual notifying setters.
Solver support determines automatic margin distribution.

**Returns:** this layout

#### marginHorizontalAuto

```java
public Layout marginHorizontalAuto()
```

Sets left and right margins to auto, preserving top and bottom margins.
Each changed side can notify separately.

**Returns:** this layout

#### marginVerticalAuto

```java
public Layout marginVerticalAuto()
```

Sets top and bottom margins to auto, preserving left and right margins.
Each changed side can notify separately.

**Returns:** this layout

#### getPaddingLeft

```java
public LayoutValue getPaddingLeft()
```

Reads the stored left inner padding without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current left inner padding specification

#### paddingLeft

```java
public Layout paddingLeft(LayoutValue paddingLeft)
```

Assigns the left inner padding when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`paddingLeft`** — non-null left inner padding specification

**Returns:** this layout

**Throws `NullPointerException`:** if paddingLeft is null

#### paddingLeft

```java
public Layout paddingLeft(float paddingLeft)
```

Sets the left inner padding in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`paddingLeft`** — requested left inner padding in points

**Returns:** this layout

#### paddingLeftPercent

```java
public Layout paddingLeftPercent(float paddingLeft)
```

Sets the left inner padding as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`paddingLeft`** — requested percentage value

**Returns:** this layout

#### getPaddingTop

```java
public LayoutValue getPaddingTop()
```

Reads the stored top inner padding without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current top inner padding specification

#### paddingTop

```java
public Layout paddingTop(LayoutValue paddingTop)
```

Assigns the top inner padding when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`paddingTop`** — non-null top inner padding specification

**Returns:** this layout

**Throws `NullPointerException`:** if paddingTop is null

#### paddingTop

```java
public Layout paddingTop(float paddingTop)
```

Sets the top inner padding in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`paddingTop`** — requested top inner padding in points

**Returns:** this layout

#### paddingTopPercent

```java
public Layout paddingTopPercent(float paddingTop)
```

Sets the top inner padding as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`paddingTop`** — requested percentage value

**Returns:** this layout

#### getPaddingRight

```java
public LayoutValue getPaddingRight()
```

Reads the stored right inner padding without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current right inner padding specification

#### paddingRight

```java
public Layout paddingRight(LayoutValue paddingRight)
```

Assigns the right inner padding when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`paddingRight`** — non-null right inner padding specification

**Returns:** this layout

**Throws `NullPointerException`:** if paddingRight is null

#### paddingRight

```java
public Layout paddingRight(float paddingRight)
```

Sets the right inner padding in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`paddingRight`** — requested right inner padding in points

**Returns:** this layout

#### paddingRightPercent

```java
public Layout paddingRightPercent(float paddingRight)
```

Sets the right inner padding as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`paddingRight`** — requested percentage value

**Returns:** this layout

#### getPaddingBottom

```java
public LayoutValue getPaddingBottom()
```

Reads the stored bottom inner padding without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current bottom inner padding specification

#### paddingBottom

```java
public Layout paddingBottom(LayoutValue paddingBottom)
```

Assigns the bottom inner padding when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`paddingBottom`** — non-null bottom inner padding specification

**Returns:** this layout

**Throws `NullPointerException`:** if paddingBottom is null

#### paddingBottom

```java
public Layout paddingBottom(float paddingBottom)
```

Sets the bottom inner padding in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`paddingBottom`** — requested bottom inner padding in points

**Returns:** this layout

#### paddingBottomPercent

```java
public Layout paddingBottomPercent(float paddingBottom)
```

Sets the bottom inner padding as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`paddingBottom`** — requested percentage value

**Returns:** this layout

#### padding

```java
public Layout padding(LayoutValue padding)
```

Sets all four inner padding from one value.
Retains the immutable unit-bearing value without resolving it.
Assigns all sides directly without invoking the change callback; explicitly
invalidate the owning node if this change requires a new layout.

- **`padding`** — non-null immutable specification

**Returns:** this layout

**Throws `NullPointerException`:** if a supplied LayoutValue is null

#### padding

```java
public Layout padding(float padding)
```

Sets all four inner padding from one value.
Inputs use UI points and are retained without validation.
Assigns all sides directly without invoking the change callback; explicitly
invalidate the owning node if this change requires a new layout.

- **`padding`** — inner padding value in UI points

**Returns:** this layout

#### paddingPercent

```java
public Layout paddingPercent(float padding)
```

Sets all four inner padding from one value.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Assigns all sides directly without invoking the change callback; explicitly
invalidate the owning node if this change requires a new layout.

- **`padding`** — inner padding value in percent (100 denotes the full reference extent)

**Returns:** this layout

#### padding

```java
public Layout padding(float horizontal, float vertical)
```

Sets all four inner padding from the supplied side values.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`horizontal`** — value for left and right in UI points
- **`vertical`** — value for top and bottom in UI points

**Returns:** this layout

#### padding

```java
public Layout padding(float left, float top, float right, float bottom)
```

Sets all four inner padding from the supplied side values.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`left`** — left side value in UI points
- **`top`** — top side value in UI points
- **`right`** — right side value in UI points
- **`bottom`** — bottom side value in UI points

**Returns:** this layout

#### paddingPercent

```java
public Layout paddingPercent(float horizontal, float vertical)
```

Sets all four inner padding from the supplied side values.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`horizontal`** — value for left and right in percent (100 denotes the full reference extent)
- **`vertical`** — value for top and bottom in percent (100 denotes the full reference extent)

**Returns:** this layout

#### paddingPercent

```java
public Layout paddingPercent(float left, float top, float right, float bottom)
```

Sets all four inner padding from the supplied side values.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`left`** — left side value in percent (100 denotes the full reference extent)
- **`top`** — top side value in percent (100 denotes the full reference extent)
- **`right`** — right side value in percent (100 denotes the full reference extent)
- **`bottom`** — bottom side value in percent (100 denotes the full reference extent)

**Returns:** this layout

#### paddingHorizontal

```java
public Layout paddingHorizontal(float padding)
```

Sets left and right inner padding from one value.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`padding`** — inner padding value in UI points

**Returns:** this layout

#### paddingVertical

```java
public Layout paddingVertical(float padding)
```

Sets top and bottom inner padding from one value.
Inputs use UI points and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`padding`** — inner padding value in UI points

**Returns:** this layout

#### paddingHorizontalPercent

```java
public Layout paddingHorizontalPercent(float padding)
```

Sets left and right inner padding from one value.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`padding`** — inner padding value in percent (100 denotes the full reference extent)

**Returns:** this layout

#### paddingVerticalPercent

```java
public Layout paddingVerticalPercent(float padding)
```

Sets top and bottom inner padding from one value.
Inputs use percent (100 denotes the full reference extent) and are retained without validation.
Delegates to individual side setters, so changed sides can notify separately.
Other properties are retained and callback failure does not roll back prior sides.

- **`padding`** — inner padding value in percent (100 denotes the full reference extent)

**Returns:** this layout

#### getFlexGrow

```java
public float getFlexGrow()
```

Reads the configured relative share of positive main-axis free space without running layout or notifying.

**Returns:** current flex grow

#### flexGrow

```java
public Layout flexGrow(float flexGrow)
```

Sets the relative share of positive main-axis free space and notifies only on an actual stored change.
The input is retained without clamping or finiteness validation.

- **`flexGrow`** — requested flex grow

**Returns:** this layout

#### grow

```java
public Layout grow()
```

Sets flex growth to one, allowing participation in positive main-axis space.
Notifies only if the stored grow factor changes.

**Returns:** this layout

#### grow

```java
public Layout grow(float amount)
```

Sets the positive-space growth factor and notifies only when Float.compare differs.
Values are stored unchecked; the layout solver interprets the factor.

- **`amount`** — requested factor

**Returns:** this layout

#### noGrow

```java
public Layout noGrow()
```

Sets flex growth to zero, preventing positive-space distribution to this item.
Notifies only if the factor changes.

**Returns:** this layout

#### getFlexShrink

```java
public float getFlexShrink()
```

Reads the configured relative shrink factor when main-axis space is insufficient without running layout or notifying.

**Returns:** current flex shrink

#### flexShrink

```java
public Layout flexShrink(float flexShrink)
```

Sets the relative shrink factor when main-axis space is insufficient and notifies only on an actual stored change.
The input is retained without clamping or finiteness validation.

- **`flexShrink`** — requested flex shrink

**Returns:** this layout

#### shrink

```java
public Layout shrink()
```

Sets flex shrink to one for deficit-space distribution along the main axis.
Notifies only if the stored shrink factor changes.

**Returns:** this layout

#### shrink

```java
public Layout shrink(float amount)
```

Sets the deficit-space shrink factor and notifies only when Float.compare differs.
Values are stored unchecked; the layout solver interprets the factor.

- **`amount`** — requested factor

**Returns:** this layout

#### noShrink

```java
public Layout noShrink()
```

Sets flex shrink to zero without changing preferred or minimum dimensions.
Notifies only if the factor changes.

**Returns:** this layout

#### getFlexBasis

```java
public LayoutValue getFlexBasis()
```

Reads the stored initial main-axis size before flexible space distribution without resolving auto or percentage units.
The immutable value may be shared; this call does not trigger layout.

**Returns:** current initial main-axis size before flexible space distribution specification

#### flexBasis

```java
public Layout flexBasis(LayoutValue flexBasis)
```

Assigns the initial main-axis size before flexible space distribution when the immutable value differs, then notifies
the change callback. Units and numeric payload are retained without conversion.

- **`flexBasis`** — non-null initial main-axis size before flexible space distribution specification

**Returns:** this layout

**Throws `NullPointerException`:** if flexBasis is null

#### flexBasis

```java
public Layout flexBasis(float flexBasis)
```

Sets the initial main-axis size before flexible space distribution in fixed UI points, notifying only when its unit
or Float.compare value differs. No numeric range or finiteness checks occur.

- **`flexBasis`** — requested initial main-axis size before flexible space distribution in points

**Returns:** this layout

#### flexBasisPercent

```java
public Layout flexBasisPercent(float flexBasis)
```

Sets the initial main-axis size before flexible space distribution as a percentage, using 100 for the full solver-selected
reference extent. Notifies only when unit/value changes; input is not clamped.

- **`flexBasis`** — requested percentage value

**Returns:** this layout

#### flexBasisAuto

```java
public Layout flexBasisAuto()
```

Restores automatic interpretation of the initial main-axis size before flexible space distribution and notifies only
when that specification changes. The solver determines what auto means for
this property; no geometry is calculated here.

**Returns:** this layout

#### fill

```java
public Layout fill()
```

Sets grow and shrink to one and restores auto flex basis. Preferred dimensions
are retained; each changed property can notify separately.

**Returns:** this layout

#### fillX

```java
public Layout fillX()
```

Enables growth with factor one and restores preferred width to auto.
The parent still determines the growth axis; height is retained.

**Returns:** this layout

#### fillY

```java
public Layout fillY()
```

Enables growth with factor one and restores preferred height to auto.
The parent still determines the growth axis; width is retained.

**Returns:** this layout

#### fit

```java
public Layout fit()
```

Disables growth and shrink and restores automatic width and height. Minimum,
maximum, and flex-basis settings remain unchanged.

**Returns:** this layout

#### getFlexDirection

```java
public FlexDirection getFlexDirection()
```

Reads the configured main-axis direction used to arrange children without running layout or notifying.

**Returns:** current flex direction

#### flexDirection

```java
public Layout flexDirection(FlexDirection flexDirection)
```

Sets the main-axis direction used to arrange children and notifies only on an actual stored change.
The enum is required; unsupported combinations are interpreted by the solver.

- **`flexDirection`** — requested flex direction

**Returns:** this layout

**Throws `NullPointerException`:** if flexDirection is null

#### row

```java
public Layout row()
```

Selects normal row direction for children and notifies only on change.
Does not reorder the child collection or enable wrapping.

**Returns:** this layout

#### column

```java
public Layout column()
```

Selects normal column direction for children and notifies only on change.
Does not reorder the child collection or alter alignment.

**Returns:** this layout

#### rowReverse

```java
public Layout rowReverse()
```

Selects reversed row layout and notifies only on change. The underlying child
collection retains its order; reversal is a solver configuration.

**Returns:** this layout

#### columnReverse

```java
public Layout columnReverse()
```

Selects reversed column layout and notifies only on change. The underlying
child collection retains its order.

**Returns:** this layout

#### centerContent

```java
public Layout centerContent()
```

Sets main-axis justification and cross-axis item alignment to center.
Each changed policy can notify separately; this item's self alignment is retained.

**Returns:** this layout

#### getJustifyContent

```java
public JustifyContent getJustifyContent()
```

Reads the configured child distribution along the main axis without running layout or notifying.

**Returns:** current justify content

#### justifyContent

```java
public Layout justifyContent(JustifyContent justifyContent)
```

Sets the child distribution along the main axis and notifies only on an actual stored change.
The enum is required; unsupported combinations are interpreted by the solver.

- **`justifyContent`** — requested justify content

**Returns:** this layout

**Throws `NullPointerException`:** if justifyContent is null

#### justifyStart

```java
public Layout justifyStart()
```

Configures main-axis child distribution to pack at the main-axis start.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### justifyCenter

```java
public Layout justifyCenter()
```

Configures main-axis child distribution to center along the main axis.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### justifyEnd

```java
public Layout justifyEnd()
```

Configures main-axis child distribution to pack at the main-axis end.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### justifyBetween

```java
public Layout justifyBetween()
```

Configures main-axis child distribution to distribute free space between items.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### justifyAround

```java
public Layout justifyAround()
```

Configures main-axis child distribution to distribute space around items.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### justifyEvenly

```java
public Layout justifyEvenly()
```

Configures main-axis child distribution to distribute equal gaps including outer edges.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### getAlignItems

```java
public Align getAlignItems()
```

Reads the configured default child alignment across the main axis without running layout or notifying.

**Returns:** current align items

#### alignItems

```java
public Layout alignItems(Align alignItems)
```

Sets the default child alignment across the main axis and notifies only on an actual stored change.
The enum is required; unsupported combinations are interpreted by the solver.

- **`alignItems`** — requested align items

**Returns:** this layout

**Throws `NullPointerException`:** if alignItems is null

#### itemsAuto

```java
public Layout itemsAuto()
```

Configures default cross-axis child alignment to use the solver's automatic alignment policy.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### itemsStart

```java
public Layout itemsStart()
```

Configures default cross-axis child alignment to align at cross-axis start.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### itemsCenter

```java
public Layout itemsCenter()
```

Configures default cross-axis child alignment to center across the main axis.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### itemsEnd

```java
public Layout itemsEnd()
```

Configures default cross-axis child alignment to align at cross-axis end.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### itemsStretch

```java
public Layout itemsStretch()
```

Configures default cross-axis child alignment to stretch eligible auto-sized cross dimensions.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### itemsBaseline

```java
public Layout itemsBaseline()
```

Configures default cross-axis child alignment to request baseline alignment.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### itemsSpaceBetween

```java
public Layout itemsSpaceBetween()
```

Configures default cross-axis child alignment to request SPACE_BETWEEN alignment where supported by the solver.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### itemsSpaceAround

```java
public Layout itemsSpaceAround()
```

Configures default cross-axis child alignment to request SPACE_AROUND alignment where supported by the solver.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### getAlignSelf

```java
public Align getAlignSelf()
```

Reads the configured this item's cross-axis alignment override without running layout or notifying.

**Returns:** current align self

#### alignSelf

```java
public Layout alignSelf(Align alignSelf)
```

Sets the this item's cross-axis alignment override and notifies only on an actual stored change.
The enum is required; unsupported combinations are interpreted by the solver.

- **`alignSelf`** — requested align self

**Returns:** this layout

**Throws `NullPointerException`:** if alignSelf is null

#### selfAuto

```java
public Layout selfAuto()
```

Configures this item's cross-axis alignment to use the solver's automatic alignment policy.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### selfStart

```java
public Layout selfStart()
```

Configures this item's cross-axis alignment to align at cross-axis start.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### selfCenter

```java
public Layout selfCenter()
```

Configures this item's cross-axis alignment to center across the main axis.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### selfEnd

```java
public Layout selfEnd()
```

Configures this item's cross-axis alignment to align at cross-axis end.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### selfStretch

```java
public Layout selfStretch()
```

Configures this item's cross-axis alignment to stretch eligible auto-sized cross dimensions.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### selfBaseline

```java
public Layout selfBaseline()
```

Configures this item's cross-axis alignment to request baseline alignment.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### selfSpaceBetween

```java
public Layout selfSpaceBetween()
```

Configures this item's cross-axis alignment to request SPACE_BETWEEN alignment where supported by the solver.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### selfSpaceAround

```java
public Layout selfSpaceAround()
```

Configures this item's cross-axis alignment to request SPACE_AROUND alignment where supported by the solver.
Notifies only if the stored enum changes; other axis policies are retained.

**Returns:** this layout

#### getPositionType

```java
public PositionType getPositionType()
```

Reads the configured relative-flow or absolute positioning mode without running layout or notifying.

**Returns:** current position type

#### positionType

```java
public Layout positionType(PositionType positionType)
```

Sets the relative-flow or absolute positioning mode and notifies only on an actual stored change.
The enum is required; unsupported combinations are interpreted by the solver.

- **`positionType`** — requested position type

**Returns:** this layout

**Throws `NullPointerException`:** if positionType is null

#### relative

```java
public Layout relative()
```

Selects relative positioning through the notifying mode setter, retaining offsets.
The item participates in normal layout flow under the solver's rules.

**Returns:** this layout

#### absolute

```java
public Layout absolute()
```

Selects absolute positioning through the notifying mode setter, retaining offsets.
The solver positions the item outside normal sibling flow.

**Returns:** this layout

#### getFlexWrap

```java
public FlexWrap getFlexWrap()
```

Reads the configured child line-wrapping policy without running layout or notifying.

**Returns:** current flex wrap

#### flexWrap

```java
public Layout flexWrap(FlexWrap flexWrap)
```

Sets the child line-wrapping policy and notifies only on an actual stored change.
The enum is required; unsupported combinations are interpreted by the solver.

- **`flexWrap`** — requested flex wrap

**Returns:** this layout

**Throws `NullPointerException`:** if flexWrap is null

#### wrap

```java
public Layout wrap()
```

Enables normal child line wrapping and notifies only if the policy changes.
The available main-axis space determines actual line breaks.

**Returns:** this layout

#### noWrap

```java
public Layout noWrap()
```

Disables child line wrapping and notifies only if the policy changes.
Size constraints and shrink factors remain unchanged.

**Returns:** this layout

#### wrapReverse

```java
public Layout wrapReverse()
```

Selects reversed wrapping-line placement and notifies only on change.
The flex direction and child collection order are retained.

**Returns:** this layout

#### getRowGap

```java
public float getRowGap()
```

Reads the configured gap between layout rows in UI units without running layout or notifying.

**Returns:** current row gap

#### rowGap

```java
public Layout rowGap(float rowGap)
```

Sets the gap between layout rows in UI units and notifies only on an actual stored change.
The input is retained without clamping or finiteness validation.

- **`rowGap`** — requested row gap

**Returns:** this layout

#### getColumnGap

```java
public float getColumnGap()
```

Reads the configured gap between layout columns in UI units without running layout or notifying.

**Returns:** current column gap

#### columnGap

```java
public Layout columnGap(float columnGap)
```

Sets the gap between layout columns in UI units and notifies only on an actual stored change.
The input is retained without clamping or finiteness validation.

- **`columnGap`** — requested column gap

**Returns:** this layout

#### gap

```java
public Layout gap(float gap)
```

Sets both row and column gaps in UI points. Each changed gap invokes the callback
separately after its assignment; the operation is not a single notification.

- **`gap`** — requested spacing, retained without validation

**Returns:** this layout

#### resetSize

```java
public Layout resetSize()
```

Restores preferred, minimum, and maximum width/height specifications to auto.
Each changed constraint can notify separately; spacing and flex settings remain.

**Returns:** this layout

#### resetPosition

```java
public Layout resetPosition()
```

Restores all position offsets to auto without resetting relative/absolute mode.
Changed edges notify through their setters.

**Returns:** this layout

#### resetMargin

```java
public Layout resetMargin()
```

Assigns zero-point margins on all sides through the uniform margin helper.
This path does not invoke the change callback.

**Returns:** this layout

#### resetPadding

```java
public Layout resetPadding()
```

Assigns zero-point padding on all sides through the uniform padding helper.
This path does not invoke the change callback.

**Returns:** this layout

#### resetFlex

```java
public Layout resetFlex()
```

Restores zero grow/shrink, auto basis, column direction, start justification,
stretched items, auto self alignment, relative positioning, no wrap, and zero
gaps. Changed properties notify separately; dimensions and spacing are retained.

**Returns:** this layout

#### reset

```java
public Layout reset()
```

Runs size, position, margin, padding, and flex resets in sequence. Callback
notifications are not coalesced; uniform margin/padding resets themselves do
not notify, so a spacing-only reset may require explicit invalidation.

**Returns:** this layout

</details>

<a id="type-layoutvalue"></a>

### LayoutValue

[Source](../../src/main/java/valthorne/ui/LayoutValue.java#L26)

Represents a value used for layout calculations. The value is expressed in
a specific unit, which can be either auto, points, or percent.

Instances are immutable and may be safely shared between layout configurations.
Point values express fixed layout units, while percentages use 100 for the full
reference extent, not 1. The layout property and solver determine the reference
size and the interpretation of auto; this value object performs no resolution.

Factories retain numeric inputs without range or finiteness validation.
Negative values, infinities and NaN can therefore exist in point or percentage
values, and callers must honor the receiving property's contract. Auto stores
NaN as its numeric payload and is identified by its unit, not by that payload.

Auto, positive zero points and 100 percent reuse cached instances. Other
factory calls allocate values. Equality compares the unit and Float.compare
semantics: signed zeros differ, while NaN payloads compare equal. Use value
equality rather than object identity for layout-change detection.

<details>
<summary>LayoutValue operation reference (10 declarations)</summary>

#### auto

```java
public static LayoutValue auto()
```

Returns the predefined "auto" layout value.
The "auto" value indicates that the layout should automatically determine its size
or position based on the context or parent constraints.

**Returns:** a `LayoutValue` instance representing the "auto" layout value.

#### points

```java
public static LayoutValue points(float value)
```

Creates a `LayoutValue` instance representing a measurement in points.
The "points" unit is typically used for specifying fixed sizes or positions
in layout calculations.
Positive zero reuses a shared instance; negative zero and every other value
create a new instance. No range or finiteness checks are performed, and the
numeric value is retained exactly as supplied.

- **`value`** — the measurement value in points.

**Returns:** a `LayoutValue` instance with the unit set to `LayoutUnit.POINT`
and the specified value.

#### percent

```java
public static LayoutValue percent(float value)
```

Creates a `LayoutValue` instance representing a measurement in percent.
The "percent" unit is typically used for specifying relative sizes
or positions in layout calculations based on a reference value or context.
Supply 100 for the full reference extent. Exactly 100 reuses a shared
instance; all other values allocate a new one. Inputs are not clamped to
0-100 or checked for finiteness.

- **`value`** — the percentage measurement value.

**Returns:** a `LayoutValue` instance with the unit set to `LayoutUnit.PERCENT`
and the specified value.

#### equals

```java
    public boolean equals(Object other)
```

Compares unit and numeric value without converting between units. NaN values
compare equal within the same unit, while positive and negative zero differ.
Null and objects of other types do not match.

- **`other`** — the candidate value

**Returns:** whether both layout unit and float value match

#### hashCode

```java
    public int hashCode()
```

Combines the unit hash with the float hash, preserving the signed-zero and
canonical-NaN distinctions used by equals. The result remains stable for
this immutable instance and is intended for in-process collections.

**Returns:** the hash consistent with value equality

#### getUnit

```java
public LayoutUnit getUnit()
```

Retrieves the unit of measurement associated with this layout value.
The unit indicates whether the value is expressed in points, percent, or is set to 'auto'.

**Returns:** the `LayoutUnit` representing the unit of this layout value.

#### getValue

```java
public float getValue()
```

Retrieves the numeric value of this layout value. The meaning of the value
depends on the associated unit of measurement (`LayoutUnit`).
Auto returns NaN. This accessor returns the stored payload without resolving
percentages against a parent or converting point values into screen pixels.

**Returns:** the numeric value associated with this layout value.

#### isAuto

```java
public boolean isAuto()
```

Determines if this layout value has the predefined "auto" unit.
The "auto" unit indicates that the layout should automatically calculate
its size or position based on contextual or parent constraints.

**Returns:** `true` if the layout value is set to `LayoutUnit.AUTO`,
otherwise `false`.

#### isPoints

```java
public boolean isPoints()
```

Determines if this layout value is measured in points.
The "points" unit is typically used for specifying fixed sizes or positions
in layout calculations.

**Returns:** `true` if the layout value is set to `LayoutUnit.POINT`,
otherwise `false`.

#### isPercent

```java
public boolean isPercent()
```

Determines if the layout value is measured in percent.
The "percent" unit is generally used to define relative sizes or positions
based on a reference value or context.

**Returns:** `true` if the layout value is set to `LayoutUnit.PERCENT`,
otherwise `false`.

</details>

<a id="type-locatable"></a>

### Locatable

[Source](../../src/main/java/valthorne/math/geometry/Locatable.java#L12)

The Locatable interface provides a contract for classes that represent objects with
a specific position in a two-dimensional coordinate system.
Implementing classes are required to provide methods to retrieve the x and y
coordinates of the object.

<details>
<summary>Locatable operation reference (5 declarations)</summary>

#### getX

```java
float getX()
```

Retrieves the x-coordinate of the object in a two-dimensional space.

**Returns:** the x-coordinate as a float.

#### setX

```java
void setX(float x)
```

Sets the x-coordinate of an object in a two-dimensional space.

- **`x`** — the x-coordinate to set for the object.

#### getY

```java
float getY()
```

Retrieves the y-coordinate of the object in a two-dimensional space.

**Returns:** the y-coordinate as a float.

#### setY

```java
void setY(float y)
```

Sets the y-coordinate of the object in a two-dimensional space.

- **`y`** — the y-coordinate to set for the object.

#### setPosition

```java
void setPosition(float x, float y)
```

Sets the position of the object within a two-dimensional coordinate system.

- **`x`** — the x-coordinate to set for the object.
- **`y`** — the y-coordinate to set for the object.

</details>

<a id="type-sizeable"></a>

### Sizeable

[Source](../../src/main/java/valthorne/math/geometry/Sizeable.java#L16)

The Sizeable interface defines a contract for objects that have width and height properties.
Implementing classes are expected to provide mechanisms to retrieve their dimensions.

Methods in this interface are typically used for layout constraints, size calculations,
or bounding operations on objects that have spatial characteristics.

It is designed to be implemented by classes that require width and height attributes
to define their size properties.

<details>
<summary>Sizeable operation reference (5 declarations)</summary>

#### getWidth

```java
float getWidth()
```

Retrieves the width of the object implementing this method.

**Returns:** the width of the object as a float value.

#### setWidth

```java
void setWidth(float width)
```

Sets the width of the object.
This method adjusts the width property, which might be used in layout calculations or spatial transformations.

- **`width`** — the new width value for the object, specified as a float. It represents the horizontal dimension.

#### getHeight

```java
float getHeight()
```

Retrieves the height of the object implementing this method.

**Returns:** the height of the object as a float value.

#### setHeight

```java
void setHeight(float height)
```

Sets the height of the object.
This method adjusts the height property, which might be used in layout calculations or spatial transformations.

- **`height`** — the new height value for the object, specified as a float. It represents the vertical dimension.

#### setSize

```java
void setSize(float width, float height)
```

Sets the size of the object by specifying its width and height.
This method adjusts both the width and height properties to the provided values.
It is typically used to define or update the spatial dimensions of the object.

- **`width`** — the new width value for the object, specified as a float. It represents the horizontal dimension.
- **`height`** — the new height value for the object, specified as a float. It represents the vertical dimension.

</details>

## Related guides

- [UI roots, nodes, and input routing](ui-core.md)
- [Viewport scaling and coordinate conversion](viewports.md)
- [Standard UI controls](ui-controls.md)
