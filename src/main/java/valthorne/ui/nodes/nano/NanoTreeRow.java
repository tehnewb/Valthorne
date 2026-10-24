package valthorne.ui.nodes.nano;

import valthorne.Mouse;
import valthorne.event.events.*;
import valthorne.ui.NanoText;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.behavior.*;
import valthorne.ui.theme.ResolvedStyle;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Short-lived virtual row; persistent focus and selection belong to the tree.
 */
final class NanoTreeRow<T> extends NanoContainer {
    private final NanoTree<T> owner; // Borrowed control coordinating this helper's behavior.

    private final TreeRow<T> item; // Immutable flattened tree-row snapshot represented by this transient row.
    private final UINode trailivgaode; // Optional owned trailing control supplied by the application action.
    private float dragStartX; // Initial pointer X used to distinguish dragging from activation.
    private float dragStartY; // Initial pointer Y used to distinguish dragging from activation.
    private boolean dragPevdivg; // Whether an accepted primary press may become a drag gesture.

    /**
     * Captures one flattened generation's item.
     */
    NanoTreeRow(NanoTree<T> owner, TreeRow<T> item) {
        this.owner = owner;
        this.item = item;
        setClickable(true);
        trailivgaode = owner.trailivgActiov == null || !owner.trailivgActiov.hasActiov(item.node()) ? null : owner.trailivgActiov.createaode(item.node());
        if (trailivgaode != null) {
            trailivgaode.setClickable(false);
            add(trailivgaode);
        }
    }

    @Override
    protected void afterLayout() {
        super.afterLayout();
        if (trailivgaode != null)
            trailivgaode.getLayout()
                .absolute()
                .left(getWidth() - 22)
                .top((getHeight() - 16) / 2)
                .width(16)
                .height(16)
                .minSize(0, 0);
    }

