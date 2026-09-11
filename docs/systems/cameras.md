# Cameras and picking

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Cameras convert world geometry into clip space and support picking and culling. Choose an orthographic camera for constant apparent size and a perspective camera for depth-dependent size. Camera3D owns derived view/projection data; changing pose requires rebuilding that derived state.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| 2D cameras | Orthographic and UI camera variants support planar rendering conventions. |
| 3D projections | Perspective and orthographic projections control field of view or visible extents. |
| Picking | Unprojection and screen rays translate a cursor into world-space queries. |
| Frustum culling | Derived planes reject geometry outside the current view. |
| Orbit control | A controller maps interaction into a camera pose around a target. |

## Getting started

1. Choose the camera type for your scene and configure pose and clipping distances.
2. Rebuild after pose, projection, or viewport dimensions change.
3. Use the resulting matrices for rendering and the same dimensions for picking.
4. Convert top-left window input to the coordinate convention required by the camera before generating a ray.

## Ownership and lifecycle

Camera accessors often expose live mutable vectors and matrices. Copy them when you need a snapshot, and rebuild after changing pose. Scratch storage makes concurrent operations on one camera unsafe.

## Important behavior

- Camera3D picking rays start on the near plane, not necessarily at the eye.
- The 3D helpers use bottom-left viewport coordinates and normalized depth zero through one.
- A stale frustum can disagree with the image if you render after changing pose without rebuilding.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Camera`](#type-camera)
- [`Camera3D`](#type-camera3d)
- [`OrbitCameraController`](#type-orbitcameracontroller)
- [`OrthographicCamera`](#type-orthographiccamera)
- [`OrthographicCamera3D`](#type-orthographiccamera3d)
- [`PerspectiveCamera`](#type-perspectivecamera)
- [`UIOrthographicCamera`](#type-uiorthographiccamera)

<a id="type-camera"></a>

### Camera

[Source](../../src/main/java/valthorne/camera/Camera.java#L38)

The `Camera` class serves as the abstract foundation for all 2D camera
implementations within the JGL framework. It provides common properties and
behavior needed for rendering 2D worlds, such as camera centering, zooming,
and projection matrix management.

A `Camera` defines how the world is viewed during rendering. Concrete
subclasses provide specific projection types (orthographic, pixel-perfect,
screen-space, etc.) by implementing `rebuild(float, float)`.

##### Core Responsibilities

- Store and modify the camera's world-space center position.

- Maintain camera zoom level, clamped to a minimum safe value.

- Provide access to the camera's projection matrix.

- Require subclasses to rebuild the projection matrix when needed.

##### Usage Notes

- `rebuild(float, float)` should be called once per frame or whenever
zoom or center changes.

- `getProjection()` returns the active projection matrix used in rendering.

- Zoom values below `0.001f` are automatically clamped.

This class is intended for extension\u2014use `Camera` as the base for
custom camera types tailored to specific rendering strategies.

<details>
<summary>Camera operation reference (9 declarations)</summary>

#### center

```java
protected final  Vector2f center
```

Live world-space camera center used by concrete projection implementations.

#### projection

```java
protected final  Matrix4f projection
```

Reusable projection matrix rebuilt by concrete camera implementations.

#### zoom

```java
protected  float zoom
```

Camera zoom factor, initially one; setters enforce a minimum of 0.001.

#### getCenter

```java
public Vector2f getCenter()
```

Returns the current center of the camera.

**Returns:** the camera's world-space center as a `Vector2f`

#### setCenter

```java
public void setCenter(float x, float y)
```

Sets the camera's center location in world space.

- **`x`** — the new x-coordinate of the camera center
- **`y`** — the new y-coordinate of the camera center

#### getZoom

```java
public float getZoom()
```

Returns the current zoom level of the camera.

**Returns:** the zoom factor

#### setZoom

```java
public void setZoom(float z)
```

Sets the zoom level of the camera. Zoom is clamped to a minimum of `0.001f`
to prevent projection matrix instability or division-by-zero calculations.

- **`z`** — the desired zoom level

#### rebuild

```java
public abstract void rebuild(float worldWidth, float worldHeight)
```

Rebuilds the camera's projection matrix. This method is called whenever
the camera changes (zoom, center) or once each frame depending on implementation.

Subclasses must define how the projection matrix is constructed based on
the world width and height.

- **`worldWidth`** — the width of the world or viewport
- **`worldHeight`** — the height of the world or viewport

#### getProjection

```java
public Matrix4f getProjection()
```

Returns the active projection matrix used by the camera during rendering.

**Returns:** the internal `Matrix4f` projection matrix

</details>

<a id="type-camera3d"></a>

### Camera3D

[Source](../../src/main/java/valthorne/camera/Camera3D.java#L36)

Owns a mutable camera pose, projection and view matrices, their combined inverse,
and a frustum for projection, picking, and culling. Subclasses supply the projection
model. Pose setters change vectors immediately but derived matrices are refreshed
only by `rebuild(float, float)`.

```java
PerspectiveCamera camera = new PerspectiveCamera();
camera.setPosition(4, 4, 3);
camera.lookAt(0, 0, 0, 0, 0, 1);
camera.rebuild(1280, 720);
Rayf ray = camera.screenPointToRay(640, 360, 0, 0, 1280, 720, new Rayf());
```

Projection utilities use bottom-left viewport coordinates and normalized depth
zero through one. Convert top-left input coordinates before calling them. Picking
rays begin on the near plane, not at the camera position. Mutable accessors return
owned storage; do not modify derived matrices independently or retain them as
snapshots. Scratch vectors make concurrent operations on one camera unsafe.

When used with Valthorne's current 2D renderers, draw calls are rendered on the
world XY plane at `z = 0`. That lets the existing 2D APIs participate in a
3D scene while the camera handles perspective, orbiting, and picking.

<details>
<summary>Camera3D operation reference (45 declarations)</summary>

#### position

```java
protected final  Vector3f position
```

World-space eye position; edits require a rebuild.

#### direction

```java
protected final  Vector3f direction
```

Forward direction, initially negative Z.

#### up

```java
protected final  Vector3f up
```

View-up vector, initially positive Y.

#### right

```java
protected final  Vector3f right
```

Right vector derived from direction cross up.

#### projection

```java
protected final  Matrix4f projection
```

Owned projection matrix last produced by rebuild.

#### view

```java
protected final  Matrix4f view
```

Owned world-to-view matrix last produced by rebuild.

#### combined

```java
protected final  Matrix4f combined
```

Owned projection-times-view matrix.

#### inverseCombined

```java
protected final  Matrix4f inverseCombined
```

Owned inverse used for unprojection.

#### frustum

```java
protected final  FrustumIntersection frustum
```

Owned culling planes extracted from the combined matrix.

#### near

```java
protected  float near
```

Near clip distance in world units, initially 0.1.

#### far

```java
protected  float far
```

Far clip distance in world units, initially 1000.

#### viewportWidth

```java
protected  float viewportWidth
```

Width passed to the most recent rebuild, initially one.

#### viewportHeight

```java
protected  float viewportHeight
```

Height passed to the most recent rebuild, initially one.

#### getPosition

```java
public Vector3f getPosition()
```

Returns the live world-space eye position owned by this camera. Make a copy when
a snapshot is needed; derived state reflects the last rebuild, and direct edits
do not synchronize the other camera values.

**Returns:** the mutable world-space eye position

#### getDirection

```java
public Vector3f getDirection()
```

Returns the live forward basis vector owned by this camera. Make a copy when
a snapshot is needed; derived state reflects the last rebuild, and direct edits
do not synchronize the other camera values.

**Returns:** the mutable forward basis vector

#### getUp

```java
public Vector3f getUp()
```

Returns the live view-up basis vector owned by this camera. Make a copy when
a snapshot is needed; derived state reflects the last rebuild, and direct edits
do not synchronize the other camera values.

**Returns:** the mutable view-up basis vector

#### getRight

```java
public Vector3f getRight()
```

Returns the live right basis vector owned by this camera. Make a copy when
a snapshot is needed; derived state reflects the last rebuild, and direct edits
do not synchronize the other camera values.

**Returns:** the mutable right basis vector

#### getProjection

```java
public Matrix4f getProjection()
```

Returns the live projection matrix owned by this camera. Make a copy when
a snapshot is needed; derived state reflects the last rebuild, and direct edits
do not synchronize the other camera values.

**Returns:** the mutable projection matrix

#### getView

```java
public Matrix4f getView()
```

Returns the live view matrix owned by this camera. Make a copy when
a snapshot is needed; derived state reflects the last rebuild, and direct edits
do not synchronize the other camera values.

**Returns:** the mutable view matrix

#### getCombined

```java
public Matrix4f getCombined()
```

Returns the live projection-times-view matrix owned by this camera. Make a copy when
a snapshot is needed; derived state reflects the last rebuild, and direct edits
do not synchronize the other camera values.

**Returns:** the mutable projection-times-view matrix

#### getInverseCombined

```java
public Matrix4f getInverseCombined()
```

Returns the live inverse combined matrix owned by this camera. Make a copy when
a snapshot is needed; derived state reflects the last rebuild, and direct edits
do not synchronize the other camera values.

**Returns:** the mutable inverse combined matrix

#### getFrustum

```java
public FrustumIntersection getFrustum()
```

Returns the live culling frustum owned by this camera. Make a copy when
a snapshot is needed; derived state reflects the last rebuild, and direct edits
do not synchronize the other camera values.

**Returns:** the mutable culling frustum

#### getNear

```java
public float getNear()
```

Returns the configured near clip distance in world units. Reading it does not
rebuild the camera or validate the current matrices.

**Returns:** near clip distance in world units

#### getFar

```java
public float getFar()
```

Returns the configured far clip distance in world units. Reading it does not
rebuild the camera or validate the current matrices.

**Returns:** far clip distance in world units

#### getViewportWidth

```java
public float getViewportWidth()
```

Returns the configured viewport width retained by the last rebuild. Reading it does not
rebuild the camera or validate the current matrices.

**Returns:** viewport width retained by the last rebuild

#### getViewportHeight

```java
public float getViewportHeight()
```

Returns the configured viewport height retained by the last rebuild. Reading it does not
rebuild the camera or validate the current matrices.

**Returns:** viewport height retained by the last rebuild

#### setPosition

```java
public void setPosition(float x, float y, float z)
```

Replaces the world-space eye position without changing orientation or rebuilding
matrices. Components are stored without finiteness validation.

- **`x`** — X component
- **`y`** — Y component
- **`z`** — Z component

#### setDirection

```java
public void setDirection(float x, float y, float z)
```

Replaces the forward direction and reconstructs an orthonormal basis using the
current up hint. A zero direction falls back to negative Z; parallel up vectors
are replaced with a usable axis. Rebuild before projecting or drawing.

- **`x`** — X component
- **`y`** — Y component
- **`z`** — Z component

#### setUp

```java
public void setUp(float x, float y, float z)
```

Supplies a view-up hint and reconstructs the basis around the current direction.
Zero or parallel hints are replaced as needed, so the resulting up vector may
differ from the supplied vector. Matrices are not rebuilt.

- **`x`** — X component
- **`y`** — Y component
- **`z`** — Z component

#### setClipPlanes

```java
public void setClipPlanes(float near, float far)
```

Stores clip distances after checking their order. Supply finite values with
positive near and far greater than near; comparisons do not separately reject
NaN. Rebuild to apply the distances to projection and culling.

- **`near`** — near distance in world units
- **`far`** — far distance in world units

**Throws `IllegalArgumentException`:** if near is nonpositive or far is at most near

#### move

```java
public void move(float dx, float dy, float dz)
```

Adds a world-space offset to the eye position while preserving orientation.
The derived view and projection state is unchanged until rebuild.

- **`dx`** — world X displacement
- **`dy`** — world Y displacement
- **`dz`** — world Z displacement

#### moveForward

```java
public void moveForward(float distance)
```

Moves the eye along the current forward basis vector without rebuilding matrices.
Distance is in world units when that vector is normalized; direct accessor edits
can change its length and therefore the effective displacement.

- **`distance`** — signed displacement; negative values move in the opposite direction

#### strafeRight

```java
public void strafeRight(float distance)
```

Moves the eye along the current right basis vector without rebuilding matrices.
Distance is in world units when that vector is normalized; direct accessor edits
can change its length and therefore the effective displacement.

- **`distance`** — signed displacement; negative values move in the opposite direction

#### moveUp

```java
public void moveUp(float distance)
```

Moves the eye along the current up basis vector without rebuilding matrices.
Distance is in world units when that vector is normalized; direct accessor edits
can change its length and therefore the effective displacement.

- **`distance`** — signed displacement; negative values move in the opposite direction

#### lookAt

```java
public void lookAt(float targetX, float targetY, float targetZ)
```

Aims from the current eye toward a world-space point, rebuilding the basis with
the current up hint. A coincident target falls back to negative Z. This changes
orientation only; call rebuild to refresh matrices.

- **`targetX`** — target world X coordinate
- **`targetY`** — target world Y coordinate
- **`targetZ`** — target world Z coordinate

#### lookAt

```java
public void lookAt(float targetX, float targetY, float targetZ, float upX, float upY, float upZ)
```

Aims toward a world-space point using an explicit up hint. The hint is normalized
and made perpendicular to the viewing direction, with a fallback for degenerate
inputs. Eye position is preserved and matrices are not rebuilt.

- **`targetX`** — target world X coordinate
- **`targetY`** — target world Y coordinate
- **`targetZ`** — target world Z coordinate
- **`upX`** — up-hint X component
- **`upY`** — up-hint Y component
- **`upZ`** — up-hint Z component

#### yaw

```java
public void yaw(float radians)
```

Rotates orientation around the current up axis using Rodrigues' formula,
then reconstructs an orthonormal basis. Position is preserved; rebuild before
using derived matrices or the frustum.

- **`radians`** — signed right-handed rotation angle in radians

#### pitch

```java
public void pitch(float radians)
```

Rotates orientation around the current right axis using Rodrigues' formula,
then reconstructs an orthonormal basis. Position is preserved; rebuild before
using derived matrices or the frustum.

- **`radians`** — signed right-handed rotation angle in radians

#### roll

```java
public void roll(float radians)
```

Rotates orientation around the current forward axis using Rodrigues' formula,
then reconstructs an orthonormal basis. Position is preserved; rebuild before
using derived matrices or the frustum.

- **`radians`** — signed right-handed rotation angle in radians

#### rebuild

```java
public void rebuild(float viewportWidth, float viewportHeight)
```

Refreshes the basis, subclass projection, view matrix, combined matrix, inverse,
and frustum in that order using the supplied viewport aspect ratio. This must
follow pose or projection changes before drawing, culling, or picking. Supply
finite positive dimensions; only nonpositive dimensions are explicitly rejected.

- **`viewportWidth`** — viewport width in units matching viewportHeight
- **`viewportHeight`** — viewport height in units matching viewportWidth

**Throws `IllegalArgumentException`:** if either dimension is nonpositive

#### project

```java
public Vector3f project(Vector3f world, int viewportX, int viewportY, int viewportWidth, int viewportHeight, Vector3f out)
```

Projects a world-space point through the last rebuilt combined matrix. Output X/Y
use bottom-left screen coordinates and Z is normalized depth; results are not
clipped to the viewport. The output may alias the input vector.

- **`world`** — point to project
- **`viewportX`** — left viewport origin in screen units
- **`viewportY`** — bottom viewport origin in screen units
- **`viewportWidth`** — viewport width in screen units
- **`viewportHeight`** — viewport height in screen units
- **`out`** — destination vector overwritten with screen X/Y and depth

**Returns:** out

**Throws `NullPointerException`:** if world or out is null

#### project

```java
public Vector3f project(float worldX, float worldY, float worldZ, int viewportX, int viewportY, int viewportWidth, int viewportHeight, Vector3f out)
```

Projects coordinates with the current combined matrix and maps normalized device
coordinates to the supplied viewport. No clipping or visibility test is performed.
For zero homogeneous W the inverse is treated as zero, mapping finite clip
coordinates to the viewport center and depth 0.5. Dimensions are not validated.

- **`worldX`** — world X coordinate
- **`worldY`** — world Y coordinate
- **`worldZ`** — world Z coordinate
- **`viewportX`** — left viewport origin in screen units
- **`viewportY`** — bottom viewport origin in screen units
- **`viewportWidth`** — viewport width in screen units
- **`viewportHeight`** — viewport height in screen units
- **`out`** — destination for bottom-left screen X/Y and normalized depth

**Returns:** out

**Throws `NullPointerException`:** if out is null

#### unproject

```java
public Vector3f unproject(float screenX, float screenY, float depth, int viewportX, int viewportY, int viewportWidth, int viewportHeight, Vector3f out)
```

Transforms a bottom-left screen position and normalized depth using the inverse
from the last rebuild. Depth zero is the near plane and one is the far plane;
outside values extrapolate. A zero homogeneous W skips division. Viewport
dimensions are not validated and must be nonzero for meaningful results.

- **`screenX`** — horizontal screen coordinate
- **`screenY`** — vertical coordinate measured upward
- **`depth`** — normalized depth
- **`viewportX`** — left viewport origin in screen units
- **`viewportY`** — bottom viewport origin in screen units
- **`viewportWidth`** — viewport width in screen units
- **`viewportHeight`** — viewport height in screen units
- **`out`** — destination world-space point

**Returns:** out

**Throws `NullPointerException`:** if out is null

#### screenPointToRay

```java
public Rayf screenPointToRay(float screenX, float screenY, int viewportX, int viewportY, int viewportWidth, int viewportHeight, Rayf out)
```

Unprojects a screen point at near and far depth and writes a normalized ray
between them. Its origin is the near-plane point for both perspective and
orthographic cameras. Uses shared scratch vectors and the last rebuilt inverse.

- **`screenX`** — horizontal screen coordinate
- **`screenY`** — bottom-left vertical screen coordinate
- **`viewportX`** — left viewport origin in screen units
- **`viewportY`** — bottom viewport origin in screen units
- **`viewportWidth`** — viewport width in screen units
- **`viewportHeight`** — viewport height in screen units
- **`out`** — ray whose origin and direction will be replaced

**Returns:** out

**Throws `NullPointerException`:** if out is null

#### buildProjection

```java
protected abstract void buildProjection(float viewportWidth, float viewportHeight)
```

Replaces the owned projection matrix for the subclass's projection model.
Called during rebuild after basis correction and before combined matrices are
computed. Implementations should use the configured clip planes and supplied
aspect ratio without replacing the owned matrix reference.

- **`viewportWidth`** — width supplied to rebuild
- **`viewportHeight`** — height supplied to rebuild

</details>

<a id="type-orbitcameracontroller"></a>

### OrbitCameraController

[Source](../../src/main/java/valthorne/camera/OrbitCameraController.java#L24)

Maintains a Z-up editor camera's orbit center, angular pose, and viewing distance.
Input integration is left to the caller: drag deltas rotate or pan, wheel deltas
adjust distance, and `apply(PerspectiveCamera)` copies the pose to a camera.
Operations reuse stored vectors and do not allocate per-frame controller state.

```java
OrbitCameraController controller = new OrbitCameraController();
PerspectiveCamera camera = new PerspectiveCamera();
controller.orbit(20, -5);
controller.apply(camera);
camera.rebuild(1280, 720);
```

Applying a pose does not rebuild camera matrices. Use finite input deltas and
access the controller on the same thread as camera updates; it is unsynchronized.

<details>
<summary>OrbitCameraController operation reference (7 declarations)</summary>

#### getTarget

```java
public Vector3f getTarget()
```

Returns the live orbit center. Mutations change subsequent pan and apply
operations; copy the vector when an independent snapshot is required.

**Returns:** mutable world-space target owned by this controller

#### getDistance

```java
public float getDistance()
```

Returns the current distance from the target, initially twelve world units.
Zoom operations constrain this value to the inclusive range one to one hundred.

**Returns:** camera-to-target distance in world units

#### reset

```java
public void reset()
```

Restores target `(0, 0.5, 1.5)`, azimuth -1.45 radians, elevation 0.33
radians, and distance twelve. An already configured camera is unaffected until
the next apply call.

#### orbit

```java
public void orbit(float dx, float dy)
```

Subtracts drag deltas scaled by 0.006 radians from the orbit angles. Elevation
is clamped to plus or minus 1.45 radians; azimuth is not wrapped or clamped.

- **`dx`** — horizontal drag delta, typically pixels
- **`dy`** — vertical drag delta, typically pixels

#### zoom

```java
public void zoom(float wheel)
```

Multiplies distance by `exp(-wheel * 0.12)` and clamps the result to
one through one hundred world units. Positive wheel deltas move closer to
the target; zero preserves the current distance.

- **`wheel`** — signed scroll delta

#### pan

```java
public void pan(float dx, float dy, float viewportHeight)
```

Translates the target horizontally in the azimuth-aligned XY plane and
vertically along world Z. Sensitivity is `distance * 0.8` divided by
viewport height, with heights below one treated as one. This is a navigation
sensitivity heuristic rather than an exact projection-based screen conversion.

- **`dx`** — horizontal drag delta in the same units as viewport height
- **`dy`** — vertical drag delta; positive values raise the target
- **`viewportHeight`** — viewport height, typically pixels

#### apply

```java
public void apply(PerspectiveCamera camera)
```

Places a camera on the orbit sphere and aims it at the target using positive
Z as world up. Projection settings are preserved and matrices remain unchanged
until the camera is rebuilt.

- **`camera`** — camera whose position and orientation will be replaced

**Throws `NullPointerException`:** if camera is null

</details>

<a id="type-orthographiccamera"></a>

### OrthographicCamera

[Source](../../src/main/java/valthorne/camera/OrthographicCamera.java#L34)

The `OrthographicCamera` class provides a standard 2D orthographic camera.
It extends `Camera` and constructs an axis-aligned orthographic projection
based on the camera's center, zoom level, and the world dimensions provided.

This is the most common camera type for 2D rendering and is ideal for:

- Tile-based games

- UI systems

- Side-scrollers and top-down views

- Pixel-perfect rendering setups (when paired with appropriate world units)

##### Projection Behavior

- Computes a world-aligned orthographic projection.

- Zooming reduces or expands the viewed region.

- The projection always remains unrotated and non-skewed.

##### Usage Example

```java
Camera camera = new OrthographicCamera();
    camera.setCenter(0, 0);
    camera.setZoom(1f);
    camera.rebuild(worldWidth, worldHeight);
