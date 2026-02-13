package valthorne.ui.elements;

import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Drawable;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Rectangle;
import valthorne.ui.Element;
import valthorne.ui.styles.ScrollBarStyle;
import valthorne.viewport.Viewport;

import static org.lwjgl.opengl.GL11.*;

/**
 * A standalone scrollbar element (vertical or horizontal) with a draggable thumb.
 *
 * <p>This scrollbar does not know anything about a specific container. A parent (ex: ScrollPanel)
 * feeds it viewport/content sizes and current scroll via {@link #setScrollParams(float, float, float)}.</p>
 *
 * <p>Coordinate system assumption: bottom-left origin, +Y up.</p>
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public class ScrollBar extends Element {

    public enum Orientation {
        VERTICAL,
        HORIZONTAL
    }

    /**
     * Callback used to apply scroll changes back into the owning container.
     */
    public interface ScrollCallback {
        void onScroll(float newScroll);
    }

    private ScrollBarStyle style;

    private Orientation orientation = Orientation.VERTICAL;

    // Provided by parent
    private float viewSize;
    private float contentSize;
    private float maxScroll;
    private float scroll;
    private float thumbMinSize = 18f;

    // Track + thumb rects in WORLD space
    private final Rectangle track = new Rectangle(0, 0, 0, 0);
    private final Rectangle thumb = new Rectangle(0, 0, 0, 0);

    // Drag state
    private boolean dragging;
    private float dragOffsetInThumb;

    private ScrollCallback onScroll;

    public ScrollBar(ScrollBarStyle style) {
        this.style = style;
        this.setFocusable(false);
        this.setScrollable(true);
        this.setClickThrough(false);
    }

    public ScrollBar setOrientation(Orientation orientation) {
        this.orientation = (orientation == null) ? Orientation.VERTICAL : orientation;
        rebuildGeometry();
        return this;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public ScrollBar setThumbMinSize(float thumbMinSize) {
        this.thumbMinSize = thumbMinSize;
        rebuildGeometry();
        return this;
    }

    public ScrollBar setStyle(ScrollBarStyle style) {
        this.style = style;
        return this;
    }

    public ScrollBarStyle getStyle() {
        return style;
    }

    public ScrollBar setOnScroll(ScrollCallback onScroll) {
        this.onScroll = onScroll;
        return this;
    }

    /**
     * Parent supplies scroll metrics.
     *
     * @param viewSize    viewport size (height for vertical, width for horizontal)
     * @param contentSize content size (height for vertical, width for horizontal)
     * @param scroll      current scroll in pixels
     */
    public void setScrollParams(float viewSize, float contentSize, float scroll) {
        this.viewSize = Math.max(0f, viewSize);
        this.contentSize = Math.max(0f, contentSize);

        this.maxScroll = Math.max(0f, this.contentSize - this.viewSize);
        this.scroll = MathUtils.clamp(scroll, 0f, this.maxScroll);

        rebuildGeometry();
    }

    public float getScroll() {
        return scroll;
    }

    public float getMaxScroll() {
        return maxScroll;
    }

    public boolean isNeeded() {
        return maxScroll > 0f;
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public void draw() {
        if (!isNeeded()) return;

        Drawable barDrawable = (style != null) ? style.getBar() : null;
        Drawable thumbDrawable = (style != null) ? style.getThumb() : null;

        if (barDrawable != null) {
            barDrawable.draw(track.getX(), track.getY(), track.getWidth(), track.getHeight());
        } else {
            drawSolidRect(track.getX(), track.getY(), track.getWidth(), track.getHeight(), 1f, 1f, 1f, 0.12f);
        }

        if (thumbDrawable != null) {
            thumbDrawable.draw(thumb.getX(), thumb.getY(), thumb.getWidth(), thumb.getHeight());
        } else {
            drawSolidRect(thumb.getX(), thumb.getY(), thumb.getWidth(), thumb.getHeight(), 1f, 1f, 1f, 0.8f);
        }
    }

    @Override
    public void onMousePress(MousePressEvent event) {
        if (!isNeeded()) return;

        Vector2f w = screenToWorld(event.getX(), event.getY());
        if (w == null) {
            return;
        }

        float mx = w.getX();
        float my = w.getY();

        dragging = false;

        if (thumb.contains(mx, my)) {
            dragging = true;
            dragOffsetInThumb = (orientation == Orientation.VERTICAL) ? (my - thumb.getY()) : (mx - thumb.getX());
            return;
        }

        if (track.contains(mx, my)) {
            System.out.println("[ScrollBar.onMousePress] HIT track. dragging=false");
            if (orientation == Orientation.VERTICAL) {
                pageToY(my);
                dragOffsetInThumb = thumb.getHeight() * 0.5f;
            } else {
                pageToX(mx);
                dragOffsetInThumb = thumb.getWidth() * 0.5f;
            }
            dragging = true;
        }
    }

    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (!dragging) return;

        Vector2f w = screenToWorld(event.getToX(), event.getToY());
        if (w == null)
            return;

        if (orientation == Orientation.VERTICAL) dragToY(w.getY());
        else dragToX(w.getX());
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        dragging = false;
    }

    // -----------------------------
    // Geometry + mapping
    // -----------------------------

    private void rebuildGeometry() {
        float px = x;
        float py = y;
        float pw = Math.max(0f, width);
        float ph = Math.max(0f, height);

        track.setX(px);
        track.setY(py);
        track.setWidth(pw);
        track.setHeight(ph);

        if (!isNeeded()) {
            thumb.setX(px);
            thumb.setY(py);
            thumb.setWidth(pw);
            thumb.setHeight(ph);
            return;
        }

        if (orientation == Orientation.VERTICAL) {
            float thumbH = calcThumbSize(viewSize, contentSize, ph);
            float range = Math.max(0f, ph - thumbH);

            float t = (maxScroll <= 0f) ? 0f : (scroll / maxScroll);
            t = MathUtils.clamp(t, 0f, 1f);

            float thumbY = py + t * range;

            thumb.setX(px);
            thumb.setY(thumbY);
            thumb.setWidth(pw);
            thumb.setHeight(thumbH);
        } else {
            float thumbW = calcThumbSize(viewSize, contentSize, pw);
            float range = Math.max(0f, pw - thumbW);

            float t = (maxScroll <= 0f) ? 0f : (scroll / maxScroll);
            t = MathUtils.clamp(t, 0f, 1f);

            float thumbX = px + t * range;

            thumb.setX(thumbX);
            thumb.setY(py);
            thumb.setWidth(thumbW);
            thumb.setHeight(ph);
        }
    }

    private float calcThumbSize(float viewSize, float contentSize, float trackSize) {
        if (contentSize <= 0f) return trackSize;
        if (contentSize <= viewSize) return trackSize;

        float ratio = viewSize / contentSize;
        float size = trackSize * ratio;

        if (size < thumbMinSize) size = thumbMinSize;
        if (size > trackSize) size = trackSize;

        return size;
    }

    private void dragToY(float mouseY) {
        float trackY = track.getY();
        float trackH = track.getHeight();
        float thumbH = thumb.getHeight();

        float range = Math.max(0f, trackH - thumbH);
        if (range <= 0f || maxScroll <= 0f) return;

        float desiredThumbY = mouseY - dragOffsetInThumb;
        desiredThumbY = MathUtils.clamp(desiredThumbY, trackY, trackY + range);

        float t = (desiredThumbY - trackY) / range;
        float newScroll = t * maxScroll;

        setScrollInternal(newScroll);
    }

    private void dragToX(float mouseX) {
        float trackX = track.getX();
        float trackW = track.getWidth();
        float thumbW = thumb.getWidth();

        float range = Math.max(0f, trackW - thumbW);
        if (range <= 0f || maxScroll <= 0f) return;

        float desiredThumbX = mouseX - dragOffsetInThumb;
        desiredThumbX = MathUtils.clamp(desiredThumbX, trackX, trackX + range);

        float t = (desiredThumbX - trackX) / range;
        float newScroll = t * maxScroll;

        setScrollInternal(newScroll);
    }

    private void pageToY(float mouseY) {
        float thumbCenter = thumb.getY() + thumb.getHeight() * 0.5f;
        float page = height;

        float newScroll = (mouseY > thumbCenter) ? (scroll + page) : (scroll - page);

        setScrollInternal(newScroll);
    }

    private void pageToX(float mouseX) {
        float thumbCenter = thumb.getX() + thumb.getWidth() * 0.5f;
        float page = width;

        float newScroll = (mouseX > thumbCenter) ? (scroll + page) : (scroll - page);

        setScrollInternal(newScroll);
    }

    private void setScrollInternal(float newScroll) {
        float clamped = MathUtils.clamp(newScroll, 0f, maxScroll);
        this.scroll = clamped;
        rebuildGeometry();

        if (onScroll != null) onScroll.onScroll(clamped);
    }

    private Vector2f screenToWorld(int sx, int sy) {
        if (getUI() == null || getUI().getViewport() == null)
            return null;
        Viewport vp = getUI().getViewport();
        return vp.screenToWorld(sx, sy);
    }

    private void drawSolidRect(float rx, float ry, float rw, float rh, float r, float g, float b, float a) {
        glDisable(GL_TEXTURE_2D);
        glColor4f(r, g, b, a);

        glBegin(GL_QUADS);
        glVertex2f(rx, ry);
        glVertex2f(rx + rw, ry);
        glVertex2f(rx + rw, ry + rh);
        glVertex2f(rx, ry + rh);
        glEnd();

        glEnable(GL_TEXTURE_2D);
    }

    public boolean isDragging() {
        return dragging;
    }
}