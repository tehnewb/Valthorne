#version 330 core
uniform sampler2D u_texture;

uniform float u_alpha;
uniform vec4  u_tint;
uniform float u_time;

uniform float u_rippleAmpPx;
uniform float u_rippleFreq;
uniform float u_rippleSpeed;

uniform vec2  u_texelSize;

in vec2 v_uv;
in vec4 v_color;
out vec4 fragColor;

void main() {
    vec2 uv = v_uv;

    // Mirror vertically: top of reflection quad samples bottom of sprite.
    uv.y = 1.0 - uv.y;

    float ampPx = max(0.0, u_rippleAmpPx);
    if (ampPx > 0.0) {
        vec2 amp = u_texelSize * ampPx;
        float w = sin(uv.x * u_rippleFreq + u_time * u_rippleSpeed);
        uv.x += w * amp.x;
    }

    vec4 c = texture(u_texture, uv) * v_color;

    // Fade out as we go downward (top of quad is strongest).
    float fade = clamp(v_uv.y, 0.0, 1.0);
    fade = fade * fade;

    vec3 rgb = mix(c.rgb, u_tint.rgb, 0.35);
    float a = c.a * fade * clamp(u_alpha, 0.0, 1.0);

    fragColor = vec4(rgb, a);
}
