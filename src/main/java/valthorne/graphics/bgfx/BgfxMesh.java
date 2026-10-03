package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFXVertexLayout;

import java.nio.ByteBuffer;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.memAddress;

/**
 * Owns one immutable GPU triangle mesh. Repeated shapes share its vertices and
 * supply only transforms and tints. No source model or temporary staging storage
 * is retained, and close queues deletion on the owning bgfx runtime.
 */
public final class BgfxMesh implements AutoCloseable {
    private short handle; // Owned immutable vertex buffer, invalid after close.
    private final int vertices; // Number of triangle-list vertices stored on the GPU.
    private final boolean transparent; // Whether any vertex color requires blending without depth writes.

    /**
     * Copies packed vertex bytes into one bgfx-owned immutable buffer.
     *
     * @param data native buffer positioned after packed vertices
     * @param layout borrowed position/color declaration
     */
    public BgfxMesh(ByteBuffer data, BGFXVertexLayout layout) {
        int size = data.position();
        vertices = size / Short.toUnsignedInt(layout.stride());
        boolean translucent = false;
        for (int offset = 15; offset < size; offset += 16) {
            if (Byte.toUnsignedInt(data.get(offset)) < 255) {
                translucent = true;
                break;
            }
        }
        transparent = translucent;
        long memory = nbgfx_copy(memAddress(data) - size, size);
        if (memory == 0) throw new IllegalStateException("Could not allocate bgfx mesh upload memory");
        handle = nbgfx_create_vertex_buffer(memory, layout.address(), (short) BGFX_BUFFER_NONE);
        if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not allocate bgfx mesh buffer");
    }

    /**
     * Binds all of this mesh's vertices to stream zero for the next draw.
     */
    void bind() {
        bgfx_set_vertex_buffer(0, handle, 0, vertices);
    }

    /**
     * Returns the stored vertex count for submission statistics.
     *
     * @return triangle-list vertex count
     */
    int vertices() {
        return vertices;
    }

    /**
     * Returns whether the immutable vertex colors include translucent geometry.
     * Mixed-alpha models use no depth writes for the whole mesh and require
     * back-to-front submission like other transparent geometry.
     *
     * @return whether at least one vertex alpha is less than one
     */
    boolean transparent() {
        return transparent;
    }

    @Override
    public void close() {
        if (handle == BGFX_INVALID_HANDLE) return;
        bgfx_destroy_vertex_buffer(handle);
        handle = BGFX_INVALID_HANDLE;
    }
}
