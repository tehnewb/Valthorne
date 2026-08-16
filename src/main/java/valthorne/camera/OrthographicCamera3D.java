package valthorne.camera;

/**
 * 3D orthographic camera for isometric or editor-style scenes.
 */
public final class OrthographicCamera3D extends Camera3D {

    private float worldHeight = 10f;
    private float zoom = 1f;

    public float getWorldHeight() {
        return worldHeight;
    }

    public void setWorldHeight(float worldHeight) {
        if (worldHeight <= 0f) throw new IllegalArgumentException("worldHeight must be > 0");
        this.worldHeight = worldHeight;
    }

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        if (zoom <= 0f) throw new IllegalArgumentException("zoom must be > 0");
        this.zoom = zoom;
    }

    @Override
    protected void buildProjection(float viewportWidth, float viewportHeight) {
        float halfHeight = (worldHeight * 0.5f) / zoom;
        float halfWidth = halfHeight * (viewportWidth / viewportHeight);
        projection.ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far);
    }
}
