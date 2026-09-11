# Path-traced lighting and culling

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run demo launch tasks there; the engine's local
`src/examples/` files remain ignored and excluded from library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions.

`PathTracer3D` and `PathTracer2D` progressively compute bounced light, area shadows, metal reflections and dielectric glass on the GPU. `Lighting3D` and `Lighting2D` remain the fast gameplay renderers.

## Run the studio

```powershell
.\gradlew.bat runPathTracingExample --args="--quality=high"
```

- **Tab:** switch between the 3D material studio and 2D overhead lighting.
- **Left/right:** orbit the 3D camera.
- **1 / 2 / 3:** Interactive / High / Ultra.
- **D:** toggle spatial denoising without discarding accumulated samples.
- **V:** toggle VSync. **Escape:** close.
- FPS, frame time, quality and accumulated samples are always displayed.

With no arguments, this task now opens the [editable Lighting Studio](lighting-studio.md), with mouse navigation and light controls. The controls above describe the original renderer reference scene launched with `--quality=high`.

The reference example explicitly uses `PROGRESSIVE` mode: stationary views refine over time, and camera or scene edits restart accumulation. The editable studio and the `PathTracer3D`/`PathTracer2D` APIs now default to `REALTIME` mode. This keeps bounced-light history during camera motion by reprojecting matching surfaces at full viewport resolution. Scene, sky, quality and projection-type changes still discard history. Exposure and denoising affect presentation only. Computation stops at the stationary sample limit; subsequent stationary frames reuse the filtered image.

## Requirements and implementation

Request `JGLConfiguration.defaults().contextVersion(4, 3)`. The tracer uses the existing LWJGL OpenGL native bindings. Construction fails explicitly on older contexts; the existing OpenGL 3.3 raster renderer remains available.

This is **software ray traversal on GPU shader cores**, not RTX/DXR hardware traversal. It uses a surface-area triangle BVH, multi-bounce Monte Carlo integration, GGX reflection, Lambertian diffuse reflection, exact dielectric Fresnel/refraction, emissive triangle next-event estimation, power-heuristic multiple importance sampling, and Russian roulette termination. Area lights are selected by area and emitted luminance. Off-camera objects remain in the BVH for reflections, shadows and bounced light.

Accumulation is linear RGBA32F, with separate diffuse and reflected/transmitted signals. Diffuse lighting is divided by primary albedo before filtering and reconstructed with current albedo afterward, preserving texture contrast. Realtime primary rays and geometry guides share Halton subpixel jitter; history reprojection accounts for the previous jitter. Stationary frames accumulate in the same output pixel without repeatedly resampling history. Moving diffuse history is bounded to 64 samples; specular history uses 8–32 according to roughness, with 8 for glass. A three-pass edge-aware filter shares geometry work across both signals but uses independent radiance weights. Sharp reflections and glass receive only a small first-pass filter. Filtered color never feeds back into radiance history. ACES tone mapping and sRGB encoding run once at presentation.

Realtime pure glass evaluates weighted entry reflection, transmission through both interfaces, and exit reflection branches, twice per nominal sample. Sharp metals use four paths per nominal sample. These extra paths reduce noise without broad spatial blurring. Russian roulette compensates accumulated refractive scaling when choosing survival probability. Progressive mode retains stochastic Fresnel sampling. Texture alpha cutouts work for primary, secondary and shadow rays.

Realtime storage currently uses eleven RGBA32F images (176 bytes per internal pixel), approximately 110 MiB at 872×752, excluding geometry, atlas, framebuffer and driver storage. Separating signals increases memory; this is a deliberate quality tradeoff, not a memory optimization.

Ray traversal computes inverse direction once per ray, visits children in ray-direction order, uses a 32-entry stack with a CPU construction depth cap of 28, and terminates shadow queries on the first opaque blocker. Surface-area splits isolate large room surfaces from dense meshes. Construction sorts all three centroid axes and trades additional scene-build work for faster repeated traversal. Camera movement does not rebuild geometry. Shader sources are under `src/main/resources/valthorne/shaders/pathtrace/`.

