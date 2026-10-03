package valthorne.portable;

/**
 * Interactive extension of the scene rendering and physics boundary. Coordinates
 * and distances use meters, Y is up, and packed colors use 0xRRGGBB. Scene-owned
 * bodies remain borrowed until removed or the scene is cleared or closed.
 */
public interface InteractiveScene extends SceneBackend {
    /**
     * Creates a scene-owned rigid box and returns a borrowed removal handle.
     *
     * @param x center X in meters
     * @param y center Y in meters
     * @param z center Z in meters
     * @param hx horizontal half extent in meters
     * @param hy vertical half extent in meters
     * @param hz depth half extent in meters
     * @param rgb packed RGB color
     * @param dynamic whether physics moves the body
     * @param lockRotation whether angular movement is prohibited
     * @return borrowed handle for the scene-owned body
     */
    PhysicsBody rigidBox(float x, float y, float z, float hx, float hy, float hz, int rgb, boolean dynamic, boolean lockRotation);

    /**
     * Configures the scene camera in world coordinates.
     *
     * @param x camera X in meters
     * @param y camera Y in meters
     * @param z camera Z in meters
     * @param yaw horizontal rotation in radians
     * @param pitch vertical rotation in radians
     * @param verticalFov vertical field of view in radians
     */
    void camera(float x, float y, float z, float yaw, float pitch, float verticalFov);

    /**
     * Returns the closest physics hit distance along a normalized direction.
     *
     * @param x ray origin X
     * @param y ray origin Y
     * @param z ray origin Z
     * @param dx normalized direction X
     * @param dy normalized direction Y
     * @param dz normalized direction Z
     * @param range maximum distance in meters
     * @param ignored body to exclude, or null
     * @return closest distance in meters, or minus one when no hit exists
     */
    float rayDistance(float x, float y, float z, float dx, float dy, float dz, float range, PhysicsBody ignored);

    /**
     * Finds the closest physics body along a normalized ray.
     *
     * @param x ray origin X
     * @param y ray origin Y
     * @param z ray origin Z
     * @param dx normalized direction X
     * @param dy normalized direction Y
     * @param dz normalized direction Z
     * @param range maximum distance in meters
     * @param ignored body to exclude, or null
     * @return borrowed hit information, or null when no hit exists
     */
    RayHit castRay(float x, float y, float z, float dx, float dy, float dz, float range, PhysicsBody ignored);

    /**
     * Emits scene-owned particles under the implementation's resource budget.
     *
     * @param x emission X in meters
     * @param y emission Y in meters
     * @param z emission Z in meters
     * @param rgb packed RGB color
     * @param count requested particle count
     * @param physics whether emitted particles participate in physics
     * @param lights whether emitted particles contribute lights
     */
    void particles(float x, float y, float z, int rgb, int count, boolean physics, boolean lights);
}
