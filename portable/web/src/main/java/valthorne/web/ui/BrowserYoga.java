package valthorne.web.ui;
import org.teavm.jso.JSBody;
public final class BrowserYoga {
private BrowserYoga() {}
public static final int YGAlignAuto = 0;
public static final int YGAlignBaseline = 5;
public static final int YGAlignCenter = 2;
public static final int YGAlignFlexEnd = 3;
public static final int YGAlignFlexStart = 1;
public static final int YGAlignSpaceAround = 7;
public static final int YGAlignSpaceBetween = 6;
public static final int YGAlignStretch = 4;
public static void YGConfigFree(long config) {bYGConfigFree((int)config);}
@JSBody(params={"config"},script="valthorneHost.yoga.free(config);") private static native void bYGConfigFree(int config);
public static long YGConfigNew() {return bYGConfigNew();}
@JSBody(params={},script="return valthorneHost.yoga.config();") private static native int bYGConfigNew();
public static void YGConfigSetUseWebDefaults(long config,boolean enabled) {bYGConfigSetUseWebDefaults((int)config,enabled);}
@JSBody(params={"config","enabled"},script="valthorneHost.yoga.get(config).setUseWebDefaults(enabled);") private static native void bYGConfigSetUseWebDefaults(int config,boolean enabled);
public static final int YGDirectionLTR = 1;
public static final int YGEdgeBottom = 3;
public static final int YGEdgeLeft = 0;
public static final int YGEdgeRight = 2;
public static final int YGEdgeTop = 1;
public static final int YGFlexDirectionColumn = 0;
public static final int YGFlexDirectionColumnReverse = 1;
public static final int YGFlexDirectionRow = 2;
public static final int YGFlexDirectionRowReverse = 3;
public static final int YGGutterColumn = 0;
public static final int YGGutterRow = 1;
public static final int YGJustifyCenter = 1;
public static final int YGJustifyFlexEnd = 2;
public static final int YGJustifyFlexStart = 0;
public static final int YGJustifySpaceAround = 4;
public static final int YGJustifySpaceBetween = 3;
public static final int YGJustifySpaceEvenly = 5;
public static void YGNodeCalculateLayout(long node,float availableWidth,float availableHeight,int ownerDirection) {bYGNodeCalculateLayout((int)node,availableWidth,availableHeight,ownerDirection);}
@JSBody(params={"node","availableWidth","availableHeight","ownerDirection"},script="valthorneHost.yoga.get(node).calculateLayout(availableWidth,availableHeight,ownerDirection);") private static native void bYGNodeCalculateLayout(int node,float availableWidth,float availableHeight,int ownerDirection);
public static void YGNodeFree(long node) {bYGNodeFree((int)node);}
@JSBody(params={"node"},script="valthorneHost.yoga.free(node);") private static native void bYGNodeFree(int node);
public static void YGNodeInsertChild(long node,long child,long index) {bYGNodeInsertChild((int)node,(int)child,(int)index);}
@JSBody(params={"node","child","index"},script="valthorneHost.yoga.get(node).insertChild(valthorneHost.yoga.get(child),index);") private static native void bYGNodeInsertChild(int node,int child,int index);
public static boolean YGNodeIsDirty(long node) {return bYGNodeIsDirty((int)node);}
@JSBody(params={"node"},script="return valthorneHost.yoga.get(node).isDirty();") private static native boolean bYGNodeIsDirty(int node);
public static float YGNodeLayoutGetHeight(long node) {return bYGNodeLayoutGetHeight((int)node);}
@JSBody(params={"node"},script="return valthorneHost.yoga.get(node).getComputedHeight();") private static native float bYGNodeLayoutGetHeight(int node);
public static float YGNodeLayoutGetLeft(long node) {return bYGNodeLayoutGetLeft((int)node);}
@JSBody(params={"node"},script="return valthorneHost.yoga.get(node).getComputedLeft();") private static native float bYGNodeLayoutGetLeft(int node);
public static float YGNodeLayoutGetTop(long node) {return bYGNodeLayoutGetTop((int)node);}
@JSBody(params={"node"},script="return valthorneHost.yoga.get(node).getComputedTop();") private static native float bYGNodeLayoutGetTop(int node);
public static float YGNodeLayoutGetWidth(long node) {return bYGNodeLayoutGetWidth((int)node);}
@JSBody(params={"node"},script="return valthorneHost.yoga.get(node).getComputedWidth();") private static native float bYGNodeLayoutGetWidth(int node);
public static long YGNodeNewWithConfig(long config) {return bYGNodeNewWithConfig((int)config);}
@JSBody(params={"config"},script="return valthorneHost.yoga.node(config);") private static native int bYGNodeNewWithConfig(int config);
public static void YGNodeRemoveChild(long node,long child) {bYGNodeRemoveChild((int)node,(int)child);}
@JSBody(params={"node","child"},script="valthorneHost.yoga.get(node).removeChild(valthorneHost.yoga.get(child));") private static native void bYGNodeRemoveChild(int node,int child);
public static void YGNodeStyleSetAlignItems(long node,int alignItems) {bYGNodeStyleSetAlignItems((int)node,alignItems);}
@JSBody(params={"node","alignItems"},script="valthorneHost.yoga.get(node).setAlignItems(alignItems);") private static native void bYGNodeStyleSetAlignItems(int node,int alignItems);
public static void YGNodeStyleSetAlignSelf(long node,int alignSelf) {bYGNodeStyleSetAlignSelf((int)node,alignSelf);}
@JSBody(params={"node","alignSelf"},script="valthorneHost.yoga.get(node).setAlignSelf(alignSelf);") private static native void bYGNodeStyleSetAlignSelf(int node,int alignSelf);
public static void YGNodeStyleSetFlexBasis(long node,float flexBasis) {bYGNodeStyleSetFlexBasis((int)node,flexBasis);}
@JSBody(params={"node","flexBasis"},script="valthorneHost.yoga.get(node).setFlexBasis(flexBasis);") private static native void bYGNodeStyleSetFlexBasis(int node,float flexBasis);
public static void YGNodeStyleSetFlexBasisAuto(long node) {bYGNodeStyleSetFlexBasisAuto((int)node);}
@JSBody(params={"node"},script="valthorneHost.yoga.get(node).setFlexBasisAuto();") private static native void bYGNodeStyleSetFlexBasisAuto(int node);
public static void YGNodeStyleSetFlexBasisPercent(long node,float flexBasis) {bYGNodeStyleSetFlexBasisPercent((int)node,flexBasis);}
@JSBody(params={"node","flexBasis"},script="valthorneHost.yoga.get(node).setFlexBasisPercent(flexBasis);") private static native void bYGNodeStyleSetFlexBasisPercent(int node,float flexBasis);
public static void YGNodeStyleSetFlexDirection(long node,int flexDirection) {bYGNodeStyleSetFlexDirection((int)node,flexDirection);}
@JSBody(params={"node","flexDirection"},script="valthorneHost.yoga.get(node).setFlexDirection(flexDirection);") private static native void bYGNodeStyleSetFlexDirection(int node,int flexDirection);
public static void YGNodeStyleSetFlexGrow(long node,float flexGrow) {bYGNodeStyleSetFlexGrow((int)node,flexGrow);}
@JSBody(params={"node","flexGrow"},script="valthorneHost.yoga.get(node).setFlexGrow(flexGrow);") private static native void bYGNodeStyleSetFlexGrow(int node,float flexGrow);
public static void YGNodeStyleSetFlexShrink(long node,float flexShrink) {bYGNodeStyleSetFlexShrink((int)node,flexShrink);}
@JSBody(params={"node","flexShrink"},script="valthorneHost.yoga.get(node).setFlexShrink(flexShrink);") private static native void bYGNodeStyleSetFlexShrink(int node,float flexShrink);
public static void YGNodeStyleSetFlexWrap(long node,int flexWrap) {bYGNodeStyleSetFlexWrap((int)node,flexWrap);}
@JSBody(params={"node","flexWrap"},script="valthorneHost.yoga.get(node).setFlexWrap(flexWrap);") private static native void bYGNodeStyleSetFlexWrap(int node,int flexWrap);
public static void YGNodeStyleSetGap(long node,int gutter,float gapLength) {bYGNodeStyleSetGap((int)node,gutter,gapLength);}
@JSBody(params={"node","gutter","gapLength"},script="valthorneHost.yoga.get(node).setGap(gutter,gapLength);") private static native void bYGNodeStyleSetGap(int node,int gutter,float gapLength);
public static void YGNodeStyleSetHeight(long node,float height) {bYGNodeStyleSetHeight((int)node,height);}
@JSBody(params={"node","height"},script="valthorneHost.yoga.get(node).setHeight(height);") private static native void bYGNodeStyleSetHeight(int node,float height);
public static void YGNodeStyleSetHeightAuto(long node) {bYGNodeStyleSetHeightAuto((int)node);}
@JSBody(params={"node"},script="valthorneHost.yoga.get(node).setHeightAuto();") private static native void bYGNodeStyleSetHeightAuto(int node);
public static void YGNodeStyleSetHeightPercent(long node,float height) {bYGNodeStyleSetHeightPercent((int)node,height);}
@JSBody(params={"node","height"},script="valthorneHost.yoga.get(node).setHeightPercent(height);") private static native void bYGNodeStyleSetHeightPercent(int node,float height);
public static void YGNodeStyleSetJustifyContent(long node,int justifyContent) {bYGNodeStyleSetJustifyContent((int)node,justifyContent);}
@JSBody(params={"node","justifyContent"},script="valthorneHost.yoga.get(node).setJustifyContent(justifyContent);") private static native void bYGNodeStyleSetJustifyContent(int node,int justifyContent);
public static void YGNodeStyleSetMargin(long node,int edge,float margin) {bYGNodeStyleSetMargin((int)node,edge,margin);}
@JSBody(params={"node","edge","margin"},script="valthorneHost.yoga.get(node).setMargin(edge,margin);") private static native void bYGNodeStyleSetMargin(int node,int edge,float margin);
public static void YGNodeStyleSetMarginPercent(long node,int edge,float margin) {bYGNodeStyleSetMarginPercent((int)node,edge,margin);}
@JSBody(params={"node","edge","margin"},script="valthorneHost.yoga.get(node).setMarginPercent(edge,margin);") private static native void bYGNodeStyleSetMarginPercent(int node,int edge,float margin);
public static void YGNodeStyleSetMaxHeight(long node,float maxHeight) {bYGNodeStyleSetMaxHeight((int)node,maxHeight);}
@JSBody(params={"node","maxHeight"},script="valthorneHost.yoga.get(node).setMaxHeight(maxHeight);") private static native void bYGNodeStyleSetMaxHeight(int node,float maxHeight);
public static void YGNodeStyleSetMaxHeightPercent(long node,float maxHeight) {bYGNodeStyleSetMaxHeightPercent((int)node,maxHeight);}
@JSBody(params={"node","maxHeight"},script="valthorneHost.yoga.get(node).setMaxHeightPercent(maxHeight);") private static native void bYGNodeStyleSetMaxHeightPercent(int node,float maxHeight);
public static void YGNodeStyleSetMaxWidth(long node,float maxWidth) {bYGNodeStyleSetMaxWidth((int)node,maxWidth);}
@JSBody(params={"node","maxWidth"},script="valthorneHost.yoga.get(node).setMaxWidth(maxWidth);") private static native void bYGNodeStyleSetMaxWidth(int node,float maxWidth);
public static void YGNodeStyleSetMaxWidthPercent(long node,float maxWidth) {bYGNodeStyleSetMaxWidthPercent((int)node,maxWidth);}
@JSBody(params={"node","maxWidth"},script="valthorneHost.yoga.get(node).setMaxWidthPercent(maxWidth);") private static native void bYGNodeStyleSetMaxWidthPercent(int node,float maxWidth);
public static void YGNodeStyleSetMinHeight(long node,float minHeight) {bYGNodeStyleSetMinHeight((int)node,minHeight);}
@JSBody(params={"node","minHeight"},script="valthorneHost.yoga.get(node).setMinHeight(minHeight);") private static native void bYGNodeStyleSetMinHeight(int node,float minHeight);
public static void YGNodeStyleSetMinHeightPercent(long node,float minHeight) {bYGNodeStyleSetMinHeightPercent((int)node,minHeight);}
@JSBody(params={"node","minHeight"},script="valthorneHost.yoga.get(node).setMinHeightPercent(minHeight);") private static native void bYGNodeStyleSetMinHeightPercent(int node,float minHeight);
public static void YGNodeStyleSetMinWidth(long node,float minWidth) {bYGNodeStyleSetMinWidth((int)node,minWidth);}
@JSBody(params={"node","minWidth"},script="valthorneHost.yoga.get(node).setMinWidth(minWidth);") private static native void bYGNodeStyleSetMinWidth(int node,float minWidth);
public static void YGNodeStyleSetMinWidthPercent(long node,float minWidth) {bYGNodeStyleSetMinWidthPercent((int)node,minWidth);}
@JSBody(params={"node","minWidth"},script="valthorneHost.yoga.get(node).setMinWidthPercent(minWidth);") private static native void bYGNodeStyleSetMinWidthPercent(int node,float minWidth);
public static void YGNodeStyleSetPadding(long node,int edge,float padding) {bYGNodeStyleSetPadding((int)node,edge,padding);}
@JSBody(params={"node","edge","padding"},script="valthorneHost.yoga.get(node).setPadding(edge,padding);") private static native void bYGNodeStyleSetPadding(int node,int edge,float padding);
public static void YGNodeStyleSetPaddingPercent(long node,int edge,float padding) {bYGNodeStyleSetPaddingPercent((int)node,edge,padding);}
@JSBody(params={"node","edge","padding"},script="valthorneHost.yoga.get(node).setPaddingPercent(edge,padding);") private static native void bYGNodeStyleSetPaddingPercent(int node,int edge,float padding);
public static void YGNodeStyleSetPosition(long node,int edge,float position) {bYGNodeStyleSetPosition((int)node,edge,position);}
@JSBody(params={"node","edge","position"},script="valthorneHost.yoga.get(node).setPosition(edge,position);") private static native void bYGNodeStyleSetPosition(int node,int edge,float position);
public static void YGNodeStyleSetPositionPercent(long node,int edge,float position) {bYGNodeStyleSetPositionPercent((int)node,edge,position);}
@JSBody(params={"node","edge","position"},script="valthorneHost.yoga.get(node).setPositionPercent(edge,position);") private static native void bYGNodeStyleSetPositionPercent(int node,int edge,float position);
public static void YGNodeStyleSetPositionType(long node,int positionType) {bYGNodeStyleSetPositionType((int)node,positionType);}
@JSBody(params={"node","positionType"},script="valthorneHost.yoga.get(node).setPositionType(positionType);") private static native void bYGNodeStyleSetPositionType(int node,int positionType);
public static void YGNodeStyleSetWidth(long node,float width) {bYGNodeStyleSetWidth((int)node,width);}
@JSBody(params={"node","width"},script="valthorneHost.yoga.get(node).setWidth(width);") private static native void bYGNodeStyleSetWidth(int node,float width);
public static void YGNodeStyleSetWidthAuto(long node) {bYGNodeStyleSetWidthAuto((int)node);}
@JSBody(params={"node"},script="valthorneHost.yoga.get(node).setWidthAuto();") private static native void bYGNodeStyleSetWidthAuto(int node);
public static void YGNodeStyleSetWidthPercent(long node,float width) {bYGNodeStyleSetWidthPercent((int)node,width);}
@JSBody(params={"node","width"},script="valthorneHost.yoga.get(node).setWidthPercent(width);") private static native void bYGNodeStyleSetWidthPercent(int node,float width);
public static final int YGOverflowHidden = 1;
public static final int YGOverflowScroll = 2;
public static final int YGOverflowVisible = 0;
public static final int YGPositionTypeAbsolute = 2;
public static final int YGPositionTypeRelative = 1;
public static final int YGWrapNoWrap = 0;
public static final int YGWrapReverse = 2;
public static final int YGWrapWrap = 1;
}