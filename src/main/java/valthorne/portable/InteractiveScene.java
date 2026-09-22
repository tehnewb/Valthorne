package valthorne.portable;

/**
 * Extended scene boundary for interactive games.
 */
public interface InteractiveScene extends SceneBackend {
    /**
     * A borrowed body handle: closing it removes the hit body.
     */
    record RayHit(PhysicsBody body, float distance) {
    }

    PhysicsBody rigidBox(float x, float y, float z, float hx, float hy, float hz, int rgb, boolean dynamic, boolean lockRotation);

    void camera(float x, float y, float z, float yaw, float pitch, float verticalFov);

    /**
     * Returns distance to the closest Jolt hit, or -1. Direction must have unit length.
     */
    float rayDistance(float x, float y, float z, float dx, float dy, float dz, float range, PhysicsBody ignored);

    RayHit castRay(float x, float y, float z, float dx, float dy, float dz, float range, PhysicsBody ignored);

    void particles(float x, float y, float z, int rgb, int count, boolean physics, boolean lights);
}
