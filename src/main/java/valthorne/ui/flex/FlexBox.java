package valthorne.ui.flex;

import valthorne.ui.*;
import valthorne.ui.enums.AlignItems;
import valthorne.ui.enums.JustifyContent;

/**
 * A FlexBox-style layout container that positions child {@link Element}s in a single row or column,
 * with optional wrapping and configurable spacing/alignment rules.
 *
 * <p>This container is inspired by CSS Flexbox concepts, but is tailored to your UI coordinate system
 * and your {@link Element}/{@link ElementContainer} layout flow.</p>
 *
 * <h2>What this container does</h2>
 * <ul>
 *   <li><b>Main axis layout</b> (ROW or COLUMN) using {@link #flexDirection}</li>
 *   <li><b>Gap spacing</b> between items via {@link #gap}</li>
 *   <li><b>Main-axis distribution</b> via {@link #justifyContent}:
 *     <ul>
 *       <li>START / CENTER / END</li>
 *       <li>SPACE_BETWEEN / SPACE_AROUND / SPACE_EVENLY</li>
 *     </ul>
 *   </li>
 *   <li><b>Cross-axis alignment</b> via {@link #alignItems}: START / CENTER / END</li>
 *   <li><b>Wrapping</b> via {@link #wrap}:
 *     <ul>
 *       <li>ROW wrap creates multiple horizontal lines flowing downward</li>
 *       <li>COLUMN wrap creates multiple vertical columns flowing to the right</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Coordinate system assumptions</h2>
 * <ul>
 *   <li>X increases to the right.</li>
 *   <li>Y increases upward.</li>
 *   <li>ROW places items left → right.</li>
 *   <li>COLUMN places items top → down.</li>
 * </ul>
 *
 * <h2>Layout pipeline</h2>
 * <ul>
 *   <li>{@link #layout()} calls {@link ElementContainer#layout()} first, so the container's own
 *       margins/padding and size resolution occur before arranging children.</li>
 *   <li>The container then computes an <b>inner content box</b> by subtracting padding and uses
 *       that region for item placement.</li>
 *   <li>Each visible child is laid out once (via {@link Element#layout()}) before measurement.</li>
 * </ul>
 *
 * <h2>Wrapping notes</h2>
 * <p>When wrapping is enabled, this implementation uses a simple, predictable flow algorithm and
 * does <b>not</b> apply {@link #justifyContent} per-line/per-column. This mirrors the current code:
 * wrapping delegates to separate wrapped layout methods and keeps justification behavior limited
 * to non-wrapped mode.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * FlexBox bar = new FlexBox()
 *     .setFlexDirection(FlexDirection.ROW)
 *     .setGap(12)
 *     .setWrap(false)
 *     .setJustifyContent(JustifyContent.SPACE_BETWEEN)
 *     .setAlignItems(AlignItems.CENTER);
 *
 * bar.setPosition(20, 20);
 * bar.setSize(600, 64);
 *
 * bar.add(new Button("Back"));
 * bar.add(new Button("Settings"));
 * bar.add(new Button("Play"));
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 3rd, 2026
 */
public class FlexBox extends ElementContainer {

    private FlexDirection flexDirection = FlexDirection.ROW; // Main axis direction: ROW (x) or COLUMN (y).
    private float gap;                                       // Spacing between items on the main axis (and between lines when wrapped).
    private boolean wrap;                                    // If true, items wrap to new lines/columns when they overflow.

    private JustifyContent justifyContent = JustifyContent.START; // Main-axis distribution behavior (start/center/end/space-*).
    private AlignItems alignItems = AlignItems.START;             // Cross-axis alignment behavior (start/center/end; stretch currently not implemented).

    /**
     * Called when a child element is added to this container.
     *
     * <p>This implementation is intentionally empty. Override if you need to:
     * track child metadata, invalidate cached line measurements, or apply defaults.</p>
     *
     * @param element the element that was added
     */
    @Override
    protected void onAdd(Element element) {}

    /**
     * Called when a child element is removed from this container.
     *
     * <p>This implementation is intentionally empty. Override if you need cleanup logic
     * such as removing per-child layout state.</p>
     *
     * @param element the element that was removed
     */
    @Override
    protected void onRemove(Element element) {}

