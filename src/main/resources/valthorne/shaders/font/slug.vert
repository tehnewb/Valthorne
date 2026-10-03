#version 330 core

layout(location = 0) in vec2 a_corner;
layout(location = 1) in vec4 a_rect;
layout(location = 2) in vec4 a_texRect;
layout(location = 3) in uvec2 a_glyphPack;
layout(location = 4) in vec4 a_band;
layout(location = 5) in vec4 a_color;
layout(location = 6) in float a_pixelsPerEm;
layout(location = 7) in vec4 a_clipRect;
layout(location = 8) in float a_clipEnabled;

uniform mat4 u_mvp;
uniform vec4 u_textTransform;

out vec4 v_color;
out vec2 v_texCoord;
flat out vec4 v_banding;
flat out ivec4 v_glyph;
flat out float v_pixelsPerEm;
out vec2 v_world;
flat out vec4 v_clipRect;
flat out float v_clipEnabled;

void main() {
    vec2 xy = mix(a_rect.xy, a_rect.zw, a_corner);
    vec2 world = vec2(u_textTransform.x * xy.x - u_textTransform.y * xy.y,
              u_textTransform.y * xy.x + u_textTransform.x * xy.y) + u_textTransform.zw;
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
    v_world = world;
    v_clipRect = a_clipRect;
    v_clipEnabled = a_clipEnabled;
}