```

<details>
<summary>OrthographicCamera operation reference (1 declarations)</summary>

#### rebuild

```java
@Override
    public void rebuild(float worldWidth, float worldHeight)
```

Rebuilds the camera's projection matrix using a standard axis-aligned
orthographic projection. The visible region is determined by the world
dimensions and the current zoom factor.

The projection boundaries are computed so that the camera centers on
`center` and zooms uniformly in both X and Y directions.

- **`worldWidth`** — the width of the viewport or world units visible
- **`worldHeight`** — the height of the viewport or world units visible

</details>

<a id="type-orthographiccamera3d"></a>

### OrthographicCamera3D

[Source](../../src/main/java/valthorne/camera/OrthographicCamera3D.java#L12)

Orthographic camera whose visible height is `worldHeight / zoom` world
units. Width follows the viewport aspect ratio, and the projection is centered
on the camera's view axis. Defaults show ten world units vertically at unit zoom.
Pose and clip planes come from `Camera3D`; call `rebuild(float, float)`
after configuration changes to update projection, frustum, and picking state.

<details>
<summary>OrthographicCamera3D operation reference (5 declarations)</summary>

#### getWorldHeight

```java
public float getWorldHeight()
```

Returns the vertical span configured for unit zoom, independent of the actual
viewport size and the current zoom multiplier.

**Returns:** unzoomed vertical extent in world units

#### setWorldHeight

```java
public void setWorldHeight(float worldHeight)
```

Stores the vertical world-space span at unit zoom without rebuilding matrices.
Supply a finite positive value; the implementation rejects nonpositive values
but does not separately validate finiteness.

- **`worldHeight`** — vertical extent in world units before zoom

**Throws `IllegalArgumentException`:** if the extent is zero or negative

#### getZoom

```java
public float getZoom()
```

Returns the configured magnification. Values above one reduce the visible
world-space span; values between zero and one increase it.

**Returns:** zoom multiplier, initially one

#### setZoom

```java
public void setZoom(float zoom)
```

Stores magnification for the next rebuild. Supply a finite positive value;
the range check rejects zero and negative values but accepts NaN and positive
infinity, which do not produce a useful projection.

- **`zoom`** — divisor of the unzoomed visible world-space span

**Throws `IllegalArgumentException`:** if zoom is zero or negative

#### buildProjection

```java
    protected void buildProjection(float viewportWidth, float viewportHeight)
