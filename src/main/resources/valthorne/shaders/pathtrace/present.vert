#version 430 core
out vec2 uv;
void main(){ uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);gl_Position = vec4(uv * 2.0 - 1.0, 0, 1); }
