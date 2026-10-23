$input a_position, a_texcoord0, a_texcoord1
$output v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
uniform vec4 u_viewportAdjust;
uniform mat4 u_projection;
void main(){
v_color=vec4_splat(1.0); v_glyph=vec4_splat(0.0); v_band=vec4_splat(0.0); v_clip=vec4_splat(0.0); v_params=vec4_splat(0.0);
 v_uv=a_texcoord0; v_band.xy=a_texcoord1; v_world=a_position; gl_Position=mul(u_projection,vec4(a_position,0.0,1.0));
#if !BGFX_SHADER_LANGUAGE_GLSL
    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;
#endif

    gl_Position.xy = gl_Position.xy * u_viewportAdjust.xy + gl_Position.w * u_viewportAdjust.zw;
}
