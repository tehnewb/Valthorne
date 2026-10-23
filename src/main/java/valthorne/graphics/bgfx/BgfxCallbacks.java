package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFXCallbackInterface;
import org.lwjgl.bgfx.BGFXCallbackVtbl;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.libc.LibCStdio.nvsnprintf;

/**
 * Owns bgfx's native callback table for diagnostics and synchronous PNG captures.
 * Callbacks execute on the synchronous renderer's creating thread. Native failures
 * are retained and rethrown after returning to Java, so exceptions never unwind
 * through native callback frames. Profiling, disk shader caching and continuous
 * video capture are intentionally disabled. Screenshot staging exists only while
 * writing a requested image and never escapes a callback's native data lifetime.
 */
final class BgfxCallbacks implements AutoCloseable {
    /**
     * Reserved non-file request name for a GPU readback without image encoding.
     */
    static final String SYNCHRONIZE_PATH = "__valthorne_bgfx_gpu_sync__";
    private final BGFXCallbackVtbl table; // Owned callback trampolines and native vtable storage.
    private final BGFXCallbackInterface callback; // Owned stable interface pointer passed to bgfx.
    private RuntimeException failure; // First unreported callback failure, or null.
    private String lastScreenshot; // Last completed capture name, including empty synchronization requests.
    private long screenshots; // Completed screenshot callbacks for synchronous request tracking.

    /**
     * Installs explicit no-cache/no-profiler policies and retained callback trampolines.
     */
    BgfxCallbacks() {
        table = BGFXCallbackVtbl.calloc()
                .fatal((self, file, line, code, message) -> report(new IllegalStateException("bgfx fatal " + code + " at " + memUTF8(file) + ":" + Short.toUnsignedInt(line) + ": " + memUTF8(message))))
                .trace_vargs((self, file, line, format, arguments) -> trace(format, arguments))
                .profiler_begin((self, name, color, file, line) -> {})
                .profiler_begin_literal((self, name, color, file, line) -> {})
                .profiler_end(self -> {})
                .cache_read_size((self, id) -> 0)
                .cache_read((self, id, data, size) -> false)
                .cache_write((self, id, data, size) -> {})
                .screen_shot((self, file, width, height, pitch, format, data, size, flip) -> screenshot(file, width, height, pitch, format, data, size, flip))
                .capture_begin((self, width, height, pitch, format, flip) -> {})
                .capture_end(self -> {})
                .capture_frame((self, data, size) -> {});
        callback = BGFXCallbackInterface.calloc()
                .vtbl(table);
    }

    /**
     * Formats native diagnostics without retaining a borrowed va_list.
     *
     * @param format native printf format string
     * @param arguments native argument list valid only during this callback
     */
    private void trace(long format, long arguments) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer text = stack.malloc(4096);
            nvsnprintf(memAddress(text), text.remaining(), format, arguments);
            text.put(text.limit() - 1, (byte) 0);
            System.err.print(memUTF8(memAddress(text)));
        } catch (RuntimeException error) {
            report(error);
        }
    }

    /**
     * Writes one pitched RGBA/BGRA backbuffer image or completes a synchronization
     * request whose name is empty. Pixel orientation follows the native flip flag.
     *
     * @param file native UTF-8 output path
     * @param width captured width
     * @param height captured height
     * @param pitch byte distance between rows
     * @param format RGBA8 or BGRA8 pixel format
     * @param data borrowed native pixels
     * @param size available pixel bytes
     * @param flip whether native rows must be reversed
     */
    private void screenshot(long file, int width, int height, int pitch, int format, long data, int size, boolean flip) {
        try {
            String name = memUTF8(file);
            if (name.equals(SYNCHRONIZE_PATH)) name = "";
            if (!name.isEmpty()) {
                if (format != BGFX_TEXTURE_FORMAT_BGRA8 && format != BGFX_TEXTURE_FORMAT_RGBA8)
                    throw new UnsupportedOperationException("Unsupported screenshot format: " + format);
                if (width < 1 || height < 1 || pitch < (long) width * 4 || (long) pitch * height > size)
                    throw new IllegalArgumentException("Invalid native screenshot bounds");
                ByteBuffer pixels = memByteBuffer(data, size);
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                int[] output = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                for (int y = 0; y < height; y++) {
                    int row = (flip ? height - y - 1 : y) * pitch;
                    for (int x = 0; x < width; x++) {
                        int offset = row + x * 4;
                        int first = pixels.get(offset) & 255;
                        int green = pixels.get(offset + 1) & 255;
                        int third = pixels.get(offset + 2) & 255;
                        int alpha = pixels.get(offset + 3) & 255;
                        int red = format == BGFX_TEXTURE_FORMAT_BGRA8 ? third : first;
                        int blue = format == BGFX_TEXTURE_FORMAT_BGRA8 ? first : third;
                        output[y * width + x] = alpha << 24 | red << 16 | green << 8 | blue;
                    }
                }
                Path path = Path.of(name);
                if (path.getParent() != null) Files.createDirectories(path.getParent());
                if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("No PNG encoder is available");
            }
            lastScreenshot = name;
            screenshots++;
        } catch (IOException error) {
            report(new UncheckedIOException(error));
        } catch (RuntimeException error) {
            report(error);
        }
    }

    /**
     * Retains the first native failure and preserves additional failure causes.
     *
     * @param error callback failure to report at the next Java boundary
     */
    private void report(RuntimeException error) {
        if (failure == null) failure = error;
        else failure.addSuppressed(error);
    }

    /**
     * Rethrows pending native callback failures on the application thread.
     */
    void checkFailure() {
        if (failure == null) return;
        RuntimeException error = failure;
        failure = null;
        throw error;
    }

    /**
     * Returns the interface retained until after native renderer shutdown.
     *
     * @return borrowed native callback interface
     */
    BGFXCallbackInterface callback() {
        return callback;
    }

    /**
     * Reads the completion generation before scheduling a screenshot.
     *
     * @return completed capture count
     */
    long screenshots() {
        return screenshots;
    }

    /**
     * Checks whether a named request completed after its scheduling generation.
     *
     * @param name scheduled native screenshot path
     * @param before completion count captured before scheduling
     * @return whether the matching callback has completed
     */
    boolean completed(String name, long before) {
        return screenshots > before && (name.equals(lastScreenshot) || !name.isEmpty() && lastScreenshot != null && !lastScreenshot.isEmpty() && Path.of(name).normalize().equals(Path.of(lastScreenshot).normalize()));
    }

    @Override
    public void close() {
        table.fatal().free();
        table.trace_vargs().free();
        table.profiler_begin().free();
        table.profiler_begin_literal().free();
        table.profiler_end().free();
        table.cache_read_size().free();
        table.cache_read().free();
        table.cache_write().free();
        table.screen_shot().free();
        table.capture_begin().free();
        table.capture_end().free();
        table.capture_frame().free();
        callback.free();
        table.free();
    }
}
