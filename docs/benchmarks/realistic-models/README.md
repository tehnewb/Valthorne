# Realistic gallery validation — 2026-09-09

The default Physics Studio gallery now contains the Poly Haven ceramic vase (9,408 triangles), closed military crate (10,476 triangles), and marble bust (17,456 triangles), with original 2048×2048 albedo textures. The three assets occupy 8.24 MB on disk; the previous 83 KB furniture set remains available in the spawn selector. See [provenance and conversion](../../physics-studio-realistic-models.md).

Visual inspection caught Filament's default extra UV flip, which sampled texture-atlas padding on the vase. Both material packages now explicitly disable that flip. An asymmetric OBJ/MTL/PNG framebuffer regression failed before the correction and passes afterward for both opaque and glass paths. Full mip chains and trilinear sampling reduce minification aliasing. Native tests verify mip dimensions, averaged texels, unchanged base pixels, explicit refresh after source edits, caller GL state, and borrowed texture ownership. Mips add approximately one third to square-texture GPU storage; this is a quality tradeoff, not a total-memory reduction.

Measurements used Java 25.0.2, RTX 5070 / driver 610.88, High quality, 1600×960 window / 960×820 scene viewport, disabled VSync, a moving camera and fixed 1/60-second physics steps. Runs were serial in fresh processes, with 60 warmup and 240 measured frames. Rendering includes Filament, GPU completion, compositing and UI; it is not a GPU-only timer or total frame time.

| Final workload | Render mean / median / p95 (ms) | Physics mean / median / p95 (ms) |
| --- | --- | --- |
| Realistic gallery, 3 lights | 3.440 / 3.382 / 3.940 | 0.032 / 0.015 / 0.124 |
| Same gallery, 16 lights | 5.162 / 5.046 / 6.447 | 0.033 / 0.015 / 0.133 |
| 288 dynamic primitive bodies, 3 lights | 4.785 / 4.744 / 5.513 | 0.833 / 0.915 / 1.286 |

The gallery reuses seven native mesh-cache entries regardless of light count. These are short shared-desktop measurements, not confidence intervals or an established speedup. The earlier furniture gallery had different geometry and no albedo textures, so its timing is not a like-for-like performance baseline for this feature.

Validation passed the full build, 156 standard tests, 70 3D tests, 19 Jolt tests, 52 UI tests and 27 lighting tests (counts overlap, no failures/skips), plus six-model asset validation and 83 isolated light-rig checks. Some suite additions are concurrent UI work, not this feature. Native smoke tested camera/light input, placement and editor buttons, popup/key-repeat isolation, imported-model spawning, focus controls and GL errors. An additional isolated Jolt drop test exercised convex hulls for all six models for 180 steps each; every body fell and collided with the floor with finite state.

Final framebuffer images were inspected for the overview and close-ups of each realistic exhibit. Generated images are `build/physics-studio/realistic-gallery.png`, `focused-vase.png`, `focused-crate.png` and `focused-bust.png`. The close-up button preserves the live simulation, so moving bodies may enter the view. Saved user light rigs were untouched.

Reproduce:

```text
gradlew build verify3D verifyPhysics3D verifyUI verifyLighting verifyPhysicsStudio
gradlew runPhysicsStudio --args="--smoke"
gradlew runPhysicsStudio --args="--benchmark --scenario=3"
gradlew runPhysicsStudio --args="--benchmark --scenario=3 --lights=16"
gradlew runPhysicsStudio --args="--benchmark --scenario=2"
```

Raw results, before/after UV regression logs, final source hashes and the supplemental collision-check source accompany this report. Physics remains Jolt; rendering remains Filament; vectors, matrices and rotations remain JOML.

Release documentation: this is a historical measurement record. Use the [current release checks](../../releasing.md) for building and validating the development snapshot; preserve the recorded source hashes and results.
