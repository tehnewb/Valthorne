# Screen-space radiance cascades

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Radiance cascades estimate planar, screen-space lighting by capturing scene data and solving a hierarchy of probe intervals. The hierarchy traces short intervals, extends them within each level, and merges levels back toward the finest result. The resolved light texture is then consumed by composition.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Scene capture | RadianceSceneBuffer supplies the scene textures used for tracing. |
| Hierarchy settings | Probe spacing, ray counts, interval lengths, and solve dimensions define cost and coverage. |
| Trace and merge | Compute stages build and combine lighting over progressively larger intervals. |
| Resolved output | The final texture integrates the finest merged radiance for image-space consumption. |

## Getting started

1. Choose settings before constructing the solver so target allocation matches the hierarchy.
2. Create matching scene-buffer and solver dimensions with a compute-capable context.
3. Capture the required scene information, then call the solver's render method.
4. Sample its light texture before the next resize or disposal and release solver plus scene buffer when done.

## Ownership and lifecycle

Settings are retained by reference, but changing them does not automatically reconstruct every allocated target. The solver borrows captured scene data and owns its programs and outputs. Compute work changes texture/image bindings.

## Important behavior

- Screen-space data cannot describe geometry absent from the capture.
- A resized output invalidates borrowed references to previous targets.
- Use the component contracts for capture channels and units rather than feeding an arbitrary color image.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`RadianceCascadeLevel`](#type-radiancecascadelevel)
- [`RadianceCascades`](#type-radiancecascades)
- [`RadianceCascadeSettings`](#type-radiancecascadesettings)
- [`RadianceSceneBuffer`](#type-radiancescenebuffer)
- [`RadianceTexture`](#type-radiancetexture)

<a id="type-radiancecascadelevel"></a>

### RadianceCascadeLevel

[Source](../../src/main/java/valthorne/graphics/radiance/RadianceCascadeLevel.java#L17)

Stores one level's probe geometry and three owned floating-point textures: an
interval target, a scratch target used for alternating extension passes, and a
merged output target. Rays are packed horizontally within each probe row.

The enclosing solver creates and disposes levels on its graphics thread.
Public texture access is borrowed and becomes invalid when the solver rebuilds or
disposes its targets. Geometry is fixed for the lifetime of this level; swapping
interval targets changes only which stored image is considered current.

<details>
<summary>RadianceCascadeLevel operation reference (14 declarations)</summary>

#### getIndex

```java
public int getIndex()
```

Returns this level's zero-based level index.
The value is fixed at construction and remains available after texture disposal.

**Returns:** zero-based level index

#### getProbeSpacing

```java
public int getProbeSpacing()
```

Returns this level's probe spacing in solver pixels.
The value is fixed at construction and remains available after texture disposal.

**Returns:** probe spacing in solver pixels

#### getRayCount

```java
public int getRayCount()
```

Returns this level's directional rays per probe.
The value is fixed at construction and remains available after texture disposal.

**Returns:** directional rays per probe

#### getRaySide

```java
public int getRaySide()
```

Returns this level's compatibility ray-side value, equal to ray count.
The value is fixed at construction and remains available after texture disposal.

**Returns:** compatibility ray-side value, equal to ray count

#### getTraceCount

```java
public int getTraceCount()
```

Returns this level's trace count, equal to ray count.
The value is fixed at construction and remains available after texture disposal.

**Returns:** trace count, equal to ray count

#### getProbeCountX

```java
public int getProbeCountX()
```

Returns this level's horizontal probe count.
The value is fixed at construction and remains available after texture disposal.

**Returns:** horizontal probe count

#### getProbeCountY

```java
public int getProbeCountY()
```

Returns this level's vertical probe count.
The value is fixed at construction and remains available after texture disposal.

**Returns:** vertical probe count

#### getTextureWidth

```java
public int getTextureWidth()
```

Returns this level's packed texture width in pixels.
The value is fixed at construction and remains available after texture disposal.

**Returns:** packed texture width in pixels

#### getTextureHeight

```java
public int getTextureHeight()
```

Returns this level's packed texture height in pixels.
The value is fixed at construction and remains available after texture disposal.

**Returns:** packed texture height in pixels

#### getExtensionPassCount

```java
public int getExtensionPassCount()
```

Returns this level's number of interval extension passes, equal to level index.
The value is fixed at construction and remains available after texture disposal.

**Returns:** number of interval extension passes, equal to level index

#### getIntervalStart

```java
public float getIntervalStart()
```

Returns this level's interval start distance in solver units.
The value is fixed at construction and remains available after texture disposal.

**Returns:** interval start distance in solver units

#### getIntervalLength

```java
public float getIntervalLength()
```

Returns this level's full interval length in solver units.
The value is fixed at construction and remains available after texture disposal.

**Returns:** full interval length in solver units

#### getTextureID

```java
public int getTextureID()
```

Returns the merged-output texture name without binding it or transferring ownership.

**Returns:** merged texture name, or zero after disposal

#### getTexture

```java
public Texture getTexture()
```

Returns a borrowed wrapper for the merged output. Do not dispose it separately
or use it after the enclosing solver rebuilds or disposes this level.

**Returns:** merged output wrapper, or null after disposal

</details>

<a id="type-radiancecascades"></a>

### RadianceCascades

[Source](../../src/main/java/valthorne/graphics/radiance/RadianceCascades.java#L54)

Flatland/screenspace radiance cascades built around the scaling described in the
original paper: probe spacing doubles per level, ray count doubles per level,
and interval length doubles per level while remaining contiguous.

The hierarchy is built in three stages:

- Trace a short base-length interval for every level.

- Extend that interval inside the level by shifting and merging it with itself.

- Merge levels back-to-front so cascade 0 contains full-range radiance cones.

The final light texture resolves diffuse-like lighting by integrating the merged
cascade 0 cones at each pixel.

```java
RadianceSceneBuffer scene = new RadianceSceneBuffer(640, 360);
RadianceCascades lighting = new RadianceCascades(640, 360);
// Capture scene data into scene before solving.
lighting.render(scene);
Texture light = lighting.getLightTexture();
// Consume light before resizing or disposing lighting.
lighting.dispose();
scene.dispose();
```

Construction, solving, resizing, and disposal require a compute-capable OpenGL
context. The solver owns its programs and textures, borrows the captured scene,
and retains settings by reference. Configure hierarchy settings before creation:
changing them does not automatically rebuild targets. Rendering leaves texture
and image bindings changed and unbinds its compute program to program zero.

<details>
<summary>RadianceCascades operation reference (10 declarations)</summary>

#### Constructor

```java
public RadianceCascades(int width, int height)
```

Creates a solver with default settings, compiling four compute programs and
allocating its hierarchy immediately on the current graphics context.

- **`width`** — positive capture width in pixels
- **`height`** — positive capture height in pixels

**Throws `IllegalArgumentException`:** if a dimension is nonpositive

#### Constructor

```java
public RadianceCascades(int width, int height, RadianceCascadeSettings settings)
```

Validates Flatland settings, retains their reference, compiles compute programs,
and allocates hierarchy and output textures. Resource allocation occurs immediately;
configure structural settings before construction.

- **`width`** — positive capture width in pixels
- **`height`** — positive capture height in pixels
- **`settings`** — configuration retained without copying

**Throws `NullPointerException`:** if settings is null

**Throws `IllegalArgumentException`:** if dimensions or checked settings are invalid

**Throws `IllegalStateException`:** if shader compilation or hierarchy construction fails

#### resize

```java
public void resize(int width, int height)
```

Rebuilds textures for changed positive capture dimensions, discarding previous
lighting and invalidating borrowed wrappers. Equal dimensions are a no-op, so
this method does not refresh changed structural settings at the same size.

- **`width`** — positive capture width in pixels
- **`height`** — positive capture height in pixels

**Throws `IllegalArgumentException`:** if either dimension is nonpositive

#### render

```java
public void render(RadianceSceneBuffer sceneBuffer)
```

Solves captured scene lighting through tracing, extension, back-to-front merging,
and final resolve. Resizes targets if capture dimensions differ. GPU barriers
separate dependent passes; texture and image bindings are not restored afterward.
The scene buffer remains owned by its caller.

- **`sceneBuffer`** — populated capture buffer, not currently being written

**Throws `NullPointerException`:** if sceneBuffer is null

#### getLightTexture

```java
public Texture getLightTexture()
```

Returns the borrowed resolved-light wrapper. Its contents are produced by render;
resize invalidates earlier wrappers. Do not dispose this texture separately.

**Returns:** current lighting texture

**Throws `NullPointerException`:** if targets have been disposed

#### getLevels

```java
public List<RadianceCascadeLevel> getLevels()
```

Returns an unmodifiable live view of the owned level list. Rebuilding changes
its contents and invalidates texture resources in previously retained levels.

**Returns:** live level view ordered finest to coarsest

#### getSettings

```java
public RadianceCascadeSettings getSettings()
```

Returns the retained mutable settings reference. Structural changes are not
automatically reflected in existing targets; configure them before solver creation.

**Returns:** shared configuration

#### getSolveWidth

```java
public int getSolveWidth()
```

Returns the reduced internal width chosen at the latest target rebuild.

**Returns:** internal solve width in pixels

#### getSolveHeight

```java
public int getSolveHeight()
```

Returns the reduced internal height chosen at the latest target rebuild.

**Returns:** internal solve height in pixels

#### dispose

```java
public void dispose()
```

Releases owned targets and all four compute programs on the current context.
Borrowed output wrappers become invalid. The solver has no closed-state guard
and must not be rendered after disposal.

</details>

<a id="type-radiancecascadesettings"></a>

### RadianceCascadeSettings

[Source](../../src/main/java/valthorne/graphics/radiance/RadianceCascadeSettings.java#L17)

Mutable configuration retained by a radiance-cascade solver. Configure hierarchy
dimensions before constructing the solver: changing them later does not itself
rebuild textures. Shading controls read during render can affect the next solve.
The Flatland implementation requires a branch factor of two.

Defaults use probe spacing two, four rays, an automatically derived base interval,
half-resolution solving, no explicit level limit, and an 8192 texture-dimension cap.
Float range checks do not separately reject NaN; supply finite values for meaningful
rendering. Ray-step and cross-blur settings are retained but are not consumed by the
current RadianceCascades implementation.

<details>
<summary>RadianceCascadeSettings operation reference (26 declarations)</summary>

#### getBaseProbeSpacing

```java
public int getBaseProbeSpacing()
```

Returns the base-level probe spacing in solver pixels.
The initial value is two; reading it does not allocate or rebuild solver state.

**Returns:** configured base-level probe spacing in solver pixels

#### setBaseProbeSpacing

```java
public RadianceCascadeSettings setBaseProbeSpacing(int baseProbeSpacing)
```

Stores the base-level probe spacing in solver pixels.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`baseProbeSpacing`** — replacement value; must be positive

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

#### getBaseRayCount

```java
public int getBaseRayCount()
```

Returns the directional rays per base-level probe.
The initial value is four; reading it does not allocate or rebuild solver state.

**Returns:** configured directional rays per base-level probe

#### setBaseRayCount

```java
public RadianceCascadeSettings setBaseRayCount(int baseRayCount)
```

Stores the directional rays per base-level probe.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`baseRayCount`** — replacement value; must be positive

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

#### getBaseIntervalLength

```java
public float getBaseIntervalLength()
```

Returns the base ray interval length in solver units.
The initial value is automatic (-1); reading it does not allocate or rebuild solver state.

**Returns:** configured base ray interval length in solver units

#### setBaseIntervalLength

```java
public RadianceCascadeSettings setBaseIntervalLength(float baseIntervalLength)
```

Stores the base ray interval length in solver units.
This modifies configuration only and does not notify or rebuild an existing solver.
The automatic sentinel cannot be restored through this positive-only setter.

- **`baseIntervalLength`** — replacement value; must be positive

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

#### getBranchFactor

```java
public int getBranchFactor()
```

Returns the interval growth factor between levels.
The initial value is two; reading it does not allocate or rebuild solver state.

**Returns:** configured interval growth factor between levels

#### setBranchFactor

```java
public RadianceCascadeSettings setBranchFactor(int branchFactor)
```

Stores the interval growth factor between levels.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`branchFactor`** — replacement value; must be positive; the solver additionally requires two

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

#### isBilinearFix

```java
public boolean isBilinearFix()
```

Returns the spatial interpolation correction during extension, merging, and resolve.
The initial value is false; reading it does not allocate or rebuild solver state.

**Returns:** configured spatial interpolation correction during extension, merging, and resolve

#### setBilinearFix

```java
public RadianceCascadeSettings setBilinearFix(boolean bilinearFix)
```

Stores the spatial interpolation correction during extension, merging, and resolve.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`bilinearFix`** — replacement value

**Returns:** this settings object

#### getMaxLevels

```java
public int getMaxLevels()
```

Returns the level-count limit, with zero selecting automatic coverage.
The initial value is zero; reading it does not allocate or rebuild solver state.

**Returns:** configured level-count limit, with zero selecting automatic coverage

#### setMaxLevels

```java
public RadianceCascadeSettings setMaxLevels(int maxLevels)
```

Stores the level-count limit, with zero selecting automatic coverage.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`maxLevels`** — replacement value; must be nonnegative

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

#### getInternalScale

```java
public int getInternalScale()
```

Returns the integer divisor of captured scene dimensions for solving.
The initial value is two; reading it does not allocate or rebuild solver state.

**Returns:** configured integer divisor of captured scene dimensions for solving

#### setInternalScale

```java
public RadianceCascadeSettings setInternalScale(int internalScale)
```

Stores the integer divisor of captured scene dimensions for solving.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`internalScale`** — replacement value; must be positive

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

#### getMaxCascadeTextureWidth

```java
public int getMaxCascadeTextureWidth()
```

Returns the cap applied to both packed cascade texture dimensions.
The initial value is 8192; reading it does not allocate or rebuild solver state.

**Returns:** configured cap applied to both packed cascade texture dimensions

#### setMaxCascadeTextureWidth

```java
public RadianceCascadeSettings setMaxCascadeTextureWidth(int maxCascadeTextureWidth)
```

Stores the cap applied to both packed cascade texture dimensions.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`maxCascadeTextureWidth`** — replacement value; must be at least 64

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

#### getRayStep

```java
public float getRayStep()
```

Returns the retained ray-step setting, unused by the current solver.
The initial value is 0.1; reading it does not allocate or rebuild solver state.

**Returns:** configured retained ray-step setting, unused by the current solver

#### setRayStep

```java
public RadianceCascadeSettings setRayStep(float rayStep)
```

Stores the retained ray-step setting, unused by the current solver.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`rayStep`** — replacement value; must be positive

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

#### getTransmittanceCutoff

```java
public float getTransmittanceCutoff()
```

Returns the transmittance cutoff supplied to extension and merge shaders.
The initial value is 0.01; reading it does not allocate or rebuild solver state.

**Returns:** configured transmittance cutoff supplied to extension and merge shaders

#### setTransmittanceCutoff

```java
public RadianceCascadeSettings setTransmittanceCutoff(float transmittanceCutoff)
```

Stores the transmittance cutoff supplied to extension and merge shaders.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`transmittanceCutoff`** — replacement value; must be between zero and one

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

#### getIntensity

```java
public float getIntensity()
```

Returns the final resolved-light multiplier.
The initial value is one; reading it does not allocate or rebuild solver state.

**Returns:** configured final resolved-light multiplier

#### setIntensity

```java
public RadianceCascadeSettings setIntensity(float intensity)
```

Stores the final resolved-light multiplier.
This modifies configuration only and does not notify or rebuild an existing solver.
The multiplier is stored without sign or finiteness validation.

- **`intensity`** — replacement value

**Returns:** this settings object

#### isCrossBlur

```java
public boolean isCrossBlur()
```

Returns the retained cross-blur option, unused by the current solver.
The initial value is true; reading it does not allocate or rebuild solver state.

**Returns:** configured retained cross-blur option, unused by the current solver

#### setCrossBlur

```java
public RadianceCascadeSettings setCrossBlur(boolean crossBlur)
```

Stores the retained cross-blur option, unused by the current solver.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`crossBlur`** — replacement value

**Returns:** this settings object

#### getOpacitySimilarityThreshold

```java
public float getOpacitySimilarityThreshold()
```

Returns the surface-opacity threshold used by tracing and resolving.
The initial value is 0.1; reading it does not allocate or rebuild solver state.

**Returns:** configured surface-opacity threshold used by tracing and resolving

#### setOpacitySimilarityThreshold

```java
public RadianceCascadeSettings setOpacitySimilarityThreshold(float opacitySimilarityThreshold)
```

Stores the surface-opacity threshold used by tracing and resolving.
This modifies configuration only and does not notify or rebuild an existing solver.

- **`opacitySimilarityThreshold`** — replacement value; must be nonnegative

**Returns:** this settings object

**Throws `IllegalArgumentException`:** if the stated range check fails

</details>

<a id="type-radiancerendertarget"></a>

### RadianceRenderTarget — internal support type

[Source](../../src/main/java/valthorne/graphics/radiance/RadianceRenderTarget.java#L19)

Owns an RGBA16F texture and, optionally, a color-only framebuffer used by the
radiance pipeline. All resource operations require the owning OpenGL context.
Begin/end save one framebuffer and viewport pair, so calls must be balanced and
must not nest on the same target. Other graphics state is not generally restored.

Resizing replaces storage and invalidates previously returned texture wrappers.
Wrappers borrow the target's texture ID and must not be disposed independently.
This helper has no closed-state guard; dispose after use and do not render again.

<a id="type-radiancescenebuffer"></a>

### RadianceSceneBuffer

[Source](../../src/main/java/valthorne/graphics/radiance/RadianceSceneBuffer.java#L28)

Owns a nearest-filtered floating-point framebuffer for captured radiance scene data.
Begin/end temporarily replace the framebuffer and viewport; all resource and drawing
operations require the owning graphics context. Balance these calls and do not nest
them on this instance. Clear outside an active begin/end pair.

```java
RadianceSceneBuffer scene = new RadianceSceneBuffer(640, 360);
scene.clear();
scene.begin();
try {
    // Draw encoded scene data using the active framebuffer.
} finally {
    scene.end();
}
scene.dispose();
```

Resizing discards captured pixels and invalidates borrowed texture wrappers.
Dispose after consumers finish; this class does not manage their lifetime.

<details>
<summary>RadianceSceneBuffer operation reference (11 declarations)</summary>

#### Constructor

```java
public RadianceSceneBuffer(int width, int height)
```

Allocates a color framebuffer and nearest-filtered RGBA16F texture immediately.
Creation does not preserve all incoming graphics bindings.

- **`width`** — positive buffer width in pixels
- **`height`** — positive buffer height in pixels

**Throws `IllegalArgumentException`:** if either dimension is nonpositive

**Throws `IllegalStateException`:** if framebuffer creation is incomplete

#### begin

```java
public void begin()
```

Saves the current framebuffer and viewport and binds this buffer for capture.
Does not clear existing contents. Call end after drawing and avoid nested begins.

#### end

```java
public void end()
```

Restores the framebuffer and viewport saved by the latest begin. Other graphics
state is not restored, and unmatched calls can restore stale saved values.

#### clear

```java
public void clear()
```

Clears the color attachment to transparent black through a temporary binding.
Call outside this buffer's begin/end pair; the OpenGL clear color remains changed.

#### clear

```java
public void clear(float r, float g, float b, float a)
```

Clears the color attachment using a temporary binding and restores framebuffer
and viewport. Other clear-related state, including clear color, is not restored.

- **`r`** — red component
- **`g`** — green component
- **`b`** — blue component
- **`a`** — alpha component

#### resize

```java
public void resize(int width, int height)
```

Replaces storage if positive dimensions change, discarding captured data and
invalidating previously borrowed wrappers. Equal dimensions perform no work.

- **`width`** — positive replacement width in pixels
- **`height`** — positive replacement height in pixels

**Throws `IllegalArgumentException`:** if either dimension is nonpositive

#### getWidth

```java
public int getWidth()
```

Returns the retained storage width without querying the GPU.

**Returns:** width in pixels

#### getHeight

```java
public int getHeight()
```

Returns the retained storage height without querying the GPU.

**Returns:** height in pixels

#### getTextureID

```java
public int getTextureID()
```

Returns the captured color texture's name without binding it or transferring
ownership. The name can change after resize.

**Returns:** current texture name, or zero after disposal

#### getTexture

```java
public Texture getTexture()
```

Returns a borrowed wrapper for the captured texture. Do not dispose it separately;
resize and disposal of this buffer invalidate earlier wrappers.

**Returns:** current wrapper, or null after disposal

#### dispose

```java
public void dispose()
```

Releases the owned framebuffer and texture. Repeated calls do not delete resources
again; previously returned texture wrappers must no longer be used.

</details>

<a id="type-radiancetexture"></a>

### RadianceTexture

[Source](../../src/main/java/valthorne/graphics/radiance/RadianceTexture.java#L19)

Wraps an existing radiance texture ID for use by the texture-rendering API.
The four-byte CPU placeholder supplies dimensions only and is not a full image
or a readback of the GPU data. Construction changes the texture's filtering.
When obtained from a radiance target, the wrapper borrows that target's ID:
do not call inherited disposal independently or use it after target recreation.

<details>
<summary>RadianceTexture operation reference (1 declarations)</summary>

#### Constructor

```java
public RadianceTexture(int textureID, int width, int height, boolean linear)
```

Associates an existing texture name with dimension metadata and applies filtering.
Does not allocate the GPU image; the texture must already have valid storage.

- **`textureID`** — existing OpenGL texture name
- **`width`** — logical texture width in pixels
- **`height`** — logical texture height in pixels
- **`linear`** — true for linear filtering, false for nearest

</details>

## Related guides

- [Shaders and visual effects](shaders.md)
- [Textures, sprites, atlases, and batching](textures.md)
- [Batched 2D lighting](lighting-2d.md)
