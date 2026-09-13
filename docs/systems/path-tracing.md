# 2D and 3D path tracing

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Path tracing evaluates stochastic light transport across triangle geometry, supporting bounced illumination and view-dependent reflection/transmission. PathTracer3D exposes realtime reprojection and stationary progressive accumulation; PathTracer2D adapts the approach to planar content. This is a separate rendering path from raster lighting.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Scene preparation | PathTracingScene gathers geometry, effective materials, and transforms for tracing structures. |
| Realtime mode | Validated history is reprojected across camera motion at full viewport resolution. |
| Progressive mode | A stationary view accumulates samples at the selected quality scale. |
| Quality presets | Sample count and path depth change cost; progressive resolution also changes with quality. |
| History invalidation | Scene or relevant texture changes require restarting incompatible accumulation. |
| Reusable collection scratch | Renderer-owned collectors reuse placement, transform, and combined-material storage across captures. |

## Getting started

1. Use an OpenGL 4.3-compatible context for the compute-based tracer.
2. Prepare models, materials, and a rebuilt camera, then select realtime or progressive behavior.
3. Render repeatedly to accumulate or reuse valid history.
4. Invalidate after changing borrowed texture pixels and close the tracer when its scene is no longer rendered.

## Ownership and lifecycle

The tracer owns its GPU resources but borrows scene resources. Off-camera geometry can still affect lighting, so visibility culling for drawing is not a substitute for tracing-scene membership.

## Important behavior

