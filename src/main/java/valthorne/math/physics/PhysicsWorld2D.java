package valthorne.math.physics;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BatchBodyInterface;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.JobSystem;
import com.github.stephengold.joltjni.JobSystemSingleThreaded;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.TempAllocator;
import com.github.stephengold.joltjni.TempAllocatorImplWithMallocFallback;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EAllowedDofs;
import com.github.stephengold.joltjni.enumerate.EBodyType;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import org.joml.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.Objects;
import java.util.Arrays;

/**
 * Owns a Jolt simulation constrained to XY translation and Z rotation. World
 * coordinates are Y-up meters, gravity defaults to (0, -9.81), and orientations
 * are counterclockwise radians. All bodies remain centered at Z=0. Native
 * geometry has finite depth, so this is a planar use of Jolt's 3D solver.
 *
 * <h2>Timing and rendering</h2>
 * <p>{@link #update(float)} accumulates frame time and performs a bounded number
 * of fixed steps. Excess whole steps are dropped after stalls; the fractional
 * remainder drives body interpolation. {@link #step()} performs exactly one
 * fixed step without changing accumulated frame time. Choose one timing entry
 * point for a game loop. Forces and torques last for the next native step only;
 * use direct stepping when applying a sustained force once per physics tick.</p>
 *
 * <h2>Geometry integration</h2>
 * <p>Settings constructed from geometry retain the original render shape.
 * Frame updates automatically write interpolated positions and degree rotations;
 * direct steps and contact delivery write current poses. Circles, rectangles,
 * rounded rectangles, triangles and convex polygons are supported. Pixel mappings
 * convert corner anchors, camera origins and Y directions automatically. Set
 * pixelsPerMeter, pixelOrigin and pixelsYDown on world settings; no separate
 * conversion object is required. createBody(shape) accepts pixel geometry directly. Geometry
 * dimensions and vertex counts are frozen for collision purposes: recreate the
 * body after resizing. Moving geometry manually requires
 * {@link RigidBody2D#setTransformFromGeometry()} to teleport physics. Geometry
 * objects and their mappings share the world's thread confinement.</p>
 *
 * <h2>Ownership and threading</h2>
 * <p>The creating thread exclusively owns public access, including disposal.
 * Optional native workers complete before stepping returns. Closing a body
 * removes its attached joints and destroys it; clearing or closing invalidates
 * all joint and body handles. Close is idempotent. The shared {@link JoltRuntime} remains loaded so
 * other worlds can continue simulating. No graphics context is required.</p>
 *
 * <h2>Contacts, queries and joints</h2>
 * <p>Contact listeners receive BEGIN/PERSIST/END events on the owner thread after
 * each native step and pose capture. They may mutate bodies, joints and listener
 * membership or run queries. Recursive stepping, world clearing and world closing
 * are rejected during delivery. One borrowed event view is reused; call
 * {@link ContactEvent2D#copy()} to retain a snapshot. Listener changes take effect
 * in the next step. END also occurs when a contact leaves Jolt's cache, including
 * sleeping, and may reference a destroyed body. Callback order across independent
 * pairs is unspecified, particularly with workers. Throwing an application
 * callback discards remaining events while keeping that step and its frame time
 * committed; the world remains usable.</p>
 *
 * <p>Rays return the closest native shape hit, point queries test exact supported
 * planar outlines, and bounds queries return conservative AABB candidates.
 * Sensors participate in queries. Queries reuse caller-owned output storage and
 * report total counts even when an array truncates results. Distance and pivot
 * joints retain body-local anchors initialized from world coordinates and permit
 * normal collisions between connected bodies.</p>
 *
 * <h2>Allocation and failure behavior</h2>
 * <p>A preallocated dense array stores handles and supports constant-time body
 * removal. Stepping and destination-based pose/velocity reads reuse scratch
 * values and create no per-body Java objects when contact copying is disabled.
 * Optional query, contact and joint resources are created only on first use.
 * Worlds with at least 128 moving bodies lazily batch pose reads through two
 * native calls, using 44 native bytes and one Java reference per moving body.
 * Membership changes rebuild these buffers before their next use; clearing
 * releases them. Fully sleeping worlds keep interpolation endpoints current
 * without rereading unchanged poses after the final transition to sleep.
 * Point/bounds queries allocate no steady Java objects. Jolt JNI 6.0.0 requires
 * temporary ray/result owners for rays and borrowed wrappers/normal vectors for
 * native contacts, whose Java allocation may be eliminated by the JVM. The
 * contact queue uses four preallocated arrays with packed body pairs, subshape
 * pairs and normal/depth values.
 * Native temporary storage uses a
 * reusable arena with malloc fallback. Creation allocates a Java handle and
 * native shape/body. If native solver capacities are exhausted, the world is
 * marked failed because Jolt may already have advanced an incomplete step;
 * inspect or dispose it, then create a suitably sized replacement. Contact-buffer
 * exhaustion or native-data copying failure also fails the world and rejects
 * partial application delivery.</p>
 *
 * <pre>{@code
 * try (PhysicsWorld2D world = new PhysicsWorld2D()) {
 *     world.createBody(new BodySettings2D(CollisionShape2D.box(20, 1)).motion(MotionType2D.STATIC).position(0, -0.5f));
 *     RigidBody2D ball = world.createBody(new BodySettings2D(CollisionShape2D.circle(0.5f)).position(0, 4).restitution(0.4f));
 *     PhysicsRayHit2D hit = new PhysicsRayHit2D();
 *     world.addContactListener(event -> {
 *         if (event.getType() == ContactType2D.BEGIN) {
 *             // React to event.getFirstBody() and event.getSecondBody().
 *         }
 *     });
 *     Vector2f renderedPosition = new Vector2f();
 *     world.update(1f / 60f);
 *     ball.getInterpolatedPosition(renderedPosition);
 *     world.raycast(0, 10, 0, -2, hit);
 * }
 * }</pre>
 */
