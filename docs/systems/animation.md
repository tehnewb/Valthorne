# Frame and transform animation

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Frame animation selects an AnimationFrame over time; transform animation changes a 3D object's pose. Both belong in simulation updates rather than being advanced separately for each render pass. Listeners expose playback events so game logic can react without deriving state from a drawn image.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Frame timing | Per-frame durations control when the current frame advances. |
| Playback direction | Forward, reverse, and bidirectional modes define traversal. |
| Looping and completion | Loop limits, pause, and finish state determine whether playback continues. |
| Listeners | Callbacks expose frame and lifecycle changes. |
| 3D transforms | TransformAnimation3D interpolates pose data, including quaternion orientation. |

## Getting started

1. Create frames or transform keys and establish durations and the target object.
2. Configure direction, speed, looping, and listeners before playback.
3. Advance once using the chosen simulation delta.
4. Render the currently selected frame or transformed model in every applicable render pass without advancing again.

## Ownership and lifecycle

Animation generally borrows visual resources. Stopping an animation does not dispose its textures or model. Listener callbacks run synchronously and should not create unintended recursive state changes.

## Important behavior

- Zero or unusual durations use the implementation's documented handling; validate authored content.
- Pause and finished state are separate.
- Use the same time policy as the object being animated, especially when gameplay is paused.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Animation`](#type-animation)
- [`AnimationAdapter`](#type-animationadapter)
- [`AnimationFrame`](#type-animationframe)
- [`AnimationListener`](#type-animationlistener)
- [`AnimationUtility`](#type-animationutility)
- [`PlaybackMode`](#type-playbackmode)
- [`TransformAnimation3D`](#type-transformanimation3d)
- [`TransformAnimation3D.Keyframe`](#type-transformanimation3d-keyframe)

<a id="type-animation"></a>

### Animation

[Source](../../src/main/java/valthorne/graphics/animation/Animation.java#L80)

A lightweight, time-driven frame animation player.

##### Example

```java
// Build frames (each frame has a Drawable + a duration in seconds).
Animation walk = new Animation(
        PlaybackMode.FORWARD,
        new AnimationFrame(walk0, 0.08f),
        new AnimationFrame(walk1, 0.08f),
        new AnimationFrame(walk2, 0.08f),
        new AnimationFrame(walk3, 0.08f)
);

// Configure.
walk.setLooping(true);     // keep looping forever
walk.setSpeed(1.0f);       // 1 = normal speed

// Optional: events.
walk.setListener(new AnimationListener() {
    @Override public void onFrameChanged(Animation a, int from, int to) {
        // Example: play a footstep sound on certain frames.
        // if (to == 1 || to == 3) footstep.play();
    }
    @Override public void onFinished(Animation a) {
        // Example: switch state, fire event, etc.
    }
    @Override public void onLoop(Animation a, int loopsCompleted) {
        // Example: count loops for debugging.
    }
});

TextureBatch batch = new TextureBatch(512, 50);

// Game loop usage.
walk.update(deltaSeconds);
walk.draw(batch, playerX, playerY, 64, 64);

// Seek (optional).
walk.setTime(0.15f);       // jump to time in seconds along the timeline
walk.setFrame(2);          // force a specific frame
walk.pause();              // pause update
walk.play();               // resume update
```

##### What this class does

- Stores an ordered array of `AnimationFrame` objects (a `Drawable` + duration).

- Advances time each `update(float)` call and changes frames when their durations elapse.

- Supports multiple playback behaviors via `PlaybackMode`.

- Supports looping forever or for a fixed number of loops.

- Exposes optional callbacks via `AnimationListener`.

##### Playback modes

- `PlaybackMode#FORWARD`: 0 \u2192 1 \u2192 2 \u2192 ... \u2192 last

- `PlaybackMode#REVERSE`: last \u2192 ... \u2192 2 \u2192 1 \u2192 0

- `PlaybackMode#BIDIRECTIONAL`: ping-pongs: 0 \u2192 ... \u2192 last \u2192 ... \u2192 0

##### State flags

This class uses `ByteBits` to pack common booleans into one byte:
paused, looping, returning (ping-pong), and finished.

**Important:** If `frames` is null/empty, `update(float)` and `draw(TextureBatch, float, float, float, float)`
safely no-op and query methods return safe defaults.

<details>
<summary>Animation operation reference (34 declarations)</summary>

