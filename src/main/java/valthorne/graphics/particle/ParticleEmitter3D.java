package valthorne.graphics.particle;

import org.joml.Vector3f;
import valthorne.graphics.model.ModelBatch3D;
import valthorne.graphics.model.ModelInstance3D;
import valthorne.graphics.model.Scene3D;
import valthorne.math.physics.BodySettings3D;
import valthorne.math.physics.MotionType3D;
import valthorne.math.physics.PhysicsWorld3D;
import valthorne.math.physics.RigidBody3D;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Capacity-limited, reusable 3D particles with optional Jolt simulation. Cosmetic
 * particles use exact constant-acceleration motion and subframe emission times.
 * Mesh particles can attach to a Scene3D; billboards submit through ModelBatch3D.
 * Models, materials, textures, scenes and the physics world remain borrowed.
 *
 * <p>With {@link #setPhysics(PhysicsWorld3D, Function)}, advance the world externally.
 * The emitter emits at fixed tick boundaries, applies mass times acceleration
 * before each tick, then synchronizes native poses and ages particles afterward.
 * World gravity is additional, multiplied by the factory's gravity factor. Physics
 * births are quantized to the beginning of a tick and never analytically backdated
 * through colliders. Calling {@link #update(float)} does not advance this clock a
 * second time. Dropped world time is also dropped from the particle clock. Issue
 * bursts before advancing the world (or from an earlier before-step listener) for
 * the emitter's acceleration force to apply on their first simulation tick.</p>
 *
 * <p>The initializer and physics factory run synchronously for each spawn. They
 * must not reenter or modify the emitter. Use one owning thread (the physics
 * world's thread when attached), and treat pooled particle references as temporary.
 * Closing releases owned bodies and scene membership, never borrowed resources.</p>
 *
 * <pre>{@code
 * ParticleEmitter3D sparks = new ParticleEmitter3D(256, particle -> {
 *     particle.setLifetime(0.8f);
 *     particle.getPosition().set(0f, 0f, 1f);
 *     particle.getVelocity().set(1f, 0f, 3f);
 *     particle.getAcceleration().set(0f, 0f, -9.8f);
 *     // Configure a borrowed sprite texture or model here.
 * });
 * sparks.setEmissionRate(40f);
 * // Each frame, with a batch already begun:
 * sparks.update(deltaSeconds);
 * sparks.submit(batch);
 * // Release emitter-owned state when the effect is no longer needed:
 * sparks.close();
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class ParticleEmitter3D implements AutoCloseable {
    private final int capacity; // Maximum simultaneous active particle count.
    private final Consumer<Particle3D> initializer; // Synchronous per-birth configuration callback.
    private final ArrayList<Particle3D> particles = new ArrayList<>(); // Active particles maintained in spawn order.
    private final List<Particle3D> readOnlyParticles = Collections.unmodifiableList(particles); // Cached unmodifiable live membership view.
    private final ArrayDeque<Particle3D> pool = new ArrayDeque<>(); // Released particles available for reset and reuse.
    private float rate; // Automatic births per simulation second.
    private double remainder; // Fractional automatic birth progress, excluding discarded whole births.
    private boolean emitting = true, closed, busy; // Automatic-emission, final-closure, and reentrancy-guard flags.
    private Scene3D scene; // Borrowed destination for particle meshes and enabled lights.
    private PhysicsWorld3D physicsWorld; // Borrowed fixed-step world, null for cosmetic mode.
    private Function<Particle3D, BodySettings3D> physicsFactory; // Synchronous dynamic-body settings factory for physical births.
    private Consumer<PhysicsWorld3D> beforeStepListener, afterStepListener; // Reusable callbacks registered on the configured physics world.
    private Vector3f force; // Lazy reusable acceleration-times-mass scratch vector.

    /**
     * Creates an empty emitter with continuous emission enabled at zero rate.
     * The initializer runs synchronously for each newly allocated or reset particle
     * and must not reenter or mutate the emitter.
     *
     * @param capacity    positive maximum number of simultaneously live particles
     * @param initializer callback configuring each particle before scene/body creation
     * @throws IllegalArgumentException if capacity is nonpositive
     * @throws NullPointerException     if initializer is null
     */
    public ParticleEmitter3D(int capacity, Consumer<Particle3D> initializer) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        this.initializer = Objects.requireNonNull(initializer, "initializer");
    }

    /**
     * Rejects operations after closure or during an initializer/update callback.
     * This guard prevents reentrancy; it is not a cross-thread synchronization lock.
     *
     * @throws IllegalStateException if closed or already busy
     */
    private void checkMutable() {
        if (closed) throw new IllegalStateException("Particle emitter is closed");
        if (busy) throw new IllegalStateException("Cannot reenter or modify an emitter during initialization/update");
    }

    /**
     * Sets automatic births per simulation second and discards fractional progress.
     * Existing particles and the emitting flag are unchanged; zero disables
     * automatic births without affecting explicit bursts.
     *
     * @param perSecond finite nonnegative birth rate
     * @return this emitter
     * @throws IllegalArgumentException if the rate is negative or non-finite
     * @throws IllegalStateException    if closed or busy
     */
    public ParticleEmitter3D setEmissionRate(float perSecond) {
        checkMutable();
        if (!Float.isFinite(perSecond) || perSecond < 0)
            throw new IllegalArgumentException("Rate must be finite and nonnegative");
        rate = perSecond;
        remainder = 0;
        return this;
    }

    /**
     * Enables or pauses automatic births without resetting fractional progress.
     * Existing particles continue to age and explicit bursts remain available.
     *
     * @param enabled whether automatic emission should run
     * @return this emitter
     * @throws IllegalStateException if closed or busy
     */
    public ParticleEmitter3D setEmitting(boolean enabled) {
        checkMutable();
        emitting = enabled;
        return this;
    }

    /**
     * Reads the configured automatic birth rate, including after closure.
     *
     * @return requested particles per simulation second
     */
    public float getEmissionRate() {
        return rate;
    }

    /**
     * Reads the automatic-emission flag. A true value alone does not imply births:
     * the rate may be zero, capacity full, or emitter closed.
     *
     * @return configured automatic-emission state
     */
    public boolean isEmitting() {
        return emitting;
    }

    /**
     * Reports whether final cleanup completed and the emitter rejected further use.
     *
     * @return true after successful close
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Reads current active membership, including particles awaiting the next
     * retirement check after an external body destruction.
     *
     * @return current number of registered particles
     */
    public int getParticleCount() {
        return particles.size();
    }

    /**
     * Reads the fixed upper bound on simultaneous active membership.
     *
     * @return constructor-supplied positive capacity
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Returns a cached unmodifiable live membership view. Particle objects are
     * mutable and pooled; expiration or clear changes the view and may later reuse
     * the same object for another birth.
     *
     * @return active particles in retained spawn order
     */
    public List<Particle3D> getParticles() {
        return readOnlyParticles;
    }

    /**
     * Exposes the borrowed world whose fixed-step callbacks drive physical particles.
     * Reading does not validate world ownership or lifetime.
     *
     * @return configured world, or null for cosmetic mode
     */
    public PhysicsWorld3D getPhysicsWorld() {
        return physicsWorld;
    }

    /**
     * Enables physics for subsequent births; active membership must be empty.
     * Registers before/after-step listeners on the borrowed world and resets
     * fractional emission progress. Advance the world externally on its owner thread.
     *
     * <p>Each factory result must be non-null and dynamic. Its collision shape, mass,
     * rotation, damping, gravity factor, and flags are retained, but position and
     * linear velocity are overwritten from the initialized particle. Reused settings
     * must not be accessed concurrently. Render scale does not resize collision
     * geometry. The factory must not reenter the emitter.</p>
     *
     * @param world   live physics world owned by the calling thread
     * @param factory callback creating body settings for each initialized particle
     * @return this emitter
     * @throws NullPointerException  if world or factory is null
     * @throws IllegalStateException if closed, busy, nonempty, or the world cannot be accessed
     */
    public ParticleEmitter3D setPhysics(PhysicsWorld3D world, Function<Particle3D, BodySettings3D> factory) {
        checkMutable();
        requireEmpty();
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(factory, "factory");
        world.getBodyCount(); // Validate owner thread and world lifetime before altering registration.
        if (physicsWorld != world) {
            if (beforeStepListener == null) {
                beforeStepListener = this::beforePhysicsStep;
                afterStepListener = this::afterPhysicsStep;
                force = new Vector3f();
            }
            world.addBeforeStepListener(beforeStepListener);
            try {
                world.addAfterStepListener(afterStepListener);
            } catch (RuntimeException | Error e) {
                world.removeBeforeStepListener(beforeStepListener);
                throw e;
            }
            unregisterPhysics();
            physicsWorld = world;
        }
        physicsFactory = factory;
        remainder = 0;
        return this;
    }

    /**
     * Unregisters physics callbacks and selects cosmetic motion for future births.
     * Requires an empty emitter; borrowed world resources remain alive. Resets
     * fractional emission progress without changing rate or initializer.
     *
     * @return this emitter
     * @throws IllegalStateException if closed, busy, or nonempty
     */
    public ParticleEmitter3D disablePhysics() {
        checkMutable();
        requireEmpty();
        unregisterPhysics();
        physicsWorld = null;
        physicsFactory = null;
        remainder = 0;
        return this;
    }

    /**
     * Rejects a physics-mode change while particles remain registered.
     *
     * @throws IllegalStateException if active membership is nonempty
     */
    private void requireEmpty() {
        if (!particles.isEmpty()) throw new IllegalStateException("Clear particles before changing physics mode");
    }

    /**
     * Removes this emitter's callbacks from a configured, still-open world.
     * A closed or absent world is skipped. The stored world reference is not cleared
     * here; the caller completes the mode transition.
     */
    private void unregisterPhysics() {
        if (physicsWorld != null && !physicsWorld.isClosed()) {
            physicsWorld.removeBeforeStepListener(beforeStepListener);
            physicsWorld.removeAfterStepListener(afterStepListener);
        }
    }

    /**
     * Transfers current and future mesh particles and enabled point lights to the
     * borrowed scene. Billboards still require explicit batch submission.
     * Reattaching also refreshes particle membership missing from the same scene.
     *
     * @param destination non-null scene receiving particle meshes and lights
     * @return this emitter
     * @throws NullPointerException  if destination is null
     * @throws IllegalStateException if closed or busy
     */
    public ParticleEmitter3D attach(Scene3D destination) {
        checkMutable();
        scene = Objects.requireNonNull(destination, "scene");
        for (int i = 0; i < particles.size(); i++) particles.get(i).attach(scene);
        return this;
    }

    /**
     * Removes current mesh and light membership and clears the destination for
     * future births. Particle lifetimes, bodies, and manual submission remain active.
     *
     * @return this emitter
     * @throws IllegalStateException if closed or busy
     */
    public ParticleEmitter3D detach() {
        checkMutable();
        scene = null;
        for (int i = 0; i < particles.size(); i++) particles.get(i).attach(null);
        return this;
    }

    /**
     * Spawns at age zero up to the requested count or remaining capacity. Ignores
     * the automatic-emission flag. Initialization/body-creation failure releases
     * the failed particle; earlier successful births from this call remain active.
     *
     * @param count nonnegative requested births
     * @return number accepted if all requested initializations succeed
     * @throws IllegalArgumentException if count is negative
     * @throws IllegalStateException    if closed, busy, or the physics world is inaccessible
     */
    public int burst(int count) {
        checkMutable();
        if (count < 0) throw new IllegalArgumentException("Count must be nonnegative");
        if (physicsWorld != null) physicsWorld.getBodyCount();
        int accepted = Math.min(count, capacity - particles.size());
        busy = true;
        try {
            for (int i = 0; i < accepted; i++) spawn();
        } finally {
            busy = false;
        }
        return accepted;
    }

    /**
     * Takes a particle from the pool or allocates one, initializes it, optionally
     * creates its dynamic body, and adds scene and active-list membership. The
     * caller reserves capacity and holds the busy guard. Failed setup releases
     * owned state, returns the particle to the pool, and rethrows the original
     * exception with cleanup failure suppressed when applicable.
     *
     * @return successfully initialized active particle
     * @throws NullPointerException     if the physics factory returns null
     * @throws IllegalArgumentException if the physics factory selects a nondynamic body
     */
    private Particle3D spawn() {
        Particle3D particle = pool.pollFirst();
        if (particle == null) particle = new Particle3D();
        else particle.reset();
        try {
            initializer.accept(particle);
            if (physicsWorld != null) {
                BodySettings3D settings = Objects.requireNonNull(physicsFactory.apply(particle), "physics factory result");
                if (settings.getMotionType() != MotionType3D.DYNAMIC)
                    throw new IllegalArgumentException("Particle physics requires a dynamic body");
                Vector3f velocity = particle.getVelocity();
                settings.setPosition(particle.getPosition()).setLinearVelocity(velocity.x(), velocity.y(), velocity.z());
                particle.setBody(physicsWorld.createBody(settings), settings.getMass(), physicsWorld.getStepCount() + 1);
            }
            particle.attach(scene);
            particles.add(particle);
        } catch (RuntimeException | Error e) {
            try {
                particle.release();
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            }
            pool.addLast(particle);
            throw e;
        }
        return particle;
    }

    /**
     * Advances cosmetic motion and subframe-distributed births. In physics mode,
     * only synchronizes/compacts physical particles; fixed-step callbacks supply
     * their clock. Externally destroyed bodies are retired. Delta is validated even
     * when physical time is driven elsewhere.
     *
     * @param delta finite nonnegative elapsed seconds
     * @throws IllegalArgumentException if delta is negative or non-finite
     * @throws IllegalStateException    if closed or busy
     */
    public void update(float delta) {
        checkMutable();
        if (!Float.isFinite(delta) || delta < 0)
            throw new IllegalArgumentException("Delta must be finite and nonnegative");
        busy = true;
        try {
            if (physicsWorld != null) {
                compactPhysics(true);
                return;
            }
            updateCosmetic(delta);
            emit(delta, false);
        } finally {
            busy = false;
        }
    }

    /**
     * Converts elapsed time and fractional progress into births limited by capacity.
     * Excess whole births are discarded rather than queued. Cosmetic births retain
     * the most recent subframe times and are analytically advanced to frame end;
     * physical births start at the tick boundary with no backdated movement.
     *
     * @param delta    elapsed simulation seconds
     * @param physical true to leave new particles for native fixed-step simulation
     */
    private void emit(float delta, boolean physical) {
        if (!emitting || rate == 0) return;
        double total = remainder + (double) delta * rate;
        double births = Math.floor(total);
        int count = (int) Math.min(births, capacity - particles.size());
        remainder = total - births;
        // Cosmetic capacity limits retain the most recent births without a backlog.
        for (int i = count - 1; i >= 0; i--) {
            Particle3D particle = spawn();
            if (!physical && !particle.update((float) ((remainder + i) / rate))) {
                particles.remove(particles.size() - 1);
                recycle(particle);
            }
        }
    }

    /**
     * Integrates existing cosmetic particles, recycling expired entries and
     * compacting survivors in original order. The no-expiration path avoids
     * rewriting membership or inspecting native bodies.
     *
     * @param delta validated nonnegative elapsed seconds
     */
    private void updateCosmetic(float delta) {
        int size = particles.size(), read = 0;
        while (read < size) {
            Particle3D particle = particles.get(read);
            if (!particle.update(delta)) {
                recycle(particle);
                break;
            }
            read++;
        }
        if (read == size) return;
        // Once a gap exists, compact only the remaining tail in original spawn order.
        int write = read;
        while (++read < size) {
            Particle3D particle = particles.get(read);
            if (particle.update(delta)) particles.set(write++, particle);
            else recycle(particle);
        }
        while (particles.size() > write) particles.remove(particles.size() - 1);
    }

    /**
     * Retires missing, destroyed, or expired physical bodies and compacts survivors
     * in spawn order. Optional synchronization copies live body poses before the
     * alive check; recycling releases owned state and scene membership.
     *
     * @param syncPhysics whether to copy usable native poses before filtering
     */
    private void compactPhysics(boolean syncPhysics) {
        int size = particles.size(), write = 0;
        for (int read = 0; read < size; read++) {
            Particle3D particle = particles.get(read);
            RigidBody3D body = particle.getBody();
            if (syncPhysics && body != null && !body.isDestroyed()) particle.syncBody();
            boolean alive = body != null && !body.isDestroyed() && particle.getAge() < particle.getLifetime();
            if (alive) {
                if (write != read) particles.set(write, particle);
                write++;
            } else recycle(particle);
        }
        while (particles.size() > write) particles.remove(particles.size() - 1);
    }

    /**
     * Handles a tick only for the currently configured world while open. Retires
     * stale particles, emits tick-boundary births, records their upcoming step,
     * and applies mass-scaled acceleration force in addition to native gravity.
     * The busy guard covers initializer and force application work.
     *
     * @param world world invoking its before-step listeners
     */
    private void beforePhysicsStep(PhysicsWorld3D world) {
        if (closed || world != physicsWorld) return;
        checkMutable();
        busy = true;
        try {
            compactPhysics(false);
            emit(world.getFixedTimeStep(), true);
            long step = world.getStepCount() + 1;
            for (int i = 0; i < particles.size(); i++) {
                Particle3D particle = particles.get(i);
                particle.physicsStep = step;
                Vector3f acceleration = particle.getAcceleration();
                if (acceleration.x() != 0 || acceleration.y() != 0 || acceleration.z() != 0) {
                    force.set(acceleration).mul(particle.physicsMass);
                    particle.getBody().addForce(force);
                }
            }
        } finally {
            busy = false;
        }
    }

    /**
     * Ages particles marked for the completed step, then synchronizes body poses
     * and retires expired particles. Other-world and post-close callbacks are
     * ignored, preventing stale registrations from advancing the clock.
     *
     * @param world world invoking its after-step listeners
     */
    private void afterPhysicsStep(PhysicsWorld3D world) {
        if (closed || world != physicsWorld) return;
        checkMutable();
        busy = true;
        try {
            long step = world.getStepCount();
            float delta = world.getFixedTimeStep();
            for (int i = 0; i < particles.size(); i++) {
                Particle3D particle = particles.get(i);
                if (particle.physicsStep == step) particle.advanceAge(delta);
            }
            compactPhysics(true);
        } finally {
            busy = false;
        }
    }

    /**
     * Releases a particle's body and scene membership, then appends it to the pool.
     * State is reset on its next spawn rather than here.
     *
     * @param particle particle being retired from active membership
     */
    private void recycle(Particle3D particle) {
        particle.release();
        pool.addLast(particle);
    }

    /**
     * Synchronizes placement and submits each assigned mesh, otherwise its billboard,
     * to an already active batch. Does not update age or physics. Avoid submitting
     * scene-attached meshes again if the scene already submits them for this pass.
     *
     * @param batch active destination batch
     * @return number of submissions accepted by the batch
     * @throws IllegalStateException if closed, busy, or the batch rejects its state
     */
    public int submit(ModelBatch3D batch) {
        checkMutable();
        int accepted = 0;
        for (int i = 0; i < particles.size(); i++) {
            Particle3D particle = particles.get(i);
            particle.syncModel();
            ModelInstance3D mesh = particle.mesh();
            if (mesh != null ? batch.submit(mesh) : batch.submit(particle.getSprite())) accepted++;
        }
        return accepted;
    }

    /**
     * Releases all active bodies and scene entries, retaining reusable pooled objects
     * and configuration. Resets fractional births; physics callbacks remain
     * registered so future emissions can resume.
     *
     * @throws IllegalStateException if closed or busy
     */
    public void clear() {
        checkMutable();
        clearParticles();
    }

    /**
     * Recycles every active particle, clears membership, and discards fractional
     * emission progress. Caller supplies lifecycle/reentrancy checks; configuration
     * and borrowed resources are retained.
     */
    private void clearParticles() {
        for (int i = 0; i < particles.size(); i++) recycle(particles.get(i));
        particles.clear();
        remainder = 0;
    }

    /**
     * Releases active bodies, scene membership, physics listener registrations, and
     * pooled references. Borrowed scenes, world, models, textures, and materials
     * remain owned by their callers. Repeated calls after successful closure are
     * no-ops; mutating operations afterward fail.
     *
     * @throws IllegalStateException if invoked reentrantly while busy
     */
    @Override
    public void close() {
        if (closed) return;
        checkMutable();
        clearParticles();
        unregisterPhysics();
        physicsWorld = null;
        physicsFactory = null;
        scene = null;
        pool.clear();
        closed = true;
    }
}
