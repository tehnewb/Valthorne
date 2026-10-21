package valthorne.ui;

import valthorne.graphics.font.slug.SlugFont;

/**
 * Identity/physical-size key for root-owned hinted Slug views. A mutable lookup
 * instance avoids allocating a key on stable draws; only copied insertion keys
 * enter the map. Physical sizes are stored in deterministic 26.6 pixel units.
 */
final class SlugHintKey {
    private SlugFont font; // Borrowed source identity; never replaced on an inserted key.
    private int size; // Physical em size in sixty-fourths of a pixel.

    /**
     * Creates an empty reusable lookup key.
     */
    SlugHintKey() {}

    /**
     * Copies a lookup into a stable insertion key.
     *
     * @param source current lookup values
     */
    SlugHintKey(SlugHintKey source) { set(source.font, source.size); }

    /**
     * Updates only the noninserted lookup instance.
     *
     * @param font exact source identity
     * @param size physical 26.6 em size
     */
    void set(SlugFont font, int size) {
        this.font = font;
        this.size = size;
    }

    @Override
    public int hashCode() { return System.identityHashCode(font) * 31 + size; }

    @Override
    public boolean equals(Object other) { return other instanceof SlugHintKey key && font == key.font && size == key.size; }
}
