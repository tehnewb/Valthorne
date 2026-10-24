# Ordered ground-shadow batching benchmark

Run from the repository root with JDK 25 and a desktop OpenGL 3.3 driver:

```powershell
.\gradlew.bat -p benchmarks/ground-shadows run
```

On macOS/Linux use `./gradlew`. macOS runs automatically include `-XstartOnFirstThread`. A real hidden graphics context is required; this is not a headless/mock rendering test. The composite build uses the current checkout and does not require publishing Valthorne locally. Nothing is uploaded. The benchmark is a separate project and does not enter the production JAR or normal library source set.

The default workload generates an original four-frame alpha atlas with antialiased edges, transparent padding and holes. No external art is required. To repeat the Cacodaemon workload, copy your own **512x256 Cacodaemon sheet** to `benchmarks/ground-shadows/build/cacodaemon.png` and run:

```powershell
.\gradlew.bat -p benchmarks/ground-shadows run --args="--sprite=build/cacodaemon.png"
```

Only the first six frames are used, with the same 56x48 texture-coordinate crop as the interactive demo. The supplied sprite is not redistributed in this repository. CSV output is written to this project's `build/results/` directory. Last recorded Windows results are in [results/generated-windows-rtx5070.csv](results/generated-windows-rtx5070.csv) and [results/cacodaemon-windows-rtx5070.csv](results/cacodaemon-windows-rtx5070.csv).

## Implementation

Consecutive quads using the same texture are streamed as ordered instances. Each instance carries the original four homogeneous XYZ/UV vertices, its atlas bounds, opacity and contact flag. This moves per-caster uniforms into instance attributes without changing projection arithmetic, five-tap alpha filtering, blending or shadow softness.

The renderer flushes when a texture changes or 1024 projected/contact quads fill the staging buffer. It never sorts casters. A projected shadow followed by its contact shadow stays in that order, even across a capacity boundary. The used prefix is uploaded with orphaned streaming storage; short texture runs do not upload a full-capacity buffer. OpenGL 3.3's minimum of 16 vertex attributes accommodates the 10 instance attributes used here.

CPU staging is fixed at **104 KiB** per renderer and freed on close. Each GPU upload is at most 104 KiB. Driver-managed in-flight storage is separate. There is no growing scene-sized buffer or Java allocation per queued caster. The existing state snapshot is still taken once per `draw` invocation; it was already once per invocation in the reference renderer. Warm measured Java allocation remained constant per renderer call rather than growing with caster count.

`getDrawCalls()` reports actual batch submissions. New `getShadowCount()` reports emitted projected/contact quads. For one atlas, 512 silhouettes use one draw; 512 silhouettes plus contacts also fit one draw. A thousand silhouettes plus contacts use two draws.

## Correctness checks

`LegacyGroundShadowRenderer2D` and `ground-shadow-reference.*` preserve the renderer from commit `4c5d57b739f846e234329a184d5ba565c69556a3`, with only its Java class name and shader resource names changed. They are benchmark fixtures, not a second production renderer.

Before measuring performance, every RGBA output byte is compared against that frozen reference. Each fixture completes **192 exact full-frame comparisons**, checking **522,240,000 bytes**. Generated and Cacodaemon runs together checked **1,044,480,000 bytes with no differences**. Nonempty scenes must contain visible shadow pixels so matching blank images cannot pass.

Cases include RGBA8 and sRGB8 targets; 0, 1, 64, 512, 513, 1023, 1024 and 1025 casters; silhouettes and volume/contact passes; atlas animation; per-caster opacity and tint alpha; rotations and UV flips; disabled, zero-opacity and out-of-radius cards; clipped framebuffer edges; one texture, texture runs and alternating textures; and exact capacity boundaries. Both implementations' depth/cull/blend/sampler/texture-selector/scissor restoration is checked, totaling 3456 state assertions per fixture. Disabled/zero-elevation lights, empty fills, repeated close and drawing after close are also checked.

The comparisons establish exact agreement on the tested driver/workloads, not identical results on every possible GPU or arbitrary future input.

## Timing method and limits

Recorded October 24, 2026 on Windows 11, JDK 25.0.2, NVIDIA GeForce RTX 5070, driver 610.88, OpenGL 3.3. Each workload uses 80 warmup pairs and 160 measured pairs, alternating which renderer runs first. Atlas animation changes outside timed submission. A GPU finish before each sample avoids measuring an earlier queued sample.

CPU medians cover only the renderer's `draw` call, including its state capture/restore. Clear, animation, actor rendering, lighting composition, presentation and blocking timer-query readback are excluded. GPU columns contain elapsed query intervals around submission, including GPU command gaps and the small allocation-counter bookkeeping; they are not isolated shader execution time or whole-game FPS. Allocation columns count the owner thread's Java allocations during submission and exclude native driver memory.

Representative Cacodaemon results from the recorded run:

| Casters | Pass | Draws before → after | CPU before → after | CPU speedup |
| --- | --- | --- | --- | --- |
| 256 | Silhouette | 256 → 1 | 133.8 → 77.9 µs | 1.72× |
| 512 | Silhouette | 512 → 1 | 221.1 → 113.9 µs | 1.94× |
| 512 | Volume + contact | 1024 → 1 | 365.4 → 159.0 µs | 2.30× |
| 1000 | Silhouette | 1000 → 1 | 514.9 → 213.4 µs | 2.41× |
| 1000 | Volume + contact | 2000 → 2 | 787.3 → 285.6 µs | 2.76× |
| 512 | Volume + contact, 32-card texture runs | 1024 → 16 | 411.8 → 167.8 µs | 2.45× |
| 512 | Volume + contact, alternating textures | 1024 → 512 | 415.3 → 293.3 µs | 1.42× |
| 512 | Silhouette, alternating textures | 512 → 512 | 302.9 → 322.5 µs | 0.94× |

The final row is an intentional worst case: every silhouette changes texture, so painter-order-preserving batching cannot reduce draw count. CPU submission was about 6% slower there (about 20 µs per pass), although the recorded GPU elapsed interval was lower. Atlas sharing or adjacent texture runs produce the substantial improvements; this is not a claim that every workload becomes faster. Results vary with hardware, JIT state and system load. Runtime verification here was Windows only.
