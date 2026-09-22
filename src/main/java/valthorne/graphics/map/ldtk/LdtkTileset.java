package valthorne.graphics.map.ldtk;

import valthorne.graphics.texture.TextureData;

import java.util.List;

/**
 * Stores an LDtk tileset definition and its optional decoded CPU-side image.
 * Metadata remains available when a tileset has no image, and tags are copied
 * on construction so callers cannot change the definition through the source list.
 *
 * @param uid project-wide LDtk definition identifier
 * @param identifier human-readable LDtk identifier, normalized to an empty string
 * @param relativePath project-relative image path, or {@code null} for embedded data
 * @param tileGridSize square tile size in pixels
 * @param spacing pixels between adjacent tiles
 * @param padding pixels surrounding the tile atlas
 * @param width atlas width in pixels
 * @param height atlas height in pixels
 * @param tags immutable tileset tags
 * @param textureData decoded image data, or {@code null} when unavailable
 */
public record LdtkTileset(int uid, String identifier, String relativePath, int tileGridSize, int spacing, int padding, int width, int height, List<String> tags, TextureData textureData) {
    /**
     * Normalizes nullable textual and collection values and snapshots the tag list.
     *
     * @param uid project-wide LDtk definition identifier
     * @param identifier human-readable identifier; {@code null} becomes empty
     * @param relativePath project-relative image path
     * @param tileGridSize square tile size in pixels
     * @param spacing pixels between tiles
     * @param padding atlas padding in pixels
     * @param width atlas width in pixels
     * @param height atlas height in pixels
     * @param tags tileset tags; {@code null} becomes an empty immutable list
     * @param textureData decoded image data, if present
     */
    public LdtkTileset {
        identifier = identifier == null ? "" : identifier;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
