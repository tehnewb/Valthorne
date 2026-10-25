# Windows bgfx runtime

The bundled x64 and ARM64 DLLs match LWJGL 3.4.3's bgfx API 156. Their bgfx
source commit is the revision recorded in LWJGL's original Windows native JAR:
`780f7bf00a29573473b90dcfab9f92a7d8687702` in [LWJGL-CI/bgfx](https://github.com/LWJGL-CI/bgfx).
The bx and bimg revisions and resulting SHA-256 hashes are in `provenance.json`.
The same provenance is packaged as `META-INF/valthorne-bgfx.json`.

## Correction

In `RendererContextD3D12::requestScreenShot`, use `m_backBufferColorIdx` instead
of `(m_backBufferColorIdx-1) % m_scd.bufferCount`. bgfx runs screenshot requests
after render submission and before presentation. The old expression therefore
reads the previous swapchain buffer: captures lag by one presented frame and are
black before the first presentation or immediately after resizing.

Vulkan's screenshot routine also resolves the selected framebuffer before reading
its swapchain image, matching bgfx's existing video-capture path. Without that
resolve, MSAA screenshots can contain old or black pixels even after rendering.

The existing fence waits and readbacks retain their normal synchronization. No
extra frame, presentation, or fullscreen copy is added; the native corrections
run only when a screenshot is requested. Java backbuffer bookkeeping also tracks
the current dimensions when capture resumes an active frame after resizing.
`build.mjs` applies these screenshot corrections to generated files and
rejects an unexpected upstream function layout. Dependency checkouts stay clean.

## Rebuilding

Ordinary Gradle builds package the checked-in DLLs and do not run native tools.
To rebuild, install Node.js, Git, CMake 3.20 or later, Ninja, and the extracted
[LLVM-MinGW 20261020 UCRT toolchain](https://github.com/mstorsjo/llvm-mingw/releases/tag/20261020)
(Clang 23.1.4). Its Windows x64 host archive is
`llvm-mingw-20261020-ucrt-x86_64.zip`, SHA-256:

```text
fd5462b7f5d93b2ff981fcd3f9c7e427e47f3203c64ea91c27bcc444eebd74cd
```

From the repository root:

```text
node native/bgfx/build.mjs <extracted-llvm-mingw-directory>
```

Set `CMAKE` or `NINJA` to an executable path if it is outside `PATH`. On Windows,
the pinned bx checkout supplies Ninja by default. An optional second argument
selects an existing dependency directory containing `bgfx`, `bx`, and `bimg`.
The script fetches exact commits, builds both target architectures, and refreshes
DLLs, licenses, loader metadata, and provenance. Compiler paths are removed from
diagnostic strings, PE timestamps are disabled, and compilation dates use the
pinned bgfx commit's timestamp through `SOURCE_DATE_EPOCH`.

## Loading and qualification

Windows engine initialization selects the distinct `valthorne-bgfx.dll` resource
name before LWJGL loads bgfx. Early initialization of the shared batch and shape
renderer classes does the same. LWJGL owns extraction and native library lifetime.
Non-Windows platforms and the browser retain their existing runtimes. Browser
artifacts exclude the desktop DLLs and their loader metadata.

An explicit `org.lwjgl.bgfx.libname` setting is preserved. Applications that load
bgfx directly before initializing Valthorne must select the corrected library
before their first bgfx reference; changing the property cannot replace already
initialized LWJGL bindings. An earlier default binding initialization is rejected
with a configuration error. A custom runtime remains the application's responsibility.

Windows x64 is exercised with live GPU capture checks. The ARM64 artifact is
cross-compiled and inspected; executing it requires Windows ARM64 hardware.
