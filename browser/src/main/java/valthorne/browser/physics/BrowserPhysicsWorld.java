package valthorne.browser.physics;

import org.teavm.jso.JSObject;

/**
 * Owns a single-threaded Jolt WASM simulation and reusable native scratch.
 * The Java world retains timing, interpolation, handle validation and events.
 * Browser worker counts are intentionally mapped to the synchronous WASM job
 * system; native physics completes before each call returns. No shared-memory
 * headers or graphics context are required. Closing destroys all native owners.
 */
public interface BrowserPhysicsWorld extends JSObject {
    /**
     * Creates a planar rigid body and copies construction values.
     */
    int createBody(JSObject shape, float[] values);

    /**
     * Creates an optimized convex perimeter and center soft mesh.
     */
    PhysicsSoftMesh createSoftBody(float[] positions, int[] triangles, int[] edges, float originX, float originY, float compliance, int iterations, float radius);

    /**
     * Destroys a removed native body.
     */
    void destroyBody(int id);

    /**
     * Adds a body to native broadphase; activation is zero to wake or one to preserve sleep.
     */
    void addBody(int id, int activation);

    /**
     * Removes a body without destroying its handle.
     */
    void removeBody(int id);

    /**
     * Returns current native solver activity.
     */
    boolean isActive(int id);

    /**
     * Wakes a moving body.
     */
    void activateBody(int id);

    /**
     * Copies the pose into reusable numeric scratch.
     */
    void getPositionAndRotation(int id, PhysicsVector position, PhysicsVector rotation);

    /**
     * Teleports a body with explicit activation.
     */
    void setPositionAndRotation(int id, PhysicsVector position, PhysicsVector rotation, int activation);

    /**
     * Corrects the position without changing orientation.
     */
    void setPosition(int id, PhysicsVector position, int activation);

    /**
     * Moves a kinematic body to its next pose.
     */
    void moveKinematic(int id, PhysicsVector position, PhysicsVector rotation, float seconds);

    /**
     * Copies current velocity into reusable scratch.
     */
    void getLinearVelocity(int id, PhysicsVector destination);

    /**
     * Sets linear velocity in meters per second.
     */
    void setLinearVelocity(int id, float x, float y, float z);

    /**
     * Copies angular velocity into reusable scratch.
     */
    void getAngularVelocity(int id, PhysicsVector destination);

    /**
     * Sets angular velocity in radians per second.
     */
    void setAngularVelocity(int id, PhysicsVector velocity);

    /**
     * Accumulates force for the next step.
     */
    void addForce(int id, PhysicsVector force);

    /**
     * Applies impulse through the center of mass.
     */
    void addImpulse(int id, PhysicsVector impulse);

    /**
     * Applies impulse at an explicit world position.
     */
    void addImpulse(int id, PhysicsVector impulse, PhysicsVector position);

    /**
     * Accumulates torque for the next step.
     */
    void addTorque(int id, PhysicsVector torque);

    /**
     * Applies an angular impulse immediately.
     */
    void addAngularImpulse(int id, PhysicsVector impulse);

    /**
     * Sets validated motion mode and activation.
     */
    void setMotionType(int id, int motion, int activation);

    /**
     * Updates the native broadphase partition.
     */
    void setObjectLayer(int id, int layer);

    /**
     * Sets contact friction.
     */
    void setFriction(int id, float friction);

    /**
     * Reads contact friction.
     */
    float getFriction(int id);

    /**
     * Sets contact restitution.
     */
    void setRestitution(int id, float restitution);

    /**
     * Reads contact restitution.
     */
    float getRestitution(int id);

    /**
     * Sets world gravity in meters per second squared.
     */
    void setGravity(float x, float y, float z);

    /**
     * Advances one native step synchronously.
     * @return native update error flags; zero indicates a complete update
     */
    int update(float seconds, int collisionSteps);

    /**
     * Reports a failed native update or an unavailable shared native device.
     * @return whether stepping must stop and this world must be replaced
     */
    boolean isFailed();

    /**
     * Reports whether native memory became unusable after an abort or trap.
     * @return whether cleanup must drop owners without invoking native code
     */
    boolean isUnavailable();

    /**
     * Reports the active rigid-body population.
     */
    int getNumActiveBodies();

    /**
     * Installs or detaches contact capture.
     */
    void setContactListener(PhysicsContactCallback callback);

    /**
     * Rebuilds broadphase after bulk creation.
     */
    void optimizeBroadPhase();

    /**
     * Allocates a distance or pivot relationship without adding it yet.
     */
    PhysicsConstraint createConstraint(int first, int second, boolean distance, float firstX, float firstY, float secondX, float secondY, float minimum, float maximum);

    /**
     * Adds an owned native constraint to solving.
     */
    void addConstraint(PhysicsConstraint constraint);

    /**
     * Removes a constraint before its reference is closed.
     */
    void removeConstraint(PhysicsConstraint constraint);

    /**
     * Prepares reusable query filters and collectors on first use.
     */
    void prepareQueries(PhysicsQueryCallback callback);

    /**
     * Casts a planar segment and stores the closest rigid hit.
     */
    boolean raycast(float x, float y, float dx, float dy);

    /**
     * Returns the most recent ray hit body identifier.
     */
    int rayBody();

    /**
     * Returns the most recent ray hit fraction.
     */
    float rayFraction();

    /**
     * Collects conservative point candidates.
     */
    void queryPoint(float x, float y);

    /**
     * Collects conservative bounds candidates.
     */
    void queryBounds(float minX, float minY, float maxX, float maxY);

    /**
     * Closes lazily allocated native query resources.
     */
    void closeQueries();

    /**
     * Releases the world and all remaining native owners.
     */
    void close();
}
