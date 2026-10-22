package valthorne.graphics.particle;

import org.joml.Vector2f;
import valthorne.math.physics.BodySettings2D;
import valthorne.math.physics.CollisionShape2D;
import valthorne.math.physics.PhysicsWorld2D;
import valthorne.math.physics.RigidBody2D;

/**
 * Scene-thread rigid-body ownership for one particle system. Fixed slot arrays
 * associate each pooled particle with its own native body independently of active
 * array compaction. Inactive slots remove their bodies from simulation and reuse
 * them at the next spawn; geometry changes replace only the affected body.
 * The world is borrowed, stepped by its owner, and never closed here.
 */
final class ParticlePhysics implements AutoCloseable {
    private final PhysicsWorld2D world; // Borrowed owner-thread simulation.
    private final RigidBody2D[] bodies; // Retained body handles, including disabled reusable slots.
    private final Particle[] particles; // Current slot occupants, or null while reusable.
    private final float[] widths; // Collider widths captured when each body was constructed.
    private final float[] heights; // Collider heights captured when each body was constructed.
    private final int[] free; // Allocation-free stack of reusable slot indices.
    private final Vector2f vector = new Vector2f(); // Shared pixel/velocity conversion scratch.
    private final boolean circle; // Collider shape copied at attachment time.
    private final float colliderScale; // Initial visual-to-collider multiplier.
    private final float mass; // Body mass copied from configuration.
    private final float friction; // Native contact friction.
    private final float restitution; // Native bounce coefficient.
    private final float linearDamping; // Native linear damping.
    private final float angularDamping; // Native angular damping.
    private final float gravityFactor; // World gravity multiplier.
    private final boolean fixedRotation; // Angular locking copied at attachment time.
    private final boolean continuous; // Continuous collision mode.
    private final boolean sensor; // Contact overlap-only mode.
    private final float ySign; // Render Y direction relative to native Y-up coordinates.
    private int freeCount; // Available slot count.
    private int activeCount; // Number of participating particle bodies.
    private long synchronizedStep = -1; // Native step already copied, avoiding duplicate draw/shadow reads.

    /**
     * Copies settings and allocates bounded Java slot storage, without bodies.
     * @param world open physics world owned by the caller
     * @param settings validated reusable configuration
     * @param capacity particle-system capacity
     */
    ParticlePhysics(PhysicsWorld2D world, ParticlePhysicsSettings settings, int capacity) {
        if (world.isClosed()) throw new IllegalArgumentException("Particle physics requires an open world");
        this.world = world;
        int count = Math.min(settings.maxBodies, capacity);
        bodies = new RigidBody2D[count];
        particles = new Particle[count];
        widths = new float[count];
        heights = new float[count];
        free = new int[count];
        for (int i = 0; i < count; i++) free[i] = count - 1 - i;
        freeCount = count;
        circle = settings.circle;
        colliderScale = settings.colliderScale;
        mass = settings.mass;
        friction = settings.friction;
        restitution = settings.restitution;
        linearDamping = settings.linearDamping;
        angularDamping = settings.angularDamping;
        gravityFactor = settings.gravityFactor;
        fixedRotation = settings.fixedRotation;
        continuous = settings.continuous;
        sensor = settings.sensor;
        world.toWorld(0, 0, vector);
        float zeroY = vector.y;
        world.toWorld(0, 1, vector);
        ySign = Math.signum(vector.y - zeroY);
    }

