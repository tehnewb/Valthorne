# Scenes and game screens

Author: Albert Beaupre

[System manual](README.md)

## Purpose

`Scene` groups a screen's update, draw, UI, viewport, and listener infrastructure. `GameScreen` adapts one active scene to `Application`. This is appropriate for switching between a menu, gameplay, and a results screen while keeping their resources and input lifetimes distinct.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Scene infrastructure | Initialization prepares batch, viewport, UI, and event adapters before scene-specific setup. |
| Draw lifecycle | The scene coordinator controls the shared batch's begin/end sequence. |
| Pause | Pausing suppresses scene updates while rendering may continue. |
| Replacement | Changing the current scene disposes the previous scene; this is not a scene stack or transition animation system. |

## Getting started

1. Subclass `Scene` and implement `init`, `update`, `draw`, and `dispose`.
2. Wrap the initial scene in `GameScreen`, then pass that screen to `JGL.init`.
3. Issue draw commands using the supplied scene batch rather than beginning it a second time.
4. Use `setScene` for replacement and create transition effects explicitly if you need fades or overlapping screens.

## Ownership and lifecycle

The active scene owns its infrastructure and must release its own additional resources. Scene replacement is a lifecycle change, not a cheap pointer swap: the old scene is disposed.

## Important behavior

