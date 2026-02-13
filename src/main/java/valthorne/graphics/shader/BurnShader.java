package valthorne.graphics.shader;

/**
 * "Burn away" / dissolve shader built on top of {@link Shader}.
 *
 * <h2>What this shader does</h2>
 * <ul>
 *     <li>Samples the base texture at {@code v_uv} and tints it by {@code gl_Color}.</li>
 *     <li>Generates animated value-noise in UV space using {@code u_time}.</li>
 *     <li>Discards pixels where noise is below {@code u_threshold} (dissolve/burn-away).</li>
 *     <li>Adds a colored burn edge using {@code u_burnColor} and a small smoothstep band.</li>
 * </ul>
 *
 * <h2>Key uniforms</h2>
 * <ul>
 *     <li>{@code u_texture}: sampler2D bound to texture unit 0.</li>
 *     <li>{@code u_time}: time in seconds; drives noise animation.</li>
 *     <li>{@code u_threshold}: 0..1 dissolve threshold. Higher values burn away more of the sprite.</li>
 *     <li>{@code u_burnColor}: RGB edge/glow color and an alpha component (alpha is currently not used for output alpha).</li>
 * </ul>
 *
 * <h2>Threshold behavior</h2>
 * <p>
 * The fragment computes noise {@code n}. If {@code n < threshold} the fragment is discarded.
 * The edge band is {@code smoothstep(th, th + 0.10, n)} which creates a ~10% wide transition
 * in noise-space used to blend between burn color and original color.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BurnShader burn = new BurnShader();
 *
 * // Example: burn away from 0 -> 1 over time
 * float t = (float)elapsedSeconds;
 * float threshold = Math.min(1f, t * 0.25f);
 *
 * burn.apply(t, threshold, 1f, 0.35f, 0.05f, 1f);
 * // draw your textured quad(s)...
 * burn.unbind(); // optional
 * }</pre>
 *
 * <h2>Notes</h2>
 * <ul>
 *     <li>Fragments with near-zero alpha ({@code <= 0.001}) are preserved and not discarded.</li>
 *     <li>The burn effect is UV-based; different UV scaling changes the noise "grain".</li>
 *     <li>This shader uses {@code discard}, so sorting / blending behavior depends on your pipeline.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public class BurnShader extends Shader {

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
            uniform float u_time;
            uniform float u_threshold;
            uniform vec4 u_burnColor;
            varying vec2 v_uv;
            varying vec4 v_color;
            float hash(vec2 p){
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            float noise(vec2 p){
                vec2 i = floor(p);
                vec2 f = fract(p);
                float a = hash(i);
                float b = hash(i + vec2(1.0, 0.0));
                float c = hash(i + vec2(0.0, 1.0));
                float d = hash(i + vec2(1.0, 1.0));
                vec2 u = f*f*(3.0-2.0*f);
                return mix(a, b, u.x) + (c - a)*u.y*(1.0 - u.x) + (d - b)*u.x*u.y;
            }
            void main(){
                vec4 c = texture2D(u_texture, v_uv) * v_color;
                if (c.a <= 0.001) { gl_FragColor = c; return; }
                float n = noise(v_uv * 14.0 + vec2(u_time * 0.15, u_time * 0.10));
                float th = clamp(u_threshold, 0.0, 1.0);
                if (n < th) discard;
                float edge = smoothstep(th, th + 0.10, n);
                float hot  = 1.0 - edge;
                vec3 burnRgb = u_burnColor.rgb;
                vec3 outRgb = mix(burnRgb, c.rgb, edge);
                outRgb += burnRgb * hot * 0.65;
                gl_FragColor = vec4(outRgb, c.a);
            }
            """; // GLSL fragment shader source (animated noise dissolve + burn edge).

    /**
     * Creates a new burn shader using the built-in GLSL sources.
     */
    public BurnShader() {
        super(VERT_SRC, FRAG_SRC);
    }

    /**
     * Binds the shader and sets burn uniforms for the current draw sequence.
     *
     * <p>This method:</p>
     * <ul>
     *     <li>Binds the program via {@link #bind()}.</li>
     *     <li>Sets {@code u_texture} to texture unit 0.</li>
     *     <li>Sets {@code u_time} in seconds (drives noise animation).</li>
     *     <li>Sets {@code u_threshold} (0..1) controlling how much is burned away.</li>
     *     <li>Sets {@code u_burnColor} used for the burn edge color and glow contribution.</li>
     * </ul>
     *
     * @param timeSeconds time in seconds used to animate the noise field
     * @param threshold   dissolve threshold in range {@code [0..1]} (higher = more burned away)
     * @param burnR       burn edge red component
     * @param burnG       burn edge green component
     * @param burnB       burn edge blue component
     * @param burnA       burn edge alpha component (currently not used for output alpha)
     */
    public void apply(float timeSeconds, float threshold, float burnR, float burnG, float burnB, float burnA) {
        bind();
        setUniform1i("u_texture", 0);
        setUniform1f("u_time", timeSeconds);
        setUniform1f("u_threshold", threshold);
        setUniform4f("u_burnColor", burnR, burnG, burnB, burnA);
    }
}