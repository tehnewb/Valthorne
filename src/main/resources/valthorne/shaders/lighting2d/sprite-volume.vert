#version 330 core
layout(location=0) in vec2 a_position;
layout(location=1) in vec2 a_uv;
layout(location=2) in vec2 a_local;
uniform mat4 u_projection;
out vec2 uv,local,world;
void main(){uv=a_uv;local=a_local;world=a_position;gl_Position=u_projection*vec4(a_position,0,1);}
