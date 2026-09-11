# Physics Studio gallery and light editor — 2026-09-09

This records the earlier lightweight furniture gallery. The subsequent realistic-model request changed the scene and texture workload; see [the current realistic-model measurements](../realistic-models/README.md).

The final scene uses JOML math, Jolt physics and Filament rendering. The gallery contains three downloaded CC0 models with seven authored material parts. Its plinths reuse the existing cube mesh, and dropped models reuse the gallery geometry and cached convex-hull descriptions. Light bulbs share one mesh. The gallery retains 11 native mesh-cache entries with either 3 or 16 lights. No per-light geometry upload is needed.

Measurements use Java 25.0.2 and the NVIDIA GeForce RTX 5070 (driver 610.88), a 1600×960 window with a 960×820 scene viewport, High quality, three default lights, and disabled VSync. Each run starts a fresh process, advances Jolt by 1/60 second per frame, moves the camera, warms up 60 frames, then measures 240 frames. Render time includes Filament, synchronization, compositing and UI, with GPU completion before sampling. It is not a GPU-only timer or total application frame time. The stress workload has 288 dynamic bodies plus its static ground; gallery has 7 dynamic bodies and 7 static bodies.

| Final workload | Render mean / median / p95 (ms) | Physics mean / median / p95 (ms) |
| --- | --- | --- |
| Gallery, 3 lights | 3.150 / 3.126 / 3.473 | 0.023 / 0.011 / 0.094 |
| Same gallery, 16 lights | 4.445 / 4.385 / 4.966 | 0.026 / 0.012 / 0.110 |
| 288 dynamic bodies, 3 lights | 4.486 / 4.428 / 5.049 | 0.743 / 0.870 / 1.099 |

The gallery before removing a redundant camera rebuild measured 3.182 ms mean rendering and 0.026 ms physics. The final 3.150 ms result does **not** establish a statistically significant speedup; short desktop runs vary. The earlier lighting studio and Physics Studio used different viewports and workloads, so these figures must not be treated as a direct performance improvement over those scenes. No whole-engine memory or instruction-count reduction is claimed.

Runs were serial, with no other Java preview or system-repair process found in the process inventory. This is still a shared desktop rather than a controlled benchmark host. JMH remains appropriate for the separate CPU optimization program; completed in-application rendering measures this mixed GPU/physics feature.

Commands:

```text
gradlew build verify3D verifyPhysics3D verifyUI verifyLighting verifyPhysicsStudio
gradlew runPhysicsStudio --args="--smoke"
gradlew runPhysicsStudio --args="--benchmark --scenario=3"
gradlew runPhysicsStudio --args="--benchmark --scenario=3 --lights=16"
gradlew runPhysicsStudio --args="--benchmark --scenario=2"
```

Fresh regression runs passed 146 standard tests, 68 3D tests, 19 physics tests, 44 UI tests and 27 lighting tests, with no failures or skips. Suite counts overlap. `verifyPhysicsStudio` also passed model geometry/material validation and 83 isolated light-rig checks. Native smoke passed camera and light gestures, UI isolation, placement, toggle/duplicate/delete, repeated-key handling, imported model dropping/collision and GL error checks. Saved user rigs were untouched.

The final framebuffer captures were inspected directly for materials, cast shadows, light placement and unclipped controls. Captures are generated under `build/physics-studio/`; raw logs and final source hashes accompany this report. See [controls and behavior](../../physics-studio.md) and [model provenance](../../physics-studio-models.md).

Release documentation: this is a historical measurement record. Use the [current release checks](../../releasing.md) for building and validating the development snapshot; preserve the recorded source hashes and results.
