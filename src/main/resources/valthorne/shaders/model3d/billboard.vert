#version 330 core

layout(location = 0) in vec3 a_pos;
layout(location = 1) in vec2 a_uv;
layout(location = 2) in vec4 a_color;

uniform mat4 u_mvp;

out vec3 v_worldPos;
out vec2 v_uv;
out vec4 v_color;

void main() {
    v_worldPos = a_pos;
    v_uv = a_uv;
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
