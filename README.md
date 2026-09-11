# Valthorne

A Java library for desktop 2D and 3D games, built on LWJGL, JOML, and Jolt Physics.
Valthorne provides an application loop, rendering, assets, input, audio, scenes,
physics, particles, lighting, and a shared texture/NanoVG UI system.

**Version 2.0.0 · Java 25 · Apache-2.0**

Version 2.0.0 contains breaking API changes from 1.4.6, including public JOML math
types. See the [changelog](CHANGELOG.md) and [JOML migration guide](docs/joml-migration.md).

## Start a game

Follow the [complete Gradle and Maven integration guide](docs/getting-started.md).
In your application:

```groovy
repositories {
    mavenCentral()
}
dependencies {
    implementation 'io.github.tehnewb:Valthorne:2.0.0'
}
```

Use JDK 25. Launch with `--enable-native-access=ALL-UNNAMED`; on macOS also use
`-XstartOnFirstThread`. The integration guide includes complete launcher configuration,
a minimal application, Kotlin DSL, Maven, IDE setup, and distribution instructions.
Runtime native dependencies arrive transitively; no native compiler is required.

To use a local source build, run `./gradlew publishToMavenLocal` (Windows:
`./gradlew.bat publishToMavenLocal`) and add `mavenLocal()` before `mavenCentral()`
in the application's repositories.

## Application examples

The [integration guide](docs/getting-started.md) includes a minimal application you
can copy into your own project. Local demos under `src/examples/`, including their
resources, are excluded from Git, normal builds, CI, and published artifacts.
Their optional development launchers are described in the [local example catalog](docs/examples.md).

## Platform support

The standard renderers target **OpenGL 3.3 core** on Windows, Linux, and macOS.
The build includes LWJGL and Jolt native artifacts for their supported desktop CPU
variants. **Filament texture sharing is Windows x64 only**. Path tracing and
radiance cascades require **OpenGL 4.3** and do not run on macOS.

Read the [platform matrix](docs/platforms.md) before choosing a renderer or shipping
an installer. CPU artifact availability, automated checks, and actual GPU validation
are documented separately; no universal OS or hardware compatibility is implied.

## Explore the engine

The [system manual](docs/systems/README.md) covers lifecycle, APIs, examples,
coordinate spaces, resource ownership, and subsystem contracts.

| Area | Guides |
| --- | --- |
| Application and content | [Lifecycle](docs/systems/runtime.md), [assets](docs/systems/assets.md), [input](docs/systems/input.md), [scenes](docs/systems/scenes.md) |
| 2D rendering | [Textures and batching](docs/systems/textures.md), [fonts](docs/systems/fonts.md), [Tiled maps](docs/systems/tiled-maps.md), [lighting](docs/lighting.md) |
| 3D rendering | [Models, cameras and picking](docs/3D.md), [Filament](docs/filament.md), [path tracing](docs/path-tracing.md), [particles](docs/particles-3d.md) |
| Physics | [Jolt rigid bodies, colliders, queries and joints](docs/physics3d.md), [physics studio](docs/physics-studio.md) |
| UI | [Shared UI tree](docs/ui-system.md), [advanced controls](docs/ui-advanced.md), [themes](docs/systems/ui-themes.md) |
| Audio | [Buffered/streaming audio and spatial sound areas](docs/audio-system.md) |
| Playable example | [FPS arena](docs/fps-arena.md) |
| Performance | [Measurement ledger](docs/performance-program.md), [UI measurements](docs/ui-performance.md) |

Historical benchmark reports describe their recorded source revisions and hardware;
use them as evidence for those measurements, not as a guarantee for another machine.

## Build and verify

```sh
./gradlew build                    # Library, sources, Javadoc and documentation checks
./gradlew verifyRelease            # Also checks publication and generated consumer applications
./gradlew verifyGraphicsConsumer   # Published-library OpenGL launch and pixel check
```

Tests, integration-test folders, benchmarks, and examples remain local-only and
are ignored by Git. CI generates disposable consumers under `build/`, so release
checks require none of those folders. `test`, `graphicsTest`, `verify3D`,
`verifyLighting`, and `verifyUI` can still run separately available local tests.
Javadoc is generated under `build/docs/javadoc`; Javadoc errors fail the build.
CI includes desktop build/consumer checks and a separate Linux graphics job.
See [contributing](CONTRIBUTING.md) and the [release procedure](docs/releasing.md).

## License and community

Valthorne is [Apache-2.0 licensed](LICENSE). Fonts, native dependencies and example
assets retain their own terms; see [third-party notices](THIRD_PARTY_NOTICES.md).

Report reproducible bugs through [GitHub issues](https://github.com/tehnewb/Valthorne/issues).
Community discussion is available on [Discord](https://discord.gg/APqcDzppDv).
The [Wiki](https://github.com/tehnewb/Valthorne/wiki) and
[Project Builder](https://github.com/tehnewb/ValthorneProjectBuilder) may describe
older releases; this checkout's integration guide is the reference for 2.0.0.
