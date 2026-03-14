package valthorne.ui.theme;

import valthorne.JGL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * {@code ThemeData} is the central runtime representation of a UI theme in Valthorne.
 * It stores style tokens, named resources, and rule sets that can later be resolved
 * into a final {@link ResolvedStyle} for a specific UI element type, optional style
 * name, and active state combination.
 * </p>
 *
 * <p>
 * This class is the main data container used by the theming system. It supports
 * several layers of styling:
 * </p>
 *
 * <ul>
 *     <li>global tokens that act as base values</li>
 *     <li>named resources for non-style data such as shared drawables or assets</li>
 *     <li>type-based rules for specific UI element classes</li>
 *     <li>style-name-specific rules</li>
 *     <li>state-based rules using required and blocked state flags</li>
 *     <li>final per-node override maps</li>
 * </ul>
 *
 * <p>
 * Resolution works by starting with a copy of the token map, collecting all matching
 * rules for the requested element type and optional style name, sorting those rules by
 * priority, applying their values in order, and then finally applying explicit override
 * values if provided. The result is wrapped in a {@link ResolvedStyle}.
 * </p>
 *
 * <p>
 * The class also integrates with the event system. Any time tokens or resources are
 * changed, a reusable {@link ThemeDataChangeEvent} is updated and published through
 * {@link JGL}. This allows theme-aware systems to react to theme changes automatically.
 * </p>
 *
 * <p>
 * Rules are stored by element type and then by style name. During collection, the class
 * walks up the inheritance chain of the provided element type so that superclass rules
 * can participate in resolution.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * ThemeData theme = new ThemeData();
 *
 * theme.setToken(MyStyleKeys.TEXT_COLOR, Color.WHITE);
 * theme.setResource("buttonIcon", someDrawable);
 *
 * theme.rule(Button.class)
 *      .set(MyStyleKeys.PADDING, 8f)
 *      .set(MyStyleKeys.CORNER_RADIUS, 4f);
 *
 * theme.rule(Button.class, "primary", StyleState.HOVERED)
 *      .set(MyStyleKeys.BACKGROUND, hoverDrawable);
 *
 * StyleMap overrides = new StyleMap();
 * overrides.set(MyStyleKeys.PADDING, 12f);
 *
 * ResolvedStyle style = theme.resolve(Button.class, "primary", StyleState.HOVERED, overrides);
 * Color textColor = style.get(MyStyleKeys.TEXT_COLOR);
 *
 * theme.addThemeDataChangeListener(event -> {
 *     System.out.println("Theme changed");
 * });
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete workflow of the class: adding tokens,
 * adding resources, creating rules, resolving a final style, and listening for
 * theme changes.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 9th, 2026
 */
public class ThemeData {

    private final StyleMap tokens = new StyleMap(); // Global base style values copied into each resolved style
    private final Map<String, Object> resources = new HashMap<>(); // Named shared resources associated with the theme
    private final Map<Class<?>, Map<String, ArrayList<ThemeRule>>> rules = new HashMap<>(); // Theme rules grouped by element type and style name
    private static final ThemeDataChangeEvent THEME_EVENT = new ThemeDataChangeEvent(null);

    /**
     * <p>
     * Stores or replaces a theme token value.
     * </p>
     *
     * <p>
     * Tokens act as the base style layer for all later style resolution. After the token
     * is updated, a shared {@link ThemeDataChangeEvent} is published through {@link JGL}
     * with this theme data instance attached.
     * </p>
     *
     * @param key   the style key to set
     * @param value the value associated with the key
     * @param <T>   the value type
     */
    public <T> void setToken(StyleKey<T> key, T value) {
        tokens.set(key, value);
        JGL.publish(THEME_EVENT.setData(this));
    }

    /**
     * <p>
     * Returns the current token value associated with the supplied style key.
     * </p>
     *
     * @param key the style key to query
     * @param <T> the expected value type
     * @return the token value associated with the key
     */
    public <T> T getToken(StyleKey<T> key) {
        return tokens.get(key);
    }

    /**
     * <p>
     * Stores or replaces a named resource in the theme.
     * </p>
     *
     * <p>
     * Resources are not part of the style rule map itself, but instead provide a place
     * to store shared named objects such as images, drawables, fonts, or any other data
     * that theme users may want to retrieve by name. After the resource is updated, a
     * theme data change event is published.
     * </p>
     *
     * @param name  the resource name
     * @param value the resource value
     */
    public void setResource(String name, Object value) {
        resources.put(name, value);
        JGL.publish(THEME_EVENT.setData(this));
    }

