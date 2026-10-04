package valthorne.math.physics;

import com.github.stephengold.joltjni.CustomCollideShapeBodyCollector;

/**
 * Reusable owner-thread bridge from Jolt broadphase body IDs into caller-owned
 * body arrays. Bounds queries retain conservative candidates; point queries
 * reject candidates outside the body's exact supported planar geometry.
 * Destination exhaustion truncates writes while preserving the total count.
 */
final class PhysicsQueryCollector2D extends CustomCollideShapeBodyCollector {
    private final PhysicsWorld2D world; // World used to resolve generation-checked native IDs.
    private RigidBody2D[] destination; // Borrowed output array, null outside a query.
    private boolean pointQuery; // Whether candidates require exact planar containment.
    private float x; // Horizontal world point for containment queries.
    private float y; // Vertical world point for containment queries.
    private int count; // Total accepted bodies, including hits beyond output capacity.

    /**
     * Allocates the native collector for one world's query subsystem.
     *
     * @param world owning live simulation
     */
    PhysicsQueryCollector2D(PhysicsWorld2D world) {
        this.world = world;
    }

    /**
     * Configures one synchronous query without allocating a callback closure.
     *
     * @param destination caller-owned output array
     * @param pointQuery whether to apply exact containment
     * @param x horizontal world point
     * @param y vertical world point
     */
    void begin(RigidBody2D[] destination, boolean pointQuery, float x, float y) {
        this.destination = destination;
        this.pointQuery = pointQuery;
        this.x = x;
        this.y = y;
        count = 0;
    }

    /**
     * Releases the borrowed destination so idle queries cannot retain game state.
     */
    void finish() {
        destination = null;
    }

    /**
     * Reports accepted candidates, including hits that did not fit the output.
     *
     * @return total accepted hit count
     */
    int getCount() {
        return count;
    }

    @Override
    public void addHit(int id) {
        RigidBody2D body = world.findBody(id);
        if (body == null || pointQuery && !body.containsPoint(x, y)) return;
        if (count < destination.length) destination[count] = body;
        count++;
    }
}
