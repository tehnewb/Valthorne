# Valthorne

A Java library for desktop 2D and 3D games, built on LWJGL, JOML, and Jolt Physics.
Valthorne provides an application loop, rendering, assets, input, audio, scenes,
physics, particles, lighting, and a shared texture/NanoVG UI system.

**Version 2.0.0 · Java 25 · Apache-2.0**

Explore the [Valthorne website](https://tehnewb.github.io/Valthorne-website/) for an
overview of the engine. The [website repository](https://github.com/tehnewb/Valthorne-website)
is itself a Java Valthorne application compiled for the browser.

The development [web target](portable/README.md) builds shared Java applications with
Filament and Jolt in the browser. Run the original FPS example with
`node portable/fps.mjs run web`, or select `desktop` using the same source.
Use matching engine and companion development checkouts for these unpublished
APIs; see the [unreleased changes](CHANGELOG.md#unreleased).

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

The public [Valthorne examples project](https://github.com/tehnewb/Valthorne-examples) contains ten runnable demos with
walkthroughs, documented source, and licensed assets. Clone it or
[download the standalone ZIP](https://github.com/tehnewb/Valthorne-examples/releases/latest).
It consumes Valthorne from Maven Central and has its own build and validation.
See the [example catalog](docs/examples.md) for commands and platform requirements.

Click a demo below to download its Windows x64 package. Extract it and
double-click **Start.bat**. Java, assets and dependencies are included; no Gradle,
Java installation, or repository clone is needed.

| Download demo | Download demo |
| --- | --- |
| [Application starter](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-starter-windows-x64-2.0.1.zip) | [3D scene](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-scene-windows-x64-2.0.1.zip) |
| [Physics playground](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-physics-windows-x64-2.0.1.zip) | [2D lighting](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-lighting2d-windows-x64-2.0.1.zip) |
| [UI gallery](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-ui-windows-x64-2.0.1.zip) | [Audio studio](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-audio-windows-x64-2.0.1.zip) |
| [Lighting studio](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-lighting-studio-windows-x64-2.0.1.zip) | [Path tracing](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-path-tracing-windows-x64-2.0.1.zip) |
| [Physics studio](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-physics-studio-windows-x64-2.0.1.zip) | [FPS arena](https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-fps-windows-x64-2.0.1.zip) |

The [integration guide](docs/getting-started.md) also includes a minimal application
you can copy into your own project. Example code and resources remain excluded
from this engine repository's normal build and all published library artifacts.

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
| 3D rendering | [Models, cameras and picking](docs/3D.md), [Filament](docs/filament.md), [path tracing](docs/path-tracing.md), [culling](docs/systems/culling.md), [particles](docs/particles-3d.md) |
| Physics | [Jolt rigid bodies, colliders, queries and joints](docs/physics3d.md), [physics studio](docs/physics-studio.md) |
| UI | [Shared UI tree](docs/ui-system.md), [advanced controls](docs/ui-advanced.md), [themes](docs/systems/ui-themes.md) |
| Audio | [Buffered/streaming audio and spatial sound areas](docs/audio-system.md) |
| Playable example | [FPS arena](docs/fps-arena.md) |
| Performance | [Measurement ledger](docs/performance-program.md), [UI measurements](docs/ui-performance.md) |

Historical benchmark reports describe their recorded source revisions and hardware;
use them as evidence for those measurements, not as a guarantee for another machine.

## Repository layout

- `src/main/java/valthorne`: engine implementation and public APIs.
- `src/main/resources`: runtime shaders, materials, fonts, and their licenses.
- `gradle`: release checks, consumer checks, and optional benchmark configuration.
- `docs`: guides, API contracts, and historical performance evidence.
- `images`: Valthorne project branding.

Runnable demos live in [Valthorne-examples](https://github.com/tehnewb/Valthorne-examples);
site development lives in [Valthorne-website](https://github.com/tehnewb/Valthorne-website).
See [contributing](CONTRIBUTING.md) for testing examples against a local engine checkout.

## Build and verify

```sh
./gradlew build                    # Library, sources, Javadoc and documentation checks
./gradlew verifyRelease            # Also checks publication and generated consumer applications
./gradlew verifyGraphicsConsumer   # Published-library OpenGL launch and pixel check
```

Local test and benchmark sources, integration-test folders, and legacy examples
are ignored by Git. Example launchers belong to the companion project above.
CI generates disposable consumers under `build/`, so release
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
