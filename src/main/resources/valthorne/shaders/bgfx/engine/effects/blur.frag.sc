$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture, 0);
uniform vec4 u_texelSize;
uniform vec4 u_radiusPx;
void main() {
    float r = max(0.0, u_radiusPx.x);
    vec2 o = u_texelSize.xy * r;
    vec4 sum = vec4_splat(0.0);
    sum += texture2D(u_texture, v_uv + vec2(-o.x, -o.y));
    sum += texture2D(u_texture, v_uv + vec2(0.0, -o.y));
    sum += texture2D(u_texture, v_uv + vec2(o.x, -o.y));
    sum += texture2D(u_texture, v_uv + vec2(-o.x, 0.0));
    sum += texture2D(u_texture, v_uv);
    sum += texture2D(u_texture, v_uv + vec2(o.x, 0.0));
    sum += texture2D(u_texture, v_uv + vec2(-o.x, o.y));
    sum += texture2D(u_texture, v_uv + vec2(0.0, o.y));
    sum += texture2D(u_texture, v_uv + vec2(o.x, o.y));
    gl_FragColor = (sum / 9.0) * v_color;
}
