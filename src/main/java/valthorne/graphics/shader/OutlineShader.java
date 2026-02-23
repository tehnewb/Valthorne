package valthorne.graphics.shader;

import valthorne.graphics.texture.Texture;

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
 * Texture texture = ...;
 *
 * outline.apply(texture, 1f, 0f, 0f, 0f, 1f); // 1px black outline
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
            """;

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
            
                // Sprite pixels: draw normally.
                if (a > 0.001) {
                    gl_FragColor = center * v_color;
                    return;
                }
            
                // True source-texel step.
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
            
                if (n > 0.001) gl_FragColor = u_outlineColor;
                else gl_FragColor = vec4(0.0);
            }
            """;

    public OutlineShader() {
        super(VERT_SRC, FRAG_SRC);
    }

    /**
     * Applies a source-pixel outline.
     *
     * @param texture     the texture being drawn (used to get width/height)
     * @param thicknessPx outline thickness in SOURCE texture pixels (1 = 1 texel)
     * @param r           outline red
     * @param g           outline green
     * @param b           outline blue
     * @param a           outline alpha
     */
    public void apply(Texture texture, float thicknessPx, float r, float g, float b, float a) {
        bind(texture.getData().width(), texture.getData().height(), thicknessPx, r, g, b, a);
        texture.draw();
        unbind();
    }

    /**
     * Binds the shader program and sets uniforms for texture dimensions, outline thickness,
     * and outline color. This method calculates the texel size based on the given texture
     * dimensions and updates the relevant uniforms using the given parameters.
     *
     * @param textureWidth  the width of the texture in pixels
     * @param textureHeight the height of the texture in pixels
     * @param thicknessPx   thickness of the outline in pixels
     * @param r             red component of the outline color (0.0 to 1.0)
     * @param g             green component of the outline color (0.0 to 1.0)
     * @param b             blue component of the outline color (0.0 to 1.0)
     * @param a             alpha component of the outline color (0.0 to 1.0)
     */
    public void bind(float textureWidth, float textureHeight, float thicknessPx, float r, float g, float b, float a) {
        bind();
        setUniform1i("u_texture", 0);

        // Compute texel size internally (no caller math).
        float texelX = 1f / textureWidth;
        float texelY = 1f / textureHeight;
        setUniform2f("u_texelSize", texelX, texelY);
        setUniform1f("u_thicknessPx", thicknessPx);
        setUniform4f("u_outlineColor", r, g, b, a);
    }
}