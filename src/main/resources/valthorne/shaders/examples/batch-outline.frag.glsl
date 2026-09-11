uniform vec2 u_texelSize;
uniform float u_thicknessPx;
uniform vec4 u_outlineColor;

void main() {
    if (batchClipped()) discard;

    vec4 center = sampleBatchTexture(v_uv);
    if (center.a > 0.001) {
        gl_FragColor = center * v_col;
        return;
    }

    vec2 o = u_texelSize * u_thicknessPx;

    float n = 0.0;
    n = max(n, sampleBatchTexture(v_uv + vec2(o.x, 0.0)).a);
    n = max(n, sampleBatchTexture(v_uv + vec2(-o.x, 0.0)).a);
    n = max(n, sampleBatchTexture(v_uv + vec2(0.0, o.y)).a);
    n = max(n, sampleBatchTexture(v_uv + vec2(0.0, -o.y)).a);
    n = max(n, sampleBatchTexture(v_uv + vec2(o.x, o.y)).a);
    n = max(n, sampleBatchTexture(v_uv + vec2(-o.x, o.y)).a);
    n = max(n, sampleBatchTexture(v_uv + vec2(o.x, -o.y)).a);
    n = max(n, sampleBatchTexture(v_uv + vec2(-o.x, -o.y)).a);

    if (n > 0.001) gl_FragColor = u_outlineColor;
    else gl_FragColor = vec4(0.0);
}
