package valthorne.math.physics;

import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.Shape;
import com.github.stephengold.joltjni.SphereShape;
import com.github.stephengold.joltjni.Vec3;

/**
 * Immutable, reusable description of a centered planar collision shape in
 * meters. Descriptions own no native resources and may be shared between body
 * settings and worlds. Native geometry is allocated only during body creation.
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

    private final boolean circle; // Whether the descriptor represents a disk rather than a box.
    private final float width; // Full box width or circle radius, in meters.
    private final float height; // Full box height in meters; unused for circles.

    /**
     * Stores dimensions already validated by the public factories.
     *
     * @param circle whether to build a disk
     * @param width full width or radius in meters
     * @param height full box height in meters
     */
    private CollisionShape2D(boolean circle, float width, float height) {
        this.circle = circle;
        this.width = width;
        this.height = height;
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
        return new CollisionShape2D(false, width, height);
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
        return new CollisionShape2D(true, radius, 0);
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
        return Math.abs(cosine * x + sine * y) <= width * 0.5 && Math.abs(cosine * y - sine * x) <= height * 0.5;
    }
}
