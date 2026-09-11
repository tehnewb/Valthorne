#version 330 core
layout(location = 0) in vec4 a_light;layout(location = 1) in vec4 a_color;layout(location = 2) in vec4 a_shape;
uniform vec4 u_world;
out vec2 local;flat out vec4 light;flat out vec4 color;flat out vec4 shape;
void main(){ vec2 corner = vec2((gl_VertexID & 1) == 0?-1:1, (gl_VertexID & 2) == 0?-1:1);
    local = corner * a_light.z;light = a_light;color = a_color;shape = a_shape;
    gl_Position = vec4((a_light.xy + local - u_world.xy) / u_world.zw * 2.0 - 1.0, 0, 1); }
