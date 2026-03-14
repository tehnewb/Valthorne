package valthorne.ui.theme;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>
 * {@code StyleKey} is the strongly typed identifier used throughout Valthorne's
 * theming and styling system. A style key represents a named property that can
 * be stored in a {@link StyleMap}, assigned in a {@link ThemeRule}, included in
 * {@link ThemeData} tokens, and later read from a {@link ResolvedStyle}.
 * </p>
 *
 * <p>
 * Each key contains four important pieces of information:
 * </p>
 *
 * <ul>
 *     <li>a unique numeric ID used for fast indexed lookup inside {@link StyleMap}</li>
 *     <li>a globally registered string name</li>
 *     <li>a Java type describing the expected value type</li>
 *     <li>an optional default value returned when no explicit value exists</li>
 * </ul>
 *
 * <p>
 * The registry guarantees that repeated calls using the same key name return the
 * same shared {@code StyleKey} instance. This is important because the key ID is
 * used as the storage index in {@link StyleMap}. If multiple different key objects
 * were created for the same conceptual property, style lookup would become inconsistent.
 * </p>
 *
 * <p>
 * Because the class is generic, each key carries its expected value type. That makes
 * style access much safer and more convenient than storing everything as untyped
 * objects. When a value is retrieved from a {@link StyleMap}, the key's declared
 * type is used to cast the value back to the expected type.
 * </p>
 *
 * <p>
 * This class is typically used as a static constant holder inside style or theme key
 * definition classes. The usual pattern is to define keys once and reuse them across
 * the engine.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * StyleKey<Float> PADDING = StyleKey.of("padding", Float.class, 0f);
 * StyleKey<String> FONT_NAME = StyleKey.of("fontName", String.class, "Default");
 *
 * StyleMap map = new StyleMap();
 * map.set(PADDING, 12f);
 *
 * float padding = map.get(PADDING);
 * String fontName = map.get(FONT_NAME);
 *
 * StyleKey<Float> samePaddingKey = StyleKey.of("padding", Float.class, 0f);
 * int id = samePaddingKey.getID();
 * String name = samePaddingKey.getName();
 * }</pre>
 *
 * <p>
 * This example demonstrates the full intended use of the class: creating keys,
 * using them in a style map, reading typed values, and relying on the shared
 * registry to retrieve the same key instance again.
 * </p>
 *
 * @param <T> the value type associated with this style key
 * @author Albert Beaupre
 * @since March 9th, 2026
 */
public final class StyleKey<T> {

    /**
     * Counter used to assign unique IDs to newly created style keys.
     */
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    /**
     * Global registry mapping key names to shared key instances.
     */
    private static final Map<String, StyleKey<?>> REGISTRY = new HashMap<>();

    private final int id; // Unique numeric ID used for indexed storage in StyleMap
    private final String name; // Global string name of this style key
    private final Class<T> type; // Declared Java type for values stored under this key
    private final T defaultValue; // Default value returned when no explicit value is stored

    /**
     * <p>
     * Creates a new {@code StyleKey}.
     * </p>
     *
     * <p>
     * This constructor is private because keys must be created through the static
     * factory methods so the global registry can guarantee name uniqueness and shared
     * instances.
     * </p>
     *
     * @param name         the unique key name
     * @param type         the Java type associated with the key
     * @param defaultValue the default value returned when no explicit value exists
     * @throws NullPointerException if {@code name} or {@code type} is {@code null}
     */
    private StyleKey(String name, Class<T> type, T defaultValue) {
        this.id = NEXT_ID.getAndIncrement();
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.defaultValue = defaultValue;
    }

    /**
     * <p>
     * Returns a shared {@code StyleKey} for the given name and type with no explicit
     * default value.
     * </p>
     *
     * <p>
     * This delegates to {@link #of(String, Class, Object)} using {@code null} as the
     * default value. If a key with the same name already exists, that existing key is
     * returned instead of creating a new one.
     * </p>
     *
     * @param name the unique key name
     * @param type the Java type associated with the key
     * @param <T>  the value type associated with the key
     * @return the shared style key instance for the given name
     */
    public static synchronized <T> StyleKey<T> of(String name, Class<T> type) {
        return of(name, type, null);
    }

    /**
     * <p>
     * Returns a shared {@code StyleKey} for the given name, type, and default value.
     * </p>
     *
     * <p>
     * If a key with the supplied name already exists in the registry, that existing
     * instance is returned immediately. Otherwise a new key is created, assigned a new
     * ID, stored in the registry, and returned.
     * </p>
     *
     * <p>
     * The registry is keyed only by name, so repeated calls using the same name but
     * different type arguments or default values will still return the original key
     * instance that was first registered for that name.
     * </p>
     *
     * @param name         the unique key name
     * @param type         the Java type associated with the key
     * @param defaultValue the default value returned when the key is absent
     * @param <T>          the value type associated with the key
     * @return the shared style key instance for the given name
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T> StyleKey<T> of(String name, Class<T> type, T defaultValue) {
        StyleKey<?> existing = REGISTRY.get(name);

        if (existing != null) {
            return (StyleKey<T>) existing;
        }

        StyleKey<T> key = new StyleKey<>(name, type, defaultValue);
        REGISTRY.put(name, key);
        return key;
    }

    /**
     * <p>
     * Returns a registered style key by name.
     * </p>
     *
     * <p>
     * This method performs a registry lookup without creating a new key. If no key with
     * the given name exists, {@code null} is returned.
     * </p>
     *
     * @param name the key name to look up
     * @param <T>  the expected value type
     * @return the registered key, or {@code null} if none exists
     */
    @SuppressWarnings("unchecked")
    public static <T> StyleKey<T> get(String name) {
        return (StyleKey<T>) REGISTRY.get(name);
    }

    /**
     * <p>
     * Returns the unique numeric ID of this key.
     * </p>
     *
     * <p>
     * This ID is used directly as the index into {@link StyleMap}'s backing value array.
     * </p>
     *
     * @return the unique key ID
     */
    public int getID() {
        return id;
    }

    /**
     * <p>
     * Returns the registry name of this key.
     * </p>
     *
     * @return the key name
     */
    public String getName() {
        return name;
    }

    /**
     * <p>
     * Returns the declared Java type associated with this key.
     * </p>
     *
     * @return the value type class
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * <p>
     * Returns the default value associated with this key.
     * </p>
     *
     * <p>
     * This value is typically used by {@link StyleMap#get(StyleKey)} when no explicit
     * value is stored for the key.
     * </p>
     *
     * @return the default value, which may be {@code null}
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * <p>
     * Returns a debug-friendly string representation of this style key.
     * </p>
     *
     * <p>
     * The returned string includes both the unique ID and the name so keys are easier
     * to identify during debugging and logging.
     * </p>
     *
     * @return a string representation of this key
     */
    @Override
    public String toString() {
        return "StyleKey[" + id + ":" + name + "]";
    }

}