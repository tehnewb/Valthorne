package valthorne.ui.theme;

/**
 * <p>
 * {@code ThemeRule} represents a single rule entry inside the Valthorne theming system.
 * A rule targets a specific UI element type, an optional style name, and a set of
 * required and blocked state flags. When a theme is resolved, matching rules contribute
 * their values into the final {@link ResolvedStyle}.
 * </p>
 *
 * <p>
 * Conceptually, a theme rule answers the question:
 * </p>
 *
 * <blockquote>
 * What style values should be applied when a certain type of UI element, optionally
 * with a specific style name, is in a certain combination of states?
 * </blockquote>
 *
 * <p>
 * Each rule contains:
 * </p>
 *
 * <ul>
 *     <li>an element type the rule applies to</li>
 *     <li>an optional style name for named style variants</li>
 *     <li>a set of required state flags that must all be present</li>
 *     <li>a set of blocked state flags that must all be absent</li>
 *     <li>a {@link StyleMap} of key/value pairs contributed by the rule</li>
 * </ul>
 *
 * <p>
 * Rules are later collected and sorted by priority during theme resolution.
 * More specific rules generally receive higher priority than broader ones. For example,
 * a rule with a style name and several required states will typically sort after a
 * generic base rule, allowing its values to override less specific entries.
 * </p>
 *
 * <p>
 * This class also provides a fluent {@link #set(StyleKey, Object)} method so rules
 * can be configured in a compact chained style when building theme definitions.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * ThemeRule base = new ThemeRule(Button.class, null)
 *         .set(MyStyleKeys.PADDING, 8f)
 *         .set(MyStyleKeys.CORNER_RADIUS, 6f);
 *
 * ThemeRule hoveredPrimary = new ThemeRule(
 *         Button.class,
 *         "primary",
 *         StyleState.HOVERED,
 *         StyleState.DISABLED
 * ).set(MyStyleKeys.BACKGROUND, hoverDrawable)
 *  .set(MyStyleKeys.TEXT_COLOR, Color.WHITE);
 *
 * boolean matches = hoveredPrimary.matches(StyleState.HOVERED);
 * int priority = hoveredPrimary.getPriority();
 * StyleMap values = hoveredPrimary.getValues();
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete use of the class: creating rules,
 * assigning style values, checking whether a state combination matches, reading
 * computed priority, and accessing the stored values.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 9th, 2026
 */
public final class ThemeRule {

    private final Class<?> elementType; // UI element type targeted by this rule
    private final String styleName; // Optional style name variant targeted by this rule
    private final short requiredStates; // Bit flags that must all be present for this rule to match
    private final short blockedStates; // Bit flags that must all be absent for this rule to match
    private final StyleMap values = new StyleMap(); // Style values contributed by this rule when it matches

    /**
     * <p>
     * Creates a new theme rule for the given element type and style name with no
     * required or blocked states.
     * </p>
     *
     * <p>
     * This is the broadest rule form and is useful for defining baseline styles
     * that apply regardless of interaction state.
     * </p>
     *
     * @param elementType the element type targeted by this rule
     * @param styleName   the optional style name targeted by this rule
     */
    public ThemeRule(Class<?> elementType, String styleName) {
        this(elementType, styleName, StyleState.NONE, StyleState.NONE);
    }

    /**
     * <p>
     * Creates a new theme rule for the given element type, style name, and required
     * state flags with no blocked states.
     * </p>
     *
     * @param elementType    the element type targeted by this rule
     * @param styleName      the optional style name targeted by this rule
     * @param requiredStates the state flags that must be present for a match
     */
    public ThemeRule(Class<?> elementType, String styleName, short requiredStates) {
        this(elementType, styleName, requiredStates, StyleState.NONE);
    }

