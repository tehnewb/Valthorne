package valthorne.state;

import java.util.Arrays;

/**
 * Internal adjacency list for a state. Mutable only during graph configuration.
 * Runtime machines cache this slot, avoiding a state-map lookup every update.
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
final class StateSlot<C> {
    final State<C> state; // State behavior owned or referenced by this graph.
    Transition<C>[] rules; // Priority-sorted outgoing rules; null when none exist.
    int count; // Number of occupied outgoing-rule slots.

    /**
     * Creates an empty adjacency list.
     *
     * @param state non-null state behavior
     */
    StateSlot(State<C> state) {
        this.state = state;
    }

    /**
     * Inserts a rule in descending priority and stable insertion order.
     *
     * @param rule newly registered rule
     */
    @SuppressWarnings("unchecked")
    void add(Transition<C> rule) {
        if (rules == null) rules = (Transition<C>[]) new Transition<?>[2];
        else if (count == rules.length) rules = Arrays.copyOf(rules, count + (count >> 1) + 1);
        int i = count;
        while (i > 0 && rules[i - 1].priority() < rule.priority()) {
            rules[i] = rules[i - 1];
            i--;
        }
        rules[i] = rule;
        count++;
    }

    /**
     * Releases spare reference slots when the graph becomes immutable.
     */
    void trim() {
        if (rules != null && count != rules.length) rules = Arrays.copyOf(rules, count);
    }
}
