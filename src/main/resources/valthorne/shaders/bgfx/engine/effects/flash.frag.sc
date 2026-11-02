$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_texture, 0);
uniform vec4 u_flashColor;
uniform vec4 u_amount;


void main() {
    vec4 c = texture2D(u_texture, v_uv) * v_color;

    float amt = clamp(u_amount.x, 0.0, 1.0);
    if (amt <= 0.0 || c.a <= 0.001) {
        gl_FragColor = c;
        return;
    }

    vec3 rgb = mix(c.rgb, u_flashColor.rgb, amt);
    float a = c.a * mix(1.0, clamp(u_flashColor.a, 0.0, 1.0), amt);

    gl_FragColor = vec4(rgb, a);
}
