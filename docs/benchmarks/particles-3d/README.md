# 3D particles: CPU benchmark record

These measurements compare the original cosmetic particle emitter with the optional
mesh/Jolt implementation, using the same CPU-only JMH harness. Each operation
processes a complete emitter. They do not measure Jolt collision simulation, GPU
rendering, total retained memory, or hardware instruction counts.
The [native studio report](native/README.md) records Jolt/rendering workloads and
interactive smoke checks separately.

## Final measured implementation

Times are mean microseconds per operation ± JMH's reported 99.9% confidence error.
The refined source snapshot matches the final five engine source files and the
shared harness; their SHA-256 hashes are recorded in `refined-source-hashes.json`.

| Workload | Particles | Original µs/op | Refined µs/op | Time change | Original B/op | Refined B/op |
|---|---:|---:|---:|---:|---:|---:|
| Steady update | 256 | 1.021 ± 0.084 | 1.057 ± 0.033 | +3.5% | 0.007 | 0.007 |
| Steady update | 4096 | 31.420 ± 0.893 | 18.417 ± 0.687 | −41.4% | 0.218 | 0.128 |
| Burst and expire | 256 | 6.031 ± 0.704 | 4.926 ± 0.455 | −18.3% | 26,624.042 | 0.034 |
| Burst and expire | 4096 | 89.319 ± 2.182 | 83.703 ± 4.425 | −6.3% | 425,984.624 | 0.581 |
| Interleaved churn | 256 | 7.113 ± 0.544 | 4.233 ± 0.113 | −40.5% | 17,749.383 | 0.029 |
| Interleaved churn | 4096 | 218.454 ± 5.915 | 104.539 ± 3.518 | −52.1% | 283,990.862 | 0.725 |

The small steady-update intervals overlap, so the initial regression is no longer
established by these measurements. Both burst workloads also have overlapping
intervals; their lower means are not demonstrated speedups. The large steady
workload and both churn workloads show separated timing intervals. All refined cases allocate less than one byte per
operation, consistent with profiler background overhead; this does not claim that
first creation, native physics or mesh rendering allocates nothing.

The measured refinement separates cosmetic updates from native-body handling,
avoids membership writes until the first expiry, and skips mesh synchronization
for billboards without a mesh instance. Motion math and retained survivor order
remain unchanged. The refinement also includes the tested physical-mesh parent
reset, which prevents applying a parent transform twice to world-space Jolt poses.

## Initial measured candidate

Times are mean microseconds per operation ± JMH's reported 99.9% confidence error.
The source and class hashes identify the measured snapshot exactly. A subsequent
physical-mesh parent-transform correction does not execute in these cosmetic cases.

| Workload | Particles | Original µs/op | Initial candidate µs/op | Time change | Original B/op | Candidate B/op |
|---|---:|---:|---:|---:|---:|---:|
| Steady update | 256 | 1.021 ± 0.084 | 1.851 ± 0.227 | +81.2% | 0.007 | 0.013 |
| Steady update | 4096 | 31.420 ± 0.893 | 27.693 ± 0.534 | −11.9% | 0.218 | 0.192 |
| Burst and expire | 256 | 6.031 ± 0.704 | 5.054 ± 0.063 | −16.2% | 26,624.042 | 0.035 |
| Burst and expire | 4096 | 89.319 ± 2.182 | 88.020 ± 7.146 | −1.5% | 425,984.624 | 0.610 |
| Interleaved churn | 256 | 7.113 ± 0.544 | 4.755 ± 0.196 | −33.1% | 17,749.383 | 0.033 |
| Interleaved churn | 4096 | 218.454 ± 5.915 | 113.836 ± 7.972 | −47.9% | 283,990.862 | 0.790 |

