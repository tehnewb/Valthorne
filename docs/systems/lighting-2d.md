# Batched 2D lighting

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Lighting2D renders visible planar lights in a batch and uses polar shadow data for occlusion. Separate light properties from occluder geometry, then let the renderer reuse results when the scene remains unchanged. This path suits many local lights without constructing a separate ray mesh for every visible contribution.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Point lights | Position, radius, color, and intensity define local illumination. |
| Occluder indexing | Occluder2D and its index identify geometry relevant to shadow updates. |
| Polar shadows | Angular shadow information describes blocking around a light. |
| Caching and composition | The renderer reuses compatible unchanged data and composites the light result. |

## Getting started

1. Create the renderer with the required output dimensions and current context.
2. Populate lights and occluders in the same world-coordinate convention.
3. Update changed light/occluder state before rendering.
4. Composite the produced lighting with scene color using the documented render entry point.

## Ownership and lifecycle

Keep the renderer's dimensions and world mapping synchronized with resize changes. Cached shadow output is only valid for the geometry state the renderer knows about.

## Important behavior

- A light outside the camera may be culled, but an occluder can still influence visible illumination.
- Changing a borrowed shape without invalidation can leave stale shadows.
- Choose the separate path-tracing guide for bounced-light accumulation.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Lighting2D`](#type-lighting2d)
- [`Occluder2D`](#type-occluder2d)
- [`PointLight2D`](#type-pointlight2d)
- [`PolarShadow2D`](#type-polarshadow2d)

<a id="type-lighting2d"></a>

### Lighting2D

[Source](../../src/main/java/valthorne/graphics/lighting2d/Lighting2D.java#L45)

Batched XY-world lighting with cached polar shadows and a scaled HDR light map.
Capture scene colors between beginScene and endScene; completion composites into
the framebuffer and viewport active at capture start. Changed visible lighting
uses one instanced draw; unchanged light maps skip that draw while scene
composition still occurs. Draw unlit UI after capture completion.

Owns GPU resources and borrows light/occluder objects. Their revisions drive
cache invalidation, so use their supported mutation APIs. Resource/configuration
operations require the creating OpenGL thread. Capture is not nestable on one
instance; finish existing batches before capture transitions.

```java
Lighting2D lighting = new Lighting2D();
lighting.addLight(new PointLight2D().setPosition(100, 80));
lighting.beginScene(0, 0, 800, 600, 800, 600);
try {
    // Draw and finish scene batches with matching XY-world coordinates.
    lighting.endScene();
} finally {
    lighting.cancelScene(); // No-op after successful completion.
}
lighting.close(); // At shutdown with the creating context still current.
```

<details>
<summary>Lighting2D operation reference (18 declarations)</summary>

#### Constructor

```java
public Lighting2D()
```

Creates capacity for 512 lights with 1024 angular samples per shadow row.
Scene/light textures are sized lazily; map resolution initially uses half scale.

#### Constructor

```java
public Lighting2D(int capacity, int shadowResolution)
```

Validates atlas dimensions, allocates staging buffers, and creates shaders and
GPU resources inside a selected-state restoration scope. Requires a current
OpenGL context; scene and map storage are allocated on first capture.

- **`capacity`** — registered-light limit, one through 4096
- **`shadowResolution`** — angular samples per light, 64 through 4096

**Throws `IllegalArgumentException`:** if these or device texture-size limits are exceeded

#### addLight

```java
public Lighting2D addLight(PointLight2D light)
```

Registers a borrowed light once by identity and reserves the first free atlas
row with its own CPU shadow cache. Invalidates the map; work waits until visible.

- **`light`** — non-null light to register

**Returns:** this system

**Throws `NullPointerException`:** if light is null

**Throws `IllegalStateException`:** if capacity is full or lifecycle/thread checks fail

#### removeLight

```java
public boolean removeLight(PointLight2D light)
```

Removes the identity-matching registration, frees its atlas slot, and invalidates
the map. Does not modify or dispose the light object.

- **`light`** — light to remove

**Returns:** true if a registration was removed

#### addOccluder

```java
public Lighting2D addOccluder(Occluder2D o)
```

Retains a non-null occluder unless already contained. The next lighting pass
detects membership/revision changes through its geometry fingerprint.

- **`o`** — occluder to register

**Returns:** this system

**Throws `NullPointerException`:** if o is null

#### removeOccluder

```java
public boolean removeOccluder(Occluder2D o)
```

Removes the first matching occluder without modifying it. The next geometry
fingerprint check determines index/shadow invalidation.

- **`o`** — occluder to remove

**Returns:** true when membership changed

#### setAmbient

```java
public Lighting2D setAmbient(Color color)
```

Validates finite nonnegative RGB, copies the color, and invalidates the map.
Map clear alpha is always one, regardless of the copied input alpha.

- **`color`** — non-null linear-space ambient color

**Returns:** this system

**Throws `NullPointerException`:** if color is null

**Throws `IllegalArgumentException`:** if RGB is negative or non-finite

#### setResolutionScale

```java
public Lighting2D setResolutionScale(float scale)
```

Sets the map-size fraction applied during the next capture resize. Scene color
remains full resolution; map dimensions are rounded with a one-pixel minimum.

- **`scale`** — finite fraction from 0.25 through one

**Returns:** this system

**Throws `IllegalArgumentException`:** if scale is outside the supported range

#### setExposure

```java
public Lighting2D setExposure(float value)
```

Sets a positive finite composition multiplier without invalidating lighting
caches; exposure is applied after light-map generation.

- **`value`** — requested exposure

**Returns:** this system

**Throws `IllegalArgumentException`:** if value is nonpositive or non-finite

#### getVisibleLightCount

```java
public int getVisibleLightCount()
```

Reads lights passing enabled, contribution, and XY view-bounds checks in the
most recent pass. Does not count only unshadowed or actually visible pixels.

**Returns:** last visible light count

#### getLastLightDrawCalls

```java
public int getLastLightDrawCalls()
```

Reads light-only instanced draws: zero for cache reuse or no visible lights,
one for a refreshed map containing lights. Excludes the fullscreen composite.

**Returns:** last light draw count

#### getShadowUploadCount

```java
public long getShadowUploadCount()
```

Reads cumulative atlas-row uploads, excluding unchanged and shadow-disabled rows.

**Returns:** completed shadow upload count

#### getLightMapRenderCount

```java
public long getLightMapRenderCount()
```

Reads cumulative map refreshes, including ambient-only clears. Cache reuse
leaves this count unchanged.

**Returns:** map refresh count

#### getLightTextureId

```java
public int getLightTextureId()
```

Exposes the owned HDR texture as a borrowed identifier. Storage appears on first
capture and can be resized later; closing the system invalidates this resource.

**Returns:** light-map texture identifier; do not delete it

#### beginScene

```java
public void beginScene(float minX, float minY, float worldWidth, float worldHeight, int pixelWidth, int pixelHeight)
```

Saves selected GL state, sizes capture targets, and binds/clears the scene-color
framebuffer. Defines XY lighting bounds, sets a full capture viewport, disables
scissor/sRGB, and enables all color writes. Finish existing batches first.
Failure cancels capture and restores covered state; success requires end/cancel.

- **`minX`** — finite lower world X
- **`minY`** — finite lower world Y
- **`worldWidth`** — positive finite world extent X
- **`worldHeight`** — positive finite world extent Y
- **`pixelWidth`** — positive capture width within device texture limits
- **`pixelHeight`** — positive capture height within device texture limits

**Throws `IllegalStateException`:** if already capturing or lifecycle/thread checks fail

**Throws `IllegalArgumentException`:** if world or pixel dimensions are invalid

#### endScene

```java
public void endScene()
```

Finishes capture, refreshes changed lighting, and composites into the saved
framebuffer/viewport. Scene batches must already be finished. Capture state is
cleared before rendering; covered GL state is restored in finally on success
or failure. The saved destination receives one fullscreen composition draw.

**Throws `IllegalStateException`:** if no capture is active or lifecycle/thread checks fail

#### cancelScene

```java
public void cancelScene()
```

Restores active capture state without drawing or deleting textures. An inactive
capture is a no-op after lifecycle/thread validation.

#### close

```java
    public void close()
