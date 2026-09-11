# Raycast lighting and shape occlusion

Author: Albert Beaupre

[System manual](README.md)

## Purpose

This lighting family builds planar light geometry by casting against registered occluders. A RayHandler coordinates lights and rendering, while a RayCastWorld supplies collision queries. Use this path when shape-based visibility and explicit ray-generated light meshes fit the scene.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Light shapes | Point, cone, polygon, and vertex-cast lights express different emission regions. |
| Occluders | ShapeRaycastWorld registers geometry and indexes broad-phase bounds. |
| Light meshes | Visibility results become renderable geometry; soft-shadow meshes extend shadow edges. |
| Light maps | LightMapRenderer composites light output and supports intermediate rendering. |

## Getting started

1. Create a raycast world and register the shapes that should block light.
2. Create a handler and lights using that world's query contract.
3. Notify or rebuild the relevant occluder state after geometry changes.
4. Update light geometry and render/composite it in the intended position in your frame.

## Ownership and lifecycle

The world and lights have their own ownership contracts; do not assume adding a shape transfers its lifetime. Mutable shape points can require explicit invalidation. GPU mesh and light-map resources must be disposed on the context thread.

## Important behavior

- Broad-phase overlap is only a candidate test, not the exact ray result.
- Read ray parameter limits carefully; not every helper clamps to a finite segment.
- This family is distinct from Lighting2D's polar-shadow implementation.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`ConeLight`](#type-conelight)
- [`Light`](#type-light)
- [`LightMapRenderer`](#type-lightmaprenderer)
- [`LightMesh`](#type-lightmesh)
- [`LightOccluder`](#type-lightoccluder)
- [`LightTexture`](#type-lighttexture)
- [`PointLight`](#type-pointlight)
- [`PolygonLight`](#type-polygonlight)
- [`RayCastHit`](#type-raycasthit)
- [`RayCastWorld`](#type-raycastworld)
- [`RayHandler`](#type-rayhandler)
- [`ShapeRaycastWorld`](#type-shaperaycastworld)
- [`SoftShadowMesh`](#type-softshadowmesh)

<a id="type-conelight"></a>

### ConeLight

[Source](../../src/main/java/valthorne/graphics/lighting/ConeLight.java#L15)

Directional two-dimensional light whose rays cover an angular sector. The
center direction is counterclockwise from positive X; the aperture is the full
width, clamped to one through 179 degrees. Uniform rays are supplemented with
samples beside nearby blocking vertices to preserve silhouette corners.
Updates reuse endpoint storage and run only while dirty and active.

<details>
<summary>ConeLight operation reference (13 declarations)</summary>

#### Constructor

```java
public ConeLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y, float directionDegrees, float coneDegrees)
```

Creates an active, dirty cone light with copied color and borrowed handler.
Construction does not register the light; add it to the handler separately.

- **`rayHandler`** — handler supplying the occlusion world
- **`rays`** — base sample count, at least three
- **`color`** — light color to copy
- **`distance`** — radial reach in world units
- **`x`** — world center X
- **`y`** — world center Y
- **`directionDegrees`** — counterclockwise center direction from positive X
- **`coneDegrees`** — full angular width, clamped to one through 179 degrees

**Throws `NullPointerException`:** if handler or color is null

**Throws `IllegalArgumentException`:** if rays is below three

#### update

```java
    public void update()
```

Rebuilds active, dirty geometry using uniform, boundary, and occluder-vertex
angles. Sorting and compaction precede ray casting; a successful rebuild
clears dirty state. Inactive or clean lights retain their previous results.

#### computeRayEnd

```java
    protected void computeRayEnd(int index, float[] output)
```

Writes a uniformly sampled, unoccluded base-ray endpoint. The first and
last rays lie on the cone edges; extra vertex samples are added by update.

- **`index`** — base sample index between zero and rays minus one
- **`output`** — destination with at least two elements for world X and Y

#### getDirectionRadians

```java
public float getDirectionRadians()
```

Reads the stored center direction without rebuilding endpoints.

**Returns:** counterclockwise radians from positive X, normally in [0, 2 pi)

#### setDirectionRadians

```java
public void setDirectionRadians(float directionRadians)
```

Normalizes the direction to one revolution and marks geometry dirty even
when the resulting direction is unchanged. Supply a finite angle.

- **`directionRadians`** — counterclockwise radians from positive X

#### getDirectionDegrees

```java
public float getDirectionDegrees()
```

Converts the stored center direction to degrees without modifying it.

**Returns:** counterclockwise degrees from positive X, normally in [0, 360)

#### setDirectionDegrees

```java
public void setDirectionDegrees(float directionDegrees)
```

Converts degrees through the radian setter, wrapping the direction and
marking endpoints for a subsequent rebuild.

- **`directionDegrees`** — finite counterclockwise degrees from positive X

#### rotateRadians

```java
public void rotateRadians(float deltaRadians)
```

Adds a relative rotation through the direction setter, wrapping at a full
turn and marking the endpoint geometry dirty.

- **`deltaRadians`** — finite displacement; positive rotates counterclockwise

#### rotateDegrees

```java
public void rotateDegrees(float deltaDegrees)
```

Converts and applies a relative rotation to the stored center direction.
The new direction takes effect on the next active geometry update.

- **`deltaDegrees`** — finite displacement; positive rotates counterclockwise

#### getConeRadians

```java
public float getConeRadians()
```

Reads the full stored aperture, rather than its half-angle.

**Returns:** cone width in radians

#### setConeRadians

```java
public void setConeRadians(float coneRadians)
```

Clamps the full aperture to the radian equivalents of one and 179 degrees
and marks geometry dirty. NaN is not rejected and should not be supplied.

- **`coneRadians`** — requested full width in radians

#### getConeDegrees

```java
public float getConeDegrees()
```

Converts the stored full aperture to degrees without updating geometry.

**Returns:** cone width in degrees

#### setConeDegrees

```java
public void setConeDegrees(float coneDegrees)
```

Converts the full aperture to radians and applies the supported clamp,
marking endpoint geometry dirty.

- **`coneDegrees`** — requested full width in degrees

</details>

<a id="type-dynamicmesh2d"></a>

### DynamicMesh2D — internal support type

[Source](../../src/main/java/valthorne/graphics/lighting/DynamicMesh2D.java#L28)

Owns fixed-capacity CPU float storage, a vertex buffer, and a vertex array for
two-dimensional light geometry. Each vertex contains position XY, local XY, and RGBA
at attribute locations zero, one, and two. Subclasses write complete vertices and
upload them before rendering; storage does not grow automatically.

Creation, upload, drawing, and disposal require the owning graphics context.
Buffer and vertex-array helpers unbind to zero rather than restoring prior bindings.
Dispose once after use; the stored native names are not cleared for repeated calls.

<details>
<summary>DynamicMesh2D operation reference (8 declarations)</summary>

#### buffer

```java
protected final FloatBuffer buffer
```

Fixed-capacity interleaved CPU vertex storage.

#### Constructor

```java
protected DynamicMesh2D(int maxVertices, int drawMode)
```

Allocates fixed storage for eight floats per vertex and configures the vertex layout.
The caller supplies a valid positive capacity and OpenGL primitive mode.

- **`maxVertices`** — maximum writable vertex count
- **`drawMode`** — OpenGL draw primitive constant

#### beginWrite

```java
protected final void beginWrite()
```

Resets CPU buffer position and limit for overwriting. Existing GPU data and draw
count remain unchanged until finishWrite or clearVertices.

#### putVertex

```java
protected final void putVertex(float x, float y, float localX, float localY, float r, float g, float b, float a)
```

Appends one interleaved vertex to CPU storage without bounds growth or uploading.

- **`x`** — position X
- **`y`** — position Y
- **`localX`** — local shading coordinate X
- **`localY`** — local shading coordinate Y
- **`r`** — red component
- **`g`** — green component
- **`b`** — blue component
- **`a`** — alpha component

**Throws `java.nio.BufferOverflowException`:** if fixed storage is exhausted

#### finishWrite

```java
protected final void finishWrite()
```

Flips CPU storage, derives the complete eight-float vertex count, and uploads the
written range from offset zero. Unbinds the array-buffer target afterward.

#### clearVertices

```java
protected final void clearVertices()
```

Sets the draw count to zero without clearing CPU or GPU storage.

#### render

```java
public final void render()
```

Draws the uploaded vertex count with the configured primitive mode. A zero count
does nothing. The caller supplies shader and other draw state; this method binds
its VAO and resets that binding to zero afterward.

#### dispose

```java
public void dispose()
```

Deletes the owned vertex buffer and vertex array. Does not clear stored names or
track disposal, so the owner must call it once and avoid subsequent use.

</details>

<a id="type-light"></a>

### Light

[Source](../../src/main/java/valthorne/graphics/lighting/Light.java#L21)

Shared mutable configuration and ray results for two-dimensional lights.
Derived classes choose endpoint directions and rebuild active, dirty lights.
The handler is borrowed and construction does not register the light with it.
Position, range, ray count, occlusion mask, and x-ray changes dirty geometry;
color and soft-fringe settings affect drawing without recasting endpoints.

Colors are copied on input but exposed directly on access, as are endpoint
arrays. Callers must coordinate mutations with update and render operations on
the owning thread. This base class allocates no GPU resources.

<details>
<summary>Light operation reference (57 declarations)</summary>

#### ALL_MASK_BITS

```java
public static final  int ALL_MASK_BITS
```

Membership mask with all category bits enabled.

#### rayHandler

```java
protected final RayHandler rayHandler
```

Borrowed world and render integration handler.

#### color

```java
protected final Color color
```

Owned mutable light color.

#### rays

```java
protected int rays
```

Configured base ray count.

#### distance

```java
protected float distance
```

Radial extent in world units.

#### x

```java
protected float x
```

World center X.

#### y

```java
protected float y
```

World center Y.

#### active

```java
protected  boolean active
```

Whether the light participates in updates and rendering.

#### xray

```java
protected boolean xray
```

Whether world occlusion is bypassed.

#### soft

```java
protected  boolean soft
```

Whether soft-fringe rendering is enabled.

#### softnessLength

```java
protected  float softnessLength
```

Soft-fringe extent in world units.

#### endX

```java
protected float[] endX
```

Current endpoint X values.

#### endY

```java
protected float[] endY
```

Current endpoint Y values.

#### fractions

```java
protected float[] fractions
```

Current segment fractions, one for unoccluded rays.

#### categoryBits

```java
protected  int categoryBits
```

Application light category mask.

#### occlusionMaskBits

```java
protected  int occlusionMaskBits
```

Accepted occluder category mask.

#### dirty

```java
protected  boolean dirty
```

Whether endpoints need rebuilding.

#### Constructor

```java
protected Light(RayHandler rayHandler, int rays, Color color, float distance, float x, float y)
```

Retains the handler, copies color, and allocates ray arrays. Starts active and
dirty with soft shadows enabled. Position and distance are stored unchecked.

- **`rayHandler`** — associated handler
- **`rays`** — base ray count, at least three
- **`color`** — color to copy
- **`distance`** — radial extent
- **`x`** — world center X
- **`y`** — world center Y

**Throws `NullPointerException`:** if handler or color is null

**Throws `IllegalArgumentException`:** if rays is below three

#### update

```java
public abstract void update()
```

Refreshes endpoint geometry according to the derived light's direction pattern
and active/dirty policy. Does not itself submit GPU drawing.

#### computeRayEnd

```java
protected abstract void computeRayEnd(int index, float[] output)
```

Writes one unoccluded endpoint into reusable XY output for the base rebuild.

- **`index`** — base ray index
- **`output`** — destination with at least two elements

#### rebuild

```java
protected void rebuild()
```

Recasts base rays and sorts endpoints by polar angle, clearing dirty state after
successful completion.

#### rebuild

```java
protected final void rebuild(boolean sortEndpoints)
```

Computes and casts each base ray into existing storage, optionally sorts results,
then clears dirty state. The caller ensures sufficient endpoint-array length.

- **`sortEndpoints`** — whether to order endpoints by polar angle

#### sortEndpointsByAngle

```java
protected void sortEndpointsByAngle()
```

Orders endpoint arrays by atan2 around the center while keeping hit fractions
paired with coordinates. Allocates temporary angle, index, and sorted arrays.

#### applyRayResult

```java
protected final void applyRayResult(int index, float targetX, float targetY)
```

Stores a hit endpoint and fraction or the unobstructed endpoint with fraction one.
Reuses the light's scratch hit object.

- **`index`** — destination ray slot
- **`targetX`** — unoccluded X
- **`targetY`** — unoccluded Y

#### rayCast

```java
protected final boolean rayCast(float targetX, float targetY, RayCastHit outHit)
```

Casts from the center using this light's filtering context. X-ray mode or an absent
world yields false and clears non-null output.

- **`targetX`** — endpoint X
- **`targetY`** — endpoint Y
- **`outHit`** — optional reusable result

**Returns:** whether a hit occurred

#### rayCast

```java
protected final RayCastHit rayCast(float targetX, float targetY)
```

Queries the associated world, or returns null when occlusion is bypassed. The
world may reuse its result object, so it is not necessarily a durable snapshot.

- **`targetX`** — endpoint X
- **`targetY`** — endpoint Y

**Returns:** world result, or null when bypassed

#### getLightOccluders

```java
protected final List<LightOccluder> getLightOccluders()
```

Returns world-provided occluders without copying or additional filtering. An absent
world supplies an empty immutable list.

**Returns:** available world occluders

#### getRayHandler

```java
public RayHandler getRayHandler()
```

Returns the handler retained at construction without changing light registration.

**Returns:** borrowed associated handler

#### getColor

```java
public Color getColor()
```

Returns the live owned color. Direct changes affect rendering without recasting rays.

**Returns:** mutable light color

#### setColor

```java
public void setColor(Color color)
```

Copies replacement color without changing geometric dirty state.

- **`color`** — color to copy

**Throws `NullPointerException`:** if color is null

#### setColor

```java
public void setColor(float r, float g, float b, float a)
```

Replaces RGBA components without changing geometric dirty state.

- **`r`** — red component
- **`g`** — green component
- **`b`** — blue component
- **`a`** — alpha component

#### getRays

```java
public int getRays()
```

Returns the stored base ray count without updating endpoints.

**Returns:** base ray count

#### setRays

```java
public void setRays(int rays)
```

Changes base ray count, replaces endpoint storage, and dirties geometry. An equal
count does nothing; vertex-cast lights can expand storage during their update.

- **`rays`** — base count, at least three

**Throws `IllegalArgumentException`:** if rays is below three

#### getDistance

```java
public float getDistance()
```

Returns the stored radial extent without updating endpoints.

**Returns:** radial extent

#### setDistance

```java
public void setDistance(float distance)
```

Stores the radial extent without range validation.
Marks endpoints dirty for a later update.

- **`distance`** — replacement radial extent

#### getX

```java
public float getX()
```

Returns the stored world center X without updating endpoints.

**Returns:** world center X

#### setX

```java
public void setX(float x)
```

Stores the world center X without range validation.
Marks endpoints dirty for a later update.

- **`x`** — replacement world center X

#### getY

```java
public float getY()
```

Returns the stored world center Y without updating endpoints.

**Returns:** world center Y

#### setY

```java
public void setY(float y)
```

Stores the world center Y without range validation.
Marks endpoints dirty for a later update.

- **`y`** — replacement world center Y

#### setPosition

```java
public void setPosition(float x, float y)
```

Replaces both world coordinates and marks endpoints dirty for the next update.

- **`x`** — world center X
- **`y`** — world center Y

#### getCategoryBits

```java
public int getCategoryBits()
```

Returns the stored light membership mask without updating endpoints.

**Returns:** light membership mask

#### setCategoryBits

```java
public void setCategoryBits(int categoryBits)
```

Stores the light membership mask without range validation.
Leaves geometric dirty state unchanged.

- **`categoryBits`** — replacement light membership mask

#### getOcclusionMaskBits

```java
public int getOcclusionMaskBits()
```

Returns the stored accepted occluder mask without updating endpoints.

**Returns:** accepted occluder mask

#### setOcclusionMaskBits

```java
public void setOcclusionMaskBits(int occlusionMaskBits)
```

Stores the accepted occluder mask and dirties geometry if its value changed.

- **`occlusionMaskBits`** — replacement accepted-category mask

#### isActive

```java
public boolean isActive()
```

Returns active state without recomputing geometry.

**Returns:** current active flag

#### setActive

```java
public void setActive(boolean active)
```

Changes active state.
Does not independently dirty or rebuild endpoints.

- **`active`** — replacement flag

#### isXray

```java
public boolean isXray()
```

Returns occlusion bypass without recomputing geometry.

**Returns:** current xray flag

#### setXray

```java
public void setXray(boolean xray)
```

Changes occlusion bypass.
Marks endpoints dirty so a later update applies the changed policy.

- **`xray`** — replacement flag

#### isSoft

```java
public boolean isSoft()
```

Returns soft-fringe enablement without recomputing geometry.

**Returns:** current soft flag

#### setSoft

```java
public void setSoft(boolean soft)
```

Changes soft-fringe enablement.
Does not independently dirty or rebuild endpoints.

- **`soft`** — replacement flag

#### getSoftnessLength

```java
public float getSoftnessLength()
```

Returns the stored soft-fringe length without updating endpoints.

**Returns:** soft-fringe length

#### setSoftnessLength

```java
public void setSoftnessLength(float softnessLength)
```

Stores the soft-fringe length without range validation.
Leaves geometric dirty state unchanged.

- **`softnessLength`** — replacement soft-fringe length

#### getEndX

```java
public float[] getEndX()
```

Returns live endpoint X coordinates. Capacity changes replace this array; copy values
when retaining a snapshot outside the update/render cycle.

**Returns:** current endpoint X coordinates

#### getEndY

```java
public float[] getEndY()
```

Returns live endpoint Y coordinates. Capacity changes replace this array; copy values
when retaining a snapshot outside the update/render cycle.

**Returns:** current endpoint Y coordinates

#### getFractions

```java
public float[] getFractions()
```

Returns live hit fractions. Capacity changes replace this array; copy values
when retaining a snapshot outside the update/render cycle.

**Returns:** current hit fractions

#### ensureRayCapacity

```java
protected final void ensureRayCapacity(int count)
```

Replaces all three result arrays if their exact length differs from count. New
coordinates start at zero and fractions at one; previous results are discarded.

- **`count`** — required endpoint-array length

#### isDirty

```java
public boolean isDirty()
```

Returns pending rebuild state without recomputing geometry.

**Returns:** current dirty flag

</details>

<a id="type-lightmaprenderer"></a>

### LightMapRenderer

[Source](../../src/main/java/valthorne/graphics/lighting/LightMapRenderer.java#L23)

OpenGL helper for composing a two-dimensional light map and baking irradiance
into it. Draws a fullscreen triangle into the caller's current framebuffer and
viewport. Texture inputs are borrowed; this renderer owns only its shader
programs and VAO. Operations change depth, blend, program, texture, and VAO
state without restoring prior bindings. Create, draw, and dispose on a thread
with the appropriate current OpenGL context.

<details>
<summary>LightMapRenderer operation reference (4 declarations)</summary>

#### Constructor

```java
public LightMapRenderer()
```

Compiles the composite and bake programs and creates an empty VAO for the
vertex-ID fullscreen triangle. Requires a current OpenGL context; the
caller must dispose this renderer after its final use.

#### render

```java
public void render(int textureId)
```

Multiplies the current framebuffer by the supplied light texture using
destination-color blending. The caller supplies the framebuffer and viewport.
Leaves depth testing and blending disabled, texture unit zero active and
unbound, and the shader and VAO unbound; previous state is not restored.

- **`textureId`** — borrowed two-dimensional light-map texture

#### bake

```java
public void bake(int texId, float strength)
```

Additively blends strength-scaled irradiance into the current framebuffer.
Call while the light-map framebuffer is bound, before final composition.
Strength is forwarded unchecked. Leaves depth testing and blending disabled,
texture unit zero active and unbound, and shader and VAO unbound.

- **`texId`** — borrowed two-dimensional irradiance texture
- **`strength`** — multiplier applied by the bake shader

#### dispose

```java
public void dispose()
```

Deletes both owned shader programs and the fullscreen VAO in the current
OpenGL context. Do not render afterward; repeated disposal is not guarded.

</details>

<a id="type-lightmesh"></a>

### LightMesh

[Source](../../src/main/java/valthorne/graphics/lighting/LightMesh.java#L14)

Uploads a closed triangle fan for a two-dimensional light footprint. The first
vertex is the light center, followed by ordered ray endpoints and a repeated first
endpoint. Local shading coordinates are endpoint offsets divided by radius.
The mesh owns GPU resources through its base class and requires a graphics context.

<details>
<summary>LightMesh operation reference (2 declarations)</summary>

#### Constructor

```java
public LightMesh(int maxVertices)
```

Allocates a triangle-fan mesh with fixed vertex capacity. A fan with N endpoints
needs N+2 vertices, including its center and closing endpoint.

- **`maxVertices`** — capacity in vertices

#### setFan

```java
public void setFan(float centerX, float centerY, float radius, float[] endX, float[] endY, float r, float g, float b, float a)
```

Overwrites and uploads a closed fan using the shorter endpoint-array length.
Fewer than two endpoints clear the draw count. Endpoint order is not sorted here;
the caller supplies perimeter order. Zero radius produces zero local coordinates.

- **`centerX`** — world-space center X
- **`centerY`** — world-space center Y
- **`radius`** — divisor for local radial coordinates
- **`endX`** — ordered endpoint X coordinates
- **`endY`** — ordered endpoint Y coordinates
- **`r`** — red light component
- **`g`** — green light component
- **`b`** — blue light component
- **`a`** — alpha light component

**Throws `NullPointerException`:** if either endpoint array is null

**Throws `java.nio.BufferOverflowException`:** if fan exceeds fixed capacity

</details>

<a id="type-lightoccluder"></a>

### LightOccluder

[Source](../../src/main/java/valthorne/graphics/lighting/LightOccluder.java#L14)

Associates a borrowed geometric area with a collider identity and light-blocking
category mask. Geometry queries delegate directly to the area, so changes to the
area affect future queries and point-array ownership follows that implementation.
This wrapper does not register itself with a world or manage collider lifetime.

<details>
<summary>LightOccluder operation reference (9 declarations)</summary>

#### Constructor

```java
public LightOccluder(Area area)
```

Uses the area itself as collider identity and enables all category bits.

- **`area`** — non-null borrowed geometry

#### Constructor

```java
public LightOccluder(Area area, Object collider)
```

Retains geometry and optional collider identity with all category bits enabled.
A null collider falls back to the area itself.

- **`area`** — non-null borrowed geometry
- **`collider`** — application collision identity, or null

#### Constructor

```java
public LightOccluder(Area area, Object collider, int categoryBits)
```

Retains the area and category bits without copying geometry. Null collider identity
is replaced by the area; zero category bits blocks no non-null light masks.

- **`area`** — borrowed occlusion geometry
- **`collider`** — application identity, or null to use area
- **`categoryBits`** — occluder membership mask

**Throws `NullPointerException`:** if area is null

#### getArea

```java
public Area getArea()
```

Returns the live borrowed area. Geometry ownership stays with its original owner.

**Returns:** occlusion area

#### getCollider

```java
public Object getCollider()
```

Returns the retained collider identity used to associate hits with application data.

**Returns:** supplied collider or the area fallback

#### points

```java
public Vector2f[] points()
```

Delegates to the area's points method without an additional copy. Point ordering,
allocation, and mutability follow the concrete Area implementation.

**Returns:** area-provided vertices

#### getCategoryBits

```java
public int getCategoryBits()
```

Returns this occluder's membership mask without testing any light.

**Returns:** category bit mask

#### setCategoryBits

```java
public void setCategoryBits(int categoryBits)
```

Replaces category membership for subsequent blocking tests. Does not notify the
world or mark existing light geometry dirty.

- **`categoryBits`** — replacement membership mask

#### blocks

```java
public boolean blocks(Light light)
```

Tests for any shared bit between this category mask and the light's occlusion mask.
A null light is treated as an unfiltered query and always returns true.

- **`light`** — light supplying a mask, or null

**Returns:** whether this occluder is eligible to block the query

</details>

<a id="type-lighttexture"></a>

### LightTexture

[Source](../../src/main/java/valthorne/graphics/lighting/LightTexture.java#L24)

Adapts an RGBA16F lighting texture to the normal texture API with explicit native
ownership. The size-only constructor allocates storage; the ID constructor wraps
existing storage and records linear filtering metadata without applying parameters.
Placeholder CPU bytes are not a full image and must not be used as a pixel readback.

All native operations require the owning graphics context. Resize mutates even
borrowed texture storage. Dispose once: the native ID is not zeroed, and repeated
disposal of an owning wrapper is not guarded.

<details>
<summary>LightTexture operation reference (4 declarations)</summary>

#### Constructor

```java
public LightTexture(int width, int height)
```

Allocates an owned RGBA16F texture with linear filtering and clamp-to-edge wrapping.
Leaves the texture bound; dimension validity is delegated to the texture/GPU path.

- **`width`** — texture width in pixels
- **`height`** — texture height in pixels

#### Constructor

```java
public LightTexture(int textureID, int width, int height, boolean ownsGlTexture)
```

Wraps an existing texture with dimension metadata and a one-byte placeholder.
Does not allocate image storage or apply the recorded linear filter.

- **`textureID`** — existing OpenGL texture name
- **`width`** — reported texture width
- **`height`** — reported texture height
- **`ownsGlTexture`** — whether disposal deletes the native texture

#### resize

```java
public void resize(int width, int height)
```

Reallocates RGBA16F storage and replaces dimension metadata, discarding contents.
Keeps the same native ID and placeholder buffer, including for borrowed textures.

- **`width`** — replacement pixel width
- **`height`** — replacement pixel height

#### dispose

```java
    public void dispose()
```

Deletes the native texture only when ownership was requested, then clears data
and filter references. The stored texture ID is retained; dispose only once.

</details>

<a id="type-pointlight"></a>

### PointLight

[Source](../../src/main/java/valthorne/graphics/lighting/PointLight.java#L12)

Casts evenly spaced rays around a full circle to form a radial light footprint.
Active dirty lights rebuild endpoints; unchanged or inactive lights retain their
previous geometry. Occlusion and x-ray behavior follow the shared Light policy.

<details>
<summary>PointLight operation reference (3 declarations)</summary>

#### Constructor

```java
public PointLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y)
```

Initializes shared light state and endpoint storage. The constructor retains the
handler but does not register this light with its render list; geometry is rebuilt
by update when active and dirty.

- **`rayHandler`** — handler providing the occlusion world
- **`rays`** — base ray count, at least three
- **`color`** — color copied into the light
- **`distance`** — radial extent in world units
- **`x`** — world-space center X
- **`y`** — world-space center Y

**Throws `NullPointerException`:** if handler or color is null

**Throws `IllegalArgumentException`:** if rays is below three

#### update

```java
    public void update()
```

Recasts evenly spaced endpoints when active and dirty. Their circular order is
already suitable for the fan, so the base rebuild skips endpoint sorting.

#### computeRayEnd

```java
    protected void computeRayEnd(int index, float[] output)
```

Writes an unoccluded endpoint at index times one full turn divided by base rays.
Does not validate the index, array length, or radial extent.

- **`index`** — ray index in circular order
- **`output`** — destination with at least two elements for world X/Y

</details>

<a id="type-polygonlight"></a>

### PolygonLight

[Source](../../src/main/java/valthorne/graphics/lighting/PolygonLight.java#L14)

Refines a radial light footprint by adding rays at and around nearby occluder
vertices. Base rays maintain circular coverage while extra angles improve corner
boundaries. Only vertices within the light radius contribute; an occluder with no
nearby vertex is omitted from this refinement even if an edge crosses the radius.

<details>
<summary>PolygonLight operation reference (3 declarations)</summary>

#### Constructor

```java
public PolygonLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y)
```

Initializes shared light state and endpoint storage. The constructor retains the
handler but does not register this light with its render list; geometry is rebuilt
by update when active and dirty.

- **`rayHandler`** — handler providing the occlusion world
- **`rays`** — base ray count, at least three
- **`color`** — color copied into the light
- **`distance`** — radial extent in world units
- **`x`** — world-space center X
- **`y`** — world-space center Y

**Throws `NullPointerException`:** if handler or color is null

**Throws `IllegalArgumentException`:** if rays is below three

#### update

```java
    public void update()
```

When active and dirty, collects base circle angles plus three rays around each
eligible nearby occluder vertex. Filters category masks, sorts and compacts the
angles, casts endpoints, and clears dirty state. Skips inactive or clean lights.

#### computeRayEnd

```java
    protected void computeRayEnd(int index, float[] output)
```

Writes an unoccluded endpoint at index times one full turn divided by base rays.
Does not validate the index, array length, or radial extent.

- **`index`** — ray index in circular order
- **`output`** — destination with at least two elements for world X/Y

</details>

<a id="type-raycasthit"></a>

### RayCastHit

[Source](../../src/main/java/valthorne/graphics/lighting/RayCastHit.java#L11)

Reusable mutable result of a two-dimensional segment cast. Position, segment
fraction, hit status, and borrowed collider identity are independent stored values;
setters do not enforce consistency or range constraints. The query producer defines
coordinates and resets the object before reuse. Copy values before retaining results.

<details>
<summary>RayCastHit operation reference (14 declarations)</summary>

#### Constructor

```java
public RayCastHit()
```

Creates a miss at zero coordinates with fraction one and no collider identity.

#### Constructor

```java
public RayCastHit(float x, float y)
```

Creates a hit at the supplied coordinates with fraction one and no collider.
Does not derive a segment fraction from the position.

- **`x`** — hit X coordinate
- **`y`** — hit Y coordinate

#### isHit

```java
public boolean isHit()
```

Returns stored hit status without checking coordinates or collider identity.

**Returns:** whether a hit was recorded

#### setHit

```java
public void setHit(boolean hit)
```

Changes only hit status, leaving position, fraction, and collider unchanged.

- **`hit`** — replacement hit flag

#### getX

```java
public float getX()
```

Returns the stored hit X coordinate without interpreting or modifying the result.

**Returns:** hit X coordinate

#### setX

```java
public void setX(float x)
```

Replaces only the hit X coordinate. Other result fields remain unchanged;
no consistency or range validation is performed.

- **`x`** — replacement hit X coordinate

#### getY

```java
public float getY()
```

Returns the stored hit Y coordinate without interpreting or modifying the result.

**Returns:** hit Y coordinate

#### setY

```java
public void setY(float y)
```

Replaces only the hit Y coordinate. Other result fields remain unchanged;
no consistency or range validation is performed.

- **`y`** — replacement hit Y coordinate

#### getFraction

```java
public float getFraction()
```

Returns the stored segment parameter without interpreting or modifying the result.

**Returns:** segment parameter

#### setFraction

```java
public void setFraction(float fraction)
```

Replaces only the segment parameter. Other result fields remain unchanged;
no consistency or range validation is performed.

- **`fraction`** — replacement segment parameter

#### getCollider

```java
public Object getCollider()
```

Returns the stored borrowed collider identity without interpreting or modifying the result.

**Returns:** borrowed collider identity

#### setCollider

```java
public void setCollider(Object collider)
```

Replaces only the borrowed collider identity. Other result fields remain unchanged;
no consistency or range validation is performed.

- **`collider`** — replacement borrowed collider identity

#### set

```java
public void set(boolean hit, float x, float y, float fraction, Object collider)
```

Replaces the entire result without validation or copying the collider object.
Use a consistent coordinate space and segment fraction when populating a result.

- **`hit`** — whether the segment hit
- **`x`** — hit X coordinate
- **`y`** — hit Y coordinate
- **`fraction`** — segment parameter, conventionally zero through one
- **`collider`** — borrowed collision identity, or null

#### clear

```java
public void clear()
```

Restores a miss with zero coordinates, fraction one, and no collider. Does not
modify or dispose any previously referenced collider.

</details>

<a id="type-raycastworld"></a>

### RayCastWorld

[Source](../../src/main/java/valthorne/graphics/lighting/RayCastWorld.java#L14)

Supplies segment queries for two-dimensional light occlusion. Implementations
define geometry storage and hit-object reuse. The output-parameter overload allows
lighting code to reuse a result; the default adapter can still allocate through
the simpler query method. Queries and geometry preparation run synchronously.

<details>
<summary>RayCastWorld operation reference (5 declarations)</summary>

#### rayCast

```java
RayCastHit rayCast(float startX, float startY, float endX, float endY)
```

Queries a segment using the implementation's default filtering. Implementations
may return null or a result marked as a miss; callers must not assume a returned
mutable hit is an independent snapshot.

- **`startX`** — segment start X
- **`startY`** — segment start Y
- **`endX`** — segment end X
- **`endY`** — segment end Y

**Returns:** hit result, miss result, or null according to implementation

#### rayCast

```java
default RayCastHit rayCast(Light light, float startX, float startY, float endX, float endY)
```

Delegates to the unfiltered segment method. This default ignores the light;
implementations supporting light-specific masks should override it.

- **`light`** — requesting light, ignored by this default
- **`startX`** — segment start X
- **`startY`** — segment start Y
- **`endX`** — segment end X
- **`endY`** — segment end Y

**Returns:** implementation's segment result

#### rayCast

```java
default boolean rayCast(Light light, float startX, float startY, float endX, float endY, RayCastHit outHit)
```

Adapts the object-returning query into optional reusable output. A miss clears
non-null output; a hit copies scalar values and shares collider identity. Null
output requests only the boolean result. No allocation-free guarantee is made.

- **`light`** — requesting light passed to the query
- **`startX`** — segment start X
- **`startY`** — segment start Y
- **`endX`** — segment end X
- **`endY`** — segment end Y
- **`outHit`** — optional result storage

**Returns:** whether the returned query result reports a hit

#### prepare

```java
default void prepare()
```

Default no-op preparation hook. Implementations may refresh acceleration or
geometry state before a group of light queries.

#### getLightOccluders

```java
default List<LightOccluder> getLightOccluders()
```

Returns an empty immutable list by default. Worlds exposing polygon vertices
can override this to support lights that cast extra rays near occluder corners.

**Returns:** available occluders, empty in the default implementation

</details>

<a id="type-rayhandler"></a>

### RayHandler

[Source](../../src/main/java/valthorne/graphics/lighting/RayHandler.java#L41)

Screen-space two-dimensional lighting pipeline combining ray-cast light fans,
soft fringes, and an ambient light map. Coordinates correspond to the supplied
screen dimensions; the light map is multiplied over the default framebuffer
after scene rendering. The handler owns its OpenGL resources and borrows its
world and lights. All rendering and resource operations require a current
OpenGL context on the owning thread.

Lights cache endpoints until dirtied. When occluders change, invalidate the
affected lights explicitly. The render pass changes OpenGL state and assumes
the caller has set the viewport. Resize the handler alongside the surface.

```java
RayHandler lighting = new RayHandler(800, 600);
lighting.setAmbientLight(0.15f, 0.15f, 0.15f, 1f);
PointLight lamp = new PointLight(lighting, 64,
        new Color(1f, 0.9f, 0.7f, 1f), 200f, 400f, 300f);
lighting.addLight(lamp);
// Each frame, after drawing the scene with a matching viewport:
lighting.update();
lighting.render();
// At shutdown, while the OpenGL context is still current:
lighting.dispose();
```

<details>
<summary>RayHandler operation reference (12 declarations)</summary>

#### Constructor

```java
public RayHandler(int width, int height)
```

Creates shared meshes, compiles lighting programs, and allocates the light
framebuffer. Requires a current OpenGL context. Dimensions are passed to
texture allocation without application-level validation.

- **`width`** — light-map width and visible screen extent in pixels
- **`height`** — light-map height and visible screen extent in pixels

**Throws `IllegalStateException`:** if the framebuffer is incomplete

#### getRayCastWorld

```java
public RayCastWorld getRayCastWorld()
```

Returns the borrowed world consulted by this handler's lights.

**Returns:** configured occlusion world, or null for unobstructed rays

#### setRayCastWorld

```java
public void setRayCastWorld(RayCastWorld rayCastWorld)
```

Replaces the borrowed occlusion world without dirtying any light. Mark
existing lights dirty when the replacement must affect cached endpoints.

- **`rayCastWorld`** — new world, or null to disable world occlusion

#### setAmbientLight

```java
public void setAmbientLight(float r, float g, float b, float a)
```

Copies the supplied components into the light-map clear color. Values are
forwarded to the color object; no geometry recast is needed.

- **`r`** — ambient red
- **`g`** — ambient green
- **`b`** — ambient blue
- **`a`** — ambient alpha

#### getAmbientLight

```java
public Color getAmbientLight()
```

Exposes the live ambient color used when clearing the light map. Mutations
take effect on the next render without additional notification.

**Returns:** owned mutable ambient color; not a copy

#### addLight

```java
public void addLight(Light light)
```

Appends a non-null light unless the list already contains it. The light's
constructor-supplied handler is not changed or validated by registration.

- **`light`** — light to render with this handler

**Throws `NullPointerException`:** if light is null

#### removeLight

```java
public void removeLight(Light light)
```

Removes the first matching light from the render list without disposing it.
An absent value, including null in a normally managed list, has no effect.

- **`light`** — light to remove

#### getLights

```java
public List<Light> getLights()
```

Returns the live mutable render list. Direct changes bypass addLight's
null and duplicate checks; keep entries valid and avoid mutation during
update or render iteration.

**Returns:** backing list in draw order

#### update

```java
public void update()
```

Prepares the configured world, then updates active lights whose radial
bounds overlap the screen extent. Individual lights control dirty caching;
changing world geometry alone does not force clean lights to recast.

#### render

```java
public void render()
```

Clears the light framebuffer to ambient color, additively draws visible
active lights and optional fringes, then multiplies the default framebuffer
by the resulting light map. Dirty visible lights are updated before drawing.

The caller must establish a suitable viewport; this method does not set one.
It finishes with framebuffer zero bound and does not restore prior OpenGL
state. Draw the scene first, then composite lighting, then draw any UI that
should remain unaffected by the lighting pass.

#### resize

```java
public void resize(int width, int height)
```

Updates screen culling extents and reallocates the light-map texture when
either dimension changes. Equal dimensions are a no-op. The viewport and
light geometry are not updated; framebuffer zero is bound after resizing.

- **`width`** — requested pixel width
- **`height`** — requested pixel height

#### dispose

```java
public void dispose()
```

Releases the owned framebuffer, light texture, programs, and shared meshes.
Requires the owning OpenGL context. The borrowed world and registered lights
are retained; this handler must not be used afterward. Repeated disposal of
all subordinate resources is not guarded.

</details>

<a id="type-shaperaycastworld"></a>

### ShapeRaycastWorld

[Source](../../src/main/java/valthorne/graphics/lighting/ShapeRaycastWorld.java#L33)

Mutable polygon-boundary occlusion world backed by a uniform spatial grid.
Shapes are borrowed, duplicates are allowed, and category masks filter which
lights they block. Registration changes invalidate cached bounds; arbitrary
mutations of a shape do not. Set a non-null moving marker while geometry
changes to force prepare to rebuild all buckets. This mutable query/index
state is intended for coordinated use on one thread.

Queries test polygon edges rather than filled interiors. The implementation
uses segment bounds for broad-phase candidates but does not explicitly cap
the edge-intersection fraction at one; see the reusable rayCast overload for
that limitation.

```java
ShapeRaycastWorld world = new ShapeRaycastWorld();
world.addShape(wall); // Existing Shape in the light coordinate space.
world.setMoving(wall);
world.prepare();      // Rebuild after geometry changes.
RayCastHit hit = world.rayCast(0f, 0f, 100f, 100f);
world.setMoving(null);
// Also dirty affected lights before reusing their cached endpoints.
```

<details>
<summary>ShapeRaycastWorld operation reference (16 declarations)</summary>

#### addShape

```java
public void addShape(Shape shape)
```

Registers a borrowed shape with every light-occluder category enabled.
Null is ignored; repeated registration is allowed and creates another entry.

- **`shape`** — world-space shape to register

#### addShape

```java
public void addShape(Shape shape, int categoryBits)
```

Appends a borrowed shape and matching occluder, growing query storage and
invalidating the spatial index. Geometry is read from the live shape.

- **`shape`** — shape to register; null is ignored
- **`categoryBits`** — category mask tested against each light's occlusion mask

#### removeShape

```java
public void removeShape(Shape shape)
```

Removes every registration whose shape is the supplied object by identity.
Also clears the moving marker when it refers to that object and invalidates
the index. The shape itself is neither modified nor disposed.

- **`shape`** — registered object to remove; null is ignored

#### setShapeCategoryBits

```java
public boolean setShapeCategoryBits(Shape shape, int categoryBits)
```

Changes category masks for every identity-matching registration. Index
geometry is unchanged, and cached light endpoints are not invalidated.

- **`shape`** — registered object to match by identity
- **`categoryBits`** — replacement occluder category mask

**Returns:** true if at least one registration matched

#### getShapeCategoryBits

```java
public int getShapeCategoryBits(Shape shape)
```

Reads the category mask from the first identity-matching registration.

- **`shape`** — registered shape object

**Returns:** first matching mask, or zero when the shape is absent

#### clear

```java
public void clear()
```

Drops all registrations, bounds, and spatial buckets without modifying
borrowed shapes. Clears the moving marker and restarts query numbering.

#### getShapes

```java
public List<Shape> getShapes()
```

Returns an unmodifiable live view in registration order. Membership cannot
be changed through this view, but the shape objects remain mutable.

**Returns:** live borrowed shapes, including duplicate registrations

#### getMoving

```java
public Shape getMoving()
```

Returns the marker that keeps the spatial index rebuilding while non-null.
The marker need not identify a registered shape.

**Returns:** movement marker, or null

#### setMoving

```java
public void setMoving(Shape moving)
```

Stores the movement marker. While non-null, each prepare call rebuilds the
entire index; this is not a single-shape incremental update. Assigning null
does not itself dirty an otherwise clean index.

- **`moving`** — any non-null shape to force repeated rebuilds, or null to stop

#### getCellSize

```java
public float getCellSize()
```

Reads the spatial grid's cell width and height in world units.

**Returns:** configured cell size

#### setCellSize

```java
public void setCellSize(float cellSize)
```

Sets the grid scale and invalidates the index unless the value is unchanged.
Use a finite positive size; the check rejects zero and negatives but does
not explicitly reject NaN or positive infinity.

- **`cellSize`** — desired square-cell size in world units

**Throws `IllegalArgumentException`:** if cellSize is zero or negative

#### prepare

```java
    public void prepare()
```

Rebuilds all spatial buckets when dirty or when the moving marker is set.
A non-null marker keeps the index dirty for the next call. Live shape
mutations are not detected automatically while the index remains clean.

#### rayCast

```java
    public RayCastHit rayCast(float startX, float startY, float endX, float endY)
```

Allocates a hit record and queries without light-category filtering.
Uses the same bounds and intersection behavior as the reusable-output overload.

- **`startX`** — world-space ray origin X
- **`startY`** — world-space ray origin Y
- **`endX`** — world-space target X
- **`endY`** — world-space target Y

**Returns:** nearest accepted boundary hit, or null

#### rayCast

```java
    public RayCastHit rayCast(Light light, float startX, float startY, float endX, float endY)
```

Allocates a hit record and queries category-compatible occluders.
The temporary record is discarded on a miss.

- **`light`** — filtering light, or null to accept all categories
- **`startX`** — world-space ray origin X
- **`startY`** — world-space ray origin Y
- **`endX`** — world-space target X
- **`endY`** — world-space target Y

**Returns:** nearest accepted boundary hit, or null

#### rayCast

```java
    public boolean rayCast(Light light, float startX, float startY, float endX, float endY, RayCastHit outHit)
```

Prepares the index, scans cells overlapping the origin-to-target bounding
rectangle, and tests each compatible occluder at most once. Writes the
closest accepted edge intersection, or clears output on a miss. Polygon
interiors are not treated as immediate hits; parallel edges are skipped.

The edge solver caps its parameter by the closest hit so far, initially
Float.MAX_VALUE, rather than explicitly by one. Thus an accepted hit can
lie beyond the target even though candidates use the segment bounds.

- **`light`** — filtering light, or null to accept all categories
- **`startX`** — world-space origin X
- **`startY`** — world-space origin Y
- **`endX`** — world-space target X
- **`endY`** — world-space target Y
- **`outHit`** — reusable destination, or null for an existence-only query

**Returns:** true if an eligible edge intersection was found

#### getLightOccluders

```java
    public List<LightOccluder> getLightOccluders()
```

Exposes an unmodifiable live list of occluder wrappers in registration order.
The wrappers themselves remain mutable and refer to borrowed shapes.

**Returns:** live occluder view

</details>

<a id="type-shaperaycastworld-bounds"></a>

### ShapeRaycastWorld.Bounds — internal support type

[Source](../../src/main/java/valthorne/graphics/lighting/ShapeRaycastWorld.java#L578)

Reusable axis-aligned extent for one registered occluder. Validity is
separate from coordinates so empty geometry can reuse existing storage.

Coordinates describe world-space occluder geometry and are used only for broad-phase
rejection. An overlapping bound does not establish an exact ray intersection.

<a id="type-shaperaycastworld-intbag"></a>

### ShapeRaycastWorld.IntBag — internal support type

[Source](../../src/main/java/valthorne/graphics/lighting/ShapeRaycastWorld.java#L612)

Growable primitive index list for one spatial bucket. Only entries before
size are valid; capacity grows without boxing occluder indices.

Bucket contents can include duplicate indices. The enclosing spatial index controls
which entries are meaningful and rebuilds buckets when occluder placement changes.

<a id="type-softshadowmesh"></a>

### SoftShadowMesh

[Source](../../src/main/java/valthorne/graphics/lighting/SoftShadowMesh.java#L14)

Reusable GPU triangle mesh for radially fading shadow fringes. Each qualifying
edge between cyclic light-ray endpoints becomes a quad fading from its hit
alpha to zero. Geometry is replaced on every upload; the inherited mesh owns
the CPU staging buffer and OpenGL objects. Use on the owning rendering thread
with a current context and dispose after the last draw.

<details>
<summary>SoftShadowMesh operation reference (2 declarations)</summary>

#### Constructor

```java
public SoftShadowMesh(int maxVertices)
```

Allocates the CPU and OpenGL triangle storage used by subsequent rebuilds.
A current OpenGL context is required; release it with inherited dispose.

- **`maxVertices`** — initial vertex capacity

#### setTriangles

```java
public void setTriangles(float centerX, float centerY, float radius, float softnessLength, float[] endX, float[] endY, float[] fractions, float r, float g, float b, float a)
```

Replaces and uploads the fringe geometry around cyclic ray endpoints.
Each adjacent pair emits two triangles when at least one fraction is below
0.999 and neither endpoint coincides with the center. Inner alpha is zero
for unblocked endpoints; outer alpha is always zero. Extrusion follows
the radial direction by softnessLength, with no sign or range validation.
Local shader coordinates are divided by radius, or zero when radius is zero.

- **`centerX`** — world-space light center X
- **`centerY`** — world-space light center Y
- **`radius`** — scale for local shader coordinates
- **`softnessLength`** — radial fringe length in world units
- **`endX`** — ordered endpoint X values; determines the number of pairs
- **`endY`** — corresponding endpoint Y values, at least as long as endX
- **`fractions`** — corresponding ray fractions, at least as long as endX
- **`r`** — red component written to every vertex
- **`g`** — green component written to every vertex
- **`b`** — blue component written to every vertex
- **`a`** — inner alpha for blocked endpoints

**Throws `NullPointerException`:** if a required endpoint array is null

</details>

<a id="type-vertexcastlight"></a>

### VertexCastLight — internal support type

[Source](../../src/main/java/valthorne/graphics/lighting/VertexCastLight.java#L16)

Base for lights that accumulate, sort, and compact ray angles before casting.
Angle storage grows as needed and is reused across updates. Numerical deduplication
compares stored radians directly, so equivalent angles across a full-turn boundary
are not automatically merged. Derived lights choose which angles to collect.

<details>
<summary>VertexCastLight operation reference (10 declarations)</summary>

#### EPSILON

```java
protected static final  float EPSILON
```

Angular offset in radians for rays passing just beside an occluder vertex.

#### MIN_ANGLE_DELTA

```java
protected static final  float MIN_ANGLE_DELTA
```

Minimum retained numerical separation between sorted ray angles in radians.

#### Constructor

```java
protected VertexCastLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y)
```

Initializes shared light state and endpoint storage. The constructor retains the
handler but does not register this light with its render list; geometry is rebuilt
by update when active and dirty.

- **`rayHandler`** — handler providing the occlusion world
- **`rays`** — base ray count, at least three
- **`color`** — color copied into the light
- **`distance`** — radial extent in world units
- **`x`** — world-space center X
- **`y`** — world-space center Y

**Throws `NullPointerException`:** if handler or color is null

**Throws `IllegalArgumentException`:** if rays is below three

#### resetAngles

```java
protected final void resetAngles()
```

Clears the logical angle count while retaining allocated storage for reuse.

#### addAngle

```java
protected final void addAngle(float angle)
```

Appends a raw angle, growing storage when needed. Does not normalize its range,
reject non-finite values, or deduplicate until compaction.

- **`angle`** — ray direction in radians

#### angleAt

```java
protected final float angleAt(int index)
```

Reads angle storage directly without checking against the logical count.

- **`index`** — valid collected-angle index

**Returns:** stored radians

#### getAngleCount

```java
protected final int getAngleCount()
```

Returns the number of valid collected angles before or after compaction.

**Returns:** logical angle count

#### sortAndCompactAngles

```java
protected final void sortAndCompactAngles()
```

Sorts collected angles ascending and retains values separated from the last kept
angle by at least MIN_ANGLE_DELTA. Compacts in place without shrinking storage.
Angles are not wrapped modulo a full turn.

#### rebuildFromAngles

```java
protected final void rebuildFromAngles()
```

Sorts and compacts angles, sizes endpoint arrays to that count, and casts one ray
per angle using shared occlusion policy. Clears dirty state after completion.

#### isPotentialOccluder

```java
protected final boolean isPotentialOccluder(Vector2f[] points, float maxDistanceSquared)
```

Checks whether any non-null vertex lies within the squared radius around this
light. This is a vertex test, not a polygon-edge intersection test.

- **`points`** — candidate vertices, possibly null
- **`maxDistanceSquared`** — squared radial limit

**Returns:** true if at least one vertex is within the limit

</details>

## Related guides

- [Batched 2D lighting](lighting-2d.md)
- [Math and 2D geometry](math.md)
- [Textures, sprites, atlases, and batching](textures.md)
