package valthorne.graphics.font.adaptive;

/**
 * Prepared native pixels transferred from a worker to the graphics owner.
 * The receiver frees the image pixels after upload or discard.
 * @param key destination cache key
 * @param image owned raster image, possibly containing an empty outline
 */
record FontGlyphResult(long key, FontRaster image) {
}
