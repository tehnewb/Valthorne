package valthorne.graphics.font.slug;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTVertex;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.stb.STBTruetype.*;

/**
 * Compiles CPU-only Slug outlines, lookup bands, metrics and kerning without a
 * graphics context. OpenGL and bgfx consume the same compiler and glyph metadata;
 * neither backend owns or changes these results. Construct on an asset worker,
 * then upload the read-only buffers on the rendering thread.
 *
 * <p>Compilation allocates direct buffers managed by the JVM. Instances retain
 * encoded font bytes for STB fallback kerning, support at most 256 consecutive
 * characters, and preserve the existing UTF-16, quadratic and cubic approximation
 * behavior. Buffer accessors return independent read-only views; obtain them once
 * during upload rather than in a draw loop. No GPU resources require disposal.</p>
 *
 * <pre>{@code
 * SlugOutlines outlines = new SlugOutlines(SlugData.load("assets/font.ttf"));
 * SlugGlyph glyph = outlines.glyph('A');
 * }</pre>
 */
public final class SlugOutlines {
    /**
     * Fixed data-texture row width. Shader address packing and CPU row shifts assume 4096.
     */
    public static final int TEXTURE_WIDTH = 4096;

    /**
     * Band-count cap read once from valthorne.slug.maxBands; defaults to 48.
     */
    private static final int MAX_BANDS_PER_AXIS = Integer.getInteger("valthorne.slug.maxBands", 48);
    /**
     * Em-span band density read once from valthorne.slug.bandsPerEm; defaults to 40.
     */
    private static final float BANDS_PER_EM = Float.parseFloat(System.getProperty("valthorne.slug.bandsPerEm", "40"));
    /**
     * Squared em-space endpoint threshold below which curve segments are omitted.
     */
    private static final float MIN_CURVE_LENGTH_SQUARED = Float.parseFloat(System.getProperty("valthorne.slug.minCurveLengthSquared", "0.00000025"));
    /**
     * Squared em-space flatness threshold used to simplify quadratic and cubic outlines.
     */
    private static final float QUADRATIC_FLATNESS_SQUARED = Float.parseFloat(System.getProperty("valthorne.slug.quadraticFlatnessSquared", "0.00000009"));
    /**
     * Em-space expansion on each band edge to retain near-boundary curves.
     */
    private static final float BAND_EPSILON = 0.00005f;
    private final SlugMetrics metrics; // Compact CPU layout state independent of upload buffers.
    private final FloatBuffer curves; // Immutable row-padded quadratic control points.
    private final IntBuffer bands; // Immutable row-padded band headers and curve addresses.

    /**
     * Decodes a font and compiles its requested range with no graphics API calls.
     *
     * @param data immutable encoded font and contiguous character range
     * @throws NullPointerException if data is null
     * @throws RuntimeException if STB cannot decode the font
     */
    public SlugOutlines(SlugData data) {
        /*
         * Compile outline texels once for either backend; no graphics resource is
         * created here and no bitmap atlas is baked.
         */
        if (data == null) throw new NullPointerException("data");
        byte[] fontBytes = data.bytes();
        int firstCodepoint = data.firstCodepoint();
        int characterCount = data.characterCount();

        ByteBuffer fontBuffer = BufferUtils.createByteBuffer(fontBytes.length);
        fontBuffer.put(fontBytes)
                .flip();

        STBTTFontinfo info = STBTTFontinfo.create();
        if (!stbtt_InitFont(info, fontBuffer)) {
            throw new RuntimeException("Failed to initialize STB font.");
        }

        IntBuffer ascentRaw = BufferUtils.createIntBuffer(1);
        IntBuffer descentRaw = BufferUtils.createIntBuffer(1);
        IntBuffer lineGapRaw = BufferUtils.createIntBuffer(1);
        stbtt_GetFontVMetrics(info, ascentRaw, descentRaw, lineGapRaw);

        float emScale = stbtt_ScaleForMappingEmToPixels(info, 1.0f);
        float ascent = ascentRaw.get(0) * emScale;
        float descent = descentRaw.get(0) * emScale;
        float lineGap = lineGapRaw.get(0) * emScale;

        SlugCurveBuffer curveWriter = new SlugCurveBuffer(TEXTURE_WIDTH);
        SlugBandBuffer bandWriter = new SlugBandBuffer(TEXTURE_WIDTH);
        SlugGlyph[] glyphs = new SlugGlyph[characterCount];

        for (int i = 0; i < characterCount; i++) {
            int codepoint = firstCodepoint + i;
            glyphs[i] = compileGlyph(info, codepoint, emScale, curveWriter, bandWriter);
        }

        float[] kerning = createKerningCache(characterCount);

        int curveHeight = Math.max(1, curveWriter.height());
        int bandHeight = Math.max(1, bandWriter.height());

        curves = curveWriter.toBuffer(curveHeight);
        bands = bandWriter.toBuffer(bandHeight);

        metrics = new SlugMetrics(info, fontBuffer, glyphs, kerning, firstCodepoint, emScale, ascent, descent, ascent - descent + lineGap);
    }

