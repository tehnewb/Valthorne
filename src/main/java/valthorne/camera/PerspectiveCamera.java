package valthorne.camera;

/**
 * Perspective camera with a configurable vertical field of view in degrees.
 * The default angle is 67 degrees; aspect ratio comes from the viewport dimensions
 * supplied to {@link #rebuild(float, float)}. Changing the angle updates configuration
 * only, so rebuild before using the projection, frustum, or picking calculations.
 * Camera pose and clip-plane behavior are inherited from {@link Camera3D}.
 *
 * @author Albert Beaupre
 */
public final class PerspectiveCamera extends Camera3D {

    private float fieldOfViewDegrees = 67f; // Vertical perspective angle used at the next rebuild.

    /**
     * Returns the configured vertical view angle. The current projection may still
     * reflect an earlier value if the camera has not been rebuilt since a change.
     *
     * @return vertical field of view in degrees
     */
    public float getFieldOfViewDegrees() {
        return fieldOfViewDegrees;
    }

    /**
     * Stores the vertical view angle without rebuilding derived camera state.
     * Supply a finite angle strictly between zero and 180 degrees. Range comparisons
     * reject out-of-range values and infinities but do not explicitly reject NaN.
     *
     * @param fieldOfViewDegrees vertical field of view in degrees
     * @throws IllegalArgumentException if the angle is at most zero or at least 180
     */
    public void setFieldOfViewDegrees(float fieldOfViewDegrees) {
        if (fieldOfViewDegrees <= 0f || fieldOfViewDegrees >= 180f) {
            throw new IllegalArgumentException("fieldOfViewDegrees must be in (0, 180)");
        }
        this.fieldOfViewDegrees = fieldOfViewDegrees;
    }

    /**
     * Replaces the projection with a perspective matrix using the configured angle
     * and clip planes. Called by the base camera's rebuild operation; does not update
     * the view matrix, inverse matrix, or frustum itself.
     *
     * @param viewportWidth  viewport width used to calculate the aspect ratio
     * @param viewportHeight viewport height used to calculate the aspect ratio
     */
    @Override
    protected void buildProjection(float viewportWidth, float viewportHeight) {
        projection.setPerspective((float) Math.toRadians(fieldOfViewDegrees), viewportWidth / viewportHeight, near, far);
    }
}