- Do not retain controls or listeners from a disposed scene as active input targets.
- Use a consistent viewport for rendering and pointer conversion.
- Pause behavior should be reflected in your simulation clocks and animations.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`GameScreen`](#type-gamescreen)
- [`Scene`](#type-scene)
- [`SceneKeyListener`](#type-scenekeylistener)
- [`SceneMouseListener`](#type-scenemouselistener)
- [`SceneMouseScrollListener`](#type-scenemousescrolllistener)
- [`SceneWindowResizeListener`](#type-scenewindowresizelistener)

<a id="type-gamescreen"></a>

### GameScreen

[Source](../../src/main/java/valthorne/scene/GameScreen.java#L47)

##### GameScreen

`GameScreen` is a lightweight application-level scene controller that bridges your engine's
`Application` lifecycle with a currently active `Scene`. It is responsible for forwarding
initialization, rendering, updating, and disposal calls to whichever scene is currently active.

The class is intentionally simple. It does not implement scene stacking, transitions, fades, or
background loading on its own. Instead, it focuses on one job: keeping exactly one active
`Scene` running at a time and making it easy to replace that scene during runtime.

##### Lifecycle behavior

- `init()` initializes the current scene if one exists.

- `render()` wraps scene rendering in the scene's own `TextureBatch` begin/end cycle.

- `update(float)` skips updates while the scene is paused.

- `setScene(Scene)` disposes the previous scene before switching to the next one.

- `dispose()` disposes the currently active scene if one exists.

##### Why this class exists

Your engine already has an application entry point and a scene abstraction. `GameScreen` gives
those two systems a dedicated coordinator so your game code can stay clean. Instead of putting scene
switching logic inside the application itself, you centralize it here.

##### Example

```java
Scene menuScene = new MainMenuScene();
GameScreen gameScreen = new GameScreen(menuScene);

JGL.init(gameScreen, "My Game", 800, 600);
```

<details>
<summary>GameScreen operation reference (7 declarations)</summary>

#### Constructor

```java
public GameScreen(Scene initialScene)
```

Creates a new `GameScreen` with an optional initial scene.

The supplied scene is only stored here. It is not initialized automatically by the constructor.
Initialization still happens through the normal `init()` lifecycle call.

- **`initialScene`** — the first scene to control, or null if no scene should be active yet

#### init

```java
@Override
    public void init()
```

Initializes the currently active scene if one exists.

Before calling the scene's own `Scene#init()` method, this method first calls
`Scene#initializeFields()` so the scene has its runtime infrastructure prepared,
including its batch, viewport, UI, and listeners.

#### render

```java
@Override
    public void render()
```

Renders the currently active scene if one exists.

Rendering is wrapped in the scene's internal `valthorne.graphics.texture.TextureBatch`
begin/end calls so scene implementations only need to focus on issuing draw commands inside
`Scene#draw(valthorne.graphics.texture.TextureBatch)`.

#### update

```java
@Override
    public void update(float delta)
```

Updates the currently active scene if one exists and is not paused.

When a scene is paused, update logic is skipped completely. This allows rendering to continue
while preventing game logic, animations, or simulation state from advancing.

- **`delta`** — the elapsed time in seconds since the previous update

#### dispose

```java
@Override
    public void dispose()
```

Disposes the currently active scene if one exists.

This is typically called when the application is shutting down. Disposal is delegated entirely
to the scene itself.

#### setScene

```java
public void setScene(Scene scene)
```

Replaces the current scene with a new one.

If a scene is already active, it is disposed before the new scene is assigned. If the provided
scene is null, the current scene is simply cleared and no new scene is initialized.

Unlike `init()`, this method directly calls `Scene#initializeFields()` followed by
`Scene#init()` for the incoming scene so the scene is immediately ready for use after
the switch.

- **`scene`** — the new scene to activate, or null to clear the current scene

#### getCurrentScene

```java
public Scene getCurrentScene()
```

Returns the currently active scene.

**Returns:** the active scene, or null if no scene is currently assigned

</details>

<a id="type-scene"></a>

### Scene

[Source](../../src/main/java/valthorne/scene/Scene.java#L94)

##### Scene

`Scene` is the abstract runtime foundation for a single active state in the engine, such as
a title screen, gameplay screen, pause menu, editor, dialogue scene, or loading scene. A scene
groups together the systems and callbacks needed to manage one self-contained part of the
application's lifecycle.

Every scene owns a rendering batch, a UI root, and a viewport. It may also own a camera depending
on how the scene wants to render world content. The class handles the shared setup for these
objects and wires the scene into the global window, keyboard, mouse, and scroll event systems.
Concrete subclasses then focus only on scene-specific initialization, rendering, updating, and
disposal.

##### Lifecycle model

The intended lifecycle is:

- `init()` performs subclass-specific setup.

- `draw(TextureBatch)` is used by the scene controller to render the world and UI.

- `update(float)` is used by the scene controller to update the scene when not paused.

- `dispose()` releases subclass-owned resources.

##### Important design rule

Subclasses should place their custom cleanup in `dispose()`, then call
`disposeScene()` at the end of that method. The infrastructure disposal method
intentionally does **not** call `dispose()` again. This avoids recursive disposal bugs.

##### Rendering behavior

`drawScene()` wraps the scene render flow in a single `TextureBatch` begin/end cycle.
It first calls `draw(TextureBatch)` so subclasses can draw scene content, then it draws the
scene UI. This keeps the common render path in one place and avoids duplicating batch begin/end code
in every controller.

##### Pause behavior

`updateScene(float)` respects the scene's paused flag. When paused, update logic is skipped,
but rendering can still continue. This is useful for pause menus, overlays, and frozen gameplay
states where the scene should remain visible but not simulate.

##### Example

```java
public final class GameplayScene extends Scene {

    @Override
    public void init() {

    }

    @Override
    public void render(TextureBatch batch) {

    }

    @Override
    public void update(float delta) {

    }

    @Override
    public void dispose() {

    }
}
```

<details>
<summary>Scene operation reference (29 declarations)</summary>

#### ui

```java
protected UIRoot ui
```

The root UI object owned by this scene.

#### camera

```java
protected Camera camera
```

The optional camera used to render world content for this scene.

#### viewport

```java
protected Viewport viewport
```

The viewport controlling projection, coordinate mapping, and UI/world bounds.

#### batch

```java
protected TextureBatch batch
```

The shared texture batch used to render this scene.

#### initializeFields

```java
protected void initializeFields()
```

Initializes the common runtime fields used by all scenes.

This method prepares the scene's shared runtime infrastructure. It creates the rendering batch,
creates the UI root, creates a default screen viewport sized to the current window, assigns that
viewport to the UI, and registers all listener wrappers required for event forwarding.

This method is safe against repeated calls. Once initialization has already happened, later calls
do nothing. That prevents duplicate listener registration and accidental recreation of scene
systems.

#### drawScene

```java
protected void drawScene()
```

Draws the complete scene using the shared batch.

This method provides the standard render flow for a scene. It begins the scene batch, calls the
subclass render method so world and scene-specific content can be drawn, then draws the scene UI,
and finally ends the batch.

Controllers such as `GameScreen` can call this method directly instead of duplicating the
batch begin/end flow.

#### updateScene

```java
protected void updateScene(float delta)
```

Updates the scene while respecting the paused state.

If the scene is paused, this method returns immediately and does not call the subclass update
method. If the scene is not paused, the provided delta time is forwarded to
`update(float)`.

- **`delta`** — the elapsed time in seconds since the last update

#### init

```java
public abstract void init()
```

Initializes scene-specific resources and state.

Subclasses implement this method to load assets, configure cameras, build UI widgets, spawn
world entities, or perform any other startup logic required once the shared scene infrastructure
already exists.

#### draw

```java
public abstract void draw(TextureBatch batch)
```

Renders scene-specific content using the provided texture batch.

This method should contain the actual draw logic for the scene's world or custom content. The
shared render flow already manages batch begin/end externally, so implementations should usually
only queue draw operations here.

- **`batch`** — the texture batch supplied for rendering this scene

#### update

```java
public abstract void update(float delta)
```

Updates scene-specific logic.

Subclasses implement this method to perform simulation, input-driven logic, animation state
changes, timers, entity updates, and any other time-based progression for the scene.

- **`delta`** — the elapsed time in seconds since the previous update

#### dispose

```java
public abstract void dispose()
```

Disposes scene-specific resources.

Subclasses implement this method to release anything they own directly, such as textures, maps,
sounds, scene objects, or custom data structures. Implementations should normally call
`disposeScene()` at the end so the shared batch, UI, viewport references,
and registered listeners are also cleaned up.

#### disposeScene

```java
protected void disposeScene()
```

Disposes the shared infrastructure created by `initializeFields()`.

This method removes all registered listeners created for the scene and disposes shared runtime
objects such as the UI and texture batch. It also clears references to the viewport and camera.

This method does not call `dispose()`. That is intentional. Calling back into the scene's
abstract disposal method from here would create a recursion problem when subclasses call this
helper from their own `dispose()` implementation.

Repeated calls are safe. Once the infrastructure has already been disposed, later calls do
nothing.

#### windowResized

```java
public void windowResized(WindowResizeEvent event)
```

Handles a window resize event.

The default implementation updates the scene viewport to the new window size. If both a camera
and viewport are present, the camera is then recentered to the midpoint of the viewport's world
bounds so the scene remains visually centered after the resize.

- **`event`** — the window resize event containing the new dimensions

#### keyPressed

```java
public void keyPressed(KeyPressEvent event)
```

Handles a key press event.

The base implementation does nothing. Subclasses may override this method to respond to key
presses.

- **`event`** — the key press event

#### keyReleased

```java
public void keyReleased(KeyReleaseEvent event)
```

Handles a key release event.

The base implementation does nothing. Subclasses may override this method to respond to key
releases.

- **`event`** — the key release event

#### mousePressed

```java
public void mousePressed(MousePressEvent event)
```

Handles a mouse press event.

The base implementation does nothing. Subclasses may override this method to respond to mouse
button presses.

- **`event`** — the mouse press event

#### mouseReleased

```java
public void mouseReleased(MouseReleaseEvent event)
```

Handles a mouse release event.

The base implementation does nothing. Subclasses may override this method to respond to mouse
button releases.

- **`event`** — the mouse release event

#### mouseDragged

```java
public void mouseDragged(MouseDragEvent event)
```

Handles a mouse drag event.

The base implementation does nothing. Subclasses may override this method to respond to dragging
while a mouse button is held.

- **`event`** — the mouse drag event

#### mouseMoved

```java
public void mouseMoved(MouseMoveEvent event)
```

Handles a mouse move event.

The base implementation does nothing. Subclasses may override this method to respond to hover
behavior or cursor movement tracking.

- **`event`** — the mouse move event

#### mouseScrolled

```java
public void mouseScrolled(MouseScrollEvent event)
```

Handles a mouse scroll event.

The base implementation does nothing. Subclasses may override this method to respond to mouse
wheel input.

- **`event`** — the mouse scroll event

#### isPaused

```java
public boolean isPaused()
```

Returns whether the scene is currently paused.

**Returns:** true if the scene is paused, otherwise false

#### setPaused

```java
public void setPaused(boolean paused)
```

Sets whether the scene is paused.

A paused scene may still render, but higher-level code can use this state to skip update logic
until the scene is resumed.

- **`paused`** — true to pause the scene, false to resume it

#### getUI

```java
public UIRoot getUI()
```

Returns the UI owned by this scene.

**Returns:** the scene UI, or null if the scene infrastructure has not been initialized

#### getCamera

```java
public Camera getCamera()
```

Returns the camera used by this scene.

**Returns:** the scene camera, or null if no camera is assigned

#### setCamera

```java
public void setCamera(Camera camera)
```

Assigns the scene camera.

- **`camera`** — the camera to assign, or null to remove the current camera

#### getViewport

```java
public Viewport getViewport()
```

Returns the viewport used by this scene.

**Returns:** the scene viewport, or null if the scene infrastructure has not been initialized

#### setViewport

```java
public void setViewport(Viewport viewport)
```

Assigns the viewport used by this scene.

If the scene UI already exists, the same viewport is immediately assigned to the UI so UI
coordinate conversion remains synchronized with the scene.

- **`viewport`** — the viewport to use for this scene

#### getBatch

```java
public TextureBatch getBatch()
```

Returns the texture batch owned by this scene.

**Returns:** the scene batch, or null if the scene infrastructure has not been initialized

#### isInitialized

```java
public boolean isInitialized()
```

Returns whether the shared scene infrastructure has already been initialized.

**Returns:** true if initialization has occurred, otherwise false

</details>

<a id="type-scenekeylistener"></a>

### SceneKeyListener

[Source](../../src/main/java/valthorne/scene/SceneKeyListener.java#L33)

##### SceneKeyListener

`SceneKeyListener` is a small forwarding adapter that connects the engine's global key input
system to a specific `Scene`. Instead of forcing scenes to implement the listener interface
directly, this wrapper receives keyboard events and delegates them to the scene's high-level
scene methods.

This keeps the scene API clean while still allowing scene input registration through the engine's
listener system.

##### Example

```java
Scene scene = new GameplayScene();
SceneKeyListener listener = new SceneKeyListener(scene);

Keyboard.addKeyListener(listener);
```

<details>
<summary>SceneKeyListener operation reference (5 declarations)</summary>

#### Constructor

```java
public SceneKeyListener(Scene scene)
```

Creates a new key listener that forwards events to the supplied scene.

- **`scene`** — the scene that should receive key events

#### keyPressed

```java
@Override
    public void keyPressed(KeyPressEvent event)
```

Forwards a key press event to the current scene.

- **`event`** — the key press event to forward

#### keyReleased

```java
@Override
    public void keyReleased(KeyReleaseEvent event)
```

Forwards a key release event to the current scene.

- **`event`** — the key release event to forward

#### getScene

```java
public Scene getScene()
```

Returns the scene currently receiving forwarded key events.

**Returns:** the current target scene

#### setScene

```java
public void setScene(Scene scene)
```

Changes which scene receives forwarded key events.

- **`scene`** — the new target scene

</details>

<a id="type-scenemouselistener"></a>

### SceneMouseListener

[Source](../../src/main/java/valthorne/scene/SceneMouseListener.java#L35)

##### SceneMouseListener

`SceneMouseListener` is a forwarding adapter that routes mouse button, drag, and move events
from the engine input system into a specific `Scene`. This lets scenes expose clean scene-level
mouse hooks without implementing engine listener interfaces directly.

##### Forwarded events

- `mousePressed(MousePressEvent)`

- `mouseReleased(MouseReleaseEvent)`

- `mouseDragged(MouseDragEvent)`

- `mouseMoved(MouseMoveEvent)`

##### Example

```java
SceneMouseListener listener = new SceneMouseListener(scene);
Mouse.addMouseListener(listener);
```

<details>
<summary>SceneMouseListener operation reference (7 declarations)</summary>

#### Constructor

```java
public SceneMouseListener(Scene scene)
```

Creates a new mouse listener that forwards events to the supplied scene.

- **`scene`** — the target scene that should receive mouse input

#### mousePressed

```java
@Override
    public void mousePressed(MousePressEvent event)
```

Forwards a mouse press event to the current scene.

- **`event`** — the mouse press event to forward

#### mouseReleased

```java
@Override
    public void mouseReleased(MouseReleaseEvent event)
```

Forwards a mouse release event to the current scene.

- **`event`** — the mouse release event to forward

#### mouseDragged

```java
@Override
    public void mouseDragged(MouseDragEvent event)
```

Forwards a mouse drag event to the current scene.

- **`event`** — the mouse drag event to forward

#### mouseMoved

```java
@Override
    public void mouseMoved(MouseMoveEvent event)
```

Forwards a mouse move event to the current scene.

- **`event`** — the mouse move event to forward

#### getScene

```java
public Scene getScene()
```

Returns the scene currently receiving forwarded mouse events.

**Returns:** the current target scene

#### setScene

```java
public void setScene(Scene scene)
```

Changes which scene receives forwarded mouse events.

- **`scene`** — the new target scene

</details>

<a id="type-scenemousescrolllistener"></a>

### SceneMouseScrollListener

[Source](../../src/main/java/valthorne/scene/SceneMouseScrollListener.java#L25)

##### SceneMouseScrollListener

`SceneMouseScrollListener` is a simple forwarding wrapper that routes mouse wheel events
from the engine input system into a specific `Scene`. It exists so scenes can react to
scroll input through scene-level methods rather than implementing the engine listener contract
directly.

##### Example

```java
SceneMouseScrollListener listener = new SceneMouseScrollListener(scene);
Mouse.addScrollListener(listener);
```

<details>
<summary>SceneMouseScrollListener operation reference (4 declarations)</summary>

#### Constructor

```java
public SceneMouseScrollListener(Scene scene)
```

Creates a new scroll listener that forwards events to the supplied scene.

- **`scene`** — the target scene that should receive mouse scroll input

#### mouseScrolled

```java
@Override
    public void mouseScrolled(MouseScrollEvent event)
```

Forwards a mouse scroll event to the current scene.

- **`event`** — the mouse scroll event to forward

#### getScene

```java
public Scene getScene()
```

Returns the scene currently receiving forwarded scroll events.

**Returns:** the current target scene

#### setScene

```java
public void setScene(Scene scene)
```

Changes which scene receives forwarded scroll events.

- **`scene`** — the new target scene

</details>

<a id="type-scenewindowresizelistener"></a>

### SceneWindowResizeListener

[Source](../../src/main/java/valthorne/scene/SceneWindowResizeListener.java#L31)

##### SceneWindowResizeListener

`SceneWindowResizeListener` is a forwarding adapter that routes window resize events into
a specific `Scene`. This allows a scene to react to window size changes through its own
`Scene#windowResized(WindowResizeEvent)` method while keeping the listener registration logic
separate from scene implementations.

##### Typical use

Scenes commonly use resize events to update their `valthorne.viewport.Viewport`, reposition
cameras, rebuild layout, or refresh screen-space UI.

##### Example

```java
SceneWindowResizeListener listener = new SceneWindowResizeListener(scene);
Window.addWindowResizeListener(listener);
```

<details>
<summary>SceneWindowResizeListener operation reference (4 declarations)</summary>

#### Constructor

```java
public SceneWindowResizeListener(Scene scene)
```

Creates a new resize listener that forwards events to the supplied scene.

- **`scene`** — the target scene that should receive resize notifications

#### windowResized

```java
@Override
    public void windowResized(WindowResizeEvent event)
```

Forwards a window resize event to the current scene.

- **`event`** — the resize event to forward

#### getScene

```java
public Scene getScene()
```

Returns the scene currently receiving forwarded resize events.

**Returns:** the current target scene

#### setScene

```java
public void setScene(Scene scene)
```

Changes which scene receives forwarded resize events.

- **`scene`** — the new target scene

</details>

## Related guides

- [Application lifecycle and window management](runtime.md)
- [UI roots, nodes, and input routing](ui-core.md)
- [Viewport scaling and coordinate conversion](viewports.md)
- [Asset loading and caching](assets.md)