    /**
     * Enables or releases a particle's slot according to its per-particle flag.
     * Excess live particles retain ordinary CPU motion until a slot becomes free.
     * @param particle active particle owned by this system
     * @return whether native physics owns this particle's motion
     */
    boolean attach(Particle particle) {
        if (!particle.isPhysicsEnabled()) { release(particle); return false; }
        if (particle.physicsSlot >= 0) return true;
        if (freeCount == 0) return false;
        int slot = free[freeCount - 1];
        float width = Math.max(.1f, Math.abs(particle.getBaseWidth() * particle.getScale()) * colliderScale);
        float height = Math.max(.1f, Math.abs(particle.getBaseHeight() * particle.getScale()) * colliderScale);
        RigidBody2D body = bodies[slot];
        if (body != null && (body.isDestroyed() || widths[slot] != width || heights[slot] != height)) {
            if (!body.isDestroyed()) body.close();
            bodies[slot] = null;
            body = null;
        }
        if (body == null) {
            CollisionShape2D shape = circle ? world.circlePixels(Math.min(width, height) * .5f) : world.boxPixels(width, height);
            body = world.createBody(new BodySettings2D(shape)
                    .positionPixels(particle.getX(), particle.getY())
                    .rotation((float) Math.toRadians(particle.getRotation()) * ySign)
                    .mass(mass)
                    .friction(friction)
                    .restitution(restitution)
                    .damping(linearDamping, angularDamping)
                    .gravityFactor(gravityFactor)
                    .fixedRotation(fixedRotation)
                    .continuous(continuous)
                    .sensor(sensor));
            bodies[slot] = body;
            widths[slot] = width;
            heights[slot] = height;
        } else {
            body.setSimulationEnabled(true);
            body.setTransformPixels(particle.getX(), particle.getY(), (float) Math.toRadians(particle.getRotation()) * ySign);
        }
        body.setLinearVelocity(world.toMeters(particle.getVelX()), world.toMeters(particle.getVelY()) * ySign);
        body.setAngularVelocity(fixedRotation ? 0 : (float) Math.toRadians(particle.getRotationSpeed()) * ySign);
        body.setSimulationEnabled(true);
        freeCount--;
        particles[slot] = particle;
        particle.physicsSlot = slot;
        activeCount++;
        return true;
    }

    /**
     * Copies native motion into particle render state after the world advances.
     */
    void synchronize() {
        if (world.isClosed()) throw new IllegalStateException("Detach particle physics before using a closed world");
        long step = world.getStepCount();
        if (step == synchronizedStep) return;
        synchronizedStep = step;
        for (int i = 0; i < particles.length; i++) {
            Particle particle = particles[i];
            if (particle == null) continue;
            RigidBody2D body = bodies[i];
            if (body.isDestroyed()) { release(particle); continue; }
            body.getPosition(vector).asPixels();
            particle.setX(vector.x);
            particle.setY(vector.y);
            particle.setRotation((float) Math.toDegrees(body.getRotation()) * ySign);
            body.getLinearVelocity(vector);
            particle.setVelX(world.toPixels(vector.x));
            particle.setVelY(world.toPixels(vector.y) * ySign);
            particle.setRotationSpeed((float) Math.toDegrees(body.getAngularVelocity()) * ySign);
        }
    }

    /**
     * Removes a dying or opted-out particle from simulation without freeing its
     * reusable native body. Called before the particle returns to its pool.
     * @param particle slot occupant
     */
    void release(Particle particle) {
        int slot = particle.physicsSlot;
        if (slot < 0) return;
        RigidBody2D body = bodies[slot];
        if (!body.isDestroyed()) body.setSimulationEnabled(false);
        particles[slot] = null;
        particle.physicsSlot = -1;
        free[freeCount++] = slot;
        activeCount--;
    }

    /**
     * Returns a borrowed native body for individual force/contact customization.
     * @param particle current slot occupant
     * @return its body, or null when physics is not assigned
     */
    RigidBody2D body(Particle particle) {
        int slot = particle.physicsSlot;
        return slot >= 0 && slot < particles.length && particles[slot] == particle ? bodies[slot] : null;
    }

    /**
     * Returns the number of participating bodies without allocating a collection.
     */
    int activeCount() { return activeCount; }

    @Override
    public void close() {
        for (int i = 0; i < bodies.length; i++) {
            if (particles[i] != null) particles[i].physicsSlot = -1;
            particles[i] = null;
            if (bodies[i] != null && !bodies[i].isDestroyed()) bodies[i].close();
            bodies[i] = null;
        }
        activeCount = 0;
    }
}
