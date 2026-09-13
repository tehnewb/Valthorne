# Filament platform expansion — 2026-09-13

This is an implementation milestone, not a declaration of all-platform support.

The desktop artifact now packages the pinned FFM runtimes for Windows x64,
Linux x64, Linux ARM64 and macOS ARM64. Capability selection recognizes only
those published targets. Windows retains shared OpenGL textures when immutable
storage is available. Other packaged targets use an independent OpenGL Filament
context, cached material uploads, a reusable native output buffer and a GL
presentation texture. `invalidate()` refreshes copied material pixels.

The transfer path avoids WGL calls, restores pixel pack/unpack state, releases
output storage on resize/close and corrects Filament readback's vertical origin.
It requires an OpenGL 4.1 presentation context. The shared scene fixture and
local FPS now request 4.1 rather than 4.3, which macOS cannot provide.

Validation on Windows / RTX 5070:

- Platform-selection unit test passed, including rejection of unavailable native architectures.
- Native Jolt and 1,437 FPS gameplay checks passed.
- FPS smoke passed with direct sharing and forced readback; the corrected readback image was inspected.
- Shared `CommonSceneApplication` completed using forced readback.
- Short 1600 × 960 combat benchmark: direct-sharing render mean 8.128 ms,
  p95 11.424 ms; readback render mean 9.957 ms, p95 12.899 ms. The readback
  measurement preceded the final vertical-blit correction. These are local,
  single-run submission/completion timings, not cross-device performance claims.

Use `-Dvalthorne.filament.readback=true` on the application JVM to exercise the
transfer path on Windows. This is a diagnostic override; other packaged desktop
platforms choose the path automatically. No gameplay branch is required.

The desktop workflow automatically runs committed platform-selection and native
physics compatibility checks on Windows, Linux and Apple Silicon macOS. It writes
a report of the executed cases and assertions without requiring local test folders.
The shared Filament scene remains a manually dispatched graphics check because
hosted runners do not guarantee accelerated OpenGL/NSGL. Native graphics
initialization and image orientation still need verification on Linux/macOS;
Windows testing cannot prove those backends.

Remaining work:

- Native Linux x64/ARM64 and macOS ARM64 execution, image assertions and profiling.
- A GPU-only transfer path where platform APIs permit it, avoiding per-frame readback.
- Runtime builds/integration for Intel macOS, Windows ARM64 and any other desired architecture.
- Android and iOS launchers, lifecycle/input/audio/storage/rendering adapters,
  packaging and device tests. The Java 25 FFM desktop runtime is not an Android adapter.
- Existing browser compatibility gaps, including WebKit pointer capture and actual Safari testing.

Jolt remains the physics system. The available desktop Jolt artifacts and the
browser WebAssembly implementation are unchanged.
