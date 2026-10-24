package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryUtil;
import valthorne.graphics.GraphicsResource;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.memAddress;

/**
 * Owns growable native CPU staging and a bgfx dynamic vertex buffer. Each frame
 * appends sequentially, then transfers its complete contents once. Commands refer
 * to separate ranges, so later geometry cannot overwrite earlier queued draws.
 * CPU storage is reused and released explicitly; native upload memory belongs to bgfx.
 */
final class BgfxVertexBuffer implements AutoCloseable, GraphicsResource {
    private ByteBuffer bytes; // Owned native staging storage positioned after appended bytes.
    private short handle; // Current owned dynamic GPU buffer handle.
    private final int stride; // Number of bytes per vertex or instance record.
    private final BGFXVertexLayout layout; // Owned layout copy for growing deferred upload slots.
    private short[] uploads = new short[4]; // Retained GPU handles for distinct uploads within one native frame.
    private int uploadCount; // Allocated upload slots.
    private int uploadIndex; // Next unused upload slot in the current frame.
    private long frame = -1; // Last coordinator frame generation.

    /**
     * Creates staging and resizable GPU storage with one immutable record layout.
     *
     * @param layout record declaration borrowed only during buffer creation
     * @param records initial record capacity
     * @throws IllegalStateException if bgfx cannot allocate a buffer handle
     */
    BgfxVertexBuffer(BGFXVertexLayout layout, int records) {
        stride = Short.toUnsignedInt(layout.stride());
        this.layout = BGFXVertexLayout.calloc();
        this.layout.set(layout);
        bytes = MemoryUtil.memAlloc(Math.multiplyExact(stride, records));
        handle = bgfx_create_dynamic_vertex_buffer(records, layout, BGFX_BUFFER_ALLOW_RESIZE);
        if (handle == BGFX_INVALID_HANDLE) {
            MemoryUtil.memFree(bytes);
            bytes = null;
            this.layout.free();
            throw new IllegalStateException("Could not allocate bgfx dynamic shape buffer");
        }
        uploads[uploadCount++] = handle;
        BgfxResources.register(this);
    }

    /**
     * Empties frame staging without releasing its capacity.
     */
    void clear() {
        bytes.clear();
    }

    /**
     * Returns the number of complete records appended to this frame.
     *
     * @return current record count
     */
    int count() {
        return bytes.position() / stride;
    }

    /**
     * Ensures enough writable bytes, growing geometrically only when necessary.
     *
     * @param additionalBytes number of bytes to append
     * @return borrowed staging storage, with its current position retained
     */
    ByteBuffer reserve(int additionalBytes) {
        if (additionalBytes > bytes.remaining()) {
            int required = Math.addExact(bytes.position(), additionalBytes);
            int capacity = (int) Math.min(Integer.MAX_VALUE - 8L, Math.max(required, bytes.capacity() * 2L));
            bytes = MemoryUtil.memRealloc(bytes, capacity);
        }
        return bytes;
    }

    /**
     * Copies staged bytes into bgfx-owned upload memory without a Java wrapper allocation.
     */
    void upload() {
        int size = bytes.position();
        if (size == 0) return;
        if (BgfxGraphics.isActive()) {
            if (frame != BgfxGraphics.frameId()) {
                frame = BgfxGraphics.frameId();
                uploadIndex = 0;
            }
            if (uploadIndex == uploadCount) {
                if (uploadCount == uploads.length) uploads = Arrays.copyOf(uploads, uploads.length * 2);
                short next = bgfx_create_dynamic_vertex_buffer(Math.max(1, size / stride), layout, BGFX_BUFFER_ALLOW_RESIZE);
                if (next == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not allocate deferred shape upload");
                uploads[uploadCount++] = next;
            }
            handle = uploads[uploadIndex++];
        }
        long memory = nbgfx_copy(memAddress(bytes) - size, size);
        if (memory == 0) throw new IllegalStateException("Could not allocate bgfx upload memory");
        nbgfx_update_dynamic_vertex_buffer(handle, 0, memory);
    }

    /**
     * Returns the borrowed GPU buffer used by queued draw ranges.
     *
     * @return dynamic vertex-buffer handle
     */
    short handle() {
        return handle;
    }

    @Override
    public void close() {
        if (bytes == null) return;
        BgfxResources.unregister(this);
        releaseGraphicsResources();
        layout.free();
        MemoryUtil.memFree(bytes);
        bytes = null;
    }

    @Override
    public void releaseGraphicsResources() {
        for (int i = 0; i < uploadCount; i++) bgfx_destroy_dynamic_vertex_buffer(uploads[i]);
        uploadCount = 0;
        handle = BGFX_INVALID_HANDLE;
    }

    @Override
    public void restoreGraphicsResources() {
        BgfxLayouts.restore(layout);
        handle = bgfx_create_dynamic_vertex_buffer(Math.max(1, bytes.capacity() / stride), layout, BGFX_BUFFER_ALLOW_RESIZE);
        if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not restore shape stream");
        uploads[uploadCount++] = handle;
        frame = -1;
        uploadIndex = 0;
        bytes.clear();
    }

}
