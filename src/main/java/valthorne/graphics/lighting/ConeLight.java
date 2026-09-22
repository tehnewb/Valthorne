package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import org.joml.Vector2f;

/**
 * Directional two-dimensional light whose rays cover an angular sector. The
 * center direction is counterclockwise from positive X; the aperture is the full
 * width, clamped to one through 179 degrees. Uniform rays are supplemented with
 * samples beside nearby blocking vertices to preserve silhouette corners.
 * Updates reuse endpoint storage and run only while dirty and active.
 *
 * @author Albert Beaupre
 */
public final class ConeLight extends VertexCastLight {

    /**
     * Full revolution used to normalize directions and unwrap vertex angles.
     */
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private float directionRadians; // Center direction counterclockwise from positive X, normalized to one turn.
    private float coneRadians; // Full aperture constrained to the supported one-to-179-degree range.

    /**
     * Creates an active, dirty cone light with copied color and borrowed handler.
     * Construction does not register the light; add it to the handler separately.
     *
     * @param rayHandler       handler supplying the occlusion world
     * @param rays             base sample count, at least three
     * @param color            light color to copy
     * @param distance         radial reach in world units
     * @param x                world center X
     * @param y                world center Y
     * @param directionDegrees counterclockwise center direction from positive X
     * @param coneDegrees      full angular width, clamped to one through 179 degrees
     * @throws NullPointerException     if handler or color is null
     * @throws IllegalArgumentException if rays is below three
     */
    public ConeLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y, float directionDegrees, float coneDegrees) {
        super(rayHandler, rays, color, distance, x, y);
        setDirectionDegrees(directionDegrees);
        setConeDegrees(coneDegrees);
    }

    /**
     * Rebuilds active, dirty geometry using uniform, boundary, and occluder-vertex
     * angles. Sorting and compaction precede ray casting; a successful rebuild
     * clears dirty state. Inactive or clean lights retain their previous results.
     */
    @Override
    public void update() {
        if (!active || !dirty) {
            return;
        }

        float start = directionRadians - coneRadians * 0.5f;
        float end = directionRadians + coneRadians * 0.5f;

        resetAngles();
        addBaseAngles(start, end);
        addBoundaryAngles(start, end);
        addOccluderVertexAngles(start, end);
        rebuildFromAngles();
    }

    /**
     * Writes a uniformly sampled, unoccluded base-ray endpoint. The first and
     * last rays lie on the cone edges; extra vertex samples are added by update.
     *
     * @param index  base sample index between zero and rays minus one
     * @param output destination with at least two elements for world X and Y
     */
    @Override
    protected void computeRayEnd(int index, float[] output) {
        float start = directionRadians - coneRadians * 0.5f;
        float t = rays <= 1 ? 0.5f : index / (float) (rays - 1);
        float angle = start + coneRadians * t;
        output[0] = x + (float) Math.cos(angle) * distance;
        output[1] = y + (float) Math.sin(angle) * distance;
    }

    /**
     * Appends at least three evenly spaced samples including both sector edges.
     * Duplicates remain until the inherited compaction step.
     *
     * @param start unwrapped lower boundary in radians
     * @param end   unwrapped upper boundary in radians
     */
    private void addBaseAngles(float start, float end) {
        int baseCount = Math.max(3, rays);
        for (int i = 0; i < baseCount; i++) {
            float t = i / (float) (baseCount - 1);
            addAngle(start + (end - start) * t);
        }
    }

    /**
     * Adds both sector edges and slightly inward samples to stabilize the cone
     * boundaries when vertex samples are merged.
     *
     * @param start lower boundary in radians
     * @param end   upper boundary in radians
     */
    private void addBoundaryAngles(float start, float end) {
        addAngle(start);
        addAngle(start + EPSILON);
        addAngle(end - EPSILON);
        addAngle(end);
    }

    /**
     * Adds rays through and beside category-compatible vertices within the light
     * radius. Directions are unwrapped around the center before the cone test,
     * so a sector crossing a full-turn boundary is handled consistently.
     *
     * @param start inclusive unwrapped lower boundary
     * @param end   inclusive unwrapped upper boundary
     */
    private void addOccluderVertexAngles(float start, float end) {
        float maxDistanceSquared = distance * distance;

        for (LightOccluder occluder : getLightOccluders()) {
            if (occluder == null || !occluder.blocks(this)) {
                continue;
            }

            Vector2f[] points = occluder.points();
            if (points == null || points.length == 0 || !isPotentialOccluder(points, maxDistanceSquared)) {
                continue;
            }

            for (Vector2f point : points) {
                if (point == null) {
                    continue;
                }

                float dx = point.x() - x;
                float dy = point.y() - y;
                float pointDistanceSquared = dx * dx + dy * dy;
                if (pointDistanceSquared > maxDistanceSquared) {
                    continue;
                }

                float angle = unwrapNear((float) Math.atan2(dy, dx), directionRadians);
                addAngleIfInside(angle - EPSILON, start, end);
                addAngleIfInside(angle, start, end);
                addAngleIfInside(angle + EPSILON, start, end);
            }
        }
    }

    /**
     * Appends a candidate only within the inclusive sector interval. This helper
     * does not normalize or deduplicate angles.
     *
     * @param angle candidate radians
     * @param start unwrapped lower boundary
     * @param end   unwrapped upper boundary
     */
    private void addAngleIfInside(float angle, float start, float end) {
        if (angle >= start && angle <= end) {
            addAngle(angle);
        }
    }

    /**
     * Shifts a finite angle by full turns until it lies within half a revolution
     * of the finite reference, preserving its geometric direction.
     *
     * @param angle     input radians
     * @param reference reference radians
     * @return equivalent angle near the reference
     */
    private float unwrapNear(float angle, float reference) {
        while (angle - reference > Math.PI) {
            angle -= TWO_PI;
        }
        while (angle - reference < -Math.PI) {
            angle += TWO_PI;
        }
        return angle;
    }

    /**
     * Reads the stored center direction without rebuilding endpoints.
     *
     * @return counterclockwise radians from positive X, normally in [0, 2 pi)
     */
    public float getDirectionRadians() {
        return directionRadians;
    }

    /**
     * Normalizes the direction to one revolution and marks geometry dirty even
     * when the resulting direction is unchanged. Supply a finite angle.
     *
     * @param directionRadians counterclockwise radians from positive X
     */
    public void setDirectionRadians(float directionRadians) {
        this.directionRadians = normalize(directionRadians);
        dirty = true;
    }

    /**
     * Converts the stored center direction to degrees without modifying it.
     *
     * @return counterclockwise degrees from positive X, normally in [0, 360)
     */
    public float getDirectionDegrees() {
        return (float) Math.toDegrees(directionRadians);
    }

    /**
     * Converts degrees through the radian setter, wrapping the direction and
     * marking endpoints for a subsequent rebuild.
     *
     * @param directionDegrees finite counterclockwise degrees from positive X
     */
    public void setDirectionDegrees(float directionDegrees) {
        setDirectionRadians((float) Math.toRadians(directionDegrees));
    }

    /**
     * Adds a relative rotation through the direction setter, wrapping at a full
     * turn and marking the endpoint geometry dirty.
     *
     * @param deltaRadians finite displacement; positive rotates counterclockwise
     */
    public void rotateRadians(float deltaRadians) {
        setDirectionRadians(directionRadians + deltaRadians);
    }

    /**
     * Converts and applies a relative rotation to the stored center direction.
     * The new direction takes effect on the next active geometry update.
     *
     * @param deltaDegrees finite displacement; positive rotates counterclockwise
     */
    public void rotateDegrees(float deltaDegrees) {
        rotateRadians((float) Math.toRadians(deltaDegrees));
    }

    /**
     * Reads the full stored aperture, rather than its half-angle.
     *
     * @return cone width in radians
     */
    public float getConeRadians() {
        return coneRadians;
    }

    /**
     * Clamps the full aperture to the radian equivalents of one and 179 degrees
     * and marks geometry dirty. NaN is not rejected and should not be supplied.
     *
     * @param coneRadians requested full width in radians
     */
    public void setConeRadians(float coneRadians) {
        float min = (float) Math.toRadians(1f);
        float max = (float) Math.toRadians(179f);
        this.coneRadians = Math.max(min, Math.min(max, coneRadians));
        dirty = true;
    }

    /**
     * Converts the stored full aperture to degrees without updating geometry.
     *
     * @return cone width in degrees
     */
    public float getConeDegrees() {
        return (float) Math.toDegrees(coneRadians);
    }

    /**
     * Converts the full aperture to radians and applies the supported clamp,
     * marking endpoint geometry dirty.
     *
     * @param coneDegrees requested full width in degrees
     */
    public void setConeDegrees(float coneDegrees) {
        setConeRadians((float) Math.toRadians(coneDegrees));
    }

    /**
     * Wraps an angle using a full-turn remainder, shifting negative remainders
     * into the nonnegative range. Non-finite values produce NaN.
     *
     * @param angle input radians
     * @return equivalent angle in [0, 2 pi), or NaN
     */
    private float normalize(float angle) {
        angle %= TWO_PI;
        if (angle < 0f) {
            angle += TWO_PI;
        }
        return angle;
    }
}
