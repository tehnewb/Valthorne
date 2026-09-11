#version 330 core
out vec2 uv;void main(){ vec2 p = vec2(gl_VertexID == 1?3:-1, gl_VertexID == 2?3:-1);uv = p * .5 + .5;gl_Position = vec4(p, 0, 1); }
