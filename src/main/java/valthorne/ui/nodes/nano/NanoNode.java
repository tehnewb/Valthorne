package valthorne.ui.nodes.nano;

/**
 * Declares the NanoVG painting capability of a {@link valthorne.ui.UINode}.
 * During {@link valthorne.ui.UIRoot#draw()}, the active render context detects
 * this interface and invokes {@link #draw(long)} regardless of whether a parent
 * or child uses texture-batch painting. Implementing this interface does not
 * itself supply layout, input handling, or child traversal.
 *
 * <p>The owning root manages the NanoVG context and frame lifetime. Implementations
 * draw into that prepared context and must not begin or end frames, delete the
 * context, or bypass mixed-backend dispatch when drawing children. The render
 * context applies the shared camera, translation and clipping state before the
 * callback and restores saved NanoVG state afterward.</p>
 *
 * <p>Containers can delegate traversal to {@link NanoContainer} or call
 * {@link valthorne.ui.UINode#render(valthorne.graphics.texture.TextureBatch)}
 * with the active context's batch. Drawing a NanoVG node through a detached
 * node's normal render entry point is unsupported; attach it to a root and use
 * the root's draw lifecycle.</p>
 *
 * @author Albert Beaupre
 * @see valthorne.ui.UIRenderContext
 */
public interface NanoNode {

    /**
     * Paints this node using the root-owned NanoVG context and the coordinate
     * mapping prepared by the active UI render context. The callback runs on
     * the graphics-context thread during a root draw; implementations may issue
     * NanoVG drawing commands but must leave frame and context ownership to the root.
     *
     * <p>A container implementation is responsible for rendering its children
     * through the shared UI dispatch in the intended painter order. Saved NanoVG
     * state is restored when this callback exits, including exceptional exits.
     * The render context restores backend state at the enclosing dispatch or
     * child-traversal boundary; adjacent siblings can share a NanoVG interval.</p>
     *
     * @param nanoHandle the borrowed, nonzero NanoVG context handle for this draw
     */
    void draw(long nanoHandle);
}
