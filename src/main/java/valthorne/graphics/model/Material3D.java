package valthorne.graphics.model;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;

/**
 * Mutable surface and render-state configuration shared by the built-in 3D
 * pipelines. Individual renderers consume different subsets: raster batches use
 * pass, tint, texture and state settings, while the path tracer also reads
 * dielectric transmission, index of refraction and the HDR emission multiplier.
 * Assigning values does not itself bind GL state or update previously captured scene data.
 *
 * <p>Defaults are opaque pass, white tint, transparent emission, all mix factors
 * one, depth test/write enabled, back-face culling disabled, no texture, alpha
 * cutoff 0.001, shadow casting/receiving enabled, roughness 0.65, metallic and
 * transmission zero, index of refraction 1.5, emission strength one, and Filament's
 * implicit emission light enabled.</p>
 *
 * <pre>{@code
 * Material3D material = new Material3D()
 *         .setRenderPass(RenderPass3D.TRANSLUCENT)
 *         .setTint(new Color(1f, 1f, 1f, 0.5f))
 *         .setDepthWrite(false);
 * }</pre>
 *
 * <p>Tint and emissive colors are owned mutable objects exposed directly by
 * getters. Textures are borrowed and never disposed here; copies share the texture
 * but have independent color storage. Sharing a material shares all later mutations.
 * Set render pass before an explicit depth-write override, because pass assignment
 * resets that flag. Instances provide no synchronization or change notifications.</p>
 *
 * @author Albert Beaupre
 */
public final class Material3D {

    private final Color tint = Color.WHITE.copy(); // Owned RGBA surface multiplier.
    private final Color emissive = Color.TRANSPARENT.copy(); // Owned emission RGB with alpha used as emission weight.
    private RenderPass3D renderPass = RenderPass3D.OPAQUE; // Render pass; assignment also resets depth-write policy.
    private float lightingMix = 1f; // Lighting contribution mix.
    private float fogMix = 1f; // Fog contribution mix.
    private float radianceMix = 1f; // Radiance contribution mix.
    private boolean depthTest = true; // Whether raster drawing requests depth testing.
    private boolean depthWrite = true; // Whether raster drawing requests depth-buffer writes.
    private boolean cullBackFaces; // Whether raster drawing requests back-face culling.
    private Texture texture; // Borrowed diffuse texture, never disposed here.
    private float alphaCutoff = 0.001f; // Combined-alpha discard threshold.
    private boolean castsShadow = true, receivesShadow = true; // Shadow casting and receiving flags, both initially true.
    private boolean emissionLightEnabled = true; // Filament's legacy implicit point light; independent of visible emission.
    private float roughness = .65f, metallic; // Roughness and metallic surface parameters.
    private float transmission, indexOfRefraction = 1.5f, emissionStrength = 1f; // Transmission, dielectric refraction index and HDR emission multiplier.

    /**
     * Clamps values below zero to zero and above one to one, including infinities. NaN passes through Math.min unchanged; this is not a finiteness validator.
     *
     * @param value the candidate mix factor
     * @return the clamped value, or NaN when supplied
     */
    private static float clamp01(float value) {
        if (value < 0f) return 0f;
        return Math.min(value, 1f);
    }

    /**
     * Reads the path-tracer dielectric transmission fraction. Zero means no transmitted contribution; this does not imply any raster blending mode.
     *
     * @return the transmission value in zero through one
     */
    public float getTransmission() {return transmission;}

    /**
     * Sets dielectric transmission without changing render pass, tint alpha or depth policy. Invalid values leave the previous setting unchanged.
     *
     * @param value the finite fraction in zero through one
     * @return this material
     * @throws IllegalArgumentException if value is non-finite or outside zero through one
     */
    public Material3D setTransmission(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Transmission must be in [0,1]");
        transmission = value;
        return this;
    }

    /**
     * Reads the dielectric index used for refraction independently of the transmission amount.
     *
     * @return the finite index, at least one
     */
    public float getIndexOfRefraction() {return indexOfRefraction;}

