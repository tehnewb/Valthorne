package valthorne.ui;

/**
 * Represents different types of values used to define measurements or sizes.
 * Each ValueType encapsulates its own layout resolution logic.
 *
 * @author Albert Beaupre
 * @since January 7th, 2026
 */
public enum ValueType {

    /**
     * Percentage of the available size.
     */
    PERCENTAGE {
        @Override
        public float resolve(float number, float origin, float size, float fallback) {
            return number * size;
        }
    },

    /**
     * Absolute pixel value.
     */
    PIXELS {
        @Override
        public float resolve(float number, float origin, float size, float fallback) {
            return origin + number;
        }
    },

    /**
     * Automatically determined value.
     */
    AUTO {
        @Override
        public float resolve(float number, float origin, float size, float fallback) {
            return fallback;
        }
    },

    /**
     * Fills available space.
     */
    FILL {
        @Override
        public float resolve(float number, float origin, float size, float fallback) {
            return fallback;
        }
    },

    ALIGNMENT {
        @Override
        public float resolve(float number, float origin, float size, float fallback) {
            return fallback;
        }
    };

    /**
     * Resolves the final layout value.
     *
     * @param number   the raw numeric value
     * @param origin   the origin offset (x/y)
     * @param size     the reference size (width/height)
     * @param fallback the fallback value (AUTO/FILL)
     * @return resolved value
     */
    public abstract float resolve(float number, float origin, float size, float fallback);

    /**
     * Creates a new Value instance with this ValueType.
     *
     * @param value numeric value
     * @return Value instance
     */
    public Value of(float value) {
        return new Value(this, value);
    }
}
