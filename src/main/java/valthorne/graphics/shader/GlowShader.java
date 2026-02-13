package valthorne.graphics.shader;

import valthorne.graphics.texture.Texture;

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
 * // For a 256x256 sprite texture:
 * float texelX = 1f / 256f;
 * float texelY = 1f / 256f;
 *
 * glow.apply(texelX, texelY, 6f, 1.25f, 1f, 0.8f, 0.2f, 1f); // warm glow
 * // draw your sprite(s)...
 * glow.unbind(); // optional
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public class GlowShader extends Shader {

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

    private static final String FRAG_SRC = """
            #version 120
            uniform sampler2D u_texture;
            uniform vec2 u_texelSize;
            uniform float u_radiusPx;
            uniform float u_intensity;
            uniform vec4 u_glowColor;
            
            varying vec2 v_uv;
            varying vec4 v_color;
            
            float aAt(vec2 uv){
                return texture2D(u_texture, uv).a;
            }
            
            void main(){
                vec4 center = texture2D(u_texture, v_uv);
                float a = center.a;
            
                // Sprite pixels: draw normally.
                if (a > 0.001) {
                    gl_FragColor = center * v_color;
                    return;
                }
            
                // Glow for transparent pixels: sample nearby alpha.
                float r1 = max(1.0, u_radiusPx * 0.35);
                float r2 = max(1.0, u_radiusPx * 0.70);
                float r3 = max(1.0, u_radiusPx);
            
                vec2 o1 = u_texelSize * r1;
                vec2 o2 = u_texelSize * r2;
                vec2 o3 = u_texelSize * r3;
            
                float s = 0.0;
            
                // 4-way + diagonals at 3 radii
                s += aAt(v_uv + vec2( o1.x, 0.0));
                s += aAt(v_uv + vec2(-o1.x, 0.0));
                s += aAt(v_uv + vec2(0.0,  o1.y));
                s += aAt(v_uv + vec2(0.0, -o1.y));
                s += aAt(v_uv + vec2( o1.x,  o1.y));
                s += aAt(v_uv + vec2(-o1.x,  o1.y));
                s += aAt(v_uv + vec2( o1.x, -o1.y));
                s += aAt(v_uv + vec2(-o1.x, -o1.y));
            
                s += aAt(v_uv + vec2( o2.x, 0.0)) * 0.75;
                s += aAt(v_uv + vec2(-o2.x, 0.0)) * 0.75;
                s += aAt(v_uv + vec2(0.0,  o2.y)) * 0.75;
                s += aAt(v_uv + vec2(0.0, -o2.y)) * 0.75;
                s += aAt(v_uv + vec2( o2.x,  o2.y)) * 0.75;
                s += aAt(v_uv + vec2(-o2.x,  o2.y)) * 0.75;
                s += aAt(v_uv + vec2( o2.x, -o2.y)) * 0.75;
                s += aAt(v_uv + vec2(-o2.x, -o2.y)) * 0.75;
            
                s += aAt(v_uv + vec2( o3.x, 0.0)) * 0.45;
                s += aAt(v_uv + vec2(-o3.x, 0.0)) * 0.45;
                s += aAt(v_uv + vec2(0.0,  o3.y)) * 0.45;
                s += aAt(v_uv + vec2(0.0, -o3.y)) * 0.45;
                s += aAt(v_uv + vec2( o3.x,  o3.y)) * 0.45;
                s += aAt(v_uv + vec2(-o3.x,  o3.y)) * 0.45;
                s += aAt(v_uv + vec2( o3.x, -o3.y)) * 0.45;
                s += aAt(v_uv + vec2(-o3.x, -o3.y)) * 0.45;
            
                // Normalize and shape the glow.
                float glow = clamp(s / 17.6, 0.0, 1.0);
                glow = pow(glow, 1.8) * u_intensity;
            
                gl_FragColor = vec4(u_glowColor.rgb, u_glowColor.a * glow);
            }
            """;

    /**
     * Creates a new {@code GlowShader} using the built-in GLSL sources.
     */
    public GlowShader() {
        super(VERT_SRC, FRAG_SRC);
    }

    /**
     * Binds this shader and sets uniforms required to render a soft glow.
     *
     * @param texture   the texture being drawn (used for width/height -> texel size)
     * @param radiusPx  glow radius in source texture pixels (>= 0)
     * @param intensity glow strength multiplier (>= 0 recommended)
     * @param r         glow red
     * @param g         glow green
     * @param b         glow blue
     * @param a         glow alpha (final alpha is {@code a * computedGlow})
     */
    public void apply(Texture texture, float radiusPx, float intensity, float r, float g, float b, float a) {
        bind();
        setUniform1i("u_texture", 0);

        float texelX = 1f / texture.getData().width();
        float texelY = 1f / texture.getData().height();
        setUniform2f("u_texelSize", texelX, texelY);

        setUniform1f("u_radiusPx", radiusPx);
        setUniform1f("u_intensity", intensity);
        setUniform4f("u_glowColor", r, g, b, a);
    }
}