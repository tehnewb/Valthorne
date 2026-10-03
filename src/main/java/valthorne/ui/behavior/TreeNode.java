package valthorne.ui.behavior;

import java.util.*;

/**
 * Mutable, identity-based tree item with an application value and a display label.
 * Changes propagate to ancestors. Mutate and observe nodes on the UI thread.
 * A child has exactly one parent; detach it before inserting it elsewhere.
 * @param <T> application value type
 */
public class TreeNode<T> {
    private String text; // Nonnull display label.
    private T value; // Application value, which may be null.
    private TreeNode<T> parent; // Borrowed parent, or null for a detached root.
    private final List<TreeNode<T>> children = new ArrayList<>(); // Owned child order; each node belongs to only one parent.
    private final List<TreeNode<T>> view = Collections.unmodifiableList(children); // Owned child order; each node belongs to only one parent.
    private final ChangeSignal changes = new ChangeSignal(); // UI-thread synchronous change notification.

    /**
     * Creates an unlabeled item for subclasses that assign their label later.
     */
    protected TreeNode() { this(""); }
    /**
     * Creates a labeled item with a null application value.
     */
    public TreeNode(String text) { this(text, null); }
    /**
     * Creates a labeled item with an optional application value.
     */
    public TreeNode(String text, T value) { this.text = Objects.requireNonNull(text); this.value = value; }
    /**
     * Returns the nonnull display label.
     */
    public String getText() { return text; }
    /**
     * Returns the application value, which may be null.
     */
    public T getValue() { return value; }
    /**
     * Returns the parent, or null for an unattached node.
     */
    public TreeNode<T> getParent() { return parent; }
    /**
     * Returns an unmodifiable live view in insertion order.
     */
    public List<TreeNode<T>> getChildren() { return view; }
    /**
     * Returns whether the node currently has no children.
     */
    public boolean isLeaf() { return children.isEmpty(); }
    /**
     * Replaces the label and notifies ancestors if it changed; returns this node.
     */
    public TreeNode<T> text(String text) {
        Objects.requireNonNull(text);
        if (!this.text.equals(text)) { this.text = text; changed(); }
        return this;
    }
    /**
     * Replaces the optional application value and notifies ancestors; returns this node.
     */
    public TreeNode<T> value(T value) { this.value = value; changed(); return this; }
    /**
     * Appends an unattached child and returns this parent.
     */
    public TreeNode<T> add(TreeNode<T> child) { return insert(children.size(), child); }
    /**
     * Inserts an unattached child; rejects cycles and invalid indices before modifying the tree.
     */
    public TreeNode<T> insert(int index, TreeNode<T> child) {
        Objects.requireNonNull(child);
        if (index < 0 || index > children.size()) throw new IndexOutOfBoundsException(index);
        for (TreeNode<T> node = this; node != null; node = node.parent)
            if (node == child) throw new IllegalArgumentException("Tree cycle");
        if (child.parent != null) throw new IllegalArgumentException("Child already has a parent");
        children.add(index, child); child.parent = this; changed(); return this;
    }
    /**
     * Detaches a direct child and returns whether it belonged to this parent.
     */
    public boolean remove(TreeNode<T> child) {
        if (!children.remove(child)) return false;
        child.parent = null; changed(); return true;
    }
    /**
     * Detaches all direct children, preserving their subtrees.
     */
    public void clear() {
        if (children.isEmpty()) return;
        for (TreeNode<T> child : children) child.parent = null;
        children.clear(); changed();
    }
    /**
     * Observes mutations to this node or its descendants until the returned action is run.
     */
    public Runnable onChange(Runnable listener) { return changes.subscribe(listener); }
    /**
     * Propagates synchronous notifications without recursive stack growth.
     */
    private void changed() {
        for (TreeNode<T> node = this; node != null; node = node.parent) node.changes.fire();
    }
    /**
     * Keeps subclass instances distinct even when their labels or values match.
     */
    @Override public final boolean equals(Object other) { return this == other; }
    /**
     * Uses the stable object identity required by expansion and selection sets.
     */
    @Override public final int hashCode() { return System.identityHashCode(this); }
    /**
     * Returns the display label.
     */
    @Override public String toString() { return text; }
}
