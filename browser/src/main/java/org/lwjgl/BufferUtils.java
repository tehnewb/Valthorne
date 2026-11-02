package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.DoubleBuffer;
import java.nio.ShortBuffer;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * Creates Java-managed contiguous browser staging for shared engine algorithms.
 * Typed views use the platform byte order and upload without per-element boxing.
 */
public final class BufferUtils {
    /**
     * Prevents constructing buffer-allocation utility instances.
     */
    private BufferUtils() {
    }

    /**
     * Allocates byte staging.
     * @param count byte count
     * @return independent direct buffer
     */
    public static ByteBuffer createByteBuffer(int count) {
        /*
         * Shared engine upload paths consume a contiguous NIO typed-array view.
         */
        return MemoryUtil.memAlloc(count);
    }

    /**
     * Allocates float staging.
     * @param count element count
     * @return independent float view
     */
    public static FloatBuffer createFloatBuffer(int count) {
        /*
         * One byte allocation backs the complete typed upload range.
         */
        return createByteBuffer(Math.multiplyExact(count, Float.BYTES))
                .asFloatBuffer();
    }

    /**
     * Allocates integer staging.
     * @param count element count
     * @return independent integer view
     */
    public static IntBuffer createIntBuffer(int count) {
        /*
         * Preserve contiguous integer storage for band and layout data.
         */
        return createByteBuffer(Math.multiplyExact(count, Integer.BYTES))
                .asIntBuffer();
    }

    /**
     * Allocates double staging.
     * @param count element count
     * @return independent double view
     */
    public static DoubleBuffer createDoubleBuffer(int count) {
        /*
         * Pointer position queries reuse this typed staging without boxing.
         */
        return createByteBuffer(Math.multiplyExact(count, Double.BYTES))
                .asDoubleBuffer();
    }

    /**
     * Allocates short staging.
     * @param count element count
     * @return independent short view
     */
    public static ShortBuffer createShortBuffer(int count) {
        /*
         * Native handle arrays retain their unsigned sixteen-bit representation.
         */
        return createByteBuffer(Math.multiplyExact(count, Short.BYTES))
                .asShortBuffer();
    }

    /**
     * Allocates long staging.
     * @param count element count
     * @return independent long view
     */
    public static LongBuffer createLongBuffer(int count) {
        /*
         * Long-valued data remains exact in Java before crossing the native bridge.
         */
        return createByteBuffer(Math.multiplyExact(count, Long.BYTES))
                .asLongBuffer();
    }
}
