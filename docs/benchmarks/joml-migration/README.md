# JOML migration measurements

Java 25, JOML 1.10.8, two forked JVMs; two 500 ms warmups and three 500 ms measurements per fork, GC profiler. Benchmarks ran serially with the ordinary JMH lock. A Windows storage repair was active; treat timings as diagnostic, not controlled before/after evidence.

| Workload | Instances | Moving | ns/op | B/op |
|---|---:|---|---:|---:|
| worldTransforms | 1 | false | 8.131 | 0.0001 |
| worldTransforms | 1 | true | 46.161 | 0.0006 |
| worldTransforms | 256 | false | 2519.124 | 0.0349 |
| worldTransforms | 256 | true | 11426.378 | 0.1593 |
| cameraRebuild | — | — | 72.792 | 0.001 |
| composedTransform | — | — | 29.23 | 0.0004 |
| matrixProduct | — | — | 5.904 | 0.0001 |

Raw JSON includes confidence intervals and per-fork data. Allocation values close to zero include profiler background; no instruction-count or overall FPS claim is made. Historical custom-math benchmark outputs had different returned objects and longer sampling, so direct speedup ratios are not reported.

Build and 146 standard / 68 3D / 19 Jolt / 44 UI / 27 lighting tests passed (overlapping suites). Native camera, mouse, UI, and lighting/physics scene checks passed. Eight duplicate classes are absent from the packaged library. Jolt JNI and its native runtime dependencies remain present.

Release documentation: this is a historical measurement record. Use the [current release checks](../../releasing.md) for building and validating the development snapshot; preserve the recorded source hashes and results.
