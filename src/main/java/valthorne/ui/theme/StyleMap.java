package valthorne.ui.theme;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * <p>
 * {@code StyleMap} is the core value container used by Valthorne's theming system.
 * It stores style values indexed by {@link StyleKey} ID, allowing very fast lookups
 * and merges without relying on repeated string-based map access during runtime.
 * </p>
 *
 * <p>
 * Unlike a traditional hash map keyed directly by property names, this class uses the
 * integer ID assigned by each {@link StyleKey} as the storage index inside an internal
 * object array. This makes reads and writes extremely efficient once keys have been
 * created and registered.
 * </p>
 *
 * <p>
 * A {@code StyleMap} is used in several important places across the UI styling system:
 * </p>
 *
 * <ul>
 *     <li>as the token store inside {@link ThemeData}</li>
 *     <li>as the value store inside each {@link ThemeRule}</li>
 *     <li>as the resolved backing map inside {@link ResolvedStyle}</li>
 *     <li>as optional override data during theme resolution</li>
 * </ul>
 *
 * <p>
 * The map supports:
 * </p>
 *
 * <ul>
 *     <li>setting typed values with {@link #set(StyleKey, Object)}</li>
 *     <li>getting typed values with {@link #get(StyleKey)}</li>
 *     <li>checking whether a value exists with {@link #contains(StyleKey)}</li>
 *     <li>removing a value</li>
 *     <li>merging values from another {@code StyleMap}</li>
 *     <li>copying itself</li>
 *     <li>clearing all stored values</li>
 * </ul>
 *
 * <p>
 * A key detail of this class is that missing values do not simply return {@code null}.
 * Instead, {@link #get(StyleKey)} falls back to the key's default value when no stored
 * value exists for that key.
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
 * String fontName = map.get(FONT_NAME); // Falls back to default value
 *
 * boolean hasPadding = map.contains(PADDING);
 *
 * StyleMap copy = map.copy();
 * copy.remove(PADDING);
 *
 * map.putAll(copy);
 * map.clear();
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete intended use of the class: setting values,
 * reading values with defaults, checking presence, copying, removing, merging, and
 * clearing the map.
 * </p>
 *
 * <p>Values are retained by reference, and null represents absence rather than
 * an explicit override of a key's default. Mutations notify an optional callback
 * after updating storage; changing a mutable stored object directly does not
 * notify the map. Copies and snapshots share those value objects. Instances
 * are not synchronized and should be accessed on their owning UI thread.</p>
 *
 * @author Albert Beaupre
 * @since March 9th, 2026
 */
public final class StyleMap {
    private final Runnable changed; // Synchronous callback invoked after notified changes to stored values.
    private Object[] values = new Object[16]; // Backing array indexed by StyleKey ID

    /**
     * Creates an empty map with a no-op change callback. Keys use their defaults
     * until explicit nonnull values are stored; initial capacity is 16 slots.
     */
    public StyleMap() {this(() -> {});}

    /**
     * Creates an empty map that calls the supplied action after notified mutations.
     * The callback is retained by reference and is not invoked during construction.
     * Callback failures propagate after the map has changed, without rollback.
     *
     * @param changed the nonnull synchronous mutation callback
     * @throws NullPointerException if changed is null
     */
    public StyleMap(Runnable changed) {this.changed = Objects.requireNonNull(changed);}

    /**
     * Copies explicitly stored values into an unmodifiable map ordered by key
     * name. Default-only values are omitted. The map structure is independent of
     * subsequent edits, but referenced values are shared and can remain mutable.
     * This operation scans registered keys and does not notify the change callback.
     *
     * @return an unmodifiable, shallow snapshot indexed by registered key name
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new TreeMap<>();
        for (StyleKey<?> key : StyleKey.registeredKeys())
            if (contains(key)) result.put(key.getName(), get(key));
        return Collections.unmodifiableMap(result);
    }

    /**
     * <p>
     * Stores a value for the supplied {@link StyleKey}.
     * </p>
     *
     * <p>
     * The backing array is expanded if necessary so the key's ID can be used as a valid
     * index. The provided value is then written directly into that slot.
     * A value equal to the current slot under Objects.equals is ignored, retaining
     * the existing reference. Otherwise the new reference is stored and the callback
     * runs once. Null clears the slot and restores default lookup behavior; values
     * are not copied or runtime-checked until retrieval.
     * </p>
     *
     * @param key   the style key whose value should be set
     * @param value the value to store
     * @param <T>   the value type
     * @throws NullPointerException if key is null
     */
    public <T> void set(StyleKey<T> key, T value) {
        int id = key.getID();
        ensureCapacity(id + 1);
        if (Objects.equals(values[id], value)) return;
        values[id] = value;
        changed.run();
    }

    /**
     * <p>
     * Returns the value associated with the supplied {@link StyleKey}.
     * </p>
     *
     * <p>
     * If the key's ID falls outside the current array bounds, or if the slot exists but
     * contains {@code null}, the key's default value is returned instead.
     * </p>
     *
     * <p>
     * When a stored value exists, the key's declared type is used to cast it back to
     * the expected result type.
     * </p>
     *
     * @param key the style key to query
     * @param <T> the expected value type
     * @return the stored value, or the key's default value if none is present
     * @throws NullPointerException if key is null
     * @throws ClassCastException   if an explicitly stored value does not match the key type
     */
    public <T> T get(StyleKey<T> key) {
        int id = key.getID();
        if (id >= values.length) return key.getDefaultValue();

        Object value = values[id];
        if (value == null) return key.getDefaultValue();

        return key.getType().cast(value);
    }

    /**
     * <p>
     * Returns whether an explicit value is currently stored for the supplied key.
     * </p>
     *
     * <p>
     * This method only checks for a concrete stored value. It does not consider a key's
     * default value to mean the key is present.
     * </p>
     *
     * @param key the style key to test
     * @return {@code true} if a concrete value is stored for the key
     * @throws NullPointerException if key is null
     */
    public boolean contains(StyleKey<?> key) {
        int id = key.getID();
        return id < values.length && values[id] != null;
    }

    /**
     * <p>
     * Removes the explicitly stored value for the supplied key.
     * </p>
     *
     * <p>
     * After removal, future lookups for that key will fall back to the key's default
     * value unless another value is stored later.
     * Removing a present value invokes the callback once after clearing its slot;
     * removing an absent value is silent and does not shrink the backing array.
     * </p>
     *
     * @param key the style key whose stored value should be removed
     * @throws NullPointerException if key is null
     */
    public void remove(StyleKey<?> key) {
        int id = key.getID();
        if (id < values.length && values[id] != null) {
            values[id] = null;
            changed.run();
        }
    }

    /**
     * <p>
     * Copies all explicitly stored values from another {@code StyleMap} into this one.
     * </p>
     *
     * <p>
     * The backing array is expanded as needed to fit the other map. Only non-null values
     * are copied, which means absent values in the source map do not clear values that
     * already exist in this map.
     * Equal values retain their existing references. The callback runs once after
     * the entire merge if any slot changed, and does not run for an unchanged merge.
     * Copied values remain shared with the source; no deep copy is performed.
     * </p>
     *
     * @param other the source map whose values should be copied into this map
     * @throws NullPointerException if other is null
     */
    public void putAll(StyleMap other) {
        ensureCapacity(other.values.length);
        boolean modified = false;
        for (int i = 0; i < other.values.length; i++) {
            Object value = other.values[i];
            if (value != null && !Objects.equals(values[i], value)) {
                values[i] = value;
                modified = true;
            }
        }
        if (modified) changed.run();
    }

    /**
     * <p>
     * Creates and returns a shallow copy of this {@code StyleMap}.
     * </p>
     *
     * <p>
     * The backing value array is duplicated, but the individual stored objects are not
     * cloned. The resulting map therefore shares the same referenced values while
     * maintaining its own independent storage array.
     * The copy uses the no-op callback from the default constructor; it does not
     * inherit the original map's observer or notify it during copying.
     * </p>
     *
     * @return a copy of this style map
     */
    public StyleMap copy() {
        StyleMap map = new StyleMap();
        map.values = Arrays.copyOf(values, values.length);
        return map;
    }

    /**
     * <p>
     * Ensures that the backing array can store at least the requested number of entries.
     * </p>
     *
     * <p>
     * If the array is already large enough, nothing happens. Otherwise, the array grows
     * by powers of two until it can hold the requested capacity.
     * </p>
     *
     * @param capacity the minimum required capacity
     */
    private void ensureCapacity(int capacity) {
        if (capacity <= values.length) return;

        int newCapacity = values.length;
        while (newCapacity < capacity) newCapacity <<= 1;

        values = Arrays.copyOf(values, newCapacity);
    }

    /**
     * <p>
     * Clears all explicitly stored values from the map.
     * </p>
     *
     * <p>
     * After clearing, all keys behave as though no explicit values are stored and will
     * therefore resolve to their default values when queried.
     * Capacity is retained. The callback runs once even when the map was already
     * empty, so callers can use this operation as an explicit invalidation.
     * </p>
     */
    public void clear() {
        Arrays.fill(values, null);
        changed.run();
    }

}
