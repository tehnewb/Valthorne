package valthorne.graphics.font.adaptive;

/**
 * Immutable CPU preparation request crossing from the font owner to a worker.
 * @param key owner cache key identifying glyph, raster size and hinting policy
 * @param glyph shaped OpenType glyph index
 * @param raster physical coverage size, or zero for MSDF
 * @param hinting coverage policy; ignored for MSDF
 * @param priority whether visible text should precede speculative prewarming
 */
record FontGlyphRequest(long key, int glyph, int raster, FontHinting hinting, boolean priority) {
}
