# Lighting and FPS

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run launcher commands
from that repository. Example source and resources are separate from the engine
checkout and library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions.

Valthorne now has separate 2D and 3D lighting implementations built for OpenGL 3.3.
They do not depend on the old ray-handler meshes or radiance cascades. Existing lighting
APIs remain available for compatibility; attach the new systems as shown below.

## See it

```shell
./gradlew runPhysics3DExample
./gradlew runLighting2DExample
./gradlew build
```

Use `gradlew.bat` on Windows. Both playgrounds show live FPS and milliseconds per frame.
Press **V** to toggle VSync: with VSync enabled, FPS normally follows the display refresh
rate. The FPS overlay measures wall-clock frame intervals independently of physics delta.
It uploads a glyph atlas once and reuses it; it does not rasterize text or upload new
textures each frame. The engine timer also keeps its absolute clock in double precision
to avoid losing short frame intervals during long-running sessions.

In 3D, Space drops a ball, clicking pushes objects, arrows orbit, and R resets.
In 2D, the mouse moves a light and Space pauses the moving obstacle. Escape closes either
playground. Both examples generate their assets and can save a screenshot:

```shell
./gradlew runPhysics3DExample --args="--snapshot=build/lighting3d.png"
./gradlew runLighting2DExample --args="--snapshot=build/lighting2d.png"
```

## 3D setup

Create lighting after the OpenGL context exists. It is borrowed by the render state;
close it yourself in `Application.dispose()`.

```java
Lighting3D lighting = new Lighting3D()
        .setEnvironment(new Color(.16f, .21f, .3f, 1), new Color(.035f, .028f, .025f, 1))
        .setExposure(1);
lighting.addLight(new PointLight3D()
        .setPosition(-3, -1, 4)
        .setColor(new Color(.32f, .55f, 1, 1))
        .setRange(9)
        .setIntensity(22));

MeshRenderState3D state = new MeshRenderState3D()
        .setCamera(camera)
        .setLighting(lighting)
        .setDirectionalLight(new Color(3.8f, 3.45f, 2.9f, 1))
        .setLightDirection(5, -5, 10)
        .setFog(25, 70, .8f);

Material3D metal = new Material3D()
        .setTint(new Color(.9f, .6f, .25f, 1))
        .setRoughness(.25f)
        .setMetallic(.8f);

// Render normally. ModelBatch3D prepares light data once for this pass.
scene.render(batch, state);
```

Imports are `valthorne.graphics.lighting3d.Lighting3D`,
`valthorne.graphics.model.*`, and `valthorne.graphics.Color`.
Add local lights to `Lighting3D`; the old eight-light list on `MeshRenderState3D`
belongs to its compatibility shader. The directional light and fog settings are shared.

The new shader uses GGX specular highlights, correlated Smith visibility, Schlick Fresnel,
energy-conserving diffuse/metallic material response, smooth finite-range attenuation,
and a filmic exposure curve. Surface color textures and tints are treated as sRGB;
lighting is computed in linear RGB and encoded for display. Light colors/intensities
and the sky/ground environment are linear values, so intensities may exceed one.
Roughness and metallic are in `[0,1]`. An internal roughness floor and normal derivatives
reduce unstable, tiny specular highlights. `setLightingMix(0)` retains exact unlit colors.

The environment is an inexpensive Z-up hemisphere approximation for diffuse and specular
fill. It does not provide cubemap reflections or global illumination. Point lights do
not cast shadows in this implementation. The new material path does not sample the old
XY radiance texture, nor does it expose normal maps, metallic/roughness textures, SSAO,
or bloom. Transparent meshes retain the existing sorting/blending path; tone mapping
happens per material, rather than after a shared HDR transparency pass.

### How 3D cost is bounded

- Up to **1,024 point lights**, with frustum rejection and conservative screen bounds.
- A CPU-built screen grid defaults to **64-pixel tiles**. Fragments fetch only their
  tile's candidate lights, then reject points outside each light's range before the BRDF.
- No depth readback, compute shader, G-buffer, or per-light shadow render is required.
- Light and tile data use reusable buffers. Unchanged cameras/lights skip GPU uploads.
  Material changes do not rebuild the light grid.