#### Constructor

```java
public Animation(PlaybackMode playbackMode, AnimationFrame... frames)
```

Creates an `Animation` using a playback mode and a frame list.

Construction behavior:

- Stores the provided frame array reference (no copy).

- Starts at index 0 by default (unless later `reset()` changes it for REVERSE).

- Precomputes `totalDuration` as the sum of all non-null frame durations.

Note: If frames contain null elements, they are allowed. Null frames act like 0-duration frames for timing,
and `draw(TextureBatch, float, float, float, float)` will no-op when the current frame is null.

- **`playbackMode`** — playback mode to use
- **`frames`** — ordered frames to play

#### draw

```java
@Override
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint)
```

Draws the current animation frame using the specified parameters.

- **`batch`** — the TextureBatch to draw the frame onto
- **`x`** — the x-coordinate of the bottom left corner where the frame will be drawn
- **`y`** — the y-coordinate of the bottom left corner where the frame will be drawn
- **`width`** — the width of the frame to be drawn
- **`height`** — the height of the frame to be drawn
- **`regionX`** — the x-coordinate of the texture region to draw from
- **`regionY`** — the y-coordinate of the texture region to draw from
- **`regionWidth`** — the width of the texture region to draw
- **`regionHeight`** — the height of the texture region to draw
- **`originX`** — the x-coordinate of the rotation origin relative to the frame
- **`originY`** — the y-coordinate of the rotation origin relative to the frame
- **`rotation`** — the rotation angle in degrees
- **`tint`** — the color tint to apply when drawing the frame

#### getWidth

```java
@Override
    public float getWidth()
```

Returns the width of the current frame's `Drawable`.

This is a convenience method for layout code.

If there is no current frame (null/empty frames) or drawable is null, returns 0.

**Returns:** drawable width or 0

#### getHeight

```java
@Override
    public float getHeight()
```

Returns the height of the current frame's `Drawable`.

This is a convenience method for layout code.

If there is no current frame (null/empty frames) or drawable is null, returns 0.

**Returns:** drawable height or 0

#### update

```java
public void update(float delta)
```

Advances the animation timeline and updates the current frame index when needed.

Key properties:

- Time is scaled by `speed`.

- Large deltas can advance across multiple frames in one call.

- 0-duration frames are skipped without getting stuck in an infinite loop.

- If `isPaused()` or `isFinished()` is true, this method does nothing.

Finish behavior:

- If not looping: reaching the logical endpoint sets FINISHED and PAUSED.

- If loop-limited: reaching the limit sets FINISHED and PAUSED.

- **`delta`** — elapsed seconds since last update

#### isFinished

```java
public boolean isFinished()
```

Returns whether the animation is finished.

Finished is not the same as paused:

- Paused can be set manually via `pause()` or `setPaused(boolean)`.

- Finished is set when reaching an endpoint in non-looping mode, or reaching a loop limit.

**Returns:** true if finished, otherwise false

#### restart

```java
public void restart()
```

Restarts playback from the mode's logical start and unpauses.

This is equivalent to:

```java
reset();
play();
```

#### reset

```java
public void reset()
```

Resets time and frame index to the mode's logical starting point.

Behavior by mode:

- FORWARD: frame 0.

- REVERSE: last frame.

- BIDIRECTIONAL: frame 0 and "forward" direction.

This clears FINISHED and resets loop counters.

This does not automatically unpause (use `play()` if desired).

#### play

```java
public void play()
```

Unpauses the animation and clears FINISHED state.

If you want to restart from the beginning, call `restart()` instead.

#### pause

```java
public void pause()
```

Pauses animation updates.

#### stop

```java
public void stop()
```

Stops playback by pausing and resetting to the mode's start.

This is useful for one-shot animations you want to reuse later.

#### setFrame

```java
public void setFrame(int index)
```

Forces the animation to a specific frame index.

This clears FINISHED and resets `elapsedTime` so the frame starts "fresh".

The requested index is clamped into [0, frameCount-1].

- **`index`** — desired frame index

#### setRandomStart

```java
public void setRandomStart()
```

Chooses a random start frame and jumps to it.

This clears FINISHED and resets elapsed time on the selected frame.

#### getSpeed

```java
public float getSpeed()
```

Returns the current playback speed multiplier.

**Returns:** speed multiplier

#### setSpeed

```java
public void setSpeed(float speed)
```