References: [PBRT path tracing and MIS](https://pbr-book.org/4ed/Light_Transport_I_Surface_Reflection/A_Better_Path_Tracer), [Filament's material model](https://google.github.io/filament/main/filament.html), [OpenGL 4.3 GLSL specification](https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.4.30.pdf).

## 3D integration

```java
// Create after OpenGL initialization, on the context thread.
PathTracer3D tracer = new PathTracer3D()
    .setQuality(PathTracer3D.Quality.HIGH)
    .setSky(.02f, .03f, .05f) // linear environment radiance
    .setMaxSamples(4096);
Material3D gold = new Material3D()
    .setTint(new Color(.94f, .7f, .32f, 1))
    .setMetallic(1).setRoughness(.16f);
Material3D glass = new Material3D()
    .setTransmission(1).setIndexOfRefraction(1.5f);
Material3D panel = new Material3D()
    .setEmissive(1, .94f, .83f, 1).setEmissionStrength(14);

// Existing Scene3D, ModelInstance3D, OBJ parts and SceneNode3D hierarchies.
// Set the destination framebuffer and viewport first.
tracer.render(scene, camera);
// Draw the existing 2D UI afterward. At shutdown on the context thread:
tracer.close();
```

The tracer owns GPU buffers/images and borrows models/textures. It restores touched GL state, including image bindings, indexed storage-buffer ranges and pixel-transfer state. Work counters are `getSceneBuildCount()`, `getTriangleCount()`, `getBvhNodeCount()` and `getAccumulatedSamples()`.

Diffuse textures are copied into a 512-pixel-per-layer texture array when a scene snapshot changes. Call `invalidate()` after modifying an existing texture's pixels. Material, camera and transform edits are detected automatically; unchanged scenes do not upload geometry again.

## 2D integration

`PathTracer2D` gives scenery physical height instead of reconstructing it from a flattened color image. Use consistent world units, such as one unit per tile. Sprites are textured XY planes, walls are extruded boxes, and light sources are emissive spheres.

```java
PathTracer2D lighting = new PathTracer2D().setView(0, 0, 12, 100);
lighting.addSurface(-8, -6, 16, 12, 0,
    new Material3D().setTexture(floorTexture));
ModelInstance3D wall = lighting.addWall(-2, -2, .5f, 4, 1,
    new Material3D().setTint(new Color(.7f, .12f, .06f, 1)));
lighting.addAreaLight(2, 1, 3, .4f, Color.WHITE, 16);
lighting.getTracer().setQuality(PathTracer3D.Quality.INTERACTIVE);
lighting.render();
```

Viewport aspect ratio controls orthographic world width. Keep the camera above all geometry. Move returned instances directly or remove them through `getScene()`. This alternative requires explicit scene geometry; existing `Lighting2D.beginScene/endScene` games continue using their cached light maps.

## Quality and measured cost

The resolution scales below apply to `PROGRESSIVE` mode. `REALTIME` renders at 100% resolution for all three settings; samples/frame and maximum bounces remain as listed. Select the stationary algorithm with `setRenderMode(PathTracer3D.RenderMode.PROGRESSIVE)` or the studio dropdown. The older timings below predate the traversal improvements; see the [current motion benchmark](performance-program.md#accepted-pass-2-camera-motion-lighting) for current studio results.

| Setting | Internal resolution | Samples/frame | Maximum bounces |
|---|---:|---:|---:|
| Interactive | 50% on each axis | 1 | 5 |
| High | 75% on each axis | 2 | 8 |
| Ultra | 100% | 4 | 12 |

On this machine's RTX 5070 at 1100×760 output, the 12,000-triangle studio measured **8.08 ms/frame in Interactive**, **35.38 ms/frame in High**, and **140.43 ms/frame in Ultra**, with filtering enabled (roughly 124, 28 and 7 rendered frames/second). These are GPU-completed render times averaged over 120 frames after 30 warmup frames, VSync disabled. They exclude window presentation and are not guarantees for other scenes/hardware.

```powershell
.\gradlew.bat runPathTracingExample --args="--benchmark --quality=interactive"
.\gradlew.bat runPathTracingExample --args="--benchmark --quality=high"
.\gradlew.bat runPathTracingExample --args="--benchmark --quality=ultra"
.\gradlew.bat runPathTracingExample --args="--2d --snapshot=build/pathtracing2d.png"
```

Ultra increases sampling and bounce depth, with a substantial GPU cost. Realtime history reduces motion noise but cannot supply missing information for newly exposed surfaces. View-dependent reflections and refraction may show residual noise or short temporal lag; filtering can soften details. D shows the raw result in the reference example. Scene edits currently rebuild the BVH rather than refitting it, so rapidly moving large scenes should use the raster renderer.

Not included: hardware RT extensions, dedicated caustic solver, volumetric scattering, nested-medium stack, normal/roughness texture maps, HDR environment-image loader or depth of field. Temporal history uses primary-surface reprojection rather than separate reflection hit distances or motion vectors for animated geometry. Glass uses ideal smooth refraction with surface tint; roughness controls opaque reflection. Raster-only depth/blend/fog/shadow material flags are not tracing controls. Non-model renderables, including billboards, produce an explicit unsupported-type error; draw such effects separately.

## Engine draw culling

- JOML `FrustumIntersection` extracts six normalized planes once per camera rebuild. Each box uses one support vertex per plane, replacing repeated eight-corner matrix transforms. Boundary geometry is conservatively retained.
- `ModelBatch3D` culls loose meshes and billboards. Scene-node submission also rejects whole subtrees using aggregate bounds. Parent transforms, direct mutable-vector edits and descendants extending beyond their parent's model are included. `getCulledSubtreeCount()` counts grouped rejections; model counters include skipped descendants. Bounds refresh during submission; this is not a persistent spatial index.
- `TextureBatch` rejects offscreen standard-shader quads before texture binding/upload. Rotation, origins, negative dimensions, translations and active projection are included. Custom shaders are conservatively exempt because they may displace vertices.
- `setCullingEnabled(false)` enables reference comparisons. Sprite lifetime counters: `getTotalSubmittedSprites()` and `getTotalCulledSprites()`.

Culling does not stop physics or animation. Camera culling does not remove secondary-ray geometry. No GPU occlusion-query or hierarchical-depth occlusion pass is claimed.

## Verification

`verify3D` includes 24,000 randomized frustum comparisons against an independent corner-transform reference, transformed sprite boundaries, GPU pixel equivalence with culling on/off, 100-model subtree rejection and mutable-transform restoration, actual compute shader execution, area lighting, texture alpha cutouts, orthographic lighting, accumulation invalidation and GL state restoration. Run `build verify3D verifyPhysics3D verifyUI` for complete regression checks.
