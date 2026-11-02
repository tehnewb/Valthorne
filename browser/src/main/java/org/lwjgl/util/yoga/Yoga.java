package org.lwjgl.util.yoga;

import org.teavm.jso.JSBody;

/**
 * Browser bindings for the shared Yoga layout API. Node and configuration handles
 * reference the official Yoga WebAssembly package; the existing engine layout
 * algorithms and public UI types remain unchanged. Calls use the application
 * coroutine, and nodes must be released before their borrowed configuration.
 */
public final class Yoga {
    /**
     * Yoga align auto value.
     */
    public static final int YGAlignAuto = 0;
    /**
     * Yoga align baseline value.
     */
    public static final int YGAlignBaseline = 5;
    /**
     * Yoga align center value.
     */
    public static final int YGAlignCenter = 2;
    /**
     * Yoga align flex end value.
     */
    public static final int YGAlignFlexEnd = 3;
    /**
     * Yoga align flex start value.
     */
    public static final int YGAlignFlexStart = 1;
    /**
     * Yoga align space around value.
     */
    public static final int YGAlignSpaceAround = 7;
    /**
     * Yoga align space between value.
     */
    public static final int YGAlignSpaceBetween = 6;
    /**
     * Yoga align stretch value.
     */
    public static final int YGAlignStretch = 4;
    /**
     * Yoga direction l t r value.
     */
    public static final int YGDirectionLTR = 1;
    /**
     * Yoga edge bottom value.
     */
    public static final int YGEdgeBottom = 3;
    /**
     * Yoga edge left value.
     */
    public static final int YGEdgeLeft = 0;
    /**
     * Yoga edge right value.
     */
    public static final int YGEdgeRight = 2;
    /**
     * Yoga edge top value.
     */
    public static final int YGEdgeTop = 1;
    /**
     * Yoga flex direction column value.
     */
    public static final int YGFlexDirectionColumn = 0;
    /**
     * Yoga flex direction column reverse value.
     */
    public static final int YGFlexDirectionColumnReverse = 1;
    /**
     * Yoga flex direction row value.
     */
    public static final int YGFlexDirectionRow = 2;
    /**
     * Yoga flex direction row reverse value.
     */
    public static final int YGFlexDirectionRowReverse = 3;
    /**
     * Yoga gutter column value.
     */
    public static final int YGGutterColumn = 0;
    /**
     * Yoga gutter row value.
     */
    public static final int YGGutterRow = 1;
    /**
     * Yoga justify center value.
     */
    public static final int YGJustifyCenter = 1;
    /**
     * Yoga justify flex end value.
     */
    public static final int YGJustifyFlexEnd = 2;
    /**
     * Yoga justify flex start value.
     */
    public static final int YGJustifyFlexStart = 0;
    /**
     * Yoga justify space around value.
     */
    public static final int YGJustifySpaceAround = 4;
    /**
     * Yoga justify space between value.
     */
    public static final int YGJustifySpaceBetween = 3;
    /**
     * Yoga justify space evenly value.
     */
    public static final int YGJustifySpaceEvenly = 5;
    /**
     * Yoga overflow hidden value.
     */
    public static final int YGOverflowHidden = 1;
    /**
     * Yoga overflow scroll value.
     */
    public static final int YGOverflowScroll = 2;
    /**
     * Yoga overflow visible value.
     */
    public static final int YGOverflowVisible = 0;
    /**
     * Yoga position type absolute value.
     */
    public static final int YGPositionTypeAbsolute = 2;
    /**
     * Yoga position type relative value.
     */
    public static final int YGPositionTypeRelative = 1;
    /**
     * Yoga wrap no wrap value.
     */
    public static final int YGWrapNoWrap = 0;
    /**
     * Yoga wrap reverse value.
     */
    public static final int YGWrapReverse = 2;
    /**
     * Yoga wrap wrap value.
     */
    public static final int YGWrapWrap = 1;

