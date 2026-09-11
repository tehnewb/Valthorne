out vec4 fragColor;

void main() {
    if (batchClipped()) discard;
    vec4 color = sampleBatchTexture(v_uv);
    fragColor = color * v_col;
}
