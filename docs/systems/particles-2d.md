# 2D particles and spawn distributions

Author: Albert Beaupre

[System manual](README.md)

## Purpose

The 2D particle system combines emitter state, reusable particles, and configurable spawn distributions. Use distributions to shape where particles begin without embedding geometry sampling in the effect update loop. Keep visual resource ownership separate from particle lifetime.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Emitter and system | An emitter describes the effect; ParticleSystem coordinates active effects and updates. |
| Particle state | Position, motion, lifetime, and visual values evolve over time. |
| Spawn distributions | Point, line, box, circle, cone, ring, rectangle-edge, spiral, and radial-burst samplers create different birth patterns. |
| Reuse | Particle reuse reduces allocation during repeated effects. |

## Getting started

1. Create an emitter and choose a distribution matching the effect's source geometry.
2. Configure lifetime and motion alongside the borrowed visual resources.
3. Advance the effect once per simulation frame and draw through the active rendering path.
4. Stop emission or remove the system when the effect ends, then release owned backing resources.

## Ownership and lifecycle

Spawn callbacks and updates operate on mutable particle state. Avoid retaining a recycled particle as a permanent gameplay object. Shared textures must outlive all active particles that use them.

## Important behavior

- Equal-probability rectangle edges are not uniform sampling over perimeter length.
- A spiral parameter is not necessarily uniform distance along the curve.
- Cone edge sampling and disk/ring area sampling have distinct distributions; choose the intended geometry.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`BoxSpawnDistributor`](#type-boxspawndistributor)
- [`CircleSpawnDistributor`](#type-circlespawndistributor)
- [`ConeSpawnDistributor`](#type-conespawndistributor)
- [`LineSpawnDistributor`](#type-linespawndistributor)
- [`Particle`](#type-particle)
- [`ParticleEmitter`](#type-particleemitter)
- [`ParticleSystem`](#type-particlesystem)
- [`PointSpawnDistributor`](#type-pointspawndistributor)
- [`RadialBurstSpawnDistributor`](#type-radialburstspawndistributor)
- [`RectEdgeSpawnDistributor`](#type-rectedgespawndistributor)
- [`RingSpawnDistributor`](#type-ringspawndistributor)
- [`SpawnDistributor`](#type-spawndistributor)
- [`SpiralSpawnDistributor`](#type-spiralspawndistributor)

<a id="type-boxspawndistributor"></a>

### BoxSpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/BoxSpawnDistributor.java#L16)

A concrete implementation of the `SpawnDistributor` interface that distributes
spawn locations within the bounds of a rectangular area. The rectangle is defined
by the half-width and half-height, with the center of the rectangle at the origin.

The spawn offsets are generated stochastically, ensuring that the resulting positions
are uniformly distributed within the rectangle.

<details>
<summary>BoxSpawnDistributor operation reference (6 declarations)</summary>

#### Constructor

```java
public BoxSpawnDistributor(float halfWidth, float halfHeight)
```

Creates a centered rectangular distribution, clamping negative half-extents
to zero. Nonfinite values are not rejected.

- **`halfWidth`** — horizontal half-extent
- **`halfHeight`** — vertical half-extent

#### getHalfWidth

```java
public float getHalfWidth()
```

Returns the stored width half-extent.

**Returns:** half-extent in emitter coordinate units

#### setHalfWidth

```java
public BoxSpawnDistributor setHalfWidth(float halfWidth)
```

Sets the width half-extent, clamping negative values to zero.
Nonfinite values are not rejected.

- **`halfWidth`** — requested half-extent

**Returns:** this distributor

#### getHalfHeight

```java
public float getHalfHeight()
```

Returns the stored height half-extent.

**Returns:** half-extent in emitter coordinate units

#### setHalfHeight

```java
public BoxSpawnDistributor setHalfHeight(float halfHeight)
```

Sets the height half-extent, clamping negative values to zero.
Nonfinite values are not rejected.

- **`halfHeight`** — requested half-extent

**Returns:** this distributor

#### computeOffset

```java
    public void computeOffset(Random random, float[] out)
```

Samples X and Y independently and uniformly inside the centered rectangle.
The positive edges are excluded by Random.nextFloat's half-open interval.

- **`random`** — nonnull random source
- **`out`** — nonnull destination with at least two entries; X then Y

**Throws `NullPointerException`:** if random or out is null

**Throws `ArrayIndexOutOfBoundsException`:** if out has fewer than two entries

</details>

<a id="type-circlespawndistributor"></a>

### CircleSpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/CircleSpawnDistributor.java#L16)

A `CircleSpawnDistributor` is an implementation of the `SpawnDistributor` interface
that calculates spawn offsets within a circular region. Spawn positions can either be uniformly
distributed within the circle's area or restricted to its edge.

This class utilizes a radius to define the circle's size and a boolean flag to determine
whether the spawn offsets are restricted to the perimeter of the circle.

<details>
<summary>CircleSpawnDistributor operation reference (6 declarations)</summary>

#### Constructor

```java
public CircleSpawnDistributor(float radius, boolean edgeOnly)
```

Creates a circular distribution with negative radius clamped to zero.

- **`radius`** — circle radius
- **`edgeOnly`** — true for circumference sampling, false for uniform area

#### getRadius

```java
public float getRadius()
```

Returns the configured maximum radius.

**Returns:** radius in emitter coordinate units

#### setRadius

```java
public CircleSpawnDistributor setRadius(float radius)
```

Stores a radius clamped against zero; nonfinite values are not rejected.

- **`radius`** — requested radius

**Returns:** this distributor

#### isEdgeOnly

```java
public boolean isEdgeOnly()
```

Reports whether samples lie only on the circumference.

**Returns:** true for edge-only sampling

#### setEdgeOnly

```java
public CircleSpawnDistributor setEdgeOnly(boolean edgeOnly)
```

Switches between circumference and uniform-area sampling without changing radius.

- **`edgeOnly`** — true for circumference samples

**Returns:** this distributor

#### computeOffset

```java
    public void computeOffset(Random random, float[] out)
```

Samples a uniform angle and either the fixed radius or a square-root-scaled
radius for uniform area density. Consumes one random float in edge mode and
two in area mode.

- **`random`** — nonnull random source
- **`out`** — nonnull destination with at least two entries; X then Y

**Throws `NullPointerException`:** if random or out is null

**Throws `ArrayIndexOutOfBoundsException`:** if out has fewer than two entries

</details>

<a id="type-conespawndistributor"></a>

### ConeSpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/ConeSpawnDistributor.java#L18)

A `ConeSpawnDistributor` is a specific implementation of the `SpawnDistributor` interface
that calculates spawn offsets within a conical region. The cone is defined by a direction, a spread
angle, a radius, and an optional restriction to the cone's edge.

This distributor allows for randomized distribution of offsets that simulate spawning within a
cone-shaped area, as determined by the given parameters.

<details>
<summary>ConeSpawnDistributor operation reference (2 declarations)</summary>

#### Constructor

```java
public ConeSpawnDistributor(float directionDeg, float spreadDeg, float radius, boolean edgeOnly)
```

Creates a 2D circular sector distribution. Negative radius is clamped to zero;
angles are stored without normalization or range validation. Edge mode selects
the outer arc, not the sector's radial sides.

- **`directionDeg`** — center angle in degrees
- **`spreadDeg`** — full angular width in degrees
- **`radius`** — maximum radial distance
- **`edgeOnly`** — true for the outer arc

#### computeOffset

```java
    public void computeOffset(Random random, float[] out)
```

Samples an angle across the configured full spread and uses a fixed or
square-root-distributed radius. Conventional positive sector spans give uniform
area density when edgeOnly is false.

- **`random`** — nonnull random source
- **`out`** — nonnull destination with at least two entries; X then Y

**Throws `NullPointerException`:** if random or out is null

**Throws `ArrayIndexOutOfBoundsException`:** if out has fewer than two entries

</details>

<a id="type-linespawndistributor"></a>

### LineSpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/LineSpawnDistributor.java#L16)

The LineSpawnDistributor class provides an implementation of the SpawnDistributor interface
where spawn positions are distributed along a straight line defined by two endpoints.
The positions are computed by interpolating between the start and end points using
randomly generated weights.

<details>
<summary>LineSpawnDistributor operation reference (2 declarations)</summary>

#### Constructor

```java
public LineSpawnDistributor(float x0, float y0, float x1, float y1)
```

Stores the endpoints of a line distribution without numeric validation.

- **`x0`** — first endpoint X
- **`y0`** — first endpoint Y
- **`x1`** — second endpoint X
- **`y1`** — second endpoint Y

#### computeOffset

```java
    public void computeOffset(Random random, float[] out)
```

Uses one uniform interpolation parameter for both coordinates, producing
uniform distance sampling along the segment. The first endpoint is possible;
the second is excluded except for degenerate segments.

- **`random`** — nonnull random source
- **`out`** — nonnull destination with at least two entries; X then Y

**Throws `NullPointerException`:** if random or out is null

**Throws `ArrayIndexOutOfBoundsException`:** if out has fewer than two entries

</details>

<a id="type-particle"></a>

### Particle

[Source](../../src/main/java/valthorne/graphics/particle/Particle.java#L38)

A pooled particle state container used by `ParticleSystem` during CPU simulation.

This class is intentionally a simple **mutable data object**. It stores all per-particle runtime state
so the particle system can update and render particles without allocating new objects.

##### What this class stores

- **Lifecycle**: active flag, age, and total life duration

- **Motion**: position and velocity

- **Transform**: rotation and rotation speed

- **Appearance**: start/end colors and current interpolated color

- **Scaling**: start/end scale endpoints and current interpolated scale

##### Pooling rules

- This class implements `Poolable`. The pool should call `reset()` before reuse.

- `Color` instances are stored as final fields to avoid allocating colors per particle or per frame.

- `reset()` restores deterministic defaults so the next spawn starts from a known state.

##### Coordinate and unit conventions

- Position is world-space in your engine's units.

- Velocity is units/second.

- Rotation is degrees, rotationSpeed is degrees/second.

<details>
<summary>Particle operation reference (28 declarations)</summary>

#### reset

```java
@Override
    public void reset()
```

Resets this particle to deterministic defaults so it can be reused by a pool.

This method is expected to be called by your `valthorne.io.pool.Pool` implementation
(or by `ParticleSystem`) before initializing a particle for a new spawn.

Defaults:

- inactive, position/velocity/rotation = 0

- life = 0, age = 0

- startScale = 1, endScale = 0, current scale = 1

- startColor = white (1,1,1,1), endColor = transparent white (1,1,1,0), current color = white

#### isActive

```java
public boolean isActive()
```

Returns whether this particle is currently active (alive).

**Returns:** true if active, false if inactive

#### setActive

```java
public void setActive(boolean active)
```

Sets whether this particle is currently active (alive).

- **`active`** — true to mark alive, false to mark inactive

#### getX

```java
public float getX()
```

Returns the particle's world-space x position.

**Returns:** x position

#### setX

```java
public void setX(float x)
```

Sets the particle's world-space x position.

- **`x`** — new x position

#### getY

```java
public float getY()
```

Returns the particle's world-space y position.

**Returns:** y position

#### setY

```java
public void setY(float y)
```

Sets the particle's world-space y position.

- **`y`** — new y position

#### getVelX

```java
public float getVelX()
```

Returns the particle's x velocity component.

**Returns:** x velocity

#### setVelX

```java
public void setVelX(float velX)
```

Sets the particle's x velocity component.

- **`velX`** — new x velocity

#### getVelY

```java
public float getVelY()
```

Returns the particle's y velocity component.

**Returns:** y velocity

#### setVelY

```java
public void setVelY(float velY)
```

Sets the particle's y velocity component.

- **`velY`** — new y velocity

#### getAge

```java
public float getAge()
```

Returns the particle's current age in seconds since spawn.

**Returns:** age in seconds

#### setAge

```java
public void setAge(float age)
```

Sets the particle's current age in seconds since spawn.

- **`age`** — new age in seconds

#### getLife

```java
public float getLife()
```

Returns the particle's total lifetime in seconds.

**Returns:** lifetime in seconds

#### setLife

```java
public void setLife(float life)
```

Sets the particle's total lifetime in seconds.

- **`life`** — new lifetime in seconds

#### getRotation

```java
public float getRotation()
```

Returns the particle's current rotation in degrees.

**Returns:** rotation in degrees

#### setRotation

```java
public void setRotation(float rotation)
```

Sets the particle's current rotation in degrees.

- **`rotation`** — new rotation in degrees

#### getRotationSpeed

```java
public float getRotationSpeed()
```

Returns the particle's rotation speed in degrees per second.

**Returns:** rotation speed in degrees/sec

#### setRotationSpeed

```java
public void setRotationSpeed(float rotationSpeed)
```

Sets the particle's rotation speed in degrees per second.

- **`rotationSpeed`** — new rotation speed in degrees/sec

#### getStartScale

```java
public float getStartScale()
```

Returns the scale at spawn time (t = 0).

**Returns:** start scale

#### setStartScale

```java
public void setStartScale(float startScale)
```

Sets the scale at spawn time (t = 0).

- **`startScale`** — start scale

#### getEndScale

```java
public float getEndScale()
```

Returns the scale at death time (t = 1).

**Returns:** end scale

#### setEndScale

```java
public void setEndScale(float endScale)
```

Sets the scale at death time (t = 1).

- **`endScale`** — end scale

#### getScale

```java
public float getScale()
```

Returns the current interpolated scale.

**Returns:** current scale

#### setScale

```java
public void setScale(float scale)
```

Sets the current interpolated scale.

- **`scale`** — current scale

#### getStartColor

```java
public Color getStartColor()
```

Returns the color at spawn time (t = 0).

**Returns:** start color reference (mutable, reused)

#### getEndColor

```java
public Color getEndColor()
```

Returns the color at death time (t = 1).

**Returns:** end color reference (mutable, reused)

#### getColor

```java
public Color getColor()
```

Returns the current interpolated color used for rendering.

**Returns:** current color reference (mutable, reused)

</details>

<a id="type-particleemitter"></a>

### ParticleEmitter

[Source](../../src/main/java/valthorne/graphics/particle/ParticleEmitter.java#L48)

Configuration object that controls how `ParticleSystem` spawns and initializes particles.

This class is intentionally a **mutable** parameter bag with a fluent API, designed to be edited
at runtime (e.g., changing emission rate, colors, gravity) without reallocating or rebuilding the
`ParticleSystem`.

##### Responsibilities

- **Spawn rate**: Controls continuous emission via `emissionRate` and `spawnAccumulator`.

- **Initialization ranges**: Life, speed, angle, starting rotation, rotation speed, scale endpoints.

- **Forces**: Simple constant acceleration inputs used by `ParticleSystem` (gravity Y and wind X).

- **Rendering defaults**: Base quad size and optional texture atlas region.

- **Spawn distribution**: Delegates spawn offsets to a `SpawnDistributor` strategy.

##### Notes

- `spawnAccumulator` is owned/advanced by `ParticleSystem`; treat it as internal state.

- `startColor` and `endColor` are stored as mutable `Color` instances to avoid allocations.

- The region values are stored in the same coordinate units expected by your `Texture.setRegion(...)` usage.

##### Example

```java
ParticleEmitter e = new ParticleEmitter()
    .setEmissionRate(120f)
    .setLifetime(0.15f, 0.5f)
    .setVelocity(40f, 180f)
    .setAngleDeg(0f, 360f)
    .setGravity(0f, -350f)
    .setWind(25f, 0f)
    .setBaseSize(12f, 12f)
    .setStartEndScale(1f, 0f)
    .setRotationSpeed(-90f, 90f)
    .setStartEndColor(new Color(1f, 0.9f, 0.2f, 1f), new Color(1f, 0.2f, 0f, 0f))
    .setSpawnCircle(32f, false)
    .setRegion(0, 0, 16, 16);
```

<details>
<summary>ParticleEmitter operation reference (44 declarations)</summary>

#### setLifetime

```java
public ParticleEmitter setLifetime(float minSeconds, float maxSeconds)
```

Sets lifetime range for newly spawned particles.

- **`minSeconds`** — minimum lifetime in seconds (clamped to `>= 0`)
- **`maxSeconds`** — maximum lifetime in seconds (clamped to `>= minSeconds`)

**Returns:** this emitter for chaining

#### setVelocity

```java
public ParticleEmitter setVelocity(float minSpeed, float maxSpeed)
```

Sets initial speed range for newly spawned particles.

- **`minSpeed`** — minimum speed magnitude (clamped to `>= 0`)
- **`maxSpeed`** — maximum speed magnitude (clamped to `>= minSpeed`)

**Returns:** this emitter for chaining

#### setAngleDeg

```java
public ParticleEmitter setAngleDeg(float minDeg, float maxDeg)
```

Sets initial direction range (in degrees) for newly spawned particles.

This range is sampled uniformly and used with the sampled speed to produce initial velocity.

- **`minDeg`** — minimum angle in degrees
- **`maxDeg`** — maximum angle in degrees

**Returns:** this emitter for chaining

#### setGravity

```java
public ParticleEmitter setGravity(float x, float y)
```

Sets gravity acceleration.

This API keeps an X parameter for symmetry, but this implementation currently uses only Y.

- **`x`** — gravity X (ignored)
- **`y`** — gravity Y acceleration in units/second^2

**Returns:** this emitter for chaining

#### setWind

```java
public ParticleEmitter setWind(float x, float y)
```

Sets wind acceleration.

This API keeps a Y parameter for symmetry, but this implementation currently uses only X.

- **`x`** — wind X acceleration in units/second^2
- **`y`** — wind Y (ignored)

**Returns:** this emitter for chaining

#### setBaseSize

```java
public ParticleEmitter setBaseSize(float w, float h)
```

Sets the base quad size applied to every particle before per-particle scaling.

- **`w`** — base width (clamped to `>= 0`)
- **`h`** — base height (clamped to `>= 0`)

**Returns:** this emitter for chaining

#### setStartEndScale

```java
public ParticleEmitter setStartEndScale(float start, float end)
```

Sets scale endpoints used for interpolation over particle lifetime.

- **`start`** — starting scale value
- **`end`** — ending scale value

**Returns:** this emitter for chaining

#### setStartEndRotation

```java
public ParticleEmitter setStartEndRotation(float startMinDeg, float startMaxDeg)
```

Sets initial rotation range (in degrees) for newly spawned particles.

- **`startMinDeg`** — minimum initial rotation in degrees
- **`startMaxDeg`** — maximum initial rotation in degrees

**Returns:** this emitter for chaining

#### setRotationSpeed

```java
public ParticleEmitter setRotationSpeed(float minDegPerSec, float maxDegPerSec)
```

Sets rotation speed range (in degrees/second) for newly spawned particles.

- **`minDegPerSec`** — minimum rotation speed
- **`maxDegPerSec`** — maximum rotation speed

**Returns:** this emitter for chaining

#### setStartEndColor

```java
public ParticleEmitter setStartEndColor(Color start, Color end)
```

Sets color endpoints used for interpolation over particle lifetime.

This copies the provided colors into internal reusable `Color` instances.

- **`start`** — start color (non-null)
- **`end`** — end color (non-null)

**Returns:** this emitter for chaining

**Throws `NullPointerException`:** if start or end is null

#### setRegion

```java
public ParticleEmitter setRegion(float left, float top, float right, float bottom)
```

Enables region usage and sets the region rectangle.

The `ParticleSystem` will call `Texture.setRegion(left, top, right, bottom)` every draw.

- **`left`** — left region coordinate
- **`top`** — top region coordinate
- **`right`** — right region coordinate
- **`bottom`** — bottom region coordinate

**Returns:** this emitter for chaining

#### clearRegion

```java
public ParticleEmitter clearRegion()
```

Disables region usage.

The region rectangle values are preserved but ignored until `setRegion(float, float, float, float)` is called again.

**Returns:** this emitter for chaining

#### setSpawnPoint

```java
public ParticleEmitter setSpawnPoint()
```

Convenience helper that sets point spawning (zero offset).

**Returns:** this emitter for chaining

#### setSpawnCircle

```java
public ParticleEmitter setSpawnCircle(float radius, boolean edgeOnly)
```

Convenience helper that sets circle spawning.

- **`radius`** — circle radius (passed to distributor)
- **`edgeOnly`** — if true, spawns on the circle edge; otherwise spawns over the area

**Returns:** this emitter for chaining

#### setSpawnBox

```java
public ParticleEmitter setSpawnBox(float halfWidth, float halfHeight)
```

Convenience helper that sets box spawning.

- **`halfWidth`** — half-width of the box in X
- **`halfHeight`** — half-height of the box in Y

**Returns:** this emitter for chaining

#### getEmissionRate

```java
public float getEmissionRate()
```

**Returns:** continuous emission rate in particles per second

#### setEmissionRate

```java
public ParticleEmitter setEmissionRate(float particlesPerSecond)
```

Sets continuous emission rate.

- **`particlesPerSecond`** — particles spawned per second (clamped to `>= 0`)

**Returns:** this emitter for chaining

#### getSpawnAccumulator

```java
public float getSpawnAccumulator()
```

**Returns:** spawn accumulator used to convert fractional spawns into whole spawns

#### setSpawnAccumulator

```java
public void setSpawnAccumulator(float spawnAccumulator)
```

Sets the spawn accumulator value.

This is typically updated by `ParticleSystem` and not manually by gameplay code.

- **`spawnAccumulator`** — new accumulator value

#### getLifeMin

```java
public float getLifeMin()
```

**Returns:** minimum lifetime in seconds

#### getLifeMax

```java
public float getLifeMax()
```

**Returns:** maximum lifetime in seconds

#### getSpeedMin

```java
public float getSpeedMin()
```

**Returns:** minimum initial speed magnitude

#### getSpeedMax

```java
public float getSpeedMax()
```

**Returns:** maximum initial speed magnitude

#### getAngleMinDeg

```java
public float getAngleMinDeg()
```

**Returns:** minimum initial velocity angle in degrees

#### getAngleMaxDeg

```java
public float getAngleMaxDeg()
```

**Returns:** maximum initial velocity angle in degrees

#### getGravityY

```java
public float getGravityY()
```

**Returns:** constant Y acceleration (gravity) in units/second^2

#### getWindX

```java
public float getWindX()
```

**Returns:** constant X acceleration (wind) in units/second^2

#### getBaseWidth

```java
public float getBaseWidth()
```

**Returns:** base particle width before per-particle scaling

#### getBaseHeight

```java
public float getBaseHeight()
```

**Returns:** base particle height before per-particle scaling

#### getStartScale

```java
public float getStartScale()
```

**Returns:** starting scale used when initializing particles

#### getEndScale

```java
public float getEndScale()
```

**Returns:** ending scale used during lifetime interpolation

#### getStartRotMinDeg

```java
public float getStartRotMinDeg()
```

**Returns:** minimum initial rotation in degrees

#### getStartRotMaxDeg

```java
public float getStartRotMaxDeg()
```

**Returns:** maximum initial rotation in degrees

#### getRotSpeedMinDegPerSec

```java
public float getRotSpeedMinDegPerSec()
```

**Returns:** minimum rotation speed in degrees/second

#### getRotSpeedMaxDegPerSec

```java
public float getRotSpeedMaxDegPerSec()
```

**Returns:** maximum rotation speed in degrees/second

#### getStartColor

```java
public Color getStartColor()
```

**Returns:** reusable start color instance (do not replace; you may mutate its channels if desired)

#### getEndColor

```java
public Color getEndColor()
```

**Returns:** reusable end color instance (do not replace; you may mutate its channels if desired)

#### isUseRegion

```java
public boolean isUseRegion()
```

**Returns:** true if region rendering is enabled

#### getRegionLeft

```java
public float getRegionLeft()
```

**Returns:** region left coordinate

#### getRegionTop

```java
public float getRegionTop()
```

**Returns:** region top coordinate

#### getRegionRight

```java
public float getRegionRight()
```

**Returns:** region right coordinate

#### getRegionBottom

```java
public float getRegionBottom()
```

**Returns:** region bottom coordinate

#### getShape

```java
public SpawnDistributor getShape()
```

**Returns:** current spawn distribution strategy

#### setShape

```java
public ParticleEmitter setShape(SpawnDistributor shape)
```

Sets the spawn distribution strategy.

The `SpawnDistributor` computes an offset (dx, dy) from the system's base position each spawn.

- **`shape`** — spawn distribution strategy (non-null)

**Returns:** this emitter for chaining

**Throws `NullPointerException`:** if shape is null

</details>

<a id="type-particlesystem"></a>

### ParticleSystem

[Source](../../src/main/java/valthorne/graphics/particle/ParticleSystem.java#L78)

Particle system optimized for rendering speed using point sprites (one vertex per particle) and a single draw call.

##### Key features

- **Point sprite rendering**: uploads 1 vertex per particle and renders with `glDrawArrays(GL_POINTS)`.

- **No per-particle `Texture` mutation** during drawing (unlike quad-based sprite particles).

- **Explicit projection state**: uses an engine-managed projection uniform instead of the fixed-function matrix stack.

- **Frame-rate independent continuous emission**: uses an accumulator (rate * delta).

- **Frame-rate independent burst emission**: burst is released over time using an accumulator (burstRate * delta).

- **Optional render throttling**: draw every N seconds and/or cap particles drawn per draw call.

##### Required render state

- Blending should be enabled for typical particles: `glEnable(GL_BLEND)`.

- The active window or viewport projection must be configured through the engine before drawing.

##### Example

```java
Texture smoke = new Texture("./assets/particles.png");

ParticleSystem ps = new ParticleSystem(smoke, 20000);
ps.setPosition(0, 0);

ps.getEmitter()
  .setEmissionRate(300)
  .setLifetime(0.4f, 1.2f)
  .setVelocity(25f, 140f)
  .setAngleDeg(70f, 110f)
  .setGravity(0f, -220f)
  .setWind(30f, 0f)
  .setBaseSize(10f, 22f)
  .setStartEndScale(1.0f, 0.0f)
  .setRotationSpeed(-180f, 180f)
  .setSpawnCircle(18f, false);

// In your update loop:
ps.update(delta);

// When you want a burst:
ps.burst(1200, 0.15f);

// Before draw(), ensure your viewport/camera has loaded matrices (like your normal sprite pipeline).
ps.draw();

// Cleanup:
ps.dispose();
```

<details>
<summary>ParticleSystem operation reference (13 declarations)</summary>

#### Constructor

```java
public ParticleSystem(Texture particleTexture, int maxParticles)
```

Creates a new point-sprite particle system.

This allocates a fixed-size CPU staging buffer and a GPU VBO sized to `maxParticles`.
It also initializes a `Pool` and an `active[]` list of the same capacity.

Attribute bindings are set explicitly (0..4) and the shader is re-linked.

- **`particleTexture`** — shared texture used for all particles (not owned by this system)
- **`maxParticles`** — maximum number of particles that may be alive at once

**Throws `NullPointerException`:** if `particleTexture` is null

**Throws `IllegalArgumentException`:** if `maxParticles <= 0`

#### setPosition

```java
public void setPosition(float x, float y)
```

Sets the world-space emitter position.

New particles spawn at this position plus an offset computed by `ParticleEmitter#getShape()`.

- **`x`** — world-space x position
- **`y`** — world-space y position

#### getEmitter

```java
public ParticleEmitter getEmitter()
```

Returns the emitter configuration object used by this system.

**Returns:** emitter configuration instance

#### burst

```java
public void burst(int count)
```

Queues a burst of particles and spreads it across a default duration of 0.10 seconds.

- **`count`** — number of particles to queue; ignored if `count <= 0`

#### burst

```java
public void burst(int count, float spreadSeconds)
```

Queues a burst of particles and releases it over `spreadSeconds` (frame-rate independent).

If `spreadSeconds <= 0`, the burst is released as fast as possible (subject to capacity and pool).

- **`count`** — number of particles to queue; ignored if `count <= 0`
- **`spreadSeconds`** — burst release duration in seconds; `<= 0` means immediate release

#### setBurstRatePerSecond

```java
public void setBurstRatePerSecond(float particlesPerSecond)
```

Sets the burst release rate in particles per second (frame-rate independent).

- **`particlesPerSecond`** — burst release rate; values &lt; 0 are clamped to 0

#### getBurstRemaining

```java
public int getBurstRemaining()
```

Returns how many burst particles are still queued.

**Returns:** remaining burst particles

#### setRenderInterval

```java
public void setRenderInterval(float seconds)
```

Sets the minimum interval (seconds) between successful draws.

If set to 0, rendering occurs every frame. Simulation still runs every update.

- **`seconds`** — draw interval in seconds; values &lt; 0 are clamped to 0

#### setMaxDrawPerFrame

```java
public void setMaxDrawPerFrame(int max)
```

Caps how many particles are drawn each time `draw()` runs.

This uses round-robin sampling via `drawCursor` so all particles eventually render
even when capped.

- **`max`** — maximum particles to draw per call; values &lt; 1 are clamped to 1

#### update

```java
public void update(float delta)
```

Updates emission and advances all active particles.

This method is frame-rate independent for both continuous emission and burst emission:
both use accumulators that scale by `delta`.

- **`delta`** — seconds since last frame; ignored if `delta <= 0`

#### draw

```java
public void draw()
```

Draws particles using point sprites.

This performs one upload to the VBO and one `glDrawArrays(GL_POINTS)` call.
If render throttling is enabled, this may skip drawing until enough time has elapsed.

#### clear

```java
public void clear()
```

Kills all particles immediately and returns them to the pool.

This also clears all accumulators and resets draw throttling state.

#### dispose

```java
public void dispose()
```

Disposes GPU resources created by this particle system.

This disposes the internal shader and deletes the internal VBO. It does not dispose the shared `Texture`.

</details>

<a id="type-pointspawndistributor"></a>

### PointSpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/PointSpawnDistributor.java#L15)

A concrete implementation of the `SpawnDistributor` interface. The
`PointSpawnDistributor` provides a fixed point spawn distribution,
where all offsets are consistently set to zero. This creates a static
positioning system with no variation, ensuring all entities or particles
spawn at the origin.

<details>
<summary>PointSpawnDistributor operation reference (1 declarations)</summary>

#### computeOffset

```java
    public void computeOffset(Random random, float[] out)
```

Writes a zero offset, leaving the particle at the emitter origin. Does not
read or advance the supplied random generator.

- **`random`** — unused generator, possibly null
- **`out`** — nonnull destination with at least two entries; X then Y

**Throws `NullPointerException`:** if out is null

**Throws `ArrayIndexOutOfBoundsException`:** if out has fewer than two entries

</details>

<a id="type-radialburstspawndistributor"></a>

### RadialBurstSpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/RadialBurstSpawnDistributor.java#L23)

A radial burst spawn distributor that calculates offsets for positioning entities or particles
in a circular burst pattern around an origin point. The spawn positions are distributed
along the circumference of a circle with the specified radius.

This implementation uses a provided random number generator to generate random angles
(in radians), and calculates the corresponding (x, y) offsets for positions
around the circle.

The computed offsets are written into the provided output array, indexed as follows:
- Index 0: X-offset
- Index 1: Y-offset

The output values reflect positions on a circle defined by the radius of this distributor.

<details>
<summary>RadialBurstSpawnDistributor operation reference (2 declarations)</summary>

#### Constructor

```java
public RadialBurstSpawnDistributor(float radius)
```

Creates circumference sampling with negative radius clamped to zero.

- **`radius`** — distance from the emitter origin

#### computeOffset

```java
    public void computeOffset(Random random, float[] out)
```

Samples one uniform angle and writes a point on the fixed-radius circumference.
A zero radius produces the origin.

- **`random`** — nonnull random source
- **`out`** — nonnull destination with at least two entries; X then Y

**Throws `NullPointerException`:** if random or out is null

**Throws `ArrayIndexOutOfBoundsException`:** if out has fewer than two entries

</details>

<a id="type-rectedgespawndistributor"></a>

### RectEdgeSpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/RectEdgeSpawnDistributor.java#L20)

A `RectEdgeSpawnDistributor` is responsible for distributing spawn offsets along the edges
of a rectangle. The rectangle is defined by its half-width and half-height. Spawn positions
are randomly chosen along one of the four edges: top, right, bottom, or left.
The output offsets are computed based on a random choice of edge and a linear interpolation
along the chosen edge.

This implementation ensures that the computed offsets always lie on the perimeter of the rectangle.
The `MathUtils#lerp` method is used for interpolating positions along the edges.

<details>
<summary>RectEdgeSpawnDistributor operation reference (2 declarations)</summary>

#### Constructor

```java
public RectEdgeSpawnDistributor(float halfWidth, float halfHeight)
```

Creates a centered rectangular edge distribution, clamping each negative
half-extent to zero.

- **`halfWidth`** — horizontal half-extent
- **`halfHeight`** — vertical half-extent

#### computeOffset

```java
    public void computeOffset(Random random, float[] out)
```

Chooses each of four edges with equal probability, then samples uniformly
along that edge. This is not uniform per unit perimeter length when width
and height differ.

- **`random`** — nonnull random source
- **`out`** — nonnull destination with at least two entries; X then Y

**Throws `NullPointerException`:** if random or out is null

**Throws `ArrayIndexOutOfBoundsException`:** if out has fewer than two entries

</details>

<a id="type-ringspawndistributor"></a>

### RingSpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/RingSpawnDistributor.java#L15)

Implements the `SpawnDistributor` interface to distribute spawn locations
within a ring-shaped area. The spawn points are calculated randomly and uniformly
between the inner and outer radii of the ring.

<details>
<summary>RingSpawnDistributor operation reference (2 declarations)</summary>

#### Constructor

```java
public RingSpawnDistributor(float innerRadius, float outerRadius)
```

Clamps inner radius to zero and outer radius to at least that result. Equal
radii produce circumference sampling; nonfinite inputs are not rejected.

- **`innerRadius`** — requested inner radius
- **`outerRadius`** — requested outer radius

#### computeOffset

```java
    public void computeOffset(Random random, float[] out)
```

Samples a uniform angle and interpolates squared radii before taking a square
root, giving uniform area density across the annulus for valid finite radii.

- **`random`** — nonnull random source
- **`out`** — nonnull destination with at least two entries; X then Y

**Throws `NullPointerException`:** if random or out is null

**Throws `ArrayIndexOutOfBoundsException`:** if out has fewer than two entries

</details>

<a id="type-spawndistributor"></a>

### SpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/SpawnDistributor.java#L13)

An interface representing a mechanism for distributing spawn offsets. Implementations
determine how positions are calculated for entities or particles relative to an origin.
The offsets are generated stochastically, using a random number generator.

<details>
<summary>SpawnDistributor operation reference (1 declarations)</summary>

#### computeOffset

```java
void computeOffset(Random random, float[] out)
```

Computes and writes spawn offsets into the provided output array.
The computed offsets are based on a provided random number generator,
allowing stochastic variations for distributing entities or particles.

- **`random`** — the random number generator used for stochastic offset calculation
- **`out`** — the array to store the computed offset values, must be pre-initialized and of sufficient length

</details>

<a id="type-spiralspawndistributor"></a>

### SpiralSpawnDistributor

[Source](../../src/main/java/valthorne/graphics/particle/SpiralSpawnDistributor.java#L17)

The SpiralSpawnDistributor is an implementation of the SpawnDistributor interface.
This class generates stochastic spawn offsets based on a spiral pattern.
The spiral is defined by a maximum radius and a number of turns, both of which
influence the distribution of offsets.

The spawn offsets are computed by mapping a random value onto a spiral equation,
resulting in offsets distributed along the spiral's path in 2D space.

<details>
<summary>SpiralSpawnDistributor operation reference (2 declarations)</summary>

#### Constructor

```java
public SpiralSpawnDistributor(float maxRadius, float turns)
```

Creates an Archimedean spiral parameterization, clamping negative radius and
turn count to zero. Values are not checked for finiteness.

- **`maxRadius`** — final spiral radius
- **`turns`** — revolutions from the origin to the outer end

#### computeOffset

```java
    public void computeOffset(Random random, float[] out)
```

Samples one uniform parameter t and evaluates radius maxRadius*t and angle
2*pi*turns*t. Sampling is uniform in the parameter, not arc length or area.

- **`random`** — nonnull random source
- **`out`** — nonnull destination with at least two entries; X then Y

**Throws `NullPointerException`:** if random or out is null

**Throws `ArrayIndexOutOfBoundsException`:** if out has fewer than two entries

</details>

## Related guides

- [Textures, sprites, atlases, and batching](textures.md)
- [Ticks and frame timing](timing.md)
- [3D particles and physics integration](particles-3d.md)
