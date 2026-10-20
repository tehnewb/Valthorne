package valthorne.math.physics;

import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.ConvexHullShapeSettings;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.Shape;
import com.github.stephengold.joltjni.SphereShape;
import com.github.stephengold.joltjni.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable, reusable description of a centered planar collision shape in
 * meters. Descriptions own no native resources and may be shared between body
 * settings and worlds. Convex polygons use copied local XY coordinates and a
 * sharp one-meter extrusion; concave outlines are rejected. Native geometry is allocated only during body creation.
 *
 * <p>Boxes use their full width and height with a one-meter extrusion along Z.
 * Circles use native sphere collision geometry: its cross section at Z=0 is the
 * exact circle, and Jolt can use specialized sphere collision algorithms. Dynamic
 * circles scale native inertia by 5/4 to preserve the disk's Z-axis inertia,
 * one half of mass times radius squared. Other rotational axes are locked.
 * Bodies remain centered at Z=0 and cannot tilt. This is constrained 3D
 * collision detection, so native contact
 * tolerances still apply. Use meter-scale dimensions rather than pixels.</p>
 */
public final class CollisionShape2D {

    /**
     * Rectangle half-depth keeps box geometry centered across the XY plane.
     */
    private static final float HALF_DEPTH = 0.5f;

    /**
     * Millimeter-scale minimum avoids degenerate native half extents.
     */
    private static final float MIN_DIMENSION = 0.001f;

    private final float[] vertices; // Optional immutable counterclockwise convex outline in local meters.
    private final boolean circle; // Whether the descriptor represents a disk rather than a box.
    private final float width; // Full box width or circle radius, in meters.
    private final float height; // Full box height in meters; unused for circles.

    /**
     * Stores dimensions already validated by the public factories.
     *
     * @param circle whether to build a disk
     * @param width full width or radius in meters
     * @param height full box height in meters
     * @param vertices validated convex outline, or null for circles and boxes
     */
    private CollisionShape2D(boolean circle, float width, float height, float[] vertices) {
        this.vertices = vertices;
        this.circle = circle;
        this.width = width;
        this.height = height;
    }

    /**
     * Describes a convex polygon using local XY meter coordinates. Both winding
     * directions are accepted; the origin is the body's rotation pivot. Concave,
     * degenerate and self-intersecting outlines are rejected rather than enlarged.
     * @param coordinates interleaved X/Y coordinates, copied during construction
     * @return immutable collision descriptor
     */
    public static CollisionShape2D polygon(float... coordinates) {
        /*
         * Validation and copying happen once, keeping simulation and point queries allocation-free.
         */
        return new CollisionShape2D(false, 0, 0, validatePolygon(coordinates));
    }

    /**
     * Describes a centered rectangle with sharp corners.
     *
     * @param width full width in meters, at least 0.001
     * @param height full height in meters, at least 0.001
     * @return immutable box descriptor
     * @throws IllegalArgumentException if either dimension is too small or nonfinite
     */
    public static CollisionShape2D box(float width, float height) {
        /*
         * Descriptions stay independent of the native runtime so configuration
         * can be assembled before a world is initialized.
         */
        dimension(width, "width");
        dimension(height, "height");
        return new CollisionShape2D(false, width, height, null);
    }

    /**
     * Describes a centered circle.
     *
     * @param radius radius in meters, at least 0.001
     * @return immutable circle descriptor
     * @throws IllegalArgumentException if the radius is too small or nonfinite
     */
    public static CollisionShape2D circle(float radius) {
        /*
         * Store the radius directly; no native shape exists until body creation.
         */
        dimension(radius, "radius");
        return new CollisionShape2D(true, radius, 0, null);
    }

    /**
     * Rejects dimensions below the supported native geometry scale.
     *
     * @param value dimension in meters
     * @param name dimension name used in failures
     */
    private static void dimension(float value, String name) {
        /*
         * Half extents must remain nonzero after conversion to native floats.
         */
        if (!(value >= MIN_DIMENSION) || !Float.isFinite(value))
            throw new IllegalArgumentException(name + " must be finite and at least " + MIN_DIMENSION + " meters");
    }

    /**
     * Allocates a native shape for one body. The caller must close its reference
     * after passing it to body creation; Jolt retains the body's own reference.
     *
     * @return newly owned native shape
     */
    Shape createNative() {
        if (vertices != null) return createPolygon();
        if (circle) return new SphereShape(width);
        return new BoxShape(new Vec3(width * 0.5f, height * 0.5f, HALF_DEPTH), 0);
    }

    /**
     * Converts sphere inertia (2/5 mr²) into planar disk inertia (1/2 mr²).
     * The unused X/Y rotational axes remain locked by the body's planar DOFs.
     *
     * @return native inertia multiplier, or one for box geometry
     */
    float getInertiaMultiplier() {
        return circle ? 1.25f : 1;
    }

    /**
     * Returns the exact disk radius for planar soft-boundary contacts.
     * @return radius in meters, or zero for another shape kind
     */
    float circleRadius() {
        return circle ? width : 0;
    }

