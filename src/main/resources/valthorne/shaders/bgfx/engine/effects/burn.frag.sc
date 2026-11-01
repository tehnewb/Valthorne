$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture, 0);
uniform vec4 u_time;
uniform vec4 u_threshold;
uniform vec4 u_burnColor;
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}
void main() {
    vec4 c = texture2D(u_texture, v_uv) * v_color;
    if (c.a <= 0.001) { gl_FragColor = c; return; }
    float n = noise(v_uv * 14.0 + vec2(u_time.x * 0.15, u_time.x * 0.10));
    float th = clamp(u_threshold.x, 0.0, 1.0);
    if (n < th) discard;
    float edge = smoothstep(th, th + 0.10, n);
    float hot = 1.0 - edge;
    vec3 burnRgb = u_burnColor.rgb;
    vec3 outRgb = mix(burnRgb, c.rgb, edge);
    outRgb += burnRgb * hot * 0.65;
    gl_FragColor = vec4(outRgb, c.a);
}
