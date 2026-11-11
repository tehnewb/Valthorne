package org.lwjgl.bgfx;

import java.nio.ByteBuffer;
import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.COPY;

/**
 * Holds a writable Java upload or one bgfx-owned immutable upload pointer.
 * The native consumer takes ownership exactly once, as on the desktop binding.
 */
public final class BGFXMemory {
    private final ByteBuffer bytes; // Writable staging, or null after a direct native copy.
    private int pointer; // bgfx-owned memory descriptor, zero until staging is uploaded.

    /**
     * Wraps a new writable staging buffer.
     * @param bytes caller-visible staging
     */
    BGFXMemory(ByteBuffer bytes) {
        this.bytes = bytes;
    }

    /**
     * Wraps an already copied native upload.
     * @param pointer native bgfx memory descriptor
     */
    BGFXMemory(int pointer) {
        this.bytes = null;
        this.pointer = pointer;
    }

    /**
     * Returns the writable staging with the requested visible byte count.
     * @param size number of writable bytes
     * @return borrowed staging
     */
    public ByteBuffer data(int size) {
        if (bytes == null || size < 0 || size > bytes.capacity()) throw new IllegalStateException("Upload has no writable staging");
        bytes.limit(size);
        return bytes;
    }

    /**
     * Copies writable staging once and returns its native upload descriptor.
     * @return bgfx-owned upload pointer
     */
    int pointer() {
        if (pointer == 0) {
            bytes.clear();
            pointer = BrowserNative.call(COPY, bytes);
            if (pointer == 0) throw new IllegalStateException("bgfx upload allocation failed");
        }
        return pointer;
    }
    /**
     * Borrows the writable upload staging without changing its current cursor.
     * @return writable staging
     */
    public ByteBuffer data() {
        if (bytes == null) throw new IllegalStateException("Upload has no writable staging");
        return bytes;
    }

}
