package valthorne.math.physics;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.BodyFilter;
import com.github.stephengold.joltjni.BroadPhaseLayerFilter;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.ObjectLayerFilter;
import com.github.stephengold.joltjni.RRayCast;
import com.github.stephengold.joltjni.RayCastResult;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.readonly.ConstBroadPhaseQuery;
import com.github.stephengold.joltjni.readonly.ConstNarrowPhaseQuery;

/**
 * Lazily created, world-owned spatial-query resources. Reuses native filters,
 * a bounds box, and a primitive-ID collector, avoiding implicit filter creation
 * by Jolt JNI's convenience overloads. Point and bounds queries allocate no
 * steady Java objects; each ray cast owns and promptly closes the two temporary
 * native objects required by this binding's immutable ray/result API.
 * All queries run synchronously on the world's owner thread after simulation.
 */
final class PhysicsQueries2D implements AutoCloseable {
    private final PhysicsWorld2D world; // Owning simulation and shared position/direction scratch values.
    private final ConstBroadPhaseQuery broadphase; // Borrowed conservative native query interface.
    private final ConstNarrowPhaseQuery narrowphase; // Borrowed precise native query interface.
    private final JoltPhysicsObject[] resources = new JoltPhysicsObject[6]; // Owned query resources in construction order.
    private int resourceCount; // Number of resources that must be released.
    private BroadPhaseLayerFilter broadphaseFilter; // Native filter permitting both motion partitions.
    private ObjectLayerFilter objectFilter; // Native filter permitting both object layers.
    private BodyFilter bodyFilter; // Native filter permitting all live bodies, including sensors.
    private PhysicsRigidBodyFilter2D rigidFilter; // Lazy mixed-world ray filter excluding soft meshes.
    private AaBox bounds; // Reusable query box with Z spanning the simulation plane.
    private PhysicsQueryCollector2D collector; // Reusable callback storing primitive-ID hits.
    private final Vec3 boundScratch = new Vec3(); // Heap-only scratch for native bounds updates.

    /**
     * Creates query resources and rolls back partial allocation on failure.
     *
     * @param world live owning world
     */
    PhysicsQueries2D(PhysicsWorld2D world) {
        this.world = world;
        broadphase = world.bodies.getSystem()
                .getBroadPhaseQuery();
        narrowphase = world.bodies.getSystem()
                .getNarrowPhaseQueryNoLock();
        try {
            broadphaseFilter = own(new BroadPhaseLayerFilter());
            objectFilter = own(new ObjectLayerFilter());
            bodyFilter = own(new BodyFilter());
            bounds = own(new AaBox());
            collector = own(new PhysicsQueryCollector2D(world));
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Registers one native resource for reverse-order cleanup.
     *
     * @param resource newly owned native object
     * @param <T> concrete native object type
     * @return the supplied resource
     */
    private <T extends JoltPhysicsObject> T own(T resource) {
        resources[resourceCount++] = resource;
        return resource;
    }

    /**
     * Casts a planar segment and copies its closest native hit.
     *
     * @param x start X in meters
     * @param y start Y in meters
     * @param dx finite horizontal segment displacement
     * @param dy finite vertical segment displacement
     * @param destination reusable hit destination
     * @return whether the segment hit a body
     */
    boolean raycast(float x, float y, float dx, float dy, PhysicsRayHit2D destination) {
        destination.set(null, 0, 0, 0);
        if (dx == 0 && dy == 0) return false;
        BodyFilter filter = bodyFilter;
        if (world.getSoftBodyCount() != 0) {
            if (rigidFilter == null) rigidFilter = own(new PhysicsRigidBodyFilter2D(world));
            filter = rigidFilter;
        }
        world.positionScratch.set(x, y, 0);
        world.vectorScratch.set(dx, dy, 0);
        try (RRayCast ray = new RRayCast(world.positionScratch, world.vectorScratch); RayCastResult result = new RayCastResult()) {
            if (!narrowphase.castRay(ray, result, broadphaseFilter, objectFilter, filter)) return false;
            RigidBody2D body = world.findBody(result.getBodyId());
            if (body == null) throw new IllegalStateException("Ray hit an unowned native body");
            float fraction = result.getFraction();
            destination.set(body, x + dx * fraction, y + dy * fraction, fraction);
            return true;
        }
    }

    /**
     * Collects exact planar point hits after native broadphase pruning.
     *
     * @param x horizontal world point
     * @param y vertical world point
     * @param destination caller-owned output array
     * @return total hit count, potentially greater than output capacity
     */
    int point(float x, float y, RigidBody2D[] destination) {
        collector.begin(destination, true, x, y);
        world.vectorScratch.set(x, y, 0);
        try {
            broadphase.collidePoint(world.vectorScratch, collector, broadphaseFilter, objectFilter);
            return collector.getCount();
        } finally {
            collector.finish();
        }
    }

    /**
     * Collects conservative native bounds overlaps on the simulation plane.
     *
     * @param minX minimum horizontal world coordinate
     * @param minY minimum vertical world coordinate
     * @param maxX maximum horizontal world coordinate
     * @param maxY maximum vertical world coordinate
     * @param destination caller-owned output array
     * @return total candidate count, potentially greater than output capacity
     */
    int bounds(float minX, float minY, float maxX, float maxY, RigidBody2D[] destination) {
        boundScratch.set(minX, minY, -1);
        bounds.setMin(boundScratch);
        boundScratch.set(maxX, maxY, 1);
        bounds.setMax(boundScratch);
        collector.begin(destination, false, 0, 0);
        try {
            broadphase.collideAaBox(bounds, collector, broadphaseFilter, objectFilter);
            return collector.getCount();
        } finally {
            collector.finish();
        }
    }

    @Override
    public void close() {
        Throwable failure = null;
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