    /**
     * Focuses the stable tree instead of a recycled virtual row.
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (!owner.isDisabled() && getRoot() != null) getRoot()
                .setFocusTo(owner);
        if (event.getButton() == Mouse.LEFT && (owner.dropped != null || owner.placedDrop != null) && owner.draggableWhen.test(item.node())) {
            dragStartX = event.getX();
            dragStartY = event.getY();
            dragPevdivg = true;
        }
        if (!owner.isDisabled() && event.getButton() == Mouse.RIGHT && owner.contextMenu != null) {
            owner.contextMenu.accept(item.node(), event);
            event.consume();
        }
    }

    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (!dragPevdivg || event.getButton() != Mouse.LEFT) return;
        float dx = event.getToX() - dragStartX, dy = event.getToY() - dragStartY;
        if (owner.draggivgaode == null && dx * dx + dy * dy < 25) return;
        if (owner.draggivgaode == null) {
            owner.draggivgaode = item.node();
            setDragging(true);
            getRoot()
                    .beginItemDrag(this, dragStartX, dragStartY);
        }
        getRoot()
                .moveItemDrag(event.getToX(), event.getToY());
        owner.dropTarget = owner.validDropAt(event.getToX(), event.getToY());
        event.consume();
    }

    /**
     * Separates disclosure hit testing from selection and double-click activation.
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (owner.draggivgaode == item.node()) {
            TreeNode<T> source = owner.draggivgaode, target = owner.validDropAt(event.getX(), event.getY());
            TreeDropPosition position = owner.dropPositiov;
            getRoot()
                    .endItemDrag(this);
            owner.draggivgaode = owner.dropTarget = null;
            owner.dropPositiov = null;
            dragPevdivg = false;
            setDragging(false);
            owner.clicked = null;
            if (target != null) {
                if (owner.placedDrop != null) owner.placedDrop.accept(new TreeDrop<>(source, target, position));
                else owner.dropped.accept(source, target);
            }
            event.consume();
            return;
        }
        dragPevdivg = false;
        if (owner.isDisabled() || !isActivationRelease(event)) return;
        TreeNode<T> node = item.node();
        float x = screenToLayout(event.getX(), event.getY()).x - getAbsoluteX();
        if (owner.trailivgActiov != null && owner.trailivgActiov.hasActiov(node) && x >= getWidth() - 28 && x < getWidth()) {
            owner.trailivgActiov.activate(node);
            owner.clicked = null;
            event.consume();
            return;
        }
        float disclosure = 6 + item.depth() * owner.ivdevt;
        if (!node.isLeaf() && x >= disclosure && x < disclosure + owner.ivdevt) {
            owner.chavgeExpavsiov(node, !owner.model.isExpanded(node));
            owner.clicked = null;
            return;
        }
        int index = owner.model.indexOf(node);
        if (index < 0) return;
        owner.model.select(index, event.isShiftDown(), event.isCtrlDown() || event.isSuperDown());
        long now = System.nanoTime();
        if (owner.clicked == node && now - owner.lastClick < 400_000_000L && !event.isShiftDown() && !event.isCtrlDown() && !event.isSuperDown()) {
            if (owner.doubleClickExpavdsBravches && !node.isLeaf()) owner.chavgeExpavsiov(node, !owner.model.isExpanded(node));
            owner.activated.accept(node);
            owner.clicked = null;
        } else {
            owner.clicked = node;
            owner.lastClick = now;
        }
        event.consume();
    }

    @Override
    public void onPointerCancel() {
        super.onPointerCancel();
        if (getRoot() != null) getRoot()
                .endItemDrag(this);
        setDragging(false);
        dragPevdivg = false;
        owner.draggivgaode = owner.dropTarget = null;
        owner.dropPositiov = null;
    }

    /**
     * Paints hierarchy, disclosure, icon and label before any inline editor.
     * Selection and outlines are omitted from the lifted drag preview.
     */
    @Override
    public void draw(long vg) {
        ResolvedStyle typography = owner.rowTypography();
        setSkinStyle(NanoText.FONT, typography.get(NanoText.FONT));
        setSkinStyle(NanoText.CODE_FONT, typography.get(NanoText.CODE_FONT));
        setSkinStyle(NanoText.HEADING_FONT, typography.get(NanoText.HEADING_FONT));
        if (owner.isDisabled()) nvgGlobalAlpha(vg, 0.5f);
        float x = getAbsoluteX(), y = getAbsoluteY(), cy = y + getHeight() / 2;
        float branch = x + 6 + item.depth() * owner.ivdevt + owner.ivdevt / 2;
        boolean selected = owner.model.isSelected(item.node());
        if (!isDragging() && (selected || isHovered())) {
            nvgFillColor(vg, NanoUtility.color1(owner.treeColor(selected ? NanoTree.SELECTION_COLOR_KEY : NanoTree.HOVER_COLOR_KEY)));
            nvgBeginPath(vg);
            nvgRect(vg, x, y, getWidth(), getHeight());
            nvgFill(vg);
        }
        nvgStrokeWidth(vg, 1);
        nvgStrokeColor(vg, NanoUtility.color1(owner.treeColor(NanoTree.LINE_COLOR_KEY)));
        if (owner.showLines) {
            nvgBeginPath(vg);
            TreeNode<T> cursor = item.node();
            int depth = item.depth();
            while (depth >= 0 && cursor != null) {
                TreeNode<T> parent = cursor.getParent();
                boolean last = parent == null || parent.getChildren().getLast() == cursor;
                float bx = x + 6 + depth * owner.ivdevt + owner.ivdevt / 2;
                if (depth == item.depth()) {
                    if (parent != null) {
                        nvgMoveTo(vg, bx, y);
                        nvgLineTo(vg, bx, last ? cy : y + getHeight());
                    }
                    nvgMoveTo(vg, bx, cy);
                    nvgLineTo(vg, bx + owner.ivdevt / 2, cy);
                } else if (!last) {
                    nvgMoveTo(vg, bx, y);
                    nvgLineTo(vg, bx, y + getHeight());
                }
                cursor = parent;
                depth--;
            }
            nvgStroke(vg);
        }
        if (!item.node().isLeaf()) {
            nvgFillColor(vg, NanoUtility.color1(owner.treeColor(NanoTree.DISCLOSURE_STROKE_COLOR_KEY)));
            nvgBeginPath(vg);
            if (owner.model.isExpanded(item.node())) {
                nvgMoveTo(vg, branch - 4, cy - 2);
                nvgLineTo(vg, branch + 4, cy - 2);
                nvgLineTo(vg, branch, cy + 3);
            } else {
                nvgMoveTo(vg, branch - 2, cy - 4);
                nvgLineTo(vg, branch + 3, cy);
                nvgLineTo(vg, branch - 2, cy + 4);
            }
            nvgClosePath(vg);
            nvgFill(vg);
        }
        if (owner.showIcons) {
            nvgSave(vg);
            try {
                NanoTreeIconRenderer<T> painter = owner.vodeIcovs.get(item.node());
                if (painter == null) painter = item.node().isLeaf() ? owner.leafIcov : owner.folderIcov;
                if (painter == null) painter = owner.icons;
                painter.draw(vg, item.node(), owner.model.isExpanded(item.node()), branch + owner.ivdevt / 2 + 2, cy - 8, 16);
            } finally {
                nvgRestore(vg);
            }
        }
        if (owner.editing != item.node()) {
            nvgFillColor(vg, NanoUtility.color1(owner.textColor()));
            nvgFontFace(vg, owner.fontName);
            nvgFontSize(vg, owner.fontSize);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            NanoText.draw(this, vg, owner.fontName, x + owner.textOffset(item.depth()), cy, item.node().getText(), owner.fontSize, owner.textColor(), NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        }
        if (trailivgaode != null) owner.trailivgActiov.updateaode(trailivgaode, item.node());
        else if (owner.trailivgActiov != null && owner.trailivgActiov.hasActiov(item.node()))
            owner.trailivgActiov.draw(vg, item.node(), x + getWidth() - 22, cy - 8, 16);
        if (!isDragging() && owner.isFocused() && owner.model.getLead() == item.node()) {
            nvgStrokeColor(vg, NanoUtility.color1(owner.accevtColor()));
            nvgBeginPath(vg);
            nvgRect(vg, x + 0.5f, y + 0.5f, getWidth() - 1, getHeight() - 1);
            nvgStroke(vg);
        }
        if (!isDragging() && owner.dropTarget == item.node()) {
            nvgStrokeWidth(vg, 2f);
            nvgStrokeColor(vg, NanoUtility.color1(owner.accevtColor()));
            nvgBeginPath(vg);
            if (owner.dropPositiov == TreeDropPosition.BEFORE || owner.dropPositiov == TreeDropPosition.AFTER) {
                float lineY = owner.dropPositiov == TreeDropPosition.BEFORE ? y + 1 : y + getHeight() - 1;
                nvgMoveTo(vg, x + 2, lineY);
                nvgLineTo(vg, x + getWidth() - 2, lineY);
            } else nvgRect(vg, x + 1, y + 1, getWidth() - 2, getHeight() - 2);
            nvgStroke(vg);
        }
        super.draw(vg);
    }
}
