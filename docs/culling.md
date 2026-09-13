# Visibility and occlusion culling

The engine rejects offscreen standard 2D sprites and performs 3D frustum culling. Camera-pass occlusion now supplements those checks in `ModelBatch3D` and `FilamentRenderer3D`, enabled by default. `PathTracer3D` retains geometry outside the camera for secondary rays, but still excludes explicitly invisible renderables and hidden node subtrees.

`OcclusionCuller3D` projects actual opaque triangles for the current camera. A candidate is rejected only when its entire projected bound, with a two-pixel margin, is behind and inside one accepted triangle. It never treats a mesh's enclosing box as a solid wall. Near-plane crossings, uncertain projections, uncovered gaps and touching depth remain visible. Storage is fixed at 128 occluder triangles, with at most 2048 source triangles examined per pass. These limits bound the work and can miss opportunities to cull; they do not permit false coverage.

Occluders must be untextured, opaque, depth-tested, depth-writing, non-transmissive and not back-face-culled. Texture transparency is not inferred. In particular, a downloaded textured building is not automatically a solid occluder. Small projected triangles are skipped. Multiple triangles are not merged into coverage, so objects crossing a wall's triangle boundary may remain visible.

## Model-batch renderer

Submit large opaque walls before smaller models. Previously accepted submissions provide occluders for later submissions; hidden objects are rejected before instance/material snapshots and drawing. Shadow passes bypass occlusion. Overlay models with depth testing disabled remain visible. Custom procedural renderables and billboards are not occlusion candidates in this path.

```java
batch.setOcclusionCullingEnabled(true);
scene.render(batch, renderState);
int hidden = batch.getOccludedCount();
```

Turning off the existing `setCullingEnabled` also bypasses these occlusion tests. The existing frustum and subtree counters remain available.

## Filament renderer

The renderer gathers current opaque occluders before classification, so scene insertion order does not determine whether a candidate can be rejected. Only non-shadow-casting objects without active implicit emission lights are eligible for removal from the native scene. Explicit point lights are updated independently and remain active. A scene containing transmission above 0.01 bypasses additional camera rejection to preserve screen-space refraction. Native Filament frustum culling continues operating.

```java
renderer.setOcclusionCullingEnabled(true);
renderer.render(scene, camera);
int hidden = renderer.getOccludedCount();
int offscreen = renderer.getOffscreenCount();
int refreshed = renderer.getUpdatedEntryCount();
```

Native entries stay allocated while culled. Their transform/material uploads are deferred and refreshed before reappearing, including camera-only reveals. Unchanged visible entries are no longer resent because an unrelated object moved. `invalidate()` still forces refresh. Objects are still collected and their current transforms/materials inspected; physics, animation and gameplay simulation are not suspended by rendering visibility.

Filament's scene/layer visibility also controls shadow participation, so this integration deliberately retains shadow casters instead of dropping their shadows. See [Filament's visibility-mask implementation](https://github.com/google/filament/blob/v1.75.0/filament/src/details/View.cpp#L936-L961). This is a conservative CPU culling system, not GPU hierarchical-depth occlusion or a separate camera-only shadow-caster submission path.

## Controls and measurements

The optional local FPS test console includes **Occlusion culling: on/off**; `--no-occlusion` disables it for a comparison. Jolt and particle lights remain active. Local examples/resources remain excluded from release artifacts.

Run `./gradlew benchmarkOcclusion` for forked JMH CPU/allocation measurements. Run `./gradlew benchmarkOcclusionNative --args="hidden true"` for the Filament wall workload, replacing `true` with `false` for comparison. Add `batch` for the model-batch renderer. Native workloads require a graphics context; Filament requires its supported Windows x64 integration.

Occlusion has overhead when everything is visible. [Measurements, validation and limitations](benchmarks/occlusion/README.md) include that case rather than claiming a universal FPS gain.