    /**
     * Lays out this container and positions its child elements according to flex rules.
     *
     * <p>Step-by-step:</p>
     * <ol>
     *   <li>Calls {@link ElementContainer#layout()} to resolve this container's own layout metrics.</li>
     *   <li>Computes the inner content box by subtracting padding from this element's bounds.</li>
     *   <li>Dispatches to {@link #layoutRow(float, float, float, float)} or
     *       {@link #layoutColumn(float, float, float, float)} depending on {@link #flexDirection}.</li>
     * </ol>
     *
     * <p>Padding behavior:</p>
     * <ul>
     *   <li>Padding values are read from {@link Layout} and resolved via {@link ValueType#resolve(float, float, float, float)}.</li>
     *   <li>AUTO padding is treated as 0.</li>
     * </ul>
     *
     * <p>Clamping:</p>
     * <ul>
     *   <li>If inner width/height becomes negative after padding, it is clamped to 0.</li>
     * </ul>
     */
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

    /**
     * Lays out children in a single horizontal line (left → right) when {@link #flexDirection} is ROW.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Calls {@link Element#layout()} for each visible child to ensure sizes are resolved.</li>
     *   <li>If {@link #wrap} is enabled, delegates to {@link #layoutRowWrapped(float, float, float, float)}.</li>
     *   <li>If wrap is disabled, computes total content width and distributes free space according
     *       to {@link #justifyContent}.</li>
     *   <li>Aligns items vertically according to {@link #alignItems} within the inner height.</li>
     * </ul>
     *
     * <p>Justify behavior (no wrap):</p>
     * <ul>
     *   <li>START: items begin at innerX</li>
     *   <li>CENTER: items are centered as a group</li>
     *   <li>END: items are right-aligned as a group</li>
     *   <li>SPACE_BETWEEN: gap is expanded so first/last touch edges</li>
     *   <li>SPACE_AROUND: equal space around each item (half-space at ends)</li>
     *   <li>SPACE_EVENLY: equal space including ends</li>
     * </ul>
     *
     * <p>Cross-axis alignment:</p>
     * <ul>
     *   <li>START maps to "top" within your coordinate system (higher Y)</li>
     *   <li>CENTER centers vertically</li>
     *   <li>END maps to "bottom"</li>
     *   <li>STRETCH is currently a placeholder (does not resize children)</li>
     * </ul>
     *
     * @param innerX left edge of inner content box
     * @param innerY bottom edge of inner content box
     * @param innerW width of inner content box
     * @param innerH height of inner content box
     */
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
                case STRETCH -> 0.0F;                         // TODO: implement child stretching on cross axis
                case END -> innerY;                           // bottom
            };

            child.setPosition(x, y);
            x += cw + effectiveGap;
        }
    }

    /**
     * Lays out children in wrapped rows (left → right, then new line below).
     *
     * <p>Wrapping behavior:</p>
     * <ul>
     *   <li>Items are placed sequentially until the next item would exceed the inner width</li>
     *   <li>When overflow would occur (and we're not at the start of a row), a new row is started</li>
     *   <li>Rows progress downward by {@code lineH + gap}</li>
     * </ul>
     *
     * <p>Cross-axis (row) alignment:</p>
     * <ul>
     *   <li>Uses {@link #alignItems} to position each item within the current row height</li>
     *   <li>STRETCH is currently a placeholder and does not resize children</li>
     * </ul>
     *
     * <p>Justify note:</p>
     * <p>This wrapped implementation does not apply {@link #justifyContent} per-row. Items are placed
     * with a fixed {@link #gap} and start at the left edge of the inner box.</p>
     *
     * @param innerX left edge of inner content box
     * @param innerY bottom edge of inner content box
     * @param innerW width of inner content box
     * @param innerH height of inner content box
     */
    private void layoutRowWrapped(float innerX, float innerY, float innerW, float innerH) {
        float cx = innerX;            // Current pen X in the active row.
        float cyTop = innerY + innerH; // Top edge of the inner content box.
        float lineH = 0f;             // Height of the current row (max child height in row).

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
                case START -> penYTop - ch;                    // top within row
                case CENTER -> rowBottom + (lineH - ch) * 0.5f; // centered in row
                case STRETCH -> 0.0F;                          // TODO: implement stretching within row
                case END -> rowBottom;                          // bottom within row
            };

            child.setPosition(cx, y);
            cx += cw + gap;
        }
    }

    /**
     * Lays out children in a single vertical stack (top → down) when {@link #flexDirection} is COLUMN.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Calls {@link Element#layout()} for each visible child to ensure sizes are resolved.</li>
     *   <li>If {@link #wrap} is enabled, delegates to {@link #layoutColumnWrapped(float, float, float, float)}.</li>
     *   <li>If wrap is disabled, computes total content height and distributes free space according
     *       to {@link #justifyContent}.</li>
     *   <li>Aligns items horizontally according to {@link #alignItems} within the inner width.</li>
     * </ul>
     *
     * <p>Main axis in COLUMN is vertical:</p>
     * <ul>
     *   <li>START begins at the top edge</li>
     *   <li>END begins at the bottom edge</li>
     *   <li>SPACE_* modifies the effective gap between items</li>
     * </ul>
     *
     * <p>Cross-axis in COLUMN is horizontal:</p>
     * <ul>
     *   <li>START = left, CENTER = centered, END = right</li>
     *   <li>STRETCH is currently a placeholder (does not resize children)</li>
     * </ul>
     *
     * @param innerX left edge of inner content box
     * @param innerY bottom edge of inner content box
     * @param innerW width of inner content box
     * @param innerH height of inner content box
     */
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
                case START -> innerX;                         // left
                case CENTER -> innerX + (innerW - cw) * 0.5f;  // centered
                case STRETCH -> 0.0F;                         // TODO: implement child stretching on cross axis
                case END -> innerX + (innerW - cw);            // right
            };

            float y = penTopY - ch; // place downward
            child.setPosition(x, y);

            penTopY = y - effectiveGap;
        }
    }

    /**
     * Lays out children in wrapped columns (top → down, then new column to the right).
     *
     * <p>Wrapping behavior:</p>
     * <ul>
     *   <li>Items are stacked downward until the next item would drop below {@code innerY}</li>
     *   <li>When overflow would occur (and we're not at the top), a new column is started</li>
     *   <li>Columns progress rightward by {@code colW + gap}</li>
     * </ul>
     *
     * <p>Cross-axis (column) alignment:</p>
     * <ul>
     *   <li>Uses {@link #alignItems} to position each item within the current column width (best effort)</li>
     *   <li>STRETCH is currently a placeholder and does not resize children</li>
     * </ul>
     *
     * <p>Justify note:</p>
     * <p>This wrapped implementation does not apply {@link #justifyContent} per-column. Items are placed
     * with a fixed {@link #gap} and start at the top of each new column.</p>
     *
     * @param innerX left edge of inner content box
     * @param innerY bottom edge of inner content box
     * @param innerW width of inner content box
     * @param innerH height of inner content box
     */
    private void layoutColumnWrapped(float innerX, float innerY, float innerW, float innerH) {
        float cx = innerX;             // Current column left edge.
        float cyTop = innerY + innerH; // Top edge of inner content box.
        float penY = cyTop;            // Current pen Y (top-down).
        float colW = 0f;               // Width of the current column (max child width).

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
                case STRETCH -> 0.0F;                  // TODO: implement stretching within column
                case END -> cx + (colW - cw);
            };

            float childY = penY - ch;
            child.setPosition(x, childY);

            penY = childY - gap;

            if (cw > colW) colW = cw;
        }
    }

    /**
     * @return the current flex direction (ROW or COLUMN)
     */
    public FlexDirection getFlexDirection() {
        return flexDirection;
    }

    /**
     * Sets the main axis direction used by this container.
     *
     * @param flexDirection new direction (ROW or COLUMN)
     * @return this container for chaining
     */
    public FlexBox setFlexDirection(FlexDirection flexDirection) {
        this.flexDirection = flexDirection;
        return this;
    }

    /**
     * @return the gap size in pixels between items (and between lines/columns when wrapped)
     */
    public float getGap() {
        return gap;
    }

    /**
     * Sets the spacing between children on the main axis.
     *
     * <p>In wrapped mode, this gap is also used between rows (ROW) or between columns (COLUMN).</p>
     *
     * @param gap new gap in pixels
     * @return this container for chaining
     */
    public FlexBox setGap(float gap) {
        this.gap = gap;
        return this;
    }

    /**
     * @return true if wrapping is enabled
     */
    public boolean isWrap() {
        return wrap;
    }

    /**
     * Enables or disables wrapping.
     *
     * <p>When enabled, children flow into additional rows/columns when they overflow the inner box.</p>
     *
     * @param wrap true to enable wrapping, false to disable
     * @return this container for chaining
     */
    public FlexBox setWrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    /**
     * @return the current main-axis justification mode
     */
    public JustifyContent getJustifyContent() {
        return justifyContent;
    }

    /**
     * Sets how free space is distributed along the main axis when wrapping is disabled.
     *
     * @param justifyContent justification mode
     * @return this container for chaining
     */
    public FlexBox setJustifyContent(JustifyContent justifyContent) {
        this.justifyContent = justifyContent;
        return this;
    }

    /**
     * @return the current cross-axis alignment mode
     */
    public AlignItems getAlignItems() {
        return alignItems;
    }

    /**
     * Sets how items are positioned along the cross axis.
     *
     * <p>Note: STRETCH is currently not implemented in this class (it does not resize children).</p>
     *
     * @param alignItems alignment mode
     * @return this container for chaining
     */
    public FlexBox setAlignItems(AlignItems alignItems) {
        this.alignItems = alignItems;
        return this;
    }
}
