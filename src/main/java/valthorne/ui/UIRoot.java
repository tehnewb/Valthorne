package valthorne.ui;

import org.lwjgl.util.yoga.Yoga;
import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.event.events.*;
import valthorne.event.listeners.KeyListener;
import valthorne.event.listeners.MouseListener;
import valthorne.event.listeners.MouseScrollListener;
import valthorne.event.listeners.WindowResizeListener;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.Vector2f;
import valthorne.ui.nodes.Panel;
import valthorne.ui.nodes.Tooltip;
import valthorne.ui.nodes.nano.NanoNode;
import valthorne.viewport.Viewport;

import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFont;
import static org.lwjgl.nanovg.NanoVG.nvgEndFrame;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;

/**
 * <p>
 * {@code UIRoot} is the top-level root container for Valthorne's UI system.
 * It acts as the central coordinator for layout, rendering, focus management,
 * mouse interaction, keyboard interaction, scroll handling, tooltip display,
 * overlay rendering, and window resize propagation.
 * </p>
 *
 * <p>
 * As the root of the UI tree, this class is responsible for owning the Yoga
 * configuration used for layout calculation, maintaining a dedicated
 * {@link TextureBatch} for UI rendering, and providing a special overlay layer
 * that is drawn above normal UI content. This overlay layer is primarily used
 * for transient or top-level UI elements such as tooltips, popups, and other
 * floating interface components that should render above the standard widget tree.
 * </p>
 *
 * <p>
 * In addition to layout and rendering, {@code UIRoot} also serves as the global
 * input router for the UI hierarchy. It listens to:
 * </p>
 *
 * <ul>
 *     <li>{@link KeyPressEvent} and {@link KeyReleaseEvent}</li>
 *     <li>{@link MousePressEvent}, {@link MouseReleaseEvent}, {@link MouseMoveEvent}, and {@link MouseDragEvent}</li>
 *     <li>{@link MouseScrollEvent}</li>
 *     <li>{@link WindowResizeEvent}</li>
 * </ul>
 *
 * <p>
 * These events are processed centrally and then forwarded to the appropriate
 * {@link UINode} based on visibility, focusability, clickability, scrollability,
 * and hit testing rules.
 * </p>
 *
 * <p>
 * The root also manages:
 * </p>
 *
 * <ul>
 *     <li>the currently focused node</li>
 *     <li>the currently pressed node</li>
 *     <li>the currently hovered node</li>
 *     <li>tooltip activation timing and placement</li>
 *     <li>tab focus traversal</li>
 *     <li>viewport-aware input coordinate conversion</li>
 * </ul>
 *
 * <p>
 * If a {@link Viewport} is assigned, the root converts screen coordinates to world
 * coordinates before hit testing and rendering logic is applied. This allows the
 * UI to operate in either raw window space or viewport-controlled world space.
 * </p>
 *
 * <p>
 * The normal lifecycle of a root UI container is:
 * </p>
 *
 * <ol>
 *     <li>Create the root</li>
 *     <li>Add child nodes</li>
 *     <li>Call {@link #layout()} whenever layout needs recalculation</li>
 *     <li>Call {@link #update(float)} each frame</li>
 *     <li>Call {@link #draw()} each frame</li>
 *     <li>Call {@link #dispose()} on shutdown</li>
 * </ol>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * UIRoot root = new UIRoot();
 *
 * Panel panel = new Panel();
 * panel.getLayout()
 *      .width(300)
 *      .height(200)
 *      .left(20)
 *      .top(20);
 *
 * root.add(panel);
 * root.layout();
 *
 * // Game loop
 * root.update(delta);
 * root.draw();
 *
 * // Focus management
 * root.focusNext();
 * UINode focused = root.getFocused();
 *
 * // Optional viewport support
 * root.setViewport(viewport);
 *
 * // Overlay usage
 * Tooltip tooltip = new Tooltip();
 * root.showOverlay(tooltip);
 * root.hideOverlay(tooltip);
 *
 * // Shutdown
 * root.dispose();
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete usage of the class: creation, child
 * attachment, layout, per-frame update and draw, focus traversal, viewport usage,
 * overlay control, and final disposal.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public class UIRoot extends UIContainer {

    private final long nanoVGHandle;
    private final long yogaConfig; // Yoga configuration handle owned by this UI root
    private final TextureBatch batch = new TextureBatch(4096); // Batch used to render the full UI tree
    private final Panel overlayLayer = new Panel(); // Top-most overlay container used for tooltips and floating UI

    private UINode focused; // Node that currently owns keyboard focus
    private UINode pressed; // Node currently being pressed by the mouse
    private UINode hovered; // Node currently being hovered by the mouse
    private UINode found; // Temporary node reference used during focus traversal searches
    private boolean seenFrom; // Temporary traversal flag used while searching for the next focusable node
    private Viewport viewport; // Optional viewport used for screen-to-world conversion and rendering

    private float hoverTime; // Time accumulated while hovering the current node
    private Tooltip activeTooltip; // Tooltip currently being displayed in the overlay layer

    private final RootKeyListener keyListener = new RootKeyListener(); // Root-level keyboard listener instance
    private final RootMouseListener mouseListener = new RootMouseListener(); // Root-level mouse listener instance
    private final RootScrollListener scrollListener = new RootScrollListener(); // Root-level scroll listener instance
    private final RootWindowListener windowListener = new RootWindowListener(); // Root-level window resize listener instance

    /**
     * <p>
     * Creates a new {@code UIRoot}, initializes Yoga configuration, attaches the
     * root to the Yoga system, configures the overlay layer, sizes the root to the
     * current window, and registers all required global input and window listeners.
     * </p>
     *
     * <p>
     * The overlay layer is configured as an absolute-positioned full-size panel that
     * does not participate in interaction and exists purely for top-level floating UI.
     * </p>
     */
    public UIRoot() {
        this.nanoVGHandle = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (nanoVGHandle != 0) {
            nvgCreateFont(nanoVGHandle, "default", switch (System.getProperty("os.name").toLowerCase()) {
                case "win" -> "C:/Windows/Fonts/segoeui.ttf";
                case "mac" -> "/System/Library/Fonts/SFNS.ttf";
                default -> "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf";
            });
        }

        this.yogaConfig = Yoga.YGConfigNew();

        Yoga.YGConfigSetUseWebDefaults(yogaConfig, true);
        attachToRoot(yogaConfig);
        setRoot(this);

        setSize(Window.getWidth(), Window.getHeight());

        overlayLayer.getLayout().absolute().left(0).top(0).width(Window.getWidth()).height(Window.getHeight()).noGrow().noShrink();
        overlayLayer.setClickable(false);
        overlayLayer.setFocusable(false);
        overlayLayer.setScrollable(false);
        super.add(overlayLayer);

        Keyboard.addKeyListener(keyListener);
        Mouse.addMouseListener(mouseListener);
        Mouse.addScrollListener(scrollListener);
        Window.addWindowResizeListener(windowListener);
    }

    /**
     * <p>
     * Returns the Yoga configuration handle owned by this root.
     * </p>
     *
     * <p>
     * This configuration is shared by the UI hierarchy attached to the root and is
     * typically only needed by lower-level layout code.
     * </p>
     *
     * @return the Yoga configuration handle
     */
    public long getYogaConfig() {
        return yogaConfig;
    }

    /**
     * <p>
     * Returns the {@link TextureBatch} used by this UI root for rendering.
     * </p>
     *
     * @return the UI render batch
     */
    public TextureBatch getBatch() {
        return batch;
    }

    /**
     * <p>
     * Returns the viewport currently assigned to this root, or {@code null} if none
     * is being used.
     * </p>
     *
     * @return the active viewport, or {@code null}
     */
    public Viewport getViewport() {
        return viewport;
    }

    /**
     * <p>
     * Assigns a viewport to this root.
     * </p>
     *
     * <p>
     * When a viewport is set, it is immediately updated to match the current window
     * size. If the viewport has a camera, the camera is centered in the viewport's
     * world dimensions. The root size is then updated to match the viewport world
     * dimensions and the UI is laid out again.
     * </p>
     *
     * @param viewport the new viewport, or {@code null} to disable viewport usage
     */
    public void setViewport(Viewport viewport) {
        this.viewport = viewport;

        if (viewport != null) {
            viewport.update(Window.getWidth(), Window.getHeight());
            if (viewport.getCamera() != null)
                viewport.getCamera().setCenter(viewport.getWorldWidth() * .5f, viewport.getWorldHeight() * .5f);
            setSize(viewport.getWorldWidth(), viewport.getWorldHeight());
            layout();
        }
    }

    /**
     * <p>
     * Sets the root UI size.
     * </p>
     *
     * <p>
     * This updates the Yoga style width and height of the root node, resizes the
     * overlay layer to match, and marks layout as dirty so a future layout pass
     * will recompute positions and sizes.
     * </p>
     *
     * @param width  the new root width
     * @param height the new root height
     */
    public void setSize(float width, float height) {
        Yoga.YGNodeStyleSetWidth(getYogaMemoryAddress(), width);
        Yoga.YGNodeStyleSetHeight(getYogaMemoryAddress(), height);
        overlayLayer.getLayout().width(width).height(height);
        markLayoutDirty();
    }

    /**
     * <p>
     * Performs a full layout pass on the UI tree.
     * </p>
     *
     * <p>
     * This method first synchronizes layout properties from nodes into Yoga by
     * calling {@link #syncTree(UINode)}, then asks Yoga to calculate layout for the
     * entire hierarchy, and finally applies the results back into the node tree by
     * calling {@code updateLayoutTree()}.
     * </p>
     */
    public void layout() {
        syncTree(this);
        Yoga.YGNodeCalculateLayout(getYogaMemoryAddress(), Float.NaN, Float.NaN, Yoga.YGDirectionLTR);
        updateLayoutTree();
    }

    /**
     * <p>
     * Shows the supplied node in the overlay layer.
     * </p>
     *
     * <p>
     * If the node currently belongs to another parent, it is removed from that parent
     * and reattached to the overlay layer. The node is then made visible and a new
     * layout pass is triggered.
     * </p>
     *
     * @param node the node to show in the overlay layer
     */
    public void showOverlay(UINode node) {
        if (node == null) return;

        if (node.getParent() != overlayLayer) {
            if (node.getParent() != null) node.getParent().remove(node);

            overlayLayer.add(node);
        }

        node.setVisible(true);
        layout();
    }

    /**
     * <p>
     * Hides the supplied node from the overlay layer.
     * </p>
     *
     * <p>
     * The node is marked invisible, removed from the overlay layer if it is currently
     * attached there, and the UI is relaid out afterward.
     * </p>
     *
     * @param node the overlay node to hide
     */
    public void hideOverlay(UINode node) {
        if (node == null) return;

        node.setVisible(false);

        if (node.getParent() == overlayLayer) overlayLayer.remove(node);

        layout();
    }

    /**
     * <p>
     * Updates the full UI tree and tooltip state.
     * </p>
     *
     * <p>
     * This first updates all child nodes through the superclass update chain. It then
     * manages tooltip timing for the currently hovered node. If no hovered node exists,
     * or the hovered node has no tooltip, the active tooltip is hidden and hover timing
     * is reset. If a tooltip exists and the hover delay has elapsed, the tooltip is
     * shown and positioned near the mouse.
     * </p>
     *
     * @param delta the frame delta time in seconds
     */
    @Override
    public void update(float delta) {
        super.update(delta);

        if (hovered == null) {
            hideActiveTooltip();
            hoverTime = 0f;
            return;
        }

        Tooltip tooltip = hovered.getTooltip();
        if (tooltip == null) {
            hideActiveTooltip();
            hoverTime = 0f;
            return;
        }

        hoverTime += delta;
        if (hoverTime < 1f) {
            if (activeTooltip != null && activeTooltip != tooltip) hideActiveTooltip();
            return;
        }

        if (activeTooltip != tooltip) {
            hideActiveTooltip();
            activeTooltip = tooltip;
            activeTooltip.getLayout().absolute().widthAuto().heightAuto().noGrow().noShrink();
            showTooltip(activeTooltip);
            layout();
        }

        positionTooltip(activeTooltip);
    }

    /**
     * <p>
     * Returns this instance as the root of the UI tree.
     * </p>
     *
     * @return this root
     */
    @Override
    public UIRoot getRoot() {
        return this;
    }

    /**
     * <p>
     * Draws the UI using this root's internal {@link TextureBatch}.
     * </p>
     *
     * <p>
     * If a viewport is assigned, the viewport is bound before drawing and unbound
     * afterward. Otherwise drawing is performed directly without viewport wrapping.
     * In both cases, the batch is begun, the tree is drawn, and the batch is ended.
     * </p>
     */
    public void draw() {
        if (viewport != null)
            viewport.bind();

        batch.begin();
        draw(batch);
        batch.end();

        if (nanoVGHandle != 0L) {
            beginNanoFrame(nanoVGHandle);

            for (int i = 0; i < size(); i++) {
                UINode child = get(i);
                if (child == null)
                    continue;
                if (!child.isVisible())
                    continue;
                if (child == overlayLayer)
                    continue;
                if (child instanceof NanoNode nano)
                    nano.draw(nanoVGHandle);
            }

            endNanoFrame(nanoVGHandle);
        }

        batch.begin();
        if (overlayLayer.isVisible())
            overlayLayer.draw(batch);
        batch.end();

        if (viewport != null)
            viewport.unbind();
    }

    @Override
    public void draw(TextureBatch batch) {
        for (int i = 0; i < size(); i++) {
            UINode child = get(i);

            if (child == null)
                continue;
            if (!child.isVisible())
                continue;
            if (child == overlayLayer)
                continue;

            child.draw(batch);
        }
    }

    private void drawNanoTree(UINode node, long vg) {
        if (node == null)
            return;
        if (!node.isVisible())
            return;

        if (node instanceof NanoNode nano)
            nano.draw(vg);

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size(); i++)
                drawNanoTree(container.get(i), vg);
        }
    }

    private void beginNanoFrame(long vg) {
        float width = viewport != null ? viewport.getWorldWidth() : getWidth();
        float height = viewport != null ? viewport.getWorldHeight() : getHeight();
        nvgBeginFrame(vg, width, height, 1f);
    }

    private void endNanoFrame(long vg) {
        nvgEndFrame(vg);
    }

    /**
     * <p>
     * Disposes this root and releases all resources and listeners it owns.
     * </p>
     *
     * <p>
     * This hides any active tooltip, unregisters input and window listeners, detaches
     * the UI tree from Yoga, frees the Yoga configuration, and disposes the render batch.
     * After this method is called, the root should no longer be used.
     * </p>
     */
    public void dispose() {
        hideActiveTooltip();

        Keyboard.removeKeyListener(keyListener);
        Mouse.removeMouseListener(mouseListener);
        Mouse.removeScrollListener(scrollListener);
        Window.removeWindowResizeListener(windowListener);

        detachFromRoot();
        Yoga.YGConfigFree(yogaConfig);
        batch.dispose();
    }

    /**
     * <p>
     * Returns the node currently holding keyboard focus.
     * </p>
     *
     * @return the focused node, or {@code null} if no node is focused
     */
    public UINode getFocused() {
        return focused;
    }

    /**
     * <p>
     * Transfers focus to the provided node if it is currently focusable.
     * </p>
     *
     * <p>
     * The previously focused node is unfocused first. If the next node is not focusable
     * under current runtime conditions, focus is cleared instead. Any active tooltip is
     * hidden when focus changes.
     * </p>
     *
     * @param next the node to focus, or {@code null} to clear focus
     */
    public void setFocusTo(UINode next) {
        if (focused == next) return;

        if (focused != null) focused.setFocused(false);

        focused = isNodeFocusableNow(next) ? next : null;

        if (focused != null) focused.setFocused(true);

        hideActiveTooltip();
    }

    /**
     * <p>
     * Moves focus to the next focusable node in traversal order.
     * </p>
     *
     * <p>
     * If no current focus exists, the first focusable node is selected. If traversal
     * reaches the end of the tree, focus wraps back to the first focusable node.
     * </p>
     */
    public void focusNext() {
        UINode next = findNextFocusable(focused);
        setFocusTo(next);
    }

    /**
     * <p>
     * Finds the top-most matching node at the provided coordinates.
     * </p>
     *
     * <p>
     * If a viewport is active, the input coordinates are first converted from screen
     * space into world space. The overlay layer is checked first, allowing overlay
     * elements to take precedence over normal UI nodes. If no overlay node matches,
     * the normal container search is used.
     * </p>
     *
     * @param x           the X coordinate
     * @param y           the Y coordinate
     * @param requiredBit the required node bit flag
     * @return the matching node, or {@code null} if none was found
     */
    @Override
    public UINode findNodeAt(float x, float y, int requiredBit) {
        if (viewport != null) {
            Vector2f world = viewport.screenToWorld(x, y);
            if (world == null) return null;
            x = world.getX();
            y = world.getY();
        }

        UINode node = overlayLayer.findNodeAt(x, y, requiredBit);
        if (node != null) return node;

        return super.findNodeAt(x, y, requiredBit);
    }

    /**
     * <p>
     * Applies layout behavior for the root itself.
     * </p>
     *
     * <p>
     * The root does not apply additional layout work in this override because layout
     * is handled at a higher level through Yoga and tree synchronization.
     * </p>
     */
    @Override
    protected void applyLayout() {
    }

    /**
     * <p>
     * Synchronizes the full node tree into Yoga before layout calculation.
     * </p>
     *
     * <p>
     * This recursively calls {@link UINode#applyLayout()} on the supplied node and
     * all descendants so Yoga receives the latest style and layout values.
     * </p>
     *
     * @param node the node whose subtree should be synchronized
     */
    private void syncTree(UINode node) {
        node.applyLayout();

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size(); i++)
                syncTree(container.get(i));
        }
    }

    /**
     * <p>
     * Finds the next currently focusable node after the supplied node.
     * </p>
     *
     * <p>
     * If {@code from} is {@code null}, the first focusable node in the tree is returned.
     * Otherwise the tree is traversed in order until the node after {@code from} that
     * is currently focusable is found. If no later focusable node exists, traversal
     * wraps to the first focusable node in the tree.
     * </p>
     *
     * @param from the node to search after
     * @return the next focusable node, or {@code null} if none exist
     */
    private UINode findNextFocusable(UINode from) {
        if (from == null) return findFirstFocusable(this);

        found = null;
        seenFrom = false;

        traverseNextFocusable(this, from);
        return found != null ? found : findFirstFocusable(this);
    }

    /**
     * <p>
     * Recursively traverses the tree looking for the next focusable node after a given node.
     * </p>
     *
     * <p>
     * The traversal uses the {@code seenFrom} flag to detect when the starting node
     * has been encountered. After that point, the first node that passes
     * {@link #isNodeFocusableNow(UINode)} is stored in {@code found}.
     * </p>
     *
     * @param node the current node being visited
     * @param from the node after which traversal should find the next focus target
     */
    private void traverseNextFocusable(UINode node, UINode from) {
        if (node == null || found != null) return;

        if (seenFrom && isNodeFocusableNow(node)) {
            found = node;
            return;
        }

        if (node == from) seenFrom = true;

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size(); i++) {
                UINode child = container.get(i);
                if (child.isDisabled())
                    continue;
                traverseNextFocusable(child, from);
                if (found != null) return;
            }
        }
    }

    /**
     * <p>
     * Finds the first currently focusable node in the provided subtree.
     * </p>
     *
     * @param root the subtree root
     * @return the first focusable node, or {@code null} if none were found
     */
    private UINode findFirstFocusable(UINode root) {
        found = null;
        traverseFirstFocusable(root);
        return found;
    }

    /**
     * <p>
     * Recursively traverses the tree searching for the first currently focusable node.
     * </p>
     *
     * @param node the current node being visited
     */
    private void traverseFirstFocusable(UINode node) {
        if (node == null || found != null) return;

        if (isNodeFocusableNow(node)) {
            found = node;
            return;
        }

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size(); i++) {
                traverseFirstFocusable(container.get(i));
                if (found != null) return;
            }
        }
    }

    /**
     * <p>
     * Returns whether the provided node is currently eligible to receive focus.
     * </p>
     *
     * <p>
     * A node is considered focusable only when it is non-null, visible, enabled,
     * and marked focusable.
     * </p>
     *
     * @param node the node to test
     * @return {@code true} if the node can currently receive focus
     */
    private boolean isNodeFocusableNow(UINode node) {
        return node != null && node.isVisible() && node.isEnabled() && node.isFocusable();
    }

    /**
     * <p>
     * Shows a tooltip inside the overlay layer.
     * </p>
     *
     * <p>
     * If the tooltip belongs to another parent, it is removed and reattached to the
     * overlay layer. The tooltip is then marked visible.
     * </p>
     *
     * @param tooltip the tooltip to show
     */
    private void showTooltip(Tooltip tooltip) {
        if (tooltip.getParent() != overlayLayer) {
            if (tooltip.getParent() != null) tooltip.getParent().remove(tooltip);

            overlayLayer.add(tooltip);
        }

        tooltip.setVisible(true);
    }

    /**
     * <p>
     * Positions the supplied tooltip near the current mouse location.
     * </p>
     *
     * <p>
     * If a viewport is active, the current mouse position is converted into world
     * coordinates first. The tooltip is then placed using absolute layout so that
     * its top edge appears above the mouse according to the current render-space
     * height. After updating its position, the tooltip is marked dirty and the full
     * layout is recalculated.
     * </p>
     *
     * @param tooltip the tooltip to position
     */
    private void positionTooltip(Tooltip tooltip) {
        float mouseX = Mouse.getX();
        float mouseY = Mouse.getY();

        if (viewport != null) {
            Vector2f world = viewport.screenToWorld(mouseX, mouseY);
            if (world == null) return;

            mouseX = world.getX();
            mouseY = world.getY();
        }

        tooltip.getLayout().absolute().left(mouseX).top(getRenderSpaceHeight() - mouseY - tooltip.getHeight());

        tooltip.markLayoutDirty();
        layout();
    }

    /**
     * <p>
     * Hides and detaches the currently active tooltip, if one exists.
     * </p>
     *
     * <p>
     * The tooltip is marked invisible, removed from the overlay layer if necessary,
     * and the active tooltip reference is cleared.
     * </p>
     */
    private void hideActiveTooltip() {
        if (activeTooltip != null) {
            activeTooltip.setVisible(false);

            if (activeTooltip.getParent() == overlayLayer) overlayLayer.remove(activeTooltip);

            activeTooltip = null;
        }
    }

    /**
     * <p>
     * Handles a key press event at the root level.
     * </p>
     *
     * <p>
     * Any active tooltip is hidden first. If the pressed key is {@link Keyboard#TAB},
     * focus traversal is performed instead of forwarding the event. Otherwise, the event
     * is passed to the currently focused node if one exists.
     * </p>
     *
     * @param event the key press event
     */
    private void handleKeyPressed(KeyPressEvent event) {
        hideActiveTooltip();

        if (event.getKey() == Keyboard.TAB) {
            focusNext();
            return;
        }

        if (focused != null) focused.onKeyPress(event);
    }

    /**
     * <p>
     * Handles a key release event at the root level.
     * </p>
     *
     * <p>
     * The event is forwarded to the currently focused node if one exists.
     * </p>
     *
     * @param event the key release event
     */
    private void handleKeyReleased(KeyReleaseEvent event) {
        if (focused != null) focused.onKeyRelease(event);
    }

    /**
     * <p>
     * Handles a mouse press event at the root level.
     * </p>
     *
     * <p>
     * Any active tooltip is hidden first. The tree is then searched for a clickable
     * node at the mouse position. If a target is found, focus is assigned if the node
     * is focusable, the node is marked pressed, and the event is delivered to it.
     * If no target is found, focus is cleared.
     * </p>
     *
     * @param event the mouse press event
     */
    private void handleMousePressed(MousePressEvent event) {
        hideActiveTooltip();

        UINode target = findNodeAt(event.getX(), event.getY(), UINode.CLICKABLE_BIT);

        if (target != null) {
            if (target.isFocusable()) setFocusTo(target);
            else setFocusTo(null);

            if (target.isDisabled())
                return;

            pressed = target;
            pressed.setPressed(true);
            pressed.onMousePress(event);
        } else {
            setFocusTo(null);
        }
    }

    /**
     * <p>
     * Handles a mouse release event at the root level.
     * </p>
     *
     * <p>
     * If a pressed node exists, it is unpressed, receives the release event, and the
     * pressed reference is cleared.
     * </p>
     *
     * @param event the mouse release event
     */
    private void handleMouseReleased(MouseReleaseEvent event) {
        if (pressed != null) {
            pressed.setPressed(false);
            pressed.onMouseRelease(event);
            pressed = null;
        }
    }

    /**
     * <p>
     * Handles a mouse drag event at the root level.
     * </p>
     *
     * <p>
     * Dragging hides any active tooltip and resets hover timing. If a node is currently
     * pressed, the drag event is forwarded to that node.
     * </p>
     *
     * @param event the mouse drag event
     */
    private void handleMouseDragged(MouseDragEvent event) {
        hideActiveTooltip();
        hoverTime = 0f;

        if (pressed != null) pressed.onMouseDrag(event);
    }

    /**
     * <p>
     * Handles a mouse move event at the root level.
     * </p>
     *
     * <p>
     * The method searches for a clickable target under the current mouse position.
     * If the hovered node changes, any active tooltip is hidden, hover timing is reset,
     * and the previous hovered node is unhovered. If a new target exists, it becomes
     * the hovered node and receives the move event.
     * </p>
     *
     * @param event the mouse move event
     */
    private void handleMouseMoved(MouseMoveEvent event) {
        UINode target = findNodeAt(event.getX(), event.getY(), UINode.CLICKABLE_BIT);

        if (target != hovered) {
            hideActiveTooltip();
            hoverTime = 0f;

            if (hovered != null) hovered.setHovered(false);
        }

        if (target == null) {
            hovered = null;
            return;
        }

        if (target.isDisabled())
            return;

        hovered = target;
        hovered.setHovered(true);
        hovered.onMouseMove(event);
    }

    /**
     * <p>
     * Handles a mouse scroll event at the root level.
     * </p>
     *
     * <p>
     * Any active tooltip is hidden and hover timing is reset. The node under the
     * current mouse position that is marked scrollable is then found and receives
     * the scroll event.
     * </p>
     *
     * @param event the mouse scroll event
     */
    private void handleMouseScrolled(MouseScrollEvent event) {
        hideActiveTooltip();
        hoverTime = 0f;

        UINode target = findNodeAt(Mouse.getX(), Mouse.getY(), UINode.SCROLLABLE_BIT);
        if (target != null) target.onMouseScroll(event);
    }

    /**
     * <p>
     * Handles a window resize event at the root level.
     * </p>
     *
     * <p>
     * Any active tooltip is hidden first. If a viewport is active, the viewport and
     * optional camera are updated and the root size is changed to match viewport world
     * dimensions. Otherwise the root size is changed directly to the new window size.
     * The UI is then relaid out and the resize event is propagated through the node tree.
     * </p>
     *
     * @param event the window resize event
     */
    private void handleWindowResized(WindowResizeEvent event) {
        hideActiveTooltip();

        if (viewport != null) {
            viewport.update(event.getNewWidth(), event.getNewHeight());
            if (viewport.getCamera() != null)
                viewport.getCamera().setCenter(viewport.getWorldWidth() * .5f, viewport.getWorldHeight() * .5f);
            setSize(viewport.getWorldWidth(), viewport.getWorldHeight());
        } else {
            setSize(event.getNewWidth(), event.getNewHeight());
        }

        layout();
        propagateResize(this, event);
    }

    /**
     * <p>
     * Recursively propagates a window resize event through the UI tree.
     * </p>
     *
     * <p>
     * Each visited node has layout marked dirty and then receives the resize callback.
     * Container children are then processed recursively.
     * </p>
     *
     * @param node  the current node being visited
     * @param event the resize event to propagate
     */
    private void propagateResize(UINode node, WindowResizeEvent event) {
        if (node == null) return;

        markLayoutDirty();
        node.onWindowResize(event);

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size(); i++)
                propagateResize(container.get(i), event);
        }
    }

    /**
     * <p>
     * Root keyboard listener that forwards keyboard events into the owning root.
     * </p>
     *
     * <p>
     * This inner listener exists so the root can register a persistent listener
     * instance with the global keyboard subsystem.
     * </p>
     */
    private final class RootKeyListener implements KeyListener {

        /**
         * <p>
         * Forwards a key press event to {@link UIRoot#handleKeyPressed(KeyPressEvent)}.
         * </p>
         *
         * @param event the key press event
         */
        @Override
        public void keyPressed(KeyPressEvent event) {
            handleKeyPressed(event);
        }

        /**
         * <p>
         * Forwards a key release event to {@link UIRoot#handleKeyReleased(KeyReleaseEvent)}.
         * </p>
         *
         * @param event the key release event
         */
        @Override
        public void keyReleased(KeyReleaseEvent event) {
            handleKeyReleased(event);
        }
    }

    /**
     * <p>
     * Root mouse listener that forwards mouse events into the owning root.
     * </p>
     */
    private final class RootMouseListener implements MouseListener {

        /**
         * <p>
         * Forwards a mouse press event to {@link UIRoot#handleMousePressed(MousePressEvent)}.
         * </p>
         *
         * @param event the mouse press event
         */
        @Override
        public void mousePressed(MousePressEvent event) {
            handleMousePressed(event);
        }

        /**
         * <p>
         * Forwards a mouse release event to {@link UIRoot#handleMouseReleased(MouseReleaseEvent)}.
         * </p>
         *
         * @param event the mouse release event
         */
        @Override
        public void mouseReleased(MouseReleaseEvent event) {
            handleMouseReleased(event);
        }

        /**
         * <p>
         * Forwards a mouse drag event to {@link UIRoot#handleMouseDragged(MouseDragEvent)}.
         * </p>
         *
         * @param event the mouse drag event
         */
        @Override
        public void mouseDragged(MouseDragEvent event) {
            handleMouseDragged(event);
        }

        /**
         * <p>
         * Forwards a mouse move event to {@link UIRoot#handleMouseMoved(MouseMoveEvent)}.
         * </p>
         *
         * @param event the mouse move event
         */
        @Override
        public void mouseMoved(MouseMoveEvent event) {
            handleMouseMoved(event);
        }
    }

    /**
     * <p>
     * Root scroll listener that forwards scroll events into the owning root.
     * </p>
     */
    private final class RootScrollListener implements MouseScrollListener {

        /**
         * <p>
         * Forwards a mouse scroll event to {@link UIRoot#handleMouseScrolled(MouseScrollEvent)}.
         * </p>
         *
         * @param event the mouse scroll event
         */
        @Override
        public void mouseScrolled(MouseScrollEvent event) {
            handleMouseScrolled(event);
        }
    }

    /**
     * <p>
     * Root window resize listener that forwards resize events into the owning root.
     * </p>
     */
    private final class RootWindowListener implements WindowResizeListener {

        /**
         * <p>
         * Forwards a window resize event to {@link UIRoot#handleWindowResized(WindowResizeEvent)}.
         * </p>
         *
         * @param event the resize event
         */
        @Override
        public void windowResized(WindowResizeEvent event) {
            handleWindowResized(event);
        }
    }

    /**
     * Retrieves the handle for the NanoVG context.
     *
     * @return the handle corresponding to the NanoVG context as a long.
     */
    public long getNanoVGHandle() {
        return nanoVGHandle;
    }
}