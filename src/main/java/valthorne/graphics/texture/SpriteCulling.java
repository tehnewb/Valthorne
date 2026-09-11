package valthorne.graphics.texture;

/**
 * Allocation-free conservative clip test for a sprite quad on the world XY plane
 * at Z zero. The test rotates its four corners around a local origin, translates
 * them into world space, and applies a column-major homogeneous transform. It
 * rejects a quad only when all four corners are outside one common OpenGL clip
 * plane, retaining potential intersections without perspective division.
 *
 * <p>A positive result means potentially visible, not an exact intersection or
 * guaranteed pixel coverage. A small tolerance proportional to clip-coordinate
 * magnitude retains near-boundary quads despite floating-point roundoff. The
 * helper owns no state; callers supply current matrix coefficients and already
 * computed sine/cosine values from the same rotation used for drawing.</p>
 *
 * @author Albert Beaupre
 */
public final class SpriteCulling {
    /**
     * Prevents construction of this stateless helper. All inputs are supplied
     * explicitly to the static visibility test.
     */
    private SpriteCulling() {}

    /**
     * Tests the transformed rectangle against the six homogeneous clip limits
     * {@code -w <= x, y, z <= w}. For each corner, local coordinates are offset
     * by the supplied origin, rotated, and added to (x + originX, y + originY).
     * The matrix then transforms that world XY position with Z zero.
     *
     * <p>Outside-plane bits are intersected across corners. Once no common outside
     * plane remains, the method returns true immediately. Each corner uses a
     * tolerance of 1e-6 times one plus the sum of the absolute clip coordinates,
     * including homogeneous W. No input is modified or copied into an array.</p>
     *
     * <p>Inputs are not validated. Supply at least sixteen matrix coefficients
     * and finite coordinates and trigonometric values. Signed extents are processed
     * as supplied, and degenerate quads are not separately rejected. Invalid numeric
     * values do not provide a meaningful visibility guarantee.</p>
     *
     * @param m       the column-major world-to-clip matrix with at least sixteen entries
     * @param x       the unrotated rectangle's world X position
     * @param y       the unrotated rectangle's world Y position
     * @param w       the rectangle's local horizontal extent
     * @param h       the rectangle's local vertical extent
     * @param originX the rotation pivot's local X offset
     * @param originY the rotation pivot's local Y offset
     * @param sin     the sine of the sprite rotation angle
     * @param cos     the cosine of the same rotation angle
     * @return false if a common clip plane rejects every corner; true otherwise
     * @throws NullPointerException           if m is null
     * @throws ArrayIndexOutOfBoundsException if m has fewer than sixteen entries
     */
    public static boolean visible(float[] m, float x, float y, float w, float h, float originX, float originY, float sin, float cos) {
        int outside = 63;
        for (int corner = 0; corner < 4; corner++) {
            float lx = ((corner & 1) == 0 ? 0 : w) - originX, ly = ((corner & 2) == 0 ? 0 : h) - originY;
            float wx = x + originX + lx * cos - ly * sin, wy = y + originY + lx * sin + ly * cos;
            float cx = m[0] * wx + m[4] * wy + m[12], cy = m[1] * wx + m[5] * wy + m[13], cz = m[2] * wx + m[6] * wy + m[14], cw = m[3] * wx + m[7] * wy + m[15];
            float e = 1e-6f * (1 + Math.abs(cx) + Math.abs(cy) + Math.abs(cz) + Math.abs(cw));
            int mask = (cx < -cw - e ? 1 : 0) | (cx > cw + e ? 2 : 0) | (cy < -cw - e ? 4 : 0) | (cy > cw + e ? 8 : 0) | (cz < -cw - e ? 16 : 0) | (cz > cw + e ? 32 : 0);
            outside &= mask;
            if (outside == 0) return true;
        }
        return outside == 0;
    }
}
