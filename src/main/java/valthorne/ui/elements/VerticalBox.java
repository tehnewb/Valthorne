package valthorne.ui.elements;

import valthorne.event.events.WindowResizeEvent;
import valthorne.ui.*;

public class VerticalBox extends ElementContainer {

    private Alignment alignment;
    private Justify justify = Justify.START;

    public VerticalBox(Alignment alignment) {
        this.alignment = alignment;
    }

    public VerticalBox() {
        this(Alignment.CENTER_CENTER);
    }

    @Override
    protected void onAdd(Element element) {
        layout();
    }

    @Override
    protected void onRemove(Element element) {
        layout();
    }

    private void layout() {
        float fixedHeight = 0f;
        float totalExpandWeight = 0f;

        int count = 0;
        float[] baseHeights = new float[size];

        /* ---------- Measure pass ---------- */
        for (int i = 0; i < size; i++) {
            Element e = elements[i];
            if (e == null || e.isHidden()) continue;

            Layout l = e.getLayout();
            baseHeights[i] = e.getHeight();

            fixedHeight +=
                    l.getSpacingBefore()
                            + l.getPaddingTop()
                            + baseHeights[i]
                            + l.getPaddingBottom()
                            + l.getSpacingAfter();

            if (l.isExpand()) {
                totalExpandWeight += l.getExpandWeight();
            }

            count++;
        }

        float free = Math.max(0f, height - fixedHeight);

        /* ---------- Justify (vertical spacing) ---------- */
        float justifyOffset = 0f;
        float gap = 0f;

        switch (justify) {
            case START -> justifyOffset = 0f;
            case CENTER -> justifyOffset = free * 0.5f;
            case END -> justifyOffset = free;

            case SPACE_BETWEEN -> {
                gap = count > 1 ? free / (count - 1) : 0f;
            }

            case SPACE_AROUND -> {
                gap = count > 0 ? free / count : 0f;
                justifyOffset = gap * 0.5f;
            }

            case SPACE_EVENLY -> {
                gap = count > 0 ? free / (count + 1) : 0f;
                justifyOffset = gap;
            }
        }

        float occupiedHeight = fixedHeight + gap * Math.max(0, count - 1);

        /* ---------- Alignment (group placement) ---------- */
        float alignmentOffset = switch (alignment.getVertical()) {
            case TOP -> 0f;
            case CENTER -> (height - occupiedHeight) * 0.5f;
            case BOTTOM -> height - occupiedHeight;
        };

        /* ---------- Start at top, move downward ---------- */
        float yCursor = y + height - alignmentOffset - justifyOffset;

        /* ---------- Layout pass ---------- */
        for (int i = 0; i < size; i++) {
            Element e = elements[i];
            if (e == null || e.isHidden()) continue;

            Layout l = e.getLayout();

            yCursor -= l.getSpacingBefore();

            float extra = 0f;
            if (l.isExpand() && totalExpandWeight > 0f) {
                extra = free * (l.getExpandWeight() / totalExpandWeight);
            }

            float finalHeight = baseHeights[i] + extra;

            e.setSize(Math.min(e.getWidth(), this.width), finalHeight);

            float xPos = switch (alignment.getHorizontal()) {
                case LEFT -> x + l.getPaddingLeft();
                case CENTER -> x + (width - e.getWidth()) * 0.5f;
                case RIGHT -> x + width - e.getWidth() - l.getPaddingRight();
            };

            yCursor -= l.getPaddingTop() + finalHeight;

            e.setPosition(xPos, yCursor);

            yCursor -= l.getPaddingBottom() + l.getSpacingAfter() + gap;
        }
    }

    @Override
    public void onWindowResize(WindowResizeEvent event) {
        super.onWindowResize(event);
        layout();
    }

    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        layout();
    }

    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        layout();
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
        layout();
    }

    public Justify getJustify() {
        return justify;
    }

    public void setJustify(Justify justify) {
        this.justify = justify;
        layout();
    }
}
