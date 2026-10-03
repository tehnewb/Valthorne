package valthorne.ui.behavior;

import java.util.*;
import java.util.function.Predicate;

/**
 * Renderer-independent expansion and identity selection for a tree. Visible rows
 * are immutable depth-first snapshots; traversal is iterative even for deep trees.
 * Stop observing when finished to unsubscribe from its root. UI-thread only.
 * @param <T> application value type
 */
public final class TreeModel<T> {
    private final TreeNode<T> root; // Fixed borrowed tree root.
    private final Set<TreeNode<T>> expanded = new HashSet<>(); // Nodes with retained expansion state.
    private final LinkedHashSet<TreeNode<T>> selected = new LinkedHashSet<>(); // Identity selection in insertion order.
    private final ChangeSignal changes = new ChangeSignal(); // Synchronous UI-thread change notifications.
    private Runnable subscription; // Root observation removal action, or null while detached.
    private List<TreeRow<T>> rows = List.of(); // Immutable visible depth-first snapshot.
    private TreeNode<T> lead; // Keyboard navigation node, or null without a lead.
    private TreeNode<T> anchor; // Range-selection origin, or null without selection.
    private boolean rootVisible = true; // Whether the root occupies a visible row.
    private boolean multiple; // Whether discontiguous selection is permitted.
    private Predicate<TreeNode<T>> filter; // Optional match rule that keeps matching paths visible.

