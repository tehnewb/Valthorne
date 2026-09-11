# Runnable examples

The public [Valthorne examples repository](https://github.com/tehnewb/Valthorne-examples) contains the complete examples
and redistributable resources for Valthorne 2.0.0. Each demo includes a walkthrough,
documented source, controls, platform requirements, and a bounded smoke mode.
It builds against the released Maven Central dependency.

```sh
git clone https://github.com/tehnewb/Valthorne-examples.git
cd Valthorne-examples
./gradlew listExamples
./gradlew runMinimalExample
```

Use JDK 25; on Windows use `./gradlew.bat`. You can also
[download the standalone source-and-assets ZIP](https://github.com/tehnewb/Valthorne-examples/releases/latest).
See [companion setup](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/getting-started.md) for IDE configuration
and distribution scripts. Run these tasks **from the examples repository**:

| Task | Walkthrough | Requirements |
| --- | --- | --- |
| `runMinimalExample` | [Application starter](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/starter.md) | OpenGL 3.3 |
| `run3DExample` | [3D scene](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/scene.md) | OpenGL 3.3 |
| `runPhysics3DExample` | [Physics playground](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/physics.md) | OpenGL 3.3 + Jolt |
| `runLighting2DExample` | [2D lighting](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/lighting2d.md) | OpenGL 3.3 |
| `runUIShowcase` | [UI gallery](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/ui.md) | OpenGL 3.3 |
| `runAudioStudio` | [Audio studio](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/audio.md) | OpenGL 3.3 + audio output |
| `runLightingStudio` | [Lighting studio](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/lighting-studio.md) | OpenGL 4.3 |
| `runPathTracingExample` | [Path tracing](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/path-tracing.md) | OpenGL 4.3 |
| `runPhysicsStudio` | [Physics studio](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/physics-studio.md) | Windows x64 / Filament |
| `runFpsArena` | [FPS arena](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/fps.md) | Windows x64 / Filament |

Every launcher accepts `--args="--help"` and `--args="--smoke"`. Graphical smoke
checks need a compatible driver and display. See the companion project's
[platform guide](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/platforms.md) and
[verification guide](https://github.com/tehnewb/Valthorne-examples/blob/main/docs/verification.md).

## Engine checkout and library packaging

Examples remain separate from the engine dependency, sources JAR, Javadoc JAR,
normal engine build, and release checks. No example or test folders are required
in an engine checkout. Optional legacy launchers still work for developers with
ignored `src/examples/` files locally; the public companion is the maintained
collection for users. Historical benchmark reports retain their original source
paths and describe the revisions measured at the time.
