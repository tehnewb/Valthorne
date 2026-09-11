# Math and 2D geometry

Author: Albert Beaupre

[System manual](README.md)

## Purpose

MathUtils supplies interpolation, random sampling, statistics, easing, angle, distance, and bit helpers. The geometry package provides mutable 2D shapes and boundary points. Valthorne uses JOML for vectors, matrices, quaternions, rays, and 3D bounds; these helpers complement that dependency.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Numeric helpers | Clamp, normalize, map, average, median, and rounding implement specific arithmetic rules. |
| Animation math | Easing and angle interpolation convert normalized progress into motion. |
| Geometry | Circle, rectangle, rounded rectangle, triangle, and polygon expose boundaries and movement. |
| Approximation | Fast square-root and distance helpers trade accuracy for a compact approximation. |
| Mutable boundaries | Point arrays and cached centers can be live or reused rather than snapshots. |

## Getting started

1. Choose the method by its exact units and boundary behavior.
2. Validate ranges before normalization, wrapping, or random interval selection.
3. Use shape setters/movement to update defining geometry and copy returned caches when retaining snapshots.
4. Use JOML types directly where the public API expects them.

## Ownership and lifecycle

Most geometry is mutable and unsynchronized. Returned vectors/arrays may belong to the shape and be overwritten by later operations. Integer overflow and floating-point nonfinite behavior are not universally rejected by MathUtils.

## Important behavior

- Some angle helpers use signed-remainder formulas rather than fully normalized shortest-arc behavior.
- Fast approximations do not promise Math.sqrt's zero/NaN/infinity semantics.
- A polygon array can contain null vertices if its constructor validates only the array length.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Area`](#type-area)
- [`Border`](#type-border)
- [`Circle`](#type-circle)
- [`Polygon`](#type-polygon)
- [`Rectangle`](#type-rectangle)
- [`RoundedRectangle`](#type-roundedrectangle)
- [`Shape`](#type-shape)
- [`Triangle`](#type-triangle)
- [`MathUtils`](#type-mathutils)

<a id="type-area"></a>

### Area

[Source](../../src/main/java/valthorne/math/geometry/Area.java#L12)

Represents a geometric area defined by a sequence of points that form its boundary.
Provides geometric operations such as point containment and area intersection.

<details>
<summary>Area operation reference (3 declarations)</summary>

#### points

```java
Vector2f[] points()
```

Returns the points that define the shape or boundary of this area.

**Returns:** An array of Vector2f objects representing the vertices of the area in sequence.

#### contains

```java
default boolean contains(float x, float y)
```

Determines if the point specified by the coordinates (x, y) lies within
the boundary defined by the area. This method uses the even-odd rule
algorithm to check whether the point is inside the polygon formed by
the area points.

- **`x`** — the x-coordinate of the point to check
- **`y`** — the y-coordinate of the point to check

**Returns:** true if the point (x, y) is inside the boundary of the area;
false otherwise

#### overlaps

```java
default boolean overlaps(Area other)
```

Determines if this area overlaps with the given area.
Two areas are considered to overlap if any of their edges intersect
or if one area contains any point from the other area.

- **`other`** — the other area to check for overlap, can be null

**Returns:** true if the two areas overlap, false otherwise

</details>

<a id="type-border"></a>

### Border

[Source](../../src/main/java/valthorne/math/geometry/Border.java#L13)

Represents a border with a specified color and thickness.
This class allows customization of a border's appearance
by providing methods to get and set its color and thickness.

<details>
<summary>Border operation reference (6 declarations)</summary>

#### Constructor

```java
public Border()
```

Creates an opaque white border with thickness one, allocating an independent
mutable color.

#### Constructor

```java
public Border(Color color, float thickness)
```

Stores a borrowed color and thickness without validation. Individual renderers
decide how null colors and nonpositive widths affect drawing.

- **`color`** — borrowed border tint, possibly null
- **`thickness`** — requested border width

#### getColor

```java
public Color getColor()
```

Returns the borrowed mutable tint used for border drawing.

**Returns:** current color, possibly null

#### setColor

```java
public void setColor(Color color)
```

Replaces the tint reference without copying or disposing either color.

- **`color`** — new borrowed tint, possibly null

#### getThickness

```java
public float getThickness()
```

Returns the stored border width. Units and supported ranges depend on the
renderer using this descriptor.

**Returns:** requested thickness

#### setThickness

```java
public void setThickness(float thickness)
```

Stores border thickness without clamping or finite-value validation.

- **`thickness`** — new requested width

</details>

<a id="type-circle"></a>

### Circle

[Source](../../src/main/java/valthorne/math/geometry/Circle.java#L19)

A polygonal circle defined by the bottom-left corner of its bounding square,
a nonnegative radius, and at least three perimeter samples. The center is
(x + radius, y + radius); changing radius therefore moves the center while
preserving the bottom-left anchor. Perimeter points run counterclockwise from
the positive-X direction.

Center and point getters expose mutable cached storage. Setters rebuild those
values from the defining fields, overwriting external edits. Segment changes
replace the point array, so callers needing stable geometry must copy it.

<details>
<summary>Circle operation reference (13 declarations)</summary>

#### Constructor

```java
public Circle(float x, float y, float radius, int segments)
```

Creates a sampled circle, clamping radius to at least zero and segments to
at least three. Coordinates and radius are not checked for finiteness.

- **`x`** — bounding-square left coordinate
- **`y`** — bounding-square bottom coordinate
- **`radius`** — radius, with negative values clamped to zero
- **`segments`** — perimeter count, clamped to at least three

#### Constructor

```java
public Circle(float x, float y, float radius)
```

Creates a circle with 32 perimeter samples.

- **`x`** — bounding-square left coordinate
- **`y`** — bounding-square bottom coordinate
- **`radius`** — radius, with negative values clamped to zero

#### getX

```java
public float getX()
```

Returns the left edge of the circle's bounding square.

**Returns:** bottom-left anchor X

#### setX

```java
public void setX(float x)
```

Moves the bounding-square left edge and rebuilds center/perimeter storage.

- **`x`** — new anchor X

#### getY

```java
public float getY()
```

Returns the bottom edge of the circle's bounding square.

**Returns:** bottom-left anchor Y

#### setY

```java
public void setY(float y)
```

Moves the bounding-square bottom edge and rebuilds center/perimeter storage.

- **`y`** — new anchor Y

#### getRadius

```java
public float getRadius()
```

Returns the stored radius in the same coordinate units as the anchor.

**Returns:** current radius

#### setRadius

```java
public void setRadius(float radius)
```

Clamps negative radius to zero and rebuilds geometry while retaining the
bottom-left anchor. NaN is not rejected.

- **`radius`** — new radius

#### getSegments

```java
public int getSegments()
```

Returns the number of equally spaced perimeter vertices.

**Returns:** sample count, at least three

#### setSegments

```java
public void setSegments(int segments)
```

Clamps the sample count to at least three and replaces perimeter storage when
the effective count changes. Retained old arrays cease to reflect this circle.

- **`segments`** — requested perimeter count

#### getCenter

```java
public Vector2f getCenter()
```

Returns the live cached center. Mutating it alone does not update the anchor
or perimeter, and the next geometry rebuild overwrites the edit.

**Returns:** mutable cached center

#### move

```java
public void move(Vector2f offset)
```

Translates the bottom-left anchor by an offset and rebuilds cached geometry.

- **`offset`** — nonnull translation vector

**Throws `NullPointerException`:** if offset is null

#### points

```java
    public Vector2f[] points()
