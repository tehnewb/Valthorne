package valthorne.graphics.bgfx;

/**
 * Retains one reflected shader uniform's values between deferred draw submissions.
 * Native handles are borrowed from the owning program's retained shader stages;
 * the shader owns this metadata and its primitive value array.
 */
final class BgfxUniform {
    final String name; // Exact reflected shader name.
    final short handle; // Borrowed native uniform handle.
    final int type; // bgfx sampler, vector or matrix type.
    final int count; // Number of array elements declared in the shader.
    final float[] values; // Reused uniform values; empty for samplers.
    final boolean viewport; // Whether the coordinator supplies this reserved viewport remap.
    int unit; // Texture slot assigned to a sampler.
    int stages; // Bit mask of compiled sampler registers consuming the selected texture unit.
    boolean owned; // Whether an explicitly declared load-only sampler needs a matching destroy.

    /**
     * Stores reflected metadata and allocates reusable numeric value storage.
     *
     * @param name reflected name
     * @param handle native uniform reference retained by the program
     * @param type native uniform type
     * @param count declared array length
     * @param components float components per array element, or zero for a sampler
     */
    BgfxUniform(String name, short handle, int type, int count, int components) {
        this.name = name;
        this.handle = handle;
        this.type = type;
        this.count = count;
        values = new float[count * components];
        viewport = name.equals("u_viewportAdjust");
    }
}
