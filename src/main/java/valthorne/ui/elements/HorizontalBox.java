package valthorne.ui.elements;

import valthorne.event.events.WindowResizeEvent;
import valthorne.ui.*;

public class HorizontalBox extends ElementContainer {

    private Alignment alignment;
    private Justify justify = Justify.START;

    public HorizontalBox(Alignment alignment) {
        this.alignment = alignment;
    }

    public HorizontalBox() {
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
        float fixedWidth = 0f;
        float totalExpandWeight = 0f;

        int count = 0;
        float[] baseWidths = new float[size];

        for (int i = 0; i < size; i++) {
            Element e = elements[i];
            if (e == null || e.isHidden()) continue;

            Layout l = e.getLayout();
            baseWidths[i] = e.getWidth();

            fixedWidth += l.getSpacingBefore() + l.getPaddingLeft() + baseWidths[i] + l.getPaddingRight() + l.getSpacingAfter();

            if (l.isExpand()) totalExpandWeight += l.getExpandWeight();

            count++;
        }

        float free = Math.max(0f, width - fixedWidth);

        float offset = 0f;
        float gap = 0f;

        switch (justify) {
            case START -> offset = 0f;
            case CENTER -> offset = free * 0.5f;
            case END -> offset = free;
            case SPACE_BETWEEN -> gap = count > 1 ? free / (count - 1) : 0f;
            case SPACE_AROUND -> gap = count > 0 ? free / count : 0f;
            case SPACE_EVENLY -> gap = count > 0 ? free / (count + 1) : 0f;
        }

        float xCursor = x + offset + (justify == Justify.SPACE_EVENLY ? gap : 0f);

        for (int i = 0; i < size; i++) {
            Element e = elements[i];
            if (e == null || e.isHidden()) continue;

            Layout l = e.getLayout();

            xCursor += l.getSpacingBefore();

            float extra = 0f;
            if (l.isExpand() && totalExpandWeight > 0f) {
                extra = free * (l.getExpandWeight() / totalExpandWeight);
            }

            float finalWidth = baseWidths[i] + extra;
            e.setSize(finalWidth, Math.min(e.getHeight(), this.height));

            float yPos = switch (alignment.getVertical()) {
                case TOP -> y + height - e.getHeight() - l.getPaddingTop();
                case CENTER -> y + (height - e.getHeight()) * 0.5f;
                case BOTTOM -> y + l.getPaddingBottom();
            };

            e.setPosition(xCursor + l.getPaddingLeft(), yPos);

            xCursor += l.getPaddingLeft() + finalWidth + l.getPaddingRight() + l.getSpacingAfter() + gap;
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