```

Returns the live perimeter array without repeating the first point at the end.
Coordinate setters overwrite its vectors, and segment changes replace the array.

**Returns:** mutable sampled boundary

</details>

<a id="type-polygon"></a>

### Polygon

[Source](../../src/main/java/valthorne/math/geometry/Polygon.java#L14)

Represents a polygon defined by a sequence of vertices in 2D space.
A polygon is required to have a minimum of three vertices.
Implements the `Area` interface to provide operations related to
geometric areas such as retrieving points that define the boundary.

<details>
<summary>Polygon operation reference (4 declarations)</summary>

#### Constructor

```java
public Polygon(Vector2f... points)
```

Creates a polygon from the given vertices.

- **`points`** — polygon vertices (must contain at least 3 points)

#### setPoints

```java
public void setPoints(Vector2f... points)
```

Sets the vertices of the polygon. A valid polygon must have at least three points.
If the provided points are null or less than three, an `IllegalArgumentException` is thrown.

- **`points`** — an array of `Vector2f` objects representing the polygon's vertices

**Throws `IllegalArgumentException`:** if the provided points array is null or contains fewer than three points

#### move

```java
@Override
    public void move(Vector2f offset)
```

Translates each non-null vertex in place, preserving array order and identity.
The offset is read for each vertex, so use an independent vector: aliasing a
stored vertex can change the offset while the traversal is still in progress.

- **`offset`** — translation in the polygon's coordinate units

#### points

```java
@Override
    public Vector2f[] points()
```

Exposes the live boundary array retained by construction or setPoints.
Neither the array nor its vectors are copied; caller mutations affect the polygon.
Null entries are possible because vertex elements are not validated.

**Returns:** live ordered vertex array

</details>

<a id="type-rectangle"></a>

### Rectangle

[Source](../../src/main/java/valthorne/math/geometry/Rectangle.java#L15)

Represents a rectangle defined by its top-left corner coordinates and its dimensions.
Provides methods to retrieve and manipulate the rectangle's properties,
as well as to calculate its center and geometric transformations.

This implementation performs NO allocations after construction.

<details>
<summary>Rectangle operation reference (15 declarations)</summary>

#### Constructor

```java
public Rectangle()
```

Constructs a new `Rectangle` with uninitialized position and dimensions.
By default, all fields such as x, y, width, and height will be set to their
default values (e.g., zero for numerical fields).

#### Constructor

```java
public Rectangle(float x, float y, float width, float height)
```

Constructs a new `Rectangle` with the specified position and dimensions.

- **`x`** — the x-coordinate of the rectangle's top-left corner
- **`y`** — the y-coordinate of the rectangle's top-left corner
- **`width`** — the width of the rectangle
- **`height`** — the height of the rectangle

#### getX

```java
public float getX()
```

Retrieves the x-coordinate of the rectangle's top-left corner.

**Returns:** the x-coordinate of the rectangle

#### setX

```java
public void setX(float x)
```

Updates the x-coordinate of the rectangle's top-left corner
and recalculates its corner points.

- **`x`** — the new x-coordinate of the rectangle

#### getY

```java
public float getY()
```

Retrieves the y-coordinate of the rectangle's top-left corner.

**Returns:** the y-coordinate of the rectangle

#### setY

```java
public void setY(float y)
```

Updates the y-coordinate of the rectangle's top-left corner
and recalculates its corner points.

- **`y`** — the new y-coordinate of the rectangle

#### getWidth

```java
public float getWidth()
```

Retrieves the width of the rectangle.

**Returns:** the width of the rectangle

#### setWidth

```java
public void setWidth(float width)
```

Updates the width of the rectangle and recalculates its corner points.

- **`width`** — the new width of the rectangle

#### getHeight

```java
public float getHeight()
```

Retrieves the height of the rectangle.

**Returns:** the height of the rectangle

#### setHeight

```java
public void setHeight(float height)
```

Updates the height of the rectangle and recalculates its corner points.

- **`height`** — the new height of the rectangle

#### setPosition

```java
public void setPosition(float x, float y)
```

Sets the position of the rectangle by updating its x and y coordinates.
Recalculates the rectangle's corner points after the position is updated.

- **`x`** — the new x-coordinate of the rectangle's top-left corner
- **`y`** — the new y-coordinate of the rectangle's top-left corner

#### setSize

```java
public void setSize(float width, float height)
```

Updates the dimensions of the rectangle by setting its width and height.
Recalculates the rectangle's corner points after the dimensions are updated.

- **`width`** — the new width of the rectangle
- **`height`** — the new height of the rectangle

#### getCenter

```java
public Vector2f getCenter()
```

Retrieves the center point of the rectangle without allocating.

**Returns:** a reused `Vector2f` representing the rectangle center

#### move

```java
@Override
    public void move(Vector2f offset)
```

Adds the supplied translation to the origin and refreshes all cached corners.
Dimensions are preserved, and the offset vector is read before the corner cache
is updated, allowing cached corner values to be used as an offset.

- **`offset`** — translation in rectangle coordinate units

#### points

```java
@Override
    public Vector2f[] points()
