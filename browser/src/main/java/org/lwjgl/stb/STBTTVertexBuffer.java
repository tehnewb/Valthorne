package org.lwjgl.stb;

/**
 * Borrows a native STB outline array until the corresponding FreeShape call.
 * Indexed traversal reuses one vertex object and allocates nothing per vertex.
 */
public final class STBTTVertexBuffer {
    private int pointer; // Owned native shape array; zero after release.
    private final int count; // Number of valid outline commands.
    private final STBTTVertex vertex = new STBTTVertex(); // Reused indexed vertex view.

    /**
     * Wraps a newly allocated native shape.
     * @param pointer first native vertex
     * @param count number of vertices
     */
    STBTTVertexBuffer(int pointer, int count) { this.pointer = pointer; this.count = count; }

    /**
     * Returns the number of valid outline vertices.
     * @return exclusive vertex limit
     */
    public int limit() { return count; }

    /**
     * Copies one native vertex into the buffer's reusable view.
     * @param index requested vertex
     * @return borrowed view, overwritten by the next get
     */
    public STBTTVertex get(int index) {
        if (pointer == 0) throw new IllegalStateException("Glyph outline is released");
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
        vertex.read(pointer, index);
        return vertex;
    }

    /**
     * Returns the native shape address before release.
     * @return native shape pointer, or zero after release
     */
    int pointer() { return pointer; }

    /**
     * Marks the native shape released without changing reusable vertex metadata.
     */
    void released() { pointer = 0; }
}
