package valthorne.graphics.particle;

import valthorne.graphics.model.ModelBatch3D;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages a capacity-limited pool of billboard particles with explicit bursts,
 * continuous emission and constant-acceleration motion. Existing particles advance
 * before continuous births are added, and expired particles return to a reuse pool.
 * This emitter owns particle state but does not dispose textures or the render batch.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ParticleEmitter3D emitter = new ParticleEmitter3D(256, particle -> {
 *     particle.setLifetime(2);
 *     particle.getSprite().setTextureRegion(region);
 *     particle.getVelocity().set(0, 0, 3);
 *     particle.getAcceleration().set(0, 0, -1);
 * }).setEmissionRate(20);
 * emitter.update(deltaSeconds);
 * emitter.submit(activeModelBatch);
 * }</pre>
 *
 * <p>Continuous birth times are distributed within an update, so newly created
 * particles are advanced to their appropriate age before submission. If births
 * exceed free capacity, only the most recent eligible births are retained; missed
 * births do not accumulate as a future backlog. A fractional birth remainder is
 * retained across active updates.</p>
 *
 * <p>The initializer runs synchronously for every spawn, including pooled reuse.
 * Do not reenter or structurally modify the emitter from that callback. Use the
 * emitter on one owning thread, and treat exposed particle references as temporary
 * because expiration permits their reuse for later spawns.</p>
 *
 * @author Albert Beaupre
 */
public final class ParticleEmitter3D {
    private final int capacity; // Maximum simultaneously live particles.
    private final Consumer<Particle3D> initializer; // Synchronous callback applied to each new or reset particle.
    private final ArrayList<Particle3D> particles = new ArrayList<>(); // Active particles in retained spawn order.
    private final ArrayDeque<Particle3D> pool = new ArrayDeque<>(); // Expired or failed-spawn objects available for reuse.
    private float rate; // Continuous births per second; zero disables automatic births.
    private double remainder; // Fractional birth count preserved between active emission updates.
    private boolean emitting = true; // Gates continuous births without pausing existing-particle motion.

    /**
     * Creates an empty emitter with a positive live-particle limit. Continuous
     * emission is enabled but its rate starts at zero; no particles are preallocated.
     *
     * @param capacity    the maximum live-particle count
     * @param initializer the nonnull callback configuring each spawn
     * @throws IllegalArgumentException if capacity is not positive
     * @throws NullPointerException     if initializer is null
     */
    public ParticleEmitter3D(int capacity, Consumer<Particle3D> initializer) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        this.initializer = java.util.Objects.requireNonNull(initializer);
    }

    /**
     * Sets continuous births per second and clears fractional emission progress,
     * even when the rate is unchanged. Existing particles are unaffected.
     *
     * @param perSecond the finite nonnegative emission rate
     * @return this emitter for chaining
     * @throws IllegalArgumentException if the rate is negative or non-finite
     */
    public ParticleEmitter3D setEmissionRate(float perSecond) {
        if (!Float.isFinite(perSecond) || perSecond < 0)
            throw new IllegalArgumentException("Rate must be finite and nonnegative");
        rate = perSecond;
        remainder = 0;
        return this;
    }

    /**
     * Enables or pauses automatic births without clearing fractional progress.
     * Existing particles still advance during update, and explicit bursts remain allowed.
     *
     * @param enabled whether continuous emission should run on subsequent updates
     * @return this emitter for chaining
     */
    public ParticleEmitter3D setEmitting(boolean enabled) {
        emitting = enabled;
        return this;
    }

    /**
     * Returns an unmodifiable live view of active membership. Elements remain
     * mutable and can later be recycled; copy values for a durable snapshot.
     *
     * @return a read-only view of the currently active particles
     */
    public List<Particle3D> getParticles() {return Collections.unmodifiableList(particles);}

    /**
     * Immediately spawns up to the requested count or available capacity, whichever
     * is smaller. Burst particles start at age zero and do not alter continuous
     * emission progress. An initializer failure propagates after earlier successful
     * spawns remain active; the burst is not rolled back.
     *
     * @param count the nonnegative requested number of particles
     * @return the number spawned when all accepted initializations succeed
     * @throws IllegalArgumentException if count is negative
     */
    public int burst(int count) {
        if (count < 0) throw new IllegalArgumentException("Count must be nonnegative");
        int accepted = Math.min(count, capacity - particles.size());
        for (int i = 0; i < accepted; i++) spawn();
        return accepted;
    }

    /**
     * Obtains a pooled particle or creates one, resets reused state, and invokes
     * the initializer before adding it to active membership. On callback failure,
     * returns that object to the pool and rethrows. The caller enforces capacity.
     *
     * @return the newly active particle after successful initialization
     */
    private Particle3D spawn() {
        Particle3D particle = pool.pollFirst();
        if (particle == null) particle = new Particle3D();
        else particle.reset();
        try {initializer.accept(particle);} catch (RuntimeException | Error e) {
            pool.addLast(particle);
            throw e;
        }
        particles.add(particle);
        return particle;
    }

    /**
     * Advances active particles, recycles expirations, then creates continuous
     * births when enabled. Births are aged according to their positions within
     * the elapsed interval; particles already expired at that age are recycled
     * immediately. Capacity is calculated before this birth loop.
     *
     * <p>Paused or zero-rate emission leaves the fractional remainder unchanged.
     * Missed capacity-limited births are discarded rather than carried forward.
     * Callback failures propagate without undoing earlier motion or successful births.</p>
     *
     * @param delta the finite nonnegative elapsed time in seconds
     * @throws IllegalArgumentException if delta is negative or non-finite
     */
    public void update(float delta) {
        if (!Float.isFinite(delta) || delta < 0)
            throw new IllegalArgumentException("Delta must be finite and nonnegative");
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle3D particle = particles.get(i);
            if (!particle.update(delta)) {
                particles.remove(i);
                pool.addLast(particle);
            }
        }
        if (!emitting || rate == 0) return;
        double total = remainder + (double) delta * rate;
        double births = Math.floor(total);
        int count = (int) Math.min(births, capacity - particles.size());
        // Retain the most recent births when the frame exceeds capacity; never accumulate a backlog.
        remainder = total - births;
        for (int i = count - 1; i >= 0; i--) {
            Particle3D particle = spawn();
            float age = (float) ((remainder + i) / rate);
            if (!particle.update(age)) {
                particles.remove(particles.size() - 1);
                pool.addLast(particle);
            }
        }
    }

    /**
     * Submits each active particle's billboard to an already-begun model batch.
     * Does not advance simulation or begin/end rendering. The batch may reject
     * invisible, culled or untextured sprites according to its submission policy.
     *
     * @param batch the active destination batch
     * @return the number of sprites accepted by the batch
     * @throws NullPointerException if batch is null and at least one particle is active
     */
    public int submit(ModelBatch3D batch) {
        int accepted = 0;
        for (Particle3D particle : particles) if (batch.submit(particle.getSprite())) accepted++;
        return accepted;
    }

    /**
     * Recycles all active particles and clears fractional emission progress.
     * Rate and enabled state are preserved. Particle objects and referenced
     * textures are not disposed; pooled objects are reset when next spawned.
     */
    public void clear() {
        pool.addAll(particles);
        particles.clear();
        remainder = 0;
    }
}
