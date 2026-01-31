package valthorne.ui.flex;

import valthorne.ui.*;

/**
 * A simple FlexBox-style container that positions children in a row or column with gap + wrap.
 * <p>
 * Adds:
 * <ul>
 *   <li><b>Justify</b> on the main axis (start/center/end/space-between/space-around/space-evenly)</li>
 *   <li><b>Align</b> on the cross axis (start/center/end)</li>
 * </ul>
 * <p>
 * Notes for your coordinate system:
 * <ul>
 *   <li>X increases to the right.</li>
 *   <li>Y increases upward.</li>
 *   <li>COLUMN stacks from top -> down.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since January 28th, 2026
 */
public class FlexBox extends ElementContainer {

    private FlexDirection flexDirection = FlexDirection.ROW;
    private float gap;
    private boolean wrap;

    private JustifyContent justifyContent = JustifyContent.START;
    private AlignItems alignItems = AlignItems.START;

    @Override
    protected void onAdd(Element element) {}

    @Override
    protected void onRemove(Element element) {}

    @Override
    public void layout() {
        // Resolve this container (margins/padding handled by Element.layout()).
        super.layout();

        // Inner content box (padding pushes inward).
        float innerX = this.x;
        float innerY = this.y;
        float innerW = this.width;
        float innerH = this.height;

        float padL = 0, padR = 0, padT = 0, padB = 0;

        Layout l = getLayout();
        if (l != null) {
            Value lp = l.getLeftPadding();
            Value rp = l.getRightPadding();
            Value tp = l.getTopPadding();
            Value bp = l.getBottomPadding();

            if (lp.type() != ValueType.AUTO) padL = lp.type().resolve(lp.number(), 0, innerW, 0);
            if (rp.type() != ValueType.AUTO) padR = rp.type().resolve(rp.number(), 0, innerW, 0);
            if (tp.type() != ValueType.AUTO) padT = tp.type().resolve(tp.number(), 0, innerH, 0);
            if (bp.type() != ValueType.AUTO) padB = bp.type().resolve(bp.number(), 0, innerH, 0);
        }

        innerX += padL;
        innerY += padT;
        innerW -= (padL + padR);
        innerH -= (padT + padB);

        if (innerW < 0) innerW = 0;
        if (innerH < 0) innerH = 0;

        if (flexDirection == FlexDirection.ROW) {
            layoutRow(innerX, innerY, innerW, innerH);
        } else {
            layoutColumn(innerX, innerY, innerW, innerH);
        }
    }

    private void layoutRow(float innerX, float innerY, float innerW, float innerH) {
        // Collect visible children and resolve their sizes first.
        int count = 0;
        float totalW = 0f;
        float maxH = 0f;

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            child.layout();

            float cw = child.getWidth();
            float ch = child.getHeight();

            totalW += cw;
            if (ch > maxH) maxH = ch;
            count++;
        }

        if (count == 0) return;

        // If wrap is enabled, we fall back to the old wrapping behavior (justify is per-line, which we don't do here).
        // This keeps the implementation simple and predictable.
        if (wrap) {
            layoutRowWrapped(innerX, innerY, innerW, innerH);
            return;
        }

        float totalGap = gap * (count - 1);
        float contentW = totalW + totalGap;

        // Compute main-axis starting X and effective gap based on justify.
        float startX = innerX;
        float effectiveGap = gap;

        if (count == 1) {
            // Space-* don't make sense with 1 item; treat as CENTER/START/END only.
            startX = switch (justifyContent) {
                case CENTER -> innerX + (innerW - totalW) * 0.5f;
                case END -> innerX + (innerW - totalW);
                default -> innerX;
            };
        } else {
            float free = innerW - contentW;
            if (free < 0) free = 0;

            switch (justifyContent) {
                case START -> {
                    startX = innerX;
                    effectiveGap = gap;
                }
                case CENTER -> {
                    startX = innerX + free * 0.5f;
                    effectiveGap = gap;
                }
                case END -> {
                    startX = innerX + free;
                    effectiveGap = gap;
                }
                case SPACE_BETWEEN -> {
                    startX = innerX;
                    effectiveGap = (innerW - totalW) / (count - 1);
                }
                case SPACE_AROUND -> {
                    float space = (innerW - totalW) / count;
                    startX = innerX + space * 0.5f;
                    effectiveGap = space;
                }
                case SPACE_EVENLY -> {
                    float space = (innerW - totalW) / (count + 1);
                    startX = innerX + space;
                    effectiveGap = space;
                }
            }
        }

        float x = startX;

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            float cw = child.getWidth();
            float ch = child.getHeight();

            float y = switch (alignItems) {
                case START -> innerY + (innerH - ch);          // top
                case CENTER -> innerY + (innerH - ch) * 0.5f; // middle
                case END -> innerY;                           // bottom
            };

