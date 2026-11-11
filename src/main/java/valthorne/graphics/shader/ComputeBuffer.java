package valthorne.graphics.shader;

import java.nio.ByteBuffer;
import valthorne.graphics.GraphicsBackend;
import valthorne.graphics.GraphicsResource;
import valthorne.graphics.ImageAccess;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxResources;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memSet;

/**
 * Owns uint32 compute storage with explicit shader access and byte bounds.
 * Read-only shader storage accepts CPU updates; writable shader storage is
 * initialized at creation or by a compute dispatch and cannot be updated from
 * the CPU afterward. Sizes and byte offsets must be divisible by four.
 *
 * <p>Bind before dispatch and use the access declared by the compiled shader.
 * Storage belongs to the active device and rendering thread. Close before
 * graphics shutdown; repeated close is harmless. Updates copy source bytes into
 * renderer-owned memory, so callers may reuse their buffers immediately.</p>
 *
 * <p>Device replacement restores initial bytes and CPU updates. GPU-only writes
 * are not retained: regenerate those results after the backend-change future
 * completes. Read-only storage without initial data begins with zero bytes.</p>
 */
public final class ComputeBuffer implements AutoCloseable, GraphicsResource {
    private ByteBuffer recovery; // Owned CPU upload state; GPU-only writes regenerate after device changes.
    private boolean closed; // Whether recovery memory and resource registration were released.

    private final int size; // Allocated byte capacity, fixed for this resource's lifetime.
    private final ImageAccess access; // Shader access permitted by the allocation.
    private short handle = BGFX_INVALID_HANDLE; // Owned native storage handle, invalid after close.

    /**
     * Allocates zero-filled read-only storage or uninitialized GPU-writable storage.
     *
     * @param size positive byte count divisible by four
     * @param access shader access permitted by the allocation
     */
    public ComputeBuffer(int size, ImageAccess access) {
        this(size, access, null);
    }

    /**
     * Allocates compute storage with optional initial contents. Initial data must
     * fill the allocation and is copied before construction returns.
     *
     * @param size positive byte count divisible by four
     * @param access shader access permitted by the allocation
     * @param initial direct buffer filling the allocation, or null for uninitialized storage
     */
    public ComputeBuffer(int size, ImageAccess access, ByteBuffer initial) {
        if (size < 4 || (size & 3) != 0) throw new IllegalArgumentException("Storage requires a positive four-byte-aligned size");
        if (access == null) throw new NullPointerException("access");
        if (initial != null && initial.remaining() != size) throw new IllegalArgumentException("Initial contents must fill the allocation");
        if (!ComputeShader.isComputeSupported()) throw new UnsupportedOperationException("Compute storage is unavailable");
        this.size = size;
        this.access = access;
        if (access == ImageAccess.READ || initial != null) {
            recovery = memAlloc(size);
            if (initial != null) recovery.put(initial.duplicate())
                    .flip();
            else memSet(memAddress(recovery), 0, size);
        }
        try {
            restoreGraphicsResources();
            BgfxResources.register(this);
        } catch (RuntimeException | Error failure) {
            releaseGraphicsResources();
            if (recovery != null) memFree(recovery);
            throw failure;
        }
    }

    /**
     * Copies remaining bytes into read-only shader storage without resizing it.
     *
     * @param offset destination byte offset divisible by four
     * @param bytes direct source buffer with a byte count divisible by four
     * @throws IllegalStateException if closed or allocated for GPU writes
     */
    public void update(int offset, ByteBuffer bytes) {
        requireOpen();
        if (access != ImageAccess.READ) throw new IllegalStateException("GPU-writable storage cannot be updated from the CPU; supply initial contents at creation");
        if (offset < 0 || (offset & 3) != 0 || (bytes.remaining() & 3) != 0 || offset > size - bytes.remaining())
            throw new IllegalArgumentException("Storage update must be aligned and fit the allocation");
        if (bytes.hasRemaining()) {
            memCopy(memAddress(bytes), memAddress(recovery) + offset, bytes.remaining());
            bgfx_update_dynamic_index_buffer(handle, offset / 4, bgfx_copy(bytes));
        }
    }

    /**
     * Binds this storage for the next dispatch. Required access must be permitted
     * by the allocation and match the compiled shader's buffer declaration.
     *
     * @param binding shader storage binding index
     * @param required shader access for this dispatch
     */
    public void bind(int binding, ImageAccess required) {
        requireOpen();
        if (binding < 0 || binding >= bgfx_get_caps().limits().maxComputeBindings())
            throw new IllegalArgumentException("Storage binding exceeds device limits");
        if (required == null) throw new NullPointerException("required");
        if (access != ImageAccess.READ_WRITE && required != access)
            throw new IllegalArgumentException("Dispatch access exceeds the storage allocation");
        bgfx_set_compute_dynamic_index_buffer(binding, handle, required.nativeAccess());
    }

    /**
     * Returns the fixed allocation capacity.
     *
     * @return byte capacity
     */
    public int size() {
        return size;
    }

    /**
     * Validates rendering-thread ownership and resource lifetime.
     */
    private void requireOpen() {
        BgfxGraphics.device();
        if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Compute storage is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        BgfxGraphics.device();
        BgfxResources.unregister(this);
        releaseGraphicsResources();
        if (recovery != null) memFree(recovery);
        recovery = null;
        closed = true;
    }

    @Override
    public void releaseGraphicsResources() {
        if (handle == BGFX_INVALID_HANDLE) return;
        bgfx_destroy_dynamic_index_buffer(handle);
        handle = BGFX_INVALID_HANDLE;
    }

    @Override
    public void restoreGraphicsResources() {
        if (!ComputeShader.isComputeSupported()) throw new UnsupportedOperationException("Compute storage is unavailable on the selected device");
        int flags = BGFX_BUFFER_INDEX32 | switch (access) {
            case READ -> BGFX_BUFFER_COMPUTE_READ;
            case WRITE -> BGFX_BUFFER_COMPUTE_WRITE;
            case READ_WRITE -> BGFX_BUFFER_COMPUTE_READ_WRITE;
        };
        handle = recovery == null ? bgfx_create_dynamic_index_buffer(size / 4, flags) : bgfx_create_dynamic_index_buffer_mem(bgfx_copy(recovery), flags);
        if (handle == BGFX_INVALID_HANDLE) throw new IllegalStateException("Could not restore compute storage");
    }

}
