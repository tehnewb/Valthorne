# Scene snapshot collection

The subsequent [material pooling, linear hierarchy traversal and bounded flare-shadow report](../scene-collection-pools/README.md) supersedes the residual-allocation and retained-capacity limitations here. These original measurements remain historical evidence.

The reusable collector is now implemented and validated. See [the accepted results, limitations and reproduction commands](RESULTS.md). The following preparation notes are historical; an independent copy is preserved in [PRELIMINARY.md](PRELIMINARY.md).

## Historical preliminary baseline

This pass prepared collection benchmarks and regression coverage on 2026-09-10. **No runtime optimization was applied or accepted. A new isolated baseline is required before a candidate comparison.**

The earlier FPS preview had closed before this pass. Initial process and task inventories showed no renderer or competing benchmark. During the JMH run, IntelliJ started another Gradle daemon and the separate **Fix main class compile errors** task began building and changing source-set configuration in this checkout. Its build overlapped the measurement period; subsequent build cleanup also removed the temporary stdout and test XML files. Work on the optimization was deferred, and the existing renderer construction paths were restored.

`before.json` preserves all twelve preliminary JMH results, including per-fork data and GC metrics. Treat the timings as contaminated diagnostic data, not an accepted comparison baseline. The measured sources and their hashes are under `before-sources/` and `before-source-hashes.json`. The archived renderer sources include a behavior-preserving collector facade used during measurement; current renderers retain their original direct snapshot construction. The package-private collector factory still creates a fresh snapshot and exists only as a benchmark/test entry point until a measured implementation is ready.

## Workloads

Java 25.0.2, AMD Ryzen 9 7945HX, Windows. JMH 1.37 uses two forks, three one-second warmups, five one-second measurements, a 256 MiB heap and the GC profiler. Every operation consumes the signature and every collected transform; no OpenGL context, GPU upload or BVH construction is included in the timed operation. Inputs are one or 256 shared-model placements, unchanged or moving, in three forms:

- Direct model instances.
- Children under a transformed parent; the moving case changes the parent.
- Untextured OBJ instances with two material groups each, exercising effective-material expansion.

Preliminary normalized allocations per complete collection were approximately 320 / 31,416 bytes for direct placements, 640 / 52,104 bytes for hierarchies, and 552 / 93,024 bytes for two-part OBJ placements at one / 256 instances. These are transient Java allocation measurements, not total memory, GPU memory or hardware instruction counts. They identify a candidate for the next isolated run but establish no improvement.

```text
./gradlew benchmarkSceneSnapshot -PsceneSnapshotReport=docs/benchmarks/scene-snapshot/new-before.json
./gradlew test --tests valthorne.graphics.model.PathTracingSceneSnapshotTest
```

Do not run alongside the active IntelliJ repair, another benchmark or a scene preview. The preliminary run launched Java directly using the Gradle-generated benchmark runtime classpath to keep its own build work outside the timed interval; it did not disable JMH's lock.

## Regression preparation and next change

The new eight-test CPU contract suite passed before any optimization was attempted. It checks captured signatures/transforms/materials and packed triangle/BVH/emitter equivalence against independent constructor snapshots. Cases include public mutable inputs, hierarchy visibility/reparenting, growth/shrink/empty scenes, OBJ expansion, direct/OBJ slot replacement, Filament-style empty-geometry compaction, collection/build failure recovery and independent constructor snapshots. The selected Gradle test command exited successfully; its temporary XML and stdout were removed by the concurrent cleanup, so no original test-log archive is available for this preliminary pass.

The candidate remains a renderer-owned reusable collector: retain one matrix per source placement, including shared transforms across OBJ parts, reuse compatible instance records, release retired references and clear all collection state on close or failure. Preserve current hashing and OBJ material-copy/texture-rebinding behavior. Keep independent constructor snapshots supported. Path-tracer packed build data must be released after synchronous upload rather than retained between rebuilds. This is a design for the next pass, not current optimized behavior.

First rerun the unchanged harness against a fresh source/class inventory after shared build activity is idle. Then implement one coherent collector change, run the same JMH cases immediately, and validate both renderers. Account for retained scratch capacity separately from reduced allocation and reject timing regressions or report uncertainty honestly.