Sets the playback speed multiplier.

Speed scales time inside `update(float)`:

- 1.0: normal speed

- 2.0: twice as fast

- 0.5: half speed

Values &lt;= 0 are clamped to a tiny positive value to avoid freezing or division edge cases.

- **`speed`** — playback speed multiplier

#### getDuration

```java
public float getDuration()
```

Returns the total duration of one forward pass (sum of all frame durations).

This is precomputed during construction from the provided frames.

**Returns:** total duration in seconds

#### getFrameCount

```java
public int getFrameCount()
```

Returns the number of frames in this animation.

**Returns:** frame count (0 if frames is null)

#### getProgress01

```java
public float getProgress01()
```

Returns progress in the range [0..1] for the current mode's forward-pass timeline.

Meaning:

- FORWARD: 0 at start, 1 at end.

- REVERSE: still maps to 0..1 along the same forward timeline (via `getTime()`).

- BIDIRECTIONAL: maps to the current forward-pass position (not the full ping-pong cycle).

**Returns:** normalized progress in [0..1]

#### time

```java
public Animation time(float seconds)
```

Seeks the animation to an absolute time along the mode's logical timeline.

Rules:

- FORWARD: t = 0 means start; t = duration means end.

- REVERSE: t = 0 means end; t = duration means start.

- BIDIRECTIONAL: time wraps into a cycle of length 2*duration and selects forward/backward half.

This clears FINISHED state.

- **`seconds`** — time in seconds (clamped to >= 0 and into valid range)

#### getTime

```java
public float getTime()
```

Returns the current absolute time along the mode's logical timeline.

Interpretation:

- FORWARD / BIDIRECTIONAL: forward-pass time.

- REVERSE: reverse time where 0 means "at end".

**Returns:** time in seconds

#### listener

```java
public Animation listener(AnimationListener animationListener)
```

Sets an optional `AnimationListener`.

Set to null to disable callbacks.

- **`animationListener`** — animationListener or null

#### getCurrentFrame

```java
public AnimationFrame getCurrentFrame()
```

Returns the current `AnimationFrame`.

This may be null if frames are null/empty or if the current frame element is null.

**Returns:** current frame or null

#### getFrames

```java
public AnimationFrame[] getFrames()
```

Returns the backing frame array reference.

No defensive copy is made.

**Returns:** frames array (may be null)

#### getCurrentIndex

```java
public short getCurrentIndex()
```

Returns the current frame index.

**Returns:** current frame index (0 when frames empty)

#### loopCount

```java
public Animation loopCount(int loops)
```

Sets a loop count limit.

Values:

- -1: infinite looping (if looping enabled)

- 0: no looping (one-shot)

- N: loop N times

This also sets `LOOPING` to (loops != 0).

- **`loops`** — loop limit (-1, 0, or N)

#### getLoopCount

```java
public int getLoopCount()
```

Returns the current loop count limit.

**Returns:** loop limit (-1, 0, or N)

#### getLoopsCompleted

```java
public int getLoopsCompleted()
```

Returns how many loops have completed so far.

This counter resets in `reset()` and `restart()`.

**Returns:** loops completed

#### playOnce

```java
public void playOnce()
```

Convenience helper to configure this as a one-shot animation.

This disables looping and clears any loop limit.

#### isLooping

```java
public boolean isLooping()
```

Returns whether looping is enabled.

**Returns:** true if looping

#### setLooping

```java
public Animation setLooping(boolean looping)
```

Enables or disables looping.

If you set a loop count with `loopCount(int)`, looping is automatically enabled when loops != 0.

- **`looping`** — true to loop, false for one-shot behavior

#### isPaused

```java
public boolean isPaused()
```

Returns whether updates are paused.

**Returns:** true if paused

#### setPaused

```java
public void setPaused(boolean paused)
```

Pauses or unpauses the animation.

This does not affect the finished flag.

- **`paused`** — true to pause, false to resume

#### getPlaybackMode

```java
public PlaybackMode getPlaybackMode()
```

Returns the current playback mode.

**Returns:** playback mode

#### setPlaybackMode

```java
public void setPlaybackMode(PlaybackMode playbackMode)
```

Sets a new playback mode.

This does not automatically reset the animation. If you want "mode start",
call `reset()` after changing the mode.

- **`playbackMode`** — new playback mode

</details>