    /**
     * <p>
     * Returns a named resource cast to the requested type.
     * </p>
     *
     * @param name the resource name
     * @param type the expected resource type
     * @param <T>  the expected resource type
     * @return the stored resource cast to the requested type
     */
    public <T> T getResource(String name, Class<T> type) {
        return type.cast(resources.get(name));
    }

    /**
     * <p>
     * Creates and registers a new rule for the given element type with no style name
     * and no required or blocked states.
     * </p>
     *
     * @param elementType the UI element type the rule should target
     * @return the newly created rule
     */
    public ThemeRule rule(Class<?> elementType) {
        return rule(elementType, null, StyleState.NONE, StyleState.NONE);
    }

    /**
     * <p>
     * Creates and registers a new rule for the given element type and style name with
     * no required or blocked states.
     * </p>
     *
     * @param elementType the UI element type the rule should target
     * @param styleName   the optional style name
     * @return the newly created rule
     */
    public ThemeRule rule(Class<?> elementType, String styleName) {
        return rule(elementType, styleName, StyleState.NONE, StyleState.NONE);
    }

    /**
     * <p>
     * Creates and registers a new rule for the given element type and state.
     * </p>
     *
     * <p>
     * If the supplied state is {@code null}, {@link StyleState#NONE} is used.
     * </p>
     *
     * @param elementType the UI element type the rule should target
     * @param state       the required state
     * @return the newly created rule
     */
    public ThemeRule rule(Class<?> elementType, StyleState state) {
        return rule(elementType, null, state == null ? StyleState.NONE : state.getFlags(), StyleState.NONE);
    }

    /**
     * <p>
     * Creates and registers a new rule for the given element type, style name, and state.
     * </p>
     *
     * <p>
     * If the supplied state is {@code null}, {@link StyleState#NONE} is used.
     * </p>
     *
     * @param elementType the UI element type the rule should target
     * @param styleName   the optional style name
     * @param state       the required state
     * @return the newly created rule
     */
    public ThemeRule rule(Class<?> elementType, String styleName, StyleState state) {
        return rule(elementType, styleName, state == null ? StyleState.NONE : state.getFlags(), StyleState.NONE);
    }

    /**
     * <p>
     * Creates and registers a new rule for the given element type using raw required
     * state flags.
     * </p>
     *
     * @param elementType    the UI element type the rule should target
     * @param requiredStates the required state flags
     * @return the newly created rule
     */
    public ThemeRule rule(Class<?> elementType, short requiredStates) {
        return rule(elementType, null, requiredStates, StyleState.NONE);
    }

    /**
     * <p>
     * Creates and registers a new rule for the given element type, style name, and raw
     * required state flags.
     * </p>
     *
     * @param elementType    the UI element type the rule should target
     * @param styleName      the optional style name
     * @param requiredStates the required state flags
     * @return the newly created rule
     */
    public ThemeRule rule(Class<?> elementType, String styleName, short requiredStates) {
        return rule(elementType, styleName, requiredStates, StyleState.NONE);
    }

    /**
     * <p>
     * Creates and registers a new rule with full control over element type, style name,
     * required states, and blocked states.
     * </p>
     *
     * <p>
     * Rules are stored first by element type and then by style name. A {@code null}
     * style name is normalized to an empty string key. The created rule is appended to
     * the list for later resolution.
     * </p>
     *
     * @param elementType    the UI element type the rule should target
     * @param styleName      the optional style name
     * @param requiredStates the bit flags that must be present
     * @param blockedStates  the bit flags that must not be present
     * @return the newly created rule
     */
    public ThemeRule rule(Class<?> elementType, String styleName, short requiredStates, short blockedStates) {
        Map<String, ArrayList<ThemeRule>> byName = rules.computeIfAbsent(elementType, k -> new HashMap<>());
        String key = styleName == null ? "" : styleName;
        ArrayList<ThemeRule> list = byName.computeIfAbsent(key, k -> new ArrayList<>());
        ThemeRule rule = new ThemeRule(elementType, styleName, requiredStates, blockedStates);
        list.add(rule);
        return rule;
    }

