package valthorne.graphics.shader;

/**
 * Draws a crisp outline around non-transparent pixels.
 *
 * <h2>What this shader does</h2>
 * <ul>
 *     <li>Samples the center pixel alpha from {@code u_texture}.</li>
 *     <li>If the center pixel is opaque, renders the sprite normally (texture * vertex color).</li>
 *     <li>If the center pixel is transparent, samples neighboring alpha values.</li>
 *     <li>If any neighbor is opaque, outputs {@code u_outlineColor} to create an outline.</li>
 * </ul>
 *
 * <h2>Best use</h2>
 * <ul>
 *     <li>Works best on sprites with clean alpha edges (hard-ish cutouts).</li>
 *     <li>{@code u_texelSize} must be {@code (1/textureWidth, 1/textureHeight)} for correct thickness.</li>
 *     <li>{@code u_thicknessPx} is measured in <b>source texture pixels</b>, not screen pixels.</li>
 * </ul>
 *
 * <h2>Uniforms</h2>
 * <ul>
 *     <li>{@code u_texture} (sampler2D): texture unit index (use 0).</li>
 *     <li>{@code u_texelSize} (vec2): texel size ({@code 1/width}, {@code 1/height}).</li>
 *     <li>{@code u_thicknessPx} (float): outline thickness in source-texture pixels.</li>
 *     <li>{@code u_outlineColor} (vec4): outline RGBA color.</li>
 * </ul>
 *
 * <h2>Output rules</h2>
 * <ul>
 *     <li>If center alpha &gt; ~0.001: {@code gl_FragColor = center * v_color}.</li>
 *     <li>Else if any neighbor alpha &gt; ~0.001: {@code gl_FragColor = u_outlineColor}.</li>
 *     <li>Else: transparent.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * OutlineShader outline = new OutlineShader();
 *
 * // For a 256x256 texture:
 * float texelX = 1f / 256f;
 * float texelY = 1f / 256f;
 *
 * outline.apply(texelX, texelY, 1f, 0f, 0f, 0f, 1f); // 1px black outline
 * // draw your sprite(s)...
 * outline.unbind(); // optional
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public class OutlineShader extends Shader {

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
            uniform vec2 u_texelSize;
            uniform float u_thicknessPx;
            uniform vec4 u_outlineColor;
            
            varying vec2 v_uv;
            varying vec4 v_color;
            
            void main(){
                vec4 center = texture2D(u_texture, v_uv);
                float a = center.a;
            
                // Draw sprite normally where opaque.
                if (a > 0.001) {
                    gl_FragColor = center * v_color;
                    return;
                }
            
                // If transparent, check nearby alpha to decide outline.
                vec2 o = u_texelSize * u_thicknessPx;
            
                float n = 0.0;
                n = max(n, texture2D(u_texture, v_uv + vec2( o.x, 0.0)).a);
                n = max(n, texture2D(u_texture, v_uv + vec2(-o.x, 0.0)).a);
                n = max(n, texture2D(u_texture, v_uv + vec2(0.0,  o.y)).a);
                n = max(n, texture2D(u_texture, v_uv + vec2(0.0, -o.y)).a);
            
                // Diagonals
                n = max(n, texture2D(u_texture, v_uv + vec2( o.x,  o.y)).a);
                n = max(n, texture2D(u_texture, v_uv + vec2(-o.x,  o.y)).a);
                n = max(n, texture2D(u_texture, v_uv + vec2( o.x, -o.y)).a);
                n = max(n, texture2D(u_texture, v_uv + vec2(-o.x, -o.y)).a);
            
                if (n > 0.001) {
                    gl_FragColor = u_outlineColor;
                } else {
                    gl_FragColor = vec4(0.0);
                }
            }
            """; // GLSL fragment shader source (neighbor alpha test for outline).

    /**
     * Creates a new {@code OutlineShader} using the built-in GLSL sources.
     */
    public OutlineShader() {
        super(VERT_SRC, FRAG_SRC);
    }

    /**
     * Binds this shader and sets uniforms required to render an outline.
     *
     * <p>{@code texelX/texelY} must match the underlying source texture dimensions:</p>
     * <pre>{@code
     * texelX = 1f / textureWidth;
     * texelY = 1f / textureHeight;
     * }</pre>
     *
     * @param texelX      {@code 1 / textureWidth}
     * @param texelY      {@code 1 / textureHeight}
     * @param thicknessPx outline thickness in source-texture pixels (typically 1..3)
     * @param r           outline red
     * @param g           outline green
     * @param b           outline blue
     * @param a           outline alpha
     */
    public void apply(float texelX, float texelY, float thicknessPx, float r, float g, float b, float a) {
        bind();
        setUniform1i("u_texture", 0);
        setUniform2f("u_texelSize", texelX, texelY);
        setUniform1f("u_thicknessPx", thicknessPx);
        setUniform4f("u_outlineColor", r, g, b, a);
    }
}