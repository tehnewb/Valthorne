package valthorne.browser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBuffer;
import org.teavm.jso.JSBufferType;

/**
 * Marshals the engine's graphics commands into the browser's bgfx WebAssembly
 * module. The module owns the actual WebGL device and GPU resources. Arguments
 * share fixed scratch buffers on the application coroutine; calls must not overlap
 * or suspend. Uploads are copied to bgfx-owned memory before scratch is reused.
 */
public final class BrowserNative {
    /**
     * Native-backed empty view for commands with no byte upload.
     */
    private static final ByteBuffer empty = ByteBuffer.allocateDirect(0);

    /**
     * Reused integer argument and result storage, sized for framebuffer attachments.
     */
    private static final IntBuffer words = ByteBuffer.allocateDirect(128)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer();
    /**
     * Reused scalar and matrix storage; the engine limits uniform uploads to 64 KiB.
     */
    private static final FloatBuffer values = ByteBuffer.allocateDirect(65536)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();

    /**
     * Populated floating-point argument range for the next native command.
     */
    private static int valueCount;

    /**
     * Prevents creating stateless bridge instances.
     */
    private BrowserNative() {
    }

    /**
     * Supplies one integer argument without allocating a request object.
     *
     * @param index argument word
     * @param value copied numeric value
     */
    public static void word(int index, int value) {
        /*
         * Absolute writes preserve the scratch buffer's position for JavaScript views.
         */
        words.put(index, value);
    }

    /**
     * Reads one integer result written by the native command.
     *
     * @param index result word
     * @return native result
     */
    public static int word(int index) {
        /*
         * Results are valid only until the next command reuses the scratch storage.
         */
        return words.get(index);
    }

    /**
     * Supplies both halves of a native 64-bit flag mask without numeric rounding.
     *
     * @param index first argument word
     * @param value complete flag mask
     */
    public static void wide(int index, long value) {
        /*
         * JavaScript numbers cannot represent every 64-bit integer exactly.
         */
        words.put(index, (int) value);
        words.put(index + 1, (int) (value >>> 32));
    }

    /**
     * Supplies one scalar or matrix component.
     *
     * @param index component index
     * @param value copied component
     */
    public static void value(int index, float value) {
        /*
         * Matrix and vector uploads share one bounded buffer throughout a frame.
         */
        values.put(index, value);
        valueCount = Math.max(valueCount, index + 1);
    }

    /**
     * Executes a command with no byte upload.
     *
     * @param operation native operation identifier
     * @return command-specific integer result
     */
    public static int call(int operation) {
        /*
         * Empty uploads avoid creating temporary zero-length Java buffers.
         */
        int count = valueCount;
        valueCount = 0;
        return execute(operation, words, values, empty, 0, 0, count);
    }

    /**
     * Executes a command borrowing the current byte-buffer range.
     *
     * @param operation native operation identifier
     * @param bytes upload source; position and limit are preserved
     * @return command-specific integer result
     */
    public static int call(int operation, ByteBuffer bytes) {
        /*
         * Passing a typed view lets JavaScript copy the contiguous upload in bulk.
         */
        int count = valueCount;
        valueCount = 0;
        return execute(operation, words, values, bytes, bytes.position(), bytes.remaining(), count);
    }

    /**
     * Copies a native diagnostic or reflected name into an ordinary Java string.
     *
     * @param address native UTF-8 string address
     * @return independent Java string
     */
    @JSBody(params = "address", script = "return globalThis.valthorneNative.UTF8ToString(address);")
    public static native String string(int address);

