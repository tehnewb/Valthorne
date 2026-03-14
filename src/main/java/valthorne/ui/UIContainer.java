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
        x = transformChildHitX(x);
        y = transformChildHitY(y);

        for (int i = size() - 1; i >= 0; i--) {
            UINode child = get(i);

            if (child == null || !child.isVisible()) continue;

            if (child instanceof UIContainer container) {
                UINode hit = container.findNodeAt(x, y, requiredBit);
                if (hit != null && (requiredBit < 0 || hit.getBit(requiredBit))) return hit;
            }

            if (child.contains(x, y) && (requiredBit < 0 || child.getBit(requiredBit))) return child;
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

    @Override
    public void onCreate() {
    }

    @Override
    public void onDestroy() {
    }

    /**
     * Updates all child nodes.
     *
     * <p>
     * Containers ensure that layout updates are processed before updating
     * child nodes.
     * </p>
     *
     * @param delta the elapsed time since the previous frame
     */
    @Override
    public void update(float delta) {
        if (isLayoutDirty()) getRoot().layout();

        for (int i = 0; i < size; i++) {
            UINode child = children[i];
            if (child.isLayoutDirty()) child.getRoot().layout();
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
        for (int i = 0; i < size; i++) {
            UINode child = children[i];
            if (child.isVisible()) child.draw(batch);
        }
    }

    /**
     * Adds a child node to this container.
     *
     * <p>
     * This method establishes the parent relationship, inserts the Yoga node,
     * propagates the UI root reference, and triggers layout recalculation.
     * </p>
     *
     * @param child the node to add
     */
    public void add(UINode child) {
        if (child == null) throw new NullPointerException("child");

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
        child.onCreate();
        child.invalidateStyleTree();
        child.markLayoutDirty();
        markLayoutDirty();
    }

    /**
     * Removes a child node from this container.
     *
     * @param child the node to remove
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

        if (hasYogaNode() && child.hasYogaNode())
            Yoga.YGNodeRemoveChild(getYogaMemoryAddress(), child.getYogaMemoryAddress());

        child.onDestroy();
        detachTree(child);
        child.setParent(null);
        propagateRoot(child, null);

        int move = size - index - 1;
        if (move > 0) System.arraycopy(children, index + 1, children, index, move);

        children[--size] = null;

        markLayoutDirty();
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
     * Returns a child node by index.
     *
     * @param index the child index
     * @return the child node
     */
    public final UINode get(int index) {
        return children[index];
    }

    /**
     * Returns an immutable list view of the children.
     *
     * @return the children list
     */
    public final List<UINode> getChildren() {
        return Arrays.asList(children).subList(0, size);
    }

    @Override
    protected void onNodeCreated(long yogaNode) {
        long config = findRootConfig();

        for (int i = 0; i < size; i++) {
            UINode child = children[i];
            child.attachToRoot(config);
            Yoga.YGNodeInsertChild(yogaNode, child.getYogaMemoryAddress(), i);
        }
    }

    @Override
    protected void onNodeWillDestroy(long yogaNode) {
        for (int i = 0; i < size; i++)
            detachTree(children[i]);
    }

    final void updateLayoutTree() {
        updateLayoutTree(this);
    }

    private void updateLayoutTree(UINode node) {
        node.clearLayoutDirty();

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size; i++)
                updateLayoutTree(container.children[i]);
        }

        node.afterLayout();
        node.updateComputedLayout();
    }

    private void detachTree(UINode node) {
        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size; i++)
                detachTree(container.children[i]);
        }

        node.detachFromRoot();
    }

    private void propagateRoot(UINode node, UIRoot root) {
        node.setRoot(root);

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size; i++)
                propagateRoot(container.children[i], root);
        }
    }

    private long findRootConfig() {
        UIRoot root = getRoot();

        if (root != null) return root.getYogaConfig();

        throw new IllegalStateException("Container is not attached to a root.");
    }
}