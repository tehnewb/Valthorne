#version 330 core
layout(location = 0) in vec3 a_pos;
layout(location = 2) in vec4 a_color;
layout(location = 3) in vec2 a_uv;
uniform mat4 u_mvp;
out vec2 uv;out float alpha;
void main(){ uv = a_uv;alpha = a_color.a;gl_Position = u_mvp * vec4(a_pos, 1); }
