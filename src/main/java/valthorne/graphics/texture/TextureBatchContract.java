package valthorne.graphics.texture;

import valthorne.graphics.shader.Shader;

/**
 * <h1>TextureBatchContract</h1>
 *
 * <p>
 * {@code TextureBatchContract} defines the shared rendering contract used by {@link TextureBatch},
 * {@link TextureBatchShader}, and any custom shader intended to work with the texture batch
 * pipeline. It centralizes all fixed constants, attribute locations, attribute names, vertex shader
 * defaults, fragment shader generation, and sampler binding helpers so every batch-compatible shader
 * uses the exact same layout.
 * </p>
 *
 * <p>
 * This class is intentionally stateless and non-instantiable. It exists purely as a shared source of
 * truth for the batch rendering format. Any code that wants to remain compatible with the batch
 * should rely on these constants and helper methods instead of duplicating names or indices manually.
 * </p>
 *
 * <h2>What the contract includes</h2>
 *
 * <ul>
 *     <li>Quad geometry constants used for the static shared sprite mesh</li>
 *     <li>Per-instance layout constants that define how instance data is packed</li>
 *     <li>Attribute locations and names for local position, UVs, tint, texture unit, and clipping</li>
 *     <li>A default batch-compatible vertex shader</li>
 *     <li>A generated default fragment shader for a configurable number of samplers</li>
 *     <li>A generated fragment preamble for partial fragment shader authoring</li>
 *     <li>Helpers for binding attributes and texture samplers on shader objects</li>
 * </ul>
 *
 * <h2>Why this exists</h2>
 *
 * <p>
 * Without a shared contract, it is easy for attribute indices, names, or sampler bindings to drift
 * out of sync between the batch and custom shaders. This class prevents that problem by keeping the
 * entire agreement in one place.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Shader shader = new Shader(
 *     TextureBatchContract.defaultVertexShader(),
 *     TextureBatchContract.buildDefaultFragmentShader(8)
 * );
 *
 * TextureBatchContract.bindAttributes(shader);
 * shader.reload();
 * TextureBatchContract.bindSamplers(shader, 8);
 * }</pre>
 *
 * <h2>Partial fragment example</h2>
 * <pre>{@code
 * String fragment = TextureBatchContract.buildFragmentPreamble(8) + """
 * void main() {
 *     if (batchClipped()) discard;
 *     gl_FragColor = sampleBatchTexture(v_uv) * v_col;
 * }
 * """;
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 8th, 2026
 */
public final class TextureBatchContract {

    /**
     * The number of vertices used to represent one quad as two triangles.
     */
    public static final int QUAD_VERTS = 6;

    /**
     * The number of floats stored for each vertex in the static quad mesh.
     */
    public static final int QUAD_FLOATS_PER_VERT = 4;

    /**
     * The size in bytes of one float value.
     */
    public static final int BYTES_PER_FLOAT = 4;

    /**
     * The number of floats stored for one sprite instance in the instance buffer.
     */
    public static final int INST_FLOATS = 18;

    /**
     * The size in bytes of one packed sprite instance record.
     */
    public static final int INST_STRIDE_BYTES = INST_FLOATS * BYTES_PER_FLOAT;

    /**
     * Attribute index for the local quad position.
     */
    public static final int ATTR_LOCAL = 0;

    /**
     * Attribute index for the local quad UV coordinates.
     */
    public static final int ATTR_UV = 1;

    /**
     * Attribute index for the packed destination x, y, width, and height.
     */
    public static final int ATTR_XYWH = 2;

    /**
     * Attribute index for the per-instance tint color.
     */
    public static final int ATTR_COL = 3;

    /**
     * Attribute index for the per-instance texture unit selector.
     */
    public static final int ATTR_TEX = 4;

    /**
     * Attribute index for the per-instance UV rectangle.
     */
    public static final int ATTR_UVRECT = 5;

    /**
     * Attribute index for the per-instance clip rectangle.
     */
    public static final int ATTR_CLIPRECT = 6;

    /**
     * Attribute index for the per-instance clip enabled flag.
     */
    public static final int ATTR_CLIPENABLED = 7;

    /**
     * Attribute name for the local quad position.
     */
    public static final String ATTR_NAME_LOCAL = "a_local";

