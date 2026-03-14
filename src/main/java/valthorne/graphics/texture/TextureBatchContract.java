package valthorne.graphics.texture;

import valthorne.graphics.shader.Shader;

/**
 * <p>
 * {@code TextureBatchContract} defines the shared rendering contract used by
 * {@link TextureBatch} and its shaders. It centralizes all attribute indices,
 * attribute names, vertex layout constants, instance layout constants, and
 * shader source generation helpers so that both the Java-side renderer and the
 * GLSL-side programs stay in sync.
 * </p>
 *
 * <p>
 * The main purpose of this class is to ensure that the batch renderer uses a
 * single authoritative definition for:
 * </p>
 *
 * <ul>
 *     <li>how many vertices make up the base quad</li>
 *     <li>how many floats are stored per quad vertex</li>
 *     <li>how many floats are stored per instance</li>
 *     <li>which attribute locations map to which shader variables</li>
 *     <li>how the default batch shaders are generated</li>
 *     <li>how texture sampler uniforms are bound</li>
 * </ul>
 *
 * <p>
 * Since the batch renderer relies on instanced rendering, the layout defined
 * here is extremely important. If the attribute indices, names, or buffer
 * packing order ever drift apart from the data written by {@link TextureBatch},
 * rendering will break immediately. Keeping all of that information in one
 * final utility class prevents duplication and makes maintenance much easier.
 * </p>
 *
 * <p>
 * This class also generates shader source strings for the default vertex shader,
 * the default fragment shader, and the reusable fragment preamble used when
 * building custom fragment shaders that still need access to the batch's
 * texture-sampling and clipping logic.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Shader shader = new Shader(
 *         TextureBatchContract.defaultVertexShader(),
 *         TextureBatchContract.buildDefaultFragmentShader(8)
 * );
 *
 * TextureBatchContract.bindAttributes(shader);
 * shader.reload();
 * TextureBatchContract.bindSamplers(shader, 8);
 * }</pre>
 *
 * <p>
 * In normal usage, you will not create instances of this class. It is a pure
 * static contract holder and helper.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 8th, 2026
 */
public final class TextureBatchContract {

    /**
     * The number of vertices used to represent the base quad.
     */
    public static final int QUAD_VERTS = 6;

    /**
     * The number of float components stored for each quad vertex.
     */
    public static final int QUAD_FLOATS_PER_VERT = 4;

    /**
     * The size of a single float in bytes.
     */
    public static final int BYTES_PER_FLOAT = 4;

    /**
     * The number of float components stored for each sprite instance.
     */
    public static final int INST_FLOATS = 22;

    /**
     * The total size in bytes of one instance entry.
     */
    public static final int INST_STRIDE_BYTES = INST_FLOATS * BYTES_PER_FLOAT;

    /**
     * Attribute index for the quad's local position attribute.
     */
    public static final int ATTR_LOCAL = 0;

    /**
     * Attribute index for the quad's local UV attribute.
     */
    public static final int ATTR_UV = 1;

    /**
     * Attribute index for instance position and size.
     */
    public static final int ATTR_XYWH = 2;

    /**
     * Attribute index for instance color.
     */
    public static final int ATTR_COL = 3;

    /**
     * Attribute index for instance texture unit selection.
     */
    public static final int ATTR_TEX = 4;

    /**
     * Attribute index for instance UV rectangle bounds.
     */
    public static final int ATTR_UVRECT = 5;

    /**
     * Attribute index for instance rotation origin.
     */
    public static final int ATTR_ORIGIN = 6;

    /**
     * Attribute index for instance rotation sine and cosine data.
     */
    public static final int ATTR_ROT = 7;

    /**
     * Attribute index for instance clip rectangle data.
     */
    public static final int ATTR_CLIPRECT = 8;

    /**
     * Attribute index for instance clip-enabled state.
     */
    public static final int ATTR_CLIPENABLED = 9;

    /**
     * Shader attribute name for the quad's local position.
     */
    public static final String ATTR_NAME_LOCAL = "a_local";

    /**
     * Shader attribute name for the quad's UV coordinate.
     */
    public static final String ATTR_NAME_UV = "a_uv";

    /**
     * Shader attribute name for instance position and size.
     */
    public static final String ATTR_NAME_XYWH = "i_xywh";

    /**
     * Shader attribute name for instance color.
     */
    public static final String ATTR_NAME_COL = "i_col";

    /**
     * Shader attribute name for instance texture unit selection.
     */
    public static final String ATTR_NAME_TEX = "i_tex";

    /**
     * Shader attribute name for instance UV rectangle.
     */
    public static final String ATTR_NAME_UVRECT = "i_uvRect";

    /**
     * Shader attribute name for instance rotation origin.
     */
    public static final String ATTR_NAME_ORIGIN = "i_origin";

    /**
     * Shader attribute name for instance rotation sine and cosine.
     */
    public static final String ATTR_NAME_ROT = "i_rot";

    /**
     * Shader attribute name for instance clip rectangle.
     */
    public static final String ATTR_NAME_CLIPRECT = "i_clipRect";

    /**
     * Shader attribute name for instance clip-enabled flag.
     */
    public static final String ATTR_NAME_CLIPENABLED = "i_clipEnabled";

