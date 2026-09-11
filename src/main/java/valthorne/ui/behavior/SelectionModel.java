package valthorne.ui.behavior;

import java.util.BitSet;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Mutable index-based selection for lists, grids and trees, with single-item,
 * toggle and inclusive range policies. Membership uses a bit set without
 * allocating an object for each item. The anchor starts an extended range and
 * the lead tracks the most recent selection target; either can refer to an
 * unselected item after a toggle, or be -1 when unset.
 *
 * <pre>{@code
 * SelectionModel selection = new SelectionModel(100, true);
 * selection.select(10, false, false);
 * selection.select(15, true, false);
 * selection.select(30, false, true);
 * selection.forEachSelected(index -> System.out.println(index));
 * }</pre>
 *
 * <p>Selections refer to positions, not data identities. Changing item order
 * requires application-level remapping or clearing; changing the item count
 * only removes out-of-range membership and adjusts invalid anchor/lead values.
 * This model does not manipulate focus, scroll position or rendered item state.</p>
 *
 * <p>Use on the UI thread; operations are not synchronized. Notifications are
 * synchronous and occur after state updates, at most once per direct mutator
 * call. Some no-op cases return silently, while repeated range requests still
 * notify. Listener failures propagate without rolling back the updated state;
 * listener-driven mutations can produce nested notifications.</p>
 *
 * @author Albert Beaupre
 */
public final class SelectionModel {
    private final BitSet selected = new BitSet(); // Selected positions within the current item range.
    private final ChangeSignal changes = new ChangeSignal(); // Synchronous notifications after selection or configuration changes.
    private int count, anchor = -1, lead = -1; // Item count, range origin and latest target; unset positions use -1.
    private boolean multiple; // Whether toggle and range requests may retain multiple items.

    /**
     * Creates an empty selection over the supplied number of items. Anchor and
     * lead start unset, regardless of whether multiple selection is enabled.
     *
     * @param count    the nonnegative number of selectable positions
     * @param multiple whether more than one position may be selected
     * @throws IllegalArgumentException if count is negative
     */
    public SelectionModel(int count, boolean multiple) {
        if (count < 0) throw new IllegalArgumentException("Negative count");
        this.count = count;
        this.multiple = multiple;
    }

    /**
     * Returns the current upper exclusive bound for valid item indices. This
     * describes the available data range, independently of selection membership.
     *
     * @return the nonnegative item count
     */
    public int itemCount() {return count;}

    /**
     * Returns the origin used for extended selection ranges. Toggling an item
     * sets this origin even when that operation removes the item from selection.
     *
     * @return the range-origin index, or -1 when unset
     */
    public int anchor() {return anchor;}

    /**
     * Returns the latest selection target used by keyboard navigation and range
     * extension. The target need not currently belong to the selected set.
     *
     * @return the lead index, or -1 when unset
     */
    public int lead() {return lead;}

    /**
     * Reports whether toggle and range policies are enabled. When false,
     * selection requests replace membership with a single target.
     *
     * @return whether multiple selection is enabled
     */
    public boolean isMultiple() {return multiple;}

    /**
     * Tests membership without requiring the caller to validate an index first.
     * Negative indices and positions at or beyond the item count return false.
     *
     * @param index the position to test
     * @return true only for an in-range selected item
     */
    public boolean isSelected(int index) {return index >= 0 && index < count && selected.get(index);}

    /**
     * Counts the currently selected bits, independently of anchor and lead.
     * The count is computed from membership rather than maintained separately.
     *
     * @return the number of selected items
     */
    public int selectedCount() {return selected.cardinality();}

    /**
     * Finds the next selected position at or after the supplied starting index.
     * This supports ascending traversal without copying the selected set.
     *
     * @param fromIndex the nonnegative inclusive search start
     * @return the next selected index, or -1 if none remains
     * @throws IndexOutOfBoundsException if fromIndex is negative
     */
    public int nextSelected(int fromIndex) {return selected.nextSetBit(fromIndex);}

    /**
     * Registers a synchronous observer of selection and configuration changes.
     * The listener receives no payload and should read the current model state.
     * Registration does not invoke it; close the returned handle to unsubscribe.
     * Dispatch ordering and subscription changes follow {@link ChangeSignal}.
     *
     * @param listener the nonnull change callback
     * @return an independently removable subscription
     * @throws NullPointerException if listener is null
     */
    public AutoCloseable onChange(Runnable listener) {return changes.subscribe(listener);}

