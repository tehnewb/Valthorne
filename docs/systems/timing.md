# Ticks and frame timing

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Valthorne's time helpers operate on values supplied by your game loop. `Tick` is a manually updated repeating callback, not a background scheduler. `TimeUtility` provides conversions, smoothing, frame-rate calculations, fixed-step accumulation, progress, and formatting.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Repeating callbacks | A Tick accumulates elapsed seconds and invokes a configured callback when due. |
| Stop conditions | A predicate can end repeated execution based on iteration count or application state. |
| Fixed-step accumulation | Helpers consume frame time in bounded simulation steps and retain a remainder. |
| Conversions and display | Convert frames, seconds, milliseconds, and formatted durations without mixing units. |

## Getting started

1. Decide which clock owns each feature: real frame time, paused game time, or fixed simulation time.
2. Configure and start a Tick, then call its update with the chosen delta.
3. For fixed simulation, keep an accumulator and use a bounded number of steps per frame.
4. Use interpolation or display helpers on the remaining time rather than advancing the simulation twice.

## Ownership and lifecycle

These helpers do not create threads. Stopping updates effectively stops their progress. Callbacks run in the caller's thread and should not assume exact wall-clock scheduling.

## Important behavior

- Read zero-delay and catch-up behavior before using Tick for bursts.
- All delta values in engine update callbacks are seconds.
- Fixed-step limits intentionally prevent unbounded catch-up after a long stall.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Tick`](#type-tick)
- [`TimeUtility`](#type-timeutility)

<a id="type-tick"></a>

### Tick

[Source](../../src/main/java/valthorne/tick/Tick.java#L42)

A lightweight time-based tick utility.

`Tick` executes a callback repeatedly at a fixed delay interval.
It is manually driven by calling `update(float)` every frame.

##### Core Behavior

- Accumulates time using `delta`.

- When accumulated time reaches `delay`, the callback is invoked.

- Supports optional stop conditions.

- Tracks how many times it has fired via `iterations`.

##### Example Usage

```java
Tick tick = new Tick()
    .delay(1.0f) // fire every 1 second
    .callback(t -> System.out.println("Tick: " + t.getIterations()))
    .stopCondition(t -> t.getIterations() >= 5);

tick.start();

// inside game update method
tick.update(delta);
```

This will print once per second and automatically stop after 5 iterations.

<details>
<summary>Tick operation reference (10 declarations)</summary>

#### update

```java
public void update(float delta)
```

Updates the tick timer.

This method must be called every frame with the elapsed time since
the previous frame.

Execution order:

- If stopped \u2192 return immediately.

- If stop condition exists and evaluates true \u2192 stop.

- Accumulate time.

- If accumulated time >= delay \u2192 fire callback.

- **`delta`** — time in seconds since last update

#### restart

```java
public void restart()
```

Restarts the tick from a clean state.

This resets elapsed time, iteration count,
and immediately starts the tick.

#### start

```java
public void start()
```

Starts the tick without resetting state.

If the tick was previously stopped,
it resumes from its current elapsed time.

#### stopCondition

```java
public Tick stopCondition(Predicate<Tick> stopCondition)
```

Sets a stop condition.

The predicate is evaluated before each callback execution.
If it returns true, the tick automatically stops.

- **`stopCondition`** — condition to evaluate

**Returns:** this, for chaining

#### callback

```java
public Tick callback(Consumer<Tick> callback)
```

Sets the callback executed each time the delay threshold is reached.

- **`callback`** — callback consumer

**Returns:** this, for chaining

#### delay

```java
public Tick delay(float delay)
```

Sets the delay interval between executions.

- **`delay`** — time in seconds between ticks

**Returns:** this, for chaining

#### stop

```java
public void stop()
```

Stops the tick immediately.

No further updates will trigger the callback
until `start()` or `restart()` is called.

#### isStopped

```java
public boolean isStopped()
```

Returns whether this tick is currently stopped.

**Returns:** true if stopped

#### getIterations

```java
public long getIterations()
```

Returns how many times the callback has been executed.

**Returns:** iteration count

#### getDelay

```java
public float getDelay()
```

Returns the current delay interval.

**Returns:** delay in seconds

</details>

<a id="type-timeutility"></a>

### TimeUtility

[Source](../../src/main/java/valthorne/utility/TimeUtility.java#L29)

Time-related utilities focused on real engine needs (game loop, cooldowns, timers),
without duplicating obvious `System` wrappers.

##### Example

```java
// Conversion
long ms = TimeUtils.toMillis(0.5f);      // 500
float s  = TimeUtils.toSeconds(250);     // 0.25

// Clamp delta (avoid huge jumps after tab-out)
dt = TimeUtils.clampDelta(dt, 0.1f);

// Smooth/lerp delta
smoothedDt = TimeUtils.smoothDelta(smoothedDt, dt, 0.08f);

// Cooldown
if (TimeUtils.cooldownReady(nowSec, lastFireSec, 0.2f)) lastFireSec = nowSec;