```

Replaces the projection with a centered orthographic volume using the zoomed
world height, viewport aspect ratio, and configured near and far clip planes.
The base rebuild operation updates the remaining derived camera state.

- **`viewportWidth`** — viewport width used for the aspect ratio
- **`viewportHeight`** — viewport height used for the aspect ratio

</details>

<a id="type-perspectivecamera"></a>

### PerspectiveCamera

[Source](../../src/main/java/valthorne/camera/PerspectiveCamera.java#L12)

Perspective camera with a configurable vertical field of view in degrees.
The default angle is 67 degrees; aspect ratio comes from the viewport dimensions
supplied to `rebuild(float, float)`. Changing the angle updates configuration
only, so rebuild before using the projection, frustum, or picking calculations.
Camera pose and clip-plane behavior are inherited from `Camera3D`.

<details>
<summary>PerspectiveCamera operation reference (3 declarations)</summary>

#### getFieldOfViewDegrees

```java
public float getFieldOfViewDegrees()
```

Returns the configured vertical view angle. The current projection may still
reflect an earlier value if the camera has not been rebuilt since a change.

**Returns:** vertical field of view in degrees

#### setFieldOfViewDegrees

```java
public void setFieldOfViewDegrees(float fieldOfViewDegrees)
```

Stores the vertical view angle without rebuilding derived camera state.
Supply a finite angle strictly between zero and 180 degrees. Range comparisons
reject out-of-range values and infinities but do not explicitly reject NaN.

- **`fieldOfViewDegrees`** — vertical field of view in degrees

**Throws `IllegalArgumentException`:** if the angle is at most zero or at least 180

#### buildProjection

```java
    protected void buildProjection(float viewportWidth, float viewportHeight)
