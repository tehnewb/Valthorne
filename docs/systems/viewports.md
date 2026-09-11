# Viewport scaling and coordinate conversion

Author: Albert Beaupre

[System manual](README.md)

## Purpose

A viewport chooses how a logical world maps into available screen pixels. This is separate from the camera's projection model: the viewport decides scaling and placement, and the camera supplies the view. Use one policy consistently for drawing, resize handling, and pointer conversion.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Fit | Preserves aspect ratio while allowing unused borders. |
| Fill | Preserves aspect ratio while allowing content outside the visible screen. |
| Stretch | Maps dimensions independently and can distort aspect ratio. |
| Screen | Tracks screen-sized coordinates for pixel-oriented layouts. |
| Perspective integration | PerspectiveViewport connects a 3D camera to viewport dimensions and matrix application. |

## Getting started

1. Choose logical dimensions and the resize policy that fits the screen.
2. Update the viewport when the window or framebuffer size changes.
3. Apply its projection and viewport before drawing the associated content.
4. Use its screen/world conversion for input rather than assuming window pixels equal world units.

## Ownership and lifecycle

A viewport borrows its camera. Reused upload buffers avoid per-frame allocation, so do not assume returned mutable projection data is an independent copy.

## Important behavior

- Fit borders are not drawable world content; account for them during hit testing.
- Fill can intentionally crop world edges.
- High-DPI framebuffer dimensions can differ from logical window dimensions.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`FillViewport`](#type-fillviewport)
- [`FitViewport`](#type-fitviewport)
- [`PerspectiveViewport`](#type-perspectiveviewport)
- [`ScreenViewport`](#type-screenviewport)
- [`StretchViewport`](#type-stretchviewport)
- [`Viewport`](#type-viewport)

<a id="type-fillviewport"></a>

### FillViewport

[Source](../../src/main/java/valthorne/viewport/FillViewport.java#L37)

A viewport strategy that preserves the logical world aspect ratio while ensuring the screen
is completely filled, even if part of the world must be cropped.

This viewport is useful when you want to avoid black bars and are willing to let some content
fall outside the visible region on one axis. The world aspect ratio is preserved, so content
is not stretched, but the viewport may extend beyond the window bounds on either width or
height depending on the screen aspect ratio.

##### Behavior

- If the screen is wider than the world aspect ratio, the viewport expands width-first

- If the screen is taller than the world aspect ratio, the viewport expands height-first

- In both cases the viewport is centered on the opposite axis

##### Example

```java
FillViewport viewport = new FillViewport(1280f, 720f);
viewport.update(windowWidth, windowHeight);

viewport.bind();
try {
    worldRenderer.render();
} finally {
    viewport.unbind();
}
```

<details>
<summary>FillViewport operation reference (2 declarations)</summary>

#### Constructor

```java
public FillViewport(float worldWidth, float worldHeight)
```

Creates a fill viewport with the specified logical world size.

- **`worldWidth`** — the logical world width
- **`worldHeight`** — the logical world height

#### update

```java
@Override
    public void update(int screenWidth, int screenHeight)
```

Updates this viewport so the screen is fully covered while preserving world aspect ratio.

The resulting viewport may become larger than the actual screen on one axis, which causes
cropping rather than letterboxing. The fallback orthographic projection is then rebuilt for
the current world size.

- **`screenWidth`** — the window width in pixels
- **`screenHeight`** — the window height in pixels

</details>

<a id="type-fitviewport"></a>

### FitViewport

[Source](../../src/main/java/valthorne/viewport/FitViewport.java#L34)

A viewport strategy that preserves the logical world aspect ratio while fitting the entire
world inside the available screen area.

This viewport is commonly used when you want the whole world region to remain visible without
distortion. If the window aspect ratio does not match the world aspect ratio, unused space
appears as letterboxing or pillarboxing.

##### Behavior

- The full world is always visible

- Aspect ratio is preserved

- No stretching occurs

- Black bars or unused margins may appear on one axis

##### Example

```java
FitViewport viewport = new FitViewport(1920f, 1080f);
viewport.update(windowWidth, windowHeight);

viewport.render(() -> {
    game.render();
});
```

<details>
<summary>FitViewport operation reference (2 declarations)</summary>

#### Constructor

```java
public FitViewport(float worldWidth, float worldHeight)
```

Creates a fit viewport with the specified logical world size.

- **`worldWidth`** — the logical world width
- **`worldHeight`** — the logical world height

#### update

```java
@Override
    public void update(int screenWidth, int screenHeight)
```

Updates this viewport so the full world remains visible while preserving aspect ratio.

The screen rectangle is centered on whichever axis has remaining unused space. The fallback
orthographic projection is then rebuilt for the current world size.

- **`screenWidth`** — the window width in pixels
- **`screenHeight`** — the window height in pixels

</details>

<a id="type-perspectiveviewport"></a>

### PerspectiveViewport

[Source](../../src/main/java/valthorne/viewport/PerspectiveViewport.java#L45)

Viewport that binds a 3D camera's combined projection-view matrix into the engine.

While this viewport is bound, existing 2D shader-based renderers continue to work.
Their vertices are rendered on the world XY plane at `z = 0`, which makes it
easy to mix sprites, shapes, and other 2D content into a 3D camera setup.

```java
PerspectiveViewport viewport = new PerspectiveViewport(800, 600);
viewport.renderWithOverlay(() -> {
    // Draw world geometry with the viewport's camera.
}, () -> {
    // Draw UI in bottom-left-origin overlay coordinates.
});
```

Viewport bounds use OpenGL bottom-left-origin pixel coordinates. Overlay
dimensions are independent logical units, initially matching the constructor
dimensions. Screen conversion methods use the camera's current matrices and
reuse internal result objects; copy results that must survive another call.

Binding saves the previous viewport rectangle and engine projection only.
Overlay entry additionally captures the state covered by RenderStateSnapshot3D.
Rendering requires a current OpenGL context on the owning thread. Flush pending
batches before switching projection modes, and close nested scopes in reverse
order. The camera is borrowed and mutable; this viewport does not own its lifetime.

<details>
<summary>PerspectiveViewport operation reference (24 declarations)</summary>

#### Constructor

```java
public PerspectiveViewport(int width, int height)
```

Creates an origin-aligned viewport with a new default perspective camera.
Overlay dimensions initially match the viewport; camera matrices are rebuilt.

- **`width`** — the positive pixel width
- **`height`** — the positive pixel height

**Throws `IllegalArgumentException`:** if either dimension is not positive

#### Constructor

```java
public PerspectiveViewport(int width, int height, Camera3D camera)
```

Creates an origin-aligned viewport retaining the supplied camera and rebuilding
its matrices for these dimensions. Construction does not bind OpenGL state.

- **`width`** — the positive pixel width
- **`height`** — the positive pixel height
- **`camera`** — the nonnull borrowed camera

**Throws `NullPointerException`:** if camera is null

**Throws `IllegalArgumentException`:** if either dimension is not positive

#### update

```java
public void update(int screenWidth, int screenHeight)
```

Resets bounds to the origin and rebuilds the camera for a new screen size.
Overlay dimensions follow this size until setOverlaySize has been called.
This configures future rendering without issuing glViewport.

- **`screenWidth`** — the positive screen width in pixels
- **`screenHeight`** — the positive screen height in pixels

**Throws `IllegalArgumentException`:** if either dimension is not positive

#### setBounds

```java
public void setBounds(int x, int y, int width, int height)
```

Stores pixel bounds and rebuilds an attached camera. Overlay dimensions are
unchanged, even in automatic mode; update is the resize operation that also
adjusts them. The new rectangle is applied to OpenGL by apply or bind.

- **`x`** — the left edge in bottom-left-origin pixels
- **`y`** — the bottom edge in bottom-left-origin pixels
- **`width`** — the positive pixel width
- **`height`** — the positive pixel height

**Throws `IllegalArgumentException`:** if width or height is not positive

#### setOverlaySize

```java
public void setOverlaySize(float overlayWidth, float overlayHeight)
```

Sets logical overlay extents and disables automatic sizing on later updates.
The next overlay entry builds the new projection. Validation rejects values
at or below zero but does not explicitly reject NaN or positive infinity;
callers should supply finite positive dimensions.

- **`overlayWidth`** — the logical horizontal extent
- **`overlayHeight`** — the logical vertical extent

**Throws `IllegalArgumentException`:** if either extent compares at or below zero

#### apply

```java
public void apply()
```

Installs the viewport rectangle, rebuilds camera matrices, and publishes
the camera's combined matrix as the engine projection. This saves no prior
state and does not mark the viewport bound. Requires a current GL context.

#### bind

```java
public void bind()
```

Saves the current GL viewport and engine projection, then applies this
viewport. Pair a successful bind with unbind on the same rendering thread.
The same instance cannot be nested within itself.

**Throws `IllegalStateException`:** if this viewport is already bound

#### unbind

```java
public void unbind()
```

Ends an active overlay, restores the projection and rectangle saved by bind,
and clears the bound flag. Calling while unbound does nothing. World drawing
state beyond those saved values is not restored by the viewport scope.

#### render

```java
public void render(DrawFunction function)
```

Runs a drawing callback inside a bind/unbind scope. After a successful bind,
unbind runs even when drawing throws; the callback's failure propagates.

- **`function`** — the nonnull world drawing callback

**Throws `NullPointerException`:** if function is null

**Throws `IllegalStateException`:** if this viewport is already bound

#### renderWithOverlay

```java
public void renderWithOverlay(DrawFunction worldFunction, DrawFunction overlayFunction)
```

Draws world content followed by an orthographic overlay in nested restoration
scopes. If world drawing fails, the overlay is skipped. Successful overlay
entry is paired with exit even on a callback failure, followed by unbind.

- **`worldFunction`** — the nonnull world drawing callback
- **`overlayFunction`** — the nonnull overlay drawing callback

**Throws `NullPointerException`:** if either callback is null

**Throws `IllegalStateException`:** if the viewport is already bound or overlay entry is invalid

#### beginOverlay2D

```java
public void beginOverlay2D()
```

Captures world projection and selected render state, then installs a
bottom-left-origin orthographic projection spanning the overlay dimensions.
Depth testing, depth writes and culling are disabled; standard source-alpha
blending with additive blend equations is enabled. The viewport rectangle
stays unchanged. Finish outstanding batches before this transition.

**Throws `IllegalStateException`:** if unbound or an overlay is already active

#### endOverlay2D

```java
public void endOverlay2D()
```

Restores projection and the render-state snapshot captured at overlay entry,
then releases the snapshot reference. An inactive overlay is a no-op. Finish
overlay batches before leaving so they use the intended projection and state.

#### containsScreenPoint

```java
public boolean containsScreenPoint(float screenX, float screenY)
```

Tests the stored rectangle with inclusive edges, without consulting camera
state. Coordinates use the same bottom-left-origin pixels as the GL viewport.

- **`screenX`** — the horizontal screen coordinate
- **`screenY`** — the vertical screen coordinate

**Returns:** whether both coordinates lie inside or on the rectangle edges

#### screenToRay

```java
public Rayf screenToRay(float screenX, float screenY)
```

Creates a picking ray from the current camera matrices for an in-bounds
screen point. The ray starts on the near plane and has a normalized direction
toward the far plane. This does not rebuild a camera modified since the last
rebuild; the returned ray is reused by the next successful call.

- **`screenX`** — the bottom-left-origin screen X coordinate
- **`screenY`** — the bottom-left-origin screen Y coordinate

**Returns:** the borrowed ray, or null outside the rectangle

#### project

```java
public Vector3f project(Vector3f world)
```

Projects a world point using current camera matrices without clipping or
rebuilding them. Result X/Y are bottom-left-origin screen pixels and Z is
normalized depth, with near/far planes at zero/one. Off-screen results are
permitted. The vector is shared with unproject and overwritten on later calls.

- **`world`** — the nonnull world-space point

**Returns:** the borrowed screen-position and depth vector

**Throws `NullPointerException`:** if world is null

#### unproject

```java
public Vector3f unproject(float screenX, float screenY, float depth)
```

Converts an in-bounds screen point and normalized depth through the current
inverse camera matrix. Depth zero/one represents the near/far plane; depth
is not clamped or validated here. The result shares storage with project.

- **`screenX`** — the bottom-left-origin screen X coordinate
- **`screenY`** — the bottom-left-origin screen Y coordinate
- **`depth`** — the normalized depth to unproject

**Returns:** the borrowed world point, or null outside the rectangle

#### getCamera

```java
public Camera3D getCamera()
```

Returns the retained camera directly. After mutating it, rebuild its matrices
before coordinate queries, or use apply/bind to rebuild for this viewport.

**Returns:** the live nonnull camera

#### setCamera

```java
public void setCamera(Camera3D camera)
```

Replaces the borrowed camera and rebuilds it for current pixel dimensions.
This does not install its projection in Window until apply or bind is used.

- **`camera`** — the nonnull replacement camera

**Throws `NullPointerException`:** if camera is null

#### getX

```java
public int getX()
```

Reads the configured left edge; pending bounds need not yet be applied to GL.

**Returns:** the left edge in screen pixels

#### getY

```java
public int getY()
```

Reads the configured bottom edge in OpenGL's screen-coordinate convention.

**Returns:** the bottom edge in screen pixels

#### getWidth

```java
public int getWidth()
```

Reads the pixel width used to rebuild the camera, independent of overlay units.

**Returns:** the positive configured viewport width

#### getHeight

```java
public int getHeight()
```

Reads the pixel height used to rebuild the camera, independent of overlay units.

**Returns:** the positive configured viewport height

#### getOverlayWidth

```java
public float getOverlayWidth()
```

Reads the logical horizontal extent used on the next overlay entry.
It can differ from the viewport's pixel width after explicit sizing.

**Returns:** the configured overlay width in logical units

#### getOverlayHeight

```java
public float getOverlayHeight()
```

Reads the logical vertical extent used on the next overlay entry.
It can differ from the viewport's pixel height after explicit sizing.

**Returns:** the configured overlay height in logical units

</details>

<a id="type-screenviewport"></a>

### ScreenViewport

[Source](../../src/main/java/valthorne/viewport/ScreenViewport.java#L37)

A viewport strategy where the viewport fills the entire screen and, by default, the logical
world size matches the actual screen size.

This viewport is useful for UI rendering or screen-space rendering where you want one world
unit to correspond directly to one screen pixel after each resize. It can also be constructed
with a custom initial world size, but `update(int, int)` will overwrite the world size
so that it matches the latest screen dimensions.

##### Behavior

- The viewport always covers the full screen

- The world size is updated to match the current screen size

- No aspect preservation logic is needed because the world tracks the screen directly

##### Example

```java
ScreenViewport viewport = new ScreenViewport(1280, 720);
viewport.update(windowWidth, windowHeight);

viewport.bind();
try {
    ui.draw(batch);
} finally {
    viewport.unbind();
}
```

<details>
<summary>ScreenViewport operation reference (3 declarations)</summary>

#### Constructor

```java
public ScreenViewport(int width, int height)
```

Creates a screen viewport whose initial logical world size matches the supplied pixel size.

- **`width`** — the initial screen-like world width
- **`height`** — the initial screen-like world height

#### Constructor

```java
public ScreenViewport(float worldWidth, float worldHeight)
```

Creates a screen viewport with a custom initial world size.

Note that once `update(int, int)` is called, the world size is overwritten so it
matches the current screen size.

- **`worldWidth`** — the initial logical world width
- **`worldHeight`** — the initial logical world height

#### update

```java
@Override
    public void update(int screenWidth, int screenHeight)
```

Updates this viewport so it fully matches the current screen dimensions.

Both the screen rectangle and the logical world size are updated to the provided dimensions.
The fallback orthographic projection is then rebuilt so world units match screen pixels.

- **`screenWidth`** — the window width in pixels
- **`screenHeight`** — the window height in pixels

</details>

<a id="type-stretchviewport"></a>

### StretchViewport

[Source](../../src/main/java/valthorne/viewport/StretchViewport.java#L33)

A viewport strategy that stretches the logical world to fill the entire screen rectangle.

This viewport never letterboxes and never crops, but it does not preserve aspect ratio.
If the screen aspect ratio differs from the world aspect ratio, content is stretched on one
axis.

##### Behavior

- The viewport always fills the full screen

- The whole world remains visible

- Aspect ratio is not preserved

- Content may appear wider or taller depending on the screen shape

##### Example

```java
StretchViewport viewport = new StretchViewport(800f, 600f);
viewport.update(windowWidth, windowHeight);

viewport.render(() -> {
    renderer.drawScene();
});
```

<details>
<summary>StretchViewport operation reference (2 declarations)</summary>

#### Constructor

```java
public StretchViewport(float worldWidth, float worldHeight)
```

Creates a stretch viewport with the specified logical world size.

- **`worldWidth`** — the logical world width
- **`worldHeight`** — the logical world height

#### update

```java
@Override
    public void update(int screenWidth, int screenHeight)
```

Updates this viewport so it fills the entire screen without preserving aspect ratio.

The screen rectangle is set to the full window size and the fallback orthographic
projection is rebuilt for the current world size.

- **`screenWidth`** — the window width in pixels
- **`screenHeight`** — the window height in pixels

</details>

<a id="type-viewport"></a>

### Viewport

[Source](../../src/main/java/valthorne/viewport/Viewport.java#L106)

Base 2D viewport abstraction responsible for mapping a logical world area into a screen-space
rectangle, applying the proper OpenGL viewport and engine-managed projection state,
converting screen coordinates into world coordinates, and handling viewport-aware
scissor rectangles.

A viewport controls two separate concepts:

- **Screen region**: the rectangle in actual window pixels where rendering occurs

- **World region**: the logical coordinate space visible inside that screen region

Concrete subclasses decide how the screen rectangle is computed during
`update(int, int)`. For example:

- `FitViewport` preserves aspect ratio and letterboxes when needed

- `FillViewport` preserves aspect ratio but fills the whole screen, even if that means cropping

- `StretchViewport` stretches world content to the full screen rectangle

- `ScreenViewport` maps the world directly to screen pixels

##### Rendering flow

The normal render flow is:

- Call `update(int, int)` when the window size changes

- Call `bind()` before drawing content for this viewport

- Draw your world or UI

- Call `unbind()` to restore the previous OpenGL viewport and projection

If you want a scoped one-call render, you can instead use `render(DrawFunction)`.

##### Camera behavior

When a `Camera` is assigned, this viewport delegates projection generation to that
camera each time `apply()` is called. If no camera is assigned, the viewport uses its
own fallback `projectionMatrix`, which subclasses usually update inside
`update(int, int)`.

##### Scissor behavior

The scissor methods operate in **world-space coordinates**, not raw screen-space pixel
coordinates. The viewport converts the provided world rectangle into screen-space pixels and
applies it through OpenGL's scissor test. Nested scissors are handled by intersecting the new
rectangle with any existing active scissor box.

##### Coordinate system

This class assumes a bottom-left world coordinate system, matching the rest of your engine
and standard OpenGL-style orthographic rendering.

##### Example

```java
Viewport viewport = new FitViewport(1280f, 720f);
viewport.update(windowWidth, windowHeight);

viewport.bind();
try {
    batch.begin();

    batch.draw(background, 0f, 0f, 1280f, 720f);

    if (viewport.beginScissor(100f, 100f, 400f, 200f)) {
        try {
            batch.draw(panelTexture, 100f, 100f, 400f, 200f);
        } finally {
            viewport.endScissor();
        }
    }

    batch.end();
} finally {
    viewport.unbind();
}

Vector2f world = viewport.screenToWorld(mouseX, mouseY);
if (world != null) {
    System.out.println("Mouse in world: " + world.x() + ", " + world.y());
}
```

<details>
<summary>Viewport operation reference (32 declarations)</summary>

#### projectionMatrix

```java
protected final  Matrix4f projectionMatrix
```

Live projection matrix rebuilt by viewport updates.

#### x

```java
protected int x
```

X position of this viewport in actual screen pixels.

#### y

```java
protected int y
```

Y position of this viewport in actual screen pixels.

#### width

```java
protected int width
```

Width of this viewport in actual screen pixels.

#### height

```java
protected int height
```

Height of this viewport in actual screen pixels.

#### worldWidth

```java
protected float worldWidth
```

Logical world width visible through this viewport.

#### worldHeight

```java
protected float worldHeight
```

Logical world height visible through this viewport.

#### camera

```java
protected Camera camera
```

Optional camera used to build the active projection transform.

#### Constructor

```java
public Viewport(float worldWidth, float worldHeight)
```

Creates a viewport with the specified logical world size.

The provided world size represents the logical coordinate space that this viewport exposes
to rendering and input conversion. The actual screen rectangle is not defined here and must
later be computed by a concrete subclass inside `update(int, int)`.

- **`worldWidth`** — the logical width visible through this viewport
- **`worldHeight`** — the logical height visible through this viewport

#### update

```java
public abstract void update(int screenWidth, int screenHeight)
```

Recomputes this viewport's screen rectangle and fallback projection using the current
window dimensions.

Every concrete viewport strategy must implement this method. Typical responsibilities are:

- Choosing the screen-space x and y position of the viewport

- Choosing the screen-space width and height of the viewport

- Updating `projectionMatrix` to match the intended world-space projection

- **`screenWidth`** — the current window width in pixels
- **`screenHeight`** — the current window height in pixels

#### apply

```java
public void apply()
```

Applies this viewport's OpenGL viewport rectangle and projection state.

This method updates the current OpenGL viewport to this viewport's screen rectangle and
then updates the engine-managed projection matrix. If a camera is present, the camera is
rebuilt using the current world size and its projection matrix is applied. Otherwise the
fallback viewport projection is used.

#### bind

```java
public void bind()
```

Binds this viewport for scoped rendering.

This method captures the currently active OpenGL viewport and engine projection, and then
applies this viewport. It is intended to be paired with
`unbind()`.

Use this when you want full control over when rendering begins and ends inside the viewport.

#### unbind

```java
public void unbind()
```

Restores the OpenGL state captured by `bind()`.

This method restores the previously active engine projection matrix and viewport rectangle.

#### render

```java
public void render(DrawFunction function)
```

Renders a drawing function inside this viewport while automatically preserving and restoring
the previous OpenGL state.

This is a convenience wrapper around the same bind/apply/unbind flow used by
`bind()` and `unbind()`, but scoped to a single callback.

- **`function`** — the drawing function to execute inside this viewport

**Throws `NullPointerException`:** if `function` is null

#### screenToWorld

```java
public Vector2f screenToWorld(float screenX, float screenY)
```

Converts a screen-space coordinate into a world-space coordinate using this viewport's
current layout and optional camera.

If the given screen coordinate lies outside the viewport's screen rectangle, this method
returns `null`. Otherwise it converts the point into viewport-local coordinates,
maps it into the logical world dimensions, and then optionally applies the inverse camera
transform if a camera is assigned.

The returned vector is reused internally, so it should be used immediately and not stored
long-term if more conversions may happen later.

- **`screenX`** — the x position in actual screen pixels
- **`screenY`** — the y position in actual screen pixels

**Returns:** a reused vector containing the corresponding world coordinate, or `null` if the point is outside the viewport

#### screenToWorldUnclipped

```java
public Vector2f screenToWorldUnclipped(float screenX, float screenY)
```

Converts a captured pointer even after it leaves the viewport rectangle.

#### beginScissor

```java
public boolean beginScissor(float wx, float wy, float ww, float wh)
```

Begins a viewport-aware scissor rectangle using world-space coordinates.

The provided world-space rectangle is converted into the viewport's actual screen-space
pixel rectangle. The result is then clamped to the viewport bounds. If another scissor
rectangle is already active, the new rectangle is intersected with the existing scissor
region so nested scissoring behaves correctly.

If the resulting scissor rectangle has no visible area, this method returns `false`
and no new scissor state is applied.

- **`wx`** — the world-space x coordinate of the scissor rectangle
- **`wy`** — the world-space y coordinate of the scissor rectangle
- **`ww`** — the world-space width of the scissor rectangle
- **`wh`** — the world-space height of the scissor rectangle

**Returns:** true if a valid scissor rectangle was applied, false if the rectangle clipped to nothing

#### endScissor

```java
public void endScissor()
```

Ends the most recently applied scissor scope started by `beginScissor(float, float, float, float)`.

If a scissor test was already active before this viewport began its scissor scope, the
previous scissor rectangle is restored. Otherwise the OpenGL scissor test is disabled.

#### applyScissor

```java
public void applyScissor(float wx, float wy, float ww, float wh, DrawFunction function)
```

Executes a draw function inside a temporary world-space scissor rectangle.

This is a convenience wrapper around `beginScissor(float, float, float, float)` and
`endScissor()`. If the requested scissor rectangle clips to nothing, the function is
not called.

- **`wx`** — the world-space x coordinate of the scissor rectangle
- **`wy`** — the world-space y coordinate of the scissor rectangle
- **`ww`** — the world-space width of the scissor rectangle
- **`wh`** — the world-space height of the scissor rectangle
- **`function`** — the drawing code to execute while the scissor is active

**Throws `NullPointerException`:** if `function` is null

#### setWorldSize

```java
public void setWorldSize(float worldWidth, float worldHeight)
```

Sets the logical world size visible through this viewport.

This changes the world-space dimensions used for projection and input conversion. Concrete
subclasses may need `update(int, int)` to be called afterward so their projection
matrix and screen rectangle stay in sync with the new world dimensions.

- **`worldWidth`** — the new logical world width
- **`worldHeight`** — the new logical world height

#### setSize

```java
public void setSize(int width, int height)
```

Sets the screen-space size of this viewport in pixels.

This directly updates the viewport rectangle dimensions. Most subclasses normally calculate
these values inside `update(int, int)`.

- **`width`** — the new viewport width in pixels
- **`height`** — the new viewport height in pixels

#### setPosition

```java
public void setPosition(int x, int y)
```

Sets the screen-space position of this viewport in pixels.

- **`x`** — the new screen-space x position
- **`y`** — the new screen-space y position

#### getX

```java
public int getX()
```

Returns the screen-space x position of this viewport.

**Returns:** the viewport x position in pixels

#### setX

```java
public void setX(int x)
```

Sets the screen-space x position of this viewport.

- **`x`** — the new viewport x position in pixels

#### getY

```java
public int getY()
```

Returns the screen-space y position of this viewport.

**Returns:** the viewport y position in pixels

#### setY

```java
public void setY(int y)
```

Sets the screen-space y position of this viewport.

- **`y`** — the new viewport y position in pixels

#### getWidth

```java
public int getWidth()
```

Returns the screen-space width of this viewport.

**Returns:** the viewport width in pixels

#### getHeight

```java
public int getHeight()
```

Returns the screen-space height of this viewport.

**Returns:** the viewport height in pixels

#### getWorldWidth

```java
public float getWorldWidth()
```

Returns the logical world width visible through this viewport.

**Returns:** the logical world width

#### getWorldHeight

```java
public float getWorldHeight()
```

Returns the logical world height visible through this viewport.

**Returns:** the logical world height

#### getCamera

```java
public Camera getCamera()
```

Returns the camera currently assigned to this viewport.

**Returns:** the current camera, or `null` if none is assigned

#### setCamera

```java
public void setCamera(Camera camera)
```

Assigns a camera to this viewport.

When a camera is assigned, `apply()` rebuilds the camera using the current world
size and loads the camera projection instead of the fallback viewport projection matrix.
Passing `null` restores fallback projection behavior.

- **`camera`** — the camera to assign, or `null` to disable camera-based projection

</details>

## Related guides

- [Cameras and picking](cameras.md)
- [UI roots, nodes, and input routing](ui-core.md)
- [Application lifecycle and window management](runtime.md)
