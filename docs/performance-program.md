# Engine performance program

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run demo launch tasks there; the engine's local
`src/examples/` files remain ignored and excluded from library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions. Engine `verify*` and benchmark tasks in
those records still require their optional local test/benchmark sources; use the
companion project's `build` and documented smoke tasks for current examples.

This is an ongoing measurement program, not a claim that every subsystem has been optimized or that no further optimization exists. Latency, allocation, resident memory, throughput and code complexity can conflict. CPU instructions and GPU work are not inferred from elapsed time alone.

## Rules for each change

1. Select a reproducible workload and record the current revision/file state, JVM, hardware and settings. Keep unrelated work out of the comparison where possible.
2. Record baseline timing and allocation. Use forked JMH for CPU hot paths and GPU-completed frame measurements for graphics. Do not bypass JMH's lock or overlap benchmark processes.
3. Make one coherent candidate change. Add regression coverage for meaningful failure modes, including aliasing, boundaries, lifecycle and mutable inputs.
4. Run the relevant tests and the same benchmark immediately. Keep raw reports. Compare multiple workloads (small/large, static/dynamic, hit/miss or sparse/dense).
5. Retain demonstrated wins; revert regressions or explicitly document a justified tradeoff. Treat overlapping confidence intervals as inconclusive. Allocation reductions are not automatically latency improvements.
6. Finish with integration tests. Update this ledger and choose the next bottleneck. Avoid unrelated rewrites and public behavior changes for speculative savings.

## Current coverage and queue

| Area | Evidence available | Next measurement |
|---|---|---|
| Math, camera, transforms | Forked JMH product/composition/camera and 1/256-instance static/moving transform batches; randomized aliasing/projective/inverse/cache tests | Profile hierarchical bounds and whole-scene snapshot allocation |
| 3D path tracing | GPU-completed Interactive/High/Ultra; real shader tests | Static snapshot allocation, moving light updates, BVH rebuild/refit, texture memory |
| Raster meshes/billboards/culling | Visibility and image-equivalence tests; 258-light tiled/all-light comparison | Static/dynamic meshes, material batching, visibility distributions |
| 2D lighting/sprites | Cached/static/moving 260-light benchmark; image-equivalence tests | Light-map sizes, shadow updates, rotated sprite batches |
| UI/layout/text | Separate UI task maintains JMH allocation budgets and mixed-renderer tests | Use its reports; coordinate before editing shared UI files |
| Jolt physics | Native regression suite and physics rendering workload | Headless stepping across sleeping/active body counts and contacts |
| Particles/animation | Functional coverage for 3D | 2D/3D emitter updates at different occupancy and animation counts |
| Events/input/window/ticks | Native mouse/UI integration checks | Publication with zero/one/many listeners, subscription churn, allocations |
| Collections/pools | Not measured in this pass | Primitive/object workloads, resizing, iteration, pool reuse |
| Assets/fonts/textures | Rendering/asset loading regressions | Cached/uncached loading, atlas construction, text layout, upload memory |
| Audio | Not measured in this pass | Decoder throughput, streaming refill allocation, source churn |
| IO/buffers/cache | Not measured in this pass | Sequential/random IO, buffer growth, cache hit/miss, large archives |
| Compression/encryption | Not measured in this pass | Round-trip correctness and representative payload sizes; preserve security contracts |
| State machines/scenes/plugins/utilities | Not measured in this pass | Transition dispatch, scene lifecycle and loading; prioritize profiles before changes |

## Commands and report locations

```powershell
.\gradlew.bat benchmarkEngine '-PengineBenchmarkReport=build/reports/engine-benchmark/results.json'
.\gradlew.bat benchmarkUI
.\gradlew.bat runLightingStudio --args="--benchmark"
.\gradlew.bat runPathTracingExample --args="--benchmark --quality=interactive"
.\gradlew.bat runPhysics3DExample --args="--benchmark --stress-lights"
.\gradlew.bat runLighting2DExample --args="--benchmark --stress-lights --static"
.\gradlew.bat build verify3D verifyPhysics3D verifyUI
```

