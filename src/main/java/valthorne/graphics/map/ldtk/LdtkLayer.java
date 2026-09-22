package valthorne.graphics.map.ldtk;

import java.util.List;
import java.util.Map;

/**
 * Represents a fully decoded LDtk layer instance, including tile placements,
 * entities, IntGrid cells, custom fields, visibility, and render offsets. Mutable
 * inputs are defensively copied; {@link #intGrid()} also returns a fresh copy.
 *
 * @param identifier layer-definition identifier
 * @param iid unique layer-instance identifier
 * @param type LDtk layer kind
 * @param definitionUid referenced layer-definition UID
 * @param gridSize cell size in pixels
 * @param columns grid width in cells
 * @param rows grid height in cells
 * @param pixelOffsetX total horizontal render offset
 * @param pixelOffsetY total vertical render offset
 * @param tilesetUid referenced tileset UID, or {@code -1}
 * @param tilesetPath project-relative tileset path, when present
 * @param opacity layer opacity
 * @param visible whether LDtk marks the layer visible
 * @param intGrid row-major IntGrid values
 * @param gridTiles manually placed grid tiles
 * @param autoTiles automatically generated tiles
 * @param entities entity instances on this layer
 * @param fields custom layer fields keyed by identifier
 */
public record LdtkLayer(String identifier, String iid, String type, int definitionUid, int gridSize, int columns, int rows, int pixelOffsetX, int pixelOffsetY, int tilesetUid, String tilesetPath, float opacity, boolean visible, int[] intGrid, List<LdtkTile> gridTiles, List<LdtkTile> autoTiles, List<LdtkEntity> entities, Map<String, LdtkField> fields) {
    /**
     * Normalizes metadata and snapshots every mutable array or collection.
     *
     * @param identifier layer identifier; {@code null} becomes empty
     * @param iid instance identifier; {@code null} becomes empty
     * @param type layer kind; {@code null} becomes empty
     * @param definitionUid layer-definition UID
     * @param gridSize cell size in pixels
     * @param columns grid width in cells
     * @param rows grid height in cells
     * @param pixelOffsetX horizontal render offset
     * @param pixelOffsetY vertical render offset
     * @param tilesetUid tileset UID, or {@code -1}
     * @param tilesetPath optional tileset path
     * @param opacity layer opacity
     * @param visible layer visibility
     * @param intGrid IntGrid values; {@code null} becomes an empty array
     * @param gridTiles grid tiles; {@code null} becomes an empty immutable list
     * @param autoTiles auto tiles; {@code null} becomes an empty immutable list
     * @param entities entities; {@code null} becomes an empty immutable list
     * @param fields fields; {@code null} becomes an empty immutable map
     */
    public LdtkLayer {
        identifier = identifier == null ? "" : identifier;
        iid = iid == null ? "" : iid;
        type = type == null ? "" : type;
        intGrid = intGrid == null ? new int[0] : intGrid.clone();
        gridTiles = gridTiles == null ? List.of() : List.copyOf(gridTiles);
        autoTiles = autoTiles == null ? List.of() : List.copyOf(autoTiles);
        entities = entities == null ? List.of() : List.copyOf(entities);
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    /**
     * Returns an isolated copy of the row-major IntGrid storage.
     *
     * @return a new array that callers may modify freely
     */
    @Override
    public int[] intGrid() {
        return intGrid.clone();
    }

    /**
     * Reads a cell by zero-based column and row.
     *
     * @param column zero-based grid column
     * @param row zero-based grid row
     * @return the stored value, or zero when coordinates or backing data are out of range
     */
    public int intGridAt(int column, int row) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) return 0;
        int index = row * columns + column;
        return index < intGrid.length ? intGrid[index] : 0;
    }
}