public final class PhysicsWorld2D implements AutoCloseable {

    /**
     * Static bodies collide only with moving bodies; moving bodies collide with both layers.
     */
    static final int STATIC_LAYER = 0;

    /**
     * Dynamic and kinematic bodies share the moving broadphase partition.
     */
    static final int MOVING_LAYER = 1;

    /**
     * Jolt stores its body index in the lower 23 bits and its generation above them.
     */
    private static final int BODY_INDEX_MASK = 0x7fffff;

    private final Thread owner = Thread.currentThread(); // Thread permitted to access world state and native objects.
    private final RigidBody2D[] handles; // Dense live handles, sized once to the configured body limit.
    private final RigidBody2D[] nativeHandles; // Direct native-index lookup; full IDs reject stale generations.
    private SoftBody2D[] softHandles; // Lazy dense deformable handles; null in rigid-only worlds.
    private int softCount; // Live deformable prefix length, sharing the native body capacity.
    private PhysicsJoints2D joints; // Lazily owned native joint lifecycle; null before first joint.
    private final int maxJoints; // Copied maximum native joint count.
    private final int contactEventCapacity; // Copied size of the optional native contact buffer.
    private final boolean threaded; // Whether native contact capture requires a shared lock.
    private PhysicsQueries2D queries; // Lazily owned query resources; null until first query.
    private PhysicsContacts2D contacts; // Lazily owned contact buffer/bridge; null until listener registration.
    private final JoltPhysicsObject[] resources = new JoltPhysicsObject[6]; // Native owners in construction order for reverse disposal.
    private int resourceCount; // Number of native resources successfully registered for cleanup.
    private int bodyCount; // Occupied prefix length of the dense body array.
    private int movingBodyCount; // Live nonstatic bodies eligible for batch pose reads.
    private PhysicsPoses2D poses; // Lazily owned pose buffers; null for small or wholly static worlds.
    private final float fixedTimeStep; // Seconds simulated by each native update.
    private final int maxSubSteps; // Maximum native steps performed for one frame update.
    private final int collisionSteps; // Native collision subdivisions per fixed step.
    private TempAllocator allocator; // Reusable native scratch allocator, assigned during construction.
    private JobSystem jobs; // Native execution scheduler, assigned during construction.
    private PhysicsSystem system; // Owned native simulation; null until construction reaches this stage.
    BodyInterface bodies; // Borrowed native body API, valid only while the world is open.
    final RVec3 positionScratch = new RVec3(); // Reusable position for owner-thread native reads and writes.
    final Quat rotationScratch = new Quat(); // Reusable orientation for owner-thread native reads and writes.
    final Vec3 vectorScratch = new Vec3(); // Reusable vector for forces, torques, and velocity reads.
    private final float metersPerPixel; // Cached reciprocal for pixel-to-world conversion.
    private final float verticalPixelScale; // Signed rendering scale implementing the Y direction.
    private float pixelOriginX; // Render X coordinate of the physics world origin.
    private float pixelOriginY; // Render Y coordinate of the physics world origin.
    private Vector2f geometryPosition; // Lazily allocated scratch shared by all geometry bindings.
    private int geometryCount; // Live geometry bindings; zero skips all render synchronization.
    private double accumulator; // Unsatisfied frame time, normally less than one fixed step.
    private double droppedTime; // Whole-step frame time discarded by the catch-up limit.
    private long stepCount; // Number of successfully completed fixed simulation steps.
    private float gravityX; // Current horizontal gravitational acceleration in meters per second squared.
    private float gravityY; // Current vertical gravitational acceleration in meters per second squared.
    private boolean closed; // Whether the world has relinquished its native resources.
    private boolean failed; // Whether an incomplete native step prohibits further simulation.
    private boolean advancing; // Whether a simulation entry point is active, including callback delivery.
    private boolean capturePreviousActivity; // Whether the next capture must retain the final pose of newly sleeping bodies.

    /**
     * Constructs a world using default timing, capacities, and Y-up gravity.
     *
     * @throws RuntimeException if native setup fails
     * @throws LinkageError if the bundled native library cannot be loaded
     */
    public PhysicsWorld2D() {
        this(new PhysicsWorldSettings2D());
    }

