package valthorne.graphics.shader;

/**
 * Compatibility raster shader for world-space 3D mesh vertices. Supports vertex
 * color and albedo tinting, directional and up to eight point lights, directional
 * depth shadows, emissive color, projected 2D radiance, alpha cutout, and distance
 * fog. MeshBatch3D supplies world-space positions/normals and uploads the draw state;
 * the vertex stage applies only the combined camera matrix.
 * <p>
 * Construction compiles bundled GLSL on the current OpenGL context and establishes
 * sampler units zero for projected radiance, one for albedo, and two for shadows.
 * Other material/camera uniforms must be configured before drawing. The Shader
 * base class owns program disposal; borrowed textures remain caller-owned.
 * </p>
 * @author Albert Beaupre
 */
public final class Mesh3DShader extends Shader {

    /**
     * Attribute location for three-float world-space position.
     */
    public static final int ATTR_POSITION = 0;
    /**
     * Attribute location for three-float world-space normal.
     */
    public static final int ATTR_NORMAL = 1;
    /**
     * Attribute location for four-float RGBA vertex color.
     */
    public static final int ATTR_COLOR = 2;
    /**
     * Attribute location for two-float albedo texture coordinates.
     */
    public static final int ATTR_UV = 3;

    /**
     * Combined camera matrix mapping world positions to clip space.
     */
    public static final String UNIFORM_MVP = "u_mvp";
    /**
     * World-space directional-light vector, normalized by the fragment shader.
     */
    public static final String UNIFORM_LIGHT_DIR = "u_lightDir";
    /**
     * World-space camera position used for view-dependent shading and fog distance.
     */
    public static final String UNIFORM_CAMERA_POS = "u_cameraPos";
    /**
     * RGBA fog color; the fragment stage uses its RGB components.
     */
    public static final String UNIFORM_FOG_COLOR = "u_fogColor";
    /**
     * RGBA multiplier applied to vertex and optional albedo texture color.
     */
    public static final String UNIFORM_MATERIAL_TINT = "u_materialTint";
    /**
     * Emissive RGB with alpha used as its additive strength.
     */
    public static final String UNIFORM_MATERIAL_EMISSIVE = "u_materialEmissive";
    /**
     * Blend amount between unlit base color and compatibility lighting.
     */
    public static final String UNIFORM_MATERIAL_LIGHTING_MIX = "u_materialLightingMix";
    /**
     * Material multiplier for distance-fog contribution.
     */
    public static final String UNIFORM_MATERIAL_FOG_MIX = "u_materialFogMix";
    /**
     * Material multiplier for projected 2D radiance contribution.
     */
    public static final String UNIFORM_MATERIAL_RADIANCE_MIX = "u_materialRadianceMix";
    /**
     * Projected world-light sampler assigned to texture unit zero.
     */
    public static final String UNIFORM_LIGHT_TEXTURE = "u_lightTexture";
    /**
     * World XY origin used to map fragments into the light texture.
     */
    public static final String UNIFORM_LIGHT_WORLD_MIN = "u_lightWorldMin";
    /**
     * World XY extent used to normalize light-texture coordinates.
     */
    public static final String UNIFORM_LIGHT_WORLD_SIZE = "u_lightWorldSize";
    /**
     * Global strength of the projected radiance contribution.
     */
    public static final String UNIFORM_RADIANCE_STRENGTH = "u_radianceStrength";
    /**
     * Integer switch enabling projected radiance when equal to one.
     */
    public static final String UNIFORM_APPLY_RADIANCE = "u_applyRadiance";

    /**
     * Loads and compiles the bundled mesh stages, binds the named position/normal/
     * color attributes, relinks, and initializes sampler-unit uniforms. The UV location
     * is declared explicitly in the vertex source. Leaves the shader unbound.
     */
    public Mesh3DShader() {
        super(vertexSource(), fragmentSource());
        bindAttribLocation(ATTR_POSITION, "a_pos");
        bindAttribLocation(ATTR_NORMAL, "a_normal");
        bindAttribLocation(ATTR_COLOR, "a_color");
        reload();
        bind();
        setUniform1i(UNIFORM_LIGHT_TEXTURE, 0);
        setUniform1i("u_texture", 1);
        setUniform1i("u_shadowTexture", 2);
        unbind();
    }

    /**
     * Loads the shared world-space mesh vertex stage from bundled resources.
     * Also used by specialized 3D lighting shaders.
     *
     * @return GLSL vertex source
     * @throws IllegalStateException if the bundled resource is missing
     */
    public static String vertexSource() {
        return ShaderSources.load("model3d/mesh.vert");
    }

    /**
     * Loads the compatibility lighting and fog fragment stage.
     *
     * @return GLSL fragment source
     * @throws IllegalStateException if the bundled resource is missing
     */
    private static String fragmentSource() {
        return ShaderSources.load("model3d/mesh.frag");
    }
}