```

Cancels capture, releases owned shaders/textures/framebuffers/buffers/VAO, clears
registrations, and marks disposed. Borrowed lights and occluders remain alive.
After successful disposal, repeated calls return immediately.

</details>

<a id="type-lighting2d-entry"></a>

### Lighting2D.Entry — internal support type

[Source](../../src/main/java/valthorne/graphics/lighting2d/Lighting2D.java#L552)

Registration tying one borrowed light to a reserved atlas row and owned polar
shadow cache. Revision markers control reconsideration when the light is visible.
Removal releases the row for a subsequent registration.

<a id="type-lighting2d-state"></a>

### Lighting2D.State — internal support type

[Source](../../src/main/java/valthorne/graphics/lighting2d/Lighting2D.java#L581)

Selected OpenGL snapshot extending shared render state with framebuffer bindings,
viewport, clear color, write mask, scissor/sRGB enablement, and samplers zero/one.
It owns no GPU objects and does not preserve arbitrary unlisted context state.
Keep captured object identifiers alive until restoration.

<details>
<summary>Lighting2D.State operation reference (1 declarations)</summary>

#### close

```java
public void close()
```

Restores captured supplemental state and then the shared render snapshot.
Repeated calls reapply original values; no closed flag is maintained.
Captured GPU objects must remain alive in the same current context.

</details>

<a id="type-occluder2d"></a>

### Occluder2D

[Source](../../src/main/java/valthorne/graphics/lighting2d/Occluder2D.java#L28)

Closed polygon used to block light in the XY world plane. Supply a simple
convex or concave boundary without holes; the final vertex connects back to
the first automatically. Local coordinates are copied at construction and
remain fixed, while a separate translation positions the polygon in the world.

```java
Occluder2D wall = Occluder2D.rectangle(20, -10, 4, 20)
        .setCategory(2);
