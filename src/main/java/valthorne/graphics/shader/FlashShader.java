package valthorne.graphics.shader;

/**
 * Time-based flash shader.
 *
 * <h2>What this shader does</h2>
 * <ul>
 *     <li>Samples {@code u_texture} at {@code v_uv} and tints by {@code gl_Color}.</li>
 *     <li>Linearly mixes the sampled RGB toward {@code u_flashColor.rgb} by {@code u_amount}.</li>
 *     <li>Preserves original alpha (flash affects color only).</li>
 * </ul>
 *
 * <h2>Time-driven behavior</h2>
 * <p>
 * This class has no internal state. The flash repeats purely from {@code timeSeconds} and
 * {@code durationSeconds}. Each cycle computes a 0..1 ramp and converts it into a fade-out
 * amount that starts strong and decays to 0.
 * </p>
 *
 * <h2>Uniforms</h2>
 * <ul>
 *     <li>{@code u_texture}: sampler2D bound to texture unit 0.</li>
 *     <li>{@code u_flashColor}: RGB flash color (alpha is unused by the shader).</li>
 *     <li>{@code u_amount}: 0..1 mix amount (0 = no flash, 1 = full flash color).</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FlashShader flash = new FlashShader();
 *
 * // Repeating flash (0.5 seconds per cycle).
 * flash.apply(JGL.getTime(), 0.5f, 1f, 0.2f, 0.2f);
 * // draw your quad(s)...
 * flash.unbind(); // optional
 * }</pre>
 *
 * <h2>Notes</h2>
 * <ul>
 *     <li>If {@code u_amount <= 0} or sampled alpha is near zero, the shader returns the base color.</li>
 *     <li>Make sure {@code durationSeconds > 0} to avoid a division-by-zero / NaN phase.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public class FlashShader extends Shader {

    private static final String VERT_SRC = """
            #version 120
            varying vec2 v_uv;
            varying vec4 v_color;
            void main(){
                gl_Position = ftransform();
                v_uv = gl_MultiTexCoord0.st;
                v_color = gl_Color;
            }
            """; // GLSL vertex shader source (passes UVs + vertex color through).

    private static final String FRAG_SRC = """
            #version 120
            uniform sampler2D u_texture;
            uniform vec4 u_flashColor;
            uniform float u_amount;
            
            varying vec2 v_uv;
            varying vec4 v_color;
            
            void main(){
                vec4 c = texture2D(u_texture, v_uv) * v_color;
            
                float amt = clamp(u_amount, 0.0, 1.0);
                if (amt <= 0.0 || c.a <= 0.001) {
                    gl_FragColor = c;
                    return;
                }
            
                vec3 rgb = mix(c.rgb, u_flashColor.rgb, amt);
                gl_FragColor = vec4(rgb, c.a);
            }
            """; // GLSL fragment shader source (mixes RGB toward flash color, preserves alpha).

    /**
     * Creates a new {@code FlashShader} using the built-in GLSL sources.
     */
    public FlashShader() {
        super(VERT_SRC, FRAG_SRC);
    }

    /**
     * Applies a repeating, time-based flash and binds this shader.
     *
     * <p>Computation:</p>
     * <ul>
     *     <li>{@code phase = (timeSeconds % durationSeconds) / durationSeconds} gives a repeating 0..1 ramp.</li>
     *     <li>{@code amount = 1 - phase} turns that into a fade-out (starts at 1, ends at 0).</li>
     *     <li>{@code amount *= amount} applies a simple ease curve (stronger at the start).</li>
     * </ul>
     *
     * <p>This method sets:</p>
     * <ul>
     *     <li>{@code u_texture = 0}</li>
     *     <li>{@code u_amount = amount}</li>
     *     <li>{@code u_flashColor = (r,g,b,1)}</li>
     * </ul>
     *
     * @param timeSeconds     current time in seconds (e.g., {@code JGL.getTime()})
     * @param durationSeconds length of one flash cycle in seconds (must be {@code > 0})
     * @param r               flash red component
     * @param g               flash green component
     * @param b               flash blue component
     * @throws IllegalArgumentException if {@code durationSeconds <= 0}
     */
    public void apply(float timeSeconds, float durationSeconds, float r, float g, float b) {
        if (durationSeconds <= 0f) throw new IllegalArgumentException("durationSeconds must be > 0");

        bind();

        // Compute repeating 0..1 ramp over duration.
        float phase = (timeSeconds % durationSeconds) / durationSeconds; // 0..1 repeating phase of this cycle.
        float amount = 1.0f - phase;                                     // Fade-out curve (1 → 0).
        amount *= amount;                                                // Optional: sharper start (ease-out).

        setUniform1i("u_texture", 0);
        setUniform1f("u_amount", amount);
        setUniform4f("u_flashColor", r, g, b, 1.0f);
    }
}
