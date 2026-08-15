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
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * GPU outline font based on Eric Lengyel's public Slug shader reference.
 *
 * <p>This version is optimized for live rendering compared to the original integration:</p>
 * <ul>
 *     <li>glyphs are compiled into real horizontal and vertical bands, not one giant band</li>
 *     <li>kerning is precomputed into a dense table for the configured codepoint range</li>
 *     <li>text can be pre-laid out into a {@link SlugTextRun}</li>
 *     <li>the curve texture uses 16-bit floats to reduce bandwidth</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public final class SlugFont implements Dimensional {

    static final int TEXTURE_WIDTH = 4096; // Fixed Slug data texture width used by the shader.

    private static final int MAX_BANDS_PER_AXIS = Integer.getInteger("valthorne.slug.maxBands", 48);
    private static final float BANDS_PER_EM = Float.parseFloat(System.getProperty("valthorne.slug.bandsPerEm", "40"));
    private static final float MIN_CURVE_LENGTH_SQUARED = Float.parseFloat(System.getProperty("valthorne.slug.minCurveLengthSquared", "0.00000025"));
    private static final float QUADRATIC_FLATNESS_SQUARED = Float.parseFloat(System.getProperty("valthorne.slug.quadraticFlatnessSquared", "0.00000009"));
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

    private int curveTexture; // OpenGL texture id storing curve control points.
    private int bandTexture; // OpenGL texture id storing band lookup data.
    private int curveTextureHeight; // Height of the curve texture in texels.
    private int bandTextureHeight; // Height of the band texture in texels.

    private String text = ""; // Optional retained text for Font-like usage.
    private float x; // Optional retained x position for Font-like usage.
    private float y; // Optional retained y position for Font-like usage.
    private float size = 32f; // Optional retained size in world units per em.
    private final Color color = new Color(1f, 1f, 1f, 1f); // Optional retained color for Font-like usage.
    private float width; // Cached measured width of retained text.
    private float height; // Cached measured height of retained text.

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
     * Loads a Slug font from disk using the printable ASCII range.
     *
     * @param path path to a TrueType/OpenType font file
     * @return loaded Slug font
     */
    public static SlugFont load(String path) {
        return load(path, 32, 95);
    }

    /**
     * Loads a Slug font from disk using a configurable contiguous character range.
     *
     * @param path           path to a TrueType/OpenType font file
     * @param firstCodepoint first codepoint to compile
     * @param characterCount number of codepoints to compile
     * @return loaded Slug font
     */
    public static SlugFont load(String path, int firstCodepoint, int characterCount) {
        try {
            return load(Files.readAllBytes(Path.of(path)), firstCodepoint, characterCount);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a Slug font from raw font bytes using a configurable contiguous character range.
     *
     * @param fontBytes      TrueType/OpenType font bytes
     * @param firstCodepoint first codepoint to compile
     * @param characterCount number of codepoints to compile
     * @return loaded Slug font
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

    private static int chooseBandCount(float span, int curveCount) {
        if (span <= 0f || curveCount <= 2) {
            return 1;
        }
        int byCurves = (int) Math.ceil(Math.sqrt(curveCount) * 2.25f);
        int bySpan = (int) Math.ceil(span * BANDS_PER_EM);
        return clamp(Math.max(byCurves, bySpan), 1, MAX_BANDS_PER_AXIS);
    }

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

    private static void addLine(List<SlugCurve> curves, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        if (dx * dx + dy * dy < MIN_CURVE_LENGTH_SQUARED) {
            return;
        }
        curves.add(new SlugCurve(x0, y0, (x0 + x1) * 0.5f, (y0 + y1) * 0.5f, x1, y1));
    }

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

    private static int uploadCurveTexture(FloatBuffer buffer) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        int height = Math.max(1, buffer.capacity() / (TEXTURE_WIDTH * 4));
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, TEXTURE_WIDTH, height, 0, GL_RGBA, GL_FLOAT, buffer);
        glBindTexture(GL_TEXTURE_2D, 0);
        return texture;
    }

    private static int uploadBandTexture(IntBuffer buffer) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        int height = Math.max(1, buffer.capacity() / (TEXTURE_WIDTH * 2));
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32UI, TEXTURE_WIDTH, height, 0, GL_RG_INTEGER, GL_UNSIGNED_INT, buffer);
        glBindTexture(GL_TEXTURE_2D, 0);
        return texture;
    }

    static int packLocation(int x, int y) {
        return (y << 16) | (x & 0xFFFF);
    }

    private static int packGlyphInfo(int bandMaxX, int bandMaxY, boolean evenOdd) {
        int value = (bandMaxX & 0xFF) | ((bandMaxY & 0xFF) << 16);
        if (evenOdd) {
            value |= 0x10000000;
        }
        return value;
    }

    private static int unpackX(int packed) {
        return packed & 0xFFFF;
    }

    private static int unpackY(int packed) {
        return (packed >>> 16) & 0xFFFF;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    SlugGlyph glyph(int codepoint) {
        int index = codepoint - firstCodepoint;
        if (index < 0 || index >= characterCount) {
            return null;
        }
        return glyphs[index];
    }

    int curveTexture() {
        return curveTexture;
    }

    int bandTexture() {
        return bandTexture;
    }

    float kerning(int previousCodepoint, int codepoint) {
        int previousIndex = previousCodepoint - firstCodepoint;
        int index = codepoint - firstCodepoint;
        if (previousIndex < 0 || previousIndex >= characterCount || index < 0 || index >= characterCount) {
            return stbtt_GetCodepointKernAdvance(info, previousCodepoint, codepoint) * emScale;
        }
        return kerning[previousIndex * characterCount + index];
    }

    /**
     * Creates a reusable pre-laid-out text run. This removes per-frame text layout and kerning work.
     *
     * @param text text to layout
     * @param size world units per em
     * @return reusable text run
     */
    public SlugTextRun createRun(String text, float size) {
        return new SlugTextRun(this, text, size);
    }

    /**
     * Draws text using this Slug font.
     *
     * @param batch batch that receives glyph instances
     * @param text  text to draw
     * @param x     baseline x position
     * @param y     baseline y position
     * @param size  world units per em
     * @param color text color
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
     * Draws the retained text at the retained position, size, and color.
     *
     * @param batch batch that receives glyph instances
     */
    public void draw(SlugBatch batch) {
        draw(batch, text, x, y, size, color);
    }

    /**
     * Sets retained text for Font-like usage.
     *
     * @param text text to retain
     * @return this font
     */
    public SlugFont setText(String text) {
        this.text = text == null ? "" : text;
        recalcSize();
        return this;
    }

    @Override
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void setX(float x) {
        this.x = x;
    }

    @Override
    public void setY(float y) {
        this.y = y;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    /**
     * Sets retained size in world units per em.
     *
     * @param size font size
     * @return this font
     */
    public SlugFont setSize(float size) {
        this.size = size;
        recalcSize();
        return this;
    }

    /**
     * Sets retained color.
     *
     * @param color color to copy
     * @return this font
     */
    public SlugFont setColor(Color color) {
        if (color != null) {
            this.color.set(color);
        }
        return this;
    }

    private void recalcSize() {
        width = getWidth(text, size);
        height = getHeight(text, size);
    }

    /**
     * Measures text width without mutating retained state.
     *
     * @param text text to measure
     * @param size world units per em
     * @return measured width
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
     * Measures text height without mutating retained state.
     *
     * @param text text to measure
     * @param size world units per em
     * @return measured height
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

    @Override
    public float getWidth() {
        return width;
    }

    @Override
    public float getHeight() {
        return height;
    }

    @Override
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void setWidth(float width) {
        this.width = width;
    }

    @Override
    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Returns the retained font size in world units per em.
     *
     * @return font size
     */
    public float getFontSize() {
        return size;
    }

    /**
     * Returns the mutable retained color object.
     *
     * @return retained color
     */
    public Color getColor() {
        return color;
    }

    /**
     * Returns the font ascent in em units.
     *
     * @return ascent
     */
    public float ascent() {
        return ascent;
    }

    /**
     * Returns the font descent in em units.
     *
     * @return descent
     */
    public float descent() {
        return descent;
    }

    /**
     * Returns the line height in em units.
     *
     * @return line height
     */
    public float lineHeight() {
        return lineHeight;
    }

    /**
     * Deletes the GPU data textures owned by this font.
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

    private static final class FloatTexelWriter {
        private final int width;
        private final List<Float> values = new ArrayList<>();

        FloatTexelWriter(int width) {
            this.width = width;
        }

        int writeCurve(SlugCurve curve) {
            int texel = values.size() / 4;
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

        void write(float r, float g, float b, float a) {
            values.add(r);
            values.add(g);
            values.add(b);
            values.add(a);
        }

        int height() {
            int texels = Math.max(1, (values.size() + 3) / 4);
            return Math.max(1, (texels + width - 1) / width);
        }

        FloatBuffer toBuffer(int height) {
            int capacity = width * height * 4;
            FloatBuffer buffer = BufferUtils.createFloatBuffer(capacity);
            for (Float value : values) {
                buffer.put(value);
            }
            while (buffer.position() < capacity) {
                buffer.put(0f);
            }
            buffer.flip();
            return buffer;
        }
    }

    private static final class UIntTexelWriter {
        private final int width;
        private final List<Integer> values = new ArrayList<>();

        UIntTexelWriter(int width) {
            this.width = width;
        }

        int position() {
            return values.size() / 2;
        }

        void write(int r, int g) {
            values.add(r);
            values.add(g);
        }

        int height() {
            int texels = Math.max(1, (values.size() + 1) / 2);
            return Math.max(1, (texels + width - 1) / width);
        }

        IntBuffer toBuffer(int height) {
            int capacity = width * height * 2;
            IntBuffer buffer = BufferUtils.createIntBuffer(capacity);
            for (Integer value : values) {
                buffer.put(value);
            }
            while (buffer.position() < capacity) {
                buffer.put(0);
            }
            buffer.flip();
            return buffer;
        }
    }
}
