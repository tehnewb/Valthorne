package valthorne.math.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.Edge;
import com.github.stephengold.joltjni.Face;
import com.github.stephengold.joltjni.SoftBodyCreationSettings;
import com.github.stephengold.joltjni.SoftBodyMotionProperties;
import com.github.stephengold.joltjni.SoftBodySharedSettings;
import com.github.stephengold.joltjni.SoftBodyVertex;
import com.github.stephengold.joltjni.Vertex;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.Vec3;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryUtil;
import valthorne.math.geometry.Shape;

import java.nio.FloatBuffer;
import java.util.Objects;

/**
 * World-owned deformable XY mesh solved by Jolt's native soft-body solver.
 * Construction triangulates a supported convex shape as a perimeter/center fan
 * with edge constraints; there is no enclosed 3D volume or pressure simulation.
 * Eight constraint iterations and compliance 0.00001 are used initially.
 * Smaller compliance is stiffer; zero requests inextensible edges.
 *
 * <p>The original shape's live perimeter vectors receive interpolated pixels.
 * Native orientation is identity, with the shape's initial degree rotation baked
 * into its mesh. Render rotation is subsequently zero. Parametric properties
 * such as a circle's radius describe its original outline, not its deformed size;
 * use points() for the current boundary. Do not edit shape setters or replace
 * vertices while attached. Closing releases native and direct-buffer resources;
 * no updates reach the shape afterward. Access belongs to the world's thread.</p>
 *
 * <p>Dynamic and static modes are supported. Static mode freezes the vertices;
 * kinematic mode is rejected because Jolt soft bodies have no rigid kinematic
 * trajectory. Pinning fixes an individual vertex in world space. Mass excludes
 * pinned vertices. Forces/impulses and velocities use world meters and seconds.
 * Native vertex contacts handle rigid geometry. Circles additionally collide
 * against the complete deforming perimeter, with swept edge/corner checks and
 * inverse-mass-weighted impulse sharing. This prevents circles passing between
 * sparse vertices, including on pinned or frozen meshes. Other rigid shapes
 * retain native vertex-sampled coverage. Jolt does not solve soft/soft collisions.
 * Existing rigid-body query and contact-event APIs enumerate rigid handles only.</p>
 *
 * <p>One packed native read captures all mesh positions per step. Native vertex
 * wrappers, interpolation arrays and a direct buffer are allocated once; normal
 * frame updates allocate no Java objects. After each native step any out-of-plane
 * vertex displacement/velocity is projected back to XY before capture. Sleeping
 * meshes skip native reads. No volumetric deformation or out-of-plane bending is
 * exposed by this API.</p>
 *
 * <pre>{@code
 * SoftBody2D jelly = physics.soft(new Circle(200, 100, 24))
 *         .mass(2)
 *         .compliance(0.0001f)
 *         .restitution(0.3f)
 *         .friction(0.5f);
 * jelly.pin(0, true);
 * physics.update(deltaSeconds);
 * }</pre>
 */
public final class SoftBody2D implements AutoCloseable {
    /**
     * Initial inverse edge stiffness, allowing small elastic stretch under load.
     */
    private static final float DEFAULT_COMPLIANCE = 0.00001f;
    /**
     * Initial native XPBD constraint iterations per soft update.
     */
    private static final int DEFAULT_ITERATIONS = 8;
    /**
     * Upper bound preventing accidentally excessive native solver work.
     */
    private static final int MAX_ITERATIONS = 64;
    final PhysicsWorld2D world; // Owning native solver and pixel mapping.
    final Shape geometry; // Original mutable shape receiving the deformable perimeter.
    final int id; // Native body ID, including its generation.
    int index; // Index in the dense soft-body array.
    boolean destroyed; // Whether native ownership has ended.
    private final Body nativeBody; // Borrowed body wrapper for native sleep configuration.
    private final SoftBodyMotionProperties properties; // Borrowed native mesh properties, valid until destruction.
    final SoftBodyVertex[] vertices; // Cached native vertex wrappers, perimeter followed by center.
    private final Edge[] edges; // Cached optimized edge wrappers for compliance changes.
    private SoftBodySharedSettings mesh; // Explicitly owned reference retaining the optimized native edge storage.
    private final Vector2f[] boundary; // Original perimeter vector identities, reused for rendering.
    private final boolean[] pinned; // Explicit vertex pins retained across motion changes.
    final float[] current; // Packed world XY positions for all vertices.
    final float[] previous; // Previous captured world XY positions for interpolation.
    private FloatBuffer positions; // Explicitly owned direct buffer for packed native vertex capture.
    private Vector2f destination; // Lazily reused output for fluent position-to-pixel reads.
    private float originX; // Fixed native center-of-mass origin in world meters.
    private float originY; // Fixed native center-of-mass origin in world meters.
    private float mass = 1; // Total mass distributed over unpinned vertices.
    private MotionType2D motion = MotionType2D.DYNAMIC; // Mesh response, dynamic or static.
    private boolean interpolatedPosition; // Whether pending pixel conversion blends captured endpoints.
    private boolean previouslyActive = true; // Whether the final sleeping pose still needs capture.