```

Returns the reusable four-corner array in boundary order, beginning at the
stored origin. Mutating these vectors does not change rectangle bounds and is
overwritten by the next bounds update; copy values when retaining a snapshot.

**Returns:** live cached corner array

</details>

<a id="type-roundedrectangle"></a>

### RoundedRectangle

[Source](../../src/main/java/valthorne/math/geometry/RoundedRectangle.java#L19)

Mutable axis-aligned rounded rectangle represented by reusable boundary points.
Positive-radius corners use segmentsPerCorner plus one samples each, including
arc endpoints; near-zero radius uses four square corners. Position and size
changes rebuild coordinates immediately, retaining vector objects when the
required point count is unchanged. The points accessor exposes this live storage.

Dimensions are stored unchecked; use finite nonnegative sizes. Radius is
clamped to half the smaller nonnegative dimension. Construction first raises
requested radius to at least one, unlike setRadius, which accepts zero. Thus
the four-argument constructor can produce rounded corners despite passing zero.

<details>
<summary>RoundedRectangle operation reference (18 declarations)</summary>

#### Constructor

```java
public RoundedRectangle(float x, float y, float width, float height, float radius, int segmentsPerCorner)
```

Stores geometry, clamps the requested radius first to at least one and then
to the size-dependent maximum, and constructs boundary points. Sampling count
is clamped to at least one segment per corner.

- **`x`** — rectangle origin X
- **`y`** — rectangle origin Y
- **`width`** — finite nonnegative width expected by the caller
- **`height`** — finite nonnegative height expected by the caller
- **`radius`** — requested corner radius in geometry units
- **`segmentsPerCorner`** — requested line-segment count for each quarter arc

#### Constructor

```java
public RoundedRectangle(float x, float y, float width, float height)
```

Constructs with four segments per corner and an initial requested radius of
zero. The delegated constructor raises that radius to one before size clamping;
call setRadius(0) afterward when square corners are required.

- **`x`** — rectangle origin X
- **`y`** — rectangle origin Y
- **`width`** — rectangle width
- **`height`** — rectangle height

#### getX

```java
public float getX()
```

Reads stored origin X without deriving it from exposed boundary points.

**Returns:** origin X in geometry units

#### setX

```java
public void setX(float x)
```

Replaces origin X and rewrites current boundary coordinates immediately.
Size, radius, and sample count are unchanged.

- **`x`** — finite origin X

#### getY

```java
public float getY()
```

Reads stored origin Y without deriving it from exposed boundary points.

**Returns:** origin Y in geometry units

#### setY

```java
public void setY(float y)
```

Replaces origin Y and rewrites current boundary coordinates immediately.
Size, radius, and sample count are unchanged.

- **`y`** — finite origin Y

#### getWidth

```java
public float getWidth()
```

Reads stored width without deriving it from exposed boundary points.

**Returns:** width in geometry units

#### setWidth

```java
public void setWidth(float width)
```

Replaces width, reclamps radius to fit, and immediately rebuilds points.
A radius reduced by shrinking is not automatically restored after growth.

- **`width`** — finite nonnegative width expected by the caller

#### getHeight

```java
public float getHeight()
```

Reads stored height without deriving it from exposed boundary points.

**Returns:** height in geometry units

#### setHeight

```java
public void setHeight(float height)
```

Replaces height, reclamps radius to fit, and immediately rebuilds points.
A radius reduced by shrinking is not automatically restored after growth.

- **`height`** — finite nonnegative height expected by the caller

#### getRadius

```java
public float getRadius()
```

Reads the effective clamped corner radius used by the last geometry rebuild.

**Returns:** current radius in geometry units

#### setRadius

```java
public void setRadius(float radius)
```

Clamps a requested radius to zero through half the smaller nonnegative size
and rebuilds points immediately. Unlike construction, this permits zero.

- **`radius`** — finite requested corner radius

#### getSegmentsPerCorner

```java
public int getSegmentsPerCorner()
```

Reads configured sampling density; the square-corner path still uses four
points regardless of this value.

**Returns:** line segments per quarter arc, at least one

#### setSegmentsPerCorner

```java
public void setSegmentsPerCorner(int segmentsPerCorner)
```

Clamps density to at least one and rebuilds geometry. A changed total point
count replaces the exposed array and its vectors; excessive sizes are not guarded.

- **`segmentsPerCorner`** — requested segments per quarter arc

#### setPosition

```java
public void setPosition(float x, float y)
```

Replaces both origin coordinates and updates points without changing size,
radius, or sampling density.

- **`x`** — finite origin X
- **`y`** — finite origin Y

#### setSize

```java
public void setSize(float width, float height)
```

Replaces dimensions, clamps the existing radius to fit, and rebuilds points.
Growing later does not restore a radius previously reduced by this operation.

- **`width`** — finite nonnegative width expected by the caller
- **`height`** — finite nonnegative height expected by the caller

#### move

```java
    public void move(Vector2f offset)
```

Adds a borrowed displacement to the origin and refreshes all boundary points.
The supplied vector is read only and is not retained.

- **`offset`** — non-null translation in geometry units

**Throws `NullPointerException`:** if offset is null

#### points

```java
    public Vector2f[] points()
```

Exposes the current mutable boundary array and its vectors without copying.
Do not overwrite its entries; updates reuse them, or replace the entire array
when sampling count changes. External coordinate edits do not update origin/size.

**Returns:** live world/geometry-space boundary points

</details>

<a id="type-shape"></a>

### Shape

[Source](../../src/main/java/valthorne/math/geometry/Shape.java#L14)

The Shape class serves as an abstract base class for defining 2D geometric shapes.
It provides core functionality such as color and border management, rendering methods,
and movement capabilities, as well as a contract for subclasses to implement behavior specific to each shape type.

<details>
<summary>Shape operation reference (8 declarations)</summary>

#### Constructor

```java
public Shape()
```

Constructs a new Shape instance with a default color of white.
The default color is represented as an RGBA value of (1f, 1f, 1f, 1f).

#### Constructor

```java
public Shape(Color color)
```

Constructs a new Shape instance with a specified color.
If the provided color is null, the default color is set to white,
represented as an RGBA value of (1f, 1f, 1f, 1f).

- **`color`** — the color of the shape. If null, the default color white is assigned.

#### move

```java
public abstract void move(Vector2f offset)
```

Moves the shape by a specified offset in 2D space.
The method adjusts the position of the shape based on the given offset vector.
This operation modifies the shape's position without altering its size, rotation, or other properties.

- **`offset`** — the 2D vector specifying the movement in the x and y directions. It represents the amount by which the shape's position should be shifted.

#### getColor

```java
public Color getColor()
```

Retrieves the current color assigned to the shape.

**Returns:** the color of the shape, which represents its fill color.

#### setColor

```java
public void setColor(Color color)
```

Sets the color of the shape. If the provided color is null,
the color is set to the default value of white, represented
as an RGBA value of (1f, 1f, 1f, 1f).

- **`color`** — the new color to assign to the shape. If null, the color is set to white.

#### getBorder

```java
public Border getBorder()
```

Retrieves the border of the shape.

**Returns:** the border assigned to the shape. If no border has been set,
the method may return null or a default border, depending on the
implementation.

#### setBorder

```java
public void setBorder(Border border)
```

Sets the border of the shape. The border specifies the appearance of the shape's outline,
including its color and thickness. If `null` is provided, the shape will have no border.

- **`border`** — the border to assign to the shape, which defines its outline's color and thickness. If `null`, the border is removed.

#### hasBorder

```java
public boolean hasBorder()
```

Determines whether the shape has a defined border.
A shape is considered to have a border if the border is not null,
its thickness is greater than 0, and its color is defined.

**Returns:** true if the shape has a border, false otherwise

</details>

<a id="type-triangle"></a>

### Triangle

[Source](../../src/main/java/valthorne/math/geometry/Triangle.java#L14)

Mutable 2D triangle with three copied vertex positions. Vertex getters and
points expose the same internal vectors, so direct edits affect geometry.
The center is recomputed as the arithmetic mean when requested and is returned
in reusable scratch storage. No winding, degeneracy, or finiteness validation
is performed.

<details>
<summary>Triangle operation reference (10 declarations)</summary>

#### Constructor

```java
public Triangle(Vector2f a, Vector2f b, Vector2f c)
```

Copies three input vertices and installs them in the reusable boundary array.

- **`a`** — first nonnull vertex
- **`b`** — second nonnull vertex
- **`c`** — third nonnull vertex

**Throws `NullPointerException`:** if a vertex is null

#### getA

```java
public Vector2f getA()
```

Returns the live A vertex; coordinate mutations immediately affect geometry.

**Returns:** internal A position

#### setA

```java
public void setA(Vector2f a)
```

Copies coordinates into the existing A vertex without replacing its identity.

- **`a`** — nonnull source position

**Throws `NullPointerException`:** if the source is null

#### getB

```java
public Vector2f getB()
```

Returns the live B vertex; coordinate mutations immediately affect geometry.

**Returns:** internal B position

#### setB

```java
public void setB(Vector2f b)
```

Copies coordinates into the existing B vertex without replacing its identity.

- **`b`** — nonnull source position

**Throws `NullPointerException`:** if the source is null

#### getC

```java
public Vector2f getC()
```

Returns the live C vertex; coordinate mutations immediately affect geometry.

**Returns:** internal C position

#### setC

```java
public void setC(Vector2f c)
```

Copies coordinates into the existing C vertex without replacing its identity.

- **`c`** — nonnull source position

**Throws `NullPointerException`:** if the source is null

#### getCenter

```java
public Vector2f getCenter()
```

Recomputes the centroid from the current vertices and returns reusable storage.
Later calls overwrite it; mutating the result does not move the triangle.

**Returns:** borrowed centroid vector

#### move

```java
public void move(Vector2f offset)
```

Adds an offset to each vertex in order. Use a separate offset vector: aliasing
one of the triangle's vertices can change the offset during this operation.

- **`offset`** — nonnull translation

**Throws `NullPointerException`:** if offset is null

#### points

```java
    public Vector2f[] points()
