package valthorne.graphics.font.directwrite;

import static valthorne.graphics.font.directwrite.WindowsTextAbi.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.Locale;
import java.util.Arrays;

import static valthorne.graphics.font.directwrite.WindowsCom.*;

/**
 * Retains one real IDWriteTextLayout with native shaping, line metrics, glyph
 * overhangs, and UTF-16 hit testing. The owning face bounds its cache and disposes
 * layouts on eviction. Callers must not retain a borrowed layout across arbitrary
 * cache mutations; immutable raster images have independent lifetimes.
 */
public final class DirectWriteLayout implements AutoCloseable {
    private final DirectWriteBackend backend; // Owner of the factory and COM apartment.
    private final MemorySegment pointer; // Owned IDWriteTextLayout native reference.
    private final String text; // Source UTF-16 contents for caret boundary validation.
    private final float width; // Native advance including trailing spaces, in DIPs.
    private final float height; // Native total line-box height in DIPs.
    private final float baseline; // Native first-line baseline from layout top, in DIPs.
    private final float leftOverhang; // Visible overflow to the left of the advance rectangle.
    private final float topOverhang; // Visible overflow above the layout rectangle.
    private final float rightOverhang; // Visible overflow beyond the advance rectangle.
    private final float bottomOverhang; // Visible overflow below the line boxes.
    private float[] carets; // Lazily retained native leading-edge positions for repeated editing measurements.
    private DirectWriteSelection selection; // Cached native visual rectangles for the most recently selected range.
    private int selectionStart; // Inclusive UTF-16 position of the cached selection.
    private int selectionEnd; // Exclusive UTF-16 position of the cached selection.
    private boolean closed; // Whether the native layout has been released.

    /**
     * Shapes exact text through DirectWrite using a private font collection.
     *
     * @param backend native owner
     * @param collection exact-file private collection
     * @param family native family name
     * @param weight native face weight
     * @param style native face style
     * @param stretch native face stretch
     * @param text UTF-16 contents
     * @param size DIPs per em
     */
    DirectWriteLayout(DirectWriteBackend backend, MemorySegment collection, String family, int weight, int style, int stretch, String text, float size) {
        this.backend = backend;
        this.text = text;
        WindowsCom com = backend.com();
        MemorySegment format = MemorySegment.NULL;
        MemorySegment layout = MemorySegment.NULL;
        try (Arena scope = Arena.ofConfined()) {
            MemorySegment output = scope.allocate(ADDRESS);
            com.hr(backend.factory(), WRITE_CREATE_FORMAT, new MemoryLayout[]{ADDRESS, ADDRESS, INT, INT, INT, FLOAT, ADDRESS, ADDRESS},
                    wide(scope, family), collection, weight, style, stretch, size, wide(scope, Locale.getDefault().toLanguageTag()), output);
            format = output.get(ADDRESS, 0);
            com.hr(format, FORMAT_SET_WRAPPING, new MemoryLayout[]{INT}, 1);
            com.hr(backend.factory(), WRITE_CREATE_LAYOUT, new MemoryLayout[]{ADDRESS, INT, ADDRESS, FLOAT, FLOAT, ADDRESS},
                    wide(scope, text), text.length(), format, 100000f, 100000f, output);
            layout = output.get(ADDRESS, 0);
            MemorySegment metrics = scope.allocate(36, 4);
            com.hr(layout, LAYOUT_GET_METRICS, new MemoryLayout[]{ADDRESS}, metrics);
            width = metrics.get(FLOAT, 12);
            height = metrics.get(FLOAT, 16);
            com.hr(layout, LAYOUT_SET_WIDTH, new MemoryLayout[]{FLOAT}, Math.max(1, width));
            com.hr(layout, LAYOUT_SET_HEIGHT, new MemoryLayout[]{FLOAT}, Math.max(1, height));
            MemorySegment count = scope.allocate(INT);
            MemorySegment lines = scope.allocate(Math.max(1, metrics.get(INT, 32)) * 24L, 4);
            com.hr(layout, LAYOUT_GET_LINES, new MemoryLayout[]{ADDRESS, INT, ADDRESS}, lines, Math.max(1, metrics.get(INT, 32)), count);
            baseline = lines.get(FLOAT, 16);
            MemorySegment overhang = scope.allocate(16, 4);
            com.hr(layout, LAYOUT_GET_OVERHANG, new MemoryLayout[]{ADDRESS}, overhang);
            leftOverhang = Math.max(0, overhang.get(FLOAT, 0));
            topOverhang = Math.max(0, overhang.get(FLOAT, 4));
            rightOverhang = Math.max(0, overhang.get(FLOAT, 8));
            bottomOverhang = Math.max(0, overhang.get(FLOAT, 12));
            pointer = layout;
        } catch (RuntimeException | Error failure) {
            com.release(layout);
            throw failure;
        } finally {
            com.release(format);
        }
    }

    /**
     * Returns the real shaped width including trailing whitespace.
     *
     * @return advance in DIPs
     */
    public float width() { return width; }