    /**
     * Observes the supplied root, initially visible and expanded, with single selection.
     */
    public TreeModel(TreeNode<T> root) {
        this.root = Objects.requireNonNull(root); expanded.add(root);
        observe();
    }
    /**
     * Returns the fixed root represented by this model.
     */
    public TreeNode<T> getRoot() { return root; }
    /**
     * Returns the immutable visible depth-first snapshot.
     */
    public List<TreeRow<T>> getRows() { return rows; }
    /**
     * Returns an immutable selection snapshot in selection insertion order.
     */
    public List<TreeNode<T>> getSelection() { return List.copyOf(selected); }
    /**
     * Returns the keyboard lead, which may be unselected or null.
     */
    public TreeNode<T> getLead() { return lead; }
    /**
     * Tests identity membership in selection.
     */
    public boolean isSelected(TreeNode<T> node) { return selected.contains(node); }
    /**
     * Returns retained expansion state, including currently hidden descendants.
     */
    public boolean isExpanded(TreeNode<T> node) { return expanded.contains(node); }
    /**
     * Returns whether the root itself occupies a row.
     */
    public boolean isRootVisible() { return rootVisible; }
    /**
     * Returns whether discontiguous multiple selection is enabled.
     */
    public boolean isMultipleSelection() { return multiple; }
    /**
     * Shows matches and their ancestors, expanding matching paths without changing saved expansion state.
     */
    public TreeModel<T> filter(Predicate<TreeNode<T>> predicate) {
        filter = predicate;
        rebuild();
        return this;
    }
    /**
     * Observes any model change until the returned action is run.
     */
    public Runnable onChange(Runnable listener) { return changes.subscribe(listener); }
    /**
     * Shows or hides the root; hidden roots always expose their immediate children.
     */
    public TreeModel<T> rootVisible(boolean visible) {
        if (rootVisible != visible) { rootVisible = visible; rebuild(); }
        return this;
    }
    /**
     * Changes selection mode, retaining one selected item when switching to single mode.
     */
    public TreeModel<T> multipleSelection(boolean multiple) {
        this.multiple = multiple;
        if (!multiple && selected.size() > 1) {
            TreeNode<T> keep = selected.contains(lead) ? lead : selected.iterator().next();
            selected.clear(); selected.add(keep); lead = anchor = keep;
        }
        changes.fire(); return this;
    }
    /**
     * Returns a visible row index by identity, or -1 for hidden/foreign/null nodes.
     */
    public int indexOf(TreeNode<T> node) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).node() == node) return i;
        return -1;
    }
    /**
     * Tests whether the supplied node belongs to the root's subtree.
     */
    public boolean contains(TreeNode<T> node) {
        for (; node != null; node = node.getParent()) if (node == root) return true;
        return false;
    }
    /**
     * Rejects null and nodes outside this model before mutation.
     */
    private void requireMember(TreeNode<T> node) {
        if (!contains(node)) throw new IllegalArgumentException("Node is outside this tree");
    }
    /**
     * Opens a member branch; leaves have no disclosure state to change.
     */
    public void expand(TreeNode<T> node) {
        requireMember(node);
        if (!node.isLeaf() && expanded.add(node)) rebuild();
    }
    /**
     * Closes a member branch and promotes descendant selection to a visible ancestor.
     */
    public void collapse(TreeNode<T> node) {
        requireMember(node);
        if (expanded.remove(node)) rebuild();
    }
    /**
     * Toggles a member branch's expansion state.
     */
    public void toggle(TreeNode<T> node) { if (isExpanded(node)) collapse(node); else expand(node); }
    /**
     * Expands every existing branch in one model notification.
     */
    public void expandAll() {
        ArrayDeque<TreeNode<T>> pending = new ArrayDeque<>(); pending.push(root);
        while (!pending.isEmpty()) {
            TreeNode<T> node = pending.pop();
            if (!node.isLeaf()) { expanded.add(node); pending.addAll(node.getChildren()); }
        }
        rebuild();
    }
    /**
     * Clears all expansion state; hidden roots still expose their immediate children.
     */
    public void collapseAll() { expanded.clear(); rebuild(); }
    /**
     * Opens ancestors of a member node without changing selection.
     */
    public void reveal(TreeNode<T> node) {
        requireMember(node);
        if (!rootVisible && node == root) return;
        for (TreeNode<T> parent = node.getParent(); parent != null && contains(parent); parent = parent.getParent()) expanded.add(parent);
        rebuild();
    }
    /**
     * Reveals and exclusively selects a member node; an invisible root is not selectable.
     */
    public void select(TreeNode<T> node) {
        requireMember(node);
        if (node == root && !rootVisible) throw new IllegalArgumentException("The hidden root cannot be selected");
        reveal(node); select(indexOf(node), false, false);
    }
    /**
     * Selects a visible row, with Shift range and Control toggle semantics.
     */
    public void select(int index, boolean extend, boolean toggle) {
        Objects.checkIndex(index, rows.size());
        TreeNode<T> node = rows.get(index).node();
        if (!multiple || (!extend && !toggle)) { selected.clear(); selected.add(node); anchor = node; }
        else if (extend) {
            int start = indexOf(anchor);
            if (start < 0) { start = index; anchor = node; }
            if (!toggle) selected.clear();
            for (int i = Math.min(start, index); i <= Math.max(start, index); i++) selected.add(rows.get(i).node());
        } else { if (!selected.remove(node)) selected.add(node); anchor = node; }
        lead = node; changes.fire();
    }
    /**
     * Moves keyboard focus to a visible row without changing selection or its range anchor.
     */
    public void moveLead(int index) { lead = rows.get(index).node(); changes.fire(); }
    /**
     * Clears selection, keyboard lead and range anchor.
     */
    public void clearSelection() { selected.clear(); lead = anchor = null; changes.fire(); }
    /**
     * Selects all visible rows in multiple mode, or the lead/first row in single mode.
     */
    public void selectAll() {
        if (rows.isEmpty()) return;
        if (!multiple) { select(Math.max(0, indexOf(lead)), false, false); return; }
        for (TreeRow<T> row : rows) selected.add(row.node());
        if (lead == null) lead = rows.getFirst().node();
        anchor = rows.getFirst().node(); changes.fire();
    }
    /**
     * Rebuilds visible rows and reconciles selection after structural or expansion changes.
     */
    private void rebuild() {
        expanded.removeIf(node -> !contains(node));
        ArrayList<TreeRow<T>> result = new ArrayList<>();
        ArrayDeque<TreeRow<T>> pending = new ArrayDeque<>();
        Set<TreeNode<T>> matches = null;
        if (filter != null) {
            ArrayList<TreeNode<T>> all = new ArrayList<>();
            ArrayDeque<TreeNode<T>> nodes = new ArrayDeque<>();
            nodes.push(root);
            while (!nodes.isEmpty()) {
                TreeNode<T> node = nodes.pop();
                all.add(node);
                for (TreeNode<T> child : node.getChildren()) nodes.push(child);
            }
            matches = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int index = all.size() - 1; index >= 0; index--) {
                TreeNode<T> node = all.get(index);
                boolean included = filter.test(node);
                if (!included) for (TreeNode<T> child : node.getChildren())
                    if (matches.contains(child)) { included = true; break; }
                if (!included) continue;
                matches.add(node);
            }
        }
        if (rootVisible) pending.push(new TreeRow<>(root, 0));
        else pushChildren(pending, root, 0);
        while (!pending.isEmpty()) {
            TreeRow<T> row = pending.pop();
            if (matches != null && !matches.contains(row.node())) continue;
            result.add(row);
            if (matches != null || expanded.contains(row.node())) pushChildren(pending, row.node(), row.depth() + 1);
        }
        rows = List.copyOf(result);
        Set<TreeNode<T>> visible = new HashSet<>();
        for (TreeRow<T> row : rows) visible.add(row.node());
        LinkedHashSet<TreeNode<T>> retained = new LinkedHashSet<>();
        for (TreeNode<T> node : selected) { node = visibleAncestor(node, visible); if (node != null) retained.add(node); }
        selected.clear(); selected.addAll(retained);
        lead = visibleAncestor(lead, visible); anchor = visibleAncestor(anchor, visible);
        changes.fire();
    }
    /**
     * Finds a still-visible ancestor; detached nodes have no fallback.
     */
    private TreeNode<T> visibleAncestor(TreeNode<T> node, Set<TreeNode<T>> visible) {
        if (!contains(node)) return null;
        while (node != null && !visible.contains(node)) node = node.getParent();
        return node;
    }
    /**
     * Pushes in reverse order so iterative traversal preserves sibling order.
     */
    private static <T> void pushChildren(Deque<TreeRow<T>> pending, TreeNode<T> parent, int depth) {
        /*
         * Reverse pushes preserve sibling order in the depth-first traversal stack.
         */
        List<TreeNode<T>> children = parent.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) pending.push(new TreeRow<>(children.get(i), depth));
    }
    /**
     * Resumes observation after detachment and refreshes changes made while detached.
     */
    public void observe() {
        if (subscription == null) subscription = root.onChange(this::rebuild);
        rebuild();
    }
    /**
     * Stops observing node mutations; repeated calls are harmless.
     */
    public void stopObserving() {
        if (subscription == null) return;
        subscription.run();
        subscription = null;
    }
}
