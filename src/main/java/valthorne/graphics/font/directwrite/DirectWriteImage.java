package valthorne.graphics.font.directwrite;

import static valthorne.graphics.font.directwrite.WindowsTextAbi.*;

import org.lwjgl.BufferUtils;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

import static valthorne.graphics.font.directwrite.WindowsCom.*;

/**
 * Immutable CPU raster produced by Microsoft's Direct2D software render target from
 * an actual IDWriteTextLayout. Native WIC PBGRA output is converted to bottom-up,
 * straight RGBA solely for the existing OpenGL batch's texture/blend convention.
 * Coverage, hinting, shaping, gamma, and grayscale/ClearType decisions remain native.
 * Opaque ClearType images retain their real background and RGB samples; they must
 * never be treated as transparent masks or tinted after rasterization.
 *
 * <p>The raster owns only a JVM-managed direct buffer. All COM bitmap, brush, and
 * render-target references are released before construction returns. The caller
 * may upload it once and bound its texture cache independently of native layouts.</p>
 */
public final class DirectWriteImage {
    /**
     * Maximum pixel count of one native image; UI clips long layouts before rasterizing.
     */
    private static final long MAX_IMAGE_PIXELS = 4_194_304;
    /**
     * Maximum native raster dimension supported by this texture path.
     */
    private static final int MAX_IMAGE_EDGE = 16384;
    private final ByteBuffer pixels; // Immutable bottom-up straight RGBA staging bytes.
    private final int width; // Physical raster width, including overhang and guard pixels.
    private final int height; // Physical raster height, including overhang and guard pixels.
    private final int left; // Physical margin from image left to the layout origin before subpixel phase.
    private final int top; // Physical margin from image top to the layout origin before subpixel phase.
    private final boolean opaque; // Whether ClearType-safe opaque composition was requested.

    /**
     * Renders a shaped layout with native DPI, rendering parameters, and target alpha.
     *
     * @param backend live Microsoft factories
     * @param layout shaped text and bounds
     * @param scaleX horizontal physical pixels per DIP
     * @param scaleY vertical physical pixels per DIP
     * @param phaseX fractional physical X position
     * @param phaseY fractional physical Y position
     * @param rgba packed RRGGBBAA foreground
     * @param background optional RRGGBB opaque background
     */
    DirectWriteImage(DirectWriteBackend backend, DirectWriteLayout layout, float scaleX, float scaleY, float phaseX, float phaseY, int rgba, Integer background) {
        this(backend, layout, scaleX, scaleY, phaseX, phaseY, rgba, background, true, 0, 0, layout.pixelWidth(scaleX), layout.pixelHeight(scaleY));
    }

