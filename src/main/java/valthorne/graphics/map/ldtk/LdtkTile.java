package valthorne.graphics.map.ldtk;

/**
 * Describes one LDtk tile placement. Destination and source coordinates retain
 * LDtk's top-left pixel convention, while {@code flipBits} encodes horizontal
 * and vertical mirroring and {@code alpha} carries per-tile opacity.
 *
 * @param x destination x-coordinate within the layer
 * @param y destination y-coordinate within the layer
 * @param sourceX source x-coordinate within the tileset
 * @param sourceY source y-coordinate within the tileset
 * @param tileId LDtk tile identifier
 * @param flipBits LDtk bit mask for horizontal and vertical mirroring
 * @param alpha per-tile opacity, normally in the inclusive range zero to one
 */
public record LdtkTile(int x, int y, int sourceX, int sourceY, int tileId, int flipBits, float alpha) {
    /**
     * Reports whether LDtk requests horizontal mirroring.
     *
     * @return {@code true} when the horizontal flip bit is set
     */
    public boolean flipX() {
        return (flipBits & 1) != 0;
    }

    /**
     * Reports whether LDtk requests vertical mirroring.
     *
     * @return {@code true} when the vertical flip bit is set
     */
    public boolean flipY() {
        return (flipBits & 2) != 0;
    }
}
