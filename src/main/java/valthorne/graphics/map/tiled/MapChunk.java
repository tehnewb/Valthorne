package valthorne.graphics.map.tiled;

/**
 * Represents a rectangular chunk of a map consisting of tiles.
 * <p>
 * A map chunk stores its position, size, and a one-dimensional array of global tile IDs
 * which define the tiles in the chunk. It is typically used to represent a portion of a
 * map layer. The {@code globalTileIDs} array length is expected to be equal to {@code width * height}.
 * A supplied array is retained directly and exposed by the generated accessor, so
 * component references are fixed but tile contents remain mutable. Array length and
 * dimensions are not validated. Null data allocates a zero-filled array whose length
 * is {@code Math.max(0, width * height)}, using ordinary integer multiplication.
 *
 * @param x top-left column in tile units
 * @param y top-left row in tile units
 * @param width chunk width in tiles
 * @param height chunk height in tiles
 * @param globalTileIDs row-major tile IDs, retained directly, or null to allocate empty data
 * @author Albert Beaupre
 */
public record MapChunk(int x, int y, int width, int height, int[] globalTileIDs) {

    /**
     * Initializes the {@code MapChunk} record component. If the provided {@code globalTileIDs} array is {@code null},
     * it is initialized to a new array of zeros with length {@code Math.max(0, width * height)}.
     * Non-null arrays are neither copied nor checked against the dimensions.
     *
     * @param x             The x-coordinate of the top-left corner of the chunk in tile units.
     * @param y             The y-coordinate of the top-left corner of the chunk in tile units.
     * @param width         The width of the chunk in tiles.
     * @param height        The height of the chunk in tiles.
     * @param globalTileIDs A one-dimensional array of global tile IDs defining the tiles in this chunk. If {@code null},
     *                      it is initialized to an array of zeros with a size equal to {@code width * height}.
     */
    public MapChunk {
        if (globalTileIDs == null)
            globalTileIDs = new int[Math.max(0, width * height)];
    }
}
