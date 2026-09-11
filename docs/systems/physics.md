# Jolt rigid-body physics

Author: Albert Beaupre

[System manual](README.md)

## Purpose

PhysicsWorld3D owns a native Jolt simulation with fixed-step advancement. BodySettings3D describes initial body state; CollisionShape3D describes its collision geometry. RigidBody3D exposes runtime controls and can synchronize a visual model with native simulation.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Motion types | Static, kinematic, and dynamic bodies serve stationary geometry, controlled motion, and simulation-driven motion. |
| Shapes and layers | Primitive/mesh colliders define contact geometry; layer rules filter interactions. |
| Forces and impulses | Forces act over simulation time, while impulses directly change momentum. |
| Sensors and contacts | Sensors report overlap without ordinary solid response; contact events expose interaction. |
| Queries and joints | Raycasts inspect the world and distance joints constrain body relationships. |
| Step listeners | Before/after-step hooks integrate systems that must share the physics clock. |

## Getting started

1. Create shapes and a world, then configure collision-layer relationships.
2. Build settings with units and initial pose appropriate to the scene.
3. Create bodies and advance the world from a single fixed-step owner.
4. Apply inputs before the relevant step and read synchronized poses afterward.
5. Remove/close bodies and constraints according to ownership before closing the world and shapes.

## Ownership and lifecycle

World operations and native resources follow an owning-thread lifecycle. Use meters, seconds, kilograms, and their derived units where specified. Settings configure future bodies; editing settings does not retroactively modify existing bodies.

## Important behavior

