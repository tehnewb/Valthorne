package valthorne.graphics.shader;

import valthorne.graphics.texture.Texture;
import valthorne.math.MathUtils;

/**
 * Reflection shader for drawing a vertically mirrored "water reflection" under a sprite.
 *
 * <h2>What it does</h2>
 * <ul>
 *     <li>Draws a second copy of a {@link Texture} directly below its current position.</li>
 *     <li>Mirrors the sampled UVs vertically so the reflection is an inverted version of the sprite.</li>
 *     <li>Applies an optional horizontal sine-wave ripple in UV space (amplitude is specified in pixels).</li>
 *     <li>Fades alpha out toward the bottom of the reflection quad.</li>
 *     <li>Applies a subtle tint mix to push the reflection toward a desired color.</li>
 * </ul>
 *
 * <h2>Important behavior</h2>
 * <ul>
 *     <li>This temporarily changes the {@link Texture}'s position and size to draw the reflection quad,
 *     then restores the original values before returning.</li>
 *     <li>This assumes texture unit 0 is active (or at least the {@link Texture#draw()} call binds appropriately).</li>
 *     <li>This shader is GLSL 120 and uses fixed-function varyings so it matches your current pipeline.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ReflectionShader reflection = new ReflectionShader();
 *
 * // In your render loop:
 * float time = (float) (System.nanoTime() * 1e-9);
 *
 * // Draw the sprite normally.
 * playerTexture.draw();
 *
 * // Draw a reflection under it (35% height, slightly bluish, with small ripple).
 * reflection.apply(
 *         playerTexture,
 *         time,
 *         0.35f,          // amount: reflection height as a fraction of sprite height
 *         0.65f,          // alpha: overall reflection strength
 *         0.60f, 0.75f, 1.00f, // tint RGB
 *         2.0f,           // rippleAmpPx: ripple amplitude in pixels
 *         18.0f,          // rippleFreq: frequency across X
 *         2.5f            // rippleSpeed: speed multiplier
 * );
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 22nd, 2026
 */
public class ReflectionShader extends Shader {

    private static final String VERT_SRC = """
            #version 120
            varying vec2 v_uv;
            varying vec4 v_color;
            void main(){
                gl_Position = ftransform();
                v_uv = gl_MultiTexCoord0.st;
                v_color = gl_Color;
            }
            """; // GLSL 120 vertex shader source.

    private static final String FRAG_SRC = """
            #version 120
            uniform sampler2D u_texture;
            
            uniform float u_alpha;
            uniform vec4  u_tint;
            uniform float u_time;
            
            uniform float u_rippleAmpPx;
            uniform float u_rippleFreq;
            uniform float u_rippleSpeed;
            
            uniform vec2  u_texelSize;
            
            varying vec2 v_uv;
            varying vec4 v_color;
            
            void main(){
                vec2 uv = v_uv;
            
                // Mirror vertically: top of reflection quad samples bottom of sprite.
                uv.y = 1.0 - uv.y;
            
                float ampPx = max(0.0, u_rippleAmpPx);
                if (ampPx > 0.0) {
                    vec2 amp = u_texelSize * ampPx;
                    float w = sin(uv.x * u_rippleFreq + u_time * u_rippleSpeed);
                    uv.x += w * amp.x;
                }
            
                vec4 c = texture2D(u_texture, uv) * v_color;
            
                // Fade out as we go downward (top of quad is strongest).
                float fade = clamp(v_uv.y, 0.0, 1.0);
                fade = fade * fade;
            
                vec3 rgb = mix(c.rgb, u_tint.rgb, 0.35);
                float a = c.a * fade * clamp(u_alpha, 0.0, 1.0);
            
                gl_FragColor = vec4(rgb, a);
            }
            """; // GLSL 120 fragment shader source.

