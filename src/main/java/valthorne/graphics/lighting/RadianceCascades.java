package valthorne.graphics.lighting;

/**
 * Standalone radiance cascade GI helper for callers that want bounce lighting
 * without going through {@link RayHandler}.
 */
public final class RadianceCascades {

    private final QuadMesh quad;
    private final LightMapRenderer compositor;
    private final RadianceCascadeRenderer renderer;

    public RadianceCascades(int width, int height) {
        this(width, height, RadianceCascadeConfig.defaults());
    }

    public RadianceCascades(int width, int height, RadianceCascadeConfig config) {
        this.quad = new QuadMesh();
        this.compositor = new LightMapRenderer(quad);
        this.renderer = new RadianceCascadeRenderer(width, height, config);
    }

    public static boolean isSupported() {
        return RadianceCascadeRenderer.isSupported();
    }

    public void update(GISceneBuffer sceneBuffer) {
        renderer.render(sceneBuffer);
    }

    /**
     * Additively blends the resolved irradiance onto the currently bound
     * framebuffer. The caller is responsible for binding the intended target.
     */
    public void blendOnto(float strength) {
        compositor.bake(renderer.getIrradianceTextureId(), strength);
    }

    public int getIrradianceTextureId() {
        return renderer.getIrradianceTextureId();
    }

    public void setExposure(float exposure) {
        renderer.setExposure(exposure);
    }

    public float getExposure() {
        return renderer.getExposure();
    }

    public RadianceCascadeConfig getConfig() {
        return renderer.getConfig();
    }

    public void resize(int width, int height) {
        renderer.resize(width, height);
    }

    public void dispose() {
        renderer.dispose();
        compositor.dispose();
        quad.dispose();
    }
}
