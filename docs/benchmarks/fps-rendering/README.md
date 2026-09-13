# FPS rendering optimization pass — 2026-09-11

The requested 10× frame-rate improvement is **not achieved**. Rendering dominates this workload; physics is roughly 0.4–0.5 ms/frame. These results are provisional because PathOfExileSteam was running concurrently and the GPU showed activity outside the benchmark. No other user application was stopped.

## Retained changes

- Exact indexing of all 13 vertex attributes before immutable Filament uploads, preserving raw float bits, triangle order, normals, UVs and colors. Temporary indexing storage is released after upload. This does not reduce CPU source-model storage or triangle count.
- Separate compiled opaque material without alpha discard, selected only when the complete alpha calculation cannot discard. Unknown textured alpha retains the existing shader. The local demo's opaque JPG environment materials explicitly use zero cutoff. Alpha-blended particles and glass retain their dedicated materials.
- Opt-in renderer phase timing and cumulative vertex-upload counters for diagnosis.
- Optional PERFORMANCE quality preset uses half-resolution AO and disables MSAA. The FPS demo still defaults to HIGH. Lower quality was not the measured fix and is excluded from the comparison below.

Automatic instancing/shared material pooling was removed after the experiment regressed timings (10.763 ms render mean). No shared-material ownership changes remain.

## Same-binary comparison

Windows 11, RTX 5070, driver 610.88, Java 25.0.2, Filament 1.75.0 binding. Hidden 1600×960 framebuffer, HIGH quality, uncapped, 450 scripted combat frames: 90 warm-up and 360 measured frames. Both variants retain Jolt, particle lights, two particle shadow lights, culling and all scene assets. Peak 104 particles, 108 lights, 158 bodies, 21 cached meshes. Render timing includes UI rendering and GPU completion; it is not a pure GPU timestamp or whole-frame FPS measurement.

Order: candidate, original, original, candidate. “Original” disables the two renderer optimizations in the same binary; both variants use the demo's explicit opaque environment cutoff. Thus this is an ablation comparison, not a frozen checkout of the initial scene.

| Variant/run | Render mean ms | Median ms | p95 ms | Simulation mean ms |
| --- | ---: | ---: | ---: | ---: |
| Candidate 1 | 7.996 | 7.587 | 10.389 | 0.453 |
| Original 1 | 8.818 | 8.314 | 12.254 | 0.477 |
| Original 2 | 7.933 | 7.745 | 9.922 | 0.463 |
| Candidate 2 | 7.541 | 6.994 | 9.732 | 0.409 |

Average render mean: 8.376 → 7.769 ms, approximately 7.2% less time (1.078× rendering throughput). Short runs and concurrent GPU activity prevent a statistically reliable speedup claim. The initial pre-experiment run was 7.924 ms, which further demonstrates why comparisons between separate sessions should not be treated as firm gains.

Uploaded vertices: 314,709 → 75,316, a deterministic 76.1% reduction in vertex-buffer bytes (52 bytes/vertex). Saves 12,448,436 bytes, about 11.87 MiB, across those uploads; unchanged index-buffer bytes and unrelated textures/native memory are excluded. Counters are cumulative uploads, not current retained GPU memory.

Dominant instrumented stage remains Filament render submission plus completion wait, about 6–7 ms. Caller GL completion and Filament completion protect shared-texture ownership and were preserved. Removing these waits without redesigning resource synchronization is not a safe optimization.

## Reproduction

Local ignored example/test sources are deliberately excluded from the published library. With those sources present, use `runFpsArena --args="--benchmark"`. The included `compare.gradle` init script enables timing; add `-PoriginalMesh` to disable solid-material selection and indexing together. For example:

```
.\gradlew.bat -I docs/benchmarks/fps-rendering/compare.gradle runFpsArena --args="--benchmark"
.\gradlew.bat -I docs/benchmarks/fps-rendering/compare.gradle -PoriginalMesh runFpsArena --args="--benchmark"
```

Raw local logs: `.codex-temp/fps-speed/compare-{candidate,original}-{1,2}.log`. Earlier exploratory logs and the complete validation log are in that directory. The diagnostics switches are JVM startup properties, not runtime configuration APIs.

## Validation

`build verify3D verifyPhysics3D verifyLighting verifyFpsArena graphicsTest verifyRelease` passed, including 1,437 FPS gameplay checks and published-library consumers. The added native cutoff-transition regression passed in a subsequent focused graphics run. Vertex compaction tests reconstruct every raw attribute, including signed zero and distinct NaN payloads. FPS `--smoke` passed movement, jump, aiming, shooting, reload, grenades, flares, light/physics/color editing, pause and restart. The actual combat framebuffer was inspected.

Remaining work: obtain uncontended, longer GPU stage measurements, then target shadow/main-pass costs and investigate properly synchronized rendering overlap. These require further evidence; this pass does not establish a route to 10× improvement at identical scene quality.