`TransformBenchmark` uses changing inputs and escaping results, two forked JVMs, three one-second warmups, five one-second measurements and JMH's GC profiler. [JMH documentation](https://github.com/openjdk/jmh) explains methodology and common pitfalls. Run timing comparisons on an otherwise idle machine; ordinary desktop scheduling adds noise.

Baseline reports for the first matrix pass are in `build/reports/engine-benchmark/before.json`; candidate results are in `after-matrix.json`. These generated reports live in the local build directory. Stable aggregate findings belong in this document; do not claim unmeasured subsystem improvements.

Archived full JMH output for the accepted matrix pass: [before](benchmarks/matrix-before.json) and [after](benchmarks/matrix-after.json), including score errors, JVM settings and allocation metrics.

## Accepted pass 1: matrix operations

Measured on the local Java 25 runtime with the above JMH settings. Times are means; bytes are normalized allocation from the GC profiler.

| Workload | Before (ns/op) | After (ns/op) | Before (B/op) | After (B/op) |
|---|---:|---:|---:|---:|
| Matrix product | 17.68 | 9.13 | 80 | approximately 0 |
| Translation + three rotations + scale | 110.19 | 50.24 | 800 | approximately 0 |
| Camera rebuild | 131.92 | 113.89 | 160 | approximately 0 |

Products and inverses compute scalar results before storing, preserving aliasing safety without temporary arrays. Translation, rotation and scale update the relevant columns directly, including projective fourth-row terms. No per-instance scratch array is added. `get()` remains a borrowed mutable backing array; callers requiring snapshots must copy it.

The regression suite compares randomized products with left/right/self/shared-array aliases, specialized transforms against general projective multiplication, inverse round trips, singular-inverse preservation, and the existing graphics/physics/UI tests. This pass does not claim fewer hardware instructions based solely on Java source or timing.

The complete 1440×900 Lighting Studio measured 10.89 ms/frame before and 10.48 ms/frame afterward (30 warmup + 120 GPU-completed measured frames). This small single-run difference is inconclusive for end-to-end speedup; UI work was also concluding during this session. The accepted evidence is the repeatable JMH timing/allocation improvement and passing tests.

An hourly follow-up attached to the current task continues this queue. It performs bounded, measured changes and reports meaningful outcomes. Scheduled runs depend on the local Codex host being available; this is not a continuously running native profiler.

## Accepted pass 2: camera-motion lighting

The studio no longer discards all bounced-light information on every camera update. `REALTIME` is the default for both tracer APIs and the editable studio; the original stationary reference explicitly selects `PROGRESSIVE`. Realtime uses full viewport resolution, validated bilinear history reprojection, per-surface filtering and a frame sequence independent of camera resets. Geometry, material, lighting and environment edits invalidate history. Newly revealed surfaces and view-dependent glass/reflections can still exhibit noise or temporal lag; this is not a hardware-RT or noise-free rendering claim.

Measured on the local RTX 5070, Java 25, 1440×900 studio window with an 872×752 scene viewport; Interactive, VSync off, complete UI included, GPU completion awaited. Each timing run has 30 warmup and 120 measured frames. Continuous motion is a fixed orbit increment per frame, so these are equal-work frame comparisons rather than equal-wall-time camera trajectories.

| Workload / implementation | Mean ms | Median ms | p95 ms |
|---|---:|---:|---:|
| Original progressive, moving, half-resolution axes | 10.446 | 10.226 | 12.113 |
| Realtime full resolution, before traversal optimization | 42.012 | 41.748 | 46.894 |
| Realtime full resolution, optimized final validation | 17.744 | 17.655 | 19.491 |
| Realtime full resolution, stationary | 18.604 | 18.553 | 19.481 |
| Progressive moving, same optimized traversal, half-resolution axes | 5.554 | 5.663 | 6.724 |

Repeated full-resolution moving measurements ranged from 17.7–18.5 ms. The quality change performs four times as many primary pixel samples as the original half-resolution-axis preview and retains two additional history images. Six RGBA32F images require approximately 60 MiB at this viewport size, excluding scene geometry and the texture atlas, versus approximately 10 MiB for the original four half-resolution images. This is an explicit quality/memory tradeoff, not a memory reduction claim.

