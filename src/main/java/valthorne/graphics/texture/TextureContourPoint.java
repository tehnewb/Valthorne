package valthorne.graphics.texture;

/**
 * Immutable integer pixel-edge coordinate used as a graph key during boundary
 * stitching. Equality and hashing compare coordinate values.
 * <p>Pixel-edge coordinates identify corners between image pixels rather than pixel-center
 * samples. Value equality lets independently created boundary segments meet at a
 * shared graph vertex.</p>
 *
 * @author Albert Beaupre
 */
final class TextureContourPoint {
    final int x; // Horizontal pixel-edge coordinate.
    final int y; // Vertical pixel-edge coordinate.

    /**
     * Stores an integer pixel-edge coordinate.
     *
     * @param x horizontal coordinate
     * @param y vertical coordinate
     */
    TextureContourPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof TextureContourPoint other))
            return false;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}

