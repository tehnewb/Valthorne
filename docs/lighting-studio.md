# Lighting Studio

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run demo launch tasks there; the engine's local
`src/examples/` files remain ignored and excluded from library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions.

On Windows x64 the studio defaults to **Filament / real-time PBR**. Other supported
OpenGL 4.3 desktop hosts use the path tracer and omit the Filament choice. See
[Filament integration](filament.md) for its lighting contract and
[platform requirements](platforms.md). This studio cannot run on macOS OpenGL;
use `run3DExample` or `runPhysics3DExample` for the portable raster path.

Run `./gradlew runLightingStudio`. The companion project launches a separate progressive
path-tracing scene with `runPathTracingExample`; it no longer aliases this workbench.

The workbench uses Valthorne's themed UI, including scrollable properties, dropdowns, keyboard-focusable buttons and sliders. It renders a material gallery with glass, gold, roughness samples and colored walls. The central viewport is separate from the UI, so UI input does not move the camera.

## Navigation and editing

- Right-drag: orbit the 3D camera. Middle-drag: pan. Mouse wheel: zoom.
- **3D / Top view** switches projection. Middle-drag and wheel navigate the overhead view.
- Click a visible emitter to select it, or use the light dropdown (including disabled lights).
- Left-drag a selected emitter across its horizontal plane. Shift-drag changes height.
- **Place colored light**, then click a surface, creates a light above it. Escape cancels placement.
- Choose a palette color or edit RGB channels. Power, source radius, X/Y/height and sphere/panel shape are editable.
- Enable/disable, duplicate and delete lights. Undo/redo stores up to 64 light-rig states, coalescing slider gestures.
- **Save rig / Load rig** use `build/lighting-studio/rig.properties`. Loading validates the complete rig before applying it. Camera/render settings are not saved.
- Material studio, neon gallery and 64-light stress presets provide different workloads. New rigs are capped at 256 lights.
- **Realtime / temporal GI** is the default. It renders at full viewport resolution and reuses valid lighting history while navigating. **Progressive / bounced light** is the original stationary accumulation mode.
- Interactive/High/Ultra controls samples and bounce depth; progressive mode also changes internal resolution. Exposure, denoising, VSync and sample restart are available in the properties panel. Scroll down for all rendering controls.

The current selection is labeled in the viewport. The status bar shows FPS and asynchronously measured GPU time, rendering mode or progressive sample count, light count and action feedback. A converged stationary image is labeled “Cached image.” Realtime mode validates history against surface identity, normal and world position. Light, material, geometry, sky, projection-type and quality changes invalidate history; camera navigation reprojects matching surfaces. Newly exposed regions still need fresh samples, and glass/reflections may retain noise or short temporal lag. Stationary views continue refining. See [path-tracing requirements and limitations](path-tracing.md).

The default rig uses neutral sources, a closer camera, and a toggleable checker reference behind the material spheres. The blue sphere is ceramic; the gold sphere is metallic and the center sphere is clear glass. Colored presets and placement remain available. “Reference pattern on / off” is in the rendering panel.

## Reproducible validation

```powershell
.\gradlew.bat runLightingStudio --args="--smoke"
.\gradlew.bat runLightingStudio --args="--benchmark"
.\gradlew.bat runLightingStudio --args="--benchmark-motion"
.\gradlew.bat runLightingStudio --args="--benchmark-motion --progressive"
.\gradlew.bat runLightingStudio --args="--visual-validation=build/visual-validation"
.\gradlew.bat build
```

The hidden smoke run invokes installed native cursor/button/wheel callbacks and checks orbiting, zooming, activation of the placement button, surface placement, light dragging, undo/redo, both projections and GL errors. It writes `build/lighting-studio/studio.png` and `top-view.png` for visual review.

The studio benchmark uses 30 warmup frames and 120 measured frames, waits for GPU completion, disables VSync, and includes the UI. The motion workload continuously orbits at a fixed increment per frame and saves frame 60 as `build/lighting-studio/motion-realtime.png` or `motion-progressive.png`. Capture overhead is outside the timed render interval. `--progressive` selects the former rendering algorithm for comparisons. The existing `PathTracingExample --benchmark --quality=...` workload remains separate for renderer comparisons.

Visual validation uses a separate fixed 640×480 scene: a 1024-sample raw/filtered reference, consecutive moving frames, and a settled view. It does not load or modify saved studio rigs. See [visual audit and results](lighting-visual-audit.md).