    /**
     * Attribute name for the local quad UV coordinates.
     */
    public static final String ATTR_NAME_UV = "a_uv";

    /**
     * Attribute name for the packed destination x, y, width, and height.
     */
    public static final String ATTR_NAME_XYWH = "i_xywh";

    /**
     * Attribute name for the per-instance tint color.
     */
    public static final String ATTR_NAME_COL = "i_col";

    /**
     * Attribute name for the per-instance texture unit selector.
     */
    public static final String ATTR_NAME_TEX = "i_tex";

    /**
     * Attribute name for the per-instance UV rectangle.
     */
    public static final String ATTR_NAME_UVRECT = "i_uvRect";

    /**
     * Attribute name for the per-instance clip rectangle.
     */
    public static final String ATTR_NAME_CLIPRECT = "i_clipRect";

    /**
     * Attribute name for the per-instance clip enabled flag.
     */
    public static final String ATTR_NAME_CLIPENABLED = "i_clipEnabled";

    /**
     * Prevents construction of this utility contract class.
     *
     * <p>
     * This class only contains constants and helper methods and is not meant to be instantiated.
     * </p>
     */
    private TextureBatchContract() {
    }

    /**
     * Returns the default vertex shader source used by the texture batch system.
     *
     * <p>
     * The shader expects all attribute names and locations defined by this contract. It transforms the
     * shared local quad into world space using the per-instance destination rectangle, computes the
     * final interpolated UV coordinate using the instance UV rectangle, forwards tint color and
     * texture unit data, and also forwards per-instance clipping data.
     * </p>
     *
     * @return the complete GLSL 120 vertex shader source for batch rendering
     */
    public static String defaultVertexShader() {
        return """
                #version 120
                
                attribute vec2 a_local;
                attribute vec2 a_uv;
                attribute vec4 i_xywh;
                attribute vec4 i_col;
                attribute float i_tex;
                attribute vec4 i_uvRect;
                attribute vec4 i_clipRect;
                attribute float i_clipEnabled;
                
                varying vec2 v_uv;
                varying vec4 v_col;
                varying float v_tex;
                varying vec2 v_world;
                varying vec4 v_clipRect;
                varying float v_clipEnabled;
                
                void main() {
                    vec2 world = i_xywh.xy + (a_local * i_xywh.zw);
                    gl_Position = gl_ModelViewProjectionMatrix * vec4(world.xy, 0.0, 1.0);
                    v_uv = mix(i_uvRect.xy, i_uvRect.zw, a_uv);
                    v_col = i_col;
                    v_tex = i_tex;
                    v_world = world;
                    v_clipRect = i_clipRect;
                    v_clipEnabled = i_clipEnabled;
                }
                """;
    }

    /**
     * Builds the standard default fragment shader source for the texture batch.
     *
     * <p>
     * The generated shader declares one sampler uniform per supported texture unit, performs optional
     * clip-rectangle discard testing, selects the correct sampler based on {@code v_tex}, samples the
     * texture, and multiplies the sampled color by the interpolated tint color.
     * </p>
     *
     * @param maxTextureUnits the number of sampler uniforms to generate
     * @return the complete GLSL 120 fragment shader source
     */
    public static String buildDefaultFragmentShader(int maxTextureUnits) {
        StringBuilder sb = new StringBuilder();
        sb.append("#version 120\n");
        for (int i = 0; i < maxTextureUnits; i++) {
            sb.append("uniform sampler2D u_tex").append(i).append(";\n");
        }
        sb.append("""
                varying vec2 v_uv;
                varying vec4 v_col;
                varying float v_tex;
                varying vec2 v_world;
                varying vec4 v_clipRect;
                varying float v_clipEnabled;
                
                void main() {
                    if (v_clipEnabled > 0.5) {
                        if (v_world.x < v_clipRect.x || v_world.y < v_clipRect.y ||
                            v_world.x > v_clipRect.x + v_clipRect.z ||
                            v_world.y > v_clipRect.y + v_clipRect.w) {
                            discard;
                        }
                    }
                
                    vec4 c;
                """);

        sb.append("    float t = v_tex;\n");
        sb.append("    if (t < 0.5) c = texture2D(u_tex0, v_uv);\n");
        for (int i = 1; i < maxTextureUnits; i++) {
            sb.append("    else if (t < ").append(i).append(".5) c = texture2D(u_tex").append(i).append(", v_uv);\n");
        }
        sb.append("""
                    else c = texture2D(u_tex0, v_uv);
                    gl_FragColor = c * v_col;
                }
                """);
        return sb.toString();
    }

