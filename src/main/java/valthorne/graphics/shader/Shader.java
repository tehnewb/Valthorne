package valthorne.graphics.shader;

import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxShader;

/**
 * Owns a renderer-independent program identified by packaged vertex/fragment stage
 * names. The active graphics backend selects the corresponding compiled shader
 * containers. Uniform values remain in primitive arrays until draw submission;
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
 */
public class Shader {
    private BgfxShader program; // Owned compiled program, or null after disposal.
    private String vertexSource; // Packaged vertex-stage identifier.
    private String fragmentSource; // Packaged fragment-stage identifier.

    /**
     * Loads a program compiled for the current graphics backend.
     *
     * @param vertexSource packaged vertex-stage name without binary suffix
     * @param fragmentSource packaged fragment-stage name without binary suffix
     */
    public Shader(String vertexSource, String fragmentSource) {
        this.vertexSource = stage(vertexSource);
        this.fragmentSource = stage(fragmentSource);
        program = new BgfxShader(this.vertexSource, this.fragmentSource);
    }

    /**
     * Validates and normalizes a stage resource identifier.
     *
     * @param source packaged stage identifier
     * @return normalized stage name
     */
    private static String stage(String source) {
        /*
         * Source text is never compiled against one API at runtime; every backend
         * consumes the same logical stage name from its own compiled directory.
         */
        if (source == null) throw new NullPointerException("stage");
        if (source.startsWith("bgfx:")) source = source.substring(5);
        if (source.isBlank() || source.indexOf('\n') >= 0 || source.indexOf('\r') >= 0 || source.contains(".."))
            throw new IllegalArgumentException("Expected a packaged shader stage name");
        return source;
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
     * Reloads the stored stage resources, retaining the old program on failure.
     *
     * @return whether both replacement stages were loaded successfully
     */
    public boolean reload() {
        return reload(vertexSource, fragmentSource);
    }

    /**
     * Atomically replaces the program with another packaged stage pair.
     *
     * @param vertexSource replacement vertex-stage identifier
     * @param fragmentSource replacement fragment-stage identifier
     * @return whether replacement succeeded; failures are reported to stderr
     */
    public boolean reload(String vertexSource, String fragmentSource) {
        try {
            String vertex = stage(vertexSource);
            String fragment = stage(fragmentSource);
            BgfxShader replacement = new BgfxShader(vertex, fragment);
            if (program != null) program.close();
            program = replacement;
            this.vertexSource = vertex;
            this.fragmentSource = fragment;
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
     * Returns the configured vertex-stage resource name.
     *
     * @return packaged vertex identifier
     */
    public String getVertexSource() {
        return vertexSource;
    }

    /**
     * Returns the configured fragment-stage resource name.
     *
     * @return packaged fragment identifier
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
