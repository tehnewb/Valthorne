package valthorne.graphics.shader;

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
 *
 * // Before drawing your textured quads:
 * blur.apply(1f / texWidth, 1f / texHeight, 2f);
 * // draw calls...
 * blur.unbind(); // optional, if your pipeline expects it
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
     * Binds the shader and sets blur uniforms for the current draw sequence.
     *
     * <p>This method:</p>
     * <ul>
     *     <li>Binds the program via {@link #bind()}.</li>
     *     <li>Sets {@code u_texture} to texture unit 0.</li>
     *     <li>Sets {@code u_texelSize} to the provided texel step (usually {@code 1/width, 1/height}).</li>
     *     <li>Sets {@code u_radiusPx} to the provided radius (clamped in shader to {@code >= 0}).</li>
     * </ul>
     *
     * @param texelX   texel size x ({@code 1/textureWidth})
     * @param texelY   texel size y ({@code 1/textureHeight})
     * @param radiusPx blur radius multiplier in "pixel units" (non-negative recommended)
     */
    public void apply(float texelX, float texelY, float radiusPx) {
        bind();
        setUniform1i("u_texture", 0);
        setUniform2f("u_texelSize", texelX, texelY);
        setUniform1f("u_radiusPx", radiusPx);
    }
}