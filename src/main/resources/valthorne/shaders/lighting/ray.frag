#version 330 core
in vec2 v_local;
in vec4 v_color;

out vec4 fragColor;

void main() {
    float dist = length(v_local);
    float intensity = 1.0 - dist;
    intensity = clamp(intensity, 0.0, 1.0);
    intensity = smoothstep(0.0, 1.0, intensity);
    intensity = pow(intensity, 1.6);
    intensity *= v_color.a;
    fragColor = vec4(v_color.rgb * intensity, intensity);
}
