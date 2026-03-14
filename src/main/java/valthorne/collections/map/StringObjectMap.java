package valthorne.collections.map;

import java.util.Arrays;

/**
 * <p>
 * {@code StringObjectMap} is a hash map implementation specialized for {@link String}
 * keys and arbitrary object values. It uses open addressing with linear probing
 * instead of chaining, which allows it to store data in compact parallel arrays
 * and avoid per-entry node allocations.
 * </p>
 *
 * <p>
 * This map is designed for fast lookup, insertion, and removal when keys are strings.
 * Internally it stores keys in one array and values in another array at matching
 * indices. When collisions occur, probing continues forward through the table until
 * an empty slot or matching key is found.
 * </p>
 *
 * <p>
 * The table size is always kept as a power of two. This allows the implementation
 * to use a bitmask instead of a modulo operation when wrapping indices, which is
 * faster and keeps probing logic simple.
 * </p>
 *
 * <p>
 * Important characteristics of this implementation:
 * </p>
 *
 * <ul>
 *     <li>Keys must be non-null.</li>
 *     <li>Values may be null.</li>
 *     <li>Insertion order is not preserved.</li>
 *     <li>The backing table automatically resizes when the load threshold is reached.</li>
 *     <li>Removal uses backward-shift deletion so probe chains remain valid.</li>
 * </ul>
 *
 * <p>
 * This class is useful when you want a lightweight map for string-keyed lookups
 * without the overhead of a more general-purpose collection. It is especially
 * appropriate in engine code, asset registries, style/resource lookups, and
 * other systems where many repeated string-based queries occur.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * StringObjectMap<Integer> scores = new StringObjectMap<>();
 *
 * scores.put("Albert", 10);
 * scores.put("Saphira", 25);
 * scores.put("Turaya", 17);
 *
 * Integer albert = scores.get("Albert");
 * Integer missing = scores.getOrDefault("Unknown", 0);
 *
 * boolean hasTuraya = scores.containsKey("Turaya");
 * boolean hasScore25 = scores.containsValue(25);
 *
 * scores.remove("Albert");
 *
 * String[] keys = scores.keys();
 * Integer[] values = scores.values(new Integer[scores.size()]);
 *
 * int size = scores.size();
 * int capacity = scores.capacity();
 * boolean empty = scores.isEmpty();
 *
 * scores.ensureCapacity(16);
 * scores.shrink(8);
 * scores.clear();
 * }</pre>
 *
 * <p>
 * This example demonstrates the full intended use of the class: insertion,
 * lookup, default lookup, containment checks, removal, key/value export,
 * capacity management, and clearing.
 * </p>
 *
 * @param <T> the value type stored in the map
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class StringObjectMap<T> {

    /**
     * Default initial capacity requested by the no-argument constructor.
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 32;

    /**
     * Default load factor used when none is specified.
     */
    private static final float DEFAULT_LOAD_FACTOR = 0.8f;

    private String[] keyTable; // Backing array storing keys
    private Object[] valueTable; // Backing array storing values parallel to the key table
    private int size; // Number of entries currently stored in the map
    private int threshold; // Size limit that triggers a resize based on load factor
    private int mask; // Bitmask used for wrapping probe indices
    private final float loadFactor; // Configured load factor for resize calculations

    /**
     * <p>
     * Creates a new map using the default initial capacity and default load factor.
     * </p>
     *
     * <p>
     * The actual backing table size is rounded up to a power of two large enough
     * to satisfy the requested capacity and load factor.
     * </p>
     */
    public StringObjectMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * <p>
     * Creates a new map using the given initial capacity and the default load factor.
     * </p>
     *
     * @param initialCapacity the requested initial capacity before load factor adjustment
     */
    public StringObjectMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * <p>
     * Creates a new map using the given initial capacity and load factor.
     * </p>
     *
     * <p>
     * The requested capacity is converted to an internal power-of-two table size
     * large enough to keep the map within the desired load factor.
     * </p>
     *
     * @param initialCapacity the requested initial capacity
     * @param loadFactor the load factor used to determine resize thresholds
     * @throws IllegalArgumentException if {@code initialCapacity} is negative
     * @throws IllegalArgumentException if {@code loadFactor} is not greater than 0 and less than 1
     */
    public StringObjectMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) throw new IllegalArgumentException("initialCapacity must be >= 0");
        if (loadFactor <= 0f || loadFactor >= 1f || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("loadFactor must be > 0 and < 1");

        int capacity = tableSize(Math.max(2, initialCapacity), loadFactor);

        this.loadFactor = loadFactor;
        this.keyTable = new String[capacity];
        this.valueTable = new Object[capacity];
        this.mask = capacity - 1;
        this.threshold = (int) (capacity * loadFactor);
    }

    /**
     * <p>
     * Inserts or replaces a value associated with the given key.
     * </p>
     *
     * <p>
     * If the key does not already exist, a new entry is inserted and the size grows
     * by one. If the key already exists, its existing value is replaced and the old
     * value is returned.
     * </p>
     *
     * <p>
     * If the current size has reached the threshold, the table is resized before
     * insertion occurs.
     * </p>
     *
     * @param key the non-null key to insert
     * @param value the value to store, which may be null
     * @return the previous value associated with the key, or null if the key was not present
     * @throws NullPointerException if {@code key} is null
     */
    public T put(String key, T value) {
        if (key == null) throw new NullPointerException("key cannot be null");

        if (size >= threshold) resize(keyTable.length << 1);

        String[] keys = keyTable;
        int index = place(key);

        while (true) {
            String existing = keys[index];
            if (existing == null) {
                keys[index] = key;
                valueTable[index] = value;
                size++;
                return null;
            }
            if (existing.equals(key)) {
                @SuppressWarnings("unchecked") T oldValue = (T) valueTable[index];
                valueTable[index] = value;
                return oldValue;
            }
            index = (index + 1) & mask;
        }
    }

    /**
     * <p>
     * Returns the value associated with the given key.
     * </p>
     *
     * <p>
     * If the key is null or not present in the table, this method returns null.
     * </p>
     *
     * @param key the key to look up
     * @return the stored value, or null if the key is absent
     */
    public T get(String key) {
        if (key == null) return null;

        int index = locateKey(key);
        if (index < 0) return null;

        @SuppressWarnings("unchecked") T value = (T) valueTable[index];
        return value;
    }

    /**
     * <p>
     * Returns the value associated with the given key, or a supplied fallback value
     * if the key is not present or resolves to null.
     * </p>
     *
     * @param key the key to look up
     * @param defaultValue the fallback value to return when lookup yields null
     * @return the stored value if non-null, otherwise {@code defaultValue}
     */
    public T getOrDefault(String key, T defaultValue) {
        T value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * <p>
     * Returns whether the map currently contains the given key.
     * </p>
     *
     * @param key the key to test
     * @return {@code true} if the key exists in the map
     */
    public boolean containsKey(String key) {
        return locateKey(key) >= 0;
    }

    /**
     * <p>
     * Returns whether the map currently contains the given value.
     * </p>
     *
     * <p>
     * Because values are stored as general objects, this method performs a linear
     * scan through the occupied table slots. Null values are supported and checked
     * explicitly.
     * </p>
     *
     * @param value the value to test
     * @return {@code true} if the value is present in any occupied entry
     */
    public boolean containsValue(T value) {
        Object[] values = valueTable;
        String[] keys = keyTable;

        if (value == null) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] != null && values[i] == null) return true;
            }
        } else {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] != null && value.equals(values[i])) return true;
            }
        }

        return false;
    }

    /**
     * <p>
     * Removes the entry associated with the given key.
     * </p>
     *
     * <p>
     * If the key is present, its value is returned and the entry is removed using
     * backward-shift deletion so remaining probe chains still function correctly.
     * If the key is absent or null, this method returns null.
     * </p>
     *
     * @param key the key to remove
     * @return the removed value, or null if the key was not present
     */
    public T remove(String key) {
        if (key == null) return null;

        int index = locateKey(key);
        if (index < 0) return null;

        @SuppressWarnings("unchecked") T oldValue = (T) valueTable[index];

        removeIndex(index);
        size--;

        return oldValue;
    }

    /**
     * <p>
     * Removes all entries from the map without changing the current table capacity.
     * </p>
     *
     * <p>
     * All key and value slots are cleared and size becomes zero.
     * </p>
     */
    public void clear() {
        Arrays.fill(keyTable, null);
        Arrays.fill(valueTable, null);
        size = 0;
    }

    /**
     * <p>
     * Removes all entries from the map and optionally shrinks the backing table so it
     * does not exceed the capacity required for the specified maximum capacity.
     * </p>
     *
     * <p>
     * If the current table is already at or below the required size, this behaves
     * like {@link #clear()}. Otherwise a new smaller table is allocated.
     * </p>
     *
     * @param maximumCapacity the maximum logical capacity to retain after clearing
     */
    public void clear(int maximumCapacity) {
        int tableSize = tableSize(maximumCapacity, loadFactor);
        if (keyTable.length <= tableSize) {
            clear();
            return;
        }

        size = 0;
        keyTable = new String[tableSize];
        valueTable = new Object[tableSize];
        mask = tableSize - 1;
        threshold = (int) (tableSize * loadFactor);
    }

    /**
     * <p>
     * Ensures that the map can accept the given number of additional entries without
     * resizing again.
     * </p>
     *
     * <p>
     * If the current table is already large enough, nothing happens. Otherwise the
     * table is resized to the next appropriate power-of-two size.
     * </p>
     *
     * @param additionalCapacity the number of extra entries that should fit
     */
    public void ensureCapacity(int additionalCapacity) {
        int needed = size + additionalCapacity;
        if (needed >= threshold) resize(tableSize(needed, loadFactor));
    }

    /**
     * <p>
     * Shrinks the table if its current capacity is larger than needed.
     * </p>
     *
     * <p>
     * The new size is chosen so it can still hold at least the current number of
     * entries and the requested maximum capacity under the configured load factor.
     * </p>
     *
     * @param maximumCapacity the maximum desired logical capacity after shrinking
     * @throws IllegalArgumentException if {@code maximumCapacity} is negative
     */
    public void shrink(int maximumCapacity) {
        if (maximumCapacity < 0) throw new IllegalArgumentException("maximumCapacity must be >= 0");

        int tableSize = tableSize(Math.max(size, maximumCapacity), loadFactor);
        if (keyTable.length > tableSize) resize(tableSize);
    }

    /**
     * <p>
     * Returns the number of entries currently stored in the map.
     * </p>
     *
     * @return the current size
     */
    public int size() {
        return size;
    }

    /**
     * <p>
     * Returns whether the map currently contains no entries.
     * </p>
     *
     * @return {@code true} if the map is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * <p>
     * Returns the current backing table capacity.
     * </p>
     *
     * <p>
     * This is the number of slots in the internal key/value arrays, not the number
     * of entries currently stored.
     * </p>
     *
     * @return the backing table capacity
     */
    public int capacity() {
        return keyTable.length;
    }

    /**
     * <p>
     * Returns the configured load factor used by this map.
     * </p>
     *
     * @return the load factor
     */
    public float getLoadFactor() {
        return loadFactor;
    }

    /**
     * <p>
     * Returns a new array containing all keys currently stored in the map.
     * </p>
     *
     * <p>
     * Keys are returned in table iteration order, which is not insertion order and
     * may change after resizing or removals.
     * </p>
     *
     * @return an array containing all stored keys
     */
    public String[] keys() {
        String[] out = new String[size];
        int n = 0;
        for (String key : keyTable) {
            if (key != null) out[n++] = key;
        }
        return out;
    }

    /**
     * <p>
     * Copies all stored values into the provided output array.
     * </p>
     *
     * <p>
     * The output array must be large enough to hold all values currently stored.
     * If the output array is larger than the map size, the element immediately after
     * the last copied value is set to null.
     * </p>
     *
     * <p>
     * Values are copied in table iteration order, which is not insertion order.
     * </p>
     *
     * @param out the destination array
     * @return the same destination array, filled with values
     * @throws IllegalArgumentException if {@code out.length < size()}
     */
    @SuppressWarnings("unchecked")
    public T[] values(T[] out) {
        if (out.length < size) throw new IllegalArgumentException("Output array is too small");

        int n = 0;
        for (int i = 0; i < keyTable.length; i++) {
            if (keyTable[i] != null) out[n++] = (T) valueTable[i];
        }

        if (out.length > size) out[size] = null;

        return out;
    }

    /**
     * <p>
     * Computes the initial placement index for a key.
     * </p>
     *
     * <p>
     * The key hash code is mixed before masking so distribution is improved across
     * the table.
     * </p>
     *
     * @param key the key to place
     * @return the initial table index for probing
     */
    private int place(String key) {
        int h = key.hashCode();
        h ^= h >>> 16;
        h *= 0x9E3779B9;
        return h & mask;
    }

    /**
     * <p>
     * Locates the table index of a key.
     * </p>
     *
     * <p>
     * If the key is present, its index is returned. Otherwise {@code -1} is returned.
     * Linear probing continues until either the key is found or an empty slot is hit.
     * </p>
     *
     * @param key the key to locate
     * @return the table index of the key, or {@code -1} if absent
     */
    private int locateKey(String key) {
        String[] keys = keyTable;
        int index = place(key);

        while (true) {
            String existing = keys[index];
            if (existing == null) return -1;
            if (existing.equals(key)) return index;
            index = (index + 1) & mask;
        }
    }

    /**
     * <p>
     * Removes an entry at a known table index using backward-shift deletion.
     * </p>
     *
     * <p>
     * This method preserves valid probe chains by shifting later clustered entries
     * backward when needed. It is an essential part of open-addressing removal.
     * </p>
     *
     * @param index the occupied table index to remove
     */
    private void removeIndex(int index) {
        int mask = this.mask;
        String[] keys = keyTable;
        Object[] values = valueTable;

        int next = (index + 1) & mask;
        while (keys[next] != null) {
            int placement = place(keys[next]);
            if (((next - placement) & mask) > ((index - placement) & mask)) {
                keys[index] = keys[next];
                values[index] = values[next];
                index = next;
            }
            next = (next + 1) & mask;
        }

        keys[index] = null;
        values[index] = null;
    }

    /**
     * <p>
     * Resizes the backing table to the specified new size and rehashes all existing entries.
     * </p>
     *
     * <p>
     * The new size must already be a valid power-of-two table size. All existing keys
     * are reinserted into the new table using the current placement logic.
     * </p>
     *
     * @param newSize the new backing table size
     */
    private void resize(int newSize) {
        String[] oldKeys = keyTable;
        Object[] oldValues = valueTable;

        keyTable = new String[newSize];
        valueTable = new Object[newSize];
        mask = newSize - 1;
        threshold = (int) (newSize * loadFactor);

        if (size == 0) return;

        for (int i = 0; i < oldKeys.length; i++) {
            String key = oldKeys[i];
            if (key == null) continue;

            int index = place(key);
            while (keyTable[index] != null) index = (index + 1) & mask;

            keyTable[index] = key;
            valueTable[index] = oldValues[i];
        }
    }

    /**
     * <p>
     * Computes the backing table size needed to support a desired logical capacity
     * under the given load factor.
     * </p>
     *
     * <p>
     * The returned value is always a power of two and at least {@code 2}.
     * </p>
     *
     * @param capacity the desired logical capacity
     * @param loadFactor the load factor to satisfy
     * @return the computed power-of-two table size
     */
    private static int tableSize(int capacity, float loadFactor) {
        int target = Math.max(2, (int) Math.ceil(capacity / loadFactor));
        int tableSize = 1;
        while (tableSize < target) tableSize <<= 1;
        return tableSize;
    }
}