$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture, 0);
void main() {
    gl_FragColor = texture2D(u_texture, v_uv) * v_color;
}