The traversal optimization computes reciprocal ray direction once rather than per BVH node, reduces stack capacity from 64 to 32 using the balanced-tree depth bound, and stops shadow rays at their first valid blocker. The moving full-resolution workload fell from 42.0 to roughly 18 ms; the original progressive workload also benefits. No hardware-instruction count is inferred from timing.

The GPU image regression compares a moving diffuse patch against a 1024-sample stationary reference, with denoising disabled in both measurements: display-space red-channel mean squared error decreased from **662.1279 to 16.3320** (97.5%). This metric applies to that patch, not every pixel or material in the studio. Tests also cover newly revealed surfaces, immediate light/sky invalidation, additional GL image-binding restoration, both tracing modes and orthographic lighting. The full build and native studio interaction smoke run pass.

Raw output is archived in [lighting-motion-results.txt](benchmarks/lighting-motion-results.txt). Generated moving-view screenshots are in `build/lighting-studio/`. Rejected deterministic-preview experiments were removed; their timing is not an accepted engine result.

## Accepted pass 3: moving model transform allocation

`ModelInstance3D.updateTransform()` no longer creates a temporary nine-float array on a cache miss. It validates scalar position/scale/rotation components before writing the existing cache keys, preserving invalid-input recovery and mutable-vector detection. It adds no per-instance scratch storage. Cached lookups follow the existing fast path.

Forked JMH on the local Ryzen 9 7945HX and Java 25 used two forks, three one-second warmups and five one-second measurements with the GC profiler. Each operation visits the complete batch, updates positions only in the moving workload, writes a reused matrix and returns a coordinate checksum. Figures below are per batch; ± values are JMH's reported score errors.

| Instances | Workload | Before ns/op | After ns/op | Before B/op | After B/op |
|---|---|---:|---:|---:|---:|
| 1 | Cached | 13.43 ± 2.07 | 13.21 ± 0.43 | approximately 0 | approximately 0 |
| 1 | Moving | 80.98 ± 1.32 | 75.15 ± 3.38 | 56.00 | approximately 0 |
| 256 | Cached | 3387.31 ± 78.35 | 3355.23 ± 105.09 | approximately 0 | approximately 0 |
| 256 | Moving | 20993.02 ± 1038.44 | 19476.97 ± 2076.45 | 14336.15 | 0.14 profiler background |

The accepted result removes **56 bytes per moving instance update**. The single-instance timing improves by about 7.2% with nonoverlapping reported intervals. Large-batch and cached timing intervals overlap, so their timing improvements are inconclusive. No end-to-end FPS or hardware-instruction reduction is claimed.

Regression tests ran against the baseline before editing and against the candidate afterward. They cover 300 parent/local compositions, cached repeated reads, direct position/scale mutations, negative nonuniform scaling, Euler/quaternion rotations, every nonfinite local component, zero scales and recovery after invalid input. Existing 3D tests and full build/physics/UI integration checks also pass.

Reports: [before](benchmarks/model-transform-before.json), [after](benchmarks/model-transform-after.json), [environment and baseline hash](benchmarks/model-transform-environment.txt). Local full logs, baseline source and candidate diff are in `build/reports/model-transform/`. No stage, commit or push was performed.

Next measurement: path-tracing scene-snapshot allocation for unchanged versus moving scenes at small/large instance counts, then hierarchical bounds. Continue the remaining subsystem queue rather than generalizing this transform result to unrelated systems.

## Accepted pass 4: visual reconstruction and ray traversal

The visual audit drove this pass. Separate diffuse/specular histories, albedo demodulation, coherent ray/guide jitter, weighted glass branches and stationary accumulation preserve detail that the previous combined filter lost. See [visual evidence and limitations](lighting-visual-audit.md). A neutral studio rig and checker reference make refraction inspectable. The footer now reports asynchronous GPU time and identifies cached stationary images.

