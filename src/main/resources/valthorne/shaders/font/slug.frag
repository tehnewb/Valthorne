#version 330 core

const int BAND_TEXTURE_WIDTH_LOG2 = 12;
const int BAND_TEXTURE_WIDTH = 1 << BAND_TEXTURE_WIDTH_LOG2;

uniform sampler2D u_curveTexture;
uniform usampler2D u_bandTexture;
uniform bool u_linearColor;
uniform bool u_hintedUi;
uniform sampler2D u_coverageTexture;
uniform bool u_writeCoverage;

in vec4 v_color;
in vec2 v_texCoord;
flat in vec4 v_sampleOrigin;
flat in vec4 v_banding;
flat in ivec4 v_glyph;
flat in float v_pixelsPerEm;
in vec2 v_world;
flat in vec4 v_clipRect;
flat in float v_clipEnabled;

out vec4 fragColor;

// Grayscale alpha-response adaptation from Microsoft Terminal (MIT).
// Source: src/renderer/atlas/dwrite_helpers.hlsl. License: META-INF/licenses/microsoft-terminal.txt.
// Fixed published default gamma 1.8 / grayscale contrast 1 keeps portable UI output
// independent of platform settings. The geometry and scalable world path are unchanged.
float uiCoverage(float coverage, vec3 foreground) {
    const vec4 gammaRatios = vec4(0.148054421, -0.894594550, 1.47590804, -0.324668258);
    float contrast = clamp(3.0 - 4.0 * dot(foreground, vec3(0.30, 0.59, 0.11)), 0.0, 1.0);
    float contrasted = coverage * (contrast + 1.0) / (coverage * contrast + 1.0);
    float intensity = dot(foreground, vec3(0.25, 0.5, 0.25));
    return clamp(contrasted + contrasted * (1.0 - contrasted) * ((gammaRatios.x * intensity + gammaRatios.y) * contrasted + gammaRatios.z * intensity + gammaRatios.w), 0.0, 1.0);
}

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

float slugRender(vec2 renderCoord, vec4 bandTransform, ivec4 glyphData, vec2 pixelsPerEm) {
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
#ifndef SLUG_CACHED_COVERAGE
    // Keep the integer baseline separate from its exact fractional phase. This
    // gives live and atlas draws identical coordinates despite viewport offsets.
    vec2 renderCoord = v_pixelsPerEm > 0.0 ? (floor(gl_FragCoord.xy) - v_sampleOrigin.xy + (vec2(0.5) - v_sampleOrigin.zw)) / v_pixelsPerEm : v_texCoord;
    // Derivatives precede divergent clipping; rejected fragments skip curve work.
    vec2 pixelsPerEm = v_pixelsPerEm > 0.0 ? vec2(v_pixelsPerEm) : 1.0 / max(fwidth(renderCoord), vec2(1.0e-6));
#endif
    if (v_clipEnabled > 0.5 && (v_world.x < v_clipRect.x || v_world.y < v_clipRect.y ||
        v_world.x > v_clipRect.x + v_clipRect.z || v_world.y > v_clipRect.y + v_clipRect.w)) {
        discard;
    }
#ifdef SLUG_CACHED_COVERAGE
    float coverage = texelFetch(u_coverageTexture, ivec2(floor(v_texCoord)), 0).r;
#else
    float coverage = slugRender(renderCoord, v_banding, v_glyph, pixelsPerEm);
    if (u_writeCoverage) {
        fragColor = vec4(coverage, 0.0, 0.0, 1.0);
        return;
    }
#endif
    if (u_hintedUi) coverage = uiCoverage(coverage, v_color.rgb);
    if (coverage <= 0.001) {
        discard;
    }
    float alpha = v_color.a * coverage;
    vec3 color = v_color.rgb;
    if (u_linearColor) {
        color = mix(color / 12.92, pow((color + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), color));
    }
    fragColor = vec4(color * alpha, alpha);
}
