package valthorne.ui;

import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.yoga.Yoga;
import valthorne.JGL;
import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.event.Event;
import valthorne.event.EventHandler;
import valthorne.event.EventTypes;
import valthorne.event.events.*;
import valthorne.event.listeners.KeyListener;
import valthorne.event.listeners.MouseListener;
import valthorne.event.listeners.MouseScrollListener;
import valthorne.event.listeners.WindowResizeListener;
import valthorne.graphics.font.SystemFonts;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugData;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.behavior.TextEditing;
import valthorne.ui.nodes.Label;
import valthorne.ui.nodes.Panel;
import valthorne.ui.nodes.Tooltip;
import valthorne.ui.nodes.nano.NanoContainer;
import valthorne.ui.nodes.nano.NanoImage;
import valthorne.ui.nodes.nano.NanoLabel;
import valthorne.ui.nodes.nano.NanoPanel;
import valthorne.ui.theme.ThemeData;
import valthorne.ui.theme.ThemeDataChangeEvent;
import valthorne.viewport.Viewport;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFont;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;

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

    private final EventHandler<ThemeDataChangeEvent> themeListener = event -> refreshTheme(this, event.getData()); // Persistent listener invalidating nodes that use changed theme data.
    private final UIInspector inspector = new UIInspector(); // Root-owned optional draw inspection controller.
    private final List<FocusScope> focusScopes = new ArrayList<>(); // Modal scope stack with prior-focus restoration targets.
    private final long yogaConfig; // Yoga configuration handle owned by this UI root
    private final TextureBatch batch = new TextureBatch(4096); // Batch used to render the full UI tree
    private SlugFont nanoSlugFont; // Default proportional face for smooth Nano text.
    private SlugFont nanoSlugCodeFont; // Default monospace face for smooth code text.
    private final Panel overlayLayer = new Panel(); // Top-most overlay container used for tooltips and floating UI
    private final UIDragPreview dragPreview = new UIDragPreview(); // Paints the captured item above scroll clips while dragging.
    private Set<UINode> externallyPresentedNodes; // Top-level nodes painted and picked by an external editor; allocated only when needed.
    private final RootKeyListener keyListener = new RootKeyListener(); // Root-level keyboard listener instance
    private final RootMouseListener mouseListener = new RootMouseListener(); // Root-level mouse listener instance
    private final RootScrollListener scrollListener = new RootScrollListener(); // Root-level scroll listener instance
    private final RootWindowListener windowListener = new RootWindowListener(); // Root-level window resize listener instance
    private long nanoVGHandle; // Stored NanoVG context deleted by this root on disposal, or zero when unavailable.
    private UIRotationShader rotationShader; // Lazily created texture shader owned by this root for rotated UI scopes.
    private UIRenderContext renderContext; // Borrowed active mixed-backend drawing context, null outside drawing.
    private boolean disposed; // Whether root resource disposal has been requested.
    private boolean blockLowerInput, pointerOwned;
    private int inputPriority; // Priority of this root's keyboard, pointer, scroll, and text subscriptions.
    private UIFrameStats frameStats = new UIFrameStats(0, 0, 0, 0, 0, 0, 0); // Latest immutable drawing and layout statistics.
    private long pendingLayoutPasses, pendingLayoutNanos; // Layout work accumulated until the next recorded draw.
    private UINode focused; // Node that currently owns keyboard focus
    private final EventHandler<TextInputEvent> textListener = event -> {
        boolean active = blockLowerInput && getFocused() != null;
        route(getFocused(), event, Float.NaN, Float.NaN, node -> node.onTextInput(event), false);
        if (active) event.consume();
    }; // Persistent committed-text listener targeting the current focus.
    private UINode pressed; // Node currently being pressed by the mouse
    private int pressedButton = -1; // Button associated with capture, or -1 when no gesture is captured.
    private final ArrayList<NanoLabel> selectionLabels = new ArrayList<>();
    private boolean textDragPending, textDragActive;
    private float textPressX, textPressY;
    private int textAnchorLabel, textAnchorOffset;

    private void collectSelectableText(UINode node) {
        if (!isNodeInteractiveNow(node)) return;
        if (node instanceof NanoLabel label && label.isSelectable()) selectionLabels.add(label);
        if (node instanceof UIContainer container)
            for (UINode child : container.getChildren()) collectSelectableText(child);
    }

    private void clearDocumentSelection() {
        for (var label : selectionLabels) label.documentSelection(0, 0);
        selectionLabels.clear();
        textDragPending = textDragActive = false;
    }

    private int nearestText(float x, float y) {
        int best = -1;
        float distance = Float.POSITIVE_INFINITY;
        for (int i = 0; i < selectionLabels.size(); i++) {
            var label = selectionLabels.get(i);
            if (!isNodeFocusableNow(label) || !label.isSelectable()) continue;
            var point = label.screenToLayout(x, y);
            float dx = Math.max(0, Math.max(label.getAbsoluteX() - point.x(), point.x() - label.getAbsoluteX() - label.getWidth()));
            float dy = Math.max(0, Math.max(label.getAbsoluteY() - point.y(), point.y() - label.getAbsoluteY() - label.getHeight()));
            float score = dx * dx + dy * dy;
            if (score < distance) {
                best = i;
                distance = score;
            }
        }
        return best;
    }

    private boolean extendDocumentSelection(MouseDragEvent event) {
        if (!textDragPending || event.getButton() != Mouse.LEFT) return false;
        if (!textDragActive) {
            float dx = event.getToX() - textPressX, dy = event.getToY() - textPressY;
            if (dx * dx + dy * dy < 16) return false;
            textAnchorLabel = nearestText(textPressX, textPressY);
            if (textAnchorLabel < 0) return false;
            textAnchorOffset = selectionLabels.get(textAnchorLabel).selectionIndexAt(textPressX, textPressY);
            textDragActive = true;
            setFocusTo(selectionLabels.get(textAnchorLabel));
        }
        int endLabel = nearestText(event.getToX(), event.getToY());
        if (endLabel < 0) return false;
        int endOffset = selectionLabels.get(endLabel).selectionIndexAt(event.getToX(), event.getToY());
        int low = Math.min(textAnchorLabel, endLabel), high = Math.max(textAnchorLabel, endLabel);
        boolean forward = textAnchorLabel <= endLabel;
        for (int i = 0; i < selectionLabels.size(); i++) {
            var label = selectionLabels.get(i);
            if (!isNodeFocusableNow(label) || !label.isSelectable() || i < low || i > high) {
                label.documentSelection(0, 0);
                continue;
            }
            int start = i == low ? (forward ? textAnchorOffset : endOffset) : 0;
            int end = i == high ? (forward ? endOffset : textAnchorOffset) : label.selectionTextLength();
            label.documentSelection(start, end);
        }
        event.consume();
        return true;
    }

    private UINode hovered; // Node currently being hovered by the mouse
    private Viewport viewport; // Optional viewport used for screen-to-world conversion and rendering
    private boolean presentationEnabled = true; // Whether this root paints or accepts input in the active scene view.
    private boolean clipToViewport; // Whether this root clips every child to its viewport's local UI bounds.
    private boolean customViewportClip; // Whether a parent viewport further restricts this root's clip.
    private float viewportClipX; // Left edge of the active viewport-local UI clip.
    private float viewportClipY; // Bottom edge of the active viewport-local UI clip.
    private float viewportClipWidth; // Width of the active viewport-local UI clip.
    private float viewportClipHeight; // Height of the active viewport-local UI clip.
    private float hoverTime; // Time accumulated while hovering the current node
    private Tooltip activeTooltip; // Tooltip currently being displayed in the overlay layer
    private final EventHandler<WindowFocusEvent> focusListener = event -> {
        if (!event.isFocused()) cancelInput();
    }; // Persistent window-focus listener cancelling UI input on focus loss.

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
    public UIRoot() {this(0);}

    /**
     * Creates a UI root whose input is delivered before lower-priority application listeners.
     */
    public UIRoot(int inputPriority) {
        this.inputPriority = inputPriority;
        this.nanoVGHandle = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (nanoVGHandle != 0L) {
            registerDefaultNanoFont();
        }

        this.yogaConfig = Yoga.YGConfigNew();

        Yoga.YGConfigSetUseWebDefaults(yogaConfig, true);
        attachToRoot(yogaConfig);
        setRoot(this);
        if (nanoVGHandle != 0L) {
            ThemeData defaults = new ThemeData();
            defaults.setToken(Label.FONT_KEY, getDefaultFont());
            setTheme(defaults);
        }

        setSize(Window.getWidth(), Window.getHeight());

        overlayLayer.getLayout().absolute().left(0).top(0).width(Window.getWidth()).height(Window.getHeight()).noGrow().noShrink();
        overlayLayer.setClickable(false);
        overlayLayer.setFocusable(false);
        overlayLayer.setScrollable(false);
        super.add(overlayLayer);

        JGL.subscribe(EventTypes.KEY_PRESS, inputPriority, keyListener);
        JGL.subscribe(EventTypes.KEY_RELEASE, inputPriority, keyListener);
        JGL.subscribe(EventTypes.MOUSE_MOVE, inputPriority, mouseListener);
        JGL.subscribe(EventTypes.MOUSE_DRAG, inputPriority, mouseListener);
        JGL.subscribe(EventTypes.MOUSE_PRESS, inputPriority, mouseListener);
        JGL.subscribe(EventTypes.MOUSE_RELEASE, inputPriority, mouseListener);
        JGL.subscribe(EventTypes.MOUSE_SCROLL, inputPriority, scrollListener);
        Window.addWindowResizeListener(windowListener);
        JGL.subscribe(EventTypes.WINDOW_FOCUS, focusListener);
        JGL.subscribe(EventTypes.TEXT_INPUT, inputPriority, textListener);
        JGL.subscribe(EventTypes.THEME_DATA_CHANGE, themeListener);
    }

    /**
     * Consumes input handled by this root so underlying controls cannot activate through an overlay.
     */
    public UIRoot blockLowerInput(boolean enabled) {
        blockLowerInput = enabled;
        if (!enabled) pointerOwned = false;
        return this;
    }

    /**
     * Changes input routing priority without rebuilding the UI tree. Larger
     * values receive shared events before lower roots, allowing viewport-local
     * controls to follow the same front-to-back order as viewport painting.
     * Existing focus and pointer capture stay with this root.
     *
     * @param priority larger values route first
     */
    public void setInputPriority(int priority) {
        if (inputPriority == priority) return;
        JGL.unsubscribe(EventTypes.KEY_PRESS, keyListener);
        JGL.unsubscribe(EventTypes.KEY_RELEASE, keyListener);
        JGL.unsubscribe(EventTypes.MOUSE_MOVE, mouseListener);
        JGL.unsubscribe(EventTypes.MOUSE_DRAG, mouseListener);
        JGL.unsubscribe(EventTypes.MOUSE_PRESS, mouseListener);
        JGL.unsubscribe(EventTypes.MOUSE_RELEASE, mouseListener);
        JGL.unsubscribe(EventTypes.MOUSE_SCROLL, scrollListener);
        JGL.unsubscribe(EventTypes.TEXT_INPUT, textListener);
        JGL.subscribe(EventTypes.KEY_PRESS, priority, keyListener);
        JGL.subscribe(EventTypes.KEY_RELEASE, priority, keyListener);
        JGL.subscribe(EventTypes.MOUSE_MOVE, priority, mouseListener);
        JGL.subscribe(EventTypes.MOUSE_DRAG, priority, mouseListener);
        JGL.subscribe(EventTypes.MOUSE_PRESS, priority, mouseListener);
        JGL.subscribe(EventTypes.MOUSE_RELEASE, priority, mouseListener);
        JGL.subscribe(EventTypes.MOUSE_SCROLL, priority, scrollListener);
        JGL.subscribe(EventTypes.TEXT_INPUT, priority, textListener);
        inputPriority = priority;
    }

    private boolean hasUiAt(float x, float y) {
        UINode hit = findNodeAt(x, y, UINode.CLICKABLE_BIT);
        return hit != null && hit != this && hit.blocksLowerInput();
    }

    /**
     * Tests identity ancestry, including the node itself. A null node is outside
     * every scope.
     *
     * @param node     candidate descendant
     * @param ancestor scope root to match
     * @return true if ancestor occurs in the parent chain
     */
    private static boolean within(UINode node, UINode ancestor) {
        for (UINode current = node; current != null; current = current.getParent())
            if (current == ancestor) return true;
        return false;
    }

    /**
     * Traverses the tree and invalidates style/layout for nodes resolving to the
     * changed ThemeData object. Uses identity matching so unrelated themes are left
     * to their own change events.
     *
     * @param node subtree to inspect
     * @param data changed theme data
     */
    private void refreshTheme(UINode node, ThemeData data) {
        if (node.getTheme() == data) {
            node.invalidateStyle();
            node.markLayoutDirty();
        }
        if (node instanceof UIContainer container)
            for (UINode child : container.getChildren()) refreshTheme(child, data);
    }

    /**
     * Returns the live root-owned inspector used to capture optional drawing
     * diagnostics. Its lifetime follows this root.
     *
     * @return mutable inspection controller
     */
    public UIInspector getInspector() {return inspector;}

    /**
     * Returns the last recorded frame's immutable statistics, initially all zero.
     * Layout counters accumulate until a drawing context is recorded.
     *
     * @return latest frame statistics
     */
    public UIFrameStats getFrameStats() {return frameStats;}

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
        } else {
            setSize(Window.getWidth(), Window.getHeight());
            layout();
        }
    }

    /**
     * Restricts all texture and NanoVG children to the viewport's logical
     * rectangle. Scene-owned viewport UI roots use this so overflowing child
     * content cannot paint into another viewport.
     *
     * @param clipToViewport true to clip the whole UI tree to its viewport
     */
    public void setClipToViewport(boolean clipToViewport) {
        this.clipToViewport = clipToViewport;
    }

    /**
     * Enables or suppresses rendering and input for this complete UI root.
     * Disabling also releases captured and focused state so an invisible root
     * cannot continue handling keys or pointer drags.
     *
     * @param enabled true to present and interact with this root
     */
    public void setPresentationEnabled(boolean enabled) {
        if (presentationEnabled == enabled) return;
        presentationEnabled = enabled;
        if (!enabled) cancelInput();
    }

    /**
     * Narrows this root's viewport clip to a local rectangle. A scene uses the
     * intersection of nested viewport bounds so a child viewport cannot paint
     * outside its parent. The rectangle is expressed in bottom-left UI units.
     *
     * @param x      left edge within the root viewport
     * @param y      bottom edge within the root viewport
     * @param width  visible horizontal extent
     * @param height visible vertical extent
     */
    public void setViewportClip(float x, float y, float width, float height) {
        if (width < 0f || height < 0f)
            throw new IllegalArgumentException("Viewport clip dimensions cannot be negative");
        viewportClipX = x;
        viewportClipY = y;
        viewportClipWidth = width;
        viewportClipHeight = height;
        customViewportClip = true;
        clipToViewport = true;
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
        getLayout().width(width).height(height);
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
     * calling {@link #syncTree(UINode, boolean)}, then asks Yoga to calculate layout for the
     * entire hierarchy, and finally applies the results back into the node tree by
     * calling {@code updateLayoutTree()}.
     * </p>
     */
    public void layout() {
        layout(true);
    }

    /**
     * Synchronizes styles into Yoga, calculates layout, copies computed geometry,
     * and repeats when afterLayout hooks dirty the tree. Stops after eight passes,
     * recording pass count and elapsed nanoseconds for frame statistics.
     *
     * @param force whether node layout values must be reapplied even when locally clean
     * @throws IllegalStateException if layout remains dirty after eight passes
     */
    private void layout(boolean force) {
        long started = System.nanoTime();
        int passes = 0;
        do {
            syncTree(this, force);
            Yoga.YGNodeCalculateLayout(getYogaMemoryAddress(), Float.NaN, Float.NaN, Yoga.YGDirectionLTR);
            updateLayoutTree();
            pendingLayoutPasses++;
        } while (isLayoutDirty() && ++passes < 8);
        pendingLayoutNanos += System.nanoTime() - started;
        if (isLayoutDirty())
            throw new IllegalStateException("UI layout did not stabilize after 8 passes; check afterLayout mutations.");
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

        UINode restore = null;
        boolean restoreFocus = false;
        for (int i = focusScopes.size() - 1; i >= 0; i--) {
            if (focusScopes.get(i).node() == node) {
                restoreFocus = i == focusScopes.size() - 1;
                restore = focusScopes.remove(i).previous();
                break;
            }
        }
        if (within(pressed, node)) cancelPointer();
        node.setVisible(false);

        if (node.getParent() == overlayLayer) overlayLayer.remove(node);
        if (restoreFocus) {
            setFocusTo(restore);
            if (focused == null) focusNext();
        }

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
        if (pressed != null && !isNodeInteractiveNow(pressed)) cancelPointer();
        if (focused != null && !isNodeFocusableNow(focused)) setFocusTo(null);
        if (isLayoutDirty()) layout(false);
        super.update(delta);
        if (isLayoutDirty()) layout(false);
        updateCursor();

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
     * In both cases, the batch is begun, the tree is drawn, the active dragged
     * item is repainted above overlays and scroll clips, and the batch is ended.
     * </p>
     */
    public void draw() {
        if (disposed) throw new IllegalStateException("UIRoot has been disposed.");
        if (!presentationEnabled) return;
        if (renderContext != null) throw new IllegalStateException("UIRoot is already drawing.");
        refreshStyles(this);
        if (isLayoutDirty() || Yoga.YGNodeIsDirty(getYogaMemoryAddress())) layout(false);
        long renderStarted = System.nanoTime();
        long textureCallsBefore = batch.getTotalDrawCalls();
        inspector.beginFrame();
        if (viewport != null) viewport.bind();
        try {
            batch.begin();
            try {
                if (clipToViewport) {
                    if (customViewportClip)
                        batch.beginScissor(viewportClipX, viewportClipY, viewportClipWidth, viewportClipHeight);
                    else
                        batch.beginScissor(0f, 0f, getWidth(), getHeight());
                }
                if (nanoVGHandle != 0L) beginNanoFrame(nanoVGHandle);
                renderContext = new UIRenderContext(batch, nanoVGHandle, this);
                draw(batch);
                overlayLayer.render(batch);
                dragPreview.draw(renderContext);
                renderContext.drawInspection();
            } finally {
                UIRenderContext completed = renderContext;
                renderContext = null;
                if (clipToViewport) batch.endScissor();
                batch.end();
                if (completed != null) {
                    frameStats = new UIFrameStats(pendingLayoutPasses, pendingLayoutNanos, System.nanoTime() - renderStarted, completed.getNodesDrawn(), completed.getBackendSwitches(), completed.getNanoFlushes(), batch.getTotalDrawCalls() - textureCallsBefore);
                    pendingLayoutPasses = pendingLayoutNanos = 0;
                }
            }
        } finally {
            if (viewport != null) viewport.unbind();
        }
    }

    /**
     * Draws normal visible children while excluding the overlay layer, which the
     * root's no-argument draw method renders afterward. Uses the active mixed context
     * when present; otherwise delegates each child directly to the supplied batch.
     * Does not begin/end the batch or create a NanoVG frame.
     *
     * @param batch prepared destination batch
     */
    @Override
    public void draw(TextureBatch batch) {
        if (renderContext != null) {
            renderContext.drawChildren(this, overlayLayer);
            return;
        }
        for (UINode child : getChildren())
            if (child != overlayLayer && child.isVisible() && !skipsExternalPresentation(child)) child.render(batch);
    }

    /**
     * Returns the borrowed mixed-backend context while the root is drawing.
     * The reference is cleared when the drawing scope exits and must not be retained
     * as a reusable context.
     *
     * @return active context, or null outside draw
     */
    public UIRenderContext getRenderContext() {return renderContext;}

    /**
     * Lazily creates the root-owned shader for textured content inside a rotated
     * UI subtree. Called only with the root's graphics context current.
     *
     * @return reusable UI rotation shader
     */
    UIRotationShader rotationShader() {
        if (rotationShader == null) rotationShader = new UIRotationShader();
        return rotationShader;
    }

    /**
     * Lifts an attached list or tree item into the final rendering pass. The
     * press position preserves the point where the pointer first grabbed it.
     *
     * @param node   item to paint above the normal UI tree
     * @param pressX original pointer X in screen coordinates
     * @param pressY original pointer Y in screen coordinates
     */
    public void beginItemDrag(UINode node, float pressX, float pressY) {
        dragPreview.begin(this, node, pressX, pressY);
    }

    /**
     * Moves the active lifted item with the pointer without changing layout.
     */
    public void moveItemDrag(float pointerX, float pointerY) {
        dragPreview.move(this, pointerX, pointerY);
    }

    /**
     * Ends a drag only when the supplied item owns the active preview.
     */
    public void endItemDrag(UINode node) {
        dragPreview.end(node);
    }

    /**
     * Tells normal rendering to leave the active item's original slot empty.
     */
    boolean skipsDragPreviewSource(UINode node) {
        return dragPreview.skips(node);
    }

    /**
     * Chooses whether a top-level UI node is rendered and hit-tested by this
     * root. An editor can present the same live node through its own viewport
     * while this root continues to own its layout and updates. Restoring the
     * node reenables normal game-window presentation.
     *
     * @param node    top-level UI node owned by this root
     * @param enabled true to use normal root presentation
     */
    public void setNodePresentationEnabled(UINode node, boolean enabled) {
        if (node == null) throw new NullPointerException("node");
        if (enabled) {
            if (externallyPresentedNodes != null) externallyPresentedNodes.remove(node);
        } else {
            if (externallyPresentedNodes == null)
                externallyPresentedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
            externallyPresentedNodes.add(node);
        }
    }

    /**
     * Checks whether the editor owns presentation of this UI subtree.
     *
     * @param node candidate root child or descendant
     * @return true when normal root rendering and input must skip it
     */
    boolean skipsExternalPresentation(UINode node) {
        return externallyPresentedNodes != null && externallyPresentedNodes.contains(node);
    }

    @Override
    protected boolean acceptsHitChild(UINode child) {
        return !skipsExternalPresentation(child);
    }

    /**
     * Begins NanoVG with root/viewport world dimensions and a pixel ratio derived
     * from the current GL viewport. Uses the larger axis ratio, bounded below by 0.01.
     * The mixed rendering context coordinates later flushes.
     *
     * @param vg valid NanoVG context handle
     */
    private void beginNanoFrame(long vg) {
        float width = viewport != null ? viewport.getWorldWidth() : getWidth();
        float height = viewport != null ? viewport.getWorldHeight() : getHeight();
        int[] pixels = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, pixels);
        float ratio = Math.max(pixels[2] / Math.max(1f, width), pixels[3] / Math.max(1f, height));
        nvgBeginFrame(vg, width, height, Math.max(0.01f, ratio));
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
        if (disposed) return;
        disposed = true;
        hideActiveTooltip();

        Keyboard.removeKeyListener(keyListener);
        Mouse.removeMouseListener(mouseListener);
        Mouse.removeScrollListener(scrollListener);
        Window.removeWindowResizeListener(windowListener);
        JGL.unsubscribe(EventTypes.WINDOW_FOCUS, focusListener);
        JGL.unsubscribe(EventTypes.TEXT_INPUT, textListener);
        JGL.unsubscribe(EventTypes.THEME_DATA_CHANGE, themeListener);
        cancelInput();
        focusScopes.clear();
        if (externallyPresentedNodes != null) externallyPresentedNodes.clear();

        detachFromRoot();
        Yoga.YGConfigFree(yogaConfig);
        batch.dispose();
        if (rotationShader != null) rotationShader.dispose();
        if (nanoSlugFont != null) {
            nanoSlugFont.dispose();
            nanoSlugFont = null;
        }
        if (nanoSlugCodeFont != null) {
            nanoSlugCodeFont.dispose();
            nanoSlugCodeFont = null;
        }
        if (nanoVGHandle != 0L) nvgDelete(nanoVGHandle);
        nanoVGHandle = 0L;
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
     * Returns a shared smooth default font once this root has a graphics context.
     *
     * @param code true for the monospace editing face
     * @return borrowed root-owned font, or null before graphics initialization
     */
    SlugFont getNanoTextFont(boolean code) {
        if (disposed || nanoVGHandle == 0) return null;
        SlugFont current = code ? nanoSlugCodeFont : nanoSlugFont;
        if (current != null) return current;
        current = SlugData.load(SystemFonts.find(code).toString(), 32, 224)
                .asFont();
        if (code) nanoSlugCodeFont = current;
        else nanoSlugFont = current;
        return current;
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
        moveFocus(1);
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
        if (!presentationEnabled) return null;
        if (viewport != null) {
            Vector2f world = viewport.screenToWorld(x, y);
            if (world == null) return null;
            x = world.x();
            y = world.y();
        }
        if (clipToViewport && customViewportClip && (x < viewportClipX || y < viewportClipY
                || x > viewportClipX + viewportClipWidth || y > viewportClipY + viewportClipHeight)) return null;

        UINode scope = activeFocusScope();
        if (scope != this)
            return scope instanceof UIContainer container ? container.findNodeAt(x, y, requiredBit) : scope.contains(x, y) && (requiredBit < 0 || scope.getBit(requiredBit)) ? scope : null;
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
        super.applyLayout();
    }

    /**
     * Refreshes resolved style caches for a node and all descendants before drawing.
     * Style application may mark layout dirty; layout synchronization happens afterward.
     *
     * @param node subtree to refresh
     */
    private void refreshStyles(UINode node) {
        node.refreshStyle();
        if (node instanceof UIContainer container)
            for (int i = 0; i < container.size(); i++) refreshStyles(container.get(i));
    }

    /**
     * Synchronizes each node's layout settings into Yoga before calculation.
     * Recurses through all container children in stored order.
     *
     * @param node  subtree to synchronize
     * @param force whether to reapply clean layout settings
     */
    private void syncTree(UINode node, boolean force) {
        node.synchronizeLayout(force);

        if (node instanceof UIContainer container) {
            for (int i = 0; i < container.size(); i++)
                syncTree(container.get(i), force);
        }
    }

    /**
     * Move backwards through the active focus scope, wrapping at its start.
     */
    public void focusPrevious() {moveFocus(-1);}

    /**
     * Collects eligible nodes inside the current modal scope and moves focus with
     * wraparound. Excludes the scope node itself when eligible descendants exist.
     * An empty candidate list clears focus.
     *
     * @param direction positive for forward traversal, negative for backward traversal
     */
    private void moveFocus(int direction) {
        List<UINode> nodes = new ArrayList<>();
        UINode scope = activeFocusScope();
        collectFocus(scope, nodes);
        if (nodes.size() > 1 && nodes.getFirst() == scope) nodes.removeFirst();
        if (nodes.isEmpty()) {
            setFocusTo(null);
            return;
        }
        int index = nodes.indexOf(focused);
        setFocusTo(nodes.get(index < 0 ? (direction > 0 ? 0 : nodes.size() - 1) : Math.floorMod(index + direction, nodes.size())));
    }

    /**
     * Appends focusable nodes in depth-first child order, skipping entire subtrees
     * whose nodes or ancestors are hidden, disabled, or detached from this root.
     *
     * @param node  subtree candidate
     * @param nodes mutable traversal list
     */
    private void collectFocus(UINode node, List<UINode> nodes) {
        if (!isNodeInteractiveNow(node)) return;
        if (node.isFocusable()) nodes.add(node);
        if (node instanceof UIContainer container)
            for (UINode child : container.getChildren()) collectFocus(child, nodes);
    }

    /**
     * Removes scopes whose nodes are detached or directly hidden, then returns the
     * topmost remaining modal node or this root when no modal scope remains.
     *
     * @return current input/focus boundary
     */
    private UINode activeFocusScope() {
        focusScopes.removeIf(scope -> scope.node().getRoot() != this || !scope.node().isVisible());
        return focusScopes.isEmpty() ? this : focusScopes.getLast().node();
    }

    /**
     * Moves a nonnull node into the overlay layer, remembers prior focus, and pushes
     * a modal scope. Cancels pointer capture, clears focus, and selects the first
     * eligible node in the scope. Reopening an already registered scope has no effect.
     *
     * @param node nonnull modal subtree
     */
    public void showModal(UINode node) {
        if (focusScopes.stream().anyMatch(scope -> scope.node() == node)) return;
        UINode previous = focused;
        showOverlay(node);
        focusScopes.add(new FocusScope(node, previous));
        cancelPointer();
        setFocusTo(null);
        focusNext();
    }

    /**
     * Hides/removes a modal through hideOverlay. Removing the top scope restores
     * its saved focus when eligible, otherwise traversal chooses a replacement.
     *
     * @param node modal node to hide; null has no effect
     */
    public void hideModal(UINode node) {hideOverlay(node);}

    /**
     * Clear captured gestures and focus without synthesizing a release/click.
     */
    public void cancelInput() {
        cancelPointer();
        dragPreview.clear();
        if (hovered != null) hovered.setHovered(false);
        hovered = null;
        Mouse.clearCursorOverride(this);
        setFocusTo(null);
        hideActiveTooltip();
    }

    /**
     * Clears capture, focus, hover, and tooltip state referring to a subtree before
     * it loses its parent/root references. Pointer cancellation is delivered without
     * synthesizing a click or release.
     *
     * @param node subtree about to detach
     */
    void nodeWillDetach(UINode node) {
        cancelInput(node);
    }

    /**
     * Clears capture, focus, hover, and tooltip state owned by a particular subtree.
     * Other controls retain their input state. Useful when a reusable UI window closes
     * without detaching; passing null has no effect. No release or click is synthesized.
     *
     * @param node subtree whose interactions are being abandoned
     */
    public void cancelInput(UINode node) {
        if (node == null) return;
        if (dragPreview.belongsTo(node)) dragPreview.clear();
        if (within(pressed, node)) cancelPointer();
        if (within(focused, node)) setFocusTo(null);
        if (within(hovered, node)) {
            hovered.setHovered(false);
            hovered = null;
            hideActiveTooltip();
        }
        updateCursor();
    }

    /**
     * Applies a target's cursor request, favoring capture over hover so resize cursors
     * persist outside their handles during dragging. Hidden/disabled/detached nodes and
     * nodes outside the current modal scope release this root's temporary override.
     */
    private void updateCursor() {
        UINode target = pressed != null ? pressed : hovered;
        int shape = isNodeInteractiveNow(target) && within(target, activeFocusScope()) ? target.getCursorShape() : 0;
        if (shape == 0) Mouse.clearCursorOverride(this);
        else Mouse.overrideCursor(this, shape);
    }

    /**
     * Returns the node currently captured by a mouse press, independently of current
     * pointer location.
     *
     * @return captured node, or null
     */
    public UINode getCaptured() {return pressed;}

    /**
     * Returns the last node selected by pointer hover handling.
     *
     * @return hovered node, or null
     */
    public UINode getHovered() {return hovered;}

    /**
     * Clears the captured node and button before notifying the former target through
     * onPointerCancel. Does not synthesize a release or click event.
     */
    private void cancelPointer() {
        clearDocumentSelection();
        UINode previous = pressed;
        pressed = null;
        pressedButton = -1;
        if (previous != null) previous.onPointerCancel();
        Mouse.clearCursorOverride(this);
    }

    /**
     * Routes an unconsumed event through a snapshot of the target's ancestor path
     * up to the active scope. Preview hooks run from boundary to target; handler and
     * bubble hooks then run outward until consumption. Rechecks interactivity while
     * bubbling so detached/disabled targets stop delivery.
     *
     * @param target         candidate input recipient
     * @param event          shared consumable event
     * @param x              event X coordinate, or NaN for nonpointer input
     * @param y              event Y coordinate, or NaN for nonpointer input
     * @param handler        event-specific node callback
     * @param bubbleHandlers whether ancestor event-specific handlers also run
     * @return true if delivery reached the target phase, even if its callback consumes the event
     */
    private boolean route(UINode target, Event event, float x, float y, Consumer<UINode> handler, boolean bubbleHandlers) {
        if (!isNodeInteractiveNow(target) || event.isConsumed()) return false;
        List<UINode> path = new ArrayList<>();
        UINode boundary = activeFocusScope();
        for (UINode node = target; node != null; node = node.getParent()) {
            path.add(node);
            if (node == boundary) break;
        }
        for (int i = path.size() - 1; i >= 0 && !event.isConsumed(); i--) {
            UINode node = path.get(i);
            node.onInputPreview(new UIInputEvent(event, target, node, x, y));
        }
        boolean delivered = false;
        for (int i = 0; i < path.size() && !event.isConsumed(); i++) {
            UINode node = path.get(i);
            if (!isNodeInteractiveNow(node)) break;
            if (i == 0) delivered = true;
            if (i == 0 || bubbleHandlers) handler.accept(node);
            if (!event.isConsumed()) node.onInputBubble(new UIInputEvent(event, target, node, x, y));
        }
        return delivered;
    }

    /**
     * Requires an attached interactive node, its focusable flag, and membership in
     * the active modal scope.
     *
     * @param node candidate focus target
     * @return whether focus may currently be assigned
     */
    private boolean isNodeFocusableNow(UINode node) {
        return isNodeInteractiveNow(node) && node.isFocusable() && within(node, activeFocusScope());
    }

    /**
     * Requires attachment to this root and visible/enabled state for the node and
     * every ancestor. Does not check hit bounds, input capability bits, or modal scope.
     *
     * @param node candidate input node
     * @return whether attachment and ancestor state permit interaction
     */
    private boolean isNodeInteractiveNow(UINode node) {
        if (!presentationEnabled || node == null || node.getRoot() != this) return false;
        for (UINode ancestor = node; ancestor != null; ancestor = ancestor.getParent()) {
            if (!ancestor.isVisible() || !ancestor.isEnabled() || skipsExternalPresentation(ancestor)) return false;
        }
        return true;
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

            mouseX = world.x();
            mouseY = world.y();
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
        if (!isNodeFocusableNow(focused)) setFocusTo(null);
        if (focused instanceof NanoLabel && (event.isCtrlDown() || event.isSuperDown()) && event.getKey() == Keyboard.A) {
            clearDocumentSelection();
            collectSelectableText(activeFocusScope());
            for (var label : selectionLabels) label.documentSelection(0, label.selectionTextLength());
            textDragActive = !selectionLabels.isEmpty();
            textAnchorLabel = 0;
            textAnchorOffset = 0;
            event.consume();
            return;
        }
        if (textDragActive && focused instanceof NanoLabel && (event.isCtrlDown() || event.isSuperDown()) && event.getKey() == Keyboard.C) {
            StringBuilder text = new StringBuilder();
            for (var label : selectionLabels) {
                if (!isNodeFocusableNow(label) || !label.isSelectable()) continue;
                String selected = label.getSelectedText();
                if (selected.isEmpty()) continue;
                if (!text.isEmpty()) text.append('\n');
                text.append(selected);
            }
            TextEditing.copyText(text.toString());
            event.consume();
            return;
        }
        if (textDragActive && (event.getKey() == Keyboard.TAB || event.getKey() == Keyboard.A && (event.isCtrlDown() || event.isSuperDown())))
            clearDocumentSelection();

        if (event.getKey() == Keyboard.TAB) {
            if (event.isShiftDown()) focusPrevious();
            else focusNext();
            event.consume();
            return;
        }

        route(focused, event, Float.NaN, Float.NaN, node -> node.onKeyPress(event), true);
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
        if (!isNodeFocusableNow(focused)) setFocusTo(null);
        route(focused, event, Float.NaN, Float.NaN, node -> node.onKeyRelease(event), true);
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
        if (pressed != null) return;

        UINode target = findNodeAt(event.getX(), event.getY(), UINode.CLICKABLE_BIT);

        if (event.getButton() == Mouse.LEFT && event.isShiftDown() && textDragActive) {
            textDragPending = true;
            extendDocumentSelection(new MouseDragEvent(Mouse.LEFT, 0, (int) textPressX, (int) textPressY, event.getX(), event.getY()));
            event.consume();
            return;
        }
        clearDocumentSelection();
        boolean textSurface = target == this || target instanceof NanoLabel label && label.isSelectable() || target != null && (target.getClass() == NanoPanel.class || target.getClass() == NanoContainer.class || target.getClass() == NanoImage.class);
        if (event.getButton() == Mouse.LEFT && textSurface) {
            collectSelectableText(activeFocusScope());
            textPressX = event.getX();
            textPressY = event.getY();
            textDragPending = !selectionLabels.isEmpty();
        }

        if (target != null) {
            if (target.isFocusable()) setFocusTo(target);
            else setFocusTo(null);

            if (target.isDisabled()) return;

            pressed = target;
            pressedButton = event.getButton();
            pressed.setPressed(true);
            updateCursor();
            if (!route(pressed, event, event.getX(), event.getY(), node -> node.onMousePress(event), false))
                cancelPointer();

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
        if (event.getButton() == Mouse.LEFT) {
            textDragPending = false;
            if (textDragActive) {
                if (pressed != null) {
                    pressed.setPressed(false);
                    pressed.onPointerCancel();
                }
                pressed = null;
                pressedButton = -1;
                event.consume();
                return;
            }
        }
        if (pressed != null && event.getButton() == pressedButton) {
            UINode target = pressed;
            pressed = null;
            pressedButton = -1;
            target.setPressed(false);
            route(target, event, event.getX(), event.getY(), node -> node.onMouseRelease(event), false);
            UINode nextHover = findNodeAt(event.getX(), event.getY(), UINode.CLICKABLE_BIT);
            if (hovered != nextHover) {
                hideActiveTooltip();
                hoverTime = 0f;
                if (hovered != null) hovered.setHovered(false);
                hovered = nextHover;
                if (hovered != null) hovered.setHovered(true);
            }
            updateCursor();
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
        if (extendDocumentSelection(event)) return;

        if (event.getButton() == pressedButton)
            route(pressed, event, event.getToX(), event.getToY(), node -> node.onMouseDrag(event), false);
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
        UINode target = findNodeAt(event.getToX(), event.getToY(), UINode.CLICKABLE_BIT);

        if (target != hovered) {
            hideActiveTooltip();
            hoverTime = 0f;

            if (hovered != null) hovered.setHovered(false);
        }

        if (target == null) {
            hovered = null;
            updateCursor();
            return;
        }

        if (target.isDisabled()) return;

        hovered = target;
        hovered.setHovered(true);
        updateCursor();
        route(hovered, event, event.getToX(), event.getToY(), node -> node.onMouseMove(event), false);
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
        route(target, event, Mouse.getX(), Mouse.getY(), node -> {
            if (node.isScrollable()) node.onMouseScroll(event);
        }, true);
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
     * Returns the stored NanoVG handle. It is zero when creation failed or disposal
     * cleared it; callers borrow the context and must coordinate with root rendering.
     *
     * @return current native NanoVG context handle
     */
    public long getNanoVGHandle() {
        return nanoVGHandle;
    }

    /**
     * Replaces the stored NanoVG handle without deleting the old one or registering
     * fonts on the replacement. The root will delete the stored nonzero handle during
     * disposal, so callers must arrange ownership of both contexts.
     *
     * @param nanoVGHandle replacement handle, or zero to disable NanoVG rendering
     */
    public void setNanoVGHandle(long nanoVGHandle) {
        this.nanoVGHandle = nanoVGHandle;
    }

    /**
     * Registers installed proportional and monospace fonts in the root's context.
     * Both renderers share the same filesystem selection and override settings.
     */
    private void registerDefaultNanoFont() {
        Path proportional = SystemFonts.find(false);
        Path monospace = SystemFonts.find(true);
        if (nvgCreateFont(nanoVGHandle, "default", proportional.toString()) == -1)
            throw new IllegalStateException("NanoVG could not load installed UI font: " + proportional);
        if (nvgCreateFont(nanoVGHandle, "code", monospace.toString()) == -1)
            throw new IllegalStateException("NanoVG could not load installed code font: " + monospace);
    }

    /**
     * Returns a shared root-owned installed font for regular UI controls.
     *
     * @return borrowed proportional font, or null before graphics initialization
     */
    public SlugFont getDefaultFont() {return getNanoTextFont(false);}

    /**
     * One modal input boundary and the focus target that preceded it. Both nodes
     * are borrowed references; eligibility is rechecked when restoring focus.
     *
     * <p>The stack of these entries constrains routing to the active modal subtree. Removing
     * a scope may restore its previous focus only if that node is still attached and eligible.</p>
     *
     * @param node     modal subtree root
     * @param previous focus target to restore, possibly null
     * @author Albert Beaupre
     */
    private record FocusScope(UINode node, UINode previous) {
    }

    /**
     * Persistent global keyboard adapter owned by this root. Forwards press and
     * release events into scoped root routing and is unregistered during disposal.
     * <p>The adapter retains its enclosing root and forwards the original event objects, allowing
     * consumption to remain visible to subsequent routing. Keyboard policy stays in the
     * root handlers rather than in the adapter.</p>
     *
     * @author Albert Beaupre
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
            boolean active = blockLowerInput && getFocused() != null;
            handleKeyPressed(event);
            if (active || blockLowerInput && getFocused() != null) event.consume();
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
            boolean active = blockLowerInput && getFocused() != null;
            handleKeyReleased(event);
            if (active) event.consume();
        }
    }

    /**
     * Persistent pointer adapter forwarding presses, releases, drags, and movement
     * to the owning root's capture and hover logic.
     * <p>The original event object is passed through so capture, hit testing, and consumption
     * share one routing decision. Registration and removal follow the enclosing root's
     * lifecycle; the adapter owns no native cursor resources.</p>
     *
     * @author Albert Beaupre
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
            boolean onUi = blockLowerInput && hasUiAt(event.getX(), event.getY());
            handleMousePressed(event);
            if (onUi) {
                pointerOwned = true;
                event.consume();
            }
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
            boolean owned = pointerOwned && event.getButton() == pressedButton;
            if (pointerOwned && pressedButton < 0) owned = true;
            handleMouseReleased(event);
            if (owned) {
                pointerOwned = false;
                event.consume();
            }
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
            if (pointerOwned) event.consume();
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
            if (blockLowerInput && hasUiAt(event.getToX(), event.getToY())) event.consume();
        }
    }

    /**
     * Persistent global scroll adapter forwarding events into the owning root's
     * hit testing and bubbling path.
     * <p>It forwards the same event object so a consuming scroll target can stop further
     * propagation. Scroll offsets belong to controls, while this adapter only connects
     * global delivery to root routing.</p>
     *
     * @author Albert Beaupre
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
            boolean onUi = blockLowerInput && hasUiAt(Mouse.getX(), Mouse.getY());
            handleMouseScrolled(event);
            if (onUi) event.consume();
        }
    }

    /**
     * Persistent resize adapter updating the root and optional viewport when the
     * window dimensions change. Unregistered with the root lifecycle.
     * <p>The resize event is forwarded to the root's sizing policy instead of directly mutating
     * individual children. Layout and viewport decisions remain centralized in the owning
     * root, and this listener allocates no window resources.</p>
     *
     * @author Albert Beaupre
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
}
