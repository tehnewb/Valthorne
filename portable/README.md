# Valthorne web target

This is a working first browser target, not a port of every desktop subsystem.
Java gameplay is compiled with TeaVM; Filament renders through WebGL 2 and Jolt
runs through WebAssembly. There is no JVM, desktop LWJGL, JNI or Java FFM in the
browser distribution. Android remains future work.

## Build and run

Use JDK 25 and Node/npm. Install browser dependencies with `npm ci` in
`portable/web`. To compile the shared library and browser sources:

`./gradlew -p portable :core:classes :web:classes`

Export an application by supplying `-PapplicationMain`, `-PapplicationSources`,
and optionally `-PapplicationResources` to `buildGame -Ptarget=web`.
The separate examples repository supplies the FPS application:

`node portable/fps.mjs build web`

Serve the result with `node portable/web/serve.mjs`. Use `gradlew.bat` on Windows.
The bundled test applications, default test launcher, and automated test runners
have been removed. Exports now require an application entry point.

## Historical backend notes

The notes below describe earlier validation and implementation work. References
to compatibility fixtures, browser test scripts, and verify commands are historical;
those suites are no longer present or available to run.

## Same application source, selected backend

### Committed compatibility verification

`portable/verification/java` contains executable consumers kept outside the engine
artifacts. `:core:verifyCoreCompatibility` checks frame-loop timing, lifecycle and
failure cleanup, plus collection growth and deletion. Its JSON report is written
to `portable/core/build/reports/verification/core.json`.

Verify native physics and Filament platform selection without opening a window:

```sh
./gradlew runGame '-PapplicationSources=portable/verification/java' '-PapplicationMain=valthorne.verification.DesktopNativeVerification'
```

The native consumer checks collisions, raycasts, model binding, impulses, joints,
cleanup, layer filtering, catch-up steps and sensors using the public engine API.
It writes `build/reports/verification/desktop-native.json`. Both suites require
every declared case and its minimum assertion count to run; missing cases fail.
CI runs these committed consumers on a fresh checkout. Native checks run on
Windows, Linux and macOS; the separate renderer smoke job is manual because
hosted runners do not guarantee an accelerated graphics context.

### Full original FPS arena

The separate `Valthorne-examples` repository's `valthorne.examples.fps.FpsArena` builds for
both targets, with the same gameplay, downloaded model resources, Jolt physics,
particle lights, and custom HUD. These commands select that full game instead
of the smaller browser integration arena:

```sh
node portable/fps.mjs run web
node portable/fps.mjs run desktop
node portable/fps.mjs verify web
node portable/fps.mjs verify desktop
```

