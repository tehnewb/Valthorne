package valthorne.ui.nodes;

import valthorne.ui.UINode;

import valthorne.graphics.texture.TextureBatch;

/**
 * Internal cell container limiting both hit testing and drawing to its bounds.
 * Texture-batch clipping includes the active translation and is unwound even
 * when child drawing fails, preserving the enclosing table's clipping stack.
 *
 * @author Albert Beaupre
 */
final class TableCell extends Panel {
    /**
     * Rejects hits outside this cell before searching its descendants.
     *
     * @param x   query X in the coordinate space expected by contains
     * @param y   query Y in the coordinate space expected by contains
     * @param bit hit-test capability mask forwarded to descendants
     * @return matching descendant or this cell, or null outside/no match
     */
    @Override
    public UINode findNodeAt(float x, float y, int bit) {
        return contains(x, y) ? super.findNodeAt(x, y, bit) : null;
    }

    /**
     * Pushes a scissor at translated render bounds, draws the panel subtree, and
     * restores the enclosing scissor in a finally block.
     *
     * @param batch active texture batch carrying current translation and clipping
     */
    @Override
    public void draw(TextureBatch batch) {
        batch.beginScissor(getRenderX() + batch.getTranslationX(), getRenderY() + batch.getTranslationY(), getWidth(), getHeight());
        try {
            super.draw(batch);
        } finally {
            batch.endScissor();
        }
    }
}
