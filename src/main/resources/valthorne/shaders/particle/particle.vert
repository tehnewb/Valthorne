#version 330 core

uniform mat4 u_mvp;

in vec2 a_pos;
in float a_size;
in vec2 a_aspect;
in float a_rot;
in vec4 a_col;

out vec4 v_col;
out vec2 v_aspect;
out float v_rot;

void main() {
    gl_Position = u_mvp * vec4(a_pos.xy, 0.0, 1.0);
    gl_PointSize = a_size;

    v_col = a_col;
    v_aspect = a_aspect;
    v_rot = a_rot;
}
