package valthorne.graphics.shader;

import valthorne.graphics.Sprite;

/**
 * Soft glow around a sprite using alpha falloff sampling.
 *
 * <h2>What this shader does</h2>
 * <ul>
 *     <li>Samples the sprite normally for opaque pixels (alpha &gt; ~0.001).</li>
 *     <li>For transparent pixels, samples nearby alpha at multiple radii.</li>
 *     <li>Converts nearby alpha into a glow strength and outputs {@code u_glowColor} with scaled alpha.</li>
 * </ul>
 *
 * <h2>How glow is computed</h2>
 * <ul>
 *     <li>Three radii are used: {@code r1 = radius*0.35}, {@code r2 = radius*0.70}, {@code r3 = radius} (each clamped to &gt;= 1px).</li>
 *     <li>At each radius, the shader samples 8 directions (4 cardinal + 4 diagonals).</li>
 *     <li>Samples are weighted by radius: r1 = 1.00, r2 = 0.75, r3 = 0.45.</li>
 *     <li>The sum is normalized, shaped ({@code pow(glow, 1.8)}), then multiplied by {@code u_intensity}.</li>
 * </ul>
 *
 * <h2>Important notes</h2>
 * <ul>
 *     <li>{@code u_texelSize} must be {@code (1/textureWidth, 1/textureHeight)} or the radius will be wrong.</li>
 *     <li>{@code u_radiusPx} is measured in <b>source texture pixels</b>, not screen pixels.</li>
 *     <li>Only transparent pixels output glow. Sprite pixels output the sprite normally.</li>
 *     <li>This is a fixed sample-count glow (24 samples + center). It is stable and predictable.</li>
 * </ul>
 *
 * <h2>Uniforms</h2>
 * <ul>
 *     <li>{@code u_texture} (sampler2D): texture unit index (use 0).</li>
 *     <li>{@code u_texelSize} (vec2): texel size ({@code 1/width}, {@code 1/height}).</li>
 *     <li>{@code u_radiusPx} (float): glow radius in source texture pixels.</li>
 *     <li>{@code u_intensity} (float): glow strength multiplier (typical range 0..3).</li>
 *     <li>{@code u_glowColor} (vec4): glow RGBA color (alpha is scaled by computed glow).</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * GlowShader glow = new GlowShader();
 *
 * Texture texture = ...;
 *
 * glow.apply(texture, 6f, 1.25f, 1f, 0.8f, 0.2f, 1f); // warm glow
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public class GlowShader extends TexturedQuadShader {

    /**
     * Fragment source loaded from the packaged glow effect shader resource.
     */
    private static final String FRAG_SRC = ShaderSources.load("effects/glow.frag");

    /**
     * Creates a new {@code GlowShader} using the built-in GLSL sources.
     */
    public GlowShader() {
        super(FRAG_SRC);
    }

    /**
     * Binds the glow effect using the sprite's backing texture dimensions, draws
     * the sprite immediately, and unbinds the program on normal completion. Requires
     * a current OpenGL context. The previous shader is not restored, and exceptions
     * can leave this program bound.
     *
     * @param sprite sprite with a valid backing texture
     * @param radiusPx glow sampling extent in texture pixels
     * @param intensity glow strength multiplier
     * @param r glow red component
     * @param g glow green component
     * @param b glow blue component
     * @param a glow alpha component
     */
    public void apply(Sprite sprite, float radiusPx, float intensity, float r, float g, float b, float a) {
        bind(sprite.getTexture().getData().width(), sprite.getTexture().getData().height(), radiusPx, intensity, r, g, b, a);
        sprite.draw();
        unbind();
    }

    /**
     * Configures and binds this shader program for rendering with a soft glow effect.
     *
     * @param textureWidth  the width of the texture in pixels
     * @param textureHeight the height of the texture in pixels
     * @param radiusPx      the glow radius in texture pixels (>= 0)
     * @param intensity     the glow strength multiplier (>= 0 recommended)
     * @param r             the red component of the glow color
     * @param g             the green component of the glow color
     * @param b             the blue component of the glow color
     * @param a             the alpha component of the glow color
     */
    public void bind(float textureWidth, float textureHeight, float radiusPx, float intensity, float r, float g, float b, float a) {
        bind();
        setUniform1i(UNIFORM_TEXTURE, 0);

        float texelX = 1f / textureWidth;
        float texelY = 1f / textureHeight;
        setUniform2f("u_texelSize", texelX, texelY);

        setUniform1f("u_radiusPx", radiusPx);
        setUniform1f("u_intensity", intensity);
        setUniform4f("u_glowColor", r, g, b, a);
    }
}
