# Sprite-alpha shadows — local CPU measurements

JMH 1.37, JDK 25.0.2, Windows, 2026-09-11. Two forks, two 1-second warmups and
three 1-second measurements per fork, GC profiling. No before/after comparison:
this is a newly added capability. These are CPU microbenchmarks, not scene FPS or GPU
timings; normal desktop background processes were not disabled.

| Operation | Mean ± 99.9% CI, µs/op | Java allocation, B/op |
| --- | ---: | ---: |
| Extract 64×64 tree with noisy foliage/holes | 27.963 ± 4.369 | 47,424 |
| Move retained tree and rebuild one 1,024-bin shadow | 26.616 ± 2.512 | 0.186 |
| Reuse unchanged shadow | 0.004 ± 0.001 | approximately zero |

Sub-byte allocations are profiler noise. Extraction deliberately happens on cache
misses, not each render. Moving complex foliage still costs real CPU time per affected
light; this is not an instruction-free shadow solution. This fixture does not measure
Sprite frame-key lookup, GPU compositing, spatial-index rebuilds, or a whole forest.

Reproduce with `./gradlew benchmarkSpriteShadows`. The fixture lives in
`src/benchmark/java/valthorne/lighting/benchmark/SpriteShadowBenchmark.java` and the
complete observations are retained in [results.json](results.json).

Regression tests cover mask parity at every pixel center of 30 random images, holes,
islands, threshold/buffer position, outline simplification, transparent passage and
light containment, transform/frame/category invalidation, sprite atlas animation,
bounded cache reuse, flips, negative scale, rotation/pivots, tint transparency, and
real Lighting2D output before/after sprite frame changes and movement.

See [setup and limitations](../../systems/lighting-2d.md#sprite-shaped-shadows).

Validation: `test graphicsTest` completed successfully. Standard suite: 159 tests,
157 passed and 2 skipped. Native suite: 84 passed. No failures or errors. This
includes 7 new CPU silhouette regressions and 3 new native sprite-shadow regressions.
