#version 330 core
layout(location=0) in vec3 a_projected;
layout(location=1) in vec2 a_uv;
uniform mat4 u_projection;
out vec2 uv;
void main(){uv=a_uv;gl_Position=u_projection*vec4(a_projected.xy,0.0,a_projected.z);}
