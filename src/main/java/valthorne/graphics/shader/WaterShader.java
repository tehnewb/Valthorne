package valthorne.graphics.shader;

import valthorne.graphics.texture.Texture;

/**
 * Simple UV-distortion "water" shader (wobble / ripple) for 2D sprites.
 *
 * <h2>What this shader does</h2>
 * <ul>
 *     <li>Samples the base texture normally, but perturbs the UV coordinates over time.</li>
 *     <li>Uses two sine waves:
 *         <ul>
 *             <li>A horizontal wave that offsets {@code uv.y} based on {@code v_uv.x}.</li>
 *             <li>A vertical wave that offsets {@code uv.x} based on {@code v_uv.y}.</li>
 *         </ul>
 *     </li>
 *     <li>The result is a continuous animated ripple that looks like water distortion.</li>
 * </ul>
 *
 * <h2>Coordinate / unit notes</h2>
 * <ul>
 *     <li>{@code u_texelSize} must be {@code (1/textureWidth, 1/textureHeight)}.</li>
 *     <li>{@code u_amp} is in <b>pixels</b>. The shader converts it to UV units via {@code u_texelSize * u_amp}.</li>
 *     <li>{@code u_freq} is frequency in UV space (larger = more ripples).</li>
 *     <li>{@code u_speed} scales how fast the ripples move over time.</li>
 * </ul>
 *
 * <h2>Uniforms</h2>
 * <ul>
 *     <li>{@code u_texture} (sampler2D): texture unit index (use 0).</li>
 *     <li>{@code u_texelSize} (vec2): texel size ({@code 1/width}, {@code 1/height}).</li>
 *     <li>{@code u_time} (float): time in seconds (e.g., {@code JGL.getTime()}).</li>
 *     <li>{@code u_amp} (float): distortion amplitude in pixels.</li>
 *     <li>{@code u_freq} (float): ripple frequency.</li>
 *     <li>{@code u_speed} (float): ripple speed multiplier.</li>
 * </ul>
 *
 * <h2>Visual behavior</h2>
 * <ul>
 *     <li>Amplitude too high can cause UVs to sample outside the sprite and smear/warp edges.</li>
 *     <li>Because this uses raw {@code texture2D} sampling, you may see edge bleed unless your atlas
 *         has padding or you clamp regions appropriately.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * WaterShader water = new WaterShader();
 * Texture waterTexture = ...;
 *
 * water.apply(waterTexture, JGL.getTime(), 3f, 18f, 2.0f);
 * // draw sprite(s)...
 * water.unbind(); // optional
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public class WaterShader extends Shader {

    private static final String VERT_SRC = """
            #version 120
            varying vec2 v_uv;
            varying vec4 v_color;
            void main(){
                gl_Position = ftransform();
                v_uv = gl_MultiTexCoord0.st;
                v_color = gl_Color;
            }
            """;

    public static final String FRAG_SRC = """
            #version 120
            uniform sampler2D u_texture;
            uniform vec2 u_texelSize;
            uniform float u_time;
            uniform float u_amp;
            uniform float u_freq;
            uniform float u_speed;
            varying vec2 v_uv;
            varying vec4 v_color;
            void main(){
                vec2 amp = u_texelSize * u_amp;
                float w1 = sin(v_uv.x * u_freq + u_time * u_speed);
                float w2 = sin(v_uv.y * (u_freq * 0.8) - u_time * (u_speed * 1.15));
                vec2 uv = v_uv;
                uv.y += w1 * amp.y;
                uv.x += w2 * amp.x;
                gl_FragColor = texture2D(u_texture, uv) * v_color;
            }
            """;

    /**
     * Creates a new {@code WaterShader} using the built-in GLSL sources.
     */
    public WaterShader() {
        super(VERT_SRC, FRAG_SRC);
    }

    /**
     * Binds this shader and sets uniforms for the animated water distortion.
     *
     * @param texture     the texture being drawn (used to compute texel size)
     * @param timeSeconds time in seconds (e.g., {@code JGL.getTime()})
     * @param ampPx       distortion amplitude in pixels (>= 0 recommended)
     * @param freq        ripple frequency (typical range 6..30 depending on look)
     * @param speed       ripple speed multiplier (typical range 0..5)
     */
    public void apply(Texture texture, float timeSeconds, float ampPx, float freq, float speed) {
        bind();
        setUniform1i("u_texture", 0);

        float texelX = 1f / texture.getData().width();
        float texelY = 1f / texture.getData().height();
        setUniform2f("u_texelSize", texelX, texelY);

        setUniform1f("u_time", timeSeconds);
        setUniform1f("u_amp", ampPx);
        setUniform1f("u_freq", freq);
        setUniform1f("u_speed", speed);
    }
}