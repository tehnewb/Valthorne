#version 330 core

layout(location = 0) in vec2 a_corner;
layout(location = 1) in vec4 a_rect;
layout(location = 2) in vec4 a_texRect;
layout(location = 3) in uvec2 a_glyphPack;
layout(location = 4) in vec4 a_band;
layout(location = 5) in vec4 a_color;
layout(location = 6) in float a_pixelsPerEm;

uniform mat4 u_mvp;

out vec4 v_color;
out vec2 v_texCoord;
flat out vec4 v_banding;
flat out ivec4 v_glyph;
flat out float v_pixelsPerEm;

void main() {
    vec2 xy = mix(a_rect.xy, a_rect.zw, a_corner);
    v_texCoord = mix(a_texRect.xy, a_texRect.zw, a_corner);
    gl_Position = u_mvp * vec4(xy, 0.0, 1.0);

    v_glyph = ivec4(
            int(a_glyphPack.x & 0xFFFFu),
            int(a_glyphPack.x >> 16u),
            int(a_glyphPack.y & 0xFFFFu),
            int(a_glyphPack.y >> 16u)
    );
    v_banding = a_band;
    v_pixelsPerEm = a_pixelsPerEm;
    v_color = a_color;
}
