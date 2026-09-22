package valthorne.graphics.map.ldtk;

import java.util.List;
import java.util.Map;

/**
 * Represents an LDtk entity instance with placement, definition identity, tags,
 * and typed custom fields. Collection components are immutable snapshots, and
 * absent identifiers are normalized to empty strings.
 *
 * @param identifier entity type identifier
 * @param iid unique instance identifier
 * @param x horizontal position in layer pixels
 * @param y vertical position in layer pixels
 * @param width instance width in pixels
 * @param height instance height in pixels
 * @param pivotX normalized horizontal pivot
 * @param pivotY normalized vertical pivot
 * @param definitionUid referenced entity-definition UID
 * @param tags immutable entity tags
 * @param fields immutable custom fields keyed by identifier
 */
public record LdtkEntity(String identifier, String iid, int x, int y, int width, int height, float pivotX, float pivotY, int definitionUid, List<String> tags, Map<String, LdtkField> fields) {
    /**
     * Normalizes nullable values and snapshots the supplied collections.
     *
     * @param identifier entity type identifier; {@code null} becomes empty
     * @param iid instance identifier; {@code null} becomes empty
     * @param x horizontal position in layer pixels
     * @param y vertical position in layer pixels
     * @param width instance width in pixels
     * @param height instance height in pixels
     * @param pivotX normalized horizontal pivot
     * @param pivotY normalized vertical pivot
     * @param definitionUid referenced definition UID
     * @param tags tags; {@code null} becomes an empty immutable list
     * @param fields fields; {@code null} becomes an empty immutable map
     */
    public LdtkEntity {
        identifier = identifier == null ? "" : identifier;
        iid = iid == null ? "" : iid;
        tags = tags == null ? List.of() : List.copyOf(tags);
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    /**
     * Looks up a custom field by its exact LDtk identifier.
     *
     * @param name field identifier; a missing or {@code null} key yields no value
     * @return the field, or {@code null} when it is absent
     */
    public LdtkField field(String name) {
        return fields.get(name);
    }
}