    /**
     * Returns native line-box height, including fallback-font extents.
     *
     * @return height in DIPs
     */
    public float height() { return height; }

    /**
     * Returns the first-line baseline offset from the top of the layout.
     *
     * @return baseline offset in DIPs
     */
    public float baseline() { return baseline; }

    /**
     * Obtains a native leading-edge caret position in the fully shaped string.
     * Prefix substrings are not reshaped, preserving ligature and kerning context.
     *
     * @param position UTF-16 insertion position from zero through text length
     * @return native horizontal caret coordinate in DIPs
     */
    public float caretX(int position) {
        requireOpen();
        if (position < 0 || position > text.length()) throw new IndexOutOfBoundsException(position);
        if (carets == null) {
            carets = new float[text.length() + 1];
            Arrays.fill(carets, Float.NaN);
        }
        if (Float.isNaN(carets[position])) {
            try (Arena scope = Arena.ofConfined()) {
                MemorySegment x = scope.allocate(FLOAT);
                MemorySegment y = scope.allocate(FLOAT);
                MemorySegment hit = scope.allocate(36, 4);
                backend.com().hr(pointer, LAYOUT_HIT_POSITION, new MemoryLayout[]{INT, INT, ADDRESS, ADDRESS, ADDRESS}, position, 0, x, y, hit);
                carets[position] = x.get(FLOAT, 0);
            }
        }
        return carets[position];
    }

    /**
     * Maps a point to the actual native glyph cluster rather than estimating from
     * separately measured prefixes. Supports RTL runs and multi-code-unit clusters.
     *
     * @param x horizontal layout coordinate in DIPs
     * @param y vertical layout coordinate in DIPs, measured downward
     * @return UTF-16 insertion position
     */
    public int hitTest(float x, float y) {
        requireOpen();
        try (Arena scope = Arena.ofConfined()) {
            MemorySegment trailing = scope.allocate(INT);
            MemorySegment inside = scope.allocate(INT);
            MemorySegment hit = scope.allocate(36, 4);
            backend.com().hr(pointer, LAYOUT_HIT_POINT, new MemoryLayout[]{FLOAT, FLOAT, ADDRESS, ADDRESS, ADDRESS}, x, y, trailing, inside, hit);
            return Math.min(text.length(), hit.get(INT, 0) + (trailing.get(INT, 0) != 0 ? hit.get(INT, 4) : 0));
        }
    }

    /**
     * Gets native visual rectangles for one logical selection, retaining a snapshot
     * until the requested range changes. Mixed RTL/LTR selections can be disjoint.
     *
     * @param start inclusive UTF-16 position
     * @param end exclusive UTF-16 position
     * @return immutable selection geometry
     */
    public DirectWriteSelection selection(int start, int end) {
        requireOpen();
        if (start < 0 || end < start || end > text.length()) throw new IndexOutOfBoundsException("Invalid selection range");
        if (selection != null && selectionStart == start && selectionEnd == end) return selection;
        try (Arena scope = Arena.ofConfined()) {
            int capacity = Math.max(1, end - start + 1);
            MemorySegment hits = scope.allocate(capacity * 36L, 4);
            MemorySegment count = scope.allocate(INT);
            backend.com().hr(pointer, LAYOUT_HIT_RANGE, new MemoryLayout[]{INT, INT, FLOAT, FLOAT, ADDRESS, INT, ADDRESS}, start, end - start, 0f, 0f, hits, capacity, count);
            float[] boxes = new float[count.get(INT, 0) * 4];
            for (int index = 0; index < boxes.length / 4; index++)
                for (int component = 0; component < 4; component++) boxes[index * 4 + component] = hits.get(FLOAT, index * 36L + 8 + component * 4L);
            selection = new DirectWriteSelection(boxes);
            selectionStart = start;
            selectionEnd = end;
            return selection;
        }
    }

    /**
     * Rasterizes through native Direct2D with separate framebuffer DPI and subpixel phase.
     *
     * @param scaleX physical pixels per DIP horizontally
     * @param scaleY physical pixels per DIP vertically
     * @param phaseX horizontal origin fraction in physical pixels, in [0,1)
     * @param phaseY vertical origin fraction in physical pixels, in [0,1)
     * @param rgba straight RGBA text color packed RRGGBBAA
     * @param opaqueBackground optional opaque RRGGBB color, or null for real grayscale transparency
     * @return independently owned native raster bytes and placement metadata
     */
    public DirectWriteImage rasterize(float scaleX, float scaleY, float phaseX, float phaseY, int rgba, Integer opaqueBackground) {
        requireOpen();
        return new DirectWriteImage(backend, this, scaleX, scaleY, phaseX, phaseY, rgba, opaqueBackground);
    }

