# Scene collection and flare shadow improvements — 2026-09-10

This follow-up addresses residual allocations, excess retained collection capacity, repeated hierarchy transforms, and the lack of particle-light shadows in the local FPS example. Jolt remains enabled. The example and its resources remain excluded from release artifacts.

## Accepted engine changes

- `Material3D.set(source)` copies every setting into existing storage, preserving independent colors, borrowed textures and explicit depth-write overrides. `copy()` uses the same implementation.
- Each renderer's scene collector reuses its own combined OBJ materials. Source materials are never used as writable scratch, including when placements change between direct models and OBJ models.
- Indexed internal traversal avoids allocating public list wrappers and iterators. Public membership views remain unmodifiable.
- Retired object references are removed immediately. Backing arrays shrink after 32 consecutive captures below one quarter of their observed peak, or immediately when empty. A later growth burst can allocate again; this deliberately balances retained storage against resizing churn.
- Hierarchy collection composes each descendant from its captured parent transform once. Registered roots still use full world-transform queries, including any later external reparenting. Local quaternion/Euler rotations, negative/nonuniform scales and affine shear are preserved.

## CPU measurements

Windows, Ryzen 9 7945HX, Java 25.0.2, JMH 1.37. Each case uses two fresh JVM forks, 256 MiB heap, two 500 ms warmups and three 500 ms measurements per fork, with GC profiling. Sixteen cases cover one/256 static/moving placements: direct, shallow hierarchy, eight intermediate grouping nodes per hierarchy leaf, and two-part untextured OBJ models. Each operation consumes the signature and captured transforms. The benchmark excludes BVH construction, texture upload and GPU rendering.

The first candidate adds material reuse, indexed traversal and capacity trimming. The second adds linear hierarchy composition. Every runtime candidate was measured separately, using frozen compiled classpaths outside Gradle's build directory. UI/release work was coordinated before measurement; no parallel benchmark or lock bypass was used.

See [all timings with JMH 99.9% error estimates](comparison.md), [baseline JSON](before.json), [pooled candidate JSON](pooled.json), and [final candidate JSON](linear.json).

For 256 placements, temporary allocation falls from approximately **65,649 B to below 1 B** for OBJ scenes, **20,560 B to below 1 B** for shallow hierarchies, and **184,540 B to below 2 B** for deep hierarchies. Tiny residual GC-profiler values are measurement-level overhead, not proof of literally zero allocation in every production workload.

Deep hierarchy means fall from **421.8 to 123.7 µs** static and **416.3 to 120.3 µs** moving, approximately 71% lower with separated timing intervals. Shallow hierarchy intervals also separate. Static OBJ improves from **35.6 to 29.5 µs** with separated intervals; moving OBJ and direct-scene intervals overlap, so no timing improvement is established for those cases.

## Local FPS lighting

The test console now offers **Flare shadows: off / 2 / 4**, defaulting to two. The brightest active flares receive the available shadow slots; ordinary debris retains its transparent glow and attached light without a shadow map. Disabling lights or setting power to zero releases shadow slots. Selection scans at most 12 flares, without sorting or allocating temporary collections.

The native combat workload runs 90 warmup frames and measures 360 frames at 1600×960. Rendering waits for GPU completion. Jolt and particle lights stay enabled. All six runs validate the same peak counts: **104 particles, 108 lights, 158 bodies, 21 cached meshes**. Two independent runs per setting were performed in forward and reverse order on an RTX 5070.

| Flare shadow budget | Render mean, runs 1 / 2 | Render p95, runs 1 / 2 | Simulation mean, runs 1 / 2 |
| ---: | ---: | ---: | ---: |
| 0 | 6.098 / 6.083 ms | 7.129 / 6.858 ms | 0.302 / 0.289 ms |
| 2 | 6.834 / 6.588 ms | 8.198 / 7.653 ms | 0.314 / 0.286 ms |
| 4 | 7.191 / 7.241 ms | 8.072 / 8.148 ms | 0.307 / 0.314 ms |

Two shadows cost roughly 0.5–0.7 ms in this workload; four cost roughly 1.1–1.2 ms. These are quality costs, not performance wins or portable FPS guarantees. The small run count does not establish confidence intervals. The first set predates the final hierarchy optimization; the reverse-order set uses the final code. This FPS scene uses loose placements, so the hierarchy change is not exercised here.

Framebuffers were inspected directly: [no flare shadows](shadows-0.png), [two shadows](shadows-2.png), [four shadows](shadows-4.png), and [edited console](console.png). The native interaction smoke test exercises the new shadow selector and verifies active bounded shadow slots, alongside movement, aiming, firing, reload, grenades, Jolt toggling, light/color controls and restart.

## Validation and remaining limits

`verifyRelease` also passes, including both published-library consumer checks. The source and frozen benchmark class hashes are archived beside the raw results. The optional local FPS files remain ignored as requested by the release task.

`build`, `verify3D`, `verifyPhysics3D`, `verifyLighting`, `verifyFpsArena`, and the isolated `graphicsTest` suite pass. Reports contain 144 standard tests (two optional audio-fixture skips), 87 3D tests, 32 physics tests, 27 lighting tests, 77 graphics tests and 1,437 gameplay checks. These suite counts overlap and must not be summed as unique tests. Thirteen snapshot contracts cover copied material ownership, reuse across model changes, capacity shrink/regrowth, independent snapshots, mutable inputs, hierarchy ordering, external parents, affine transforms and failure recovery.

This does not add normal/roughness texture maps to OBJ materials, dynamic environment reflections, or dynamic global illumination. Those require coordinated importer, material, tangent-frame and shader work with image-based validation. It does not measure retained total heap, hardware instruction counts, arbitrary scene churn, all engine subsystems, or end-to-end CPU optimization gains. Scratch matrices/materials remain proportional to the active scene, and recursion depth remains bounded by the Java stack. Further work should measure these specific limits rather than claim the engine cannot be optimized further.
