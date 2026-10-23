package valthorne.browser.json;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Immutable parse-time JSON view used by the browser LDtk loader. Scalars retain
 * Java long precision, arrays preserve order, and objects preserve insertion order.
 * Missing properties are distinct from explicit JSON null for get/has semantics.
 * Nodes never reference browser globals or graphics resources.
 */
public final class JsonNode implements Iterable<JsonNode> {
    /**
     * Shared immutable fallback for path access to absent properties.
     */
    private static final JsonNode MISSING = new JsonNode(null);
    private final Object value; // String, Number, Boolean, child collection, or JSON null.

    /**
     * Takes ownership of a completed parser value; collections are never mutated again.
     * @param value decoded JSON value
     */
    JsonNode(Object value) {
        this.value = value instanceof List<?> list ? Collections.unmodifiableList(list) : value instanceof Map<?, ?> map ? Collections.unmodifiableMap(map) : value;
    }

    /**
     * Returns an object property, or null when absent or not an object.
     * @param name property name
     * @return child node or null
     */
    @SuppressWarnings("unchecked")
    public JsonNode get(String name) {
        return value instanceof Map<?, ?> ? ((Map<String, JsonNode>) value).get(name) : null;
    }

    /**
     * Returns one array element with null for an unavailable index.
     * @param index zero-based index
     * @return child node or null
     */
    @SuppressWarnings("unchecked")
    public JsonNode get(int index) {
        return value instanceof List<?> list && index >= 0 && index < list.size() ? ((List<JsonNode>) list).get(index) : null;
    }

    /**
     * Returns a property or an immutable missing-node fallback.
     * @param name property name
     * @return always nonnull node
     */
    public JsonNode path(String name) {
        JsonNode node = get(name);
        return node == null ? MISSING : node;
    }

    /**
     * Checks whether an object contains a property, including explicit JSON null.
     * @param name property name
     * @return whether the property is present
     */
    public boolean has(String name) {
        return get(name) != null;
    }

    /**
     * Reports JSON null or a missing-node fallback.
     * @return whether no scalar or container value exists
     */
    public boolean isNull() {
        return value == null;
    }

    /**
     * Reports whether the value is a JSON string.
     * @return string type flag
     */
    public boolean isTextual() {
        return value instanceof String;
    }

    /**
     * Reports whether the value is a JSON Boolean.
     * @return Boolean type flag
     */
    public boolean isBoolean() {
        return value instanceof Boolean;
    }

    /**
     * Reports an integral JSON number retained without double conversion.
     * @return integral type flag
     */
    public boolean isIntegralNumber() {
        return value instanceof Long;
    }

    /**
     * Reports a fractional, exponent, or out-of-long-range JSON number.
     * @return floating-point type flag
     */
    public boolean isFloatingPointNumber() {
        return value instanceof Double;
    }

    /**
     * Reports whether the value is numeric.
     * @return numeric type flag
     */
    public boolean isNumber() {
        return value instanceof Number;
    }

    /**
     * Reports whether the value is an ordered JSON array.
     * @return array type flag
     */
    public boolean isArray() {
        return value instanceof List<?>;
    }

    /**
     * Reports whether the value is an ordered JSON object.
     * @return object type flag
     */
    public boolean isObject() {
        return value instanceof Map<?, ?>;
    }

    /**
     * Returns a container's child count; scalar nodes have zero children.
     * @return child count
     */
    public int size() {
        if (value instanceof List<?> list) return list.size();
        if (value instanceof Map<?, ?> map) return map.size();
        return 0;
    }

    /**
     * Reports whether this node has no children.
     * @return whether size is zero
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns a string scalar without coercion.
     * @return string value or null
     */
    public String textValue() {
        return value instanceof String text ? text : null;
    }

    /**
     * Returns a Boolean scalar without coercion.
     * @return Boolean value or false
     */
    public boolean booleanValue() {
        return Boolean.TRUE.equals(value);
    }

    /**
     * Returns an exact integral scalar or a numeric narrowing conversion.
     * @return numeric long value or zero
     */
    public long longValue() {
        return value instanceof Number number ? number.longValue() : 0;
    }

    /**
     * Returns a numeric scalar as double.
     * @return numeric value or zero
     */
    public double doubleValue() {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    /**
     * Converts scalar values to text with empty text for containers.
     * @return scalar text, including the JSON null spelling
     */
    public String asText() {
        return isArray() || isObject() ? "" : String.valueOf(value);
    }

    /**
     * Coerces numeric, Boolean, or numeric-text values to an integer.
     * @return coerced value or zero
     */
    public int asInt() {
        return asInt(0);
    }

    /**
     * Coerces numeric, Boolean, or numeric-text values to an integer.
     * @param fallback invalid-value fallback
     * @return coerced value or fallback
     */
    public int asInt(int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof Boolean flag) return flag ? 1 : 0;
        if (value instanceof String text) {
            try {
                return (int) Double.parseDouble(text.trim());
            } catch (NumberFormatException failure) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Coerces a scalar to double with a zero fallback.
     * @return coerced value or zero
     */
    public double asDouble() {
        return asDouble(0);
    }

    /**
     * Coerces numeric, Boolean, or numeric-text values to double.
     * @param fallback invalid-value fallback
     * @return coerced value or fallback
     */
    public double asDouble(double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Boolean flag) return flag ? 1 : 0;
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException failure) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Coerces Boolean or Boolean-text values, preserving the supplied fallback.
     * @param fallback invalid-value fallback
     * @return decoded Boolean or fallback
     */
    public boolean asBoolean(boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.doubleValue() != 0;
        if (value instanceof String text) {
            if (text.trim().equals("true")) return true;
            if (text.trim().equals("false")) return false;
        }
        return fallback;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<JsonNode> iterator() {
        if (value instanceof List<?>) return ((List<JsonNode>) value).iterator();
        if (value instanceof Map<?, ?>) return ((Map<String, JsonNode>) value).values().iterator();
        return Collections.emptyIterator();
    }

    /**
     * Iterates immutable object entries in source order.
     * @return entry iterator, empty for non-object nodes
     */
    @SuppressWarnings("unchecked")
    public Iterator<Map.Entry<String, JsonNode>> fields() {
        return value instanceof Map<?, ?> ? Collections.unmodifiableMap((Map<String, JsonNode>) value).entrySet().iterator() : Collections.emptyIterator();
    }
}
