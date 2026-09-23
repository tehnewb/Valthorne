# Conservative 3D visibility and occlusion

Author: Albert Beaupre

[System manual](README.md)

## Purpose

OcclusionCuller3D answers whether a conservative bound can be rejected from the current camera pass. It projects actual opaque triangles and rejects a candidate only when its expanded projected rectangle lies strictly behind and inside one retained triangle. Use it to reduce eligible draw submissions while preserving geometry needed by shadows, reflections, refraction, and other passes.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Camera-pass reset | `begin` captures the current view-projection matrix, validates positive viewport dimensions, and discards prior-frame occluders. |
| Opaque triangle collection | `addOccluder` samples actual untextured, opaque, depth-tested, depth-writing, non-transmissive triangles that are not back-face-culled. |
| Model and world bounds | Test a model with its world transform or an already conservative world-space AABB. Neither overload owns or changes the source geometry. |
| Three visibility outcomes | VISIBLE includes uncertain results; OFFSCREEN proves a shared outside clip plane; OCCLUDED proves single-triangle coverage and depth separation. |
| Bounded CPU work | At most 2048 source triangles are examined and 128 sufficiently large projected triangles are retained per pass. |
| Renderer integration | ModelBatch3D uses submission order; Filament collects occluders before classification and preserves secondary-effect contributors. |

## Getting started

1. Rebuild the camera matrix and begin with the actual viewport width and height in pixels.
2. Add only occluders that will really be drawn with matching geometry, transforms, material, and depth writes in this pass. Expand OBJ parts before direct calls.
3. Test each candidate using conservative bounds. Submit VISIBLE results, and treat rejected results as decisions for this camera pass only.
4. Begin again whenever starting another camera pass. Preserve scene membership and update simulation independently of draw rejection.

## Ownership and lifecycle

The culler owns fixed-size CPU scratch only. It borrows models, materials, transforms, and bounds for each call and does not alter GL state. It is neither thread-safe nor reentrant. addOccluder and test require a successful begin; there is no GPU resource to dispose.

## Important behavior

- A two-pixel viewport margin expands candidate coverage. Touching depth, near-plane crossings, invalid projections, gaps, and uncertain bounds remain visible unless an outside-plane rejection is proven.
- Triangles are never merged into a solid wall proxy. Candidates crossing a wall triangle boundary may remain visible, even when several triangles together cover them.
- Textured or alpha-uncertain surfaces are conservatively excluded as occluders. Budget exhaustion and small-triangle rejection reduce opportunities to cull; they do not justify assuming coverage.
- The model-batch path benefits from front-loaded opaque walls and bypasses occlusion for shadow passes. Its general culling switch must also be enabled.
- Filament retains shadow casters and active implicit emission-light contributors, and bypasses extra rejection when collected transmission exceeds 0.01. Native entries stay allocated while culled; stale values refresh before reveal.
- CPU culling has overhead when everything is visible. Compare draw/update counters and measured timings with culling disabled for the same workload.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`OcclusionCuller3D`](#type-occlusionculler3d)
- [`OcclusionCuller3D.Visibility`](#type-occlusionculler3d-visibility)

<a id="type-occlusionculler3d"></a>

### OcclusionCuller3D

[Source](../../src/main/java/valthorne/graphics/render/OcclusionCuller3D.java#L32)

Conservative, current-frame CPU visibility tests using actual opaque triangles.
A bound is occluded only when its entire projected rectangle lies strictly behind
one accepted triangle. Holes between triangles are never filled by a proxy bound.
Near-plane crossings, invalid projections and uncertain coverage remain visible.
This deliberately misses some occlusion rather than hide visible geometry.

Begin each camera pass, add its occluders, then test candidates. Occluders must
actually be drawn with depth writes in that pass. The caller must preserve objects
needed by other passes, shadows, refraction and reflection. This class does not own
models or change rendering state. It is not thread-safe or reentrant.

```java
OcclusionCuller3D culler = new OcclusionCuller3D();
culler.begin(camera.getCombined(), viewportWidth, viewportHeight);
culler.addOccluder(wallModel, wallTransform, wallMaterial);
if (culler.test(candidateModel, candidateTransform) == OcclusionCuller3D.Visibility.VISIBLE) {
    // Submit the candidate to this camera pass.
}
```

The camera matrix must already be rebuilt. Bounds and occluders must match the
geometry actually drawn in this pass; repeat begin after camera or viewport changes.

<details>
<summary>OcclusionCuller3D operation reference (5 declarations)</summary>

#### begin

```java
public void begin(Matrix4fc viewProjection, int width, int height)
```

Resets fixed-size scratch for a camera pass; no previous-frame decisions survive.

- **`viewProjection`** — current projection multiplied by view
- **`width`** — viewport width in pixels, positive
- **`height`** — viewport height in pixels, positive

#### getOccluderCount

```java
public int getOccluderCount()
```

Reads retained occluder count without testing another candidate. The count is
zero before the first begin and is reset at each subsequent begin.

**Returns:** number of projected triangles retained for the current pass

#### addOccluder

```java
public void addOccluder(Model3D model, Matrix4fc world, Material3D material)
```

Adds a bounded sample of actual triangles, without reading textures or allocating.
Textured, transparent, transmissive, back-face-culled and non-depth-writing
materials are conservatively excluded. At most 2048 triangles are examined and
128 sufficiently large projected triangles retained per pass.

- **`model`** — triangle model (expand OBJ parts before calling)
- **`world`** — current model-to-world transform
- **`material`** — effective drawn material

#### test

```java
public Visibility test(Model3D model, Matrix4fc world)
```

Tests a model's conservative bounds against the camera and accepted occluders.

- **`model`** — triangle model
- **`world`** — current model-to-world transform

**Returns:** visible whenever rejection cannot be proven

#### test

```java
public Visibility test(AABBf bounds)
```

Tests a world-space bound, for example a prepared scene subtree.

- **`bounds`** — conservative world-space bounds

**Returns:** visibility result

</details>

<a id="type-occlusionculler3d-visibility"></a>

### OcclusionCuller3D.Visibility

[Source](../../src/main/java/valthorne/graphics/render/OcclusionCuller3D.java#L39)

Result of a conservative test for one camera pass. A visible result includes
uncertain projections and does not promise that a pixel will be drawn. Rejections
apply only to this pass and must not remove geometry needed by secondary effects.

<details>
<summary>OcclusionCuller3D.Visibility operation reference (3 declarations)</summary>

#### VISIBLE

```java
public static final  Visibility VISIBLE
```

No rejection was proven, including invalid or uncertain projected bounds.

#### OFFSCREEN

```java
public static final  Visibility OFFSCREEN
```

All bound corners lie outside a common expanded camera clip plane.

#### OCCLUDED

```java
public static final  Visibility OCCLUDED
```

The expanded bound lies strictly behind and inside one retained opaque triangle.

</details>

## Related guides

- [3D models, materials, scenes, and billboards](models.md)
- [Filament rendering](filament.md)
- [Cameras and picking](cameras.md)
- [2D and 3D path tracing](path-tracing.md)
- [Performance overlays and UI inspection](diagnostics.md)
- [Existing culling guide](../culling.md)
