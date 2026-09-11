package valthorne.math.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Reusable configuration for creating a rigid body in {@link PhysicsWorld3D}.
 * Shape and motion type are fixed at construction; fluent setters change the
 * initial transform, velocity, material response and simulation flags. Settings
 * use meters, kilograms and seconds, matching the physics world's conventions.
 *
 * <h2>Creation and Reuse</h2>
 * <pre>{@code
 * BodySettings3D settings = new BodySettings3D(shape, MotionType3D.DYNAMIC)
 *         .setMass(2f)
 *         .setPosition(0, 0, 5)
 *         .setRestitution(0.25f);
 * RigidBody3D first = world.createBody(settings);
 * settings.setPosition(3, 0, 5);
 * RigidBody3D second = world.createBody(settings);
 * }</pre>
 *
 * <p>The world reads these values at creation; changing this object afterward
 * does not update existing bodies. Position and rotation setters copy their
 * inputs, while the shape definition is retained by reference. Construction
 * allocates no native body. Do not mutate settings concurrently with body creation.</p>
 *
 * <h2>Defaults</h2>
 * <p>Initial position and linear velocity are zero and rotation is identity.
 * Mass is one kilogram, friction is {@code 0.5f}, restitution is zero, both
 * damping values are {@code 0.05f}, and gravity factor is one. The body uses layer
 * zero, permits sleeping, and starts with sensor and continuous-collision modes
 * disabled. Native static-body creation ignores the mass override.</p>
 *
 * @author Albert Beaupre
 */
public final class BodySettings3D {
    final CollisionShape3D shape; // Retained collision-shape definition used during native body creation.
    final MotionType3D motion; // Fixed motion category validated against shape restrictions.
    final Vector3f position = new Vector3f(), velocity = new Vector3f(); // Initial world position (meters) and linear velocity (meters/second).
    final Quaternionf rotation = new Quaternionf(); // Copied initial orientation, identity by default.
    float mass = 1f, friction = .5f, restitution = 0f, linearDamping = .05f, angularDamping = .05f, gravityFactor = 1f; // Initial mass, surface response, damping and gravity multiplier.
    int layer; // User collision-layer index, initially zero.
    boolean sensor, continuous, sleeping = true; // Sensor mode, linear-cast collision mode, and permission to sleep.

    /**
     * Retains the shape and motion category and initializes all other settings
     * to their defaults. Static-only shapes are rejected for moving categories;
     * choose a convex shape for a dynamic or kinematic body.
     *
     * @param shape  the nonnull collision-shape definition
     * @param motion the nonnull motion category
     * @throws NullPointerException     if shape or motion is null
     * @throws IllegalArgumentException if a static-only shape is paired with nonstatic motion
     */
    public BodySettings3D(CollisionShape3D shape, MotionType3D motion) {
        this.shape = java.util.Objects.requireNonNull(shape, "shape");
        this.motion = java.util.Objects.requireNonNull(motion, "motion");
        if (shape.isStaticOnly() && motion != MotionType3D.STATIC)
            throw new IllegalArgumentException("Triangle meshes require static bodies; use a convex hull for moving bodies");
    }

    /** Returns the fixed motion category of bodies created with these settings. */
    public MotionType3D getMotionType() {return motion;}

    /** Returns the configured mass override in kilograms. */
    public float getMass() {return mass;}

    /**
     * Assigns the initial world-space position from finite components. Values
     * are copied into internal storage and affect only bodies created afterward.
     *
     * @param x the world X coordinate in meters
     * @param y the world Y coordinate in meters
     * @param z the world Z coordinate in meters
     * @return these settings for chaining
     * @throws IllegalArgumentException if any component is non-finite
     */
    public BodySettings3D setPosition(float x, float y, float z) {return setPosition(new Vector3f(x, y, z));}

    /**
     * Copies a finite world-space position. Later changes to the supplied vector
     * do not alter this configuration, and failed validation leaves it unchanged.
     *
     * @param position the initial world position in meters
     * @return these settings for chaining
     * @throws NullPointerException     if position is null
     * @throws IllegalArgumentException if any component is non-finite
     */
    public BodySettings3D setPosition(Vector3f position) {
        this.position.set(PhysicsMath3D.check(position));
        return this;
    }

    /**
     * Copies and normalizes the initial orientation into internal quaternion storage.
     * Invalid components are rejected before the existing orientation is changed.
     *
     * @param rotation the nonnull orientation to copy
     * @return these settings for chaining
     * @throws NullPointerException if rotation is null
     * @throws IllegalArgumentException if rotation is non-finite or zero-length
     */
    public BodySettings3D setRotation(Quaternionf rotation) {
        PhysicsMath3D.normalizeRotation(rotation, this.rotation);
        return this;
    }

