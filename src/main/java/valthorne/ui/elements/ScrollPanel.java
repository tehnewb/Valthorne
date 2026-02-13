package valthorne.ui.elements;

import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.event.events.MouseScrollEvent;
import valthorne.math.geometry.Rectangle;
import valthorne.ui.Element;
import valthorne.ui.ElementContainer;
import valthorne.ui.UI;
import valthorne.ui.styles.ScrollBarStyle;
import valthorne.ui.styles.ScrollPanelStyle;

import java.util.HashMap;
import java.util.Map;

/**
 * A scrollable container that clips its children and uses {@link ScrollBar} elements.
 *
 * <p>Behavior:</p>
 * <ul>
 *     <li>ScrollBars live inside the panel (visually inside the panel bounds).</li>
 *     <li>Children are clipped to {@code clipBounds}, which is the panel area minus the scrollbar strips.</li>
 *     <li>Keeps your "move children by scroll" logic (children positions are updated by scroll values).</li>
 *     <li>ScrollBars are not part of {@link ElementContainer}'s {@code elements[]} array, so they do not scroll.</li>
 * </ul>
 *
 * <p>Coordinate system: bottom-left origin, +Y up.</p>
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public class ScrollPanel extends ElementContainer {

    private ScrollPanelStyle style;

    private float scrollX, scrollY;
    private float contentWidth, contentHeight;
    private float maxScrollX, maxScrollY;

    private float scrollSpeed = 40f;

    private final Map<Element, Float> baseX = new HashMap<>();
    private final Map<Element, Float> baseY = new HashMap<>();

    /**
     * Content clip area (panel area minus scrollbar strips).
     */
    private Rectangle clipBounds;

    private boolean initialLayout = true;

    // -----------------------------
    // Scrollbar settings
    // -----------------------------
    private boolean showScrollbars = true;
    private float scrollbarThickness = 10f;
    private float thumbMinSize = 18f;

    // Standalone Elements owned by this panel (not in child array).
    private ScrollBar vBar;
    private ScrollBar hBar;

    public ScrollPanel() {
        this.setFocusable(true);
        this.setScrollable(true);
        ensureBars();
    }

    public ScrollPanel(ScrollPanelStyle style) {
        this();
        this.style = style;
    }

    public ScrollPanel setStyle(ScrollPanelStyle style) {
        this.style = style;
        return this;
    }

    private void ensureBars() {
        if (!showScrollbars)
            return;

        if (vBar == null) {
            vBar = new ScrollBar(null)
                    .setOrientation(ScrollBar.Orientation.VERTICAL)
                    .setThumbMinSize(thumbMinSize)
                    .setOnScroll(this::setScrollYInternal);
        }
        if (hBar == null) {
            hBar = new ScrollBar(null)
                    .setOrientation(ScrollBar.Orientation.HORIZONTAL)
                    .setThumbMinSize(thumbMinSize)
                    .setOnScroll(this::setScrollXInternal);
        }
    }

    @Override
    public void setUI(UI ui) {
        super.setUI(ui);
        ensureBars();
        ui.addElement(vBar);
        ui.addElement(hBar);
    }

    @Override
    public void onRemove() {
        this.getUI().removeElement(vBar);
        this.getUI().removeElement(hBar);
    }

    @Override
    public void layout() {
        super.layout();
        ensureBars();

        // Ensure clipBounds exists.
        if (clipBounds == null) {
            clipBounds = new Rectangle(x, y, width, height);
        }

        // PASS 0: start with full panel as clip (we'll shrink after we know if bars are needed)
        clipBounds.setX(x);
        clipBounds.setY(y);
        clipBounds.setWidth(width);
        clipBounds.setHeight(height);

        // Compute base positions + content size from children.
        computeBasePositionsAndContentBounds();

        // PASS 1: compute max scroll as if full panel is viewport
        float viewW = clipBounds.getWidth();
        float viewH = clipBounds.getHeight();

        maxScrollX = Math.max(0f, contentWidth - viewW);
        maxScrollY = Math.max(0f, contentHeight - viewH);

        boolean needV = showScrollbars && maxScrollY > 0f;
        boolean needH = showScrollbars && maxScrollX > 0f;

        // Apply the real content clip bounds (panel minus scrollbar strips).
        applyContentClipBounds(needV, needH);

        // PASS 2: recompute max scroll with reduced viewport (real clip size)
        viewW = clipBounds.getWidth();
        viewH = clipBounds.getHeight();

        maxScrollX = Math.max(0f, contentWidth - viewW);
        maxScrollY = Math.max(0f, contentHeight - viewH);

        clampScroll();
        applyScrollToChildren();

        // First-time: start at bottom.
        if (initialLayout) {
            initialLayout = false;
            setScrollY(maxScrollY);
        }

        // Clip children to content area.
        this.setClipBounds(clipBounds);

        // Layout bars inside the panel, occupying the reserved strips.
        layoutScrollBarsInside(viewW, viewH, needV, needH);
    }

    /**
     * Sets the content clip bounds to be panel minus scrollbar strips.
     *
     * <p>Bars are placed INSIDE the panel:</p>
     * <ul>
     *     <li>Vertical bar strip: right side</li>
     *     <li>Horizontal bar strip: bottom side</li>
     * </ul>
     *
     * <p>So the content clip shrinks:</p>
     * <ul>
     *     <li>Width reduced if vertical bar needed</li>
     *     <li>Height reduced and Y raised if horizontal bar needed (bottom bar)</li>
     * </ul>
     */
    private void applyContentClipBounds(boolean needV, boolean needH) {
        float t = scrollbarThickness;

        // We reserve "t + pad" inside the panel so content doesn't draw under the bar.
        float reserveW = needV ? t : 0f;
        float reserveH = needH ? t : 0f;
    }

    /**
     * Layout scrollbars inside reserved strips.
     *
     * <p>Bars sit inside the panel:</p>
     * <ul>
     *     <li>Vertical bar at right edge</li>
     *     <li>Horizontal bar at bottom edge</li>
     * </ul>
     *
     * <p>Bars are sized to match the content viewport span they control.</p>
     */
    private void layoutScrollBarsInside(float viewW, float viewH, boolean needV, boolean needH) {
        // viewW/viewH are the content clip sizes at this point.
        // We'll place bars in the reserved strips that were subtracted from clipBounds.

        if (!showScrollbars) return;

        float t = scrollbarThickness;

        // Content clip rectangle
        float cx = clipBounds.getX();
        float cy = clipBounds.getY();
        float cw = clipBounds.getWidth();
        float ch = clipBounds.getHeight();

        // Vertical bar strip occupies: x + (width - t) .. x + width
        // Its vertical extent matches content clip height.
        vBar.setPosition(x + width, cy);
        vBar.setSize(t, Math.max(0f, ch));
        vBar.setThumbMinSize(thumbMinSize);

        // Horizontal bar strip occupies: y .. y + t
        // Its horizontal extent matches content clip width.
        hBar.setPosition(cx, y - t);
        hBar.setSize(Math.max(0f, cw), t);
        hBar.setThumbMinSize(thumbMinSize);

        // Feed metrics to bars.
        // For vertical: viewH is the content viewport height; contentHeight is full content height; scrollY is current scroll.
        // For horizontal: viewW is the content viewport width; contentWidth is full content width; scrollX is current scroll.
        vBar.setScrollParams(viewH, contentHeight, scrollY);
        hBar.setScrollParams(viewW, contentWidth, scrollX);
    }

    /**
     * Computes cached base positions and content bounds.
     *
     * <p>Children might already be scrolled (child = base - scroll).
     * So we reconstruct base as: base = child + scroll.</p>
     */
    private void computeBasePositionsAndContentBounds() {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            float bx = child.getX() + scrollX;
            float by = child.getY() + scrollY;

            baseX.put(child, bx);
            baseY.put(child, by);

            float x1 = bx + child.getWidth();
            float y1 = by + child.getHeight();

            if (bx < minX) minX = bx;
            if (x1 > maxX) maxX = x1;
            if (by < minY) minY = by;
            if (y1 > maxY) maxY = y1;
        }

        contentWidth = (minX == Float.POSITIVE_INFINITY) ? 0f : (maxX - minX);
        contentHeight = (minY == Float.POSITIVE_INFINITY) ? 0f : (maxY - minY);
    }

    @Override
    public void update(float delta) {
        super.update(delta);

        if (showScrollbars) {
            if (vBar != null) vBar.update(delta);
            if (hBar != null) hBar.update(delta);
        }
    }

    @Override
    public void draw() {
        if (style != null && style.getBackground() != null) {
            style.getBackground().draw(x, y, width, height);
        }

        // Children clipped by ElementContainer's scissor logic (uses clipBounds).
        super.draw();

        // Bars on top (not scrolled)
        if (showScrollbars) {
            if (vBar != null && vBar.isNeeded()) vBar.draw();
            if (hBar != null && hBar.isNeeded()) hBar.draw();
        }
    }

    // ----------------------------------------------------
    // Input forwarding (bars first)
    // ----------------------------------------------------

    @Override
    public void onMousePress(MousePressEvent event) {
        if (showScrollbars) {
            if (vBar != null && vBar.isNeeded()) vBar.onMousePress(event);
            if (hBar != null && hBar.isNeeded()) hBar.onMousePress(event);
        }
        super.onMousePress(event);
    }

    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (showScrollbars) {
            if (vBar != null && vBar.isNeeded()) vBar.onMouseDrag(event);
            if (hBar != null && hBar.isNeeded()) hBar.onMouseDrag(event);
            // If a bar is dragging, it should "win" and we should not scroll children via drag.
            if ((vBar != null && vBar.isDragging()) || (hBar != null && hBar.isDragging())) {
                return;
            }
        }
        super.onMouseDrag(event);
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (showScrollbars) {
            if (vBar != null) vBar.onMouseRelease(event);
            if (hBar != null) hBar.onMouseRelease(event);
        }
        super.onMouseRelease(event);
    }

    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        float deltaX = (float) event.xOffset() * scrollSpeed;
        float deltaY = (float) event.yOffset() * scrollSpeed;

        setScroll(scrollX + deltaX, scrollY + deltaY);

        super.onMouseScroll(event);
    }

    // ----------------------------------------------------
    // Container hooks
    // ----------------------------------------------------

    @Override
    protected void onAdd(Element element) {
        // Base caching is computed during layout().
    }

    @Override
    protected void onRemove(Element element) {
        baseX.remove(element);
        baseY.remove(element);
    }

    private void applyScrollToChildren() {
        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            Float bx = baseX.get(child);
            Float by = baseY.get(child);

            if (bx == null) bx = child.getX() + scrollX;
            if (by == null) by = child.getY() + scrollY;

            child.setPosition(bx - scrollX, by - scrollY);
            child.layout();
        }
    }

    private void clampScroll() {
        if (scrollX < 0f) scrollX = 0f;
        if (scrollY < 0f) scrollY = 0f;

        if (scrollX > maxScrollX) scrollX = maxScrollX;
        if (scrollY > maxScrollY) scrollY = maxScrollY;
    }

    public float getScrollX() {return scrollX;}

    public float getScrollY() {return scrollY;}

    public void setScrollX(float x) {
        scrollX = x;
        clampScroll();
        applyScrollToChildren();
        if (vBar != null)
            vBar.setScrollParams((clipBounds != null ? clipBounds.getHeight() : height), contentHeight, scrollY);
        if (hBar != null)
            hBar.setScrollParams((clipBounds != null ? clipBounds.getWidth() : width), contentWidth, scrollX);
    }

    public void setScrollY(float y) {
        scrollY = y;
        clampScroll();
        applyScrollToChildren();
        if (vBar != null)
            vBar.setScrollParams((clipBounds != null ? clipBounds.getHeight() : height), contentHeight, scrollY);
        if (hBar != null)
            hBar.setScrollParams((clipBounds != null ? clipBounds.getWidth() : width), contentWidth, scrollX);
    }

    public void setScroll(float x, float y) {
        scrollX = x;
        scrollY = y;
        clampScroll();
        applyScrollToChildren();
        if (vBar != null)
            vBar.setScrollParams((clipBounds != null ? clipBounds.getHeight() : height), contentHeight, scrollY);
        if (hBar != null)
            hBar.setScrollParams((clipBounds != null ? clipBounds.getWidth() : width), contentWidth, scrollX);
    }

    // Called by ScrollBar callbacks (to avoid re-clamping loops)
    private void setScrollYInternal(float y) {
        scrollY = y;
        clampScroll();
        applyScrollToChildren();
        if (vBar != null)
            vBar.setScrollParams((clipBounds != null ? clipBounds.getHeight() : height), contentHeight, scrollY);
    }

    private void setScrollXInternal(float x) {
        scrollX = x;
        clampScroll();
        applyScrollToChildren();
        if (hBar != null)
            hBar.setScrollParams((clipBounds != null ? clipBounds.getWidth() : width), contentWidth, scrollX);
    }

    public float getScrollSpeed() {
        return scrollSpeed;
    }

    public void setScrollSpeed(float scrollSpeed) {
        this.scrollSpeed = scrollSpeed;
    }

    public ScrollPanel setShowScrollbars(boolean show) {
        this.showScrollbars = show;
        if (!show) {
            this.getUI().removeElement(vBar);
            this.getUI().removeElement(hBar);
        }
        return this;
    }

    public ScrollPanel setScrollbarThickness(float pixels) {
        this.scrollbarThickness = pixels;
        return this;
    }

    public ScrollPanel setThumbMinSize(float pixels) {
        this.thumbMinSize = pixels;
        if (vBar != null) vBar.setThumbMinSize(pixels);
        if (hBar != null) hBar.setThumbMinSize(pixels);
        return this;
    }

    public ScrollBar getVerticalBar() {
        ensureBars();
        return vBar;
    }

    public ScrollBar getHorizontalBar() {
        ensureBars();
        return hBar;
    }
}
