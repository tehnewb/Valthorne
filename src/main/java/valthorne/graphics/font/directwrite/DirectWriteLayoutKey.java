package valthorne.graphics.font.directwrite;

/**
 * Font-local shaped-line cache identity. Inserted keys retain stable values;
 * a separate mutable lookup instance avoids allocations for repeated text.
 */
final class DirectWriteLayoutKey {
    private String text; // Exact UTF-16 text retained by an inserted key.
    private float size; // Logical DIPs per em.

    /**
     * Creates a reusable lookup or stable inserted key.
     *
     * @param text shaped contents
     * @param size logical em size
     */
    DirectWriteLayoutKey(String text, float size) {
        set(text, size);
    }

    /**
     * Updates a lookup key that is never inserted into the map itself.
     *
     * @param text shaped contents
     * @param size logical em size
     */
    void set(String text, float size) {
        this.text = text;
        this.size = size;
    }

    @Override
    public int hashCode() { return text.hashCode() * 31 + Float.floatToIntBits(size); }

    @Override
    public boolean equals(Object other) {
        return other instanceof DirectWriteLayoutKey key && size == key.size && text.equals(key.text);
    }
}
