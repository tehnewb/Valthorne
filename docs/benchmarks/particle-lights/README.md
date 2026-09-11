# Attached particle lights: CPU compatibility measurements

This comparison checks the cost of adding optional attached lights to existing
particles that do not enable a light. It uses the unchanged six-case
`ParticleEmitter3DBenchmark` harness and frozen source/class snapshots from before
and after the feature. Enabled-light rendering and Jolt simulation are separate
workloads; these CPU numbers do not describe their cost.

Times are mean microseconds per complete emitter operation ± JMH's reported
99.9% confidence error. Allocation is bytes per operation.

| Workload | Count | Before µs/op | With support µs/op | Before B/op | With support B/op |
|---|---:|---:|---:|---:|---:|
| Steady update | 256 | 1.015 ± 0.016 | 1.023 ± 0.042 | 0.007 | 0.007 |
| Steady update | 4096 | 17.674 ± 0.427 | 17.526 ± 0.117 | 0.123 | 0.122 |
| Burst and expire | 256 | 4.763 ± 0.152 | 4.482 ± 0.052 | 0.033 | 0.031 |
| Burst and expire | 4096 | 81.605 ± 1.691 | 83.207 ± 1.885 | 0.566 | 0.577 |
| Interleaved churn | 256 | 3.959 ± 0.143 | 4.296 ± 0.282 | 0.027 | 0.030 |
| Interleaved churn | 4096 | 99.583 ± 1.778 | 109.146 ± 8.612 | 0.690 | 0.756 |

Both steady-update means differ by less than 1%, with overlapping intervals.
Churn means rose 8.5% and 9.6%, but their intervals also overlap. This run does not
establish a slowdown in those workloads. All cases remain below one byte per
operation, consistent with profiler background overhead rather than per-particle
allocation. It does not measure retained object size or prove zero cost for every
application. The small burst workload had a lower, separated timing interval;
there was no targeted optimization of that workload in this change.

JMH 1.37, Java 25.0.2, Windows, Ryzen 9 7945HX. Each configuration used two forks,
three one-second warmups and five one-second measured iterations per fork,
a fixed 256 MiB heap, and the GC profiler. Heavy test/rendering tasks were paused
during both runs, and JMH's ordinary lock remained enabled. The previous studio
PID from `build/particle-live.pid` was already absent; no unrelated process was
stopped. The process inventories contained two idle Gradle daemons. Source editing
continued on this ordinary desktop, so this is not a dedicated-host measurement.

Before candidate timing, four CPU attached-light lifecycle tests, one native
Jolt-follow test and all six Filament tests passed. The tests cover lazy/inert light
access, pooled identity/default restoration, scene membership transfer/repair,
expiry/close cleanup, physics following, explicit light rendering, native light
entity reuse and disabled shadows. The implementation does not allocate a light or its
attachment state for particles that never request one.

Run the current implementation's same CPU workload:

```powershell
.\gradlew.bat -I docs/benchmarks/particles-3d/benchmark.gradle benchmarkParticles3D -PparticleBenchmarkReport=build/reports/particle-lights/repeat.json
```

`baseline.json`, `candidate.json`, and `comparison.json` contain raw samples and
machine-readable comparisons. `baseline-jmh.txt` and `candidate-jmh.txt` preserve
the full runs; `light-functional-tests.txt` records pre-benchmark checks.
The `baseline-sources/` and `candidate-sources/` directories contain source snapshots
and SHA-256 source/class hashes. The shared harness has SHA-256
`79AD1FEF6E2AE47FEC7C51C8DC2654C7E7D141B5FCD92D34AADCC6DD3DCC4336`.
Local frozen classpaths and JVM argument files remain in
`build/particle-lights-baseline/` and `build/particle-lights-candidate/`.

## Gameplay and final native boundary checks

After the CPU timing, headless FPS validation exposed a native layer-filter bridge
argument-order defect: non-default-layer capsules could fall through the floor.
The world constructor now compensates for the pinned Jolt JNI 6.0.0 bridge.
This constructor is never called by the cosmetic CPU benchmark; the final particle,
emitter and benchmark source hashes still match the measured candidate. Final
validation source identities are recorded separately in
`final-validation-source-hashes.json`.

`verifyFpsArena` passes 1,273 checks, including walking/sprinting, normalized diagonal
movement, jumping/landing, wall blocking, hitscan damage and cover, cooldown, empty
magazine/reloading, enemy damage/death, score/waves, grenade fuse cleanup and restart
ownership. Over 0.75 seconds, walking travels 2.990 m, sprinting 4.533 m, and diagonal
walking 3.091 m including acceleration. Jump height is 1.609 m. These are deterministic
gameplay observations, not performance measurements.

Four engine regressions additionally pass: rotation lock under torque/impulse,
player-excluding rays against an adjacent wall, grounded capsules on user layers
0/1/7/15, and a 512-body scene covering all 16×16 static/dynamic user-layer collision
pairs. Enabled pairs settle on their floor; disabled pairs fall through it. The
settling tolerance includes Jolt's documented 0.02 m penetration slop and float
roundoff. This covers all 32 native motion/layer encodings without inspecting private
filter state. See `gameplay-validation.txt` and `fps-physics-tests.xml`.

```powershell
.\gradlew.bat verifyPhysics3D --tests valthorne.physics3d.FpsPhysics3DTest verifyFpsArena
```

Release documentation: this is a historical measurement record. Use the [current release checks](../../releasing.md) for building and validating the development snapshot; preserve the recorded source hashes and results.
