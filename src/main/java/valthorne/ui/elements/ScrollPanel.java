package valthorne.ui.elements;

import valthorne.event.events.MouseScrollEvent;
import valthorne.math.geometry.Rectangle;
import valthorne.ui.Element;
import valthorne.ui.ElementContainer;

import java.util.HashMap;
import java.util.Map;

public class ScrollPanel extends ElementContainer {

    private float scrollX, scrollY;
    private float contentWidth, contentHeight;
    private float maxScrollX, maxScrollY;

    private float scrollSpeed = 40f;

    private final Map<Element, Float> baseX = new HashMap<>();
    private final Map<Element, Float> baseY = new HashMap<>();

    private Rectangle clipBounds;
    private boolean initialLayout = true;

    public ScrollPanel() {
        this.setFocusable(true);
        this.setScrollable(true);
    }

    @Override
    public void layout() {
        super.layout();

        if (clipBounds == null)
            clipBounds = new Rectangle(x, y, width, height);

        if (clipBounds.getX() != x || clipBounds.getY() != y || clipBounds.getWidth() != width || clipBounds.getHeight() != height) {
            clipBounds.setX(x);
            clipBounds.setY(y);
            clipBounds.setWidth(width);
            clipBounds.setHeight(height);
        }

        this.setClipBounds(clipBounds);

        computeBasePositionsAndContentBounds();

        float viewW = clipBounds.getWidth();
        float viewH = clipBounds.getHeight();

        maxScrollX = Math.max(0f, contentWidth - viewW);
        maxScrollY = Math.max(0f, contentHeight - viewH);

        clampScroll();
        applyScrollToChildren();

        if (initialLayout) {
            this.initialLayout = false;

            setScrollY(maxScrollY);
        }
    }

    /**
     * Computes cached base positions and content bounds.
     *
     * <p>Children might already be scrolled (child = base - scroll).
     * So we reconstruct base as: base = child + scroll.
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
    public void draw() {
        super.draw();

        // TODO scrollbar
    }

    @Override
    protected void onAdd(Element element) {

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
    }

    public void setScrollY(float y) {
        scrollY = y;
        clampScroll();
        applyScrollToChildren();
    }

    public void setScroll(float x, float y) {
        scrollX = x;
        scrollY = y;
        clampScroll();
        applyScrollToChildren();
    }

    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        float deltaX = (float) event.xOffset() * scrollSpeed;
        float deltaY = (float) event.yOffset() * scrollSpeed;

        setScroll(scrollX + deltaX, scrollY + deltaY);
    }

    public float getScrollSpeed() {
        return scrollSpeed;
    }

    public void setScrollSpeed(float scrollSpeed) {
        this.scrollSpeed = scrollSpeed;
    }
}
