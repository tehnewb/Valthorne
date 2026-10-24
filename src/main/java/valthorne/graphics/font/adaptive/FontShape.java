package valthorne.graphics.font.adaptive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memRealloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Reusable packed shaping output shared by desktop and browser font backends.
 * Glyph IDs and UTF-16 clusters precede normalized advance and offset values.
 * One face owns this scratch image; the next shaping call replaces its contents.
 */
final class FontShape implements AutoCloseable {
    /**
     * Two integer indices and three normalized floating-point measurements.
     */
    static final int STRIDE = 20;
    private ByteBuffer pixels; // Owned reusable packed shaping data, null before first shaping.
    private int count; // Active glyph count.

    /**
     * Creates empty shaping scratch without allocating native storage.
     */
    FontShape() {}

    /**
     * Reserves storage for the next native shaping result.
     * @param count nonnegative shaped glyph count
     * @return owned packed output positioned at zero
     */
    ByteBuffer prepare(int count) {
        int bytes = Math.multiplyExact(count, STRIDE);
        if (pixels == null) pixels = memAlloc(Math.max(320, bytes));
        else if (pixels.capacity() < bytes) pixels = memRealloc(pixels, Math.max(bytes, pixels.capacity() * 2));
        pixels.order(ByteOrder.nativeOrder()).clear().limit(bytes);
        this.count = count;
        return pixels;
    }

    /**
     * Returns the active shaped glyph count.
     * @return populated record count
     */
    int count() { return count; }
    /**
     * Reads one shaped glyph index.
     * @param index trusted record index
     * @return font glyph index
     */
    int glyph(int index) { return pixels.getInt(index * STRIDE); }
    /**
     * Reads one logical source cluster.
     * @param index trusted record index
     * @return UTF-16 source offset
     */
    int cluster(int index) { return pixels.getInt(index * STRIDE + 4); }
    /**
     * Reads one normalized horizontal advance.
     * @param index trusted record index
     * @return advance in em units
     */
    float advance(int index) { return pixels.getFloat(index * STRIDE + 8); }
    /**
     * Reads one normalized horizontal bearing.
     * @param index trusted record index
     * @return offset in em units
     */
    float offsetX(int index) { return pixels.getFloat(index * STRIDE + 12); }
    /**
     * Reads one normalized vertical bearing.
     * @param index trusted record index
     * @return offset in em units
     */
    float offsetY(int index) { return pixels.getFloat(index * STRIDE + 16); }

    @Override
    public void close() { memFree(pixels); pixels = null; count = 0; }
}
