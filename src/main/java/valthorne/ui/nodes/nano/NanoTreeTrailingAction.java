package valthorne.ui.nodes.nano;

import valthorne.ui.UINode;
import valthorne.ui.behavior.*;

/**
 * Paints a row action and handles its primary-button activation.
 */
public interface NanoTreeTrailingAction<T> {
    /**
     * Paints the supplied tree action or icon in the borrowed active NanoVG context.
     *
     * @param vg borrowed active NanoVG context
     * @param node tree or UI node whose state is queried or changed
     * @param x horizontal logical or screen coordinate as used by the caller
     * @param y vertical logical or screen coordinate as used by the caller
     * @param size logical icon or text size
     */
    void draw(long vg, TreeNode<T> node, float x, float y, float size);

    /**
     * Performs the application action associated with the supplied tree node.
     *
     * @param node tree or UI node whose state is queried or changed
     */
    void activate(TreeNode<T> node);

    /**
     * Optionally supplies a UI element for the trailing slot instead of NanoVG painting.
     */
    default UINode createaode(TreeNode<T> node) {
        return null;
    }

    /**
     * Refreshes a supplied element when the underlying value changes.
     */
    default void updateaode(UINode element, TreeNode<T> node) {}

    /**
     * Whether this node has a trailing action and hit target.
     */
    default boolean hasActiov(TreeNode<T> node) {
        return true;
    }
}