lighting.addOccluder(wall);
light.setOcclusionMask(2);
```

Construction checks vertex count, finite coordinates and nonzero signed
area, but does not establish that edges never intersect. Callers are responsible
for supplying a simple boundary. Either winding order is accepted. Coordinates
and translation use the same world units as PointLight2D.

Translation starts at zero and category defaults to all bits set. Position
and category setters advance a revision when values change, allowing lighting
updates to reconsider affected shadows. This object owns no rendering resource
and is not synchronized; avoid mutation while lighting reads its geometry.

<details>
<summary>Occluder2D operation reference (6 declarations)</summary>

#### Constructor

```java
public Occluder2D(float... xy)
```

Copies a polygon boundary and computes its local axis-aligned bounds.
At least three XY pairs are required. The absolute shoelace sum, which is
twice signed area, must be at least 1e-8; no self-intersection test is made.
Subsequent changes to the supplied array do not affect this polygon.

- **`xy`** — alternating local X and Y coordinates in boundary order

**Throws `NullPointerException`:** if xy is null

**Throws `IllegalArgumentException`:** if fewer than three pairs or an odd count
is supplied, a coordinate is non-finite,
or the absolute doubled area is below 1e-8

#### rectangle

```java
public static Occluder2D rectangle(float x, float y, float width, float height)
```

Creates an axis-aligned rectangle with local corners from (0,0) through
(width,height), then translates that local origin to the requested position.
Polygon area validation also applies, so extremely small rectangles can be
rejected despite positive dimensions.

- **`x`** — the finite world X coordinate of the local origin
- **`y`** — the finite world Y coordinate of the local origin
- **`width`** — the finite positive X extent in world units
- **`height`** — the finite positive Y extent in world units

**Returns:** a new rectangular occluder with all category bits enabled

**Throws `IllegalArgumentException`:** if a coordinate or dimension is non-finite,
a dimension is not positive, or area is too small

#### setPosition

```java
public Occluder2D setPosition(float x, float y)
```

Changes world translation without rewriting local vertices or bounds.
Both coordinates are validated before assignment, and a changed coordinate
advances revision once; an equal position leaves revision unchanged.

- **`x`** — the finite world X translation
- **`y`** — the finite world Y translation

**Returns:** this occluder for chaining

**Throws `IllegalArgumentException`:** if either coordinate is non-finite

#### setCategory

```java
public Occluder2D setCategory(int bits)
```

Assigns category bits used for light-mask filtering. Any shared bit allows
occlusion; zero matches no light mask. A different mask advances revision,
while reassigning the current mask is a no-op.

- **`bits`** — the category bitmask, with all integer values permitted

**Returns:** this occluder for chaining

#### getX

```java
public float getX()
```

Returns the X translation applied to every local vertex, not the world
minimum X unless the polygon's local minimum is zero.

**Returns:** the world X translation

#### getY

```java
public float getY()
```

Returns the Y translation applied to every local vertex, independently of
the polygon's local minimum and maximum coordinates.

**Returns:** the world Y translation

</details>

<a id="type-occluderindex2d"></a>

### OccluderIndex2D — internal support type

[Source](../../src/main/java/valthorne/graphics/lighting2d/OccluderIndex2D.java#L26)

Broad-phase XY grid used to reduce candidate occluders for radial shadow maps.
Fixed 256-world-unit cells contain borrowed polygon references based on their
translated axis-aligned bounds. Polygons spanning more than 17 cells along
either axis are kept in a separate list included in every ordinary query.

The owning Lighting2D detects geometry changes and calls rebuild; this
index does not observe occluder movement or membership changes itself. Query
results are conservative candidates. The shadow builder still checks category
masks and actual bounds overlap before processing polygon edges.

A query spanning more than 33 cells along either axis returns the supplied
full list rather than visiting every grid cell. Ordinary queries reuse one
mutable result list, so callers must consume results before querying again
and must not modify returned lists. This helper is not thread-safe or reentrant.

<a id="type-pointlight2d"></a>

### PointLight2D

[Source](../../src/main/java/valthorne/graphics/lighting2d/PointLight2D.java#L32)

Mutable point or cone light for the XY world plane used by `Lighting2D`.
Position, influence radius and source radius use world units; cone direction
and half angles use radians. RGB emission is linear and independent of alpha.
This object stores configuration and cache revisions, not GPU resources.

Defaults describe an enabled white omnidirectional light at the origin:
influence radius 250, intensity 2, source radius 5, shadows enabled and all
occluder categories accepted. Both cone half angles initially equal PI.

```java
PointLight2D light = new PointLight2D()
        .setPosition(100, 80)
        .setRadius(200)
        .setIntensity(3)
        .setCone(0, 0.3f, 0.6f);
