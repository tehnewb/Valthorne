# UI performance: measured costs and regression checks

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run demo launch tasks there; the engine's local
`src/examples/` files remain ignored and excluded from library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions. Engine `verify*` and benchmark tasks in
those records still require their optional local test/benchmark sources; use the
companion project's `build` and documented smoke tasks for current examples.

This suite measures defined workloads, not a universal UI performance score.
Minimizing allocations must not break painter order, Unicode boundaries, focus,
capture or lifecycle. Correctness tests remain mandatory alongside benchmarks.

## Reproduce

```powershell
.\gradlew.bat verifyUI
.\gradlew.bat runUIShowcase --args=--smoke
.\gradlew.bat verifyUIBenchmark
```

The last command runs all 16 parameterized benchmark cases (roughly five minutes
on this host), writes `build/reports/ui-benchmark/results.json`, and checks allocation
budgets. It is intentionally separate from `build`/`check`: timing/GL benchmarks
should not silently burden every development build or require a display on CPU-only CI.

Run a subset without the complete-suite gate:

```powershell
.\gradlew.bat benchmarkUI '--args=UIHotPathBenchmark -foe true -prof gc -rf json -rff build/reports/ui-benchmark/hot.json'
.\gradlew.bat benchmarkUI '--args=UIFrameBenchmark -foe true -prof gc -rf json -rff build/reports/ui-benchmark/frames.json'
.\gradlew.bat benchmarkUI '--args=UIDataBenchmark -foe true -prof gc -rf json -rff build/reports/ui-benchmark/data.json'
```

