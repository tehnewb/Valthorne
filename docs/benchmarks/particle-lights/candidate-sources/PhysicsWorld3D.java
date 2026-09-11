package valthorne.math.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import org.joml.primitives.Rayf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Jolt-backed Z-up physics world. Thread-confined public API with optional native worker threads.
 * Owns bodies, joints and all native simulation resources. Does not own render models.
 */
public final class PhysicsWorld3D implements AutoCloseable {
    final BodyInterface bodies;
    private final Thread owner = Thread.currentThread();
    private final Settings settings;
    private final ArrayList<JoltPhysicsObject> resources = new ArrayList<>();
    private final LinkedHashMap<Integer, RigidBody3D> bodyMap = new LinkedHashMap<>();
    private final HashMap<Integer, RigidBody3D> retiredBodies = new HashMap<>();
    private final ArrayList<DistanceJoint3D> joints = new ArrayList<>();
    private final ArrayList<Consumer<ContactEvent3D>> contactListeners = new ArrayList<>();
    private final ArrayList<Consumer<PhysicsWorld3D>> beforeStepListeners = new ArrayList<>();
    private final ArrayList<Consumer<PhysicsWorld3D>> afterStepListeners = new ArrayList<>();
    private List<Consumer<PhysicsWorld3D>> beforeStepSnapshot = List.of(), afterStepSnapshot = List.of();
    private final ConcurrentLinkedQueue<RawContact> pendingContacts = new ConcurrentLinkedQueue<>();
    private final TempAllocator allocator;
    private final JobSystem jobs;
    private PhysicsSystem system;
    private boolean closed, advancing, inNative;
    private double accumulator, droppedTime;
    private long stepCount;
    public PhysicsWorld3D() {this(Settings.defaults(), new CollisionLayers3D());}

    public PhysicsWorld3D(Settings settings) {this(settings, new CollisionLayers3D());}