            child.setPosition(x, y);
            x += cw + effectiveGap;
        }
    }

    private void layoutRowWrapped(float innerX, float innerY, float innerW, float innerH) {
        float cx = innerX;
        float cyTop = innerY + innerH;
        float lineH = 0f;

        // Wrap rows go downward (new row below).
        float penYTop = cyTop;

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            child.layout();

            float cw = child.getWidth();
            float ch = child.getHeight();

            if (cx > innerX && (cx + cw) > (innerX + innerW)) {
                cx = innerX;
                penYTop -= (lineH + gap);
                lineH = 0f;
            }

            // Align in the row (cross axis = vertical) within this row height.
            if (ch > lineH) lineH = ch;

            float rowBottom = penYTop - lineH;

            float y = switch (alignItems) {
                case START -> penYTop - ch;                         // top within row
                case CENTER -> rowBottom + (lineH - ch) * 0.5f;      // centered in row
                case END -> rowBottom;                               // bottom within row
            };

            child.setPosition(cx, y);
            cx += cw + gap;
        }
    }

    private void layoutColumn(float innerX, float innerY, float innerW, float innerH) {
        // Collect visible children and resolve their sizes first.
        int count = 0;
        float totalH = 0f;
        float maxW = 0f;

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            child.layout();

            float cw = child.getWidth();
            float ch = child.getHeight();

            totalH += ch;
            if (cw > maxW) maxW = cw;
            count++;
        }

        if (count == 0) return;

        // If wrap enabled, we fall back to old column wrapping behavior (justify per-column not implemented).
        if (wrap) {
            layoutColumnWrapped(innerX, innerY, innerW, innerH);
            return;
        }

        float totalGap = gap * (count - 1);
        float contentH = totalH + totalGap;

        // Main axis in COLUMN is vertical, and we stack top->down.
        float topY = innerY + innerH; // top edge
        float startTopY = topY;
        float effectiveGap = gap;

        if (count == 1) {
            startTopY = switch (justifyContent) {
                case CENTER -> topY - (innerH - totalH) * 0.5f;
                case END -> topY - (innerH - totalH);
                default -> topY;
            };
        } else {
            float free = innerH - contentH;
            if (free < 0) free = 0;

            switch (justifyContent) {
                case START -> {
                    startTopY = topY;
                    effectiveGap = gap;
                }
                case CENTER -> {
                    startTopY = topY - free * 0.5f;
                    effectiveGap = gap;
                }
                case END -> {
                    startTopY = topY - free;
                    effectiveGap = gap;
                }
                case SPACE_BETWEEN -> {
                    startTopY = topY;
                    effectiveGap = (innerH - totalH) / (count - 1);
                }
                case SPACE_AROUND -> {
                    float space = (innerH - totalH) / count;
                    startTopY = topY - space * 0.5f;
                    effectiveGap = space;
                }
                case SPACE_EVENLY -> {
                    float space = (innerH - totalH) / (count + 1);
                    startTopY = topY - space;
                    effectiveGap = space;
                }
            }
        }

        float penTopY = startTopY;

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            float cw = child.getWidth();
            float ch = child.getHeight();

            // Cross axis in COLUMN is horizontal.
            float x = switch (alignItems) {
                case START -> innerX;                        // left
                case CENTER -> innerX + (innerW - cw) * 0.5f; // centered
                case END -> innerX + (innerW - cw);           // right
            };

            float y = penTopY - ch; // place downward
            child.setPosition(x, y);

            penTopY = y - effectiveGap;
        }
    }

    private void layoutColumnWrapped(float innerX, float innerY, float innerW, float innerH) {
        float cx = innerX;
        float cyTop = innerY + innerH;
        float penY = cyTop;
        float colW = 0f;

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            child.layout();

            float cw = child.getWidth();
            float ch = child.getHeight();

            float nextBottom = penY - ch;
            if (penY < cyTop && nextBottom < innerY) {
                penY = cyTop;
                cx += colW + gap;
                colW = 0f;
            }

            float x = switch (alignItems) {
                case START -> cx;
                case CENTER -> cx + (colW - cw) * 0.5f; // best-effort within current colW
                case END -> cx + (colW - cw);
            };

            float childY = penY - ch;
            child.setPosition(x, childY);

            penY = childY - gap;

            if (cw > colW) colW = cw;
        }
    }

    public FlexDirection getFlexDirection() {
        return flexDirection;
    }

    public FlexBox setFlexDirection(FlexDirection flexDirection) {
        this.flexDirection = flexDirection;
        return this;
    }

    public float getGap() {
        return gap;
    }

    public FlexBox setGap(float gap) {
        this.gap = gap;
        return this;
    }

    public boolean isWrap() {
        return wrap;
    }

    public FlexBox setWrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    public JustifyContent getJustifyContent() {
        return justifyContent;
    }

    public FlexBox setJustifyContent(JustifyContent justifyContent) {
        this.justifyContent = justifyContent;
        return this;
    }

    public AlignItems getAlignItems() {
        return alignItems;
    }

    public FlexBox setAlignItems(AlignItems alignItems) {
        this.alignItems = alignItems;
        return this;
    }
}