    /**
     * Tests exact containment relative to the center of a rotated shape.
     * Circles skip rotation because their containment is orientation-independent.
     *
     * @param x horizontal displacement from the center in meters
     * @param y vertical displacement from the center in meters
     * @param angle counterclockwise shape orientation in radians
     * @return whether the point is on or inside the shape boundary
     */
    boolean contains(double x, double y, float angle) {
        if (circle) return x * x + y * y <= (double) width * width;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        if (vertices != null) {
            double localX = cosine * x + sine * y;
            y = cosine * y - sine * x;
            x = localX;
            for (int i = 0; i < vertices.length; i += 2) {
                int next = (i + 2) % vertices.length;
                double dx = (double) vertices[next] - vertices[i];
                double dy = (double) vertices[next + 1] - vertices[i + 1];
                if (dx * (y - vertices[i + 1]) - dy * (x - vertices[i]) < -1e-12) return false;
            }
            return true;
        }
        return Math.abs(cosine * x + sine * y) <= width * 0.5 && Math.abs(cosine * y - sine * x) <= height * 0.5;
    }

    /**
     * Copies a convex boundary, removing adjacent duplicates within four coordinate
     * ULPs and normalizing winding. Validation happens only during construction.
     * @param coordinates finite interleaved local XY meter coordinates
     * @return immutable counterclockwise snapshot with 3 to 128 vertices
     * @throws IllegalArgumentException for degenerate, concave or intersecting outlines
     */
    private static float[] validatePolygon(float[] coordinates) {
        /*
         * Check every vertex against each boundary half plane to reject concavity
         * and crossings. Reuse the copied array when no duplicates were removed.
         */
        Objects.requireNonNull(coordinates, "coordinates");
        if (coordinates.length < 6 || (coordinates.length & 1) != 0)
            throw new IllegalArgumentException("A polygon requires at least three XY pairs");
        float magnitude = 0;
        for (float coordinate : coordinates) {
            PhysicsValidation2D.finite(coordinate, "vertex coordinate");
            magnitude = Math.max(magnitude, Math.abs(coordinate));
        }
        double tolerance = Math.max(1e-9, Math.ulp(magnitude) * 4.0);
        float[] copy = new float[coordinates.length];
        int size = 0;
        for (int i = 0; i < coordinates.length; i += 2) {
            if (size != 0 && Math.abs((double) copy[size - 2] - coordinates[i]) <= tolerance && Math.abs((double) copy[size - 1] - coordinates[i + 1]) <= tolerance) continue;
            copy[size++] = coordinates[i];
            copy[size++] = coordinates[i + 1];
        }
        if (size > 2 && Math.abs((double) copy[0] - copy[size - 2]) <= tolerance && Math.abs((double) copy[1] - copy[size - 1]) <= tolerance) size -= 2;
        if (size < 6 || size > 256)
            throw new IllegalArgumentException("A polygon requires 3 to 128 distinct boundary vertices");
        double area = 0;
        for (int i = 0; i < size; i += 2) {
            int next = (i + 2) % size;
            area += (double) copy[i] * copy[next + 1] - (double) copy[next] * copy[i + 1];
        }
        if (Math.abs(area) < 0.000001)
            throw new IllegalArgumentException("Polygon area must be at least 0.0000005 square meters");
        double direction = area > 0 ? 1 : -1;
        for (int i = 0; i < size; i += 2) {
            int next = (i + 2) % size;
            double dx = (double) copy[next] - copy[i];
            double dy = (double) copy[next + 1] - copy[i + 1];
            for (int j = 0; j < size; j += 2) {
                double cross = dx * ((double) copy[j + 1] - copy[i + 1]) - dy * ((double) copy[j] - copy[i]);
                if (cross * direction < -(Math.abs(dx) + Math.abs(dy)) * tolerance)
                    throw new IllegalArgumentException("Polygon must have a convex, non-intersecting boundary");
                if (j != i && copy[j] == copy[i] && copy[j + 1] == copy[i + 1])
                    throw new IllegalArgumentException("Polygon contains a repeated non-adjacent vertex");
            }
        }
        float[] points = size == copy.length ? copy : Arrays.copyOf(copy, size);
        if (area < 0) {
            for (int i = 0, j = size - 2; i < j; i += 2, j -= 2) {
                float x = points[i];
                float y = points[i + 1];
                points[i] = points[j];
                points[i + 1] = points[j + 1];
                points[j] = x;
                points[j + 1] = y;
            }
        }
        return points;
    }

    /**
     * Builds a sharp convex prism using one temporary packed native buffer.
     * @return independently owned native shape reference
     * @throws IllegalArgumentException if Jolt rejects the hull
     */
    private Shape createPolygon() {
        FloatBuffer hullPoints = MemoryUtil.memAllocFloat(vertices.length * 3);
        try {
            for (int i = 0; i < vertices.length; i += 2) {
                hullPoints.put(vertices[i])
                        .put(vertices[i + 1])
                        .put(-0.5f);
                hullPoints.put(vertices[i])
                        .put(vertices[i + 1])
                        .put(0.5f);
            }
            hullPoints.flip();
            try (ConvexHullShapeSettings settings = new ConvexHullShapeSettings(vertices.length, hullPoints, 0); ShapeResult result = settings.create()) {
                if (!result.isValid()) throw new IllegalArgumentException("Jolt rejected polygon: " + result.getError());
                try (ShapeRefC reference = result.get()) {
                    // The concrete Shape wrapper owns its own native reference independently of the result.
                    return (Shape) reference.getPtr();
                }
            }
        } finally {
            MemoryUtil.memFree(hullPoints);
        }
    }
}