- A large render delta is not a reason to apply a force multiple times outside the step policy.
- Avoid mutating world structure in callbacks that prohibit it.
- Close a physical particle emitter before the borrowed world it uses.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`BodySettings3D`](#type-bodysettings3d)
- [`CollisionLayers3D`](#type-collisionlayers3d)
- [`CollisionShape3D`](#type-collisionshape3d)
- [`ContactEvent3D`](#type-contactevent3d)
- [`ContactEvent3D.Type`](#type-contactevent3d-type)
- [`DistanceJoint3D`](#type-distancejoint3d)
- [`MotionType3D`](#type-motiontype3d)
- [`PhysicsRayHit3D`](#type-physicsrayhit3d)
- [`PhysicsWorld3D`](#type-physicsworld3d)
- [`PhysicsWorld3D.Settings`](#type-physicsworld3d-settings)
- [`RigidBody3D`](#type-rigidbody3d)

<a id="type-bodysettings3d"></a>

### BodySettings3D

[Source](../../src/main/java/valthorne/math/physics/BodySettings3D.java#L37)

Reusable configuration for creating a rigid body in `PhysicsWorld3D`.
Shape and motion type are fixed at construction; fluent setters change the
initial transform, velocity, material response and simulation flags. Settings
use meters, kilograms and seconds, matching the physics world's conventions.

##### Creation and Reuse

```java
BodySettings3D settings = new BodySettings3D(shape, MotionType3D.DYNAMIC)
        .setMass(2f)
        .setPosition(0, 0, 5)
        .setRestitution(0.25f);
RigidBody3D first = world.createBody(settings);
settings.setPosition(3, 0, 5);
RigidBody3D second = world.createBody(settings);
```

The world reads these values at creation; changing this object afterward
does not update existing bodies. Position and rotation setters copy their
inputs, while the shape definition is retained by reference. Construction
allocates no native body. Do not mutate settings concurrently with body creation.

##### Defaults

Initial position and linear velocity are zero and rotation is identity.
Mass is one kilogram, friction is `0.5f`, restitution is zero, both
damping values are `0.05f`, and gravity factor is one. The body uses layer
zero, permits sleeping, and starts with sensor and continuous-collision modes
disabled. Native static-body creation ignores the mass override.

<details>
<summary>BodySettings3D operation reference (18 declarations)</summary>

#### Constructor

```java
public BodySettings3D(CollisionShape3D shape, MotionType3D motion)
```

Retains the shape and motion category and initializes all other settings
to their defaults. Static-only shapes are rejected for moving categories;
choose a convex shape for a dynamic or kinematic body.

- **`shape`** — the nonnull collision-shape definition
- **`motion`** — the nonnull motion category

**Throws `NullPointerException`:** if shape or motion is null

**Throws `IllegalArgumentException`:** if a static-only shape is paired with nonstatic motion

#### getMotionType

```java
public MotionType3D getMotionType()
```

Returns the fixed motion category of bodies created with these settings.

#### getMass

```java
public float getMass()
```

Returns the configured mass override in kilograms.

#### setRotationLocked

```java
public BodySettings3D setRotationLocked(boolean locked)
```

Locks all angular degrees of freedom while preserving free translation.

#### isRotationLocked

```java
public boolean isRotationLocked()
```

Returns whether bodies subsequently created from these settings lock all
angular degrees of freedom. This flag does not modify bodies already created.

**Returns:** true when rotational motion is disabled for new bodies

#### setPosition

```java
public BodySettings3D setPosition(float x, float y, float z)
```

Assigns the initial world-space position from finite components. Values
are copied into internal storage and affect only bodies created afterward.

- **`x`** — the world X coordinate in meters
- **`y`** — the world Y coordinate in meters
- **`z`** — the world Z coordinate in meters

**Returns:** these settings for chaining

**Throws `IllegalArgumentException`:** if any component is non-finite

#### setPosition

```java
public BodySettings3D setPosition(Vector3f position)
```

Copies a finite world-space position. Later changes to the supplied vector
do not alter this configuration, and failed validation leaves it unchanged.

- **`position`** — the initial world position in meters

**Returns:** these settings for chaining

**Throws `NullPointerException`:** if position is null

**Throws `IllegalArgumentException`:** if any component is non-finite

#### setRotation

```java
public BodySettings3D setRotation(Quaternionf rotation)
```

Copies and normalizes the initial orientation into internal quaternion storage.
Invalid components are rejected before the existing orientation is changed.

- **`rotation`** — the nonnull orientation to copy

**Returns:** these settings for chaining

**Throws `NullPointerException`:** if rotation is null

**Throws `IllegalArgumentException`:** if rotation is non-finite or zero-length

#### setLinearVelocity

```java
public BodySettings3D setLinearVelocity(float x, float y, float z)
```

Assigns a finite initial world-space linear velocity. This is a creation
value rather than an impulse or a change to an existing body's velocity.

- **`x`** — the X velocity in meters per second
- **`y`** — the Y velocity in meters per second
- **`z`** — the Z velocity in meters per second

**Returns:** these settings for chaining

**Throws `IllegalArgumentException`:** if any component is non-finite

#### setMass

```java
public BodySettings3D setMass(float kilograms)
```

Sets the positive mass override supplied for nonstatic body creation.
The world asks the native engine to calculate inertia for that mass.
Static bodies do not use this override, but validation still applies here.

- **`kilograms`** — the finite mass, strictly greater than zero

**Returns:** these settings for chaining

**Throws `IllegalArgumentException`:** if kilograms is non-finite or not positive

#### setFriction

```java
public BodySettings3D setFriction(float friction)
```

Sets the nonnegative surface-friction coefficient forwarded at creation.
The wrapper does not cap the coefficient at one or change existing contacts.

- **`friction`** — the finite, nonnegative friction coefficient

**Returns:** these settings for chaining

**Throws `IllegalArgumentException`:** if friction is negative or non-finite

#### setRestitution

```java
public BodySettings3D setRestitution(float restitution)
```

Sets the surface restitution coefficient within the inclusive zero-to-one
interval. Invalid input is rejected before replacing the previous value.

- **`restitution`** — the finite coefficient controlling collision bounce

**Returns:** these settings for chaining

**Throws `IllegalArgumentException`:** if restitution is non-finite or outside zero through one

#### setDamping

```java
public BodySettings3D setDamping(float linear, float angular)
```

Sets the finite nonnegative damping values passed to native body creation.
No upper bound is imposed by this wrapper. Linear damping is assigned
first, so an invalid angular value leaves the new linear value in place.

- **`linear`** — the nonnegative linear damping setting
- **`angular`** — the nonnegative angular damping setting

**Returns:** these settings for chaining

**Throws `IllegalArgumentException`:** if either setting is negative or non-finite

#### setGravityFactor

```java
public BodySettings3D setGravityFactor(float factor)
```

Sets the multiplier applied to world gravity by the native body.
Zero suppresses gravity and negative factors are accepted, reversing its
direction for bodies affected by gravity. The value is not clamped.

- **`factor`** — the finite gravity multiplier

**Returns:** these settings for chaining

**Throws `IllegalArgumentException`:** if factor is non-finite

#### setLayer

```java
public BodySettings3D setLayer(int layer)
```

Selects a user collision layer. The world maps it together with the motion
category into its native filter layers when the body is created.

- **`layer`** — the user-layer index from zero through fifteen

**Returns:** these settings for chaining

**Throws `IllegalArgumentException`:** if layer is outside the supported range

#### setSensor

```java
public BodySettings3D setSensor(boolean sensor)
```

Selects the native sensor flag for subsequently created bodies. Sensor
configuration does not bypass the world's collision-layer filtering or
register contact listeners automatically.

- **`sensor`** — whether to create the body as a sensor

**Returns:** these settings for chaining

#### setContinuousCollision

```java
public BodySettings3D setContinuousCollision(boolean enabled)
```

Chooses native linear-cast motion quality when enabled, or discrete motion
quality when disabled. This setting changes creation-time collision handling;
it does not change the world's fixed timestep or substep limit.

- **`enabled`** — whether to request continuous linear-cast collision handling

**Returns:** these settings for chaining

#### setAllowSleeping

```java
public BodySettings3D setAllowSleeping(boolean enabled)
```

Sets whether the native body is allowed to sleep. This is a permission,
not a command to sleep immediately; nonstatic bodies are initially added
in the activated state by the world.

- **`enabled`** — whether native sleeping should be allowed

**Returns:** these settings for chaining

</details>

<a id="type-collisionlayers3d"></a>

### CollisionLayers3D

[Source](../../src/main/java/valthorne/math/physics/CollisionLayers3D.java#L24)

Configures symmetric collision permissions between sixteen user-defined layers.
Every pair, including a layer with itself, starts enabled. Changing one pair
updates both directions so collision eligibility does not depend on body order.

`PhysicsWorld3D` reads this matrix when constructing its native filters.
Later edits affect this configuration object, not worlds already constructed
from it. The world also excludes static/static pairs independently of these
permissions; enabling a layer pair does not override that motion-type filter.

```java
CollisionLayers3D layers = new CollisionLayers3D();
layers.setCollision(1, 2, false); // Disable both 1-to-2 and 2-to-1 collisions.
PhysicsWorld3D world = new PhysicsWorld3D(PhysicsWorld3D.Settings.defaults(), layers);
```

This mutable configuration has no synchronization. Finish configuring it
before constructing a world that reads it.

<details>
<summary>CollisionLayers3D operation reference (4 declarations)</summary>

#### COUNT

```java
public static final  int COUNT
```

Number of user collision layers. Valid indices range from zero through
fifteen; native static/moving subdivisions are managed by the world.

#### Constructor

```java
public CollisionLayers3D()
```

Creates a matrix with all user-layer pairs enabled, including diagonal
entries. Configure exceptions with `setCollision(int, int, boolean)`
before passing the matrix to a world constructor.

#### setCollision

```java
public CollisionLayers3D setCollision(int a, int b, boolean collides)
```

Enables or disables a pair in both directions. Both indices are checked
before mutation; assigning a diagonal entry is supported. Repeating the
same setting is harmless and does not update existing native worlds.

- **`a`** — the first user-layer index
- **`b`** — the second user-layer index
- **`collides`** — whether the layer pair is permitted to collide

**Returns:** this configuration for chaining

**Throws `IllegalArgumentException`:** if either layer is outside zero through fifteen

#### collides

```java
public boolean collides(int a, int b)
```

Reads the configured permission for a validated pair. This reports the
user-layer matrix only, without checking body motion types or native filters.

- **`a`** — the first user-layer index
- **`b`** — the second user-layer index

**Returns:** whether this matrix enables the pair

**Throws `IllegalArgumentException`:** if either layer is outside zero through fifteen

</details>

<a id="type-collisionshape3d"></a>

### CollisionShape3D

[Source](../../src/main/java/valthorne/math/physics/CollisionShape3D.java#L39)

Reusable collision geometry definition for bodies created by `PhysicsWorld3D`.
Primitive dimensions use meters and full extents, except for explicitly named
radii. Boxes use X width, Y depth and Z height; capsules and cylinders have
their longitudinal axis along Z. Model-based geometry retains model-local
coordinates, so body settings supply its placement in the world.

##### Creating a Body

```java
CollisionShape3D shape = CollisionShape3D.box(1f, 1f, 2f);
BodySettings3D settings = new BodySettings3D(shape, MotionType3D.DYNAMIC)
        .setPosition(0, 0, 5);
RigidBody3D body = world.createBody(settings);
```

A definition can be reused for multiple bodies and worlds. Each native
creation invokes its factory again; this object does not hold a shared native
shape instance. Model and vector inputs are captured when the definition is
made, so subsequent source edits do not update collision geometry. Convex
shapes support moving bodies; triangle meshes require static motion.

Java-side validation checks dimensions and input coordinates. Hull and mesh
construction also performs native validation when a body requests its shape;
supplying enough vertices does not by itself establish valid hull geometry.

<details>
<summary>CollisionShape3D operation reference (8 declarations)</summary>

#### box

```java
public static CollisionShape3D box(float width, float depth, float height)
```

Defines a box centered at the body-local origin. Native half extents are
derived from the supplied full dimensions; the convex radius is capped at
0.05 meters and one quarter of the smallest dimension.

- **`width`** — the full X extent in meters
- **`depth`** — the full Y extent in meters
- **`height`** — the full Z extent in meters

**Returns:** a reusable box definition supporting all motion categories

**Throws `IllegalArgumentException`:** if any dimension is non-finite or not positive

#### sphere

```java
public static CollisionShape3D sphere(float radius)
```

Defines a sphere centered at the body-local origin. Radius is retained
directly and is not interpreted as a diameter or a model scale factor.

- **`radius`** — the positive sphere radius in meters

**Returns:** a reusable sphere definition supporting all motion categories

**Throws `IllegalArgumentException`:** if radius is non-finite or not positive

#### capsule

```java
public static CollisionShape3D capsule(float radius, float height)
```

Defines a centered capsule aligned along Z. Height includes both end
hemispheres; the cylindrical section has length `height - 2 * radius`.
A height exactly equal to the diameter produces a sphere instead.

- **`radius`** — the radius of the cylinder and hemispheres in meters
- **`height`** — the total tip-to-tip extent in meters

**Returns:** a reusable capsule, or sphere when no cylindrical section remains

**Throws `IllegalArgumentException`:** if either input is non-finite or not positive,
or height is less than twice radius

#### cylinder

```java
public static CollisionShape3D cylinder(float radius, float height)
```

Defines a centered cylinder whose flat ends are perpendicular to Z.
Native construction uses half the supplied height and a convex radius
capped at 0.05 meters and half the smaller of radius and half-height.

- **`radius`** — the cylinder radius in meters
- **`height`** — the full distance between the end planes in meters

**Returns:** a reusable cylinder definition supporting all motion categories

**Throws `IllegalArgumentException`:** if either input is non-finite or not positive

#### convexHull

```java
public static CollisionShape3D convexHull(Vector3f... vertices)
```

Captures points for a convex hull with zero additional convex radius.
Coordinates are validated and copied immediately; later changes to the
input array or vectors have no effect. At least four entries are required,
but duplicates or degenerate arrangements can still fail native creation.

- **`vertices`** — the body-local hull points in meters

**Returns:** a reusable convex definition suitable for moving bodies

**Throws `NullPointerException`:** if the array or an element is null

**Throws `IllegalArgumentException`:** if fewer than four points are supplied or
a coordinate is non-finite

#### convexHull

```java
public static CollisionShape3D convexHull(Model3D model)
```

Defines a convex hull from all model triangle corners in model-local
coordinates. Shared corners are included repeatedly; no deduplication,
instance transform or material information is applied. Concave detail is
replaced by the resulting convex envelope, making this useful for moving
bodies that cannot use a static triangle mesh.

- **`model`** — the model whose current triangle positions are captured

**Returns:** a reusable convex-hull definition

**Throws `NullPointerException`:** if model or a triangle corner is null

**Throws `IllegalArgumentException`:** if the model is empty, supplies fewer than
four corners, or contains non-finite coordinates

#### mesh

```java
public static CollisionShape3D mesh(Model3D model)
```

Captures model triangles for static level collision, preserving corner
order and positions relative to the model origin. Only geometry is copied;
render materials and instance transforms are not part of the shape.
Each native request receives a new direct buffer containing this snapshot.

The returned definition requires static body motion. Use
`convexHull(Model3D)` when a convex approximation must move.

- **`model`** — the nonempty model whose triangle coordinates are captured

**Returns:** a reusable static-only triangle-mesh definition

**Throws `NullPointerException`:** if model or a triangle corner is null

**Throws `IllegalArgumentException`:** if the model has no triangles or any
coordinate is non-finite

#### isStaticOnly

```java
public boolean isStaticOnly()
```

Reports whether body settings must use `MotionType3D#STATIC`.
This reflects the geometry category, independent of any particular body.

**Returns:** true for triangle meshes; false for primitive and convex-hull shapes

</details>

<a id="type-contactevent3d"></a>

### ContactEvent3D

[Source](../../src/main/java/valthorne/math/physics/ContactEvent3D.java#L30)

Captures one native subshape-contact notification for deferred listener delivery.
The world copies native contact data during simulation and invokes application
listeners afterward on its owning thread. A body pair can have several
simultaneous subshape contacts, so notifications are not unique per body pair.

The normal is defensively copied, but body references remain live handles.
A body can already be destroyed when a notification is delivered; check its
state before invoking operations that require a live native body. Retaining
an event does not extend the body's native lifetime.

World-generated removed notifications contain a zero normal and zero
penetration because their callback supplies identifiers rather than a manifold.
Direct construction does not enforce those conventions or validate identifiers,
type, penetration or body references.

- **`type`** — the lifecycle phase reported for this subshape contact
- **`bodyA`** — the first body reference associated with the native notification
- **`bodyB`** — the second body reference associated with the native notification
- **`subShapeA`** — the native subshape identifier within the first body
- **`subShapeB`** — the native subshape identifier within the second body
- **`normal`** — the native manifold's world-space normal, or zero for a removed contact
- **`penetration`** — the reported penetration depth in world units, or zero on removal

<details>
<summary>ContactEvent3D operation reference (2 declarations)</summary>

#### Constructor

```java
public ContactEvent3D
```

Copies the normal and retains all other components unchanged. This is a
data snapshot operation and does not query native body state.

- **`type`** — the contact phase to retain
- **`bodyA`** — the first borrowed body reference
- **`bodyB`** — the second borrowed body reference
- **`subShapeA`** — the first native subshape identifier
- **`subShapeB`** — the second native subshape identifier
- **`normal`** — the nonnull normal vector to copy
- **`penetration`** — the reported penetration depth

**Throws `NullPointerException`:** if normal is null

#### normal

```java
    public Vector3f normal()
```

Returns a defensive copy of the recorded normal, preserving its magnitude.
The zero vector for a removed contact supplies no surface direction.

**Returns:** a newly allocated copy of the snapshot's world-space normal

</details>

<a id="type-contactevent3d-type"></a>

### ContactEvent3D.Type

[Source](../../src/main/java/valthorne/math/physics/ContactEvent3D.java#L70)

Identifies the native contact callback represented by a snapshot.
Phases apply to a subshape pair rather than all contacts between two bodies.
A persisted notification should not be interpreted as a new collision.

<details>
<summary>ContactEvent3D.Type operation reference (3 declarations)</summary>

#### ADDED

```java
public static final  Type ADDED
```

Reports that a subshape contact was added, with current manifold data.

#### PERSISTED

```java
public static final  Type PERSISTED
```

Reports an existing subshape contact observed again by the simulation.

#### REMOVED

```java
public static final  Type REMOVED
```

Reports removal of a subshape contact without current manifold data.

</details>

<a id="type-distancejoint3d"></a>

### DistanceJoint3D

[Source](../../src/main/java/valthorne/math/physics/DistanceJoint3D.java#L22)

Represents a native distance constraint registered with a `PhysicsWorld3D`.
The world creates the constraint from two distinct bodies, world-space anchors,
and an allowed distance interval. At least one body must be dynamic. This
handle exposes lifecycle management; it does not expose runtime anchor or
distance-limit editing.

The owning world removes the constraint when either body is destroyed or
the world closes. Calling `close()` releases only the joint, leaving
both bodies alive. An already-destroyed joint can be closed again harmlessly.

Close a live joint on the world's creating thread, outside a native update.
The handle and its destruction flag are not synchronized. Retaining this Java
object does not keep a removed native constraint alive.

<details>
<summary>DistanceJoint3D operation reference (2 declarations)</summary>

#### isDestroyed

```java
public boolean isDestroyed()
```

Returns the destruction flag recorded by the world without querying native
state or validating the current thread. Read it under the world's normal
thread-ownership rules because the flag is not volatile.

**Returns:** whether this joint has been removed and its native constraint released

#### close

```java
    public void close()
```

Requests removal and release through the owning world unless destruction
has already completed. Both endpoint bodies remain owned by the world.
An already-destroyed handle returns without accessing world state.

**Throws `IllegalStateException`:** if a live joint is closed from the wrong
thread, after world closure, or during a native update

</details>

<a id="type-joltruntime"></a>

### JoltRuntime — internal support type

[Source](../../src/main/java/valthorne/math/physics/JoltRuntime.java#L31)

Initializes the shared Jolt native runtime used by Valthorne physics worlds.
Resolves a bundled platform library, extracts it to a temporary directory,
loads it, and registers the default allocator, tracing callback, factory and
physics types. Individual worlds own their simulation resources independently;
closing a world does not shut down this shared runtime.

Initialization is synchronized on this class and skipped after successful
completion. The success flag is set only after all registration steps finish.
Failure does not roll back native registration already performed, so a later
call is a retry rather than a guaranteed clean start.

Platform detection recognizes Windows, macOS and Linux, with x86-64 and
AArch64 aliases and Linux-only ARMHF support. A matching native resource must
still be present in the runtime dependencies. Extracted files are scheduled
for deletion at JVM exit, not removed when an individual world closes.

<a id="type-motiontype3d"></a>

### MotionType3D

[Source](../../src/main/java/valthorne/math/physics/MotionType3D.java#L15)

Selects the motion category assigned when a rigid body is created.
`BodySettings3D` retains this choice and `PhysicsWorld3D` maps it
to the corresponding native motion type. It also controls which velocity,
force and kinematic operations `RigidBody3D` permits.

Triangle-mesh collision shapes require static bodies in this integration.
Static/static collision pairs are excluded by the world's native filters;
kinematic and dynamic bodies occupy the moving side of those filters.

<details>
<summary>MotionType3D operation reference (3 declarations)</summary>

#### STATIC

```java
public static final  MotionType3D STATIC
```

Represents stationary collision geometry. The wrapper rejects velocity,
force and impulse operations, but permits explicit transform changes.

#### KINEMATIC

```java
public static final  MotionType3D KINEMATIC
```

Represents application-driven motion. Velocity setters and kinematic target
movement are supported; dynamic-only force and impulse methods are rejected.

#### DYNAMIC

```java
public static final  MotionType3D DYNAMIC
```

Represents a simulated rigid body eligible for forces, impulses, torque,
velocity changes and sleeping. Motion is advanced by the world's fixed steps.

</details>

<a id="type-physicsmath3d"></a>

### PhysicsMath3D — internal support type

[Source](../../src/main/java/valthorne/math/physics/PhysicsMath3D.java#L26)

Internal numeric validation and value conversion for the Jolt physics boundary.
Scalar helpers reject non-finite values and optionally require positive or
nonnegative inputs. Vector validation returns the supplied engine vector;
conversion helpers instead construct separate engine or Jolt value objects.

Conversions preserve component order and do not change axes or units.
Engine positions use floats, so reading a native real-valued position narrows
its components to float precision. Quaternion conversion in either direction
validates and normalizes components before crossing the physics boundary.

This utility has no shared mutable state. It does not manage body lifetime,
synchronize access to borrowed native objects, or perform world-thread checks;
callers must honor those requirements before reading native values.

<a id="type-physicsrayhit3d"></a>

### PhysicsRayHit3D

[Source](../../src/main/java/valthorne/math/physics/PhysicsRayHit3D.java#L22)

Captures the closest collision-shape hit returned by a physical ray query.
`PhysicsWorld3D#raycast(org.joml.primitives.Rayf, float)` queries native
collision geometry independently of rendered visibility, textures or mesh picking.
Its distance is measured along the normalized query ray in world units.

Position and normal are copied on construction and on access. The body is
a borrowed live reference whose lifecycle remains owned by its physics world;
retaining a hit does not prevent body destruction. Direct record construction
does not validate distance, body state or vector finiteness.

- **`body`** — the collision body identified by the query
- **`distance`** — distance from the ray origin in world units
- **`position`** — the world-space hit position to snapshot
- **`normal`** — the world-space collision surface normal to snapshot

<details>
<summary>PhysicsRayHit3D operation reference (3 declarations)</summary>

#### Constructor

```java
public PhysicsRayHit3D
```

Copies the hit point and normal while retaining the body reference and
distance unchanged. Later changes to the supplied vectors cannot alter the hit.

- **`body`** — the body reference to retain without validation
- **`distance`** — the distance to retain without clamping
- **`position`** — the nonnull world-space hit point
- **`normal`** — the nonnull world-space surface normal

**Throws `NullPointerException`:** if position or normal is null

#### position

```java
    public Vector3f position()
```

Returns an independent copy of the recorded world-space point. Mutating
the result does not affect this snapshot or move the associated body.

**Returns:** a newly allocated hit-position vector

#### normal

```java
    public Vector3f normal()
```

Returns an independent copy of the recorded surface normal. No additional
normalization is performed, including for directly constructed snapshots.

**Returns:** a newly allocated world-space normal vector

</details>

<a id="type-physicsworld3d"></a>

### PhysicsWorld3D

[Source](../../src/main/java/valthorne/math/physics/PhysicsWorld3D.java#L43)

Owns a Z-up Jolt simulation, its rigid bodies, distance joints, collision filters,
and native execution resources. Gravity initially points along negative Z at
9.81 distance units per second squared. Keep distances, masses, forces, and
velocities in a consistent unit system.

The public simulation API is confined to the thread that creates the world.
Optional native workers perform internal simulation only; contact data is copied
into a queue and application listeners run later on the owner thread. Before-step,
after-step, and contact callbacks may modify bodies and listeners, but may not
recursively step or close the world. Callback exceptions propagate to the caller.

`update(float)` accumulates elapsed time, advances a bounded number of
fixed steps, and discards excess whole steps after stalls. `step()` advances
one fixed step independently of that accumulator. Bound models normally receive
the latest pose; call `syncModels(boolean)` with true after updating to
interpolate visuals with one step of display latency. Models remain caller-owned.
Close the world on its owner thread to invalidate handles and release native
resources.

```java
try (PhysicsWorld3D world = new PhysicsWorld3D()) {
    RigidBody3D body = world.createBody(bodySettings);
    body.bind(model);
    world.update(frameSeconds);
    world.syncModels(true);
}
```

<details>
<summary>PhysicsWorld3D operation reference (29 declarations)</summary>

#### Constructor

```java
public PhysicsWorld3D()
```

Creates a world with default capacities, a 60 Hz fixed step, up to eight
catch-up steps per update, and single-threaded native execution. Uses the
default collision-layer policy and binds access to the creating thread.

#### Constructor

```java
public PhysicsWorld3D(Settings settings)
```

Creates a world with supplied immutable simulation settings and the default
collision-layer policy.

- **`settings`** — nonnull timestep, capacity, and worker configuration

**Throws `NullPointerException`:** if settings is null

#### Constructor

```java
public PhysicsWorld3D(Settings settings, CollisionLayers3D layers)
```

Initializes Jolt, builds native filters from the supplied collision policy,
allocates the selected job system, and installs contact capture callbacks.
Static/static pairs are excluded regardless of policy. Layer rules are copied
into native tables; later policy edits do not reconfigure this world.
Resources registered before a construction failure are released.

- **`settings`** — nonnull immutable simulation configuration
- **`layers`** — nonnull application collision-layer policy

**Throws `NullPointerException`:** if either argument is null

#### getFixedTimeStep

```java
public float getFixedTimeStep()
```

Returns the immutable duration of one simulation step. This metadata remains
readable after closure.

**Returns:** fixed timestep in seconds

#### isClosed

```java
public boolean isClosed()
```

Reports whether successful cleanup has marked this world closed. Available
after close; this flag does not make other access thread-safe.

**Returns:** whether the world is closed

#### getStepCount

```java
public long getStepCount()
```

Returns the number of native update calls that returned. A returned capacity
error still increments the count before it is reported to the caller.

**Returns:** cumulative executed step count

#### getDroppedTime

```java
public double getDroppedTime()
```

Returns accumulated whole-step time discarded when update reaches its substep
limit. Fractional time is retained for later frames.

**Returns:** discarded elapsed time in seconds

#### getInterpolationAlpha

```java
public float getInterpolationAlpha()
```

Returns accumulated time divided by the fixed timestep. After a successful
update this is normally in [0,1); during callbacks or after an interrupted
update it may be larger. This getter does not clamp the value.

**Returns:** fraction used for optional visual interpolation

#### getBodies

```java
public List<RigidBody3D> getBodies()
```

Copies current handles into an unmodifiable list in body insertion order.
The list membership is a snapshot, but its handles remain live and world-owned.

**Returns:** snapshot of currently registered bodies

**Throws `IllegalStateException`:** if world access is prohibited

#### getBodyCount

```java
public int getBodyCount()
```

Counts currently registered bodies, excluding destroyed handles retained
temporarily for contact resolution.

**Returns:** live body count

**Throws `IllegalStateException`:** if world access is prohibited

#### getGravity

```java
public Vector3f getGravity()
```

Copies native gravity into independent engine storage.

**Returns:** world acceleration vector in distance units per second squared

**Throws `IllegalStateException`:** if world access is prohibited

#### setGravity

```java
public PhysicsWorld3D setGravity(Vector3f gravity)
```

Copies a finite world acceleration into the native simulation without changing
individual bodies' gravity factors.

- **`gravity`** — nonnull finite world-space gravity vector

**Returns:** this world

**Throws `NullPointerException`:** if gravity is null

**Throws `IllegalArgumentException`:** if a component is nonfinite

**Throws `IllegalStateException`:** if world access is prohibited

#### addContactListener

```java
public void addContactListener(Consumer<ContactEvent3D> listener)
```

Adds an owner-thread listener for queued contact events after successful
simulation. Duplicate registrations are allowed. The entire event dispatch
uses a listener snapshot, so membership changes affect a later dispatch.

- **`listener`** — nonnull contact consumer

**Throws `NullPointerException`:** if listener is null

**Throws `IllegalStateException`:** if world access is prohibited

#### removeContactListener

```java
public void removeContactListener(Consumer<ContactEvent3D> listener)
```

Removes the first matching contact-listener registration. Missing or null
listeners have no effect; an already captured dispatch snapshot is unchanged.

- **`listener`** — registration to remove

**Throws `IllegalStateException`:** if world access is prohibited

#### addBeforeStepListener

```java
public void addBeforeStepListener(Consumer<PhysicsWorld3D> listener)
```

Registers a callback before every fixed native step, including each catch-up
substep. Use it for sustained forces and kinematic targets. Membership is
snapshotted for each dispatch; callbacks may modify bodies and listeners.
A callback failure prevents that invocation's native step from starting.

- **`listener`** — nonnull owner-thread callback

**Throws `NullPointerException`:** if listener is null

**Throws `IllegalStateException`:** if world access is prohibited

#### removeBeforeStepListener

```java
public void removeBeforeStepListener(Consumer<PhysicsWorld3D> listener)
```

Removes the first matching before-step registration and invalidates the cached
snapshot for a later dispatch. An active dispatch keeps its existing snapshot.

- **`listener`** — callback to remove; absent or null values have no effect

**Throws `IllegalStateException`:** if world access is prohibited

#### addAfterStepListener

```java
public void addAfterStepListener(Consumer<PhysicsWorld3D> listener)
```

Registers an owner-thread callback after a successful native step, pose capture,
and immediate model synchronization, but before contact dispatch. Bodies and
listeners may be changed; recursive stepping and world closure are prohibited.
Membership changes affect subsequent dispatch snapshots.

- **`listener`** — nonnull callback

**Throws `NullPointerException`:** if listener is null

**Throws `IllegalStateException`:** if world access is prohibited

#### removeAfterStepListener

```java
public void removeAfterStepListener(Consumer<PhysicsWorld3D> listener)
```

Removes the first matching after-step registration and invalidates the next
dispatch snapshot. Does not alter callbacks already captured for dispatch.

- **`listener`** — callback to remove; absent or null values have no effect

**Throws `IllegalStateException`:** if world access is prohibited

#### createBody

```java
public RigidBody3D createBody(BodySettings3D settings)
```

Creates and adds a native body using the supplied shape, material, motion, and
collision settings, then returns its world-owned handle. Static bodies start
inactive; dynamic and kinematic bodies start active. Temporary shape and
creation wrappers are closed after the native body acquires its shape reference.
If handle creation fails after allocation, the native body is removed and destroyed.

- **`settings`** — nonnull body configuration

**Returns:** newly registered body handle

**Throws `NullPointerException`:** if settings is null

**Throws `IllegalStateException`:** if world access is invalid, capacity is reached, or native allocation fails

#### destroyBody

```java
public void destroyBody(RigidBody3D body)
```

Destroys attached distance joints, removes the body from simulation, and
invalidates its handle. Temporarily retains the handle to resolve queued removal
events. Does nothing for an already destroyed body from this world, provided
world access itself remains valid.

- **`body`** — handle owned by this world

**Throws `IllegalArgumentException`:** if body is null or belongs to another world

**Throws `IllegalStateException`:** if world access is prohibited

#### update

```java
public int update(float elapsedSeconds)
```

Accumulates finite nonnegative elapsed time and performs at most maxSubSteps
fixed updates. Drops remaining whole steps after reaching that limit, records
their duration, and preserves the fractional remainder. Synchronizes models
to current poses before returning.

Listener and native failures propagate. Time is consumed only after native
integration returns, before after-step and contact callbacks; a later callback
failure does not roll back the simulated step. The recursion guard is released
on every exit.

- **`elapsedSeconds`** — frame duration in seconds

**Returns:** number of fixed steps performed by this call

**Throws `IllegalArgumentException`:** if elapsedSeconds is negative or nonfinite

**Throws `IllegalStateException`:** if access is invalid, stepping is recursive, or Jolt reports a capacity error

#### step

```java
public void step()
```

Advances exactly one fixed simulation step without consuming or adding update's
accumulated time. Runs the same callbacks and pose synchronization as a substep.
Useful when the caller controls the fixed-step clock directly.

**Throws `IllegalStateException`:** if access is invalid, stepping is recursive, or Jolt reports a capacity error

#### syncModels

```java
public void syncModels(boolean interpolate)
```

Copies physical poses into bound models, optionally interpolating between the
previous and current captured poses using the accumulator fraction. Interpolation
adds one fixed step of visual latency and does not change physical state.
Call after update to override its default current-pose synchronization.

- **`interpolate`** — true for interpolated visuals, false for the latest physical pose

**Throws `IllegalStateException`:** if world access is prohibited

#### optimizeBroadPhase

```java
public void optimizeBroadPhase()
```

Requests native broadphase optimization, useful after bulk body placement.
Must run on the owner thread outside native integration.

**Throws `IllegalStateException`:** if world access is prohibited

#### raycast

```java
public PhysicsRayHit3D raycast(Rayf ray, float maxDistance)
```

Finds the closest native shape hit along a finite world ray, considering all
query layers and bodies. Direction magnitude is normalized internally, so
maxDistance determines the segment length.

- **`ray`** — nonnull ray with finite origin and finite nonzero direction
- **`maxDistance`** — finite positive segment length in world units

**Returns:** closest hit, or null if no hit can be returned

**Throws `NullPointerException`:** if ray is null

**Throws `IllegalArgumentException`:** if ray values or distance are invalid

#### raycast

```java
public PhysicsRayHit3D raycast(Rayf ray, float maxDistance, RigidBody3D ignoredBody)
```

Queries the closest shape hit while optionally excluding one live body from
this world. Uses default broadphase and object filters, normalizes the ray
direction, and obtains the surface normal under a native read lock.

- **`ray`** — nonnull ray with finite origin and finite nonzero direction
- **`maxDistance`** — finite positive world-space ray length
- **`ignoredBody`** — live body to exclude, or null for no exclusion

**Returns:** closest hit with world point, normal, and distance; null for no hit or a failed read lock

**Throws `NullPointerException`:** if ray is null

**Throws `IllegalArgumentException`:** if inputs are invalid or the excluded body belongs elsewhere

**Throws `IllegalStateException`:** if world access is invalid or the excluded body is destroyed

#### createDistanceJoint

```java
public DistanceJoint3D createDistanceJoint(RigidBody3D a, RigidBody3D b, Vector3f anchorA, Vector3f anchorB, float minDistance, float maxDistance)
```

Creates a world-space distance constraint between two distinct live bodies,
at least one of which is dynamic. Equal minimum and maximum distances form a
fixed-length constraint; a range allows separation within those limits.
The world owns the joint and destroys it when either body is destroyed.

- **`a`** — first body owned by this world
- **`b`** — second body owned by this world
- **`anchorA`** — finite world-space anchor on the first body
- **`anchorB`** — finite world-space anchor on the second body
- **`minDistance`** — finite nonnegative minimum separation
- **`maxDistance`** — finite maximum separation at least minDistance

**Returns:** newly registered joint

**Throws `NullPointerException`:** if an anchor is null

**Throws `IllegalArgumentException`:** if ownership, motion, anchors, or distance limits are invalid

**Throws `IllegalStateException`:** if world access is invalid or either body is destroyed

#### dispose

```java
public void dispose()
```

Releases this world through `close()`, including all bodies, joints,
listeners, and registered native resources.

**Throws `IllegalStateException`:** if a live world is disposed from an invalid access context or callback

#### close

```java
    public void close()
```

Detaches native contact capture, destroys joints and bodies, clears callbacks
and queued events, then releases registered native resources. Models remain
caller-owned. Successful closure makes subsequent calls harmless.

**Throws `IllegalStateException`:** if a live world is closed off its owner thread, during native integration, or from a step callback

</details>

<a id="type-physicsworld3d-settings"></a>

### PhysicsWorld3D.Settings

[Source](../../src/main/java/valthorne/math/physics/PhysicsWorld3D.java#L801)

Immutable fixed-step timing, native capacity, and worker configuration.
Capacities bound simulation storage; reaching them can cause body allocation
or update failures. A worker count of zero chooses the single-threaded job
system while preserving the same owner-thread public API.

- **`fixedTimeStep`** — finite positive step duration in seconds
- **`maxSubSteps`** — maximum fixed steps performed by one update
- **`maxBodies`** — maximum concurrently registered bodies
- **`maxBodyPairs`** — native body-pair capacity
- **`maxContacts`** — native contact-constraint capacity
- **`workerThreads`** — native worker count, with zero selecting single-threaded execution

<details>
<summary>PhysicsWorld3D.Settings operation reference (2 declarations)</summary>

#### Constructor

```java
public Settings
```

Validates immutable simulation settings at construction.

**Throws `IllegalArgumentException`:** if the timestep is nonfinite or nonpositive, a capacity is below one, or workers are negative

#### defaults

```java
public static Settings defaults()
```

Creates the default 60 Hz configuration: eight substeps per update, 4096
bodies, 65536 body pairs, 20480 contacts, and no native worker threads.

**Returns:** a new default settings value

</details>

<a id="type-physicsworld3d-rawcontact"></a>

### PhysicsWorld3D.RawContact — internal support type

[Source](../../src/main/java/valthorne/math/physics/PhysicsWorld3D.java#L837)

Copied native contact data queued until owner-thread dispatch. Holds IDs and
engine values rather than borrowed native addresses. Removed contacts carry a
zero normal and zero penetration because no manifold is available.

- **`type`** — contact transition
- **`a`** — first body ID
- **`b`** — second body ID
- **`subA`** — first subshape ID
- **`subB`** — second subshape ID
- **`normal`** — copied world-space normal
- **`penetration`** — copied penetration depth in world units

<a id="type-rigidbody3d"></a>

### RigidBody3D

[Source](../../src/main/java/valthorne/math/physics/RigidBody3D.java#L28)

A handle to a native rigid body owned by one `PhysicsWorld3D`. The world
controls creation, stepping, and destruction; closing this handle also destroys
joints attached to it. Physics operations must run on the world's creating
thread while the world and body are alive, outside the native update.

Pose getters copy the last captured simulation pose, whereas velocity getters
read native state. A borrowed model may be bound for automatic visual pose
synchronization; interpolation affects the model only, never the physical body.
World positions, forces, and velocities use the world's consistent unit system,
with Z pointing up. Returned position and rotation values are independent copies.
Metadata getters remain readable after destruction but are not synchronization
primitives.

<details>
<summary>RigidBody3D operation reference (31 declarations)</summary>

#### getId

```java
public int getId()
```

Returns the native ID assigned at creation. It remains available after
destruction and must not then be used to access native body state.

**Returns:** body ID

#### getMotionType

```java
public MotionType3D getMotionType()
```

Returns the motion category copied from creation settings. This metadata
getter does not query native state.

**Returns:** static, kinematic, or dynamic motion type

#### getLayer

```java
public int getLayer()
```

Returns the application collision-layer index, before the world's internal
static/moving layer encoding.

**Returns:** creation-time collision layer

#### isSensor

```java
public boolean isSensor()
```

Reports whether this body was created as a contact sensor.

**Returns:** creation-time sensor flag

#### isDestroyed

```java
public boolean isDestroyed()
```

Reports whether the world has invalidated this handle. Reading the flag does
not establish thread safety for other body operations.

**Returns:** true after body destruction

#### getUserData

```java
public Object getUserData()
```

Returns the application attachment without copying it or checking body
lifetime. The attachment remains stored after destruction.

**Returns:** borrowed user value, possibly null

#### setUserData

```java
public RigidBody3D setUserData(Object data)
```

Stores an application attachment without taking ownership. Replacing it does
not release the previous value.

- **`data`** — arbitrary value, or null to clear it

**Returns:** this body

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### getPosition

```java
public Vector3f getPosition()
```

Allocates and returns a copy of the last captured world position. Model
interpolation and changes to the returned vector do not change the physical pose.

**Returns:** independent position vector

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### getPosition

```java
public Vector3f getPosition(Vector3f destination)
```

Copies the last captured world position into caller storage without a native
pose query.

- **`destination`** — nonnull output vector

**Returns:** destination

**Throws `NullPointerException`:** if destination is null

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### getRotation

```java
public Quaternionf getRotation()
```

Allocates and returns the last captured normalized world orientation.
Modifying the result does not rotate the body.

**Returns:** independent orientation quaternion

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### getRotation

```java
public Quaternionf getRotation(Quaternionf destination)
```

Copies the last captured normalized world orientation into caller storage.

- **`destination`** — nonnull output quaternion

**Returns:** destination

**Throws `NullPointerException`:** if destination is null

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### getLinearVelocity

```java
public Vector3f getLinearVelocity()
```

Reads current native linear velocity into a newly allocated vector.

**Returns:** independent world-space velocity in distance units per second

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### getLinearVelocity

```java
public Vector3f getLinearVelocity(Vector3f destination)
```

Reads native linear velocity using reusable native scratch storage and writes
the result into the caller's vector.

- **`destination`** — nonnull output vector

**Returns:** destination, in world distance units per second

**Throws `NullPointerException`:** if destination is null

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### setLinearVelocity

```java
public RigidBody3D setLinearVelocity(Vector3f velocity)
```

Copies a world-space linear velocity into a nonstatic body and activates it.

- **`velocity`** — finite velocity in distance units per second

**Returns:** this body

**Throws `NullPointerException`:** if velocity is null

**Throws `IllegalArgumentException`:** if a component is nonfinite

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or static

#### getAngularVelocity

```java
public Vector3f getAngularVelocity()
```

Reads current native angular velocity into a new vector. Its direction is the
world rotation axis and its magnitude is angular speed.

**Returns:** independent angular velocity in radians per second

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### getAngularVelocity

```java
public Vector3f getAngularVelocity(Vector3f destination)
```

Reads native angular velocity into caller storage using reusable native scratch.

- **`destination`** — nonnull output vector

**Returns:** destination, containing world-axis angular velocity in radians per second

**Throws `NullPointerException`:** if destination is null

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### setAngularVelocity

```java
public RigidBody3D setAngularVelocity(Vector3f velocity)
```

Copies angular velocity into a nonstatic body and activates it.

- **`velocity`** — finite world-axis angular velocity in radians per second

**Returns:** this body

**Throws `NullPointerException`:** if velocity is null

**Throws `IllegalArgumentException`:** if a component is nonfinite

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or static

#### isActive

```java
public boolean isActive()
```

Queries whether the native body currently participates as an active body.
This reflects simulation activation, independently of any bound model visibility.

**Returns:** native activation state

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### activate

```java
public RigidBody3D activate()
```

Requests activation for dynamic or kinematic motion. Static bodies are left
unchanged after validating access.

**Returns:** this body

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### sleep

```java
public RigidBody3D sleep()
```

Deactivates a dynamic body immediately through the native body interface.
Later interactions or explicit activation may wake it again.

**Returns:** this body

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or nondynamic

#### setLinearVelocity

```java
public RigidBody3D setLinearVelocity(float x, float y, float z)
```

Sets a nonstatic body's world linear velocity and activates it. Components
are copied directly after finite-value validation.

- **`x`** — X velocity in distance units per second
- **`y`** — Y velocity in distance units per second
- **`z`** — Z velocity in distance units per second

**Returns:** this body

**Throws `IllegalArgumentException`:** if a component is nonfinite

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or static

#### addImpulse

```java
public RigidBody3D addImpulse(Vector3f impulse)
```

Applies an instantaneous linear impulse at the dynamic body's center of mass.
The resulting velocity change depends on body mass; this is not a force that
must be integrated over a timestep.

- **`impulse`** — finite world-space momentum change

**Returns:** this body

**Throws `NullPointerException`:** if impulse is null

**Throws `IllegalArgumentException`:** if a component is nonfinite

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or nondynamic

#### addImpulse

```java
public RigidBody3D addImpulse(Vector3f impulse, Vector3f worldPoint)
```

Applies a world-space impulse at a world point. An offset from the center of
mass can also change angular velocity through the body's inertia.

- **`impulse`** — finite world-space linear impulse
- **`worldPoint`** — finite world-space application point

**Returns:** this body

**Throws `NullPointerException`:** if either vector is null

**Throws `IllegalArgumentException`:** if a component is nonfinite

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or nondynamic

#### addAngularImpulse

```java
public RigidBody3D addAngularImpulse(Vector3f impulse)
```

Applies an instantaneous angular impulse to a dynamic body. Native inertia
determines the resulting change in angular velocity.

- **`impulse`** — finite world-space angular impulse

**Returns:** this body

**Throws `NullPointerException`:** if impulse is null

**Throws `IllegalArgumentException`:** if a component is nonfinite

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or nondynamic

#### addForce

```java
public RigidBody3D addForce(Vector3f force)
```

Accumulates a center-of-mass force for the next simulation step. For sustained
acceleration, apply it from a before-step listener so each fixed step receives
the force, including frames that perform multiple substeps.

- **`force`** — finite world-space force in mass times distance per second squared

**Returns:** this body

**Throws `NullPointerException`:** if force is null

**Throws `IllegalArgumentException`:** if a component is nonfinite

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or nondynamic

#### addTorque

```java
public RigidBody3D addTorque(Vector3f torque)
```

Accumulates torque for a dynamic body's next simulation step. Reapply it in
each before-step callback for sustained rotational acceleration.

- **`torque`** — finite world-space torque

**Returns:** this body

**Throws `NullPointerException`:** if torque is null

**Throws `IllegalArgumentException`:** if a component is nonfinite

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or nondynamic

#### setTransform

```java
public RigidBody3D setTransform(Vector3f position, Quaternionf rotation)
```

Teleports the native body and resets both captured poses to the new transform,
then immediately synchronizes a bound model. Nonstatic bodies are activated;
existing velocity is retained. The supplied quaternion is normalized during
conversion without modifying the caller's value.

- **`position`** — finite world position
- **`rotation`** — finite, nonzero world orientation

**Returns:** this body

**Throws `NullPointerException`:** if either argument is null

**Throws `IllegalArgumentException`:** if position or orientation is invalid

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### moveKinematic

```java
public RigidBody3D moveKinematic(Vector3f position, Quaternionf rotation)
```

Requests velocity-driven movement of a kinematic body toward a target over one
fixed timestep. Call once from a before-step listener. Captured pose and bound
model are updated when the simulation advances, rather than immediately.

- **`position`** — finite target world position
- **`rotation`** — finite, nonzero target world orientation

**Returns:** this body

**Throws `NullPointerException`:** if either argument is null

**Throws `IllegalArgumentException`:** if position or orientation is invalid

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed or not kinematic

#### bind

```java
public RigidBody3D bind(ModelInstance3D model)
```

Binds a borrowed model, clears its parent transform to identity, and immediately
copies the current physical pose. Preserves visual scale. Later world updates
or explicit model synchronization overwrite the model's position and rotation.
Replacing a binding does not dispose or restore the previous model.

- **`model`** — nonnull visual instance

**Returns:** this body

**Throws `NullPointerException`:** if model is null

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### unbind

```java
public void unbind()
```

Stops visual synchronization without modifying or disposing the previously
bound model. Its last synchronized transform remains in place.

**Throws `IllegalStateException`:** if world access is prohibited or this body is destroyed

#### close

```java
    public void close()
```

Asks the owning world to destroy this body and its attached joints. Repeated
calls after invalidation do nothing, including after the world is closed.
Does not dispose a formerly bound model.

**Throws `IllegalStateException`:** if a live body is closed from an invalid world access context

</details>

## Related guides

- [3D models, materials, scenes, and billboards](models.md)
- [Ticks and frame timing](timing.md)
- [3D particles and physics integration](particles-3d.md)
- [Existing physics3d guide](../physics3d.md)