```

Replaces the projection with a perspective matrix using the configured angle
and clip planes. Called by the base camera's rebuild operation; does not update
the view matrix, inverse matrix, or frustum itself.

- **`viewportWidth`** — viewport width used to calculate the aspect ratio
- **`viewportHeight`** — viewport height used to calculate the aspect ratio

</details>

<a id="type-uiorthographiccamera"></a>

### UIOrthographicCamera

[Source](../../src/main/java/valthorne/camera/UIOrthographicCamera.java#L40)

The `UIOrthographicCamera` class provides a specialized orthographic camera
designed for UI and screen-space rendering. Unlike a world-space camera, this camera
aligns its projection to a top-left origin system (0,0 \u2192 top-left, width/height \u2192 bottom-right),
which is the common coordinate system used for user interfaces.

This camera does **not** center its projection. Instead, it treats the
`center` vector as the top-left corner of the visible region, making UI layout
logic intuitive and consistent with screen-space coordinates.

##### Key Differences from `OrthographicCamera`

- Origin aligns to the top-left of the UI.

- No world-centered projection\u2014uses `center` as the top-left.

- Projection Y-axis is inverted to match screen-space coords
(top \u2192 0, bottom \u2192 +height).

- Zoom scales UI uniformly without shifting the camera\u2019s origin.

##### Use Cases

- User interface rendering

- HUDs, menus, overlays, tooltips

- Pixel-based or resolution-independent UI layouts

##### Example

```java
Camera uiCamera = new UIOrthographicCamera();
    uiCamera.setCenter(0, 0);   // Top-left of screen
    uiCamera.setZoom(1f);
    uiCamera.rebuild(windowWidth, windowHeight);
```

<details>
<summary>UIOrthographicCamera operation reference (1 declarations)</summary>

#### rebuild

```java
@Override
    public void rebuild(float worldWidth, float worldHeight)
```

Rebuilds the projection matrix using a UI-friendly coordinate system.
The visible region begins at `center` (treated as the top-left corner)
and extends rightward and downward according to the scaled world dimensions.

The projection Y-axis is inverted by supplying `bottom` above
`top` when calling `ortho`, matching typical UI coordinate
conventions.

- **`worldWidth`** — the width of the screen or UI layout region
- **`worldHeight`** — the height of the screen or UI layout region

</details>

## Related guides

- [Viewport scaling and coordinate conversion](viewports.md)
- [3D models, materials, scenes, and billboards](models.md)
- [Jolt rigid-body physics](physics.md)
- [Keyboard, mouse, and cursor input](input.md)
