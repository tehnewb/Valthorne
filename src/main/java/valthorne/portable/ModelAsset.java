package valthorne.portable;

/** An attached imported model, invalidated by scene clear or backend closure. Does not imply a collider. */
public interface ModelAsset extends AutoCloseable {
    /** Positions the model root in meters, rotates around Y in radians, and uniformly scales it. */
    void transform(float x, float y, float z, float yaw, float scale);
    /** Number of animation clips in the source asset. */
    int animationCount();
    /** Applies an animation at an explicit time in seconds and updates skinning matrices. */
    void animate(int clip, float seconds);
    /** Idempotently detaches and destroys owned model resources. */
    @Override void close();
}
