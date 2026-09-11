# Platform and runtime support

Valthorne is a **Java 25 desktop library**. Native dependencies are published
transitively and independently of the publisher's operating system. They are
selected/extracted by the native loaders at runtime; users need no native compiler.
This does not make every renderer available on every operating system.

| Target JVM | Core / raster OpenGL | Jolt physics | Filament adapter | Compute effects |
| --- | --- | --- | --- | --- |
| Windows x86-64 | OpenGL 3.3+ | Native included | Windows x64 sharing path | OpenGL 4.3+ |
| Windows ARM64 | OpenGL 3.3+ driver required | Native included | Unavailable with ARM64 JVM | OpenGL 4.3+ driver required |
| Linux x86-64 / ARM64 | OpenGL 3.3+ | Native included | Unavailable | OpenGL 4.3+ |
| macOS Intel / Apple Silicon | OpenGL 3.3 core | Native included | Unavailable | Unavailable |
| Linux ARM32 hard-float | Native artifacts included; suitable JDK 25 and drivers required | Native included | Unavailable | Driver-dependent |

ARM32 is an artifact target, not a release-tested Java 25 environment. Android,
iOS, browsers, other Unix systems, and Windows x86 are not supported by this build.
Native availability alone is not a claim of hardware validation. The CI workflow
runs build/native-physics/consumer checks on Windows x64, Linux x64, and macOS
Intel and ARM64;
other listed CPUs require testing on their own hardware before game distribution.

## Graphics and launchers

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

Filament's upstream runtime supports more platforms than Valthorne's current
shared-texture adapter. Valthorne currently uses WGL and the Windows x64 binding;
adding another native JAR does not implement another sharing backend. Use
`ModelBatch3D` and the standard scene/physics examples for portable raster rendering.
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
