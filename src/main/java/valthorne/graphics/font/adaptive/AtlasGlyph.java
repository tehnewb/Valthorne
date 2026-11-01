package valthorne.graphics.font.adaptive;

/**
 * Immutable atlas location and normalized draw rectangle. Empty glyphs retain
 * no page. Retained text stores glyph indices rather than these evictable entries.
 * @param page borrowed page, null for whitespace
 * @param width image width in em units
 * @param height image height in em units
 * @param left image left in em units
 * @param bottom image bottom in em units
 * @param u0 left texture coordinate
 * @param v0 bottom texture coordinate
 * @param u1 right texture coordinate
 * @param v1 top texture coordinate
 * @param provisional whether this scalable coverage image awaits prepared final-quality pixels
 */
record AtlasGlyph(FontAtlasPage page, float width, float height, float left, float bottom, float u0, float v0, float u1, float v1, boolean provisional) {
}
