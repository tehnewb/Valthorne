#version 330 core
in vec2 v_uv;

uniform sampler2D u_texture;
uniform float     u_strength;

out vec4 fragColor;

void main() {
    fragColor = texture(u_texture, v_uv) * u_strength;
}