GPU-completed measurements on the same local machine used 30 warmup and 120 measured frames with VSync disabled. The new, closer studio scene is a different workload from earlier passes; its timings must not be compared directly with the old scene as an end-to-end speedup.

| Change / workload | Mean ms | Median ms | p95 ms |
|---|---:|---:|---:|
| Revised studio, separate filter dispatches | 63.061 | 62.729 | 67.748 |
| Revised studio, shared filter dispatches, stationary | 60.237 | 59.719 | 64.259 |
| Revised studio, surface-area BVH, stationary | 13.336 | 13.167 | 14.599 |
| Final revised studio, moving camera | 13.491 | 13.365 | 14.238 |
| Fixed 640×480 visual scene, original reconstruction | 5.949 | — | — |
| Fixed 640×480 visual scene, final reconstruction | 4.183 | 4.029 | 4.978 |

Combining diffuse/specular filtering shares guide reads and geometry weights across three passes. Surface-area BVH construction isolates large room planes from detailed meshes, replacing median splits that produced expensive overlapping traversal. It increases scene-build work and temporary allocation; camera-only movement reuses the tree. Construction caps depth at 28 to retain the 32-entry GPU stack. These timings are individual local runs, not confidence intervals or guarantees for animated/stress scenes.

The quality changes increase realtime image storage to 176 bytes per internal pixel (eleven RGBA32F images). No total-memory reduction is claimed. Fine glossy noise and some moving-view softness remain. JMH is appropriate for CPU work; these shader changes were measured with GPU completion instead.

Validation: full build, 137 standard tests, 62 3D tests, 17 physics tests and 44 UI tests passed. Suite counts may overlap. The native studio smoke run passed orbit, zoom, light placement/drag, undo/redo, 2D/3D switching and GL checks. Added regressions cover checker contrast, glass energy conservation, bright emitter trails, stationary glass blur and extra image bindings. [Benchmark log](benchmarks/lighting-visual-quality/studio-benchmark.txt), [reference timing](benchmarks/lighting-visual-quality/reference-benchmark.txt), [image metrics](benchmarks/lighting-visual-quality/metrics.txt).

## Filament / Physics Studio validation and scheduling note — 2026-09-09

The user-selected Filament integration and interactive Jolt lab are documented in [physics-studio.md](physics-studio.md), with raw measurements in [benchmarks/physics-studio](benchmarks/physics-studio/README.md). The final 288-body run measured 4.617 ms mean completed rendering and 0.856 ms mean physics update. These are workload measurements, not improvements over the previous path tracer or all engine subsystems. The native-light cleanup reduces the stress scene from 303 light components to three; its timing improvement is **not established**. This is retained as an explicit resource-management tradeoff. Tests cover disabling/re-enabling emission, empty geometry, resource ownership, rendering, and native UI controls.

At the 21:55 UTC scheduled audit, the Physics Studio preview (PID 27836) was still running for the user. The separate task **Diagnose and fix computer slowdowns** was actively running a Windows-drive filesystem scan and investigating storage latency. Consequently, this run deferred new timing experiments and runtime modifications: adding CPU/GPU benchmarks would compete with the preview/repair work and cannot provide a clean baseline. No preview was stopped and no JMH lock was bypassed. Existing benchmark variation must not be interpreted as an engine regression or speedup without a controlled rerun.

Next eligible measurement remains CPU scene-snapshot allocation: unchanged versus moving scenes at small and large model counts, using forked JMH and GC profiling. First verify that system repair and competing rendering/benchmarks are inactive; inspect current file activity before editing `PathTracingScene` or Filament synchronization. Record a fresh comparable baseline before selecting a candidate. The remaining subsystem queue above is unchanged.

## JOML migration — 2026-09-09

At the user's request, vectors, matrices, quaternions, rays, bounds, intersection operations and frustum tests now use JOML/JOML primitives directly. Eight duplicate classes were removed. Jolt remains the physics engine. See [migration/validation details](joml-migration.md) and [new benchmark reports](benchmarks/joml-migration/README.md).

