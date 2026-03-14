package valthorne.ui.theme;

/**
 * <p>
 * {@code ResolvedStyle} represents the final computed style values for a UI element
 * after theme tokens, matching rules, and explicit overrides have been merged together.
 * It is the read-oriented result object returned by {@link ThemeData#resolve(Class, String, short, StyleMap)}
 * and related overloads.
 * </p>
 *
 * <p>
 * Internally, the class wraps a {@link StyleMap} that contains the resolved values.
 * It does not itself perform any rule resolution logic. Instead, it acts as a stable
 * container that consumers can query for final values using {@link StyleKey} instances.
 * </p>
 *
 * <p>
 * This class is intentionally lightweight. It provides:
 * </p>
 *
 * <ul>
 *     <li>typed value lookup through {@link #get(StyleKey)}</li>
 *     <li>presence checks through {@link #contains(StyleKey)}</li>
 *     <li>direct access to the underlying map through {@link #asMap()}</li>
 * </ul>
 *
 * <p>
 * A resolved style is typically used by UI nodes during skinning, layout preparation,
 * drawing, and runtime state updates. Because it represents the final merged result,
 * callers generally treat it as the authoritative style view for the node at that moment.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * ThemeData theme = new ThemeData();
 * theme.setToken(MyStyleKeys.TEXT_COLOR, Color.WHITE);
 *
 * ResolvedStyle style = theme.resolve(Button.class, StyleState.NONE, null);
 *
 * Color textColor = style.get(MyStyleKeys.TEXT_COLOR);
 * boolean hasTextColor = style.contains(MyStyleKeys.TEXT_COLOR);
 * StyleMap allValues = style.asMap();
 * }</pre>
 *
 * <p>
 * This example demonstrates the full intended use of the class: receiving a resolved
 * style, reading typed values from it, checking for key presence, and accessing the
 * underlying map when necessary.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 9th, 2026
 */
public final class ResolvedStyle {

    private final StyleMap values; // Final merged style values wrapped by this resolved style

    /**
     * <p>
     * Creates a new {@code ResolvedStyle} wrapping the provided value map.
     * </p>
     *
     * <p>
     * The supplied map is stored directly and becomes the source for all future lookups.
     * </p>
     *
     * @param values the resolved style values to wrap
     */
    public ResolvedStyle(StyleMap values) {
        this.values = values;
    }

    /**
     * <p>
     * Returns the typed value associated with the supplied {@link StyleKey}.
     * </p>
     *
     * @param key the style key to query
     * @param <T> the expected value type
     * @return the value associated with the key
     */
    public <T> T get(StyleKey<T> key) {
        return values.get(key);
    }

    /**
     * <p>
     * Returns whether the supplied key exists in the resolved style.
     * </p>
     *
     * @param key the style key to test
     * @return {@code true} if the key exists in the resolved values
     */
    public boolean contains(StyleKey<?> key) {
        return values.contains(key);
    }

    /**
     * <p>
     * Returns the underlying {@link StyleMap} used by this resolved style.
     * </p>
     *
     * <p>
     * This allows callers to inspect or pass along the full resolved map when direct
     * access to the whole result is needed.
     * </p>
     *
     * @return the underlying resolved style map
     */
    public StyleMap asMap() {
        return values;
    }

}