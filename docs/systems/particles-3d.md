# 3D particles and physics integration

Author: Albert Beaupre

[System manual](README.md)

## Purpose

ParticleEmitter3D manages a capacity-limited pool of reusable particle instances. Cosmetic particles use analytic constant-acceleration motion; optional physics integration gives particles Jolt bodies. A particle can contribute a mesh or billboard and configure appearance through its material.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Spawn initialization | A callback configures each recycled particle's lifetime, motion, and visual references. |
| Rate and bursts | Automatic emission and explicit bursts serve continuous and instantaneous effects. |
| Visual modes | Mesh particles can attach to Scene3D; billboards submit through the batch path. |
| Physics mode | A settings factory creates bodies and advances particle lifetime on the world clock. |
| Pooling | Released particles are reset and reused rather than permanently retained. |

## Getting started

1. Construct an emitter with a capacity and initializer.
2. Assign borrowed visuals and configure lifetime, velocity, acceleration, and emission rate.
3. Choose cosmetic updates or attach physics through a body-settings factory.
4. Update/submit on the proper clock, and close the emitter when removing the effect.

## Ownership and lifecycle

Particles are pooled; references are temporary and can later represent another birth. The emitter owns its created bodies and memberships but borrows models, materials, textures, scenes, and the physics world.

## Important behavior

