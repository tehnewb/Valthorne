package valthorne.graphics.lighting;

import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.shader.Shader;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public final class RayHandler {

    private static final String vertexSource = """
            #version 330 core
            layout (location = 0) in vec2 a_position;
            layout (location = 1) in vec2 a_local;
            layout (location = 2) in vec4 a_color;
            
            uniform vec2 u_screenSize;
            
            out vec2 v_local;
            out vec4 v_color;
            
            void main() {
                vec2 ndc = vec2(
                    (a_position.x / u_screenSize.x) * 2.0 - 1.0,
                    (a_position.y / u_screenSize.y) * 2.0 - 1.0
                );
                gl_Position = vec4(ndc, 0.0, 1.0);
                v_local = a_local;
                v_color = a_color;
            }
            """;

    private static final String fragmentSource = """
            #version 330 core
            in vec2 v_local;
            in vec4 v_color;
            
            out vec4 fragColor;
            
            void main() {
                float dist = length(v_local);
                float intensity = 1.0 - dist;
                intensity = clamp(intensity, 0.0, 1.0);
                intensity = smoothstep(0.0, 1.0, intensity);
                intensity = pow(intensity, 1.6);
                intensity *= v_color.a;
                fragColor = vec4(v_color.rgb * intensity, intensity);
            }
            """;

    private final List<Light> lights = new ArrayList<>();
    private final Color ambientLight = new Color(0f, 0f, 0f, 1f);
    private final LightMesh lightMesh;
    private final SoftShadowMesh softShadowMesh;
    private final QuadMesh quad;
    private final Shader shader;
    private final LightMapRenderer lightMapRenderer;

    private RayCastWorld rayCastWorld;
    private int width;
    private int height;
    private int fboId;
    private LightTexture lightMap;

    private RadianceCascadeRenderer giRenderer;
    private GISceneBuffer giScene;
    private float giStrength = 1f;

    public static boolean isGISupported() {
        return RadianceCascadeRenderer.isSupported();
    }

    public RayHandler(int width, int height) {
        this.width = width;
        this.height = height;
        this.lightMesh = new LightMesh(8192);
        this.softShadowMesh = new SoftShadowMesh(8192 * 3);
        this.quad = new QuadMesh();

        shader = new Shader(vertexSource, fragmentSource);
        shader.bindAttribLocation(0, "a_position");
        shader.bindAttribLocation(1, "a_local");
        shader.bindAttribLocation(2, "a_color");
        shader.reload();

        this.lightMapRenderer = new LightMapRenderer(quad);
        createFramebuffer(width, height);
    }

    /**
     * Enables global illumination using Radiance Cascades.
     *
     * <p>Each frame, fill {@code sceneBuffer} with the emissive/transmittance
     * description of your scene before calling {@link #render()}:
     * <ul>
     *   <li>RGB – emissive colour (glowing tiles, lit surfaces)</li>
     *   <li>A   – transmittance: 1.0 = air, 0.0 = solid occluder</li>
     * </ul>
     *
     * @param sceneBuffer the scene description buffer (must remain valid for
     *                    the lifetime of GI rendering)
     */
    public void enableGI(GISceneBuffer sceneBuffer) {
        if (sceneBuffer == null) throw new NullPointerException("sceneBuffer cannot be null");
        validateGISceneBuffer(sceneBuffer);
        disableGI();
        this.giScene = sceneBuffer;
        this.giRenderer = new RadianceCascadeRenderer(width, height);
    }

    public void enableGI(GISceneBuffer sceneBuffer, RadianceCascadeConfig config) {
        if (sceneBuffer == null) throw new NullPointerException("sceneBuffer cannot be null");
        if (config == null) throw new NullPointerException("config cannot be null");
        validateGISceneBuffer(sceneBuffer);
        disableGI();
        this.giScene = sceneBuffer;
        this.giRenderer = new RadianceCascadeRenderer(width, height, config);
    }

    /**
     * Enables global illumination with custom cascade parameters.
     *
     * @param sceneBuffer  the scene description buffer
     * @param cascadeCount number of cascade levels (default 8, max range = baseRange × 2^(count-1))
     * @param baseRays     rays per probe at level 0 (default 4)
     * @param baseSpacing  probe spacing in pixels at level 0 (default 4)
     * @param baseRange    ray interval length in pixels at level 0 (default 4)
     * @param steps        raymarch steps per ray (default 16)
     */
    public void enableGI(GISceneBuffer sceneBuffer, int cascadeCount, int baseRays, float baseSpacing, float baseRange, int steps) {
        if (sceneBuffer == null) throw new NullPointerException("sceneBuffer cannot be null");
        enableGI(sceneBuffer, new RadianceCascadeConfig(
                cascadeCount,
                baseRays,
                baseSpacing,
                baseRange,
                steps,
                RadianceCascadeRenderer.DEFAULT_EXPOSURE
        ));
    }

    public boolean tryEnableGI(GISceneBuffer sceneBuffer) {
        if (!isGISupported()) {
            return false;
        }
        enableGI(sceneBuffer);
        return true;
    }

    public boolean tryEnableGI(GISceneBuffer sceneBuffer, RadianceCascadeConfig config) {
        if (!isGISupported()) {
            return false;
        }
        enableGI(sceneBuffer, config);
        return true;
    }

    /**
     * Disables and disposes the GI renderer.  The scene buffer is not disposed.
     */
    public void disableGI() {
        if (giRenderer != null) {
            giRenderer.dispose();
            giRenderer = null;
        }
        giScene = null;
    }

    /**
     * Controls how strongly GI irradiance is baked into the light map.
     * 0.0 = no GI contribution, 1.0 = full (default).
     */
    public void setGIStrength(float strength) {
        this.giStrength = strength;
    }

    public float getGIStrength() {
        return giStrength;
    }

    /**
     * Reinhard exposure applied to raw cascade irradiance before it is composited
     * onto the light map.  Lower values = dimmer but cleaner GI; higher = more
     * saturated bounce light.  Default 1.0.  Has no effect if GI is disabled.
     */
    public void setGIExposure(float exposure) {
        if (giRenderer != null) giRenderer.setExposure(exposure);
    }

    public float getGIExposure() {
        return giRenderer != null ? giRenderer.getExposure() : RadianceCascadeRenderer.DEFAULT_EXPOSURE;
    }

    public void setRayCastWorld(RayCastWorld rayCastWorld) {
        this.rayCastWorld = rayCastWorld;
    }

    public RayCastWorld getRayCastWorld() {
        return rayCastWorld;
    }

    public void setAmbientLight(float r, float g, float b, float a) {
        ambientLight.set(r, g, b, a);
    }

    public Color getAmbientLight() {
        return ambientLight;
    }

    public void addLight(Light light) {
        if (light == null) throw new NullPointerException("light cannot be null");
        if (!lights.contains(light)) {
            lights.add(light);
        }
    }

    public void removeLight(Light light) {
        lights.remove(light);
    }

    public List<Light> getLights() {
        return lights;
    }

    public void update() {
        lights.forEach(Light::update);
    }

    public void render() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        Window.clear(ambientLight);

        shader.bind();
        shader.setUniform2f("u_screenSize", width, height);

        glBlendFunc(GL_SRC_ALPHA, GL_ONE);

        for (Light light : lights) {
            if (!light.isActive()) continue;

            if (light.isDirty()) {
                light.update();
            }

            Color color = light.getColor();

            lightMesh.setFan(light.getX(), light.getY(), light.getDistance(), light.getEndX(), light.getEndY(), color.r(), color.g(), color.b(), color.a());
            lightMesh.render();

            if (light.isSoft()) {
                softShadowMesh.setTriangles(light.getX(), light.getY(), light.getDistance(), light.getSoftnessLength(), light.getEndX(), light.getEndY(), light.getFractions(), color.r(), color.g(), color.b(), color.a());
                softShadowMesh.render();
            }
        }

        shader.unbind();

        // GI: run Radiance Cascades and bake irradiance into the light FBO
        if (giRenderer != null && giScene != null) {
            giRenderer.render(giScene);
            glBindFramebuffer(GL_FRAMEBUFFER, fboId);
            lightMapRenderer.bake(giRenderer.getIrradianceTextureId(), giStrength);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        lightMapRenderer.render(lightMap.getTextureID());
    }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;

        this.width = width;
        this.height = height;

        deleteFramebuffer();
        createFramebuffer(width, height);

        if (giRenderer != null) giRenderer.resize(width, height);
    }

    public void dispose() {
        disableGI();
        deleteFramebuffer();
        shader.dispose();
        lightMapRenderer.dispose();
        lightMesh.dispose();
        softShadowMesh.dispose();
        quad.dispose();
    }

    private void createFramebuffer(int width, int height) {
        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        lightMap = new LightTexture(width, height);
        lightMap.bind();
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, lightMap.getTextureID(), 0);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Light framebuffer is incomplete. Status: " + status);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void deleteFramebuffer() {
        if (lightMap != null) {
            lightMap.dispose();
            lightMap = null;
        }

        if (fboId != 0) {
            glDeleteFramebuffers(fboId);
            fboId = 0;
        }
    }

    private void validateGISceneBuffer(GISceneBuffer sceneBuffer) {
        if (sceneBuffer.getWidth() != width || sceneBuffer.getHeight() != height) {
            throw new IllegalArgumentException(
                    "GISceneBuffer size must match RayHandler size. Expected "
                            + width + "x" + height + " but was "
                            + sceneBuffer.getWidth() + "x" + sceneBuffer.getHeight()
            );
        }
    }
}
