package valthorne.graphics.particle;

/**
 * Opt-in rigid-body configuration copied by {@link ParticleSystem#enablePhysics}.
 * Each simulated particle receives a collider sized from its initial visual
 * dimensions, multiplied by {@link #colliderScale(float)}. Collider dimensions
 * stay fixed while lifetime color and visual scale continue to interpolate.
 *
 * <p>The body limit bounds native resource ownership; particles beyond that
 * limit retain ordinary emitter motion. Bodies are reused when particles expire.
 * Physical particles use the shared world's gravity rather than the emitter's
 * simple gravity/wind integration. Configuration changes take effect when physics
 * is enabled again; modifying this object does not mutate an existing adapter.</p>
 */
public final class ParticlePhysicsSettings {
    int maxBodies = 256; // Maximum retained native bodies, including reusable inactive slots.
    boolean circle = true; // Circle collider when true, box collider otherwise.
    float colliderScale = 1; // Collider extent relative to the initial visual dimensions.
    float mass = .1f; // Kilograms per particle body.
    float friction = .3f; // Contact friction coefficient.
    float restitution = .5f; // Fraction of normal impact velocity retained.
    float linearDamping = .05f; // Native linear velocity damping.
    float angularDamping = .05f; // Native angular velocity damping.
    float gravityFactor = 1; // Multiplier of shared world gravity.
    boolean fixedRotation; // Whether angular simulation is locked.
    boolean continuous = true; // Linear-cast detection for fast small particles.
    boolean sensor; // Overlap-only contacts instead of collision response.

    /**
     * Creates bounded dynamic-particle defaults with circular colliders.
     */
    public ParticlePhysicsSettings() {
    }

    /**
     * Sets the maximum simultaneous/reusable physics slots.
     * @param count positive body limit
     * @return these settings
     */
    public ParticlePhysicsSettings maxBodies(int count) {
        if (count < 1) throw new IllegalArgumentException("Particle body limit must be positive");
        maxBodies = count;
        return this;
    }

    /**
     * Selects circles using half the smaller initial particle dimension.
     * @return these settings
     */
    public ParticlePhysicsSettings circle() {
        circle = true;
        return this;
    }

    /**
     * Selects boxes matching initial particle width and height.
     * @return these settings
     */
    public ParticlePhysicsSettings box() {
        circle = false;
        return this;
    }

    /**
     * Adjusts collider size without changing rendered particle dimensions.
     * @param scale finite positive dimension multiplier
     * @return these settings
     */
    public ParticlePhysicsSettings colliderScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Collider scale must be finite and positive");
        colliderScale = scale;
        return this;
    }

    /**
     * Sets dynamic mass for each particle.
     * @param kilograms finite positive mass
     * @return these settings
     */
    public ParticlePhysicsSettings mass(float kilograms) {
        if (!Float.isFinite(kilograms) || kilograms <= 0) throw new IllegalArgumentException("Particle mass must be finite and positive");
        mass = kilograms;
        return this;
    }

    /**
     * Sets native contact friction.
     * @param coefficient finite nonnegative coefficient
     * @return these settings
     */
    public ParticlePhysicsSettings friction(float coefficient) {
        if (!Float.isFinite(coefficient) || coefficient < 0) throw new IllegalArgumentException("Friction must be finite and nonnegative");
        friction = coefficient;
        return this;
    }

    /**
     * Sets impact bounce.
     * @param coefficient fraction between zero and one
     * @return these settings
     */
    public ParticlePhysicsSettings restitution(float coefficient) {
        if (!Float.isFinite(coefficient) || coefficient < 0 || coefficient > 1) throw new IllegalArgumentException("Restitution must be between zero and one");
        restitution = coefficient;
        return this;
    }

    /**
     * Sets linear and angular damping coefficients.
     * @param linear finite fraction between zero and one
     * @param angular finite fraction between zero and one
     * @return these settings
     */
    public ParticlePhysicsSettings damping(float linear, float angular) {
        if (!Float.isFinite(linear) || linear < 0 || linear > 1 || !Float.isFinite(angular) || angular < 0 || angular > 1)
            throw new IllegalArgumentException("Damping must be between zero and one");
        linearDamping = linear;
        angularDamping = angular;
        return this;
    }

    /**
     * Scales native world gravity; zero gives floating physical particles.
     * @param factor finite gravity multiplier, including negative values
     * @return these settings
     */
    public ParticlePhysicsSettings gravityFactor(float factor) {
        if (!Float.isFinite(factor)) throw new IllegalArgumentException("Gravity factor must be finite");
        gravityFactor = factor;
        return this;
    }

    /**
     * Controls whether particle bodies can rotate from torque and collisions.
     * @param fixed whether rotation is locked
     * @return these settings
     */
    public ParticlePhysicsSettings fixedRotation(boolean fixed) {
        fixedRotation = fixed;
        return this;
    }

    /**
     * Enables continuous collision detection for fast small debris.
     * @param enabled whether to use native linear casts
     * @return these settings
     */
    public ParticlePhysicsSettings continuous(boolean enabled) {
        continuous = enabled;
        return this;
    }

    /**
     * Selects overlap detection without physically blocking other bodies.
     * @param enabled whether particle bodies are sensors
     * @return these settings
     */
    public ParticlePhysicsSettings sensor(boolean enabled) {
        sensor = enabled;
        return this;
    }
}
