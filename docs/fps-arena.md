# Live Fire: first-person feature arena

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run launcher commands
from that repository. Example source and resources are separate from the engine
checkout and library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions.

Run `./gradlew runFpsArena` with JDK 25 on Windows x64 and an OpenGL 4.3-capable GPU. Click **Enter arena** to capture the mouse. Escape pauses and releases it; the test console adjusts lighting and effects. Losing window focus also pauses play.

| Control | Action |
| --- | --- |
| WASD | Move; Jolt resolves walls, floor, ramps and props |
| Mouse | Look around |
| Shift | Sprint |
| Space | Jump when grounded |
| Left mouse | Fire the rifle; hold for automatic fire |
| Right mouse | Aim with a narrower field of view |
| R | Reload |
| G | Throw a bouncing grenade |
| F | Launch a colored light flare |
| Escape | Pause/resume through the test console |

Clear approaching drone waves, protect your health and build a score. The rifle has 24 rounds, a 1.5-second reload and limited reserve ammunition. Grenades bounce under Jolt, then apply an occluded blast with damage and impulses. Crates can be pushed and destroyed. A defeated player can restart from the console. Drones and grenades have explicit population limits.

The arena uses downloaded, textured CC0 models: an M4A1 and posed arms, a Mk2 grenade, SENTRY-2 targets, factory brick/window modules, piers, cornices, metal decks and stairs, concrete barriers, shipping crates, oil drums and industrial pipes. A matching standalone magazine is also packaged for reuse; the game displays the rifle's integrated magazine. Solid downloaded panel underlays back the open metal grates. The existing Poly Haven vase, military crate and bust remain as exhibits. Each model is loaded once and reused across its instances. See [combat authors and conversion details](fps-arena-combat-models.md) and environment credits and provenance (optional local file).

Jolt uses simple collision proxies for structural modules and moving props; gallery exhibits retain mesh colliders. The stairs use a continuous ramp collider for smooth walking. The first-person capsule has locked rotation so collisions cannot roll the camera. Ground and weapon rays exclude the player through a native Jolt body filter. Diagonal movement is normalized. The sentry is an authored science-fiction robot; weapon/arms geometry is posed rather than skinned at runtime.

## Lighting and effects

Every moving debris particle and flare visibly glows and carries its own **explicit point light**. The visuals are camera-facing quads with a shared radial-opacity mask and real alpha compositing, so their edges and lifetime fade remain transparent. They also receive Filament lighting. Shooting itself creates no muzzle light or stationary impact flash: illumination follows the moving particles. Particle color can be amber, cyan or violet; attached light power, scene exposure, environment intensity and the key light are editable in the pause console.

The budget is 96 debris particles plus 12 flares: at most 108 attached lights, alongside four room lights. Hits emit ten flecks and explosions emit 36. Each pooled particle reuses its own material for independent opacity/glow fading; all share one two-triangle quad and one 32×32 radial-alpha texture. Initial opacity is 0.48, and opacity, glow and light intensity fade smoothly. `setEmissionLightEnabled(false)` prevents the glowing material from creating a second, implicit light. Native light entities are reused during movement and fading.

Flare origins are clamped before nearby walls, with clearance for their sphere radius. Launches with no safe space or no free particle slot are rejected. Jolt retains spherical colliders while the visuals face the camera. Two room lights cast shadows; attached particle lights have shadows disabled for cost. They can illuminate across occluders; the engine API allows enabling shadows on selected lights.

**Particle physics** switches debris and flares between Jolt collisions and cosmetic ballistic motion. Changing it clears existing effects. **Particle lights** switches their illumination independently; existing lights update immediately. Cosmetic particles do not collide. The world, particles and weapon timers pause together. Restart closes effect emitters before replacing the world and removes their scene/light membership.

The HUD displays FPS, render/simulation timings, particles, lights and body counts, plus health, ammo, wave progress and score. Timings include application work, not just GPU execution. Filament culls offscreen geometry; hidden physics objects continue simulating so turning the camera does not change gameplay.

## Checks and measurements

```text
./gradlew build verifyAssets verifyFpsArena
./gradlew runFpsArena --args="--smoke"
./gradlew runFpsArena --args="--benchmark"
./gradlew runFpsArena --args="--benchmark --no-particle-lights"
./gradlew runFpsArena --args="--benchmark --visual-particles"
```

The archived `verifyFpsArena` run passed 1,425 checks of native movement, walls, jumping, shooting, reloads, cover, grenades, waves, effect placement, opacity/glow fading, camera-facing geometry and resource ownership without a window. Use the companion project's `verifyCombatAssets` and `verifyEnvironmentAssets` tasks for combat/environment bounds, material groups and texture dependencies; `verifyAssets` also covers the shared gallery. Current check counts appear in task output. The native smoke test clicks the real console controls and injects native input events for movement, aiming, firing, reloading, grenades, flares, pause and restart. It writes framebuffer captures under `build/fps-arena`.

Benchmarks run hidden and uncapped with fixed simulation time, 90 warmup frames and 360 measured completed frames. The same scripted combat and moving camera compare physical effects with/without lights and cosmetic effects. Do not run them alongside the visible preview. See the [current realistic/transparent arena report](benchmarks/fps-realistic-alpha/README.md), [earlier particle-light CPU measurements](benchmarks/particle-lights/README.md), and [historical procedural-arena workload](benchmarks/fps-arena/README.md). Different asset sets and particle budgets are not equivalent performance workloads.

This is a playable engine test game with downloaded assets, procedural lane decals/UI, and transparent effects. Rendering uses Filament's direct point lighting, shadows and static environment reflections; it does not add dynamic path-traced global illumination. Source normal/roughness texture maps are not imported by the current OBJ material path; authored albedo textures and per-model material values are used.