    /**
     * Assigns a dielectric index without enabling transmission automatically.
     *
     * @param value the finite refraction index, at least one
     * @return this material
     * @throws IllegalArgumentException if value is non-finite or below one
     */
    public Material3D setIndexOfRefraction(float value) {
        if (!Float.isFinite(value) || value < 1)
            throw new IllegalArgumentException("Index of refraction must be at least 1");
        indexOfRefraction = value;
        return this;
    }

    /**
     * Reads the separate HDR emission multiplier, which the path-tracing scene multiplies by emissive alpha.
     *
     * @return the finite nonnegative multiplier
     */
    public float getEmissionStrength() {return emissionStrength;}

    /**
     * Sets the separate HDR emission multiplier without rewriting emissive RGB or alpha. Renderer support for this parameter is independent of packed emissive color.
     *
     * @param value the finite nonnegative emission multiplier
     * @return this material
     * @throws IllegalArgumentException if value is non-finite or negative
     */
    public Material3D setEmissionStrength(float value) {
        if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("Emission must be nonnegative");
        emissionStrength = value;
        return this;
    }

    /**
     * Reports whether Filament creates its legacy point light from this mesh's
     * emission. Defaults to true; visible emissive radiance is independent.
     */
    public boolean isEmissionLightEnabled() {return emissionLightEnabled;}

    /**
     * Enables Filament's implicit mesh point light without changing visible glow.
     * Disable when an explicit scene or particle light supplies illumination, so
     * an emissive mesh does not create an additional light or shadow map.
     * Path-traced emission remains physically emissive regardless of this flag.
     *
     * @param enabled whether Filament derives a point light from mesh emission
     * @return this material
     */
    public Material3D setEmissionLightEnabled(boolean enabled) {
        emissionLightEnabled = enabled;
        return this;
    }

    /**
     * Reads the surface roughness consumed by supporting lighting and path-tracing shaders.
     *
     * @return the roughness in zero through one
     */
    public float getRoughness() {return roughness;}

    /**
     * Assigns normalized roughness; no other material properties are inferred or changed.
     *
     * @param value the finite roughness in zero through one
     * @return this material
     * @throws IllegalArgumentException if value is non-finite or outside zero through one
     */
    public Material3D setRoughness(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Roughness must be in [0,1]");
        roughness = value;
        return this;
    }

    /**
     * Reads the metallic surface fraction without changing other surface parameters.
     *
     * @return the metallic fraction in zero through one
     */
    public float getMetallic() {return metallic;}

    /**
     * Assigns the metallic fraction independently of tint, roughness and transmission.
     *
     * @param value the finite metallic fraction in zero through one
     * @return this material
     * @throws IllegalArgumentException if value is non-finite or outside zero through one
     */
    public Material3D setMetallic(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Metallic must be in [0,1]");
        metallic = value;
        return this;
    }

    /**
     * Reads permission to submit this material to shadow rendering. The render pipeline can impose additional restrictions, such as opaque-pass membership.
     *
     * @return the shadow-casting flag
     */
    public boolean isCastsShadow() {return castsShadow;}

    /**
     * Changes shadow-casting permission without changing render pass or scene membership.
     *
     * @param enabled whether shadow submission is permitted
     * @return this material
     */
    public Material3D setCastsShadow(boolean enabled) {
        castsShadow = enabled;
        return this;
    }

    /**
     * Reads whether supporting raster lighting should apply an available shadow map.
     *
     * @return the shadow-receiving flag
     */
    public boolean isReceivesShadow() {return receivesShadow;}

    /**
     * Changes shadow-receiving permission without enabling lighting or creating a shadow map.
     *
     * @param enabled whether available shadow maps may affect this material
     * @return this material
     */
    public Material3D setReceivesShadow(boolean enabled) {
        receivesShadow = enabled;
        return this;
    }

    /**
     * Returns the borrowed diffuse texture directly. The material does not manage its native lifetime.
     *
     * @return the assigned texture, or null when untextured
     */
    public Texture getTexture() {return texture;}

