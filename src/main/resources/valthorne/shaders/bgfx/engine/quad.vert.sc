$input a_position, a_texcoord0, i_data0, i_data1, i_data2, i_data3, i_data4
$output v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
uniform vec4 u_viewportAdjust;
uniform mat4 u_mvp;
void main() {
    vec2 lower = mix(i_data0.xy, i_data0.zw, a_position.x);
    vec2 upper = mix(i_data1.zw, i_data1.xy, a_position.x);
    vec2 xy = mix(lower, upper, a_position.y);
    gl_Position = mul(u_mvp, vec4(xy, 0.0, 1.0));

#if !BGFX_SHADER_LANGUAGE_GLSL
    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
#endif

    vec2 uvLower = mix(i_data2.xy, i_data2.zw, a_texcoord0.x);
    vec2 uvUpper = mix(i_data3.zw, i_data3.xy, a_texcoord0.x);
    v_uv = mix(uvLower, uvUpper, a_texcoord0.y);
    v_color = i_data4;
    v_world = xy;
    v_clip = vec4_splat(0.0);
    v_params = vec4_splat(0.0);
    v_glyph = vec4_splat(0.0);
    v_band = vec4_splat(0.0);
    gl_Position.xy = gl_Position.xy * u_viewportAdjust.xy + gl_Position.w * u_viewportAdjust.zw;
}
