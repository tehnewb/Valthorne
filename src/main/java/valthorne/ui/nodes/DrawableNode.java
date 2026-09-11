package valthorne.ui.nodes;

import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;

/**
 * Adapts a borrowed drawable to a UI node's current render position and dimensions.
 * Drawing stretches the drawable across the node bounds; layout and drawable
 * resource lifetime remain the caller's responsibility. The draw and update overrides
 * do not traverse children, so use this adapter as a leaf in the UI hierarchy.
 *
 * @author Albert Beaupre
 */
public class DrawableNode extends UINode {

    private Drawable drawable; // Borrowed drawing content, never disposed by this node.

    /**
     * Stores drawing content without copying it or allocating rendering resources.
     * Null is accepted here but will fail if draw is called before replacement.
     *
     * @param drawable content to render using this node's bounds
     */
    public DrawableNode(Drawable drawable) {
        this.drawable = drawable;
    }

    /**
     * Performs no creation work; the supplied drawable must already be ready for
     * use when the node is drawn.
     */
    @Override
    public void onCreate() {

    }

    /**
     * Leaves the borrowed drawable untouched. Its owner remains responsible for
     * releasing any associated resources after dependent nodes are finished.
     */
    @Override
    public void onDestroy() {

    }

    /**
     * Performs no animation or child updates. Animate the drawable externally if
     * it needs time-dependent state.
     *
     * @param delta elapsed seconds, unused by this leaf adapter
     */
    @Override
    public void update(float delta) {

    }

    /**
     * Draws the current content at the node's render coordinates with its layout
     * width and height. Batch setup is supplied by the caller; children are not drawn.
     *
     * @param batch active texture batch used by the drawable
     * @throws NullPointerException if no drawable has been supplied
     */
    @Override
    public void draw(TextureBatch batch) {
        drawable.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());
    }

    /**
     * Replaces the borrowed drawable without disposing the previous one or changing
     * layout dimensions. Null defers failure until drawing.
     *
     * @param drawable replacement content
     * @return this node
     */
    public DrawableNode drawable(Drawable drawable) {
        this.drawable = drawable;
        return this;
    }

    /**
     * Returns the current drawable reference; this accessor does not transfer
     * resource ownership to or from the node.
     *
     * @return borrowed drawable, possibly null
     */
    public Drawable getDrawable() {
        return drawable;
    }
}
