package org.lwjgl.system;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Java-managed browser staging allocation for the engine's buffer APIs. Browser
 * NIO storage is reclaimed by the JavaScript garbage collector; no desktop native
 * allocator, unsafe address, or JNI library is involved.
 */
public final class MemoryUtil {
    /**
     * Absent native handle value used by shared engine boundary checks.
     */
    public static final long NULL = 0;

    /**
     * Prevents constructing allocation utility instances.
     */
    private MemoryUtil() {
    }

    /**
     * Allocates browser staging in the platform's native byte order.
     * @param size required bytes
     * @return independent direct buffer
     */
    public static ByteBuffer memAlloc(int size) {
        /*
         * Direct NIO buffers expose one contiguous typed-array view for uploads.
         */
        return ByteBuffer.allocateDirect(size)
                .order(ByteOrder.nativeOrder());
    }

    /**
     * Allocates zero-filled browser staging.
     * @param size required bytes
     * @return zero-filled independent buffer
     */
    public static ByteBuffer memCalloc(int size) {
        /*
         * Java guarantees newly allocated buffer storage starts at zero.
         */
        return memAlloc(size);
    }

    /**
     * Grows staging while preserving bytes and the original write position.
     * @param previous old buffer
     * @param size required capacity
     * @return replacement staging
     */
    public static ByteBuffer memRealloc(ByteBuffer previous, int size) {
        /*
         * Growth occurs only at a new workload peak; normal frames reuse storage.
         */
        ByteBuffer next = memAlloc(size);
        if (previous == null) return next;
        int count = Math.min(previous.capacity(), size);
        ByteBuffer source = previous.duplicate();
        source.clear();
        next.put(0, source, 0, count);
        next.position(Math.min(previous.position(), size));
        return next;
    }

    /**
     * Allocates floating-point staging backed by a direct byte buffer.
     * @param count required elements
     * @return independent floating-point view
     */
    public static FloatBuffer memAllocFloat(int count) {
        /*
         * Typed views preserve contiguous storage for the WebAssembly bridge.
         */
        return memAlloc(Math.multiplyExact(count, Float.BYTES))
                .asFloatBuffer();
    }

    /**
     * Completes the shared buffer-ownership API; browser storage is GC-managed.
     * Callers must still clear their references and dispose their GPU resources.
     * @param buffer former staging allocation, including null
     */
    public static void memFree(Buffer buffer) {
        /*
         * JavaScript owns these backing arrays, so invoking a C free operation
         * would be invalid. Dropping the owner's reference releases its lifetime.
         */
    }
    /**
     * Completes byte-buffer staging ownership for precompiled engine callers.
     * @param buffer former byte allocation, including null
     */
    public static void memFree(ByteBuffer buffer) {
        /*
         * The precise overload preserves the desktop bytecode method descriptor.
         */
        memFree((Buffer) buffer);
    }

    /**
     * Completes floating-point staging ownership for precompiled engine callers.
     * @param buffer former allocation, including null
     */
    public static void memFree(FloatBuffer buffer) {
        /*
         * Preserve the desktop method descriptor while JavaScript owns the storage.
         */
        memFree((Buffer) buffer);
    }

}
