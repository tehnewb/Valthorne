# Changelog

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
