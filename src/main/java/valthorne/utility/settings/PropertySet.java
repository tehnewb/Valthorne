package valthorne.utility.settings;

import valthorne.io.buffer.DynamicByteBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable collection of named game properties. Each non-null value is wrapped in a
 * {@link PropertyValue}, allowing callers to retain its declared type or request one
 * of the wrapper's common converted representations. Property names and values must
 * be non-null.
 * <p>
 * This class is not thread-safe. Callers that share an instance across threads must
 * provide external synchronization. {@link #asMap()} creates an immutable snapshot
 * suitable for publication without exposing subsequent mutations.
 *
 * @author Albert Beaupre
 * @since September 21st, 2026
 */
public final class PropertySet {

    private final Map<String, PropertyValue<?>> values = new HashMap<>();

    /**
     * Creates an empty property collection.
     */
    public PropertySet() {
    }

    /**
     * Creates a collection containing a copy of the supplied entries.
     *
     * @param values initial property names and values
     * @throws NullPointerException if the map, a name, or a value is null
     */
    public PropertySet(Map<String, ?> values) {
        Objects.requireNonNull(values, "values").forEach(this::set);
    }

    /**
     * Returns a property using the caller's inferred target type. Because the type is
     * not checked until the wrapped value is used, prefer {@link #get(String, Class)}
     * when a property name does not guarantee its value type.
     *
     * @param name property name
     * @param <T>  inferred declared value type
     * @return matching property, or null when absent
     * @throws NullPointerException if name is null
     */
    @SuppressWarnings("unchecked")
    public <T> PropertyValue<T> get(String name) {
        return (PropertyValue<T>) values.get(Objects.requireNonNull(name, "name"));
    }

    /**
     * Returns a property after verifying its stored value has the requested type.
     *
     * @param name property name
     * @param type requested value class
     * @param <T>  requested value type
     * @return matching property, or null when absent
     * @throws NullPointerException if name or type is null
     * @throws ClassCastException   if the stored value is not an instance of type
     */
    public <T> PropertyValue<T> get(String name, Class<T> type) {
        Objects.requireNonNull(type, "type");
        PropertyValue<?> property = values.get(Objects.requireNonNull(name, "name"));
        return property == null ? null : new PropertyValue<>(type.cast(property.value()));
    }

    /**
     * Returns a required property using the caller's inferred target type.
     *
     * @param name property name
     * @param <T>  inferred declared value type
     * @return matching property
     * @throws NullPointerException     if name is null
     * @throws IllegalArgumentException if no property has that name
     */
    public <T> PropertyValue<T> getRequired(String name) {
        PropertyValue<T> value = get(name);
        if (value == null) throw new IllegalArgumentException("No game property named: " + name);
        return value;
    }

    /**
     * Returns a required property after verifying its stored value type.
     *
     * @param name property name
     * @param type requested value class
     * @param <T>  requested value type
     * @return matching property
     * @throws NullPointerException     if name or type is null
     * @throws IllegalArgumentException if no property has that name
     * @throws ClassCastException       if the stored value is not an instance of type
     */
    public <T> PropertyValue<T> getRequired(String name, Class<T> type) {
        PropertyValue<T> value = get(name, type);
        if (value == null) throw new IllegalArgumentException("No game property named: " + name);
        return value;
    }

    /**
     * Returns a property or wraps the supplied fallback when the property is absent.
     *
     * @param name         property name
     * @param defaultValue non-null fallback value
     * @param <T>          value type
     * @return stored property or a newly wrapped fallback
     * @throws NullPointerException if name or defaultValue is null
     */
    public <T> PropertyValue<T> getOrDefault(String name, T defaultValue) {
        PropertyValue<T> value = get(name);
        return value != null ? value : new PropertyValue<>(defaultValue);
    }

    /**
     * Finds a property using the caller's inferred target type.
     *
     * @param name property name
     * @param <T>  inferred declared value type
     * @return optional containing the property when present
     * @throws NullPointerException if name is null
     */
    public <T> Optional<PropertyValue<T>> find(String name) {
        return Optional.ofNullable(get(name));
    }

    /**
     * Tests whether a property is present.
     *
     * @param name property name
     * @return whether the name has an associated property
     * @throws NullPointerException if name is null
     */
    public boolean contains(String name) {
        return values.containsKey(Objects.requireNonNull(name, "name"));
    }

    /**
     * Adds or replaces a property.
     *
     * @param name  property name
     * @param value non-null value
     * @param <T>   value type
     * @return wrapper stored by this collection
     * @throws NullPointerException if name or value is null
     */
    public <T> PropertyValue<T> set(String name, T value) {
        PropertyValue<T> property = new PropertyValue<>(value);
        values.put(Objects.requireNonNull(name, "name"), property);
        return property;
    }

    /**
     * Removes a property.
     *
     * @param name property name
     * @return removed property, or null when absent
     * @throws NullPointerException if name is null
     */
    public PropertyValue<?> remove(String name) {
        return values.remove(Objects.requireNonNull(name, "name"));
    }

    /**
     * Returns the number of stored properties.
     *
     * @return number of stored properties
     */
    public int size() {
        return values.size();
    }

    /**
     * Tests whether this collection contains no properties.
     *
     * @return whether no properties are stored
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Removes every property from this collection.
     */
    public void clear() {
        values.clear();
    }

    /**
     * Returns an immutable snapshot of the current properties. Later changes to this
     * collection are not reflected in the returned map.
     *
     * @return immutable property snapshot
     */
    public Map<String, PropertyValue<?>> asMap() {
        return Map.copyOf(values);
    }

    /**
     * Writes this set to a file using the versioned binary property format.
     *
     * @param path destination path
     * @throws IOException if writing fails
     * @see PropertySetIO#write(PropertySet, Path)
     */
    public void write(Path path) throws IOException {
        PropertySetIO.write(this, path);
    }

    /** Writes this set to a caller-owned stream without closing it.
     * @param output destination stream
     * @throws IOException if writing fails */
    public void write(OutputStream output) throws IOException {
        PropertySetIO.write(this, output);
    }

    /** Writes this set at the buffer's current write position.
     * @param buffer destination buffer */
    public void write(DynamicByteBuffer buffer) {
        PropertySetIO.write(this, buffer);
    }

    /** Encodes this set into a new byte array.
     * @return encoded bytes */
    public byte[] toBytes() {
        return PropertySetIO.toBytes(this);
    }

    /** Reads a property set from a file.
     * @param path source path
     * @return decoded set
     * @throws IOException if reading fails or the file is invalid */
    public static PropertySet read(Path path) throws IOException {
        return PropertySetIO.read(path);
    }

    /** Reads a property set from a caller-owned stream without closing it.
     * @param input source stream
     * @return decoded set
     * @throws IOException if reading fails or the data is invalid */
    public static PropertySet read(InputStream input) throws IOException {
        return PropertySetIO.read(input);
    }

    /** Reads a property set at the buffer's current read position.
     * @param buffer source buffer
     * @return decoded set
     * @throws IOException if the data is invalid */
    public static PropertySet read(DynamicByteBuffer buffer) throws IOException {
        return PropertySetIO.read(buffer);
    }

    /** Decodes a property set from bytes.
     * @param bytes encoded bytes
     * @return decoded set
     * @throws IOException if the data is invalid */
    public static PropertySet fromBytes(byte[] bytes) throws IOException {
        return PropertySetIO.fromBytes(bytes);
    }

    /** Writes this set to a human-readable UTF-8 text file.
     * @param path destination path
     * @throws IOException if writing fails */
    public void writeText(Path path) throws IOException {
        PropertySetTextIO.write(this, path);
    }

    /** Writes this set as UTF-8 text to a caller-owned stream.
     * @param output destination stream
     * @throws IOException if writing fails */
    public void writeText(OutputStream output) throws IOException {
        PropertySetTextIO.write(this, output);
    }

    /** Writes this set to a caller-owned character stream.
     * @param writer destination writer
     * @throws IOException if writing fails */
    public void writeText(Writer writer) throws IOException {
        PropertySetTextIO.write(this, writer);
    }

    /** Returns this set's editable text representation.
     * @return typed property text */
    public String toText() {
        return PropertySetTextIO.toText(this);
    }

    /** Reads a human-readable UTF-8 property file.
     * @param path source path
     * @return decoded set
     * @throws IOException if reading fails or the text is invalid */
    public static PropertySet readText(Path path) throws IOException {
        return PropertySetTextIO.read(path);
    }

    /** Reads UTF-8 property text from a caller-owned stream.
     * @param input source stream
     * @return decoded set
     * @throws IOException if reading fails or the text is invalid */
    public static PropertySet readText(InputStream input) throws IOException {
        return PropertySetTextIO.read(input);
    }

    /** Reads property text from a caller-owned character stream.
     * @param reader source reader
     * @return decoded set
     * @throws IOException if reading fails or the text is invalid */
    public static PropertySet readText(Reader reader) throws IOException {
        return PropertySetTextIO.read(reader);
    }

    /** Decodes a property set from human-readable text.
     * @param text encoded text
     * @return decoded set
     * @throws IOException if the text is invalid */
    public static PropertySet fromText(String text) throws IOException {
        return PropertySetTextIO.fromText(text);
    }
}
