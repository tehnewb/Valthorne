# Application lifecycle and window management

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Start here when embedding Valthorne in a desktop application. `Application` supplies four callbacks; `JGL` establishes the engine and drives them. `Window` exposes the active native window, while `JGLConfiguration` supplies launch settings. Initialize graphics resources after the context exists, update simulation with elapsed seconds, and keep rendering separate from simulation.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Application callbacks | `init` creates application resources, `update(delta)` advances state, `render` draws it, and `dispose` releases what the application owns. |
| Launch configuration | Title, dimensions, context settings, and swap interval describe the initial window and graphics behavior. |
| Main-thread work | `JGL.runTask` provides a route back to engine execution for work that must leave a loading thread. |
| Window controls | Window operations expose sizing, state, and lifecycle of the current native window. |

## Getting started

Follow [dependency and launcher setup](../getting-started.md) first. Java 25 native
launchers need `--enable-native-access=ALL-UNNAMED`, and macOS additionally needs
`-XstartOnFirstThread`. The [minimal application](../getting-started.md#your-first-application)
is the asset-free starting point; [platform support](../platforms.md) describes renderer limits.

1. Implement `Application`, keeping resource references in instance fields.
2. Call `JGL.init(application, title, width, height)` or the configuration overload from your entry point.
3. Create textures, batches, renderers, and UI inside initialization, then update them once per frame.
4. Release application-owned resources in `dispose` while the engine still performs its shutdown lifecycle.

## Usage example

This complete class launches the engine. Add your selected renderer and resources in the callbacks.

```java
import valthorne.Application;
import valthorne.JGL;

public final class MinimalApplication implements Application {
    public static void main(String[] args) {
        JGL.init(new MinimalApplication(), "Valthorne", 800, 600);
    }

    @Override
    public void init() {
        // Create application resources after engine initialization.
    }

    @Override
    public void update(float delta) {
        // Advance simulation using delta seconds.
    }

    @Override
    public void render() {
        // Submit drawing commands with your chosen renderer.
    }

    @Override
    public void dispose() {
        // Release resources created by this application.
    }
}
```

## Ownership and lifecycle

Do not create a second owner for the same OpenGL handle. A CPU asset-loading future is not a graphics context. Schedule uploads onto the engine/context thread. Window logical dimensions and framebuffer pixels can differ on scaled displays.

## Important behavior

- The repository build targets Java 25; use the version declared in `build.gradle` for local compilation.
- A paused simulation and an unrendered application are different decisions: continue drawing when you want a visible pause screen.
- Follow cleanup order from dependents to their backing resources.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Application`](#type-application)
- [`JGL`](#type-jgl)
- [`JGLConfiguration`](#type-jglconfiguration)
- [`SwapInterval`](#type-swapinterval)
- [`Window`](#type-window)

<a id="type-application"></a>

### Application

[Source](../../src/main/java/valthorne/Application.java#L11)

Defines the initialization, frame update, rendering, and cleanup callbacks of a
Valthorne application. Implementations keep application state between callbacks
and own the resources they allocate. Separate state advancement from drawing so
elapsed time is applied once by `update(float)` rather than per draw call.

<details>
<summary>Application operation reference (4 declarations)</summary>

#### init

```java
void init()
```

Initializes the application. This method is called once when the application
starts and should contain initialization logic such as creating resources,
loading assets, and setting up the initial application state.

#### render

```java
void render()
```

Renders the application's graphics. This method is called once per frame
and should contain all rendering logic for displaying the application's
visual content.

#### update

```java
void update(float delta)
```

Updates the application's state. This method is called once per frame
before rendering, with the time elapsed since the last update.

- **`delta`** — The time elapsed since the last update in seconds

#### dispose

```java
void dispose()
```

Cleans up and releases resources used by the application. This method is
called once when the application is shutting down and should properly
dispose of any resources that were allocated during the application's lifecycle.

</details>

<a id="type-jgl"></a>

### JGL

[Source](../../src/main/java/valthorne/JGL.java#L43)

The `JGL` class forms the core of the Valthorne 2D game engine framework.
It operates as a high-level orchestrator responsible for managing the application's lifecycle,
initializing essential subsystems, and executing the game loop. The class is designed
to be versatile, allowing developers to focus on implementing game logic while JGL
handles resource management and low-level operations.

Key capabilities of the `JGL` class include:

- Framework Initialization: Initializes GLFW as a windowing system and configures
systems like audio, input devices (keyboard, mouse), and window display.

- Main Application Loop: Ensures consistent updates to the game state and renders
every frame, calculating delta time and frame rates for precise updates.

- Event-Driven Design: Provides a centralized event bus to facilitate communication
between various components by subscribing, unsubscribing, and broadcasting events.

- Resource Cleanup: At the end of the application's lifecycle, ensures proper
disposal of resources like the input devices, window, and audio systems.

The `JGL` class requires that an `Application` implementation is passed to
provide game-specific lifecycle methods, offering entry points for initialization,
updates, rendering, and cleanup.

<details>
<summary>JGL operation reference (10 declarations)</summary>

#### init

```java
public static void init(Application application, String title, int width, int height)
```

Initializes the application and configures the window settings such as title and dimensions.
This method delegates to another `init` method that accepts a `JGLConfiguration`
object with default settings.

- **`application`** — the `Application` instance containing the logic for the application. It must implement the lifecycle methods defined in the `Application` interface.
- **`title`** — the title of the application window.
- **`width`** — the width of the application window in pixels.
- **`height`** — the height of the application window in pixels.

**Throws `NullPointerException`:** if `application` is `null`.

#### init

```java
public static void init(Application application, JGLConfiguration config)
```

Initializes the application and its associated systems. This method sets up the
necessary components for the application to run, including input devices, audio,
and the rendering window. It also initializes the provided `Application`
instance, starts the application's main loop, handles frame updates, and ensures
proper cleanup upon exit.

- **`application`** — the `Application` instance containing the logic for the application. It must implement the lifecycle methods defined in the `Application` interface.
- **`config`** — the `JGLConfiguration` object containing the settings for the application, such as window size, title, and rendering options.

**Throws `NullPointerException`:** if `application` or `config` is `null`.

**Throws `IllegalStateException`:** if the GLFW library could not be initialized.

#### runTask

```java
public static void runTask(Runnable task)
```

Schedules a task for execution. The provided `Runnable` task will
be added to the task list for processing. If the specified task is null,
a `NullPointerException` is thrown.

- **`task`** — the `Runnable` task to be added to the task list

**Throws `NullPointerException`:** if `task` is null

#### subscribe

```java
public static <T extends Event> void subscribe(EventType<T> eventType, EventHandler<? super T> listener)
```

Registers an `EventHandler` for one numeric event route.

- **`<T>`** — the event payload type routed by `eventType`
- **`eventType`** — the concrete event route to subscribe to
- **`listener`** — the handler that should receive publications on that route

**Throws `NullPointerException`:** if `eventType` or `listener` is null

#### subscribe

```java
public static <T extends Event> void subscribe(EventType<T> eventType, int priority, EventHandler<? super T> listener)
```

Registers an `EventHandler` with an explicit priority.

- **`<T>`** — the event payload type routed by `eventType`
- **`eventType`** — the concrete event route to subscribe to
- **`priority`** — larger values run first
- **`listener`** — the handler that should receive publications on that route

**Throws `NullPointerException`:** if `eventType` or `listener` is null

#### unsubscribe

```java
public static <T extends Event> void unsubscribe(EventType<T> eventType, EventHandler<? super T> listener)
```

Unregisters an `EventHandler` from one numeric event route.

- **`<T>`** — the event payload type routed by `eventType`
- **`eventType`** — the concrete event route to unsubscribe from
- **`listener`** — the exact handler instance to remove

**Throws `NullPointerException`:** if `eventType` or `listener` is null

#### publish

```java
public static void publish(Event event)
```

Publishes the specified `Event` to the event bus, allowing all
registered listeners for the event's type to handle it. If the event
is null, a `NullPointerException` is thrown.

- **`event`** — the event to be published; must not be null

**Throws `NullPointerException`:** if the provided event is null

#### getTime

```java
public static float getTime()
```

Retrieves the current time in seconds since the GLFW timer was initialized.

**Returns:** The current time in seconds as a float.

#### getFramesPerSecond

```java
public static short getFramesPerSecond()
```

Retrieves the current frames per second (FPS) value, providing an
indication of the application's performance and rendering speed.

**Returns:** The current frames per second as a double.

#### getDeltaTime

```java
public static float getDeltaTime()
```

Retrieves the time elapsed between the current frame and the previous frame.
This value is typically used to calculate frame-dependent operations, such as
animations or physics updates, ensuring consistent behavior regardless of frame rate.

**Returns:** The time difference (delta time) in seconds as a double.

</details>

<a id="type-jglconfiguration"></a>

### JGLConfiguration

[Source](../../src/main/java/valthorne/JGLConfiguration.java#L83)

`JGLConfiguration` is the central fluent configuration object used to define
how Valthorne should create and initialize its GLFW window and OpenGL context.
It collects window metadata, framebuffer settings, context creation flags,
platform-specific hint strings, swap behavior, and any extra custom GLFW hints
before those settings are finally applied through `applyWindowHints()`.

This class is designed to be a builder-style configuration container. Rather than
forcing callers to pass a very large constructor argument list, it exposes a large
collection of small fluent setter methods such as `title(String)`,
`size(int, int)`, `samples(int)`, `contextVersion(int, int)`,
and `swapInterval(SwapInterval)`. Each of these methods mutates the current
configuration and returns the same instance so configuration code remains readable
and chainable.

The configuration stores values for several distinct categories of GLFW state:

- window presentation settings such as title, size, visibility, decorations, and resizability

- framebuffer precision settings such as color bits, depth bits, stencil bits, and accumulation bits

- OpenGL context settings such as version, profile, debug flags, and robustness behavior

- platform-specific string hints for Cocoa and X11

- swap interval preferences through `SwapInterval`

- arbitrary extra GLFW hints stored in `extraHints`

The class also exposes a convenience factory through `defaults()`.
That method returns a fresh configuration instance each time so callers can use
it as a safe baseline without accidentally sharing mutable startup state.

The actual application of stored values happens inside `applyWindowHints()`.
That method resets GLFW to its default hint state by calling
`glfwDefaultWindowHints()` and then pushes all stored configuration values
into GLFW through the appropriate `glfwWindowHint` and
`glfwWindowHintString` calls.

##### Example Usage

```java
JGLConfiguration configuration = new JGLConfiguration()
        .title("My Game")
        .size(1920, 1080)
        .samples(4)
        .resizable(true)
        .visible(true)
        .contextVersion(3, 3)
        .openglProfile(GLFW_OPENGL_CORE_PROFILE)
        .openglDebugContext(true)
        .swapInterval(SwapInterval.VSYNC)
        .depthBits(24)
        .stencilBits(8)
        .srgbCapable(true);

JGL.init(application, configuration);
```

This example demonstrates the intended full usage pattern for the class:
create a configuration, customize the properties you care about, inspect them
if needed, and finally apply the stored values before creating the GLFW window.

<details>
<summary>JGLConfiguration operation reference (81 declarations)</summary>

#### defaults

```java
public static JGLConfiguration defaults()
```

Returns a fresh default configuration instance.

**Returns:** a new configuration initialized with Valthorne's baseline defaults

#### getTitle

```java
public String getTitle()
```

Returns the configured window title.

**Returns:** the configured title string

#### getWidth

```java
public int getWidth()
```

Returns the configured initial window width.

**Returns:** the configured width in pixels

#### getHeight

```java
public int getHeight()
```

Returns the configured initial window height.

**Returns:** the configured height in pixels

#### getSamples

```java
public int getSamples()
```

Returns the requested multisample sample count.

**Returns:** the configured sample count

#### isVisible

```java
public boolean isVisible()
```

Returns whether the window should start visible.

**Returns:** `true` if the window should start visible

#### isResizable

```java
public boolean isResizable()
```

Returns whether the window should be resizable.

**Returns:** `true` if resizing is enabled

#### isDoubleBuffer

```java
public boolean isDoubleBuffer()
```

Returns whether double buffering should be enabled.

**Returns:** `true` if double buffering is enabled

#### isContextNoError

```java
public boolean isContextNoError()
```

Returns whether the context should request no-error mode.

**Returns:** `true` if no-error mode is requested

#### isFullscreen

```java
public boolean isFullscreen()
```

Returns whether fullscreen mode is requested.

This class stores the fullscreen preference, although actual fullscreen window
creation is normally handled at the point where the window is created.

**Returns:** `true` if fullscreen mode is requested

#### isMaximized

```java
public boolean isMaximized()
```

Returns whether the window should start maximized.

**Returns:** `true` if the window should start maximized

#### isFocused

```java
public boolean isFocused()
```

Returns whether the window should start focused.

**Returns:** `true` if initial focus is requested

#### isFocusOnShow

```java
public boolean isFocusOnShow()
```

Returns whether the window should receive focus when shown.

**Returns:** `true` if focus-on-show is enabled

#### isDecorated

```java
public boolean isDecorated()
```

Returns whether the window should use normal decorations.

**Returns:** `true` if decorations are enabled

#### isFloating

```java
public boolean isFloating()
```

Returns whether the window should float above normal windows.

**Returns:** `true` if floating mode is enabled

#### isTransparentFramebuffer

```java
public boolean isTransparentFramebuffer()
```

Returns whether a transparent framebuffer is requested.

**Returns:** `true` if a transparent framebuffer is requested

#### isScaleToMonitor

```java
public boolean isScaleToMonitor()
```

Returns whether scale-to-monitor behavior is requested.

**Returns:** `true` if scale-to-monitor is enabled

#### isSrgbCapable

```java
public boolean isSrgbCapable()
```

Returns whether an sRGB-capable framebuffer is requested.

**Returns:** `true` if sRGB capability is requested

#### isCocoaRetinaFramebuffer

```java
public boolean isCocoaRetinaFramebuffer()
```

Returns whether Retina framebuffer support is enabled for Cocoa.

**Returns:** `true` if Cocoa Retina framebuffer support is enabled

#### getRefreshRate

```java
public int getRefreshRate()
```

Returns the requested refresh rate hint.

**Returns:** the configured refresh rate, or `GLFW_DONT_CARE`

#### getSwapInterval

```java
public SwapInterval getSwapInterval()
```

Returns the configured swap interval preference.

**Returns:** the configured swap interval

#### getRedBits

```java
public int getRedBits()
```

Returns the requested red channel precision.

**Returns:** the requested red channel bits

#### getGreenBits

```java
public int getGreenBits()
```

Returns the requested green channel precision.

**Returns:** the requested green channel bits

#### getBlueBits

```java
public int getBlueBits()
```

Returns the requested blue channel precision.

**Returns:** the requested blue channel bits

#### getAlphaBits

```java
public int getAlphaBits()
```

Returns the requested alpha channel precision.

**Returns:** the requested alpha channel bits

#### getDepthBits

```java
public int getDepthBits()
```

Returns the requested depth buffer precision.

**Returns:** the requested depth buffer bits

#### getStencilBits

```java
public int getStencilBits()
```

Returns the requested stencil buffer precision.

**Returns:** the requested stencil buffer bits

#### getAccumRedBits

```java
public int getAccumRedBits()
```

Returns the requested accumulation red channel precision.

**Returns:** the requested accumulation red bits

#### getAccumGreenBits

```java
public int getAccumGreenBits()
```

Returns the requested accumulation green channel precision.

**Returns:** the requested accumulation green bits

#### getAccumBlueBits

```java
public int getAccumBlueBits()
```

Returns the requested accumulation blue channel precision.

**Returns:** the requested accumulation blue bits

#### getAccumAlphaBits

```java
public int getAccumAlphaBits()
```

Returns the requested accumulation alpha channel precision.

**Returns:** the requested accumulation alpha bits

#### getAuxBuffers

```java
public int getAuxBuffers()
```

Returns the requested auxiliary buffer count.

**Returns:** the requested auxiliary buffer count

#### getContextVersionMajor

```java
public int getContextVersionMajor()
```

Returns the requested OpenGL context major version.

**Returns:** the configured major version

#### getContextVersionMinor

```java
public int getContextVersionMinor()
```

Returns the requested OpenGL context minor version.

**Returns:** the configured minor version

#### getOpenglProfile

```java
public int getOpenglProfile()
```

Returns the requested OpenGL profile hint.

**Returns:** the configured GLFW OpenGL profile constant

#### getOpenglForwardCompat

```java
public int getOpenglForwardCompat()
```

Returns the raw GLFW forward-compatibility hint value.

**Returns:** `GLFW_TRUE` or `GLFW_FALSE`

#### getOpenglDebugContext

```java
public int getOpenglDebugContext()
```

Returns the raw GLFW debug-context hint value.

**Returns:** `GLFW_TRUE` or `GLFW_FALSE`

#### getOpenglRobustness

```java
public int getOpenglRobustness()
```

Returns the configured OpenGL robustness hint.

**Returns:** the configured GLFW robustness constant

#### getContextReleaseBehavior

```java
public int getContextReleaseBehavior()
```

Returns the configured context release behavior hint.

**Returns:** the configured GLFW context release behavior constant

#### getContextCreationApi

```java
public int getContextCreationApi()
```

Returns the configured context creation API hint.

**Returns:** the configured GLFW context creation API constant

#### getClientApi

```java
public int getClientApi()
```

Returns the configured client API hint.

**Returns:** the configured GLFW client API constant

#### getCocoaFrameName

```java
public String getCocoaFrameName()
```

Returns the optional Cocoa frame name hint.

**Returns:** the Cocoa frame name, or `null` if none is set

#### getX11ClassName

```java
public String getX11ClassName()
```

Returns the optional X11 class name hint.

**Returns:** the X11 class name, or `null` if none is set

#### getX11InstanceName

```java
public String getX11InstanceName()
```

Returns the optional X11 instance name hint.

**Returns:** the X11 instance name, or `null` if none is set

#### getExtraHints

```java
public Map<Integer, Integer> getExtraHints()
```

Returns the map of extra raw GLFW hints that will be applied after the built-in hints.

The returned map is the live backing map, so callers may add, remove, or replace
entries directly if they want full manual control over additional hint values.

**Returns:** the live extra hint map

#### title

```java
public JGLConfiguration title(String title)
```

Sets the window title.

- **`title`** — the new title string

**Returns:** this configuration instance

#### size

```java
public JGLConfiguration size(int width, int height)
```

Sets the initial window size.

- **`width`** — the desired window width in pixels
- **`height`** — the desired window height in pixels

**Returns:** this configuration instance

#### samples

```java
public JGLConfiguration samples(int samples)
```

Sets the requested multisample anti-aliasing sample count.

Negative values are clamped to `0`.

- **`samples`** — the requested sample count

**Returns:** this configuration instance

#### visible

```java
public JGLConfiguration visible(boolean visible)
```

Sets whether the window should start visible.

- **`visible`** — whether the window should start visible

**Returns:** this configuration instance

#### resizable

```java
public JGLConfiguration resizable(boolean resizable)
```

Sets whether the window should be resizable.

- **`resizable`** — whether the window should be resizable

**Returns:** this configuration instance

#### doubleBuffer

```java
public JGLConfiguration doubleBuffer(boolean doubleBuffer)
```

Sets whether double buffering should be enabled.

- **`doubleBuffer`** — whether double buffering should be enabled

**Returns:** this configuration instance

#### contextNoError

```java
public JGLConfiguration contextNoError(boolean contextNoError)
```

Sets whether the OpenGL context should request no-error mode.

- **`contextNoError`** — whether no-error mode should be requested

**Returns:** this configuration instance

#### fullscreen

```java
public JGLConfiguration fullscreen(boolean fullscreen)
```

Sets whether fullscreen mode should be requested.

This value is stored in the configuration and is typically interpreted later
by whatever code is actually responsible for choosing the monitor and creating
the GLFW window.

- **`fullscreen`** — whether fullscreen mode should be requested

**Returns:** this configuration instance

#### maximized

```java
public JGLConfiguration maximized(boolean maximized)
```

Sets whether the window should start maximized.

- **`maximized`** — whether the window should start maximized

**Returns:** this configuration instance

#### focused

```java
public JGLConfiguration focused(boolean focused)
```

Sets whether the window should start focused.

- **`focused`** — whether the window should start focused

**Returns:** this configuration instance

#### focusOnShow

```java
public JGLConfiguration focusOnShow(boolean focusOnShow)
```

Sets whether the window should receive focus when shown.

- **`focusOnShow`** — whether focus-on-show should be enabled

**Returns:** this configuration instance

#### decorated

```java
public JGLConfiguration decorated(boolean decorated)
```

Sets whether the window should use standard decorations.

- **`decorated`** — whether window decorations should be enabled

**Returns:** this configuration instance

#### floating

```java
public JGLConfiguration floating(boolean floating)
```

Sets whether the window should float above normal windows.

- **`floating`** — whether the window should float

**Returns:** this configuration instance

#### transparentFramebuffer

```java
public JGLConfiguration transparentFramebuffer(boolean transparentFramebuffer)
```

Sets whether the framebuffer should support transparency.

- **`transparentFramebuffer`** — whether a transparent framebuffer should be requested

**Returns:** this configuration instance

#### scaleToMonitor

```java
public JGLConfiguration scaleToMonitor(boolean scaleToMonitor)
```

Sets whether the window should scale to the monitor.

- **`scaleToMonitor`** — whether scale-to-monitor should be enabled

**Returns:** this configuration instance

#### srgbCapable

```java
public JGLConfiguration srgbCapable(boolean srgbCapable)
```

Sets whether an sRGB-capable framebuffer should be requested.

- **`srgbCapable`** — whether sRGB framebuffer capability should be requested

**Returns:** this configuration instance

#### cocoaRetinaFramebuffer

```java
public JGLConfiguration cocoaRetinaFramebuffer(boolean cocoaRetinaFramebuffer)
```

Sets whether Cocoa Retina framebuffers should be enabled.

- **`cocoaRetinaFramebuffer`** — whether Retina framebuffer support should be enabled

**Returns:** this configuration instance

#### refreshRate

```java
public JGLConfiguration refreshRate(int refreshRate)
```

Sets the requested refresh rate hint.

- **`refreshRate`** — the desired refresh rate or `GLFW_DONT_CARE`

**Returns:** this configuration instance

#### swapInterval

```java
public JGLConfiguration swapInterval(SwapInterval swapInterval)
```

Sets the preferred swap interval mode.

- **`swapInterval`** — the desired swap interval behavior

**Returns:** this configuration instance

#### colorBits

```java
public JGLConfiguration colorBits(int redBits, int greenBits, int blueBits, int alphaBits)
```

Sets the requested framebuffer color channel precision.

- **`redBits`** — requested red channel bits
- **`greenBits`** — requested green channel bits
- **`blueBits`** — requested blue channel bits
- **`alphaBits`** — requested alpha channel bits

**Returns:** this configuration instance

#### depthBits

```java
public JGLConfiguration depthBits(int depthBits)
```

Sets the requested depth buffer precision.

- **`depthBits`** — the requested depth buffer bits

**Returns:** this configuration instance

#### stencilBits

```java
public JGLConfiguration stencilBits(int stencilBits)
```

Sets the requested stencil buffer precision.

- **`stencilBits`** — the requested stencil buffer bits

**Returns:** this configuration instance

#### accumBits

```java
public JGLConfiguration accumBits(int redBits, int greenBits, int blueBits, int alphaBits)
```

Sets the requested accumulation buffer precision.

- **`redBits`** — requested accumulation red bits
- **`greenBits`** — requested accumulation green bits
- **`blueBits`** — requested accumulation blue bits
- **`alphaBits`** — requested accumulation alpha bits

**Returns:** this configuration instance

#### auxBuffers

```java
public JGLConfiguration auxBuffers(int auxBuffers)
```

Sets the requested auxiliary buffer count.

- **`auxBuffers`** — the requested auxiliary buffer count

**Returns:** this configuration instance

#### contextVersion

```java
public JGLConfiguration contextVersion(int major, int minor)
```

Sets the requested OpenGL context version.

- **`major`** — the requested major version
- **`minor`** — the requested minor version

**Returns:** this configuration instance

#### openglProfile

```java
public JGLConfiguration openglProfile(int openglProfile)
```

Sets the requested OpenGL profile hint.

- **`openglProfile`** — the GLFW profile constant to request

**Returns:** this configuration instance

#### openglForwardCompat

```java
public JGLConfiguration openglForwardCompat(boolean value)
```

Sets whether a forward-compatible OpenGL context should be requested.

The stored value is converted into `GLFW_TRUE` or `GLFW_FALSE`
because that is what GLFW expects when hints are applied.

- **`value`** — whether forward compatibility should be enabled

**Returns:** this configuration instance

#### openglDebugContext

```java
public JGLConfiguration openglDebugContext(boolean value)
```

Sets whether an OpenGL debug context should be requested.

The stored value is converted into `GLFW_TRUE` or `GLFW_FALSE`.

- **`value`** — whether a debug context should be requested

**Returns:** this configuration instance

#### openglRobustness

```java
public JGLConfiguration openglRobustness(int openglRobustness)
```

Sets the requested OpenGL robustness hint.

- **`openglRobustness`** — the GLFW robustness constant to request

**Returns:** this configuration instance

#### contextReleaseBehavior

```java
public JGLConfiguration contextReleaseBehavior(int contextReleaseBehavior)
```

Sets the requested context release behavior hint.

- **`contextReleaseBehavior`** — the GLFW context release behavior constant

**Returns:** this configuration instance

#### contextCreationApi

```java
public JGLConfiguration contextCreationApi(int contextCreationApi)
```

Sets the requested context creation API hint.

- **`contextCreationApi`** — the GLFW context creation API constant

**Returns:** this configuration instance

#### clientApi

```java
public JGLConfiguration clientApi(int clientApi)
```

Sets the requested client API hint.

- **`clientApi`** — the GLFW client API constant

**Returns:** this configuration instance

#### cocoaFrameName

```java
public JGLConfiguration cocoaFrameName(String cocoaFrameName)
```

Sets the optional Cocoa frame name hint.

- **`cocoaFrameName`** — the frame autosave name to use on macOS

**Returns:** this configuration instance

#### x11ClassName

```java
public JGLConfiguration x11ClassName(String x11ClassName)
```

Sets the optional X11 class name hint.

- **`x11ClassName`** — the X11 class name to advertise

**Returns:** this configuration instance

#### x11InstanceName

```java
public JGLConfiguration x11InstanceName(String x11InstanceName)
```

Sets the optional X11 instance name hint.

- **`x11InstanceName`** — the X11 instance name to advertise

**Returns:** this configuration instance

#### hint

```java
public JGLConfiguration hint(int hint, int value)
```

Adds or replaces an extra raw GLFW integer hint.

Extra hints are applied after all built-in hints inside
`applyWindowHints()`, which means they can be used to augment or even
override a previously assigned standard hint if the same GLFW hint constant is used.

- **`hint`** — the GLFW hint constant
- **`value`** — the GLFW hint value

**Returns:** this configuration instance

</details>

<a id="type-swapinterval"></a>

### SwapInterval

[Source](../../src/main/java/valthorne/SwapInterval.java#L11)

Represents available swap interval (VSync) settings.
Values are passed to the window's buffer-swap configuration. Positive intervals
request a number of display refreshes between swaps; the achieved frame rate also
depends on rendering time, the platform, and driver settings.

<details>
<summary>SwapInterval operation reference (6 declarations)</summary>

#### OFF

```java
public static final  SwapInterval OFF
```

VSync disabled \u2014 uncapped FPS

#### VSYNC

```java
public static final  SwapInterval VSYNC
```

Standard VSync \u2014 syncs every frame (\u224860 FPS on a 60Hz monitor)

#### HALF

```java
public static final  SwapInterval HALF
```

Half refresh rate \u2014 syncs every 2 frames (\u224830 FPS on a 60Hz monitor)

#### TRIPLE

```java
public static final  SwapInterval TRIPLE
```

One third of the refresh rate \u2014 swaps every 3 refreshes (about 20 FPS at 60 Hz).

#### ADAPTIVE

```java
public static final  SwapInterval ADAPTIVE
```

Adaptive VSync \u2014 driver-dependent (may act like 0 or 1)

#### getValue

```java
public int getValue()
```

Returns the native value to supply when configuring buffer swaps.
Reading the value has no effect on the active graphics context.

**Returns:** this setting's swap interval

</details>

<a id="type-window"></a>

### Window

[Source](../../src/main/java/valthorne/Window.java#L46)

GLFW window wrapper for Valthorne.

##### Example

```java
// These are called inside your Application.init() method
Window.setSwapInterval(SwapInterval.ON); // vsync
Window.setResizable(true);
Window.center();

Window.addWindowResizeListener(evt -> {
    System.out.println("Resize: " + evt.getOldWidth() + "x" + evt.getOldHeight()
            + " -> " + evt.getNewWidth() + "x" + evt.getNewHeight());
});
```

This class is a static utility. It owns the GLFW window handle, registers GLFW callbacks,
manages cached window state (size/position/fullscreen/etc.), and publishes engine events such as
`WindowResizeEvent` through `JGL`.

<details>
<summary>Window operation reference (38 declarations)</summary>

#### init

```java
public static void init(JGLConfiguration config)
```

Initializes the application window with the given configuration parameters.
Configures the OpenGL context and GLFW window hints and creates the application window.
It sets up various callbacks for handling resize, position, focus, and other window events.
The method also ensures proper rendering capabilities, including enabling multisampling and
blending if required, based on the configuration.

- **`config`** — the `JGLConfiguration` object containing all the configuration parameters required for setting up the application window, such as dimensions, fullscreen mode, OpenGL settings, and additional hints. This parameter must not be null. If null, a `NullPointerException` will be thrown.

#### clear

```java
public static void clear(Color color)
```

Clears the current OpenGL framebuffer using the specified color.

This sets the clear color to the RGBA values of the provided `Color` object
and clears the color buffer bit, effectively resetting the frame for new drawing.

- **`color`** — the color to use for clearing the framebuffer; must not be null

#### clear3D

```java
public static void clear3D(Color color)
```

Clears color and depth for a new 3D frame, even after a pass disabled depth writes.

#### addWindowResizeListener

```java
public static void addWindowResizeListener(WindowResizeListener listener)
```

Adds a window resize listener.

This subscribes the listener directly to `EventTypes#WINDOW_RESIZE` via `JGL`.

- **`listener`** — resize listener (must be non-null)

**Throws `NullPointerException`:** if `listener` is null

#### addWindowResizeListener

```java
public static void addWindowResizeListener(WindowResizeListener listener, int priority)
```

Adds a window resize listener with an explicit execution priority.

- **`listener`** — resize listener (must be non-null)
- **`priority`** — execution priority; higher values execute earlier

**Throws `NullPointerException`:** if `listener` is null

#### removeWindowResizeListener

```java
public static void removeWindowResizeListener(WindowResizeListener listener)
```

Removes a window resize listener.

This unsubscribes the listener from `EventTypes#WINDOW_RESIZE` via `JGL`.

- **`listener`** — resize listener (must be non-null)

**Throws `NullPointerException`:** if `listener` is null

#### setTitle

```java
public static void setTitle(String newTitle)
```

Sets the window title.

This delegates to `GLFW#glfwSetWindowTitle(long, CharSequence)`.

- **`newTitle`** — new window title

#### setSize

```java
public static void setSize(int width, int height)
```

Sets the window size.

This requests a size change; GLFW will typically call your size callback, which updates cached
`width` and `height` and updates the OpenGL projection.

- **`width`** — new window width (pixels)
- **`height`** — new window height (pixels)

#### getWidth

```java
public static int getWidth()
```

Returns the cached window width.

**Returns:** window width in pixels

#### getHeight

```java
public static int getHeight()
```

Returns the cached window height.

**Returns:** window height in pixels

#### getX

```java
public static int getX()
```

Returns the cached window X position.

**Returns:** window X in screen coordinates

#### getY

```java
public static int getY()
```

Returns the cached window Y position.

**Returns:** window Y in screen coordinates

#### getFramebufferSize

```java
public static int[] getFramebufferSize()
```

Queries the framebuffer size.

On HiDPI displays, the framebuffer size can differ from `getWidth()`/`getHeight()`.

**Returns:** array {framebufferWidth, framebufferHeight}

#### getSwapInterval

```java
public static SwapInterval getSwapInterval()
```

Returns the current swap interval.

**Returns:** swap interval enum

#### setSwapInterval

```java
public static void setSwapInterval(SwapInterval type)
```

Sets the swap interval (VSync).

This updates the cached `swapInterval` and immediately calls `GLFW#glfwSwapInterval(int)`.

- **`type`** — swap interval enum (null is ignored)

#### setPosition

```java
public static void setPosition(int x, int y)
```

Sets the window position (top-left corner) in screen coordinates.

This also updates the cached `x`/`y` immediately so callers see the new values
even before the GLFW position callback runs.

- **`x`** — new window X
- **`y`** — new window Y

#### isFullscreen

```java
public static boolean isFullscreen()
```

Returns whether the window is currently marked fullscreen.

**Returns:** true if fullscreen

#### setFullscreen

```java
public static void setFullscreen(boolean fullscreen)
```

Sets fullscreen mode using the primary monitor.

When entering fullscreen, this sets the window monitor to the primary monitor and uses the monitor's
current video mode dimensions and refresh rate.

When leaving fullscreen, this restores the window to its cached position and size.

- **`fullscreen`** — true for fullscreen, false for windowed

**Throws `RuntimeException`:** if the primary monitor video mode cannot be queried

#### isBorderless

```java
public static boolean isBorderless()
```

Returns whether the window is currently marked borderless/undecorated.

**Returns:** true if borderless

#### setBorderless

```java
public static void setBorderless(boolean borderless)
```

Sets whether the window is borderless (undecorated).

This updates both the cached `borderless` flag and the GLFW window attribute.

- **`borderless`** — true for borderless, false for normal window decorations

#### isResizable

```java
public static boolean isResizable()
```

Returns whether the window is currently marked resizable.

**Returns:** true if resizable

#### setResizable

```java
public static void setResizable(boolean resizable)
```

Sets whether the window is resizable.

This updates both the cached `resizable` flag and the GLFW window attribute.

- **`resizable`** — true to allow resizing, false to lock the window size

#### toggleFullscreen

```java
public static void toggleFullscreen()
```

Toggles fullscreen mode.

This calls `setFullscreen(boolean)`. Note that `setFullscreen(boolean)`
already updates `fullscreen`.

#### minimize

```java
public static void minimize()
```

Minimizes (iconifies) the window.

No-op if the window has not been created.

#### maximize

```java
public static void maximize()
```

Maximizes the window.

No-op if the window has not been created.

#### restore

```java
public static void restore()
```

Restores the window from minimized/maximized state.

No-op if the window has not been created.

#### focus

```java
public static void focus()
```

Brings focus to the window.

No-op if the window has not been created.

#### requestClose

```java
public static void requestClose()
```

Requests the window to close.

This sets the GLFW should-close flag. Your engine loop should check it and exit cleanly.

#### setAlwaysOnTop

```java
public static void setAlwaysOnTop(boolean value)
```

Sets whether the window should stay above other windows.

This uses the GLFW floating attribute.

- **`value`** — true to keep on top, false to allow normal z-ordering

#### setOpacity

```java
public static void setOpacity(float opacity)
```

Sets the window opacity (where supported).

Values are clamped to [0, 1]. On platforms that do not support opacity, GLFW may ignore it.

- **`opacity`** — opacity in [0..1]

#### setIcon

```java
public static void setIcon(TextureData texture)
```

Sets the icon of the window using the provided texture data.

- **`texture`** — the texture data that defines the icon. It cannot be null. The data should include the width, height, and pixel buffer of the image to be used as the window's icon.

**Throws `NullPointerException`:** if the texture parameter is null.

#### center

```java
public static void center()
```

Centers the window on the primary monitor.

This uses the current cached window size to compute the center position.

#### setSizeLimits

```java
public static void setSizeLimits(int minW, int minH, int maxW, int maxH)
```

Sets minimum and maximum window size limits.

GLFW uses `GLFW_DONT_CARE` if you want to disable a bound.

- **`minW`** — minimum width
- **`minH`** — minimum height
- **`maxW`** — maximum width
- **`maxH`** — maximum height

#### getAddress

```java
public static long getAddress()
```

Returns the underlying GLFW window handle.

**Returns:** GLFW window handle (0/NULL if not created)

#### getProjectionMatrix

```java
public static float[] getProjectionMatrix()
```

Returns the engine-managed projection matrix values used by shader-based render paths.

The returned array is a reused snapshot of the current projection state.
Callers should treat it as read-only.

**Returns:** the current projection matrix values in column-major order

#### setProjectionMatrix

```java
public static void setProjectionMatrix(float[] matrixData)
```

Replaces the engine-managed projection matrix with the supplied column-major matrix values.

This does not update OpenGL's fixed-function matrix stack directly. It exists so
shader-driven renderers can follow the same logical viewport/camera state as legacy
code while the engine transitions away from implicit OpenGL matrices.

- **`matrixData`** — projection values in column-major order

**Throws `NullPointerException`:** if `matrixData` is null

**Throws `IllegalArgumentException`:** if `matrixData.length < 16`

#### copyProjectionMatrix

```java
public static void copyProjectionMatrix(float[] destination)
```

Copies the current engine-managed projection matrix into the supplied destination array.

- **`destination`** — destination array that must contain room for 16 floats

**Throws `NullPointerException`:** if `destination` is null

**Throws `IllegalArgumentException`:** if `destination.length < 16`

#### getDimensional

```java
public static Dimensional getDimensional()
```

Returns the shared window adapter. Its getters expose a zero content origin
and current content dimensions, while position setters move the desktop window
and size setters resize it, truncating float inputs to integers.

**Returns:** live shared dimensional adapter

</details>

## Related guides

- [Scenes and game screens](scenes.md)
- [Asset loading and caching](assets.md)
- [Keyboard, mouse, and cursor input](input.md)
- [Ticks and frame timing](timing.md)