    /**
     * Allocates direct pair-cache slots without querying unused character pairs.
     *
     * @param characterCount number of compiled characters
     * @return pair cache with NaN denoting a missing query
     */
    private static float[] createKerningCache(int characterCount) {
        /*
         * Retain indexed lookup while moving native work to first use of a pair.
         */
        float[] table = new float[characterCount * characterCount];
        Arrays.fill(table, Float.NaN);
        return table;
    }

    /**
     * Reads metrics and outlines, appends curve texels, and builds horizontal and
     * vertical band headers followed by curve-address lists. Empty outlines or empty
     * bounds retain advance metrics but produce a nondrawable glyph. Packed glyph
     * metadata maps em-space coordinates into the selected bands.
     *
     * @param info        initialized STB font
     * @param codepoint   character to compile
     * @param emScale     raw-unit to em multiplier
     * @param curveWriter shared curve-data destination
     * @param bandWriter  shared band-data destination
     * @return compiled metrics and GPU lookup metadata
     */
    private static SlugGlyph compileGlyph(STBTTFontinfo info, int codepoint, float emScale, SlugCurveBuffer curveWriter, SlugBandBuffer bandWriter) {
        /*
         * Append each outline to shared texture staging and retain only immutable draw metadata.
         */
        IntBuffer advanceRaw = BufferUtils.createIntBuffer(1);
        IntBuffer lsbRaw = BufferUtils.createIntBuffer(1);
        stbtt_GetCodepointHMetrics(info, codepoint, advanceRaw, lsbRaw);

        float advance = advanceRaw.get(0) * emScale;
        float lsb = lsbRaw.get(0) * emScale;

        IntBuffer ix0 = BufferUtils.createIntBuffer(1);
        IntBuffer iy0 = BufferUtils.createIntBuffer(1);
        IntBuffer ix1 = BufferUtils.createIntBuffer(1);
        IntBuffer iy1 = BufferUtils.createIntBuffer(1);
        boolean hasBox = stbtt_GetCodepointBox(info, codepoint, ix0, iy0, ix1, iy1);

        float x0 = hasBox ? ix0.get(0) * emScale : 0f;
        float y0 = hasBox ? iy0.get(0) * emScale : 0f;
        float x1 = hasBox ? ix1.get(0) * emScale : 0f;
        float y1 = hasBox ? iy1.get(0) * emScale : 0f;

        List<SlugCurve> curves = loadCurves(info, codepoint, emScale);
        if (curves.isEmpty() || x1 <= x0 || y1 <= y0) {
            return new SlugGlyph(codepoint, advance, lsb, x0, y0, x1, y1, 0, 0, 0f, 0f, 0f, 0f, false);
        }

        int[] curveLocations = new int[curves.size()];
        for (int i = 0; i < curves.size(); i++) {
            curveLocations[i] = curveWriter.writeCurve(curves.get(i));
        }

        int horizontalBandCount = chooseBandCount(y1 - y0, curves.size());
        int verticalBandCount = chooseBandCount(x1 - x0, curves.size());

        int[][] horizontalBands = buildBands(curves, curveLocations, horizontalBandCount, y0, y1, true);
        int[][] verticalBands = buildBands(curves, curveLocations, verticalBandCount, x0, x1, false);

        int glyphStart = bandWriter.position();
        int listOffset = horizontalBandCount + verticalBandCount;
        int runningOffset = listOffset;

        for (int i = 0; i < horizontalBandCount; i++) {
            bandWriter.write(horizontalBands[i].length, runningOffset);
            runningOffset += horizontalBands[i].length;
        }
        for (int i = 0; i < verticalBandCount; i++) {
            bandWriter.write(verticalBands[i].length, runningOffset);
            runningOffset += verticalBands[i].length;
        }

        for (int[] band : horizontalBands) {
            for (int loc : band) {
                bandWriter.write(unpackX(loc), unpackY(loc));
            }
        }
        for (int[] band : verticalBands) {
            for (int loc : band) {
                bandWriter.write(unpackX(loc), unpackY(loc));
            }
        }

        float bandScaleX = verticalBandCount / Math.max(x1 - x0, 1.0e-6f);
        float bandScaleY = horizontalBandCount / Math.max(y1 - y0, 1.0e-6f);
        float bandOffsetX = -x0 * bandScaleX;
        float bandOffsetY = -y0 * bandScaleY;

        int glyphPack = packLocation(glyphStart & (TEXTURE_WIDTH - 1), glyphStart >> 12);
        int glyphInfo = packGlyphInfo(verticalBandCount - 1, horizontalBandCount - 1, false);
        return new SlugGlyph(codepoint, advance, lsb, x0, y0, x1, y1, glyphPack, glyphInfo, bandScaleX, bandScaleY, bandOffsetX, bandOffsetY, true);
    }

