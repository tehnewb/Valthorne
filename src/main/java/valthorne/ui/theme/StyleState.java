package valthorne.ui.theme;

import valthorne.collections.bits.ShortBits;

/**
 * <p>
 * {@code StyleState} is a compact mutable container used to represent the active
 * visual and interaction state of a UI element in Valthorne's theming system.
 * It wraps a {@link ShortBits} instance and stores state flags inside a single
 * {@code short}, allowing very fast checks, additions, removals, and rule matching.
 * </p>
 *
 * <p>
 * These state flags are used by the theme system to determine which
 * {@link ThemeRule} instances apply to a given UI element at a given moment.
 * For example, a button may currently be hovered, pressed, focused, disabled,
 * checked, or selected. A {@code StyleState} object makes it easy to build,
 * inspect, and compare those flag combinations.
 * </p>
 *
 * <p>
 * The class defines a standard set of built-in flags such as:
 * </p>
 *
 * <ul>
 *     <li>{@link #HOVERED}</li>
 *     <li>{@link #PRESSED}</li>
 *     <li>{@link #FOCUSED}</li>
 *     <li>{@link #DISABLED}</li>
 *     <li>{@link #CHECKED}</li>
 *     <li>{@link #SELECTED}</li>
 *     <li>{@link #DRAGGING}</li>
 *     <li>{@link #ACTIVE}</li>
 *     <li>{@link #ERROR}</li>
 * </ul>
 *
 * <p>
 * This class is mutable and chainable. Methods such as {@link #add(short)},
 * {@link #remove(short)}, {@link #set(short, boolean)}, and {@link #clear()}
 * return the current instance so callers can build state combinations fluently.
 * </p>
 *
 * <p>
 * It also provides a convenience {@link #matches(short, short)} method that mirrors
 * the matching behavior used by {@link ThemeRule}. This makes it useful both for
 * runtime state tracking and for direct comparisons against theme rule requirements.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * StyleState state = new StyleState();
 *
 * state.add(StyleState.HOVERED)
 *      .add(StyleState.FOCUSED)
 *      .set(StyleState.PRESSED, true);
 *
 * boolean hovered = state.has(StyleState.HOVERED);
 * boolean matches = state.matches(
 *         (short) (StyleState.HOVERED | StyleState.FOCUSED),
 *         StyleState.DISABLED
 * );
 *
 * short flags = state.getFlags();
 *
 * state.remove(StyleState.PRESSED);
 * state.clear();
 *
 * StyleState restored = new StyleState(flags);
 * }</pre>
 *
 * <p>
 * This example demonstrates the full intended use of the class: creating state,
 * adding flags, testing flags, matching against required and blocked states,
 * reading the raw bitmask, removing flags, clearing all flags, and restoring
 * state from an existing bitmask.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 9th, 2026
 */
public final class StyleState {

    /**
     * Represents no active state flags.
     */
    public static final short NONE = 0;

    /**
     * State flag indicating that the element is currently hovered.
     */
    public static final short HOVERED = 1;

    /**
     * State flag indicating that the element is currently pressed.
     */
    public static final short PRESSED = 1 << 1;

    /**
     * State flag indicating that the element is currently focused.
     */
    public static final short FOCUSED = 1 << 2;

    /**
     * State flag indicating that the element is currently disabled.
     */
    public static final short DISABLED = 1 << 3;

    /**
     * State flag indicating that the element is currently checked.
     */
    public static final short CHECKED = 1 << 4;

    /**
     * State flag indicating that the element is currently selected.
     */
    public static final short SELECTED = 1 << 5;

    /**
     * State flag indicating that the element is currently being dragged.
     */
    public static final short DRAGGING = 1 << 6;

    /**
     * State flag indicating that the element is currently active.
     */
    public static final short ACTIVE = 1 << 7;

    /**
     * State flag indicating that the element is currently in an error state.
     */
    public static final short ERROR = 1 << 8;

    private final ShortBits bits; // Mutable short-backed bit container storing the current style flags

