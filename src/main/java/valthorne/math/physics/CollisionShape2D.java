package valthorne.math.physics;

import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.CylinderShape;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RotatedTranslatedShape;
import com.github.stephengold.joltjni.Shape;
import com.github.stephengold.joltjni.Vec3;

/**
 * Immutable, reusable description of a centered planar collision shape in
 * meters. Descriptions own no native resources and may be shared between body
 * settings and worlds. Native geometry is allocated only during body creation.
 *
 * <p>Boxes use their full width and height. Circles use a cylinder rotated so
 * its axis follows Z, preserving disk inertia and a circular XY cross section.
 * All shapes have a one-meter extrusion along Z; bodies remain centered at Z=0
 * and cannot tilt. This is constrained 3D collision detection, so native contact
 * tolerances still apply. Use meter-scale dimensions rather than pixels.</p>
 */
public final class CollisionShape2D {

    /**
     * Common extrusion keeps every planar collider overlapping along Z.
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
        if (!circle) return new BoxShape(new Vec3(width * 0.5f, height * 0.5f, HALF_DEPTH), 0);
        float quarterTurn = (float) Math.sqrt(0.5);
        try (CylinderShape cylinder = new CylinderShape(HALF_DEPTH, width, 0)) {
            return new RotatedTranslatedShape(new Vec3(), new Quat(quarterTurn, 0, 0, quarterTurn), cylinder);
        }
    }

    /**
     * Tests exact containment in the centered planar descriptor geometry.
     *
     * @param x shape-local horizontal coordinate in meters
     * @param y shape-local vertical coordinate in meters
     * @return whether the point is on or inside the shape boundary
     */
    boolean contains(double x, double y) {
        if (circle) return x * x + y * y <= (double) width * width;
        return Math.abs(x) <= width * 0.5 && Math.abs(y) <= height * 0.5;
    }
}