    /**
     * Rasterizes a physical subrectangle of a potentially long native layout.
     * Text is still shaped as one complete string; only the native bitmap target
     * is clipped, preserving glyph context while bounding texture memory.
     *
     * @param backend live native owner
     * @param layout complete shaped layout
     * @param scaleX horizontal DPI scale
     * @param scaleY vertical DPI scale
     * @param phaseX fractional physical X origin
     * @param phaseY fractional physical Y origin
     * @param rgba native foreground RRGGBBAA
     * @param background optional opaque RRGGBB background
     * @param allowClearType whether an opaque target may use the system's ClearType preference
     * @param clipLeft physical offset into the padded full image
     * @param clipTop physical vertical offset into the padded full image
     * @param clipWidth positive physical target width
     * @param clipHeight positive physical target height
     */
    DirectWriteImage(DirectWriteBackend backend, DirectWriteLayout layout, float scaleX, float scaleY, float phaseX, float phaseY, int rgba, Integer background, boolean allowClearType, int clipLeft, int clipTop, int clipWidth, int clipHeight) {
        if (!Float.isFinite(scaleX) || !Float.isFinite(scaleY) || scaleX <= 0 || scaleY <= 0 || scaleX > 16 || scaleY > 16)
            throw new IllegalArgumentException("Invalid framebuffer DPI scale");
        if (!Float.isFinite(phaseX) || !Float.isFinite(phaseY) || phaseX < 0 || phaseY < 0 || phaseX >= 1 || phaseY >= 1)
            throw new IllegalArgumentException("Subpixel phase must be in [0,1)");
        if (clipLeft < 0 || clipTop < 0 || clipWidth <= 0 || clipHeight <= 0) throw new IllegalArgumentException("Invalid native raster subrectangle");
        width = Math.min(clipWidth, layout.pixelWidth(scaleX) - clipLeft);
        height = Math.min(clipHeight, layout.pixelHeight(scaleY) - clipTop);
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Native raster subrectangle is outside the layout");
        if ((long) width * height > MAX_IMAGE_PIXELS || width > MAX_IMAGE_EDGE || height > MAX_IMAGE_EDGE) throw new IllegalArgumentException("Native text raster is too large");
        left = layout.leftMargin(scaleX) - clipLeft;
        top = layout.topMargin(scaleY) - clipTop;
        opaque = background != null;
        WindowsCom com = backend.com();
        MemorySegment bitmap = MemorySegment.NULL;
        MemorySegment target = MemorySegment.NULL;
        MemorySegment brush = MemorySegment.NULL;
        try (Arena scope = Arena.ofConfined()) {
            MemorySegment output = scope.allocate(ADDRESS);
            MemorySegment pixelFormat = guid(scope, opaque ? "6fddc324-4e03-4bfe-b185-3d77768dc90e" : "6fddc324-4e03-4bfe-b185-3d77768dc910");
            com.hr(backend.wic(), WIC_CREATE_BITMAP, new MemoryLayout[]{INT, INT, ADDRESS, INT, ADDRESS}, width, height, pixelFormat, 2, output);
            bitmap = output.get(ADDRESS, 0);
            MemorySegment properties = scope.allocate(28, 4);
            properties.set(INT, 0, 1);
            properties.set(INT, 4, 87);
            properties.set(INT, 8, opaque ? 3 : 1);
            properties.set(FLOAT, 12, 96 * scaleX);
            properties.set(FLOAT, 16, 96 * scaleY);
            com.hr(backend.d2d(), D2D_CREATE_WIC_TARGET, new MemoryLayout[]{ADDRESS, ADDRESS, ADDRESS}, bitmap, properties, output);
            target = output.get(ADDRESS, 0);
            MemorySegment color = scope.allocate(16, 4);
            setColor(color, (rgba >>> 24) & 255, (rgba >>> 16) & 255, (rgba >>> 8) & 255, rgba & 255);
            com.hr(target, TARGET_CREATE_SOLID_BRUSH, new MemoryLayout[]{ADDRESS, ADDRESS, ADDRESS}, color, MemorySegment.NULL, output);
            brush = output.get(ADDRESS, 0);
            com.call(target, TARGET_SET_TEXT_ANTIALIAS, null, new MemoryLayout[]{INT}, backend.antialiasMode(opaque && allowClearType));
            com.call(target, TARGET_SET_TEXT_PARAMS, null, new MemoryLayout[]{ADDRESS}, backend.renderingParams());
            if (opaque) setColor(color, (background >>> 16) & 255, (background >>> 8) & 255, background & 255, 255);
            else setColor(color, 0, 0, 0, 0);
            com.call(target, TARGET_BEGIN, null, new MemoryLayout[0]);
            com.call(target, TARGET_CLEAR, null, new MemoryLayout[]{ADDRESS}, color);
            MemorySegment origin = scope.allocate(8, 4);
            origin.set(FLOAT, 0, (left + phaseX) / scaleX);
            origin.set(FLOAT, 4, (top + phaseY) / scaleY);
            com.call(target, TARGET_DRAW_TEXT_LAYOUT, null, new MemoryLayout[]{MemoryLayout.structLayout(FLOAT, FLOAT), ADDRESS, ADDRESS, INT}, origin, layout.pointer(), brush, 0);
            com.hr(target, TARGET_END, new MemoryLayout[]{ADDRESS, ADDRESS}, MemorySegment.NULL, MemorySegment.NULL);
            int stride = width * 4;
            MemorySegment bytes = scope.allocate((long) stride * height);
            com.hr(bitmap, WIC_COPY_PIXELS, new MemoryLayout[]{ADDRESS, INT, INT, ADDRESS}, MemorySegment.NULL, stride, stride * height, bytes);
            pixels = BufferUtils.createByteBuffer(stride * height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    long source = ((long) y * width + x) * 4;
                    int destination = ((height - y - 1) * width + x) * 4;
                    int alpha = opaque ? 255 : bytes.get(ValueLayout.JAVA_BYTE, source + 3) & 255;
                    for (int channel = 0; channel < 3; channel++) {
                        int premultiplied = bytes.get(ValueLayout.JAVA_BYTE, source + 2 - channel) & 255;
                        pixels.put(destination + channel, (byte) (alpha == 0 ? 0 : Math.min(255, (premultiplied * 255 + alpha / 2) / alpha)));
                    }
                    pixels.put(destination + 3, (byte) alpha);
                }
            }
        } finally {
            com.release(brush);
            com.release(target);
            com.release(bitmap);
        }
    }

    /**
     * Writes an unpremultiplied Direct2D color; the native target owns alpha handling.
     *
     * @param color four-float native destination
     * @param red red byte
     * @param green green byte
     * @param blue blue byte
     * @param alpha alpha byte
     */
    private static void setColor(MemorySegment color, int red, int green, int blue, int alpha) {
        /*
         * Do not perform grayscale or ClearType coverage conversion in Java.
         */
        color.set(FLOAT, 0, red / 255f);
        color.set(FLOAT, 4, green / 255f);
        color.set(FLOAT, 8, blue / 255f);
        color.set(FLOAT, 12, alpha / 255f);
    }

    /**
     * Borrows immutable RGBA bytes suitable for a non-owning TextureData wrapper.
     *
     * @return read-only direct buffer in bottom-up row order
     */
    public ByteBuffer pixels() { return pixels.asReadOnlyBuffer(); }

    /**
     * Returns physical image width.
     *
     * @return padded pixel width
     */
    public int width() { return width; }

    /**
     * Returns physical image height.
     *
     * @return padded pixel height
     */
    public int height() { return height; }

    /**
     * Returns the physical left margin used to position the image.
     *
     * @return left margin in physical pixels
     */
    public int left() { return left; }

    /**
     * Returns the physical top margin used to position the image.
     *
     * @return top margin in physical pixels
     */
    public int top() { return top; }

    /**
     * Reports whether the native target includes its final opaque background.
     *
     * @return true for opaque native output, including eligible native ClearType
     */
    public boolean opaque() { return opaque; }
}
