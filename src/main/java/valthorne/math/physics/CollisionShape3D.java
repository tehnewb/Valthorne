package valthorne.math.physics;

import com.github.stephengold.joltjni.*;
import valthorne.graphics.model.Model3D;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.function.Supplier;
import java.util.Objects;

/**
 * Reusable collision geometry definition for bodies created by {@link PhysicsWorld3D}.
 * Primitive dimensions use meters and full extents, except for explicitly named
 * radii. Boxes use X width, Y depth and Z height; capsules and cylinders have
 * their longitudinal axis along Z. Model-based geometry retains model-local
 * coordinates, so body settings supply its placement in the world.
 *
 * <h2>Creating a Body</h2>
 * <pre>{@code
 * CollisionShape3D shape = CollisionShape3D.box(1f, 1f, 2f);
 * BodySettings3D settings = new BodySettings3D(shape, MotionType3D.DYNAMIC)
 *         .setPosition(0, 0, 5);
 * RigidBody3D body = world.createBody(settings);
 * }</pre>
 *
 * <p>A definition can be reused for multiple bodies and worlds. Each native
 * creation invokes its factory again; this object does not hold a shared native
 * shape instance. Model and vector inputs are captured when the definition is
 * made, so subsequent source edits do not update collision geometry. Convex
 * shapes support moving bodies; triangle meshes require static motion.</p>
 *
 * <p>Java-side validation checks dimensions and input coordinates. Hull and mesh
 * construction also performs native validation when a body requests its shape;
 * supplying enough vertices does not by itself establish valid hull geometry.</p>
 *
 * @author Albert Beaupre
 */
public final class CollisionShape3D {
    private final Supplier<Shape> factory; // Creates an independent native shape wrapper for each body request.
    private final boolean staticOnly; // Restricts triangle-mesh definitions to static body motion.

    /**
     * Captures the native creation operation and its body-motion restriction.
     * Construction stores the factory without invoking it or creating a body.
     *
     * @param factory    the operation used for each native shape request
     * @param staticOnly whether moving bodies must reject this definition
     */
    private CollisionShape3D(Supplier<Shape> factory, boolean staticOnly) {
        this.factory = factory;
        this.staticOnly = staticOnly;
    }

    /**
     * Defines a box centered at the body-local origin. Native half extents are
     * derived from the supplied full dimensions; the convex radius is capped at
     * 0.05 meters and one quarter of the smallest dimension.
     *
     * @param width  the full X extent in meters
     * @param depth  the full Y extent in meters
     * @param height the full Z extent in meters
     * @return a reusable box definition supporting all motion categories
     * @throws IllegalArgumentException if any dimension is non-finite or not positive
     */
    public static CollisionShape3D box(float width, float depth, float height) {
        PhysicsMath3D.positive(width, "width");
        PhysicsMath3D.positive(depth, "depth");
        PhysicsMath3D.positive(height, "height");
        return new CollisionShape3D(() -> new BoxShape(new Vec3(width / 2, depth / 2, height / 2), Math.min(.05f, Math.min(width, Math.min(depth, height)) / 4)), false);
    }

    /**
     * Defines a sphere centered at the body-local origin. Radius is retained
     * directly and is not interpreted as a diameter or a model scale factor.
     *
     * @param radius the positive sphere radius in meters
     * @return a reusable sphere definition supporting all motion categories
     * @throws IllegalArgumentException if radius is non-finite or not positive
     */
    public static CollisionShape3D sphere(float radius) {
        PhysicsMath3D.positive(radius, "radius");
        return new CollisionShape3D(() -> new SphereShape(radius), false);
    }

    /**
     * Defines a centered capsule aligned along Z. Height includes both end
     * hemispheres; the cylindrical section has length {@code height - 2 * radius}.
     * A height exactly equal to the diameter produces a sphere instead.
     *
     * @param radius the radius of the cylinder and hemispheres in meters
     * @param height the total tip-to-tip extent in meters
     * @return a reusable capsule, or sphere when no cylindrical section remains
     * @throws IllegalArgumentException if either input is non-finite or not positive,
     *                                  or height is less than twice radius
     */
    public static CollisionShape3D capsule(float radius, float height) {
        PhysicsMath3D.positive(radius, "radius");
        PhysicsMath3D.positive(height, "height");
        if (height < 2 * radius) throw new IllegalArgumentException("Capsule height must be at least twice its radius");
        if (height == 2 * radius) return sphere(radius);
        return new CollisionShape3D(() -> zUp(new CapsuleShape((height - 2 * radius) / 2, radius)), false);
    }

    /**
     * Defines a centered cylinder whose flat ends are perpendicular to Z.
     * Native construction uses half the supplied height and a convex radius
     * capped at 0.05 meters and half the smaller of radius and half-height.
     *
     * @param radius the cylinder radius in meters
     * @param height the full distance between the end planes in meters
     * @return a reusable cylinder definition supporting all motion categories
     * @throws IllegalArgumentException if either input is non-finite or not positive
     */
    public static CollisionShape3D cylinder(float radius, float height) {
        PhysicsMath3D.positive(radius, "radius");
        PhysicsMath3D.positive(height, "height");
        return new CollisionShape3D(() -> zUp(new CylinderShape(height / 2, radius, Math.min(.05f, Math.min(radius, height / 2) / 2))), false);
    }

    /**
     * Wraps a Y-aligned native primitive with a quarter-turn about X so its axis
     * follows Valthorne's Z direction. No translation is applied. The supplied
     * wrapper is closed on exit, including when wrapper construction fails.
     *
     * @param shape the native primitive whose wrapper ownership is consumed
     * @return an owning wrapper for the rotated shape
     */
    private static Shape zUp(Shape shape) {
        try (shape) {
            return new RotatedTranslatedShape(new Vec3(), new Quat((float) Math.sqrt(.5), 0, 0, (float) Math.sqrt(.5)), shape);
        }
    }