    /**
     * Builds the reusable fragment preamble used for partial batch fragment shaders.
     *
     * <p>
     * The returned source includes:
     * </p>
     * <ul>
     *     <li>The GLSL version declaration</li>
     *     <li>One sampler uniform per supported texture unit</li>
     *     <li>All batch varyings needed by fragment effects</li>
     *     <li>A {@code sampleBatchTexture(vec2)} helper for sampler selection</li>
     *     <li>A {@code batchClipped()} helper for clip-rectangle testing</li>
     * </ul>
     *
     * <p>
     * This method is useful when you want to write only the custom {@code main()} body while reusing
     * the standard batch texture selection and clipping logic.
     * </p>
     *
     * @param maxTextureUnits the number of sampler uniforms to generate
     * @return the GLSL preamble that should appear before a custom fragment shader body
     */
    public static String buildFragmentPreamble(int maxTextureUnits) {
        StringBuilder sb = new StringBuilder();
        sb.append("#version 120\n");
        for (int i = 0; i < maxTextureUnits; i++) {
            sb.append("uniform sampler2D u_tex").append(i).append(";\n");
        }
        sb.append("""
                varying vec2 v_uv;
                varying vec4 v_col;
                varying float v_tex;
                varying vec2 v_world;
                varying vec4 v_clipRect;
                varying float v_clipEnabled;
                
                vec4 sampleBatchTexture(vec2 uv) {
                    float t = v_tex;
                """);

        sb.append("    if (t < 0.5) return texture2D(u_tex0, uv);\n");
        for (int i = 1; i < maxTextureUnits; i++) {
            sb.append("    else if (t < ").append(i).append(".5) return texture2D(u_tex").append(i).append(", uv);\n");
        }

        sb.append("""
                    return texture2D(u_tex0, uv);
                }
                
                bool batchClipped() {
                    return v_clipEnabled > 0.5 &&
                           (v_world.x < v_clipRect.x ||
                            v_world.y < v_clipRect.y ||
                            v_world.x > v_clipRect.x + v_clipRect.z ||
                            v_world.y > v_clipRect.y + v_clipRect.w);
                }
                
                """);
        return sb.toString();
    }

    /**
     * Binds all required batch attribute locations on the supplied shader.
     *
     * <p>
     * This method should be called before the shader is reloaded or linked so that the batch and the
     * shader agree on every attribute index.
     * </p>
     *
     * @param shader the shader that should receive the batch attribute bindings
     */
    public static void bindAttributes(Shader shader) {
        shader.bindAttribLocation(ATTR_LOCAL, ATTR_NAME_LOCAL);
        shader.bindAttribLocation(ATTR_UV, ATTR_NAME_UV);
        shader.bindAttribLocation(ATTR_XYWH, ATTR_NAME_XYWH);
        shader.bindAttribLocation(ATTR_COL, ATTR_NAME_COL);
        shader.bindAttribLocation(ATTR_TEX, ATTR_NAME_TEX);
        shader.bindAttribLocation(ATTR_UVRECT, ATTR_NAME_UVRECT);
        shader.bindAttribLocation(ATTR_CLIPRECT, ATTR_NAME_CLIPRECT);
        shader.bindAttribLocation(ATTR_CLIPENABLED, ATTR_NAME_CLIPENABLED);
    }

    /**
     * Binds the sampler uniforms used by the texture batch on the supplied shader.
     *
     * <p>
     * This method binds the shader, assigns each {@code u_texN} uniform to the texture unit index
     * {@code N}, and then unbinds the shader again.
     * </p>
     *
     * @param shader          the shader whose sampler uniforms should be assigned
     * @param maxTextureUnits the number of texture sampler uniforms to bind
     */
    public static void bindSamplers(Shader shader, int maxTextureUnits) {
        shader.bind();
        for (int i = 0; i < maxTextureUnits; i++) {
            shader.setUniform1i("u_tex" + i, i);
        }
        shader.unbind();
    }
}