The 4096-particle burst timing intervals overlap; this run does not establish a
speed improvement there. The small steady workload regressed and prompted a
separate refinement reported above. All measured candidate workloads allocate less than one byte
per operation, consistent with profiler background overhead rather than per-particle
allocation. The original reset allocated approximately 104 bytes per reused particle.
Stable survivor compaction replaces repeated array-tail shifts during interleaved
expiry, and reset reuses owned sprite/material state instead of creating replacements.

## Workloads and controls

- **Steady update:** full nonexpiring ballistic population, no emission/deaths;
  velocity and constant acceleration are nonzero. The live membership view is cached
  before measurement, so accessor wrapper allocation is excluded from both versions.
- **Burst and expire:** spawn a full population from a warmed pool, then expire all
  particles in one update. Includes initializer, pooled reset and removal work.
- **Interleaved churn:** alternate one-frame and two-frame lifetimes, then refill
  free capacity. Setup stabilizes population before measuring survivor handling.

JMH 1.37, Java 25.0.2, Windows, AMD Ryzen 9 7945HX (16 cores/32 logical processors).
All three runs use two JVM forks, three one-second warmups and five one-second measured
iterations per fork, a fixed 256 MiB heap, and the GC profiler. The harness is unchanged
between original and candidate (SHA-256
`79AD1FEF6E2AE47FEC7C51C8DC2654C7E7D141B5FCD92D34AADCC6DD3DCC4336`).
JMH's normal process lock stayed enabled. Other coordinated test/benchmark tasks
were paused; the process inventory contained two idle Gradle daemons and no live
preview. This was an ordinary desktop, not a dedicated benchmark host.

Before the baseline run, all 13 existing `Geometry3DTest` cases passed against
frozen original engine classes. Before the initial candidate run, all 19 existing
`PhysicsWorld3DTest` cases, 13 geometry cases and 15 new particle tests passed.
The new regressions exercise pooled defaults, retained order, scene membership,
fixed-step timing, forces/gravity, collision/expiry, factory rollback, externally
destroyed bodies, borrowed resource ownership and listener mutation/cleanup.
The same 47 tests passed before the refined benchmark, including the added physical
parent-transform assertions. Two additional pooled-material/visibility assertions
then passed in the final targeted rerun. The isolated benchmark task was also
loaded and checked successfully.

## Reproduction and records

Run the current checked-out implementation only:

```powershell
.\gradlew.bat -I docs/benchmarks/particles-3d/benchmark.gradle benchmarkParticles3D
```

To choose a separate output, add
`-PparticleBenchmarkReport=build/reports/particles-3d/repeat.json`.
Run tests first and avoid concurrent rendering/performance tasks.

- `baseline.json`, `baseline-jmh.txt`: original raw samples and complete JMH output.
- `candidate-initial.json`, `candidate-initial-jmh.txt`: first candidate samples/output.
- `refined.json`, `refined-jmh.txt`: final measured implementation samples/output.
- `comparison-initial.json`: machine-readable means, errors, allocation and deltas.
- `comparison-refined.json`: final means, errors, allocation and baseline deltas.
- `baseline-sources/`: original particle implementation used for the baseline.
- `measured-sources/`: first candidate implementation used for the initial comparison.
- `refined-sources/`: final measured engine and benchmark sources.
- `ParticleEmitter3DBenchmark.java`: exact shared harness.
- `*-source-hashes.json`, `*-class-hashes.json`: SHA-256 snapshot identities.
- `baseline-tests.txt`, `candidate-initial-tests.txt`: pre-benchmark regression checks.
- `refined-tests.txt`, `final-test-and-command-check.txt`: final checks.

Local build artifacts additionally retain frozen engine/harness class directories,
generated harness sources, classpaths and JVM argument files under
`build/particle3d-baseline/`, `build/particle3d-candidate/` and
`build/particle3d-refined/`. Reproduce the frozen
original locally with `java '@build/particle3d-baseline/jmh.args'`. These build
directories are machine-specific artifacts, not required library resources.

Release documentation: this is a historical measurement record. Use the [current release checks](../../releasing.md) for building and validating the development snapshot; preserve the recorded source hashes and results.
