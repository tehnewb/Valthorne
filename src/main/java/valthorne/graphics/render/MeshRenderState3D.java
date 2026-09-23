package valthorne.graphics.render;

import valthorne.graphics.model.Material3D;
import valthorne.graphics.lighting3d.PointLight3D;

import org.joml.Vector3f;
import valthorne.camera.Camera3D;
import valthorne.graphics.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import valthorne.graphics.lighting3d.Lighting3D;

/**
 * Mutable draw configuration combining camera, material, compatibility lighting,
 * fog, shadow-map references, and optional screen-space radiance mapping. Cameras,
 * materials, lights, and rendering systems are borrowed; colors and light direction
 * are copied by setters but exposed as live owned values by getters.
 *
 * <p>This container does not allocate GPU resources, prepare lights, rebuild cameras,
 * or draw. The batch consumes its current values. Do not mutate it during deferred
 * frame emission. Compatibility point lights are capped at eight, independently of
 * the optional tiled lighting system.</p>
 *
 * @author Albert Beaupre
 */
public final class MeshRenderState3D {

    /**
     * Maximum number of lights retained by the compatibility point-light list.
     */
    public static final int MAX_POINT_LIGHTS = 8;
    private final ArrayList<PointLight3D> pointLights = new ArrayList<>(); // Borrowed compatibility lights in insertion order.
    private final Color ambient = new Color(0.14f, 0.14f, 0.14f, 1f); // Owned ambient color, initially RGB 0.14.
    private final Color directional = new Color(0.46f, 0.46f, 0.46f, 1f); // Owned directional color, initially RGB 0.46.
    private final Vector3f lightDirection = new Vector3f(0f, -1f, 0f); // Owned light direction, stored without normalization.
    private final Color fogColor = Color.BLACK.copy(); // Owned fog color, initially black.
    private Lighting3D lighting; // Borrowed tiled lighting system, or null for compatibility lighting.
    private float fogStart = 260f, fogEnd = 880f, fogAmount = 0.58f; // Fog start/end distances and maximum blend amount.
    private ShadowMap3D shadowMap; // Borrowed shadow map, or null.
    private boolean shadowPass; // Internal switch selecting depth-only shadow submission.
    private Camera3D camera; // Borrowed camera, initially absent.
    private Material3D material; // Borrowed current material, initially absent.
    private int lightTextureId; // Borrowed radiance texture name, zero when absent.
    private boolean applyRadiance; // Whether radiance contribution is requested.
    private float lightWorldMinX = -1f; // World-space X origin of the radiance mapping rectangle.
    private float lightWorldMinY = -1f; // World-space Y origin of the radiance mapping rectangle.
    private float lightWorldSizeX = 2f; // World-space X extent of the radiance mapping rectangle.
    private float lightWorldSizeY = 2f; // World-space Y extent of the radiance mapping rectangle.
    private float radianceStrength = 0.32f; // Nonnegative radiance multiplier, initially 0.32.

    /**
     * Returns the borrowed tiled lighting system without preparing or copying it.
     * A null reference indicates that this component has not been supplied.
     *
     * @return borrowed tiled lighting system, or null
     */
    public Lighting3D getLighting() {
        return lighting;
    }

    /**
     * Retains the borrowed tiled lighting system without taking disposal responsibility.
     * Null clears the reference; this setter does not perform rendering or resource setup.
     *
     * @param lighting replacement reference, or null
     * @return this state
     */
    public MeshRenderState3D setLighting(Lighting3D lighting) {
        this.lighting = lighting;
        return this;
    }

    /**
     * Returns the borrowed shadow map without preparing or copying it.
     * A null reference indicates that this component has not been supplied.
     *
     * @return borrowed shadow map, or null
     */
    public ShadowMap3D getShadowMap() {
        return shadowMap;
    }

    /**
     * Retains the borrowed shadow map without taking disposal responsibility.
     * Null clears the reference; this setter does not perform rendering or resource setup.
     *
     * @param shadowMap replacement reference, or null
     * @return this state
     */
    public MeshRenderState3D setShadowMap(ShadowMap3D shadowMap) {
        this.shadowMap = shadowMap;
        return this;
    }

    /**
     * Reports whether internal shadow-pass rendering is requested.
     *
     * @return shadow-pass flag
     */
    boolean isShadowPass() {
        return shadowPass;
    }

    /**
     * Sets the internal shadow-pass flag without changing lighting or material objects.
     * Batch submission uses this flag to filter shadow casters.
     *
     * @param shadowPass whether the draw is a shadow pass
     * @return this state
     */
    MeshRenderState3D setShadowPass(boolean shadowPass) {
        this.shadowPass = shadowPass;
        return this;
    }

    /**
     * Returns the live owned ambient light color. Mutations affect later draws using this
     * state; copy it when retaining an independent value.
     *
     * @return mutable ambient light color
     */
    public Color getAmbientLight() {
        return ambient;
    }

