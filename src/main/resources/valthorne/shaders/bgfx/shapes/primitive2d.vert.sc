$input a_position, i_data0, i_data1
$output v_color, v_local, v_polygon
#include <bgfx_shader.sh>
void main() {
    bool circle = i_data0.z < 0.0;
    vec2 size = circle ? vec2_splat(-i_data0.z) : i_data0.zw;
    vec2 position = i_data0.xy + a_position.xy * size;
    gl_Position = mul(u_viewProj, vec4(position, 0.0, 1.0));
    v_color = i_data1;
    v_local = a_position.xy * 2.0 - 1.0;
    v_polygon = circle ? vec2(i_data0.w, cos(3.141592653589793 / i_data0.w)) : vec2_splat(0.0);
}
