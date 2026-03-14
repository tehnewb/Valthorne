package valthorne.ui;

import valthorne.ui.enums.*;

import java.util.Objects;

public class Layout {

    private LayoutValue width = LayoutValue.auto();
    private LayoutValue height = LayoutValue.auto();
    private LayoutValue minWidth = LayoutValue.auto();
    private LayoutValue minHeight = LayoutValue.auto();
    private LayoutValue maxWidth = LayoutValue.auto();
    private LayoutValue maxHeight = LayoutValue.auto();

    private LayoutValue left = LayoutValue.auto();
    private LayoutValue top = LayoutValue.auto();
    private LayoutValue right = LayoutValue.auto();
    private LayoutValue bottom = LayoutValue.auto();

    private LayoutValue marginLeft = LayoutValue.points(0f);
    private LayoutValue marginTop = LayoutValue.points(0f);
    private LayoutValue marginRight = LayoutValue.points(0f);
    private LayoutValue marginBottom = LayoutValue.points(0f);

    private LayoutValue paddingLeft = LayoutValue.points(0f);
    private LayoutValue paddingTop = LayoutValue.points(0f);
    private LayoutValue paddingRight = LayoutValue.points(0f);
    private LayoutValue paddingBottom = LayoutValue.points(0f);

    private float flexGrow;
    private float flexShrink;
    private LayoutValue flexBasis = LayoutValue.auto();

    private FlexDirection flexDirection = FlexDirection.COLUMN;
    private JustifyContent justifyContent = JustifyContent.FLEX_START;
    private Align alignItems = Align.STRETCH;
    private Align alignSelf = Align.AUTO;
    private PositionType positionType = PositionType.RELATIVE;
    private FlexWrap flexWrap = FlexWrap.NO_WRAP;
    private float rowGap;
    private float columnGap;

    public LayoutValue getWidth() {
        return width;
    }

    public Layout width(LayoutValue width) {
        this.width = normalize(width);
        return this;
    }

    public Layout width(float width) {
        this.width = LayoutValue.points(width);
        return this;
    }

    public Layout widthPercent(float width) {
        this.width = LayoutValue.percent(width);
        return this;
    }

    public Layout widthAuto() {
        this.width = LayoutValue.auto();
        return this;
    }

    public Layout fillWidth() {
        return widthPercent(100f);
    }

    public Layout widthFill() {
        return flexGrow(1f);
    }

    public LayoutValue getHeight() {
        return height;
    }

    public Layout height(LayoutValue height) {
        this.height = normalize(height);
        return this;
    }

    public Layout height(float height) {
        this.height = LayoutValue.points(height);
        return this;
    }

    public Layout heightPercent(float height) {
        this.height = LayoutValue.percent(height);
        return this;
    }

    public Layout heightAuto() {
        this.height = LayoutValue.auto();
        return this;
    }

    public Layout fillHeight() {
        return heightPercent(100f);
    }

    public Layout heightFill() {
        return flexGrow(1f);
    }

    public LayoutValue getMinWidth() {
        return minWidth;
    }

    public Layout minWidth(LayoutValue minWidth) {
        this.minWidth = normalize(minWidth);
        return this;
    }

    public Layout minWidth(float minWidth) {
        this.minWidth = LayoutValue.points(minWidth);
        return this;
    }

    public Layout minWidthPercent(float minWidth) {
        this.minWidth = LayoutValue.percent(minWidth);
        return this;
    }

    public Layout minWidthAuto() {
        this.minWidth = LayoutValue.auto();
        return this;
    }

    public LayoutValue getMinHeight() {
        return minHeight;
    }

    public Layout minHeight(LayoutValue minHeight) {
        this.minHeight = normalize(minHeight);
        return this;
    }

    public Layout minHeight(float minHeight) {
        this.minHeight = LayoutValue.points(minHeight);
        return this;
    }

    public Layout minHeightPercent(float minHeight) {
        this.minHeight = LayoutValue.percent(minHeight);
        return this;
    }

    public Layout minHeightAuto() {
        this.minHeight = LayoutValue.auto();
        return this;
    }

    public LayoutValue getMaxWidth() {
        return maxWidth;
    }

    public Layout maxWidth(LayoutValue maxWidth) {
        this.maxWidth = normalize(maxWidth);
        return this;
    }

    public Layout maxWidth(float maxWidth) {
        this.maxWidth = LayoutValue.points(maxWidth);
        return this;
    }

