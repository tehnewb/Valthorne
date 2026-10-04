package valthorne.math.physics;

/**
 * Fluent capacity and timing configuration copied by {@link PhysicsWorld2D}
 * during construction. Later changes affect only worlds created afterward.
 * Settings own no native resources and are not intended for concurrent mutation.
 *
 * <p>Defaults provide 4096 bodies, 16384 candidate pairs, 4096 contacts, a
 * 1/60-second step, eight catch-up steps per update, one collision subdivision,
 * a four-megabyte reusable native scratch arena, and no worker threads. Increase
 * pair/contact capacities for dense scenes; exhausted solver capacities make
 * the world fail explicitly rather than silently accept incomplete contacts.
 * Optional subsystems default to 1024 simultaneous joints and 8192 buffered
 * contact events per fixed step. Their storage is allocated only on first use;
 * contact-buffer overflow also fails the world before partial event delivery.</p>
 */
public final class PhysicsWorldSettings2D {
    int maxBodies = 4096; // Maximum simultaneously owned bodies and dense handle-array capacity.
    int maxJoints = 1024; // Maximum live constraints; the handle array is allocated on first use.
    int contactEventCapacity = 8192; // Buffered native events per step when contact listening is enabled.
    int maxBodyPairs = 16384; // Maximum queued native collision candidates.
    int maxContacts = 4096; // Maximum native contact constraints.
    float fixedTimeStep = 1f / 60f; // Duration of each native step in seconds.
    int maxSubSteps = 8; // Maximum catch-up steps performed by one update call.
    int collisionSteps = 1; // Collision subdivisions within each fixed step.
    int workerThreads; // Native workers; zero selects a single-threaded job system.
    int scratchBytes = 4 * 1024 * 1024; // Reusable native scratch capacity before malloc fallback.
    float gravityX; // Horizontal gravitational acceleration in meters per second squared.
    float gravityY = -9.81f; // Vertical gravitational acceleration in a Y-up world.

    /**
     * Creates a configuration with the documented default capacities and timing.
     */
    public PhysicsWorldSettings2D() {
    }

    /**
     * Sets the native body limit and the size of the world's handle array.
     *
     * @param count maximum live bodies, between 1 and 8388608
     * @return these settings
     * @throws IllegalArgumentException if the count exceeds Jolt's body index range
     */
    public PhysicsWorldSettings2D maxBodies(int count) {
        if (count < 1 || count > 0x800000)
            throw new IllegalArgumentException("Body capacity must be between 1 and 8388608");
        maxBodies = count;
        return this;
    }

    /**
     * Sets the maximum number of simultaneously owned native joints.
     *
     * @param count positive joint capacity
     * @return these settings
     */
    public PhysicsWorldSettings2D maxJoints(int count) {
        if (count < 1) throw new IllegalArgumentException("Joint capacity must be positive");
        maxJoints = count;
        return this;
    }

    /**
     * Sizes the optional contact buffer. Overflow fails the world after native
     * stepping rather than silently losing gameplay events. Collision subdivisions
     * and CCD may produce more than one event for a pair within a fixed step.
     *
     * @param count positive maximum buffered events per step
     * @return these settings
     */
    public PhysicsWorldSettings2D contactEventCapacity(int count) {
        if (count < 1) throw new IllegalArgumentException("Contact event capacity must be positive");
        contactEventCapacity = count;
        return this;
    }

    /**
     * Sets solver capacities independently of the number of live bodies.
     *
     * @param bodyPairs positive candidate-pair capacity
     * @param contacts positive contact capacity, within Jolt's native limit
     * @return these settings
     * @throws IllegalArgumentException if either capacity is nonpositive
     */
    public PhysicsWorldSettings2D collisionCapacity(int bodyPairs, int contacts) {
        if (bodyPairs < 1 || contacts < 1)
            throw new IllegalArgumentException("Collision capacities must be positive");
        maxBodyPairs = bodyPairs;
        maxContacts = contacts;
        return this;
    }

    /**
     * Sets the duration of each simulation step.
     *
     * @param seconds finite duration greater than zero
     * @return these settings
     */
    public PhysicsWorldSettings2D fixedTimeStep(float seconds) {
        PhysicsValidation2D.positive(seconds, "fixed time step");
        fixedTimeStep = seconds;
        return this;
    }

    /**
     * Bounds catch-up work after frame stalls. Excess whole steps are discarded
     * and reported by the world, while fractional time remains for interpolation.
     *
     * @param count positive maximum steps per update
     * @return these settings
     */
    public PhysicsWorldSettings2D maxSubSteps(int count) {
        if (count < 1) throw new IllegalArgumentException("Maximum substeps must be positive");
        maxSubSteps = count;
        return this;
    }

    /**
     * Subdivides each fixed step for collision detection and response.
     *
     * @param count positive subdivision count
     * @return these settings
     */
    public PhysicsWorldSettings2D collisionSteps(int count) {
        if (count < 1) throw new IllegalArgumentException("Collision steps must be positive");
        collisionSteps = count;
        return this;
    }

    /**
     * Selects native worker threads while retaining owner-thread public access.
     *
     * @param count worker count, zero for single-threaded simulation
     * @return these settings
     */
    public PhysicsWorldSettings2D workerThreads(int count) {
        if (count < 0) throw new IllegalArgumentException("Worker threads must be nonnegative");
        workerThreads = count;
        return this;
    }

    /**
     * Sizes the reusable temporary arena. Native heap allocation is used as a
     * fallback if a step requires more scratch space than this arena provides.
     *
     * @param bytes positive arena size in bytes
     * @return these settings
     */
    public PhysicsWorldSettings2D scratchBytes(int bytes) {
        if (bytes < 1) throw new IllegalArgumentException("Scratch bytes must be positive");
        scratchBytes = bytes;
        return this;
    }

    /**
     * Sets the initial world gravity in a Y-up coordinate system.
     *
     * @param x horizontal acceleration in meters per second squared
     * @param y vertical acceleration in meters per second squared
     * @return these settings
     */
    public PhysicsWorldSettings2D gravity(float x, float y) {
        PhysicsValidation2D.finite(x, "gravity x");
        PhysicsValidation2D.finite(y, "gravity y");
        gravityX = x;
        gravityY = y;
        return this;
    }
}
