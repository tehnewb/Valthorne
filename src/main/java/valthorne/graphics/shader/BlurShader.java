package valthorne.graphics.shader;

import valthorne.graphics.Sprite;

/**
 * Simple 3x3 box blur shader (fixed-function friendly) built on top of {@link Shader}.
 *
 * <h2>What this shader does</h2>
 * <ul>
 *     <li>Samples a 3x3 neighborhood around the current fragment (9 taps).</li>
 *     <li>Averages those samples to produce a soft blur.</li>
 *     <li>Multiplies the result by the incoming vertex color ({@code gl_Color}).</li>
 * </ul>
 *
 * <h2>How blur radius works</h2>
 * <p>
 * {@code u_radiusPx} is expressed in "pixel units" but becomes meaningful only when paired with
 * {@code u_texelSize}. The shader computes:
 * </p>
 * <pre>{@code
 * vec2 o = u_texelSize * max(0.0, u_radiusPx);
 * }</pre>
 * <p>
 * Where {@code u_texelSize} should be {@code (1/textureWidth, 1/textureHeight)} for the bound texture.
 * Larger radius values increase the sampling offset distance.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BlurShader blur = new BlurShader();
 * Texture texture = ...;
 *
 * // Before drawing your textured quads:
 * blur.apply(texture, 2f);
 *
 * }</pre>
 *
 * <h2>Notes</h2>
 * <ul>
 *     <li>This is a <b>box blur</b>, not a gaussian blur.</li>
 *     <li>Works best on UI panels / sprites where a cheap blur is acceptable.</li>
 *     <li>Assumes the texture is bound to texture unit 0.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public class BlurShader extends TexturedQuadShader {

    /**
     * Fragment source loaded from the packaged blur effect shader resource.
     */
    private static final String FRAG_SRC = ShaderSources.load("effects/blur.frag");

    /**
     * Creates a new blur shader using the built-in GLSL sources.
     */
    public BlurShader() {
        super(FRAG_SRC);
    }

    /**
     * Binds the blur effect using the sprite's backing texture dimensions, draws
     * the sprite immediately, and unbinds the program on normal completion. Requires
     * a current OpenGL context. The previous shader is not restored, and exceptions
     * can leave this program bound.
     *
     * @param sprite sprite with a valid backing texture
     * @param radiusPx blur sampling extent in texture pixels
     */
    public void apply(Sprite sprite, float radiusPx) {
        bind(sprite.getTexture().getData().width(), sprite.getTexture().getData().height(), radiusPx);
        sprite.draw();
        unbind();
    }

    /**
     * Applies the blur shader with the specified texture dimensions and blur radius.
     * <p>
     * This method sets up the shader uniforms for texture size, texel size, and blur radius,
     * and ensures the shader is bound before these values are applied.
     *
     * @param textureWidth  the width of the texture in pixels
     * @param textureHeight the height of the texture in pixels
     * @param radiusPx      the blur radius in pixels
     */
    public void bind(float textureWidth, float textureHeight, float radiusPx) {
        bind();
        float texelX = 1f / textureWidth;
        float texelY = 1f / textureHeight;
        setUniform1i(UNIFORM_TEXTURE, 0);
        setUniform2f("u_texelSize", texelX, texelY);
        setUniform1f("u_radiusPx", radiusPx);
    }
}