// Fixed-step accumulator
accumulator = TimeUtils.stepFixed(accumulator, dt, 1f / 60f, 5, () -> physicsStep());
```

<details>
<summary>TimeUtility operation reference (16 declarations)</summary>

#### toMillis

```java
public static long toMillis(float seconds)
```

Converts float seconds to milliseconds.

Examples:

- 1.0f  -> 1000

- 0.5f  -> 500

- 2.25f -> 2250

- **`seconds`** — time in seconds

**Returns:** time in milliseconds

#### toSeconds

```java
public static float toSeconds(long millis)
```

Converts milliseconds to float seconds.

Examples:

- 1000 -> 1.0f

- 500  -> 0.5f

- 250  -> 0.25f

- **`millis`** — time in milliseconds

**Returns:** time in seconds

#### clampDelta

```java
public static float clampDelta(float deltaSeconds, float maxDeltaSeconds)
```

Clamps delta-time to avoid huge simulation jumps (e.g., when the window was paused or unfocused).

- **`deltaSeconds`** — measured dt
- **`maxDeltaSeconds`** — maximum allowed dt (common values: 0.05f to 0.25f)

**Returns:** clamped dt

#### smoothDelta

```java
public static float smoothDelta(float previousSmoothed, float currentDelta, float smoothing)
```

Applies exponential smoothing to delta-time.
Useful to reduce jitter for camera movement, UI animations, or variable frame rates.

- **`previousSmoothed`** — previous smoothed dt
- **`currentDelta`** — current measured dt
- **`smoothing`** — 0..1 (lower = smoother, higher = more responsive). Typical: 0.05f - 0.2f

**Returns:** new smoothed dt

#### fpsFromDelta

```java
public static int fpsFromDelta(float deltaSeconds)
```

Calculates FPS (frames per second) from delta seconds.

- **`deltaSeconds`** — dt in seconds

**Returns:** fps, or 0 if dt &lt;= 0

#### tick

```java
public static boolean tick(float accumulatorSeconds, float intervalSeconds)
```

Accumulates dt and returns true if enough time has passed to "tick" at a fixed interval.
This is handy for cheap periodic logic (blink, UI pulses, autosave timer).

Usage pattern:

```java
acc += dt;
if (TimeUtils.tick(acc, 0.25f)) {
    acc -= 0.25f;
    doThing();
}
```

- **`accumulatorSeconds`** — current accumulator (seconds)
- **`intervalSeconds`** — tick interval (seconds)

**Returns:** true if accumulator >= interval

#### stepFixed

```java
public static float stepFixed(float accumulatorSeconds, float deltaSeconds, float stepSeconds, int maxStepsPerFrame, Runnable stepAction)
```

Advances a fixed-step simulation using an accumulator.

Why this exists:

Variable dt makes physics unstable and non-deterministic.
This helper consumes dt in fixed increments and runs `stepAction` for each step,
with an upper bound to prevent spiral-of-death.

Pattern:

```java
accumulator = TimeUtils.stepFixed(accumulator, dt, 1f/60f, 5, this::physicsStep);
```

- **`accumulatorSeconds`** — current accumulator (seconds)
- **`deltaSeconds`** — frame dt in seconds
- **`stepSeconds`** — fixed step size (e.g., 1/60f)
- **`maxStepsPerFrame`** — safety cap (e.g., 5..10)
- **`stepAction`** — executed once per fixed step

**Returns:** updated accumulator

#### fixedAlpha

```java
public static float fixedAlpha(float accumulatorSeconds, float stepSeconds)
```

Returns interpolation alpha for rendering between fixed steps.
Use this after `stepFixed(float, float, float, int, Runnable)`.

- **`accumulatorSeconds`** — remaining accumulator (seconds)
- **`stepSeconds`** — fixed step size (seconds)

**Returns:** alpha in [0..1]

#### cooldownReady

```java
public static boolean cooldownReady(float nowSeconds, float lastSeconds, float cooldownSeconds)
```

Returns true if a cooldown has finished.

- **`nowSeconds`** — current time in seconds (your engine time, not necessarily wall clock)
- **`lastSeconds`** — last time the action was performed (seconds)
- **`cooldownSeconds`** — cooldown length (seconds)

**Returns:** true if now - last >= cooldown

#### cooldownRemaining

```java
public static float cooldownRemaining(float nowSeconds, float lastSeconds, float cooldownSeconds)
```

Returns remaining cooldown time (seconds).

- **`nowSeconds`** — current time in seconds
- **`lastSeconds`** — time when action started (seconds)
- **`cooldownSeconds`** — cooldown length (seconds)

**Returns:** remaining seconds (0 if ready)

#### progress01

```java
public static float progress01(float nowSeconds, float startSeconds, float durationSeconds)
```

Returns progress in [0..1] for a cooldown or timed action.

- **`nowSeconds`** — current time in seconds
- **`startSeconds`** — start time in seconds
- **`durationSeconds`** — duration in seconds

**Returns:** progress 0..1

#### finished

```java
public static boolean finished(float nowSeconds, float startSeconds, float durationSeconds)
```

Returns true if a timed action has finished (now >= start + duration).

- **`nowSeconds`** — current time in seconds
- **`startSeconds`** — start time in seconds
- **`durationSeconds`** — duration in seconds

**Returns:** true if finished

#### formatMinutesSeconds

```java
public static String formatMinutesSeconds(float seconds)
```

Formats seconds into "MM:SS" (minutes and seconds).
Intended for UI overlays (timers, speedrun clocks, etc).

- **`seconds`** — time in seconds (>= 0)

**Returns:** formatted string

#### formatHoursMinutesSeconds

```java
public static String formatHoursMinutesSeconds(float seconds)
```

Formats seconds into "HH:MM:SS".

- **`seconds`** — time in seconds (>= 0)

**Returns:** formatted string

#### secondsToFrames

```java
public static int secondsToFrames(float seconds, int fps)
```

Converts seconds to an integer frame count at a given FPS.
Useful for aligning animation durations to frames.

- **`seconds`** — duration in seconds
- **`fps`** — frames per second (e.g., 60)

**Returns:** frame count (>= 0)

#### framesToSeconds

```java
public static float framesToSeconds(int frames, int fps)
```

Converts frame count to seconds at a given FPS.

- **`frames`** — frame count (>= 0)
- **`fps`** — frames per second (e.g., 60)

**Returns:** seconds

</details>

## Related guides

- [Application lifecycle and window management](runtime.md)
- [Jolt rigid-body physics](physics.md)
- [Frame and transform animation](animation.md)