- A tile stores 64 indices. Overflow falls back to every visible light, preserving the
  result instead of dropping lights. Dense overlap still costs more; inspect
  `getGrid().getOverflowTileCount()` and `getAverageLightsPerTile()`.
- Tile size grows if needed to fit the device's texture-buffer limit. Near-eye light
  bounds conservatively cover the viewport, avoiding disappearing lights.
- A scene batch saves/restores GL state once around its material draws rather than
  querying/restoring driver state for every mesh material.

`Lighting3D.getUploadCount()` exposes upload activity. `setTiledCullingEnabled(false)`
selects the all-visible-lights reference path for comparisons; shading stays identical.
Direct `MeshBatch3D` draws also prepare attached lighting. Camera matrices must be current.

### Directional shadows

```java
ShadowMap3D shadows = new ShadowMap3D(1024)
        .setBias(.0006f).setSoftness(2.5f).setStrength(.85f);
shadows.getCamera().setPosition(5, -5, 10);
shadows.getCamera().lookAt(0, 0, 0, 0, 0, 1);
shadows.getCamera().setWorldHeight(24);
state.setShadowMap(shadows);

// Before the scene color pass:
shadows.render(scene);
```

The shadow pass now uses a dedicated depth-only shader with alpha cutouts. It performs
no lighting, fog, or radiance calculations. The new color shader uses four bilinear
hardware comparison samples for soft edges. Configure the light camera tightly around
the relevant scene region; softness is in shadow texels, and bias is normalized depth.

For mostly static scenes, call `shadows.renderIfChanged(scene, casterRevision)` instead.
Increment the revision whenever caster geometry, transforms, visibility, texture alpha,
or shadow material settings change. Light-camera changes are detected automatically.
`render` always redraws and invalidates the revision cache. `getRenderCount()` reports
actual shadow passes. Moving physics scenes can simply call `render` each frame.

## 2D setup

`valthorne.graphics.lighting2d` contains `Lighting2D`, `PointLight2D`, and `Occluder2D`.
It captures the scene's color into a texture, computes an HDR light map, and composites
lighting in linear color. Finish a `TextureBatch` before beginning/ending scene capture.
Draw UI afterward so it is unaffected by illumination.

```java
Lighting2D lighting = new Lighting2D()
        .setAmbient(new Color(.055f, .075f, .12f, 1))
        .setResolutionScale(.5f);
PointLight2D lamp = new PointLight2D()
        .setPosition(250, 400).setRadius(420)
        .setColor(new Color(.2f, .55f, 1, 1))
        .setIntensity(5).setSourceRadius(15);
lighting.addLight(lamp);

Occluder2D wall = Occluder2D.rectangle(370, 260, 45, 235);
lighting.addOccluder(wall);

// Inside your active 2D viewport, using matching world bounds and pixel dimensions:
lighting.beginScene(0, 0, worldWidth, worldHeight, pixelWidth, pixelHeight);
try {
    batch.begin();
    // Draw the scene's background, sprites, and obstacles.
    batch.end();
    lighting.endScene();
} finally {
    lighting.cancelScene(); // Restores the framebuffer if scene drawing fails.
}
// Draw the UI here.
```

Capture covers the viewport active at `beginScene`; it replaces that viewport's color
on composition and preserves the captured alpha. Provide an opaque scene background
when that is what your game needs. The capture target has no depth attachment. World
bounds are axis-aligned XY; offset/zoom them to match an unrotated orthographic camera.
Rotated camera mappings and normal-map lighting are not currently supported.

Colors are linear RGB for lights/ambient, while captured sprite colors are sRGB.
`setExposure` controls the final filmic curve. Sprite transparency is resolved in the
existing sprite pass before illumination. These are direct lights, not a GI solver.

`setSourceRadius` controls soft-shadow width; zero gives a hard source. To make a
spotlight, use `setCone(directionRadians, innerHalfAngle, outerHalfAngle)`. The outer
half angle must be at most PI. Equal inner/outer angles make a hard cone boundary.
Use `setCastsShadows(false)` for inexpensive decorative lights. `setEnabled(false)`
temporarily hides a light. Occluder categories and light masks are integer bitfields:
`wall.setCategory(2)` and `lamp.setOcclusionMask(2)` make them interact.

