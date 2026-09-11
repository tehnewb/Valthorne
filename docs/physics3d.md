# 3D physics with Jolt

Local demo commands in this guide require separately available `src/examples/`
source and resources. These are ignored by Git and excluded from releases and CI;
see [optional local examples](examples.md).

## Pinned Jolt JNI compatibility

The engine pins Jolt JNI 6.0.0. Its [native broad-phase filter bridge](https://github.com/stephengold/jolt-jni/blob/6.0.0/src/main/native/glue/o/ObjectVsBroadPhaseLayerFilterTable.cpp#L42) reverses the object-layer and broad-phase-layer counts. `PhysicsWorld3D` compensates when constructing the table so all 16 user collision groups work correctly. Without that correction, non-default groups can miss contacts even though raycasts still see the geometry. Keep the complete layer-filter regression when upgrading the binding and revisit the argument order if upstream fixes it.

`BodySettings3D.setRotationLocked(true)` permits translation while locking native angular degrees of freedom, useful for upright first-person capsules. `PhysicsWorld3D.raycast(ray, distance, ignoredBody)` excludes an owned body through Jolt's native filter, allowing player ground and gun rays to start at the actual player position. The existing two-argument raycast remains unchanged.

`valthorne.math.physics` integrates actual native [Jolt Physics](https://github.com/jrouwe/JoltPhysics)
through [Jolt JNI 6.0.0](https://github.com/stephengold/jolt-jni). It uses Valthorne's
JOML vectors, quaternions and ray types with engine render models. A physics world requires no window
or OpenGL context, so the same simulation can run on a server or in tests.

## Run the playground

```shell
./gradlew runPhysics3DExample
./gradlew verifyPhysics3D
./gradlew runPhysics3DExample --args="--snapshot=build/physics3d-example.png"
```

On Windows, use `gradlew.bat`. Space drops a ball, clicking pushes the closest physical
object, left/right arrows orbit the camera, R resets the scene, and Escape exits.
The example generates its own textures. Snapshot mode runs 150 fixed frames in a
hidden window, saves the scene, and exits. The playground needs OpenGL 3.3;
`verifyPhysics3D` runs against native Jolt without graphics.

The playground also uses the [new 3D lighting system](lighting.md), shows live FPS and
frame time, and lets you toggle VSync with **V**.

## Create a world and bodies

Use meters, kilograms, and seconds. Coordinates are right-handed and **Z-up**;
the default gravity is `(0, 0, -9.81)`. Box dimensions are full extents, and capsule
and cylinder heights run along Z. Body positions describe the shape origin.

```java
import valthorne.physics3d.*;
import valthorne.math.*;
import valthorne.graphics.model.*;

PhysicsWorld3D physics = new PhysicsWorld3D();

RigidBody3D floor = physics.createBody(new BodySettings3D(
        CollisionShape3D.box(20, 20, 1), MotionType3D.STATIC)
        .setPosition(0, 0, -0.5f));

ModelInstance3D crateModel = new ModelInstance3D()
        .setModel(ModelBuilder3D.box(1, 1, 1));
RigidBody3D crate = physics.createBody(new BodySettings3D(
        CollisionShape3D.box(1, 1, 1), MotionType3D.DYNAMIC)
        .setPosition(0, 0, 4)
        .setMass(2)
        .setFriction(0.6f)
        .setRestitution(0.1f))
        .bind(crateModel);
scene.add(crateModel); // Your existing Scene3D.

// Application.update(delta):
physics.update(delta);

// Application.dispose():
physics.close();
```

`STATIC` bodies are level geometry, `DYNAMIC` bodies respond to forces and collisions,
and `KINEMATIC` bodies follow your commanded movement while pushing dynamic objects.
Creation settings are reusable; changing them later does not modify existing bodies.
You can set initial velocity, mass, friction, restitution, damping, gravity factor,
collision layer, sensor status, sleeping, and continuous collision detection.

For fast-moving bodies, enable `.setContinuousCollision(true)` to use Jolt's linear
cast motion quality. Choose collider thickness, speed, and timestep for your game;
continuous collision detection does not replace an appropriate simulation scale.

## Shapes and rendering

| Factory | Dimensions / purpose |
| --- | --- |
| `box(width, depth, height)` | Full X/Y/Z dimensions |
| `sphere(radius)` | Sphere radius |
| `capsule(radius, height)` | Total Z height, including both hemispheres; at least twice the radius |
| `cylinder(radius, height)` | Radius and full Z height |
| `convexHull(vertices...)` | Convex hull of local positions; at least four vertices |
| `convexHull(model)` | Convex envelope of model geometry |
| `mesh(model)` | Triangle collider for static level geometry only |

Shape descriptions contain only Java data and can be reused across bodies and worlds.
Invalid native hulls/meshes report a shape error at body creation. A moving concave
mesh is not supported by this wrapper; use suitable convex collision geometry.

Binding copies the body's world position and rotation into a borrowed model after
simulation. It clears the model's parent transform and preserves visual scale. Add
bound models directly to the scene as independent instances; a scene node's parent
transform would otherwise transform the physical pose again. Model scale and imported
asset transforms do **not** resize or reposition a collider. Supply collider dimensions
and local geometry that match the visual. `unbind()` stops synchronization.

`getPosition()` and `getRotation()` return copies. Use `setTransform(position, rotation)`
to teleport a body and reset interpolation; velocities are retained. For normal dynamic
movement, apply forces or impulses instead of overwriting the pose each frame.

## Fixed steps, forces, and kinematic movement

`update(delta)` accumulates elapsed seconds and defaults to 60 simulation steps per
second, with at most eight steps per call. Long stalls discard excess whole steps to
bound catch-up work. `getDroppedTime()` reports the discarded seconds, `getStepCount()`
reports completed steps, and `update` returns the number of steps it simulated.
`step()` advances exactly one fixed step without consuming the update accumulator.

```java
crate.addImpulse(new Vector3f(0, 0, 5));
crate.addImpulse(new Vector3f(3, 0, 0), worldHitPoint); // Also produces torque.
crate.addAngularImpulse(new Vector3f(0, 0, 1));

// Forces are cleared after each physics step, so apply sustained forces here:
physics.addBeforeStepListener(world -> crate.addForce(new Vector3f(1, 0, 0)));

RigidBody3D platform = physics.createBody(new BodySettings3D(
        CollisionShape3D.box(3, 2, 0.3f), MotionType3D.KINEMATIC));
physics.addBeforeStepListener(world -> {
    float time = (world.getStepCount() + 1) * world.getFixedTimeStep();
    platform.moveKinematic(new Vector3f(3, 0, 1 + (float)Math.sin(time)),
            new Quaternionf());
});
```

Each before-step listener runs once per actual substep, including catch-up steps.
Command kinematic targets there so their velocity matches the fixed timestep.
Dynamic bodies can also receive torque, explicit velocities, activation, and sleep.

Bound models normally show the latest completed pose. After `update`, optionally call
`syncModels(true)` to interpolate the previous/current poses using the remaining
fractional time. This smooths rendering at the cost of one physics step of display
latency. Use `syncModels(false)` to show the latest pose again.

## Collision layers, sensors, and events

```java
CollisionLayers3D layers = new CollisionLayers3D()
        .setCollision(1, 2, false);
PhysicsWorld3D filtered = new PhysicsWorld3D(PhysicsWorld3D.Settings.defaults(), layers);

RigidBody3D trigger = filtered.createBody(new BodySettings3D(
        CollisionShape3D.box(4, 4, 2), MotionType3D.STATIC)
        .setSensor(true).setLayer(1));
trigger.setUserData("checkpoint");

filtered.addContactListener(event -> {
    if (event.type() == ContactEvent3D.Type.ADDED
            && (event.bodyA() == trigger || event.bodyB() == trigger)) {
        // Handle entry into the trigger.
    }
});
```

There are 16 layers numbered 0–15. The symmetric matrix initially enables every pair;
static/static pairs are always excluded. Configure it before world creation: the world
copies the matrix. Sensors report contacts without collision response. Static sensors
observe moving bodies; they do not generate static/static contacts.

Events are `ADDED`, `PERSISTED`, or `REMOVED` snapshots of individual subshape contacts.
A body pair can have multiple contacts. Normal and penetration are copied while the
native contact is valid; removal events have a zero normal and penetration. Events can
reference bodies destroyed earlier, so check `isDestroyed()` before using a live-body
operation. Metadata such as IDs and user data remains readable.

Game contact listeners run on the world's creating thread **after** Jolt releases its
simulation locks and body/model poses are refreshed. They may create/destroy bodies
and change listeners safely. Recursive stepping and closing the world inside a step
callback are rejected. Listener exceptions propagate; completed physics time is not
replayed. If a before-step listener fails, unsimulated time remains pending.

## Physical raycasts and joints

```java
PhysicsRayHit3D hit = physics.raycast(viewport.screenToRay(mouseX, mouseY), 100);
if (hit != null && hit.body().getMotionType() == MotionType3D.DYNAMIC) {
    hit.body().addImpulse(new Vector3f(0, 0, 3), hit.position());
}

DistanceJoint3D tether = physics.createDistanceJoint(
        anchorBody, hangingBody, anchorWorldPoint, hangingWorldPoint, 2, 2);
// Equal minimum/maximum lengths make a fixed-length tether.
tether.close();
```

Raycasts return the nearest collider hit, including sensors, with its body, world
position, outward surface normal, and distance. They use collision shapes independently
of render visibility. A miss returns `null`; pass a nonzero ray and positive distance.
Skip the call if a minimized viewport returns a null ray. Use `Scene3D.pick` when you
want visible render-triangle selection instead.

Distance joints accept world-space anchors, require distinct bodies from the same
world, and require at least one dynamic body. Removing either body also destroys its
joints. The current public wrapper does not expose other constraint types, ragdolls,
vehicles, soft bodies, or character controllers.

## Capacity, threading, native resources, and packaging

```java
PhysicsWorld3D.Settings settings = new PhysicsWorld3D.Settings(
        1f / 120f, // Fixed timestep.
        8,         // Maximum catch-up substeps per update.
        4096,      // Maximum bodies.
        65536,     // Maximum body pairs.
        20480,     // Maximum contact constraints.
        2);        // Native worker threads; 0 uses the single-threaded job system.
```

Keep world/body/joint operations on the thread that created the world. Native workers
are internal; game listeners are still delivered on the owner thread. Exceeding body
capacity throws before creation; native simulation capacity errors are reported as
exceptions. `optimizeBroadPhase()` is available after bulk level creation.

Close each world explicitly, normally from `Application.dispose()` or try-with-resources.
It owns its native bodies, shapes, joints, allocator, workers, filters, and listeners.
Body and joint handles can be closed early. Closing is idempotent; live operations on
destroyed handles fail. Models and textures remain application-owned. Jolt's global
factory lives for the JVM's lifetime, allowing independent worlds to coexist.

Gradle includes single-precision release natives for Windows x64/ARM64, Linux
x64/ARM64/ARM32 hard-float, and macOS x64/ARM64. This integration was exercised on
Windows x64; the other binaries are packaged but have not been tested here. The loader
extracts the matching library from the runtime classpath into a temporary directory
on first world creation. Java 25 applications should enable
`--enable-native-access=ALL-UNNAMED`; the supplied Gradle run/test tasks already do so.
These changes are in the source tree and do not change an already-published release.

Upstream references: [adding Jolt JNI](https://stephengold.github.io/jolt-jni-docs/jolt-jni-en/English/add.html)
and [native object ownership](https://stephengold.github.io/jolt-jni-docs/jolt-jni-en/English/free.html).


Math types in these examples are imported from "org.joml" and "org.joml.primitives". See [JOML migration](joml-migration.md).
