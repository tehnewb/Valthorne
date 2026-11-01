$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture, 0);
uniform vec4 u_texelSize;
uniform vec4 u_thicknessPx;
uniform vec4 u_outlineColor;


void main() {
    vec4 center = texture2D(u_texture, v_uv);
    float a = center.a;

    // Sprite pixels: draw normally.
    if (a > 0.001) {
        gl_FragColor = center * v_color;
        return;
    }

    // True source-texel step.
    vec2 o = u_texelSize.xy * u_thicknessPx.x;

    float n = 0.0;
    n = max(n, texture2D(u_texture, v_uv + vec2(o.x, 0.0)).a);
    n = max(n, texture2D(u_texture, v_uv + vec2(-o.x, 0.0)).a);
    n = max(n, texture2D(u_texture, v_uv + vec2(0.0, o.y)).a);
    n = max(n, texture2D(u_texture, v_uv + vec2(0.0, -o.y)).a);

    // Diagonals
    n = max(n, texture2D(u_texture, v_uv + vec2(o.x, o.y)).a);
    n = max(n, texture2D(u_texture, v_uv + vec2(-o.x, o.y)).a);
    n = max(n, texture2D(u_texture, v_uv + vec2(o.x, -o.y)).a);
    n = max(n, texture2D(u_texture, v_uv + vec2(-o.x, -o.y)).a);

    if (n > 0.001) gl_FragColor = u_outlineColor;
    else gl_FragColor = vec4_splat(0.0);
}