    /**
     * Assigns a finite initial world-space linear velocity. This is a creation
     * value rather than an impulse or a change to an existing body's velocity.
     *
     * @param x the X velocity in meters per second
     * @param y the Y velocity in meters per second
     * @param z the Z velocity in meters per second
     * @return these settings for chaining
     * @throws IllegalArgumentException if any component is non-finite
     */
    public BodySettings3D setLinearVelocity(float x, float y, float z) {
        velocity.set(PhysicsMath3D.check(new Vector3f(x, y, z)));
        return this;
    }

    /**
     * Sets the positive mass override supplied for nonstatic body creation.
     * The world asks the native engine to calculate inertia for that mass.
     * Static bodies do not use this override, but validation still applies here.
     *
     * @param kilograms the finite mass, strictly greater than zero
     * @return these settings for chaining
     * @throws IllegalArgumentException if kilograms is non-finite or not positive
     */
    public BodySettings3D setMass(float kilograms) {
        mass = PhysicsMath3D.positive(kilograms, "mass");
        return this;
    }

    /**
     * Sets the nonnegative surface-friction coefficient forwarded at creation.
     * The wrapper does not cap the coefficient at one or change existing contacts.
     *
     * @param friction the finite, nonnegative friction coefficient
     * @return these settings for chaining
     * @throws IllegalArgumentException if friction is negative or non-finite
     */
    public BodySettings3D setFriction(float friction) {
        this.friction = PhysicsMath3D.nonnegative(friction, "friction");
        return this;
    }

    /**
     * Sets the surface restitution coefficient within the inclusive zero-to-one
     * interval. Invalid input is rejected before replacing the previous value.
     *
     * @param restitution the finite coefficient controlling collision bounce
     * @return these settings for chaining
     * @throws IllegalArgumentException if restitution is non-finite or outside zero through one
     */
    public BodySettings3D setRestitution(float restitution) {
        PhysicsMath3D.nonnegative(restitution, "restitution");
        if (restitution > 1) throw new IllegalArgumentException("Restitution must be in [0,1]");
        this.restitution = restitution;
        return this;
    }

    /**
     * Sets the finite nonnegative damping values passed to native body creation.
     * No upper bound is imposed by this wrapper. Linear damping is assigned
     * first, so an invalid angular value leaves the new linear value in place.
     *
     * @param linear  the nonnegative linear damping setting
     * @param angular the nonnegative angular damping setting
     * @return these settings for chaining
     * @throws IllegalArgumentException if either setting is negative or non-finite
     */
    public BodySettings3D setDamping(float linear, float angular) {
        linearDamping = PhysicsMath3D.nonnegative(linear, "linear damping");
        angularDamping = PhysicsMath3D.nonnegative(angular, "angular damping");
        return this;
    }

    /**
     * Sets the multiplier applied to world gravity by the native body.
     * Zero suppresses gravity and negative factors are accepted, reversing its
     * direction for bodies affected by gravity. The value is not clamped.
     *
     * @param factor the finite gravity multiplier
     * @return these settings for chaining
     * @throws IllegalArgumentException if factor is non-finite
     */
    public BodySettings3D setGravityFactor(float factor) {
        gravityFactor = PhysicsMath3D.finite(factor, "gravity factor");
        return this;
    }

    /**
     * Selects a user collision layer. The world maps it together with the motion
     * category into its native filter layers when the body is created.
     *
     * @param layer the user-layer index from zero through fifteen
     * @return these settings for chaining
     * @throws IllegalArgumentException if layer is outside the supported range
     * @see CollisionLayers3D
     */
    public BodySettings3D setLayer(int layer) {
        this.layer = CollisionLayers3D.check(layer);
        return this;
    }

    /**
     * Selects the native sensor flag for subsequently created bodies. Sensor
     * configuration does not bypass the world's collision-layer filtering or
     * register contact listeners automatically.
     *
     * @param sensor whether to create the body as a sensor
     * @return these settings for chaining
     */
    public BodySettings3D setSensor(boolean sensor) {
        this.sensor = sensor;
        return this;
    }

    /**
     * Chooses native linear-cast motion quality when enabled, or discrete motion
     * quality when disabled. This setting changes creation-time collision handling;
     * it does not change the world's fixed timestep or substep limit.
     *
     * @param enabled whether to request continuous linear-cast collision handling
     * @return these settings for chaining
     */
    public BodySettings3D setContinuousCollision(boolean enabled) {
        continuous = enabled;
        return this;
    }

    /**
     * Sets whether the native body is allowed to sleep. This is a permission,
     * not a command to sleep immediately; nonstatic bodies are initially added
     * in the activated state by the world.
     *
     * @param enabled whether native sleeping should be allowed
     * @return these settings for chaining
     */
    public BodySettings3D setAllowSleeping(boolean enabled) {
        sleeping = enabled;
        return this;
    }
}
