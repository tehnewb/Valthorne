$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture, 0);
uniform vec4 u_uvRect;
uniform vec4 u_discSoftness;
void main() {
    if (u_discSoftness.x >= 0.0) {
        float radius = length(v_uv * 2.0 - 1.0);
        float edge = max(fwidth(radius), 0.0001);
        float alpha = 1.0 - smoothstep(1.0 - max(u_discSoftness.x, edge), 1.0, radius);
        gl_FragColor = vec4(v_color.rgb, v_color.a * alpha);
    } else {
        vec2 uv = mix(u_uvRect.xy, u_uvRect.zw, v_uv);
        gl_FragColor = texture2D(u_texture, uv) * v_color;
    }
}