    /**
     * Copies the ambient light color into owned storage without retaining the input.
     * Does not clamp components or upload any shader uniforms.
     *
     * @param color replacement color
     * @return this state
     */
    public MeshRenderState3D setAmbientLight(Color color) {
        ambient.set(color);
        return this;
    }

    /**
     * Returns the live owned directional light color. Mutations affect later draws using this
     * state; copy it when retaining an independent value.
     *
     * @return mutable directional light color
     */
    public Color getDirectionalLight() {
        return directional;
    }

    /**
     * Copies the directional light color into owned storage without retaining the input.
     * Does not clamp components or upload any shader uniforms.
     *
     * @param color replacement color
     * @return this state
     */
    public MeshRenderState3D setDirectionalLight(Color color) {
        directional.set(color);
        return this;
    }

    /**
     * Returns an unmodifiable live list of compatibility lights. Light objects remain
     * shared and mutable; the list does not snapshot their values.
     *
     * @return live light-list view
     */
    public List<PointLight3D> getPointLights() {
        return Collections.unmodifiableList(pointLights);
    }

    /**
     * Appends a borrowed light, allowing repeated references, up to the compatibility
     * limit. Does not transfer ownership or configure the tiled lighting system.
     *
     * @param light light to append
     * @return this state
     * @throws NullPointerException  if light is null
     * @throws IllegalStateException if eight lights are already present
     */
    public MeshRenderState3D addLight(PointLight3D light) {
        Objects.requireNonNull(light, "light");
        if (pointLights.size() == MAX_POINT_LIGHTS)
            throw new IllegalStateException("At most eight point lights are supported");
        pointLights.add(light);
        return this;
    }

    /**
     * Removes the first matching light reference according to list equality semantics.
     * Does not dispose the removed light.
     *
     * @param light light to remove
     * @return true if an entry was removed
     */
    public boolean removeLight(PointLight3D light) {
        return pointLights.remove(light);
    }

    /**
     * Removes all compatibility light references without disposing them or changing
     * the optional tiled lighting system.
     */
    public void clearLights() {
        pointLights.clear();
    }

    /**
     * Returns the stored fog start distance without changing or validating draw state.
     *
     * @return fog start distance
     */
    public float getFogStart() {
        return fogStart;
    }

    /**
     * Returns the stored fog end distance without changing or validating draw state.
     *
     * @return fog end distance
     */
    public float getFogEnd() {
        return fogEnd;
    }

    /**
     * Returns the stored maximum fog blend fraction without changing or validating draw state.
     *
     * @return maximum fog blend fraction
     */
    public float getFogAmount() {
        return fogAmount;
    }

    /**
     * Stores validated fog distances and maximum blend fraction. Color is configured
     * separately; these values are consumed by subsequent draws.
     *
     * @param start  finite nonnegative start distance in world units
     * @param end    finite end distance greater than start
     * @param amount finite maximum blend fraction from zero through one
     * @return this state
     * @throws IllegalArgumentException if distances or amount violate their constraints
     */
    public MeshRenderState3D setFog(float start, float end, float amount) {
        if (!Float.isFinite(start) || !Float.isFinite(end) || !Float.isFinite(amount) || start < 0f || end <= start || amount < 0f || amount > 1f)
            throw new IllegalArgumentException("Fog requires 0 <= start < end and amount in [0,1]");
        fogStart = start;
        fogEnd = end;
        fogAmount = amount;
        return this;
    }

    /**
     * Returns the borrowed camera without preparing or copying it.
     * A null reference indicates that this component has not been supplied.
     *
     * @return borrowed camera, or null
     */
    public Camera3D getCamera() {
        return camera;
    }

    /**
     * Retains the borrowed camera without taking disposal responsibility.
     * Null clears the reference; this setter does not perform rendering or resource setup.
     *
     * @param camera replacement reference, or null
     * @return this state
     */
    public MeshRenderState3D setCamera(Camera3D camera) {
        this.camera = camera;
        return this;
    }

    /**
     * Returns the live owned directional-light vector. It is not normalized by this
     * container; consumers apply their own interpretation.
     *
     * @return mutable light direction
     */
    public Vector3f getLightDirection() {
        return lightDirection;
    }

    /**
     * Copies a light-direction vector without normalizing or validating its components.
     * The supplied vector is not retained.
     *
     * @param direction vector to copy
     * @return this state
     * @throws NullPointerException if direction is null
     */
    public MeshRenderState3D setLightDirection(Vector3f direction) {
        if (direction == null) throw new NullPointerException("direction");
        this.lightDirection.set(direction);
        return this;
    }

