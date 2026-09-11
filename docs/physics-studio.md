# Physics Studio

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run demo launch tasks there; the engine's local
`src/examples/` files remain ignored and excluded from library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions.

Run `./gradlew runPhysicsStudio` on Windows x64 with JDK 25 and an OpenGL 4.3 capable GPU.

The laboratory combines the engine's Jolt rigid-body bindings with the Filament renderer. Engine vectors, matrices and quaternions use JOML; simulation remains handled by Jolt. It opens in the **Design gallery** scene and includes:

- Three realistic Poly Haven models on display plinths, plus moving spheres: a patterned ceramic vase, a weathered military crate and a carved marble bust. Each uses its original 2K diffuse texture.
- A 63-block pyramid, four metal spheres, and a sinusoidally driven kinematic platform.
- Three domino chains and a sloped rolling-ball ramp.
- A stress scenario with 288 mixed spheres and cubes.
- A 3D particle fountain with angled collision deflectors, a catch tray, and optional Jolt physics. Its shared mesh particles respond to the editable light rig.
- Continuous collision detection for dynamic bodies, fixed-step simulation, sleeping, native broad-phase collision acceleration, and Filament frustum culling.
- Studio environment reflections, a warm key/cool fill/backlight rig, shadow maps, ambient occlusion, antialiasing, and metallic/roughness materials.

The realistic model set totals 8.24 MB, including its three original 2048×2048 JPEG textures, geometry and CC0 provenance. Three lightweight Kenney furniture models remain available in the drop selector and add 83 KB. See [realistic model sources and conversion details](physics-studio-realistic-models.md) and [Kenney furniture provenance](physics-studio-models.md).

All models have their display scale baked into geometry and are normalized to Z-up with their bases at zero, so rendering and collision geometry share the same local coordinates. The bust faces the viewer through the same 180-degree yaw on its rendered instance and physics body. Display exhibits use static triangle-mesh collision. **Drop selected model** creates a moving copy with a convex-hull collider, which fills concave gaps and the vase opening for rigid-body testing.

The exhibits use uniform roughness values of 0.30 for the vase, 0.80 for the crate and 0.55 for the bust. Textures use trilinear mipmap filtering to reduce distant shimmer. These are diffuse-textured surfaces with detailed geometry; the original assets' normal and roughness texture maps are not loaded.

## Controls

| Action | Control |
| --- | --- |
| Orbit the camera | Right-drag in the scene |
| Pan the camera | Middle-drag in the scene |
| Zoom | Mouse wheel over the scene |
| Inspect a displayed model closely | Select it under **Imported models**, then **Focus selected exhibit** |
| Restore the overview | **Reset camera** |
| Push a dynamic body | Click the body |
| Launch a sphere | Space |
| Select and move a light horizontally | Click and drag its bulb |
| Change a light's height | Shift + drag its bulb |
| Reset the current physics scene | R or **Reset scene** |
| Cancel light placement | Escape |
| Close the studio | Escape when placement is inactive |

The left panel selects scenarios, pauses/resumes, advances one fixed step, adjusts gravity/time scale, and spawns cubes, spheres or any of six imported models. **Focus selected exhibit** is enabled for the first three imported models—the vase, crate and bust—while the Design gallery scenario is active. It frames the selected exhibit closely for lighting inspection; **Reset camera** restores the overview. Mass and bounce apply to subsequently created bodies, including bodies after a reset. Spawning stops at 600 dynamic bodies; reset releases the previous physics world and retains the current light rig.

The right **Light rig** panel supports up to 16 lights. Select a bulb in the scene or choose its name in the list. Its position, power and red/green/blue sliders update immediately. The color presets offer warm white, daylight, ice blue, mint, rose, violet and amber. **+ Place light** arms placement; click a scene surface to create a light above it. **Duplicate**, **Switch off/on** and **Delete** act on the selected light.

**Save rig** writes light positions, colors, powers and enabled flags to `build/physics-studio/rig.properties`; **Load rig** restores that file. Environment intensity and exposure are adjustable in the left panel. Lower environment illumination and weaker fill lights reveal stronger shadow contrast; raising them produces softer contrast while retaining environment reflections.

FPS measures application frames. Physics timing includes world update and model synchronization. Render timing includes Filament, synchronization, compositing, and UI; it is not a GPU-only timer. Shared meshes avoid uploading duplicate geometry for each body.

## Validation and benchmarks

