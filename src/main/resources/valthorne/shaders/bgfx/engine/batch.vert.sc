$input a_position, a_texcoord0, i_data0, i_data1, i_data2, i_data3, i_data4, i_data5
$output v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
uniform vec4 u_viewportAdjust;
uniform mat4 u_mvp;
uniform vec4 u_uiTransform;
void main() {
    vec2 origin = i_data3.yz;
    vec2 local = a_position * i_data0.zw - origin;
    vec2 rotated = vec2(local.x * i_data4.x - local.y * i_data3.w,
                        local.x * i_data3.w + local.y * i_data4.x);
    vec2 untransformed = i_data0.xy + origin + rotated;
    vec2 world = vec2(u_uiTransform.x * untransformed.x - u_uiTransform.y * untransformed.y,
                      u_uiTransform.y * untransformed.x + u_uiTransform.x * untransformed.y) + u_uiTransform.zw;
    gl_Position = mul(u_mvp, vec4(world, 0.0, 1.0));

#if !BGFX_SHADER_LANGUAGE_GLSL
    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
#endif

    v_color = i_data1;
    v_uv = mix(i_data2.yz, vec2(i_data2.w, i_data3.x), a_texcoord0);
    v_world = world;
    v_clip = vec4(i_data4.yzw, i_data5.x);
    v_params = vec4(i_data2.x, i_data5.y, 0.0, 0.0);
    v_glyph = vec4_splat(0.0);
    v_band = vec4_splat(0.0);
    gl_Position.xy = gl_Position.xy * u_viewportAdjust.xy + gl_Position.w * u_viewportAdjust.zw;
}
