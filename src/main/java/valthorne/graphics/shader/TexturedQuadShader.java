package valthorne.graphics.shader;

/**
 * Shared shader base for textured quad rendering with explicit attributes and uniforms.
 *
 * <p>This contract is used by immediate quad renderers such as {@code Sprite.draw()},
 * framebuffer blits, and standalone sprite effect shaders on every supported
 * graphics backend.</p>
 *
 * @author Albert Beaupre
 * @since August 15th, 2026
 */
public class TexturedQuadShader extends Shader {

    /**
     * Attribute location for two-component quad position.
     */
    public static final int ATTR_POSITION = 0;
    /**
     * Attribute location for two-component texture coordinates.
     */
    public static final int ATTR_UV = 1;
    /**
     * Attribute location for RGBA vertex tint.
     */
    public static final int ATTR_COLOR = 2;

    /**
     * Uniform name for the quad model/view/projection matrix.
     */
    public static final String UNIFORM_MVP = "u_mvp";
    /**
     * Uniform name for the sampled 2D texture.
     */
    public static final String UNIFORM_TEXTURE = "u_texture";

    /**
     * Compiles the default bundled textured-quad vertex and fragment stages.
     * Requires a current graphics device; drawing code supplies uniforms and textures.
     */
    public TexturedQuadShader() {
        this(null, null);
    }

    /**
     * Compiles a custom fragment stage with the default vertex contract.
     *
     * @param fragmentSource packaged fragment identifier, or null/blank for the default
     */
    public TexturedQuadShader(String fragmentSource) {
        this(null, fragmentSource);
    }

    /**
     * Compiles caller-supplied stages, independently substituting defaults for
     * null or blank arguments. Custom stages must match the drawing helper's
     * position, UV, color, sampler, and projection contract.
     *
     * @param vertexSource packaged vertex identifier, or null/blank
     * @param fragmentSource packaged fragment identifier, or null/blank
     */
    public TexturedQuadShader(String vertexSource, String fragmentSource) {
        super(normalizeVertexSource(vertexSource), normalizeFragmentSource(fragmentSource));
    }

    /**
     * Returns the cached bundled vertex source without another resource read.
     *
     * @return default vertex stage identifier
     */
    protected static String defaultVertexSource() {
        /*
         * Logical identifiers are shared by all packaged backend variants.
         */
        return "engine/quad.vert";
    }

    /**
     * Returns the cached bundled fragment source without another resource read.
     *
     * @return default fragment stage identifier
     */
    protected static String defaultFragmentSource() {
        /*
         * Resolve the compiled program family through the active graphics device.
         */
        return "engine/texture.frag";
    }

    /**
     * Selects the bundled vertex stage for an absent or blank override.
     *
     * @param vertexSource optional override
     * @return nonblank source passed to compilation
     */
    private static String normalizeVertexSource(String vertexSource) {
        /*
         * Keep caller-supplied stages intact while selecting backend defaults for omissions.
         */
        return vertexSource == null || vertexSource.isBlank() ? defaultVertexSource() : vertexSource;
    }

    /**
     * Selects the bundled fragment stage for an absent or blank override.
     *
     * @param fragmentSource optional override
     * @return nonblank source passed to compilation
     */
    private static String normalizeFragmentSource(String fragmentSource) {
        /*
         * Compiled native stage names pass through without GLSL rewriting.
         */
        return fragmentSource == null || fragmentSource.isBlank() ? defaultFragmentSource() : fragmentSource;
    }
}
