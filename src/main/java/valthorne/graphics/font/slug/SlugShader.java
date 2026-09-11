package valthorne.graphics.font.slug;

import valthorne.graphics.shader.Shader;
import valthorne.graphics.shader.ShaderSources;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;

/**
 * GLSL shader used by the fast 2D Slug font renderer.
 *
 * <p>This variant keeps Slug's curve coverage fragment shader, but removes the expensive reference
 * vertex dilation path. Glyph quads are padded on the CPU by {@link SlugBatch}, so the vertex shader
 * only expands a compact instance into a four-corner triangle strip.</p>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
final class SlugShader extends Shader {

    /**
     * Vertex attribute location for the unit-quad corner.
     */
    static final int ATTR_CORNER = 0;
    /**
     * Instance attribute location for the glyph's destination rectangle.
     */
    static final int ATTR_RECT = 1;
    /**
     * Instance attribute location for the glyph's curve-coordinate rectangle.
     */
    static final int ATTR_TEX_RECT = 2;
    /**
     * Instance attribute location for packed glyph lookup information.
     */
    static final int ATTR_GLYPH = 3;
    /**
     * Instance attribute location for band lookup information.
     */
    static final int ATTR_BAND = 4;
    /**
     * Instance attribute location for glyph color.
     */
    static final int ATTR_COLOR = 5;
    /**
     * Instance attribute location for the glyph's rasterization scale in pixels per em.
     */
    static final int ATTR_PIXELS_PER_EM = 6;

    private int mvpLocation; // Cached model-view-projection uniform location in the linked program.

    /**
     * Creates the shader program.
     */
    public SlugShader() {
        super(vertexSource(), fragmentSource());
        // Attribute locations are explicit in the GLSL; no second compile/link needed.
        bind();
        mvpLocation = glGetUniformLocation(getProgramID(), "u_mvp");
        setUniform1i("u_curveTexture", 0);
        setUniform1i("u_bandTexture", 1);
        unbind();
    }

    /**
     * Loads the packaged Slug instance-expansion vertex program for shader creation.
     * Resource lookup and failure handling are delegated to ShaderSources.
     *
     * @return vertex shader source text
     */
    private static String vertexSource() {
        return ShaderSources.load("font/slug.vert");
    }

    /**
     * Loads the packaged Slug curve-coverage fragment program. This source samples
     * the curve and band textures configured by the constructor.
     *
     * @return fragment shader source text
     */
    private static String fragmentSource() {
        return ShaderSources.load("font/slug.frag");
    }

    /**
     * Returns the model-view-projection uniform location cached after initial linking.
     * The value belongs to this program and must not be reused with another program;
     * manual relinking can invalidate cached uniform locations.
     *
     * @return cached uniform location, or minus one if the uniform was not active
     */
    int mvpLocation() {
        return mvpLocation;
    }
}
