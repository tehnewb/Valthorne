$input a_position, a_texcoord0, i_data0, i_data1, i_data2, i_data3, i_data4, i_data5
$output v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
uniform vec4 u_viewportAdjust;
uniform mat4 u_mvp;
uniform vec4 u_textTransform;
void main() {
    vec2 xy = mix(i_data0.xy, i_data0.zw, a_position);
    gl_Position = mul(u_mvp, vec4(xy, 0.0, 1.0));

#if !BGFX_SHADER_LANGUAGE_GLSL
    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
#endif

    v_world = vec2(u_textTransform.x * xy.x - u_textTransform.y * xy.y,
                   u_textTransform.y * xy.x + u_textTransform.x * xy.y) + u_textTransform.zw;
    v_uv = mix(i_data1.xy, i_data1.zw, a_texcoord0);
    uint location = floatBitsToUint(i_data2.x);
    uint info = floatBitsToUint(i_data2.y);
    v_glyph = vec4(float(location & 65535u), float(location >> 16u), float(info & 65535u), float(info >> 16u));
    v_band = vec4(i_data2.zw, i_data3.xy);
    uint packed = floatBitsToUint(i_data3.w);
    v_color = vec4(float(packed & 255u), float((packed >> 8u) & 255u), float((packed >> 16u) & 255u), float(packed >> 24u)) / 255.0;
    v_clip = i_data4;
    v_params = vec4(0.0, i_data5.x, i_data3.z, 0.0);
    gl_Position.xy = gl_Position.xy * u_viewportAdjust.xy + gl_Position.w * u_viewportAdjust.zw;
}