The archived custom-math passes above remain historical evidence; their code and timing are no longer the current implementation. This migration does not establish a before/after speedup: benchmark result objects changed to direct JOML objects, sampling duration differs from historical reports, and a separate Windows storage repair was active during this session. New forked JMH/GC measurements are diagnostic. Preserve this distinction in future performance claims.

Shader upload buffers are reused. Window's column-major snapshot refreshes only when projection changes, avoiding an added matrix copy at each sprite culling call. JOML positive-volume AABB validation differs from the engine's planar meshes, so engine culling explicitly preserves inclusive bounds. Explicit quaternion/normal validation guards the native/rendering boundaries.

Next concrete measurement: unchanged/moving scene-snapshot allocation at small/large counts, on an idle machine after system repair. The subsystem queue otherwise remains active.

## Realistic gallery and light editor — 2026-09-09

Physics Studio now includes a 16-light editor, mouse placement/dragging, saved rigs and three realistic textured Poly Haven props. Jolt remains the physics backend and JOML remains the math API. Shared plinth/bulb meshes and model reuse keep the realistic gallery at seven native mesh-cache entries. Filament albedo textures now use trilinear mipmapping; square-texture GPU storage grows by about one third to reduce minification aliasing. Material UV flipping is disabled to match engine conventions, with a framebuffer regression that failed before the correction and passes afterward.

Final completed-render means were 3.440 ms for the realistic gallery with three lights, 5.162 ms with sixteen lights, and 4.785 ms for the 288-body stress scene. Mean physics times were 0.032, 0.033 and 0.833 ms. These are feature workload measurements, not a speedup over different earlier model sets. Full validation, source hashes, memory tradeoffs and raw logs are in [the realistic-model report](benchmarks/realistic-models/README.md).

The CPU scene-snapshot allocation measurement remains next in the broader subsystem queue. Do not benchmark against the live preview or infer universal instruction/memory improvements from these scene timings.

## 3D particles and optional Jolt simulation — 2026-09-09

The engine now supports scene-attached mesh particles and optional dynamic Jolt bodies while retaining cosmetic/billboard emission. Filament reuses compatible native entries through particle churn. Pooled reset reuses sprite/material state, cosmetic survivor compaction avoids repeated array shifts, native destination getters reuse storage, and step-listener snapshots are cached until membership changes.

The identical forked JMH harness measured the original implementation, an initial candidate and a refined candidate. The initial small-emitter steady update regression prompted a targeted hot-loop change. The final 256-particle steady update overlaps the original confidence interval (1.057 vs 1.021 microseconds); 4,096-particle steady time fell 41.4% and churn time fell 52.1%. Every refined cosmetic workload allocates less than 0.73 bytes per operation, compared with roughly 104 bytes per reused particle in the old reset path. These measurements do not establish total retained-memory or hardware-instruction reductions. Raw samples, frozen sources, confidence intervals and reproduction instructions are in [the particle report](benchmarks/particles-3d/README.md).

Native moving-camera runs measured a 192-particle Jolt fountain at 4.055 ms mean completed rendering / 0.343 ms simulation and a 480-particle fountain at 4.939 / 0.548 ms. [Native comparisons](benchmarks/particles-3d/native/README.md) include cosmetic particles and the existing 288-body stress scenario. All required build suites and native interaction/scene-switch checks pass. The particle preview is left open for the user; do not run competing benchmarks while it is active.

The broader queue remains active. Next measure small/large unchanged/moving scene snapshot allocation; particle birth/death and per-frame scene collection still have costs. No claim is made that every engine system has reached a theoretical optimization limit.

## Attached particle lights and first-person arena — 2026-09-09

Particles can now receive Filament surface lighting and optionally carry independent point lights. Light state is lazy for ordinary particles; enabled lights follow cosmetic or Jolt motion, reuse native entities while moving/fading, and leave the scene on expiry, detach or close. The new playable [Live Fire arena](fps-arena.md) combines first-person Jolt movement, drone waves, weapon/grenade combat, realistic props, bounded debris/flaring effects, editable lighting and an FPS HUD. A pinned Jolt JNI layer-filter bridge correction is guarded by physical tests of all 256 user-layer pairs.