    /**
     * Creates a reflection shader using built-in GLSL 120 sources.
     *
     * <p>This compiles and links the shader program immediately via {@link Shader#Shader(String, String)}.</p>
     */
    public ReflectionShader() {
        super(VERT_SRC, FRAG_SRC);
    }

    /**
     * Draws a reflection of {@code texture} directly below it using this shader.
     *
     * <p>How this method works:</p>
     * <ul>
     *     <li>Reads the texture's current (x,y,width,height).</li>
     *     <li>Computes the reflection height: {@code reflectionHeight = height * clamp01(amount)}.</li>
     *     <li>Temporarily moves the texture to {@code (x, y - reflectionHeight)} and sets its size to {@code (width, reflectionHeight)}.</li>
     *     <li>Binds the shader and uploads all uniforms, including {@code u_texelSize} derived from the backing texture data size.</li>
     *     <li>Calls {@link Texture#draw()} to render the reflection quad.</li>
     *     <li>Unbinds the shader and restores the texture's original position/size.</li>
     * </ul>
     *
     * <p>Uniform meanings:</p>
     * <ul>
     *     <li>{@code u_alpha}: overall reflection alpha multiplier (0..1 recommended).</li>
     *     <li>{@code u_tint}: RGB tint (alpha component is ignored in shader; it uses 1.0).</li>
     *     <li>{@code u_time}: time in seconds used to animate ripples.</li>
     *     <li>{@code u_rippleAmpPx}: ripple amplitude in pixels (converted to UV by multiplying with {@code u_texelSize}).</li>
     *     <li>{@code u_rippleFreq}: ripple frequency across X (higher = more waves).</li>
     *     <li>{@code u_rippleSpeed}: ripple animation speed multiplier.</li>
     * </ul>
     *
     * <p>Early-outs:</p>
     * <ul>
     *     <li>If {@code amount <= 0} (after clamping), nothing is drawn and the method returns.</li>
     * </ul>
     *
     * @param texture     the sprite texture to reflect (must be non-null)
     * @param timeSeconds time in seconds (usually accumulated time) used for animation
     * @param amount      reflection height as a fraction of the sprite height (clamped to 0..1)
     * @param alpha       overall reflection alpha multiplier (recommended 0..1)
     * @param tintR       tint red channel (0..1 recommended)
     * @param tintG       tint green channel (0..1 recommended)
     * @param tintB       tint blue channel (0..1 recommended)
     * @param rippleAmpPx ripple amplitude in pixels (0 disables ripple)
     * @param rippleFreq  ripple frequency across X (e.g., 12..30 typical)
     * @param rippleSpeed ripple speed multiplier (e.g., 1..5 typical)
     * @throws NullPointerException if {@code texture} is null
     */
    public void apply(Texture texture, float timeSeconds, float amount, float alpha, float tintR, float tintG, float tintB, float rippleAmpPx, float rippleFreq, float rippleSpeed) {
        if (texture == null) throw new NullPointerException("Texture cannot be null");

        float ox = texture.getX();
        float oy = texture.getY();
        float ow = texture.getWidth();
        float oh = texture.getHeight();
        float a01 = MathUtils.clamp(amount, 0, 1);

        if (a01 <= 0f) return;

        float rh = oh * a01;

        texture.setPosition(ox, oy - rh);
        texture.setSize(ow, rh);

        bind();
        setUniform1i("u_texture", 0);
        setUniform2f("u_texelSize", 1f / texture.getData().width(), 1f / texture.getData().height());
        setUniform1f("u_alpha", alpha);
        setUniform4f("u_tint", tintR, tintG, tintB, 1.0f);
        setUniform1f("u_time", timeSeconds);
        setUniform1f("u_rippleAmpPx", rippleAmpPx);
        setUniform1f("u_rippleFreq", rippleFreq);
        setUniform1f("u_rippleSpeed", rippleSpeed);
        texture.draw();
        unbind();

        texture.setPosition(ox, oy);
        texture.setSize(ow, oh);
    }
}