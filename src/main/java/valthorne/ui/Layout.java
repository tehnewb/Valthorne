package valthorne.ui;

public final class Layout {

    private float paddingLeft;
    private float paddingRight;
    private float paddingTop;
    private float paddingBottom;

    private float spacingBefore;
    private float spacingAfter;

    private boolean expand;
    private float expandWeight = 1f;

    public Layout() {}

    public float getPaddingLeft() {
        return paddingLeft;
    }

    public float getPaddingRight() {
        return paddingRight;
    }

    public float getPaddingTop() {
        return paddingTop;
    }

    public float getPaddingBottom() {
        return paddingBottom;
    }

    public float getVerticalPadding() {
        return paddingTop + paddingBottom;
    }

    public float getHorizontalPadding() {
        return paddingLeft + paddingRight;
    }

    public Layout setPadding(float left, float right, float top, float bottom) {
        this.paddingLeft = left;
        this.paddingRight = right;
        this.paddingTop = top;
        this.paddingBottom = bottom;
        return this;
    }

    public Layout setPadding(float all) {
        return setPadding(all, all, all, all);
    }

    public float getSpacingBefore() {
        return spacingBefore;
    }

    public float getSpacingAfter() {
        return spacingAfter;
    }

    public Layout setSpacing(float before, float after) {
        this.spacingBefore = before;
        this.spacingAfter = after;
        return this;
    }

    public boolean isExpand() {
        return expand;
    }

    public float getExpandWeight() {
        return expandWeight;
    }

    public Layout setExpand(boolean expand) {
        this.expand = expand;
        return this;
    }

    public Layout setExpand(boolean expand, float weight) {
        this.expand = expand;
        this.expandWeight = weight;
        return this;
    }
}