    /**
     * Visits selected positions in ascending order without taking a snapshot.
     * Callbacks should not mutate this model during traversal: each next position
     * is read from live membership, so changes can alter which later items run.
     * Callback failures propagate and stop traversal immediately.
     *
     * @param action the nonnull consumer for each selected index
     * @throws NullPointerException if action is null, even for an empty selection
     */
    public void forEachSelected(IntConsumer action) {
        Objects.requireNonNull(action);
        for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1)) action.accept(i);
    }

    /**
     * Updates the available item range while retaining surviving selected
     * positions. Shrinking clears out-of-range bits and unsets an invalid anchor.
     * An invalid lead moves to the highest surviving selected index, or -1 if
     * none remains. Growing does not restore previously removed selections.
     *
     * <p>A changed count emits one notification even if membership is unchanged;
     * the same count returns silently.</p>
     *
     * @param count the replacement nonnegative item count
     * @throws IllegalArgumentException if count is negative
     */
    public void itemCount(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative count");
        if (this.count == count) return;
        if (count < selected.length()) selected.clear(count, selected.length());
        this.count = count;
        if (anchor >= count) anchor = -1;
        if (lead >= count) lead = selected.previousSetBit(count - 1);
        changes.fire();
    }

    /**
     * Changes whether multiple items may be selected. Disabling the mode with
     * nonempty membership keeps the selected lead if possible, otherwise the
     * lowest selected index, and sets both anchor and lead to the retained item.
     * With empty membership, existing anchor and lead values are preserved.
     *
     * @param value the desired multiple-selection mode; a changed mode emits one
     *              notification, while the existing mode returns silently
     */
    public void multiple(boolean value) {
        if (multiple == value) return;
        multiple = value;
        if (!value && !selected.isEmpty()) {
            int keep = isSelected(lead) ? lead : selected.nextSetBit(0);
            selected.clear();
            selected.set(keep);
            anchor = lead = keep;
        }
        changes.fire();
    }

    /**
     * Removes all membership and resets anchor and lead to -1. A notification
     * is emitted if any of these values needed clearing, including an empty set
     * that still had a toggle target. An already fully cleared model is unchanged.
     */
    public void clear() {
        if (selected.isEmpty() && anchor == -1 && lead == -1) return;
        selected.clear();
        anchor = lead = -1;
        changes.fire();
    }

    /**
     * Applies the selection policy used by pointer and keyboard interactions.
     * Single-selection mode ignores both modifiers and replaces membership with
     * the target. In multiple mode, an unmodified request does the same; a toggle
     * without extension flips the target bit and sets both anchor and lead to it.
     *
     * <p>Extension selects the inclusive range between anchor and target, creating
     * an anchor at the target if none exists. Without toggle the range replaces
     * membership; with toggle it is added to existing membership rather than
     * flipping each range bit. Extension preserves the anchor and updates lead.</p>
     *
     * <p>Replacement returns silently only when membership already contains just
     * the target and both anchor and lead match it. Other valid requests notify
     * once, including an extended range whose membership was already selected.</p>
     *
     * @param index  the zero-based target within the current item range
     * @param extend whether to extend from anchor, normally the Shift modifier
     * @param toggle whether to toggle one item or add an extended range, normally Ctrl/Cmd
     * @throws IndexOutOfBoundsException if index is outside the current item range
     */
    public void select(int index, boolean extend, boolean toggle) {
        Objects.checkIndex(index, count);
        if (!multiple || (!extend && !toggle)) {
            if (selected.cardinality() == 1 && selected.get(index) && anchor == index && lead == index) return;
            selected.clear();
            selected.set(index);
            anchor = lead = index;
        } else if (extend) {
            if (anchor < 0) anchor = index;
            if (!toggle) selected.clear();
            selected.set(Math.min(anchor, index), Math.max(anchor, index) + 1);
            lead = index;
        } else {
            selected.flip(index);
            anchor = lead = index;
        }
        changes.fire();
    }

    /**
     * Selects the entire item range in multiple mode, setting anchor to zero and
     * lead to the final item. If every item is already selected, returns without
     * changing anchor or lead or notifying. An empty model also returns silently.
     *
     * <p>In single-selection mode this selects the current lead, or index zero
     * when lead is unset, using the normal replacement policy. A changed
     * selection emits one notification.</p>
     */
    public void selectAll() {
        if (count == 0) return;
        if (!multiple) {
            select(lead < 0 ? 0 : lead, false, false);
            return;
        }
        if (selected.cardinality() == count) return;
        selected.set(0, count);
        anchor = 0;
        lead = count - 1;
        changes.fire();
    }
}
