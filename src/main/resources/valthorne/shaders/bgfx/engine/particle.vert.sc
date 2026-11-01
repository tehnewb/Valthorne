$input a_position, a_texcoord0, i_data0, i_data1, i_data2
$output v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
uniform vec4 u_viewportAdjust;
uniform mat4 u_mvp;
uniform vec4 u_pixelScale;
void main() {
    vec2 corner = a_position;
    vec2 offset = (corner - vec2_splat(0.5)) * i_data0.z * vec2(i_data0.w, i_data1.x);
    float angle = radians(i_data1.y);
    float c = cos(angle);
    float s = sin(angle);
    offset = vec2(c * offset.x - s * offset.y, s * offset.x + c * offset.y);
    vec4 center = mul(u_mvp, vec4(i_data0.xy, 0.0, 1.0));
    center.xy += offset * u_pixelScale.xy * center.w;
    center.z = (center.z + center.w) * 0.5;
    gl_Position = center;
    v_color = vec4(i_data1.zw, i_data2.xy);
    v_uv = vec2(corner.x, 1.0 - corner.y);
    v_glyph = vec4_splat(0.0);
    v_band = vec4_splat(0.0);
    v_world = i_data0.xy;
    v_clip = vec4_splat(0.0);
    v_params = vec4_splat(0.0);
    gl_Position.xy = gl_Position.xy * u_viewportAdjust.xy + gl_Position.w * u_viewportAdjust.zw;
}