    /**
     * Chooses one band for empty spans or at most two curves; otherwise combines
     * curve-count and em-span heuristics and clamps to the configured band limit.
     *
     * @param span       axis extent in em units
     * @param curveCount outline segment count
     * @return band count for one axis
     */
    private static int chooseBandCount(float span, int curveCount) {
        /*
         * Balance band lookup density against curve count and the configured upper limit.
         */
        if (span <= 0f || curveCount <= 2) {
            return 1;
        }
        int byCurves = (int) Math.ceil(Math.sqrt(curveCount) * 2.25f);
        int bySpan = (int) Math.ceil(span * BANDS_PER_EM);
        return clamp(Math.max(byCurves, bySpan), 1, MAX_BANDS_PER_AXIS);
    }

    /**
     * Assigns overlapping curves to each expanded axis band, then sorts each list
     * by descending maximum extent on the perpendicular axis for shader traversal.
     *
     * @param curves         source quadratic segments
     * @param curveLocations matching packed curve texture addresses
     * @param bandCount      number of bands
     * @param min            minimum em-space axis coordinate
     * @param max            maximum em-space axis coordinate
     * @param horizontal     true for Y bands traversed along X, false for X bands
     * @return packed curve-address list per band
     */
    private static int[][] buildBands(List<SlugCurve> curves, int[] curveLocations, int bandCount, float min, float max, boolean horizontal) {
        /*
         * Group and order intersecting curve addresses once during loading, outside draw loops.
         */
        int[][] result = new int[bandCount][];
        float span = Math.max(max - min, 1.0e-6f);

        for (int band = 0; band < bandCount; band++) {
            float bandMin = min + span * band / bandCount - BAND_EPSILON;
            float bandMax = min + span * (band + 1) / bandCount + BAND_EPSILON;
            ArrayList<Integer> list = new ArrayList<>();

            for (int i = 0; i < curves.size(); i++) {
                SlugCurve curve = curves.get(i);
                float cmin = horizontal ? curve.minY() : curve.minX();
                float cmax = horizontal ? curve.maxY() : curve.maxX();
                if (cmax >= bandMin && cmin <= bandMax) {
                    list.add(i);
                }
            }

            list.sort((a, b) -> {
                SlugCurve ca = curves.get(a);
                SlugCurve cb = curves.get(b);
                float va = horizontal ? ca.maxX() : ca.maxY();
                float vb = horizontal ? cb.maxX() : cb.maxY();
                return Float.compare(vb, va);
            });

            int[] packed = new int[list.size()];
            for (int i = 0; i < packed.length; i++) {
                packed[i] = curveLocations[list.get(i)];
            }
            result[band] = packed;
        }

        return result;
    }

