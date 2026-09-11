# Slug optimization measurements — 2026-09-10

Measured locally on Windows 11, Ryzen 9 7945HX, NVIDIA RTX 5070 (610.88),
JDK 25.0.2, LWJGL 3.4.1, JMH 1.37. Timed runs were serialized with the other
active performance task. Results describe this machine/workload, not universal FPS.

## Results

Means with JMH 99.9% confidence intervals; lower is better.

| Operation | Before, µs/op | After, µs/op | Before → after allocation, B/op |
| --- | ---: | ---: | ---: |
| Changed retained layout | 4.158 ± 0.170 | 4.297 ± 0.190 | 13,008 → 0.040 |
| Unchanged retained layout | 4.084 ± 0.121 | ~0.002 | 13,008 → ~0 |
| Entire run offscreen, including begin/end | 69.806 ± 2.131 | 1.294 ± 0.077 | 207.297 → 0.012 |
| Visible/partially clipped run, including begin/end | 69.991 ± 1.358 | 33.181 ± 8.363 | 207.929 → 0.305 |

The unchanged case now measures an early-out, not faster execution of a layout.
Changed layout retains essentially the same CPU cost (slightly higher mean, overlapping
intervals), but eliminates its repeated arrays. It now also computes ink bounds.
Offscreen culling avoids glyph iteration/upload entirely. Visible submission averages
about 2.1× faster in this fixture, which includes lines below the viewport; this is not
an all-glyphs-visible comparison. The visible timings drifted during warmup/measurement;
the full distribution is retained, not just the fastest iteration. Sub-byte allocation
figures are profiler/background noise, not a guarantee that the entire application
allocates nothing. Native driver allocation is not measured by the Java GC profiler.

## Reproduce

From the repository root, with an available desktop GL context:

```powershell
.\gradlew.bat benchmarkSlug '-PslugReport=build/reports/slug/recheck.json' --no-daemon
.\gradlew.bat test graphicsTest --no-daemon
```

Fixture: `src/benchmark/java/valthorne/font/benchmark/SlugBenchmark.java`.
One thread, two forks, 256 MiB fixed Java heap, three 1-second warmup iterations,
five 1-second measurement iterations per fork, GC profiler. Each fork creates a
hidden 800×600 window, loads the bundled Atkinson Hyperlegible font (ASCII 32–126),
and creates a 2,048-glyph batch. The run contains 40 lines / 1,080 UTF-16 characters
at 20 world units per em. Changed layout alternates digit ordering. Drawing uses
an orthographic pixel projection and baseline (20,570), or (2000,570) offscreen.
Font loading/resource creation is outside timing. Draw timing includes CPU submission
and driver backpressure, without swap/presentation or isolated GPU timer queries.

[Before data](before.json) was captured from the pre-optimization working tree before
main-source edits; [after data](after.json) uses the optimized implementation. The repo
was already dirty, so these reports are not a comparison of two clean release commits.

## Changes and coverage

- Reuse retained-layout arrays, early-out unchanged text, cache ink bounds.
- Cull runs/glyphs and clip instance quads before upload; retain 64-byte glyph records.
- Reuse direct storage for native state queries; no per-scope scalar-query temporaries.
- Replace boxed font-upload texel staging with primitive arrays and bulk copies.
- Remove redundant shader recompilation during batch construction.
- Restore foreign rendering state and preserve texture streaming/PBO upload state.
- Correct coverage for orthographic zoom; derivative fallback for other transforms.
- Add `SlugLabel` for mixed texture/NanoVG UI with shared resources.

Eight native Slug regressions cover metrics/storage reuse; retained-vs-immediate pixel
identity; small-capacity flushing; whole-run culling and partial clipping; zoom and
rotation; external GL state/cancellation; PBO uploads and disposed resources; NanoVG
parenting; and texture/Slug painter order with translation, clipping, and framebuffer
rendering. The native test task isolates fixtures in separate processes because
Window/GLFW lifetimes and callbacks are process-global.

Final validation passed: 140 standard tests and 77 native graphics tests, including
the eight Slug regressions and 30 mixed-UI tests. [Validation and source hashes](validation.json).

## Boundaries

This remains an overlay curve renderer, not a 3D depth-tested text/material system.
Runs retain their largest array capacity; labels borrow font/batch resources and incur
a renderer boundary per label. Group bulk text into one batch scope when possible.
Arbitrary rotated/perspective transforms use derivative coverage but require adequate
world-space quad padding; only axis-aligned projections get automatic viewport CPU
culling. Shaping, bidirectional text and surrogate-pair composition are unchanged and
not supplied by this optimization. Font load time, isolated GPU time, and every possible
custom shader/render-state combination have not been benchmarked or certified.