    /**
     * Prevents instantiation of this static contract class.
     */
    private TextureBatchContract() {
    }

    /**
     * <p>
     * Builds and returns the default vertex shader source used by
     * {@link TextureBatch}.
     * </p>
     *
     * <p>
     * This shader reads the shared quad's local vertex position and UV, applies
     * per-instance size, origin, rotation, translation, and clip data, then
     * forwards the computed values to the fragment shader.
     * </p>
     *
     * <p>
     * The rotation data is passed as precomputed sine and cosine values in
     * {@code i_rot}, allowing the CPU side to avoid sending raw angles and the
     * shader to avoid computing trigonometric functions per vertex.
     * </p>
     *
     * @return the default vertex shader source code
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
                attribute vec2 i_origin;
                attribute vec2 i_rot;
                attribute vec4 i_clipRect;
                attribute float i_clipEnabled;
                
                varying vec2 v_uv;
                varying vec4 v_col;
                varying float v_tex;
                varying vec2 v_world;
                varying vec4 v_clipRect;
                varying float v_clipEnabled;
                
                void main() {
                    vec2 local = a_local * i_xywh.zw;
                    local -= i_origin;
                
                    vec2 rotated = vec2(
                        local.x * i_rot.y - local.y * i_rot.x,
                        local.x * i_rot.x + local.y * i_rot.y
                    );
                
                    vec2 world = i_xywh.xy + i_origin + rotated;
                
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
     * <p>
     * Builds the default fragment shader source for the batch.
     * </p>
     *
     * <p>
     * The generated shader declares one sampler uniform per supported texture
     * unit, applies shader-side clipping when a clip rectangle is active, selects
     * the correct sampler based on the per-instance texture index, samples the
     * texture, and multiplies the sampled color by the instance tint color.
     * </p>
     *
     * <p>
     * The sampler selection is generated as a chained sequence of conditional
     * branches so the shader can work with an arbitrary number of supported
     * texture units up to the limit provided by the caller.
     * </p>
     *
     * @param maxTextureUnits the number of texture samplers the generated shader should support
     * @return the default fragment shader source code
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
     * <p>
     * Builds a reusable fragment shader preamble for custom batch fragment shaders.
     * </p>
     *
     * <p>
     * This preamble declares the required sampler uniforms and varyings, and also
     * injects helper functions for:
     * </p>
     *
     * <ul>
     *     <li>sampling the currently selected batch texture via {@code sampleBatchTexture(vec2 uv)}</li>
     *     <li>checking whether the current fragment is clipped via {@code batchClipped()}</li>
     * </ul>
     *
     * <p>
     * This helper is useful when building custom fragment shaders that should still
     * participate in the same texture-routing and clipping rules used by the default
     * batch shader.
     * </p>
     *
     * @param maxTextureUnits the number of texture samplers the generated preamble should support
     * @return a fragment shader preamble string that can be concatenated with custom shader logic
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
     * <p>
     * Binds all batch attribute locations on the supplied shader.
     * </p>
     *
     * <p>
     * The attribute indices defined in this contract must match the VBO layout
     * written by {@link TextureBatch}. This method ensures that the shader uses
     * the correct fixed attribute locations before it is linked or reloaded.
     * </p>
     *
     * @param shader the shader whose attributes should be bound to the batch contract
     */
    public static void bindAttributes(Shader shader) {
        shader.bindAttribLocation(ATTR_LOCAL, ATTR_NAME_LOCAL);
        shader.bindAttribLocation(ATTR_UV, ATTR_NAME_UV);
        shader.bindAttribLocation(ATTR_XYWH, ATTR_NAME_XYWH);
        shader.bindAttribLocation(ATTR_COL, ATTR_NAME_COL);
        shader.bindAttribLocation(ATTR_TEX, ATTR_NAME_TEX);
        shader.bindAttribLocation(ATTR_UVRECT, ATTR_NAME_UVRECT);
        shader.bindAttribLocation(ATTR_ORIGIN, ATTR_NAME_ORIGIN);
        shader.bindAttribLocation(ATTR_ROT, ATTR_NAME_ROT);
        shader.bindAttribLocation(ATTR_CLIPRECT, ATTR_NAME_CLIPRECT);
        shader.bindAttribLocation(ATTR_CLIPENABLED, ATTR_NAME_CLIPENABLED);
    }

    /**
     * <p>
     * Binds the batch sampler uniforms on the supplied shader.
     * </p>
     *
     * <p>
     * Each generated sampler uniform is assigned to its matching texture unit
     * index so that {@code u_tex0} samples from texture unit 0,
     * {@code u_tex1} samples from texture unit 1, and so on.
     * </p>
     *
     * <p>
     * The shader is temporarily bound while uniforms are assigned and then unbound
     * before the method returns.
     * </p>
     *
     * @param shader          the shader whose sampler uniforms should be initialized
     * @param maxTextureUnits the number of texture-unit sampler uniforms to bind
     */
    public static void bindSamplers(Shader shader, int maxTextureUnits) {
        shader.bind();
        for (int i = 0; i < maxTextureUnits; i++) {
            shader.setUniform1i("u_tex" + i, i);
        }
        shader.unbind();
    }
}