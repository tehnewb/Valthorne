# Lighting contrast correction

Only the Physics Studio lighting preset and controls changed; no renderer algorithm changed. Native shadow regression: the same floor pixel measured red=0 with an occluding cube and red=66 after removing it, with environment disabled. Build and standard/3D/physics/UI checks passed. Native orbit, zoom, pause/resume/single-step smoke passed.

Benchmarks use the existing serial 60-warmup/240-measurement completed-frame workload. Current means for scenarios 0/1/2: render 15.745 / 15.477 / 6.635 ms; physics 0.203 / 0.197 / 1.100 ms. There is pronounced desktop timing variation (p95 up to 38 ms) and an ongoing separate Windows storage investigation. These runs are recorded as diagnostics, not a controlled before/after performance comparison or a speedup claim. The changed light balance also makes this a different image workload from prior measurements. Recheck on an idle machine before attributing timing differences to this preset.

Images show the final preset. Earlier images remain in ../physics-studio. Camera and simulation frames differ between smoke and benchmark captures, so these are visual inspections, not pixelwise A/B metrics.

Release documentation: this is a historical measurement record. Use the [current release checks](../../releasing.md) for building and validating the development snapshot; preserve the recorded source hashes and results.
