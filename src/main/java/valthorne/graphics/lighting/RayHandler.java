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
    private final Shader shader;
    private final LightMapRenderer lightMapRenderer;

    private RayCastWorld rayCastWorld;
    private int width;
    private int height;
    private int fboId;
    private LightTexture lightMap;

    public RayHandler(int width, int height) {
        this.width = width;
        this.height = height;
        this.lightMesh = new LightMesh(8192);
        this.softShadowMesh = new SoftShadowMesh(8192 * 3);

        shader = new Shader(vertexSource, fragmentSource);
        shader.bindAttribLocation(0, "a_position");
        shader.bindAttribLocation(1, "a_local");
        shader.bindAttribLocation(2, "a_color");
        shader.reload();

        this.lightMapRenderer = new LightMapRenderer();
        createFramebuffer(width, height);
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
        if (rayCastWorld != null) {
            rayCastWorld.prepare();
        }
        for (Light light : lights) {
            if (!light.isActive() || !isVisible(light)) {
                continue;
            }
            light.update();
        }
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
            if (!isVisible(light)) continue;

            if (light.isDirty()) {
                light.update();
            }

            Color color = light.getColor();

            lightMesh.setFan(
                    light.getX(),
                    light.getY(),
                    light.getDistance(),
                    light.getEndX(),
                    light.getEndY(),
                    color.r(),
                    color.g(),
                    color.b(),
                    color.a()
            );
            lightMesh.render();

            if (light.isSoft()) {
                softShadowMesh.setTriangles(
                        light.getX(),
                        light.getY(),
                        light.getDistance(),
                        light.getSoftnessLength(),
                        light.getEndX(),
                        light.getEndY(),
                        light.getFractions(),
                        color.r(),
                        color.g(),
                        color.b(),
                        color.a()
                );
                softShadowMesh.render();
            }
        }

        shader.unbind();

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        lightMapRenderer.render(lightMap.getTextureID());
    }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;

        this.width = width;
        this.height = height;

        resizeFramebuffer(width, height);
    }

    public void dispose() {
        deleteFramebuffer();
        shader.dispose();
        lightMapRenderer.dispose();
        lightMesh.dispose();
        softShadowMesh.dispose();
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

    private void resizeFramebuffer(int width, int height) {
        if (lightMap == null || fboId == 0) {
            createFramebuffer(width, height);
            return;
        }

        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        lightMap.resize(width, height);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, lightMap.getTextureID(), 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private boolean isVisible(Light light) {
        float extent = light.getDistance();
        if (light.isSoft()) {
            extent += Math.max(0f, light.getSoftnessLength());
        }

        float minX = light.getX() - extent;
        float maxX = light.getX() + extent;
        float minY = light.getY() - extent;
        float maxY = light.getY() + extent;

        return maxX >= 0f && maxY >= 0f && minX <= width && minY <= height;
    }
}