    /**
     * Copies configuration and allocates native simulation resources. Failed
     * construction releases resources already created and preserves the cause.
     *
     * @param settings configuration to copy, not null
     * @throws IllegalArgumentException if contact capacity exceeds the native limit
     * @throws RuntimeException if native setup fails
     * @throws LinkageError if the native library cannot be loaded
     */
    public PhysicsWorld2D(PhysicsWorldSettings2D settings) {
        Objects.requireNonNull(settings, "settings");
        metersPerPixel = 1 / settings.pixelsPerMeter;
        verticalPixelScale = settings.pixelsYDown ? -settings.pixelsPerMeter : settings.pixelsPerMeter;
        pixelOriginX = settings.pixelOriginX;
        pixelOriginY = settings.pixelOriginY;
        handles = new RigidBody2D[settings.maxBodies];
        nativeHandles = new RigidBody2D[settings.maxBodies];
        maxJoints = settings.maxJoints;
        contactEventCapacity = settings.contactEventCapacity;
        threaded = settings.workerThreads != 0;
        fixedTimeStep = settings.fixedTimeStep;
        maxSubSteps = settings.maxSubSteps;
        collisionSteps = settings.collisionSteps;
        gravityX = settings.gravityX;
        gravityY = settings.gravityY;
        JoltRuntime.initialize();
        if (settings.maxContacts > PhysicsSystem.cMaxContactConstraintsLimit())
            throw new IllegalArgumentException("Contact capacity exceeds Jolt's native limit");
        try {
            ObjectLayerPairFilterTable pairs = own(new ObjectLayerPairFilterTable(2));
            pairs.enableCollision(STATIC_LAYER, MOVING_LAYER);
            pairs.enableCollision(MOVING_LAYER, MOVING_LAYER);
            BroadPhaseLayerInterfaceTable mapping = own(new BroadPhaseLayerInterfaceTable(2, 2));
            mapping.mapObjectToBroadPhaseLayer(STATIC_LAYER, STATIC_LAYER);
            mapping.mapObjectToBroadPhaseLayer(MOVING_LAYER, MOVING_LAYER);
            /*
             * Jolt JNI 6.0.0 reverses the layer counts in its native constructor.
             * Both counts are two here, so this setup is correct on either side
             * of that binding fix without version-dependent argument reversal.
             */
            ObjectVsBroadPhaseLayerFilterTable broadphase = own(new ObjectVsBroadPhaseLayerFilterTable(mapping, 2, pairs, 2));
            allocator = own(new TempAllocatorImplWithMallocFallback(settings.scratchBytes));
            jobs = settings.workerThreads == 0
                    ? own(new JobSystemSingleThreaded(Jolt.cMaxPhysicsJobs))
                    : own(new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, settings.workerThreads));
            // The binding's process-wide system registry requires serialized mutation.
            synchronized (JoltRuntime.class) {
                system = own(new PhysicsSystem());
            }
            system.init(settings.maxBodies, 0, settings.maxBodyPairs, settings.maxContacts, mapping, broadphase, pairs);
            system.setGravity(gravityX, gravityY, 0);
            bodies = system.getBodyInterfaceNoLock();
        } catch (RuntimeException | Error failure) {
            try {
                releaseResources();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Registers a native owner in the fixed construction-order cleanup array.
     *
     * @param resource newly constructed native owner
     * @param <T> concrete resource type
     * @return the supplied resource
     */
    private <T extends JoltPhysicsObject> T own(T resource) {
        resources[resourceCount++] = resource;
        return resource;
    }

    /**
     * Requires owner-thread access, including after disposal.
     *
     * @throws IllegalStateException if called from another thread
     */
    void checkOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("PhysicsWorld2D must be accessed on its creating thread");
    }

    /**
     * Requires an open world on its creating thread.
     *
     * @throws IllegalStateException if the caller is foreign or the world is closed
     */
    void check() {
        checkOwner();
        if (closed) throw new IllegalStateException("Physics world is closed");
    }

    /**
     * Reports whether native resources have been relinquished.
     *
     * @return whether close has been called
     */
    public boolean isClosed() {
        checkOwner();
        return closed;
    }

    /**
     * Reports whether an incomplete native update prevents further stepping.
     *
     * @return whether the world must be replaced before simulation can continue
     */
    public boolean isFailed() {
        checkOwner();
        return failed;
    }

    /**
     * Returns the configured fixed-step duration.
     *
     * @return seconds per native step
     */
    public float getFixedTimeStep() {
        checkOwner();
        return fixedTimeStep;
    }

    /**
     * Counts successful native simulation steps, including direct steps.
     *
     * @return completed step count
     */
    public long getStepCount() {
        checkOwner();
        return stepCount;
    }

    /**
     * Reports whole-step time discarded by bounded catch-up.
     *
     * @return discarded elapsed time in seconds
     */
    public double getDroppedTime() {
        checkOwner();
        return droppedTime;
    }

    /**
     * Returns the fractional fixed-step remainder for rendering interpolation.
     * Direct stepping leaves this value unchanged; use current poses with it.
     *
     * @return interpolation fraction in [0, 1), after successful frame updates
     */
    public float getInterpolationAlpha() {
        check();
        return Math.min((float) (accumulator / fixedTimeStep), Math.nextDown(1f));
    }

    /**
     * Returns the number of rigid bodies still owned by this world.
     *
     * @return current live-body count
     */
    public int getBodyCount() {
        check();
        return bodyCount;
    }

    /**
     * Reads a live body from the dense array without allocating a collection.
     * Indices may change when any body is removed and are not persistent IDs.
     *
     * @param index index between zero and the current count minus one
     * @return live body at that index
     * @throws IndexOutOfBoundsException if the index is outside the live prefix
     */
    public RigidBody2D getBody(int index) {
        check();
        Objects.checkIndex(index, bodyCount);
        return handles[index];
    }

    /**
     * Copies gravity into caller-owned storage.
     *
     * @param destination output vector, not null
     * @return the destination containing acceleration in meters per second squared
     */
    public Vector2f getGravity(Vector2f destination) {
        check();
        Objects.requireNonNull(destination, "destination");
        return destination.set(gravityX, gravityY);
    }

    /**
     * Sets gravity for subsequent dynamic simulation. Sleeping bodies stay
     * asleep until explicitly activated or awakened by interaction.
     *
     * @param x horizontal acceleration in meters per second squared
     * @param y vertical acceleration in meters per second squared
     * @return this world
     */
    public PhysicsWorld2D setGravity(float x, float y) {
        check();
        PhysicsValidation2D.finite(x, "gravity x");
        PhysicsValidation2D.finite(y, "gravity y");
        system.setGravity(x, y, 0);
        gravityX = x;
        gravityY = y;
        return this;
    }

    /**
     * Creates and adds a body from a snapshot of the supplied settings. Static
     * velocities are ignored. Native shapes and temporary settings references
     * are released once Jolt retains the body's geometry.
     *
     * @param settings reusable body configuration, not null
     * @return newly owned live body
     * @throws IllegalStateException if the body limit is reached or native creation fails
     */
    public RigidBody2D createBody(BodySettings2D settings) {
        check();
        Objects.requireNonNull(settings, "settings");
        if (bodyCount + softCount == handles.length) throw new IllegalStateException("Physics body capacity reached");
        if (settings.geometry != null) {
            if (settings.geometry.pixelWorld != null && settings.geometry.pixelWorld != this)
                throw new IllegalArgumentException("Geometry settings belong to another world");
            for (int i = 0; i < bodyCount; i++) {
                if (handles[i].geometry != null && handles[i].geometry.shape == settings.geometry.shape)
                    throw new IllegalArgumentException("Geometry is already attached to a live body");
            }
            for (int i = 0; i < softCount; i++) {
                if (softHandles[i].geometry == settings.geometry.shape)
                    throw new IllegalArgumentException("Geometry is already attached to a live soft body");
            }
        }
        int id = Jolt.cInvalidBodyId;
        RigidBody2D body;
        try (com.github.stephengold.joltjni.Shape shape = settings.shape.createNative(); BodyCreationSettings nativeSettings = new BodyCreationSettings()) {
            float halfAngle = settings.angle * 0.5f;
            rotationScratch.set(0, 0, (float) Math.sin(halfAngle), (float) Math.cos(halfAngle));
            boolean moving = settings.motion != MotionType2D.STATIC;
            nativeSettings.setShape(shape)
                    .setPosition(settings.positionInPixels ? worldX(settings.x) : settings.x, settings.positionInPixels ? worldY(settings.y) : settings.y, 0)
                    .setRotation(rotationScratch)
                    .setMotionType(switch (settings.motion) {
                        case STATIC -> EMotionType.Static;
                        case KINEMATIC -> EMotionType.Kinematic;
                        case DYNAMIC -> EMotionType.Dynamic;
                    })
                    .setObjectLayer(moving ? MOVING_LAYER : STATIC_LAYER)
                    .setAllowedDofs(settings.fixedRotation ? EAllowedDofs.TranslationX | EAllowedDofs.TranslationY : EAllowedDofs.Plane2D)
                    .setLinearVelocity(moving ? settings.velocityX : 0, moving ? settings.velocityY : 0, 0)
                    .setAngularVelocity(0, 0, moving && !settings.fixedRotation ? settings.angularVelocity : 0)
                    .setFriction(settings.friction)
                    .setRestitution(settings.restitution)
                    .setLinearDamping(settings.linearDamping)
                    .setAngularDamping(settings.angularDamping)
                    .setGravityFactor(settings.gravityFactor)
                    .setAllowSleeping(settings.sleeping)
                    .setIsSensor(settings.sensor)
                    .setCollideKinematicVsNonDynamic(settings.motion != MotionType2D.STATIC)
                    .setMotionQuality(settings.continuous && !settings.sensor && settings.motion == MotionType2D.DYNAMIC
                            ? EMotionQuality.LinearCast : EMotionQuality.Discrete);
            if (settings.motion == MotionType2D.DYNAMIC) {
                nativeSettings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
                nativeSettings.setInertiaMultiplier(settings.shape.getInertiaMultiplier());
                nativeSettings.getMassPropertiesOverride()
                        .setMass(settings.mass);
            }
            id = bodies.createAndAddBody(nativeSettings, moving ? EActivation.Activate : EActivation.DontActivate);
            if (id == Jolt.cInvalidBodyId) throw new IllegalStateException("Jolt could not allocate a body");
            body = new RigidBody2D(this, id, bodyCount, settings);
            if (body.geometry != null) body.geometry.sync(body, false);
        } catch (RuntimeException | Error failure) {
            if (id != Jolt.cInvalidBodyId) {
                bodies.removeBody(id);
                bodies.destroyBody(id);
            }
            throw failure;
        }
        if (body.geometry != null) geometryCount++;
        handles[bodyCount++] = body;
        nativeHandles[id & BODY_INDEX_MASK] = body;
        capturePreviousActivity = true;
        if (settings.motion != MotionType2D.STATIC) movingBodyCount++;
        if (poses != null) poses.invalidate();
        return body;
    }

    /**
     * Creates a default dynamic body directly from pixel geometry using this world's
     * scale, origin and Y direction. The original shape updates automatically.
     * @param shape supported geometry shape, not null
     * @return newly owned body retaining that shape
     */
    public RigidBody2D createBody(Shape shape) {
        return createBody(bodySettings(shape));
    }

    /**
     * Creates a native deformable mesh, sharing this world's solver and body limit.
     * Only convex geometry is supported. Rigid query and contact-event APIs do not
     * enumerate soft handles; native soft/rigid collision response remains enabled.
     * @param shape supported convex pixel geometry, not null
     * @return newly owned soft body retaining the original perimeter vectors
     */
    public SoftBody2D createSoftBody(Shape shape) {
        check();
        Objects.requireNonNull(shape, "shape");
        if (bodyCount + softCount == handles.length) throw new IllegalStateException("Physics body capacity reached");
        for (int i = 0; i < bodyCount; i++) {
            if (handles[i].geometry != null && handles[i].geometry.shape == shape)
                throw new IllegalArgumentException("Geometry is already attached to a rigid body");
        }
        for (int i = 0; i < softCount; i++) {
            if (softHandles[i].geometry == shape) throw new IllegalArgumentException("Geometry is already attached to a soft body");
        }
        if (softHandles == null) softHandles = new SoftBody2D[Math.min(8, handles.length)];
        else if (softCount == softHandles.length) softHandles = Arrays.copyOf(softHandles, Math.min(handles.length, softHandles.length * 2));
        SoftBody2D body = new SoftBody2D(this, shape, softCount);
        softHandles[softCount++] = body;
        return body;
    }

    /**
     * Reports native deformable bodies; rigid bodies are counted separately.
     * @return occupied soft-body prefix length
     */
    public int getSoftBodyCount() {
        check();
        return softCount;
    }

    /**
     * Returns a live deformable handle without creating a collection.
     * @param index index between zero and the soft-body count minus one
     * @return live soft body; indices may change after removals
     */
    public SoftBody2D getSoftBody(int index) {
        check();
        Objects.checkIndex(index, softCount);
        return softHandles[index];
    }

    /**
     * Removes a deformable mesh and its direct capture buffer. Repeated removal
     * is harmless; foreign-world handles are rejected before native access.
     * @param body soft handle owned by this world, not null
     */
    public void destroyBody(SoftBody2D body) {
        checkOwner();
        Objects.requireNonNull(body, "body");
        if (body.world != this) throw new IllegalArgumentException("Soft body belongs to another world");
        if (body.destroyed) return;
        check();
        bodies.removeBody(body.id);
        bodies.destroyBody(body.id);
        int last = --softCount;
        if (body.index != last) {
            SoftBody2D moved = softHandles[last];
            softHandles[body.index] = moved;
            moved.index = body.index;
        }
        softHandles[last] = null;
        body.release();
    }

    /**
     * Refreshes pose batching when a body crosses the static/moving boundary.
     * @param previous old native motion type
     * @param current new native motion type
     */
    void motionChanged(MotionType2D previous, MotionType2D current) {
        if (previous == MotionType2D.STATIC) movingBodyCount++;
        if (current == MotionType2D.STATIC) movingBodyCount--;
        capturePreviousActivity = true;
        if (poses != null) poses.invalidate();
    }

    /**
     * Creates configurable geometry-backed settings with this world's pixel mapping.
     * Configure optional mass, motion or material properties before createBody(settings).
     * @param shape supported pixel geometry shape, not null
     * @return new settings retaining the geometry and shared world mapping
     */
    public BodySettings2D bodySettings(Shape shape) {
        check();
        return new BodySettings2D(shape, this);
    }

    /**
     * Updates the screen location of the world origin for camera movement.
     * Geometry reflects the change on the next update, including update(0).
     * @param x finite render X origin
     * @param y finite render Y origin
     * @return this world
     */
    public PhysicsWorld2D pixelOrigin(float x, float y) {
        check();
        PhysicsValidation2D.finite(x, "pixel origin x");
        PhysicsValidation2D.finite(y, "pixel origin y");
        pixelOriginX = x;
        pixelOriginY = y;
        return this;
    }

    /**
     * Converts a length or signed displacement without applying a camera origin.
     * @param pixels finite pixel magnitude
     * @return equivalent meters
     */
    public float toMeters(float pixels) {
        check();
        PhysicsValidation2D.finite(pixels, "pixels");
        float meters = pixels * metersPerPixel;
        PhysicsValidation2D.finite(meters, "converted meters");
        return meters;
    }

    /**
     * Converts a length or signed displacement without applying a camera origin.
     * @param meters finite world magnitude
     * @return equivalent pixels
     */
    public float toPixels(float meters) {
        check();
        PhysicsValidation2D.finite(meters, "meters");
        float pixels = meters * Math.abs(verticalPixelScale);
        PhysicsValidation2D.finite(pixels, "converted pixels");
        return pixels;
    }

    /**
     * Converts a render position into world meters, supporting aliased vectors.
     * @param x finite horizontal pixel coordinate
     * @param y finite vertical pixel coordinate
     * @param destination reusable output vector, not null
     * @return the supplied vector containing world meters
     */
    public Vector2f toWorld(float x, float y, Vector2f destination) {
        check();
        Objects.requireNonNull(destination, "destination");
        return destination.set(worldX(x), worldY(y));
    }

    /**
     * Converts a world position into pixels, supporting aliased vectors.
     * @param x finite horizontal world coordinate in meters
     * @param y finite vertical world coordinate in meters
     * @param destination reusable output vector, not null
     * @return the supplied vector containing render pixels
     */
    public Vector2f toScreen(float x, float y, Vector2f destination) {
        check();
        Objects.requireNonNull(destination, "destination");
        PhysicsValidation2D.finite(x, "world x");
        PhysicsValidation2D.finite(y, "world y");
        float screenX = (float) ((double) pixelOriginX + (double) x * Math.abs(verticalPixelScale));
        float screenY = (float) ((double) pixelOriginY + (double) y * verticalPixelScale);
        PhysicsValidation2D.finite(screenX, "screen x");
        PhysicsValidation2D.finite(screenY, "screen y");
        return destination.set(screenX, screenY);
    }

    /**
     * Converts horizontal render position into world meters.
     * @param pixels finite render X coordinate
     * @return physics X coordinate
     */
    float worldX(float pixels) {
        PhysicsValidation2D.finite(pixels, "pixel x");
        float worldX = (float) (((double) pixels - pixelOriginX) * metersPerPixel);
        PhysicsValidation2D.finite(worldX, "converted world x");
        return worldX;
    }

    /**
     * Converts vertical render position into world meters.
     * @param pixels finite render Y coordinate
     * @return physics Y coordinate
     */
    float worldY(float pixels) {
        PhysicsValidation2D.finite(pixels, "pixel y");
        float worldY = (float) (((double) pixels - pixelOriginY) * (verticalPixelScale < 0 ? -metersPerPixel : metersPerPixel));
        PhysicsValidation2D.finite(worldY, "converted world y");
        return worldY;
    }

    /**
     * Describes a rectangle using full pixel dimensions, independent of origin.
     * @param width full pixel width
     * @param height full pixel height
     * @return reusable collision descriptor in world meters
     */
    public CollisionShape2D boxPixels(float width, float height) {
        return CollisionShape2D.box(toMeters(width), toMeters(height));
    }

    /**
     * Describes a circle using its pixel radius, independent of origin.
     * @param radius pixel radius
     * @return reusable collision descriptor in world meters
     */
    public CollisionShape2D circlePixels(float radius) {
        return CollisionShape2D.circle(toMeters(radius));
    }

    /**
     * Converts geometry degrees into world radians, including the configured Y reflection.
     * @param degrees finite geometry orientation
     * @return counterclockwise world radians
     */
    float toWorldRotation(float degrees) {
        check();
        PhysicsValidation2D.finite(degrees, "degrees");
        return (float) Math.toRadians(degrees) * (verticalPixelScale < 0 ? -1 : 1);
    }

    /**
     * Converts world radians into geometry degrees, including the configured Y reflection.
     * @param radians finite world orientation
     * @return geometry orientation in degrees
     */
    float toGeometryRotation(float radians) {
        check();
        PhysicsValidation2D.finite(radians, "radians");
        float degrees = (float) Math.toDegrees(radians);
        PhysicsValidation2D.finite(degrees, "converted degrees");
        return degrees * (verticalPixelScale < 0 ? -1 : 1);
    }

    /**
     * Resolves a live native ID without boxing or collection lookup. Native
     * callback workers may read this array while its owner is inside update;
     * that owner never mutates body membership until update returns.
     *
     * @param id complete native body ID, including its generation
     * @return matching live handle, or null if it has been removed or replaced
     */
    RigidBody2D findBody(int id) {
        int index = id & BODY_INDEX_MASK;
        if (index >= nativeHandles.length) return null;
        RigidBody2D body = nativeHandles[index];
        return body != null && body.id == id ? body : null;
    }

    /**
     * Creates optional query resources on first demand.
     *
     * @return owned spatial-query subsystem
     */
    private PhysicsQueries2D queries() {
        if (queries == null) queries = new PhysicsQueries2D(this);
        return queries;
    }

    /**
     * Registers a contact callback once by identity. Native events are buffered
     * and delivered after each fixed step on the owner thread. Listener membership
     * is snapshotted per dispatch; changes inside a callback affect later steps.
     * Subscribing does not reset native contacts, so existing pairs may first
     * report PERSIST. The optional buffer/bridge is allocated on first subscription.
     *
     * @param listener nonnull callback receiving a borrowed reusable event view
     */
    public void addContactListener(PhysicsContactListener2D listener) {
        check();
        Objects.requireNonNull(listener, "listener");
        if (contacts == null) {
            PhysicsContacts2D created = new PhysicsContacts2D(this, contactEventCapacity, threaded);
            try {
                system.setContactListener(created.bridge());
                contacts = created;
            } catch (RuntimeException | Error failure) {
                try {
                    created.close();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }
        contacts.add(listener);
    }

    /**
     * Unregisters a callback by identity. An absent listener is harmless, and a
     * change made during delivery takes effect after that step's listener snapshot.
     * With no listeners, native callbacks skip borrowed-data copying entirely.
     *
     * @param listener nonnull callback to remove
     */
    public void removeContactListener(PhysicsContactListener2D listener) {
        check();
        Objects.requireNonNull(listener, "listener");
        if (contacts != null) contacts.remove(listener);
    }

    /**
     * Casts a segment through native collision shapes at Z=0 and returns the
     * closest body, including sensors. Starting inside a convex body reports
     * fraction zero. A zero-length segment is a miss; use queryPoint for picking.
     * Each cast closes the two temporary native ray/result objects required by
     * Jolt JNI 6.0.0; filters and caller result storage are reused.
     *
     * @param startX start horizontal coordinate in meters
     * @param startY start vertical coordinate in meters
     * @param endX end horizontal coordinate in meters
     * @param endY end vertical coordinate in meters
     * @param destination reusable result, cleared on a valid miss
     * @return whether any body was hit
     * @throws IllegalArgumentException if coordinates or segment displacement are nonfinite
     */
    public boolean raycast(float startX, float startY, float endX, float endY, PhysicsRayHit2D destination) {
        check();
        Objects.requireNonNull(destination, "destination");
        PhysicsValidation2D.finite(startX, "start x");
        PhysicsValidation2D.finite(startY, "start y");
        PhysicsValidation2D.finite(endX, "end x");
        PhysicsValidation2D.finite(endY, "end y");
        float dx = (float) ((double) endX - startX);
        float dy = (float) ((double) endY - startY);
        PhysicsValidation2D.finite(dx, "ray displacement x");
        PhysicsValidation2D.finite(dy, "ray displacement y");
        return queries()
                .raycast(startX, startY, dx, dy, destination);
    }

    /**
     * Collects exact supported-shape containment hits after native broadphase
     * pruning. Bodies include sensors and are in unspecified order. Writes only
     * the first destination.length hits; returns the total count so truncation
     * is explicit. Unused destination slots retain their previous contents.
     * Steady calls allocate no Java objects after query-resource initialization.
     *
     * @param x horizontal world point in meters
     * @param y vertical world point in meters
     * @param destination caller-owned output, possibly empty to count hits
     * @return total hits, possibly greater than output capacity
     */
    public int queryPoint(float x, float y, RigidBody2D[] destination) {
        check();
        Objects.requireNonNull(destination, "destination");
        PhysicsValidation2D.finite(x, "point x");
        PhysicsValidation2D.finite(y, "point y");
        return queries()
                .point(x, y, destination);
    }

    /**
     * Collects conservative native AABB candidates for a planar rectangle.
     * Rotated shapes and circles may produce candidates outside their exact
     * outline. Includes sensors; order and unused output slots follow queryPoint.
     * Returns the total count even when only a prefix fits the destination.
     *
     * @param minX minimum horizontal world coordinate in meters
     * @param minY minimum vertical world coordinate in meters
     * @param maxX maximum horizontal world coordinate, at least minX
     * @param maxY maximum vertical world coordinate, at least minY
     * @param destination caller-owned output array
     * @return total native bounds candidates, possibly exceeding output capacity
     * @throws IllegalArgumentException if bounds are nonfinite or reversed
     */
    public int queryBounds(float minX, float minY, float maxX, float maxY, RigidBody2D[] destination) {
        check();
        Objects.requireNonNull(destination, "destination");
        PhysicsValidation2D.finite(minX, "minimum x");
        PhysicsValidation2D.finite(minY, "minimum y");
        PhysicsValidation2D.finite(maxX, "maximum x");
        PhysicsValidation2D.finite(maxY, "maximum y");
        if (maxX < minX || maxY < minY) throw new IllegalArgumentException("Query bounds must be ordered");
        return queries()
                .bounds(minX, minY, maxX, maxY, destination);
    }

    /**
     * Creates the owned joint subsystem on first demand.
     *
     * @return owned constraint lifecycle
     */
    private PhysicsJoints2D joints() {
        if (joints == null) joints = new PhysicsJoints2D(this, system, maxJoints);
        return joints;
    }

    /**
     * Creates a distance relationship between world-space anchors at Z=0.
     * Equal limits impose fixed length; minimum zero allows a rope-like limit.
     * Connected bodies still collide normally.
     *
     * @param first first live body owned by this world
     * @param second second live body owned by this world
     * @param anchorFirstX first anchor's world X in meters
     * @param anchorFirstY first anchor's world Y in meters
     * @param anchorSecondX second anchor's world X in meters
     * @param anchorSecondY second anchor's world Y in meters
     * @param minimum finite nonnegative minimum anchor distance
     * @param maximum finite positive maximum anchor distance, at least minimum
     * @return newly owned joint
     * @throws IllegalArgumentException if ownership, anchors, limits or motion pairing are invalid
     * @throws IllegalStateException if a body is destroyed or joint capacity is reached
     */
    public PhysicsJoint2D createDistanceJoint(RigidBody2D first, RigidBody2D second, float anchorFirstX, float anchorFirstY, float anchorSecondX, float anchorSecondY, float minimum, float maximum) {
        check();
        return joints()
                .createDistanceJoint(first, second, anchorFirstX, anchorFirstY, anchorSecondX, anchorSecondY, minimum, maximum);
    }

    /**
     * Creates a pivot relationship at a shared world anchor. Native point
     * constraints enforce coincidence while the bodies' plane locks leave only
     * rotation around Z free, providing a planar pin joint.
     *
     * @param first first live owned body
     * @param second second live owned body
     * @param anchorX shared anchor's horizontal world coordinate in meters
     * @param anchorY shared anchor's vertical world coordinate in meters
     * @return newly owned pivot joint
     * @throws IllegalArgumentException if ownership, anchors or motion pairing are invalid
     */
    public PhysicsJoint2D createPivotJoint(RigidBody2D first, RigidBody2D second, float anchorX, float anchorY) {
        check();
        return joints()
                .createPivotJoint(first, second, anchorX, anchorY);
    }

    /**
     * Returns the number of still-owned native joints.
     *
     * @return current live-joint count
     */
    public int getJointCount() {
        check();
        return joints == null ? 0 : joints.getCount();
    }

    /**
     * Removes a joint and relinquishes its native reference. Repeated removal
     * is harmless, including after its world closes.
     *
     * @param joint handle belonging to this world
     * @throws IllegalArgumentException if the joint belongs to another world
     */
    public void destroyJoint(PhysicsJoint2D joint) {
        checkOwner();
        Objects.requireNonNull(joint, "joint");
        if (joint.world != this) throw new IllegalArgumentException("Joint belongs to another world");
        if (joint.destroyed) return;
        check();
        joints.destroy(joint);
    }

    /**
     * Destroys an owned body and invalidates its handle. Repeated destruction
     * of that handle is harmless, including after the world closes. Attached
     * joints are removed first. Native contact END events may refer to this
     * original, destroyed handle during the following fixed step.
     *
     * @param body handle owned by this world, not null
     * @throws IllegalArgumentException if the handle belongs to another world
     */
    public void destroyBody(RigidBody2D body) {
        checkOwner();
        Objects.requireNonNull(body, "body");
        if (body.world != this) throw new IllegalArgumentException("Body belongs to another world");
        if (body.destroyed) return;
        check();
        if (joints != null) joints.destroyAttached(body);
        if (contacts != null) contacts.retire(body);
        bodies.removeBody(body.id);
        bodies.destroyBody(body.id);
        int last = --bodyCount;
        if (body.getMotionType() != MotionType2D.STATIC) movingBodyCount--;
        if (body.index != last) {
            RigidBody2D moved = handles[last];
            handles[body.index] = moved;
            moved.index = body.index;
        }
        handles[last] = null;
        nativeHandles[body.id & BODY_INDEX_MASK] = null;
        if (body.geometry != null) geometryCount--;
        body.destroyed = true;
        if (poses != null) {
            if (movingBodyCount < PhysicsPoses2D.MIN_BODIES) {
                poses.close();
                poses = null;
            } else {
                poses.invalidate();
            }
        }
    }

    /**
     * Removes all joints and bodies and resets interpolation backlog.
     * Successful-step and dropped-time counters remain cumulative; a failed
     * world stays failed. Cached contact END events may arrive in the next step.
     *
     * @throws IllegalStateException if called while stepping or delivering contacts
     */
    public void clear() {
        check();
        if (advancing) throw new IllegalStateException("Cannot clear the world while stepping or delivering callbacks");
        if (joints != null) joints.close();
        while (bodyCount != 0) destroyBody(handles[bodyCount - 1]);
        while (softCount != 0) destroyBody(softHandles[softCount - 1]);
        if (poses != null) {
            poses.close();
            poses = null;
        }
        accumulator = 0;
    }

    /**
     * Adds elapsed frame time and simulates bounded fixed steps. Excess whole
     * steps are discarded while the fractional remainder is preserved. Zero
     * elapsed time performs no native step when there is no complete step pending.
     * Attached geometry receives the interpolated render pose, even when no step
     * is needed, allowing camera-origin changes to take effect immediately.
     * Contact delivery follows each completed step and sees current geometry.
     * A thrown application callback commits that step's time, discards its remaining events and retains any
     * unprocessed frame backlog for a subsequent call.
     *
     * @param elapsedSeconds finite, nonnegative elapsed frame time in seconds
     * @return number of completed native steps
     * @throws IllegalStateException if stepping is recursive, the world is failed or capture/solver capacity is exhausted
     */
    public int update(float elapsedSeconds) {
        check();
        PhysicsValidation2D.nonnegative(elapsedSeconds, "elapsed time");
        if (advancing) throw new IllegalStateException("Recursive physics stepping is not allowed");
        if (failed) throw new IllegalStateException("Physics world failed; create a replacement before stepping");
        advancing = true;
        try {
            accumulator += elapsedSeconds;
            int completed = 0;
            while (accumulator >= fixedTimeStep && completed < maxSubSteps) {
                simulate();
                // Commit time before user code so a thrown callback cannot replay it.
                accumulator -= fixedTimeStep;
                completed++;
                if (contacts != null) {
                    syncGeometry(false);
                    contacts.dispatch();
                }
            }
            if (accumulator >= fixedTimeStep) {
                double remainder = accumulator % fixedTimeStep;
                droppedTime += accumulator - remainder;
                accumulator = remainder;
            }
            syncGeometry(true);
            return completed;
        } finally {
            advancing = false;
        }
    }

    /**
     * Performs exactly one configured step, independently of frame accumulation.
     * Apply sustained forces before each call and render current body poses.
     * Contact callbacks run after committing native state and the step count.
     * A callback exception discards undelivered events without failing the world.
     *
     * @throws IllegalStateException if stepping is recursive, a prior native step failed or capacity is exhausted
     */
    public void step() {
        check();
        if (advancing) throw new IllegalStateException("Recursive physics stepping is not allowed");
        if (failed) throw new IllegalStateException("Physics world failed; create a replacement before stepping");
        advancing = true;
        try {
            simulate();
            syncGeometry(false);
            if (contacts != null) contacts.dispatch();
        } finally {
            advancing = false;
        }
    }

    /**
     * Provides owner-thread scratch only when geometry integration is used.
     * @return shared position storage, never retained by a binding
     */
    Vector2f geometryPosition() {
        check();
        if (geometryPosition == null) geometryPosition = new Vector2f();
        return geometryPosition;
    }

    /**
     * Updates original render shapes using shared owner-thread scratch storage. Worlds with
     * no geometry bindings avoid the traversal entirely.
     * @param interpolated whether to apply fractional render interpolation
     */
    private void syncGeometry(boolean interpolated) {
        for (int i = 0; i < softCount; i++) softHandles[i].sync(interpolated);
        if (geometryCount == 0) return;
        for (int i = 0; i < bodyCount; i++) {
            RigidBody2D body = handles[i];
            if (body.geometry != null) body.geometry.sync(body, interpolated);
        }
    }

    /**
     * Commits one native step and captures interpolation poses. A failed native
     * update is never retried because Jolt may have partially advanced bodies.
     */
    private void simulate() {
        if (contacts != null) contacts.beginStep();
        try {
            int errors = system.update(fixedTimeStep, collisionSteps, allocator, jobs);
            for (int i = 0; i < softCount; i++) softHandles[i].capture();
            if (contacts != null) contacts.checkCapture();
            boolean active = movingBodyCount != 0 && system.getNumActiveBodies(EBodyType.RigidBody) != 0;
            boolean capture = active || capturePreviousActivity;
            if (capture && movingBodyCount >= PhysicsPoses2D.MIN_BODIES) {
                if (poses == null) poses = new PhysicsPoses2D((BatchBodyInterface) bodies);
                poses.capture(handles, bodyCount, movingBodyCount);
            } else if (movingBodyCount != 0) {
                for (int i = 0; i < bodyCount; i++) handles[i].captureStep(capture);
            }
            capturePreviousActivity = active;
            if (errors != 0)
                throw new IllegalStateException("Jolt simulation capacity exhausted (error flags " + errors + ")");
        } catch (RuntimeException | Error failure) {
            failed = true;
            if (contacts != null) contacts.discard();
            throw failure;
        }
        stepCount++;
    }

    /**
     * Optimizes the native broadphase after bulk scene creation. Normal stepping
     * maintains it incrementally, so this is not required every frame.
     */
    public void optimizeBroadPhase() {
        check();
        system.optimizeBroadPhase();
    }

    @Override
    public void close() {
        checkOwner();
        if (closed) return;
        if (advancing) throw new IllegalStateException("Cannot close the world while stepping or delivering callbacks");
        Throwable failure = null;
        try {
            clear();
        } catch (RuntimeException | Error cleanupFailure) {
            failure = cleanupFailure;
        }
        closed = true;
        while (softCount != 0) {
            try {
                softHandles[--softCount].release();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
            softHandles[softCount] = null;
        }
        // Invalidate remaining handles even if a native removal failed.
        while (bodyCount != 0) {
            RigidBody2D body = handles[--bodyCount];
            if (body.geometry != null) geometryCount--;
            body.destroyed = true;
            nativeHandles[body.id & BODY_INDEX_MASK] = null;
            handles[bodyCount] = null;
        }
        try {
            releaseResources();
        } catch (RuntimeException | Error cleanupFailure) {
            if (failure == null) failure = cleanupFailure;
            else failure.addSuppressed(cleanupFailure);
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }

    /**
     * Removes the binding's global system reference and releases every native
     * owner in reverse order. Cleanup continues after a failure and reports
     * additional failures as suppressed causes.
     */
    private void releaseResources() {
        Throwable failure = null;
        if (poses != null) {
            try {
                poses.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure = cleanupFailure;
            }
            poses = null;
        }
        if (joints != null) {
            try {
                joints.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
            joints = null;
        }
        if (contacts != null) {
            try {
                system.setContactListener(null);
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
            try {
                contacts.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
            contacts = null;
        }
        if (queries != null) {
            try {
                queries.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
            queries = null;
        }
        if (system != null) {
            try {
                synchronized (JoltRuntime.class) {
                    system.forgetMe();
                }
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
        }
        while (resourceCount != 0) {
            JoltPhysicsObject resource = resources[--resourceCount];
            resources[resourceCount] = null;
            try {
                resource.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
}