    /**
     * Stores light-direction components directly without normalization or finiteness checks.
     *
     * @param x X component
     * @param y Y component
     * @param z Z component
     * @return this state
     */
    public MeshRenderState3D setLightDirection(float x, float y, float z) {
        this.lightDirection.set(x, y, z);
        return this;
    }

    /**
     * Returns the live owned fog color. Mutations affect later draws using this
     * state; copy it when retaining an independent value.
     *
     * @return mutable fog color
     */
    public Color getFogColor() {
        return fogColor;
    }

    /**
     * Copies the fog color into owned storage without retaining the input.
     * Does not clamp components or upload any shader uniforms.
     *
     * @param fogColor replacement color
     * @return this state
     */
    public MeshRenderState3D setFogColor(Color fogColor) {
        if (fogColor == null) throw new NullPointerException("fogColor");
        this.fogColor.set(fogColor);
        return this;
    }

    /**
     * Returns the borrowed material without preparing or copying it.
     * A null reference indicates that this component has not been supplied.
     *
     * @return borrowed material, or null
     */
    public Material3D getMaterial() {
        return material;
    }

    /**
     * Retains the borrowed material without taking disposal responsibility.
     * Null clears the reference; this setter does not perform rendering or resource setup.
     *
     * @param material replacement reference, or null
     * @return this state
     */
    public MeshRenderState3D setMaterial(Material3D material) {
        this.material = material;
        return this;
    }

    /**
     * Returns the stored radiance texture name without changing or validating draw state.
     *
     * @return radiance texture name
     */
    public int getLightTextureId() {
        return lightTextureId;
    }

    /**
     * Stores a borrowed radiance texture name, clamping negative integers to zero.
     * Does not validate the OpenGL name, bind it, or manage its lifetime.
     *
     * @param lightTextureId texture name, or zero for none
     * @return this state
     */
    public MeshRenderState3D setLightTextureId(int lightTextureId) {
        this.lightTextureId = Math.max(0, lightTextureId);
        return this;
    }

    /**
     * Reports whether draws should apply the configured radiance contribution.
     * This flag does not imply that a valid texture has been supplied.
     *
     * @return radiance enablement
     */
    public boolean isApplyRadiance() {
        return applyRadiance;
    }

    /**
     * Changes radiance enablement without allocating or clearing the referenced texture.
     *
     * @param applyRadiance whether to request radiance contribution
     * @return this state
     */
    public MeshRenderState3D setApplyRadiance(boolean applyRadiance) {
        this.applyRadiance = applyRadiance;
        return this;
    }

    /**
     * Returns the stored radiance rectangle minimum X without changing or validating draw state.
     *
     * @return radiance rectangle minimum X
     */
    public float getLightWorldMinX() {
        return lightWorldMinX;
    }

    /**
     * Returns the stored radiance rectangle minimum Y without changing or validating draw state.
     *
     * @return radiance rectangle minimum Y
     */
    public float getLightWorldMinY() {
        return lightWorldMinY;
    }

    /**
     * Returns the stored radiance rectangle X extent without changing or validating draw state.
     *
     * @return radiance rectangle X extent
     */
    public float getLightWorldSizeX() {
        return lightWorldSizeX;
    }

    /**
     * Returns the stored radiance rectangle Y extent without changing or validating draw state.
     *
     * @return radiance rectangle Y extent
     */
    public float getLightWorldSizeY() {
        return lightWorldSizeY;
    }

    /**
     * Stores the world XY rectangle used to map radiance texture coordinates. Rejects
     * nonpositive extents but does not separately validate finiteness or origin values.
     *
     * @param minX  world-space left coordinate
     * @param minY  world-space lower coordinate
     * @param sizeX positive world-space X extent
     * @param sizeY positive world-space Y extent
     * @return this state
     * @throws IllegalArgumentException if either extent is nonpositive
     */
    public MeshRenderState3D setLightWorldBounds(float minX, float minY, float sizeX, float sizeY) {
        if (sizeX <= 0f) throw new IllegalArgumentException("sizeX must be > 0");
        if (sizeY <= 0f) throw new IllegalArgumentException("sizeY must be > 0");
        this.lightWorldMinX = minX;
        this.lightWorldMinY = minY;
        this.lightWorldSizeX = sizeX;
        this.lightWorldSizeY = sizeY;
        return this;
    }

    /**
     * Returns the stored radiance contribution multiplier without changing or validating draw state.
     *
     * @return radiance contribution multiplier
     */
    public float getRadianceStrength() {
        return radianceStrength;
    }

    /**
     * Stores the radiance multiplier with negative values clamped to zero. NaN and
     * positive infinity are not rejected by this setter.
     *
     * @param radianceStrength requested multiplier
     * @return this state
     */
    public MeshRenderState3D setRadianceStrength(float radianceStrength) {
        this.radianceStrength = Math.max(0f, radianceStrength);
        return this;
    }
}