    /**
     * Prevents creating stateless layout bindings.
     */
    private Yoga() {
    }

    /**
     * Applies the native YGConfigFree layout operation.
     * @param config layout value
     */
    public static void YGConfigFree(long config) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGConfigFree((int)config);
    }

    /**
     * Applies the native YGConfigNew layout operation.
     * @return native layout result
     */
    public static long YGConfigNew() {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        return bYGConfigNew();
    }

    /**
     * Applies the native YGConfigSetUseWebDefaults layout operation.
     * @param config layout value
     * @param enabled layout value
     */
    public static void YGConfigSetUseWebDefaults(long config, boolean enabled) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGConfigSetUseWebDefaults((int)config,enabled);
    }

    /**
     * Applies the native YGNodeCalculateLayout layout operation.
     * @param node layout value
     * @param availableWidth layout value
     * @param availableHeight layout value
     * @param ownerDirection layout value
     */
    public static void YGNodeCalculateLayout(long node, float availableWidth, float availableHeight, int ownerDirection) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeCalculateLayout((int)node,availableWidth,availableHeight,ownerDirection);
    }

    /**
     * Applies the native YGNodeFree layout operation.
     * @param node layout value
     */
    public static void YGNodeFree(long node) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeFree((int)node);
    }

    /**
     * Applies the native YGNodeInsertChild layout operation.
     * @param node layout value
     * @param child layout value
     * @param index layout value
     */
    public static void YGNodeInsertChild(long node, long child, long index) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeInsertChild((int)node,(int)child,(int)index);
    }

    /**
     * Applies the native YGNodeIsDirty layout operation.
     * @param node layout value
     * @return native layout result
     */
    public static boolean YGNodeIsDirty(long node) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        return bYGNodeIsDirty((int)node);
    }

    /**
     * Applies the native YGNodeLayoutGetHeight layout operation.
     * @param node layout value
     * @return native layout result
     */
    public static float YGNodeLayoutGetHeight(long node) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        return bYGNodeLayoutGetHeight((int)node);
    }

    /**
     * Applies the native YGNodeLayoutGetLeft layout operation.
     * @param node layout value
     * @return native layout result
     */
    public static float YGNodeLayoutGetLeft(long node) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        return bYGNodeLayoutGetLeft((int)node);
    }

    /**
     * Applies the native YGNodeLayoutGetTop layout operation.
     * @param node layout value
     * @return native layout result
     */
    public static float YGNodeLayoutGetTop(long node) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        return bYGNodeLayoutGetTop((int)node);
    }

    /**
     * Applies the native YGNodeLayoutGetWidth layout operation.
     * @param node layout value
     * @return native layout result
     */
    public static float YGNodeLayoutGetWidth(long node) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        return bYGNodeLayoutGetWidth((int)node);
    }

    /**
     * Applies the native YGNodeNewWithConfig layout operation.
     * @param config layout value
     * @return native layout result
     */
    public static long YGNodeNewWithConfig(long config) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        return bYGNodeNewWithConfig((int)config);
    }

    /**
     * Applies the native YGNodeRemoveChild layout operation.
     * @param node layout value
     * @param child layout value
     */
    public static void YGNodeRemoveChild(long node, long child) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeRemoveChild((int)node,(int)child);
    }

    /**
     * Applies the native YGNodeStyleSetAlignItems layout operation.
     * @param node layout value
     * @param alignItems layout value
     */
    public static void YGNodeStyleSetAlignItems(long node, int alignItems) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetAlignItems((int)node,alignItems);
    }

    /**
     * Applies the native YGNodeStyleSetAlignSelf layout operation.
     * @param node layout value
     * @param alignSelf layout value
     */
    public static void YGNodeStyleSetAlignSelf(long node, int alignSelf) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetAlignSelf((int)node,alignSelf);
    }

    /**
     * Applies the native YGNodeStyleSetFlexBasis layout operation.
     * @param node layout value
     * @param flexBasis layout value
     */
    public static void YGNodeStyleSetFlexBasis(long node, float flexBasis) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetFlexBasis((int)node,flexBasis);
    }

    /**
     * Applies the native YGNodeStyleSetFlexBasisAuto layout operation.
     * @param node layout value
     */
    public static void YGNodeStyleSetFlexBasisAuto(long node) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetFlexBasisAuto((int)node);
    }

    /**
     * Applies the native YGNodeStyleSetFlexBasisPercent layout operation.
     * @param node layout value
     * @param flexBasis layout value
     */
    public static void YGNodeStyleSetFlexBasisPercent(long node, float flexBasis) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetFlexBasisPercent((int)node,flexBasis);
    }

    /**
     * Applies the native YGNodeStyleSetFlexDirection layout operation.
     * @param node layout value
     * @param flexDirection layout value
     */
    public static void YGNodeStyleSetFlexDirection(long node, int flexDirection) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetFlexDirection((int)node,flexDirection);
    }

    /**
     * Applies the native YGNodeStyleSetFlexGrow layout operation.
     * @param node layout value
     * @param flexGrow layout value
     */
    public static void YGNodeStyleSetFlexGrow(long node, float flexGrow) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetFlexGrow((int)node,flexGrow);
    }

    /**
     * Applies the native YGNodeStyleSetFlexShrink layout operation.
     * @param node layout value
     * @param flexShrink layout value
     */
    public static void YGNodeStyleSetFlexShrink(long node, float flexShrink) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetFlexShrink((int)node,flexShrink);
    }

    /**
     * Applies the native YGNodeStyleSetFlexWrap layout operation.
     * @param node layout value
     * @param flexWrap layout value
     */
    public static void YGNodeStyleSetFlexWrap(long node, int flexWrap) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetFlexWrap((int)node,flexWrap);
    }

    /**
     * Applies the native YGNodeStyleSetGap layout operation.
     * @param node layout value
     * @param gutter layout value
     * @param gapLength layout value
     */
    public static void YGNodeStyleSetGap(long node, int gutter, float gapLength) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetGap((int)node,gutter,gapLength);
    }

    /**
     * Applies the native YGNodeStyleSetHeight layout operation.
     * @param node layout value
     * @param height layout value
     */
    public static void YGNodeStyleSetHeight(long node, float height) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetHeight((int)node,height);
    }

    /**
     * Applies the native YGNodeStyleSetHeightAuto layout operation.
     * @param node layout value
     */
    public static void YGNodeStyleSetHeightAuto(long node) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetHeightAuto((int)node);
    }

    /**
     * Applies the native YGNodeStyleSetHeightPercent layout operation.
     * @param node layout value
     * @param height layout value
     */
    public static void YGNodeStyleSetHeightPercent(long node, float height) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetHeightPercent((int)node,height);
    }

    /**
     * Applies the native YGNodeStyleSetJustifyContent layout operation.
     * @param node layout value
     * @param justifyContent layout value
     */
    public static void YGNodeStyleSetJustifyContent(long node, int justifyContent) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetJustifyContent((int)node,justifyContent);
    }

    /**
     * Applies the native YGNodeStyleSetMargin layout operation.
     * @param node layout value
     * @param edge layout value
     * @param margin layout value
     */
    public static void YGNodeStyleSetMargin(long node, int edge, float margin) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMargin((int)node,edge,margin);
    }

    /**
     * Applies the native YGNodeStyleSetMarginPercent layout operation.
     * @param node layout value
     * @param edge layout value
     * @param margin layout value
     */
    public static void YGNodeStyleSetMarginPercent(long node, int edge, float margin) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMarginPercent((int)node,edge,margin);
    }

    /**
     * Applies the native YGNodeStyleSetMaxHeight layout operation.
     * @param node layout value
     * @param maxHeight layout value
     */
    public static void YGNodeStyleSetMaxHeight(long node, float maxHeight) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMaxHeight((int)node,maxHeight);
    }

    /**
     * Applies the native YGNodeStyleSetMaxHeightPercent layout operation.
     * @param node layout value
     * @param maxHeight layout value
     */
    public static void YGNodeStyleSetMaxHeightPercent(long node, float maxHeight) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMaxHeightPercent((int)node,maxHeight);
    }

    /**
     * Applies the native YGNodeStyleSetMaxWidth layout operation.
     * @param node layout value
     * @param maxWidth layout value
     */
    public static void YGNodeStyleSetMaxWidth(long node, float maxWidth) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMaxWidth((int)node,maxWidth);
    }

    /**
     * Applies the native YGNodeStyleSetMaxWidthPercent layout operation.
     * @param node layout value
     * @param maxWidth layout value
     */
    public static void YGNodeStyleSetMaxWidthPercent(long node, float maxWidth) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMaxWidthPercent((int)node,maxWidth);
    }

    /**
     * Applies the native YGNodeStyleSetMinHeight layout operation.
     * @param node layout value
     * @param minHeight layout value
     */
    public static void YGNodeStyleSetMinHeight(long node, float minHeight) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMinHeight((int)node,minHeight);
    }

    /**
     * Applies the native YGNodeStyleSetMinHeightPercent layout operation.
     * @param node layout value
     * @param minHeight layout value
     */
    public static void YGNodeStyleSetMinHeightPercent(long node, float minHeight) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMinHeightPercent((int)node,minHeight);
    }

    /**
     * Applies the native YGNodeStyleSetMinWidth layout operation.
     * @param node layout value
     * @param minWidth layout value
     */
    public static void YGNodeStyleSetMinWidth(long node, float minWidth) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMinWidth((int)node,minWidth);
    }

    /**
     * Applies the native YGNodeStyleSetMinWidthPercent layout operation.
     * @param node layout value
     * @param minWidth layout value
     */
    public static void YGNodeStyleSetMinWidthPercent(long node, float minWidth) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetMinWidthPercent((int)node,minWidth);
    }

    /**
     * Applies the native YGNodeStyleSetPadding layout operation.
     * @param node layout value
     * @param edge layout value
     * @param padding layout value
     */
    public static void YGNodeStyleSetPadding(long node, int edge, float padding) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetPadding((int)node,edge,padding);
    }

    /**
     * Applies the native YGNodeStyleSetPaddingPercent layout operation.
     * @param node layout value
     * @param edge layout value
     * @param padding layout value
     */
    public static void YGNodeStyleSetPaddingPercent(long node, int edge, float padding) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetPaddingPercent((int)node,edge,padding);
    }

    /**
     * Applies the native YGNodeStyleSetPosition layout operation.
     * @param node layout value
     * @param edge layout value
     * @param position layout value
     */
    public static void YGNodeStyleSetPosition(long node, int edge, float position) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetPosition((int)node,edge,position);
    }

    /**
     * Applies the native YGNodeStyleSetPositionPercent layout operation.
     * @param node layout value
     * @param edge layout value
     * @param position layout value
     */
    public static void YGNodeStyleSetPositionPercent(long node, int edge, float position) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetPositionPercent((int)node,edge,position);
    }

    /**
     * Applies the native YGNodeStyleSetPositionType layout operation.
     * @param node layout value
     * @param positionType layout value
     */
    public static void YGNodeStyleSetPositionType(long node, int positionType) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetPositionType((int)node,positionType);
    }

    /**
     * Applies the native YGNodeStyleSetWidth layout operation.
     * @param node layout value
     * @param width layout value
     */
    public static void YGNodeStyleSetWidth(long node, float width) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetWidth((int)node,width);
    }

    /**
     * Applies the native YGNodeStyleSetWidthAuto layout operation.
     * @param node layout value
     */
    public static void YGNodeStyleSetWidthAuto(long node) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetWidthAuto((int)node);
    }

    /**
     * Applies the native YGNodeStyleSetWidthPercent layout operation.
     * @param node layout value
     * @param width layout value
     */
    public static void YGNodeStyleSetWidthPercent(long node, float width) {
        /*
         * Convert stable engine handles to the browser layout registry's indices.
         */
        bYGNodeStyleSetWidthPercent((int)node,width);
    }

    /**
     * Calls the official Yoga browser binding.
     * @param config layout value
     */
    @JSBody(params = {"config"}, script = "valthorneHost.yoga.free(config);")
    private static native void bYGConfigFree(int config);

    /**
     * Calls the official Yoga browser binding.
     * @return native layout result
     */
    @JSBody(params = {}, script = "return valthorneHost.yoga.config();")
    private static native int bYGConfigNew();

    /**
     * Calls the official Yoga browser binding.
     * @param config layout value
     * @param enabled layout value
     */
    @JSBody(params = {"config", "enabled"}, script = "valthorneHost.yoga.get(config).setUseWebDefaults(enabled);")
    private static native void bYGConfigSetUseWebDefaults(int config, boolean enabled);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param availableWidth layout value
     * @param availableHeight layout value
     * @param ownerDirection layout value
     */
    @JSBody(params = {"node", "availableWidth", "availableHeight", "ownerDirection"}, script = "valthorneHost.yoga.get(node).calculateLayout(availableWidth,availableHeight,ownerDirection);")
    private static native void bYGNodeCalculateLayout(int node, float availableWidth, float availableHeight, int ownerDirection);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     */
    @JSBody(params = {"node"}, script = "valthorneHost.yoga.free(node);")
    private static native void bYGNodeFree(int node);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param child layout value
     * @param index layout value
     */
    @JSBody(params = {"node", "child", "index"}, script = "valthorneHost.yoga.get(node).insertChild(valthorneHost.yoga.get(child),index);")
    private static native void bYGNodeInsertChild(int node, int child, int index);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @return native layout result
     */
    @JSBody(params = {"node"}, script = "return valthorneHost.yoga.get(node).isDirty();")
    private static native boolean bYGNodeIsDirty(int node);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @return native layout result
     */
    @JSBody(params = {"node"}, script = "return valthorneHost.yoga.get(node).getComputedHeight();")
    private static native float bYGNodeLayoutGetHeight(int node);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @return native layout result
     */
    @JSBody(params = {"node"}, script = "return valthorneHost.yoga.get(node).getComputedLeft();")
    private static native float bYGNodeLayoutGetLeft(int node);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @return native layout result
     */
    @JSBody(params = {"node"}, script = "return valthorneHost.yoga.get(node).getComputedTop();")
    private static native float bYGNodeLayoutGetTop(int node);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @return native layout result
     */
    @JSBody(params = {"node"}, script = "return valthorneHost.yoga.get(node).getComputedWidth();")
    private static native float bYGNodeLayoutGetWidth(int node);

    /**
     * Calls the official Yoga browser binding.
     * @param config layout value
     * @return native layout result
     */
    @JSBody(params = {"config"}, script = "return valthorneHost.yoga.node(config);")
    private static native int bYGNodeNewWithConfig(int config);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param child layout value
     */
    @JSBody(params = {"node", "child"}, script = "valthorneHost.yoga.get(node).removeChild(valthorneHost.yoga.get(child));")
    private static native void bYGNodeRemoveChild(int node, int child);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param alignItems layout value
     */
    @JSBody(params = {"node", "alignItems"}, script = "valthorneHost.yoga.get(node).setAlignItems(alignItems);")
    private static native void bYGNodeStyleSetAlignItems(int node, int alignItems);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param alignSelf layout value
     */
    @JSBody(params = {"node", "alignSelf"}, script = "valthorneHost.yoga.get(node).setAlignSelf(alignSelf);")
    private static native void bYGNodeStyleSetAlignSelf(int node, int alignSelf);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param flexBasis layout value
     */
    @JSBody(params = {"node", "flexBasis"}, script = "valthorneHost.yoga.get(node).setFlexBasis(flexBasis);")
    private static native void bYGNodeStyleSetFlexBasis(int node, float flexBasis);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     */
    @JSBody(params = {"node"}, script = "valthorneHost.yoga.get(node).setFlexBasisAuto();")
    private static native void bYGNodeStyleSetFlexBasisAuto(int node);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param flexBasis layout value
     */
    @JSBody(params = {"node", "flexBasis"}, script = "valthorneHost.yoga.get(node).setFlexBasisPercent(flexBasis);")
    private static native void bYGNodeStyleSetFlexBasisPercent(int node, float flexBasis);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param flexDirection layout value
     */
    @JSBody(params = {"node", "flexDirection"}, script = "valthorneHost.yoga.get(node).setFlexDirection(flexDirection);")
    private static native void bYGNodeStyleSetFlexDirection(int node, int flexDirection);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param flexGrow layout value
     */
    @JSBody(params = {"node", "flexGrow"}, script = "valthorneHost.yoga.get(node).setFlexGrow(flexGrow);")
    private static native void bYGNodeStyleSetFlexGrow(int node, float flexGrow);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param flexShrink layout value
     */
    @JSBody(params = {"node", "flexShrink"}, script = "valthorneHost.yoga.get(node).setFlexShrink(flexShrink);")
    private static native void bYGNodeStyleSetFlexShrink(int node, float flexShrink);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param flexWrap layout value
     */
    @JSBody(params = {"node", "flexWrap"}, script = "valthorneHost.yoga.get(node).setFlexWrap(flexWrap);")
    private static native void bYGNodeStyleSetFlexWrap(int node, int flexWrap);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param gutter layout value
     * @param gapLength layout value
     */
    @JSBody(params = {"node", "gutter", "gapLength"}, script = "valthorneHost.yoga.get(node).setGap(gutter,gapLength);")
    private static native void bYGNodeStyleSetGap(int node, int gutter, float gapLength);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param height layout value
     */
    @JSBody(params = {"node", "height"}, script = "valthorneHost.yoga.get(node).setHeight(height);")
    private static native void bYGNodeStyleSetHeight(int node, float height);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     */
    @JSBody(params = {"node"}, script = "valthorneHost.yoga.get(node).setHeightAuto();")
    private static native void bYGNodeStyleSetHeightAuto(int node);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param height layout value
     */
    @JSBody(params = {"node", "height"}, script = "valthorneHost.yoga.get(node).setHeightPercent(height);")
    private static native void bYGNodeStyleSetHeightPercent(int node, float height);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param justifyContent layout value
     */
    @JSBody(params = {"node", "justifyContent"}, script = "valthorneHost.yoga.get(node).setJustifyContent(justifyContent);")
    private static native void bYGNodeStyleSetJustifyContent(int node, int justifyContent);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param edge layout value
     * @param margin layout value
     */
    @JSBody(params = {"node", "edge", "margin"}, script = "valthorneHost.yoga.get(node).setMargin(edge,margin);")
    private static native void bYGNodeStyleSetMargin(int node, int edge, float margin);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param edge layout value
     * @param margin layout value
     */
    @JSBody(params = {"node", "edge", "margin"}, script = "valthorneHost.yoga.get(node).setMarginPercent(edge,margin);")
    private static native void bYGNodeStyleSetMarginPercent(int node, int edge, float margin);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param maxHeight layout value
     */
    @JSBody(params = {"node", "maxHeight"}, script = "valthorneHost.yoga.get(node).setMaxHeight(maxHeight);")
    private static native void bYGNodeStyleSetMaxHeight(int node, float maxHeight);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param maxHeight layout value
     */
    @JSBody(params = {"node", "maxHeight"}, script = "valthorneHost.yoga.get(node).setMaxHeightPercent(maxHeight);")
    private static native void bYGNodeStyleSetMaxHeightPercent(int node, float maxHeight);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param maxWidth layout value
     */
    @JSBody(params = {"node", "maxWidth"}, script = "valthorneHost.yoga.get(node).setMaxWidth(maxWidth);")
    private static native void bYGNodeStyleSetMaxWidth(int node, float maxWidth);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param maxWidth layout value
     */
    @JSBody(params = {"node", "maxWidth"}, script = "valthorneHost.yoga.get(node).setMaxWidthPercent(maxWidth);")
    private static native void bYGNodeStyleSetMaxWidthPercent(int node, float maxWidth);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param minHeight layout value
     */
    @JSBody(params = {"node", "minHeight"}, script = "valthorneHost.yoga.get(node).setMinHeight(minHeight);")
    private static native void bYGNodeStyleSetMinHeight(int node, float minHeight);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param minHeight layout value
     */
    @JSBody(params = {"node", "minHeight"}, script = "valthorneHost.yoga.get(node).setMinHeightPercent(minHeight);")
    private static native void bYGNodeStyleSetMinHeightPercent(int node, float minHeight);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param minWidth layout value
     */
    @JSBody(params = {"node", "minWidth"}, script = "valthorneHost.yoga.get(node).setMinWidth(minWidth);")
    private static native void bYGNodeStyleSetMinWidth(int node, float minWidth);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param minWidth layout value
     */
    @JSBody(params = {"node", "minWidth"}, script = "valthorneHost.yoga.get(node).setMinWidthPercent(minWidth);")
    private static native void bYGNodeStyleSetMinWidthPercent(int node, float minWidth);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param edge layout value
     * @param padding layout value
     */
    @JSBody(params = {"node", "edge", "padding"}, script = "valthorneHost.yoga.get(node).setPadding(edge,padding);")
    private static native void bYGNodeStyleSetPadding(int node, int edge, float padding);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param edge layout value
     * @param padding layout value
     */
    @JSBody(params = {"node", "edge", "padding"}, script = "valthorneHost.yoga.get(node).setPaddingPercent(edge,padding);")
    private static native void bYGNodeStyleSetPaddingPercent(int node, int edge, float padding);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param edge layout value
     * @param position layout value
     */
    @JSBody(params = {"node", "edge", "position"}, script = "valthorneHost.yoga.get(node).setPosition(edge,position);")
    private static native void bYGNodeStyleSetPosition(int node, int edge, float position);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param edge layout value
     * @param position layout value
     */
    @JSBody(params = {"node", "edge", "position"}, script = "valthorneHost.yoga.get(node).setPositionPercent(edge,position);")
    private static native void bYGNodeStyleSetPositionPercent(int node, int edge, float position);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param positionType layout value
     */
    @JSBody(params = {"node", "positionType"}, script = "valthorneHost.yoga.get(node).setPositionType(positionType);")
    private static native void bYGNodeStyleSetPositionType(int node, int positionType);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param width layout value
     */
    @JSBody(params = {"node", "width"}, script = "valthorneHost.yoga.get(node).setWidth(width);")
    private static native void bYGNodeStyleSetWidth(int node, float width);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     */
    @JSBody(params = {"node"}, script = "valthorneHost.yoga.get(node).setWidthAuto();")
    private static native void bYGNodeStyleSetWidthAuto(int node);

    /**
     * Calls the official Yoga browser binding.
     * @param node layout value
     * @param width layout value
     */
    @JSBody(params = {"node", "width"}, script = "valthorneHost.yoga.get(node).setWidthPercent(width);")
    private static native void bYGNodeStyleSetWidthPercent(int node, float width);

}
