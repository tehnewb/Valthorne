#version 330 core

uniform sampler2D u_texture;
uniform vec4 u_tint;
in vec2 v_uv;
in vec4 v_color;
out vec4 fragColor;

void main() {
    vec4 color = texture(u_texture, v_uv) * v_color;
#if USE_TINT
    color *= u_tint;
#endif
    fragColor = color;
}