    public Layout maxWidthPercent(float maxWidth) {
        this.maxWidth = LayoutValue.percent(maxWidth);
        return this;
    }

    public Layout maxWidthAuto() {
        this.maxWidth = LayoutValue.auto();
        return this;
    }

    public LayoutValue getMaxHeight() {
        return maxHeight;
    }

    public Layout maxHeight(LayoutValue maxHeight) {
        this.maxHeight = normalize(maxHeight);
        return this;
    }

    public Layout maxHeight(float maxHeight) {
        this.maxHeight = LayoutValue.points(maxHeight);
        return this;
    }

    public Layout maxHeightPercent(float maxHeight) {
        this.maxHeight = LayoutValue.percent(maxHeight);
        return this;
    }

    public Layout maxHeightAuto() {
        this.maxHeight = LayoutValue.auto();
        return this;
    }

    public LayoutValue getLeft() {
        return left;
    }

    public Layout left(LayoutValue left) {
        this.left = normalize(left);
        return this;
    }

    public Layout left(float left) {
        this.left = LayoutValue.points(left);
        return this;
    }

    public Layout leftPercent(float left) {
        this.left = LayoutValue.percent(left);
        return this;
    }

    public Layout leftAuto() {
        this.left = LayoutValue.auto();
        return this;
    }

    public LayoutValue getTop() {
        return top;
    }

    public Layout top(LayoutValue top) {
        this.top = normalize(top);
        return this;
    }

    public Layout top(float top) {
        this.top = LayoutValue.points(top);
        return this;
    }

    public Layout topPercent(float top) {
        this.top = LayoutValue.percent(top);
        return this;
    }

    public Layout topAuto() {
        this.top = LayoutValue.auto();
        return this;
    }

    public LayoutValue getRight() {
        return right;
    }

    public Layout right(LayoutValue right) {
        this.right = normalize(right);
        return this;
    }

    public Layout right(float right) {
        this.right = LayoutValue.points(right);
        return this;
    }

    public Layout rightPercent(float right) {
        this.right = LayoutValue.percent(right);
        return this;
    }

    public Layout rightAuto() {
        this.right = LayoutValue.auto();
        return this;
    }

    public LayoutValue getBottom() {
        return bottom;
    }

    public Layout bottom(LayoutValue bottom) {
        this.bottom = normalize(bottom);
        return this;
    }

    public Layout bottom(float bottom) {
        this.bottom = LayoutValue.points(bottom);
        return this;
    }

    public Layout bottomPercent(float bottom) {
        this.bottom = LayoutValue.percent(bottom);
        return this;
    }

    public Layout bottomAuto() {
        this.bottom = LayoutValue.auto();
        return this;
    }

    public Layout inset(float inset) {
        return left(inset).top(inset).right(inset).bottom(inset);
    }

    public Layout inset(float horizontal, float vertical) {
        return left(horizontal).right(horizontal).top(vertical).bottom(vertical);
    }

    public Layout inset(float left, float top, float right, float bottom) {
        return left(left).top(top).right(right).bottom(bottom);
    }

    public Layout insetPercent(float inset) {
        return leftPercent(inset).topPercent(inset).rightPercent(inset).bottomPercent(inset);
    }

    public Layout insetPercent(float horizontal, float vertical) {
        return leftPercent(horizontal).rightPercent(horizontal).topPercent(vertical).bottomPercent(vertical);
    }

    public Layout size(LayoutValue width, LayoutValue height) {
        return width(width).height(height);
    }

    public Layout size(float width, float height) {
        return width(width).height(height);
    }

    public Layout sizePercent(float width, float height) {
        return widthPercent(width).heightPercent(height);
    }

    public Layout sizeAuto() {
        return widthAuto().heightAuto();
    }

    public Layout minSize(LayoutValue width, LayoutValue height) {
        return minWidth(width).minHeight(height);
    }

    public Layout minSize(float width, float height) {
        return minWidth(width).minHeight(height);
    }

    public Layout minSizePercent(float width, float height) {
        return minWidthPercent(width).minHeightPercent(height);
    }

    public Layout maxSize(LayoutValue width, LayoutValue height) {
        return maxWidth(width).maxHeight(height);
    }

    public Layout maxSize(float width, float height) {
        return maxWidth(width).maxHeight(height);
    }

    public Layout maxSizePercent(float width, float height) {
        return maxWidthPercent(width).maxHeightPercent(height);
    }

