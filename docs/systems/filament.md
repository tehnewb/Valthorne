# Filament rendering

Author: Albert Beaupre

[System manual](README.md)

## Purpose

FilamentRenderer3D connects Valthorne scene models and materials to the native Filament renderer. Use it for the physically based rendering path supported by this wrapper. It maintains native scene slots, imported texture wrappers, cached meshes, and an output target that integrates with the current OpenGL viewport.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Material translation | Valthorne material values are synchronized into native material parameters. |
| Mesh sharing | Model identity allows multiple instances to share cached native geometry. |
| Lighting | Explicit point lights, supported emissive approximations, and environment illumination feed the native scene. |
| Quality | Presets select multisample behavior while retaining full-resolution ambient occlusion. |
| Invalidation | Explicit invalidation refreshes supported cached scene/texture state. |

## Getting started

1. Check the native runtime and material-package requirements in the existing Filament integration guide.
2. Create the renderer on the graphics thread and populate a Scene3D.
3. Render with an updated camera, then draw overlays as a separate phase.
4. Close the renderer on its owning thread before destroying the context.

## Ownership and lifecycle

Filament resources and the confined native arena belong to the creating thread. Source models, materials, and textures are borrowed. Cached geometry is identity-based; changing an existing model in place is not equivalent to creating a new native mesh.

## Important behavior