    /**
     * Retains an optional diffuse texture without copying, uploading or disposing either the new or previous resource.
     *
     * @param texture the borrowed texture, or null
     * @return this material
     */
    public Material3D setTexture(Texture texture) {
        this.texture = texture;
        return this;
    }

    /**
     * Reads the threshold used by supporting shaders to discard combined texture, vertex and tint alpha at or below the cutoff.
     *
     * @return the finite cutoff in zero through one
     */
    public float getAlphaCutoff() {return alphaCutoff;}

    /**
     * Sets the alpha-discard threshold without changing render pass or enabling blending.
     *
     * @param cutoff the finite threshold in zero through one
     * @return this material
     * @throws IllegalArgumentException if cutoff is non-finite or outside zero through one
     */
    public Material3D setAlphaCutoff(float cutoff) {
        if (!Float.isFinite(cutoff) || cutoff < 0f || cutoff > 1f)
            throw new IllegalArgumentException("Alpha cutoff must be between 0 and 1");
        alphaCutoff = cutoff;
        return this;
    }

    /**
     * Reads the pass used for ordering and batch render-state selection.
     *
     * @return the nonnull configured pass
     */
    public RenderPass3D getRenderPass() {
        return renderPass;
    }

    /**
     * Assigns the pass and unconditionally resets depthWrite to true only for OPAQUE. Even reassigning the same pass replaces an earlier explicit depth-write override.
     * Filament uses true alpha compositing for TRANSLUCENT; transmission on an
     * opaque-pass material independently selects its refractive glass pipeline.
     *
     * @param renderPass the nonnull desired pass
     * @return this material
     * @throws NullPointerException if renderPass is null
     */
    public Material3D setRenderPass(RenderPass3D renderPass) {
        if (renderPass == null) throw new NullPointerException("renderPass");
        this.renderPass = renderPass;
        depthWrite = renderPass == RenderPass3D.OPAQUE;
        return this;
    }

    /**
     * Exposes the owned mutable RGBA tint; direct changes affect later consumers of this material.
     *
     * @return the live tint color
     */
    public Color getTint() {
        return tint;
    }

    /**
     * Copies color components into owned tint storage without retaining the argument.
     *
     * @param tint the nonnull source tint
     * @return this material
     * @throws NullPointerException if tint is null
     */
    public Material3D setTint(Color tint) {
        if (tint == null) throw new NullPointerException("tint");
        this.tint.set(tint);
        return this;
    }

    /**
     * Exposes the owned emission color. Its alpha is an emission weight, separate from base opacity and emissionStrength.
     *
     * @return the live emissive color
     */
    public Color getEmissive() {
        return emissive;
    }

    /**
     * Copies emission color components without changing the separate HDR multiplier.
     *
     * @param emissive the nonnull source emission color
     * @return this material
     * @throws NullPointerException if emissive is null
     */
    public Material3D setEmissive(Color emissive) {
        if (emissive == null) throw new NullPointerException("emissive");
        this.emissive.set(emissive);
        return this;
    }

    /**
     * Delegates component assignment to Color.set, storing strength in emissive alpha. This parameter is not the separate emissionStrength field; Color controls component storage semantics.
     *
     * @param r        the red emission component
     * @param g        the green emission component
     * @param b        the blue emission component
     * @param strength the emission weight stored in alpha
     * @return this material
     */
    public Material3D setEmissive(float r, float g, float b, float strength) {
        emissive.set(r, g, b, strength);
        return this;
    }

    /**
     * Reads the lighting mix. Normally within zero through one, but NaN is preserved by the setter.
     *
     * @return the stored lighting mix
     */
    public float getLightingMix() {
        return lightingMix;
    }

    /**
     * Sets the lighting contribution mix using clamp01. Out-of-range values and infinities clamp to endpoints; NaN is preserved rather than rejected.
     *
     * @param lightingMix the desired mix factor
     * @return this material
     */
    public Material3D setLightingMix(float lightingMix) {
        this.lightingMix = clamp01(lightingMix);
        return this;
    }

