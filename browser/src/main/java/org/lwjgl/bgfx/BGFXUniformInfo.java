package org.lwjgl.bgfx;

import org.lwjgl.system.MemoryStack;

/**
 * Reusable uniform reflection result copied from bgfx's native shader metadata.
 */
public final class BGFXUniformInfo {
    private String name; // Reflected UTF-8 name copied into Java storage.
    private int type; // bgfx uniform element type.
    private short count; // Declared array length.

    /**
     * Creates empty reflection storage.
     */
    private BGFXUniformInfo() {
    }

    /**
     * Creates reusable reflection storage owned by the Java caller.
     * @param stack lexical scratch lifetime
     * @return independent reflection result
     */
    public static BGFXUniformInfo calloc(MemoryStack stack) {
        /*
         * Reflection results contain Java fields rather than native struct storage.
         */
        return new BGFXUniformInfo();
    }

    /**
     * Copies one native reflection result.
     * @param name uniform name
     * @param type element type
     * @param count array length
     */
    void set(String name, int type, int count) {
        this.name = name;
        this.type = type;
        this.count = (short) count;
    }

    /**
     * Returns the copied uniform name.
     * @return reflected name
     */
    public String nameString() { return name; }

    /**
     * Returns the native uniform type.
     * @return bgfx uniform type
     */
    public int type() { return type; }

    /**
     * Returns the declared array length.
     * @return unsigned native array length
     */
    public short num() { return count; }
}
