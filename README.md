<p align="center">
  <img width="761" height="322" alt="Valthorne" src="https://github.com/user-attachments/assets/5bca2ab7-aa5a-4272-a690-4e91a5965ff5" />
</p>

<p align="center">
  A Java 25 library for building desktop 2D and 3D games.
</p>

<p align="center">
  <a href="https://github.com/tehnewb/Valthorne/releases/latest"><img alt="Version 2.3.0" src="https://img.shields.io/badge/version-2.3.0-blue" /></a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://github.com/tehnewb/Valthorne/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/tehnewb/Valthorne" /></a>
  &nbsp;&nbsp;&nbsp;
  <a href="LICENSE"><img alt="Apache-2.0 license" src="https://img.shields.io/github/license/tehnewb/Valthorne?cacheSeconds=60&color=orange" /></a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://discord.gg/APqcDzppDv"><img alt="Discord" src="https://img.shields.io/discord/1480243912240136395?logo=discord&logoColor=white&label=Discord&color=green" /></a>
</p>

# Valthorne

Valthorne is a Java game-development library built on LWJGL, JOML, Jolt Physics,
OpenAL, NanoVG, Yoga, and an optional Filament renderer. It provides the runtime,
rendering, content, physics, audio, UI, and utility systems needed to build a game
without hiding the underlying desktop APIs.

The current release is **2.3.0** and requires **JDK 25**. Valthorne is licensed
under [Apache-2.0](LICENSE).

## What is included

- **Application runtime:** configurable GLFW windows and OpenGL contexts, frame
  lifecycle, input, typed events, scenes, state machines, fixed-rate ticks, and
  timing utilities.
- **2D rendering:** textures, sprites, atlases, batching, shaders, cameras,
  viewports, animation, particles, raycast lighting, batched lighting, radiance
  cascades, and 2D path tracing.
- **Fonts and UI:** bitmap fonts, GPU-rendered Slug curve fonts, asset loaders,
  retained UI trees, Yoga layout, texture and NanoVG controls, themes, tables,
  virtual lists, text editing, diagnostics, and performance overlays.
- **Maps:** Tiled TMX maps and tilesets, plus LDtk projects with embedded or
  external levels, tiles, IntGrid data, entities, custom fields, backgrounds,
  definitions, and direct `TextureBatch` rendering.
- **3D rendering:** OBJ models, materials, scene graphs, raster and Filament
  renderers, billboards, picking, shadow maps, lighting grids, culling, particles,
  skeletal and morph animation support, and path tracing.
- **Physics and audio:** Jolt rigid bodies, collision shapes, queries, contacts,
  joints, WAV/OGG/MP3 decoding, buffered and streaming playback, and spatial
  sound areas.
- **Assets and utilities:** asynchronous asset loading and caching, primitive
  collections, pools, geometry, dynamic byte buffers, file helpers, plugins,
  compression, encryption, hashing, and typed property sets with binary and
  human-readable text persistence.
- **Portable target:** a development TeaVM browser backend for supported shared
  application code, including WebGL2/WebGPU adapters and browser-specific file,
  audio, and window behavior.

The [system manual](docs/systems/README.md) maps the public engine systems to the
current Java source. Platform-specific capabilities and limitations are documented
in the [platform matrix](docs/platforms.md).

## Add Valthorne to a project

Valthorne 2.3.0 is available from Maven Central:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.tehnewb:Valthorne:2.3.0'
}
```

Use the Java 25 toolchain and configure application launchers with the native-access
and JOML access options used by Valthorne itself:

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = 'game.Main'
    applicationDefaultJvmArgs = [
        '--enable-native-access=ALL-UNNAMED',
        '--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED'
    ]
    if (System.getProperty('os.name').toLowerCase(java.util.Locale.ROOT).contains('mac')) {
        applicationDefaultJvmArgs += '-XstartOnFirstThread'
    }
}
```

Runtime native dependencies are selected transitively; consumers do not need a
native compiler or manually selected LWJGL/Jolt classifiers. See the
[integration guide](docs/getting-started.md) for Gradle Kotlin DSL, Maven, IDE,
local-publication, and distribution setup.

## Minimal application

```java
package game;

import valthorne.Application;
import valthorne.JGL;
import valthorne.Keyboard;
import valthorne.Window;
import valthorne.graphics.Color;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

public final class Main implements Application {
    private final Color background = new Color(0.055f, 0.075f, 0.12f, 1.0f);

    public static void main(String[] args) {
        JGL.init(new Main(), "My Game", 1280, 720);
    }

    @Override
    public void init() {
        // Load assets and create graphics resources on the context thread.
    }

    @Override
    public void update(float deltaSeconds) {
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE)) {
            Window.requestClose();
        }
    }

    @Override
    public void render() {
        Window.clear(background);
    }

    @Override
    public void dispose() {
        // Dispose application-owned resources before the context closes.
    }
}
```

