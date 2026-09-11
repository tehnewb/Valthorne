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
 * Target metadata is fixed after construction, but the value map is live and mutable.
 * Rules created through ThemeData publish theme changes through that map's callback;
 * directly constructed rules have no change observer and are not automatically
 * registered in a theme. Use on the owning UI thread, without concurrent mutation.
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
    private final StyleMap values; // Style values contributed by this rule when it matches

    /**
     * <p>
     * Creates a new theme rule for the given element type and style name with no
     * required or blocked states.
     * </p>
     *
     * <p>
     * This is the broadest rule form and is useful for defining baseline styles
     * that apply regardless of interaction state.
     * This creates an empty, standalone rule without registering it in ThemeData.
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
     * The required mask is stored unchanged; no state bits are validated or inferred.
     * The new rule is standalone and initially contributes no values.
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
     * No target or mask validation is performed. Overlapping required and blocked
     * bits are accepted but can never match. This public constructor installs a
     * no-op change callback and does not register the rule in a theme.
     * </p>
     *
     * @param elementType    the element type targeted by this rule
     * @param styleName      the optional style name targeted by this rule
     * @param requiredStates the state flags that must all be present
     * @param blockedStates  the state flags that must all be absent
     */
    public ThemeRule(Class<?> elementType, String styleName, short requiredStates, short blockedStates) {
        this(elementType, styleName, requiredStates, blockedStates, () -> {});
    }

    /**
     * Creates a rule whose value map invokes an owning theme's change callback.
     * Metadata is retained unchanged and an empty StyleMap is created immediately;
     * construction itself does not notify. Later map callbacks run after mutation
     * and propagate failures without restoring previous values.
     *
     * @param elementType    the target class, retained without validation
     * @param styleName      the optional variant name, retained without normalization
     * @param requiredStates the bits that must all be present
     * @param blockedStates  the bits that must all be absent
     * @param changed        the nonnull callback used by the rule's value map
     * @throws NullPointerException if changed is null
     */
    ThemeRule(Class<?> elementType, String styleName, short requiredStates, short blockedStates, Runnable changed) {
        this.values = new StyleMap(changed);
        this.elementType = elementType;
        this.styleName = styleName;
        this.requiredStates = requiredStates;
        this.blockedStates = blockedStates;
    }

    /**
     * <p>
     * Returns the element type targeted by this rule.
     * The same Class reference supplied at construction is returned. This metadata
     * is used for theme rule selection, not checked by the state-only matches method.
     * </p>
     *
     * @return the targeted element type, possibly null if constructed that way
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
     * The constructor preserves the original value rather than normalizing null
     * to an empty string; neither form receives the named-style priority bonus.
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
     * All of these bits must occur in a matching state mask; zero imposes no
     * positive state requirement. The stored mask is returned unchanged.
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
     * Any shared bit rejects a match; zero blocks no states. Bits may overlap
     * the required mask, in which case no state combination can satisfy the rule.
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
     * This is the owned, mutable map rather than a copy. Editing it directly uses
     * the same callback as set; mutable value objects remain shared by reference,
     * and changing those objects internally does not trigger map notifications.
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
     * <p>This tests state masks only: it does not compare element classes or style
     * names. ThemeData selects candidate rules before invoking this predicate.
     * Additional active bits that are neither required nor blocked are allowed.</p>
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
     * The exact score is the number of classes in the target's superclass chain,
     * plus 1000 for a nonempty style name, plus 100 per required bit, minus 10 per
     * blocked bit. Bit counts use the unsigned 16-bit masks. A null target contributes
     * zero type depth; interfaces are not traversed as a separate hierarchy.
     * More blocked bits therefore lower the score rather than increasing specificity.
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
     * Values are retained by reference. Null removes an explicit contribution,
     * and an equal replacement is ignored by StyleMap. A changed value invokes
     * the map's callback after storage; this does not register a standalone rule.
     * </p>
     *
     * @param key   the style key to assign
     * @param value the value to store for the key
     * @param <T>   the value type
     * @return this rule for fluent configuration
     * @throws NullPointerException if key is null
     */
    public <T> ThemeRule set(StyleKey<T> key, T value) {
        values.set(key, value);
        return this;
    }
}
