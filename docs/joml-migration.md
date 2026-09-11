# JOML math migration

Valthorne now exposes JOML types directly. `org.joml:joml:1.10.8` and `org.joml:joml-primitives:1.10.0` are Gradle `api` dependencies, so consumers receive them transitively. **Jolt remains the native 3D physics engine.** This migration changes math storage and operations, not the physics backend.

## Source compatibility

The duplicate Valthorne math classes have been removed; this is a breaking source/API change, without compatibility wrappers.

| Removed type | Replacement |
|---|---|
| `valthorne.math.Vector2f`, `Vector3f` | `org.joml.Vector2f`, `Vector3f` |
| `valthorne.math.Matrix4f` | `org.joml.Matrix4f` |
| `valthorne.math.Quaternionf` | `org.joml.Quaternionf` |
| `valthorne.math.Bounds3D` | `org.joml.primitives.AABBf` |
| `valthorne.math.Ray3f` | `org.joml.primitives.Rayf` |
| `valthorne.math.Intersections3D` | JOML intersection operations at engine call sites |
| `valthorne.camera.Frustum3D` | `org.joml.FrustumIntersection` |

Rendering shapes (`Shape`, `Rectangle`, `Circle`, `Polygon`, etc.) remain engine objects with colors, borders, layout, and polygon generation. They now use JOML vectors. General engine math helpers also remain where there is no equivalent replacement class.

## Updating client code

Import the JOML types instead of Valthorne's types. Vector components use `.x`, `.y`, `.z` or `.x()`, `.y()`, `.z()`. Make independent copies with `new Vector3f(existing)` or `new Matrix4f(existing)`.

Matrices no longer expose a mutable float-array backing store. Use `matrix.get(reusedFloatArray)` or `matrix.get(reusedFloatBuffer)` for column-major shader uploads, and `matrix.set(values)` to replace values. JOML tracks matrix properties internally; modify matrices through its API. `Window.getProjectionMatrix()` remains a borrowed, reused upload snapshot; use `setProjectionMatrix` to change projection state.

Use `setPerspective`, `setOrtho`, and `setLookAt` when replacing a transform. Their unprefixed counterparts multiply the existing matrix. JOML uses the same right-handed OpenGL convention; engine models and physics retain their Z-up world convention, and camera up remains configurable (the default camera up is Y). The engine's Euler inputs still apply X, then Y, then Z, expressed as `new Quaternionf().rotationZYX(z, y, x)`. For an arbitrary axis, use `rotationAxis(angle, x, y, z)`.

`Rayf` stores scalar `oX/oY/oZ` and `dX/dY/dZ` fields. Its empty constructor has a zero direction. Engine camera rays populate normalized directions; physics raycasts validate and normalize supplied directions while preserving world-distance results. Set valid finite directions explicitly when creating rays yourself.

JOML math methods have JOML's contracts, including behavior for singular matrices and zero-length normalization. Engine boundaries retain the validation needed for cameras, model transforms and native physics. Physics quaternions are explicitly normalized and checked before passing to Jolt.

[JOML's official guide](https://github.com/JOML-CI/JOML) explains mutable destination objects, transformation order, and allocation-conscious use.

JOML AABBf.isValid() requires positive volume; it rejects planar or point bounds. Engine culling deliberately accepts equal minima/maxima to preserve flat meshes. Client code validating planar bounds should likewise compare min <= max on each axis.

## Validation

The build, examples, JMH sources, and all standard/3D/physics/UI/lighting suites pass. Current suite counts are 146 standard, 68 3D, 19 native Jolt, 44 UI, and 27 lighting tests; suites overlap. Checks cover projection replacement, column-major uploads, mirrored meshes, planar/point bounds, picking edge hits, inverse-transpose normals, zero normals, normalized quaternions, physics ray distances, and reused Window projection snapshots. Physics Studio and Lighting Studio native-input smoke tests pass, with a rendered-framebuffer visual check.

The library JAR contains none of the eight removed classes. [Validation and benchmark artifacts](benchmarks/joml-migration/README.md) record the new implementation. This is an API/library migration, not a claim that every workload is faster.
