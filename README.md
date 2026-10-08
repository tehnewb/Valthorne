<p align="center">
  <img width="761" height="322" alt="Valthorne" src="https://github.com/user-attachments/assets/5bca2ab7-aa5a-4272-a690-4e91a5965ff5" />
</p>

<p align="center">
  A Java 25 library for building desktop 2D games.
</p>

<p align="center">
  <a href="https://github.com/tehnewb/Valthorne/blob/main/gradle.properties"><img alt="Version 1.2.1.0" src="https://img.shields.io/badge/version-1.2.1.0-blue" /></a>
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

- **Application and scenes:** Desktop window creation, application lifecycle
  callbacks, scene switching, and scene-level input listeners.
- **Input and events:** Keyboard, mouse, scrolling, dragging, file-drop events,
  and an event publishing system.
- **State machines and scheduling:** Conditional state transitions, guards,
  transition actions, fixed-rate ticks, and delayed and repeating actions.
- **2D rendering:** Textures, sprites, texture atlases, sprite culling, batched
  drawing, nine-patch textures, framebuffers, and geometric primitives.
- **Shaders and effects:** Custom shaders, compute shaders, and effects including
  blur, glow, outlines, flashing, water, and reflections.
- **Cameras and viewports:** Orthographic cameras and fit, fill, stretch, screen,
  and integer-fit viewports for different resolutions and pixel-art scaling.
- **Animation and particles:** Frame-based animation with playback modes and
  listeners, plus particle emitters with box, circle, cone, and line spawning.
- **2D lighting:** Batched lights and ground-shadow rendering.
- **Fonts and vector drawing:** Bitmap fonts, live Slug curve fonts, and NanoVG
  drawing. Regular UI uses Slug; Nano controls use NanoVG. Default fonts are
  loaded from installed system fonts.
- **UI layout and styling:** Retained UI trees, Yoga layout, themes, gradients,
  borders, scrolling, and a UI inspector.
- **UI controls:** Buttons, checkboxes, radio groups, sliders, number spinners,
  progress bars, combo boxes, color pickers, tabs, split panes, menus, tooltips,
  and modal windows.
- **Data and editing interfaces:** Tables, directory trees, virtual lists,
  file explorers and choosers, text fields, and a NanoVG code editor.
- **2D physics:** Jolt-backed planar rigid and soft bodies, gravity, forces,
  impulses, sensors, collision filtering, contact events, ray and area queries,
  distance and pivot joints, and interpolated geometry updates.
- **Maps and assets:** Tiled and LDtk map loading, tile sets, map layers and
  objects, and configurable asset loaders.
- **Audio:** OpenAL playback with buffered and streaming WAV, OGG, and MP3 audio,
  sound sources, and sound areas.
- **Game data and utilities:** Artemis ECS integration, primitive collections,
  object pooling, geometry, dynamic byte buffers, property sets, serialization,
  compression, encryption, hashing, and native file dialogs.
- **Plugins:** Asynchronous discovery and loading of plugins from JAR files.

## Release version

**Current Valthorne version: 1.2.1.0.** Versions use four numbers:

| Position | Meaning |
| --- | --- |
| First | Massive updates, major migrations, or architectural changes |
| Second | Big feature changes |
| Third | Bug fixes, glitch fixes, and small feature changes |
| Fourth | Quick fixes and hotfixes |

When a number increases, the numbers to its right reset to zero.

Merged pull requests can update the development version automatically with
`version:feature` (second number), `version:patch` (third number), or
`version:hotfix` (fourth number). Unlabeled PRs
leave it unchanged. See [automatic version setup](.github/VERSION_AUTOMATION.md)
for the owner-controlled GitHub App configuration and release behavior.
The `version:feature` label publishes automatically after release checks pass.
Adding `release:urgent` alongside a version label also requests automatic Maven
Central publication. Increasing the first or second version number explicitly
also requests publication; routine version bumps do not.

## Getting started

The version references on this page and the wiki track `gradle.properties` on
`main`. A development version may not yet be published to Maven Central.

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
    implementation 'io.github.tehnewb:Valthorne:1.2.1.0'
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

// Apply these runtime options to every JavaExec task, including Gradle's run task.
tasks.withType(JavaExec).configureEach {
    jvmArgs '--enable-native-access=ALL-UNNAMED', // Permit classpath libraries such as LWJGL to load and call native code.
            '--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED' // Allow dependencies to use JDK internal memory APIs; prevents access errors when those APIs are used.
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
        <version>1.2.1.0</version>
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

## License and community

Valthorne is distributed under the [Apache License 2.0](LICENSE). Third-party
components retain their own terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Report reproducible bugs through [GitHub issues](https://github.com/tehnewb/Valthorne/issues).
Community discussion is available on [Discord](https://discord.gg/APqcDzppDv).
