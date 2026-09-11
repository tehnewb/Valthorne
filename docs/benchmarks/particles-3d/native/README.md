# Native particle studio measurements — 2026-09-09

Java 25.0.2, Windows x64, NVIDIA RTX 5070, driver 610.88. These are completed-frame workload comparisons, not controlled before/after engine speedups. The studio ran hidden and uncapped at 1600×960, with a 960×820 Filament viewport, High quality, three editable lights, and the default environment/exposure. The camera moved by 0.15 degrees/frame. Physics advanced by 1/60 second/frame. Each process warmed up 60 frames and measured 240 frames; `glFinish` completed rendering before timing ended.

| Workload | Particles at finish | Native bodies | Render mean / median / p95, ms | Simulation mean / median / p95, ms |
| --- | ---: | ---: | --- | --- |
| Fountain, Jolt, 48 births/s | 192 | 201 | 4.055 / 3.991 / 4.796 | 0.343 / 0.334 / 0.520 |
| Fountain, visual, 48 births/s | 193 | 9 | 4.361 / 4.264 / 5.226 | 0.064 / 0.059 / 0.115 |
| Fountain, Jolt, 120 births/s | 480 | 489 | 4.939 / 4.870 / 6.440 | 0.548 / 0.515 / 0.943 |
| Existing stress scene, 288 dynamic bodies | 0 | 289 | 4.708 / 4.650 / 5.443 | 0.801 / 0.919 / 1.223 |

The fountain uses one shared 48-triangle particle mesh, four non-emissive materials, nine static collision bodies, four cached meshes overall and a 512-particle cap. The measured interval includes filling and steady population, not 240 frames at the final count. Physical and visual birth timing differs by a fixed tick, hence the one-particle count difference. Visual particles do not collide and can leave the tray/viewport; their rendering workload differs spatially. The higher visual render mean does not establish that removing physics makes rendering slower.

Simulation timing includes the world update and visual-particle update, where applicable. Rendering includes scene collection, Filament, synchronization, compositing and UI; it is not a GPU-only timer. Full-application FPS also includes work outside these timers. Native body birth/death, mesh reconciliation and scene snapshot allocation remain real costs.

Commands use `./gradlew runPhysicsStudio --args="..."` with:

```text
--benchmark --scenario=4
--benchmark --scenario=4 --visual-particles
--benchmark --scenario=4 --particle-rate=120
--benchmark --scenario=2
```

The archived logs correspond to the first tested core candidate, including its native parent-transform correction. A later refinement changes the cosmetic update loop; its final visual timing is recorded separately rather than replacing these measurements. The physical workload and renderer are unaffected by that refinement. The parent report preserves exact measured particle source snapshots and hashes.

The final visual run measured **4.224 / 4.059 / 5.335 ms** render and **0.061 / 0.047 / 0.132 ms** simulation (mean / median / p95), with 193 particles and nine native static bodies. This short repeat confirms that the refined code runs correctly under the native workload; timing variation does not establish a render speedup. See `particle-visual-final-benchmark.txt`.

Native smoke runs passed for the realistic gallery and the particle scene starting in each mode. Actual mouse events exercise orbit, zoom, pause/single-step, UI isolation, editable lights, stopping emission, clearing, bursting and Jolt off/on. Checks verify owned-body counts, finite particles above the floor and matching mesh positions. The gallery still passes imported-model drops and focus controls. Framebuffer inspection confirmed visible colored particles, cast shadows across the tray/deflectors, and fully visible controls.

The final smoke also clicks the scene selector from gallery to particles and back. It verifies closed prior worlds/emitters, removed particle meshes, retained light identities and restored model-drop controls. All required build checks pass: 173 standard tests, 79 3D checks, 27 physics checks, 52 UI checks and 27 lighting checks (suites overlap), with zero failures/errors/skips. Asset validation covers all six imported models; the light rig adds 83 headless checks. See `particle-final-verification.txt`, `particle-scene-switch-smoke.txt`, and `particle-final-smoke.txt`.

Release documentation: this is a historical measurement record. Use the [current release checks](../../../releasing.md) for building and validating the development snapshot; preserve the recorded source hashes and results.