    /**
     * <p>
     * Creates a new theme rule with full control over element type, style name,
     * required states, and blocked states.
     * </p>
     *
     * <p>
     * The rule does not copy these values into another structure; they are stored
     * directly and later used by {@link #matches(short)} and {@link #getPriority()}.
     * </p>
     *
     * @param elementType    the element type targeted by this rule
     * @param styleName      the optional style name targeted by this rule
     * @param requiredStates the state flags that must all be present
     * @param blockedStates  the state flags that must all be absent
     */
    public ThemeRule(Class<?> elementType, String styleName, short requiredStates, short blockedStates) {
        this.elementType = elementType;
        this.styleName = styleName;
        this.requiredStates = requiredStates;
        this.blockedStates = blockedStates;
    }

    /**
     * <p>
     * Returns the element type targeted by this rule.
     * </p>
     *
     * @return the targeted element type
     */
    public Class<?> getElementType() {
        return elementType;
    }

    /**
     * <p>
     * Returns the optional style name targeted by this rule.
     * </p>
     *
     * <p>
     * A {@code null} or empty style name generally represents an unnamed base rule.
     * </p>
     *
     * @return the style name, or {@code null} if none was assigned
     */
    public String getStyleName() {
        return styleName;
    }

    /**
     * <p>
     * Returns the required state flags for this rule.
     * </p>
     *
     * @return the required state bitmask
     */
    public short getRequiredStates() {
        return requiredStates;
    }

    /**
     * <p>
     * Returns the blocked state flags for this rule.
     * </p>
     *
     * @return the blocked state bitmask
     */
    public short getBlockedStates() {
        return blockedStates;
    }

    /**
     * <p>
     * Returns the style values stored by this rule.
     * </p>
     *
     * <p>
     * These values are merged into resolved theme output when the rule matches.
     * </p>
     *
     * @return the rule's style value map
     */
    public StyleMap getValues() {
        return values;
    }

    /**
     * <p>
     * Returns whether this rule matches the provided state flags.
     * </p>
     *
     * <p>
     * A match occurs only when:
     * </p>
     *
     * <ul>
     *     <li>all required state flags are present in {@code states}</li>
     *     <li>none of the blocked state flags are present in {@code states}</li>
     * </ul>
     *
     * @param states the active state flags to test against this rule
     * @return {@code true} if the rule matches the provided state combination
     */
    public boolean matches(short states) {
        return (states & requiredStates) == requiredStates && (states & blockedStates) == 0;
    }

    /**
     * <p>
     * Computes and returns this rule's priority value.
     * </p>
     *
     * <p>
     * The priority is used during theme resolution to sort matching rules before
     * their values are applied. Higher priority rules are considered more specific.
     * </p>
     *
     * <p>
     * The priority calculation considers:
     * </p>
     *
     * <ul>
     *     <li>type depth in the class hierarchy</li>
     *     <li>whether a non-empty style name is present</li>
     *     <li>how many required state bits are specified</li>
     *     <li>how many blocked state bits are specified</li>
     * </ul>
     *
     * <p>
     * This makes named, state-specific rules sort after more generic base rules.
     * </p>
     *
     * @return the computed priority for this rule
     */
    public int getPriority() {
        int priority = 0;

        Class<?> type = elementType;
        while (type != null) {
            priority++;
            type = type.getSuperclass();
        }

        if (styleName != null && !styleName.isEmpty()) priority += 1000;

        priority += Integer.bitCount(requiredStates & 0xFFFF) * 100;
        priority -= Integer.bitCount(blockedStates & 0xFFFF) * 10;

        return priority;
    }

    /**
     * <p>
     * Stores a style value in this rule and returns the rule for fluent chaining.
     * </p>
     *
     * <p>
     * This method is commonly used while building themes so multiple values can be
     * assigned in a concise chained form.
     * </p>
     *
     * @param key   the style key to assign
     * @param value the value to store for the key
     * @param <T>   the value type
     * @return this rule for fluent configuration
     */
    public <T> ThemeRule set(StyleKey<T> key, T value) {
        values.set(key, value);
        return this;
    }
}