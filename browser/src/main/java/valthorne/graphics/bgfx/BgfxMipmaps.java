package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.IMAGE_FREE;
import static valthorne.browser.BrowserOperation.MIPMAP_GENERATE;

/**
 * Generates RGBA8 mip chains with STB inside the browser's WebAssembly module.
 * One command borrows the base pixels, generates every level with independent
 * channels and box filtering, and copies only the resulting mip bytes back into
 * the retained Java upload. Native output is freed before returning, including
 * failures while copying. No Java per-pixel filtering or frame-loop work remains.
 * Calls are synchronous and confined to the application coroutine.
 */
final class BgfxMipmaps {
    /**
     * Prevents constructing stateless native command adapters.
     */
    private BgfxMipmaps() {
    }

    /**
     * Appends every downsampled level without changing the upload's cursor.
     *
     * @param upload validated storage containing base pixels at absolute offset zero
     * @param width validated positive base width
     * @param height validated positive base height
     * @throws IllegalStateException if native allocation or STB filtering fails
     */
    static void generate(ByteBuffer upload, int width, int height) {
        /*
         * Upload only the populated base image. Bulk views avoid materializing
         * Java arrays or copying the base image back from WebAssembly.
         */
        if (width == 1 && height == 1) return;
        int baseSize = width * height * 4;
        ByteBuffer source = upload.duplicate()
                .position(0)
                .limit(baseSize);
        BrowserNative.word(0, width);
        BrowserNative.word(1, height);
        BrowserNative.word(2, upload.limit() - baseSize);
        int pointer = BrowserNative.call(MIPMAP_GENERATE, source);
        if (pointer == 0) throw new IllegalStateException("Could not generate texture mipmaps");
        try {
            ByteBuffer destination = upload.duplicate()
                    .position(baseSize)
                    .slice();
            BrowserNative.readBytes(pointer, destination, destination.remaining());
        } finally {
            BrowserNative.word(0, pointer);
            BrowserNative.call(IMAGE_FREE);
        }
    }
}
