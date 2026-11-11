$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture, 0);

uniform vec4 u_alpha;
uniform vec4  u_tint;
uniform vec4 u_time;

uniform vec4 u_rippleAmpPx;
uniform vec4 u_rippleFreq;
uniform vec4 u_rippleSpeed;

uniform vec4 u_texelSize;
uniform vec4 u_regionBounds;
uniform vec4 u_regionY;


void main() {
    vec2 uv = v_uv;

    // Mirror vertically: top of reflection quad samples bottom of sprite.
    uv.y = u_regionY.x + u_regionY.y - uv.y;

    float ampPx = max(0.0, u_rippleAmpPx.x);
    if (ampPx > 0.0) {
        vec2 amp = u_texelSize.xy * ampPx;
        float w = sin(uv.x * u_rippleFreq.x + u_time.x * u_rippleSpeed.x);
        uv.x += w * amp.x;
    }

    vec2 inset = min(u_texelSize.xy * 0.5, (u_regionBounds.zw - u_regionBounds.xy) * 0.5);
    uv = clamp(uv, u_regionBounds.xy + inset, u_regionBounds.zw - inset);
    vec4 c = texture2D(u_texture, uv) * v_color;

    // Fade out as we go downward (top of quad is strongest).
    float height = u_regionY.y - u_regionY.x;
    float fade = height == 0.0 ? 0.0 : clamp((v_uv.y - u_regionY.x) / height, 0.0, 1.0);
    fade = fade * fade;

    vec3 rgb = mix(c.rgb, u_tint.rgb, 0.35);
    float a = c.a * fade * clamp(u_alpha.x, 0.0, 1.0);

    gl_FragColor = vec4(rgb, a);
}
