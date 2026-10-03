#version 330 core
uniform sampler2D u_texture;
uniform vec4 u_uvRect;
uniform float u_discSoftness;
in vec4 v_col;
in vec2 v_uv;
out vec4 fragColor;
void main() {
    if (u_discSoftness >= 0.0) {
        float radius = length(v_uv * 2.0 - 1.0);
        float edge = max(fwidth(radius), 0.0001);
        float alpha = 1.0 - smoothstep(1.0 - max(u_discSoftness, edge), 1.0, radius);
        fragColor = vec4(v_col.rgb, v_col.a * alpha);
    } else {
        vec2 uv = mix(u_uvRect.xy, u_uvRect.zw, v_uv);
        fragColor = texture(u_texture, uv) * v_col;
    }
}
