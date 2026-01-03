package valthorne.ui;

import valthorne.graphics.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * A utility class for managing styles in the UI system.
 * Provides static methods to store and retrieve Drawable objects using string keys.
 */
public class StyleData {

    /**
     * Map storing style objects mapped to their string keys
     */
    private static final Map<String, Drawable> styles = new HashMap<>();

    public static void init() {

    }

    /**
     * Retrieves a style object of the specified type associated with the given key.
     *
     * @param key   The string key to look up the style
     * @param clazz The expected class type of the style object
     * @param <T>   The type parameter extending Drawable
     * @return The style object cast to the specified type
     */
    public static <T extends Drawable> T get(String key, Class<T> clazz) {
        return clazz.cast(styles.get(key));
    }

    /**
     * Associates a style object with a string key in the styles map.
     *
     * @param key   The string key to store the style under
     * @param value The Drawable style object to store
     */
    public static void set(String key, Drawable value) {
        styles.put(key, value);
    }

    /**
     * Removes all stored styles from the map.
     */
    public static void clear() {
        styles.clear();
    }
}