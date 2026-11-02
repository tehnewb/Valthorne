package org.lwjgl.system;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

/**
 * Lexical browser scratch scope for shared engine initialization and reflection.
 * Allocations are Java-managed; registered native owners close in reverse order.
 * Scopes must remain on the application coroutine and must not cross async waits.
 */
public final class MemoryStack implements AutoCloseable {
    private final ArrayList<AutoCloseable> owners = new ArrayList<>(); // Native temporaries released with this scope.

    /**
     * Creates an empty lexical allocation scope.
     */
    private MemoryStack() {
    }

    /**
     * Opens a scratch scope for try-with-resources callers.
     * @return new lexical scope
     */
    public static MemoryStack stackPush() {
        /*
         * Browser scopes contain no native stack pointer and can unwind safely
         * when Java exceptions leave resource initialization partway through.
         */
        return new MemoryStack();
    }

    /**
     * Registers a temporary native resource for reverse-order cleanup.
     * @param owner temporary resource
     */
    public void own(AutoCloseable owner) { owners.add(owner); }

    /**
     * Allocates uninitialized byte scratch storage.
     * @param bytes required byte count
     * @return independent staging
     */
    public ByteBuffer malloc(int bytes) { return MemoryUtil.memAlloc(bytes); }

    /**
     * Allocates zero-filled byte scratch storage.
     * @param bytes required byte count
     * @return zero-filled staging
     */
    public ByteBuffer calloc(int bytes) { return MemoryUtil.memCalloc(bytes); }

    /**
     * Allocates short-element scratch storage.
     * @param count required elements
     * @return short staging view
     */
    public ShortBuffer mallocShort(int count) {
        return malloc(Math.multiplyExact(count, Short.BYTES))
                .asShortBuffer();
    }

    /**
     * Allocates integer-element scratch storage.
     * @param count required elements
     * @return integer staging view
     */
    public IntBuffer mallocInt(int count) {
        return malloc(Math.multiplyExact(count, Integer.BYTES))
                .asIntBuffer();
    }

    /**
     * Allocates floating-point scratch storage.
     * @param count required elements
     * @return floating-point staging view
     */
    public FloatBuffer mallocFloat(int count) {
        return malloc(Math.multiplyExact(count, Float.BYTES))
                .asFloatBuffer();
    }

    /**
     * Copies initial integer values into scratch storage.
     * @param values initial values
     * @return readable integer staging
     */
    public IntBuffer ints(int... values) {
        return mallocInt(values.length)
                .put(values)
                .flip();
    }

    /**
     * Copies initial floating-point values into scratch storage.
     * @param values initial values
     * @return readable floating-point staging
     */
    public FloatBuffer floats(float... values) {
        return mallocFloat(values.length)
                .put(values)
                .flip();
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (int i = owners.size() - 1; i >= 0; i--) {
            try {
                owners.get(i)
                        .close();
            } catch (Exception error) {
                if (failure == null) failure = new IllegalStateException("Browser scratch cleanup failed", error);
                else failure.addSuppressed(error);
            }
        }
        owners.clear();
        if (failure != null) throw failure;
    }
    /**
     * Allocates double-element scratch storage.
     * @param count required elements
     * @return double staging view
     */
    public DoubleBuffer mallocDouble(int count) {
        return malloc(Math.multiplyExact(count, Double.BYTES))
                .asDoubleBuffer();
    }

}
