$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture, 0);
uniform vec4 u_texelSize;
uniform vec4 u_radiusPx;
uniform vec4 u_intensity;
uniform vec4 u_glowColor;


float aAt(vec2 uv) {
    return texture2D(u_texture, uv).a;
}

void main() {
    vec4 center = texture2D(u_texture, v_uv);
    float a = center.a;

    // Sprite pixels: draw normally.
    if (a > 0.001) {
        gl_FragColor = center * v_color;
        return;
    }

    // Glow for transparent pixels: sample nearby alpha.
    float r1 = max(1.0, u_radiusPx.x * 0.35);
    float r2 = max(1.0, u_radiusPx.x * 0.70);
    float r3 = max(1.0, u_radiusPx.x);

    vec2 o1 = u_texelSize.xy * r1;
    vec2 o2 = u_texelSize.xy * r2;
    vec2 o3 = u_texelSize.xy * r3;

    float s = 0.0;

    // 4-way + diagonals at 3 radii
    s += aAt(v_uv + vec2(o1.x, 0.0));
    s += aAt(v_uv + vec2(-o1.x, 0.0));
    s += aAt(v_uv + vec2(0.0, o1.y));
    s += aAt(v_uv + vec2(0.0, -o1.y));
    s += aAt(v_uv + vec2(o1.x, o1.y));
    s += aAt(v_uv + vec2(-o1.x, o1.y));
    s += aAt(v_uv + vec2(o1.x, -o1.y));
    s += aAt(v_uv + vec2(-o1.x, -o1.y));

    s += aAt(v_uv + vec2(o2.x, 0.0)) * 0.75;
    s += aAt(v_uv + vec2(-o2.x, 0.0)) * 0.75;
    s += aAt(v_uv + vec2(0.0, o2.y)) * 0.75;
    s += aAt(v_uv + vec2(0.0, -o2.y)) * 0.75;
    s += aAt(v_uv + vec2(o2.x, o2.y)) * 0.75;
    s += aAt(v_uv + vec2(-o2.x, o2.y)) * 0.75;
    s += aAt(v_uv + vec2(o2.x, -o2.y)) * 0.75;
    s += aAt(v_uv + vec2(-o2.x, -o2.y)) * 0.75;

    s += aAt(v_uv + vec2(o3.x, 0.0)) * 0.45;
    s += aAt(v_uv + vec2(-o3.x, 0.0)) * 0.45;
    s += aAt(v_uv + vec2(0.0, o3.y)) * 0.45;
    s += aAt(v_uv + vec2(0.0, -o3.y)) * 0.45;
    s += aAt(v_uv + vec2(o3.x, o3.y)) * 0.45;
    s += aAt(v_uv + vec2(-o3.x, o3.y)) * 0.45;
    s += aAt(v_uv + vec2(o3.x, -o3.y)) * 0.45;
    s += aAt(v_uv + vec2(-o3.x, -o3.y)) * 0.45;

    // Normalize and shape the glow.
    float glow = clamp(s / 17.6, 0.0, 1.0);
    glow = pow(glow, 1.8) * u_intensity.x;

    gl_FragColor = vec4(u_glowColor.rgb, u_glowColor.a * glow);
}
