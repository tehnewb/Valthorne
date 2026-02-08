# Valthorne

### What this project does best

Valthorne is a lightweight 2D game library that wraps LWJGL to remove boilerplate and make small-to-medium 2D apps fast to prototype and ship. Its strongest qualities are:

- Minimal app lifecycle and loop that “just works”
    - Single-call bootstrap via `JGL.init(...)` running an `Application` (`init`, `update(delta)`, `render`, `dispose`). See `src/main/java/valthorne/JGL.java` and `Application.java`.
    - Built-in frame timing (`getDeltaTime`, `getFramesPerSecond`) and graceful shutdown.

- Straightforward windowing and input over GLFW
    - Easy window setup and 2D orthographic projection out of the box (`Window.java`), with resize handling published as events.
    - Mouse/keyboard utilities encapsulated so you don’t touch raw GLFW everywhere.

- Simple, practical UI toolkit for 2D games/tools
    - Ready-made widgets (e.g., `Button`, `TextField`, `Checkbox`) with stateful styling (`ui/elements`, `ui/styles`).
    - Skinning via textures and nine-patch drawables (`graphics/texture/NinePatchTexture`, used in `UITest.java`).
    - Percentage/pixel-based layout API (`UI`, `Layout`, `Value`, `ValueType`) that responds to window resize events.
    - The `src/test/java/UITest.java` demo shows a complete styled login UI with hover/press/disabled states.

- Pragmatic 2D rendering building blocks
    - Immediate, fixed-function style 2D setup (orthographic `glOrtho`), good for overlays, tools, and classic 2D games without heavy shader boilerplate.
    - Texture and font helpers (`graphics/texture/*`, `graphics/font/*`) to draw skinned UI and text quickly.

- Asset handling focused on developer velocity
    - Centralized async-friendly asset manager with typed loaders/caching (`asset/Assets.java`) and progress tracking—used in tests.

- Event bus for decoupled subsystems
    - Lightweight pub/sub (`JGL.subscribe`, `JGL.publish`) used by the window system to deliver resize events (`event/*`).

- Useful support modules bundled in
    - Viewports/cameras for 2D (`viewport`, `camera`), audio helpers (`sound`), math/collections utilities to avoid external dependencies.

### Bottom line
Valthorne excels at rapidly standing up polished 2D applications with a skinnable UI and minimal LWJGL boilerplate. If you want to prototype or ship a 2D tool/game with custom-styled controls, simple layout, and a clean app loop without wrestling with raw GLFW/OpenGL setup, this project does it perfectly.
## Getting Started

Add Valthorne to your build as a dependency.

### Gradle (Groovy)
```groovy
implementation 'io.github.tehnewb:Valthorne:1.0.2'
```

### Gradle (Kotlin DSL)
```kotlin
implementation("io.github.tehnewb:Valthorne:1.0.2")
```

### Maven
```xml
<dependency>
    <groupId>io.github.tehnewb</groupId>
    <artifactId>Valthorne</artifactId>
    <version>1.0.2</version>
</dependency>
```

Notes:
- Valthorne wraps LWJGL. If you run on desktop, ensure LWJGL natives for your OS are available at runtime. If your project doesn’t already include them, add the appropriate LWJGL classifier dependencies (e.g., `natives-windows`, `natives-linux`, `natives-macos`).
- Requires a recent JDK (the library is built with Java 23 toolchain).
- A project builder will be done in the future so all of this can be generated for you

### What I'm currently working on
- UI system
    - Easy to construct UI system possibly based on chaining
    - System similar to CSS when designing the layout
    - I'm thinking about creating a shader for painting based on CSS attributes
       - I have prototyped this system probably 5 times already and have failed miserably because I'm picky how I want it, so for now we're just dealing with a skin system how libGDX does it.
    - Element transitions and effects

### What I plan on doing
- Shaders
- Lighting
- Tilemaps
- Basic effects like tint/alpha, additive/muliply blend, particles
- Animation system based on sprite sheets or a collection of sprites
- State machine system that switches based on conditions
- Scenes
- Physics & collision system
- Controller support