JMH is isolated to the benchmark source set and is not a shipped library runtime
dependency. The harness uses [OpenJDK JMH](https://github.com/openjdk/jmh), with
returned values consumed by the harness, three one-second warmups, five one-second
measurements, two forked JVMs, one benchmark thread, a fixed 256 MiB heap, and the
GC allocation profiler. Output includes confidence intervals and per-iteration data.

## Workloads

| Suite | Workload | What it does not establish |
| --- | --- | --- |
| Hot paths | 2,816-character clean ASCII sanitization/reset, cursor traversal with one listener, read-only child view | Arbitrary Unicode edit cost, clipboard/native input latency, user listener cost |
| Frame | 100/1,000 leaf nodes under five containers; idle update, one width change, mixed backend dispatch/draw | Application-wide FPS, GPU execution time, textured/font-heavy widget throughput |
| Data | 1,000/1,000,000 height-index rows and selection bits, deterministic strided access | Creating a million widgets, row pooling, sorting or real application data sources |

The frame scene groups backends in runs of 20. Nano panels paint rounded fills and
borders; texture panels have no background skin and exercise traversal/backend
transitions, not textured fill throughput. The window is invisible, with no
presentation or per-operation `glFinish`. Draw measurements include CPU submission
and driver backpressure, **not isolated GPU time**. Native regression tests separately
verify real textured/Nano pixel ordering, clipping, scrolling and font controls.

## Environment and provenance

Recorded September 8, 2026 on Windows 11 Pro 10.0.26200, AMD Ryzen 9 7945HX
(16 cores/32 logical processors), Oracle HotSpot JDK 25.0.2+10-LTS-69,
JMH 1.37 and LWJGL 3.4.1. Enumerated adapters: NVIDIA GeForce RTX 5070
(driver 32.0.16.1088), AMD Radeon 610M (32.0.21045.5002). The benchmark did not
record `GL_RENDERER`, so adapter enumeration is not proof of which GPU was used.

Runs used the same working repository, based on commit
`a6ca439e4dd1e257d4a0c3e1053ad37e89fdf034`, with uncommitted UI and unrelated work.
They are **working-tree measurements, not clean-commit release comparisons**.
The exact initial implementation snapshot was not archived; the final code and
benchmark definitions are supplied, so final runs are reproducible, while historical
before runs are evidence rather than a fully reproducible checkout.
Other desktop/user work and power/thermal scheduling were not controlled. Draw
timings vary significantly; avoid drawing conclusions from overlapping intervals.

Raw JMH data is retained in [the run archive](benchmarks/ui-2026-09-08/):

- `before.json`: initial text/child-view baseline.
- `after.json`: first hot-path optimization pass.
- `frame-before.json`: before coordinate/traversal/layout-input optimizations,
  but after the hot-path pass.
- `frame-after.json`: coordinate/traversal changes before incremental layout submission.
- `data.json`: initial primitive-index/selection measurements.
- `final.json`: complete final suite used by the allocation gate.

These distinct stages are intentional; do not mix them into a claim that every
change was tested against one clean baseline. JMH JSON includes JVM flags and
statistical detail. Values near zero bytes/op include profiler/harness noise.

## Recorded results

Means below compare the relevant baseline file with `final.json`. Timing errors
are JMH's reported 99.9% interval half-widths. Allocations are bytes per operation.
`~0` means less than one measured byte/op, not a proof of no allocation anywhere.

| Workload | Before time | Final time | Before allocation | Final allocation |
| --- | ---: | ---: | ---: | ---: |
| Cursor move, ASCII field | 188.74 ± 1.70 ns | 3.42 ± 0.38 ns | 24.01 B | ~0 B |
| Reset 2,816-character text | 37.32 ± 1.86 µs | 5.31 ± 0.52 µs | 118,536 B | ~0 B |
| Sanitize clean text | 6.75 ± 0.20 µs | 1.12 ± 0.10 µs | 12,352 B | ~0 B |
| Read child view | 4.79 ± 0.11 ns | 0.36 ± 0.02 ns | 80 B | ~0 B |
| Changed layout, 1,000 nodes | 851.09 ± 53.35 µs | 353.95 ± 21.16 µs | 55.96 B | 27.26 B |
| Idle update, 1,000 nodes | 1.74 ± 0.06 µs | 1.82 ± 0.05 µs | ~0 B | ~0 B |
| Mixed draw, 1,000 nodes | 2.13 ± 0.13 ms | 2.27 ± 0.42 ms | 42,564 B | 173.26 B |

Incremental input submission reduced the measured changed-layout cost by about
58%. Draw allocation fell by about 99.6%, but draw time did **not** demonstrate
an improvement. Idle update also did not improve. These negative/neutral results
are retained rather than hidden. The tiny child-view timing is sensitive to JIT
inlining/hoisting; eliminating wrapper allocation is the meaningful result there.

At one million data rows, final pixel lookup measured 169.54 ± 25.45 ns,
height update plus prefix read 55.15 ± 2.84 ns, and selection toggle 3.22 ± 0.19 ns.
All three stayed below one measured Java byte/op after setup. Selection backing
storage was preallocated using select-all; this does not measure capacity growth.

The complete allocation gate passed all 16 cases. Verification also passed
44 UI tests (27 native integration, 17 model/behavior tests) and the six-page
OpenGL showcase smoke run. Tests include randomized reference comparisons for
height indexing and selection, Unicode boundary equivalence, callback mutation,
layout invalidation, retained tab lifecycle and native split/selection interaction.

## Table projection measurements (2026-09-09)

`./gradlew benchmarkUITable` measures the new table model separately from the
original 16-case allocation gate. Raw JMH data, JVM settings and confidence
intervals are archived in [table.json](benchmarks/ui-2026-09-09/table.json).
Each case uses two forks, three one-second warmups and five one-second measurements
per fork, with the GC profiler and a 256 MiB heap.

| Operation | Rows | Mean time | Java allocation/op |
| --- | ---: | ---: | ---: |
| Read projected row | 1,000 / 100,000 | approximately 2 ns | below 1 byte |
| Filter projection | 1,000 | 0.945 microseconds | 6,032 bytes |
| Filter projection | 100,000 | 299.849 microseconds | 600,034 bytes |
| Sort projection | 1,000 | 30.027 microseconds | 8,032 bytes |
| Sort projection | 100,000 | 6.429 milliseconds | 800,077 bytes |

The 100,000-row sort has a wide 99.9% confidence interval (mean +/- 1.453 ms).
These fixtures use deterministic integer data, cached callbacks and alternating
sort/filter requests. They establish an initial baseline, not a measured speedup.
Mutations allocate projection/work arrays; reads do not create per-row objects.
This does not measure widget construction, custom callbacks, rendering or FPS.
The table virtualizes rows, not columns. The seven-page showcase smoke run and
52 UI regression tests passed, including mixed-backend table cell clipping,
keyboard sorting, bounded live row count and disclosure focus/lifecycle behavior.

## Allocation gate

`verifyUIBenchmark` rejects missing/duplicate cases, missing profiler data and
exceeded per-operation Java allocation budgets:

| Case | Budget |
| --- | ---: |
| All four hot paths | 1 byte/op |
| Data queries, point updates, selection toggles | 1 byte/op |
| Idle frame update | 1 byte/op |
| Changed layout | 256 bytes/op |
| Mixed frame drawing | 1,024 bytes/op |

No wall-clock threshold is enforced across different machines. For release
comparisons, use a dedicated host, stable power settings, identical JVM flags,
representative application scenes and repeated runs with wider workload coverage.
The budgets cover these fixtures only; arbitrary callbacks/themes can allocate.

## Memory and CPU boundaries

- `gc.alloc.rate.norm` measures **Java allocation**, not retained heap, native Yoga/
  NanoVG allocations, driver memory or VRAM. No native leak-free claim follows from it.
- Cursor/clean-text paths eliminate temporary collections/strings; actual insertion
  and undo still allocate. Undo retains at most 100 snapshots, not a byte budget.
- Variable-height storage is approximately 12 bytes per data row plus array headers;
  selection is approximately one bit per highest selected index. These are storage
  formulas, not whole-process memory measurements. Fixed-height grids avoid the index.
- Stable visible virtual ranges return without recreating nodes. Scrolling still
  destroys/creates rows; there is no recycling pool. Row factories own their costs.
- Geometry caches reuse existing bounds. Automatic layout avoids resubmitting
  unchanged inputs, but still traverses the tree and updates computed geometry.
- No retired-instruction, branch-miss or cache-miss hardware counters were measured.
  Nanosecond timings must not be described as exact CPU instruction reductions.
- No full shaping/IME/accessibility, docking, advanced table/tree or GPU layer cache
  completeness claim is made. See [remaining capabilities](ui-advanced.md#remaining-capability-roadmap).