Use `build web` to produce a static deployment in `portable/web/build/dist`.
Clone `Valthorne-examples` alongside this repository first. Set
`VALTHORNE_EXAMPLES_DIR` to use another location. The FPS launcher reads example
sources and assets there; maintain runnable examples in that repository.
CI pins [example revision d9c8ba4](https://github.com/tehnewb/Valthorne-examples/commit/d9c8ba498674686efc41f8e7ce0a343d95159e5e),
whose own checks use [engine revision 256cde5](https://github.com/tehnewb/Valthorne/commit/256cde556a407f347e27644e9d0de9e744f8103e).
Use these revisions to reproduce the shared FPS build; the portable APIs are
development sources and are not included in the older published engine dependency.
Set `PORT` to change the preview server's default port of 8095.
The application uses `Canvas2D` for its custom HUD and `PlatformTools` for
graphics diagnostics and test input; native bindings stay inside the engine.
Other applications can select individual Java source files with the optional
comma-separated `applicationIncludes` build property.

Browser verification exercises movement, jumping, aiming, shooting, reload,
grenades, flares, particle controls, pause, five restarts, and final resource
cleanup. Its frame interval report is a local browser measurement, not a
minimum-hardware guarantee. See `validation/full-fps-web-2026-09-12.md`.

Set `TEST_BROWSER` to `chrome`, `edge`, `firefox`, or `webkit` to select the FPS
test browser. The initial [browser matrix](validation/browser-matrix-2026-09-12.md)
records a WebKit mouse-capture failure and local Firefox/Edge setup blockers;
cross-browser support is not yet fully validated.

The web build now supplies backend implementations under the existing `valthorne`
API names. Application code uses `JGL.init`, `Application`, `JGLConfiguration`,
`Window`, `Keyboard`, `Mouse`, `Color` and the existing event API. It does not import
`Web*` classes or branch on the platform for this supported subset.

The same build selection now covers the existing `PhysicsWorld3D`, `RigidBody3D`,
`CollisionShape3D`, `DistanceJoint3D`, `FilamentRenderer3D`, and `SceneRenderer3D`
APIs. `Scene3D`, `SceneNode3D`, `Model3D`, `ModelBuilder3D`, `ModelInstance3D`,
cameras, materials, point lights and mesh-based `ParticleEmitter3D` use the original
engine bytecode. Physics and model coordinates retain Z-up; cameras retain their
existing configurable up vector. This is separate from the earlier Y-up demo host.

From the repository root, select a target while keeping the main class and source
path unchanged (quote property arguments in PowerShell):

```powershell
./gradlew -p portable buildGame '-Ptarget=desktop' '-PapplicationMain=com.example.Game' '-PapplicationSources=game/src/main/java'
./gradlew -p portable buildGame '-Ptarget=web' '-PapplicationMain=com.example.Game' '-PapplicationSources=game/src/main/java'
```

Source paths are relative to the repository root or absolute. Desktop `buildGame`
compiles the application; `runGame` with the same properties launches it. Web
`buildGame` produces the static distribution; serve it with the existing server.
The named main class must provide `public static void main(String[] args)`.
These are repository build tasks, not yet a published consumer Gradle plugin.

`compatibility/CommonApplication.java` is a single unchanged source compiled and
executed against both backends. It checks 120 update/render callbacks, task
scheduling, event dispatch, return-after-shutdown ordering, keyboard character mapping, mouse cursor configuration
and clearing through `Window.clear(Color)`. The browser test additionally injects
real keyboard/mouse input and checks the original event API and polling state.
`FailureApplication` separately checks that update failures reach the caller after
application disposal. Browser lifecycle suspension uses TeaVM coroutines rather
than blocking the browser event loop.

`CommonGestureApplication` verifies trusted Java UI input while browser animation
is suspended for more than six seconds. It checks mouse capture, queued input
during filesystem I/O, deferred tasks, frame timing and shutdown. This fixture
uses only engine APIs, but runs only in the browser suite because its driver
controls browser input and scheduling; `--desktop` still runs this case in Chrome.

```powershell
./gradlew -p portable runGame '-Ptarget=desktop' '-PapplicationMain=compatibility.CommonApplication'
./gradlew -p portable buildGame '-Ptarget=web' '-PapplicationMain=compatibility.CommonApplication'
node portable/web/verify-common.mjs
```

The browser test needs the local server running. Rebuild the normal examples with
`./gradlew -p portable :web:webDist` afterward.

Backend replacement is explicit: the build filters overridden classes out of the
engine API artifact, then compiles their browser implementations. There is only
one implementation of each overridden class on TeaVM's classpath. `webDist` also
compares compiled public signatures and constants with the desktop artifact to
catch API drift: JGL 10/10, Keyboard 130/130, Mouse 31/31 and Window 39/39 public
static members currently match. The replacement physics world (29), body (31),
shape (8), joint (2), and Filament renderer (17) match every declared public member,
including constructors, with nested settings/enums and `SceneRenderer3D` checked too.
The report currently checks 106 classes and 1,832 public members, all matching.
These counts describe API surface, not exhaustive
behavioral or hardware coverage. Engine API
bytecode is reused without pulling in the desktop native dependency graph.

`JGL`, keyboard, mouse and all declared `Window` methods have browser backends.
Window size, position, borders, resize limits, opacity, minimize/maximize/restore
and always-on-top operate on the game surface inside the page. Icons and custom
cursors use RGBA images. Fullscreen and pointer lock require a user gesture;
fullscreen requests made during startup wait for the next click or key press.
The browser owns OS windows and frame scheduling: native handles return zero,
cursor positioning changes engine coordinates without moving the OS pointer,
and swap interval cannot override browser scheduling. Raw JNI/FFM/LWJGL calls
remain desktop-specific. The supported original API paths below are tested
separately from the older portable demo interfaces.

## Audio, fonts, UI, models and lighting

The original `SoundPlayer` control and queue logic now uses Web Audio. Tests cover
PCM16 WAV loading, playback, pause/resume, looping, seeking, gain/pitch, sound-area
attenuation and disposal, including measured browser waveform output. WAV probing
preserves source payload offsets. MP3 and Vorbis use pinned incremental WASM
decoders at the source sample rate; URL sources use streaming Fetch readers.
Long sounds retain bounded decoder blocks instead of the full decoded track.
Metadata probing scans compressed content with bounded storage; seeking reopens
and discards samples, so startup and seek cost grow with track length. Short
sounds (at most 15 seconds and 8 MiB PCM) remain buffered. Explicit whole-file
decoder calls intentionally return complete PCM. The shared audio fixture covers
short/long MP3 and Vorbis from paths and encoded byte arrays as well as WAV.
Audio requires a user gesture to unlock, as required by browsers.

`FontData` bakes TrueType/OpenType glyphs with browser font rasterization. The
original `Font` layout/drawing code uses those atlases. The backend releases its
temporary atlas copies after transfer and retains a 1×1 measurement canvas for
cached kerning. The exposed native STB handle is null in browser builds.

Existing Yoga layouts, texture widgets, Nano widgets, themes and UI routing now
run through their original APIs. Yoga 3.2.1 runs in WebAssembly. Canvas vector
commands are composited into the same WebGL batch at painting boundaries so mixed
controls retain their drawing order. Tests exercise button clicks, text editing,
Unicode grapheme boundaries, slider dragging, resize and full teardown. Clipboard
operations use browser permission-controlled APIs. The managed NVGColor/NVGPaint
values support the engine's UI calls; arbitrary native NanoVG pointer interop is
not available.

`SlugFont`, `SlugBatch`, retained `SlugTextRun` and `SlugLabel` now retain the original
curve compilation, band lookup and instanced shader algorithms. OpenType.js 1.3.4
replaces the native font-outline parser. WebGL 2 stores curves and integer band
addresses in the same texture formats; text is not substituted with raster labels.
The font fixture checks advances against desktop, multiline/space metrics, actual
white/yellow text pixels, UI layout and disposal. TrueType is exercised; the parser's
CFF/cubic-outline path still needs a representative font fixture. Byte-buffer uploads
use TeaVM's existing typed-array view without an extra CPU staging copy.

## Asset management and Tiled maps

The original `Assets` API now runs with cooperative loading tasks and a browser
`CompletableFuture`/`CompletionStage` implementation. Existing `loadAsync`, `get`,
prepared batches, progress, cache keys, custom loaders, unload and shutdown/restart
work without a separate promise API. Tests cover deep completion chains, cancellation,
failure propagation, timed waits and mixed texture/font/OBJ/Tiled batches. Public
zero-argument `dispose()` methods on user assets are retained for reflection, alongside
the original `AutoCloseable` convention. Java tasks can suspend for I/O; this does not
provide CPU parallelism or native threads. It is not full Java SE library compatibility.

TMX/TSX parsing uses Aalto 1.3.3 and the original Tiled engine classes. URL-relative,
in-memory and recursive classpath dependencies are covered, including texture decoding,
object layers, properties, animated tile selection, unsigned tile flags, infinite
chunks, CSV and MIME Base64 with gzip/zlib. Malformed XML is rejected. Pixel checks
verify actual tile rendering, and the same fixture runs on desktop. This preserves
the existing parser/renderer feature set; unsupported desktop Tiled features such
as XML-encoded tile payloads are not added by the port.

Native input callbacks deliver events on the application coroutine, allowing
handlers to load assets without blocking the browser. When the coroutine is
waiting for its next frame, input wakes it immediately without advancing the
game simulation or rendering another frame. This lets click/key handlers request
mouse capture while the browser's user activation is still valid. Input that
arrives during a suspended asset load stays queued until the application can
handle it; browser permissions can expire during that wait. Key/button polling
advances with event delivery, including press/release pairs within one frame.

The existing `ObjModel3D` and `ModelLoader` now load OBJ, MTL and diffuse images
from asset URLs or custom resolvers. Parsing, material grouping, coordinate
conversion and triangulation remain the original engine implementation. Uploaded
textures and part meshes are shared across instances and disposed explicitly.
These models use the engine's Filament materials and receive shadows; the older
GLB demo's imported-ubershader restriction described below is a separate path.

`Lighting2D` runs the original cached polar-shadow and HDR light-map algorithm on
WebGL 2. It requires `EXT_color_buffer_float`. Pixel tests verify warm/cool lights,
occlusion and disabling shadows. Cache checks verify zero redundant shadow uploads
or light-map renders for static scenes. Timing fixtures compare cached, moving and
shadow-disabled scenarios with desktop swap throttling disabled. They measure CPU
submission, including possible driver stalls, rather than isolated GPU time.

The original raster `MeshBatch3D`, `BillboardBatch3D`, `ShadowMap3D` and
`Lighting3D` also run on WebGL 2. `SceneRenderer3D.Backend.RASTER` selects this path
without changing the scene API. Lighting's desktop texture buffers are represented
by ordinary float/integer textures; GLSL fetch helpers preserve the indexed data.
Tests check colored meshes, textured billboards, cached shadows, receiver pixels
with shadows enabled/disabled, and resource teardown. Browser capabilities report
raster and Filament support, and independently probe WebGPU compute and float
WebGL path-tracing capabilities. See the backend details below.

The older `RayHandler` lighting API, `ShapeShader`, `TexturePacker` and 2D
`ParticleSystem` retain their original algorithms too. The shared fixture checks
atlas coordinates, colored-light output, dark background pixels, particle bursts,
the draw budget, and teardown. Shader-controlled point size works directly in
WebGL 2. `GroundShadowRenderer2D` and `SpriteVolumeRenderer2D` now have a dedicated
shared-source rendering fixture too: disabling ground shadows brightens receiver
pixels, and disabling the lights changes sprite shading. The original
`PerformanceOverlay` uses a browser-generated ASCII atlas and retains its sampling
and drawing code. Atlas construction happens once; drawing does not rasterize text.

`RadianceCascades` now uses fragment passes for its four independent-pixel kernels.
The original tracing, interval extension, hierarchy merge, and resolve calculations
are preserved. The browser adaptation rejects shader changes that introduce
compute-only behavior; this is not a general GLSL compute implementation. Rendering
preserves the surrounding framebuffer, viewport, vertex-array, write-mask and raster
enable state. Tests cover resize/replacement, colored radiance, blocker removal,
zero intensity, and cleanup. Like the HDR light-map renderer, it needs
`EXT_color_buffer_float`.

Run `node portable/web/verify-compatibility.mjs --desktop` to compile and execute
the shared fixtures against both targets. It also compares public signatures for
the new backends. Browser screenshots and measurements are under `web/build/verification`.
Passing these fixtures does not establish complete engine coverage or mobile support.
The suite includes 22 shared applications. The web CI workflow runs these browser
checks and the older demo checks on changes; its hosted environment still needs its
first successful run. The API checker batches class inspection and caches unchanged
inputs. A local comparison produced the identical report in 0.69 seconds versus
33.4 seconds before batching; this is a build-validation improvement, not game FPS.

## Existing texture and 2D API backends

`TextureData`, `Texture`, `TextureBatch`, `Sprite`, `NinePatchTexture`, `FrameBuffer`,
`ImmediateTextureRenderer`, `Shader` and `ShaderSources` now retain their existing
public APIs in browser builds. The build generates backend sources from the original
engine files, replacing native imports only; batching, transforms, clipping,
nine-patch layout and sprite culling remain shared code. Public signature checks
cover all nine classes (206 declared members including constructors and record methods).

Images can be decoded from the original path and encoded-byte overloads, with RGBA
pixels and optional vertical flipping. `TextureData.load` suspends the Java coroutine
while the browser fetches/decodes; initialization and frame callbacks stay on the same
coroutine, preserving Jolt thread ownership. Loading during an update is tested.
This does not block the browser or require application-side promise wrappers.
Browser-supported image formats are used; parity with every STB image format is not
claimed. CPU pixel buffers are garbage-collected after application references are
released; `dispose()` does not retain them in a global registry.

The original instanced sprite batch runs on a WebGL 2 canvas composited over Filament.
It supports shader overrides, texture units, UV regions, tint/alpha, rotation,
translations, nested clipping, framebuffer draws and the original culling counters.
Bundled shader resources are embedded at build time. GLSL 330 core version directives
are adapted to GLSL ES 300 with precision declarations; desktop-only shader features
and direct LWJGL calls are not portable. Browser alpha accumulation uses separate
alpha factors so transparent overlays composite correctly.

Original `Material3D.setTexture` works with uploaded textures. Filament copies are
created lazily, shared between material instances, mipmapped and released with the
source texture. The two graphics contexts keep separate GPU texture objects; there
is also a retained pixel copy for cross-backend uploads. This is a compatibility
tradeoff, not a zero-copy implementation. Sampling a 2D framebuffer attachment in
Filament is not implemented. Browser shader packages perform their own sRGB decoding.
Mip generation explicitly sets the flag documented by [Filament 1.75's texture usage
contract](https://github.com/google/filament/blob/v1.75.0/filament/backend/include/backend/DriverEnums.h).

Asset paths preserve the application's URL directory layout. Add
`'-PapplicationResources=game/assets'` to a web build to copy that directory's contents
to the distribution root. Files referenced by `assets/image.png`, for example, must
be packaged at `dist/assets/image.png`. The compatibility fixtures include their own
small PNG. There is no automatic discovery of arbitrary desktop filesystem paths.

The same `applicationResources` directory is now on both targets' classpaths.
Engine and declared application resources are registered with TeaVM, preserving
`Class.getResourceAsStream`, `ValthorneFiles` reads/existence checks and resource
extraction. Temporary files use TeaVM's in-memory filesystem, and image/audio/font
loaders can read those paths. Temporary files do not persist across reloads. Classpath
resources are embedded in the JavaScript build, so very large resource packs increase
download and memory costs; ordinary URL-based loading remains available through the
existing path overloads.

`CommonGraphicsApplication` runs unchanged on desktop and web. It tests image path
and byte decoding, flipped pixels, alpha, all texture filters, shader reload and
failed-reload cleanup, projection matrices, sprites, clipping, framebuffer blits,
nine-patches, culling and disposal. Browser tests check actual RGBA output, not just
draw counts. It also reports submission timing for the base graphics scene,
2,000 visible sprites and 2,000 culled sprites, with 20 warm-up and 60 measured frames
per scenario. No explicit GPU-completion wait is inserted. Driver stalls can still appear in
these timings; they are not isolated CPU or GPU execution measurements. The fixture
requests VSync off on desktop; browsers continue scheduling with animation frames.

## Existing physics and 3D API backends

The Jolt backend supports independent worlds, all six collision geometry paths
(box, sphere, Z-axis capsule/cylinder, convex hull, static triangle mesh), body
settings, forces/impulses/velocities, sensors, 16 collision layers, deferred contact
events, fixed stepping, interpolation, model binding, ray hits and distance joints.
Destroyed handles and cross-world misuse retain their Java validation. Settings
capacities are used by Jolt; all worlds share the loaded WASM runtime, but have
separate simulations. Browser execution is single-threaded regardless of the
worker-count performance hint. The selected binding's `Step` returns no capacity
error flags, unlike the desktop binding. WASM memory exhaustion is still possible.

The Filament backend draws immutable triangle models and nested scene nodes with
their original transforms, vertex colors and normals. It compacts vertex uploads,
caches meshes and material/transform state, releases unused resources, and reuses
the original conservative occlusion culler. Shadow and emission contributors remain
in secondary passes. Explicit point lights are editable, transparent materials use
Filament's fade shader, and emissive materials can create real lights. Quality,
exposure and environment settings use native Filament controls. Ambient occlusion
uses explicit filtering to avoid the noisy unfiltered browser result.

`SceneRenderer3D.AUTO` chooses Filament. Explicit desktop `RASTER`, legacy
`ModelBatch3D`, imported model subclasses, and arbitrary
custom renderables are not mapped yet. Those paths are not silently approximated.
The glass shader is included, but glass/refraction has not received the same visual
regression coverage as opaque and translucent triangles. Environment lighting uses
a simple diffuse SH environment; desktop HDR reflections are not reproduced.
Profiling getters currently retain their default zero values in the browser.

The browser `engine-*.filamat` packages compile the existing shared shader sources
using `matc` 1.75.0 with `-p mobile -a opengl`. `webDist` verifies their source and
binary hashes against `web/engine-materials.json`; shader edits require recompiling
the matching package and updating that manifest. Source SHA-256 hashes use UTF-8
with LF line endings; binary hashes cover the exact package bytes. This keeps
validation consistent across Windows, Linux and macOS checkouts without ignoring
shader edits. Builds do not require a locally
installed shader compiler while the checked-in packages match their sources.

JOML remains version 1.10.8. The build compiles narrowly patched upstream sources
with their original licenses: it selects JOML's standard non-FMA and NIO paths,
and spells out the negative number-format pattern for TeaVM. No vector or matrix
API is replaced. Native-address methods still cannot operate in a browser.

Run the consolidated unchanged-source suite from the repository root:

```powershell
node portable/web/verify-compatibility.mjs --desktop
```

Omit `--desktop` for browser-only verification. The suite starts its own local
server on an available port, builds each application, runs it, and restores the
normal lab/arena distribution afterward. `CommonPhysicsApplication` covers 196
checks, including original mesh particles with physics and attached lights.
`CommonSceneApplication` runs against both backends; Chrome additionally verifies
rendered pixels change when a light or texture is disabled, 2D overlays on 3D,
culling, mesh sharing and teardown.
The screenshots are in `web/build/verification/`. This does not prove full-engine
portability or pixel-identical rendering across platforms.

The Linux browser CI runs shared-source/browser checks and the full FPS check in
independent parallel jobs. Both set `WEBGPU_SOFTWARE=1` to select Chrome's software GPU.
Frame-based graphics checks allow up to two minutes while the application makes
progress, but fail after ten seconds without progress. Their timing reports record
frame counts and distinguish a stalled loop, an application error and an overall
timeout. The model check still requires all 180 frames and its resource cleanup.
Arena and FPS input checks wait for rendered frames or simulated physics time;
slow rendering must not shorten a movement, reload or grenade-fuse check. Software
FPS checks select the engine's Performance preset at 1200×800, keep all menu
controls visible, and allow up to five minutes for each simulation-time condition.
Normal FPS checks retain the game's High preset at 1600×960. Both exercise the
same gameplay, lights, shadows and resource ownership assertions. The software
verifier allows five minutes for a simulation condition because a SwiftShader
grenade burst can block one Filament frame for several minutes; hardware retains
the shorter deadline.
Each entry, restart and resume must acquire mouse capture. Repeated capture
requests are spaced to respect browser rate limits; failure reports include
activation, focus and capture events alongside the current gameplay phase.
Software runs collect 60 timing samples per phase; normal runs collect
360. Software GPU frame timings are diagnostic results, not hardware performance
benchmarks.

`npm run benchmark:physics` measures the WASM bridge without a renderer or Java
overhead. On this Windows machine with Node 24.14, 1,200 steps with 96 active bodies
averaged about 0.31 ms (0.47 ms p95); 32 bodies averaged 0.073 ms. Bodies receive
periodic impulses so the test does not time an entirely sleeping world. A separate
100-cycle shape/body/joint/world teardown check retained zero WASM allocator bytes
after warm-up. These are local microbenchmarks, not mobile/Pi performance evidence.

## What is shared

The core build directly compiles selected original sources under
`src/main/java`, with Java 17 bytecode and no native dependencies:

- `Application` lifecycle, `Tick`, `MathUtils`, the state-machine package and all
  34 existing collection classes (arrays, stacks, queues, maps, tree and bits).
- `FrameLoop`: bounded fixed-step updates, pause/resume, and cleanup.
- `AssetService` / `ModelAsset`: asynchronous GLB loading, cancellation, owned model
  handles, transforms and animation controls.
- `PlatformServices`: keyboard/mouse input, pointer capture, settings, tones and 2D drawing.
- `MediaService`: cancellable raster-image and sampled-sound loads with owned handles.
- `InteractiveScene` / `PhysicsBody`: owned Jolt bodies, velocity/impulse/position,
  first-person cameras, closest-hit raycasts and particle bursts.
- `SceneBackend`: a small interface for boxes, one editable point light,
  rendering, stepping physics, and clearing owned objects.

The browser test exercises Application, Tick, MathUtils and FrameLoop; the state
package compiles into the portable core but does not yet have browser-specific
behavioral coverage. This is an explicit source allowlist, not evidence that other
desktop packages are portable. Do not combine the portable-core JAR with the
desktop JAR: both contain the shared source classes.

`WebSceneBackend` implements the small interface with Filament/Jolt. `WebRuntime`
drives Java applications with requestAnimationFrame and a 60 Hz fixed update step,
at most five updates per displayed frame. It drops excess elapsed time after long
stalls, clears partial time on pause transitions, and resets wall-clock input when
the tab's visibility changes. Paused scenes still render. Graphics context loss
stops the loop and requests a page reload.

`PhysicsPlayground.java` owns scene setup and controls; `host.js` owns browser input,
native resources, shaders, presentation and DOM UI. New Java applications can use
`WebSceneBackend` and `WebRuntime.start`, and select their entry point with
`-PwebMainClass=your.package.Main`. Extend or replace the supplied host's input/UI
bindings for a different game. The backend assumes an initialized browser host
and currently supports one application per page.

## Supported and outstanding

| Area | Browser status |
| --- | --- |
| Java application lifecycle | Running through TeaVM |
| 3D | Original Scene3D, meshes, cameras, PBR materials and Filament; raster meshes, billboards, light buffers and cached shadow maps |
| Physics | Original Jolt APIs: six shape types, bodies, forces/impulses, distance joints, contacts, layers, queries, sleeping and teardown; 196 shared checks |
| Input | Keyboard, mouse buttons, pointer lock, mouse/touch orbit, wheel zoom, focus reset; gamepads and phone execution outstanding |
| UI | Original Yoga, texture, Nano and SlugLabel APIs supported; full IME/mobile coverage outstanding |
| Desktop Scene3D / PhysicsWorld3D API | Original APIs supported by the compatibility backend; the earlier demo API remains separate |
| Models | Original OBJ/MTL importing with relative textures; demo API also loads self-contained GLB 2.0, with the shadow-receiver limitation described above |
| Animation | Clip controls, node animation, two-bone skinning and morph deformation validated against rendered pixels, interpolation, rewind and repeated resource disposal |
| Particles | Original 2D particles and draw budgets; original 3D emitter and physics/light integration; separate demo caps at 64 particles / 8 lights |
| 2D | Original batching, atlases, shapes, shaders, framebuffers, fonts, Tiled, HDR/legacy lighting, sprite lighting and fragment-based radiance cascades |
| Asset manager | Original API, cooperative futures, prepared batches, caches, custom loaders and disposal |
| Audio | Original SoundPlayer controls and attenuation; bounded WAV/MP3/Vorbis streams with seeking and teardown |
| Storage | Java file streams, Reader.transferTo, random access and common Files operations backed by persistent IndexedDB; 16 MiB/file and 64 MiB resident file capacity |
| Compute / path tracing | Original ComputeShader API through WebGPU; original PathTracer3D and PathTracer2D algorithms through WebGL2 float MRT passes |
| Existing FPS game | Original arena source and model resources run on desktop and web, including enemies, grenades, particle lights and custom UI; use `node portable/fps.mjs run web` |
| Android / Raspberry Pi | Not validated or claimed supported by this pass |

The requested compute/path-tracing, bounded audio, window/file and skeletal-animation
work is implemented. The original desktop FPS gameplay and imported assets are
also migrated. Remaining broader project work includes mobile input coverage and validation
on additional browsers/devices. Android needs a separate launcher and native
adapters; its SDK is not present here. This is not a claim that arbitrary desktop
native code or every Java SE filesystem operation can run inside a browser.

## Compute, path tracing and files

`ComputeShader` compiles GLSL through pinned glslang and Tint builds to real
WebGPU pipelines. Storage buffers stay on the GPU; shared memory and workgroup
barriers execute there. Image interoperability copies between WebGPU and WebGL,
including asynchronous readback, so it has a substantial transfer cost. Supported
image formats are rgba8, rgba32f, rgba32ui and rgba32i; unsupported shader features
fail explicitly. Loose scalar/vector/matrix uniforms are packed into a uniform
buffer. Read/write images use storage buffers to preserve reads after writes with
the pinned Tint compiler. Uniform arrays, arbitrary native GL interop and user-provided uniform
blocks, aliased image bindings and standalone GLSL memory-barrier intrinsics are
outside this adapter. Workgroup `barrier()` and the Java dispatch/barrier methods
are supported. WebGPU requires a supported browser/device and
a secure context (localhost qualifies); lack of WebGPU does not prevent raster
or Filament startup. `deleteSSBO` is available on both targets for buffer cleanup.

`PathTracer3D` and `PathTracer2D` share the desktop BVH, light transport, temporal
history and denoising code. Their independent-pixel kernels run as fullscreen
WebGL2 passes with float attachments, without WebGPU readback. Float render-target
and filtering extensions, four draw buffers and twelve texture units are required.
Texture atlases are resampled on the GPU into 512×512 layers. Denoising writes
destinations directly; tracing retains the copies needed for conditional history
writes. Shared tests cover textured/emissive scenes, progressive/realtime modes,
sample budgets, camera reset, scene rebuilds, 2D presentation and cleanup.

Java `File`, stream and common `Files` operations use an origin-private IndexedDB
filesystem with a bounded memory mirror. Flush/close waits for transaction commit;
failed commits propagate to Java. Tests cover reopen/reload persistence, sparse
random access, truncate, directory moves, open-handle rename/unlink and quota
failure. `/tmp` is session-only; `/home` is persistent. This does not grant access
to the user's OS filesystem. Browser storage can be evicted or cleared, and
simultaneous writers in multiple tabs are not coordinated. Packaged read-only
resources continue through the existing resource loader.

Run `node web/verify-compatibility.mjs --desktop` from this directory for the
shared fixtures, and `node web/verify-examples.mjs` for the six browser scenarios.
Both runners start an isolated local server; the compatibility runner restores
the normal demo distribution afterward. Reports are in `web/build/verification`.

## Reproducible dependencies

TeaVM is pinned to 0.15.0; npm's lockfile pins Jolt Physics 1.1.0. The build
fetches the official Filament 1.75.0 browser archive and verifies its SHA-256
before extracting the runtime. It caches the archive under `web/build/runtime`.
This matches the desktop Filament version. `lit.filamat` was compiled from
`lit.mat` with the official version 1.75.0 compiler:

```text
matc -p mobile -a opengl -o public/lit.filamat public/lit.mat
```

Compiler/runtime: [official release](https://github.com/google/filament/releases/tag/v1.75.0).
Runtime licenses are in `web/public/licenses/` and copied into the distribution.
See [TeaVM](https://teavm.org/docs/tooling/gradle.html),
[Filament web](https://github.com/google/filament/tree/main/web), and
[Jolt WebAssembly and ownership](https://github.com/jrouwe/JoltPhysics.js).

## Model loading and ownership

`WebAssets` implements the shared `AssetService`. Serve self-contained GLB files
from the same origin; external texture/buffer URIs are rejected. Loading returns
a cancellable request. Completion transfers an attached `ModelAsset` handle to
the listener. Cancel before completion delivers one `failed("cancelled")` callback;
cancel after completion does nothing. Close the model to release it. Clearing the
scene cancels pending requests and invalidates all its handles. Closing is idempotent.
Transforms use Y-up metres, yaw in radians and a positive uniform scale.
Imported visuals do not automatically acquire Jolt colliders.

Fetching is asynchronous, but native decoding runs synchronously on the browser
render thread and can stall a frame. Limits are eight pending/loaded models,
8 MiB source per model, a 15-second fetch timeout, 2,048 nodes, 512 meshes and
one million elements per accessor. These are source/complexity limits, **not a
complete decoded texture, GPU or process-memory budget**. The source-byte counter
records input sizes for live models; source buffers are released after decoding.

Imported renderables currently **cast but do not receive shadows**. Receiving
shadows with imported ubershaders caused WebGL context loss on the tested Windows
hardware path, including with a known-good Khronos model. This restriction keeps
hardware rendering, direct lighting and shadows on the primitive floor working.
It remains a rendering limitation to resolve before migrating a complete game.

The playground includes CC0 Kenney Nature Kit trees and a tent. Their license and
hash provenance are bundled. `webDist` validates the shipped GLBs with the Khronos
validator and checks hashes. `node import-models.mjs` regenerates them when the
original local Nature Kit assets are available: it repairs the exporter's invalid
scene root and sets nonmetallic materials, leaving source files untouched.

## Platform, media and effects

`WebPlatform` implements the portable input, settings and overlay interfaces.
Input resets on blur, visibility loss and pointer-lock changes. Audio unlock is
triggered by a real keyboard or pointer interaction. Local settings use a
`valthorne:` prefix; storage denial/quota errors are exposed to the caller.

`WebMedia` loads same-origin PNG/JPEG/WebP images and browser-supported audio.
Requests are cancellable; decode completion after cancellation is discarded.
Close image/sound handles when no longer needed; scene resets intentionally keep
media alive, and host shutdown disposes it. Limits: 32 assets/requests, 8 MiB
encoded input each, 32 MiB decoded each and 64 MiB total retained decoded data.
These checks run after browser decoding and do not bound transient decoder memory.
Sound playback shares a 32-voice budget with tones. Closing a sound stops its voices.
The included crosshair and impact WAV are procedurally authored sample assets.

The particle renderer shares geometry and at most eight transparent material
instances, with a fixed color palette and eight attached lights. It supports
ballistic visual particles and actual Jolt particles; neither mode gives light
to the shot itself. Particle lifetimes use fixed simulation updates. Primitive
materials are reference-counted and released with their final body. Filament's
renderable bounds provide native frustum culling; this is not the desktop engine's
occlusion/LOD/streaming implementation.

[`FirstPersonDemo`](fixtures/java/valthorne/portable/FirstPersonDemo.java) is a
browser integration fixture compiled by TeaVM from `portable/fixtures/java`.
Its gameplay code is excluded from the desktop and portable core library,
sources and Javadoc artifacts. The original desktop `FpsArena` is a separate build selected
with `node portable/fps.mjs run web` from the repository root.
It uses a rotation-locked rigid box for the player, not Jolt's full character
controller. In particular, stairs/slopes and dynamic platforms need more work.

## Validation and measurements

With the local server running, execute `npm run verify` in `portable/web`.
Alternatively, `npm run verify:examples` starts an isolated local test server and
stops it after both demo suites finish.
The browser test uses installed Chrome (set `BROWSER_CHANNEL=msedge` for Edge).
Both physics-lab and first-person browser suites run. They check loading, Java-driven physics and floor contact, pause/resume, spawning,
camera movement, zoom, color editing, 12 rebuild cycles, live resource counts,
mobile-sized layout capture and explicit native teardown. Asset checks cover
cancellation, missing files, model-budget rejection, transforms, idempotent release
and stale handles. Further checks verify node animation, settings, 2D alpha pixels,
keyboard focus reset, audio cleanup, Jolt body handles/rays, pointer capture,
first-person movement, reload and exact target hits, media cancellation, particle
limits and teardown. Unhandled browser errors fail the test.
Screenshots and JSON measurements are written under `portable/web/build/`.

Measured here on Windows/Chrome at 1280×800: 26 bodies including the floor,
three primitive material instances plus three imported models after rebuilds,
approximately 8.33 ms mean /
8.70 ms p95 animation-frame interval across 180 sampled frames. This is a short,
refresh-rate-limited browser measurement, **not GPU execution time**, a comparison
with the desktop FPS workload, or evidence of mobile/Pi performance.

Jolt's WASM linear memory was 128 MiB. This excludes Filament, JavaScript, GPU
resources and browser overhead; it is not total application memory. The distribution bundles the runtime and models; use HTTP compression in deployment.
Long-session leak tests, other browsers and physical mobile hardware remain needed.

Five portable-core tests (lifecycle and shared collections) passed.
The collection package is compiled into the portable core; these tests run on the
JVM and do not establish full TeaVM behavioral coverage for all 34 classes. The desktop build, native Jolt tests
and 1,437 FPS gameplay checks also passed. Existing optional test/example sources
remain excluded from publication by the repository's ignore/release rules.
# Optional physics runtime and memory profiling

Application web exports default to physics enabled. A document-only application
can pass `-PwebPhysics=false` to `:web:webExport` to omit Jolt initialization and
its WebAssembly heap. This is an explicit capability opt-out, not lazy physics:
attempts to create physics bodies/worlds in that export report an error. Normal
game exports need no changes. The exporter supplies the browser configuration.

Append `?profileMemory` to an exported application URL to log five-second samples
of JS heap estimates, available WASM heap capacities and live backend resource
counts. These diagnostics are opt-in and do not measure total GPU/process RAM.
Run `node --test web/physics-runtime.test.mjs` from this directory to check the
runtime selection and its enabled-by-default behavior.
