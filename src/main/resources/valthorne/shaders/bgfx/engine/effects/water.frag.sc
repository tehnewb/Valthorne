$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture, 0);
uniform vec4 u_texelSize;
uniform vec4 u_time;
uniform vec4 u_amp;
uniform vec4 u_freq;
uniform vec4 u_speed;
void main() {
    vec2 amp = u_texelSize.xy * u_amp.x;
    float w1 = sin(v_uv.x * u_freq.x + u_time.x * u_speed.x);
    float w2 = sin(v_uv.y * (u_freq.x * 0.8) - u_time.x * (u_speed.x * 1.15));
    vec2 uv = v_uv;
    uv.y += w1 * amp.y;
    uv.x += w2 * amp.x;
    gl_FragColor = texture2D(u_texture, uv) * v_color;
}
