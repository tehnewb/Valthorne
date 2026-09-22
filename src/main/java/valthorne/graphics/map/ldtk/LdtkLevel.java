package valthorne.graphics.map.ldtk;

import java.util.List;
import java.util.Map;

/**
 * Represents one LDtk level, whether embedded in the project or resolved from an
 * external {@code .ldtkl} file. It preserves world placement and decoded content
 * while exposing immutable layer and field collections.
 *
 * @param identifier human-readable level identifier
 * @param iid unique level-instance identifier
 * @param uid numeric level UID
 * @param worldX horizontal world position in pixels
 * @param worldY vertical world position in pixels
 * @param width level width in pixels
 * @param height level height in pixels
 * @param backgroundColor LDtk background color
 * @param background decoded background placement, or {@code null}
 * @param layers immutable layer instances in LDtk order
 * @param fields immutable level fields keyed by identifier
 * @param externalPath external level path, or {@code null} for embedded levels
 */
public record LdtkLevel(String identifier, String iid, int uid, int worldX, int worldY, int width, int height, String backgroundColor, LdtkBackground background, List<LdtkLayer> layers, Map<String, LdtkField> fields, String externalPath) {
    /**
     * Normalizes identifiers and snapshots layer and field collections.
     *
     * @param identifier level identifier; {@code null} becomes empty
     * @param iid instance identifier; {@code null} becomes empty
     * @param uid numeric level UID
     * @param worldX horizontal world position
     * @param worldY vertical world position
     * @param width width in pixels
     * @param height height in pixels
     * @param backgroundColor LDtk background color
     * @param background decoded background, if present
     * @param layers layers; {@code null} becomes an empty immutable list
     * @param fields fields; {@code null} becomes an empty immutable map
     * @param externalPath external level path, if present
     */
    public LdtkLevel {
        identifier = identifier == null ? "" : identifier;
        iid = iid == null ? "" : iid;
        layers = layers == null ? List.of() : List.copyOf(layers);
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    /**
     * Finds the first layer with an exact identifier match.
     *
     * @param name layer identifier to find
     * @return the matching layer, or {@code null} when absent
     */
    public LdtkLayer layer(String name) {
        return layers.stream().filter(it -> it.identifier().equals(name)).findFirst().orElse(null);
    }

    /**
     * Returns the source path of the decoded background image.
     *
     * @return the relative image path, or {@code null} when there is no background
     */
    public String backgroundImagePath() {
        return background == null ? null : background.relativePath();
    }
}
