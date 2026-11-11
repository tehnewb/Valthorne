package valthorne.android.internal;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.Buffer;

/**
 * Transfers the shared graphics command protocol to native bgfx, NanoVG, STB and
 * font libraries through JNI. CPU asset workers have independent reusable scratch;
 * graphics commands remain confined to the application render thread. Uploads are
 * copied into bgfx-owned memory before a call returns. Resource tokens preserve
 * full-width ARM64 pointers inside the native registry.
 */
public final class AndroidNative {
    /**
     * Independently retained staging for each thread using native assets or graphics.
     */
    private static final ThreadLocal<AndroidNativeScratch> scratch = ThreadLocal.withInitial(AndroidNativeScratch::new);

    static {
        System.loadLibrary("valthorne_android");
    }

    /**
     * Prevents constructing the stateless native boundary.
     */
    private AndroidNative() {
    }

    /**
     * Writes one integer argument into the current thread's command.
     * @param index argument slot
     * @param value raw argument bits
     */
    public static void word(int index, int value) { scratch.get().words.put(index, value); }

    /**
     * Reads a result before the current thread submits another command.
     * @param index result slot
     * @return raw result bits
     */
    public static int word(int index) { return scratch.get().words.get(index); }

    /**
     * Writes a 64-bit flag mask as two exact words.
     * @param index first argument slot
     * @param value complete mask
     */
    public static void wide(int index, long value) { word(index, (int) value); word(index + 1, (int) (value >>> 32)); }

    /**
     * Writes one scalar or matrix component.
     * @param index scalar slot
     * @param value component value
     */
    public static void value(int index, float value) {
        AndroidNativeScratch state = scratch.get();
        state.values.put(index, value);
    }

    /**
     * Executes a command with no byte upload.
     * @param operation protocol operation
     * @return command result or resource token
     */
    public static int call(int operation) {
        AndroidNativeScratch state = scratch.get();
        return executeSimple(operation, state.wordsAddress, state.valuesAddress);
    }

    /**
     * Validates and caches a direct staging address once when its owning thread
     * initializes scratch. The thread-local buffer remains strongly reachable
     * throughout every native call; callers never expose or free this address.
     * @param buffer owned direct command storage
     * @return stable native address
     */
    static native long address(Buffer buffer);

    /**
     * Executes a command using already validated, thread-owned staging addresses.
     */
    private static native int executeSimple(int operation, long words, long values);

    /**
     * Borrows the current byte range without modifying its cursor.
     * @param operation protocol operation
     * @param source input bytes
     * @return command result or resource token
     */
    public static int call(int operation, ByteBuffer source) {
        AndroidNativeScratch state = scratch.get();
        int offset = source.position();
        int length = source.remaining();
        if (!source.isDirect()) {
            if (state.upload.capacity() < length) state.upload = ByteBuffer.allocateDirect(Math.max(length, state.upload.capacity() * 2));
            state.upload.clear();
            state.upload.put(0, source, offset, length);
            source = state.upload;
            offset = 0;
        }
        return execute(operation, state.words, state.values, source, offset, length);
    }

    /**
     * Uploads a contiguous floating-point range.
     * @param operation protocol operation
     * @param source direct float storage
     * @return command result
     */
    public static int callFloats(int operation, FloatBuffer source) {
        AndroidNativeScratch state = scratch.get();
        return executeFloats(operation, state.words, state.values, source, source.position(), source.remaining());
    }

    /**
     * Encodes UTF-16 text into reusable native UTF-8 staging.
     * @param operation protocol operation
     * @param text text or name to upload
     * @return command result
     */
    public static int callString(int operation, String text) {
        AndroidNativeScratch state = scratch.get();
        return executeString(operation, state.words, state.values, text);
    }

    /**
     * Executes one command using Java-owned direct argument buffers.
     */
    private static native int execute(int operation, IntBuffer words, FloatBuffer values, ByteBuffer bytes, int offset, int length);
    /**
     * Executes one command with a direct float upload.
     */
    private static native int executeFloats(int operation, IntBuffer words, FloatBuffer values, FloatBuffer bytes, int offset, int length);
    /**
     * Executes one command with standard UTF-8 text.
     */
    private static native int executeString(int operation, IntBuffer words, FloatBuffer values, String text);
    /**
     * Copies native bytes into independent direct Java storage.
     */
    public static native void readBytes(int token, ByteBuffer destination, int size);

    /**
     * Copies packed glyph metadata following an atlas's alpha pixels. The token
     * identifies the allocation; byte offsets are applied only after resolution.
     * @param token owned atlas allocation token
     * @param pixels number of alpha bytes preceding the packed metadata
     * @param destination direct buffer receiving packed glyph records
     * @param size number of metadata bytes to copy
     */
    static native void readAtlasGlyphs(int token, int pixels, ByteBuffer destination, int size);
    /**
     * Copies native coverage with a one-pixel transparent border.
     */
    public static native void readPaddedAlpha(int token, ByteBuffer destination, int width, int height);
    /**
     * Expands a native alpha atlas into white RGBA pixels.
     */
    static native void readAlphaRgba(int token, ByteBuffer destination, int pixels);
    /**
     * Reads a native UTF-8 diagnostic or reflected uniform name.
     */
    public static native String string(int token);
}