    public Layout widthRange(float minWidth, float maxWidth) {
        return minWidth(minWidth).maxWidth(maxWidth);
    }

    public Layout heightRange(float minHeight, float maxHeight) {
        return minHeight(minHeight).maxHeight(maxHeight);
    }

    public Layout position(float left, float top) {
        return left(left).top(top);
    }

    public Layout positionPercent(float left, float top) {
        return leftPercent(left).topPercent(top);
    }

    public Layout edgesAuto() {
        return leftAuto().topAuto().rightAuto().bottomAuto();
    }

    public LayoutValue getMarginLeft() {
        return marginLeft;
    }

    public Layout marginLeft(LayoutValue marginLeft) {
        this.marginLeft = normalize(marginLeft);
        return this;
    }

    public Layout marginLeft(float marginLeft) {
        this.marginLeft = LayoutValue.points(marginLeft);
        return this;
    }

    public Layout marginLeftPercent(float marginLeft) {
        this.marginLeft = LayoutValue.percent(marginLeft);
        return this;
    }

    public Layout marginLeftAuto() {
        this.marginLeft = LayoutValue.auto();
        return this;
    }

    public LayoutValue getMarginTop() {
        return marginTop;
    }

    public Layout marginTop(LayoutValue marginTop) {
        this.marginTop = normalize(marginTop);
        return this;
    }

    public Layout marginTop(float marginTop) {
        this.marginTop = LayoutValue.points(marginTop);
        return this;
    }

    public Layout marginTopPercent(float marginTop) {
        this.marginTop = LayoutValue.percent(marginTop);
        return this;
    }

    public Layout marginTopAuto() {
        this.marginTop = LayoutValue.auto();
        return this;
    }

    public LayoutValue getMarginRight() {
        return marginRight;
    }

    public Layout marginRight(LayoutValue marginRight) {
        this.marginRight = normalize(marginRight);
        return this;
    }

    public Layout marginRight(float marginRight) {
        this.marginRight = LayoutValue.points(marginRight);
        return this;
    }

    public Layout marginRightPercent(float marginRight) {
        this.marginRight = LayoutValue.percent(marginRight);
        return this;
    }

    public Layout marginRightAuto() {
        this.marginRight = LayoutValue.auto();
        return this;
    }

    public LayoutValue getMarginBottom() {
        return marginBottom;
    }

    public Layout marginBottom(LayoutValue marginBottom) {
        this.marginBottom = normalize(marginBottom);
        return this;
    }

    public Layout marginBottom(float marginBottom) {
        this.marginBottom = LayoutValue.points(marginBottom);
        return this;
    }

    public Layout marginBottomPercent(float marginBottom) {
        this.marginBottom = LayoutValue.percent(marginBottom);
        return this;
    }

    public Layout marginBottomAuto() {
        this.marginBottom = LayoutValue.auto();
        return this;
    }

    public Layout margin(LayoutValue margin) {
        LayoutValue value = normalize(margin);
        marginLeft = value;
        marginTop = value;
        marginRight = value;
        marginBottom = value;
        return this;
    }

    public Layout margin(float margin) {
        LayoutValue value = LayoutValue.points(margin);
        marginLeft = value;
        marginTop = value;
        marginRight = value;
        marginBottom = value;
        return this;
    }

    public Layout marginPercent(float margin) {
        LayoutValue value = LayoutValue.percent(margin);
        marginLeft = value;
        marginTop = value;
        marginRight = value;
        marginBottom = value;
        return this;
    }

    public Layout margin(float horizontal, float vertical) {
        return marginLeft(horizontal).marginRight(horizontal).marginTop(vertical).marginBottom(vertical);
    }

    public Layout margin(float left, float top, float right, float bottom) {
        return marginLeft(left).marginTop(top).marginRight(right).marginBottom(bottom);
    }

    public Layout marginPercent(float horizontal, float vertical) {
        return marginLeftPercent(horizontal).marginRightPercent(horizontal).marginTopPercent(vertical).marginBottomPercent(vertical);
    }

    public Layout marginPercent(float left, float top, float right, float bottom) {
        return marginLeftPercent(left).marginTopPercent(top).marginRightPercent(right).marginBottomPercent(bottom);
    }

    public Layout marginHorizontal(float margin) {
        return marginLeft(margin).marginRight(margin);
    }

    public Layout marginVertical(float margin) {
        return marginTop(margin).marginBottom(margin);
    }

