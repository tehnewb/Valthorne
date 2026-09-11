package valthorne.ui;

import org.lwjgl.util.yoga.Yoga;
import valthorne.graphics.texture.TextureBatch;

import java.util.Arrays;
import java.util.List;

/**
 * <h1>UIContainer</h1>
 *
 * <p>
 * {@code UIContainer} is a specialized {@link UINode} that can contain and manage
 * other {@link UINode} instances as children. It forms the backbone of the UI
 * hierarchy system used throughout the Valthorne UI framework.
 * </p>
 *
 * <p>
 * A container maintains an ordered list of children and is responsible for:
 * </p>
 *
 * <ul>
 *     <li>managing parent/child relationships</li>
 *     <li>propagating the UI root to descendants</li>
 *     <li>propagating style invalidation through the UI tree</li>
 *     <li>updating and rendering child nodes</li>
 *     <li>handling Yoga node attachment and detachment</li>
 *     <li>performing hit testing for input handling</li>
 * </ul>
 *
 * <p>
 * Containers can be nested, forming a full UI tree structure where each node
 * can optionally hold children. Layout and rendering operations traverse this
 * tree recursively.
 * </p>
 *
 * <h2>Yoga Layout Integration</h2>
 *
 * <p>
 * Each container participates in the Yoga layout tree. When children are added
 * or removed, the corresponding Yoga nodes are inserted or removed to ensure
 * that layout computation remains synchronized with the UI hierarchy.
 * </p>
 *
 * <p>
 * Layout updates propagate through the tree and are eventually resolved by
 * {@link UIRoot#layout()}.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * UIContainer panel = new Panel();
 *
 * Label label = new Label();
 * Button button = new Button();
 *
 * panel.add(label);
 * panel.add(button);
 *
 * root.add(panel);
 *
 * // Update loop
 * panel.update(delta);
 *
 * // Rendering
 * panel.draw(batch);
 * }</pre>
 *
 * <p>
 * Containers themselves usually do not draw anything. Instead they delegate
 * drawing to their children.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public abstract class UIContainer extends UINode {

    private UINode[] children = new UINode[8]; // Internal array storing all child nodes.
    private int size; // The number of currently stored children.
    private List<UINode> childrenView; // Lazily cached unmodifiable live adapter over the current child array.

    /**
     * Performs hit testing within the container hierarchy.
     *
     * <p>
     * This method recursively searches the container tree from top-most child
     * to bottom-most child to determine which node is located at the provided
     * coordinates.
     * </p>
     *
     * <p>
     * The search respects visibility and optional bit requirements. If a
     * {@code requiredBit} is specified, the node must have that bit enabled.
     * </p>
     *
     * <p>
     * Children are checked in reverse order to ensure nodes drawn later
     * (higher z-order) receive input priority.
     * </p>
     *
     * @param x           the x coordinate in world space
     * @param y           the y coordinate in world space
     * @param requiredBit an optional bit requirement or -1 to ignore
     * @return the node found at the location or null if none matches
     */
    public UINode findNodeAt(float x, float y, int requiredBit) {
        if (!isVisible() || !isEnabled()) return null;
        float childX = transformChildHitX(x);
        float childY = transformChildHitY(y);

        for (int i = size() - 1; i >= 0; i--) {
            UINode child = get(i);

            if (child == null || !child.isVisible() || !child.isEnabled()) continue;

            if (child instanceof UIContainer container) {
                UINode hit = container.findNodeAt(childX, childY, requiredBit);
                if (hit != null && (requiredBit < 0 || hit.getBit(requiredBit))) return hit;
            }

            if (!(child instanceof UIContainer) && child.contains(childX, childY) && (requiredBit < 0 || child.getBit(requiredBit)))
                return child;
        }

        if (contains(x, y) && (requiredBit < 0 || getBit(requiredBit))) return this;

        return null;
    }

    /**
     * Transforms the input x coordinate before hit testing children.
     *
     * <p>
     * Containers can override this method to apply coordinate transformations
     * such as scroll offsets or clipping translations before hit detection.
     * </p>
     *
     * @param x the original x coordinate
     * @return the transformed x coordinate
     */
    protected float transformChildHitX(float x) {
        return x;
    }

    /**
     * Transforms the input y coordinate before hit testing children.
     *
     * <p>
     * Containers can override this method to apply coordinate transformations
     * such as scroll offsets or clipping translations before hit detection.
     * </p>
     *
     * @param y the original y coordinate
     * @return the transformed y coordinate
     */
    protected float transformChildHitY(float y) {
        return y;
    }

    /**
     * Invalidates style state for this container and all descendants.
     *
     * <p>
     * When a theme or style property changes, the style cache must be cleared
     * so that nodes can recompute their resolved styles.
     * </p>
     */
    @Override
    protected void invalidateStyleTree() {
        super.invalidateStyleTree();

        for (int i = 0; i < size; i++) {
            UINode child = children[i];
            if (child != null) child.invalidateStyleTree();
        }
    }

    /**
     * Provides an empty container creation hook. Tree/Yoga attachment is handled
     * by the node lifecycle; subclasses may initialize their own resources here.
     */
    @Override
    public void onCreate() {
    }

    /**
     * Provides an empty container destruction hook. Child native-node detachment
     * is handled separately by onNodeWillDestroy; subclasses release owned resources here.
     */
    @Override
    public void onDestroy() {
    }

    /**
     * Updates children in their stored order, including hidden children. Layout
     * synchronization is coordinated by the root around tree updates; this method
     * only delegates update calls.
     *
     * @param delta elapsed frame time in seconds
     */
    @Override
    public void update(float delta) {
        for (int i = 0; i < size; i++) {
            UINode child = children[i];
            child.update(delta);
        }
    }

    /**
     * Draws all visible children in order.
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (getRoot() != null && getRoot().getRenderContext() != null) {
            getRoot().getRenderContext().drawChildren(this, null);
            return;
        }
        for (int i = 0; i < size; i++) {
            UINode child = children[i];
            if (child == null)
                continue;
            if (!child.isVisible())
                continue;

            child.render(batch);
        }
    }

    /**
     * Appends an unattached child, propagates this root through its subtree, attaches
     * Yoga nodes when this container is live, and invalidates style/layout. Rejects
     * cycles and already-parented nodes; remove a node from its old parent first.
     *
     * @param child nonnull child to attach
     * @throws NullPointerException if child is null
     * @throws IllegalArgumentException if attachment would create a cycle
     * @throws IllegalStateException if child already has a parent
     */
    public void add(UINode child) {
        if (child == null) throw new NullPointerException("child");
        for (UINode ancestor = this; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor == child) throw new IllegalArgumentException("A UI tree cannot contain cycles.");
        }

        if (child.getParent() != null) throw new IllegalStateException("Node already has a parent.");

        if (size == children.length) children = Arrays.copyOf(children, children.length * 2);

        children[size] = child;
        child.setParent(this);
        propagateRoot(child, getRoot());

        if (hasYogaNode()) {
            child.attachToRoot(findRootConfig());
            Yoga.YGNodeInsertChild(getYogaMemoryAddress(), child.getYogaMemoryAddress(), size);
        }

        size++;
        child.invalidateStyleTree();
        child.markLayoutDirty();
        markLayoutDirty();
    }

    /**
     * Adds children sequentially using single-child attachment. Earlier additions
     * remain if a later child fails validation; this operation is not transactional.
     *
     * @param children nonnull array of nonnull unattached children
     * @throws NullPointerException if the array or an entry is null
     * @throws IllegalArgumentException if an entry would create a cycle
     * @throws IllegalStateException if an entry already has a parent
     */
    public void add(UINode... children) {
        for (UINode child : children) {
            if (child == null) throw new NullPointerException("Cannot add a null UINode to a UIContainer");
            add(child);
        }
    }

    /**
     * Detaches an immediate child and its native Yoga subtree, clears propagated
     * root/parent references, and compacts child order. Notifies the root first so
     * capture, focus, hover, and tooltips in the subtree can be cleared. Null or absent
     * children have no effect; descendant relationships within the detached subtree remain.
     *
     * @param child immediate child to detach
     */
    public void remove(UINode child) {
        if (child == null) return;

        int index = -1;
        for (int i = 0; i < size; i++) {
            if (children[i] == child) {
                index = i;
                break;
            }
        }

        if (index == -1) return;
        if (getRoot() != null) getRoot().nodeWillDetach(child);

        if (hasYogaNode() && child.hasYogaNode())
            Yoga.YGNodeRemoveChild(getYogaMemoryAddress(), child.getYogaMemoryAddress());

        detachTree(child);
        child.setParent(null);
        propagateRoot(child, null);

        int move = size - index - 1;
        if (move > 0) System.arraycopy(children, index + 1, children, index, move);

        children[--size] = null;

        markLayoutDirty();
    }

    /**
     * Removes one or more child nodes from this container.
     * <p>
     * This method iterates through the specified child nodes, ignoring any that
     * are null, and removes them from the container. If a child is already not
     * present in the container, it will have no effect.
     *
     * @param children the array of child nodes to be removed
     */
    public void remove(UINode... children) {
        for (UINode child : children) {
            if (child == null) continue;
            remove(child);
        }
    }

    /**
     * Removes all children from this container.
     */
    public void clear() {
        for (int i = size - 1; i >= 0; i--)
            remove(children[i]);
    }

    /**
     * Returns the number of child nodes.
     *
     * @return the number of children
     */
    public final int size() {
        return size;
    }

    /**
     * Returns the live child at an insertion-order index.
     *
     * @param index index from zero through size minus one
     * @return child reference
     * @throws IndexOutOfBoundsException if index is outside the current child range
     */
    public final UINode get(int index) {
        java.util.Objects.checkIndex(index, size);
        return children[index];
    }

    /**
     * Returns a cached, read-only live view of the children. UI-thread-only;
     * structural edits during iteration are not supported. Use List.copyOf for a snapshot.
     *
     * @return the children list
     */
    public final List<UINode> getChildren() {
        if (childrenView == null) childrenView = java.util.Collections.unmodifiableList(new ChildView());
        return childrenView;
    }

    /**
     * After base node initialization, attaches each existing child to the root's Yoga
     * configuration and inserts its native node in child order.
     *
     * @param yogaNode newly created native container node
     */
    @Override
    protected void onNodeCreated(long yogaNode) {
        super.onNodeCreated(yogaNode);
        long config = findRootConfig();

        for (int i = 0; i < size; i++) {
            UINode child = children[i];
            child.attachToRoot(config);
            Yoga.YGNodeInsertChild(yogaNode, child.getYogaMemoryAddress(), i);
        }
    }

    /**
     * Detaches every child subtree before invoking base native-node destruction.
     * Java child membership is retained for a future attachment.
     *
     * @param yogaNode native container node about to be destroyed
     */
    @Override
    protected void onNodeWillDestroy(long yogaNode) {
        for (int i = 0; i < size; i++)
            detachTree(children[i]);
        super.onNodeWillDestroy(yogaNode);
    }

    /**
     * Applies Yoga's computed results to this subtree, clearing dirty flags and
     * running each node's afterLayout hook after its descendants.
     */
    final void updateLayoutTree() {
        updateLayoutTree(this);
    }

    /**
     * Clears one node's dirty state, copies computed geometry, visits descendants,
     * then invokes afterLayout. Hooks may dirty layout again for the root's bounded
     * stabilization loop.
     *
     * @param node subtree to update
     */
    private void updateLayoutTree(UINode node) {
        node.clearLayoutDirty();
        node.updateComputedLayout();

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size; i++)
                updateLayoutTree(container.children[i]);
        }

        node.afterLayout();
    }

    /**
     * Delegates native subtree detachment to the node lifecycle. Container nodes
     * recursively detach their children through their destruction hooks.
     *
     * @param node subtree root to detach
     */
    private void detachTree(UINode node) {
        node.detachFromRoot();
    }

    /**
     * Sets the same root reference recursively throughout a Java subtree.
     * This updates ownership context without itself creating or freeing Yoga nodes.
     *
     * @param node subtree root
     * @param root attached root, or null while detached
     */
    private void propagateRoot(UINode node, UIRoot root) {
        node.setRoot(root);

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size; i++)
                propagateRoot(container.children[i], root);
        }
    }

    /**
     * Obtains the Yoga configuration of the currently attached root.
     *
     * @return borrowed native configuration handle
     * @throws IllegalStateException if this container is not attached to a root
     */
    private long findRootConfig() {
        UIRoot root = getRoot();

        if (root != null) return root.getYogaConfig();

        throw new IllegalStateException("Container is not attached to a root.");
    }

    /**
     * Read-only list adapter over the enclosing container's current child storage.
     * Membership is live rather than copied, and random indexed access delegates to
     * the container. UI-thread use is required; structural edits during iteration
     * are unsupported.
     * @author Albert Beaupre
     */
    private final class ChildView extends java.util.AbstractList<UINode> implements java.util.RandomAccess {
        /**
         * Returns the enclosing container's current child at the requested index.
         *
         * @param index current child index
         * @return live child reference
         * @throws IndexOutOfBoundsException if index is outside the current size
         */
        @Override
        public UINode get(int index) {return UIContainer.this.get(index);}

        /**
         * Returns the enclosing container's current child count.
         *
         * @return live list size
         */
        @Override
        public int size() {return UIContainer.this.size;}
    }
}
