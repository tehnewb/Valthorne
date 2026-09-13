package valthorne.graphics.font.slug;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTVertex;
import valthorne.graphics.Color;
import valthorne.ui.Dimensional;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_RG32UI;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL30.GL_RG_INTEGER;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * Owns GPU outline data and font metrics for a contiguous character range,
 * using Slug-style banded curve evaluation. Loading compiles glyph outlines into
 * quadratic segments, horizontal/vertical curve lookup bands, and a dense kerning
 * table, then uploads RGBA16F curve and RG32UI band textures. It therefore requires
 * a current OpenGL context even though font decoding uses STB on the CPU.
 * <p>
 * Text drawing and measurement iterate UTF-16 characters, recognize newline and
 * tab, and use a quarter-em advance for characters outside the compiled range.
 * They do not perform script shaping or surrogate-pair composition. Cubic outlines
 * are approximated by short line sequences; small segments are omitted according
 * to the configured thresholds.
 * </p>
 * <p>
 * The font retains backing bytes for STB fallback kerning queries and owns its two
 * GPU textures. Dispose them with the context current after all drawing completes.
 * Measurement data remains stored after disposal, but drawing then has no valid
 * texture resources. Optional retained text, baseline position, size, and color
 * support Dimensional usage; explicit width/height setters only override measured
 * metadata and do not scale rendered glyphs.
 * </p>
 * <pre>{@code
 * SlugFont font = SlugFont.load("assets/font.ttf");
 * try {
 *     font.setText("Hello").setSize(32);
 *     font.setPosition(20, 60);
 *     font.draw(batch); // Inside the caller's SlugBatch drawing lifecycle.
 * } finally {
 *     font.dispose();
 * }
 * }</pre>
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public final class SlugFont implements Dimensional {
    private SlugTextRun retainedRun; // Reusable convenience-draw layout; separate runs are required for independent retained text.

    /**
     * Fixed data-texture row width. Shader address packing and CPU row shifts assume 4096.
     */
    static final int TEXTURE_WIDTH = 4096; // Fixed Slug data texture width used by the shader.

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

    private final STBTTFontinfo info; // STB font info kept alive for fallback queries.
    private final ByteBuffer fontBuffer; // Backing font bytes kept alive for STB font info.
    private final SlugGlyph[] glyphs; // Glyph lookup table for the configured character range.
    private final float[] kerning; // Dense kerning table in em units for the configured range.
    private final int firstCodepoint; // First codepoint included in the lookup table.
    private final int characterCount; // Number of codepoints included in the lookup table.
    private final float emScale; // Raw font unit to em-space scale.
    private final float ascent; // Font ascent in em units.
    private final float descent; // Font descent in em units.
    private final float lineGap; // Line gap in em units.
    private final float lineHeight; // Line height in em units.
    private final float fallbackAdvance; // Advance used when a glyph is not present.
    private final Color color = new Color(1f, 1f, 1f, 1f); // Optional retained color for Font-like usage.
    private int curveTexture; // OpenGL texture id storing curve control points.
    private int bandTexture; // OpenGL texture id storing band lookup data.
    private int curveTextureHeight; // Height of the curve texture in texels.
    private int bandTextureHeight; // Height of the band texture in texels.
    private String text = ""; // Optional retained text for Font-like usage.
    private float x; // Optional retained x position for Font-like usage.
    private float y; // Optional retained y position for Font-like usage.
    private float size = 32f; // Optional retained size in world units per em.
    private float width; // Cached measured width of retained text.
    private float height; // Cached measured height of retained text.

    /**
     * Takes ownership of compiled glyph tables and uploaded textures, retaining the
     * font bytes that back STB metric queries. Initializes retained-text measurement.
     *
     * @param info initialized STB font information
     * @param fontBuffer backing bytes that must outlive info
     * @param glyphs compiled character table
     * @param kerning dense pair advances in em units
     * @param firstCodepoint first table codepoint
     * @param characterCount number of compiled entries
     * @param emScale font-unit to em multiplier
     * @param ascent ascent in em units
     * @param descent descent in em units
     * @param lineGap additional line spacing in em units
     * @param curveTexture owned curve texture name
     * @param curveTextureHeight curve texture rows
     * @param bandTexture owned band texture name
     * @param bandTextureHeight band texture rows
     */
    private SlugFont(STBTTFontinfo info, ByteBuffer fontBuffer, SlugGlyph[] glyphs, float[] kerning,
                     int firstCodepoint, int characterCount, float emScale, float ascent, float descent, float lineGap,
                     int curveTexture, int curveTextureHeight, int bandTexture, int bandTextureHeight) {
        this.info = info;
        this.fontBuffer = fontBuffer;
        this.glyphs = glyphs;
        this.kerning = kerning;
        this.firstCodepoint = firstCodepoint;
        this.characterCount = characterCount;
        this.emScale = emScale;
        this.ascent = ascent;
        this.descent = descent;
        this.lineGap = lineGap;
        this.lineHeight = ascent - descent + lineGap;
        this.fallbackAdvance = 0.25f;
        this.curveTexture = curveTexture;
        this.curveTextureHeight = curveTextureHeight;
        this.bandTexture = bandTexture;
        this.bandTextureHeight = bandTextureHeight;
        recalcSize();
    }

    /**
     * Loads and uploads the 95 printable ASCII characters beginning at codepoint 32.
     * Requires a current OpenGL context for data texture creation.
     *
     * @param path TrueType/OpenType filesystem path
     * @return newly owned font
     * @throws RuntimeException if reading or STB initialization fails
     */
    public static SlugFont load(String path) {
        return load(path, 32, 95);
    }

    /**
     * Reads a font file and compiles the requested contiguous character range,
     * including its pairwise kerning table and GPU data textures.
     *
     * @param path TrueType/OpenType filesystem path
     * @param firstCodepoint first character to compile
     * @param characterCount range length from 1 through 256
     * @return newly owned font
     * @throws IllegalArgumentException if characterCount is outside its supported range
     * @throws RuntimeException if reading or STB initialization fails
     */
    public static SlugFont load(String path, int firstCodepoint, int characterCount) {
        try {
            return load(Files.readAllBytes(Path.of(path)), firstCodepoint, characterCount);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Copies source bytes into retained direct storage, initializes STB, compiles
     * glyphs and kerning, and uploads the two owned data textures. The caller may
     * reuse its byte array afterward. Character-count validation does not validate
     * the first codepoint. Texture creation preserves texture and pixel-unpack state.
     *
     * @param fontBytes nonnull, nonempty font data
     * @param firstCodepoint first codepoint in the lookup table
     * @param characterCount table length from 1 through 256
     * @return newly owned font
     * @throws IllegalArgumentException if bytes are absent or characterCount is invalid
     * @throws RuntimeException if STB cannot initialize the font
     */
    public static SlugFont load(byte[] fontBytes, int firstCodepoint, int characterCount) {
        if (fontBytes == null || fontBytes.length == 0) {
            throw new IllegalArgumentException("fontBytes cannot be null or empty.");
        }
        if (characterCount <= 0 || characterCount > 256) {
            throw new IllegalArgumentException("characterCount must be in the range [1, 256].");
        }

        ByteBuffer fontBuffer = BufferUtils.createByteBuffer(fontBytes.length);
        fontBuffer.put(fontBytes).flip();

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

        FloatTexelWriter curveWriter = new FloatTexelWriter(TEXTURE_WIDTH);
        UIntTexelWriter bandWriter = new UIntTexelWriter(TEXTURE_WIDTH);
        SlugGlyph[] glyphs = new SlugGlyph[characterCount];

        for (int i = 0; i < characterCount; i++) {
            int codepoint = firstCodepoint + i;
            glyphs[i] = compileGlyph(info, codepoint, emScale, curveWriter, bandWriter);
        }

        float[] kerning = compileKerning(info, firstCodepoint, characterCount, emScale);

        int curveHeight = Math.max(1, curveWriter.height());
        int bandHeight = Math.max(1, bandWriter.height());

        int curveTexture = uploadCurveTexture(curveWriter.toBuffer(curveHeight));
        int bandTexture = uploadBandTexture(bandWriter.toBuffer(bandHeight));

        return new SlugFont(info, fontBuffer, glyphs, kerning, firstCodepoint, characterCount, emScale, ascent, descent, lineGap, curveTexture, curveHeight, bandTexture, bandHeight);
    }

    /**
     * Precomputes every ordered pair's STB kerning advance in em units. Rows index
     * the previous character and columns index the current character.
     *
     * @param info initialized font information
     * @param firstCodepoint first table codepoint
     * @param characterCount row and column count
     * @param emScale raw-unit to em multiplier
     * @return dense characterCount-squared table
     */
    private static float[] compileKerning(STBTTFontinfo info, int firstCodepoint, int characterCount, float emScale) {
        float[] table = new float[characterCount * characterCount];
        for (int p = 0; p < characterCount; p++) {
            int previous = firstCodepoint + p;
            int row = p * characterCount;
            for (int c = 0; c < characterCount; c++) {
                table[row + c] = stbtt_GetCodepointKernAdvance(info, previous, firstCodepoint + c) * emScale;
            }
        }
        return table;
    }

    /**
     * Reads metrics and outlines, appends curve texels, and builds horizontal and
     * vertical band headers followed by curve-address lists. Empty outlines or empty
     * bounds retain advance metrics but produce a nondrawable glyph. Packed glyph
     * metadata maps em-space coordinates into the selected bands.
     *
     * @param info initialized STB font
     * @param codepoint character to compile
     * @param emScale raw-unit to em multiplier
     * @param curveWriter shared curve-data destination
     * @param bandWriter shared band-data destination
     * @return compiled metrics and GPU lookup metadata
     */
    private static SlugGlyph compileGlyph(STBTTFontinfo info, int codepoint, float emScale, FloatTexelWriter curveWriter, UIntTexelWriter bandWriter) {
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
     * @param span axis extent in em units
     * @param curveCount outline segment count
     * @return band count for one axis
     */
    private static int chooseBandCount(float span, int curveCount) {
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
     * @param curves source quadratic segments
     * @param curveLocations matching packed curve texture addresses
     * @param bandCount number of bands
     * @param min minimum em-space axis coordinate
     * @param max maximum em-space axis coordinate
     * @param horizontal true for Y bands traversed along X, false for X bands
     * @return packed curve-address list per band
     */
    private static int[][] buildBands(List<SlugCurve> curves, int[] curveLocations, int bandCount, float min, float max, boolean horizontal) {
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
     * @param info initialized font information
     * @param codepoint character whose outline is requested
     * @param emScale raw-unit to em multiplier
     * @return compiled segments, possibly empty
     */
    private static List<SlugCurve> loadCurves(STBTTFontinfo info, int codepoint, float emScale) {
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
     * @param x0 start X in em units
     * @param y0 start Y in em units
     * @param x1 end X in em units
     * @param y1 end Y in em units
     */
    private static void addLine(List<SlugCurve> curves, float x0, float y0, float x1, float y1) {
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
     * @param x0 start X
     * @param y0 start Y
     * @param cx control X
     * @param cy control Y
     * @param x1 end X
     * @param y1 end Y
     */
    private static void addQuadratic(List<SlugCurve> curves, float x0, float y0, float cx, float cy, float x1, float y1) {
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
     * @param x0 start X
     * @param y0 start Y
     * @param cx0 first control X
     * @param cy0 first control Y
     * @param cx1 second control X
     * @param cy1 second control Y
     * @param x1 end X
     * @param y1 end Y
     */
    private static void approximateCubic(List<SlugCurve> curves, float x0, float y0, float cx0, float cy0, float cx1, float cy1, float x1, float y1) {
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
     * Creates an owned 4096-wide RGBA16F texture from four-float texels using nearest
     * sampling and edge clamping. Height follows buffer capacity, with at least one
     * row. Preserves the active unit's texture binding and pixel-unpack state.
     *
     * @param buffer complete row-padded curve data
     * @return owned OpenGL texture name
     */
    private static int uploadCurveTexture(FloatBuffer buffer) {
        try (UploadState ignored = new UploadState()) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        int height = Math.max(1, buffer.capacity() / (TEXTURE_WIDTH * 4));
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, TEXTURE_WIDTH, height, 0, GL_RGBA, GL_FLOAT, buffer);
        return texture;
        }
    }

    /**
     * Creates an owned 4096-wide RG32UI texture from two-integer texels, with nearest
     * sampling and edge clamping. Preserves texture and pixel-unpack state.
     *
     * @param buffer complete row-padded band headers and curve locations
     * @return owned OpenGL texture name
     */
    private static int uploadBandTexture(IntBuffer buffer) {
        try (UploadState ignored = new UploadState()) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        int height = Math.max(1, buffer.capacity() / (TEXTURE_WIDTH * 2));
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32UI, TEXTURE_WIDTH, height, 0, GL_RG_INTEGER, GL_UNSIGNED_INT, buffer);
        return texture;
        }
    }

    /**
     * Scopes texture-upload state on the current GL context. Construction disables the
     * pixel-unpack buffer and establishes tightly packed rows; close restores the previous
     * texture binding and unpack settings even when upload exits exceptionally.
     * Instances belong to one context-thread operation and must be closed on that thread.
     * @author Albert Beaupre
     */
    private static final class UploadState implements AutoCloseable {
        private final int texture = glGetInteger(GL_TEXTURE_BINDING_2D); // Texture binding captured on the active unit before upload.
        private final int pbo = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING); // Pixel-unpack buffer binding restored after CPU uploads.
        private final int alignment = glGetInteger(GL_UNPACK_ALIGNMENT); // Saved unpack row alignment in bytes.
        private final int rowLength = glGetInteger(GL_UNPACK_ROW_LENGTH); // Saved unpack row-length override.
        private final int skipRows = glGetInteger(GL_UNPACK_SKIP_ROWS); // Saved unpack row skip count.
        private final int skipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS); // Saved unpack pixel skip count.
        private final int swapBytes = glGetInteger(GL_UNPACK_SWAP_BYTES); // Saved unpack byte-swap flag.
        /**
         * Captures texture and pixel-unpack state, then selects CPU-buffer uploads with
         * four-byte alignment and no row skips, row-length override, or byte swapping.
         */
        UploadState() {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SWAP_BYTES, 0);
        }
        /**
         * Restores the captured texture binding, pixel-unpack buffer, alignment, row layout,
         * skip offsets, and byte-swap flag on the same context used during construction.
         */
        @Override public void close() {
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
            glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
            glPixelStorei(GL_UNPACK_SWAP_BYTES, swapBytes);
        }
    }

    /**
     * Packs texel coordinates into low and high 16-bit halves without range checks.
     *
     * @param x horizontal coordinate stored in the low half
     * @param y vertical coordinate stored in the high half
     * @return packed texture address
     */
    static int packLocation(int x, int y) {
        return (y << 16) | (x & 0xFFFF);
    }

    /**
     * Packs zero-based band maxima and the optional even-odd fill flag for the shader.
     * Only eight bits of each band maximum are retained.
     *
     * @param bandMaxX last vertical-band index
     * @param bandMaxY last horizontal-band index
     * @param evenOdd whether to set the even-odd fill bit
     * @return packed glyph metadata
     */
    private static int packGlyphInfo(int bandMaxX, int bandMaxY, boolean evenOdd) {
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
        return packed & 0xFFFF;
    }

    /**
     * Extracts the unsigned high-half texel coordinate.
     *
     * @param packed packed texture address
     * @return Y from 0 through 65535
     */
    private static int unpackY(int packed) {
        return (packed >>> 16) & 0xFFFF;
    }

    /**
     * Clamps an integer to inclusive limits, assuming min does not exceed max.
     *
     * @param value candidate value
     * @param min lower limit
     * @param max upper limit
     * @return bounded value
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Looks up a compiled character without substituting another glyph.
     *
     * @param codepoint requested character value
     * @return compiled entry, or null outside the configured range
     */
    SlugGlyph glyph(int codepoint) {
        int index = codepoint - firstCodepoint;
        if (index < 0 || index >= characterCount) {
            return null;
        }
        return glyphs[index];
    }

    /**
     * Returns the owned curve texture name for batch binding.
     *
     * @return texture name, or zero after disposal
     */
    int curveTexture() {
        return curveTexture;
    }

    /**
     * Returns the owned band-data texture name for batch binding.
     *
     * @return texture name, or zero after disposal
     */
    int bandTexture() {
        return bandTexture;
    }

    /**
     * Returns an em-space pair adjustment from the dense table when both characters
     * are compiled, otherwise queries the retained STB font information.
     *
     * @param previousCodepoint preceding character
     * @param codepoint current character
     * @return pair advance adjustment in em units
     */
    float kerning(int previousCodepoint, int codepoint) {
        int previousIndex = previousCodepoint - firstCodepoint;
        int index = codepoint - firstCodepoint;
        if (previousIndex < 0 || previousIndex >= characterCount || index < 0 || index >= characterCount) {
            return stbtt_GetCodepointKernAdvance(info, previousCodepoint, codepoint) * emScale;
        }
        return kerning[previousIndex * characterCount + index];
    }

    /**
     * Creates a reusable layout tied to this font, avoiding repeated glyph and kerning
     * layout during drawing. Keep the font's GPU resources alive while drawing the run.
     *
     * @param text text to lay out
     * @param size world units per em
     * @return new reusable run
     */
    public SlugTextRun createRun(String text, float size) {
        return new SlugTextRun(this, text, size);
    }

    /**
     * Appends drawable glyphs at a baseline origin, applying kerning and advances.
     * Newlines reset X and move the baseline down one line height; tabs advance four
     * spaces and reset kerning. Uncompiled UTF-16 characters advance a quarter em.
     * Null/empty text or zero size emits nothing. The batch manages actual submission.
     *
     * @param batch nonnull destination batch
     * @param text text to draw
     * @param x baseline origin X
     * @param y baseline origin Y
     * @param size world units per em; not validated here
     * @param color copied draw tint, or null for white
     * @throws NullPointerException if batch is null
     */
    public void draw(SlugBatch batch, String text, float x, float y, float size, Color color) {
        if (batch == null) throw new NullPointerException("batch");
        if (text == null || text.isEmpty() || size == 0f) return;

        Color tint = color == null ? Color.WHITE : color;
        float penX = x;
        float penY = y;
        int previous = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                penX = x;
                penY -= lineHeight * size;
                previous = 0;
                continue;
            }

            if (c == '\t') {
                SlugGlyph space = glyph(' ');
                float tab = (space != null ? space.advance : fallbackAdvance) * 4f * size;
                penX += tab;
                previous = 0;
                continue;
            }

            SlugGlyph glyph = glyph(c);
            if (glyph == null) {
                penX += fallbackAdvance * size;
                previous = 0;
                continue;
            }

            if (previous != 0) {
                penX += kerning(previous, c) * size;
            }

            if (glyph.drawable) {
                batch.drawGlyph(this, glyph, penX, penY, size, tint);
            }

            penX += glyph.advance * size;
            previous = c;
        }
    }

    /**
     * Appends the retained text using its current baseline, size, and mutable tint.
     *
     * @param batch nonnull destination batch
     * @throws NullPointerException if batch is null
     */
    public void draw(SlugBatch batch) {
        if (retainedRun == null) retainedRun = createRun(text, size);
        else retainedRun.rebuild(text, size);
        retainedRun.draw(batch, x, y, color);
    }

    /**
     * Replaces retained text, normalizing null to empty, and recalculates measured
     * width and height at the retained font size.
     *
     * @param text new retained text
     * @return this font
     */
    public SlugFont setText(String text) {
        this.text = text == null ? "" : text;
        recalcSize();
        return this;
    }

    /**
     * Sets the retained baseline origin without changing layout or measurements.
     *
     * @param x baseline X
     * @param y baseline Y
     */
    @Override
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the retained baseline X coordinate.
     *
     * @return baseline X
     */
    @Override
    public float getX() {
        return x;
    }

    /**
     * Sets retained baseline X without changing measured width.
     *
     * @param x baseline X
     */
    @Override
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Returns the retained baseline Y coordinate.
     *
     * @return baseline Y
     */
    @Override
    public float getY() {
        return y;
    }

    /**
     * Sets retained baseline Y without changing measured height.
     *
     * @param y baseline Y
     */
    @Override
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Sets retained world units per em and recomputes measured dimensions.
     * The value is stored without sign or finiteness validation.
     *
     * @param size new font scale
     * @return this font
     */
    public SlugFont setSize(float size) {
        this.size = size;
        recalcSize();
        return this;
    }

    /**
     * Replaces cached retained dimensions with current text measurements. Overrides
     * any width/height values previously set through the Dimensional interface.
     */
    private void recalcSize() {
        width = getWidth(text, size);
        height = getHeight(text, size);
    }

    /**
     * Measures the maximum line advance using the same UTF-16 kerning, tab, and
     * fallback rules as draw. This measures pen advances rather than tight ink bounds.
     * Does not change retained state; null or empty text returns zero.
     *
     * @param text text to measure
     * @param size world units per em
     * @return maximum line advance in world units
     */
    public float getWidth(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }

        float lineWidth = 0f;
        float maxWidth = 0f;
        int previous = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                maxWidth = Math.max(maxWidth, lineWidth);
                lineWidth = 0f;
                previous = 0;
                continue;
            }
            if (c == '\t') {
                SlugGlyph space = glyph(' ');
                lineWidth += (space != null ? space.advance : fallbackAdvance) * 4f * size;
                previous = 0;
                continue;
            }
            SlugGlyph glyph = glyph(c);
            if (glyph == null) {
                lineWidth += fallbackAdvance * size;
                previous = 0;
                continue;
            }
            if (previous != 0) {
                lineWidth += kerning(previous, c) * size;
            }
            lineWidth += glyph.advance * size;
            previous = c;
        }

        return Math.max(maxWidth, lineWidth);
    }

    /**
     * Measures line-box height as newline count plus one times font line height and
     * size. A trailing newline adds an empty line; null or empty text returns zero.
     * This is not a tight glyph-ink measurement.
     *
     * @param text text to measure
     * @param size world units per em
     * @return total line-box height
     */
    public float getHeight(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines * lineHeight * size;
    }

    /**
     * Returns cached retained width, either measured or explicitly overridden.
     *
     * @return retained width metadata
     */
    @Override
    public float getWidth() {
        return width;
    }

    /**
     * Overrides cached width metadata without scaling glyphs or changing font size.
     * A later text or single-argument size change recomputes it.
     *
     * @param width width metadata to store
     */
    @Override
    public void setWidth(float width) {
        this.width = width;
    }

    /**
     * Returns cached retained height, either measured or explicitly overridden.
     *
     * @return retained height metadata
     */
    @Override
    public float getHeight() {
        return height;
    }

    /**
     * Overrides cached height metadata without scaling glyphs or changing line spacing.
     * A later text or single-argument size change recomputes it.
     *
     * @param height height metadata to store
     */
    @Override
    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Overrides both cached dimensions without changing the font scale used to draw.
     * Use the single-argument font-size setter to resize glyphs and recalculate bounds.
     *
     * @param width width metadata
     * @param height height metadata
     */
    @Override
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Returns the retained drawing scale, independent of overridden width/height metadata.
     *
     * @return world units per em
     */
    public float getFontSize() {
        return size;
    }

    /**
     * Returns the live retained tint. Direct mutations affect subsequent retained draws.
     *
     * @return mutable font-owned color
     */
    public Color getColor() {
        return color;
    }

    /**
     * Copies a supplied color into the retained tint, leaving it unchanged for null.
     *
     * @param color tint to copy, or null
     * @return this font
     */
    public SlugFont setColor(Color color) {
        if (color != null) {
            this.color.set(color);
        }
        return this;
    }

    /**
     * Returns STB ascent converted from font units to em units.
     *
     * @return baseline-to-ascent metric in em units
     */
    public float ascent() {
        return ascent;
    }

    /**
     * Returns STB descent in em units, normally a negative baseline-relative value.
     *
     * @return descent metric in em units
     */
    public float descent() {
        return descent;
    }

    /**
     * Returns ascent minus descent plus line gap, used for baseline spacing.
     *
     * @return line height in em units
     */
    public float lineHeight() {
        return lineHeight;
    }

    /**
     * Deletes owned curve and band textures with the GL context current, replacing
     * their IDs with zero. Repeated calls are harmless. Retains CPU glyph, kerning,
     * and font-byte data, so measurement still works while drawing is no longer valid.
     */
    public void dispose() {
        if (curveTexture != 0) {
            glDeleteTextures(curveTexture);
            curveTexture = 0;
        }
        if (bandTexture != 0) {
            glDeleteTextures(bandTexture);
            bandTexture = 0;
        }
    }

    /**
     * CPU staging writer for four-float curve texels. Each quadratic occupies two
     * adjacent texels, with row-end padding to keep the pair on the same row.
     * Address packing assumes the production width of 4096.
     * @author Albert Beaupre
     */
    private static final class FloatTexelWriter {
        private final int width; // Texture row width in texels.
        private float[] values = new float[4096]; // Growable primitive component storage for staged texels.
        private int count; // Number of populated primitive components in staging storage.

        /**
         * Creates empty curve staging storage.
         *
         * @param width row width; address packing requires 4096
         */
        FloatTexelWriter(int width) {
            this.width = width;
        }

        /**
         * Appends two texels containing a quadratic's three control points. Pads the
         * last texel of a row when necessary so the two-texel record stays together.
         *
         * @param curve quadratic control points
         * @return packed address of the first texel
         */
        int writeCurve(SlugCurve curve) {
            int texel = count / 4;
            if ((texel & (width - 1)) == width - 1) {
                write(0f, 0f, 0f, 0f);
                texel++;
            }

            int x = texel & (width - 1);
            int y = texel >> 12;
            write(curve.p1x(), curve.p1y(), curve.p2x(), curve.p2y());
            write(curve.p3x(), curve.p3y(), 0f, 0f);
            return packLocation(x, y);
        }

        /**
         * Appends one four-component texel to curve staging storage.
         *
         * @param r first component
         * @param g second component
         * @param b third component
         * @param a fourth component
         */
        void write(float r, float g, float b, float a) {
            if (count + 4 > values.length) values = java.util.Arrays.copyOf(values, values.length * 2);
            values[count++] = r; values[count++] = g; values[count++] = b; values[count++] = a;
        }

        /**
         * Computes the complete row count needed for staged texels, with at least one row.
         *
         * @return required texture height
         */
        int height() {
            int texels = Math.max(1, (count + 3) / 4);
            return Math.max(1, (texels + width - 1) / width);
        }

        /**
         * Copies staged floats into a direct buffer padded with zeros to the requested
         * row count, then flips it for upload.
         *
         * @param height row count at least the required height
         * @return upload-ready RGBA float buffer
         */
        FloatBuffer toBuffer(int height) {
            int capacity = width * height * 4;
            FloatBuffer buffer = BufferUtils.createFloatBuffer(capacity);
            buffer.put(values, 0, count);
            while (buffer.position() < capacity) {
                buffer.put(0f);
            }
            buffer.flip();
            return buffer;
        }
    }

    /**
     * CPU staging writer for two-integer band headers and curve-address texels.
     * Tracks linear texel offsets before padding rows for an RG32UI upload.
     * <p>The writer owns CPU staging values only. Generated integer buffers feed band-texture
     * uploads; texture creation and disposal belong to SlugFont rather than this helper.</p>
     *
     * @author Albert Beaupre
     */
    private static final class UIntTexelWriter {
        private final int width; // Texture row width in texels.
        private int[] values = new int[4096]; // Growable primitive component storage for staged texels.
        private int count; // Number of populated primitive components in staging storage.

        /**
         * Creates empty integer band staging storage.
         *
         * @param width number of texels per row
         */
        UIntTexelWriter(int width) {
            this.width = width;
        }

        /**
         * Returns the next linear texel index before final row padding.
         *
         * @return number of two-component texels already staged
         */
        int position() {
            return count / 2;
        }

        /**
         * Appends one two-integer band header or curve-address texel.
         *
         * @param r first unsigned-integer bit pattern
         * @param g second unsigned-integer bit pattern
         */
        void write(int r, int g) {
            if (count + 2 > values.length) values = java.util.Arrays.copyOf(values, values.length * 2);
            values[count++] = r; values[count++] = g;
        }

        /**
         * Computes the rows required for staged two-integer texels, with a minimum
         * of one row even when no band data has been written.
         *
         * @return required texture height
         */
        int height() {
            int texels = Math.max(1, (count + 1) / 2);
            return Math.max(1, (texels + width - 1) / width);
        }

        /**
         * Copies staged integers into a direct buffer padded with zeros to the requested
         * row count, then flips it for upload.
         *
         * @param height row count at least the required height
         * @return upload-ready RG integer buffer
         */
        IntBuffer toBuffer(int height) {
            int capacity = width * height * 2;
            IntBuffer buffer = BufferUtils.createIntBuffer(capacity);
            buffer.put(values, 0, count);
            while (buffer.position() < capacity) {
                buffer.put(0);
            }
            buffer.flip();
            return buffer;
        }
    }
}
