package valthorne.graphics.shader;

import valthorne.graphics.texture.Texture;

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
public class BlurShader extends Shader {

    private static final String VERT_SRC = """
            #version 120
            varying vec2 v_uv;
            varying vec4 v_color;
            void main(){
                gl_Position = ftransform();
                v_uv = gl_MultiTexCoord0.st;
                v_color = gl_Color;
            }
            """; // GLSL vertex shader source (passes through UVs and vertex color).

    private static final String FRAG_SRC = """
            #version 120
            uniform sampler2D u_texture;
            uniform vec2 u_texelSize;
            uniform float u_radiusPx;
            varying vec2 v_uv;
            varying vec4 v_color;
            void main(){
                float r = max(0.0, u_radiusPx);
                vec2 o = u_texelSize * r;
                vec4 sum = vec4(0.0);
                sum += texture2D(u_texture, v_uv + vec2(-o.x, -o.y));
                sum += texture2D(u_texture, v_uv + vec2( 0.0, -o.y));
                sum += texture2D(u_texture, v_uv + vec2( o.x, -o.y));
                sum += texture2D(u_texture, v_uv + vec2(-o.x,  0.0));
                sum += texture2D(u_texture, v_uv);
                sum += texture2D(u_texture, v_uv + vec2( o.x,  0.0));
                sum += texture2D(u_texture, v_uv + vec2(-o.x,  o.y));
                sum += texture2D(u_texture, v_uv + vec2( 0.0,  o.y));
                sum += texture2D(u_texture, v_uv + vec2( o.x,  o.y));
                gl_FragColor = (sum / 9.0) * v_color;
            }
            """; // GLSL fragment shader source (3x3 box blur around v_uv).

    /**
     * Creates a new blur shader using the built-in GLSL sources.
     */
    public BlurShader() {
        super(VERT_SRC, FRAG_SRC);
    }

    /**
     * Applies the blur shader to the given texture with the specified blur radius.
     * <p>
     * This method binds the shader with the texture's width, height, and the desired
     * blur radius. It then draws the texture and unbinds the shader.
     *
     * @param texture  the texture to which the blur effect will be applied
     * @param radiusPx the radius of the blur effect in pixels
     */
    public void apply(Texture texture, float radiusPx) {
        bind(texture.getData().width(), texture.getData().height(), radiusPx);
        texture.draw();
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
        setUniform1i("u_texture", 0);
        setUniform2f("u_texelSize", texelX, texelY);
        setUniform1f("u_radiusPx", radiusPx);
    }
}