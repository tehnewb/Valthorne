# Graphics capability selection

Valthorne supports a desktop OpenGL 3.3 raster baseline and optional higher-level
paths. `automaticContext()` tries desktop core contexts in order: 4.3, 4.1, 3.3.
An explicit `contextVersion(major, minor)` restores strict creation. Defaults remain
strict 3.3, so existing applications do not silently change their requirements.

```java
JGL.init(app, JGLConfiguration.defaults().automaticContext());
// In Application.init(), with the context current:
System.out.println(Window.getGraphicsCapabilities());
SceneRenderer3D renderer = new SceneRenderer3D();
// In Application.render():
renderer.render(scene, camera);
// In Application.dispose(), before destroying the context:
renderer.close();
```

`SceneRenderer3D.Backend.AUTO` selects Filament when the current adapter's platform
and graphics prerequisites exist, otherwise `RASTER`. Explicit backend requests
fail if unsupported. Asset, shader and native loading failures remain errors;
they do not silently downgrade quality. The selected backend is available through
`getBackend()`. The facade selects once, owns rendering resources, and leaves source
models, textures and Jolt physics under the application's ownership.

| Path | Requirement | Behavior |
| --- | --- | --- |
| Raster | Desktop OpenGL 3.3 / GLSL 330 | ModelBatch3D plus tiled Lighting3D; scene point lights, emission and transparency |
| Filament adapter | Windows x64, desktop OpenGL 4.1 and immutable texture storage (4.2 or ARB extension) | Existing shared-context PBR renderer; runtime/material loading must also succeed |
| Built-in path tracer | Desktop OpenGL 4.3 / GLSL 430 | Explicit opt-in through PathTracer3D; never selected automatically for slower hardware |
| Generic compute wrapper | Compute, shader-storage and image capabilities | Custom shader source must fit the context's GLSL version |
| OpenGL ES / Raspberry Pi | Separate backend work remains | No support claim; ES is rejected before desktop calls are used |

Raster fallback changes the image. It does not emulate Filament's image-based
lighting, screen-space glass or point-light shadows. Use `rasterState()` to configure
fog, environment lighting through its Lighting3D, and a caller-owned directional
shadow map. The facade supplies its owned lighting system and current scene light
membership each frame. Use `filament()` for settings when Filament is selected.
No automatic reduction in resolution, light count or model detail is applied.

GLFW requests are minimum versions. A 3.3 request can return 4.6; neither that request
nor an extension list establishes performance or complete older-driver compatibility.
`GraphicsCapabilities.current()` reads actual LWJGL capability flags and driver
identification. Compute probing no longer queries `GL_EXTENSIONS` through the legacy
string API, which is invalid in core contexts. Queries require a current context and
do not change GL error state. Window reports are cleared on teardown and failed setup.

## Raspberry Pi work still required

Upstream Filament supports Linux and OpenGL ES, and its community FFM project lists
Linux ARM64 runtimes. Valthorne still packages the Windows runtime and calls WGL for
context sharing. Adding another runtime alone does not port that sharing boundary.
The raster, texture, UI and window paths also use desktop OpenGL bindings and GLSL
330; changing GLFW's client API is insufficient.

The next portability milestone is an ARM64 Linux executable that opens a hardware
accelerated ES/Vulkan context, renders one textured lit object, presents UI, and steps
Jolt. It needs compatible native packaging, shader variants and presentation/resource
synchronization. Only then should the full FPS workload be used to set resolution,
shadow, particle, texture-memory and streaming budgets on an identified Pi model.
No Pi or minimum-memory configuration has been provided for testing yet.

Sources: [GLFW context requirements](https://www.glfw.org/docs/latest/context_guide.html),
[Filament platforms](https://github.com/google/filament),
[FFM native packaging](https://github.com/Erkko68/filament-kmp/blob/main/java/README.md).

## Verification in this pass

Pure tests cover renderer selection, unsupported explicit requests and configuration
precedence. Native tests cover automatic and explicit 3.3/4.1/4.3 requests, actual
capability reports, visible raster framebuffer output, light removal, error-free
compute probes, failed creation recovery, and report cleanup on teardown.

The Windows RTX 5070 driver reported 3.3, 4.1 and 4.3 for their respective explicit
requests, and 4.3 for automatic selection. These are real native tests with those
reported versions, but the driver may expose additional extensions; they do not
establish support on Linux, macOS, OpenGL ES or a Raspberry Pi. Negotiation failure of the preferred 4.3
request needs hardware/driver coverage where that request is unsupported.

The existing 1600×960 HIGH scripted combat benchmark passed with Jolt, 104 peak
particles and 108 peak lights. One earlier run measured 6.297 ms render / 0.356 ms
simulation; a later run after this pass measured 7.819 / 0.527 ms. These separated
runs do not isolate causality or establish a speedup/regression. The existing FPS
still uses Filament directly; this pass adds startup checks and a new opt-in facade,
not a rendering optimization. Local logs: `.codex-temp/fps-baseline-current.log`,
`.codex-temp/fps-capabilities-current.log`, `.codex-temp/capabilities-validation.log`.