    /**
     * Captures points for a convex hull with zero additional convex radius.
     * Coordinates are validated and copied immediately; later changes to the
     * input array or vectors have no effect. At least four entries are required,
     * but duplicates or degenerate arrangements can still fail native creation.
     *
     * @param vertices the body-local hull points in meters
     * @return a reusable convex definition suitable for moving bodies
     * @throws NullPointerException     if the array or an element is null
     * @throws IllegalArgumentException if fewer than four points are supplied or
     *                                  a coordinate is non-finite
     */
    public static CollisionShape3D convexHull(Vector3f... vertices) {
        if (vertices.length < 4) throw new IllegalArgumentException("A convex hull needs at least four vertices");
        Vec3[] points = new Vec3[vertices.length];
        for (int i = 0; i < points.length; i++) points[i] = PhysicsMath3D.vector(vertices[i]);
        return new CollisionShape3D(() -> fromSettings(new ConvexHullShapeSettings(points, 0f)), false);
    }

    /**
     * Defines a convex hull from all model triangle corners in model-local
     * coordinates. Shared corners are included repeatedly; no deduplication,
     * instance transform or material information is applied. Concave detail is
     * replaced by the resulting convex envelope, making this useful for moving
     * bodies that cannot use a static triangle mesh.
     *
     * @param model the model whose current triangle positions are captured
     * @return a reusable convex-hull definition
     * @throws NullPointerException     if model or a triangle corner is null
     * @throws IllegalArgumentException if the model is empty, supplies fewer than
     *                                  four corners, or contains non-finite coordinates
     */
    public static CollisionShape3D convexHull(Model3D model) {
        return convexHull(vertices(model));
    }

    /**
     * Captures model triangles for static level collision, preserving corner
     * order and positions relative to the model origin. Only geometry is copied;
     * render materials and instance transforms are not part of the shape.
     * Each native request receives a new direct buffer containing this snapshot.
     *
     * <p>The returned definition requires static body motion. Use
     * {@link #convexHull(Model3D)} when a convex approximation must move.</p>
     *
     * @param model the nonempty model whose triangle coordinates are captured
     * @return a reusable static-only triangle-mesh definition
     * @throws NullPointerException     if model or a triangle corner is null
     * @throws IllegalArgumentException if the model has no triangles or any
     *                                  coordinate is non-finite
     */
    public static CollisionShape3D mesh(Model3D model) {
        Vector3f[] vertices = vertices(model);
        float[] data = new float[vertices.length * 3];
        for (int i = 0; i < vertices.length; i++) {
            PhysicsMath3D.check(vertices[i]);
            data[3 * i] = vertices[i].x();
            data[3 * i + 1] = vertices[i].y();
            data[3 * i + 2] = vertices[i].z();
        }
        return new CollisionShape3D(() -> {
            FloatBuffer buffer = ByteBuffer.allocateDirect(data.length * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            buffer.put(data).flip();
            return fromSettings(new MeshShapeSettings(buffer));
        }, true);
    }

    /**
     * Flattens a nonempty model into consecutive A, B and C triangle corners.
     * The array preserves triangle order and repeated vertices; this helper
     * performs no coordinate validation or additional vector copy. Shape
     * factories validate and capture numeric values before retaining geometry.
     *
     * @param model the source of model-local triangle positions
     * @return a new array with three corner entries per triangle
     * @throws NullPointerException     if model is null
     * @throws IllegalArgumentException if model contains no triangles
     */
    private static Vector3f[] vertices(Model3D model) {
        Objects.requireNonNull(model, "model");
        if (model.getTriangleCount() == 0) throw new IllegalArgumentException("Model must contain triangles");
        Vector3f[] vertices = new Vector3f[model.getTriangleCount() * 3];
        int i = 0;
        for (Model3D.Triangle triangle : model.getTriangles()) {
            vertices[i++] = triangle.a();
            vertices[i++] = triangle.b();
            vertices[i++] = triangle.c();
        }
        return vertices;
    }

    /**
     * Builds a native shape and converts a reported geometry error into an
     * argument exception. Settings, result and temporary reference wrappers are
     * closed during unwinding; the returned owning shape wrapper survives those
     * temporary resources and must be closed by its caller.
     *
     * @param settings the native settings whose ownership is consumed
     * @return the independently owned shape wrapper from the successful result
     * @throws IllegalArgumentException if native construction reports an error
     */
    private static Shape fromSettings(ShapeSettings settings) {
        try (settings; ShapeResult result = settings.create()) {
            if (result.hasError()) throw new IllegalArgumentException("Invalid collision shape: " + result.getError());
            try (ShapeRefC reference = result.get()) {
                // get() creates a separate owning Shape wrapper, retained after the temporary reference closes.
                return (Shape) reference.getPtr();
            }
        }
    }

    /**
     * Materializes this definition for native body creation. The caller must
     * initialize the physics runtime first and close the returned wrapper after
     * passing the shape to native body settings. Factory failures propagate.
     *
     * @return a newly created owning native shape wrapper
     * @throws IllegalArgumentException if hull or mesh settings report invalid geometry
     */
    Shape createNative() {
        return factory.get();
    }

    /**
     * Reports whether body settings must use {@link MotionType3D#STATIC}.
     * This reflects the geometry category, independent of any particular body.
     *
     * @return true for triangle meshes; false for primitive and convex-hull shapes
     */
    public boolean isStaticOnly() {
        return staticOnly;
    }
}
