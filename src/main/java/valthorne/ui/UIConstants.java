package valthorne.ui;

import org.lwjgl.util.yoga.Yoga;

/**
 * <h1>UIConstants</h1>
 *
 * <p>
 * {@code UIConstants} is a utility class responsible for translating the engine's
 * {@link Layout} and {@link LayoutValue} system into Yoga layout instructions.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public final class UIConstants {

    /**
     * Constant used to represent a null Yoga node memory address.
     *
     * <p>
     * Yoga nodes are stored as native memory pointers represented by {@code long}.
     * A value of {@code 0} indicates that a node has not yet been created or has
     * already been destroyed.
     * </p>
     */
    public static final long NULL = 0;

    /**
     * Private constructor to prevent instantiation.
     *
     * <p>
     * {@code UIConstants} is a pure utility class and should never be instantiated.
     * </p>
     */
    private UIConstants() {
    }

    /**
     * Applies all layout rules from a {@link Layout} object onto a Yoga node.
     *
     * <p>
     * This method is the main entry point used during layout synchronization.
     * It maps layout values from the engine abstraction into Yoga style calls.
     * </p>
     *
     * <p>
     * The following layout categories are applied:
     * </p>
     *
     * <ul>
     *     <li>Size rules (width, height, min/max)</li>
     *     <li>Position rules</li>
     *     <li>Margin rules</li>
     *     <li>Padding rules</li>
     *     <li>Flexbox rules</li>
     * </ul>
     *
     * @param node   the Yoga node memory address
     * @param layout the layout configuration to apply
     */
    public static void applyLayout(long node, Layout layout) {
        applySize(node, layout);
        applyPosition(node, layout);
        applyMargin(node, layout);
        applyPadding(node, layout);

        Yoga.YGNodeStyleSetFlexGrow(node, layout.getFlexGrow());
        Yoga.YGNodeStyleSetFlexShrink(node, layout.getFlexShrink());
        applyFlexBasis(node, layout.getFlexBasis());

        Yoga.YGNodeStyleSetFlexDirection(node, layout.getFlexDirection().yoga());
        Yoga.YGNodeStyleSetJustifyContent(node, layout.getJustifyContent().yoga());
        Yoga.YGNodeStyleSetAlignItems(node, layout.getAlignItems().yoga());
        Yoga.YGNodeStyleSetAlignSelf(node, layout.getAlignSelf().yoga());
        Yoga.YGNodeStyleSetPositionType(node, layout.getPositionType().yoga());
        Yoga.YGNodeStyleSetFlexWrap(node, layout.getFlexWrap().yoga());
        Yoga.YGNodeStyleSetGap(node, Yoga.YGGutterRow, layout.getRowGap());
        Yoga.YGNodeStyleSetGap(node, Yoga.YGGutterColumn, layout.getColumnGap());
    }

    /**
     * Applies width and height constraints to a Yoga node.
     *
     * @param node  the Yoga node memory address
     * @param style the layout configuration
     */
    private static void applySize(long node, Layout style) {
        applyWidth(node, style.getWidth());
        applyHeight(node, style.getHeight());
        applyMinWidth(node, style.getMinWidth());
        applyMinHeight(node, style.getMinHeight());
        applyMaxWidth(node, style.getMaxWidth());
        applyMaxHeight(node, style.getMaxHeight());
    }

    /**
     * Applies positional offsets to a Yoga node.
     *
     * @param node  the Yoga node memory address
     * @param style the layout configuration
     */
    private static void applyPosition(long node, Layout style) {
        applyEdgePosition(node, Yoga.YGEdgeLeft, style.getLeft());
        applyEdgePosition(node, Yoga.YGEdgeTop, style.getTop());
        applyEdgePosition(node, Yoga.YGEdgeRight, style.getRight());
        applyEdgePosition(node, Yoga.YGEdgeBottom, style.getBottom());
    }

    /**
     * Applies margin rules to a Yoga node.
     *
     * @param node  the Yoga node memory address
     * @param style the layout configuration
     */
    private static void applyMargin(long node, Layout style) {
        applyEdgeMargin(node, Yoga.YGEdgeLeft, style.getMarginLeft());
        applyEdgeMargin(node, Yoga.YGEdgeTop, style.getMarginTop());
        applyEdgeMargin(node, Yoga.YGEdgeRight, style.getMarginRight());
        applyEdgeMargin(node, Yoga.YGEdgeBottom, style.getMarginBottom());
    }

    /**
     * Applies padding rules to a Yoga node.
     *
     * @param node  the Yoga node memory address
     * @param style the layout configuration
     */
    private static void applyPadding(long node, Layout style) {
        applyEdgePadding(node, Yoga.YGEdgeLeft, style.getPaddingLeft());
        applyEdgePadding(node, Yoga.YGEdgeTop, style.getPaddingTop());
        applyEdgePadding(node, Yoga.YGEdgeRight, style.getPaddingRight());
        applyEdgePadding(node, Yoga.YGEdgeBottom, style.getPaddingBottom());
    }

    private static void applyWidth(long node, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetWidthAuto(node);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetWidth(node, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetWidthPercent(node, value.getValue());
    }

    private static void applyHeight(long node, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetHeightAuto(node);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetHeight(node, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetHeightPercent(node, value.getValue());
    }

    private static void applyMinWidth(long node, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetMinWidth(node, Float.NaN);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetMinWidth(node, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetMinWidthPercent(node, value.getValue());
    }

    private static void applyMinHeight(long node, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetMinHeight(node, Float.NaN);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetMinHeight(node, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetMinHeightPercent(node, value.getValue());
    }

    private static void applyMaxWidth(long node, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetMaxWidth(node, Float.NaN);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetMaxWidth(node, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetMaxWidthPercent(node, value.getValue());
    }

    private static void applyMaxHeight(long node, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetMaxHeight(node, Float.NaN);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetMaxHeight(node, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetMaxHeightPercent(node, value.getValue());
    }

    private static void applyFlexBasis(long node, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetFlexBasisAuto(node);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetFlexBasis(node, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetFlexBasisPercent(node, value.getValue());
    }

    private static void applyEdgePosition(long node, int edge, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetPosition(node, edge, Float.NaN);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetPosition(node, edge, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetPositionPercent(node, edge, value.getValue());
    }

    private static void applyEdgeMargin(long node, int edge, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetMargin(node, edge, Float.NaN);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetMargin(node, edge, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetMarginPercent(node, edge, value.getValue());
    }

    private static void applyEdgePadding(long node, int edge, LayoutValue value) {
        if (value.isAuto()) {
            Yoga.YGNodeStyleSetPadding(node, edge, Float.NaN);
            return;
        }

        if (value.isPoints()) {
            Yoga.YGNodeStyleSetPadding(node, edge, value.getValue());
            return;
        }

        Yoga.YGNodeStyleSetPaddingPercent(node, edge, value.getValue());
    }
}