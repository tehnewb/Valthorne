#version 330 core
in vec3 a_position;
in vec4 a_color0;
uniform mat4 u_modelViewProj;
out vec4 v_color;
void main() {
    gl_Position = u_modelViewProj * vec4(a_position, 1.0);
    v_color = a_color0;
}
