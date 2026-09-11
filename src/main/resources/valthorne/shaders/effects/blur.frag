#version 330 core
uniform sampler2D u_texture;
uniform vec2 u_texelSize;
uniform float u_radiusPx;
in vec2 v_uv;
in vec4 v_color;
out vec4 fragColor;
void main() {
    float r = max(0.0, u_radiusPx);
    vec2 o = u_texelSize * r;
    vec4 sum = vec4(0.0);
    sum += texture(u_texture, v_uv + vec2(-o.x, -o.y));
    sum += texture(u_texture, v_uv + vec2(0.0, -o.y));
    sum += texture(u_texture, v_uv + vec2(o.x, -o.y));
    sum += texture(u_texture, v_uv + vec2(-o.x, 0.0));
    sum += texture(u_texture, v_uv);
    sum += texture(u_texture, v_uv + vec2(o.x, 0.0));
    sum += texture(u_texture, v_uv + vec2(-o.x, o.y));
    sum += texture(u_texture, v_uv + vec2(0.0, o.y));
    sum += texture(u_texture, v_uv + vec2(o.x, o.y));
    fragColor = (sum / 9.0) * v_color;
}
