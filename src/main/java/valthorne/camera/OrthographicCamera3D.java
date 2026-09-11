package valthorne.camera;

/**
 * Orthographic camera whose visible height is {@code worldHeight / zoom} world
 * units. Width follows the viewport aspect ratio, and the projection is centered
 * on the camera's view axis. Defaults show ten world units vertically at unit zoom.
 * Pose and clip planes come from {@link Camera3D}; call {@link #rebuild(float, float)}
 * after configuration changes to update projection, frustum, and picking state.
 *
 * @author Albert Beaupre
 */
public final class OrthographicCamera3D extends Camera3D {

    private float worldHeight = 10f; // Vertical world-space span before applying zoom.
    private float zoom = 1f; // Magnification divisor applied to the visible world-space span.

    /**
     * Returns the vertical span configured for unit zoom, independent of the actual
     * viewport size and the current zoom multiplier.
     *
     * @return unzoomed vertical extent in world units
     */
    public float getWorldHeight() {
        return worldHeight;
    }

    /**
     * Stores the vertical world-space span at unit zoom without rebuilding matrices.
     * Supply a finite positive value; the implementation rejects nonpositive values
     * but does not separately validate finiteness.
     *
     * @param worldHeight vertical extent in world units before zoom
     * @throws IllegalArgumentException if the extent is zero or negative
     */
    public void setWorldHeight(float worldHeight) {
        if (worldHeight <= 0f) throw new IllegalArgumentException("worldHeight must be > 0");
        this.worldHeight = worldHeight;
    }

    /**
     * Returns the configured magnification. Values above one reduce the visible
     * world-space span; values between zero and one increase it.
     *
     * @return zoom multiplier, initially one
     */
    public float getZoom() {
        return zoom;
    }

    /**
     * Stores magnification for the next rebuild. Supply a finite positive value;
     * the range check rejects zero and negative values but accepts NaN and positive
     * infinity, which do not produce a useful projection.
     *
     * @param zoom divisor of the unzoomed visible world-space span
     * @throws IllegalArgumentException if zoom is zero or negative
     */
    public void setZoom(float zoom) {
        if (zoom <= 0f) throw new IllegalArgumentException("zoom must be > 0");
        this.zoom = zoom;
    }

    /**
     * Replaces the projection with a centered orthographic volume using the zoomed
     * world height, viewport aspect ratio, and configured near and far clip planes.
     * The base rebuild operation updates the remaining derived camera state.
     *
     * @param viewportWidth  viewport width used for the aspect ratio
     * @param viewportHeight viewport height used for the aspect ratio
     */
    @Override
    protected void buildProjection(float viewportWidth, float viewportHeight) {
        float halfHeight = (worldHeight * 0.5f) / zoom;
        float halfWidth = halfHeight * (viewportWidth / viewportHeight);
        projection.setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, near, far);
    }
}
