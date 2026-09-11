package valthorne.graphics.lighting;

import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.shader.ShaderSources;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Screen-space two-dimensional lighting pipeline combining ray-cast light fans,
 * soft fringes, and an ambient light map. Coordinates correspond to the supplied
 * screen dimensions; the light map is multiplied over the default framebuffer
 * after scene rendering. The handler owns its OpenGL resources and borrows its
 * world and lights. All rendering and resource operations require a current
 * OpenGL context on the owning thread.
 *
 * <p>Lights cache endpoints until dirtied. When occluders change, invalidate the
 * affected lights explicitly. The render pass changes OpenGL state and assumes
 * the caller has set the viewport. Resize the handler alongside the surface.</p>
 *
 * <pre>{@code
 * RayHandler lighting = new RayHandler(800, 600);
 * lighting.setAmbientLight(0.15f, 0.15f, 0.15f, 1f);
 * PointLight lamp = new PointLight(lighting, 64,
 *         new Color(1f, 0.9f, 0.7f, 1f), 200f, 400f, 300f);
 * lighting.addLight(lamp);
 * // Each frame, after drawing the scene with a matching viewport:
 * lighting.update();
 * lighting.render();
 * // At shutdown, while the OpenGL context is still current:
 * lighting.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class RayHandler {

    /**
     * Vertex shader source converting light geometry into screen-space positions.
     */
    private static final String vertexSource = ShaderSources.load("lighting/ray.vert");

    /**
     * Fragment shader source shading the radial light mesh and its fringe.
     */
    private static final String fragmentSource = ShaderSources.load("lighting/ray.frag");

    private final List<Light> lights = new ArrayList<>(); // Live render-order list of borrowed light objects.
    private final Color ambientLight = new Color(0f, 0f, 0f, 1f); // Owned mutable clear color for the light map.
    private final LightMesh lightMesh; // Owned reusable mesh for one light fan at a time.
    private final SoftShadowMesh softShadowMesh; // Owned reusable mesh for one light's soft fringe.
    private final Shader shader; // Owned program for fan and fringe geometry.
    private final LightMapRenderer lightMapRenderer; // Owned helper composing the completed light map.

    private RayCastWorld rayCastWorld; // Borrowed occlusion world, or null for unblocked rays.
    private int width; // Light-map pixel width and screen culling extent.
    private int height; // Light-map pixel height and screen culling extent.
    private int fboId; // Owned framebuffer identifier, zero after deletion.
    private LightTexture lightMap; // Owned framebuffer color texture, null after deletion.

    /**
     * Creates shared meshes, compiles lighting programs, and allocates the light
     * framebuffer. Requires a current OpenGL context. Dimensions are passed to
     * texture allocation without application-level validation.
     * @param width light-map width and visible screen extent in pixels
     * @param height light-map height and visible screen extent in pixels
     * @throws IllegalStateException if the framebuffer is incomplete
     */
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

    /**
     * Returns the borrowed world consulted by this handler's lights.
     * @return configured occlusion world, or null for unobstructed rays
     */
    public RayCastWorld getRayCastWorld() {
        return rayCastWorld;
    }

    /**
     * Replaces the borrowed occlusion world without dirtying any light. Mark
     * existing lights dirty when the replacement must affect cached endpoints.
     * @param rayCastWorld new world, or null to disable world occlusion
     */
    public void setRayCastWorld(RayCastWorld rayCastWorld) {
        this.rayCastWorld = rayCastWorld;
    }

    /**
     * Copies the supplied components into the light-map clear color. Values are
     * forwarded to the color object; no geometry recast is needed.
     * @param r ambient red
     * @param g ambient green
     * @param b ambient blue
     * @param a ambient alpha
     */
    public void setAmbientLight(float r, float g, float b, float a) {
        ambientLight.set(r, g, b, a);
    }

    /**
     * Exposes the live ambient color used when clearing the light map. Mutations
     * take effect on the next render without additional notification.
     * @return owned mutable ambient color; not a copy
     */
    public Color getAmbientLight() {
        return ambientLight;
    }

    /**
     * Appends a non-null light unless the list already contains it. The light's
     * constructor-supplied handler is not changed or validated by registration.
     * @param light light to render with this handler
     * @throws NullPointerException if light is null
     */
    public void addLight(Light light) {
        if (light == null) throw new NullPointerException("light cannot be null");
        if (!lights.contains(light)) {
            lights.add(light);
        }
    }

    /**
     * Removes the first matching light from the render list without disposing it.
     * An absent value, including null in a normally managed list, has no effect.
     * @param light light to remove
     */
    public void removeLight(Light light) {
        lights.remove(light);
    }

    /**
     * Returns the live mutable render list. Direct changes bypass addLight's
     * null and duplicate checks; keep entries valid and avoid mutation during
     * update or render iteration.
     * @return backing list in draw order
     */
    public List<Light> getLights() {
        return lights;
    }

    /**
     * Prepares the configured world, then updates active lights whose radial
     * bounds overlap the screen extent. Individual lights control dirty caching;
     * changing world geometry alone does not force clean lights to recast.
     */
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

    /**
     * Clears the light framebuffer to ambient color, additively draws visible
     * active lights and optional fringes, then multiplies the default framebuffer
     * by the resulting light map. Dirty visible lights are updated before drawing.
     *
     * The caller must establish a suitable viewport; this method does not set one.
     * It finishes with framebuffer zero bound and does not restore prior OpenGL
     * state. Draw the scene first, then composite lighting, then draw any UI that
     * should remain unaffected by the lighting pass.
     */
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

    /**
     * Updates screen culling extents and reallocates the light-map texture when
     * either dimension changes. Equal dimensions are a no-op. The viewport and
     * light geometry are not updated; framebuffer zero is bound after resizing.
     * @param width requested pixel width
     * @param height requested pixel height
     */
    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;

        this.width = width;
        this.height = height;

        resizeFramebuffer(width, height);
    }

    /**
     * Releases the owned framebuffer, light texture, programs, and shared meshes.
     * Requires the owning OpenGL context. The borrowed world and registered lights
     * are retained; this handler must not be used afterward. Repeated disposal of
     * all subordinate resources is not guarded.
     */
    public void dispose() {
        deleteFramebuffer();
        shader.dispose();
        lightMapRenderer.dispose();
        lightMesh.dispose();
        softShadowMesh.dispose();
    }

    /**
     * Allocates and attaches a light texture, then checks framebuffer completeness.
     * On success framebuffer zero is bound; the prior framebuffer is not restored.
     * A failure does not roll back partially allocated resources.
     * @param width requested texture width
     * @param height requested texture height
     * @throws IllegalStateException if the framebuffer is incomplete
     */
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

    /**
     * Deletes the light texture and framebuffer when present, clearing their
     * references/identifier so this helper alone can safely be called again.
     */
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

    /**
     * Reallocates the existing light texture and reattaches it, or creates the
     * framebuffer if absent. The existing-framebuffer path does not recheck
     * completeness and finishes with framebuffer zero bound.
     * @param width requested texture width
     * @param height requested texture height
     */
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

    /**
     * Tests inclusive overlap of a light's radial square with the screen extent.
     * Soft lights expand that square by nonnegative fringe length. This is a
     * conservative bounds test and does not inspect occlusion or cone direction.
     * @param light light whose bounds are tested
     * @return true when its expanded square touches the screen extent
     */
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