    /**
     * <p>
     * Creates an empty {@code StyleState} with no active flags.
     * </p>
     *
     * <p>
     * This is equivalent to constructing the class with {@link #NONE}.
     * </p>
     */
    public StyleState() {
        this.bits = new ShortBits();
    }

    /**
     * <p>
     * Creates a {@code StyleState} initialized with the provided raw state flags.
     * </p>
     *
     * @param flags the initial state bitmask
     */
    public StyleState(short flags) {
        this.bits = new ShortBits(flags);
    }

    /**
     * <p>
     * Returns whether all bits in the supplied state mask are currently present.
     * </p>
     *
     * <p>
     * This performs a full bitmask inclusion test, not a single-bit equality test.
     * That means callers may pass a combination of flags and this method returns
     * {@code true} only if every bit in that combination is currently active.
     * </p>
     *
     * @param state the state bitmask to test
     * @return {@code true} if all supplied bits are currently active
     */
    public boolean has(short state) {
        return (bits.getBits() & state) == state;
    }

    /**
     * <p>
     * Adds the supplied state bits to the current state.
     * </p>
     *
     * <p>
     * Existing active bits remain active. The method returns this instance so calls
     * may be chained fluently.
     * </p>
     *
     * @param state the state bits to add
     * @return this state instance
     */
    public StyleState add(short state) {
        bits.setBits((short) (bits.getBits() | state));
        return this;
    }

    /**
     * <p>
     * Removes the supplied state bits from the current state.
     * </p>
     *
     * <p>
     * Any matching active bits are cleared. Non-matching bits remain unchanged.
     * The method returns this instance so calls may be chained fluently.
     * </p>
     *
     * @param state the state bits to remove
     * @return this state instance
     */
    public StyleState remove(short state) {
        bits.setBits((short) (bits.getBits() & ~state));
        return this;
    }

    /**
     * <p>
     * Enables or disables the supplied state bits based on the provided boolean.
     * </p>
     *
     * <p>
     * When {@code enabled} is {@code true}, this behaves like {@link #add(short)}.
     * When {@code enabled} is {@code false}, it behaves like {@link #remove(short)}.
     * </p>
     *
     * @param state   the state bits to enable or disable
     * @param enabled whether those bits should be enabled
     * @return this state instance
     */
    public StyleState set(short state, boolean enabled) {
        return enabled ? add(state) : remove(state);
    }

    /**
     * <p>
     * Clears all active state flags.
     * </p>
     *
     * <p>
     * After this call, {@link #getFlags()} returns {@link #NONE}.
     * </p>
     *
     * @return this state instance
     */
    public StyleState clear() {
        bits.clearAll();
        return this;
    }

    /**
     * <p>
     * Returns the raw short bitmask representing the current active state flags.
     * </p>
     *
     * @return the current state flag bitmask
     */
    public short getFlags() {
        return bits.getBits();
    }

    /**
     * <p>
     * Replaces the current state with the provided raw bitmask.
     * </p>
     *
     * @param flags the new raw state flags
     */
    public void setFlags(short flags) {
        bits.setBits(flags);
    }

    /**
     * <p>
     * Returns whether the current state satisfies a required/blocked rule pair.
     * </p>
     *
     * <p>
     * A match occurs only when:
     * </p>
     *
     * <ul>
     *     <li>all bits in {@code requiredStates} are present</li>
     *     <li>none of the bits in {@code blockedStates} are present</li>
     * </ul>
     *
     * <p>
     * This uses the same matching logic as {@link ThemeRule#matches(short)}.
     * </p>
     *
     * @param requiredStates the bits that must all be present
     * @param blockedStates  the bits that must all be absent
     * @return {@code true} if the current state matches the required and blocked masks
     */
    public boolean matches(short requiredStates, short blockedStates) {
        short flags = bits.getBits();
        return (flags & requiredStates) == requiredStates && (flags & blockedStates) == 0;
    }

    /**
     * <p>
     * Returns the string form of the underlying {@link ShortBits} state.
     * </p>
     *
     * <p>
     * This is mainly useful for debugging and logging.
     * </p>
     *
     * @return a string representation of the current state flags
     */
    @Override
    public String toString() {
        return bits.toString();
    }
}