    /**
     * Runs one synchronous native command through the initialized browser host.
     *
     * @param operation command identifier
     * @param words integer scratch view
     * @param values floating-point scratch view
     * @param bytes optional byte-upload view
     * @param offset first byte in the view
     * @param length upload byte count
     * @param count populated floating-point components
     * @return native command result
     */
    @JSBody(params = {"operation", "words", "values", "bytes", "offset", "length", "count"}, script = "return globalThis.valthorneHost.nativeCommand(operation,words,values,bytes,offset,length,count);")
    private static native int execute(int operation, @JSBuffer(JSBufferType.INT32) IntBuffer words, @JSBuffer(JSBufferType.FLOAT32) FloatBuffer values, @JSBuffer(JSBufferType.UINT8) ByteBuffer bytes, int offset, int length, int count);
    /**
     * Copies native decoder output into a Java-owned upload buffer.
     * @param address first native byte
     * @param destination direct Java destination
     * @param size byte count
     */
    @JSBody(params = {"address", "destination", "size"}, script = "destination.set(globalThis.valthorneNative.HEAPU8.subarray(address,address+size));")
    public static native void readBytes(int address, @JSBuffer(JSBufferType.UINT8) ByteBuffer destination, int size);

    /**
     * Converts a native font alpha atlas directly into Java-owned RGBA pixels.
     * @param address first native alpha byte
     * @param destination direct RGBA destination with four bytes per pixel
     * @param pixels checked pixel count
     */
    @JSBody(params = {"address", "destination", "pixels"}, script = "var source=globalThis.valthorneNative.HEAPU8, offset=0; for (var i=0;i<pixels;i++) { destination[offset++]=255; destination[offset++]=255; destination[offset++]=255; destination[offset++]=source[address+i]; }")
    static native void readAlphaRgba(int address, @JSBuffer(JSBufferType.UINT8) ByteBuffer destination, int pixels);

    /**
     * Executes a UTF-8 string command without allocating Java encoding buffers.
     * @param operation native operation identifier
     * @param text input string
     * @return native command result
     */
    public static int callString(int operation, String text) {
        /*
         * TextEncoder writes into the host's retained upload range, which is safe
         * to reuse after the synchronous native text or font-face call returns.
         */
        int count = valueCount;
        valueCount = 0;
        return executeString(operation, words, values, text, count);
    }

    /**
     * Encodes text directly into the native module's reusable command staging.
     * @param operation native operation
     * @param words integer scratch
     * @param values floating-point scratch
     * @param text input string
     * @param count populated floating-point range
     * @return native command result
     */
    @JSBody(params = {"operation", "words", "values", "text", "count"}, script = "return globalThis.valthorneHost.nativeStringCommand(operation,words,values,text,count);")
    private static native int executeString(int operation, @JSBuffer(JSBufferType.INT32) IntBuffer words, @JSBuffer(JSBufferType.FLOAT32) FloatBuffer values, String text, int count);

    /**
     * Uploads a borrowed floating-point range without creating Java staging views.
     * @param operation native command identifier
     * @param source contiguous floating-point input
     * @return native command result
     */
    public static int callFloats(int operation, FloatBuffer source) {
        /*
         * The host borrows the existing typed array and copies its byte range into
         * bgfx-owned storage before the source can be reused by the application.
         */
        int count = valueCount;
        valueCount = 0;
        return executeFloats(operation, words, values, source, source.position(), source.remaining(), count);
    }

    /**
     * Borrows a floating-point typed view for a native byte upload.
     * @param operation command identifier
     * @param words integer command scratch
     * @param values scalar command scratch
     * @param source floating-point source view
     * @param offset first source element
     * @param length source elements
     * @param count populated scalar arguments
     * @return native command result
     */
    @JSBody(params = {"operation", "words", "values", "source", "offset", "length", "count"}, script = "return globalThis.valthorneHost.nativeCommand(operation,words,values,new Uint8Array(source.buffer,source.byteOffset,source.byteLength),offset*4,length*4,count);")
    private static native int executeFloats(int operation, @JSBuffer(JSBufferType.INT32) IntBuffer words, @JSBuffer(JSBufferType.FLOAT32) FloatBuffer values, @JSBuffer(JSBufferType.FLOAT32) FloatBuffer source, int offset, int length, int count);

}