- Do not use invalidation as a promise that arbitrary geometry edits are reuploaded.
- Transparent, opaque, and glass-like material paths have different supported behavior.
- Native platform support is determined by the dependencies shipped in this checkout.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`FilamentRenderer3D`](#type-filamentrenderer3d)
- [`FilamentRenderer3D.Quality`](#type-filamentrenderer3d-quality)

<a id="type-filamentrenderer3d"></a>

### FilamentRenderer3D

[Source](../../src/main/java/valthorne/graphics/model/FilamentRenderer3D.java#L53)

Adapts Valthorne triangle scenes to Filament 1.75 through Windows x64 OpenGL
texture sharing. Create, render, configure, and close on the GLFW context's
owning thread. The adapter owns native renderables, materials, mesh buffers,
environment resources, and its shared output texture; source models and albedo
textures remain borrowed and must stay alive while referenced.

Model identities cache immutable mesh uploads. Scene changes refresh transforms
and material parameters, while explicit point lights update independently.
Emissive instances create point-light approximations at their world origins by
default, using 1000 lumens per emission unit. Disable a material's emission-light
flag to retain visible glow without an implicit light. The studio environment
provides static indirect illumination; local lights do not rebuild it.

Translucent render passes select alpha compositing. Opaque-pass transmission
above 0.01 selects the screen-space glass material. Imported albedo textures
share the caller's GL objects and receive a full mip chain with trilinear
filtering. Call invalidate after changing base pixels to regenerate mip levels.
Custom procedural renderables and in-place animated mesh-buffer updates are
not represented by this adapter.

```java
try (FilamentRenderer3D renderer = new FilamentRenderer3D()) {
    renderer.setQuality(FilamentRenderer3D.Quality.HIGH);
    renderer.setEnvironmentIntensity(1000);
    renderer.render(scene, camera); // Current destination framebuffer and viewport.
}
```

See the repository's docs/filament.md for material packages and integration limits.

<details>
<summary>FilamentRenderer3D operation reference (10 declarations)</summary>

#### getCachedMeshCount

```java
public int getCachedMeshCount()
```

Counts distinct model identities currently cached as native vertex/index
buffers. The count changes during scene synchronization and eviction; query
on the owner thread. This diagnostic does not itself check closure.

**Returns:** cached mesh count

#### getPointLightCount

```java
public int getPointLightCount()
```

Counts active native point lights after the latest render, including emissive
model approximations and explicit scene lights. Excludes the environment and
explicit lights with zero intensity or black RGB.

**Returns:** current native scene light count

**Throws `IllegalStateException`:** if called off the owner thread or after closure

#### Constructor

```java
public FilamentRenderer3D()
```

Creates a shared OpenGL Filament engine, headless rendering objects, compiled
surface/glass/alpha materials, a white fallback texture, and the bundled studio
environment. Temporarily releases the GLFW context while creating Filament's
shared driver context, then restores it. Enables temporal/FXAA smoothing and
ambient occlusion, with screen-space reflections disabled.

**Throws `UnsupportedOperationException`:** if the platform is not the supported Windows sharing path

**Throws `IllegalStateException`:** if no GLFW context is current, engine creation fails, or a required material/environment cannot be loaded

#### setExposure

```java
public void setExposure(float value)
```

Sets the positive multiplier applied through Filament camera sensitivity on
subsequent renders. Does not rebuild mesh or material caches.

- **`value`** — finite positive exposure multiplier

**Throws `IllegalArgumentException`:** if value is nonpositive or nonfinite

**Throws `IllegalStateException`:** if called off the owner thread or after closure

#### setEnvironmentIntensity

```java
public void setEnvironmentIntensity(float value)
```

Updates the bundled static indirect-light intensity immediately. This scales
environment illumination and reflections without recomputing the environment.

- **`value`** — finite nonnegative native indirect-light intensity

**Throws `IllegalArgumentException`:** if value is negative or nonfinite

**Throws `IllegalStateException`:** if called off the owner thread or after closure

#### setAntiAliasing

```java
public void setAntiAliasing(boolean enabled)
```

Toggles temporal antialiasing and the view's post-process antialiasing setting.
Does not alter MSAA, which is controlled by setQuality.

- **`enabled`** — whether temporal and post-process smoothing are enabled

**Throws `IllegalStateException`:** if called off the owner thread or after closure

#### setQuality

```java
public void setQuality(Quality quality)
```

Applies full-resolution ambient occlusion settings and configures MSAA:
disabled for INTERACTIVE, four requested samples for HIGH, or eight for ULTRA.
Does not change temporal antialiasing or output resolution.

- **`quality`** — nonnull preset

**Throws `NullPointerException`:** if quality is null

**Throws `IllegalStateException`:** if called off the owner thread or after closure

#### invalidate

```java
public void invalidate()
```

Forces scene binding refresh and imported texture mip regeneration on the
next render. Use after externally editing borrowed texture pixels. Cached
mesh buffers remain keyed by model identity, so this does not reupload mutated
geometry from an already cached model.

**Throws `IllegalStateException`:** if called off the owner thread or after closure

#### render

```java
public void render(Scene3D source, valthorne.camera.Camera3D sourceCamera)
```

Synchronizes supported scene models and lights, renders at the current viewport
size into the shared output texture, waits for Filament completion, and blits
color into the caller's current draw framebuffer. Rebuilds the source camera
for that size; zero-sized viewports return without rendering.

A caller GL completion wait protects reuse of the shared output texture.
The final blit restores the read-framebuffer binding and sRGB enablement on
normal return and preserves destination depth. This method does not install
a general try/finally GL-state guard around all native operations.

- **`source`** — supported triangle-model scene
- **`sourceCamera`** — camera whose projection and view are copied to Filament

**Throws `IllegalStateException`:** if called off the owner thread or after closure

**Throws `IllegalArgumentException`:** if scene renderables or explicit light values are unsupported

#### close

```java
    public void close()
```

Releases explicit and implicit lights, renderable slots, mesh buffers, imported
wrappers, materials, environment, rendering objects, shared output storage,
and the confined arena. Source models and textures remain caller-owned.
Repeated calls on the owner thread have no effect.

**Throws `IllegalStateException`:** if called off the creating thread

</details>

<a id="type-filamentrenderer3d-quality"></a>

### FilamentRenderer3D.Quality

[Source](../../src/main/java/valthorne/graphics/model/FilamentRenderer3D.java#L62)

Multisample quality presets at full output resolution. All presets retain
full-resolution ambient occlusion; INTERACTIVE disables MSAA, HIGH requests
four samples, and ULTRA requests eight, subject to backend support.

<details>
<summary>FilamentRenderer3D.Quality operation reference (3 declarations)</summary>

#### INTERACTIVE

```java
public static final  Quality INTERACTIVE
```

Full-resolution ambient occlusion with multisample antialiasing disabled.

#### HIGH

```java
public static final  Quality HIGH
```

Full-resolution ambient occlusion with four requested MSAA samples, subject to backend support.

#### ULTRA

```java
public static final  Quality ULTRA
```

Full-resolution ambient occlusion with eight requested MSAA samples, subject to backend support.

</details>

<a id="type-filamentrenderer3d-explicitlight"></a>

### FilamentRenderer3D.ExplicitLight — internal support type

[Source](../../src/main/java/valthorne/graphics/model/FilamentRenderer3D.java#L127)

Owner-thread cache for one reusable explicit native point-light slot. Stores
the last uploaded source components so moving lights update only changed
parameters without invalidating mesh/material bindings.

<a id="type-filamentrenderer3d-mesh"></a>

### FilamentRenderer3D.Mesh — internal support type

[Source](../../src/main/java/valthorne/graphics/model/FilamentRenderer3D.java#L160)

Owned immutable native mesh buffers and local bounds cached by source model
identity. Bounds use center/half-extent representation for Filament culling.

The native segments refer to cached vertex and index resources managed by the renderer.
Bounds are expressed in model-local coordinates so instances can share geometry while
supplying independent transforms; constructing this record does not allocate a mesh.

- **`vertices`** — native vertex buffer
- **`indices`** — native index buffer
- **`cx`** — local bounds center X
- **`cy`** — local bounds center Y
- **`cz`** — local bounds center Z
- **`hx`** — local bounds half-width
- **`hy`** — local bounds half-height
- **`hz`** — local bounds half-depth

<a id="type-filamentrenderer3d-entry"></a>

### FilamentRenderer3D.Entry — internal support type

[Source](../../src/main/java/valthorne/graphics/model/FilamentRenderer3D.java#L183)

Reusable native renderable/material slot associated with a borrowed source
model. The reserved light entity gains a component only when emission lighting
is enabled and nonzero.

- **`model`** — borrowed model identity used to find cached mesh buffers
- **`glass`** — whether the screen-space transmission material is selected
- **`alpha`** — whether the alpha-compositing material is selected
- **`entity`** — owned renderable entity ID
- **`light`** — owned entity ID reserved for implicit emission lighting
- **`material`** — owned native material instance

## Related guides

- [3D models, materials, scenes, and billboards](models.md)
- [Raster 3D lighting and shadow maps](lighting-3d.md)
- [Application lifecycle and window management](runtime.md)
- [Existing filament guide](../filament.md)
