package valthorne.graphics.texture;

import valthorne.graphics.shader.Shader;

/**
 * Packed sprite and glyph record sizes shared by the batch writers and native
 * instance uploaders. Compiled stages declare their vertex semantics at build
 * time; sampler bindings are initialized once when a batch shader is created.
 */
public final class TextureBatchContract {

    /**
     * Byte stride of packed glyph metadata followed by a world clip rectangle and flag.
     */
    public static final int GLYPH_STRIDE_BYTES = 84;

    /**
     * Uniform name used for the batch projection matrix.
     */
    public static final String UNIFORM_MVP = "u_mvp";

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
     * Prevents instantiation of this static contract class.
     */
    private TextureBatchContract() {
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
        /*
         * Sampler routing is stable for the program lifetime, avoiding per-draw updates.
         */
        shader.bind();
        for (int i = 0; i < maxTextureUnits; i++) {
            shader.setUniform1i("u_tex" + i, i);
        }
        shader.unbind();
    }

}
