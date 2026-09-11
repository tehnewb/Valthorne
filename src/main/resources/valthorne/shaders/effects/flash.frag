#version 330 core
uniform sampler2D u_texture;
uniform vec4 u_flashColor;
uniform float u_amount;

in vec2 v_uv;
in vec4 v_color;
out vec4 fragColor;

void main() {
    vec4 c = texture(u_texture, v_uv) * v_color;

    float amt = clamp(u_amount, 0.0, 1.0);
    if (amt <= 0.0 || c.a <= 0.001) {
        fragColor = c;
        return;
    }

    vec3 rgb = mix(c.rgb, u_flashColor.rgb, amt);
    float a = c.a * mix(1.0, clamp(u_flashColor.a, 0.0, 1.0), amt);

    fragColor = vec4(rgb, a);
}
