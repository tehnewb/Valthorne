package valthorne.graphics.map.tiled;

/**
 * Represents a resolved tile in a tiled map system. A ResolvedTile contains information
 * about its position, graphical ID, local identifier within its tileset, the associated
 * tileset, and the tile's defined properties.
 *
 * @param tileX      The x-coordinate of the tile on the map grid.
 * @param tileY      The y-coordinate of the tile on the map grid.
 * @param rawGid     The raw global ID (GID) of the tile, including metadata bits.
 * @param gid        The global ID of the tile, with metadata bits stripped.
 * @param localId    The local ID of the tile within its associated tileset.
 * @param tileSet    The tileset to which the tile belongs.
 * @param definition The specific properties and definition of the tile.
 */
public record ResolvedTile(int tileX, int tileY, int rawGid, int gid, int localId, TileSet tileSet, TileDefinition definition) {
}