The unchanged six-case forked JMH harness measured optional-light support before and after: steady-update means changed by less than 1% with overlapping intervals, and all cases remain below one byte per emitter operation. Churn means increased within overlapping intervals, so neither a regression nor universal zero overhead is established. See [raw CPU evidence and snapshots](benchmarks/particle-lights/README.md).

Nine sequential native arena runs compared Jolt/lit, Jolt/unlit and cosmetic/lit effects using three fresh processes per configuration. Mean completed rendering / simulation including scripted actions was 3.789 / 0.346 ms, 3.735 / 0.344 ms, and 4.104 / 0.183 ms respectively. Each reached 170 particles; native lights peaked at 15/4/15 and all retained nine mesh-cache entries. Rendering variation overlaps and cosmetic motion changes particle positions, so these are feature workload measurements, not an optimization speedup. [Full methodology, limits, logs, screenshots and measured sources](benchmarks/fps-arena/README.md).

Final validation passes 197 standard, 84 3D, 32 physics, 52 UI and 27 lighting tests, plus 1,349 headless gameplay checks, 83 light-rig checks and six packaged-model checks. Suite counts overlap. Native input validation passes movement, jumping, looking/aiming, combat, particle controls, pause and restart. Flare and muzzle spawn checks prevent emitter centers appearing through nearby walls. Particle shadows remain opt-in to keep the effect light budget practical.

The FPS preview is left running for the user; its current PID is recorded in `build/fps-live.pid`. Check for **all** active rendering applications before a scheduled performance pass, not only older Physics Studio PID files, and do not benchmark alongside the preview. The broader subsystem queue remains active. Scene-snapshot allocation is still next; ground/weapon ray-query temporary native objects are another concrete measurement candidate. No theoretical optimization limit or whole-engine instruction/memory reduction has been established.

## Transparent particle lights and downloaded FPS assets — 2026-09-09

The arena now has downloaded weapon/arms, robot, grenade, magazine and industrial environment assets. Every moving fleck/flare carries its own light and uses a shared soft alpha texture; shot and stationary impact lights were removed. The budget is 96 debris particles plus 12 flares. Filament now composites lit alpha materials and independently controls visible emission versus implicit material lighting. Jolt remains enabled for physical effects and gameplay.

After the feature scene was fixed, three identical native runs before and after an OBJ warm-texture change measured 6.935 versus 6.329 ms mean completed rendering, an observed 8.7% reduction. The change avoids repeated graphics-state snapshots and driver queries when imported textures are already uploaded, while restoring caller-modified material bindings. Simulation ranges overlap, and total memory/hardware instructions were not measured. Diagnostic Jolt/unlit and cosmetic/lit configurations were also run once each. These asset/light workloads differ from the historical arena and must not be compared directly with its timings.

All final suites passed: 200 standard, 87 3D, 32 physics, 52 UI and 27 lighting tests, plus 1,425 headless gameplay checks, 83 light-rig checks, six gallery model checks and 16 new asset entries. Suite counts overlap. Native tests verify real alpha compositing, moving explicit lights, opacity masking, warm texture requests without OpenGL access, texture ownership and gameplay controls. Actual final framebuffers were inspected. [Raw measurements, frozen sources, limitations, validation and screenshots](benchmarks/fps-realistic-alpha/README.md).

The visible FPS preview is opened after measurements; consult `build/fps-live.pid` and the current process inventory before any scheduled benchmark. The wider subsystem queue remains active, with scene-snapshot allocation next. No claim is made that every engine subsystem is fully optimized.

Scheduled audit at 2026-09-10 00:44 UTC: the FPS preview (PID 9580) remains open and responsive, and **Document changed Valthorne files** is actively working in this checkout. No benchmark or runtime edit was started: timing would compete with the user's preview, and shared-file activity requires coordination before modifications. The preview was left untouched. Next eligible work remains a fresh forked JMH/GC baseline for small/large unchanged/moving scene snapshots after rendering activity has stopped; the latest accepted result remains the OBJ warm-texture improvement above.