    /**
     * Converts STB outline commands to em-space quadratic records, closing each
     * contour. Lines become midpoint quadratics; cubic segments are approximated.
     * Always frees the borrowed native outline buffer after processing.
     *
     * @param info      initialized font information
     * @param codepoint character whose outline is requested
     * @param emScale   raw-unit to em multiplier
     * @return compiled segments, possibly empty
     */
    private static List<SlugCurve> loadCurves(STBTTFontinfo info, int codepoint, float emScale) {
        /*
         * Normalize outline commands to quadratic segments while retaining contour closure.
         */
        List<SlugCurve> curves = new ArrayList<>();
        STBTTVertex.Buffer vertices = stbtt_GetCodepointShape(info, codepoint);
        if (vertices == null) {
            return curves;
        }

        float startX = 0f;
        float startY = 0f;
        float currentX = 0f;
        float currentY = 0f;
        boolean contourOpen = false;

        try {
            for (int i = 0; i < vertices.limit(); i++) {
                STBTTVertex v = vertices.get(i);
                int type = v.type();

                if (type == STBTT_vmove) {
                    if (contourOpen) {
                        addLine(curves, currentX, currentY, startX, startY);
                    }
                    startX = v.x() * emScale;
                    startY = v.y() * emScale;
                    currentX = startX;
                    currentY = startY;
                    contourOpen = true;
                } else if (type == STBTT_vline) {
                    float x = v.x() * emScale;
                    float y = v.y() * emScale;
                    addLine(curves, currentX, currentY, x, y);
                    currentX = x;
                    currentY = y;
                } else if (type == STBTT_vcurve) {
                    float cx = v.cx() * emScale;
                    float cy = v.cy() * emScale;
                    float x = v.x() * emScale;
                    float y = v.y() * emScale;
                    addQuadratic(curves, currentX, currentY, cx, cy, x, y);
                    currentX = x;
                    currentY = y;
                } else if (type == STBTT_vcubic) {
                    float cx0 = v.cx() * emScale;
                    float cy0 = v.cy() * emScale;
                    float cx1 = v.cx1() * emScale;
                    float cy1 = v.cy1() * emScale;
                    float x = v.x() * emScale;
                    float y = v.y() * emScale;
                    approximateCubic(curves, currentX, currentY, cx0, cy0, cx1, cy1, x, y);
                    currentX = x;
                    currentY = y;
                }
            }

            if (contourOpen) {
                addLine(curves, currentX, currentY, startX, startY);
            }
        } finally {
            stbtt_FreeShape(info, vertices);
        }

        return curves;
    }

    /**
     * Appends a line encoded as a quadratic with midpoint control, unless endpoint
     * distance squared is below the configured minimum.
     *
     * @param curves destination segment list
     * @param x0     start X in em units
     * @param y0     start Y in em units
     * @param x1     end X in em units
     * @param y1     end Y in em units
     */
    private static void addLine(List<SlugCurve> curves, float x0, float y0, float x1, float y1) {
        /*
         * Represent a line as a midpoint quadratic compatible with the same GPU evaluator.
         */
        float dx = x1 - x0;
        float dy = y1 - y0;
        if (dx * dx + dy * dy < MIN_CURVE_LENGTH_SQUARED) {
            return;
        }
        curves.add(new SlugCurve(x0, y0, (x0 + x1) * 0.5f, (y0 + y1) * 0.5f, x1, y1));
    }

    /**
     * Appends a quadratic unless its endpoint separation is below the minimum.
     * A control point sufficiently close to the endpoint midpoint is replaced by
     * that midpoint, encoding a straight segment.
     *
     * @param curves destination segments
     * @param x0     start X
     * @param y0     start Y
     * @param cx     control X
     * @param cy     control Y
     * @param x1     end X
     * @param y1     end Y
     */
    private static void addQuadratic(List<SlugCurve> curves, float x0, float y0, float cx, float cy, float x1, float y1) {
        /*
         * Omit negligible segments and simplify flat control points before texture staging.
         */
        float dx = x1 - x0;
        float dy = y1 - y0;
        if (dx * dx + dy * dy < MIN_CURVE_LENGTH_SQUARED) {
            return;
        }

        float mx = (x0 + x1) * 0.5f;
        float my = (y0 + y1) * 0.5f;
        float fx = cx - mx;
        float fy = cy - my;
        if (fx * fx + fy * fy < QUADRATIC_FLATNESS_SQUARED) {
            curves.add(new SlugCurve(x0, y0, mx, my, x1, y1));
            return;
        }

        curves.add(new SlugCurve(x0, y0, cx, cy, x1, y1));
    }

    /**
     * Approximates a cubic in em space. Very short endpoint spans are omitted,
     * nearly collinear controls become one line, and other curves become five or
     * eight line segments according to control-point distance from the endpoint line.
     *
     * @param curves destination quadratic/line records
     * @param x0     start X
     * @param y0     start Y
     * @param cx0    first control X
     * @param cy0    first control Y
     * @param cx1    second control X
     * @param cy1    second control Y
     * @param x1     end X
     * @param y1     end Y
     */
    private static void approximateCubic(List<SlugCurve> curves, float x0, float y0, float cx0, float cy0, float cx1, float cy1, float x1, float y1) {
        /*
         * Sample unsupported cubic outlines into the existing quadratic line representation.
         */
        float dx = x1 - x0;
        float dy = y1 - y0;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < MIN_CURVE_LENGTH_SQUARED) {
            return;
        }

