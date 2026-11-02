package org.lwjgl.stb;

import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import valthorne.browser.BrowserFontOwner;
import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.FONT_CREATE;

/**
 * Retains a native STB parser with GC-managed ownership of its encoded font copy.
 * CPU kerning remains valid after graphics disposal, matching shared font metrics.
 */
public final class STBTTFontinfo {
    private BrowserFontOwner owner; // GC-owned native font token, null before successful initialization.

    /**
     * Creates an uninitialized parser descriptor.
     */
    private STBTTFontinfo() {
    }

    /**
     * Creates an uninitialized Java font descriptor.
     * @return new descriptor
     */
    public static STBTTFontinfo create() {
        /*
         * Native allocation is delayed until encoded bytes have been supplied.
         */
        return new STBTTFontinfo();
    }

    /**
     * Parses an owned native copy of the encoded bytes.
     * @param bytes source font bytes
     * @return whether parsing succeeded
     */
    boolean initialize(ByteBuffer bytes) {
        int pointer = BrowserNative.call(FONT_CREATE, bytes);
        if (pointer == 0) return false;
        owner = own(pointer);
        return true;
    }

    /**
     * Returns the native parser while retaining its lifetime owner.
     * @return initialized parser address
     */
    int pointer() {
        if (owner == null) throw new IllegalStateException("Font parser is not initialized");
        return owner.getPointer();
    }

    /**
     * Registers native font ownership with the browser's allocation finalizer.
     * @param pointer native parser address
     * @return independent lifetime token
     */
    @JSBody(params = "pointer", script = "return globalThis.valthorneHost.ownFont(pointer);")
    private static native BrowserFontOwner own(int pointer);
}
