#version 330 core
uniform sampler2D u_texture;

uniform float u_alpha;
uniform vec4  u_tint;
uniform float u_time;

uniform float u_rippleAmpPx;
uniform float u_rippleFreq;
uniform float u_rippleSpeed;

uniform vec2  u_texelSize;
uniform vec4  u_regionBounds;
uniform vec2  u_regionY;

in vec2 v_uv;
in vec4 v_color;
out vec4 fragColor;

void main() {
    vec2 uv = v_uv;

    // Mirror vertically: top of reflection quad samples bottom of sprite.
    uv.y = u_regionY.x + u_regionY.y - uv.y;

    float ampPx = max(0.0, u_rippleAmpPx);
    if (ampPx > 0.0) {
        vec2 amp = u_texelSize * ampPx;
        float w = sin(uv.x * u_rippleFreq + u_time * u_rippleSpeed);
        uv.x += w * amp.x;
    }

    vec2 inset = min(u_texelSize * 0.5, (u_regionBounds.zw - u_regionBounds.xy) * 0.5);
    uv = clamp(uv, u_regionBounds.xy + inset, u_regionBounds.zw - inset);
    vec4 c = texture(u_texture, uv) * v_color;

    // Fade out as we go downward (top of quad is strongest).
    float height = u_regionY.y - u_regionY.x;
    float fade = height == 0.0 ? 0.0 : clamp((v_uv.y - u_regionY.x) / height, 0.0, 1.0);
    fade = fade * fade;

    vec3 rgb = mix(c.rgb, u_tint.rgb, 0.35);
    float a = c.a * fade * clamp(u_alpha, 0.0, 1.0);

    fragColor = vec4(rgb, a);
}
