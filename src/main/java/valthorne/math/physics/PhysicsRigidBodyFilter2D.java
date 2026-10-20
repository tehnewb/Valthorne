package valthorne.math.physics;

import com.github.stephengold.joltjni.BodyFilter;

/**
 * Native ray-query filter restricting a mixed simulation to live rigid handles.
 * Created lazily only when a ray query first encounters a world with soft bodies.
 * Its generation-aware lookup avoids masking a rigid hit behind a soft mesh.
 */
final class PhysicsRigidBodyFilter2D extends BodyFilter {
    private final PhysicsWorld2D world; // World whose live rigid handles are eligible for ray hits.

    /**
     * Creates the native callback on the world's owner thread.
     * @param world owning simulation
     */
    PhysicsRigidBodyFilter2D(PhysicsWorld2D world) {
        this.world = world;
    }

    @Override
    public boolean shouldCollide(int bodyId) {
        return world.findBody(bodyId) != null;
    }
}
