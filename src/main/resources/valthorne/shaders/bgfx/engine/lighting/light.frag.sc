$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
#define local v_world
#define light v_glyph
#define color v_color
#define shape v_band
#define elevation v_params.x
#define SHADOW_SEARCH_SAMPLES 8
#define SHADOW_FILTER_SAMPLES 16
#define MAX_SHADOW_SPREAD .035

SAMPLER2D(u_shadows,0); uniform vec4 u_rows; uniform vec4 u_resolution;

float compareShadow(float u, float row, float t, float width){
    float coordinate = fract(u) * u_resolution.x - .5, base = floor(coordinate), fraction = fract(coordinate);
    float a = texture2D(u_shadows, vec2((base + .5) / u_resolution.x, row)).r;
    float b = texture2D(u_shadows, vec2((base + 1.5) / u_resolution.x, row)).r;
    // Interpolate comparisons, not depths, to avoid long streaks at depth discontinuities.
    return mix(1.0 - smoothstep(a, a + width, t), 1.0 - smoothstep(b, b + width, t), fraction);
}

float shadowVisibility(float u, float row, float t, float sourceRadius, float radius, vec2 pixel) {
    float edgeWidth = max(.0005, fwidth(t));
    if (sourceRadius <= 0.0) return compareShadow(u, row, t, edgeWidth);

    float jitter = fract(52.9829189 * fract(dot(pixel, vec2(.06711056, .00583715))));
    float blocker = texture2D(u_shadows, vec2(u, row)).r;
    float blockerDepth = blocker < t ? blocker : 0.0;
    float blockers = blocker < t ? 1.0 : 0.0;
    // Search both sides of the receiver direction. A center-only lookup gives
    // lit receivers zero penumbra and leaves a hard edge beside a softened shadow.
    for (int i = 0; i < SHADOW_SEARCH_SAMPLES; i++) {
        float offset = ((float(i) + jitter) * (2.0 / float(SHADOW_SEARCH_SAMPLES)) - 1.0) * MAX_SHADOW_SPREAD;
        float depth = texture2D(u_shadows, vec2(u + offset, row)).r;
        if (depth < t) {
            blockerDepth += depth;
            blockers += 1.0;
        }
    }
    if (blockers == 0.0) return 1.0;
    blocker = blockerDepth / blockers;
    float penumbra = sourceRadius / radius * max(t - blocker, 0.0) / max(t * blocker, .0001) / 6.28318530718;
    float spread = clamp(penumbra, 0.0, MAX_SHADOW_SPREAD);
    if (spread * u_resolution.x <= .5) return compareShadow(u, row, t, edgeWidth);

    // Stratification breaks coherent angular bands without frame-to-frame noise.
    // Use comparisons rather than interpolated blocker depths at silhouette edges.
    float sum = 0.0;
    for (int i = 0; i < SHADOW_FILTER_SAMPLES; i++) {
        float offset = ((float(i) + jitter) * (2.0 / float(SHADOW_FILTER_SAMPLES)) - 1.0) * spread;
        sum += compareShadow(u + offset, row, t, edgeWidth);
    }
    return sum / float(SHADOW_FILTER_SAMPLES);
}

void main(){ float d = length(local), t = d / light.z;if (t >= 1.0)discard;
    float angle = atan2(local.y, local.x), u = angle / 6.28318530718;float visibility = 1.0;
    if (light.w >= 0.0) visibility = shadowVisibility(u, (light.w + .5) / u_rows.x, t, shape.x, light.z, gl_FragCoord.xy);
    float cone = 1.0;
    if (shape.z > -.9999){ float c = cos(angle - shape.w);cone = shape.y - shape.z < .00001?step(shape.z, c):smoothstep(shape.z, shape.y, c); }
    float t3=length(vec3(local,elevation))/light.z;if(t3>=1.0)discard;
    float window = 1.0 - t3 * t3;float falloff = window * window / (1.0 + 6.0 * t3 * t3);
    gl_FragColor = vec4(color.rgb * color.a * falloff * visibility * cone, 0); }
