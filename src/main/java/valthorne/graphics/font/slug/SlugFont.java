package valthorne.graphics.font.slug;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTVertex;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.geometry.Dimensional;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_RG32UI;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL30.GL_RG_INTEGER;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * Owns exact-file HarfBuzz text layouts and live Slug GPU curve geometry. The
 * configured character range is precompiled; OpenType substitution and additional
 * Unicode glyph indices are compiled lazily into append-only curve/band storage.
 * Cubic CFF contours use adaptive bounded-error quadratics. The default constructor
 * uploads RGBA16F curves and RG32UI bands immediately; deferred construction and
 * CPU shaping require no graphics context. New geometry replaces the texture pair
 * together at a later binding while preserving existing glyph addresses.
 * Texture upload, drawing, and disposal of uploaded textures require a current
 * OpenGL context. Confine each font's mutable layout and resource operations to its
 * owning graphics thread.
 * <p>
 * Text is itemized by Unicode bidi direction and script, then shaped by HarfBuzz
 * with a deterministic undetermined-language profile. Ligatures, combining marks,
 * UTF-16 clusters, CR/LF lines, and four-space tabs share one layout for measurement
 * and drawing. Missing characters use this exact face's missing glyph; no system
 * font is substituted. At most 128 layouts and 65,536 source characters are cached.
 * Immediate draw runs use a separately bounded cache; createRun provides an
 * independent retained layout for caller-managed or oversized text.
 * </p>
 * <p>
 * World text uses scalable unhinted geometry. createHinted creates a disposable UI
 * view sharing the source's bytes and normalized logical layout, while FreeType
 * supplies physical-size hinted outlines. UI roots retain these views and apply a
 * fixed portable grayscale response; no bitmap atlas replaces Slug's curve shader.
 * FreeType initializes only when hinting is requested. Dispose GPU resources with
 * the context current after drawing completes; hinted views must not outlive their
 * borrowed source. Native shaping/hinting resources are released on disposal.
 * CPU-only measurement remains available, using a temporary native owner on a
 * disposed font's layout cache miss. Optional retained position, size, and color
 * support Dimensional usage; explicit width/height setters only override measured
 * metadata and do not scale rendered glyphs.
 * </p>
 * <p>
 * Draw directly into an active TextureBatch for live curve coverage at any zoom.
 * That batch owns the glyph renderer and preserves mixed sprite/text order; callers
 * only manage their existing TextureBatch. No bitmap atlases are baked or retained.
 * Reusable SlugTextRun layouts avoid repeated layout work while retaining live curves.
 * </p>
 * <pre>{@code
 * SlugFont font = SlugData.load("assets/font.ttf").asFont();
 * try {
 *     font.setText("Hello").setSize(32);
 *     font.setPosition(20, 60);
 *     font.draw(batch); // Inside the caller's TextureBatch drawing lifecycle.
 * } finally {
 *     font.dispose();
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public final class SlugFont implements Dimensional {

    /**
     * Fixed data-texture row width. Shader address packing and CPU row shifts assume 4096.
     */
    static final int TEXTURE_WIDTH = 4096;

    /**
     * Band-count cap read once from valthorne.slug.maxBands; defaults to 48.
     */
    private static final int MAX_BANDS_PER_AXIS = Integer.getInteger("valthorne.slug.maxBands", 48);
    /**
     * Em-span band density read once from valthorne.slug.bandsPerEm; defaults to 40.
     */
    private static final float BANDS_PER_EM = Float.parseFloat(System.getProperty("valthorne.slug.bandsPerEm", "40"));
    /**
     * Maximum retained shaped strings per source font.
     */
    private static final int MAX_SHAPES = 128;
    /**
     * Maximum UTF-16 characters retained across the source font's shape cache.
     */
    private static final int MAX_SHAPE_CHARACTERS = 65536;
    /**
     * Em-space expansion on each band edge to retain near-boundary curves.
     */
    private static final float BAND_EPSILON = 0.00005f;
    private SlugTextRun retainedRun; // Reusable convenience-draw layout; separate runs are required for independent retained text.

    private final STBTTFontinfo info; // STB font info kept alive for fallback queries.
    private final ByteBuffer fontBuffer; // Backing font bytes kept alive for STB font info.
    private final SlugGlyph[] glyphs; // Glyph lookup table for the configured character range.
    private final float[] kerning; // Pair cache in em units; NaN marks an unqueried pair.
    private final int firstCodepoint; // First codepoint included in the lookup table.
    private final int characterCount; // Number of codepoints included in the lookup table.
    private final float emScale; // Raw font unit to em-space scale.
    private final float ascent; // Font ascent in em units.
    private final float descent; // Font descent in em units.
    private final float lineHeight; // Line height in em units.
    private final SlugFont sourceFont; // Borrowed source for a hinted view, or null for an independently owned font.
    private final float pixelsPerEm; // Physical hinted em size; zero keeps scalable unhinted world outlines.
    private final SlugCurveBuffer curveWriter; // Append-only geometry staging preserves addresses of retained glyphs.
    private final SlugBandBuffer bandWriter; // Append-only band staging shared by all compiled glyph indices.
    private final SlugGlyph[][] indexedGlyphs; // Lazily allocated 256-glyph pages support substitutions without large sparse arrays.
    private final LinkedHashMap<String, SlugShape> shapes = new LinkedHashMap<>(16, .75f, true); // Bounded retained normalized text layouts.
    private final LinkedHashMap<String, SlugTextRun> cachedRuns = new LinkedHashMap<>(16, .75f, true); // Bounded prepared geometry/layouts for immediate drawing.
    private SlugNativeFace nativeFace; // Lazily owned shaping/hinting face, shared by borrowed hinted views.
    private int shapedCharacters; // UTF-16 character count retained in the shape cache.
    private int runCharacters; // UTF-16 character count retained by prepared immediate runs.
    private boolean geometryDirty; // Whether appended glyphs require a replacement GPU texture pair.
    private final Color color = new Color(1f, 1f, 1f, 1f); // Optional retained color for Font-like usage.
    private int curveTexture; // OpenGL texture id storing curve control points.
    private int bandTexture; // OpenGL texture id storing band lookup data.
    private FloatBuffer pendingCurves; // Curve texels retained until their first GPU upload.
    private IntBuffer pendingBands; // Band texels retained until their first GPU upload.
    private boolean disposed; // Explicit lifetime state, independent of deferred upload.
    private String text = ""; // Optional retained text for Font-like usage.
    private float x; // Optional retained x position for Font-like usage.
    private float y; // Optional retained y position for Font-like usage.
    private float size = 32f; // Optional retained size in world units per em.
    private float width; // Cached measured width of retained text.
    private float height; // Cached measured height of retained text.

    /**
     * Creates a font from immutable encoded data on the thread owning the current
     * OpenGL context. Copies bytes into retained direct storage, initializes STB,
     * compiles glyphs, initializes the pair cache, and uploads two owned data textures. The data
     * remains reusable. Texture creation preserves texture and pixel-unpack state.
     *
     * @param data immutable encoded font and character-range metadata
     * @throws NullPointerException if data is null
     * @throws RuntimeException if STB cannot initialize the font
     */
    public SlugFont(SlugData data) {
        this(data, false);
    }

    /**
     * Copies the exact encoded source for an independently owned native UI face.
     * This cold-path operation performs no OpenGL calls and never exposes mutable
     * STB backing storage. Existing outline drawing and ownership remain independent.
     *
     * @return independent TrueType/OpenType source bytes
     * @throws IllegalStateException if the font has been disposed
     */
    public byte[] copyEncodedData() {
        if (isDisposed()) throw new IllegalStateException("Font is disposed");
        ByteBuffer source = fontBuffer.asReadOnlyBuffer();
        source.position(0);
        byte[] result = new byte[source.remaining()];
        source.get(result);
        return result;
    }

    /**
     * Compiles outlines and metrics from reusable encoded font data. When upload is
     * deferred, construction and measurement perform no OpenGL calls; the first
     * {@link #bindTextures()} call uploads the prepared data on the graphics thread.
     * Disposing an unused deferred font releases its staging data without GL calls.
     * Immediate upload preserves texture and pixel-unpack state.
     *
     * @param data immutable encoded font and character-range metadata
     * @param deferUpload whether to delay texture creation until first binding
     * @throws NullPointerException if data is null
     * @throws RuntimeException if STB cannot initialize the font or upload fails
     */
    public SlugFont(SlugData data, boolean deferUpload) {
        /*
         * Retain only outline textures and the STB backing bytes. TextureBatch
         * evaluates these same outlines directly, so no bitmap atlas is needed.
         */
        if (data == null) throw new NullPointerException("data");
        byte[] fontBytes = data.bytes();
        int firstCodepoint = data.firstCodepoint();
        int characterCount = data.characterCount();

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

        SlugCurveBuffer curveWriter = new SlugCurveBuffer(TEXTURE_WIDTH);
        SlugBandBuffer bandWriter = new SlugBandBuffer(TEXTURE_WIDTH);
        SlugGlyph[] glyphs = new SlugGlyph[characterCount];
        SlugGlyph[][] indexedGlyphs = new SlugGlyph[256][];

        for (int i = 0; i < characterCount; i++) {
            int codepoint = firstCodepoint + i;
            int glyphIndex = stbtt_FindGlyphIndex(info, codepoint);
            glyphs[i] = compileGlyph(info, glyphIndex, codepoint, emScale, loadCurves(info, glyphIndex, emScale), curveWriter, bandWriter);
            int page = glyphIndex >>> 8;
            if (indexedGlyphs[page] == null) indexedGlyphs[page] = new SlugGlyph[256];
            indexedGlyphs[page][glyphIndex & 255] = glyphs[i];
        }

        float[] kerning = createKerningCache(characterCount);

        int curveHeight = Math.max(1, curveWriter.height());
        int bandHeight = Math.max(1, bandWriter.height());

        this.info = info;
        this.fontBuffer = fontBuffer;
        this.glyphs = glyphs;
        this.kerning = kerning;
        this.firstCodepoint = firstCodepoint;
        this.characterCount = characterCount;
        this.emScale = emScale;
        this.ascent = ascent;
        this.descent = descent;
        this.lineHeight = ascent - descent + lineGap;
        this.sourceFont = null;
        this.pixelsPerEm = 0;
        this.curveWriter = curveWriter;
        this.bandWriter = bandWriter;
        this.indexedGlyphs = indexedGlyphs;
        this.pendingCurves = curveWriter.toBuffer(curveHeight);
        this.pendingBands = bandWriter.toBuffer(bandHeight);
        if (!deferUpload) ensureTextures();
        recalcSize();
    }

    /**
     * Creates a GPU-independent size-hinted view borrowing immutable source bytes,
     * logical metrics, and shaping. Only this view's curve/band geometry is owned.
     *
     * @param source live source font that outlives this view
     * @param pixelsPerEm quantized physical em size
     */
    private SlugFont(SlugFont source, float pixelsPerEm) {
        sourceFont = source;
        this.pixelsPerEm = pixelsPerEm;
        info = source.info;
        fontBuffer = source.fontBuffer;
        firstCodepoint = source.firstCodepoint;
        characterCount = source.characterCount;
        emScale = source.emScale;
        ascent = source.ascent;
        descent = source.descent;
        lineHeight = source.lineHeight;
        kerning = source.kerning;
        glyphs = new SlugGlyph[characterCount];
        indexedGlyphs = new SlugGlyph[source.indexedGlyphs.length][];
        curveWriter = new SlugCurveBuffer(TEXTURE_WIDTH);
        bandWriter = new SlugBandBuffer(TEXTURE_WIDTH);
        geometryDirty = true;
    }

    /**
     * Creates an independently disposable hinted outline view for axis-aligned UI.
     * The source must outlive the view. Run advances remain unhinted and share the
     * source's shaped layout, so DPI changes do not alter logical text placement.
     * Cache views by physical em size and release them with the UI resource owner.
     *
     * @param physicalSize finite physical pixels per em from 1 through 4096
     * @return owned view whose geometry is generated lazily
     */
    public SlugFont createHinted(float physicalSize) {
        if (isDisposed()) throw new IllegalStateException("Slug font is disposed");
        if (!Float.isFinite(physicalSize) || physicalSize < 1 || physicalSize > 4096) throw new IllegalArgumentException("Invalid physical font size");
        return new SlugFont(sourceFont == null ? this : sourceFont, Math.round(physicalSize * 64) / 64f);
    }

    /**
     * Reports whether this view uses size-specific hinted geometry and display-space UI blending.
     *
     * @return true for a hinted UI view
     */
    public boolean isHinted() { return pixelsPerEm != 0; }

    /**
     * Estimates complete row-padded GPU curve/band storage without querying OpenGL.
     * CPU staging and retained text metadata are additional owned storage. UI owners
     * use this value to bound cached hinted views as new glyphs are appended.
     *
     * @return bytes required by the current RGBA16F and RG32UI texture pair
     */
    public long geometryBytes() { return (long) TEXTURE_WIDTH * (curveWriter.height() + bandWriter.height()) * 8; }

    /**
     * Borrows a retained HarfBuzz layout over the exact supplied font. Normalized
     * positions are shared by logical measurement, raw world outlines, and hinted
     * UI views. Cache misses after disposal use a temporary CPU-only native owner,
     * preserving the existing ability to measure disposed fonts without retaining
     * native shaping resources indefinitely. No OpenGL calls occur here.
     *
     * @param text nonnull complete UTF-16 text
     * @return immutable em-space layout
     */
    public SlugShape shape(String text) {
        if (text == null) throw new NullPointerException("text");
        if (sourceFont != null) return sourceFont.shape(text);
        SlugShape cached = shapes.get(text);
        if (cached != null) return cached;
        SlugShape result;
        if (disposed) {
            try (SlugNativeFace temporary = new SlugNativeFace(fontBuffer)) {
                result = new SlugShapeBuilder(temporary, text, lineHeight).build();
            }
        } else {
            result = new SlugShapeBuilder(text.isEmpty() ? null : nativeFace(), text, lineHeight).build();
        }
        if (text.length() <= MAX_SHAPE_CHARACTERS) {
            shapes.put(text, result);
            shapedCharacters += text.length();
            while (shapes.size() > MAX_SHAPES || shapedCharacters > MAX_SHAPE_CHARACTERS)
                shapedCharacters -= shapes.pollFirstEntry().getKey().length();
        }
        return result;
    }

    /**
     * Borrows a prepared immediate-draw run, avoiding repeated shaping, geometry
     * lookup, and temporary glyph arrays on stable frames. The borrowed run may be
     * rebuilt by a later call with another size; use createRun for independently
     * retained or caller-modified layouts. Cache limits match the shaped text cache.
     *
     * @param text nonnull UTF-16 text
     * @param size finite nonnegative world units per em
     * @return borrowed prepared run for immediate serialized submission
     */
    public SlugTextRun cachedRun(String text, float size) {
        if (isDisposed()) throw new IllegalStateException("Slug font is disposed");
        if (text == null) throw new NullPointerException("text");
        SlugTextRun result = cachedRuns.get(text);
        if (result != null) {
            result.rebuild(text, size);
            return result;
        }
        result = createRun(text, size);
        if (text.length() <= MAX_SHAPE_CHARACTERS) {
            cachedRuns.put(text, result);
            runCharacters += text.length();
            while (cachedRuns.size() > MAX_SHAPES || runCharacters > MAX_SHAPE_CHARACTERS)
                runCharacters -= cachedRuns.pollFirstEntry().getKey().length();
        }
        return result;
    }

    /**
     * Borrows the source's lazy native shaping and hinting owner.
     *
     * @return live exact-file native face
     */
    private SlugNativeFace nativeFace() {
        if (sourceFont != null) return sourceFont.nativeFace();
        if (disposed) throw new IllegalStateException("Slug font is disposed");
        if (nativeFace == null) nativeFace = new SlugNativeFace(fontBuffer);
        return nativeFace;
    }

    /**
     * Resolves an actual shaped glyph index, compiling geometry only on a miss.
     * Append-only texture addresses keep previously retained text runs valid.
     *
     * @param glyphIndex glyph index from this exact font's shaped layout
     * @return immutable geometry metadata, including nondrawable whitespace
     */
    public SlugGlyph glyphByIndex(int glyphIndex) {
        if (disposed || sourceFont != null && sourceFont.disposed) throw new IllegalStateException("Slug font is disposed");
        if (glyphIndex < 0 || glyphIndex > 65535) throw new IllegalArgumentException("Invalid font glyph index");
        int pageIndex = glyphIndex >>> 8;
        SlugGlyph[] page = indexedGlyphs[pageIndex];
        if (page == null) indexedGlyphs[pageIndex] = page = new SlugGlyph[256];
        SlugGlyph result = page[glyphIndex & 255];
        if (result != null) return result;
        if (glyphIndex >= nativeFace().glyphCount()) throw new IllegalArgumentException("Glyph index exceeds this font's glyph count");
        List<SlugCurve> curves = pixelsPerEm == 0 ? loadCurves(info, glyphIndex, emScale) : nativeFace().hintedCurves(glyphIndex, pixelsPerEm);
        result = compileGlyph(info, glyphIndex, -1, emScale, curves, curveWriter, bandWriter);
        page[glyphIndex & 255] = result;
        geometryDirty = true;
        return result;
    }

    /**
     * Submits live outlines through an active texture batch. Camera zoom affects
     * curve evaluation at the actual framebuffer resolution, without raster caches.
     * The batch supplies clipping, translation, opacity and submission ordering.
     *
     * @param batch active destination batch
     * @param text UTF-16 text; null or empty submits nothing
     * @param x baseline X before batch translation
     * @param y baseline Y before batch translation
     * @param size finite nonnegative world units per em
     * @param tint copied tint, or null for the batch color
     */
    public void draw(TextureBatch batch, String text, float x, float y, float size, Color tint) {
        if (batch == null) throw new NullPointerException("batch");
        batch.draw(this, text, x, y, size, tint);
    }

    /**
     * Submits retained text with its configured baseline, size and tint. The CPU
     * layout is reused until text or size changes; glyph coverage remains live.
     *
     * @param batch active destination texture batch
     */
    public void draw(TextureBatch batch) {
        if (retainedRun == null) retainedRun = createRun(text, size);
        else retainedRun.rebuild(text, size);
        retainedRun.draw(batch, x, y, color);
    }

    /**
     * Allocates a pair cache without querying STB. Rows index the previous character
     * and columns index the current character; NaN distinguishes missing entries
     * from cached zero adjustments.
     *
     * @param characterCount row and column count
     * @return characterCount-squared cache filled with missing-entry sentinels
     */
    private static float[] createKerningCache(int characterCount) {
        /*
         * Reserve direct lookup slots while avoiding native queries for unused pairs.
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
     * @param glyphIndex actual shaped font glyph index
     * @param codepoint representative source character, or -1 for glyph-index-only geometry
     * @param emScale     raw-unit to em multiplier
     * @param curves normalized raw or hinted outline curves
     * @param curveWriter shared curve-data destination
     * @param bandWriter  shared band-data destination
     * @return compiled metrics and GPU lookup metadata
     */
    private static SlugGlyph compileGlyph(STBTTFontinfo info, int glyphIndex, int codepoint, float emScale, List<SlugCurve> curves, SlugCurveBuffer curveWriter, SlugBandBuffer bandWriter) {
        /*
         * Append each outline to shared texture staging and retain only immutable draw metadata.
         */
        IntBuffer advanceRaw = BufferUtils.createIntBuffer(1);
        IntBuffer lsbRaw = BufferUtils.createIntBuffer(1);
        stbtt_GetGlyphHMetrics(info, glyphIndex, advanceRaw, lsbRaw);

        float advance = advanceRaw.get(0) * emScale;
        float lsb = lsbRaw.get(0) * emScale;

        float x0 = 0;
        float y0 = 0;
        float x1 = 0;
        float y1 = 0;
        if (!curves.isEmpty()) {
            x0 = y0 = Float.POSITIVE_INFINITY;
            x1 = y1 = Float.NEGATIVE_INFINITY;
            for (SlugCurve curve : curves) {
                x0 = Math.min(x0, curve.minX());
                y0 = Math.min(y0, curve.minY());
                x1 = Math.max(x1, curve.maxX());
                y1 = Math.max(y1, curve.maxY());
            }
        }
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
     * @param glyphIndex actual font glyph index whose outline is requested
     * @param emScale   raw-unit to em multiplier
     * @return compiled segments, possibly empty
     */
    private static List<SlugCurve> loadCurves(STBTTFontinfo info, int glyphIndex, float emScale) {
        /*
         * Normalize outline commands to quadratic segments while retaining contour closure.
         */
        List<SlugCurve> curves = new ArrayList<>();
        STBTTVertex.Buffer vertices = stbtt_GetGlyphShape(info, glyphIndex);
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
                        SlugCurves.line(curves, currentX, currentY, startX, startY);
                    }
                    startX = v.x() * emScale;
                    startY = v.y() * emScale;
                    currentX = startX;
                    currentY = startY;
                    contourOpen = true;
                } else if (type == STBTT_vline) {
                    float x = v.x() * emScale;
                    float y = v.y() * emScale;
                    SlugCurves.line(curves, currentX, currentY, x, y);
                    currentX = x;
                    currentY = y;
                } else if (type == STBTT_vcurve) {
                    float cx = v.cx() * emScale;
                    float cy = v.cy() * emScale;
                    float x = v.x() * emScale;
                    float y = v.y() * emScale;
                    SlugCurves.quadratic(curves, currentX, currentY, cx, cy, x, y);
                    currentX = x;
                    currentY = y;
                } else if (type == STBTT_vcubic) {
                    float cx0 = v.cx() * emScale;
                    float cy0 = v.cy() * emScale;
                    float cx1 = v.cx1() * emScale;
                    float cy1 = v.cy1() * emScale;
                    float x = v.x() * emScale;
                    float y = v.y() * emScale;
                    SlugCurves.cubic(curves, currentX, currentY, cx0, cy0, cx1, cy1, x, y);
                    currentX = x;
                    currentY = y;
                }
            }

            if (contourOpen) {
                SlugCurves.line(curves, currentX, currentY, startX, startY);
            }
        } finally {
            // CFF fonts can allocate a non-null shape with zero vertices (for example, space).
            // Free its native allocation without the buffer overload's minimum-length check.
            nstbtt_FreeShape(info.address(), vertices.address());
        }

        return curves;
    }

    /**
     * Uploads one row-padded outline texture with nearest sampling and edge
     * clamping. Exactly one trusted staging buffer is present: curves use RGBA16F
     * and bands use RG32UI. Preserves texture and pixel-unpack state on every exit.
     *
     * @param curves curve data, or null when uploading bands
     * @param bands band data, or null when uploading curves
     * @return newly owned texture name
     */
    private static int uploadTexture(FloatBuffer curves, IntBuffer bands) {
        /*
         * Capture upload state as primitives rather than allocating a scope object.
         * Explicit row settings make CPU staging independent of an earlier PBO upload.
         */
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int pbo = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        int rowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
        int skipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
        int skipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        int swapBytes = glGetInteger(GL_UNPACK_SWAP_BYTES);
        int texture = glGenTextures();
        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SWAP_BYTES, 0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            if (curves != null) {
                int height = Math.max(1, curves.capacity() / (TEXTURE_WIDTH * 4));
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, TEXTURE_WIDTH, height, 0, GL_RGBA, GL_FLOAT, curves);
            } else {
                int height = Math.max(1, bands.capacity() / (TEXTURE_WIDTH * 2));
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32UI, TEXTURE_WIDTH, height, 0, GL_RG_INTEGER, GL_UNSIGNED_INT, bands);
            }
            return texture;
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(texture);
            throw failure;
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
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
     * Looks up a compiled character without substituting another glyph.
     *
     * @param codepoint requested character value
     * @return compiled entry, or null outside the configured range
     */
    public SlugGlyph glyph(int codepoint) {
        int index = codepoint - firstCodepoint;
        if (index < 0 || index >= characterCount) {
            return null;
        }
        if (glyphs[index] == null) glyphs[index] = glyphByIndex(stbtt_FindGlyphIndex(info, codepoint));
        return glyphs[index];
    }

    /**
     * Returns an em-space pair adjustment, querying STB on first use and caching it
     * when both characters are compiled. Out-of-range pairs query STB directly.
     *
     * @param previousCodepoint preceding character
     * @param codepoint         current character
     * @return pair advance adjustment in em units
     */
    public float kerning(int previousCodepoint, int codepoint) {
        int previousIndex = previousCodepoint - firstCodepoint;
        int index = codepoint - firstCodepoint;
        if (previousIndex < 0 || previousIndex >= characterCount || index < 0 || index >= characterCount) {
            return stbtt_GetCodepointKernAdvance(info, previousCodepoint, codepoint) * emScale;
        }
        int pairIndex = previousIndex * characterCount + index;
        float adjustment = kerning[pairIndex];
        if (Float.isNaN(adjustment)) {
            adjustment = stbtt_GetCodepointKernAdvance(info, previousCodepoint, codepoint) * emScale;
            kerning[pairIndex] = adjustment;
        }
        return adjustment;
    }

    /**
     * Creates an independent reusable shaped layout tied to this font. HarfBuzz
     * positioning and glyph geometry are prepared on construction/rebuild and
     * reused during drawing. Keep the font alive while drawing the run.
     *
     * @param text text to lay out
     * @param size world units per em
     * @return new reusable run
     */
    public SlugTextRun createRun(String text, float size) {
        return new SlugTextRun(this, text, size);
    }

    /**
     * Reports whether the owned GPU outline textures have been released.
     *
     * @return true after disposal; measurement remains available
     */
    public boolean isDisposed() { return disposed || sourceFont != null && sourceFont.disposed; }

    /**
     * Uploads staged outline data with the owning context current. Geometry growth
     * replaces the pair while keeping glyph addresses stable. Both texture IDs
     * become visible together; a failed second upload releases the first new
     * texture and retains staging buffers so a later call can retry.
     *
     * @throws IllegalStateException if the font has been disposed
     * @throws RuntimeException if texture creation fails
     */
    private void ensureTextures() {
        if (isDisposed()) throw new IllegalStateException("Slug font is disposed.");
        if (curveTexture != 0 && !geometryDirty) return;
        if (geometryDirty) {
            pendingCurves = curveWriter.toBuffer(Math.max(1, curveWriter.height()));
            pendingBands = bandWriter.toBuffer(Math.max(1, bandWriter.height()));
        }
        int curves = uploadTexture(pendingCurves, null);
        int bands;
        try {
            bands = uploadTexture(null, pendingBands);
        } catch (RuntimeException | Error failure) {
            glDeleteTextures(curves);
            throw failure;
        }
        if (curveTexture != 0) glDeleteTextures(curveTexture);
        if (bandTexture != 0) glDeleteTextures(bandTexture);
        curveTexture = curves;
        bandTexture = bands;
        geometryDirty = false;
        pendingCurves = null;
        pendingBands = null;
    }

    /**
     * Binds outline data to the two units reserved by TextureBatch's glyph shader.
     * Uploads deferred data if needed. Requires the graphics context; the batch restores previous bindings when
     * its glyph segment finishes. This does not transfer texture ownership.
     *
     * @throws IllegalStateException if this font has been disposed
     */
    public void bindTextures() {
        ensureTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, curveTexture);
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, bandTexture);
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
     * Measures the maximum shaped line advance using the same OpenType glyph
     * positions as drawing. This measures advances rather than tight ink bounds.
     * Does not change retained state; null or empty text returns zero.
     *
     * @param text text to measure
     * @param size world units per em
     * @return maximum line advance in world units
     */
    public float getWidth(String text, float size) {
        if (!Float.isFinite(size) || size < 0) throw new IllegalArgumentException("Invalid font size");
        if (text == null || text.isEmpty() || size == 0) return 0;
        return shape(text).width() * size;
    }

    /**
     * Shapes a clamped UTF-16 substring as an independent run. Interior ranges may
     * allocate a substring on cache lookup. For full-context caret positions or
     * bidirectional selection, use shape(text) instead of measuring prefixes.
     *
     * @param text source text; null or empty yields zero
     * @param startInclusive inclusive start index, clamped to the source
     * @param endExclusive exclusive end index, clamped to the source
     * @param size finite nonnegative world units per em
     * @return maximum line advance in the requested range
     * @throws IllegalArgumentException if size is negative or nonfinite
     */
    public float getWidth(String text, int startInclusive, int endExclusive, float size) {
        if (!Float.isFinite(size) || size < 0) throw new IllegalArgumentException("Invalid font size");
        if (text == null || text.isEmpty() || size == 0) return 0;
        int start = Math.max(0, Math.min(text.length(), startInclusive));
        int end = Math.max(start, Math.min(text.length(), endExclusive));
        if (start == end) return 0;
        return getWidth(start == 0 && end == text.length() ? text : text.substring(start, end), size);
    }

    /**
     * Measures shaped line-box height using font line height and size. CR, LF,
     * and CRLF delimit lines. A trailing break adds an empty line; null or empty text returns zero.
     * This is not a tight glyph-ink measurement.
     *
     * @param text text to measure
     * @param size world units per em
     * @return total line-box height
     */
    public float getHeight(String text, float size) {
        if (!Float.isFinite(size) || size < 0) throw new IllegalArgumentException("Invalid font size");
        if (text == null || text.isEmpty() || size == 0) return 0;
        return shape(text).height() * size;
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
     * @param width  width metadata
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
     * Deletes owned geometry textures and native shaping/hinting resources. A hinted
     * view releases only its geometry; the source owns shared native resources.
     * Repeated calls are harmless. Retained bytes/cached layout metadata permit
     * CPU-only measurement after disposal, while further drawing is invalid.
     * An unuploaded deferred font needs no current context for disposal.
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        cachedRuns.clear();
        retainedRun = null;
        runCharacters = 0;
        if (nativeFace != null) {
            nativeFace.close();
            nativeFace = null;
        }
        pendingCurves = null;
        pendingBands = null;
        if (curveTexture != 0) {
            glDeleteTextures(curveTexture);
            curveTexture = 0;
        }
        if (bandTexture != 0) {
            glDeleteTextures(bandTexture);
            bandTexture = 0;
        }
    }

}
