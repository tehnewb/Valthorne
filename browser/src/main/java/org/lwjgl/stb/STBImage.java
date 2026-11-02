package org.lwjgl.stb;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryUtil;
import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.*;

/**
 * Decodes encoded images with STB inside the browser WebAssembly module. Output
 * is copied into Java-owned staging and native decoder storage is freed before
 * returning, preserving the shared TextureData ownership contract.
 */
public final class STBImage {
    /**
     * Prevents constructing stateless decoder bindings.
     */
    private STBImage() {
    }

    /**
     * Selects image-row orientation for subsequent synchronous decodes.
     * @param flip whether to flip vertically
     */
    public static void stbi_set_flip_vertically_on_load(boolean flip) {
        /*
         * Decoding remains confined to the application coroutine, matching STB's
         * global orientation setting without concurrent mutation.
         */
        BrowserNative.word(0, flip ? 1 : 0);
        BrowserNative.call(IMAGE_FLIP);
    }

    /**
     * Decodes the current encoded byte range into owned Java staging.
     * @param source encoded bytes
     * @param width decoded width destination
     * @param height decoded height destination
     * @param channels original channel-count destination
     * @param desiredChannels requested output channels, one through four
     * @return decoded bytes, or null on decoder failure
     */
    public static ByteBuffer stbi_load_from_memory(ByteBuffer source, IntBuffer width, IntBuffer height, IntBuffer channels, int desiredChannels) {
        /*
         * Copy the native result once, then release decoder memory immediately;
         * render uploads and device restoration retain the ordinary Java buffer.
         */
        if (desiredChannels < 1 || desiredChannels > 4) throw new IllegalArgumentException("Output channels must be between one and four");
        BrowserNative.word(0, desiredChannels);
        int pointer = BrowserNative.call(IMAGE_DECODE, source);
        if (pointer == 0) return null;
        try {
            int w = BrowserNative.word(0);
            int h = BrowserNative.word(1);
            int count = BrowserNative.word(2);
            int size = Math.multiplyExact(Math.multiplyExact(w, h), desiredChannels);
            ByteBuffer bytes = MemoryUtil.memAlloc(size);
            BrowserNative.readBytes(pointer, bytes, size);
            width.put(width.position(), w);
            height.put(height.position(), h);
            channels.put(channels.position(), count);
            return bytes;
        } finally {
            BrowserNative.word(0, pointer);
            BrowserNative.call(IMAGE_FREE);
        }
    }

    /**
     * Copies the last native decoder failure message.
     * @return diagnostic message, or null when STB has no diagnostic
     */
    public static String stbi_failure_reason() {
        /*
         * Copy diagnostics before another decoder call can overwrite STB state.
         */
        int pointer = BrowserNative.call(IMAGE_FAILURE);
        return pointer == 0 ? null : BrowserNative.string(pointer);
    }

    /**
     * Releases Java ownership of decoded staging through the shared buffer API.
     * @param pixels former decoded buffer
     */
    public static void stbi_image_free(ByteBuffer pixels) {
        /*
         * Native decoder storage was released before the Java buffer was returned.
         */
        MemoryUtil.memFree(pixels);
    }
}
