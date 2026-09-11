# Physics Studio measurements — September 9, 2026

Hardware: NVIDIA GeForce RTX 5070, driver 610.88. Windows x64, JDK 25, Filament HIGH preset. Window 1440×900, scene viewport 1108×760. Each standalone process warms 60 frames and measures 240 completed frames, with camera motion and fixed 1/60-second simulation progression. Rendering includes UI and synchronization; physics includes synchronization to scene models. Benchmarks run serially.

- `baseline-*`: original per-mesh native light components; 30 k-lumen key, environment intensity 1000.
- `final-0/1/2`: selective native light components, same initial rig. Means were slower than the initial run; no speedup is established.
- `repeat-*`: selective light components with the final, stronger key light (120 k-lumens) and environment intensity 500. Three independent runs show substantial desktop variability, especially in tail timings. Do not compare these to the original rig as equivalent-quality workloads.
- `release-*`: final code, including empty-geometry handling, build/test verification and native-input smoke test.

The initial scenario render means were 3.22 / 3.01 / 4.46 ms; physics means were 0.11 / 0.09 / 0.82 ms. Across the three repeated final-lighting runs, render means ranged 4.04–4.75 ms (pyramid), 4.03–6.52 ms (dominoes), and 5.33–12.16 ms (288-body stress). Stress physics means ranged 0.95–1.18 ms. These are observed ranges, not guarantees.

Resource result: the stress scene shares three uploaded meshes across 303 visible model instances. It now has three native light components rather than 303. Disabling and re-enabling emission is covered by a framebuffer regression test. No byte-level native-memory measurement was performed, and this reduction alone is not claimed as a measured speedup.

Images are direct rendered-framebuffer captures; Windows desktop capture previously returned a white window despite the user confirming correct presentation.

Final release run: render means 4.228 / 4.024 / 4.617 ms, physics means 0.132 / 0.121 / 0.856 ms for scenarios 0 / 1 / 2. Build, 138 standard tests, 63 3D tests, 17 physics tests and 44 UI tests passed (suites may overlap). Both physics and lighting studio native-input smoke checks passed.

Release documentation: this is a historical measurement record. Use the [current release checks](../../releasing.md) for building and validating the development snapshot; preserve the recorded source hashes and results.
