#version 330 core
in vec3 a_position;
in vec4 i_data0;
in vec4 i_data1;
uniform mat4 u_viewProj;
out vec4 v_color;
out vec2 v_local;
flat out vec2 v_polygon;
void main() {
    bool circle = i_data0.z < 0.0;
    vec2 size = circle ? vec2(-i_data0.z) : i_data0.zw;
    vec2 position = i_data0.xy + a_position.xy * size;
    gl_Position = u_viewProj * vec4(position, 0.0, 1.0);
    v_color = i_data1;
    v_local = a_position.xy * 2.0 - 1.0;
    v_polygon = circle ? vec2(i_data0.w, cos(3.141592653589793 / i_data0.w)) : vec2(0.0);
}