Rechecked at 2026-09-10 01:45 UTC: PID 9580 remains open and responsive. The documentation and UI tasks are idle, but the active renderer still prevents an isolated timing run. No benchmark, runtime change or preview interruption was performed; the next-work queue is unchanged.

## Scene-snapshot baseline preparation — 2026-09-10

The 23:31 UTC audit found the old preview closed and no competing renderer/benchmark. Added a twelve-case forked JMH/GC harness for one/256 unchanged/moving direct, hierarchical and two-part OBJ placements, plus eight CPU collection/build contract tests, which passed. Preliminary allocation measurements identify repeated matrix/instance/material snapshots as a concrete target; no optimization was applied or accepted.

During measurement, IntelliJ and the new **Fix main class compile errors** task began building and changing source-set configuration in this checkout. These builds contaminated the timing baseline, and subsequent cleanup removed temporary stdout/test XML. The raw JMH JSON and measured source snapshots are preserved in [the preliminary report](benchmarks/scene-snapshot/README.md). Renderer collection paths remain unchanged. No further benchmark or runtime modification was attempted alongside the active repair.

Next eligible work: after that task and previews are idle, rerun `benchmarkSceneSnapshot` with a fresh source/class inventory, then measure a renderer-owned reusable matrix/instance collector against that isolated baseline. Preserve OBJ transform sharing, mutable-input signatures, independent constructor snapshots, failure recovery and prompt release of packed build arrays. The broader subsystem queue remains active; the last accepted result is still the OBJ warm-texture optimization.

## Accepted reusable scene collection — 2026-09-10

Completed the user-requested follow-through. Filament and the path tracer now reuse matrix/instance collection storage while preserving source ordering, current mutable-value signatures, shared OBJ placement transforms, texture rebinding and independent constructor snapshots. Shrink/failure/close paths release retired references; packed path-tracer build lists are cleared and trimmed after upload in a finally block. OBJ effective-material copies are unchanged.

Twelve controlled forked JMH/GC cases compare one/256 direct, hierarchical and two-part OBJ placements, unchanged/moving. Transient allocation falls by over 99% for direct scenes, approximately 61–63% for hierarchies and 29–54% for OBJ cases. Stationary direct-scene timing intervals separate, but most timing results remain inconclusive. A longer focused static-OBJ pair measured 39.775 ± 2.449 versus 37.248 ± 3.219 µs/op, overlapping intervals with lower allocation; no OBJ slowdown/speedup is established. Retained scratch capacity is an explicit tradeoff, and no total-memory, hardware-instruction or end-to-end FPS reduction is claimed. [Raw results, frozen sources, methodology and limits](benchmarks/scene-snapshot/RESULTS.md).

The final build, nine collection contract tests, 87 3D tests, 32 Jolt tests, 27 lighting tests and 1,425 gameplay checks pass. The standard test report has two skips for optional local audio fixtures; affected rendering/physics suites have none. Native FPS interaction validation passes and the actual framebuffer was inspected. Build timing windows were coordinated with release/UI work; a lock-blocked focused run was retried only after the UI benchmark ended. No lock bypass or parallel benchmark occurred.

Next measurement: hierarchy traversal allocations at shallow/deep and sparse/dense node counts, with repeated OBJ effective-material expansion as another identified residual cost. Continue the broader subsystem queue after this bounded pass; coordinate with the active Slug/UI benchmark and release task before running more measurements. No preview was launched after the final smoke test, and no benchmark/test process from this pass remains active.

## Accepted collection pools and bounded flare shadows — 2026-09-10

The limitations follow-up now pools effective OBJ materials, removes internal collection wrappers/iterators, trims backing arrays after sustained low occupancy, and composes each hierarchy child from its captured parent once. Source material ownership, public unmodifiable membership, mutable transform semantics, external reparenting, affine shear and Jolt are preserved. Sixteen isolated JMH cases were measured before, after pooling, and after linear traversal. At 256 placements, OBJ allocation falls from about 65 KB to under 1 B per capture; deep hierarchy time falls about 71%, from roughly 420 to 120 microseconds. Direct and moving-OBJ timing intervals still overlap. These are CPU collection results, not total-memory or universal FPS claims.