    /**
     * Creates a triangulated native mesh and rolls back partial ownership on failure.
     * @param world live owning simulation
     * @param shape supported convex pixel geometry with a nondegenerate outline
     * @param index insertion index in the world's dense soft array
     */
    SoftBody2D(PhysicsWorld2D world, Shape shape, int index) {
        this.world = world;
        geometry = Objects.requireNonNull(shape, "shape");
        this.index = index;
        PhysicsGeometry2D snapshot = new PhysicsGeometry2D(shape, world);
        snapshot.collision(); // Reuse the rigid outline validation at this cold boundary.
        originX = world.worldX(snapshot.centerX);
        originY = world.worldY(snapshot.centerY);
        boundary = shape.points();
        int count = boundary.length;
        current = new float[(count + 1) * 2];
        previous = new float[current.length];
        pinned = new boolean[count + 1];
        int createdId = -1;
        try (SoftBodySharedSettings shared = new SoftBodySharedSettings(); Vertex vertex = new Vertex(); Edge edge = new Edge(); Face face = new Face(); SoftBodyCreationSettings settings = new SoftBodyCreationSettings()) {
            Vector2f[] rotated = shape.getRotatedPoints();
            float inverseMass = count + 1;
            double centerX = 0;
            double centerY = 0;
            for (int i = 0; i < count; i++) {
                current[i * 2] = world.worldX(rotated[i].x);
                current[i * 2 + 1] = world.worldY(rotated[i].y);
                centerX += current[i * 2];
                centerY += current[i * 2 + 1];
                vertex.setPosition(current[i * 2] - originX, current[i * 2 + 1] - originY, 0)
                        .setInvMass(inverseMass);
                shared.addVertex(vertex);
            }
            // A convex perimeter mean is strictly inside; a bounding pivot may lie on an edge.
            current[count * 2] = (float) (centerX / count);
            current[count * 2 + 1] = (float) (centerY / count);
            vertex.setPosition(current[count * 2] - originX, current[count * 2 + 1] - originY, 0);
            shared.addVertex(vertex);
            for (int i = 0; i < count; i++) {
                int next = (i + 1) % count;
                float dx = current[next * 2] - current[i * 2];
                float dy = current[next * 2 + 1] - current[i * 2 + 1];
                double area = (double) dx * (current[count * 2 + 1] - current[i * 2 + 1]) - (double) dy * (current[count * 2] - current[i * 2]);
                if (area == 0) throw new IllegalArgumentException("Soft mesh contains a degenerate triangle");
                edge.setVertex(0, i)
                        .setVertex(1, next)
                        .setCompliance(DEFAULT_COMPLIANCE);
                shared.addEdgeConstraint(edge);
                edge.setVertex(1, count);
                shared.addEdgeConstraint(edge);
                face.setVertex(0, count);
                face.setVertex(1, i);
                face.setVertex(2, next);
                shared.addFace(face);
            }
            shared.calculateEdgeLengths();
            shared.optimize();
            settings.setSettings(shared)
                    .setPosition(originX, originY, 0)
                    .setObjectLayer(PhysicsWorld2D.MOVING_LAYER)
                    .setMakeRotationIdentity(true)
                    .setUpdatePosition(false)
                    .setFacesDoubleSided(true)
                    .setPressure(0)
                    .setNumIterations(DEFAULT_ITERATIONS)
                    .setVertexRadius(world.toMeters(1));
            nativeBody = world.bodies.createSoftBody(settings);
            createdId = nativeBody.getId();
            id = createdId;
            properties = (SoftBodyMotionProperties) nativeBody.getMotionProperties();
            // The binding's bulk getter incorrectly uses borrowed properties as an owner.
            vertices = new SoftBodyVertex[count + 1];
            for (int i = 0; i < vertices.length; i++) vertices[i] = properties.getVertex(i);
            mesh = (SoftBodySharedSettings) properties.getSettings();
            edges = mesh.getEdgeConstraints();
            positions = MemoryUtil.memAllocFloat(vertices.length * 3);
            world.bodies.addBody(createdId, EActivation.Activate);
            System.arraycopy(current, 0, previous, 0, current.length);
            geometry.setRotation(0);
            sync(false);
        } catch (RuntimeException | Error failure) {
            try {
                if (createdId != -1) {
                    if (world.bodies.isAdded(createdId)) world.bodies.removeBody(createdId);
                    world.bodies.destroyBody(createdId);
                }
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            try {
                release();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Requires a live mesh and owner-thread access before touching native state.
     */
    private void check() {
        world.check();
        if (destroyed) throw new IllegalStateException("Soft body has been destroyed");
    }

    /**
     * Returns the original shape, whose perimeter represents current deformation.
     * @return retained geometry
     */
    public Shape getGeometry() {
        check();
        return geometry;
    }

    /**
     * Returns the current mesh response.
     * @return dynamic or static motion
     */
    public MotionType2D getMotionType() {
        check();
        return motion;
    }

    /**
     * Freezes or releases all unpinned vertices; explicit pins remain intact.
     * @param motion dynamic or static; kinematic is unsupported
     * @return this body
     */
    public SoftBody2D motion(MotionType2D motion) {
        check();
        Objects.requireNonNull(motion, "motion");
        if (motion == MotionType2D.KINEMATIC) throw new IllegalArgumentException("Soft bodies do not support kinematic motion");
        if (this.motion == motion) return this;
        this.motion = motion;
        applyMass();
        world.vectorScratch.set(0, 0, 0);
        for (SoftBodyVertex vertex : vertices) vertex.setVelocity(world.vectorScratch);
        wake();
        return this;
    }

    /**
     * Sets total mass distributed equally over unpinned vertices.
     * @param kilograms finite positive mass
     * @return this body
     */
    public SoftBody2D mass(float kilograms) {
        check();
        PhysicsValidation2D.positive(kilograms, "mass");
        if (!Float.isFinite(vertices.length / kilograms)) throw new IllegalArgumentException("Mass is too small");
        mass = kilograms;
        applyMass();
        wake();
        return this;
    }

    /**
     * Pins a vertex at its current world position or releases it. Perimeter indices
     * follow shape.points(); the final index is the internal center vertex.
     * @param vertex index between zero and getVertexCount() minus one
     * @param fixed whether the vertex should remain fixed
     * @return this body
     */
    public SoftBody2D pin(int vertex, boolean fixed) {
        check();
        Objects.checkIndex(vertex, vertices.length);
        pinned[vertex] = fixed;
        world.vectorScratch.set(0, 0, 0);
        vertices[vertex].setVelocity(world.vectorScratch);
        applyMass();
        wake();
        return this;
    }

    /**
     * Applies mass and motion without reallocating native mesh state.
     */
    private void applyMass() {
        int moving = 0;
        for (boolean fixed : pinned) if (!fixed) moving++;
        float inverse = moving / mass;
        for (int i = 0; i < vertices.length; i++) vertices[i].setInvMass(motion == MotionType2D.STATIC || pinned[i] ? 0 : inverse);
    }

    /**
     * Sets edge compliance; zero is stiffest and larger values permit stretching.
     * @param compliance finite nonnegative native compliance in inverse stiffness units
     * @return this body
     */
    public SoftBody2D compliance(float compliance) {
        check();
        PhysicsValidation2D.nonnegative(compliance, "compliance");
        for (Edge edge : edges) edge.setCompliance(compliance);
        wake();
        return this;
    }

    /**
     * Sets the native constraint iteration count; more iterations cost more CPU.
     * @param count between one and 64
     * @return this body
     */
    public SoftBody2D iterations(int count) {
        check();
        if (count < 1 || count > MAX_ITERATIONS) throw new IllegalArgumentException("Iterations must be between 1 and " + MAX_ITERATIONS);
        properties.setNumIterations(count);
        wake();
        return this;
    }

    /**
     * Sets contact friction for collisions against rigid bodies.
     * @param coefficient finite nonnegative friction
     * @return this body
     */
    public SoftBody2D friction(float coefficient) {
        check();
        PhysicsValidation2D.nonnegative(coefficient, "friction");
        world.bodies.setFriction(id, coefficient);
        return this;
    }

    /**
     * Sets the normal impact velocity fraction retained against rigid bodies.
     * @param coefficient restitution between zero and one
     * @return this body
     */
    public SoftBody2D restitution(float coefficient) {
        check();
        PhysicsValidation2D.fraction(coefficient, "restitution");
        world.bodies.setRestitution(id, coefficient);
        return this;
    }

    /**
     * Sets linear damping on moving vertices.
     * @param coefficient damping between zero and one
     * @return this body
     */
    public SoftBody2D damping(float coefficient) {
        check();
        PhysicsValidation2D.fraction(coefficient, "damping");
        properties.setLinearDamping(coefficient);
        wake();
        return this;
    }

    /**
     * Controls whether settled meshes may stop consuming native solver work.
     * @param enabled whether sleeping is permitted
     * @return this body
     */
    public SoftBody2D sleeping(boolean enabled) {
        check();
        nativeBody.setAllowSleeping(enabled);
        wake();
        return this;
    }

    /**
     * Sets gravitational acceleration relative to the owning world's gravity.
     * @param factor finite gravity multiplier
     * @return this body
     */
    public SoftBody2D gravityFactor(float factor) {
        check();
        PhysicsValidation2D.finite(factor, "gravity factor");
        properties.setGravityFactor(factor);
        wake();
        return this;
    }

    /**
     * Prescribes equal XY velocity to all movable vertices.
     * @param x horizontal meters per second
     * @param y vertical meters per second
     * @return this body
     */
    public SoftBody2D setLinearVelocity(float x, float y) {
        check();
        PhysicsValidation2D.finite(x, "velocity x");
        PhysicsValidation2D.finite(y, "velocity y");
        world.vectorScratch.set(x, y, 0);
        for (int i = 0; i < vertices.length; i++) if (motion == MotionType2D.DYNAMIC && !pinned[i]) vertices[i].setVelocity(world.vectorScratch);
        wake();
        return this;
    }

    /**
     * Adds total XY impulse evenly across movable vertices, preserving deformation.
     * @param x horizontal kilogram meters per second
     * @param y vertical kilogram meters per second
     * @return this body
     */
    public SoftBody2D addImpulse(float x, float y) {
        check();
        PhysicsValidation2D.finite(x, "impulse x");
        PhysicsValidation2D.finite(y, "impulse y");
        if (motion != MotionType2D.DYNAMIC) throw new IllegalStateException("Impulse requires a dynamic soft body");
        PhysicsValidation2D.finite(x / mass, "velocity change x");
        PhysicsValidation2D.finite(y / mass, "velocity change y");
        for (int i = 0; i < vertices.length; i++) {
            if (pinned[i]) continue;
            Vec3 velocity = vertices[i].getVelocity();
            velocity.addInPlace(x / mass, y / mass, 0);
            vertices[i].setVelocity(velocity);
        }
        wake();
        return this;
    }

    /**
     * Wakes the native mesh after a user change; workers are idle during this call.
     */
    private void wake() {
        previouslyActive = true;
        world.bodies.activateBody(id);
    }

    /**
     * Reports mesh size, including the additional center vertex.
     * @return native vertex count
     */
    public int getVertexCount() {
        check();
        return vertices.length;
    }

    /**
     * Copies a captured vertex to caller storage in pixels without allocation.
     * @param vertex perimeter or center vertex index
     * @param output nonnull caller-owned vector
     * @return the supplied output
     */
    public Vector2f getVertexPixels(int vertex, Vector2f output) {
        check();
        Objects.checkIndex(vertex, vertices.length);
        return world.toScreen(current[vertex * 2], current[vertex * 2 + 1], output);
    }

    /**
     * Selects the current mesh center for immediate asPixels() chaining.
     * @return this body
     */
    public SoftBody2D getPosition() {
        check();
        interpolatedPosition = false;
        return this;
    }

    /**
     * Selects the interpolated mesh center for immediate asPixels() chaining.
     * @return this body
     */
    public SoftBody2D getInterpolatedPosition() {
        check();
        interpolatedPosition = true;
        return this;
    }

    /**
     * Returns a reused pixel vector containing the mean of perimeter vertices.
     * The first call allocates it; subsequent calls overwrite the same vector.
     * @return reused pixel center
     */
    public Vector2f asPixels() {
        check();
        if (destination == null) destination = new Vector2f();
        float alpha = interpolatedPosition ? world.getInterpolationAlpha() : 1;
        double x = 0;
        double y = 0;
        for (int i = 0; i < boundary.length * 2; i += 2) {
            x += previous[i] + (current[i] - previous[i]) * alpha;
            y += previous[i + 1] + (current[i + 1] - previous[i + 1]) * alpha;
        }
        return world.toScreen((float) (x / boundary.length), (float) (y / boundary.length), destination);
    }

    /**
     * Captures packed world positions and removes any native Z drift. Sleeping
     * meshes still advance interpolation endpoints once after their final step.
     */
    void capture() {
        System.arraycopy(current, 0, previous, 0, current.length);
        boolean active = world.bodies.isActive(id);
        if (!active && !previouslyActive) return;
        previouslyActive = active;
        world.positionScratch.set(originX, originY, 0);
        positions.clear();
        properties.putVertexLocations(world.positionScratch, positions);
        for (int i = 0; i < vertices.length; i++) {
            float x = positions.get(i * 3);
            float y = positions.get(i * 3 + 1);
            current[i * 2] = x;
            current[i * 2 + 1] = y;
            if (positions.get(i * 3 + 2) == 0) continue;
            world.vectorScratch.set(x - originX, y - originY, 0);
            vertices[i].setPosition(world.vectorScratch);
            Vec3 velocity = vertices[i].getVelocity();
            velocity.setZ(0);
            vertices[i].setVelocity(velocity);
        }
    }

    /**
     * Corrects one movable endpoint after a continuous planar boundary contact.
     * Native constraints receive both the corrected position and impulse velocity;
     * pinned endpoints keep their exact positions and zero inverse mass.
     * @param vertex perimeter endpoint index
     * @param dx horizontal position correction
     * @param dy vertical position correction
     * @param velocityX horizontal velocity change
     * @param velocityY vertical velocity change
     */
    void correctContact(int vertex, float dx, float dy, float velocityX, float velocityY) {
        int offset = vertex * 2;
        current[offset] += dx;
        current[offset + 1] += dy;
        world.vectorScratch.set(current[offset] - originX, current[offset + 1] - originY, 0);
        vertices[vertex].setPosition(world.vectorScratch);
        Vec3 velocity = vertices[vertex].getVelocity();
        world.vectorScratch.set(velocity.getX() + velocityX, velocity.getY() + velocityY, 0);
        vertices[vertex].setVelocity(world.vectorScratch);
        wake();
    }

    /**
     * Writes the original perimeter without allocating or replacing vectors.
     * @param interpolated whether to blend the two captured simulation endpoints
     */
    void sync(boolean interpolated) {
        if (geometry.points() != boundary) throw new IllegalStateException("Soft geometry perimeter was replaced");
        float alpha = interpolated ? world.getInterpolationAlpha() : 1;
        for (int i = 0; i < boundary.length; i++) {
            world.toScreen(previous[i * 2] + (current[i * 2] - previous[i * 2]) * alpha, previous[i * 2 + 1] + (current[i * 2 + 1] - previous[i * 2 + 1]) * alpha, boundary[i]);
        }
    }

    /**
     * Releases the explicitly owned native capture buffer exactly once.
     */
    void release() {
        destroyed = true;
        if (positions != null) MemoryUtil.memFree(positions);
        positions = null;
        SoftBodySharedSettings owned = mesh;
        mesh = null;
        if (owned != null) owned.close();
    }

    /**
     * Reports whether native ownership has ended, including after world closure.
     * @return whether this handle was destroyed
     */
    public boolean isDestroyed() {
        world.checkOwner();
        return destroyed;
    }

    @Override
    public void close() {
        world.destroyBody(this);
    }
}
