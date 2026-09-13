# Original FPS application on desktop and web

The original `FpsArena` now uses the same Java source and resources on both
targets. This includes the arena, imported environment and combat models,
weapon and arms, drones, Jolt movement and raycasts, grenades, transparent
particles with optional physics and attached lights, and the original HUD/menu.
The smaller `FirstPersonDemo` remains a separate browser integration fixture in
`portable/fixtures/java`; it is excluded from published engine artifacts.

## Implementation

Native UI calls in the application now use `Canvas2D`. Native graphics and
diagnostic input operations use `PlatformTools`, with desktop and browser
implementations inside the engine. Gameplay imports no browser implementation.
Model text parsing and numeric clamps use operations supported by both Java
targets. The build accepts `applicationIncludes` to select the game's sources
without including unrelated desktop examples.

The browser host disables its playground orbit handler for Java applications.
This fixes pointer-capture errors when entering the FPS with pointer lock.
It also hides the playground footer so that it does not overlap the game HUD.

## Reproduction

With Java 25, Node 24, and `npm ci` completed in `portable/web`:

```sh
node portable/fps.mjs verify desktop
node portable/fps.mjs verify web
node portable/fps.mjs run web
```

`build web` produces the static distribution. `run desktop` launches the same
selected source files against the desktop engine. Web verification writes
screenshots and `full-fps.json` to `portable/web/build/verification`.

## Validation

Desktop gameplay validation passed 1,437 checks. Desktop input smoke passed
movement, jump, mouse look, aiming, shooting, reload, grenades, flares, lighting,
particle settings, pause/repeat isolation, and restart. Peak smoke workload was
51 particles and 55 lights. The FPS asset validation task also passed.

The browser test operates the rendered controls and input events. It checks
movement, jump, zoom, firing effects, reload, a live grenade and its fuse,
flare particles with Jolt bodies and lights, pause, settings, five restarts,
and exit. Menu and combat screenshots were visually inspected. A recorded
combat snapshot contained 41 physics particles, 45 lights and 101 bodies.

Across five restarts the Jolt heap stayed at 128 MiB, free allocator memory
stayed at 101,873,120 bytes, and each reset returned to one world, 60 bodies,
20 meshes, 471 render entries and 52 tracked graphics objects. Exit returned
tracked physics worlds, renderers, graphics objects, Yoga objects and Nano
contexts to zero. These counts do not establish whole-process memory usage or
long-session leak freedom.

## Measurement scope

Local measurements use headless Chrome at 1600 × 960 on this Windows machine
with an NVIDIA RTX 5070. The report samples 360 animation-frame intervals during
normal play and another 360 while firing with a flare and grenade. Browser
frame intervals include presentation scheduling; they are not GPU timer-query
measurements or uncapped engine throughput. The combat sequence is short and
includes reload/effect decay. No Raspberry Pi, Android, mobile touch input,
other browser, or minimum hardware performance claim is made.

The CI workflow includes the full FPS functional test. The hosted job has not
been run locally, and its software-rendered timings are not hardware benchmarks.

Final local results (Chrome 152, device pixel ratio 1):

| Scenario | Samples | Mean frame interval | 95th percentile |
| --- | ---: | ---: | ---: |
| Normal play | 360 | 8.317 ms | 10.2 ms |
| Firing, flare and grenade | 360 | 8.326 ms | 10.9 ms |

The selected desktop source build also passed. Five shared-source fixtures
(application, graphics, UI, physics and scene) passed on desktop and browser,
including 196 physics checks per target. All six existing browser example
tests passed, covering the lab, smaller arena, bounded streaming audio,
skeletal/morph animation, WebGPU compute and remaining platform operations.
Declared API comparison includes all 16 `Canvas2D` and 11 `PlatformTools` members.
