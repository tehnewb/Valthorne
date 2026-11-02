package org.lwjgl.bgfx;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import org.lwjgl.system.MemoryStack;
import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.*;

/**
 * Owns a native packed vertex declaration and caches its byte offsets in Java.
 * The declaration survives device replacement. Stack-scoped declarations release
 * their native storage when the owning scratch scope closes.
 */
public final class BGFXVertexLayout implements AutoCloseable {
    private int pointer; // Owned native declaration; zero after release.
    private short stride; // Current packed record size.
    private final ShortBuffer offsets = ShortBuffer.allocate(BGFX.BGFX_ATTRIB_COUNT); // Cached native attribute offsets.

    /**
     * Allocates native declaration storage without creating a GPU object.
     */
    private BGFXVertexLayout() {
        pointer = BrowserNative.call(LAYOUT_CREATE);
        if (pointer == 0) throw new OutOfMemoryError("Could not allocate vertex layout");
    }

    /**
     * Allocates an independently owned declaration.
     * @return caller-owned layout
     */
    public static BGFXVertexLayout calloc() {
        /*
         * Native layout objects contain renderer-specific encodings and remain
         * authoritative for attribute sizes and padding.
         */
        return new BGFXVertexLayout();
    }

    /**
     * Allocates a declaration released with its lexical scratch scope.
     * @param stack owning scope
     * @return scope-owned layout
     */
    public static BGFXVertexLayout calloc(MemoryStack stack) {
        /*
         * Register the native owner so exceptional exits release temporary layouts.
         */
        BGFXVertexLayout layout = new BGFXVertexLayout();
        stack.own(layout);
        return layout;
    }

    /**
     * Returns the native storage address for internal graphics bindings.
     * @return nonzero native address
     */
    int pointer() {
        if (pointer == 0) throw new IllegalStateException("Vertex layout is closed");
        return pointer;
    }

    /**
     * Clears the declaration for the current browser renderer.
     */
    void begin() {
        BrowserNative.word(0, pointer());
        BrowserNative.call(LAYOUT_BEGIN);
        stride = 0;
        for (int i = 0; i < offsets.capacity(); i++) offsets.put(i, (short) 0);
    }

    /**
     * Appends a packed attribute and records its native byte offset.
     * @param attribute bgfx attribute identifier
     * @param count element count
     * @param type scalar type
     * @param normalized whether integers are normalized
     * @param integer whether the shader expects integer input
     */
    void add(int attribute, byte count, int type, boolean normalized, boolean integer) {
        offsets.put(attribute, stride);
        BrowserNative.word(0, pointer());
        BrowserNative.word(1, attribute);
        BrowserNative.word(2, count);
        BrowserNative.word(3, type);
        BrowserNative.word(4, normalized ? 1 : 0);
        BrowserNative.word(5, integer ? 1 : 0);
        stride = (short) BrowserNative.call(LAYOUT_ADD);
    }

    /**
     * Appends explicit padding bytes.
     * @param bytes padding count
     */
    void skip(int bytes) {
        BrowserNative.word(0, pointer());
        BrowserNative.word(1, bytes);
        stride = (short) BrowserNative.call(LAYOUT_SKIP);
    }

    /**
     * Finalizes the declaration's native hash and packed size.
     */
    void end() {
        BrowserNative.word(0, pointer());
        stride = (short) BrowserNative.call(LAYOUT_END);
    }

    /**
     * Reports whether an attribute is present.
     * @param attribute bgfx attribute identifier
     * @return whether the attribute exists
     */
    boolean has(int attribute) {
        BrowserNative.word(0, pointer());
        BrowserNative.word(1, attribute);
        return BrowserNative.call(LAYOUT_HAS) != 0;
    }

    /**
     * Decodes a native attribute into caller-owned scratch views.
     * @param attribute bgfx attribute identifier
     * @param count scalar count destination
     * @param type scalar type destination
     * @param normalized normalization destination
     * @param integer integer-input destination
     */
    void decode(int attribute, ByteBuffer count, IntBuffer type, ByteBuffer normalized, ByteBuffer integer) {
        BrowserNative.word(0, pointer());
        BrowserNative.word(1, attribute);
        BrowserNative.call(LAYOUT_DECODE);
        count.put(0, (byte) BrowserNative.word(0));
        type.put(0, BrowserNative.word(1));
        normalized.put(0, (byte) BrowserNative.word(2));
        integer.put(0, (byte) BrowserNative.word(3));
    }

    /**
     * Copies a retained declaration without borrowing the source's lifetime.
     * @param source declaration to copy
     * @return this declaration
     */
    public BGFXVertexLayout set(BGFXVertexLayout source) {
        BrowserNative.word(0, pointer());
        BrowserNative.word(1, source.pointer());
        stride = (short) BrowserNative.call(LAYOUT_COPY);
        for (int i = 0; i < offsets.capacity(); i++) offsets.put(i, source.offsets.get(i));
        return this;
    }

    /**
     * Returns the current unsigned packed record size.
     * @return native record stride
     */
    public short stride() { return stride; }

    /**
     * Borrows the cached per-attribute byte offsets.
     * @return caller-read-only offset buffer
     */
    public ShortBuffer offset() { return offsets; }

    /**
     * Releases declaration storage once, independently of GPU resource shutdown.
     */
    public void free() {
        if (pointer == 0) return;
        BrowserNative.word(0, pointer);
        BrowserNative.call(LAYOUT_FREE);
        pointer = 0;
    }

    @Override
    public void close() { free(); }
    /**
     * Returns the native declaration address for internal browser submission.
     * @return unsigned native declaration address
     */
    public long address() { return Integer.toUnsignedLong(pointer()); }

}
