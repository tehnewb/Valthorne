package valthorne.graphics.particle;

import valthorne.math.physics.RigidBody2D;

/**
 * Optional particle motion ownership delegated to a rigid-body simulation.
 * Keeping this boundary separate from the native Jolt implementation allows
 * applications using ordinary CPU particles to omit JVM physics bindings from
 * their runtime. The particle system owns this attachment, never the world.
 */
interface ParticlePhysics extends AutoCloseable {
    /**
     * Assigns a reusable simulation slot to an opted-in particle.
     * @param particle active particle
     * @return whether simulation owns its motion
     */
    boolean attach(Particle particle);

    /**
     * Copies newly simulated poses into the current particle render state.
     */
    void synchronize();

    /**
     * Releases a particle's simulation slot before returning it to the pool.
     * @param particle dying or opted-out particle
     */
    void release(Particle particle);

    /**
     * Returns the simulation body borrowed by an attached particle.
     * @param particle slot occupant
     * @return assigned body, or null
     */
    RigidBody2D body(Particle particle);

    /**
     * Counts currently participating bodies without allocating a snapshot.
     * @return active body count
     */
    int activeCount();

    @Override
    void close();
}
