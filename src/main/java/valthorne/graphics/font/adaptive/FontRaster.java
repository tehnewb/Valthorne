package valthorne.graphics.font.adaptive;

import java.nio.ByteBuffer;

/**
 * Temporary R8 coverage or RGB8 MSDF image in bottom-up row order. Dimensions
 * count texels; bearings and texels-per-em scale locate the image in shared layout units.
 * The caller releases pixels after upload; empty outlines have null pixels.
 *
 * @param pixels owned native pixels, or null for an empty outline
 * @param width image width in texels
 * @param height image height in texels
 * @param left horizontal image origin in em units
 * @param bottom vertical image origin in em units
 * @param scale texels per em
 */
record FontRaster(ByteBuffer pixels, int width, int height, float left, float bottom, float scale) {
}
