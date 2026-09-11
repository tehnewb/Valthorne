# Optional local examples

Example source and resources under `src/examples/` are local development files.
They are ignored by Git and excluded from normal builds, CI, and all library
publication artifacts. A fresh checkout does not contain them. To start a game,
copy the application from [the integration guide](getting-started.md).

The Gradle launchers remain available for developers who already have the local
files. Each launcher compiles the examples explicitly and uses their local
resources. Use JDK 25; on Windows use `./gradlew.bat` instead of `./gradlew`.

| Local task | Guide / requirements |
| --- | --- |
| `runMinimalExample` | Minimal OpenGL 3.3 window; `--args="--smoke"` exits automatically |
| `run3DExample` | [Raster 3D](3D.md), OpenGL 3.3 |
| `runPhysics3DExample` | [Jolt playground](physics3d.md), OpenGL 3.3 |
| `runLighting2DExample` | [2D lighting](lighting.md), OpenGL 3.3 |
| `runUIShowcase` | [UI gallery](ui-system.md), OpenGL 3.3 |
| `runAudioStudio` | [Spatial audio](audio-system.md), OpenGL 3.3 and audio output |
| `runLightingStudio` | [Lighting studio](lighting-studio.md), OpenGL 4.3 |
| `runPathTracingExample` | [Path tracing](path-tracing.md), OpenGL 4.3 |
| `runPhysicsStudio` | [Physics studio](physics-studio.md), Windows x64 / Filament |
| `runFpsArena` | [FPS arena](fps-arena.md), Windows x64 / Filament |

`verifyPhysicsStudio`, `verifyFpsAssets`, and `verifyFpsArena` also require the
corresponding local examples/resources. They are optional development checks,
not requirements for `build` or `verifyRelease`. Historical reports may describe
these demos and their asset provenance; that does not include those assets in
the release. See [platform support](platforms.md).