    public Layout marginHorizontalPercent(float margin) {
        return marginLeftPercent(margin).marginRightPercent(margin);
    }

    public Layout marginVerticalPercent(float margin) {
        return marginTopPercent(margin).marginBottomPercent(margin);
    }

    public Layout marginAuto() {
        return marginLeftAuto().marginTopAuto().marginRightAuto().marginBottomAuto();
    }

    public Layout marginHorizontalAuto() {
        return marginLeftAuto().marginRightAuto();
    }

    public Layout marginVerticalAuto() {
        return marginTopAuto().marginBottomAuto();
    }

    public LayoutValue getPaddingLeft() {
        return paddingLeft;
    }

    public Layout paddingLeft(LayoutValue paddingLeft) {
        this.paddingLeft = normalize(paddingLeft);
        return this;
    }

    public Layout paddingLeft(float paddingLeft) {
        this.paddingLeft = LayoutValue.points(paddingLeft);
        return this;
    }

    public Layout paddingLeftPercent(float paddingLeft) {
        this.paddingLeft = LayoutValue.percent(paddingLeft);
        return this;
    }

    public LayoutValue getPaddingTop() {
        return paddingTop;
    }

    public Layout paddingTop(LayoutValue paddingTop) {
        this.paddingTop = normalize(paddingTop);
        return this;
    }

    public Layout paddingTop(float paddingTop) {
        this.paddingTop = LayoutValue.points(paddingTop);
        return this;
    }

    public Layout paddingTopPercent(float paddingTop) {
        this.paddingTop = LayoutValue.percent(paddingTop);
        return this;
    }

    public LayoutValue getPaddingRight() {
        return paddingRight;
    }

    public Layout paddingRight(LayoutValue paddingRight) {
        this.paddingRight = normalize(paddingRight);
        return this;
    }

    public Layout paddingRight(float paddingRight) {
        this.paddingRight = LayoutValue.points(paddingRight);
        return this;
    }

    public Layout paddingRightPercent(float paddingRight) {
        this.paddingRight = LayoutValue.percent(paddingRight);
        return this;
    }

    public LayoutValue getPaddingBottom() {
        return paddingBottom;
    }

    public Layout paddingBottom(LayoutValue paddingBottom) {
        this.paddingBottom = normalize(paddingBottom);
        return this;
    }

    public Layout paddingBottom(float paddingBottom) {
        this.paddingBottom = LayoutValue.points(paddingBottom);
        return this;
    }

    public Layout paddingBottomPercent(float paddingBottom) {
        this.paddingBottom = LayoutValue.percent(paddingBottom);
        return this;
    }

    public Layout padding(LayoutValue padding) {
        LayoutValue value = normalize(padding);
        paddingLeft = value;
        paddingTop = value;
        paddingRight = value;
        paddingBottom = value;
        return this;
    }

    public Layout padding(float padding) {
        LayoutValue value = LayoutValue.points(padding);
        paddingLeft = value;
        paddingTop = value;
        paddingRight = value;
        paddingBottom = value;
        return this;
    }

    public Layout paddingPercent(float padding) {
        LayoutValue value = LayoutValue.percent(padding);
        paddingLeft = value;
        paddingTop = value;
        paddingRight = value;
        paddingBottom = value;
        return this;
    }

    public Layout padding(float horizontal, float vertical) {
        return paddingLeft(horizontal).paddingRight(horizontal).paddingTop(vertical).paddingBottom(vertical);
    }

    public Layout padding(float left, float top, float right, float bottom) {
        return paddingLeft(left).paddingTop(top).paddingRight(right).paddingBottom(bottom);
    }

    public Layout paddingPercent(float horizontal, float vertical) {
        return paddingLeftPercent(horizontal).paddingRightPercent(horizontal).paddingTopPercent(vertical).paddingBottomPercent(vertical);
    }

    public Layout paddingPercent(float left, float top, float right, float bottom) {
        return paddingLeftPercent(left).paddingTopPercent(top).paddingRightPercent(right).paddingBottomPercent(bottom);
    }

    public Layout paddingHorizontal(float padding) {
        return paddingLeft(padding).paddingRight(padding);
    }

    public Layout paddingVertical(float padding) {
        return paddingTop(padding).paddingBottom(padding);
    }

    public Layout paddingHorizontalPercent(float padding) {
        return paddingLeftPercent(padding).paddingRightPercent(padding);
    }

