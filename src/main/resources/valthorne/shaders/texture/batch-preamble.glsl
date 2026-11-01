#version 330 core
${ SAMPLERS}in vec2 v_uv;
in vec4 v_col;
in float v_tex;
in vec2 v_world;
in vec4 v_clipRect;
in float v_clipEnabled;
uniform vec2 u_distanceRange[${TEXTURE_UNITS}];

vec4 sampleRawBatchTexture(vec2 uv) {
    float t = v_tex;
${ SELECTION}    return texture(u_tex0, uv);
}

${DISTANCE_SAMPLING}

vec4 sampleBatchTexture(vec2 uv) {
    vec4 sampled = sampleRawBatchTexture(uv);
    vec2 unitRange = u_distanceRange[int(v_tex)];
    if (unitRange.x <= 0.0) {
        if (unitRange.x < 0.0) sampled.a = batchTextCoverage(sampled.a);
        return sampled;
    }
    return vec4(1.0, 1.0, 1.0, batchTextCoverage(batchDistanceCoverage(uv, sampled.rgb, unitRange)));
}

bool batchClipped() {
    return v_clipEnabled > 0.5 &&
    (v_world.x < v_clipRect.x ||
    v_world.y < v_clipRect.y ||
    v_world.x > v_clipRect.x + v_clipRect.z ||
    v_world.y > v_clipRect.y + v_clipRect.w);
}

