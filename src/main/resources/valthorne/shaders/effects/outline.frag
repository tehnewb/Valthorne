#version 330 core
uniform sampler2D u_texture;
uniform vec2 u_texelSize;
uniform float u_thicknessPx;
uniform vec4 u_outlineColor;

in vec2 v_uv;
in vec4 v_color;
out vec4 fragColor;

void main() {
    vec4 center = texture(u_texture, v_uv);
    float a = center.a;

    // Sprite pixels: draw normally.
    if (a > 0.001) {
        fragColor = center * v_color;
        return;
    }

    // True source-texel step.
    vec2 o = u_texelSize * u_thicknessPx;

    float n = 0.0;
    n = max(n, texture(u_texture, v_uv + vec2(o.x, 0.0)).a);
    n = max(n, texture(u_texture, v_uv + vec2(-o.x, 0.0)).a);
    n = max(n, texture(u_texture, v_uv + vec2(0.0, o.y)).a);
    n = max(n, texture(u_texture, v_uv + vec2(0.0, -o.y)).a);

    // Diagonals
    n = max(n, texture(u_texture, v_uv + vec2(o.x, o.y)).a);
    n = max(n, texture(u_texture, v_uv + vec2(-o.x, o.y)).a);
    n = max(n, texture(u_texture, v_uv + vec2(o.x, -o.y)).a);
    n = max(n, texture(u_texture, v_uv + vec2(-o.x, -o.y)).a);

    if (n > 0.001) fragColor = u_outlineColor;
    else fragColor = vec4(0.0);
}