lighting.addLight(light);
```

Setters validate before changing state. Geometry-related changes also advance
the shadow revision; appearance changes advance only the general revision.
Color and cone assignments always advance that revision, even for equal inputs.
Use setters rather than changing package fields so rendering caches can detect
updates. Access is not synchronized; avoid mutation during lighting updates.

<details>
<summary>PointLight2D operation reference (12 declarations)</summary>

#### getX

```java
public float getX()
```

Reads the horizontal world position without changing revision counters.

**Returns:** the light center's X coordinate in world units

#### getY

```java
public float getY()
```

Reads the vertical coordinate within the XY world plane.

**Returns:** the light center's Y coordinate in world units

#### getRadius

```java
public float getRadius()
```

Returns the influence radius used for lighting coverage and occluder queries.
This is separate from the source radius used for soft-shadow appearance.

**Returns:** the positive influence radius in world units

#### setRadius

```java
public PointLight2D setRadius(float radius)
```

Changes the light's influence radius. A changed radius advances both
revision counters so shadow geometry is reconsidered for the new extent.

- **`radius`** — the finite positive radius in world units

**Returns:** this light for chaining

**Throws `IllegalArgumentException`:** if radius is non-finite or not positive

#### setPosition

```java
public PointLight2D setPosition(float x, float y)
```

Moves the light center and invalidates lighting and shadow geometry when
either coordinate changes. Both inputs are checked before any assignment.

- **`x`** — the finite world X coordinate
- **`y`** — the finite world Y coordinate

**Returns:** this light for chaining

**Throws `IllegalArgumentException`:** if either coordinate is non-finite

#### setColor

```java
public PointLight2D setColor(Color c)
```

Copies linear RGB emission components, ignoring alpha. Components may exceed
one but must be finite and nonnegative. The Color reference is not retained;
every successful assignment advances the general revision only.

- **`c`** — the source color with valid RGB components

**Returns:** this light for chaining

**Throws `NullPointerException`:** if c is null

**Throws `IllegalArgumentException`:** if an RGB component is non-finite or negative

#### setIntensity

```java
public PointLight2D setIntensity(float value)
```

Sets the nonnegative emission multiplier. Zero removes its emitted intensity
without changing the enabled flag. A changed value advances general revision.

- **`value`** — the finite nonnegative intensity

**Returns:** this light for chaining

**Throws `IllegalArgumentException`:** if value is non-finite or negative

#### setSourceRadius

```java
public PointLight2D setSourceRadius(float value)
```

Sets the source size used by lighting to control shadow softness, independently
of influence radius. Zero is permitted. Changes affect appearance revision
without rebuilding the cached occluder geometry.

- **`value`** — the finite nonnegative source radius in world units

**Returns:** this light for chaining

**Throws `IllegalArgumentException`:** if value is non-finite or negative

#### setCone

```java
public PointLight2D setCone(float direction, float inner, float outer)
```

Assigns cone direction and half angles. The inner angle marks full cone
contribution and the outer angle bounds its falloff; equal angles produce
a hard edge. Setting both to PI produces omnidirectional coverage. Direction
is stored without wrapping, and every successful call advances general revision.

- **`direction`** — the finite cone direction in radians in the XY plane
- **`inner`** — the finite nonnegative inner half angle in radians
- **`outer`** — the finite positive outer half angle, at most PI

**Returns:** this light for chaining

**Throws `IllegalArgumentException`:** if inputs are non-finite, inner is negative,
outer is not positive, or inner exceeds outer or outer exceeds PI

#### setCastsShadows

```java
public PointLight2D setCastsShadows(boolean enabled)
```

Enables or disables occluder shadows while retaining the light's emission
settings. A changed flag advances both general and shadow revisions.

- **`enabled`** — whether this light should cast occluder shadows

**Returns:** this light for chaining

#### setEnabled

```java
public PointLight2D setEnabled(boolean enabled)
```

Controls participation in lighting without discarding configuration.
Changing this flag advances only the general revision.

- **`enabled`** — whether the renderer should consider this light

**Returns:** this light for chaining

#### setOcclusionMask

```java
public PointLight2D setOcclusionMask(int mask)
```

Selects occluder categories by bit intersection. An occluder participates
when its category shares any bit with this mask; zero excludes all categories
and -1 accepts all. Changes invalidate both revision counters.

- **`mask`** — the accepted category bitmask, with all integer values permitted

**Returns:** this light for chaining

</details>

<a id="type-polarshadow2d"></a>

### PolarShadow2D

[Source](../../src/main/java/valthorne/graphics/lighting2d/PolarShadow2D.java#L34)

CPU cache of nearest occluder distances around a 2D point light. The full
circle is divided into equally sized angular bins, sampled at their centers
from zero through 2 * PI in the XY plane. Stored distances are divided by
the light's influence radius: one means no nearer blocker, and zero is used
for every bin when the light is inside a participating polygon.

```java
PolarShadow2D shadow = new PolarShadow2D(512);
PointLight2D light = new PointLight2D().setRadius(100);
Occluder2D wall = Occluder2D.rectangle(20, -10, 4, 20);
boolean rebuilt = shadow.update(light, java.util.List.of(wall));
float normalizedDistance = shadow.getDistance(0);
```

Each segment updates only the angular bins it spans, including wraparound.
An endpoint-distance fallback conservatively covers narrow obstacles when a
bin-center ray misses their segment. This is a discretized shadow representation,
not an exact continuous visibility query.

Update fingerprints include light identity and shadow revision plus ordered
identities and revisions of relevant occluders. Unchanged fingerprints reuse
existing data. Color, intensity and other appearance-only light edits do not
rebuild geometry. Use configuration setters so revision changes remain visible
to this cache. The object is mutable, unsynchronized and owns no GPU resource.

<details>
<summary>PolarShadow2D operation reference (4 declarations)</summary>

#### Constructor

```java
public PolarShadow2D(int resolution)
```

Allocates fixed-size depth and direction arrays and precomputes bin-center
rays. Depth storage initially contains zeros; call update before interpreting
it as a shadow map. Resolution need not be a power of two.

- **`resolution`** — the angular bin count, inclusive range 64 through 4096

**Throws `IllegalArgumentException`:** if resolution is outside the supported range

#### getRebuildCount

```java
public long getRebuildCount()
```

Reports completed geometry rebuilds since construction. Cached updates do
not increment this diagnostic counter, even though they scan candidate geometry.

**Returns:** the number of updates that rebuilt distance data

#### getDistance

```java
public float getDistance(int bin)
```

Reads a normalized radial depth from current storage. Multiply by the light
radius used in the last rebuild to recover world distance. Before the first
update, this returns the array's initial zero value rather than a valid map.

- **`bin`** — the zero-based angular sample index

**Returns:** the cached distance relative to the last light radius

**Throws `ArrayIndexOutOfBoundsException`:** if bin is outside the allocated samples

#### update

```java
public boolean update(PointLight2D light, List<Occluder2D> occluders)
```

Rebuilds the map only when the computed geometry fingerprint changes.
Candidate occluders participate when their categories intersect the light
mask and their bounds overlap its influence square. Changes to excluded
occluders do not affect the fingerprint; reordering included ones can.

A rebuild first resets all depths to one. If shadows are enabled, edges
reduce those depths, or an enclosing occluder sets the entire map to zero.
Disabled shadows retain ones. The light's enabled flag and cone settings
are not applied here; the rendering layer handles their contribution.

The supplied list and objects are read directly, not retained as a copied
geometry snapshot. Do not mutate them during this call. Fingerprinting is
hash-based rather than a full equality comparison.

- **`light`** — the nonnull light with valid geometry settings
- **`occluders`** — the nonnull candidate list containing no null entries

**Returns:** true if distance data was rebuilt; false if its fingerprint matched

**Throws `NullPointerException`:** if light, the list or a list entry is null

</details>

## Related guides

- [Raycast lighting and shape occlusion](raycast-lighting.md)
- [2D and 3D path tracing](path-tracing.md)
- [Textures, sprites, atlases, and batching](textures.md)
- [Existing lighting guide](../lighting.md)