`JGL.init` owns the synchronous application loop. Create and dispose GPU resources
on the context thread, treat `deltaSeconds` as seconds, and keep packaged assets on
the classpath under `src/main/resources`.

## Documentation

| Topic | Reference |
| --- | --- |
| Setup and runtime | [Getting started](docs/getting-started.md), [lifecycle](docs/systems/runtime.md), [platform support](docs/platforms.md), [graphics capabilities](docs/graphics-capabilities.md) |
| Application structure | [Assets](docs/systems/assets.md), [events](docs/systems/events.md), [scenes](docs/systems/scenes.md), [state machines](docs/systems/state-machines.md), [timing](docs/systems/timing.md) |
| 2D graphics | [Textures](docs/systems/textures.md), [cameras](docs/systems/cameras.md), [shaders](docs/systems/shaders.md), [particles](docs/systems/particles-2d.md), [lighting](docs/systems/lighting-2d.md) |
| Fonts and UI | [Bitmap fonts](docs/systems/fonts.md), [Slug fonts](docs/systems/slug-fonts.md), [UI foundations](docs/systems/ui-core.md), [controls](docs/systems/ui-controls.md), [themes](docs/systems/ui-themes.md) |
| Maps | [Tiled maps](docs/systems/tiled-maps.md), [LDtk source package](src/main/java/valthorne/graphics/map/ldtk) |
| 3D graphics | [Models and scenes](docs/systems/models.md), [3D lighting](docs/systems/lighting-3d.md), [Filament](docs/systems/filament.md), [path tracing](docs/systems/path-tracing.md), [culling](docs/systems/culling.md) |
| Physics and audio | [Jolt physics](docs/systems/physics.md), [audio](docs/systems/audio.md) |
| Foundation APIs | [Collections](docs/systems/arrays.md), [buffers](docs/systems/buffers.md), [files](docs/systems/files.md), [compression](docs/systems/compression.md), [encryption](docs/systems/encryption.md), [utilities](docs/systems/utilities.md) |
| Browser target | [Portable desktop/web target](portable/README.md) |

The [complete system index](docs/systems/README.md) covers the engine's 473 Java
source files and links each documented area back to its implementation.

## Runnable examples

Runnable applications are maintained in the separate
[Valthorne-examples](https://github.com/tehnewb/Valthorne-examples) repository.
It includes starter, audio, UI, physics, lighting, 3D scene, FPS, LDtk, and Tiled
examples. The LDtk and Tiled viewers use matching 4096×1440 maps for direct
format and renderer comparisons.

Clone both repositories beside one another to test unpublished engine changes:

```sh
cd ../Valthorne-examples
./gradlew -PvalthorneDir=../Valthorne build
```

On Windows, use `gradlew.bat`. The examples repository also provides documented
launch tasks and standalone Windows packages. Example source and assets do not
belong in this engine repository.

## Platform notes

The standard desktop renderers require OpenGL 3.3 core. OpenGL 4.3 compute
features, including compute-based path tracing and radiance cascades, are not
available on macOS. Filament has a direct Windows x64 sharing path and transfer
paths for supported Linux and macOS ARM64 runtimes; consult the platform matrix
before selecting it for distribution.

Desktop artifacts include native dependencies for the supported Windows, Linux,
and macOS targets listed in [platform support](docs/platforms.md). Native artifact
availability does not guarantee that a particular driver or machine supports every
renderer. Android, iOS, Windows x86, and other Unix systems are not desktop targets.

## Build and verify

Use the included Gradle wrapper:

```sh
./gradlew build
./gradlew verifyRelease
./gradlew verifyGraphicsConsumer
```

- `build` compiles the library and creates the binary, source, and Javadoc archives.
- `verifyRelease` checks release packaging, metadata, native physics, and isolated
  module-path and Maven-style consumers.
- `verifyGraphicsConsumer` launches a published-library consumer with a real OpenGL
  context and verifies rendered pixels; it requires a usable display and driver.

Focused `verifyPhysics3D`, `verify3D`, `verifyLighting`, `verifyUI`, and benchmark
tasks are also available when their local test sources and required graphics
environment are present. See [contributing](CONTRIBUTING.md) and the
[release procedure](docs/releasing.md) before submitting or publishing changes.

## Repository organization

- `src/main/java/valthorne` — engine implementation and public APIs.
- `src/main/resources` — packaged shaders, Filament materials, fonts, and licenses.
- `docs` — integration guides, subsystem contracts, and performance records.
- `portable` — shared-source desktop and TeaVM web compatibility tooling.
- `gradle` — verification, publication, consumer-test, and benchmark tasks.
- `images` — project branding.

The project website is maintained separately in
[Valthorne-website](https://github.com/tehnewb/Valthorne-website).

## License and community

Valthorne is distributed under the [Apache License 2.0](LICENSE). Bundled fonts,
native libraries, and other third-party components retain their own terms; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Use [GitHub issues](https://github.com/tehnewb/Valthorne/issues) for reproducible
bugs and feature requests. Community discussion is available on
[Discord](https://discord.gg/APqcDzppDv).
