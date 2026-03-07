package valthorne.graphics.map.tiled;

/**
 * Represents different types of shapes in a Tiled map format.
 * <p>
 * This enumeration is used to define the types of shapes that can be associated
 * with objects or areas within a Tiled map. Each type corresponds to specific
 * geometric properties or visual appearances.
 */
public enum TiledShapeType {
    /**
     * Represents a rectangular shape type within the Tiled map format.
     */
    RECT,

    /**
     * Represents a single point shape type within the Tiled map format.
     */
    POINT,

    /**
     * Represents an elliptical shape type within the Tiled map format.
     */
    ELLIPSE,

    /**
     * Represents a closed shape with multiple straight sides within the Tiled map format.
     */
    POLYGON,

    /**
     * Represents an open, connected sequence of straight line segments.
     */
    POLYLINE,

    /**
     * Represents a textual object in the Tiled map format.
     */
    TEXT
}