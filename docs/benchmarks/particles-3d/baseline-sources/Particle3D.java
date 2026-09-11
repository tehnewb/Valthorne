package valthorne.graphics.particle;

import valthorne.graphics.model.BillboardMode3D;
import valthorne.graphics.model.BillboardSprite3D;
import org.joml.Vector3f;

/**
 * Mutable billboard-particle state configured by a {@link ParticleEmitter3D}
 * initializer. Time is measured in seconds; position, velocity and acceleration
 * use world units, world units per second and world units per second squared.
 * A particle starts with a one-second lifetime, zero motion, and a spherical
 * billboard anchored at its center.
 *
 * <p>The emitter advances constant-acceleration motion and recycles expired
 * particles. Configure the sprite's texture, appearance and initial transform in
 * the spawn callback. Position is the sprite's own mutable position, not a second
 * independently synchronized vector.</p>
 *
 * <p>All object getters return live mutable state. Do not retain a particle as
 * a permanent identity after expiration: the pool may reset and reuse it for a
 * different spawn. This object does not own or dispose sprite texture resources.</p>
 *
 * @author Albert Beaupre
 */
public final class Particle3D {
    private final BillboardSprite3D sprite = new BillboardSprite3D().setMode(BillboardMode3D.SPHERICAL).setAnchor(.5f, .5f); // Reused centered spherical billboard holding position and appearance.
    private final Vector3f velocity = new Vector3f(); // Live world-space velocity in world units per second.
    private final Vector3f acceleration = new Vector3f(); // Live constant acceleration used during each update.
    private float age, lifetime = 1f; // Elapsed age and positive lifetime in seconds.

    /**
     * Returns the particle's live billboard for configuring its texture and
     * appearance. The emitter submits this same sprite when drawing particles.
     *
     * @return the internal mutable billboard
     */
    public BillboardSprite3D getSprite() {return sprite;}

    /**
     * Returns the sprite's live world position. Changes move the particle directly;
     * no copy or separate position synchronization occurs.
     *
     * @return the internal billboard position vector
     */
    public Vector3f getPosition() {return sprite.getPosition();}

    /**
     * Returns the live velocity vector, updated by acceleration during simulation.
     * Supply finite values when changing it; this getter performs no validation.
     *
     * @return velocity in world units per second
     */
    public Vector3f getVelocity() {return velocity;}

    /**
     * Returns the live acceleration vector. Its current value is treated as
     * constant over each update and may be changed between updates.
     *
     * @return acceleration in world units per second squared
     */
    public Vector3f getAcceleration() {return acceleration;}

    /**
     * Returns elapsed age, including the full last update delta even when motion
     * was capped at expiration. Age can therefore exceed the lifetime.
     *
     * @return elapsed particle age in seconds
     */
    public float getAge() {return age;}

    /**
     * Returns the configured positive lifetime. Reaching this age makes the
     * particle eligible for removal on the emitter's next update.
     *
     * @return lifetime in seconds, initially one
     */
    public float getLifetime() {return lifetime;}

    /**
     * Changes lifetime without resetting age. Shortening it below current age
     * leaves removal to the next emitter update; invalid values leave it unchanged.
     *
     * @param seconds the finite lifetime, strictly greater than zero
     * @return this particle for initializer chaining
     * @throws IllegalArgumentException if seconds is non-finite or not positive
     */
    public Particle3D setLifetime(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0f)
            throw new IllegalArgumentException("Lifetime must be positive and finite");
        lifetime = seconds;
        return this;
    }

    /**
     * Computes age divided by lifetime, capped at one after expiration.
     * Normal emitter updates keep age nonnegative; this does not remove the particle.
     *
     * @return normalized lifetime progress capped at one
     */
    public float getProgress() {return Math.min(1f, age / lifetime);}

    /**
     * Restores spawn defaults before a pooled particle is initialized again.
     * Resets age, lifetime and motion, and copies a fresh centered spherical
     * billboard's state into the existing sprite. Former texture resources are
     * not disposed; resource lifetime remains with their owner.
     */
    void reset() {
        age = 0;
        lifetime = 1;
        velocity.set(0, 0, 0);
        acceleration.set(0, 0, 0);
        sprite.set(new BillboardSprite3D().setMode(BillboardMode3D.SPHERICAL).setAnchor(.5f, .5f));
    }

    /**
     * Integrates position using {@code v * dt + 0.5 * a * dt * dt} and velocity
     * using {@code a * dt}, limiting motion to remaining lifetime. Age advances
     * by the full supplied delta. The owning emitter validates delta beforehand.
     *
     * @param delta the nonnegative finite elapsed time in seconds
     * @return true while age is strictly below lifetime, false at or after expiration
     */
    boolean update(float delta) {
        float dt = Math.min(delta, Math.max(0, lifetime - age));
        getPosition().add(velocity.x() * dt + .5f * acceleration.x() * dt * dt,
                velocity.y() * dt + .5f * acceleration.y() * dt * dt, velocity.z() * dt + .5f * acceleration.z() * dt * dt);
        velocity.add(acceleration.x() * dt, acceleration.y() * dt, acceleration.z() * dt);
        age += delta;
        return age < lifetime;
    }
}
