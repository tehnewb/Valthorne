$input a_position, a_texcoord0
$output v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
uniform vec4 u_viewportAdjust;
void main() {
v_color = vec4_splat(1.0); v_uv = vec2_splat(0.0); v_glyph = vec4_splat(0.0); v_band = vec4_splat(0.0); v_world = vec2_splat(0.0); v_clip = vec4_splat(0.0); v_params = vec4_splat(0.0);
 v_uv=a_texcoord0; gl_Position=vec4(a_position,0.5,1.0);
    gl_Position.xy = gl_Position.xy * u_viewportAdjust.xy + gl_Position.w * u_viewportAdjust.zw;
}