    public Layout paddingVerticalPercent(float padding) {
        return paddingTopPercent(padding).paddingBottomPercent(padding);
    }

    public float getFlexGrow() {
        return flexGrow;
    }

    public Layout flexGrow(float flexGrow) {
        this.flexGrow = flexGrow;
        return this;
    }

    public Layout grow() {
        this.flexGrow = 1f;
        return this;
    }

    public Layout grow(float amount) {
        this.flexGrow = amount;
        return this;
    }

    public Layout noGrow() {
        this.flexGrow = 0f;
        return this;
    }

    public float getFlexShrink() {
        return flexShrink;
    }

    public Layout flexShrink(float flexShrink) {
        this.flexShrink = flexShrink;
        return this;
    }

    public Layout shrink() {
        this.flexShrink = 1f;
        return this;
    }

    public Layout shrink(float amount) {
        this.flexShrink = amount;
        return this;
    }

    public Layout noShrink() {
        this.flexShrink = 0f;
        return this;
    }

    public LayoutValue getFlexBasis() {
        return flexBasis;
    }

    public Layout flexBasis(LayoutValue flexBasis) {
        this.flexBasis = normalize(flexBasis);
        return this;
    }

    public Layout flexBasis(float flexBasis) {
        this.flexBasis = LayoutValue.points(flexBasis);
        return this;
    }

    public Layout flexBasisPercent(float flexBasis) {
        this.flexBasis = LayoutValue.percent(flexBasis);
        return this;
    }

    public Layout flexBasisAuto() {
        this.flexBasis = LayoutValue.auto();
        return this;
    }

    public Layout fill() {
        return flexGrow(1f).flexShrink(1f).flexBasisAuto();
    }

    public Layout fillX() {
        return flexGrow(1f).widthAuto();
    }

    public Layout fillY() {
        return flexGrow(1f).heightAuto();
    }

    public Layout fit() {
        return noGrow().noShrink().widthAuto().heightAuto();
    }

    public FlexDirection getFlexDirection() {
        return flexDirection;
    }

    public Layout flexDirection(FlexDirection flexDirection) {
        this.flexDirection = Objects.requireNonNull(flexDirection);
        return this;
    }

    public Layout row() {
        this.flexDirection = FlexDirection.ROW;
        return this;
    }

    public Layout column() {
        this.flexDirection = FlexDirection.COLUMN;
        return this;
    }

    public Layout rowReverse() {
        this.flexDirection = FlexDirection.ROW_REVERSE;
        return this;
    }

    public Layout columnReverse() {
        this.flexDirection = FlexDirection.COLUMN_REVERSE;
        return this;
    }

    public Layout centerContent() {
        return justifyCenter().itemsCenter();
    }

    public JustifyContent getJustifyContent() {
        return justifyContent;
    }

    public Layout justifyContent(JustifyContent justifyContent) {
        this.justifyContent = Objects.requireNonNull(justifyContent);
        return this;
    }

    public Layout justifyStart() {
        this.justifyContent = JustifyContent.FLEX_START;
        return this;
    }

    public Layout justifyCenter() {
        this.justifyContent = JustifyContent.CENTER;
        return this;
    }

    public Layout justifyEnd() {
        this.justifyContent = JustifyContent.FLEX_END;
        return this;
    }

    public Layout justifyBetween() {
        this.justifyContent = JustifyContent.SPACE_BETWEEN;
        return this;
    }

    public Layout justifyAround() {
        this.justifyContent = JustifyContent.SPACE_AROUND;
        return this;
    }

    public Layout justifyEvenly() {
        this.justifyContent = JustifyContent.SPACE_EVENLY;
        return this;
    }

    public Align getAlignItems() {
        return alignItems;
    }

    public Layout alignItems(Align alignItems) {
        this.alignItems = Objects.requireNonNull(alignItems);
        return this;
    }

    public Layout itemsAuto() {
        this.alignItems = Align.AUTO;
        return this;
    }

    public Layout itemsStart() {
        this.alignItems = Align.FLEX_START;
        return this;
    }

    public Layout itemsCenter() {
        this.alignItems = Align.CENTER;
        return this;
    }

    public Layout itemsEnd() {
        this.alignItems = Align.FLEX_END;
        return this;
    }

    public Layout itemsStretch() {
        this.alignItems = Align.STRETCH;
        return this;
    }

    public Layout itemsBaseline() {
        this.alignItems = Align.BASELINE;
        return this;
    }

