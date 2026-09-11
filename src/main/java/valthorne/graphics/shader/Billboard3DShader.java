package valthorne.graphics.shader;

/**
 * GLSL 3.30 program for textured billboard geometry already oriented in world
 * space by BillboardBatch3D. The vertex stage transforms supplied positions;
 * it does not calculate camera-facing axes. Fragment shading combines texture,
 * vertex color and material tint, adds emission and optional XY radiance, then
 * applies camera-distance fog while preserving the combined base alpha.
 *
 * <p>Fragments whose combined alpha is at or below u_alphaCutoff are discarded.
 * Lighting RGB is clamped to zero through one before fog mixing, so this shader
 * is not an HDR output path. Fog parameters in u_fog are start distance, end
 * distance and amount; callers must supply a nonzero distance interval.</p>
 *
 * <p>Construction compiles and reloads the program, initializes texture samplers
 * on units zero and one, and finishes by unbinding. It does not restore a previously
 * bound program. Other uniforms, textures, blend/depth state and geometry are
 * supplied by the batch. Use a current compatible GL context and dispose through
 * Shader when finished; textures remain owned by their providers.</p>
 *
 * @author Albert Beaupre
 */
public final class Billboard3DShader extends Shader {

    /**
     * World-space vec3 position attribute location; billboard orientation is computed before submission.
     */
    public static final int ATTR_POSITION = 0;
    /**
     * Texture-coordinate vec2 attribute location for the billboard region.
     */
    public static final int ATTR_UV = 1;
    /**
     * Per-vertex RGBA color attribute location, multiplied with texture and material tint.
     */
    public static final int ATTR_COLOR = 2;

    /**
     * Matrix uniform mapping supplied world positions into homogeneous clip coordinates.
     */
    public static final String UNIFORM_MVP = "u_mvp";
    /**
     * World-space vec3 camera position used for Euclidean fog distance.
     */
    public static final String UNIFORM_CAMERA_POS = "u_cameraPos";
    /**
     * RGBA fog-color uniform; only RGB participates in fragment fog mixing.
     */
    public static final String UNIFORM_FOG_COLOR = "u_fogColor";
    /**
     * RGBA multiplier applied to sampled texture and vertex color, including alpha cutoff.
     */
    public static final String UNIFORM_MATERIAL_TINT = "u_materialTint";
    /**
     * RGBA emission value whose RGB is multiplied by its alpha before addition.
     */
    public static final String UNIFORM_MATERIAL_EMISSIVE = "u_materialEmissive";
    /**
     * Scalar multiplier for the distance-fog blend factor.
     */
    public static final String UNIFORM_MATERIAL_FOG_MIX = "u_materialFogMix";
    /**
     * Scalar multiplier for sampled radiance; values at or below zero skip that contribution.
     */
    public static final String UNIFORM_MATERIAL_RADIANCE_MIX = "u_materialRadianceMix";
    /**
     * Diffuse sampler name, initialized to texture unit zero.
     */
    public static final String UNIFORM_TEXTURE = "u_texture";
    /**
     * XY radiance sampler name, initialized to texture unit one.
     */
    public static final String UNIFORM_LIGHT_TEXTURE = "u_lightTexture";
    /**
     * World XY minimum used as the origin of radiance texture coordinates.
     */
    public static final String UNIFORM_LIGHT_WORLD_MIN = "u_lightWorldMin";
    /**
     * World XY extents dividing radiance coordinates; callers must supply nonzero sizes.
     */
    public static final String UNIFORM_LIGHT_WORLD_SIZE = "u_lightWorldSize";
    /**
     * Global scalar multiplying the sampled radiance contribution.
     */
    public static final String UNIFORM_RADIANCE_STRENGTH = "u_radianceStrength";
    /**
     * Integer enable uniform; radiance sampling occurs only when its value equals one.
     */
    public static final String UNIFORM_APPLY_RADIANCE = "u_applyRadiance";

    /**
     * Compiles the bundled shader resources, records attribute bindings and reloads the program. Temporarily binds it to assign diffuse and radiance sampler units, then unbinds rather than restoring the previous program. No texture objects are created.
     *
     * @throws IllegalStateException if shader compilation or program linking fails
     */
    public Billboard3DShader() {
        super(vertexSource(), fragmentSource());
        bindAttribLocation(ATTR_POSITION, "a_pos");
        bindAttribLocation(ATTR_UV, "a_uv");
        bindAttribLocation(ATTR_COLOR, "a_color");
        reload();
        bind();
        setUniform1i(UNIFORM_TEXTURE, 0);
        setUniform1i(UNIFORM_LIGHT_TEXTURE, 1);
        unbind();
    }

    /**
     * Supplies the vertex stage for world-space billboard positions, forwarding position, UV and color to fragment shading without normal calculation or billboard rotation.
     *
     * @return the GLSL 3.30 vertex shader source
     */
    private static String vertexSource() {
        return ShaderSources.load("model3d/billboard.vert");
    }

    /**
     * Supplies alpha-cutout, emission, optional radiance and fog shading. Radiance coordinates are clamped to 0.001-0.999 and its RGB is weighted by 0.08 plus 0.42 times sampled alpha. Final alpha remains texture times vertex times material alpha; emission and fog do not change it.
     *
     * @return the GLSL 3.30 fragment shader source
     */
    private static String fragmentSource() {
        return ShaderSources.load("model3d/billboard.frag");
    }
}
