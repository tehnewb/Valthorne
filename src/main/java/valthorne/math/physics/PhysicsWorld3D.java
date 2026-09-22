package valthorne.math.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.*;
import org.joml.primitives.Rayf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Owns a Z-up Jolt simulation, its rigid bodies, distance joints, collision filters,
 * and native execution resources. Gravity initially points along negative Z at
 * 9.81 distance units per second squared. Keep distances, masses, forces, and
 * velocities in a consistent unit system.
 * <p>
 * The public simulation API is confined to the thread that creates the world.
 * Optional native workers perform internal simulation only; contact data is copied
 * into a queue and application listeners run later on the owner thread. Before-step,
 * after-step, and contact callbacks may modify bodies and listeners, but may not
 * recursively step or close the world. Callback exceptions propagate to the caller.
 * </p>
 * <p>
 * {@link #update(float)} accumulates elapsed time, advances a bounded number of
 * fixed steps, and discards excess whole steps after stalls. {@link #step()} advances
 * one fixed step independently of that accumulator. Bound models normally receive
 * the latest pose; call {@link #syncModels(boolean)} with true after updating to
 * interpolate visuals with one step of display latency. Models remain caller-owned.
 * Close the world on its owner thread to invalidate handles and release native
 * resources.
 * </p>
 * <pre>{@code
 * try (PhysicsWorld3D world = new PhysicsWorld3D()) {
 *     RigidBody3D body = world.createBody(bodySettings);
 *     body.bind(model);
 *     world.update(frameSeconds);
 *     world.syncModels(true);
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class PhysicsWorld3D implements AutoCloseable {
    final BodyInterface bodies; // Borrowed body interface backed by the owned native system.
    private final Thread owner = Thread.currentThread(); // Thread on which public simulation access is permitted.
    private final Settings settings; // Immutable timing, native capacity, and worker settings.
    private final ArrayList<JoltPhysicsObject> resources = new ArrayList<>(); // Native resources registered in acquisition order for reverse cleanup.
    private final LinkedHashMap<Integer, RigidBody3D> bodyMap = new LinkedHashMap<>(); // Live body handles in insertion order, indexed by native ID.
    private final HashMap<Integer, RigidBody3D> retiredBodies = new HashMap<>(); // Destroyed handles retained until queued contact identifiers are resolved.
    private final ArrayList<DistanceJoint3D> joints = new ArrayList<>(); // Live world-owned distance joints.
    private final ArrayList<Consumer<ContactEvent3D>> contactListeners = new ArrayList<>(); // Owner-thread contact consumers; duplicates are allowed.
    private final ArrayList<Consumer<PhysicsWorld3D>> beforeStepListeners = new ArrayList<>(); // Callbacks run before each fixed native step.
    private final ArrayList<Consumer<PhysicsWorld3D>> afterStepListeners = new ArrayList<>(); // Callbacks run after successful integration and pose capture.
    private List<Consumer<PhysicsWorld3D>> beforeStepSnapshot = List.of(), afterStepSnapshot = List.of(); // Cached callback snapshots, invalidated when listener membership changes.
    private final ConcurrentLinkedQueue<RawContact> pendingContacts = new ConcurrentLinkedQueue<>(); // Thread-safe queue of copied native contact data.
    private final TempAllocator allocator; // Owned temporary native simulation allocator.
    private final JobSystem jobs; // Owned single-threaded or worker-pool job system.
    private PhysicsSystem system; // Owned native physics system, nullable during construction.
    private boolean closed, advancing, inNative; // Lifecycle, stepping-recursion, and native-update access guards.
    private double accumulator, droppedTime; // Unconsumed elapsed seconds and cumulative discarded whole-step seconds.
    private long stepCount; // Number of native updates that returned, including reported capacity errors.

    /**
     * Creates a world with default capacities, a 60 Hz fixed step, up to eight
     * catch-up steps per update, and single-threaded native execution. Uses the
     * default collision-layer policy and binds access to the creating thread.
     */
    public PhysicsWorld3D() {
        this(Settings.defaults(), new CollisionLayers3D());
    }

    /**
     * Creates a world with supplied immutable simulation settings and the default
     * collision-layer policy.
     *
     * @param settings nonnull timestep, capacity, and worker configuration
     * @throws NullPointerException if settings is null
     */
    public PhysicsWorld3D(Settings settings) {
        this(settings, new CollisionLayers3D());
    }

    /**
     * Initializes Jolt, builds native filters from the supplied collision policy,
     * allocates the selected job system, and installs contact capture callbacks.
     * Static/static pairs are excluded regardless of policy. Layer rules are copied
     * into native tables; later policy edits do not reconfigure this world.
     * Resources registered before a construction failure are released.
     *
     * @param settings nonnull immutable simulation configuration
     * @param layers   nonnull application collision-layer policy
     * @throws NullPointerException if either argument is null
     */
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
            // Jolt JNI 6.0.0 swaps these counts in its native bridge, despite the
            // Java parameter names. Supply object count first so native Jolt gets
            // (mapping, 2 broadphase layers, pairs, layerCount object layers).
            // Keep the cross-layer collision regression when upgrading the binding.
            // https://github.com/stephengold/jolt-jni/blob/6.0.0/src/main/native/glue/o/ObjectVsBroadPhaseLayerFilterTable.cpp
            ObjectVsBroadPhaseLayerFilterTable broadphase = own(new ObjectVsBroadPhaseLayerFilterTable(mapping, layerCount, pairs, 2));
            allocator = own(new TempAllocatorMalloc());
            jobs = settings.workerThreads == 0 ? own(new JobSystemSingleThreaded(Jolt.cMaxPhysicsJobs)) : own(new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, settings.workerThreads));
            // Jolt JNI maintains a process-wide registry of systems.
            synchronized (JoltRuntime.class) {
                system = own(new PhysicsSystem());
            }
            system.init(settings.maxBodies, 0, settings.maxBodyPairs, settings.maxContacts, mapping, broadphase, pairs);
            system.setGravity(0, 0, -9.81f);
            bodies = system.getBodyInterface();
            CustomContactListener listener = own(new CustomContactListener() {
                /**
                 * Copies a newly reported native contact into the owner-thread dispatch queue.
                 * Native addresses are borrowed only for this callback; application code is
                 * not invoked while native body locks may be held.
                 *
                 * @param a first native body address
                 * @param b second native body address
                 * @param manifold borrowed contact manifold address
                 * @param contactSettings native contact settings address, unused here
                 */
                @Override
                public void onContactAdded(long a, long b, long manifold, long contactSettings) {
                    contact(ContactEvent3D.Type.ADDED, a, b, manifold);
                }

                /**
                 * Copies a continuing native contact for later owner-thread notification.
                 * Does not retain native addresses or invoke application listeners.
                 *
                 * @param a first native body address
                 * @param b second native body address
                 * @param manifold borrowed contact manifold address
                 * @param contactSettings native contact settings address, unused here
                 */
                @Override
                public void onContactPersisted(long a, long b, long manifold, long contactSettings) {
                    contact(ContactEvent3D.Type.PERSISTED, a, b, manifold);
                }

                /**
                 * Queues body and subshape identifiers for a removed contact. Removal events
                 * have no manifold, so the copied normal and penetration are zero.
                 *
                 * @param pairAddress borrowed native subshape-pair address
                 */
                @Override
                public void onContactRemoved(long pairAddress) {
                    SubShapeIdPair pair = new SubShapeIdPair(pairAddress);
                    pendingContacts.add(new RawContact(ContactEvent3D.Type.REMOVED, pair.getBody1Id(), pair.getBody2Id(), pair.getSubShapeId1(), pair.getSubShapeId2(), new Vector3f(), 0));
                }
            });
            system.setContactListener(listener);
        } catch (RuntimeException | Error e) {
            releaseResources();
            throw e;
        }
    }

    /**
     * Registers a native resource for reverse-order release and returns it to the
     * construction code. The resource must remain valid until world cleanup.
     *
     * @param resource newly owned native resource
     * @param <T>      native resource type
     * @return resource
     */
    private <T extends JoltPhysicsObject> T own(T resource) {
        resources.add(resource);
        return resource;
    }

    /**
     * Copies contact identifiers, world normal, and penetration from borrowed native
     * objects into the concurrent queue. Safe for native worker callbacks because
     * it avoids owner-thread collections and application listeners.
     *
     * @param type    added or persisted contact category
     * @param a       first borrowed body address
     * @param b       second borrowed body address
     * @param address borrowed manifold address
     */
    private void contact(ContactEvent3D.Type type, long a, long b, long address) {
        // Copy borrowed native data immediately; never invoke game code while Jolt holds body locks.
        Body first = new Body(a), second = new Body(b);
        ContactManifold manifold = new ContactManifold(address);
        pendingContacts.add(new RawContact(type, first.getId(), second.getId(), manifold.getSubShapeId1(), manifold.getSubShapeId2(), PhysicsMath3D.vector(manifold.getWorldSpaceNormal()), manifold.getPenetrationDepth()));
    }

    /**
     * Rejects world access from another thread, after closure, or during the native
     * integration call. Owner-thread callbacks outside native integration may access
     * the world, subject to separate recursion and closure guards.
     *
     * @throws IllegalStateException if any world access restriction is violated
     */
    void check() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("PhysicsWorld3D must be accessed on its creating thread");
        if (closed) throw new IllegalStateException("Physics world is closed");
        if (inNative) throw new IllegalStateException("Cannot access the world during a native physics update");
    }

    /**
     * Returns the immutable duration of one simulation step. This metadata remains
     * readable after closure.
     *
     * @return fixed timestep in seconds
     */
    public float getFixedTimeStep() {
        return settings.fixedTimeStep;
    }

    /**
     * Reports whether successful cleanup has marked this world closed. Available
     * after close; this flag does not make other access thread-safe.
     *
     * @return whether the world is closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns the number of native update calls that returned. A returned capacity
     * error still increments the count before it is reported to the caller.
     *
     * @return cumulative executed step count
     */
    public long getStepCount() {
        return stepCount;
    }

    /**
     * Returns accumulated whole-step time discarded when update reaches its substep
     * limit. Fractional time is retained for later frames.
     *
     * @return discarded elapsed time in seconds
     */
    public double getDroppedTime() {
        return droppedTime;
    }

    /**
     * Returns accumulated time divided by the fixed timestep. After a successful
     * update this is normally in [0,1); during callbacks or after an interrupted
     * update it may be larger. This getter does not clamp the value.
     *
     * @return fraction used for optional visual interpolation
     */
    public float getInterpolationAlpha() {
        return (float) (accumulator / settings.fixedTimeStep);
    }

    /**
     * Copies current handles into an unmodifiable list in body insertion order.
     * The list membership is a snapshot, but its handles remain live and world-owned.
     *
     * @return snapshot of currently registered bodies
     * @throws IllegalStateException if world access is prohibited
     */
    public List<RigidBody3D> getBodies() {
        check();
        return List.copyOf(bodyMap.values());
    }

    /**
     * Counts currently registered bodies, excluding destroyed handles retained
     * temporarily for contact resolution.
     *
     * @return live body count
     * @throws IllegalStateException if world access is prohibited
     */
    public int getBodyCount() {
        check();
        return bodyMap.size();
    }

    /**
     * Copies native gravity into independent engine storage.
     *
     * @return world acceleration vector in distance units per second squared
     * @throws IllegalStateException if world access is prohibited
     */
    public Vector3f getGravity() {
        check();
        return PhysicsMath3D.vector(system.getGravity());
    }

    /**
     * Copies a finite world acceleration into the native simulation without changing
     * individual bodies' gravity factors.
     *
     * @param gravity nonnull finite world-space gravity vector
     * @return this world
     * @throws NullPointerException     if gravity is null
     * @throws IllegalArgumentException if a component is nonfinite
     * @throws IllegalStateException    if world access is prohibited
     */
    public PhysicsWorld3D setGravity(Vector3f gravity) {
        check();
        system.setGravity(PhysicsMath3D.vector(gravity));
        return this;
    }

    /**
     * Adds an owner-thread listener for queued contact events after successful
     * simulation. Duplicate registrations are allowed. The entire event dispatch
     * uses a listener snapshot, so membership changes affect a later dispatch.
     *
     * @param listener nonnull contact consumer
     * @throws NullPointerException  if listener is null
     * @throws IllegalStateException if world access is prohibited
     */
    public void addContactListener(Consumer<ContactEvent3D> listener) {
        check();
        contactListeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Removes the first matching contact-listener registration. Missing or null
     * listeners have no effect; an already captured dispatch snapshot is unchanged.
     *
     * @param listener registration to remove
     * @throws IllegalStateException if world access is prohibited
     */
    public void removeContactListener(Consumer<ContactEvent3D> listener) {
        check();
        contactListeners.remove(listener);
    }

    /**
     * Registers a callback before every fixed native step, including each catch-up
     * substep. Use it for sustained forces and kinematic targets. Membership is
     * snapshotted for each dispatch; callbacks may modify bodies and listeners.
     * A callback failure prevents that invocation's native step from starting.
     *
     * @param listener nonnull owner-thread callback
     * @throws NullPointerException  if listener is null
     * @throws IllegalStateException if world access is prohibited
     */
    public void addBeforeStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        beforeStepListeners.add(Objects.requireNonNull(listener));
        beforeStepSnapshot = null;
    }

    /**
     * Removes the first matching before-step registration and invalidates the cached
     * snapshot for a later dispatch. An active dispatch keeps its existing snapshot.
     *
     * @param listener callback to remove; absent or null values have no effect
     * @throws IllegalStateException if world access is prohibited
     */
    public void removeBeforeStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        beforeStepListeners.remove(listener);
        beforeStepSnapshot = null;
    }

    /**
     * Registers an owner-thread callback after a successful native step, pose capture,
     * and immediate model synchronization, but before contact dispatch. Bodies and
     * listeners may be changed; recursive stepping and world closure are prohibited.
     * Membership changes affect subsequent dispatch snapshots.
     *
     * @param listener nonnull callback
     * @throws NullPointerException  if listener is null
     * @throws IllegalStateException if world access is prohibited
     */
    public void addAfterStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        afterStepListeners.add(Objects.requireNonNull(listener));
        afterStepSnapshot = null;
    }

    /**
     * Removes the first matching after-step registration and invalidates the next
     * dispatch snapshot. Does not alter callbacks already captured for dispatch.
     *
     * @param listener callback to remove; absent or null values have no effect
     * @throws IllegalStateException if world access is prohibited
     */
    public void removeAfterStepListener(Consumer<PhysicsWorld3D> listener) {
        check();
        afterStepListeners.remove(listener);
        afterStepSnapshot = null;
    }

    /**
     * Creates and adds a native body using the supplied shape, material, motion, and
     * collision settings, then returns its world-owned handle. Static bodies start
     * inactive; dynamic and kinematic bodies start active. Temporary shape and
     * creation wrappers are closed after the native body acquires its shape reference.
     * If handle creation fails after allocation, the native body is removed and destroyed.
     *
     * @param settings nonnull body configuration
     * @return newly registered body handle
     * @throws NullPointerException  if settings is null
     * @throws IllegalStateException if world access is invalid, capacity is reached, or native allocation fails
     */
    public RigidBody3D createBody(BodySettings3D settings) {
        check();
        Objects.requireNonNull(settings);
        if (bodyMap.size() >= this.settings.maxBodies) throw new IllegalStateException("Physics body capacity reached");
        int id = Jolt.cInvalidBodyId;
        try (Shape shape = settings.shape.createNative(); BodyCreationSettings nativeSettings = new BodyCreationSettings()) {
            nativeSettings.setShape(shape).setPosition(PhysicsMath3D.position(settings.position)).setRotation(PhysicsMath3D.rotation(settings.rotation)).setMotionType(switch (settings.motion) {
                case STATIC -> EMotionType.Static;
                case KINEMATIC -> EMotionType.Kinematic;
                case DYNAMIC -> EMotionType.Dynamic;
            }).setObjectLayer(settings.layer * 2 + (settings.motion == MotionType3D.STATIC ? 0 : 1)).setFriction(settings.friction).setRestitution(settings.restitution).setLinearDamping(settings.linearDamping).setAngularDamping(settings.angularDamping).setGravityFactor(settings.gravityFactor).setIsSensor(settings.sensor).setAllowSleeping(settings.sleeping).setAllowedDofs(settings.rotationLocked ? EAllowedDofs.TranslationX | EAllowedDofs.TranslationY | EAllowedDofs.TranslationZ : EAllowedDofs.All).setLinearVelocity(PhysicsMath3D.vector(settings.velocity)).setMotionQuality(settings.continuous ? EMotionQuality.LinearCast : EMotionQuality.Discrete);
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

    /**
     * Destroys attached distance joints, removes the body from simulation, and
     * invalidates its handle. Temporarily retains the handle to resolve queued removal
     * events. Does nothing for an already destroyed body from this world, provided
     * world access itself remains valid.
     *
     * @param body handle owned by this world
     * @throws IllegalArgumentException if body is null or belongs to another world
     * @throws IllegalStateException    if world access is prohibited
     */
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

    /**
     * Checks handle identity ownership without checking body lifetime.
     *
     * @param body candidate handle
     * @throws IllegalArgumentException if body is null or belongs to another world
     */
    private void requireOwn(RigidBody3D body) {
        if (body == null || body.world != this) throw new IllegalArgumentException("Body belongs to another world");
    }

    /**
     * Accumulates finite nonnegative elapsed time and performs at most maxSubSteps
     * fixed updates. Drops remaining whole steps after reaching that limit, records
     * their duration, and preserves the fractional remainder. Synchronizes models
     * to current poses before returning.
     * <p>
     * Listener and native failures propagate. Time is consumed only after native
     * integration returns, before after-step and contact callbacks; a later callback
     * failure does not roll back the simulated step. The recursion guard is released
     * on every exit.
     * </p>
     *
     * @param elapsedSeconds frame duration in seconds
     * @return number of fixed steps performed by this call
     * @throws IllegalArgumentException if elapsedSeconds is negative or nonfinite
     * @throws IllegalStateException    if access is invalid, stepping is recursive, or Jolt reports a capacity error
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
        } finally {
            advancing = false;
        }
    }

    /**
     * Advances exactly one fixed simulation step without consuming or adding update's
     * accumulated time. Runs the same callbacks and pose synchronization as a substep.
     * Useful when the caller controls the fixed-step clock directly.
     *
     * @throws IllegalStateException if access is invalid, stepping is recursive, or Jolt reports a capacity error
     */
    public void step() {
        check();
        if (advancing) throw new IllegalStateException("Recursive physics stepping is not allowed");
        advancing = true;
        try {
            integrate(false);
        } finally {
            advancing = false;
        }
    }

    /**
     * Dispatches before-step callbacks, saves prior poses, and enters native
     * integration with world access blocked. On return, optionally consumes elapsed
     * time, increments the count, captures poses, and synchronizes models. Native
     * capacity flags clear queued contacts and throw before after-step/contact
     * dispatch. Callback failures are propagated without undoing completed work.
     *
     * @param consumeTime whether to subtract one fixed step from the accumulator
     * @throws IllegalStateException if native integration reports capacity error flags
     */
    private void integrate(boolean consumeTime) {
        List<Consumer<PhysicsWorld3D>> before = beforeStepSnapshot;
        if (before == null) beforeStepSnapshot = before = List.copyOf(beforeStepListeners);
        for (int i = 0; i < before.size(); i++) before.get(i).accept(this);
        for (RigidBody3D body : bodyMap.values()) body.remember();
        int errors;
        inNative = true;
        try {
            errors = system.update(settings.fixedTimeStep, 1, allocator, jobs);
        } finally {
            inNative = false;
        }
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

    /**
     * Drains raw events into application events, resolving live or recently retired
     * handles. Skips events whose bodies cannot be resolved, then clears retired
     * handles and dispatches a listener snapshot. Listeners may safely modify world
     * membership; an exception stops the remaining dispatch and propagates.
     */
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
     * Copies physical poses into bound models, optionally interpolating between the
     * previous and current captured poses using the accumulator fraction. Interpolation
     * adds one fixed step of visual latency and does not change physical state.
     * Call after update to override its default current-pose synchronization.
     *
     * @param interpolate true for interpolated visuals, false for the latest physical pose
     * @throws IllegalStateException if world access is prohibited
     */
    public void syncModels(boolean interpolate) {
        check();
        float alpha = interpolate ? getInterpolationAlpha() : 1f;
        for (RigidBody3D body : bodyMap.values()) body.sync(alpha);
    }

    /**
     * Requests native broadphase optimization, useful after bulk body placement.
     * Must run on the owner thread outside native integration.
     *
     * @throws IllegalStateException if world access is prohibited
     */
    public void optimizeBroadPhase() {
        check();
        system.optimizeBroadPhase();
    }

    /**
     * Finds the closest native shape hit along a finite world ray, considering all
     * query layers and bodies. Direction magnitude is normalized internally, so
     * maxDistance determines the segment length.
     *
     * @param ray         nonnull ray with finite origin and finite nonzero direction
     * @param maxDistance finite positive segment length in world units
     * @return closest hit, or null if no hit can be returned
     * @throws NullPointerException     if ray is null
     * @throws IllegalArgumentException if ray values or distance are invalid
     */
    public PhysicsRayHit3D raycast(Rayf ray, float maxDistance) {
        return raycast(ray, maxDistance, null);
    }

    /**
     * Queries the closest shape hit while optionally excluding one live body from
     * this world. Uses default broadphase and object filters, normalizes the ray
     * direction, and obtains the surface normal under a native read lock.
     *
     * @param ray         nonnull ray with finite origin and finite nonzero direction
     * @param maxDistance finite positive world-space ray length
     * @param ignoredBody live body to exclude, or null for no exclusion
     * @return closest hit with world point, normal, and distance; null for no hit or a failed read lock
     * @throws NullPointerException     if ray is null
     * @throws IllegalArgumentException if inputs are invalid or the excluded body belongs elsewhere
     * @throws IllegalStateException    if world access is invalid or the excluded body is destroyed
     */
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
        try (RRayCast cast = new RRayCast(PhysicsMath3D.position(new Vector3f(ray.oX, ray.oY, ray.oZ)), PhysicsMath3D.vector(direction)); RayCastResult result = new RayCastResult(); BroadPhaseLayerFilter broadFilter = new BroadPhaseLayerFilter(); ObjectLayerFilter objectFilter = new ObjectLayerFilter(); BodyFilter bodyFilter = rayBodyFilter(ignoredBody)) {
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

    /**
     * Allocates a native query filter, optionally excluding one body ID. Closes an
     * exclusion filter if initialization fails; otherwise the caller owns its cleanup.
     *
     * @param ignoredBody body to exclude, or null
     * @return owned native filter that the caller must close
     */
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

    /**
     * Creates a world-space distance constraint between two distinct live bodies,
     * at least one of which is dynamic. Equal minimum and maximum distances form a
     * fixed-length constraint; a range allows separation within those limits.
     * The world owns the joint and destroys it when either body is destroyed.
     *
     * @param a           first body owned by this world
     * @param b           second body owned by this world
     * @param anchorA     finite world-space anchor on the first body
     * @param anchorB     finite world-space anchor on the second body
     * @param minDistance finite nonnegative minimum separation
     * @param maxDistance finite maximum separation at least minDistance
     * @return newly registered joint
     * @throws NullPointerException     if an anchor is null
     * @throws IllegalArgumentException if ownership, motion, anchors, or distance limits are invalid
     * @throws IllegalStateException    if world access is invalid or either body is destroyed
     */
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

    /**
     * Removes and closes an owned native constraint, then invalidates its handle.
     * An already destroyed joint has no effect after access and ownership checks.
     *
     * @param joint joint owned by this world
     * @throws IllegalArgumentException if the joint belongs to another world
     * @throws IllegalStateException    if world access is prohibited
     */
    void destroyJoint(DistanceJoint3D joint) {
        check();
        if (joint.world != this) throw new IllegalArgumentException("Joint belongs to another world");
        if (joint.destroyed) return;
        system.removeConstraint(joint.constraint);
        joint.constraint.close();
        joints.remove(joint);
        joint.destroyed = true;
    }

    /**
     * Releases this world through {@link #close()}, including all bodies, joints,
     * listeners, and registered native resources.
     *
     * @throws IllegalStateException if a live world is disposed from an invalid access context or callback
     */
    public void dispose() {
        close();
    }

    /**
     * Detaches native contact capture, destroys joints and bodies, clears callbacks
     * and queued events, then releases registered native resources. Models remain
     * caller-owned. Successful closure makes subsequent calls harmless.
     *
     * @throws IllegalStateException if a live world is closed off its owner thread, during native integration, or from a step callback
     */
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

    /**
     * Detaches contact callbacks, removes the system from Jolt JNI's process-wide
     * registry under its shared lock, and closes resources in reverse acquisition
     * order. Used during construction failure and normal closure.
     */
    private void releaseResources() {
        if (system != null) {
            system.setContactListener(null);
            synchronized (JoltRuntime.class) {
                system.forgetMe();
            }
        }
        for (int i = resources.size() - 1; i >= 0; i--) resources.get(i).close();
        resources.clear();
    }

    /**
     * Immutable fixed-step timing, native capacity, and worker configuration.
     * Capacities bound simulation storage; reaching them can cause body allocation
     * or update failures. A worker count of zero chooses the single-threaded job
     * system while preserving the same owner-thread public API.
     *
     * @param fixedTimeStep finite positive step duration in seconds
     * @param maxSubSteps   maximum fixed steps performed by one update
     * @param maxBodies     maximum concurrently registered bodies
     * @param maxBodyPairs  native body-pair capacity
     * @param maxContacts   native contact-constraint capacity
     * @param workerThreads native worker count, with zero selecting single-threaded execution
     * @author Albert Beaupre
     */
    public record Settings(float fixedTimeStep, int maxSubSteps, int maxBodies, int maxBodyPairs, int maxContacts, int workerThreads) {
        /**
         * Validates immutable simulation settings at construction.
         *
         * @throws IllegalArgumentException if the timestep is nonfinite or nonpositive, a capacity is below one, or workers are negative
         */
        public Settings {
            PhysicsMath3D.positive(fixedTimeStep, "fixed time step");
            if (maxSubSteps < 1 || maxBodies < 1 || maxBodyPairs < 1 || maxContacts < 1 || workerThreads < 0)
                throw new IllegalArgumentException("Capacities must be positive and worker count nonnegative");
        }

        /**
         * Creates the default 60 Hz configuration: eight substeps per update, 4096
         * bodies, 65536 body pairs, 20480 contacts, and no native worker threads.
         *
         * @return a new default settings value
         */
        public static Settings defaults() {
            return new Settings(1f / 60f, 8, 4096, 65536, 20480, 0);
        }
    }

    /**
     * Copied native contact data queued until owner-thread dispatch. Holds IDs and
     * engine values rather than borrowed native addresses. Removed contacts carry a
     * zero normal and zero penetration because no manifold is available.
     *
     * @param type        contact transition
     * @param a           first body ID
     * @param b           second body ID
     * @param subA        first subshape ID
     * @param subB        second subshape ID
     * @param normal      copied world-space normal
     * @param penetration copied penetration depth in world units
     * @author Albert Beaupre
     */
    private record RawContact(ContactEvent3D.Type type, int a, int b, int subA, int subB, Vector3f normal, float penetration) {
    }
}
