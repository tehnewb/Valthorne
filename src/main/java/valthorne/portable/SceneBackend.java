package valthorne.portable;

/**
 * Small portable 3D platform boundary. Coordinates are meters, Y is up, colors are
 * 0xRRGGBB. Implementations own their graphics and physics objects until clear/close.
 * This initial interface does not replace the desktop Scene3D or PhysicsWorld3D APIs.
 */
public interface SceneBackend extends AutoCloseable {
    default void box(float x, float y, float z, float halfSize, int rgb, boolean dynamic) {
        box(x, y, z, halfSize, halfSize, halfSize, rgb, dynamic);
    }

    void box(float x, float y, float z, float halfX, float halfY, float halfZ, int rgb, boolean dynamic);

    void step(float seconds);

    void render();

    void light(float x, float y, float z, int rgb, float intensity);

    void clear();

    @Override
    void close();
}
