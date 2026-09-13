# Conservative occlusion and incremental submission — 2026-09-11

Implemented camera-pass CPU occlusion in the model-batch and Filament renderers, plus per-entry Filament update tracking and deferred uploads for eligible hidden/offscreen objects. See [the feature guide](../../culling.md) for API use and conservative eligibility rules. The path tracer retains secondary-ray geometry, and Jolt/particle lights remain active.

## Native measurements

Windows, RTX 5070, Java 25.0.2. Sequential fresh processes use 640×480, 80 warmup frames and 240 measured frames with GPU completion waits. The synthetic scene contains 512 small boxes; the hidden case adds a large opaque wall. One box moves each frame in the Filament cases, ensuring that scene changes exercise update tracking. Batch cases submit the full scene each frame. These are short local measurements, not confidence intervals or hardware-independent performance guarantees.

| Renderer/workload | Additional culling off, mean | On, mean | On p95 | Rejected |
| --- | ---: | ---: | ---: | ---: |
| Filament / hidden | 1.382 ms | 0.877 ms | 1.167 ms | 464 |
| Filament / visible | 1.332 ms | 1.468 ms | 1.753 ms | 0 |
| Filament / offscreen | 0.805 ms | 0.783 ms | 0.975 ms | 512 |
| Model batch / hidden | 1.071 ms | 0.335 ms | 0.512 ms | 464 |
| Model batch / visible | 0.799 ms | 0.864 ms | 1.396 ms | 0 |

Filament's off/on comparison retains incremental updates in both configurations: the off case updates just one changed entry; the hidden/on case updates zero entries on the final measured frame. A separate invocation through the new Gradle native benchmark task measured hidden/on at 0.955 ms mean and confirmed 464 rejections. Timing variation is visible and should not be hidden by selecting only the fastest run.

The original renderer was measured before implementation using the same native workload: hidden 1.564 ms, visible 1.606 ms, offscreen 1.055 ms means. Those baseline logs are retained, but the within-final-code toggle comparison above is the cleaner way to isolate the added culling cost. Incremental update tracking is a separate benefit.

The local FPS workload uses 1600×960, 90 warmup/360 measured frames, Jolt, particle lights and two flare shadows. Culling on/off measured **6.610/6.528 ms render means**, **7.441/7.323 ms p95**, and **0.318/0.315 ms simulation means**. Both reached 104 particles, 108 lights and 158 bodies. This does **not** establish an FPS improvement in the textured arena: textured geometry is not accepted as an occluder, and shadow casters remain native contributors. Culling has a cost even when it finds little to skip.

## CPU classification / JMH

Two fresh JVM forks, 256 MiB heap, two 500 ms warmups and three 500 ms measurements per case, with GC profiling. Each operation begins a fresh pass, adds the wall in the hidden case, and classifies every bound. No render, scene collection or native upload is included. [Raw JMH results](cpu.json).

| Candidates | Scenario | Mean ± JMH 99.9% error | Allocation |
| ---: | --- | ---: | ---: |
| 8 | hidden | 0.516 ± 0.015 µs | 0.007 B/op |
| 8 | visible | 0.437 ± 0.029 µs | 0.006 B/op |
| 8 | offscreen | 0.428 ± 0.010 µs | 0.006 B/op |
| 512 | hidden | 30.183 ± 1.429 µs | 0.419 B/op |
| 512 | visible | 27.846 ± 1.700 µs | 0.386 B/op |
| 512 | offscreen | 26.773 ± 0.189 µs | 0.371 B/op |

No collections occurred in these JMH measurements. Sub-byte allocation is profiler-level overhead; this does not claim zero allocation for the complete renderer. Occluder scratch uses fixed arrays and does not grow with scene size. The short cases do not measure every possible scene, malformed geometry, retained total heap or hardware instruction count.

## Correctness and validation

- CPU contracts cover fully hidden bounds, visible gaps inside an occluder's enclosing box, depth intersection, near-plane crossing, camera changes, invalid transforms, mirrored/nonuniform transforms, screen-edge margins, uncertain materials and storage limits.
- A native image test compares the complete framebuffer with/without hiding an occluded object. Test-only output dithering is disabled to make the comparison deterministic. It also verifies shadow/light preservation, removal of a previously active implicit light, offscreen movement, camera-only reveals, deferred upload refresh, glass fallback, occluder removal and explicit invalidation.
- Model-batch tests verify actual rejection before submission while preserving shadow passes, depth-disabled overlays and the disable switch.
- `build verify3D verifyPhysics3D verifyLighting verifyFpsArena graphicsTest verifyRelease` passes. Reports contain 148 standard tests (two optional audio-fixture skips), 93 3D tests, 32 physics tests, 27 lighting tests and 79 isolated graphics tests. Suite counts overlap. The local gameplay verifier retains 1,437 checks.
- The recorded FPS input smoke passed, including both culling-toggle directions. Its console capture was inspected locally; the image is not retained with this report. Local examples/resources remain ignored and excluded from release artifacts.

## Remaining limits

This is not full GPU hierarchical-depth occlusion. A single triangle must cover the complete padded candidate bound; coverage is not merged across triangles. Textured, transparent and uncertain occluders are exempt. Model-batch occlusion depends on submission order. Filament retains shadow casters and implicit-light contributors, and glass scenes bypass additional rejection. Native geometry stays allocated while culled. Current scene transforms/materials still need collection to recognize movement and reappearance; rendering visibility never pauses physics or gameplay. These rules prioritize image correctness and explicitly bound work at the expense of missed culling opportunities.

Reproduce with `./gradlew benchmarkOcclusion` and `./gradlew benchmarkOcclusionNative --args="hidden true"`; use `false` or add `batch` for native comparisons. Logs and source hashes are stored alongside this report.
