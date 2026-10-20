package valthorne.math.physics;

import org.joml.Vector2f;
import valthorne.math.geometry.Circle;
import valthorne.math.geometry.Polygon;
import valthorne.math.geometry.Rectangle;
import valthorne.math.geometry.RoundedRectangle;
import valthorne.math.geometry.Shape;
import valthorne.math.geometry.Triangle;

import java.util.Objects;

/**
 * Owner-thread bridge between one geometry shape and a rigid body. Collision
 * dimensions and unrotated polygon offsets are captured once. Settings and the
 * body share this immutable bridge; scratch storage belongs to the world. Writes
 * reuse the original shape and vectors. Geometry rotates around its bounding-box
 * center in degrees, whereas physics uses world radians. An optional pixel
 * world configuration handles scale, camera origin and Y reflection. Shape dimensions must
 * remain unchanged while attached; recreate the body after editing its outline.
 */
final class PhysicsGeometry2D {
    final Shape shape; // Original geometry object retained for rendering.
    final PhysicsWorld2D pixelWorld; // Owning pixel-configured world, or null for geometry expressed in meters.
    final float centerX; // Initial unrotated bounding-box center in geometry units.
    final float centerY; // Initial unrotated bounding-box center in geometry units.
    private final float[] offsets; // Frozen interleaved vertex offsets for polygons and triangles, or null.
    private final float halfWidth; // Horizontal anchor-to-center offset for anchored shapes.
    private final float halfHeight; // Vertical anchor-to-center offset for anchored shapes.

    /**
     * Captures a supported shape's unrotated outline and rotation pivot.
     * @param shape circle, rectangle, rounded rectangle, triangle or convex polygon
     * @param pixelWorld optional coordinate mapping, null for meter geometry
     */
    PhysicsGeometry2D(Shape shape, PhysicsWorld2D pixelWorld) {
        this.shape = Objects.requireNonNull(shape, "shape");
        this.pixelWorld = pixelWorld;
        if (!(shape instanceof Circle || shape instanceof Rectangle || shape instanceof RoundedRectangle || shape instanceof Triangle || shape instanceof Polygon))
            throw new IllegalArgumentException("Unsupported geometry shape: " + shape.getClass().getSimpleName());
        Vector2f center = pixelWorld == null ? new Vector2f() : pixelWorld.geometryPosition();
        readCenter(center);
        centerX = center.x;
        centerY = center.y;
        if (shape instanceof Circle circle) {
            halfWidth = circle.getRadius();
            halfHeight = halfWidth;
        } else if (shape instanceof Rectangle rectangle) {
            halfWidth = rectangle.getWidth() * 0.5f;
            halfHeight = rectangle.getHeight() * 0.5f;
        } else if (shape instanceof RoundedRectangle rectangle) {
            halfWidth = rectangle.getWidth() * 0.5f;
            halfHeight = rectangle.getHeight() * 0.5f;
        } else {
            halfWidth = 0;
            halfHeight = 0;
        }
        if (shape instanceof Triangle || shape instanceof Polygon) {
            Vector2f[] points = shape.points();
            offsets = new float[points.length * 2];
            for (int i = 0; i < points.length; i++) {
                offsets[i * 2] = points[i].x - centerX;
                offsets[i * 2 + 1] = points[i].y - centerY;
            }
        } else {
            offsets = null;
        }
    }

    /**
     * Creates the collision snapshot once when body settings are assembled.
     * @return reusable immutable descriptor with meter dimensions
     */
    CollisionShape2D collision() {
        if (shape instanceof Circle) return CollisionShape2D.circle(length(halfWidth));
        if (shape instanceof Rectangle) return CollisionShape2D.box(length(Math.abs(halfWidth * 2)), length(Math.abs(halfHeight * 2)));
        Vector2f[] points = shape.points();
        float[] meters = new float[points.length * 2];
        boolean reflected = pixelWorld != null && pixelWorld.toWorldRotation(90) < 0;
        for (int i = 0; i < points.length; i++) {
            meters[i * 2] = length(points[i].x - centerX);
            meters[i * 2 + 1] = length(points[i].y - centerY) * (reflected ? -1 : 1);
        }
        return CollisionShape2D.polygon(meters);
    }

    /**
     * Converts a geometry length or displacement without applying camera origin.
     * @param value geometry units
     * @return world meters
     */
    private float length(float value) {
        return pixelWorld == null ? value : pixelWorld.toMeters(value);
    }

    /**
     * Reads the unrotated bounding-box pivot, independent of cached center edits.
     * @param destination reusable pivot destination
     */
    private void readCenter(Vector2f destination) {
        if (shape instanceof Circle circle) {
            destination.set(circle.getX() + circle.getRadius(), circle.getY() + circle.getRadius());
        } else if (shape instanceof Rectangle rectangle) {
            destination.set(rectangle.getX() + rectangle.getWidth() * 0.5f, rectangle.getY() + rectangle.getHeight() * 0.5f);
        } else if (shape instanceof RoundedRectangle rectangle) {
            destination.set(rectangle.getX() + rectangle.getWidth() * 0.5f, rectangle.getY() + rectangle.getHeight() * 0.5f);
        } else {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (Vector2f point : shape.points()) {
                Objects.requireNonNull(point, "shape vertex");
                PhysicsValidation2D.finite(point.x, "vertex x");
                PhysicsValidation2D.finite(point.y, "vertex y");
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }
            destination.set((float) (((double) minX + maxX) * 0.5), (float) (((double) minY + maxY) * 0.5));
        }
        PhysicsValidation2D.finite(destination.x, "geometry center x");
        PhysicsValidation2D.finite(destination.y, "geometry center y");
    }

    /**
     * Writes a captured physics pose into the existing geometry without allocating.
     * @param body live owner-thread body
     * @param interpolated whether to use frame interpolation instead of the current pose
     */
    void sync(RigidBody2D body, boolean interpolated) {
        Vector2f scratch = body.world.geometryPosition();
        body.copyPosition(scratch, interpolated);
        if (pixelWorld != null) pixelWorld.toScreen(scratch.x, scratch.y, scratch);
        float x = scratch.x;
        float y = scratch.y;
        if (shape instanceof Circle circle) circle.setPosition(x - halfWidth, y - halfHeight);
        else if (shape instanceof Rectangle rectangle) rectangle.setPosition(x - halfWidth, y - halfHeight);
        else if (shape instanceof RoundedRectangle rectangle) rectangle.setPosition(x - halfWidth, y - halfHeight);
        else {
            Vector2f[] points = shape.points();
            if (points.length * 2 != offsets.length)
                throw new IllegalStateException("Geometry vertex count changed; recreate its physics body");
            for (int i = 0; i < points.length; i++) points[i].set(x + offsets[i * 2], y + offsets[i * 2 + 1]);
        }
        float radians = interpolated ? body.getInterpolatedRotation() : body.getRotation();
        shape.setRotation(pixelWorld == null ? (float) Math.toDegrees(radians) : pixelWorld.toGeometryRotation(radians));
    }

    /**
     * Teleports physics from the geometry's current anchor and degree rotation.
     * @param body live owner-thread body whose interpolation endpoints are reset
     */
    void push(RigidBody2D body) {
        Vector2f scratch = body.world.geometryPosition();
        readCenter(scratch);
        float radians = pixelWorld == null ? (float) Math.toRadians(shape.getRotation()) : pixelWorld.toWorldRotation(shape.getRotation());
        if (pixelWorld != null) pixelWorld.toWorld(scratch.x, scratch.y, scratch);
        body.setTransform(scratch.x, scratch.y, radians);
    }
}
