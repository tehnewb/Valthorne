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
 */
public final class ParticleEmitter3D implements AutoCloseable {
    private final int capacity;
    private final Consumer<Particle3D> initializer;
    private final ArrayList<Particle3D> particles = new ArrayList<>();
    private final List<Particle3D> readOnlyParticles = Collections.unmodifiableList(particles);
    private final ArrayDeque<Particle3D> pool = new ArrayDeque<>();
    private float rate;
    private double remainder;
    private boolean emitting = true, closed, busy;
    private Scene3D scene;
    private PhysicsWorld3D physicsWorld;
    private Function<Particle3D, BodySettings3D> physicsFactory;
    private Consumer<PhysicsWorld3D> beforeStepListener, afterStepListener;
    private Vector3f force;

    /** Creates an empty emitter; continuous emission starts enabled at zero rate. */
    public ParticleEmitter3D(int capacity, Consumer<Particle3D> initializer) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        this.initializer = Objects.requireNonNull(initializer, "initializer");
    }

    private void checkMutable() {
        if (closed) throw new IllegalStateException("Particle emitter is closed");
        if (busy) throw new IllegalStateException("Cannot reenter or modify an emitter during initialization/update");
    }

    /** Sets births/second and resets fractional emission progress. */
    public ParticleEmitter3D setEmissionRate(float perSecond) {
        checkMutable();
        if (!Float.isFinite(perSecond) || perSecond < 0)
            throw new IllegalArgumentException("Rate must be finite and nonnegative");
        rate = perSecond;
        remainder = 0;
        return this;
    }

    /** Pauses automatic births only; existing particles and explicit bursts continue. */
    public ParticleEmitter3D setEmitting(boolean enabled) {
        checkMutable();
        emitting = enabled;
        return this;
    }

    public float getEmissionRate() {return rate;}
    public boolean isEmitting() {return emitting;}
    public boolean isClosed() {return closed;}
    public int getParticleCount() {return particles.size();}
    public int getCapacity() {return capacity;}

    /** Returns one cached, unmodifiable live membership view with mutable particles. */
    public List<Particle3D> getParticles() {return readOnlyParticles;}

    public PhysicsWorld3D getPhysicsWorld() {return physicsWorld;}

    /**
     * Enables Jolt for subsequent spawns; the emitter must be empty. Each factory
     * result must be nonnull and DYNAMIC. Its shape, mass, rotation, collision layer,
     * gravity factor, damping and other flags are respected. Its initial position
     * and linear velocity are overwritten from the initialized particle, so reused
     * settings must not be accessed concurrently. Model scale does not resize the
     * collider. Prefer simple sphere/box shapes and configure particle collision
     * layers on the world before creating it when self-collision is unnecessary.
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
            try {world.addAfterStepListener(afterStepListener);} catch (RuntimeException | Error e) {
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

    /** Disables Jolt for subsequent spawns; clear existing particles first. */
    public ParticleEmitter3D disablePhysics() {
        checkMutable();
        requireEmpty();
        unregisterPhysics();
        physicsWorld = null;
        physicsFactory = null;
        remainder = 0;
        return this;
    }

    private void requireEmpty() {
        if (!particles.isEmpty()) throw new IllegalStateException("Clear particles before changing physics mode");
    }

    private void unregisterPhysics() {
        if (physicsWorld != null && !physicsWorld.isClosed()) {
            physicsWorld.removeBeforeStepListener(beforeStepListener);
            physicsWorld.removeAfterStepListener(afterStepListener);
        }
    }

    /** Transfers current and future mesh particles to a borrowed scene. */
    public ParticleEmitter3D attach(Scene3D destination) {
        checkMutable();
        scene = Objects.requireNonNull(destination, "scene");
        for (int i = 0; i < particles.size(); i++) particles.get(i).attach(scene);
        return this;
    }

    /** Removes this emitter's mesh instances from its scene without ending particles. */
    public ParticleEmitter3D detach() {
        checkMutable();
        scene = null;
        for (int i = 0; i < particles.size(); i++) particles.get(i).attach(null);
        return this;
    }

    /**
     * Spawns up to count or free capacity at age zero. A failed initializer/factory
     * releases the failed particle's body; previous successful spawns remain active.
     */
    public int burst(int count) {
        checkMutable();
        if (count < 0) throw new IllegalArgumentException("Count must be nonnegative");
        if (physicsWorld != null) physicsWorld.getBodyCount();
        int accepted = Math.min(count, capacity - particles.size());
        busy = true;
        try {
            for (int i = 0; i < accepted; i++) spawn();
        } finally {busy = false;}
        return accepted;
    }

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
            try {particle.release();} catch (RuntimeException | Error cleanup) {e.addSuppressed(cleanup);}
            pool.addLast(particle);
            throw e;
        }
        return particle;
    }

    /**
     * Advances cosmetic motion and distributed births. In physics mode this only
     * validates delta and retires externally destroyed bodies; the world supplies
     * the clock. Calling it after world closure clears the remaining particle state.
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
        } finally {busy = false;}
    }

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

    /** The common no-expiration path neither inspects bodies nor writes membership. */
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

    /** Retires destroyed/expired native particles, optionally synchronizing their poses. */
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
        } finally {busy = false;}
    }

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
        } finally {busy = false;}
    }

    private void recycle(Particle3D particle) {
        particle.release();
        pool.addLast(particle);
    }

    /** Submits meshes when assigned, otherwise billboards, to an active batch. */
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

    /** Releases all bodies and scene membership, retaining the pool and settings. */
    public void clear() {
        checkMutable();
        clearParticles();
    }

    private void clearParticles() {
        for (int i = 0; i < particles.size(); i++) recycle(particles.get(i));
        particles.clear();
        remainder = 0;
    }

    /** Idempotently releases bodies, listener registrations and scene membership. */
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
