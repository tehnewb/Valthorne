# Changelog

## Unreleased

Future changes will be listed here after the 2.1.0 release.

## 2.1.0

- Add the development browser target, shared Java compatibility applications,
  and platform adapters for rendering, input, audio, storage, and UI.
- Add conservative 3D occlusion, indexed Filament uploads, sprite-shaped 2D
  shadows, and desktop graphics capability selection.
- Implement a Filament readback transfer path for additional desktop runtimes;
  native Linux/macOS rendering validation remains pending.
- Keep browser integration fixtures outside published engine artifacts, preserve
  required browser source packages in Git, and run committed core/native
  compatibility checks without requiring local test folders.
- Expose an optional raw desktop mouse-motion preference through the shared API.
- Deliver browser keyboard, mouse, text, scroll, focus, and window events on the
  application coroutine so trusted input can retain browser activation without
  advancing simulation or rendering an extra frame.
- Add executable browser and native compatibility consumers, including a delayed
  trusted-input regression and full FPS gameplay/resource verification.
- Validate all seven FPS pointer-capture cycles, restart ownership, and final
  browser resource cleanup on software rendering and discrete GPU hardware.
- Publish browser source backends and compatibility fixtures without leaking
  examples, tests, or development sources into the library artifacts.

This release includes the browser target and the cross-platform release checks.
The companion examples project remains the source of truth for runnable demos.

## 2.0.0

This major release changes the public math API and adds the desktop 3D engine
systems described below. These changes are not part of the older 1.4.6 artifact.

### Breaking changes

- Public math types use JOML and JOML Primitives. Replace removed
  `valthorne.math.Vector2f`, `Vector3f`, `Matrix4f`, and `Ray3f` usages as described
  in [the migration guide](docs/joml-migration.md).
- Build and runtime require Java 25. Native launchers need
  `--enable-native-access=ALL-UNNAMED`; macOS graphics launchers also need
  `-XstartOnFirstThread`.

### Engine systems

- 3D scenes, OBJ/MTL assets, cameras, picking, transform animation, raster lighting,
  shadows, particles, and a Windows x64 Filament renderer.
- Jolt native 3D physics and scene/body integration.
- OpenGL 4.3 path tracing, cached 2D lighting, and spatial sound areas.
- Shared texture/NanoVG UI lifecycle, editing, themes, virtual controls, tables,
  tabs, split panes, modals, and inspection tools.
- Subsystem guides, asset provenance and recorded performance evidence.
  Development examples and regression suites remain optional local files.

### Release preparation

- Host-independent native dependency publication, explicit platform requirements,
  a minimal runnable starter, and complete Gradle/Maven integration instructions.
- Safe contributor build defaults; signed Maven Central publication is explicit.
- Reproducible archives with library license and asset/dependency notices;
  validated source/Javadoc artifacts and external publication consumers.
- Cross-platform CI build/native checks and generated graphics consumers.
- Ignore rules cover current caches, local credentials, prototypes and generated
  output while preserving library resources, wrapper and documentation.
- Example sources and resources are excluded from Git, standard builds and CI;
  local example launchers remain optional development tools.
- Test and benchmark folders remain local-only. Release verification generates
  temporary consumer applications under ignored build output.

## 1.4.6

Previously published release. Consult its matching tag/artifact and historical
documentation for its API; do not use this development manual as a compatibility
reference for 1.4.6.
