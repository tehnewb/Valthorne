#version 330 core
in vec3 a_position;
in vec4 a_color0;
in vec4 i_data0;
in vec4 i_data1;
in vec4 i_data2;
in vec4 i_data3;
in vec4 i_data4;
uniform mat4 u_viewProj;
out vec4 v_color;
void main() {
    mat4 model = mat4(i_data0, i_data1, i_data2, i_data3);
    gl_Position = u_viewProj * model * vec4(a_position, 1.0);
    v_color = a_color0 * i_data4;
}