    /**
     * Renders with Windows' native grayscale mode even on an explicitly opaque
     * target, or aliased text when system smoothing is disabled. This supports
     * controlled comparisons without confusing ClearType and transparent text.
     *
     * @param scaleX horizontal physical pixels per DIP
     * @param scaleY vertical physical pixels per DIP
     * @param phaseX fractional physical X origin
     * @param phaseY fractional physical Y origin
     * @param rgba packed RRGGBBAA foreground
     * @param opaqueBackground optional RRGGBB background, or null for transparency
     * @return actual native grayscale bitmap
     */
    public DirectWriteImage rasterizeGrayscale(float scaleX, float scaleY, float phaseX, float phaseY, int rgba, Integer opaqueBackground) {
        requireOpen();
        return new DirectWriteImage(backend, this, scaleX, scaleY, phaseX, phaseY, rgba, opaqueBackground, false, 0, 0, pixelWidth(scaleX), pixelHeight(scaleY));
    }

    /**
     * Rasterizes only a visible physical subrectangle while retaining complete
     * native shaping. Long horizontally scrolled editor lines therefore need no
     * giant bitmap and do not lose ligature context at the visible edge.
     *
     * @param scaleX horizontal physical pixels per DIP
     * @param scaleY vertical physical pixels per DIP
     * @param phaseX fractional physical X origin
     * @param phaseY fractional physical Y origin
     * @param rgba native RRGGBBAA foreground
     * @param left physical offset into the full padded image
     * @param top physical vertical offset into the full padded image
     * @param width positive physical target width
     * @param height positive physical target height
     * @return independent transparent native grayscale raster
     */
    public DirectWriteImage rasterizeRegion(float scaleX, float scaleY, float phaseX, float phaseY, int rgba, int left, int top, int width, int height) {
        requireOpen();
        return new DirectWriteImage(backend, this, scaleX, scaleY, phaseX, phaseY, rgba, null, false, left, top, width, height);
    }

    /**
     * Rasterizes a visible physical region onto an explicitly known final opaque
     * surface. Native ClearType is eligible only when Windows enables it; no RGB
     * coverage is collapsed to a scalar alpha. Null retains transparent grayscale.
     *
     * @param scaleX horizontal physical pixels per DIP
     * @param scaleY vertical physical pixels per DIP
     * @param phaseX fractional physical X origin
     * @param phaseY fractional physical Y origin
     * @param rgba native RRGGBBAA foreground
     * @param left physical crop X
     * @param top physical crop Y
     * @param width positive physical target width
     * @param height positive physical target height
     * @param opaqueBackground exact final RRGGBB background, or null for transparency
     * @return independent native bitmap for this target's actual alpha mode
     */
    public DirectWriteImage rasterizeRegion(float scaleX, float scaleY, float phaseX, float phaseY, int rgba, int left, int top, int width, int height, Integer opaqueBackground) {
        requireOpen();
        return new DirectWriteImage(backend, this, scaleX, scaleY, phaseX, phaseY, rgba, opaqueBackground, true, left, top, width, height);
    }

    /**
     * Returns padded physical raster bounds without allocating pixel storage.
     *
     * @param scaleX horizontal physical pixels per DIP
     * @param scaleY vertical physical pixels per DIP
     * @param bounds reusable destination for left margin, top margin, width, height
     */
    public void rasterBounds(float scaleX, float scaleY, int[] bounds) {
        if (bounds == null || bounds.length < 4) throw new IllegalArgumentException("Four raster bounds are required");
        bounds[0] = leftMargin(scaleX);
        bounds[1] = topMargin(scaleY);
        bounds[2] = pixelWidth(scaleX);
        bounds[3] = pixelHeight(scaleY);
    }

    /**
     * Borrows the native text layout for rasterization.
     *
     * @return live IDWriteTextLayout
     */
    MemorySegment pointer() { requireOpen(); return pointer; }

    /**
     * Returns the left pixel margin needed to preserve native overhang.
     *
     * @param scale horizontal framebuffer pixels per DIP
     * @return physical margin, including two transparent guard pixels
     */
    int leftMargin(float scale) { return 2 + (int) Math.ceil(leftOverhang * scale); }

    /**
     * Returns the top pixel margin needed to preserve native overhang.
     *
     * @param scale vertical framebuffer pixels per DIP
     * @return physical margin including guard pixels
     */
    int topMargin(float scale) { return 2 + (int) Math.ceil(topOverhang * scale); }

    /**
     * Returns the padded image width at a physical DPI scale.
     *
     * @param scale horizontal physical scale
     * @return image width in pixels
     */
    int pixelWidth(float scale) { return leftMargin(scale) + 3 + (int) Math.ceil((width + rightOverhang) * scale); }

    /**
     * Returns the padded image height at a physical DPI scale.
     *
     * @param scale vertical physical scale
     * @return image height in pixels
     */
    int pixelHeight(float scale) { return topMargin(scale) + 3 + (int) Math.ceil((height + bottomOverhang) * scale); }

    /**
     * Rejects native operations after the face cache evicts this layout.
     */
    private void requireOpen() {
        backend.requireOpen();
        if (closed) throw new IllegalStateException("DirectWrite layout was disposed or evicted");
    }

    @Override
    public void close() {
        if (closed) return;
        backend.com().release(pointer);
        closed = true;
    }
}
