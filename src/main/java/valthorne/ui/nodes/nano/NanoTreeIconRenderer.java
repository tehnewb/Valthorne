package valthorne.ui.nodes.nano;

import valthorne.ui.behavior.*;

/**
 * Paints a custom icon inside a square in absolute top-left UI coordinates.
 */
@FunctionalInterface
public interface NanoTreeIconRenderer<T> {
    /**
     * Paints the supplied icon square using the borrowed, active NanoVG context.
     */
    void draw(long vg, TreeNode<T> node, boolean expanded, float x, float y, float size);
}
