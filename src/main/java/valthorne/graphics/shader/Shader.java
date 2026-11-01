package valthorne.graphics.shader;

import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxShader;

/**
 * Owns a renderer-independent program created from packaged stage names or bgfx's
 * portable GLSL-based source strings. Source stages compile during construction
 * and reload, once per backend family; drawing never starts the compiler. Shared
 * engine varyings are the default, while the three-argument constructor accepts
 * custom varying declarations. Use vec4/mat3/mat4 uniforms and bgfx sampler macros;
 * desktop GLSL source is not the portable shader language.
 * Uniform values remain in primitive arrays until draw submission;
 * changing a value never changes commands already submitted. Resources belong to
 * the creating render thread and must be disposed before graphics shutdown.
 *
 * <pre>{@code
 * Shader shader = new Shader("engine/quad.vert", "engine/texture.frag");
 * shader.bind();
 * shader.setUniform1i("u_texture", 0);
 * // Draw engine geometry using the compiled stage contract.
 * shader.unbind();
 * shader.dispose();
 * }</pre>
 *
 * <p>For a custom texture fragment stage, supply portable source directly:</p>
 * <pre>{@code
 * String fragment = """
 *         $input v_color, v_uv
 *         #include <bgfx_shader.sh>
 *         SAMPLER2D(u_texture, 0);
 *         uniform vec4 u_tint;
 *         void main() {
 *             gl_FragColor = texture2D(u_texture, v_uv) * v_color * u_tint;
 *         }
 *         """;
 * Shader shader = new Shader("engine/quad.vert", fragment);
 * shader.setUniform4f("u_tint", 1, 0.5f, 0.25f, 1);
 * }</pre>
 *
 * <p>Source can include the packaged {@code bgfx_shader.sh} and
 * {@code bgfx_compute.sh} headers. Other shared code must be included in the
 * supplied string. Varying declarations follow bgfx's one-declaration-per-line
 * format. Construction, reload and a first switch to another backend may block
 * while compiling; preload source shaders outside latency-sensitive drawing.</p>
 */
public class Shader {
    private BgfxShader program; // Owned compiled program, or null after disposal.
    private String vertexSource; // Packaged vertex-stage identifier or portable source.
    private String fragmentSource; // Packaged fragment-stage identifier or portable source.
    private final String varyings; // Shared varying declarations, or null for engine defaults.

    /**
     * Creates a program from packaged identifiers or portable bgfx source strings.
     *
     * @param vertexSource packaged vertex-stage name or portable source
     * @param fragmentSource packaged fragment-stage name or portable source
     */
    public Shader(String vertexSource, String fragmentSource) {
        this(vertexSource, fragmentSource, null);
    }

    /**
     * Creates a program with explicit shared varying declarations. Null varyings
     * select the engine's standard attributes and varyings. Compiler diagnostics
     * propagate without publishing a partially constructed program.
     *
     * @param vertexSource packaged vertex-stage name or portable source
     * @param fragmentSource packaged fragment-stage name or portable source
     * @param varyings bgfx varying declarations, or null for the engine defaults
     */
    public Shader(String vertexSource, String fragmentSource, String varyings) {
        program = new BgfxShader(vertexSource, fragmentSource, varyings);
        this.vertexSource = vertexSource.startsWith("bgfx:") ? vertexSource.substring(5) : vertexSource;
        this.fragmentSource = fragmentSource.startsWith("bgfx:") ? fragmentSource.substring(5) : fragmentSource;
        this.varyings = varyings;
    }

    /**
     * Returns the native implementation borrowed by engine draw submission.
     *
     * @return live compiled program state
     */
    public BgfxShader bgfx() {
        if (program == null) throw new IllegalStateException("Shader is disposed");
        return program;
    }

    /**
     * Selects this program for immediate engine geometry.
     */
    public void bind() {
        bgfx().handle();
        BgfxGraphics.bindShader(this);
    }