The local FPS console offers off/two/four flare shadow lights, default two. Ordinary transparent debris retains attached lights without shadow maps. Six GPU-completed combat runs compare quality costs: two flare shadows add roughly 0.5–0.7 ms and four roughly 1.1–1.2 ms on the RTX 5070, with 104 particles/108 lights at peak and Jolt enabled. Native UI interaction and framebuffer inspection passed. Examples remain ignored and excluded from the release artifact per the separate release instruction.

Build, engine/physics/lighting checks, 77 isolated graphics tests, 1,437 gameplay checks, and both published-library consumers pass. Standard tests contain two optional audio-fixture skips. Thirteen collection contracts cover ownership, mutable state, failure recovery, affine transforms and shrink/regrowth. [Full results, raw measurements, validation and remaining limits](benchmarks/scene-collection-pools/README.md).

Next queue: broader churn/retained-heap measurements, renderer update/upload costs, and proper imported normal/roughness materials with UV-aligned tangent frames and image-based checks. Dynamic environment reflections/global illumination remain unimplemented in the Filament path. Continue other subsystem measurements only after confirming no competing preview, build or timed workload. This pass has finished; no preview remains running from its native checks.

## Conservative occlusion and incremental native updates — 2026-09-11

Added actual-triangle CPU occlusion for ModelBatch3D and Filament, bounded to 128 projected occluders/2048 scanned triangles per pass. Decisions use current camera/transforms, conservative projected bounds and near-plane/uncertainty fallbacks. Classic shadow passes and depth-disabled overlays bypass rejection. Filament retains shadow casters and implicit-light contributors, bypasses additional rejection for refractive scenes, and independently updates explicit lights. Textured geometry is not treated as solid occlusion. Per-entry signatures avoid resending static native transforms/materials when another object changes; hidden eligible entries defer uploads until revealed. Jolt and path-tracer secondary-ray geometry remain active.

Native comparisons reject 464/512 objects behind an opaque wall. Filament means are 1.382 ms off versus 0.877 ms on; model batch 1.071 versus 0.335 ms. Visible scenes show overhead (Filament 1.332 versus 1.468 ms; batch 0.799 versus 0.864 ms). The textured FPS scene shows no demonstrated win: 6.528 versus 6.610 ms off/on. These short local results are not statistical or universal performance guarantees. Six two-fork JMH cases measure bounded classifier cost and sub-byte profiler allocations; no measured collections occur.

Build, 148 standard tests (two optional audio skips), 93 3D tests, 32 physics tests, 27 lighting tests, 79 graphics tests, 1,437 local gameplay checks, published consumers and native FPS interaction all pass. Suite counts overlap. Native framebuffer equivalence, camera-only reveals, stale-light removal, shadow preservation, glass fallback, invalidation and disabled-culling behavior are covered. The local FPS console has an on/off switch and remains excluded from release. [Guide](culling.md) and [raw results/limits](benchmarks/occlusion/README.md).

Next culling work: measured opaque-texture certification or conservative coverage aggregation, followed by a native camera-only occlusion path that independently preserves shadow casters. Do not remove casters from Filament's scene/layers as a shortcut. Broader engine and imported material work remains on the existing queue. No preview or timed workload from this pass remains running.

## FPS render profiling and exact mesh indexing — 2026-09-11

Exact immutable vertex indexing reduces uploaded vertex-buffer bytes by 76.1% in the local FPS combat scene. A separately compiled solid-material path avoids alpha discard where it cannot affect coverage; transparent particles and uncertain cutouts keep their existing shaders. Same-binary reversed-order comparisons suggest approximately 7.2% lower render time, but concurrent GPU activity makes that timing gain provisional. The requested 10× improvement is not achieved. Automatic instancing was tested and removed after regression. HIGH remains the demo default. Build, graphics/physics/lighting/gameplay checks, release consumers and interactive FPS smoke pass. [Measurements, reproduction and limitations](benchmarks/fps-rendering/README.md).
