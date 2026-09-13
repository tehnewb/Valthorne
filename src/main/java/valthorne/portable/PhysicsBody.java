package valthorne.portable;

/** An owned rigid body; scene clearing invalidates outstanding handles. */
public interface PhysicsBody extends AutoCloseable {
    float x(); float y(); float z();
    float velocityY();
    void velocity(float x,float y,float z);
    void impulse(float x,float y,float z);
    void position(float x,float y,float z);
    @Override void close();
}
