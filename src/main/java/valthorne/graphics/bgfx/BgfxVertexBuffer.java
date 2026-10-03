package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.memAddress;

/**
 * Owns growable native CPU staging and a bgfx dynamic vertex buffer. Each frame
 * appends sequentially, then transfers its complete contents once. Commands refer
 * to separate ranges, so later geometry cannot overwrite earlier queued draws.
 * CPU storage is reused and released explicitly; native upload memory belongs to bgfx.
 */
final class BgfxVertexBuffer implements AutoCloseable {
    private ByteBuffer bytes; // Owned native staging storage positioned after appended bytes.
    private final short handle; // Owned dynamic GPU buffer handle.
    private final int stride; // Number of bytes per vertex or instance record.

    /**
     * Creates staging and resizable GPU storage with one immutable record layout.
     *
     * @param layout record declaration borrowed only during buffer creation
     * @param records initial record capacity
     * @throws IllegalStateException if bgfx cannot allocate a buffer handle
     */
    BgfxVertexBuffer(BGFXVertexLayout layout, int records) {
        stride = Short.toUnsignedInt(layout.stride());
        bytes = MemoryUtil.memAlloc(Math.multiplyExact(stride, records));
        handle = bgfx_create_dynamic_vertex_buffer(records, layout, BGFX_BUFFER_ALLOW_RESIZE);
        if (handle == BGFX_INVALID_HANDLE) {
            MemoryUtil.memFree(bytes);
            bytes = null;
            throw new IllegalStateException("Could not allocate bgfx dynamic shape buffer");
        }
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
        bgfx_destroy_dynamic_vertex_buffer(handle);
        MemoryUtil.memFree(bytes);
        bytes = null;
    }
}