    public PhysicsWorld3D(Settings settings, CollisionLayers3D layers) {
        this.settings = Objects.requireNonNull(settings);
        Objects.requireNonNull(layers);
        JoltRuntime.initialize();
        try {
            int layerCount = CollisionLayers3D.COUNT * 2;
            ObjectLayerPairFilterTable pairs = own(new ObjectLayerPairFilterTable(layerCount));
            BroadPhaseLayerInterfaceTable mapping = own(new BroadPhaseLayerInterfaceTable(layerCount, 2));
            for (int a = 0; a < layerCount; a++) {
                mapping.mapObjectToBroadPhaseLayer(a, a % 2);
                for (int b = a; b < layerCount; b++)
                    if ((a % 2 == 1 || b % 2 == 1) && layers.collides(a / 2, b / 2)) pairs.enableCollision(a, b);
            }
            ObjectVsBroadPhaseLayerFilterTable broadphase = own(new ObjectVsBroadPhaseLayerFilterTable(mapping, 2, pairs, layerCount));
            allocator = own(new TempAllocatorMalloc());
            jobs = settings.workerThreads == 0 ? own(new JobSystemSingleThreaded(Jolt.cMaxPhysicsJobs)) :
                    own(new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, settings.workerThreads));
            // Jolt JNI maintains a process-wide registry of systems.
            synchronized (JoltRuntime.class) {system = own(new PhysicsSystem());}
            system.init(settings.maxBodies, 0, settings.maxBodyPairs, settings.maxContacts, mapping, broadphase, pairs);
            system.setGravity(0, 0, -9.81f);
            bodies = system.getBodyInterface();
            CustomContactListener listener = own(new CustomContactListener() {
                @Override
                public void onContactAdded(long a, long b, long manifold, long contactSettings) {contact(ContactEvent3D.Type.ADDED, a, b, manifold);}

                @Override
                public void onContactPersisted(long a, long b, long manifold, long contactSettings) {contact(ContactEvent3D.Type.PERSISTED, a, b, manifold);}

                @Override
                public void onContactRemoved(long pairAddress) {
                    SubShapeIdPair pair = new SubShapeIdPair(pairAddress);
                    pendingContacts.add(new RawContact(ContactEvent3D.Type.REMOVED, pair.getBody1Id(), pair.getBody2Id(),
                            pair.getSubShapeId1(), pair.getSubShapeId2(), new Vector3f(), 0));
                }
            });
            system.setContactListener(listener);
        } catch (RuntimeException | Error e) {
            releaseResources();
            throw e;
        }
    }

    private <T extends JoltPhysicsObject> T own(T resource) {
        resources.add(resource);
        return resource;
    }

    private void contact(ContactEvent3D.Type type, long a, long b, long address) {
        // Copy borrowed native data immediately; never invoke game code while Jolt holds body locks.
        Body first = new Body(a), second = new Body(b);
        ContactManifold manifold = new ContactManifold(address);
        pendingContacts.add(new RawContact(type, first.getId(), second.getId(), manifold.getSubShapeId1(), manifold.getSubShapeId2(),
                PhysicsMath3D.vector(manifold.getWorldSpaceNormal()), manifold.getPenetrationDepth()));
    }

    void check() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("PhysicsWorld3D must be accessed on its creating thread");
        if (closed) throw new IllegalStateException("Physics world is closed");
        if (inNative) throw new IllegalStateException("Cannot access the world during a native physics update");
    }

    public float getFixedTimeStep() {return settings.fixedTimeStep;}

    /** Reports whether native resources have been released; remains available after close. */
    public boolean isClosed() {return closed;}

    public long getStepCount() {return stepCount;}

    public double getDroppedTime() {return droppedTime;}

    public float getInterpolationAlpha() {return (float) (accumulator / settings.fixedTimeStep);}

    public List<RigidBody3D> getBodies() {
        check();
        return List.copyOf(bodyMap.values());
    }

    public int getBodyCount() {
        check();
        return bodyMap.size();
    }

    public Vector3f getGravity() {
        check();
        return PhysicsMath3D.vector(system.getGravity());
    }

    public PhysicsWorld3D setGravity(Vector3f gravity) {
        check();
        system.setGravity(PhysicsMath3D.vector(gravity));
        return this;
    }

    public void addContactListener(Consumer<ContactEvent3D> listener) {
        check();
        contactListeners.add(Objects.requireNonNull(listener));
    }

    public void removeContactListener(Consumer<ContactEvent3D> listener) {
        check();
        contactListeners.remove(listener);
    }

    public void addBeforeStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        beforeStepListeners.add(Objects.requireNonNull(listener));
        beforeStepSnapshot = null;
    }

    public void removeBeforeStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        beforeStepListeners.remove(listener);
        beforeStepSnapshot = null;
    }

    /**
     * Registers an owner-thread callback after each successful native step and pose
     * capture, before contact dispatch. Bodies and listeners may be changed here;
     * recursive stepping and closing the world remain prohibited. Listener membership
     * is snapshotted for each dispatch, so changes affect later dispatches.
     */
    public void addAfterStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        afterStepListeners.add(Objects.requireNonNull(listener));
        afterStepSnapshot = null;
    }

    public void removeAfterStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        afterStepListeners.remove(listener);
        afterStepSnapshot = null;
    }

    public RigidBody3D createBody(BodySettings3D settings) {
        check();
        Objects.requireNonNull(settings);
        if (bodyMap.size() >= this.settings.maxBodies) throw new IllegalStateException("Physics body capacity reached");
        int id = Jolt.cInvalidBodyId;
        try (Shape shape = settings.shape.createNative(); BodyCreationSettings nativeSettings = new BodyCreationSettings()) {
            nativeSettings.setShape(shape).setPosition(PhysicsMath3D.position(settings.position)).setRotation(PhysicsMath3D.rotation(settings.rotation))
                    .setMotionType(switch (settings.motion) {
                        case STATIC -> EMotionType.Static;
                        case KINEMATIC -> EMotionType.Kinematic;
                        case DYNAMIC -> EMotionType.Dynamic;
                    })
                    .setObjectLayer(settings.layer * 2 + (settings.motion == MotionType3D.STATIC ? 0 : 1))
                    .setFriction(settings.friction).setRestitution(settings.restitution)
                    .setLinearDamping(settings.linearDamping).setAngularDamping(settings.angularDamping)
                    .setGravityFactor(settings.gravityFactor).setIsSensor(settings.sensor).setAllowSleeping(settings.sleeping)
                    .setAllowedDofs(settings.rotationLocked
                            ? EAllowedDofs.TranslationX | EAllowedDofs.TranslationY | EAllowedDofs.TranslationZ : EAllowedDofs.All)
                    .setLinearVelocity(PhysicsMath3D.vector(settings.velocity))
                    .setMotionQuality(settings.continuous ? EMotionQuality.LinearCast : EMotionQuality.Discrete);
            if (settings.motion != MotionType3D.STATIC) {
                nativeSettings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
                nativeSettings.getMassPropertiesOverride().setMass(settings.mass);
            }
            if (settings.motion == MotionType3D.KINEMATIC) nativeSettings.setCollideKinematicVsNonDynamic(true);
            id = bodies.createAndAddBody(nativeSettings, settings.motion == MotionType3D.STATIC ? EActivation.DontActivate : EActivation.Activate);
            if (id == Jolt.cInvalidBodyId) throw new IllegalStateException("Jolt could not allocate a body");
            RigidBody3D body = new RigidBody3D(this, id, settings);
            bodyMap.put(id, body);
            return body;
        } catch (RuntimeException | Error e) {
            if (id != Jolt.cInvalidBodyId) {
                bodies.removeBody(id);
                bodies.destroyBody(id);
            }
            throw e;
        }
    }

    public void destroyBody(RigidBody3D body) {
        check();
        requireOwn(body);
        if (body.destroyed) return;
        for (DistanceJoint3D joint : List.copyOf(joints)) if (joint.a == body || joint.b == body) destroyJoint(joint);
        bodies.removeBody(body.id);
        bodies.destroyBody(body.id);
        bodyMap.remove(body.id);
        retiredBodies.put(body.id, body);
        body.invalidate();
    }

    private void requireOwn(RigidBody3D body) {
        if (body == null || body.world != this) throw new IllegalArgumentException("Body belongs to another world");
    }

    /**
     * Runs a bounded number of fixed steps, discarding excess whole steps after long stalls. Returns the number simulated.
     */
    public int update(float elapsedSeconds) {
        check();
        PhysicsMath3D.nonnegative(elapsedSeconds, "elapsed time");
        if (advancing) throw new IllegalStateException("Recursive physics stepping is not allowed");
        accumulator += elapsedSeconds;
        int steps = 0;
        advancing = true;
        try {
            double tolerance = settings.fixedTimeStep * 1e-6;
            while (accumulator + tolerance >= settings.fixedTimeStep && steps < settings.maxSubSteps) {
                integrate(true);
                steps++;
            }
            if (accumulator >= settings.fixedTimeStep) {
                double remainder = accumulator % settings.fixedTimeStep;
                droppedTime += accumulator - remainder;
                accumulator = remainder;
            }
            syncModels(false);
            return steps;
        } finally {advancing = false;}
    }

    /**
     * Advances exactly one fixed step, without consuming update's accumulated fractional time.
     */
    public void step() {
        check();
        if (advancing) throw new IllegalStateException("Recursive physics stepping is not allowed");
        advancing = true;
        try {integrate(false);} finally {advancing = false;}
    }

    private void integrate(boolean consumeTime) {
        List<Consumer<PhysicsWorld3D>> before = beforeStepSnapshot;
        if (before == null) beforeStepSnapshot = before = List.copyOf(beforeStepListeners);
        for (int i = 0; i < before.size(); i++) before.get(i).accept(this);
        for (RigidBody3D body : bodyMap.values()) body.remember();
        int errors;
        inNative = true;
        try {errors = system.update(settings.fixedTimeStep, 1, allocator, jobs);} finally {inNative = false;}
        // Consume only simulated time, before invoking game contact callbacks.
        if (consumeTime) accumulator = Math.max(0, accumulator - settings.fixedTimeStep);
        stepCount++;
        for (RigidBody3D body : bodyMap.values()) body.capture();
        syncModels(false);
        if (errors != 0) {
            pendingContacts.clear();
            throw new IllegalStateException("Jolt simulation capacity error flags: " + errors);
        }
        List<Consumer<PhysicsWorld3D>> after = afterStepSnapshot;
        if (after == null) afterStepSnapshot = after = List.copyOf(afterStepListeners);
        for (int i = 0; i < after.size(); i++) after.get(i).accept(this);
        dispatchContacts();
    }

    private void dispatchContacts() {
        ArrayList<ContactEvent3D> events = new ArrayList<>();
        RawContact raw;
        while ((raw = pendingContacts.poll()) != null) {
            RigidBody3D a = bodyMap.getOrDefault(raw.a, retiredBodies.get(raw.a));
            RigidBody3D b = bodyMap.getOrDefault(raw.b, retiredBodies.get(raw.b));
            if (a != null && b != null)
                events.add(new ContactEvent3D(raw.type, a, b, raw.subA, raw.subB, raw.normal, raw.penetration));
        }
        retiredBodies.clear();
        // Snapshot listeners/events so callbacks may safely add or remove bodies and listeners.
        List<Consumer<ContactEvent3D>> listeners = List.copyOf(contactListeners);
        for (ContactEvent3D event : events) for (Consumer<ContactEvent3D> listener : listeners) listener.accept(event);
    }

    /**
     * Optionally interpolates visuals between the last two poses, adding one physics-step of display latency.
     */
    public void syncModels(boolean interpolate) {
        check();
        float alpha = interpolate ? getInterpolationAlpha() : 1f;
        for (RigidBody3D body : bodyMap.values()) body.sync(alpha);
    }

    public void optimizeBroadPhase() {
        check();
        system.optimizeBroadPhase();
    }

    public PhysicsRayHit3D raycast(Rayf ray, float maxDistance) {
        return raycast(ray, maxDistance, null);
    }

    /** Queries the closest hit while excluding one body, useful for player-owned rays. */
    public PhysicsRayHit3D raycast(Rayf ray, float maxDistance, RigidBody3D ignoredBody) {
        check();
        if (ignoredBody != null) {
            requireOwn(ignoredBody);
            ignoredBody.check();
        }
        Objects.requireNonNull(ray);
        PhysicsMath3D.positive(maxDistance, "ray distance");
        Vector3f direction = PhysicsMath3D.check(new Vector3f(ray.dX, ray.dY, ray.dZ));
        double length = Math.sqrt((double) ray.dX * ray.dX + (double) ray.dY * ray.dY + (double) ray.dZ * ray.dZ);
        if (length == 0) throw new IllegalArgumentException("Ray direction must be nonzero");
        direction.set((float) (ray.dX / length), (float) (ray.dY / length), (float) (ray.dZ / length)).mul(maxDistance);
        try (RRayCast cast = new RRayCast(PhysicsMath3D.position(new Vector3f(ray.oX, ray.oY, ray.oZ)), PhysicsMath3D.vector(direction));
             RayCastResult result = new RayCastResult(); BroadPhaseLayerFilter broadFilter = new BroadPhaseLayerFilter();
             ObjectLayerFilter objectFilter = new ObjectLayerFilter(); BodyFilter bodyFilter = rayBodyFilter(ignoredBody)) {
            if (!system.getNarrowPhaseQuery().castRay(cast, result, broadFilter, objectFilter, bodyFilter)) return null;
            RigidBody3D body = bodyMap.get(result.getBodyId());
            RVec3 point = cast.getPointOnRay(result.getFraction());
            Vector3f normal;
            try (BodyLockRead lock = new BodyLockRead(system.getBodyLockInterface(), body.id)) {
                if (!lock.succeeded()) return null;
                normal = PhysicsMath3D.vector(lock.getBody().getWorldSpaceSurfaceNormal(result.getSubShapeId2(), point));
            }
            return new PhysicsRayHit3D(body, result.getFraction() * maxDistance, PhysicsMath3D.position(point), normal);
        }
    }

    private static BodyFilter rayBodyFilter(RigidBody3D ignoredBody) {
        if (ignoredBody == null) return new BodyFilter();
        IgnoreMultipleBodiesFilter filter = new IgnoreMultipleBodiesFilter();
        try {
            filter.ignoreBody(ignoredBody.id);
            return filter;
        } catch (RuntimeException | Error failure) {
            filter.close();
            throw failure;
        }
    }

    public DistanceJoint3D createDistanceJoint(RigidBody3D a, RigidBody3D b, Vector3f anchorA, Vector3f anchorB, float minDistance, float maxDistance) {
        check();
        requireOwn(a);
        requireOwn(b);
        a.check();
        b.check();
        if (a == b || a.motion != MotionType3D.DYNAMIC && b.motion != MotionType3D.DYNAMIC)
            throw new IllegalArgumentException("Joint requires distinct bodies and at least one dynamic body");
        PhysicsMath3D.nonnegative(minDistance, "minimum distance");
        PhysicsMath3D.nonnegative(maxDistance, "maximum distance");
        if (maxDistance < minDistance)
            throw new IllegalArgumentException("Maximum distance must be at least the minimum");
        try (DistanceConstraintSettings settings = new DistanceConstraintSettings()) {
            settings.setSpace(EConstraintSpace.WorldSpace);
            settings.setPoint1(PhysicsMath3D.position(anchorA));
            settings.setPoint2(PhysicsMath3D.position(anchorB));
            settings.setMinDistance(minDistance);
            settings.setMaxDistance(maxDistance);
            TwoBodyConstraint constraint = bodies.createConstraint(settings, a.id, b.id);
            try {
                system.addConstraint(constraint);
                DistanceJoint3D joint = new DistanceJoint3D(this, a, b, constraint);
                joints.add(joint);
                return joint;
            } catch (RuntimeException | Error e) {
                constraint.close();
                throw e;
            }
        }
    }

    void destroyJoint(DistanceJoint3D joint) {
        check();
        if (joint.world != this) throw new IllegalArgumentException("Joint belongs to another world");
        if (joint.destroyed) return;
        system.removeConstraint(joint.constraint);
        joint.constraint.close();
        joints.remove(joint);
        joint.destroyed = true;
    }

    public void dispose() {close();}

    @Override
    public void close() {
        if (closed) return;
        check();
        if (advancing) throw new IllegalStateException("Cannot close a world inside a step callback");
        system.setContactListener(null);
        for (DistanceJoint3D joint : List.copyOf(joints)) destroyJoint(joint);
        for (RigidBody3D body : List.copyOf(bodyMap.values())) destroyBody(body);
        pendingContacts.clear();
        retiredBodies.clear();
        contactListeners.clear();
        beforeStepListeners.clear();
        afterStepListeners.clear();
        beforeStepSnapshot = afterStepSnapshot = List.of();
        releaseResources();
        closed = true;
    }

    private void releaseResources() {
        if (system != null) {
            system.setContactListener(null);
            synchronized (JoltRuntime.class) {system.forgetMe();}
        }
        for (int i = resources.size() - 1; i >= 0; i--) resources.get(i).close();
        resources.clear();
    }

    public record Settings(float fixedTimeStep, int maxSubSteps, int maxBodies,
            int maxBodyPairs, int maxContacts, int workerThreads) {
        public Settings {
            PhysicsMath3D.positive(fixedTimeStep, "fixed time step");
            if (maxSubSteps < 1 || maxBodies < 1 || maxBodyPairs < 1 || maxContacts < 1 || workerThreads < 0)
                throw new IllegalArgumentException("Capacities must be positive and worker count nonnegative");
        }

        public static Settings defaults() {return new Settings(1f / 60f, 8, 4096, 65536, 20480, 0);}
    }

    private record RawContact(ContactEvent3D.Type type, int a, int b, int subA, int subB, Vector3f normal, float penetration) {
    }
}
