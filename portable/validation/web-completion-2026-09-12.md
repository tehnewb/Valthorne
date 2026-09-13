# Web backend completion — September 12, 2026

The requested compute/path-tracing, bounded audio, window/file operations and
skeletal-animation validation are implemented. Jolt remains the physics backend.
Application code uses the existing Java APIs; backend selection happens at build time.

## Validation

| Check | Result |
| --- | --- |
| Shared applications | 22 fixtures passed on both native desktop and browser, including expected-failure handling |
| Declared backend API | 104/104 classes; 1,804 public members match |
| Browser scenarios | All six passed: lab, FPS arena, incremental audio, skeletal/morph deformation, WebGPU compute, platform services |
| Desktop regression | `build verifyPhysics3D verifyFpsArena` passed; 1,437 FPS gameplay checks |
| Portable core | Five tests; zero failures/errors |
| Jolt lifetime | 100 create/destroy cycles; zero retained WASM allocator bytes |

The final audio metadata and compute cleanup changes were rechecked through the
same-source desktop/browser audio and compute fixtures, followed by all six
browser scenarios. Packaging restores the normal lab/arena distribution.

Compute validation executes shared-memory workgroup barriers, storage-buffer
reads/writes, RGBA8 output, floating-point reads after writes, and signed/unsigned
integer image updates. It rejects an overflowing upload and returns program/buffer
counts to zero. Read/write images use storage buffers internally because the
pinned Tint SPIR-V reader does not accept read/write storage-image declarations.

Path-tracing tests use the original BVH and shaders in progressive and realtime
modes, textured and emissive geometry, camera accumulation resets, scene edits,
and the original 2D wrapper. Both renderers release all tracked resources and
produce no GL errors. Desktop shader edits make numeric literal types explicit
for GLSL ES; they preserve the light-transport algorithm.

Audio's 42 shared checks cover long and short MP3/Vorbis from paths and encoded
arrays, WAV, seek, buffering, playback controls and teardown. Browser output was
measured with a Web Audio analyser (peak amplitude 0.0633). The native MP3 provider
estimates this fixture's duration as 34.482 seconds; the shared test accepts its
existing estimate, while the browser decoder separately verifies the actual
60-second PCM duration. This work does not fix native MP3 metadata estimation.
A native Ogg guard now rejects heap/read-only buffers before entering STB.

File tests cover persistence after a page reload, directory moves, random-access
and sparse writes, truncation, reader transfer, open-handle rename/unlink, and
commit/quota failure. Fullscreen is tested from a real click after a deferred
startup request. Window/icon/cursor cleanup is verified.

Skeletal and morph GLBs pass the Khronos validator. Rendered vertices move
approximately 120.75 pixels at the upper edge while the mesh node's transform
remains unchanged. Midpoint interpolation and rewind are checked, followed by
ten model load/animate/release cycles returning to the asset baseline. The
captured images were also visually inspected.

## Measurements

Local Windows 11, Ryzen 9 7945HX, Java 25.0.2, Node 24.14, hardware-accelerated
Chrome. These are local regression measurements, not minimum-hardware guarantees.

| Measurement | Before | After |
| --- | ---: | ---: |
| Path trace CPU submission mean, 24 warmed realtime samples | 1.471 ms | 1.371 ms |
| Path trace CPU submission p95 | 1.900 ms | 1.700 ms |
| Denoiser destination copies per rendered frame | 6 | 0 |
| Extra denoiser copy textures | 2 | 0 |

The scene renders to 256×192 with the interactive resolution scale. Timing excludes
shader startup and test readback frames. Native CPU submission for the baseline
was 0.488 ms. These measurements include driver submission/stalls, not isolated
GPU execution. A later full-suite browser sample was 1.067 ms, illustrating
run-to-run variation; the small timing difference is not proof of a fixed
percentage speedup. Removing the copies is the deterministic reduction in work.

| 60-second stereo stream | Decoded PCM size | Peak tracked PCM working storage | Peak fetched chunk | Probe/read/seek validation elapsed |
| --- | ---: | ---: | ---: | ---: |
| MP3 | 5,292,000 B | 1,368,764 B | 65,536 B | 277.5 ms |
| Vorbis | 5,292,000 B | 1,439,744 B | 65,536 B | 326.0 ms |

PCM accounting includes the bounded prefix retained while deciding whether to
buffer a short sound. It does not measure total browser memory, codec WASM heaps,
network-internal buffers, or caller-owned encoded arrays. No decoder remained
active after the streaming and repeated teardown tests.

Jolt step-and-pose-read benchmark: 1,200 steps, 96 active bodies, median 0.322 ms,
p95 0.563 ms. This Node WASM benchmark excludes rendering and Java bridge cost.
The browser lab and small arena had mean frame intervals of approximately 8.33 ms
and 8.32 ms at 1280×800; neither is a demanding production-game benchmark.

## Boundaries

- General compute requires WebGPU; path tracing requires WebGL2 float render
  targets/filtering, four attachments and twelve texture units. Compute texture
  interop transfers through CPU readback; the path-tracing backend does not.
- The compute adapter supports the tested storage-buffer/image and uniform
  patterns. Uniform arrays/blocks, aliased image bindings, standalone GLSL
  memory-barrier intrinsics and arbitrary native GL interop remain unsupported
  and fail explicitly. Workgroup `barrier()` and Java dispatch/barrier methods work.
- Compressed metadata probing and seeking scan/decode with bounded storage; they
  are not constant-time. Explicit full-decode APIs intentionally allocate full PCM.
- Window operations control the game surface in the page. OS pointer warping,
  native handles and control over browser frame scheduling cannot be supplied.
  Fullscreen, pointer lock and audio obey browser gesture requirements.
- Persistent files belong to the origin, with 16 MiB/file and 64 MiB resident file
  capacity; `/tmp` is session-only. Browser eviction and concurrent writers in
  multiple tabs are outside the durability/coherency guarantee.
- Hosted Linux CI is configured but has not run in this session. An extra local
  SwiftShader WebGPU attempt could not obtain a software adapter from the installed
  Windows Chrome; the hardware WebGPU tests passed. No software-GPU or Linux
  execution success is claimed. Android, Raspberry Pi and other browsers remain
  unvalidated.

## Reproduce

From the repository root, with Java 25 and the pinned npm dependencies installed:

```text
node portable/web/verify-compatibility.mjs --desktop
node portable/web/verify-examples.mjs
gradlew.bat build verifyPhysics3D verifyFpsArena
gradlew.bat -p portable :core:test
```

The browser runners start their own local servers. Machine-readable results and
framebuffer captures are written to `portable/web/build/verification/`. New runtime
licenses and source provenance are packaged under `web/public/licenses/`.
