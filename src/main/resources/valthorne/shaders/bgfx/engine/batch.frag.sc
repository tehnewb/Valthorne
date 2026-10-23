$input v_color, v_uv, v_glyph, v_band, v_world, v_clip, v_params
#include <bgfx_shader.sh>
SAMPLER2D(u_tex0, 0);
SAMPLER2D(u_tex1, 1);
SAMPLER2D(u_tex2, 2);
SAMPLER2D(u_tex3, 3);
SAMPLER2D(u_tex4, 4);
SAMPLER2D(u_tex5, 5);
SAMPLER2D(u_tex6, 6);
SAMPLER2D(u_tex7, 7);
SAMPLER2D(u_tex8, 8);
SAMPLER2D(u_tex9, 9);
SAMPLER2D(u_tex10, 10);
SAMPLER2D(u_tex11, 11);
SAMPLER2D(u_tex12, 12);
SAMPLER2D(u_tex13, 13);
SAMPLER2D(u_tex14, 14);
SAMPLER2D(u_tex15, 15);
void main() {
    if (v_params.y > 0.5 && (v_world.x < v_clip.x || v_world.y < v_clip.y ||
        v_world.x > v_clip.x + v_clip.z || v_world.y > v_clip.y + v_clip.w)) discard;
    vec4 color;
    if (v_params.x < 0.5) color = texture2D(u_tex0, v_uv);
    else if (v_params.x < 1.5) color = texture2D(u_tex1, v_uv);
    else if (v_params.x < 2.5) color = texture2D(u_tex2, v_uv);
    else if (v_params.x < 3.5) color = texture2D(u_tex3, v_uv);
    else if (v_params.x < 4.5) color = texture2D(u_tex4, v_uv);
    else if (v_params.x < 5.5) color = texture2D(u_tex5, v_uv);
    else if (v_params.x < 6.5) color = texture2D(u_tex6, v_uv);
    else if (v_params.x < 7.5) color = texture2D(u_tex7, v_uv);
    else if (v_params.x < 8.5) color = texture2D(u_tex8, v_uv);
    else if (v_params.x < 9.5) color = texture2D(u_tex9, v_uv);
    else if (v_params.x < 10.5) color = texture2D(u_tex10, v_uv);
    else if (v_params.x < 11.5) color = texture2D(u_tex11, v_uv);
    else if (v_params.x < 12.5) color = texture2D(u_tex12, v_uv);
    else if (v_params.x < 13.5) color = texture2D(u_tex13, v_uv);
    else if (v_params.x < 14.5) color = texture2D(u_tex14, v_uv);
    else color = texture2D(u_tex15, v_uv);
    gl_FragColor = color * v_color;
}
