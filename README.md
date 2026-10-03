<p align="center">
  <img width="761" height="322" alt="Valthorne" src="https://github.com/user-attachments/assets/5bca2ab7-aa5a-4272-a690-4e91a5965ff5" />
</p>

<p align="center">
  A Java 25 library for building desktop 2D games.
</p>

<p align="center">
  <a href="https://github.com/tehnewb/Valthorne/releases/latest"><img alt="Version 1.0.0.0" src="https://img.shields.io/badge/version-1.0.0.0-blue" /></a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://github.com/tehnewb/Valthorne/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/tehnewb/Valthorne" /></a>
  &nbsp;&nbsp;&nbsp;
  <a href="LICENSE"><img alt="Apache-2.0 license" src="https://img.shields.io/github/license/tehnewb/Valthorne?cacheSeconds=60&color=orange" /></a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://discord.gg/APqcDzppDv"><img alt="Discord" src="https://img.shields.io/discord/1480243912240136395?logo=discord&logoColor=white&label=Discord&color=green" /></a>
</p>

# Valthorne

A Java 25 desktop 2D game library built on LWJGL and JOML, with OpenAL audio,
NanoVG drawing, Yoga layout, and Artemis ECS integration.

## Features

- Application lifecycle, scenes, input events, state machines, and fixed-rate ticks.
- Textures, sprites, atlases, batching, shaders, cameras, viewports, animation,
  particles, and batched 2D lighting.
- Bitmap fonts and live Slug curve fonts. Regular UI uses Slug; Nano controls
  use NanoVG. Default fonts are loaded from installed system fonts.
- Retained UI trees, layout, themes, tables, trees, virtual lists, and text editing.
- Tiled and LDtk maps, asset loading, primitive collections, pooling, geometry,
  serialization, compression, and file dialogs.
- Buffered and streaming WAV, OGG, and MP3 audio.

## Release version

**Valthorne 1.0.0.0 is the official initial release.** Versions use four numbers:

| Position | Meaning |
| --- | --- |
| First | Massive updates, major migrations, or architectural changes |
| Second | Big feature changes |
| Third | Bug fixes, glitch fixes, and small feature changes |
| Fourth | Quick fixes and hotfixes |

When a number increases, the numbers to its right reset to zero.

## Getting started

Install JDK 25 and add Valthorne to your application's build. The dependency
includes its runtime native libraries transitively.

### Gradle

Add this to `build.gradle`. The application plugin also provides a `run` task:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.tehnewb:Valthorne:1.0.0.0'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = 'MyGame'
    if (System.getProperty('os.name').startsWith('Mac')) {
        applicationDefaultJvmArgs = ['-XstartOnFirstThread']
    }
}
```

### Maven

Add the following inside your `pom.xml` project's `properties` and `dependencies`
sections. Maven Central is Maven's default repository:

```xml
<properties>
    <maven.compiler.release>25</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.tehnewb</groupId>
        <artifactId>Valthorne</artifactId>
        <version>1.0.0.0</version>
    </dependency>
</dependencies>
```

Use JDK 25. Native applications require compatible graphics and audio drivers.
On macOS, launch graphics applications with `-XstartOnFirstThread`.

### Create your first application

Save this as `src/main/java/MyGame.java` in your application project:

```java
import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.graphics.Color;

/**
 * Opens a desktop window and clears it each frame.
 */
public final class MyGame implements Application {
    /**
     * Starts the window and runs the application until it closes.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        /*
         * JGL manages the window and invokes the application lifecycle callbacks.
         */
        JGL.init(new MyGame(), "My Valthorne Game", 1280, 720);
    }

    @Override
    public void init() {
        // Create rendering resources and load assets here.
    }

    @Override
    public void update(float delta) {
        // Advance game state; delta is elapsed time in seconds.
    }

    @Override
    public void render() {
        Window.clear(Color.BLACK);
    }

    @Override
    public void dispose() {
        // Release resources created by this application here.
    }
}
```

With the Gradle configuration above, launch it using `./gradlew run`
(`gradlew.bat run` on Windows). With Maven, compile using `mvn compile`, then run
`MyGame.main` from your IDE with the project's runtime dependencies on its
classpath. Add `-XstartOnFirstThread` to the IDE's VM options on macOS.

`JGL.init` runs the game loop and returns when the window closes. It calls `init`
once, then `update` followed by `render` each frame, and `dispose` during shutdown.
Create GPU and audio resources in `init`, after the native systems are ready,
and release the resources you own in `dispose`.

For larger games, use `GameScreen` with a `Scene` to organize levels, a
`TextureBatch` to batch sprite rendering, and retained UI nodes for menus and
controls. The source Javadocs describe their APIs and resource lifetimes.

## Build and verification

Use the included Gradle wrapper:

```sh
./gradlew -Pprototype3d=false -PeditorPrototype=false build
./gradlew -Pprototype3d=false -PeditorPrototype=false verifyRelease verifySlugTextureBatch
```

On Windows, use `gradlew.bat`. `build` creates the library, sources, and Javadoc
archives. `verifyRelease` validates artifacts, licenses, resources, and metadata
in a local Maven repository. `verifySlugTextureBatch` checks GPU-rendered text.
These commands do not upload a release.

Private editor and 3D prototypes are excluded from public artifacts. Follow
[AGENTS.md](AGENTS.md) and [CONTRIBUTING.md](CONTRIBUTING.md) when changing code.

## Repository organization

- `src/main/java/valthorne` — engine implementation and public APIs.
- `src/main/resources` — packaged shaders and licenses.
- `src/test` — verification code.
- `portable` — shared-source desktop compatibility tooling.
- `gradle` — packaging, publication, and benchmark configuration.

## License and community

Valthorne is distributed under the [Apache License 2.0](LICENSE). Third-party
components retain their own terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Report reproducible bugs through [GitHub issues](https://github.com/tehnewb/Valthorne/issues).
Community discussion is available on [Discord](https://discord.gg/APqcDzppDv).