The **Particle fountain / Jolt** scenario replaces spawn settings with emission, lifetime, bounce, physics on/off, burst and clear controls. Its fountain scatters colored mesh particles onto sloped deflectors and a catch tray. The shared world drives physical particles; visual mode uses independent ballistic motion without collisions. Both modes honor pause/single-step, gravity and time scale. The live particle count is capped at 512. Run `./gradlew runPhysicsStudio --args="--scenario=4"`; see [3D particle API and lifecycle](particles-3d.md).

Use `--smoke --scenario=4` for actual particle-button interaction and body cleanup checks. `--benchmark --scenario=4` measures physical particles; add `--visual-particles` for visual particles or `--particle-rate=120` for a heavier emission load. Scene indices now range from 0 through 4; the gallery remains the default.

`./gradlew build verifyPhysicsStudio`

`./gradlew runPhysicsStudio --args="--smoke"` checks native mouse orbit/wheel events, actual pause/resume/single-step buttons, bulb picking and horizontal/height dragging, surface placement, toggle/duplicate/delete buttons, imported-model drops and collisions, finite simulation positions, and OpenGL errors. It also checks that UI clicks, second mouse buttons, wheel events during dragging, and repeated Escape events cannot interfere with scene controls. It writes `build/physics-studio/scenario-3.png` from the rendered framebuffer by default. Use `--scenario=0`, `1`, `2` or `3` to select another scene.

The CPU-only `valthorne.examples.PhysicsStudioModels` main class verifies all six packaged models' dimensions, centering, grounding, material groups and finite unit normals. The realistic models contain 9,408 triangles for the vase, 10,476 for the crate and 17,456 for the bust. The Kenney sofa, lounge chair and desk chair contain 232, 376 and 656 triangles respectively.

`verifyPhysicsStudio` runs this asset validation and 83 headless light-rig checks, including editing, capacity, persistence roundtrips and malformed files. It uses unique temporary files and does not touch the user's saved rig. Individual tasks are `verifyPhysicsStudioAssets` and `verifyStudioLightRig`.

`./gradlew runPhysicsStudio --args="--benchmark --scenario=2"` runs hidden and uncapped, advances simulation at 1/60 second per frame, moves the camera, warms up 60 frames, then measures 240 frames with completed rendering. Scenario indices are 0 for pyramid/platform, 1 for dominoes/ramp, 2 for the stress scene and 3 for the imported-model gallery. It prints mean/median/p95 render and physics times and saves a framebuffer image. This end-to-end GPU/physics workload is measured in the application rather than JMH.

Use `--benchmark --scenario=3 --lights=16` to compare the same gallery at the editor's light limit. `--lights` accepts 3–16 initial lights. [Current realistic-model measurements](benchmarks/realistic-models/README.md) record three-light, sixteen-light and 288-body workloads. [Earlier furniture/editor measurements](benchmarks/physics-editor/README.md) use a different model set. Check the report's scene and implementation before comparing timings across gallery revisions.

Raw before/after measurements and verification are in [benchmarks/physics-studio](benchmarks/physics-studio/README.md). Desktop timing is variable; these short runs are diagnostic, not a statistically controlled speedup claim. The earlier renderer cleanup removed inactive native light components from non-emissive meshes. The initial rig has three lights; the editor can add more, increasing lighting work. The cleanup alone does not establish a frame-time improvement.

See [Filament integration](filament.md) for platform support and lighting limitations. The renderer uses static environment lighting and point-light shadows, not dynamic path-traced global illumination or physical area-light transport.

## Lighting contrast correction — 2026-09-09

The original lab filled its shadows with a bright environment and three similarly strong lights. The revised preset uses a dominant 350 k-lumen key, fill at 8% of key, rim at 25%, environment intensity 100, and exposure multiplier 3.5. The previous values were key 120 k-lumens, fill 70%, rim 100%, environment 500, and exposure 1. This changes the lighting ratio and restores image brightness with exposure; it is not simply a darker preset.

The light editor now exposes each light's individual power, color and position, replacing the earlier key/fill-ratio controls; **Environment** and **Exposure** remain separate. The key, fill and rim start at 350, 28 and 87.5 in the power controls, corresponding to the ratios above. A framebuffer regression compares the same floor pixel with and without an intervening cube: with environment disabled, its red channel was 0 in shadow and 66 unobstructed. This verifies actual cast-shadow occlusion rather than only verifying that light intensity changes.

This balance follows Filament's guidance to use stronger direct light and darker indirect illumination when stronger contrast is needed: [Filament material lighting guidance](https://google.github.io/filament/main/materials.html). The lab retains image-based reflections and real-time point-light shadows; it is not dynamic path-traced GI.
