#version 330 core
${ SAMPLERS}in vec2 v_uv;
in vec4 v_col;
in float v_tex;
in vec2 v_world;
in vec4 v_clipRect;
in float v_clipEnabled;

out vec4 fragColor;
uniform vec2 u_distanceRange[${TEXTURE_UNITS}];

vec4 sampleRawBatchTexture(vec2 uv) {
    float t = v_tex;
${ SELECTION}    return texture(u_tex0, uv);
}

${DISTANCE_SAMPLING}

void main() {
    if (v_clipEnabled > 0.5) {
        if (v_world.x < v_clipRect.x || v_world.y < v_clipRect.y ||
            v_world.x > v_clipRect.x + v_clipRect.z ||
            v_world.y > v_clipRect.y + v_clipRect.w) {
            discard;
        }
    }

    vec4 c = sampleRawBatchTexture(v_uv);
    vec2 unitRange = u_distanceRange[int(v_tex)];
    if (unitRange.x > 0.0) {
        float coverage = batchTextCoverage(batchDistanceCoverage(v_uv, c.rgb, unitRange));
        fragColor = vec4(v_col.rgb, v_col.a * coverage);
    } else {
        if (unitRange.x < 0.0) c.a = batchTextCoverage(c.a);
        fragColor = c * v_col;
    }
}
