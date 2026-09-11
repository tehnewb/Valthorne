# Filament desktop renderer

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run demo launch tasks there; the engine's local
`src/examples/` files remain ignored and excluded from library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions.

On Windows x64, Lighting Studio defaults to **Filament / real-time PBR**. The integration uses the community `filament-ffm-runtime-windows-x64:0.4.0` binding from Maven Central, which bundles Filament with material ABI 75 (Filament 1.75). Java 25 calls its C API through the Foreign Function & Memory API; no local native compiler is required to run or build Valthorne. Other OpenGL 4.3 hosts use the studio's path tracer. See [platform support](platforms.md) and [consumer setup](getting-started.md).

## Run

```powershell
.\gradlew.bat runLightingStudio
.\gradlew.bat runLightingStudio --args="--benchmark-motion"
.\gradlew.bat runLightingStudio --args="--benchmark-motion --filament-quality=high"
.\gradlew.bat runLightingStudio --args="--benchmark-motion --filament-quality=ultra"
.\gradlew.bat runLightingStudio --args="--smoke"
```

The existing mouse orbit, pan, zoom, light selection/placement, color palette, transforms, undo/redo and rig save/load work with Filament. The overhead camera uses the same backend. The renderer dropdown retains the older path tracer for comparisons; `--pathtracer` or `--progressive` selects it at startup. Filament's footer reports FPS and completed render submission/wait time, rather than mislabeling another context's query as its GPU time.

## Engine integration

```java
// Current GLFW/OpenGL context, Windows x64, Java 25.
FilamentRenderer3D renderer = new FilamentRenderer3D();
renderer.setQuality(FilamentRenderer3D.Quality.HIGH);
renderer.setExposure(1);
renderer.setEnvironmentIntensity(1000);

// Set your destination framebuffer and viewport, then draw UI afterward.
scene.render(renderer, camera);
// Before destroying the OpenGL context:
renderer.close();
```

The adapter supports immutable triangle models, OBJ parts, visible scene-node hierarchies, world transforms, smooth normals, vertex colors, sRGB albedo textures, alpha cutouts, alpha-blended translucent materials, metallic/roughness materials and transmissive materials. Filament supplies clustered lighting, frustum culling, PCF shadows, environment reflections, ambient occlusion, temporal/FXAA smoothing and screen-space refraction. High adds 4× MSAA and Ultra requests 8× MSAA (subject to the backend's supported sample count). All presets use full output resolution and the validated full-resolution AO settings.

The GLFW context is temporarily released while Filament creates its shared driver context. Filament renders into an imported GPU texture; a GPU framebuffer blit presents it in the caller's viewport. There is no per-frame CPU pixel readback. `renderStandaloneView` avoids swapping an unused hidden window. An explicit completion wait establishes ownership before the UI consumes the texture. This serializes rendering and presentation; it is a correct initial integration, not an asynchronous pipeline.

Meshes are uploaded once per model and reused while referenced. Scene edits update native materials and transforms; camera motion does not rebuild meshes. Removed mesh and texture wrappers are evicted. Models and source textures are borrowed; the renderer owns and closes its native entities, buffers, material instances, targets and environment. Call `invalidate()` after changing source texture pixels and keep borrowed textures alive while rendering. Create, use and close the renderer on its owning context thread.

Imported albedo textures now use full mip chains and trilinear minification to reduce shimmering when fine texture details become smaller than a pixel. Mips are generated on initial import and regenerated after `invalidate()`. They share the original OpenGL texture; the base image and caller ownership are preserved. A square texture's GPU storage grows by approximately one third (2048×2048 RGBA8: 16 MiB base, about 21.33 MiB with mips). No extra CPU image copy is created by mip generation. The 1×1 fallback remains one level.

All three bundled materials set `flipUV: false`. Engine UVs and imported OpenGL images already use a consistent orientation; Filament's default additional vertex-stage flip would invert their mapping. This matters for OBJ texture atlases such as the realistic Physics Studio props. Native tests verify asymmetric rendered texture placement in addition to imported pixel data.

`RenderPass3D.TRANSLUCENT` selects the lit `alpha` material when transmission is disabled. It uses source-over compositing with fading specular/emissive contributions and depth writes disabled. Tint and texture opacity both apply, including to glowing particles. This is separate from the transmissive glass material. Native tests verify multiple opacity levels over a colored background and fully transparent corners of an emissive radial mask.

## Lighting contract and limits

- Emissive model instances create a point light at their world origin by default, using linear emission RGB and 1000 lumens per emission unit. `Material3D.setEmissionLightEnabled(false)` keeps the visible glow without an implicit point light, for example when a particle already owns an explicit light. Explicit `Scene3D` point lights follow their own position, intensity, range and shadow settings. The renderer respects material shadow flags.
- The studio's sphere/panel control changes visible source geometry. **Panels are point-light approximations, not area-light integrals**. Radius changes visible source size; PCF shadow softness does not physically follow that size.
- The bundled prefiltered HDR environment supplies static indirect illumination and reflections. Moving a local light updates direct illumination/shadows; it does not recompute bounced global illumination or the environment map.
- Glass refraction uses Filament's screen-space solid approximation and model thickness. It cannot reconstruct all offscreen objects, nested dielectric transport or path-traced caustics. Transparent glass does not cast a refracted shadow.
- This adapter currently supports **Windows x64 OpenGL texture sharing**. Filament itself supports more platforms, but the adapter explicitly rejects unimplemented sharing paths. Existing raster/2D renderers remain available; this change does not silently replace every application's render loop.
- Custom non-model renderables, normal/roughness texture maps, skinning and animated mesh-buffer updates are not mapped by this adapter. Alpha cutouts, translucent compositing and explicit material transmission are supported.

## Rebuild assets

Material source and compiled packages are committed under `src/main/resources/valthorne/filament`. To edit them, obtain `matc` from [Filament 1.75.0](https://github.com/google/filament/releases/tag/v1.75.0), then run:

```powershell
.\gradlew.bat compileFilamentMaterials -PfilamentMatc=C:/path/to/matc.exe
```

The task checks ABI 75 before compiling; packages from the newer 1.76 tool are incompatible with the pinned binding. Normal builds use committed packages and need no SDK download.

`studio-ibl.ktx` was generated with Filament 1.75 `cmgen -q -s 256 -f ktx -x <output> <input.hdr>` from [studio_small_02_2k.hdr](https://github.com/google/filament/blob/v1.75.0/third_party/environments/studio_small_02_2k.hdr), distributed by Filament under its [environment CC0 license](https://github.com/google/filament/blob/v1.75.0/third_party/environments/CC0.html). The original source is HDRI Haven / Poly Haven. The resulting cubemap contains roughness mip levels and diffuse spherical harmonics.

## Validation and measurements

The native regression exercises shared-texture rendering, a red point light changing from lit to dark (102 → 0 in the sampled red channel), scene-resource eviction, resize, caller GL state, imported alpha-cutout edits and borrowed-texture survival. Full build, 3D, physics and UI suites and the studio's native input smoke test passed.

The local audio device was unavailable during final verification; silent studio runs used `ALSOFT_DRIVERS=null`. This is a test-process setting, not a change to engine audio behavior.

See [saved benchmark reports and captures](benchmarks/filament/README.md). Timings include CPU submission and GPU completion, with 30 warmup and 120 measured frames and VSync disabled. They measure a different lighting algorithm from path tracing and are not quality-equivalent speedup claims.

References: [Filament](https://github.com/google/filament), [community desktop binding](https://github.com/Erkko68/filament-kmp), [Filament material system](https://google.github.io/filament/main/materials.html).