    /**
     * Reads the fog mix used by supporting shaders. The value may be NaN if supplied to its setter.
     *
     * @return the stored fog mix
     */
    public float getFogMix() {
        return fogMix;
    }

    /**
     * Sets the fog contribution mix using clamp01. Out-of-range values and infinities clamp to endpoints; NaN is preserved rather than rejected.
     *
     * @param fogMix the desired mix factor
     * @return this material
     */
    public Material3D setFogMix(float fogMix) {
        this.fogMix = clamp01(fogMix);
        return this;
    }

    /**
     * Reads the optional radiance mix. The value may be NaN if supplied to its setter.
     *
     * @return the stored radiance mix
     */
    public float getRadianceMix() {
        return radianceMix;
    }

    /**
     * Sets the radiance contribution mix using clamp01. Out-of-range values and infinities clamp to endpoints; NaN is preserved rather than rejected.
     *
     * @param radianceMix the desired mix factor
     * @return this material
     */
    public Material3D setRadianceMix(float radianceMix) {
        this.radianceMix = clamp01(radianceMix);
        return this;
    }

    /**
     * Reports the requested depth-test flag without querying current GL state.
     *
     * @return whether depth testing is requested
     */
    public boolean isDepthTest() {
        return depthTest;
    }

    /**
     * Sets the depth-test request independently of depth writes and render pass.
     *
     * @param depthTest whether depth testing is requested
     * @return this material
     */
    public Material3D setDepthTest(boolean depthTest) {
        this.depthTest = depthTest;
        return this;
    }

    /**
     * Reports the requested depth-write flag, which may have been reset by the last pass assignment.
     *
     * @return whether depth writes are requested
     */
    public boolean isDepthWrite() {
        return depthWrite;
    }

    /**
     * Overrides depth-write policy without changing the pass. A later setRenderPass call resets this override.
     *
     * @param depthWrite whether depth-buffer writes are requested
     * @return this material
     */
    public Material3D setDepthWrite(boolean depthWrite) {
        this.depthWrite = depthWrite;
        return this;
    }

    /**
     * Reads the back-face culling request without changing current GL state.
     *
     * @return whether back-face culling is requested
     */
    public boolean isCullBackFaces() {
        return cullBackFaces;
    }

    /**
     * Sets the raster back-face culling request without changing mesh winding or geometry.
     *
     * @param cullBackFaces whether back faces should be culled
     * @return this material
     */
    public Material3D setCullBackFaces(boolean cullBackFaces) {
        this.cullBackFaces = cullBackFaces;
        return this;
    }

    /**
     * Copies every current setting into a new material with independent tint and emissive storage. Texture is shared by reference. Depth-write is copied after render-pass assignment so an explicit override is preserved.
     *
     * @return a new independently mutable material sharing only the texture resource
     */
    public Material3D copy() {
        return new Material3D().set(this);
    }

    /**
     * Copies all settings into this material without replacing its owned colors.
     * The texture remains borrowed. Self-assignment is supported.
     *
     * @param source material to copy
     * @return this material
     * @throws NullPointerException if source is null
     */
    public Material3D set(Material3D source) {
        java.util.Objects.requireNonNull(source, "source");
        tint.set(source.tint);
        emissive.set(source.emissive);
        renderPass = source.renderPass;
        lightingMix = source.lightingMix;
        fogMix = source.fogMix;
        radianceMix = source.radianceMix;
        roughness = source.roughness;
        metallic = source.metallic;
        transmission = source.transmission;
        indexOfRefraction = source.indexOfRefraction;
        emissionStrength = source.emissionStrength;
        emissionLightEnabled = source.emissionLightEnabled;
        depthTest = source.depthTest;
        depthWrite = source.depthWrite;
        cullBackFaces = source.cullBackFaces;
        texture = source.texture;
        alphaCutoff = source.alphaCutoff;
        castsShadow = source.castsShadow;
        receivesShadow = source.receivesShadow;
        return this;
    }
}
