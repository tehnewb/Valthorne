#version 330 core
uniform sampler2D u_texture;
uniform vec2 u_texelSize;
uniform float u_time;
uniform float u_amp;
uniform float u_freq;
uniform float u_speed;
in vec2 v_uv;
in vec4 v_color;
out vec4 fragColor;
void main() {
    vec2 amp = u_texelSize * u_amp;
    float w1 = sin(v_uv.x * u_freq + u_time * u_speed);
    float w2 = sin(v_uv.y * (u_freq * 0.8) - u_time * (u_speed * 1.15));
    vec2 uv = v_uv;
    uv.y += w1 * amp.y;
    uv.x += w2 * amp.x;
    fragColor = texture(u_texture, uv) * v_color;
}
