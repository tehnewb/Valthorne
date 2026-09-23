# Raster 3D lighting and shadow maps

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Raster 3D lighting combines material shading, directional shadows, and point lights. LightGrid3D groups local lights into screen tiles so shading does not inspect every light everywhere. ShadowMap3D renders directional depth information for later light evaluation.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Tiled lights | Screen-space light lists reduce per-fragment light search work. |
| Material response | Lighting uses material inputs such as tint, roughness, metallic response, and emission. |
| Directional depth | A shadow map stores visibility from the light's view. |
| Softness and bias | Sampling radius and depth offsets control the appearance and artifacts of shadows. |

## Getting started

1. Prepare the camera and material-bearing renderables.
2. Configure point lights and directional lighting/shadow settings.
3. Render required shadow data before shading the main scene.
4. Keep light-grid and target dimensions synchronized with the active camera and viewport.

## Ownership and lifecycle

The renderer owns its GPU support resources and borrows scene geometry/materials. A shadow-map texture is tied to its owner. Resize or dispose only when no later pass still expects the old output.

## Important behavior

- Shadow softness is expressed in shadow-map texels rather than world units.
- Increase shadow coverage and resolution deliberately: spreading a fixed map over more world space reduces detail.
- LightingShader3D compatibility behavior and tiled-lighting behavior are not interchangeable contracts.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`LightGrid3D`](#type-lightgrid3d)
- [`Lighting3D`](#type-lighting3d)
- [`PointLight3D`](#type-pointlight3d)
- [`ShadowMap3D`](#type-shadowmap3d)

<a id="type-lightgrid3d"></a>

### LightGrid3D

[Source](../../src/main/java/valthorne/graphics/lighting3d/LightGrid3D.java#L23)

CPU light packer and conservative screen-tile culler for tiled forward shading.
Each active light occupies eight floats: world XYZ, range, linear RGB, and
intensity. Each tile occupies STRIDE integers: a count followed by light indices,
or a negative count meaning test every active light. Overflow uses that fallback
instead of silently dropping contributions. No depth readback is required.

Update expects rebuilt camera matrices and frustum. Input values and camera
state are cached to avoid rebuilding unchanged data; storage is reused except
when tile-grid dimensions change. The mutable arrays and scratch state belong
to one coordinated rendering thread. Tile Y follows projected bottom-to-top NDC.

<details>
<summary>LightGrid3D operation reference (13 declarations)</summary>

#### MAX_LIGHTS

```java
public static final  int MAX_LIGHTS
```

Maximum input lights, inline indices per tile, and integer tile-record stride.

#### LIGHTS_PER_TILE

```java
public static final  int LIGHTS_PER_TILE
```

Maximum input lights, inline indices per tile, and integer tile-record stride.

#### STRIDE

```java
public static final  int STRIDE
```

Maximum input lights, inline indices per tile, and integer tile-record stride.

#### isTiled

```java
public boolean isTiled()
```

Reads whether the next update assigns lights to individual tiles.

**Returns:** requested culling mode

#### setTiled

```java
public LightGrid3D setTiled(boolean enabled)
```

Selects tiled culling or all-active-light fallback without rebuilding immediately.
The next update detects the mode change and regenerates tile headers.

- **`enabled`** — true to cull per tile, false for the diagnostic fallback

**Returns:** this grid

#### getActiveLightCount

```java
public int getActiveLightCount()
```

Reads the last update's light count after zero-contribution and frustum rejection.

**Returns:** number of packed active lights

#### getColumns

```java
public int getColumns()
```

Reads the number of screen-tile columns from the last successful update.

**Returns:** column count, initially zero

#### getRows

```java
public int getRows()
```

Reads the number of screen-tile rows from the last successful update.

**Returns:** row count, initially zero

#### getTileSize

```java
public int getTileSize()
```

Reads the configured square tile extent used by the latest grid update.

**Returns:** tile size in pixels, initially zero

#### getOverflowTileCount

```java
public int getOverflowTileCount()
```

Reads tiles that exceeded their fixed light-index capacity during tiled culling.
Disabling tiled culling uses fallback headers but does not count them as overflow.

**Returns:** overflow tile count from the latest rebuild

#### getAverageLightsPerTile

```java
public double getAverageLightsPerTile()
```

Reads average effective light tests per tile, counting fallback tiles as all
active lights rather than a negative header value.

**Returns:** last rebuild's average, initially zero

#### getTileLightCount

```java
public int getTileLightCount(int x, int y)
```

Reads a tile's effective light count, expanding a negative fallback header
to the complete active-light count. Does not return the raw storage sentinel.

- **`x`** — zero-based tile column
- **`y`** — zero-based tile row

**Returns:** number of lights the shader should test

**Throws `IndexOutOfBoundsException`:** if either tile coordinate is outside the grid

#### update

```java
public boolean update(Camera3D camera, List<PointLight3D> input, int width, int height, int tileSize)
```

Validates and packs input values, then rebuilds only when viewport dimensions,
tile size, culling mode, camera matrix, or packed inputs change. Rejects black,
zero-intensity, and frustum-excluded lights. Projects each remaining sphere's
bounding-box corners to screen tiles; crossing the eye plane covers all tiles.
Alpha and shadow flags are not part of packed data.

- **`camera`** — camera with current combined matrix and frustum
- **`input`** — non-null light list of at most MAX_LIGHTS non-null entries
- **`width`** — positive viewport width in pixels
- **`height`** — positive viewport height in pixels
- **`tileSize`** — positive square tile size in pixels

**Returns:** true when cached geometry/data was rebuilt and should be uploaded

**Throws `IllegalArgumentException`:** for invalid dimensions, excess lights, non-finite
packed values, or negative RGB

**Throws `NullPointerException`:** if camera, input, or a light entry is null

**Throws `ArithmeticException`:** if tile storage size multiplication overflows

</details>

<a id="type-lighting3d"></a>

### Lighting3D

[Source](../../src/main/java/valthorne/graphics/lighting3d/Lighting3D.java#L41)

OpenGL 3.3 tiled forward-lighting resources for linear-space surface shading.
Owns texture-buffer objects for packed point lights and tile indices, while
borrowing light objects and camera state. ModelBatch3D prepares it through
MeshRenderState3D before drawing; unchanged inputs avoid repeated uploads.
The diffuse environment blends sky and ground using Z-up surface normals.

Create, prepare, bind, mutate, and close on the creating OpenGL thread.
Binding uses texture units three and four and leaves their restoration to the
batch. The grid's all-visible mode provides a diagnostic reference path with
the same shading and no per-tile rejection.

```java
Lighting3D lighting = new Lighting3D();
lighting.addLight(new PointLight3D().setPosition(0, 0, 5));
// With the intended viewport and rebuilt camera already active:
lighting.prepare(camera);
// The compatible batch binds lighting when drawing the scene.
lighting.close(); // While the creating OpenGL context remains current.
```

<details>
<summary>Lighting3D operation reference (14 declarations)</summary>

#### Constructor

```java
public Lighting3D()
```

Captures the creating thread, queries texture-buffer capacity, and allocates
light/grid buffers and texture identifiers. A current OpenGL context is required;
data storage is uploaded lazily by prepare.

#### addLight

```java
public Lighting3D addLight(PointLight3D light)
```

Appends a borrowed non-null light under the fixed input capacity. Duplicate
objects are allowed and contribute separate entries. Changes upload on prepare.

- **`light`** — light to retain

**Returns:** this lighting system

**Throws `NullPointerException`:** if light is null

**Throws `IllegalStateException`:** if full, disposed, or used from the wrong thread

#### removeLight

```java
public boolean removeLight(PointLight3D light)
```

Removes the first matching borrowed light without disposing it. The next
prepare detects changed membership and updates buffers.

- **`light`** — entry to remove

**Returns:** true if an entry was removed

**Throws `IllegalStateException`:** if disposed or used from the wrong thread

#### clearLights

```java
public void clearLights()
```

Clears light membership without changing the environment or disposing light
objects. A subsequent prepare uploads the empty grid/light-count state.

**Throws `IllegalStateException`:** if disposed or used from the wrong thread

#### getLights

```java
public List<PointLight3D> getLights()
```

Returns an unmodifiable live membership view. Contained light objects remain
mutable, and later updates read their current values. No owner check occurs here.

**Returns:** live borrowed lights in registration order

#### getGrid

```java
public LightGrid3D getGrid()
```

Exposes the live CPU grid for diagnostics and culling configuration. Coordinate
direct mutations with the normal prepare lifecycle on the owning thread.

**Returns:** owned mutable grid

#### getUploadCount

```java
public long getUploadCount()
```

Reads the count of completed light/grid upload pairs, excluding cached prepares.

**Returns:** cumulative upload count

#### getExposure

```java
public float getExposure()
```

Reads the positive exposure multiplier supplied at the next bind.

**Returns:** configured exposure, initially one

#### setExposure

```java
public Lighting3D setExposure(float exposure)
```

Validates and stores exposure without rebuilding light buffers. Bind forwards
the latest value independently of the grid upload cache.

- **`exposure`** — positive finite multiplier

**Returns:** this lighting system

**Throws `IllegalArgumentException`:** if exposure is nonpositive or non-finite

**Throws `IllegalStateException`:** if disposed or used from the wrong thread

#### setEnvironment

```java
public Lighting3D setEnvironment(Color sky, Color ground)
```

Validates both linear RGB colors, then copies them into owned sky/ground state.
Z-up normals select their blend during shading; alpha does not affect shading.
The input color objects are not retained.

- **`sky`** — non-null upper-hemisphere diffuse color
- **`ground`** — non-null lower-hemisphere diffuse color

**Returns:** this lighting system

**Throws `NullPointerException`:** if either color is null

**Throws `IllegalArgumentException`:** if RGB components are negative or their sum is non-finite

#### setTiledCullingEnabled

```java
public Lighting3D setTiledCullingEnabled(boolean enabled)
```

Selects normal tiled culling or a diagnostic mode testing every frustum-visible
light in each fragment. The next prepare rebuilds/upload data if the mode changed.

- **`enabled`** — true for tiled culling

**Returns:** this lighting system

#### prepare

```java
public void prepare(Camera3D camera)
```

Reads the current viewport, selects tiles starting at 64 pixels and enlarging
them to fit device texture-buffer capacity, then updates/uploads changed grid
data. Zero-sized viewports return without an upload. Camera matrices/frustum
must already be rebuilt. Saves and restores the affected texture-buffer binding,
unit-three/four texture bindings, and active texture selector around uploads.

- **`camera`** — current scene camera

**Throws `IllegalStateException`:** if disposed or used from the wrong thread

**Throws `IllegalArgumentException`:** if the grid rejects light or viewport data

#### bind

```java
public void bind(Shader shader)
```

Binds light/grid texture buffers on units three/four and writes the compatible
shader's lighting, viewport, environment, and exposure uniforms. The shader
must already be bound. Leaves unit four active; the caller restores bindings.
At least one successful prepare upload is required.

- **`shader`** — bound shader implementing the tiled-lighting uniform contract

**Throws `IllegalStateException`:** if unprepared, disposed, or used from the wrong thread

#### close

```java
    public void close()
```

Deletes the two owned textures and buffers once. Borrowed lights are not
disposed, and diagnostic Java state remains readable. Requires the creating
thread before first disposal; subsequent calls return immediately.

**Throws `IllegalStateException`:** if first disposal is attempted on the wrong thread

</details>

<a id="type-pointlight3d"></a>

### PointLight3D

[Source](../../src/main/java/valthorne/graphics/lighting3d/PointLight3D.java#L28)

Mutable world-space point-light parameters consumed by the 3D lighting system.
A new light is white at the origin, with a range of ten world units and an
intensity multiplier of one, and shadow casting disabled. Filament interprets
each intensity unit as 1000 lumens and range as its finite falloff radius.
This object stores parameters only; it allocates
no GPU resources and does not register itself with a scene or render state.

Position and color getters expose live mutable objects. Setters copy color
components and write position components into those same objects. Range must
be finite and positive, and intensity finite and nonnegative. Position and
color setters perform no equivalent finiteness validation here; consumers such
as the light grid validate packed lighting values before use.

The tiled lighting path uses position, range, RGB and intensity; color alpha
is not part of that packed light data. Zero intensity disables contribution
without removing the light from its owner's list. Coordinate units should match
scene geometry. Avoid mutating a light concurrently with lighting submission.

<details>
<summary>PointLight3D operation reference (10 declarations)</summary>

#### isCastsShadows

```java
public boolean isCastsShadows()
```

Reads whether Filament should allocate shadow maps for this light. This flag
is not included in the tiled forward-lighting packed data.

**Returns:** requested shadow-map participation, initially false

#### setCastsShadows

```java
public PointLight3D setCastsShadows(boolean enabled)
```

Sets optional Filament shadow participation without allocating resources here.
The owning renderer applies the flag when synchronizing light state.

- **`enabled`** — whether this light requests shadow maps

**Returns:** this light

#### getPosition

```java
public Vector3f getPosition()
```

Returns the internal position vector. Mutations affect future light reads
directly; copy the vector to retain an independent position snapshot.

**Returns:** the live world-space position

#### getColor

```java
public Color getColor()
```

Returns the internal mutable color. Changing it updates subsequent lighting
input without another setter call; this is not a defensive copy.

**Returns:** the live light color

#### setColor

```java
public PointLight3D setColor(Color color)
```

Copies color components into this light without retaining the supplied
color object. Use finite nonnegative RGB values for the tiled light grid;
this setter does not apply those validation rules itself.

- **`color`** — the color whose components should be copied

**Returns:** this light for chaining

**Throws `NullPointerException`:** if color is null

#### getRange

```java
public float getRange()
```

Returns the configured finite influence range used by lighting consumers
for attenuation and conservative light-volume culling.

**Returns:** the positive range in world units, initially ten

#### setRange

```java
public PointLight3D setRange(float range)
```

Replaces the influence range after validating that it is finite and strictly
positive. Rejected values leave the previous range unchanged.

- **`range`** — the desired influence range in world units

**Returns:** this light for chaining

**Throws `IllegalArgumentException`:** if range is non-finite, zero or negative

#### getIntensity

```java
public float getIntensity()
```

Returns the nonnegative brightness multiplier. A value of zero produces
no contribution in the tiled lighting path while retaining the light object.

**Returns:** the finite intensity multiplier, initially one

#### setIntensity

```java
public PointLight3D setIntensity(float intensity)
```

Replaces the brightness multiplier after finite, nonnegative validation.
Zero is accepted, and there is no upper cap on finite positive values.

- **`intensity`** — the requested brightness multiplier

**Returns:** this light for chaining

**Throws `IllegalArgumentException`:** if intensity is negative or non-finite

#### setPosition

```java
public PointLight3D setPosition(float x, float y, float z)
```

Writes the world-space position into the existing internal vector. Supply
finite coordinates; this setter does not reject non-finite values itself.

- **`x`** — the world X coordinate
- **`y`** — the world Y coordinate
- **`z`** — the world Z coordinate

**Returns:** this light for chaining

</details>

<a id="type-shadowmap3d"></a>

### ShadowMap3D

[Source](../../src/main/java/valthorne/graphics/render/ShadowMap3D.java#L31)

Owns a directional-light depth map and the batch used to render opaque and
alpha-cutout scene geometry into it. A square orthographic camera defines the
light's coverage; configure that camera to include every relevant caster and
receiver before rendering. The receiving mesh shader samples the depth map
with comparison filtering and a 3-by-3 PCF kernel.

Construction, rendering, and disposal require a current compatible OpenGL
context. The scene and returned camera are borrowed; this object owns its
depth texture, framebuffer, comparison sampler, and batch. A successful depth
pass makes the matrix and texture ready for the main pass. Cached rendering
requires the caller to advance a caster revision whenever geometry, pose,
visibility, or alpha coverage changes.

```java
try (ShadowMap3D shadows = new ShadowMap3D(2048)) {
    shadows.getCamera().setWorldHeight(40);
    shadows.renderIfChanged(scene, casterRevision);
    // Supply this shadow map to the main pass's MeshRenderState3D.
}
```

<details>
<summary>ShadowMap3D operation reference (17 declarations)</summary>

#### Constructor

```java
public ShadowMap3D(int resolution)
```

Allocates a 24-bit depth texture, depth-only framebuffer, comparison sampler,
and private model batch. Initializes a Z-up light camera at (5, -5, 10),
looking at the origin with a world height of 20 and clip planes 0.1 to 100.
Framebuffer bindings and the state covered by the render snapshot are restored.

- **`resolution`** — width and height in pixels

**Throws `IllegalArgumentException`:** if resolution is nonpositive or exceeds the GL texture limit

**Throws `IllegalStateException`:** if the depth framebuffer is incomplete

#### getSoftness

```java
public float getSoftness()
```

Returns the sampling radius used by the receiving shader's PCF kernel.
Changing this value affects sampling rather than the stored depth image.

**Returns:** softness in shadow-map texels; initially 2

#### setSoftness

```java
public ShadowMap3D setSoftness(float texels)
```

Sets the PCF sampling radius without invalidating the cached depth pass.
Zero collapses the sampling offsets; larger values spread the
filter over more shadow-map texels.

- **`texels`** — finite radius from 0 through 8

**Returns:** this map

**Throws `IllegalArgumentException`:** if the radius is nonfinite or outside the supported range

#### getComparisonSampler

```java
public int getComparisonSampler()
```

Returns the owned linear-filtered, clamp-to-edge depth-comparison sampler.
Its comparison function is `GL_LEQUAL`; callers may bind it but must
not delete it or retain it after disposal.

**Returns:** OpenGL sampler name

**Throws `IllegalStateException`:** if this map has been disposed

#### getRenderCount

```java
public long getRenderCount()
```

Returns the number of successfully completed depth passes. Cache hits and
failed passes do not increment this counter.

**Returns:** cumulative successful render count

#### renderIfChanged

```java
public boolean renderIfChanged(Scene3D scene, long casterRevision)
```

Renders only when the scene identity, caller-supplied caster revision, or
rebuilt light-camera matrix differs from the last cached pass. An unready map
always renders. The scene's internal mutations are not inspected: advance
the revision for every shadow-affecting change, or use `render(Scene3D)`
for procedural geometry whose changes cannot be tracked.

- **`scene`** — scene whose shadow casters are drawn
- **`casterRevision`** — application-managed revision of caster content

**Returns:** true if a depth pass completed; false if the cached image was reused

**Throws `IllegalStateException`:** if this map has been disposed

**Throws `NullPointerException`:** if the scene is null

#### getCamera

```java
public OrthographicCamera3D getCamera()
```

Returns the live light camera used by subsequent shadow passes. Mutating it
changes coverage on the next render; cached rendering detects changes to its
rebuilt combined matrix.

**Returns:** borrowed mutable light camera

#### getTextureId

```java
public int getTextureId()
```

Returns the owned depth texture name. Check `isReady()` before using
its contents; construction alone does not render a valid shadow image.
The caller must not delete the texture.

**Returns:** OpenGL depth texture name

**Throws `IllegalStateException`:** if this map has been disposed

#### getMatrix

```java
public Matrix4f getMatrix()
```

Copies the combined light-camera matrix captured by the last successful
depth pass. Later edits to the live camera do not affect this snapshot.
Before any successful render the stored matrix is identity.

**Returns:** independent world-to-light-clip matrix

#### isReady

```java
public boolean isReady()
```

Reports whether a successful depth pass is available and the resources remain
alive. Starting a new pass clears readiness until that pass succeeds.

**Returns:** true when the depth texture and captured matrix can be sampled

#### getBias

```java
public float getBias()
```

Returns the receiver-side depth bias used during shadow comparison.

**Returns:** nonnegative depth bias; initially 0.001

#### setBias

```java
public ShadowMap3D setBias(float bias)
```

Sets the receiving shader's depth-comparison bias. This sampling adjustment
does not require another depth pass and is independent of the polygon offset
applied while rendering casters.

- **`bias`** — finite, nonnegative bias in depth-comparison units

**Returns:** this map

**Throws `IllegalArgumentException`:** if bias is negative or nonfinite

#### getStrength

```java
public float getStrength()
```

Returns the fraction by which a fully shadowed receiver is darkened.

**Returns:** shadow strength from 0 through 1; initially 0.85

#### setStrength

```java
public ShadowMap3D setStrength(float strength)
```

Sets receiver shadow strength without invalidating the depth image. Zero
disables shadow darkening; one applies the full sampled shadow contribution.

- **`strength`** — finite shadow strength from 0 through 1

**Returns:** this map

**Throws `IllegalArgumentException`:** if strength is nonfinite or outside the unit interval

#### render

```java
public void render(Scene3D scene)
```

Unconditionally clears and renders the scene into the owned depth target.
Rebuilds the light camera for the square target, disables scissoring, enables
depth writes and polygon offset, and submits the scene in shadow-pass mode.
On success it captures the camera matrix, marks the image ready, and increments
the render counter. This invalidates the revision cache even for the same scene.

Restores framebuffer bindings, viewport, clear depth, scissor enablement,
polygon-offset settings, and the state covered by the render snapshot,
including when scene rendering fails. A failed pass leaves the map unready.

- **`scene`** — nonnull scene to render; ownership remains with the caller

**Throws `IllegalStateException`:** if this map has been disposed

**Throws `NullPointerException`:** if scene is null

#### dispose

```java
public void dispose()
```

Releases the private batch and all owned OpenGL objects. Call with the
appropriate context current. Repeated calls after successful disposal have
no effect; the borrowed scene and camera are not disposed.

#### close

```java
    public void close()
```

Releases this map's GPU resources by delegating to `dispose()`.
Supports try-with-resources and is harmless after successful disposal.

</details>

## Related guides

- [3D models, materials, scenes, and billboards](models.md)
- [Shaders and visual effects](shaders.md)
- [Cameras and picking](cameras.md)
- [Filament rendering](filament.md)
- [Existing lighting guide](../lighting.md)