        float flat0 = distanceToLineSquared(cx0, cy0, x0, y0, x1, y1);
        float flat1 = distanceToLineSquared(cx1, cy1, x0, y0, x1, y1);
        if (Math.max(flat0, flat1) < QUADRATIC_FLATNESS_SQUARED) {
            addLine(curves, x0, y0, x1, y1);
            return;
        }

        float px = x0;
        float py = y0;
        final int steps = Math.max(flat0, flat1) > 0.00001f ? 8 : 5;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float u = 1f - t;
            float x = u * u * u * x0 + 3f * u * u * t * cx0 + 3f * u * t * t * cx1 + t * t * t * x1;
            float y = u * u * u * y0 + 3f * u * u * t * cy0 + 3f * u * t * t * cy1 + t * t * t * y1;
            addLine(curves, px, py, x, y);
            px = x;
            py = y;
        }
    }

    /**
     * Measures squared perpendicular distance to the infinite endpoint line.
     * For a near-zero line length, returns squared distance to its first endpoint.
     *
     * @param px point X
     * @param py point Y
     * @param x0 first line point X
     * @param y0 first line point Y
     * @param x1 second line point X
     * @param y1 second line point Y
     * @return squared distance in the input coordinate units
     */
    private static float distanceToLineSquared(float px, float py, float x0, float y0, float x1, float y1) {
        /*
         * Use squared distances to avoid a square root when checking outline flatness.
         */
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        if (len2 < 1.0e-12f) {
            float ox = px - x0;
            float oy = py - y0;
            return ox * ox + oy * oy;
        }
        float cross = (px - x0) * dy - (py - y0) * dx;
        return (cross * cross) / len2;
    }

    /**
     * Packs texel coordinates into low and high 16-bit halves without range checks.
     *
     * @param x horizontal coordinate stored in the low half
     * @param y vertical coordinate stored in the high half
     * @return packed texture address
     */
    static int packLocation(int x, int y) {
        /*
         * Store the row and column in fixed halves matching the shader address decoder.
         */
        return (y << 16) | (x & 0xFFFF);
    }

    /**
     * Packs zero-based band maxima and the optional even-odd fill flag for the shader.
     * Only eight bits of each band maximum are retained.
     *
     * @param bandMaxX last vertical-band index
     * @param bandMaxY last horizontal-band index
     * @param evenOdd  whether to set the even-odd fill bit
     * @return packed glyph metadata
     */
    private static int packGlyphInfo(int bandMaxX, int bandMaxY, boolean evenOdd) {
        /*
         * Pack band maxima and the fill flag into one integer consumed by the glyph shader.
         */
        int value = (bandMaxX & 0xFF) | ((bandMaxY & 0xFF) << 16);
        if (evenOdd) {
            value |= 0x10000000;
        }
        return value;
    }

    /**
     * Extracts the unsigned low-half texel coordinate.
     *
     * @param packed packed texture address
     * @return X from 0 through 65535
     */
    private static int unpackX(int packed) {
        /*
         * Mask the low half without sign extension when decoding staged curve addresses.
         */
        return packed & 0xFFFF;
    }

    /**
     * Extracts the unsigned high-half texel coordinate.
     *
     * @param packed packed texture address
     * @return Y from 0 through 65535
     */
    private static int unpackY(int packed) {
        /*
         * Shift the upper half unsigned to recover the texture row.
         */
        return (packed >>> 16) & 0xFFFF;
    }

    /**
     * Clamps an integer to inclusive limits, assuming min does not exceed max.
     *
     * @param value candidate value
     * @param min   lower limit
     * @param max   upper limit
     * @return bounded value
     */
    private static int clamp(int value, int min, int max) {
        /*
         * Bound integer band indices directly without temporary range objects.
         */
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Returns upload-ready quadratic texels without exposing mutable storage.
     *
     * @return read-only RGBA32F view positioned at zero
     */
    public FloatBuffer curves() {
        return curves.asReadOnlyBuffer();
    }

    /**
     * Returns upload-ready band texels without exposing mutable storage.
     *
     * @return read-only RG32UI view positioned at zero
     */
    public IntBuffer bands() {
        return bands.asReadOnlyBuffer();
    }

    /**
     * Returns compact layout state without retaining curve or band upload buffers.
     *
     * @return shared glyph, kerning and line-metric state
     */
    public SlugMetrics metrics() {
        return metrics;
    }

    /**
     * Looks up a glyph in the compiled range.
     *
     * @param codepoint requested Unicode character
     * @return glyph metadata, or null outside the range
     */
    public SlugGlyph glyph(int codepoint) {
        return metrics.glyph(codepoint);
    }
}