```

Returns the live three-entry boundary array whose initial entries reference
the triangle's internal A/B/C vectors. Replacing array entries does not replace
the defining fields; modify their coordinates or use setters instead.

**Returns:** mutable boundary array

</details>

<a id="type-mathutils"></a>

### MathUtils

[Source](../../src/main/java/valthorne/math/MathUtils.java#L24)

Static numeric helpers for game calculations: interpolation, angles, random
sampling, statistics, easing, distances, and integer bit operations. Methods
preserve their documented arithmetic behavior rather than universally validating
ranges, clamping inputs, or detecting overflow. Read individual contracts for
degenerate ranges and nonfinite values.

Random helpers share one pseudorandom generator. Statistical helpers preserve
input arrays; median calculations sort copies. Fast square-root helpers trade
accuracy and special-value behavior for a compact approximation.

```java
float progress = MathUtils.clamp(elapsed / duration, 0f, 1f);
float x = MathUtils.lerp(startX, endX, progress);
double mean = MathUtils.average(10, 20, 30);
int wrappedFrame = MathUtils.wrap(frame, 0, frameCount - 1);
```

<details>
<summary>MathUtils operation reference (109 declarations)</summary>

#### TAU

```java
public static final  double TAU
```

TAU constant (2\u03c0).

#### TAU_F

```java
public static final  float TAU_F
```

TAU constant (2\u03c0) as a float.

#### DEG_TO_RAD

```java
public static final  double DEG_TO_RAD
```

Degrees to radians multiplier.

#### RAD_TO_DEG

```java
public static final  double RAD_TO_DEG
```

Radians to degrees multiplier.

#### DEG_TO_RAD_F

```java
public static final  float DEG_TO_RAD_F
```

Degrees to radians multiplier as a float.

#### RAD_TO_DEG_F

```java
public static final  float RAD_TO_DEG_F
```

Radians to degrees multiplier as a float.

#### clamp

```java
public static int clamp(int value, int min, int max)
```

Limits a value to inclusive bounds using ordered comparisons. Bounds are
not reordered or validated.

- **`value`** — value to constrain
- **`min`** — inclusive lower bound
- **`max`** — inclusive upper bound

**Returns:** value limited by the supplied bounds

#### clamp

```java
public static long clamp(long value, long min, long max)
```

Limits a value to inclusive bounds using ordered comparisons. Bounds are
not reordered or validated.

- **`value`** — value to constrain
- **`min`** — inclusive lower bound
- **`max`** — inclusive upper bound

**Returns:** value limited by the supplied bounds

#### clamp

```java
public static float clamp(float value, float min, float max)
```

Limits a value to inclusive bounds using ordered comparisons. Bounds are
not reordered or validated. A NaN value passes through unchanged.

- **`value`** — value to constrain
- **`min`** — inclusive lower bound
- **`max`** — inclusive upper bound

**Returns:** value limited by the supplied bounds

#### clamp

```java
public static double clamp(double value, double min, double max)
```

Limits a value to inclusive bounds using ordered comparisons. Bounds are
not reordered or validated. A NaN value passes through unchanged.

- **`value`** — value to constrain
- **`min`** — inclusive lower bound
- **`max`** — inclusive upper bound

**Returns:** value limited by the supplied bounds

#### lerp

```java
public static float lerp(float a, float b, float t)
```

Computes a + (b - a) * t without clamping the interpolation factor.
Factors outside zero through one extrapolate beyond the endpoints.

- **`a`** — value at factor zero
- **`b`** — value at factor one
- **`t`** — interpolation factor

**Returns:** linearly interpolated or extrapolated value

#### lerp

```java
public static double lerp(double a, double b, double t)
```

Computes a + (b - a) * t without clamping the interpolation factor.
Factors outside zero through one extrapolate beyond the endpoints.

- **`a`** — value at factor zero
- **`b`** — value at factor one
- **`t`** — interpolation factor

**Returns:** linearly interpolated or extrapolated value

#### norm

```java
public static float norm(float value, float min, float max)
```

Computes the fractional position within the supplied range without clamping.
Equal bounds invoke floating-point division by zero and can yield NaN or infinity.

- **`value`** — value to normalize
- **`min`** — range start
- **`max`** — range end

**Returns:** fraction (value - min) / (max - min)

#### norm

```java
public static double norm(double value, double min, double max)
```

Computes the fractional position within the supplied range without clamping.
Equal bounds invoke floating-point division by zero and can yield NaN or infinity.

- **`value`** — value to normalize
- **`min`** — range start
- **`max`** — range end

**Returns:** fraction (value - min) / (max - min)

#### map

```java
public static float map(float value, float inMin, float inMax, float outMin, float outMax)
```

Normalizes in the source interval and interpolates into the destination interval.
Neither interval is reordered; values outside the source range extrapolate.

- **`value`** — source value
- **`inMin`** — source interval start
- **`inMax`** — source interval end
- **`outMin`** — destination interval start
- **`outMax`** — destination interval end

**Returns:** mapped value, potentially NaN or infinite for a degenerate source interval

#### map

```java
public static double map(double value, double inMin, double inMax, double outMin, double outMax)
```

Normalizes in the source interval and interpolates into the destination interval.
Neither interval is reordered; values outside the source range extrapolate.

- **`value`** — source value
- **`inMin`** — source interval start
- **`inMax`** — source interval end
- **`outMin`** — destination interval start
- **`outMax`** — destination interval end

**Returns:** mapped value, potentially NaN or infinite for a degenerate source interval

#### round

```java
public static int round(double value)
```

Rounds through Math.round and narrows the resulting long to int.
Half ties round toward positive infinity; narrowing out-of-range results can wrap.

- **`value`** — value to round

**Returns:** rounded long narrowed to int

#### floor

```java
public static int floor(double value)
```

Applies Math.floor and converts its result to int using Java narrowing.
NaN converts to zero and values outside the int range saturate to an endpoint.

- **`value`** — value to round

**Returns:** downward-rounded integer, subject to narrowing

#### ceil

```java
public static int ceil(double value)
```

Applies Math.ceil and converts its result to int using Java narrowing.
NaN converts to zero and values outside the int range saturate to an endpoint.

- **`value`** — value to round

**Returns:** upward-rounded integer, subject to narrowing

#### roundToLong

```java
public static long roundToLong(double value)
```

Delegates to Math.round: half ties round toward positive infinity, NaN yields
zero, and values beyond the long range saturate.

- **`value`** — value to round

**Returns:** nearest long under Math.round rules

#### nearestMultiple

```java
public static int nearestMultiple(int value, int multiple)
```

Divides in float precision, rounds the quotient, and multiplies back.
Use a positive nonzero spacing. Large inputs can lose precision or overflow;
spacing is not validated.

- **`value`** — value to snap
- **`multiple`** — positive nonzero spacing

**Returns:** rounded multiple under the implementation's arithmetic

#### nearestMultiple

```java
public static long nearestMultiple(long value, long multiple)
```

Divides in double precision, rounds the quotient, and multiplies back.
Use a positive nonzero spacing. Large inputs can lose precision or overflow;
spacing is not validated.

- **`value`** — value to snap
- **`multiple`** — positive nonzero spacing

**Returns:** rounded multiple under the implementation's arithmetic

#### sinDeg

```java
public static double sinDeg(double degrees)
```

Converts degrees to radians and evaluates Math.sin.
NaN and infinite angles produce NaN; no angle normalization is performed.

- **`degrees`** — angle in degrees

**Returns:** sin of the supplied angle

#### cosDeg

```java
public static double cosDeg(double degrees)
```

Converts degrees to radians and evaluates Math.cos.
NaN and infinite angles produce NaN; no angle normalization is performed.

- **`degrees`** — angle in degrees

**Returns:** cos of the supplied angle

#### tanDeg

```java
public static double tanDeg(double degrees)
```

Converts degrees to radians and evaluates Math.tan.
NaN and infinite angles produce NaN; no angle normalization is performed.

- **`degrees`** — angle in degrees

**Returns:** tan of the supplied angle

#### asinDeg

```java
public static double asinDeg(double sin)
```

Evaluates Math.asin and converts its principal result to degrees.
Inputs outside minus one through one produce NaN.

- **`sin`** — trigonometric ratio

**Returns:** principal inverse angle in degrees

#### acosDeg

```java
public static double acosDeg(double cos)
```

Evaluates Math.acos and converts its principal result to degrees.
Inputs outside minus one through one produce NaN.

- **`cos`** — trigonometric ratio

**Returns:** principal inverse angle in degrees

#### atanDeg

```java
public static double atanDeg(double tan)
```

Evaluates Math.atan and converts its principal result to degrees.
Infinite inputs produce the corresponding signed right angle.

- **`tan`** — trigonometric ratio

**Returns:** principal inverse angle in degrees

#### atan2Deg

```java
public static double atan2Deg(double y, double x)
```

Computes the quadrant-aware angle from the positive x axis and converts it to
degrees. Signed zero and infinities follow Math.atan2 behavior.

- **`y`** — vertical component
- **`x`** — horizontal component

**Returns:** principal angle in degrees

#### fastInvSqrt

```java
public static float fastInvSqrt(float x)
```

Approximates inverse square root using an IEEE-754 bit estimate and one Newton
refinement. Intended for positive finite input; zero, negatives, infinities, and
NaN do not follow Math.sqrt's special-value guarantees.

- **`x`** — positive finite radicand

**Returns:** approximation to one divided by square root of x

#### randomInt

```java
public static int randomInt(int max)
```

Samples uniformly from zero through max using the shared generator.
The inclusive bound is implemented as max + 1, which must remain positive.

- **`max`** — inclusive upper bound from zero through Integer.MAX_VALUE minus one

**Returns:** sampled integer

**Throws `IllegalArgumentException`:** if max + 1 is not positive

#### randomInt

```java
public static int randomInt(int min, int max)
```

Samples uniformly from an inclusive integer interval using the shared generator.
The computed width max - min + 1 must be positive without integer overflow.

- **`min`** — inclusive lower bound
- **`max`** — inclusive upper bound

**Returns:** sampled integer

**Throws `IllegalArgumentException`:** if the computed interval width is not positive

#### randomFloat

```java
public static float randomFloat(float min, float max)
```

Scales a shared-generator sample from zero-inclusive, one-exclusive into the
supplied interval. Bounds are not validated; floating-point rounding can reach
the upper endpoint, and reversed bounds reverse the mapping.

- **`min`** — interval start
- **`max`** — interval end

**Returns:** scaled pseudorandom sample

#### randomDouble

```java
public static double randomDouble(double min, double max)
```

Scales a shared-generator sample from zero-inclusive, one-exclusive into the
supplied interval. Bounds are not validated; floating-point rounding can reach
the upper endpoint, and reversed bounds reverse the mapping.

- **`min`** — interval start
- **`max`** — interval end

**Returns:** scaled pseudorandom sample

#### randomBoolean

```java
public static boolean randomBoolean()
```

Draws one pseudorandom Boolean from the shared Random instance.
Successive calls advance the same generator used by the other random helpers.

**Returns:** true or false with equal probability

#### randomGaussian

```java
public static float randomGaussian(float mean, float deviation)
```

Scales a standard normal sample and shifts it by the requested mean.
The deviation is passed through unchanged; zero collapses to the mean and negative
values reflect the sample. This does not constrain the output to an interval.

- **`mean`** — distribution center
- **`deviation`** — standard-deviation multiplier

**Returns:** Gaussian-distributed float

#### angleDiff

```java
public static double angleDiff(double a, double b)
```

Wraps b - a by repeated full turns into the interval above minus pi through pi.
Supply finite angles with a reasonably bounded difference: infinite differences
or magnitudes too large to change by one turn can prevent termination.

- **`a`** — starting angle in radians
- **`b`** — ending angle in radians

**Returns:** signed wrapped difference in radians

#### lerpAngle

```java
public static double lerpAngle(double a, double b, double t)
```

Adds a fraction of angleDiff's wrapped radian difference to the starting angle.
The factor and resulting angle are not clamped or normalized; angleDiff's finite
input requirements apply.

- **`a`** — starting radians
- **`b`** — ending radians
- **`t`** — interpolation factor

**Returns:** interpolated angle in radians

#### lerpAngleDeg

```java
public static double lerpAngleDeg(double a, double b, double t)
```

Interpolates using ((b - a) + 180) % 360 - 180 as the degree difference.
Java's signed remainder means large negative differences are not always mapped
to the shortest arc. Neither factor nor output is clamped.

- **`a`** — starting degrees
- **`b`** — ending degrees
- **`t`** — interpolation factor

**Returns:** degree interpolation under the signed-remainder formula

#### isPowerOfTwo

```java
public static boolean isPowerOfTwo(int n)
```

Tests for exactly one set bit in a positive integer. Zero and negative values
return false, even when their raw bit pattern contains a single set bit.

- **`n`** — integer to test

**Returns:** whether n is a positive power of two

#### nextPowerOfTwo

```java
public static int nextPowerOfTwo(int n)
```

Propagates the highest set bit and rounds upward to a power of two.
Nonpositive input returns one; values above 2^30 overflow to Integer.MIN_VALUE.

- **`n`** — requested minimum capacity

**Returns:** rounded power of two, subject to signed overflow

#### sigmoid

```java
public static double sigmoid(double x)
```

Evaluates the logistic function 1 / (1 + exp(-x)). Extreme values approach
zero or one under floating-point arithmetic; NaN propagates.

- **`x`** — logistic input

**Returns:** logistic value

#### smoothStep

```java
public static double smoothStep(double edge0, double edge1, double x)
```

Normalizes x between the edges, clamps the fraction, and applies a cubic
smoothing polynomial. Equal edges can produce NaN; reversed edges invert the
transition rather than being rejected.

- **`edge0`** — transition start
- **`edge1`** — transition end
- **`x`** — input value

**Returns:** smoothed fraction

#### smootherStep

```java
public static double smootherStep(double edge0, double edge1, double x)
```

Normalizes x between the edges, clamps the fraction, and applies a quintic
smoothing polynomial. Equal edges can produce NaN; reversed edges invert the
transition rather than being rejected.

- **`edge0`** — transition start
- **`edge1`** — transition end
- **`x`** — input value

**Returns:** smoothed fraction

#### safeDiv

```java
public static int safeDiv(int a, int b)
```

Divides with truncation toward zero, returning zero when the divisor is zero.
Other Java integer behavior remains, including minimum-value divided by minus one.

- **`a`** — dividend
- **`b`** — divisor

**Returns:** integer quotient, or zero for a zero divisor

#### safeDiv

```java
public static long safeDiv(long a, long b)
```

Divides with truncation toward zero, returning zero when the divisor is zero.
Other Java integer behavior remains, including minimum-value divided by minus one.

- **`a`** — dividend
- **`b`** — divisor

**Returns:** integer quotient, or zero for a zero divisor

#### max

```java
public static int max(int a, int b, int... values)
```

Finds the largest value among two required values and additional inputs.
The array is traversed without modification.

- **`a`** — first required value
- **`b`** — second required value
- **`values`** — additional values, possibly empty

**Returns:** selected extreme value

**Throws `NullPointerException`:** if values is null

#### max

```java
public static long max(long a, long b, long... values)
```

Finds the largest value among two required values and additional inputs.
The array is traversed without modification.

- **`a`** — first required value
- **`b`** — second required value
- **`values`** — additional values, possibly empty

**Returns:** selected extreme value

**Throws `NullPointerException`:** if values is null

#### max

```java
public static float max(float a, float b, float... values)
```

Finds the largest value among two required values and additional inputs.
The array is traversed without modification. Math's NaN and signed-zero rules apply.

- **`a`** — first required value
- **`b`** — second required value
- **`values`** — additional values, possibly empty

**Returns:** selected extreme value

**Throws `NullPointerException`:** if values is null

#### max

```java
public static double max(double a, double b, double... values)
```

Finds the largest value among two required values and additional inputs.
The array is traversed without modification. Math's NaN and signed-zero rules apply.

- **`a`** — first required value
- **`b`** — second required value
- **`values`** — additional values, possibly empty

**Returns:** selected extreme value

**Throws `NullPointerException`:** if values is null

#### min

```java
public static int min(int a, int b, int... values)
```

Finds the smallest value among two required values and additional inputs.
The array is traversed without modification.

- **`a`** — first required value
- **`b`** — second required value
- **`values`** — additional values, possibly empty

**Returns:** selected extreme value

**Throws `NullPointerException`:** if values is null

#### min

```java
public static long min(long a, long b, long... values)
```

Finds the smallest value among two required values and additional inputs.
The array is traversed without modification.

- **`a`** — first required value
- **`b`** — second required value
- **`values`** — additional values, possibly empty

**Returns:** selected extreme value

**Throws `NullPointerException`:** if values is null

#### min

```java
public static float min(float a, float b, float... values)
```

Finds the smallest value among two required values and additional inputs.
The array is traversed without modification. Math's NaN and signed-zero rules apply.

- **`a`** — first required value
- **`b`** — second required value
- **`values`** — additional values, possibly empty

**Returns:** selected extreme value

**Throws `NullPointerException`:** if values is null

#### min

```java
public static double min(double a, double b, double... values)
```

Finds the smallest value among two required values and additional inputs.
The array is traversed without modification. Math's NaN and signed-zero rules apply.

- **`a`** — first required value
- **`b`** — second required value
- **`values`** — additional values, possibly empty

**Returns:** selected extreme value

**Throws `NullPointerException`:** if values is null

#### average

```java
public static double average(byte... values)
```

Computes an arithmetic mean without modifying the input. Empty input returns
zero. Accumulation uses long arithmetic before division as a double.

- **`values`** — values to average, possibly empty

**Returns:** arithmetic mean, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### average

```java
public static double average(short... values)
```

Computes an arithmetic mean without modifying the input. Empty input returns
zero. Accumulation uses long arithmetic before division as a double.

- **`values`** — values to average, possibly empty

**Returns:** arithmetic mean, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### average

```java
public static double average(int... values)
```

Computes an arithmetic mean without modifying the input. Empty input returns
zero. Accumulation uses long arithmetic before division as a double.

- **`values`** — values to average, possibly empty

**Returns:** arithmetic mean, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### average

```java
public static double average(long... values)
```

Computes an arithmetic mean without modifying the input. Empty input returns
zero. Accumulation uses long arithmetic, so the sum can overflow before conversion to double.

- **`values`** — values to average, possibly empty

**Returns:** arithmetic mean, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### average

```java
public static double average(float... values)
```

Computes an arithmetic mean without modifying the input. Empty input returns
zero. Accumulation uses double arithmetic and ordinary rounding; NaN propagates.

- **`values`** — values to average, possibly empty

**Returns:** arithmetic mean, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### average

```java
public static double average(double... values)
```

Computes an arithmetic mean without modifying the input. Empty input returns
zero. Accumulation uses double arithmetic and ordinary rounding; NaN propagates.

- **`values`** — values to average, possibly empty

**Returns:** arithmetic mean, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### median

```java
public static double median(int... values)
```

Sorts a copy and selects the middle value, averaging the middle pair for even
lengths. Empty input returns zero. Pair addition occurs in the input's arithmetic
type before division and can overflow; input order is preserved.

- **`values`** — values whose median is requested

**Returns:** median, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### median

```java
public static double median(long... values)
```

Sorts a copy and selects the middle value, averaging the middle pair for even
lengths. Empty input returns zero. Pair addition occurs in the input's arithmetic
type before division and can overflow; input order is preserved.

- **`values`** — values whose median is requested

**Returns:** median, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### median

```java
public static double median(float... values)
```

Sorts a copy and selects the middle value, averaging the middle pair for even
lengths. Empty input returns zero. Pair addition occurs in the input's arithmetic
type before division and can overflow; input order is preserved.

- **`values`** — values whose median is requested

**Returns:** median, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### median

```java
public static double median(double... values)
```

Sorts a copy and selects the middle value, averaging the middle pair for even
lengths. Empty input returns zero. Pair addition occurs in the input's arithmetic
type before division and can overflow; input order is preserved.

- **`values`** — values whose median is requested

**Returns:** median, or zero for empty input

**Throws `NullPointerException`:** if values is null

#### between

```java
public static boolean between(int value, int min, int max)
```

Tests both inclusive boundaries without reordering them. Reversed bounds
produce false.

- **`value`** — value to test
- **`min`** — lower boundary
- **`max`** — upper boundary

**Returns:** whether value lies in the closed interval

#### between

```java
public static boolean between(long value, long min, long max)
```

Tests both inclusive boundaries without reordering them. Reversed bounds
produce false.

- **`value`** — value to test
- **`min`** — lower boundary
- **`max`** — upper boundary

**Returns:** whether value lies in the closed interval

#### between

```java
public static boolean between(float value, float min, float max)
```

Tests both inclusive boundaries without reordering them. Reversed bounds
produce false. Any NaN operand also produces false.

- **`value`** — value to test
- **`min`** — lower boundary
- **`max`** — upper boundary

**Returns:** whether value lies in the closed interval

#### between

```java
public static boolean between(double value, double min, double max)
```

Tests both inclusive boundaries without reordering them. Reversed bounds
produce false. Any NaN operand also produces false.

- **`value`** — value to test
- **`min`** — lower boundary
- **`max`** — upper boundary

**Returns:** whether value lies in the closed interval

#### sign

```java
public static int sign(int x)
```

Compares the value with positive zero and returns an integer sign.
The comparison avoids subtraction and its overflow risk.

- **`x`** — value to classify

**Returns:** minus one, zero, or one according to the comparison

#### sign

```java
public static int sign(long x)
```

Compares the value with positive zero and returns an integer sign.
The comparison avoids subtraction and its overflow risk.

- **`x`** — value to classify

**Returns:** minus one, zero, or one according to the comparison

#### sign

```java
public static int sign(float x)
```

Compares the value with positive zero and returns an integer sign.
Negative zero compares below positive zero; NaN compares above it.

- **`x`** — value to classify

**Returns:** minus one, zero, or one according to the comparison

#### sign

```java
public static int sign(double x)
```

Compares the value with positive zero and returns an integer sign.
Negative zero compares below positive zero; NaN compares above it.

- **`x`** — value to classify

**Returns:** minus one, zero, or one according to the comparison

#### abs

```java
public static int abs(int x)
```

Delegates to Math.abs. The minimum representable integer remains negative
because its positive magnitude does not fit in this type.

- **`x`** — value whose magnitude is requested

**Returns:** absolute value under Java arithmetic rules

#### abs

```java
public static long abs(long x)
```

Delegates to Math.abs. The minimum representable integer remains negative
because its positive magnitude does not fit in this type.

- **`x`** — value whose magnitude is requested

**Returns:** absolute value under Java arithmetic rules

#### abs

```java
public static float abs(float x)
```

Delegates to Math.abs. Negative zero becomes positive zero and NaN
remains NaN.

- **`x`** — value whose magnitude is requested

**Returns:** absolute value under Java arithmetic rules

#### abs

```java
public static double abs(double x)
```

Delegates to Math.abs. Negative zero becomes positive zero and NaN
remains NaN.

- **`x`** — value whose magnitude is requested

**Returns:** absolute value under Java arithmetic rules

#### fastFloor

```java
public static int fastFloor(float x)
```

Computes floor using an integer cast and a correction for negative fractions.
Use finite values within the int range; values outside it can saturate or overflow
and this helper does not validate that precondition.

- **`x`** — finite value representable within the int range

**Returns:** greatest integer no larger than x for supported input

#### fastFloor

```java
public static int fastFloor(double x)
```

Computes floor using an integer cast and a correction for negative fractions.
Use finite values within the int range; values outside it can saturate or overflow
and this helper does not validate that precondition.

- **`x`** — finite value representable within the int range

**Returns:** greatest integer no larger than x for supported input

#### distance

```java
public static double distance(double x1, double y1, double x2, double y2)
```

Computes Euclidean distance using direct coordinate differences.
Intermediate squares can overflow for large coordinates; no scaled hypot
algorithm or finiteness validation is used.

- **`x1`** — first point's horizontal coordinate
- **`y1`** — first point's vertical coordinate
- **`x2`** — second point's horizontal coordinate
- **`y2`** — second point's vertical coordinate

**Returns:** distance in coordinate units

#### distance

```java
public static float distance(float x1, float y1, float x2, float y2)
```

Computes Euclidean distance using direct coordinate differences.
Intermediate squares can overflow for large coordinates; no scaled hypot
algorithm or finiteness validation is used.

- **`x1`** — first point's horizontal coordinate
- **`y1`** — first point's vertical coordinate
- **`x2`** — second point's horizontal coordinate
- **`y2`** — second point's vertical coordinate

**Returns:** distance in coordinate units

#### distanceSq

```java
public static double distanceSq(double x1, double y1, double x2, double y2)
```

Computes squared Euclidean distance using direct coordinate differences.
Intermediate squares can overflow for large coordinates; no scaled hypot
algorithm or finiteness validation is used.

- **`x1`** — first point's horizontal coordinate
- **`y1`** — first point's vertical coordinate
- **`x2`** — second point's horizontal coordinate
- **`y2`** — second point's vertical coordinate

**Returns:** distance squared in squared coordinate units

#### distanceSq

```java
public static float distanceSq(float x1, float y1, float x2, float y2)
```

Computes squared Euclidean distance using direct coordinate differences.
Intermediate squares can overflow for large coordinates; no scaled hypot
algorithm or finiteness validation is used.

- **`x1`** — first point's horizontal coordinate
- **`y1`** — first point's vertical coordinate
- **`x2`** — second point's horizontal coordinate
- **`y2`** — second point's vertical coordinate

**Returns:** distance squared in squared coordinate units

#### fastSqrt

```java
public static float fastSqrt(float x)
```

Takes the reciprocal of fastInvSqrt's approximation. Intended for positive finite
input; it is neither correctly rounded nor guaranteed to return exact zero at zero.

- **`x`** — positive finite radicand

**Returns:** approximate square root

#### fastDistance

```java
public static float fastDistance(float x1, float y1, float x2, float y2)
```

Applies fastSqrt to direct squared coordinate distance. This is approximate;
coincident points need not yield exact zero and large differences can overflow.

- **`x1`** — first point x
- **`y1`** — first point y
- **`x2`** — second point x
- **`y2`** — second point y

**Returns:** approximate distance in coordinate units

#### easeInQuad

```java
public static double easeInQuad(double t)
```

Evaluates quadratic acceleration for animation progress.
Input is intended for zero through one but is not clamped.

- **`t`** — normalized animation progress

**Returns:** eased progress, potentially outside zero through one

#### easeOutQuad

```java
public static double easeOutQuad(double t)
```

Evaluates quadratic deceleration for animation progress.
Input is intended for zero through one but is not clamped.

- **`t`** — normalized animation progress

**Returns:** eased progress, potentially outside zero through one

#### easeInOutQuad

```java
public static double easeInOutQuad(double t)
```

Evaluates piecewise quadratic acceleration and deceleration for animation progress.
Input is intended for zero through one but is not clamped.

- **`t`** — normalized animation progress

**Returns:** eased progress, potentially outside zero through one

#### easeInCubic

```java
public static double easeInCubic(double t)
```

Evaluates cubic acceleration for animation progress.
Input is intended for zero through one but is not clamped.

- **`t`** — normalized animation progress

**Returns:** eased progress, potentially outside zero through one

#### easeOutCubic

```java
public static double easeOutCubic(double t)
```

Evaluates cubic deceleration for animation progress.
Input is intended for zero through one but is not clamped.

- **`t`** — normalized animation progress

**Returns:** eased progress, potentially outside zero through one

#### easeInOutCubic

```java
public static double easeInOutCubic(double t)
```

Evaluates piecewise cubic acceleration and deceleration for animation progress.
Input is intended for zero through one but is not clamped.

- **`t`** — normalized animation progress

**Returns:** eased progress, potentially outside zero through one

#### easeOutElastic

```java
public static double easeOutElastic(double t)
```

Evaluates exponentially decaying oscillation for animation progress.
Input is intended for zero through one but is not clamped. Endpoint values
are not explicitly snapped, and oscillation may overshoot one.

- **`t`** — normalized animation progress

**Returns:** eased progress, potentially outside zero through one

#### easeOutBounce

```java
public static double easeOutBounce(double t)
```

Evaluates piecewise parabolic bounce for animation progress.
Input is intended for zero through one but is not clamped.

- **`t`** — normalized animation progress

**Returns:** eased progress, potentially outside zero through one

#### gcd

```java
public static int gcd(int a, int b)
```

Uses Euclid's remainder algorithm and normalizes the resulting sign.
Both-zero input returns zero. A minimum-value magnitude that cannot be represented
remains negative after the final negation.

- **`a`** — first integer
- **`b`** — second integer

**Returns:** greatest common divisor, subject to signed-type overflow

#### gcd

```java
public static long gcd(long a, long b)
```

Uses Euclid's remainder algorithm and normalizes the resulting sign.
Both-zero input returns zero. A minimum-value magnitude that cannot be represented
remains negative after the final negation.

- **`a`** — first integer
- **`b`** — second integer

**Returns:** greatest common divisor, subject to signed-type overflow

#### lcm

```java
public static int lcm(int a, int b)
```

Computes a / gcd(a, b) * b using int arithmetic. The result preserves the
product's sign rather than forcing a positive magnitude, and multiplication may
overflow. Both-zero input causes division by zero.

- **`a`** — first integer
- **`b`** — second integer

**Returns:** computed common multiple

**Throws `ArithmeticException`:** if both arguments are zero

#### isEven

```java
public static boolean isEven(int n)
```

Tests the least significant bit, which also classifies negative integers.
No absolute-value conversion or division is required.

- **`n`** — integer to classify

**Returns:** whether n is even

#### isOdd

```java
public static boolean isOdd(int n)
```

Tests the least significant bit, which also classifies negative integers.
No absolute-value conversion or division is required.

- **`n`** — integer to classify

**Returns:** whether n is odd

#### reverseBits

```java
public static int reverseBits(int n)
```

Reverses all 32 bits by progressively exchanging adjacent bit groups.
The sign bit participates as ordinary data; this is bit reversal, not byte swapping.

- **`n`** — input bit pattern

**Returns:** bit-reversed pattern

#### equalsApprox

```java
public static boolean equalsApprox(double a, double b)
```

Tests absolute difference against an inclusive 1e-9 tolerance.
This is not a relative-error comparison; NaN and equal infinities return false.

- **`a`** — first value
- **`b`** — second value

**Returns:** whether absolute difference is within the fixed tolerance

#### equalsApprox

```java
public static boolean equalsApprox(float a, float b)
```

Tests absolute difference against an inclusive 1e-6 tolerance.
This is not a relative-error comparison; NaN and equal infinities return false.

- **`a`** — first value
- **`b`** — second value

**Returns:** whether absolute difference is within the fixed tolerance

#### isZero

```java
public static boolean isZero(double x)
```

Tests magnitude against a strict 1e-12 threshold. Signed zeros pass;
NaN and infinities fail. This does not scale tolerance with input magnitude.

- **`x`** — value to test

**Returns:** whether the value is sufficiently close to zero

#### isZero

```java
public static boolean isZero(float x)
```

Tests magnitude against a strict 1e-6 threshold. Signed zeros pass;
NaN and infinities fail. This does not scale tolerance with input magnitude.

- **`x`** — value to test

**Returns:** whether the value is sufficiently close to zero

#### log2

```java
public static double log2(double x)
```

Computes logarithm to base two. Zero yields negative infinity, negative
input yields NaN, and positive infinity remains infinite.

- **`x`** — logarithm argument

**Returns:** logarithmic value

#### log10

```java
public static double log10(double x)
```

Computes logarithm to base ten. Zero yields negative infinity, negative
input yields NaN, and positive infinity remains infinite.

- **`x`** — logarithm argument

**Returns:** logarithmic value

#### wrap

```java
public static int wrap(int value, int min, int max)
```

Wraps into an inclusive integer interval using a double remainder adjustment.
Use ordered bounds with representable width and intermediate arithmetic; this
implementation does not detect overflow.

- **`value`** — value to wrap
- **`min`** — inclusive lower bound
- **`max`** — inclusive upper bound

**Returns:** wrapped value for valid arithmetic

**Throws `ArithmeticException`:** if the computed interval width is zero

#### wrap

```java
public static double wrap(double value, double min, double max)
```

Wraps a finite value into a lower-inclusive, upper-exclusive interval using floor.
Use finite ordered bounds with positive width; degenerate or nonfinite inputs
can produce NaN and rounding may affect values near boundaries.

- **`value`** — value to wrap
- **`min`** — inclusive lower bound
- **`max`** — exclusive upper bound

**Returns:** wrapped value

#### approach

```java
public static float approach(float current, float target, float delta)
```

Moves toward the target by at most a nonnegative delta without overshooting.
Delta is not validated; a negative delta can move away from the target.

- **`current`** — current value
- **`target`** — desired value
- **`delta`** — nonnegative maximum change per call

**Returns:** updated value limited to the target in the direction of travel

#### approach

```java
public static double approach(double current, double target, double delta)
```

Moves toward the target by at most a nonnegative delta without overshooting.
Delta is not validated; a negative delta can move away from the target.

- **`current`** — current value
- **`target`** — desired value
- **`delta`** — nonnegative maximum change per call

**Returns:** updated value limited to the target in the direction of travel

#### applyDeadzone

```java
public static float applyDeadzone(float value, float deadzone)
```

Returns zero inside a strict magnitude threshold and subtracts the threshold
from surviving magnitudes. The remaining range is not rescaled to full strength;
use a nonnegative deadzone, since negative values are not rejected.

- **`value`** — signed input
- **`deadzone`** — nonnegative magnitude threshold

**Returns:** thresholded input with reduced surviving magnitude

</details>

## Related guides

- [Cameras and picking](cameras.md)
- [Raycast lighting and shape occlusion](raycast-lighting.md)
- [Frame and transform animation](animation.md)
- [Bit fields and flags](bits.md)
- [Existing joml migration guide](../joml-migration.md)
