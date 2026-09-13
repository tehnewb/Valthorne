# Platform and runtime support

Valthorne is a **Java 25 desktop library**. Native dependencies are published
transitively and independently of the publisher's operating system. They are
selected/extracted by the native loaders at runtime; users need no native compiler.
This does not make every renderer available on every operating system.

A separate [web build target](../portable/README.md) compiles Java applications
with TeaVM and supplies the existing engine APIs through browser backends.
Shared-source tests cover Jolt physics, Filament and raster 3D, 2D rendering and
lighting, radiance cascades, particles, audio, fonts, UI, asset loading and Tiled maps.
Select the backend in the build while keeping supported application source unchanged.
The web target also implements WebGPU compute, WebGL2 path tracing, bounded
compressed audio, persistent Java file operations and browser window controls;
skeletal and morph deformation have rendered-pixel tests. Browser window/file
semantics and graphics capabilities still differ from the desktop. Raw native
calls remain desktop-specific. See the web target's supported/outstanding matrix
before selecting it for a game.

| Target JVM | Core / raster OpenGL | Jolt physics | Filament adapter | Compute effects |
| --- | --- | --- | --- | --- |
| Windows x86-64 | OpenGL 3.3+ | Native included | Windows x64 sharing path | OpenGL 4.3+ |
| Windows ARM64 | OpenGL 3.3+ driver required | Native included | Unavailable with ARM64 JVM | OpenGL 4.3+ driver required |
| Linux x86-64 / ARM64 | OpenGL 3.3+ | Native included | OpenGL 4.1 transfer path implemented; native validation pending | OpenGL 4.3+ |
| macOS Apple Silicon | OpenGL 3.3 core | Native included | OpenGL 4.1 transfer path implemented; native validation pending | Unavailable |
| macOS Intel | OpenGL 3.3 core | Native included | No runtime in the pinned binding release | Unavailable |
| Linux ARM32 hard-float | Native artifacts included; suitable JDK 25 and drivers required | Native included | Unavailable | Driver-dependent |

ARM32 is an artifact target, not a release-tested Java 25 environment. Android,
iOS, browsers, other Unix systems, and Windows x86 are not supported by the desktop artifact.
Native availability alone is not a claim of hardware validation. The CI workflow
runs build/native-physics/consumer checks on Windows x64, Linux x64, and macOS
Intel and ARM64;
other listed CPUs require testing on their own hardware before game distribution.

## Graphics and launchers

For opt-in desktop 4.3 → 4.1 → 3.3 context negotiation and a Filament-to-raster
selection API, see [graphics capability selection](graphics-capabilities.md).
OpenGL ES and Raspberry Pi rendering remain unimplemented; native ARM artifacts
alone do not establish compatibility.

The ordinary renderers use OpenGL 3.3 core. macOS needs
`-XstartOnFirstThread`; Valthorne's repository Java launch tasks add it automatically.
Applications consuming the library must configure their own launcher, along with
`--enable-native-access=ALL-UNNAMED`. See [complete builds](getting-started.md).

macOS OpenGL is limited to 4.1, so OpenGL 4.3 compute shaders (path tracing and
radiance cascades) cannot run there. GLFW also documents its macOS core-context
and window-system constraints in the [compatibility guide](https://www.glfw.org/docs/latest/compat_guide.html).

The hosted Intel and Apple Silicon CI runners passed native dependency and
physics checks, but could not create an NSGL OpenGL pixel format. CI checks native
window creation and the first-thread launcher separately with `verifyWindowConsumer`.
Mac rendering remains unverified; run `verifyGraphicsConsumer` on a Mac with a
working OpenGL driver before distributing a game for that target. The strict
graphics check still fails if no context can be created.

Filament uses WGL texture sharing on Windows x64. Linux x64/ARM64 and macOS
ARM64 now have packaged runtimes and a separate OpenGL-context transfer path:
material textures are uploaded on import/invalidation, and rendered pixels are
read back into a reusable buffer for presentation. This path was exercised on
Windows with `-Dvalthorne.filament.readback=true`; native Linux/macOS execution
is still pending. The transfer incurs a GPU/CPU copy every frame. Unsupported
runtime architectures retain AUTO's raster fallback. Android and iOS still
require separate native launchers and engine adapters; this change does not
complete those ports.
See [Filament contracts](filament.md).

On Linux, install your distribution's graphics driver and desktop display
dependencies. A hidden GLFW window still needs a display server. CI uses Xvfb and
Mesa software OpenGL for a published-library graphics consumer; it is not a GPU performance test.
On a Wayland desktop, the installed GLFW backend/display integration must be usable.

## Audio and temporary storage

Normal applications need a working audio output device. For silent CI launch checks,
set `ALSOFT_DRIVERS=null` in that test process. This disables audible OpenAL output
and should not be enabled in a game launcher by default. CPU and native physics
tests do not open an audio device. Native extraction needs writable temporary/cache
storage and permission to load native libraries.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Unsupported class file / no Java 25 toolchain | Use JDK 25 in `JAVA_HOME`, Gradle, and the IDE. |
| Native library missing or wrong architecture | Keep runtime dependencies and use a JVM for the intended CPU; do not copy only Valthorne's JAR. |
| GLFW initialization/window creation failure | Check display access, driver, and requested OpenGL version; on macOS check the main-thread flag. |
| Filament platform exception | Use a portable raster renderer outside Windows x64. |
| Missing shader/font/material | Preserve classpath resources when repackaging. |
| OpenAL cannot open device | Select/enable an audio output device; use the null driver only for silent tests. |

Run `./gradlew verifyRelease` for display-free build/publication checks and
`./gradlew verifyGraphicsConsumer` on a suitable graphics host. CI generates these
temporary consumer applications under `build/`; no versioned test or example
folder is required. The graphics consumer checks a real context, shader creation
and framebuffer pixels, using the macOS first-thread launcher where needed.
Focused `graphicsTest`, `verify3D`, `verifyLighting`, and `verifyUI` tasks remain
available for optional local tests. Their source folders are ignored by Git.