Occluder polygons copy local XY vertex pairs and support translation through
`setPosition`. They must be simple closed polygons with nonzero area, without holes;
concave shapes are allowed. Remove/recreate an occluder to change its local shape.
A light inside an occluder is blocked completely. An occluder does not draw geometry
itself; draw the matching wall/sprite as part of your scene.

### How 2D cost is bounded

- All visible lights render in **one instanced draw**, followed by one full-screen
  composite. Light quads limit fragment work to each light's screen footprint.
- The default light map is half resolution, independently of the full-resolution scene
  capture. Choose `.25`–`1` with `setResolutionScale`; filtering smooths the upscale.
- A spatial index selects nearby occluders. Segment rasterization touches only the
  angular bins covered by each segment, rather than casting every ray at every wall.
- Per-light polar shadows rebuild only for affected geometry/position/radius/mask
  changes. Color, intensity, and soft-source changes reuse the shadow data.
- Static frames reuse the entire light map. Camera movement and changed lighting
  redraw it; moving a distant occluder leaves unrelated shadow maps cached.
- Default capacity is 512 lights with 1,024 angular shadow bins per light. Use
  `new Lighting2D(capacity, shadowResolution)` to trade memory and angular detail.
  Resolution is 64–4,096; capacity is 1–4,096, subject to device texture limits.

Angular shadow maps approximate sub-bin geometry conservatively. Very thin obstacles
can produce wider shadows; a higher angular resolution improves detail. Dense dynamic
occluders and many overlapping large lights still cost more. The renderer exposes
`getVisibleLightCount`, `getLastLightDrawCalls`, `getShadowUploadCount`, and
`getLightMapRenderCount` so those costs can be inspected.

Close both lighting systems on their creating GL thread. They own buffers, shaders,
and render targets, but do not own your sprites, materials, or scene objects. The 2D
renderer restores framebuffer, viewport, blend/depth/cull state, color masks, scissor
enablement, sRGB state, texture bindings, and affected samplers. Nested scene captures
on the same renderer are rejected.

## Measurements and verification

The playgrounds have explicit benchmark modes. They disable VSync, simulate fixed
steps, warm up for 120 frames, then measure 300 frames. A `glFinish` is used **only in
benchmark mode** to include completed GPU work in render timing. These measurements
exclude the physics update and include scene rendering and overlay work; they are not
GPU-only timings or a guarantee of whole-game FPS.

```shell
./gradlew runPhysics3DExample --args="--benchmark --stress-lights"
./gradlew runPhysics3DExample --args="--benchmark --stress-lights --all-lights"
./gradlew runLighting2DExample --args="--benchmark --stress-lights --static"
./gradlew runLighting2DExample --args="--benchmark --stress-lights"
```

At 1100×760 on the local Windows RTX 5070:

| Measurement | Mean render time |
| --- | ---: |
| 3D, 258 lights, tiled | 1.53 ms |
| 3D, same lights/shading, all-light reference | 2.27 ms |
| 2D, 260 static lights, cached map | 0.15 ms |
| 2D, 260 lights with a moving obstacle | 0.34 ms |

The latest 3D comparison reduced render time by about 33% for this workload. The static 2D
run rendered its light map once and uploaded each light's shadow row once across all
420 frames. These numbers are workload/hardware-specific. For context, the old simple
3D playground measured 0.83 ms before this change, while the new two-local-light
playground measured 1.04 ms: richer shading has a cost, and the many-light optimization
does not imply every small scene becomes faster.

`verifyLighting` exercises CPU culling/caches and actual offscreen OpenGL output:
equivalence to all-light shading, overflow fallback, alpha-cutout shadows, 2D occlusion,
moving obstacles, spotlights, resizing, resource guards, and caller-state restoration.
The existing 3D and physics suites are also available through `verify3D` and
`verifyPhysics3D`. Run these engine verification tasks from the Valthorne checkout
with its local regression fixtures, separately from the companion launchers.

Shading references: [Filament's physically based rendering equations](https://google.github.io/filament/main/filament.html)
and [Krzysztof Narkowicz's filmic tone-mapping fit](https://knarkowicz.wordpress.com/2016/01/06/aces-filmic-tone-mapping-curve/).
The renderers here are Valthorne implementations, not integrations of the Filament library.
