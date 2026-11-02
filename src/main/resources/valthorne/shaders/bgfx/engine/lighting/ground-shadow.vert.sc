$input a_position, i_data0, i_data1, i_data2, i_data3, i_data4, i_data5, i_data6
$output v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
uniform mat4 u_projection;
uniform vec4 u_viewportAdjust;
void main() {
    vec3 projected;
    if (a_position.y < 0.5) {
        if (a_position.x < 0.5) { projected = i_data0.xyz; v_uv = vec2(i_data0.w, i_data1.x); }
        else { projected = i_data1.yzw; v_uv = i_data2.xy; }
    } else {
        if (a_position.x < 0.5) { projected = vec3(i_data2.zw, i_data3.x); v_uv = i_data3.yz; }
        else { projected = vec3(i_data3.w, i_data4.xy); v_uv = i_data4.zw; }
    }
    v_clip = i_data5;
    v_params = vec4(i_data6.xy, 0.0, 0.0);
    v_color = vec4_splat(1.0); v_glyph = vec4_splat(0.0); v_band = vec4_splat(0.0); v_world = vec2_splat(0.0);
    gl_Position = mul(u_projection, vec4(projected.xy, 0.0, projected.z));
#if !BGFX_SHADER_LANGUAGE_GLSL
    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
#endif
    gl_Position.xy = gl_Position.xy * u_viewportAdjust.xy + gl_Position.w * u_viewportAdjust.zw;
}
