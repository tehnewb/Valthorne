package valthorne.graphics.shader;

/**
 * Shared shader base for textured quad rendering with explicit attributes and uniforms.
 *
 * <p>This contract is used by immediate quad renderers such as {@code Sprite.draw()},
 * framebuffer blits, and standalone sprite effect shaders that should work on modern
 * OpenGL core profiles without relying on fixed-function state.</p>
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
     * Bundled default quad vertex GLSL, loaded once.
     */
    private static final String DEFAULT_VERTEX_SOURCE = ShaderSources.load("core/textured-quad.vert");

    /**
     * Bundled default texture-times-color fragment GLSL, loaded once.
     */
    private static final String DEFAULT_FRAGMENT_SOURCE = ShaderSources.load("core/textured-quad.frag");

    /**
     * Compiles the default bundled textured-quad vertex and fragment stages.
     * Requires a current OpenGL context; drawing code supplies uniforms and textures.
     */
    public TexturedQuadShader() {
        this(null, null);
    }

    /**
     * Compiles a custom fragment stage with the default vertex contract.
     *
     * @param fragmentSource GLSL fragment source, or null/blank for the default
     */
    public TexturedQuadShader(String fragmentSource) {
        this(null, fragmentSource);
    }

    /**
     * Compiles caller-supplied stages, independently substituting defaults for
     * null or blank arguments. Custom stages must match the drawing helper's
     * position, UV, color, sampler, and projection contract.
     *
     * @param vertexSource custom vertex GLSL, or null/blank
     * @param fragmentSource custom fragment GLSL, or null/blank
     */
    public TexturedQuadShader(String vertexSource, String fragmentSource) {
        super(normalizeVertexSource(vertexSource), normalizeFragmentSource(fragmentSource));
    }

    /**
     * Returns the cached bundled vertex source without another resource read.
     *
     * @return default vertex GLSL
     */
    protected static String defaultVertexSource() {
        return DEFAULT_VERTEX_SOURCE;
    }

    /**
     * Returns the cached bundled fragment source without another resource read.
     *
     * @return default fragment GLSL
     */
    protected static String defaultFragmentSource() {
        return DEFAULT_FRAGMENT_SOURCE;
    }

    /**
     * Selects the bundled vertex stage for an absent or blank override.
     *
     * @param vertexSource optional override
     * @return nonblank source passed to compilation
     */
    private static String normalizeVertexSource(String vertexSource) {
        return vertexSource == null || vertexSource.isBlank() ? DEFAULT_VERTEX_SOURCE : vertexSource;
    }

    /**
     * Selects the bundled fragment stage for an absent or blank override.
     *
     * @param fragmentSource optional override
     * @return nonblank source passed to compilation
     */
    private static String normalizeFragmentSource(String fragmentSource) {
        return fragmentSource == null || fragmentSource.isBlank() ? DEFAULT_FRAGMENT_SOURCE : fragmentSource;
    }
}
