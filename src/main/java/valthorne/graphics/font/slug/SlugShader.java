package valthorne.graphics.font.slug;

import valthorne.graphics.shader.Shader;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;

/**
 * GLSL shader used by the fast 2D Slug font renderer.
 *
 * <p>This variant keeps Slug's curve coverage fragment shader, but removes the expensive reference
 * vertex dilation path. Glyph quads are padded on the CPU by {@link SlugBatch}, so the vertex shader
 * only expands a compact instance into a four-corner triangle strip.</p>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
final class SlugShader extends Shader {

    static final int ATTR_CORNER = 0;
    static final int ATTR_RECT = 1;
    static final int ATTR_TEX_RECT = 2;
    static final int ATTR_GLYPH = 3;
    static final int ATTR_BAND = 4;
    static final int ATTR_COLOR = 5;
    static final int ATTR_PIXELS_PER_EM = 6;

    private int mvpLocation;

    /**
     * Creates the shader program.
     */
    public SlugShader() {
        super(vertexSource(), fragmentSource());
        bindAttribLocation(ATTR_CORNER, "a_corner");
        bindAttribLocation(ATTR_RECT, "a_rect");
        bindAttribLocation(ATTR_TEX_RECT, "a_texRect");
        bindAttribLocation(ATTR_GLYPH, "a_glyphPack");
        bindAttribLocation(ATTR_BAND, "a_band");
        bindAttribLocation(ATTR_COLOR, "a_color");
        bindAttribLocation(ATTR_PIXELS_PER_EM, "a_pixelsPerEm");
        reload();
        bind();
        mvpLocation = glGetUniformLocation(getProgramID(), "u_mvp");
        setUniform1i("u_curveTexture", 0);
        setUniform1i("u_bandTexture", 1);
        unbind();
    }

    int mvpLocation() {
        return mvpLocation;
    }

    private static String vertexSource() {
        return """
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
                """;
    }

    private static String fragmentSource() {
        return """
                #version 330 core

                const int BAND_TEXTURE_WIDTH_LOG2 = 12;
                const int BAND_TEXTURE_WIDTH = 1 << BAND_TEXTURE_WIDTH_LOG2;

                uniform sampler2D u_curveTexture;
                uniform usampler2D u_bandTexture;

                in vec4 v_color;
                in vec2 v_texCoord;
                flat in vec4 v_banding;
                flat in ivec4 v_glyph;
                flat in float v_pixelsPerEm;

                out vec4 fragColor;

                float saturate(float x) {
                    return clamp(x, 0.0, 1.0);
                }

                uint calcRootCode(float y1, float y2, float y3) {
                    uint i1 = floatBitsToUint(y1) >> 31u;
                    uint i2 = floatBitsToUint(y2) >> 30u;
                    uint i3 = floatBitsToUint(y3) >> 29u;

                    uint shift = (i2 & 2u) | (i1 & ~2u);
                    shift = (i3 & 4u) | (shift & ~4u);
                    return ((0x2E74u >> shift) & 0x0101u);
                }

                vec2 solveHorizPoly(vec4 p12, vec2 p3) {
                    vec2 a = p12.xy - p12.zw * 2.0 + p3;
                    vec2 b = p12.xy - p12.zw;
                    float ra = 1.0 / a.y;
                    float rb = 0.5 / b.y;
                    float d = sqrt(max(b.y * b.y - a.y * p12.y, 0.0));
                    float t1 = (b.y - d) * ra;
                    float t2 = (b.y + d) * ra;
                    if (abs(a.y) < 1.0 / 65536.0) {
                        t1 = p12.y * rb;
                        t2 = t1;
                    }
                    return vec2((a.x * t1 - b.x * 2.0) * t1 + p12.x, (a.x * t2 - b.x * 2.0) * t2 + p12.x);
                }

                vec2 solveVertPoly(vec4 p12, vec2 p3) {
                    vec2 a = p12.xy - p12.zw * 2.0 + p3;
                    vec2 b = p12.xy - p12.zw;
                    float ra = 1.0 / a.x;
                    float rb = 0.5 / b.x;
                    float d = sqrt(max(b.x * b.x - a.x * p12.x, 0.0));
                    float t1 = (b.x - d) * ra;
                    float t2 = (b.x + d) * ra;
                    if (abs(a.x) < 1.0 / 65536.0) {
                        t1 = p12.x * rb;
                        t2 = t1;
                    }
                    return vec2((a.y * t1 - b.y * 2.0) * t1 + p12.y, (a.y * t2 - b.y * 2.0) * t2 + p12.y);
                }

                ivec2 calcBandLoc(ivec2 glyphLoc, uint offset) {
                    ivec2 bandLoc = ivec2(glyphLoc.x + int(offset), glyphLoc.y);
                    bandLoc.y += bandLoc.x >> BAND_TEXTURE_WIDTH_LOG2;
                    bandLoc.x &= BAND_TEXTURE_WIDTH - 1;
                    return bandLoc;
                }

                uvec2 loadBandOffset(ivec2 glyphLoc, uint offset) {
                    return texelFetch(u_bandTexture, calcBandLoc(glyphLoc, offset), 0).xy;
                }

                float calcCoverage(float xcov, float ycov, float xwgt, float ywgt, int flags) {
                    float wsum = max(xwgt + ywgt, 1.0 / 65536.0);
                    float coverage = max(abs(xcov * xwgt + ycov * ywgt) / wsum, min(abs(xcov), abs(ycov)));

                    if ((flags & 0x1000) == 0) {
                        return saturate(coverage);
                    }

                    return 1.0 - abs(1.0 - fract(coverage * 0.5) * 2.0);
                }

                float slugRender(vec2 renderCoord, vec4 bandTransform, ivec4 glyphData) {
                    vec2 pixelsPerEm = vec2(max(v_pixelsPerEm, 1.0e-6));

                    ivec2 bandMax = glyphData.zw;
                    bandMax.y &= 0x00FF;

                    ivec2 bandIndex = clamp(ivec2(renderCoord * bandTransform.xy + bandTransform.zw), ivec2(0, 0), bandMax);
                    ivec2 glyphLoc = glyphData.xy;

                    float xcov = 0.0;
                    float xwgt = 0.0;

                    uvec2 hbandData = loadBandOffset(glyphLoc, uint(bandIndex.y));
                    for (int curveIndex = 0; curveIndex < int(hbandData.x); curveIndex++) {
                        ivec2 curveLoc = ivec2(loadBandOffset(glyphLoc, hbandData.y + uint(curveIndex)));
                        vec4 p12 = texelFetch(u_curveTexture, curveLoc, 0) - vec4(renderCoord, renderCoord);
                        vec2 p3 = texelFetch(u_curveTexture, ivec2(curveLoc.x + 1, curveLoc.y), 0).xy - renderCoord;

                        if (max(max(p12.x, p12.z), p3.x) * pixelsPerEm.x < -0.5) {
                            break;
                        }

                        uint code = calcRootCode(p12.y, p12.w, p3.y);
                        if (code != 0u) {
                            vec2 r = solveHorizPoly(p12, p3) * pixelsPerEm.x;
                            if ((code & 1u) != 0u) {
                                xcov += saturate(r.x + 0.5);
                                xwgt = max(xwgt, saturate(1.0 - abs(r.x) * 2.0));
                            }
                            if (code > 1u) {
                                xcov -= saturate(r.y + 0.5);
                                xwgt = max(xwgt, saturate(1.0 - abs(r.y) * 2.0));
                            }
                        }
                    }

                    float ycov = 0.0;
                    float ywgt = 0.0;

                    uvec2 vbandData = loadBandOffset(glyphLoc, uint(bandMax.y + 1 + bandIndex.x));
                    for (int curveIndex = 0; curveIndex < int(vbandData.x); curveIndex++) {
                        ivec2 curveLoc = ivec2(loadBandOffset(glyphLoc, vbandData.y + uint(curveIndex)));
                        vec4 p12 = texelFetch(u_curveTexture, curveLoc, 0) - vec4(renderCoord, renderCoord);
                        vec2 p3 = texelFetch(u_curveTexture, ivec2(curveLoc.x + 1, curveLoc.y), 0).xy - renderCoord;

                        if (max(max(p12.y, p12.w), p3.y) * pixelsPerEm.y < -0.5) {
                            break;
                        }

                        uint code = calcRootCode(p12.x, p12.z, p3.x);
                        if (code != 0u) {
                            vec2 r = solveVertPoly(p12, p3) * pixelsPerEm.y;
                            if ((code & 1u) != 0u) {
                                ycov -= saturate(r.x + 0.5);
                                ywgt = max(ywgt, saturate(1.0 - abs(r.x) * 2.0));
                            }
                            if (code > 1u) {
                                ycov += saturate(r.y + 0.5);
                                ywgt = max(ywgt, saturate(1.0 - abs(r.y) * 2.0));
                            }
                        }
                    }

                    return calcCoverage(xcov, ycov, xwgt, ywgt, glyphData.w);
                }

                void main() {
                    float coverage = slugRender(v_texCoord, v_banding, v_glyph);
                    if (coverage <= 0.001) {
                        discard;
                    }
                    float alpha = v_color.a * coverage;
                    fragColor = vec4(v_color.rgb * alpha, alpha);
                }
                """;
    }
}
