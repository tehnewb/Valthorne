#version 330 core
${ SAMPLERS}in vec2 v_uv;
in vec4 v_col;
in float v_tex;
in vec2 v_world;
in vec4 v_clipRect;
in float v_clipEnabled;

out vec4 fragColor;
uniform vec2 u_distanceRange[${TEXTURE_UNITS}];

void main() {
    if (v_clipEnabled > 0.5) {
        if (v_world.x < v_clipRect.x || v_world.y < v_clipRect.y ||
            v_world.x > v_clipRect.x + v_clipRect.z ||
            v_world.y > v_clipRect.y + v_clipRect.w) {
            discard;
        }
    }

    vec4 c;
    float t = v_tex;
${ SELECTION}    else c = texture(u_tex0, v_uv);
    vec2 unitRange = u_distanceRange[int(t)];
    if (unitRange.x > 0.0) {
        float median = max(min(c.r, c.g), min(max(c.r, c.g), c.b));
        vec2 screenTexSize = vec2(1.0) / max(fwidth(v_uv), vec2(0.000001));
        float range = max(0.5 * dot(unitRange, screenTexSize), 1.0);
        float coverage = clamp(range * (median - 0.5) + 0.5, 0.0, 1.0);
        fragColor = vec4(v_col.rgb, v_col.a * coverage);
    } else fragColor = c * v_col;
}
