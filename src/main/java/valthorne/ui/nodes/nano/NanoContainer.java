package valthorne.ui.nodes.nano;

import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UIContainer;

/**
 * Base container that participates in NanoVG rendering while delegating child
 * traversal to the root's shared UI render context. Children can use either
 * NanoVG or texture-batch painting; their normal order and backend transitions
 * are handled by that context rather than by directly invoking child draw methods.
 * This class adds no background or border painting of its own.
 *
 * <p>Layout, child ownership and input behavior come from UIContainer. Attach
 * the container to a UIRoot before rendering: the NanoVG callback obtains its
 * active render context from that root. Neither draw method creates or owns a
 * NanoVG frame. Subclasses that paint decorations should preserve shared child
 * dispatch and use the root-managed rendering lifecycle.</p>
 *
 * @author Albert Beaupre
 */
public class NanoContainer extends UIContainer implements NanoNode {
    /**
     * Enters the standard node rendering path so the active root can dispatch
     * this container to its NanoVG callback with shared clipping and backend state.
     * Child painting occurs through that callback rather than this overload.
     *
     * @param batch the active texture batch used by mixed UI traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }

    /**
     * Draws all children through the root render context without excluding any
     * child or adding container decoration. The context applies visibility and
     * culling rules and can switch backends for individual children. The supplied
     * NanoVG handle is not used directly because child dispatch belongs to the root.
     *
     * @param vg the root-managed NanoVG context for this callback
     * @throws NullPointerException if the container is detached from a root
     */
    @Override
    public void draw(long vg) {
        getRoot().getRenderContext().drawChildren(this, null);
    }
}
