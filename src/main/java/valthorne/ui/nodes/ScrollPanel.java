package valthorne.ui.nodes;

import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.event.events.MouseScrollEvent;
import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.UIContainer;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.viewport.Viewport;

/**
 * <p>
 * {@code ScrollPanel} is a container node that provides scrollable viewing of a single
 * content node. It supports horizontal scrolling, vertical scrolling, optional
 * scrollbar rendering, draggable scrollbar thumbs, mouse wheel scrolling, scissored
 * content rendering, and style-driven visuals for the panel background and both
 * scrollbars.
 * </p>
 *
 * <p>
 * This class is intended to act as a clipped viewport over another {@link UINode}.
 * A content node is stored internally and rendered inside a scissor region while the
 * panel applies a translation based on the current scroll amounts. If the content is
 * larger than the visible area, horizontal and/or vertical scrollbar metrics are
 * calculated and scrollbar visuals can be drawn.
 * </p>
 *
 * <p>
 * The panel supports:
 * </p>
 *
 * <ul>
 *     <li>horizontal scrolling</li>
 *     <li>vertical scrolling</li>
 *     <li>enabling or disabling either scroll direction independently</li>
 *     <li>showing or hiding horizontal and vertical scrollbars independently</li>
 *     <li>mouse wheel scrolling</li>
 *     <li>dragging scrollbar thumbs</li>
 *     <li>viewport-aware mouse coordinate conversion</li>
 *     <li>automatic scroll clamping after layout</li>
 *     <li>style-driven backgrounds and scrollbar visuals</li>
 * </ul>
 *
 * <p>
 * The content node defaults to a {@link Panel}, but callers may replace it using
 * {@link #setContent(UINode)}. Only a single content node is managed directly by this
 * class. Hit detection for children is adjusted using the current scroll values so
 * interaction can still target the proper child positions even though rendering is
 * translated.
 * </p>
 *
 * <p>
 * Scrollbar geometry is computed lazily through an internal {@code ScrollMetrics}
 * structure. This includes bar bounds, thumb bounds, visibility flags, and the space
 * consumed by active scrollbars.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * ScrollPanel panel = new ScrollPanel()
 *         .horizontal(true)
 *         .vertical(true)
 *         .horizontalBar(true)
 *         .verticalBar(true)
 *         .scrollSpeed(48f);
 *
 * Grid content = new Grid()
 *         .columns(4)
 *         .cellSize(64, 64)
 *         .gap(8);
 *
 * panel.setContent(content);
 *
 * panel.scroll(0f, 0f);
 * panel.scrollBy(24f, 48f);
 *
 * float x = panel.getScrollX();
 * float y = panel.getScrollY();
 * float maxX = panel.getMaxScrollX();
 * float maxY = panel.getMaxScrollY();
 *
 * panel.update(delta);
 * panel.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete usage of the class: creating the panel,
 * configuring scroll directions and bars, assigning content, reading scroll values,
 * adjusting scroll positions, and drawing the result.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class ScrollPanel extends UIContainer {

    /**
     * Style key used to resolve the panel background drawable.
     */
    public static final StyleKey<Drawable> BACKGROUND_KEY = StyleKey.of("background", Drawable.class);

    /**
     * Style key used to resolve the horizontal scrollbar background drawable.
     */
    public static final StyleKey<Drawable> HORIZONTAL_BAR_BACKGROUND_KEY = StyleKey.of("horizontalBarBackground", Drawable.class);

    /**
     * Style key used to resolve the horizontal scrollbar thumb drawable.
     */
    public static final StyleKey<Drawable> HORIZONTAL_BAR_FOREGROUND_KEY = StyleKey.of("horizontalBarForeground", Drawable.class);

    /**
     * Style key used to resolve the vertical scrollbar background drawable.
     */
    public static final StyleKey<Drawable> VERTICAL_BAR_BACKGROUND_KEY = StyleKey.of("verticalBarBackground", Drawable.class);

    /**
     * Style key used to resolve the vertical scrollbar thumb drawable.
     */
    public static final StyleKey<Drawable> VERTICAL_BAR_FOREGROUND_KEY = StyleKey.of("verticalBarForeground", Drawable.class);

    /**
     * Style key used to resolve the height of the horizontal scrollbar.
     */
    public static final StyleKey<Float> HORIZONTAL_BAR_HEIGHT_KEY = StyleKey.of("horizontalBarHeight", Float.class, 8f);

    /**
     * Style key used to resolve the width of the vertical scrollbar.
     */
    public static final StyleKey<Float> VERTICAL_BAR_WIDTH_KEY = StyleKey.of("verticalBarWidth", Float.class, 8f);

    /**
     * Style key used to resolve padding around scrollbar bars.
     */
    public static final StyleKey<Float> BAR_PADDING_KEY = StyleKey.of("barPadding", Float.class, 0f);

    /**
     * Style key used to resolve the minimum size of scrollbar thumbs.
     */
    public static final StyleKey<Float> MIN_THUMB_SIZE_KEY = StyleKey.of("minThumbSize", Float.class, 18f);

    private UINode content = new Panel(); // The single content node rendered inside the scrollable clipped region

    private boolean horizontal = true; // Whether horizontal scrolling is enabled
    private boolean vertical = true; // Whether vertical scrolling is enabled
    private boolean drawHorizontalBar = true; // Whether the horizontal scrollbar is allowed to be drawn
    private boolean drawVerticalBar = true; // Whether the vertical scrollbar is allowed to be drawn
    private final ScrollMetrics metrics = new ScrollMetrics(); // Cached metrics object reused when computing scrollbar geometry

    private float scrollX; // Current horizontal scroll offset
    private float scrollY; // Current vertical scroll offset
    private float targetScrollX; // Target horizontal scroll offset used for clamping and updates
    private float targetScrollY; // Target vertical scroll offset used for clamping and updates
    private float scrollSpeed = 32f; // Mouse wheel scroll speed multiplier

    private float mouseStartX; // Previous mouse X position used while dragging a scrollbar
    private float mouseStartY; // Previous mouse Y position used while dragging a scrollbar
    private boolean draggingHorizontalBar; // Whether the horizontal scrollbar thumb is currently being dragged
    private boolean draggingVerticalBar; // Whether the vertical scrollbar thumb is currently being dragged

    private Drawable background; // Resolved background drawable for the scroll panel
    private Drawable horizontalBarBackground; // Resolved drawable for the horizontal scrollbar track
    private Drawable horizontalBarForeground; // Resolved drawable for the horizontal scrollbar thumb
    private Drawable verticalBarBackground; // Resolved drawable for the vertical scrollbar track
    private Drawable verticalBarForeground; // Resolved drawable for the vertical scrollbar thumb
    private float horizontalBarHeight = 8f; // Resolved height of the horizontal scrollbar
    private float verticalBarWidth = 8f; // Resolved width of the vertical scrollbar
    private float barPadding = 4f; // Resolved padding applied to scrollbar positioning
    private float minThumbSize = 18f; // Resolved minimum size for scrollbar thumbs

    /**
     * <p>
     * Creates a new scroll panel with both scroll directions enabled, both scrollbars
     * allowed to render, a default content panel, and scrollable interaction enabled.
     * </p>
     *
     * <p>
     * The constructor also initializes the style cache immediately and adds the
     * default content node as a child.
     * </p>
     */
    public ScrollPanel() {
        setBit(SCROLLABLE_BIT, true);
        refreshStyleCache();
        this.add(content);
    }

    /**
     * <p>
     * Returns the current content node displayed inside this scroll panel.
     * </p>
     *
     * @return the current content node
     */
    public UINode getContent() {
        return content;
    }

    /**
     * <p>
     * Sets the scroll speed multiplier used by mouse wheel scrolling.
     * </p>
     *
     * @param speed the new scroll speed multiplier
     * @return this scroll panel
     */
    public ScrollPanel scrollSpeed(float speed) {
        this.scrollSpeed = speed;
        return this;
    }

    /**
     * <p>
     * Returns whether horizontal scrolling is currently enabled.
     * </p>
     *
     * @return {@code true} if horizontal scrolling is enabled
     */
    public boolean isHorizontalEnabled() {
        return horizontal;
    }

    /**
     * <p>
     * Enables or disables horizontal scrolling.
     * </p>
     *
     * <p>
     * Disabling horizontal scrolling resets horizontal scroll positions and cancels
     * active horizontal thumb dragging. Enabling it reclamps the current and target
     * horizontal scroll values to the valid range.
     * </p>
     *
     * @param horizontal whether horizontal scrolling should be enabled
     * @return this scroll panel
     */
    public ScrollPanel horizontal(boolean horizontal) {
        this.horizontal = horizontal;

        if (!horizontal) {
            scrollX = 0f;
            targetScrollX = 0f;
            draggingHorizontalBar = false;
        } else {
            scrollX = clampScrollX(scrollX);
            targetScrollX = clampScrollX(targetScrollX);
        }

        return this;
    }

    /**
     * <p>
     * Returns whether vertical scrolling is currently enabled.
     * </p>
     *
     * @return {@code true} if vertical scrolling is enabled
     */
    public boolean isVerticalEnabled() {
        return vertical;
    }

    /**
     * <p>
     * Enables or disables vertical scrolling.
     * </p>
     *
     * <p>
     * Disabling vertical scrolling resets vertical scroll positions and cancels active
     * vertical thumb dragging. Enabling it reclamps the current and target vertical
     * scroll values to the valid range.
     * </p>
     *
     * @param vertical whether vertical scrolling should be enabled
     * @return this scroll panel
     */
    public ScrollPanel vertical(boolean vertical) {
        this.vertical = vertical;

        if (!vertical) {
            scrollY = 0f;
            targetScrollY = 0f;
            draggingVerticalBar = false;
        } else {
            scrollY = clampScrollY(scrollY);
            targetScrollY = clampScrollY(targetScrollY);
        }

        return this;
    }

    /**
     * <p>
     * Returns whether horizontal scrollbar drawing is enabled.
     * </p>
     *
     * @return {@code true} if horizontal scrollbar drawing is enabled
     */
    public boolean isHorizontalBarVisible() {
        return drawHorizontalBar;
    }

    /**
     * <p>
     * Enables or disables horizontal scrollbar drawing.
     * </p>
     *
     * @param drawHorizontalBar whether the horizontal scrollbar should be drawn when needed
     * @return this scroll panel
     */
    public ScrollPanel horizontalBar(boolean drawHorizontalBar) {
        this.drawHorizontalBar = drawHorizontalBar;
        return this;
    }

    /**
     * <p>
     * Returns whether vertical scrollbar drawing is enabled.
     * </p>
     *
     * @return {@code true} if vertical scrollbar drawing is enabled
     */
    public boolean isVerticalBarVisible() {
        return drawVerticalBar;
    }

    /**
     * <p>
     * Enables or disables vertical scrollbar drawing.
     * </p>
     *
     * @param drawVerticalBar whether the vertical scrollbar should be drawn when needed
     * @return this scroll panel
     */
    public ScrollPanel verticalBar(boolean drawVerticalBar) {
        this.drawVerticalBar = drawVerticalBar;
        return this;
    }

    /**
     * <p>
     * Returns the current horizontal scroll offset.
     * </p>
     *
     * @return the current horizontal scroll offset
     */
    public float getScrollX() {
        return scrollX;
    }

    /**
     * <p>
     * Sets the horizontal scroll offset immediately.
     * </p>
     *
     * <p>
     * If horizontal scrolling is disabled, the value is forced to {@code 0}. Otherwise
     * the value is clamped into the valid horizontal scroll range and applied to both
     * the current and target horizontal scroll positions.
     * </p>
     *
     * @param scrollX the new horizontal scroll offset
     * @return this scroll panel
     */
    public ScrollPanel scrollX(float scrollX) {
        this.targetScrollX = horizontal ? clampScrollX(scrollX) : 0f;
        this.scrollX = this.targetScrollX;
        return this;
    }

    /**
     * <p>
     * Returns the current vertical scroll offset.
     * </p>
     *
     * @return the current vertical scroll offset
     */
    public float getScrollY() {
        return scrollY;
    }

    /**
     * <p>
     * Sets the vertical scroll offset immediately.
     * </p>
     *
     * <p>
     * If vertical scrolling is disabled, the value is forced to {@code 0}. Otherwise
     * the value is clamped into the valid vertical scroll range and applied to both
     * the current and target vertical scroll positions.
     * </p>
     *
     * @param scrollY the new vertical scroll offset
     * @return this scroll panel
     */
    public ScrollPanel scrollY(float scrollY) {
        this.targetScrollY = vertical ? clampScrollY(scrollY) : 0f;
        this.scrollY = this.targetScrollY;
        return this;
    }

    /**
     * <p>
     * Sets both horizontal and vertical scroll offsets immediately.
     * </p>
     *
     * @param scrollX the new horizontal scroll offset
     * @param scrollY the new vertical scroll offset
     * @return this scroll panel
     */
    public ScrollPanel scroll(float scrollX, float scrollY) {
        scrollX(scrollX);
        scrollY(scrollY);
        return this;
    }

    /**
     * <p>
     * Adjusts the current scroll offsets by the supplied deltas.
     * </p>
     *
     * <p>
     * Only enabled directions are affected. New values are clamped to their valid
     * ranges and applied to both current and target scroll values.
     * </p>
     *
     * @param dx horizontal scroll delta
     * @param dy vertical scroll delta
     * @return this scroll panel
     */
    public ScrollPanel scrollBy(float dx, float dy) {
        if (horizontal) {
            targetScrollX = clampScrollX(targetScrollX + dx);
            scrollX = targetScrollX;
        }

        if (vertical) {
            targetScrollY = clampScrollY(targetScrollY + dy);
            scrollY = targetScrollY;
        }

        return this;
    }

    /**
     * <p>
     * Returns the maximum horizontal scroll offset based on content width, panel width,
     * and active vertical scrollbar width contribution.
     * </p>
     *
     * @return the maximum horizontal scroll amount
     */
    public float getMaxScrollX() {
        return Math.max(0f, (content.getWidth() - getWidth()) + metrics.verticalBarWidth);
    }

    /**
     * <p>
     * Returns the maximum vertical scroll offset based on content height, panel height,
     * and active horizontal scrollbar height contribution.
     * </p>
     *
     * @return the maximum vertical scroll amount
     */
    public float getMaxScrollY() {
        return Math.max(0f, (content.getHeight() - getHeight()) + metrics.horizontalBarHeight);
    }

    /**
     * <p>
     * Replaces the current content node with the supplied child node.
     * </p>
     *
     * <p>
     * The existing content node is removed first. If the supplied child is
     * {@code null}, the method returns after removing the old content. Otherwise the
     * new child becomes the active content node and is added as a child of the panel.
     * </p>
     *
     * @param child the new content node
     */
    public void setContent(UINode child) {
        super.remove(this.content);
        if (child == null)
            return;
        super.add(this.content = child);
    }

    /**
     * <p>
     * Invalidates this node's style tree and refreshes cached style-driven values.
     * </p>
     *
     * <p>
     * This ensures scrollbar and background drawables, sizes, and padding are updated
     * whenever styles are invalidated.
     * </p>
     */
    @Override
    protected void invalidateStyleTree() {
        super.invalidateStyleTree();
        refreshStyleCache();
    }

    /**
     * <p>
     * Transforms child hit-test X coordinates by the current horizontal scroll offset.
     * </p>
     *
     * <p>
     * This allows hit testing against children to line up with the translated content.
     * </p>
     *
     * @param x the incoming hit-test X coordinate
     * @return the transformed child hit-test X coordinate
     */
    @Override
    protected float transformChildHitX(float x) {
        return x + scrollX;
    }

    /**
     * <p>
     * Transforms child hit-test Y coordinates by the current vertical scroll offset.
     * </p>
     *
     * <p>
     * This allows hit testing against children to line up with the translated content.
     * </p>
     *
     * @param y the incoming hit-test Y coordinate
     * @return the transformed child hit-test Y coordinate
     */
    @Override
    protected float transformChildHitY(float y) {
        return y - scrollY;
    }

    /**
     * <p>
     * Finds the top-most node at the supplied coordinates that satisfies the required bit.
     * </p>
     *
     * <p>
     * The method first rejects invisible or disabled states, then rejects coordinates
     * outside the panel bounds. Scrollbar regions are checked before delegating to
     * normal child hit testing so the panel itself can intercept input on visible
     * scrollbar bars and thumbs.
     * </p>
     *
     * @param x the X coordinate to test
     * @param y the Y coordinate to test
     * @param requiredBit the required interaction bit
     * @return the matched node, or {@code null} if no match exists
     */
    @Override
    public UINode findNodeAt(float x, float y, int requiredBit) {
        if (!isVisible() || isDisabled()) return null;

        if (!contains(x, y)) return null;

        ScrollMetrics metrics = getScrollMetrics();

        if (metrics.showHorizontalBar && x >= metrics.horizontalBarX && x <= metrics.horizontalBarX + metrics.horizontalBarWidth && y >= metrics.horizontalBarY && y <= metrics.horizontalBarY + metrics.horizontalBarHeight) {
            return requiredBit < 0 || getBit(requiredBit) ? this : null;
        }

        if (metrics.showVerticalBar && x >= metrics.verticalBarX && x <= metrics.verticalBarX + metrics.verticalBarWidth && y >= metrics.verticalBarY && y <= metrics.verticalBarY + metrics.verticalBarHeight) {
            return requiredBit < 0 || getBit(requiredBit) ? this : null;
        }

        return super.findNodeAt(x, y, requiredBit);
    }

    /**
     * <p>
     * Handles mouse wheel scrolling for this panel.
     * </p>
     *
     * <p>
     * Vertical scrolling is preferred when vertical scrolling is enabled and needed.
     * Otherwise, horizontal scrolling is used when enabled and needed. Scroll deltas
     * are scaled by the configured scroll speed.
     * </p>
     *
     * @param event the mouse scroll event
     */
    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        if (vertical && getMaxScrollY() > 0f) {
            scrollBy(0f, -event.yOffset() * scrollSpeed);
        } else if (horizontal && getMaxScrollX() > 0f) {
            scrollBy(-event.xOffset() * scrollSpeed, 0f);
        }
    }

    /**
     * <p>
     * Handles mouse press interaction for scrollbar dragging.
     * </p>
     *
     * <p>
     * The method optionally converts mouse coordinates through the root viewport,
     * resets both dragging flags, and then starts dragging the appropriate scrollbar
     * if the mouse press occurred inside a visible horizontal or vertical scrollbar.
     * </p>
     *
     * @param event the mouse press event
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        float mouseX = event.getX();
        float mouseY = event.getY();

        ScrollMetrics metrics = getScrollMetrics();

        Viewport viewport = getRoot().getViewport();
        if (viewport != null) {
            Vector2f world = viewport.screenToWorld(event.getX(), event.getY());
            if (world == null) return;
            mouseX = world.getX();
            mouseY = world.getY();
        }

        draggingHorizontalBar = false;
        draggingVerticalBar = false;

        if (metrics.showHorizontalBar && mouseX >= metrics.horizontalBarX && mouseX <= metrics.horizontalBarX + metrics.horizontalBarWidth && mouseY >= metrics.horizontalBarY && mouseY <= metrics.horizontalBarY + metrics.horizontalBarHeight) {
            draggingHorizontalBar = true;
            mouseStartX = mouseX;
            mouseStartY = mouseY;
            return;
        }

        if (metrics.showVerticalBar && mouseX >= metrics.verticalBarX && mouseX <= metrics.verticalBarX + metrics.verticalBarWidth && mouseY >= metrics.verticalBarY && mouseY <= metrics.verticalBarY + metrics.verticalBarHeight) {
            draggingVerticalBar = true;
            mouseStartX = mouseX;
            mouseStartY = mouseY;
        }
    }

    /**
     * <p>
     * Handles dragging of horizontal and vertical scrollbar thumbs.
     * </p>
     *
     * <p>
     * Mouse movement is measured either in world space or raw coordinates depending on
     * whether a viewport is active. That movement is then converted into scroll delta
     * based on the ratio between maximum content scroll and maximum thumb travel.
     * </p>
     *
     * @param event the mouse drag event
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        float diffX, diffY;

        Viewport viewport = getRoot().getViewport();
        if (viewport != null) {
            Vector2f world = viewport.screenToWorld(event.getToX(), event.getToY());
            if (world == null) return;
            diffX = world.getX() - mouseStartX;
            diffY = world.getY() - mouseStartY;

            mouseStartX = world.getX();
            mouseStartY = world.getY();
        } else {
            diffX = event.getToX() - mouseStartX;
            diffY = event.getToY() - mouseStartY;
            mouseStartX = event.getToX();
            mouseStartY = event.getToY();
        }

        ScrollMetrics metrics = getScrollMetrics();

        if (draggingHorizontalBar && metrics.showHorizontalBar) {
            float maxThumbTravel = Math.max(0f, metrics.horizontalBarWidth - metrics.horizontalThumbWidth);
            if (maxThumbTravel > 0f && getMaxScrollX() > 0f) {
                float scrollDeltaX = diffX * (getMaxScrollX() / maxThumbTravel);
                scrollBy(scrollDeltaX, 0f);
            }
        }

        if (draggingVerticalBar && metrics.showVerticalBar) {
            float maxThumbTravel = Math.max(0f, metrics.verticalBarHeight - metrics.verticalThumbHeight);
            if (maxThumbTravel > 0f && getMaxScrollY() > 0f) {
                float scrollDeltaY = diffY * (getMaxScrollY() / maxThumbTravel);
                scrollBy(0f, -scrollDeltaY);
            }
        }
    }

    /**
     * <p>
     * Handles mouse release by ending any active scrollbar dragging.
     * </p>
     *
     * @param event the mouse release event
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        draggingHorizontalBar = false;
        draggingVerticalBar = false;
    }

    /**
     * <p>
     * Draws the scroll panel, clipped content, and any visible scrollbars.
     * </p>
     *
     * <p>
     * The panel background is drawn first if present. Then the content is rendered
     * inside a scissor region while translated by the current scroll amounts.
     * Horizontal and vertical scrollbars are then drawn on top if needed and enabled.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (!isVisible()) return;

        refreshStyleCache();
        ScrollMetrics metrics = getScrollMetrics();

        if (background != null) background.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());

        batch.beginScissor(getRenderX(), getRenderY() + metrics.horizontalBarHeight, getWidth() - metrics.verticalBarWidth, getHeight() - metrics.horizontalBarHeight);
        batch.pushTranslation(-scrollX, scrollY);
        content.draw(batch);
        batch.popTranslation();
        batch.endScissor();

        if (metrics.showHorizontalBar && metrics.horizontalBarWidth > 0f) {
            if (horizontalBarBackground != null)
                horizontalBarBackground.draw(batch, metrics.horizontalBarX, metrics.horizontalBarY, metrics.horizontalBarWidth, metrics.horizontalBarHeight);

            if (horizontalBarForeground != null)
                horizontalBarForeground.draw(batch, metrics.horizontalThumbX, metrics.horizontalThumbY, metrics.horizontalThumbWidth, metrics.horizontalThumbHeight);
        }

        if (metrics.showVerticalBar && metrics.verticalBarHeight > 0f) {
            if (verticalBarBackground != null)
                verticalBarBackground.draw(batch, metrics.verticalBarX, metrics.verticalBarY, metrics.verticalBarWidth, metrics.verticalBarHeight);

            if (verticalBarForeground != null)
                verticalBarForeground.draw(batch, metrics.verticalThumbX, metrics.verticalThumbY, metrics.verticalThumbWidth, metrics.verticalThumbHeight);
        }
    }

    /**
     * <p>
     * Performs post-layout scroll clamping.
     * </p>
     *
     * <p>
     * After layout changes, both current and target scroll values are clamped into
     * their valid ranges, or reset to zero when the corresponding direction is disabled.
     * </p>
     */
    @Override
    protected void afterLayout() {
        super.afterLayout();
        scrollX = horizontal ? clampScrollX(targetScrollX) : 0f;
        scrollY = vertical ? clampScrollY(targetScrollY) : 0f;
        targetScrollX = scrollX;
        targetScrollY = scrollY;
    }

    /**
     * <p>
     * Refreshes cached style-driven drawables and sizing values.
     * </p>
     *
     * <p>
     * The cache is reset to defaults first, then replaced with resolved style values
     * when a style is available.
     * </p>
     */
    private void refreshStyleCache() {
        ResolvedStyle style = getStyle();

        background = null;
        horizontalBarBackground = null;
        horizontalBarForeground = null;
        verticalBarBackground = null;
        verticalBarForeground = null;
        horizontalBarHeight = 8f;
        verticalBarWidth = 8f;
        barPadding = 4f;
        minThumbSize = 18f;

        if (style == null) return;

        background = style.get(BACKGROUND_KEY);
        horizontalBarBackground = style.get(HORIZONTAL_BAR_BACKGROUND_KEY);
        horizontalBarForeground = style.get(HORIZONTAL_BAR_FOREGROUND_KEY);
        verticalBarBackground = style.get(VERTICAL_BAR_BACKGROUND_KEY);
        verticalBarForeground = style.get(VERTICAL_BAR_FOREGROUND_KEY);

        Float resolvedHorizontalBarHeight = style.get(HORIZONTAL_BAR_HEIGHT_KEY);
        Float resolvedVerticalBarWidth = style.get(VERTICAL_BAR_WIDTH_KEY);
        Float resolvedBarPadding = style.get(BAR_PADDING_KEY);
        Float resolvedMinThumbSize = style.get(MIN_THUMB_SIZE_KEY);

        if (resolvedHorizontalBarHeight != null) horizontalBarHeight = resolvedHorizontalBarHeight;

        if (resolvedVerticalBarWidth != null) verticalBarWidth = resolvedVerticalBarWidth;

        if (resolvedBarPadding != null) barPadding = resolvedBarPadding;

        if (resolvedMinThumbSize != null) minThumbSize = resolvedMinThumbSize;
    }

    /**
     * <p>
     * Clamps a horizontal scroll value into the valid horizontal scroll range.
     * </p>
     *
     * @param value the horizontal scroll value to clamp
     * @return the clamped horizontal scroll value
     */
    private float clampScrollX(float value) {
        return MathUtils.clamp(value, 0f, getMaxScrollX());
    }

    /**
     * <p>
     * Clamps a vertical scroll value into the valid vertical scroll range.
     * </p>
     *
     * @param value the vertical scroll value to clamp
     * @return the clamped vertical scroll value
     */
    private float clampScrollY(float value) {
        return MathUtils.clamp(value, 0f, getMaxScrollY());
    }

    /**
     * <p>
     * Computes and returns the current scrollbar metrics.
     * </p>
     *
     * <p>
     * This method updates the reused {@link ScrollMetrics} instance with visibility,
     * track bounds, thumb bounds, and thumb sizes for both horizontal and vertical
     * scrollbars. Thumb sizing is based on the ratio between visible area and content
     * size, with a minimum thumb size enforced.
     * </p>
     *
     * @return the updated scrollbar metrics
     */
    private ScrollMetrics getScrollMetrics() {

        metrics.showHorizontalBar = drawHorizontalBar && horizontal && getMaxScrollX() > 0f;
        metrics.showVerticalBar = drawVerticalBar && vertical && getMaxScrollY() > 0f;

        if (metrics.showHorizontalBar) {
            metrics.horizontalBarX = getRenderX() + barPadding;
            metrics.horizontalBarY = getRenderY() + barPadding;
            metrics.horizontalBarWidth = Math.max(0f, getWidth() - barPadding * 2f - (metrics.showVerticalBar ? verticalBarWidth + barPadding : 0f));
            metrics.horizontalBarHeight = horizontalBarHeight;

            float visibleWidth = getWidth();
            float contentWidth = content.getWidth();
            metrics.horizontalThumbHeight = horizontalBarHeight;
            metrics.horizontalThumbWidth = Math.min(metrics.horizontalBarWidth, Math.max(minThumbSize, metrics.horizontalBarWidth * (visibleWidth / Math.max(visibleWidth, contentWidth))));

            float maxThumbTravel = Math.max(0f, metrics.horizontalBarWidth - metrics.horizontalThumbWidth);
            metrics.horizontalThumbX = metrics.horizontalBarX + (getMaxScrollX() <= 0f ? 0f : (scrollX / getMaxScrollX()) * maxThumbTravel);
            metrics.horizontalThumbY = metrics.horizontalBarY;
        }

        if (metrics.showVerticalBar) {
            metrics.verticalBarWidth = verticalBarWidth;
            metrics.verticalBarHeight = getHeight();
            metrics.verticalBarX = getRenderX() + getWidth() - barPadding - verticalBarWidth;
            metrics.verticalBarY = getRenderY();

            float visibleHeight = getHeight();
            float contentHeight = content.getHeight();
            metrics.verticalThumbWidth = verticalBarWidth;
            metrics.verticalThumbHeight = Math.min(metrics.verticalBarHeight, Math.max(minThumbSize, metrics.verticalBarHeight * (visibleHeight / Math.max(visibleHeight, contentHeight))));

            float maxThumbTravel = Math.max(0f, metrics.verticalBarHeight - metrics.verticalThumbHeight);
            metrics.verticalThumbX = metrics.verticalBarX;
            metrics.verticalThumbY = metrics.verticalBarY + maxThumbTravel - (getMaxScrollY() <= 0f ? 0f : (scrollY / getMaxScrollY()) * maxThumbTravel);
        }

        return metrics;
    }

    /**
     * <p>
     * {@code ScrollMetrics} is a small reusable data holder that stores the computed
     * geometry and visibility state of the horizontal and vertical scrollbars.
     * </p>
     *
     * <p>
     * It is intentionally private and static because it only exists to support the
     * internal layout and rendering calculations of {@link ScrollPanel}.
     * </p>
     *
     * <h2>Example Usage</h2>
     *
     * <pre>{@code
     * // Internal to ScrollPanel:
     * ScrollMetrics metrics = getScrollMetrics();
     *
     * if (metrics.showHorizontalBar) {
     *     // draw horizontal track and thumb
     * }
     *
     * if (metrics.showVerticalBar) {
     *     // draw vertical track and thumb
     * }
     * }</pre>
     *
     * @author Albert Beaupre
     * @since March 13th, 2026
     */
    private static final class ScrollMetrics {
        private boolean showHorizontalBar; // Whether the horizontal scrollbar should currently be rendered
        private boolean showVerticalBar; // Whether the vertical scrollbar should currently be rendered

        private float horizontalBarX; // X position of the horizontal scrollbar track
        private float horizontalBarY; // Y position of the horizontal scrollbar track
        private float horizontalBarWidth; // Width of the horizontal scrollbar track
        private float horizontalBarHeight; // Height of the horizontal scrollbar track
        private float horizontalThumbX; // X position of the horizontal scrollbar thumb
        private float horizontalThumbY; // Y position of the horizontal scrollbar thumb
        private float horizontalThumbWidth; // Width of the horizontal scrollbar thumb
        private float horizontalThumbHeight; // Height of the horizontal scrollbar thumb

        private float verticalBarX; // X position of the vertical scrollbar track
        private float verticalBarY; // Y position of the vertical scrollbar track
        private float verticalBarWidth; // Width of the vertical scrollbar track
        private float verticalBarHeight; // Height of the vertical scrollbar track
        private float verticalThumbX; // X position of the vertical scrollbar thumb
        private float verticalThumbY; // Y position of the vertical scrollbar thumb
        private float verticalThumbWidth; // Width of the vertical scrollbar thumb
        private float verticalThumbHeight; // Height of the vertical scrollbar thumb
    }
}