float batchMedian(vec3 value) {
    return max(min(value.r, value.g), min(max(value.r, value.g), value.b));
}

float batchDistanceSample(vec2 uv, float range) {
    return clamp(range * (batchMedian(sampleRawBatchTexture(uv).rgb) - 0.5) + 0.5, 0.0, 1.0);
}

float batchDistanceCoverage(vec2 uv, vec3 sampled, vec2 unitRange) {
    vec2 dx = dFdx(uv);
    vec2 dy = dFdy(uv);
    vec2 screenTexSize = vec2(1.0) / max(abs(dx) + abs(dy), vec2(0.000001));
    float range = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float distance = range * (batchMedian(sampled) - 0.5);
    if (abs(distance) >= 0.75) return clamp(distance + 0.5, 0.0, 1.0);

    // Integrate four half-pixel footprints at curved edges and thin serifs.
    dx *= 0.25;
    dy *= 0.25;
    range *= 2.0;
    return 0.25 * (batchDistanceSample(uv - dx - dy, range)
            + batchDistanceSample(uv + dx - dy, range)
            + batchDistanceSample(uv - dx + dy, range)
            + batchDistanceSample(uv + dx + dy, range));
}
uniform float u_textContrast;

float batchTextCoverage(float coverage) {
    // Approximate perceptual edge blending without pow(), extra samples or RGB shifts.
    // Hardware sRGB blending supplies its own correction and disables this term.
    float foreground = dot(v_col.rgb, vec3(0.2126, 0.7152, 0.0722));
    return coverage + u_textContrast * (foreground - 0.5) * coverage * (1.0 - coverage);
}