    /**
     * <p>
     * Resolves a style for the given element type, style name, state object, and override map.
     * </p>
     *
     * <p>
     * If the provided state is {@code null}, {@link StyleState#NONE} is used.
     * </p>
     *
     * @param elementType the element type to resolve for
     * @param styleName   the optional style name
     * @param state       the active state
     * @param overrides   explicit override values
     * @return the resolved style
     */
    public ResolvedStyle resolve(Class<?> elementType, String styleName, StyleState state, StyleMap overrides) {
        return resolve(elementType, styleName, state == null ? StyleState.NONE : state.getFlags(), overrides);
    }

    /**
     * <p>
     * Resolves a style for the given element type, state object, and override map.
     * </p>
     *
     * @param elementType the element type to resolve for
     * @param state       the active state
     * @param overrides   explicit override values
     * @return the resolved style
     */
    public ResolvedStyle resolve(Class<?> elementType, StyleState state, StyleMap overrides) {
        return resolve(elementType, null, state == null ? StyleState.NONE : state.getFlags(), overrides);
    }

    /**
     * <p>
     * Resolves a final {@link ResolvedStyle} for the given element type, optional style
     * name, active state flags, and optional overrides.
     * </p>
     *
     * <p>
     * Resolution starts with a copy of the base token map. Matching unnamed rules for
     * the element type hierarchy are collected first. If a style name is provided,
     * matching named rules are also collected. All matches are then sorted by
     * {@link ThemeRule#getPriority()} and applied in order. Finally, explicit overrides
     * are applied last so they always win.
     * </p>
     *
     * @param elementType the element type to resolve for
     * @param styleName   the optional style name
     * @param states      the active state flags
     * @param overrides   explicit override values applied last
     * @return the resolved style
     */
    public ResolvedStyle resolve(Class<?> elementType, String styleName, short states, StyleMap overrides) {
        StyleMap result = tokens.copy();
        ArrayList<ThemeRule> matches = new ArrayList<>(8);

        collect(matches, elementType, null, states);

        if (styleName != null) collect(matches, elementType, styleName, states);

        matches.sort(Comparator.comparingInt(ThemeRule::getPriority));

        for (ThemeRule match : matches) result.putAll(match.getValues());

        if (overrides != null) result.putAll(overrides);

        return new ResolvedStyle(result);
    }

    /**
     * <p>
     * Resolves a final style for the given element type using raw state flags and
     * optional overrides.
     * </p>
     *
     * @param elementType the element type to resolve for
     * @param states      the active state flags
     * @param overrides   explicit override values
     * @return the resolved style
     */
    public ResolvedStyle resolve(Class<?> elementType, short states, StyleMap overrides) {
        return resolve(elementType, null, states, overrides);
    }

    /**
     * <p>
     * Collects matching rules for the given element type hierarchy, optional style name,
     * and active state flags.
     * </p>
     *
     * <p>
     * The method walks upward through the inheritance chain of the provided element
     * type. For each class, it looks up the rule list associated with the normalized
     * style name and adds only those rules whose {@link ThemeRule#matches(short)}
     * method returns {@code true}.
     * </p>
     *
     * @param out         the output list that receives matching rules
     * @param elementType the starting element type
     * @param styleName   the optional style name
     * @param states      the active state flags
     */
    private void collect(ArrayList<ThemeRule> out, Class<?> elementType, String styleName, short states) {
        Class<?> type = elementType;
        String nameKey = styleName == null ? "" : styleName;

        while (type != null) {
            Map<String, ArrayList<ThemeRule>> byName = rules.get(type);
            if (byName != null) {
                ArrayList<ThemeRule> list = byName.get(nameKey);
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        ThemeRule rule = list.get(i);
                        if (rule.matches(states)) out.add(rule);
                    }
                }
            }
            type = type.getSuperclass();
        }
    }

    /**
     * <p>
     * Registers a listener for theme data change events.
     * </p>
     *
     * <p>
     * The listener is subscribed to {@link ThemeDataChangeEvent} through {@link JGL}.
     * </p>
     *
     * @param listener the listener to register
     */
    public void addThemeDataChangeListener(ThemeListener listener) {
        JGL.subscribe(ThemeDataChangeEvent.class, listener);
    }

    /**
     * <p>
     * Unregisters a previously added theme data change listener.
     * </p>
     *
     * @param listener the listener to remove
     */
    public void removeThemeDataChangeListener(ThemeListener listener) {
        JGL.unsubscribe(ThemeDataChangeEvent.class, listener);
    }
}