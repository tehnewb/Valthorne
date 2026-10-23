$input a_position, a_texcoord0
$output v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
uniform vec4 u_viewportAdjust;
uniform mat4 u_projection;
void main(){
v_color=vec4_splat(1.0); v_glyph=vec4_splat(0.0); v_band=vec4_splat(0.0); v_clip=vec4_splat(0.0); v_params=vec4_splat(0.0);
 v_uv=a_texcoord0; v_world=vec2_splat(0.0); gl_Position=mul(u_projection,vec4(a_position.xy,0.0,a_position.z));
#if !BGFX_SHADER_LANGUAGE_GLSL
    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
#endif

    gl_Position.xy = gl_Position.xy * u_viewportAdjust.xy + gl_Position.w * u_viewportAdjust.zw;
}
