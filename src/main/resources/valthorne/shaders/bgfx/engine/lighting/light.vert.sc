$input a_position, a_texcoord0, i_data0, i_data1, i_data2, i_data3
$output v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
uniform vec4 u_viewportAdjust;
uniform vec4 u_world;
void main() {
v_color = vec4_splat(1.0); v_uv = vec2_splat(0.0); v_glyph = vec4_splat(0.0); v_band = vec4_splat(0.0); v_world = vec2_splat(0.0); v_clip = vec4_splat(0.0); v_params = vec4_splat(0.0);
 v_world=(a_position*2.0-1.0)*i_data0.z; v_glyph=i_data0; v_color=i_data1; v_band=i_data2; v_params.x=i_data3.x; gl_Position=vec4((i_data0.xy+v_world-u_world.xy)/u_world.zw*2.0-1.0,0.5,1.0);
    gl_Position.xy = gl_Position.xy * u_viewportAdjust.xy + gl_Position.w * u_viewportAdjust.zw;
}
