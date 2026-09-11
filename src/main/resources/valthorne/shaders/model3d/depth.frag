#version 330 core
in vec2 uv;in float alpha;
uniform sampler2D u_texture;
uniform int u_hasTexture;
uniform float u_alphaCutoff;
uniform vec4 u_materialTint;
void main(){ float a = alpha * u_materialTint.a;if (u_hasTexture == 1)a *= texture(u_texture, uv).a;if (a <= u_alphaCutoff)discard; }