- Progressive noise reduction requires repeated compatible frames.
- Camera movement and scene edits have different history consequences in realtime mode.
- Quality is a cost/accuracy setting, not a hardware ray-tracing-extension toggle.
- Explicitly invisible renderables and hidden node subtrees are excluded; objects merely outside the camera remain available to secondary rays.
- Internal Collector snapshots are borrowed until the next capture or clear. Consume build output before recapturing; collection clears old build data and overwrites reusable transforms. Build appends, so call it only after collection or explicit release of previous build data.
- Retired collection references are removed immediately. Backing capacity trims after 32 consecutive captures below one quarter of peak use, or immediately when empty. Source textures and models are never disposed by collection cleanup.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`PathTracer2D`](#type-pathtracer2d)
- [`PathTracer3D`](#type-pathtracer3d)
- [`PathTracer3D.RenderMode`](#type-pathtracer3d-rendermode)
- [`PathTracer3D.Quality`](#type-pathtracer3d-quality)

<a id="type-pathtracer2d"></a>

### PathTracer2D

[Source](../../src/main/java/valthorne/graphics/lighting2d/PathTracer2D.java#L28)

Physically lit orthographic 2D scenes using the same GPU path tracer as 3D.
Surfaces lie on XY; walls have real height so they cast soft shadows and bounce light.
Use consistent world units (e.g. one unit per tile). This is an alternative to
Lighting2D's fast color-capture pipeline, not a filter for already flattened sprites.
Materials may borrow sprite textures; alpha cutouts are supported.
The facade owns its tracer and retains a mutable scene and orthographic camera.
Closing releases tracer resources; caller-supplied material textures remain borrowed.

```java
PathTracer2D lighting = new PathTracer2D();
lighting.setView(0, 0, 12, 100);
lighting.addSurface(-5, -5, 10, 10, 0, new Material3D());
lighting.addAreaLight(0, 0, 3, 0.4f, Color.WHITE, 8);
lighting.render();
// Close on the graphics thread when finished.
lighting.close();
```

<details>
<summary>PathTracer2D operation reference (10 declarations)</summary>

#### Constructor

```java
public PathTracer2D()
```

Creates an empty scene with view center (0,0), world height twelve, and camera
elevation one hundred, using the underlying tracer's normal resource lifecycle.

#### setView

```java
public PathTracer2D setView(float centerX, float centerY, float worldHeight, float cameraHeight)
```

Places the camera above XY facing negative Z with positive Y up. Sets world height
and clip planes to 0.01 and twice camera height; aspect ratio later determines
visible width. Camera elevation should exceed scene geometry. Does not rebuild
matrices itself or reset a caller-customized orthographic zoom.

- **`centerX`** — world-space horizontal center
- **`centerY`** — world-space vertical center
- **`worldHeight`** — positive unzoomed visible height in world units
- **`cameraHeight`** — positive elevation above XY

**Returns:** this facade

**Throws `IllegalArgumentException`:** if the combined input check or clip-plane constraints fail

#### addSurface

```java
public ModelInstance3D addSurface(float x, float y, float width, float height, float elevation, Material3D material)
```

Creates a white XY plane anchored at its lower-left corner and adds it to the
scene at the requested elevation. Material and any texture remain shared.

- **`x`** — lower-left world X
- **`y`** — lower-left world Y
- **`width`** — finite positive X extent
- **`height`** — finite positive Y extent
- **`elevation`** — world Z coordinate
- **`material`** — non-null shared surface material

**Returns:** mutable placed instance retained by the scene

#### addWall

```java
public ModelInstance3D addWall(float x, float y, float width, float depth, float height, Material3D material)
```

Creates a box from Z zero to the supplied height, anchored at its lower-left XY
corner. Adds it to the scene for surface shading, occlusion, and light transport.

- **`x`** — lower-left world X
- **`y`** — lower-left world Y
- **`width`** — finite positive X extent
- **`depth`** — finite positive Y extent
- **`height`** — finite positive Z extent
- **`material`** — non-null shared wall material

**Returns:** mutable wall instance retained by the scene

#### addAreaLight

```java
public ModelInstance3D addAreaLight(float x, float y, float elevation, float radius, Color color, float radiance)
```

Adds a 16-segment, eight-stack emissive sphere centered at the supplied position.
Its finite geometry participates in shadows and reflections; this creates an
emissive material rather than a compatibility point-light entry.

- **`x`** — world X center
- **`y`** — world Y center
- **`elevation`** — world Z center
- **`radius`** — finite positive sphere radius
- **`color`** — emission color copied by the material
- **`radiance`** — emission-strength setting

**Returns:** mutable emissive instance retained by the scene

#### getScene

```java
public Scene3D getScene()
```

Returns the live scene for adding, removing, or updating geometry. Callers must
coordinate changes with rendering on the owning thread.

**Returns:** retained scene

#### getTracer

```java
public PathTracer3D getTracer()
```

Returns the owned tracer for quality and output configuration. Do not dispose it
independently while this facade is still used.

**Returns:** underlying tracer

#### getCamera

```java
public OrthographicCamera3D getCamera()
```

Returns the live orthographic camera for additional pose or projection settings.
Direct changes affect subsequent rendering according to the tracer's camera path.

**Returns:** owned camera

#### render

```java
public void render()
```

Delegates the current scene and camera to the GPU path tracer. Requires the
appropriate current graphics context and follows the tracer's viewport, accumulation,
and output behavior; this facade adds no separate compositing pass.

#### close

```java
    public void close()
```

Releases the underlying tracer's resources on the graphics thread. Scene geometry
and caller-owned textures are not disposed by this facade.

</details>

<a id="type-pathtracer3d"></a>

### PathTracer3D

[Source](../../src/main/java/valthorne/graphics/model/PathTracer3D.java#L32)

Multi-bounce GPU path tracer for triangle scenes. Requires OpenGL 4.3.
Uses shader cores with a balanced BVH, not hardware ray-tracing extensions.
Call on the context thread. Scene changes automatically restart accumulation;
call `invalidate()` after editing the pixels of a borrowed texture.
Owns its GPU resources, but never the scene's models or textures.
Realtime mode is the default: it renders at viewport resolution, reprojects
matching surfaces across camera changes, and rejects history after scene edits.
Progressive mode provides stationary accumulation at the selected quality scale.

```java
try (PathTracer3D lighting = new PathTracer3D()
        .setQuality(PathTracer3D.Quality.INTERACTIVE)) {
    lighting.render(scene, camera); // Current framebuffer and viewport; GL thread.
}
```

<details>
<summary>PathTracer3D operation reference (18 declarations)</summary>

#### Constructor

```java
public PathTracer3D()
```

Creates compute, denoising, and presentation programs plus scene storage
buffers on the current OpenGL thread. Render-target textures are allocated
lazily on the first nonempty viewport. Runtime construction failures release
resources allocated so far.

**Throws `IllegalStateException`:** if OpenGL 4.3 is unavailable or shader compilation/linking fails

#### setQuality

```java
public PathTracer3D setQuality(Quality quality)
```

Changes the tracing preset, resetting the nominal sample count and temporal
history when the value differs. Progressive resolution changes are applied
on the next render; realtime resolution remains the full viewport.

- **`quality`** — nonnull tracing preset

**Returns:** this renderer

**Throws `NullPointerException`:** if quality is null

#### getQuality

```java
public Quality getQuality()
```

Returns the current tracing preset, initially HIGH.

**Returns:** active quality setting

#### setRenderMode

```java
public PathTracer3D setRenderMode(RenderMode mode)
```

Switches rendering algorithms and invalidates their history. Realtime mode
renders at viewport resolution and validates reprojected surface history.

- **`mode`** — nonnull rendering mode

**Returns:** this renderer

#### getRenderMode

```java
public RenderMode getRenderMode()
```

Returns whether the renderer uses temporal reprojection or stationary
progressive accumulation.

**Returns:** active rendering mode

#### setDenoising

```java
public PathTracer3D setDenoising(boolean enabled)
```

Enables or disables edge-aware spatial filtering of the presentation images.
The raw accumulated radiance is preserved, so toggling this does not restart
tracing or change the nominal sample count.

- **`enabled`** — whether to present denoised radiance

**Returns:** this renderer

#### isDenoising

```java
public boolean isDenoising()
```

Reports whether presentation uses edge-aware filtered radiance.

**Returns:** current denoising flag, initially true

#### setExposure

```java
public PathTracer3D setExposure(float value)
```

Sets the positive presentation exposure multiplier without resetting accumulated
radiance. The tone-mapping presentation shader applies it on the next render.

- **`value`** — finite positive exposure

**Returns:** this renderer

**Throws `IllegalArgumentException`:** if value is nonfinite or nonpositive

#### setSky

```java
public PathTracer3D setSky(float r,float g,float b)
```

Sets constant linear HDR environment radiance. Changed components reset
sampling and temporal history because they alter light transport. Emissive
scene triangles supply area-light contributions separately.

- **`r`** — nonnegative red radiance
- **`g`** — nonnegative green radiance
- **`b`** — nonnegative blue radiance

**Returns:** this renderer

**Throws `IllegalArgumentException`:** if a component is negative or the component sum is nonfinite

#### setMaxSamples

```java
public PathTracer3D setMaxSamples(int value)
```

Sets the nominal accumulation/history budget without discarding existing
samples. Lowering it below the current count stops new tracing until another
change resets accumulation; raising it permits tracing to resume.

- **`value`** — sample limit from 1 through 1,000,000

**Returns:** this renderer

**Throws `IllegalArgumentException`:** if value is outside the supported range

#### invalidate

```java
public void invalidate()
```

Forces scene data and texture-atlas reconstruction on the next render and
clears accumulation/history eligibility. Use after in-place model geometry
or borrowed texture-pixel edits, which scene identity hashing cannot detect.

#### getAccumulatedSamples

```java
public int getAccumulatedSamples()
```

Returns the nominal sample count for the current view. It resets on camera,
scene, mode, or relevant setting changes. Realtime reprojection may reuse
history independently, so this is not a total path or ray counter.

**Returns:** current nominal accumulated samples

#### getMaxSamples

```java
public int getMaxSamples()
```

Returns the stationary accumulation budget. Realtime glossy pixels may use
additional paths per nominal sample; this is not a total ray counter.

**Returns:** configured positive nominal sample limit

#### getTriangleCount

```java
public int getTriangleCount()
```

Returns the number of nondegenerate world triangles in the last uploaded
scene snapshot, including off-camera geometry.

**Returns:** uploaded triangle count

#### getBvhNodeCount

```java
public int getBvhNodeCount()
```

Returns the node count of the last uploaded CPU-built bounding-volume hierarchy.

**Returns:** uploaded BVH node count

#### getSceneBuildCount

```java
public long getSceneBuildCount()
```

Counts successful scene-data and texture-atlas rebuilds. Camera-only changes
normally reset samples without rebuilding scene geometry.

**Returns:** cumulative scene rebuild count

#### render

```java
public void render(Scene3D scene,Camera3D camera)
```

Traces and presents the scene into the current draw framebuffer and viewport.
Returns immediately for an empty viewport; otherwise rebuilds the camera,
checks the scene signature, and refreshes packed geometry and textures when
needed. Visible direct renderables must be supported triangle-model instances.

Adds up to the quality preset's nominal samples while below the configured
limit. Realtime mode validates reprojected history at full viewport resolution;
progressive mode uses the preset scale. Optional spatial filtering precedes
the fullscreen presentation draw. Existing depth contents are preserved.

Restores the bindings and enable flags captured by the internal state guard,
including on failure. The current scissor and color-write mask remain in effect
during presentation. All calls belong on the creating thread with its compatible
OpenGL context current. Scene models and textures remain borrowed.

- **`scene`** — nonnull scene to sample
- **`camera`** — nonnull camera, rebuilt for the current viewport

**Throws `NullPointerException`:** if scene or camera is null

**Throws `IllegalStateException`:** if called off the creating thread, after closure, or with an unusable transform

**Throws `IllegalArgumentException`:** if scene renderables or texture storage cannot be represented

#### close

```java
@Override public void close()
```

Deletes owned programs, vertex array, scene buffers, atlas, accumulation,
history, guide, and filtering textures. Repeated calls have no effect.
Call with the creating OpenGL context current; this method does not enforce
the render method's thread check. Borrowed models and textures are untouched.

</details>

<a id="type-pathtracer3d-rendermode"></a>

### PathTracer3D.RenderMode

[Source](../../src/main/java/valthorne/graphics/model/PathTracer3D.java#L43)

REALTIME reprojects validated lighting history during camera movement;
PROGRESSIVE accumulates a stationary view without temporal reprojection.
Both modes retain the same stochastic multi-bounce light transport. Realtime
history is bounded during movement to limit lag in view-dependent reflections.

<details>
<summary>PathTracer3D.RenderMode operation reference (2 declarations)</summary>

#### REALTIME

```java
public static final  RenderMode REALTIME
```

Reprojects validated history while the camera moves and uses full viewport resolution.

#### PROGRESSIVE

```java
public static final  RenderMode PROGRESSIVE
```

Accumulates a stationary view at the selected quality's resolution scale.

</details>

<a id="type-pathtracer3d-quality"></a>

### PathTracer3D.Quality

[Source](../../src/main/java/valthorne/graphics/model/PathTracer3D.java#L67)

Preset tracing cost: resolution scale in progressive mode, nominal samples
per rendered frame, and maximum path bounces. Realtime mode always uses full
viewport resolution while retaining each preset's sample and bounce counts.

<details>
<summary>PathTracer3D.Quality operation reference (3 declarations)</summary>

#### INTERACTIVE

```java
public static final  Quality INTERACTIVE
```

Half-resolution progressive rendering with one nominal sample and up to five bounces.

#### HIGH

```java
public static final  Quality HIGH
```

Three-quarter-resolution progressive rendering with two nominal samples and up to eight bounces.

#### ULTRA

```java
public static final  Quality ULTRA
```

Full-resolution progressive rendering with four nominal samples and up to twelve bounces.

</details>

<a id="type-pathtracer3d-state"></a>

### PathTracer3D.State — internal support type

[Source](../../src/main/java/valthorne/graphics/model/PathTracer3D.java#L429)

Captures the GL bindings and enable flags modified by path tracing, including
seven image units, three shader-storage ranges, texture/sampler units zero
through two, and pixel-transfer configuration. Establishes tightly packed CPU
pixel transfers for atlas readback/upload. Used as a lexical restoration guard;
it does not own the resources whose names it records.

<details>
<summary>PathTracer3D.State operation reference (1 declarations)</summary>

#### close

```java
public void close()
```

Restores indexed buffer ranges, image bindings, pixel-transfer configuration,
textures, samplers, program, vertex array, and captured enable flags. Does not
delete any recorded resource or restore framebuffer/viewport values, which
the guarded renderer does not change.

</details>

<a id="type-pathtracingscene"></a>

### PathTracingScene — internal support type

[Source](../../src/main/java/valthorne/graphics/model/PathTracingScene.java#L35)

Collects visible triangle-model instances and builds packed geometry, emitter
sampling data, and a surface-area-heuristic bounding-volume hierarchy for the GPU
path tracer. Visibility means the scene's explicit visibility flags; geometry
outside the camera remains available to shadow and secondary rays.

Collection captures transforms and computes a signature from model identity
and selected material values, but retains model/material references until build.
In-place model geometry or texture-pixel edits are not hashed. The renderer must
invalidate those changes explicitly. OBJ parts are expanded with combined tint
and texture selection, and their pending textures are uploaded during collection,
so collecting this snapshot can require the OpenGL context.

A renderer's Collector reuses the same snapshot and its placement scratch across
captures. Consume or upload build output before capturing again, because capture
releases the previous build data and overwrites transforms and combined materials.
Call build only with empty output lists: once after collection, or after explicitly
releasing prior build data. Models, materials, and textures remain borrowed;
this package-private object owns CPU lists, matrices, and packed arrays only.

<a id="type-pathtracingscene-collector"></a>

### PathTracingScene.Collector — internal support type

[Source](../../src/main/java/valthorne/graphics/model/PathTracingScene.java#L43)

Retains reusable instance, transform, and combined-material storage for one renderer.
Each capture refreshes the same snapshot; callers must finish consuming it before
the next capture or clear. Failed collection abandons the retained snapshot so a
later attempt starts cleanly. Source scene resources remain borrowed.

<a id="type-pathtracingscene-capacity"></a>

### PathTracingScene.Capacity — internal support type

[Source](../../src/main/java/valthorne/graphics/model/PathTracingScene.java#L86)

Tracks peak active list size and consecutive small captures for reusable scene
scratch. Retired references are removed immediately, while backing arrays shrink
after 32 captures below one quarter of the peak, or immediately when empty.

<a id="type-pathtracingscene-instance"></a>

### PathTracingScene.Instance — internal support type

[Source](../../src/main/java/valthorne/graphics/model/PathTracingScene.java#L578)

Collected geometry source and effective material with a captured world matrix.
The record itself does not defensively copy its mutable components.

Collection captures the effective world transform before BVH and triangle expansion.
Models and materials remain borrowed, so their required lifetime extends through
scene construction even though instance transforms have already been captured.

- **`model`** — borrowed source triangle model
- **`material`** — borrowed effective material
- **`transform`** — captured world transform retained by reference

## Related guides

- [3D models, materials, scenes, and billboards](models.md)
- [Batched 2D lighting](lighting-2d.md)
- [Raster 3D lighting and shadow maps](lighting-3d.md)
- [Filament rendering](filament.md)
- [Existing path tracing guide](../path-tracing.md)