<a id="type-animationadapter"></a>

### AnimationAdapter

[Source](../../src/main/java/valthorne/graphics/animation/AnimationAdapter.java#L13)

Adapter class for the `AnimationListener` interface.

This class provides empty implementations for the methods in the `AnimationListener` interface.
It can be used as a base class for creating listener objects where only a subset of methods need to be implemented.
Callbacks retain no state and do not alter playback; subclasses decide whether to
act on each notification delivered by the animation update loop.

<details>
<summary>AnimationAdapter operation reference (3 declarations)</summary>

#### onFrameChanged

```java
    public void onFrameChanged(Animation animation, int fromIndex, int toIndex)
```

Ignores a frame transition. Override to react to a frame change without
implementing the remaining lifecycle callbacks.

- **`animation`** — animation that advanced
- **`fromIndex`** — previous frame index
- **`toIndex`** — newly active frame index

#### onFinished

```java
    public void onFinished(Animation animation)
```

Ignores playback completion and leaves the animation's finished state intact.

- **`animation`** — animation that finished

#### onLoop

```java
    public void onLoop(Animation animation, int loopsCompleted)
```

Ignores a completed loop. The supplied count has already been incremented
by the animation before this notification is delivered.

- **`animation`** — animation that crossed its loop boundary
- **`loopsCompleted`** — cumulative completed-loop count

</details>

<a id="type-animationframe"></a>

### AnimationFrame

[Source](../../src/main/java/valthorne/graphics/animation/AnimationFrame.java#L18)

Represents a single frame within an animation sequence. Each frame is associated
with a `Drawable` object that can be rendered, as well as a duration
specifying how long the frame should be displayed before transitioning to the next.

The record keeps fixed component references but does not copy or own the drawable.
It performs no duration or null validation; `Animation` treats null drawables
as non-rendering content and clamps negative frame durations for timing.

- **`drawable`** — the drawable object to render for this animation frame
- **`duration`** — the duration, in seconds, for which this frame should be displayed

<a id="type-animationlistener"></a>

### AnimationListener

[Source](../../src/main/java/valthorne/graphics/animation/AnimationListener.java#L11)

AnimationListener for animation lifecycle events.

All callbacks are invoked from within `Animation#update(float)`.

Keep implementations lightweight to avoid stalling your frame loop.

<details>
<summary>AnimationListener operation reference (3 declarations)</summary>

#### onFrameChanged

```java
void onFrameChanged(Animation animation, int fromIndex, int toIndex)
```

Called when the currently active frame index of the animation changes.

- **`animation`** — the animation instance where the frame change occurred
- **`fromIndex`** — the previous frame index before the change
- **`toIndex`** — the new frame index after the change

#### onFinished

```java
void onFinished(Animation animation)
```

Called when the animation has finished.

- **`animation`** — the animation instance that has completed

#### onLoop

```java
void onLoop(Animation animation, int loopsCompleted)
```

Called when a loop completes.

FORWARD/REVERSE: completing a loop means wrapping around from end\u2192start or start\u2192end.

BIDIRECTIONAL: a "full loop" is counted when the ping-pong returns back to the start boundary.

- **`animation`** — this animation instance
- **`loopsCompleted`** — total loops completed after increment

</details>

<a id="type-animationutility"></a>

### AnimationUtility

[Source](../../src/main/java/valthorne/graphics/animation/AnimationUtility.java#L14)

Constructs frame sequences from existing texture regions, wrapping each region
in a drawable without copying or taking ownership of its texture. Uniform-duration
overloads divide the supplied forward-pass duration across the generated frames;
playback direction and repetition remain properties of the returned animation.

<details>
<summary>AnimationUtility operation reference (3 declarations)</summary>

#### fromRegions

```java
public static Animation fromRegions(PlaybackMode mode, float duration, TextureRegion... regions)
```

Creates an animation using the specified playback mode, total duration, and texture regions.
Region order is preserved. An empty array creates an empty animation; duration
validation is left to the animation's timing behavior rather than this factory.

- **`mode`** — the playback mode for the animation, determining how frames are cycled.
- **`duration`** — the total duration of the animation in seconds.
- **`regions`** — the texture regions that define the frames of the animation.

**Returns:** a new `Animation` instance configured with the provided playback mode, duration, and frames.

#### fromRegions

```java
public static Animation fromRegions(PlaybackMode mode, float[] durations, TextureRegion... regions)
```

Creates an animation using the specified playback mode, an array of frame durations,
and corresponding texture regions.
The arrays are consumed in index order to create independent frame records;
the wrapped regions continue to reference their original textures. Durations
are stored as supplied and interpreted by the animation during playback.

- **`mode`** — the playback mode for the animation, determining the order and repetition of frames.
- **`durations`** — an array of durations in seconds, specifying the duration of each frame. The length of this array must match the number of provided texture regions.
- **`regions`** — the texture regions that define the frames of the animation.

**Returns:** a new `Animation` instance configured with the provided playback mode, frame durations, and frames.

**Throws `IllegalArgumentException`:** if the lengths of the `durations` array and `regions` array do not match.

#### fromGrid

```java
public static Animation fromGrid(PlaybackMode mode, float duration, TextureRegion[][] grid)
```

Creates an animation using the specified playback mode, total duration, and a 2D grid of texture regions.
Each texture region in the grid is treated as a frame in the animation, and all frames
are assigned an equal duration.
Traversal is row-major, using the first row's width for every row. Supply a
nonempty rectangular grid: short later rows cause an index error and additional
entries in longer rows are ignored. Textures remain owned by the caller.

- **`mode`** — the playback mode for the animation, determining how frames are cycled.
- **`duration`** — the total duration of the animation in seconds. This is evenly distributed across all frames.
- **`grid`** — a 2D array of texture regions, where each region represents a frame of the animation.

**Returns:** a new `Animation` instance configured with the provided playback mode, duration, and frames.

</details>

<a id="type-playbackmode"></a>

### PlaybackMode

[Source](../../src/main/java/valthorne/graphics/animation/PlaybackMode.java#L11)

Selects the direction policy used by Animation when advancing frames.
Forward and reverse traverse one direction; bidirectional changes direction at
endpoints. This enum does not determine whether playback repeats or stops;
looping and completion limits are separate animation settings.

<details>
<summary>PlaybackMode operation reference (3 declarations)</summary>

#### FORWARD

```java
public static final  PlaybackMode FORWARD
```

Indicates the playback mode for continuous forward progression.
Used to specify that playback or processing should continue in a forward
or normal direction without reversing or altering its course.

#### REVERSE

```java
public static final  PlaybackMode REVERSE
```

Represents the playback mode where the process or media progresses
in the reverse direction. Used to indicate that playback or processing
should move backward.

#### BIDIRECTIONAL

```java
public static final  PlaybackMode BIDIRECTIONAL
```

Represents the playback mode where the process or media can progress
in both forward and reverse directions. This mode allows switching
between directions as needed during playback or processing.

</details>

<a id="type-transformanimation3d"></a>

### TransformAnimation3D

[Source](../../src/main/java/valthorne/graphics/animation/TransformAnimation3D.java#L36)

Samples immutable local-transform keyframes using linear position and scale
interpolation and shortest-path quaternion interpolation. Time is measured in
seconds. Sampling can clamp to the last frame or wrap over the animation duration;
this class stores no playback clock and never advances time on its own.

##### Usage

```java
TransformAnimation3D animation = new TransformAnimation3D(
        new TransformAnimation3D.Keyframe(0, new Vector3f(),
                new Quaternionf(), new Vector3f(1, 1, 1)),
        new TransformAnimation3D.Keyframe(2, new Vector3f(3, 0, 0),
                new Quaternionf(), new Vector3f(2, 2, 2)));
animation.apply(instance, elapsedSeconds, true);
```

Frames must start at zero and increase strictly. Each scale component must
retain its sign between adjacent frames, preventing an intended interpolation
path through a zero scale. Negative scale is allowed when that sign is consistent.

The animation clones the frame array and keyframes protect their mutable
components with defensive copies. Sampling allocates a new keyframe rather
than exposing stored data. Applying a sample changes a target's local transform
while preserving its model, parent transform or hierarchy; target ownership
remains with the caller.

<details>
<summary>TransformAnimation3D operation reference (5 declarations)</summary>

#### Constructor

```java
public TransformAnimation3D(Keyframe... frames)
```

Creates an animation from at least two frames, cloning the supplied array.
Frame zero must be at time zero; subsequent times must increase strictly.
Corresponding scale components must have matching signs across each pair.
These rules yield a positive duration and nonsingular intended scale paths.

- **`frames`** — the ordered nonnull frames beginning at time zero

**Throws `NullPointerException`:** if the array or a frame is null

**Throws `IllegalArgumentException`:** if there are fewer than two frames, times
are invalidly ordered, or scale signs change

#### getDuration

```java
public float getDuration()
```

Returns the final frame's timestamp, which is the positive animation
duration because the first frame starts at zero.

**Returns:** the duration in seconds

#### apply

```java
public void apply(ModelInstance3D target, float time, boolean loop)
```

Samples the requested time and replaces a model instance's local position,
scale and quaternion orientation. The instance's model, material and parent
transform are preserved. Sampling finishes before target mutation begins.

- **`target`** — the nonnull instance to animate
- **`time`** — the finite nonnegative playback time in seconds
- **`loop`** — whether time wraps at duration instead of clamping

**Throws `IllegalArgumentException`:** if sampling rejects time or its result

**Throws `NullPointerException`:** if target is null after sampling succeeds

#### apply

```java
public void apply(SceneNode3D target, float time, boolean loop)
```

Samples the requested time and replaces a node's local position, scale and
quaternion orientation. Parent and child relationships remain intact, so
hierarchy transforms continue to compose with the sampled local transform.

- **`target`** — the nonnull hierarchy node to animate
- **`time`** — the finite nonnegative playback time in seconds
- **`loop`** — whether time wraps at duration instead of clamping

**Throws `IllegalArgumentException`:** if sampling rejects time or its result

**Throws `NullPointerException`:** if target is null after sampling succeeds

#### sample

```java
public Keyframe sample(float time, boolean loop)
```

Returns an interpolated transform at the effective playback time. Looping
uses remainder by duration, so an exact duration multiple samples time zero.
Nonlooping playback clamps later times to the last frame. Negative time is
rejected rather than wrapped backward.

A binary search selects adjacent frames. Position and scale interpolate
linearly, and orientation uses quaternion SLERP. The returned keyframe's
time is the wrapped or clamped time, not the original argument. Even exact
endpoint samples create fresh component storage.

- **`time`** — the finite nonnegative requested time in seconds
- **`loop`** — whether to repeat the animation at its duration

**Returns:** a newly allocated immutable transform snapshot

**Throws `IllegalArgumentException`:** if time is negative or non-finite, or
interpolation produces an invalid transform

</details>

<a id="type-transformanimation3d-keyframe"></a>

### TransformAnimation3D.Keyframe

[Source](../../src/main/java/valthorne/graphics/animation/TransformAnimation3D.java#L148)

Immutable transform sample at a nonnegative time. Position, rotation and
scale are copied on construction and access. Position and scale must be
finite, with every scale component nonzero. Rotation must be finite and
nonzero and is normalized while copying the supplied quaternion.

- **`time`** — the finite time in seconds
- **`position`** — the local-space position to snapshot
- **`rotation`** — the local orientation to snapshot
- **`scale`** — the finite nonzero per-axis scale to snapshot

<details>
<summary>TransformAnimation3D.Keyframe operation reference (4 declarations)</summary>

#### Constructor

```java
public Keyframe
```

Validates time, copies transform components, and rejects non-finite
position/scale or zero scale components. No caller-owned value is modified.

- **`time`** — the finite nonnegative sample time in seconds
- **`position`** — the nonnull local position
- **`rotation`** — the nonnull orientation
- **`scale`** — the nonnull per-axis scale

**Throws `IllegalArgumentException`:** if time is invalid, position or scale is
non-finite, or any scale component is zero

**Throws `NullPointerException`:** if a transform component object is null

#### position

```java
        public Vector3f position()
```

Returns an independent local-position vector. Editing the result does
not change the stored frame or any animation that references it.

**Returns:** a newly allocated copy of local position

#### rotation

```java
        public Quaternionf rotation()
```

Returns an independent orientation copy without renormalizing it.
Callers can modify the returned quaternion without altering this frame.

**Returns:** a newly allocated orientation copy

#### scale

```java
        public Vector3f scale()
```

Returns an independent scale vector, preserving signed components.
Changing the result cannot affect stored interpolation endpoints.

**Returns:** a newly allocated copy of local scale

</details>

## Related guides

- [Ticks and frame timing](timing.md)
- [Textures, sprites, atlases, and batching](textures.md)
- [3D models, materials, scenes, and billboards](models.md)