    /**
     * Clears the immediate program selection when this program is selected.
     */
    public void unbind() {
        if (BgfxGraphics.shader() == this) BgfxGraphics.bindShader(null);
    }

    /**
     * Reloads stored stages, recompiling inline sources and retaining the old
     * program when compilation or linking fails.
     *
     * @return whether both replacement stages were loaded successfully
     */
    public boolean reload() {
        return reload(vertexSource, fragmentSource);
    }

    /**
     * Atomically replaces the program with packaged stages or portable sources,
     * retaining this shader's shared varying declarations.
     *
     * @param vertexSource replacement vertex identifier or portable source
     * @param fragmentSource replacement fragment identifier or portable source
     * @return whether replacement succeeded; failures are reported to stderr
     */
    public boolean reload(String vertexSource, String fragmentSource) {
        try {
            BgfxShader replacement = new BgfxShader(vertexSource, fragmentSource, varyings);
            if (program != null) program.close();
            program = replacement;
            this.vertexSource = vertexSource.startsWith("bgfx:") ? vertexSource.substring(5) : vertexSource;
            this.fragmentSource = fragmentSource.startsWith("bgfx:") ? fragmentSource.substring(5) : fragmentSource;
            return true;
        } catch (RuntimeException failure) {
            System.err.println(failure.getMessage());
            return false;
        }
    }

    /**
     * Retains scalar/vector values for the next engine draw.
     *
     * @param name declared uniform name
     * @param value retained component value
     * @return this program
     */
    public Shader setUniform1i(String name, int value) {
        bgfx().integer(name, value);
        return this;
    }

    /**
     * Retains scalar/vector values for the next engine draw.
     *
     * @param name declared uniform name
     * @param value retained component value
     * @return this program
     */
    public Shader setUniform1f(String name, float value) {
        bgfx().vector(name, value, 0, 0, 0);
        return this;
    }

    /**
     * Retains scalar/vector values for the next engine draw.
     *
     * @param name declared uniform name
     * @param x retained component value
     * @param y retained component value
     * @return this program
     */
    public Shader setUniform2f(String name, float x, float y) {
        bgfx().vector(name, x, y, 0, 0);
        return this;
    }

    /**
     * Retains scalar/vector values for the next engine draw.
     *
     * @param name declared uniform name
     * @param x retained component value
     * @param y retained component value
     * @param z retained component value
     */
    public void setUniform3f(String name, float x, float y, float z) {
        bgfx().vector(name, x, y, z, 0);
    }

    /**
     * Retains scalar/vector values for the next engine draw.
     *
     * @param name declared uniform name
     * @param x retained component value
     * @param y retained component value
     * @param z retained component value
     * @param w retained component value
     * @return this program
     */
    public Shader setUniform4f(String name, float x, float y, float z, float w) {
        bgfx().vector(name, x, y, z, w);
        return this;
    }

    /**
     * Copies column-major matrix values into retained program storage.
     *
     * @param name declared matrix uniform name
     * @param values matrix values, at least sixteen floats
     * @return this program
     */
    public Shader setUniformMatrix4(String name, float[] values) {
        if (values == null || values.length < 16) throw new IllegalArgumentException("A matrix requires sixteen floats");
        bgfx().values(name, values);
        return this;
    }

    /**
     * Returns the configured portable vertex source or normalized packaged name.
     *
     * @return original source text or packaged vertex identifier without its bgfx prefix
     */
    public String getVertexSource() {
        return vertexSource;
    }

    /**
     * Returns the configured portable fragment source or normalized packaged name.
     *
     * @return original source text or packaged fragment identifier without its bgfx prefix
     */
    public String getFragmentSource() {
        return fragmentSource;
    }

    /**
     * Releases the owned compiled program and removes its immediate selection.
     */
    public void dispose() {
        if (program == null) return;
        unbind();
        program.close();
        program = null;
    }
}