    public Layout itemsSpaceBetween() {
        this.alignItems = Align.SPACE_BETWEEN;
        return this;
    }

    public Layout itemsSpaceAround() {
        this.alignItems = Align.SPACE_AROUND;
        return this;
    }

    public Align getAlignSelf() {
        return alignSelf;
    }

    public Layout alignSelf(Align alignSelf) {
        this.alignSelf = Objects.requireNonNull(alignSelf);
        return this;
    }

    public Layout selfAuto() {
        this.alignSelf = Align.AUTO;
        return this;
    }

    public Layout selfStart() {
        this.alignSelf = Align.FLEX_START;
        return this;
    }

    public Layout selfCenter() {
        this.alignSelf = Align.CENTER;
        return this;
    }

    public Layout selfEnd() {
        this.alignSelf = Align.FLEX_END;
        return this;
    }

    public Layout selfStretch() {
        this.alignSelf = Align.STRETCH;
        return this;
    }

    public Layout selfBaseline() {
        this.alignSelf = Align.BASELINE;
        return this;
    }

    public Layout selfSpaceBetween() {
        this.alignSelf = Align.SPACE_BETWEEN;
        return this;
    }

    public Layout selfSpaceAround() {
        this.alignSelf = Align.SPACE_AROUND;
        return this;
    }

    public PositionType getPositionType() {
        return positionType;
    }

    public Layout positionType(PositionType positionType) {
        this.positionType = Objects.requireNonNull(positionType);
        return this;
    }

    public Layout relative() {
        positionType = PositionType.RELATIVE;
        return this;
    }

    public Layout absolute() {
        positionType = PositionType.ABSOLUTE;
        return this;
    }

    public FlexWrap getFlexWrap() {
        return flexWrap;
    }

    public Layout flexWrap(FlexWrap flexWrap) {
        this.flexWrap = Objects.requireNonNull(flexWrap);
        return this;
    }

    public Layout wrap() {
        this.flexWrap = FlexWrap.WRAP;
        return this;
    }

    public Layout noWrap() {
        this.flexWrap = FlexWrap.NO_WRAP;
        return this;
    }

    public Layout wrapReverse() {
        this.flexWrap = FlexWrap.WRAP_REVERSE;
        return this;
    }

    public float getRowGap() {
        return rowGap;
    }

    public Layout rowGap(float rowGap) {
        this.rowGap = rowGap;
        return this;
    }

    public float getColumnGap() {
        return columnGap;
    }

    public Layout columnGap(float columnGap) {
        this.columnGap = columnGap;
        return this;
    }

    public Layout gap(float gap) {
        this.rowGap = gap;
        this.columnGap = gap;
        return this;
    }

    public Layout resetSize() {
        width = LayoutValue.auto();
        height = LayoutValue.auto();
        minWidth = LayoutValue.auto();
        minHeight = LayoutValue.auto();
        maxWidth = LayoutValue.auto();
        maxHeight = LayoutValue.auto();
        return this;
    }

    public Layout resetPosition() {
        left = LayoutValue.auto();
        top = LayoutValue.auto();
        right = LayoutValue.auto();
        bottom = LayoutValue.auto();
        return this;
    }

    public Layout resetMargin() {
        marginLeft = LayoutValue.points(0f);
        marginTop = LayoutValue.points(0f);
        marginRight = LayoutValue.points(0f);
        marginBottom = LayoutValue.points(0f);
        return this;
    }

    public Layout resetPadding() {
        paddingLeft = LayoutValue.points(0f);
        paddingTop = LayoutValue.points(0f);
        paddingRight = LayoutValue.points(0f);
        paddingBottom = LayoutValue.points(0f);
        return this;
    }

    public Layout resetFlex() {
        flexGrow = 0f;
        flexShrink = 0f;
        flexBasis = LayoutValue.auto();
        flexDirection = FlexDirection.COLUMN;
        justifyContent = JustifyContent.FLEX_START;
        alignItems = Align.STRETCH;
        alignSelf = Align.AUTO;
        positionType = PositionType.RELATIVE;
        flexWrap = FlexWrap.NO_WRAP;
        rowGap = 0f;
        columnGap = 0f;
        return this;
    }

    public Layout reset() {
        return resetSize()
                .resetPosition()
                .resetMargin()
                .resetPadding()
                .resetFlex();
    }

    private static LayoutValue normalize(LayoutValue value) {
        return Objects.requireNonNull(value);
    }
}