- When physics is attached, advance the world externally; emitter.update does not advance that clock a second time.
- World gravity is additional to emitter acceleration according to body settings.
- Capacity limits can drop births; tune rate, lifetime, and capacity together.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Particle3D`](#type-particle3d)
- [`ParticleEmitter3D`](#type-particleemitter3d)

<a id="type-particle3d"></a>

### Particle3D

[Source](../../src/main/java/valthorne/graphics/particle/Particle3D.java#L32)

Pooled 3D particle with a centered spherical billboard and an optional mesh.
Position, velocity and acceleration use world units and seconds. Models,
materials and textures are borrowed; the emitter owns an optional Jolt body.

Configure position through `getPosition()`, including mesh particles.
The emitter copies that position to the mesh after initialization and updates.
With physics enabled, Jolt controls position, velocity and mesh rotation after
creation: use `getBody()` to teleport, apply impulses or change velocity.
Acceleration remains editable and adds force on top of native world gravity.

Live objects may be reset and reused after expiration. Never use a retained
particle reference as a permanent identity in delayed contact callbacks.

<details>
<summary>Particle3D operation reference (15 declarations)</summary>

#### getLight

```java
public PointLight3D getLight()
```

Lazily creates and returns this particle's owned point light without enabling
it. Defaults are white, range 10, intensity 1, and no shadow maps. An enabled
light follows the canonical position plus a world-space offset; it does not
make the particle material emissive. Pooled reuse resets its parameters.

**Returns:** live point light, reused for this pooled particle

#### setLightEnabled

```java
public Particle3D setLightEnabled(boolean enabled)
```

Enables or removes the optional light in the attached scene immediately.
Enabling lazily allocates the light; disabling an unallocated light is a no-op.
The particle's render material is unchanged.

- **`enabled`** — whether the light should belong to the attached scene

**Returns:** this particle

#### isLightEnabled

```java
public boolean isLightEnabled()
```

Reads the requested light-enabled state, which can be true without a scene.

**Returns:** true if optional light state exists and is enabled

#### setLightOffset

```java
public Particle3D setLightOffset(float x, float y, float z)
```

Sets a finite world-space offset and immediately synchronizes light position
and membership. It does not rotate with the body. A zero offset avoids
allocation when no light exists; a nonzero offset does not enable the light.

- **`x`** — world X offset
- **`y`** — world Y offset
- **`z`** — world Z offset

**Returns:** this particle

**Throws `IllegalArgumentException`:** if any component is non-finite

#### getSprite

```java
public BillboardSprite3D getSprite()
```

Returns the live centered billboard used when no mesh geometry is assigned.
Its position remains canonical even while a mesh is selected. Pooled reuse
resets billboard state, so retained references are not permanent particles.

**Returns:** owned mutable billboard

#### getPosition

```java
public Vector3f getPosition()
```

Exposes the canonical world position stored by the billboard. Cosmetic
updates integrate it directly; physical updates overwrite it from the body.
For physical teleports, change the body rather than this cached vector.

**Returns:** live position in world units

#### getVelocity

```java
public Vector3f getVelocity()
```

Exposes world velocity used for cosmetic integration and initial body setup.
After body creation, physics synchronization overwrites this vector; change
the body to alter physical velocity.

**Returns:** live velocity in world units per second

#### getAcceleration

```java
public Vector3f getAcceleration()
```

Exposes constant world acceleration for cosmetic motion. In physics mode
the emitter multiplies this vector by mass and applies force before each
tick, in addition to the world's configured gravity.

**Returns:** live acceleration in world units per second squared

#### getModelInstance

```java
public ModelInstance3D getModelInstance()
```

Lazily creates a reusable mesh instance at the canonical position. Merely
requesting it does not enable mesh rendering; assign geometry with setModel.
Scale affects rendering only, while physics settings define collision size.
Physical creation clears parent transforms because body poses are world-space.

**Returns:** live reusable mesh instance

#### setModel

```java
public Particle3D setModel(Model3D geometry)
```

Selects borrowed mesh geometry and synchronizes position and scene membership.
Null restores billboard rendering and removes the mesh from the scene.
Geometry is not disposed or copied, and physics collision shape is unchanged.

- **`geometry`** — borrowed mesh, or null for billboard rendering

**Returns:** this particle

#### getBody

```java
public RigidBody3D getBody()
```

Exposes the emitter-owned body for physical forces, velocities, and teleports.
Do not retain it beyond particle expiration or treat pooled reuse as identity.

**Returns:** live body, or null for a cosmetic or released particle

#### getAge

```java
public float getAge()
```

Reads elapsed simulation time, which may exceed lifetime after a large step.

**Returns:** age in seconds

#### getLifetime

```java
public float getLifetime()
```

Reads the configured expiration threshold without changing age.

**Returns:** positive finite lifetime in seconds

#### setLifetime

```java
public Particle3D setLifetime(float seconds)
```

Changes the expiration threshold without resetting age. Shortening it below
the current age makes the particle eligible for retirement at the next update.

- **`seconds`** — positive finite lifetime

**Returns:** this particle

**Throws `IllegalArgumentException`:** if seconds is nonpositive or non-finite

#### getProgress

```java
public float getProgress()
```

Returns age divided by lifetime with an upper clamp of one. Normal emitter
updates keep age nonnegative; this accessor does not impose a lower clamp.

**Returns:** normalized lifetime progress

</details>

<a id="type-particle3d-attachedlight"></a>

### Particle3D.AttachedLight — internal support type

[Source](../../src/main/java/valthorne/graphics/particle/Particle3D.java#L61)

Lazily allocated point-light configuration and scene-registration state.
The particle owns this light independently of borrowed render materials;
pooled reuse restores its defaults without replacing the object.

<a id="type-particleemitter3d"></a>

### ParticleEmitter3D

[Source](../../src/main/java/valthorne/graphics/particle/ParticleEmitter3D.java#L59)

Capacity-limited, reusable 3D particles with optional Jolt simulation. Cosmetic
particles use exact constant-acceleration motion and subframe emission times.
Mesh particles can attach to a Scene3D; billboards submit through ModelBatch3D.
Models, materials, textures, scenes and the physics world remain borrowed.

With `setPhysics(PhysicsWorld3D, Function)`, advance the world externally.
The emitter emits at fixed tick boundaries, applies mass times acceleration
before each tick, then synchronizes native poses and ages particles afterward.
World gravity is additional, multiplied by the factory's gravity factor. Physics
births are quantized to the beginning of a tick and never analytically backdated
through colliders. Calling `update(float)` does not advance this clock a
second time. Dropped world time is also dropped from the particle clock. Issue
bursts before advancing the world (or from an earlier before-step listener) for
the emitter's acceleration force to apply on their first simulation tick.

The initializer and physics factory run synchronously for each spawn. They
must not reenter or modify the emitter. Use one owning thread (the physics
world's thread when attached), and treat pooled particle references as temporary.
Closing releases owned bodies and scene membership, never borrowed resources.

```java
ParticleEmitter3D sparks = new ParticleEmitter3D(256, particle -> {
    particle.setLifetime(0.8f);
    particle.getPosition().set(0f, 0f, 1f);
    particle.getVelocity().set(1f, 0f, 3f);
    particle.getAcceleration().set(0f, 0f, -9.8f);
    // Configure a borrowed sprite texture or model here.
});
sparks.setEmissionRate(40f);
// Each frame, with a batch already begun:
sparks.update(deltaSeconds);
sparks.submit(batch);
// Release emitter-owned state when the effect is no longer needed:
sparks.close();
```

<details>
<summary>ParticleEmitter3D operation reference (19 declarations)</summary>

#### Constructor

```java
public ParticleEmitter3D(int capacity, Consumer<Particle3D> initializer)
```

Creates an empty emitter with continuous emission enabled at zero rate.
The initializer runs synchronously for each newly allocated or reset particle
and must not reenter or mutate the emitter.

- **`capacity`** — positive maximum number of simultaneously live particles
- **`initializer`** — callback configuring each particle before scene/body creation

**Throws `IllegalArgumentException`:** if capacity is nonpositive

**Throws `NullPointerException`:** if initializer is null

#### setEmissionRate

```java
public ParticleEmitter3D setEmissionRate(float perSecond)
```

Sets automatic births per simulation second and discards fractional progress.
Existing particles and the emitting flag are unchanged; zero disables
automatic births without affecting explicit bursts.

- **`perSecond`** — finite nonnegative birth rate

**Returns:** this emitter

**Throws `IllegalArgumentException`:** if the rate is negative or non-finite

**Throws `IllegalStateException`:** if closed or busy

#### setEmitting

```java
public ParticleEmitter3D setEmitting(boolean enabled)
```

Enables or pauses automatic births without resetting fractional progress.
Existing particles continue to age and explicit bursts remain available.

- **`enabled`** — whether automatic emission should run

**Returns:** this emitter

**Throws `IllegalStateException`:** if closed or busy

#### getEmissionRate

```java
public float getEmissionRate()
```

Reads the configured automatic birth rate, including after closure.

**Returns:** requested particles per simulation second

#### isEmitting

```java
public boolean isEmitting()
```

Reads the automatic-emission flag. A true value alone does not imply births:
the rate may be zero, capacity full, or emitter closed.

**Returns:** configured automatic-emission state

#### isClosed

```java
public boolean isClosed()
```

Reports whether final cleanup completed and the emitter rejected further use.

**Returns:** true after successful close

#### getParticleCount

```java
public int getParticleCount()
```

Reads current active membership, including particles awaiting the next
retirement check after an external body destruction.

**Returns:** current number of registered particles

#### getCapacity

```java
public int getCapacity()
```

Reads the fixed upper bound on simultaneous active membership.

**Returns:** constructor-supplied positive capacity

#### getParticles

```java
public List<Particle3D> getParticles()
```

Returns a cached unmodifiable live membership view. Particle objects are
mutable and pooled; expiration or clear changes the view and may later reuse
the same object for another birth.

**Returns:** active particles in retained spawn order

#### getPhysicsWorld

```java
public PhysicsWorld3D getPhysicsWorld()
```

Exposes the borrowed world whose fixed-step callbacks drive physical particles.
Reading does not validate world ownership or lifetime.

**Returns:** configured world, or null for cosmetic mode

#### setPhysics

```java
public ParticleEmitter3D setPhysics(PhysicsWorld3D world, Function<Particle3D, BodySettings3D> factory)
```

Enables physics for subsequent births; active membership must be empty.
Registers before/after-step listeners on the borrowed world and resets
fractional emission progress. Advance the world externally on its owner thread.

Each factory result must be non-null and dynamic. Its collision shape, mass,
rotation, damping, gravity factor, and flags are retained, but position and
linear velocity are overwritten from the initialized particle. Reused settings
must not be accessed concurrently. Render scale does not resize collision
geometry. The factory must not reenter the emitter.

- **`world`** — live physics world owned by the calling thread
- **`factory`** — callback creating body settings for each initialized particle

**Returns:** this emitter

**Throws `NullPointerException`:** if world or factory is null

**Throws `IllegalStateException`:** if closed, busy, nonempty, or the world cannot be accessed

#### disablePhysics

```java
public ParticleEmitter3D disablePhysics()
```

Unregisters physics callbacks and selects cosmetic motion for future births.
Requires an empty emitter; borrowed world resources remain alive. Resets
fractional emission progress without changing rate or initializer.

**Returns:** this emitter

**Throws `IllegalStateException`:** if closed, busy, or nonempty

#### attach

```java
public ParticleEmitter3D attach(Scene3D destination)
```

Transfers current and future mesh particles and enabled point lights to the
borrowed scene. Billboards still require explicit batch submission.
Reattaching also refreshes particle membership missing from the same scene.

- **`destination`** — non-null scene receiving particle meshes and lights

**Returns:** this emitter

**Throws `NullPointerException`:** if destination is null

**Throws `IllegalStateException`:** if closed or busy

#### detach

```java
public ParticleEmitter3D detach()
```

Removes current mesh and light membership and clears the destination for
future births. Particle lifetimes, bodies, and manual submission remain active.

**Returns:** this emitter

**Throws `IllegalStateException`:** if closed or busy

#### burst

```java
public int burst(int count)
```

Spawns at age zero up to the requested count or remaining capacity. Ignores
the automatic-emission flag. Initialization/body-creation failure releases
the failed particle; earlier successful births from this call remain active.

- **`count`** — nonnegative requested births

**Returns:** number accepted if all requested initializations succeed

**Throws `IllegalArgumentException`:** if count is negative

**Throws `IllegalStateException`:** if closed, busy, or the physics world is inaccessible

#### update

```java
public void update(float delta)
```

Advances cosmetic motion and subframe-distributed births. In physics mode,
only synchronizes/compacts physical particles; fixed-step callbacks supply
their clock. Externally destroyed bodies are retired. Delta is validated even
when physical time is driven elsewhere.

- **`delta`** — finite nonnegative elapsed seconds

**Throws `IllegalArgumentException`:** if delta is negative or non-finite

**Throws `IllegalStateException`:** if closed or busy

#### submit

```java
public int submit(ModelBatch3D batch)
```

Synchronizes placement and submits each assigned mesh, otherwise its billboard,
to an already active batch. Does not update age or physics. Avoid submitting
scene-attached meshes again if the scene already submits them for this pass.

- **`batch`** — active destination batch

**Returns:** number of submissions accepted by the batch

**Throws `IllegalStateException`:** if closed, busy, or the batch rejects its state

#### clear

```java
public void clear()
```

Releases all active bodies and scene entries, retaining reusable pooled objects
and configuration. Resets fractional births; physics callbacks remain
registered so future emissions can resume.

**Throws `IllegalStateException`:** if closed or busy

#### close

```java
    public void close()
```

Releases active bodies, scene membership, physics listener registrations, and
pooled references. Borrowed scenes, world, models, textures, and materials
remain owned by their callers. Repeated calls after successful closure are
no-ops; mutating operations afterward fail.

**Throws `IllegalStateException`:** if invoked reentrantly while busy

</details>

## Related guides

- [2D particles and spawn distributions](particles-2d.md)
- [Jolt rigid-body physics](physics.md)
- [3D models, materials, scenes, and billboards](models.md)
- [Filament rendering](filament.md)
- [Existing particles 3d guide](../particles-3d.md)
