package valthorne.graphics.map.ldtk;

import java.util.List;
import java.util.Map;

/**
 * Namespace for immutable LDtk project definitions that are not tied to one
 * level instance. Definitions preserve editor metadata used to interpret layer,
 * entity, IntGrid, and enum instances after parsing.
 */
public final class LdtkDefinition {
    /**
     * Prevents instantiation of this definition namespace.
     */
    private LdtkDefinition() {
    }

    /**
     * Defines an LDtk layer type and its grid interpretation.
     *
     * @param uid project-wide definition UID
     * @param identifier layer identifier
     * @param type LDtk layer kind
     * @param gridSize cell size in pixels
     * @param tilesetUid default tileset UID, or {@code -1}
     * @param intGridValues immutable IntGrid value definitions
     */
    public record Layer(int uid, String identifier, String type, int gridSize, int tilesetUid, List<IntGridValue> intGridValues) {
        /**
         * Snapshots the IntGrid definitions, treating {@code null} as empty.
         *
         * @param uid definition UID
         * @param identifier layer identifier
         * @param type layer kind
         * @param gridSize cell size in pixels
         * @param tilesetUid default tileset UID
         * @param intGridValues definitions to copy
         */
        public Layer {
            intGridValues = intGridValues == null ? List.of() : List.copyOf(intGridValues);
        }
    }

    /**
     * Defines the editor label and color associated with an IntGrid integer.
     *
     * @param value stored integer value
     * @param identifier editor-facing value identifier
     * @param color LDtk color string
     */
    public record IntGridValue(int value, String identifier, String color) {
    }

    /**
     * Defines an entity type independently of any placed entity instance.
     *
     * @param uid project-wide definition UID
     * @param identifier entity identifier
     * @param width default width in pixels
     * @param height default height in pixels
     * @param color LDtk editor color
     * @param tags immutable definition tags
     */
    public record Entity(int uid, String identifier, int width, int height, String color, List<String> tags) {
        /**
         * Snapshots the tag list, treating {@code null} as empty.
         *
         * @param uid definition UID
         * @param identifier entity identifier
         * @param width default width
         * @param height default height
         * @param color editor color
         * @param tags tags to copy
         */
        public Entity {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    /**
     * Defines an LDtk enum and the color associated with each textual value.
     *
     * @param uid project-wide definition UID
     * @param identifier enum identifier
     * @param values immutable value-to-color mapping
     */
    public record Enum(int uid, String identifier, Map<String, String> values) {
        /**
         * Snapshots the enum mapping, treating {@code null} as empty.
         *
         * @param uid definition UID
         * @param identifier enum identifier
         * @param values values to copy
         */
        public Enum {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }
}
