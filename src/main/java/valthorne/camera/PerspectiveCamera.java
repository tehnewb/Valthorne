package valthorne.camera;

/**
 * Standard perspective 3D camera.
 */
public final class PerspectiveCamera extends Camera3D {

    private float fieldOfViewDegrees = 67f;

    public float getFieldOfViewDegrees() {
        return fieldOfViewDegrees;
    }

    public void setFieldOfViewDegrees(float fieldOfViewDegrees) {
        if (fieldOfViewDegrees <= 0f || fieldOfViewDegrees >= 180f) {
            throw new IllegalArgumentException("fieldOfViewDegrees must be in (0, 180)");
        }
        this.fieldOfViewDegrees = fieldOfViewDegrees;
    }

    @Override
    protected void buildProjection(float viewportWidth, float viewportHeight) {
        projection.perspective((float) Math.toRadians(fieldOfViewDegrees), viewportWidth / viewportHeight, near, far);
    }
}
