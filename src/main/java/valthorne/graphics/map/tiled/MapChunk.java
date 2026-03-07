package valthorne.graphics.map.tiled;

/**
 * Represents a rectangular chunk of a map consisting of tiles.
 * <p>
 * A map chunk stores its position, size, and a one-dimensional array of global tile IDs
 * which define the tiles in the chunk. It is typically used to represent a portion of a
 * map layer. The {@code globalTileIDs} array length is expected to be equal to {@code width * height}.
 * If the provided {@code globalTileIDs} is {@code null}, it is initialized to an array of zeros
 * with the size {@code width * height}.
 * <p>
 * Record Components:
 * - {@code x}: The x-coordinate of the top-left corner of the chunk in tile units.
 * - {@code y}: The y-coordinate of the top-left corner of the chunk in tile units.
 * - {@code width}: The width of the chunk in tiles.
 * - {@code height}: The height of the chunk in tiles.
 * - {@code globalTileIDs}: One-dimensional array of global tile IDs corresponding to tiles in the chunk.
 */
public record MapChunk(int x, int y, int width, int height, int[] globalTileIDs) {

    /**
     * Initializes the {@code MapChunk} record component. If the provided {@code globalTileIDs} array is {@code null},
     * it is initialized to a new array of zeros with a size determined by {@code width * height}.
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