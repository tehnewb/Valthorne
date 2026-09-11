package valthorne.ui;

import valthorne.ui.enums.*;

import java.util.Objects;

/**
 * Mutable layout configuration containing dimensions, edge offsets, spacing, and
 * flexbox policy for a UINode. This object stores values; the root's layout solver
 * resolves percentages, auto sizing, positioning, and flex distribution later.
 * It starts as a relative nonwrapping column with stretched children, zero gaps,
 * zero grow/shrink factors, and automatic size/position constraints.
 *
 * <p>Most setters notify the supplied callback synchronously only when stored
 * values differ. Numeric values are retained without range/finiteness checks.
 * Composite helpers may notify several times and are not transactional. Single-value
 * margin and padding helpers assign all four sides without calling the callback;
 * when using those on an already laid-out node, request layout invalidation explicitly.
 * Callback failures leave earlier assignments in place.</p>
 *
 * <p>Percent values use 100 for the whole solver-selected reference extent.
 * LayoutValue objects are immutable and shared safely; this Layout is mutable
 * and intended for the UI thread. A widthFill or heightFill request sets flex
 * growth on the parent's main axis rather than a percentage on the named axis.</p>
 *
 * <pre>{@code
 * Panel panel = new Panel();
 * panel.getLayout().column().width(400).height(300).gap(8);
 * panel.getLayout().padding(12);
 * panel.markLayoutDirty(); // Uniform padding does not notify the callback.
 * Button button = new Button("Apply");
 * button.getLayout().fillWidth().height(36).noShrink();
 * panel.add(button);
 * }</pre>
 *
 * @author Albert Beaupre
 */
public class Layout {
    private final Runnable onChange; // Retained synchronous invalidation callback; uniform margin/padding bypass it.
    private LayoutValue width = LayoutValue.auto(); // Immutable preferred width specification resolved by the layout solver.
    private LayoutValue height = LayoutValue.auto(); // Immutable preferred height specification resolved by the layout solver.
    private LayoutValue minWidth = LayoutValue.auto(); // Immutable minimum width constraint specification resolved by the layout solver.
    private LayoutValue minHeight = LayoutValue.auto(); // Immutable minimum height constraint specification resolved by the layout solver.
    private LayoutValue maxWidth = LayoutValue.auto(); // Immutable maximum width constraint specification resolved by the layout solver.
    private LayoutValue maxHeight = LayoutValue.auto(); // Immutable maximum height constraint specification resolved by the layout solver.
    private LayoutValue left = LayoutValue.auto(); // Immutable left position offset specification resolved by the layout solver.
    private LayoutValue top = LayoutValue.auto(); // Immutable top position offset specification resolved by the layout solver.
    private LayoutValue right = LayoutValue.auto(); // Immutable right position offset specification resolved by the layout solver.
    private LayoutValue bottom = LayoutValue.auto(); // Immutable bottom position offset specification resolved by the layout solver.
    private LayoutValue marginLeft = LayoutValue.points(0f); // Immutable left outer margin specification resolved by the layout solver.
    private LayoutValue marginTop = LayoutValue.points(0f); // Immutable top outer margin specification resolved by the layout solver.
    private LayoutValue marginRight = LayoutValue.points(0f); // Immutable right outer margin specification resolved by the layout solver.
    private LayoutValue marginBottom = LayoutValue.points(0f); // Immutable bottom outer margin specification resolved by the layout solver.
    private LayoutValue paddingLeft = LayoutValue.points(0f); // Immutable left inner padding specification resolved by the layout solver.
    private LayoutValue paddingTop = LayoutValue.points(0f); // Immutable top inner padding specification resolved by the layout solver.
    private LayoutValue paddingRight = LayoutValue.points(0f); // Immutable right inner padding specification resolved by the layout solver.
    private LayoutValue paddingBottom = LayoutValue.points(0f); // Immutable bottom inner padding specification resolved by the layout solver.
    private float flexGrow; // Relative share of positive main-axis free space.
    private float flexShrink; // Relative shrink factor when main-axis space is insufficient.
    private LayoutValue flexBasis = LayoutValue.auto(); // Immutable initial main-axis size before flexible space distribution specification resolved by the layout solver.
    private FlexDirection flexDirection = FlexDirection.COLUMN; // Main-axis direction used to arrange children.
    private JustifyContent justifyContent = JustifyContent.FLEX_START; // Child distribution along the main axis.
    private Align alignItems = Align.STRETCH; // Default child alignment across the main axis.
    private Align alignSelf = Align.AUTO; // This item's cross-axis alignment override.
    private PositionType positionType = PositionType.RELATIVE; // Relative-flow or absolute positioning mode.
    private FlexWrap flexWrap = FlexWrap.NO_WRAP; // Child line-wrapping policy.
    private float rowGap; // Gap between layout rows in UI units.
    private float columnGap; // Gap between layout columns in UI units.
    /**
     * Creates default layout state with a no-op change callback. Mutations are retained
     * but do not themselves notify an owning node.
     */
    public Layout() {this(() -> {});}
    /**
     * Creates default layout state with a retained synchronous change callback.
     * Construction does not invoke the callback. Mutators document any notification
     * exceptions, including uniform margin/padding assignment.
     *
     * @param onChange non-null action used to invalidate the owning layout
     * @throws NullPointerException if onChange is null
     */
    public Layout(Runnable onChange) {this.onChange = Objects.requireNonNull(onChange);}

    /**
     * Validates a supplied immutable layout value and returns it without copying,
     * unit conversion, or numeric range checks.
     *
     * @param value candidate layout specification
     * @return the same non-null value
     * @throws NullPointerException if value is null
     */
    private static LayoutValue normalize(LayoutValue value) {
        return Objects.requireNonNull(value);
    }

    /**
     * Reads the stored preferred width without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current preferred width specification
     */
    public LayoutValue getWidth() {
        return width;
    }

    /**
     * Assigns the preferred width when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param width non-null preferred width specification
     * @return this layout
     * @throws NullPointerException if width is null
     */
    public Layout width(LayoutValue width) {
        if (!Objects.equals(this.width, normalize(width))) {
            this.width = normalize(width);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the preferred width in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param width requested preferred width in points
     * @return this layout
     */
    public Layout width(float width) {
        if (!(this.width.isPoints() && Float.compare(this.width.getValue(), width) == 0)) {
            this.width = LayoutValue.points(width);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the preferred width as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param width requested percentage value
     * @return this layout
     */
    public Layout widthPercent(float width) {
        if (!(this.width.isPercent() && Float.compare(this.width.getValue(), width) == 0)) {
            this.width = LayoutValue.percent(width);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the preferred width and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout widthAuto() {
        if (!Objects.equals(this.width, LayoutValue.auto())) {
            this.width = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Sets preferred width to 100 percent through widthPercent. Does not change
     * flex growth or height.
     *
     * @return this layout
     */
    public Layout fillWidth() {
        return widthPercent(100f);
    }

    /**
     * Sets flex growth to one without changing width. Growth follows the parent's
     * main axis, so this is not an alias for fillWidth.
     *
     * @return this layout
     */
    public Layout widthFill() {
        return flexGrow(1f);
    }

    /**
     * Reads the stored preferred height without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current preferred height specification
     */
    public LayoutValue getHeight() {
        return height;
    }

    /**
     * Assigns the preferred height when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param height non-null preferred height specification
     * @return this layout
     * @throws NullPointerException if height is null
     */
    public Layout height(LayoutValue height) {
        if (!Objects.equals(this.height, normalize(height))) {
            this.height = normalize(height);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the preferred height in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param height requested preferred height in points
     * @return this layout
     */
    public Layout height(float height) {
        if (!(this.height.isPoints() && Float.compare(this.height.getValue(), height) == 0)) {
            this.height = LayoutValue.points(height);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the preferred height as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param height requested percentage value
     * @return this layout
     */
    public Layout heightPercent(float height) {
        if (!(this.height.isPercent() && Float.compare(this.height.getValue(), height) == 0)) {
            this.height = LayoutValue.percent(height);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the preferred height and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout heightAuto() {
        if (!Objects.equals(this.height, LayoutValue.auto())) {
            this.height = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Sets preferred height to 100 percent through heightPercent. Does not change
     * flex growth or width.
     *
     * @return this layout
     */
    public Layout fillHeight() {
        return heightPercent(100f);
    }

    /**
     * Sets flex growth to one without changing height. Growth follows the parent's
     * main axis, so this is not an alias for fillHeight.
     *
     * @return this layout
     */
    public Layout heightFill() {
        return flexGrow(1f);
    }

    /**
     * Reads the stored minimum width constraint without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current minimum width constraint specification
     */
    public LayoutValue getMinWidth() {
        return minWidth;
    }

    /**
     * Assigns the minimum width constraint when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param minWidth non-null minimum width constraint specification
     * @return this layout
     * @throws NullPointerException if minWidth is null
     */
    public Layout minWidth(LayoutValue minWidth) {
        if (!Objects.equals(this.minWidth, normalize(minWidth))) {
            this.minWidth = normalize(minWidth);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the minimum width constraint in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param minWidth requested minimum width constraint in points
     * @return this layout
     */
    public Layout minWidth(float minWidth) {
        if (!(this.minWidth.isPoints() && Float.compare(this.minWidth.getValue(), minWidth) == 0)) {
            this.minWidth = LayoutValue.points(minWidth);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the minimum width constraint as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param minWidth requested percentage value
     * @return this layout
     */
    public Layout minWidthPercent(float minWidth) {
        if (!(this.minWidth.isPercent() && Float.compare(this.minWidth.getValue(), minWidth) == 0)) {
            this.minWidth = LayoutValue.percent(minWidth);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the minimum width constraint and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout minWidthAuto() {
        if (!Objects.equals(this.minWidth, LayoutValue.auto())) {
            this.minWidth = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored minimum height constraint without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current minimum height constraint specification
     */
    public LayoutValue getMinHeight() {
        return minHeight;
    }

    /**
     * Assigns the minimum height constraint when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param minHeight non-null minimum height constraint specification
     * @return this layout
     * @throws NullPointerException if minHeight is null
     */
    public Layout minHeight(LayoutValue minHeight) {
        if (!Objects.equals(this.minHeight, normalize(minHeight))) {
            this.minHeight = normalize(minHeight);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the minimum height constraint in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param minHeight requested minimum height constraint in points
     * @return this layout
     */
    public Layout minHeight(float minHeight) {
        if (!(this.minHeight.isPoints() && Float.compare(this.minHeight.getValue(), minHeight) == 0)) {
            this.minHeight = LayoutValue.points(minHeight);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the minimum height constraint as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param minHeight requested percentage value
     * @return this layout
     */
    public Layout minHeightPercent(float minHeight) {
        if (!(this.minHeight.isPercent() && Float.compare(this.minHeight.getValue(), minHeight) == 0)) {
            this.minHeight = LayoutValue.percent(minHeight);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the minimum height constraint and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout minHeightAuto() {
        if (!Objects.equals(this.minHeight, LayoutValue.auto())) {
            this.minHeight = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored maximum width constraint without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current maximum width constraint specification
     */
    public LayoutValue getMaxWidth() {
        return maxWidth;
    }

    /**
     * Assigns the maximum width constraint when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param maxWidth non-null maximum width constraint specification
     * @return this layout
     * @throws NullPointerException if maxWidth is null
     */
    public Layout maxWidth(LayoutValue maxWidth) {
        if (!Objects.equals(this.maxWidth, normalize(maxWidth))) {
            this.maxWidth = normalize(maxWidth);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the maximum width constraint in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param maxWidth requested maximum width constraint in points
     * @return this layout
     */
    public Layout maxWidth(float maxWidth) {
        if (!(this.maxWidth.isPoints() && Float.compare(this.maxWidth.getValue(), maxWidth) == 0)) {
            this.maxWidth = LayoutValue.points(maxWidth);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the maximum width constraint as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param maxWidth requested percentage value
     * @return this layout
     */
    public Layout maxWidthPercent(float maxWidth) {
        if (!(this.maxWidth.isPercent() && Float.compare(this.maxWidth.getValue(), maxWidth) == 0)) {
            this.maxWidth = LayoutValue.percent(maxWidth);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the maximum width constraint and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout maxWidthAuto() {
        if (!Objects.equals(this.maxWidth, LayoutValue.auto())) {
            this.maxWidth = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored maximum height constraint without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current maximum height constraint specification
     */
    public LayoutValue getMaxHeight() {
        return maxHeight;
    }

    /**
     * Assigns the maximum height constraint when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param maxHeight non-null maximum height constraint specification
     * @return this layout
     * @throws NullPointerException if maxHeight is null
     */
    public Layout maxHeight(LayoutValue maxHeight) {
        if (!Objects.equals(this.maxHeight, normalize(maxHeight))) {
            this.maxHeight = normalize(maxHeight);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the maximum height constraint in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param maxHeight requested maximum height constraint in points
     * @return this layout
     */
    public Layout maxHeight(float maxHeight) {
        if (!(this.maxHeight.isPoints() && Float.compare(this.maxHeight.getValue(), maxHeight) == 0)) {
            this.maxHeight = LayoutValue.points(maxHeight);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the maximum height constraint as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param maxHeight requested percentage value
     * @return this layout
     */
    public Layout maxHeightPercent(float maxHeight) {
        if (!(this.maxHeight.isPercent() && Float.compare(this.maxHeight.getValue(), maxHeight) == 0)) {
            this.maxHeight = LayoutValue.percent(maxHeight);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the maximum height constraint and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout maxHeightAuto() {
        if (!Objects.equals(this.maxHeight, LayoutValue.auto())) {
            this.maxHeight = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored left position offset without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current left position offset specification
     */
    public LayoutValue getLeft() {
        return left;
    }

    /**
     * Assigns the left position offset when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param left non-null left position offset specification
     * @return this layout
     * @throws NullPointerException if left is null
     */
    public Layout left(LayoutValue left) {
        if (!Objects.equals(this.left, normalize(left))) {
            this.left = normalize(left);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the left position offset in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param left requested left position offset in points
     * @return this layout
     */
    public Layout left(float left) {
        if (!(this.left.isPoints() && Float.compare(this.left.getValue(), left) == 0)) {
            this.left = LayoutValue.points(left);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the left position offset as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param left requested percentage value
     * @return this layout
     */
    public Layout leftPercent(float left) {
        if (!(this.left.isPercent() && Float.compare(this.left.getValue(), left) == 0)) {
            this.left = LayoutValue.percent(left);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the left position offset and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout leftAuto() {
        if (!Objects.equals(this.left, LayoutValue.auto())) {
            this.left = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored top position offset without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current top position offset specification
     */
    public LayoutValue getTop() {
        return top;
    }

    /**
     * Assigns the top position offset when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param top non-null top position offset specification
     * @return this layout
     * @throws NullPointerException if top is null
     */
    public Layout top(LayoutValue top) {
        if (!Objects.equals(this.top, normalize(top))) {
            this.top = normalize(top);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the top position offset in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param top requested top position offset in points
     * @return this layout
     */
    public Layout top(float top) {
        if (!(this.top.isPoints() && Float.compare(this.top.getValue(), top) == 0)) {
            this.top = LayoutValue.points(top);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the top position offset as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param top requested percentage value
     * @return this layout
     */
    public Layout topPercent(float top) {
        if (!(this.top.isPercent() && Float.compare(this.top.getValue(), top) == 0)) {
            this.top = LayoutValue.percent(top);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the top position offset and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout topAuto() {
        if (!Objects.equals(this.top, LayoutValue.auto())) {
            this.top = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored right position offset without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current right position offset specification
     */
    public LayoutValue getRight() {
        return right;
    }

    /**
     * Assigns the right position offset when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param right non-null right position offset specification
     * @return this layout
     * @throws NullPointerException if right is null
     */
    public Layout right(LayoutValue right) {
        if (!Objects.equals(this.right, normalize(right))) {
            this.right = normalize(right);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the right position offset in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param right requested right position offset in points
     * @return this layout
     */
    public Layout right(float right) {
        if (!(this.right.isPoints() && Float.compare(this.right.getValue(), right) == 0)) {
            this.right = LayoutValue.points(right);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the right position offset as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param right requested percentage value
     * @return this layout
     */
    public Layout rightPercent(float right) {
        if (!(this.right.isPercent() && Float.compare(this.right.getValue(), right) == 0)) {
            this.right = LayoutValue.percent(right);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the right position offset and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout rightAuto() {
        if (!Objects.equals(this.right, LayoutValue.auto())) {
            this.right = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored bottom position offset without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current bottom position offset specification
     */
    public LayoutValue getBottom() {
        return bottom;
    }

    /**
     * Assigns the bottom position offset when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param bottom non-null bottom position offset specification
     * @return this layout
     * @throws NullPointerException if bottom is null
     */
    public Layout bottom(LayoutValue bottom) {
        if (!Objects.equals(this.bottom, normalize(bottom))) {
            this.bottom = normalize(bottom);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the bottom position offset in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param bottom requested bottom position offset in points
     * @return this layout
     */
    public Layout bottom(float bottom) {
        if (!(this.bottom.isPoints() && Float.compare(this.bottom.getValue(), bottom) == 0)) {
            this.bottom = LayoutValue.points(bottom);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the bottom position offset as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param bottom requested percentage value
     * @return this layout
     */
    public Layout bottomPercent(float bottom) {
        if (!(this.bottom.isPercent() && Float.compare(this.bottom.getValue(), bottom) == 0)) {
            this.bottom = LayoutValue.percent(bottom);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the bottom position offset and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout bottomAuto() {
        if (!Objects.equals(this.bottom, LayoutValue.auto())) {
            this.bottom = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Sets all four position offsets from one value.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param inset position offsets value in UI points
     * @return this layout
     */
    public Layout inset(float inset) {
        return left(inset).top(inset).right(inset).bottom(inset);
    }

    /**
     * Sets all four position offsets from the supplied side values.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param horizontal value for left and right in UI points
     * @param vertical value for top and bottom in UI points
     * @return this layout
     */
    public Layout inset(float horizontal, float vertical) {
        return left(horizontal).right(horizontal).top(vertical).bottom(vertical);
    }

    /**
     * Sets all four position offsets from the supplied side values.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param left left side value in UI points
     * @param top top side value in UI points
     * @param right right side value in UI points
     * @param bottom bottom side value in UI points
     * @return this layout
     */
    public Layout inset(float left, float top, float right, float bottom) {
        return left(left).top(top).right(right).bottom(bottom);
    }

    /**
     * Sets all four position offsets from one value.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param inset position offsets value in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout insetPercent(float inset) {
        return leftPercent(inset).topPercent(inset).rightPercent(inset).bottomPercent(inset);
    }

    /**
     * Sets all four position offsets from the supplied side values.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param horizontal value for left and right in percent (100 denotes the full reference extent)
     * @param vertical value for top and bottom in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout insetPercent(float horizontal, float vertical) {
        return leftPercent(horizontal).rightPercent(horizontal).topPercent(vertical).bottomPercent(vertical);
    }

    /**
     * Sets preferred width and height through their individual setters.
     * Values retain their independent units and immutable identities.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param width non-null immutable specification
     * @param height non-null immutable specification
     * @return this layout
     * @throws NullPointerException if a supplied LayoutValue is null
     */
    public Layout size(LayoutValue width, LayoutValue height) {
        return width(width).height(height);
    }

    /**
     * Sets preferred width and height through their individual setters.
     * Inputs use UI points with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param width width in UI points
     * @param height height in UI points
     * @return this layout
     */
    public Layout size(float width, float height) {
        return width(width).height(height);
    }

    /**
     * Sets preferred width and height through their individual setters.
     * Inputs use percent (100 denotes the full reference extent) with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param width width in percent (100 denotes the full reference extent)
     * @param height height in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout sizePercent(float width, float height) {
        return widthPercent(width).heightPercent(height);
    }

    /**
     * Restores automatic preferred width and height without altering minimum or
     * maximum constraints. Each changed dimension can notify separately.
     *
     * @return this layout
     */
    public Layout sizeAuto() {
        return widthAuto().heightAuto();
    }

    /**
     * Sets minimum width and height constraints through their individual setters.
     * Values retain their independent units and immutable identities.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param width non-null immutable specification
     * @param height non-null immutable specification
     * @return this layout
     * @throws NullPointerException if a supplied LayoutValue is null
     */
    public Layout minSize(LayoutValue width, LayoutValue height) {
        return minWidth(width).minHeight(height);
    }

    /**
     * Sets minimum width and height constraints through their individual setters.
     * Inputs use UI points with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param width width in UI points
     * @param height height in UI points
     * @return this layout
     */
    public Layout minSize(float width, float height) {
        return minWidth(width).minHeight(height);
    }

    /**
     * Sets minimum width and height constraints through their individual setters.
     * Inputs use percent (100 denotes the full reference extent) with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param width width in percent (100 denotes the full reference extent)
     * @param height height in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout minSizePercent(float width, float height) {
        return minWidthPercent(width).minHeightPercent(height);
    }

    /**
     * Sets maximum width and height constraints through their individual setters.
     * Values retain their independent units and immutable identities.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param width non-null immutable specification
     * @param height non-null immutable specification
     * @return this layout
     * @throws NullPointerException if a supplied LayoutValue is null
     */
    public Layout maxSize(LayoutValue width, LayoutValue height) {
        return maxWidth(width).maxHeight(height);
    }

    /**
     * Sets maximum width and height constraints through their individual setters.
     * Inputs use UI points with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param width width in UI points
     * @param height height in UI points
     * @return this layout
     */
    public Layout maxSize(float width, float height) {
        return maxWidth(width).maxHeight(height);
    }

    /**
     * Sets maximum width and height constraints through their individual setters.
     * Inputs use percent (100 denotes the full reference extent) with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param width width in percent (100 denotes the full reference extent)
     * @param height height in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout maxSizePercent(float width, float height) {
        return maxWidthPercent(width).maxHeightPercent(height);
    }

    /**
     * Sets minimum and maximum width constraints through their individual setters.
     * Inputs use UI points with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param minWidth min width in UI points
     * @param maxWidth max width in UI points
     * @return this layout
     */
    public Layout widthRange(float minWidth, float maxWidth) {
        return minWidth(minWidth).maxWidth(maxWidth);
    }

    /**
     * Sets minimum and maximum height constraints through their individual setters.
     * Inputs use UI points with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param minHeight min height in UI points
     * @param maxHeight max height in UI points
     * @return this layout
     */
    public Layout heightRange(float minHeight, float maxHeight) {
        return minHeight(minHeight).maxHeight(maxHeight);
    }

    /**
     * Sets left and top position offsets through their individual setters.
     * Inputs use UI points with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param left left in UI points
     * @param top top in UI points
     * @return this layout
     */
    public Layout position(float left, float top) {
        return left(left).top(top);
    }

    /**
     * Sets left and top position offsets through their individual setters.
     * Inputs use percent (100 denotes the full reference extent) with no numeric validation or range ordering checks.
     * Each changed component can notify separately; an exception after the first
     * assignment does not roll it back. Unrelated layout properties are retained.
     *
     * @param left left in percent (100 denotes the full reference extent)
     * @param top top in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout positionPercent(float left, float top) {
        return leftPercent(left).topPercent(top);
    }

    /**
     * Restores all four position offsets to auto without changing positioning mode.
     * Each changed offset can invoke the callback separately.
     *
     * @return this layout
     */
    public Layout edgesAuto() {
        return leftAuto().topAuto().rightAuto().bottomAuto();
    }

    /**
     * Reads the stored left outer margin without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current left outer margin specification
     */
    public LayoutValue getMarginLeft() {
        return marginLeft;
    }

    /**
     * Assigns the left outer margin when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param marginLeft non-null left outer margin specification
     * @return this layout
     * @throws NullPointerException if marginLeft is null
     */
    public Layout marginLeft(LayoutValue marginLeft) {
        if (!Objects.equals(this.marginLeft, normalize(marginLeft))) {
            this.marginLeft = normalize(marginLeft);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the left outer margin in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param marginLeft requested left outer margin in points
     * @return this layout
     */
    public Layout marginLeft(float marginLeft) {
        if (!(this.marginLeft.isPoints() && Float.compare(this.marginLeft.getValue(), marginLeft) == 0)) {
            this.marginLeft = LayoutValue.points(marginLeft);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the left outer margin as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param marginLeft requested percentage value
     * @return this layout
     */
    public Layout marginLeftPercent(float marginLeft) {
        if (!(this.marginLeft.isPercent() && Float.compare(this.marginLeft.getValue(), marginLeft) == 0)) {
            this.marginLeft = LayoutValue.percent(marginLeft);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the left outer margin and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout marginLeftAuto() {
        if (!Objects.equals(this.marginLeft, LayoutValue.auto())) {
            this.marginLeft = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored top outer margin without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current top outer margin specification
     */
    public LayoutValue getMarginTop() {
        return marginTop;
    }

    /**
     * Assigns the top outer margin when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param marginTop non-null top outer margin specification
     * @return this layout
     * @throws NullPointerException if marginTop is null
     */
    public Layout marginTop(LayoutValue marginTop) {
        if (!Objects.equals(this.marginTop, normalize(marginTop))) {
            this.marginTop = normalize(marginTop);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the top outer margin in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param marginTop requested top outer margin in points
     * @return this layout
     */
    public Layout marginTop(float marginTop) {
        if (!(this.marginTop.isPoints() && Float.compare(this.marginTop.getValue(), marginTop) == 0)) {
            this.marginTop = LayoutValue.points(marginTop);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the top outer margin as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param marginTop requested percentage value
     * @return this layout
     */
    public Layout marginTopPercent(float marginTop) {
        if (!(this.marginTop.isPercent() && Float.compare(this.marginTop.getValue(), marginTop) == 0)) {
            this.marginTop = LayoutValue.percent(marginTop);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the top outer margin and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout marginTopAuto() {
        if (!Objects.equals(this.marginTop, LayoutValue.auto())) {
            this.marginTop = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored right outer margin without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current right outer margin specification
     */
    public LayoutValue getMarginRight() {
        return marginRight;
    }

    /**
     * Assigns the right outer margin when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param marginRight non-null right outer margin specification
     * @return this layout
     * @throws NullPointerException if marginRight is null
     */
    public Layout marginRight(LayoutValue marginRight) {
        if (!Objects.equals(this.marginRight, normalize(marginRight))) {
            this.marginRight = normalize(marginRight);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the right outer margin in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param marginRight requested right outer margin in points
     * @return this layout
     */
    public Layout marginRight(float marginRight) {
        if (!(this.marginRight.isPoints() && Float.compare(this.marginRight.getValue(), marginRight) == 0)) {
            this.marginRight = LayoutValue.points(marginRight);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the right outer margin as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param marginRight requested percentage value
     * @return this layout
     */
    public Layout marginRightPercent(float marginRight) {
        if (!(this.marginRight.isPercent() && Float.compare(this.marginRight.getValue(), marginRight) == 0)) {
            this.marginRight = LayoutValue.percent(marginRight);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the right outer margin and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout marginRightAuto() {
        if (!Objects.equals(this.marginRight, LayoutValue.auto())) {
            this.marginRight = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored bottom outer margin without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current bottom outer margin specification
     */
    public LayoutValue getMarginBottom() {
        return marginBottom;
    }

    /**
     * Assigns the bottom outer margin when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param marginBottom non-null bottom outer margin specification
     * @return this layout
     * @throws NullPointerException if marginBottom is null
     */
    public Layout marginBottom(LayoutValue marginBottom) {
        if (!Objects.equals(this.marginBottom, normalize(marginBottom))) {
            this.marginBottom = normalize(marginBottom);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the bottom outer margin in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param marginBottom requested bottom outer margin in points
     * @return this layout
     */
    public Layout marginBottom(float marginBottom) {
        if (!(this.marginBottom.isPoints() && Float.compare(this.marginBottom.getValue(), marginBottom) == 0)) {
            this.marginBottom = LayoutValue.points(marginBottom);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the bottom outer margin as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param marginBottom requested percentage value
     * @return this layout
     */
    public Layout marginBottomPercent(float marginBottom) {
        if (!(this.marginBottom.isPercent() && Float.compare(this.marginBottom.getValue(), marginBottom) == 0)) {
            this.marginBottom = LayoutValue.percent(marginBottom);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the bottom outer margin and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout marginBottomAuto() {
        if (!Objects.equals(this.marginBottom, LayoutValue.auto())) {
            this.marginBottom = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Sets all four outer margins from one value.
     * Retains the immutable unit-bearing value without resolving it.
     * Assigns all sides directly without invoking the change callback; explicitly
     * invalidate the owning node if this change requires a new layout.
     *
     * @param margin non-null immutable specification
     * @return this layout
     * @throws NullPointerException if a supplied LayoutValue is null
     */
    public Layout margin(LayoutValue margin) {
        LayoutValue value = normalize(margin);
        marginLeft = value;
        marginTop = value;
        marginRight = value;
        marginBottom = value;
        return this;
    }

    /**
     * Sets all four outer margins from one value.
     * Inputs use UI points and are retained without validation.
     * Assigns all sides directly without invoking the change callback; explicitly
     * invalidate the owning node if this change requires a new layout.
     *
     * @param margin outer margins value in UI points
     * @return this layout
     */
    public Layout margin(float margin) {
        LayoutValue value = LayoutValue.points(margin);
        marginLeft = value;
        marginTop = value;
        marginRight = value;
        marginBottom = value;
        return this;
    }

    /**
     * Sets all four outer margins from one value.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Assigns all sides directly without invoking the change callback; explicitly
     * invalidate the owning node if this change requires a new layout.
     *
     * @param margin outer margins value in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout marginPercent(float margin) {
        LayoutValue value = LayoutValue.percent(margin);
        marginLeft = value;
        marginTop = value;
        marginRight = value;
        marginBottom = value;
        return this;
    }

    /**
     * Sets all four outer margins from the supplied side values.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param horizontal value for left and right in UI points
     * @param vertical value for top and bottom in UI points
     * @return this layout
     */
    public Layout margin(float horizontal, float vertical) {
        return marginLeft(horizontal).marginRight(horizontal).marginTop(vertical).marginBottom(vertical);
    }

    /**
     * Sets all four outer margins from the supplied side values.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param left left side value in UI points
     * @param top top side value in UI points
     * @param right right side value in UI points
     * @param bottom bottom side value in UI points
     * @return this layout
     */
    public Layout margin(float left, float top, float right, float bottom) {
        return marginLeft(left).marginTop(top).marginRight(right).marginBottom(bottom);
    }

    /**
     * Sets all four outer margins from the supplied side values.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param horizontal value for left and right in percent (100 denotes the full reference extent)
     * @param vertical value for top and bottom in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout marginPercent(float horizontal, float vertical) {
        return marginLeftPercent(horizontal).marginRightPercent(horizontal).marginTopPercent(vertical).marginBottomPercent(vertical);
    }

    /**
     * Sets all four outer margins from the supplied side values.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param left left side value in percent (100 denotes the full reference extent)
     * @param top top side value in percent (100 denotes the full reference extent)
     * @param right right side value in percent (100 denotes the full reference extent)
     * @param bottom bottom side value in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout marginPercent(float left, float top, float right, float bottom) {
        return marginLeftPercent(left).marginTopPercent(top).marginRightPercent(right).marginBottomPercent(bottom);
    }

    /**
     * Sets left and right outer margins from one value.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param margin outer margins value in UI points
     * @return this layout
     */
    public Layout marginHorizontal(float margin) {
        return marginLeft(margin).marginRight(margin);
    }

    /**
     * Sets top and bottom outer margins from one value.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param margin outer margins value in UI points
     * @return this layout
     */
    public Layout marginVertical(float margin) {
        return marginTop(margin).marginBottom(margin);
    }

    /**
     * Sets left and right outer margins from one value.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param margin outer margins value in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout marginHorizontalPercent(float margin) {
        return marginLeftPercent(margin).marginRightPercent(margin);
    }

    /**
     * Sets top and bottom outer margins from one value.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param margin outer margins value in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout marginVerticalPercent(float margin) {
        return marginTopPercent(margin).marginBottomPercent(margin);
    }

    /**
     * Sets all four outer margins to auto through their individual notifying setters.
     * Solver support determines automatic margin distribution.
     *
     * @return this layout
     */
    public Layout marginAuto() {
        return marginLeftAuto().marginTopAuto().marginRightAuto().marginBottomAuto();
    }

    /**
     * Sets left and right margins to auto, preserving top and bottom margins.
     * Each changed side can notify separately.
     *
     * @return this layout
     */
    public Layout marginHorizontalAuto() {
        return marginLeftAuto().marginRightAuto();
    }

    /**
     * Sets top and bottom margins to auto, preserving left and right margins.
     * Each changed side can notify separately.
     *
     * @return this layout
     */
    public Layout marginVerticalAuto() {
        return marginTopAuto().marginBottomAuto();
    }

    /**
     * Reads the stored left inner padding without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current left inner padding specification
     */
    public LayoutValue getPaddingLeft() {
        return paddingLeft;
    }

    /**
     * Assigns the left inner padding when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param paddingLeft non-null left inner padding specification
     * @return this layout
     * @throws NullPointerException if paddingLeft is null
     */
    public Layout paddingLeft(LayoutValue paddingLeft) {
        if (!Objects.equals(this.paddingLeft, normalize(paddingLeft))) {
            this.paddingLeft = normalize(paddingLeft);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the left inner padding in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param paddingLeft requested left inner padding in points
     * @return this layout
     */
    public Layout paddingLeft(float paddingLeft) {
        if (!(this.paddingLeft.isPoints() && Float.compare(this.paddingLeft.getValue(), paddingLeft) == 0)) {
            this.paddingLeft = LayoutValue.points(paddingLeft);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the left inner padding as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param paddingLeft requested percentage value
     * @return this layout
     */
    public Layout paddingLeftPercent(float paddingLeft) {
        if (!(this.paddingLeft.isPercent() && Float.compare(this.paddingLeft.getValue(), paddingLeft) == 0)) {
            this.paddingLeft = LayoutValue.percent(paddingLeft);
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored top inner padding without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current top inner padding specification
     */
    public LayoutValue getPaddingTop() {
        return paddingTop;
    }

    /**
     * Assigns the top inner padding when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param paddingTop non-null top inner padding specification
     * @return this layout
     * @throws NullPointerException if paddingTop is null
     */
    public Layout paddingTop(LayoutValue paddingTop) {
        if (!Objects.equals(this.paddingTop, normalize(paddingTop))) {
            this.paddingTop = normalize(paddingTop);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the top inner padding in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param paddingTop requested top inner padding in points
     * @return this layout
     */
    public Layout paddingTop(float paddingTop) {
        if (!(this.paddingTop.isPoints() && Float.compare(this.paddingTop.getValue(), paddingTop) == 0)) {
            this.paddingTop = LayoutValue.points(paddingTop);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the top inner padding as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param paddingTop requested percentage value
     * @return this layout
     */
    public Layout paddingTopPercent(float paddingTop) {
        if (!(this.paddingTop.isPercent() && Float.compare(this.paddingTop.getValue(), paddingTop) == 0)) {
            this.paddingTop = LayoutValue.percent(paddingTop);
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored right inner padding without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current right inner padding specification
     */
    public LayoutValue getPaddingRight() {
        return paddingRight;
    }

    /**
     * Assigns the right inner padding when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param paddingRight non-null right inner padding specification
     * @return this layout
     * @throws NullPointerException if paddingRight is null
     */
    public Layout paddingRight(LayoutValue paddingRight) {
        if (!Objects.equals(this.paddingRight, normalize(paddingRight))) {
            this.paddingRight = normalize(paddingRight);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the right inner padding in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param paddingRight requested right inner padding in points
     * @return this layout
     */
    public Layout paddingRight(float paddingRight) {
        if (!(this.paddingRight.isPoints() && Float.compare(this.paddingRight.getValue(), paddingRight) == 0)) {
            this.paddingRight = LayoutValue.points(paddingRight);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the right inner padding as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param paddingRight requested percentage value
     * @return this layout
     */
    public Layout paddingRightPercent(float paddingRight) {
        if (!(this.paddingRight.isPercent() && Float.compare(this.paddingRight.getValue(), paddingRight) == 0)) {
            this.paddingRight = LayoutValue.percent(paddingRight);
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored bottom inner padding without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current bottom inner padding specification
     */
    public LayoutValue getPaddingBottom() {
        return paddingBottom;
    }

    /**
     * Assigns the bottom inner padding when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param paddingBottom non-null bottom inner padding specification
     * @return this layout
     * @throws NullPointerException if paddingBottom is null
     */
    public Layout paddingBottom(LayoutValue paddingBottom) {
        if (!Objects.equals(this.paddingBottom, normalize(paddingBottom))) {
            this.paddingBottom = normalize(paddingBottom);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the bottom inner padding in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param paddingBottom requested bottom inner padding in points
     * @return this layout
     */
    public Layout paddingBottom(float paddingBottom) {
        if (!(this.paddingBottom.isPoints() && Float.compare(this.paddingBottom.getValue(), paddingBottom) == 0)) {
            this.paddingBottom = LayoutValue.points(paddingBottom);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the bottom inner padding as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param paddingBottom requested percentage value
     * @return this layout
     */
    public Layout paddingBottomPercent(float paddingBottom) {
        if (!(this.paddingBottom.isPercent() && Float.compare(this.paddingBottom.getValue(), paddingBottom) == 0)) {
            this.paddingBottom = LayoutValue.percent(paddingBottom);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets all four inner padding from one value.
     * Retains the immutable unit-bearing value without resolving it.
     * Assigns all sides directly without invoking the change callback; explicitly
     * invalidate the owning node if this change requires a new layout.
     *
     * @param padding non-null immutable specification
     * @return this layout
     * @throws NullPointerException if a supplied LayoutValue is null
     */
    public Layout padding(LayoutValue padding) {
        LayoutValue value = normalize(padding);
        paddingLeft = value;
        paddingTop = value;
        paddingRight = value;
        paddingBottom = value;
        return this;
    }

    /**
     * Sets all four inner padding from one value.
     * Inputs use UI points and are retained without validation.
     * Assigns all sides directly without invoking the change callback; explicitly
     * invalidate the owning node if this change requires a new layout.
     *
     * @param padding inner padding value in UI points
     * @return this layout
     */
    public Layout padding(float padding) {
        LayoutValue value = LayoutValue.points(padding);
        paddingLeft = value;
        paddingTop = value;
        paddingRight = value;
        paddingBottom = value;
        return this;
    }

    /**
     * Sets all four inner padding from one value.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Assigns all sides directly without invoking the change callback; explicitly
     * invalidate the owning node if this change requires a new layout.
     *
     * @param padding inner padding value in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout paddingPercent(float padding) {
        LayoutValue value = LayoutValue.percent(padding);
        paddingLeft = value;
        paddingTop = value;
        paddingRight = value;
        paddingBottom = value;
        return this;
    }

    /**
     * Sets all four inner padding from the supplied side values.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param horizontal value for left and right in UI points
     * @param vertical value for top and bottom in UI points
     * @return this layout
     */
    public Layout padding(float horizontal, float vertical) {
        return paddingLeft(horizontal).paddingRight(horizontal).paddingTop(vertical).paddingBottom(vertical);
    }

    /**
     * Sets all four inner padding from the supplied side values.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param left left side value in UI points
     * @param top top side value in UI points
     * @param right right side value in UI points
     * @param bottom bottom side value in UI points
     * @return this layout
     */
    public Layout padding(float left, float top, float right, float bottom) {
        return paddingLeft(left).paddingTop(top).paddingRight(right).paddingBottom(bottom);
    }

    /**
     * Sets all four inner padding from the supplied side values.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param horizontal value for left and right in percent (100 denotes the full reference extent)
     * @param vertical value for top and bottom in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout paddingPercent(float horizontal, float vertical) {
        return paddingLeftPercent(horizontal).paddingRightPercent(horizontal).paddingTopPercent(vertical).paddingBottomPercent(vertical);
    }

    /**
     * Sets all four inner padding from the supplied side values.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param left left side value in percent (100 denotes the full reference extent)
     * @param top top side value in percent (100 denotes the full reference extent)
     * @param right right side value in percent (100 denotes the full reference extent)
     * @param bottom bottom side value in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout paddingPercent(float left, float top, float right, float bottom) {
        return paddingLeftPercent(left).paddingTopPercent(top).paddingRightPercent(right).paddingBottomPercent(bottom);
    }

    /**
     * Sets left and right inner padding from one value.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param padding inner padding value in UI points
     * @return this layout
     */
    public Layout paddingHorizontal(float padding) {
        return paddingLeft(padding).paddingRight(padding);
    }

    /**
     * Sets top and bottom inner padding from one value.
     * Inputs use UI points and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param padding inner padding value in UI points
     * @return this layout
     */
    public Layout paddingVertical(float padding) {
        return paddingTop(padding).paddingBottom(padding);
    }

    /**
     * Sets left and right inner padding from one value.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param padding inner padding value in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout paddingHorizontalPercent(float padding) {
        return paddingLeftPercent(padding).paddingRightPercent(padding);
    }

    /**
     * Sets top and bottom inner padding from one value.
     * Inputs use percent (100 denotes the full reference extent) and are retained without validation.
     * Delegates to individual side setters, so changed sides can notify separately.
     * Other properties are retained and callback failure does not roll back prior sides.
     *
     * @param padding inner padding value in percent (100 denotes the full reference extent)
     * @return this layout
     */
    public Layout paddingVerticalPercent(float padding) {
        return paddingTopPercent(padding).paddingBottomPercent(padding);
    }

    /**
     * Reads the configured relative share of positive main-axis free space without running layout or notifying.
     *
     * @return current flex grow
     */
    public float getFlexGrow() {
        return flexGrow;
    }

    /**
     * Sets the relative share of positive main-axis free space and notifies only on an actual stored change.
     * The input is retained without clamping or finiteness validation.
     *
     * @param flexGrow requested flex grow
     * @return this layout
     */
    public Layout flexGrow(float flexGrow) {
        if (Float.compare(this.flexGrow, flexGrow) != 0) {
            this.flexGrow = flexGrow;
            onChange.run();
        }
        return this;
    }

    /**
     * Sets flex growth to one, allowing participation in positive main-axis space.
     * Notifies only if the stored grow factor changes.
     *
     * @return this layout
     */
    public Layout grow() {
        if (Float.compare(this.flexGrow, 1f) != 0) {
            this.flexGrow = 1f;
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the positive-space growth factor and notifies only when Float.compare differs.
     * Values are stored unchecked; the layout solver interprets the factor.
     *
     * @param amount requested factor
     * @return this layout
     */
    public Layout grow(float amount) {
        if (Float.compare(this.flexGrow, amount) != 0) {
            this.flexGrow = amount;
            onChange.run();
        }
        return this;
    }

    /**
     * Sets flex growth to zero, preventing positive-space distribution to this item.
     * Notifies only if the factor changes.
     *
     * @return this layout
     */
    public Layout noGrow() {
        if (Float.compare(this.flexGrow, 0f) != 0) {
            this.flexGrow = 0f;
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the configured relative shrink factor when main-axis space is insufficient without running layout or notifying.
     *
     * @return current flex shrink
     */
    public float getFlexShrink() {
        return flexShrink;
    }

    /**
     * Sets the relative shrink factor when main-axis space is insufficient and notifies only on an actual stored change.
     * The input is retained without clamping or finiteness validation.
     *
     * @param flexShrink requested flex shrink
     * @return this layout
     */
    public Layout flexShrink(float flexShrink) {
        if (Float.compare(this.flexShrink, flexShrink) != 0) {
            this.flexShrink = flexShrink;
            onChange.run();
        }
        return this;
    }

    /**
     * Sets flex shrink to one for deficit-space distribution along the main axis.
     * Notifies only if the stored shrink factor changes.
     *
     * @return this layout
     */
    public Layout shrink() {
        if (Float.compare(this.flexShrink, 1f) != 0) {
            this.flexShrink = 1f;
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the deficit-space shrink factor and notifies only when Float.compare differs.
     * Values are stored unchecked; the layout solver interprets the factor.
     *
     * @param amount requested factor
     * @return this layout
     */
    public Layout shrink(float amount) {
        if (Float.compare(this.flexShrink, amount) != 0) {
            this.flexShrink = amount;
            onChange.run();
        }
        return this;
    }

    /**
     * Sets flex shrink to zero without changing preferred or minimum dimensions.
     * Notifies only if the factor changes.
     *
     * @return this layout
     */
    public Layout noShrink() {
        if (Float.compare(this.flexShrink, 0f) != 0) {
            this.flexShrink = 0f;
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the stored initial main-axis size before flexible space distribution without resolving auto or percentage units.
     * The immutable value may be shared; this call does not trigger layout.
     *
     * @return current initial main-axis size before flexible space distribution specification
     */
    public LayoutValue getFlexBasis() {
        return flexBasis;
    }

    /**
     * Assigns the initial main-axis size before flexible space distribution when the immutable value differs, then notifies
     * the change callback. Units and numeric payload are retained without conversion.
     *
     * @param flexBasis non-null initial main-axis size before flexible space distribution specification
     * @return this layout
     * @throws NullPointerException if flexBasis is null
     */
    public Layout flexBasis(LayoutValue flexBasis) {
        if (!Objects.equals(this.flexBasis, normalize(flexBasis))) {
            this.flexBasis = normalize(flexBasis);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the initial main-axis size before flexible space distribution in fixed UI points, notifying only when its unit
     * or Float.compare value differs. No numeric range or finiteness checks occur.
     *
     * @param flexBasis requested initial main-axis size before flexible space distribution in points
     * @return this layout
     */
    public Layout flexBasis(float flexBasis) {
        if (!(this.flexBasis.isPoints() && Float.compare(this.flexBasis.getValue(), flexBasis) == 0)) {
            this.flexBasis = LayoutValue.points(flexBasis);
            onChange.run();
        }
        return this;
    }

    /**
     * Sets the initial main-axis size before flexible space distribution as a percentage, using 100 for the full solver-selected
     * reference extent. Notifies only when unit/value changes; input is not clamped.
     *
     * @param flexBasis requested percentage value
     * @return this layout
     */
    public Layout flexBasisPercent(float flexBasis) {
        if (!(this.flexBasis.isPercent() && Float.compare(this.flexBasis.getValue(), flexBasis) == 0)) {
            this.flexBasis = LayoutValue.percent(flexBasis);
            onChange.run();
        }
        return this;
    }

    /**
     * Restores automatic interpretation of the initial main-axis size before flexible space distribution and notifies only
     * when that specification changes. The solver determines what auto means for
     * this property; no geometry is calculated here.
     *
     * @return this layout
     */
    public Layout flexBasisAuto() {
        if (!Objects.equals(this.flexBasis, LayoutValue.auto())) {
            this.flexBasis = LayoutValue.auto();
            onChange.run();
        }
        return this;
    }

    /**
     * Sets grow and shrink to one and restores auto flex basis. Preferred dimensions
     * are retained; each changed property can notify separately.
     *
     * @return this layout
     */
    public Layout fill() {
        return flexGrow(1f).flexShrink(1f).flexBasisAuto();
    }

    /**
     * Enables growth with factor one and restores preferred width to auto.
     * The parent still determines the growth axis; height is retained.
     *
     * @return this layout
     */
    public Layout fillX() {
        return flexGrow(1f).widthAuto();
    }

    /**
     * Enables growth with factor one and restores preferred height to auto.
     * The parent still determines the growth axis; width is retained.
     *
     * @return this layout
     */
    public Layout fillY() {
        return flexGrow(1f).heightAuto();
    }

    /**
     * Disables growth and shrink and restores automatic width and height. Minimum,
     * maximum, and flex-basis settings remain unchanged.
     *
     * @return this layout
     */
    public Layout fit() {
        return noGrow().noShrink().widthAuto().heightAuto();
    }

    /**
     * Reads the configured main-axis direction used to arrange children without running layout or notifying.
     *
     * @return current flex direction
     */
    public FlexDirection getFlexDirection() {
        return flexDirection;
    }

    /**
     * Sets the main-axis direction used to arrange children and notifies only on an actual stored change.
     * The enum is required; unsupported combinations are interpreted by the solver.
     *
     * @param flexDirection requested flex direction
     * @return this layout
     * @throws NullPointerException if flexDirection is null
     */
    public Layout flexDirection(FlexDirection flexDirection) {
        if (!Objects.equals(this.flexDirection, Objects.requireNonNull(flexDirection))) {
            this.flexDirection = Objects.requireNonNull(flexDirection);
            onChange.run();
        }
        return this;
    }

    /**
     * Selects normal row direction for children and notifies only on change.
     * Does not reorder the child collection or enable wrapping.
     *
     * @return this layout
     */
    public Layout row() {
        if (!Objects.equals(this.flexDirection, FlexDirection.ROW)) {
            this.flexDirection = FlexDirection.ROW;
            onChange.run();
        }
        return this;
    }

    /**
     * Selects normal column direction for children and notifies only on change.
     * Does not reorder the child collection or alter alignment.
     *
     * @return this layout
     */
    public Layout column() {
        if (!Objects.equals(this.flexDirection, FlexDirection.COLUMN)) {
            this.flexDirection = FlexDirection.COLUMN;
            onChange.run();
        }
        return this;
    }

    /**
     * Selects reversed row layout and notifies only on change. The underlying child
     * collection retains its order; reversal is a solver configuration.
     *
     * @return this layout
     */
    public Layout rowReverse() {
        if (!Objects.equals(this.flexDirection, FlexDirection.ROW_REVERSE)) {
            this.flexDirection = FlexDirection.ROW_REVERSE;
            onChange.run();
        }
        return this;
    }

    /**
     * Selects reversed column layout and notifies only on change. The underlying
     * child collection retains its order.
     *
     * @return this layout
     */
    public Layout columnReverse() {
        if (!Objects.equals(this.flexDirection, FlexDirection.COLUMN_REVERSE)) {
            this.flexDirection = FlexDirection.COLUMN_REVERSE;
            onChange.run();
        }
        return this;
    }

    /**
     * Sets main-axis justification and cross-axis item alignment to center.
     * Each changed policy can notify separately; this item's self alignment is retained.
     *
     * @return this layout
     */
    public Layout centerContent() {
        return justifyCenter().itemsCenter();
    }

    /**
     * Reads the configured child distribution along the main axis without running layout or notifying.
     *
     * @return current justify content
     */
    public JustifyContent getJustifyContent() {
        return justifyContent;
    }

    /**
     * Sets the child distribution along the main axis and notifies only on an actual stored change.
     * The enum is required; unsupported combinations are interpreted by the solver.
     *
     * @param justifyContent requested justify content
     * @return this layout
     * @throws NullPointerException if justifyContent is null
     */
    public Layout justifyContent(JustifyContent justifyContent) {
        if (!Objects.equals(this.justifyContent, Objects.requireNonNull(justifyContent))) {
            this.justifyContent = Objects.requireNonNull(justifyContent);
            onChange.run();
        }
        return this;
    }

    /**
     * Configures main-axis child distribution to pack at the main-axis start.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout justifyStart() {
        if (!Objects.equals(this.justifyContent, JustifyContent.FLEX_START)) {
            this.justifyContent = JustifyContent.FLEX_START;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures main-axis child distribution to center along the main axis.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout justifyCenter() {
        if (!Objects.equals(this.justifyContent, JustifyContent.CENTER)) {
            this.justifyContent = JustifyContent.CENTER;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures main-axis child distribution to pack at the main-axis end.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout justifyEnd() {
        if (!Objects.equals(this.justifyContent, JustifyContent.FLEX_END)) {
            this.justifyContent = JustifyContent.FLEX_END;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures main-axis child distribution to distribute free space between items.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout justifyBetween() {
        if (!Objects.equals(this.justifyContent, JustifyContent.SPACE_BETWEEN)) {
            this.justifyContent = JustifyContent.SPACE_BETWEEN;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures main-axis child distribution to distribute space around items.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout justifyAround() {
        if (!Objects.equals(this.justifyContent, JustifyContent.SPACE_AROUND)) {
            this.justifyContent = JustifyContent.SPACE_AROUND;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures main-axis child distribution to distribute equal gaps including outer edges.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout justifyEvenly() {
        if (!Objects.equals(this.justifyContent, JustifyContent.SPACE_EVENLY)) {
            this.justifyContent = JustifyContent.SPACE_EVENLY;
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the configured default child alignment across the main axis without running layout or notifying.
     *
     * @return current align items
     */
    public Align getAlignItems() {
        return alignItems;
    }

    /**
     * Sets the default child alignment across the main axis and notifies only on an actual stored change.
     * The enum is required; unsupported combinations are interpreted by the solver.
     *
     * @param alignItems requested align items
     * @return this layout
     * @throws NullPointerException if alignItems is null
     */
    public Layout alignItems(Align alignItems) {
        if (!Objects.equals(this.alignItems, Objects.requireNonNull(alignItems))) {
            this.alignItems = Objects.requireNonNull(alignItems);
            onChange.run();
        }
        return this;
    }

    /**
     * Configures default cross-axis child alignment to use the solver's automatic alignment policy.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout itemsAuto() {
        if (!Objects.equals(this.alignItems, Align.AUTO)) {
            this.alignItems = Align.AUTO;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures default cross-axis child alignment to align at cross-axis start.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout itemsStart() {
        if (!Objects.equals(this.alignItems, Align.FLEX_START)) {
            this.alignItems = Align.FLEX_START;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures default cross-axis child alignment to center across the main axis.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout itemsCenter() {
        if (!Objects.equals(this.alignItems, Align.CENTER)) {
            this.alignItems = Align.CENTER;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures default cross-axis child alignment to align at cross-axis end.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout itemsEnd() {
        if (!Objects.equals(this.alignItems, Align.FLEX_END)) {
            this.alignItems = Align.FLEX_END;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures default cross-axis child alignment to stretch eligible auto-sized cross dimensions.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout itemsStretch() {
        if (!Objects.equals(this.alignItems, Align.STRETCH)) {
            this.alignItems = Align.STRETCH;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures default cross-axis child alignment to request baseline alignment.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout itemsBaseline() {
        if (!Objects.equals(this.alignItems, Align.BASELINE)) {
            this.alignItems = Align.BASELINE;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures default cross-axis child alignment to request SPACE_BETWEEN alignment where supported by the solver.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout itemsSpaceBetween() {
        if (!Objects.equals(this.alignItems, Align.SPACE_BETWEEN)) {
            this.alignItems = Align.SPACE_BETWEEN;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures default cross-axis child alignment to request SPACE_AROUND alignment where supported by the solver.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout itemsSpaceAround() {
        if (!Objects.equals(this.alignItems, Align.SPACE_AROUND)) {
            this.alignItems = Align.SPACE_AROUND;
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the configured this item's cross-axis alignment override without running layout or notifying.
     *
     * @return current align self
     */
    public Align getAlignSelf() {
        return alignSelf;
    }

    /**
     * Sets the this item's cross-axis alignment override and notifies only on an actual stored change.
     * The enum is required; unsupported combinations are interpreted by the solver.
     *
     * @param alignSelf requested align self
     * @return this layout
     * @throws NullPointerException if alignSelf is null
     */
    public Layout alignSelf(Align alignSelf) {
        if (!Objects.equals(this.alignSelf, Objects.requireNonNull(alignSelf))) {
            this.alignSelf = Objects.requireNonNull(alignSelf);
            onChange.run();
        }
        return this;
    }

    /**
     * Configures this item's cross-axis alignment to use the solver's automatic alignment policy.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout selfAuto() {
        if (!Objects.equals(this.alignSelf, Align.AUTO)) {
            this.alignSelf = Align.AUTO;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures this item's cross-axis alignment to align at cross-axis start.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout selfStart() {
        if (!Objects.equals(this.alignSelf, Align.FLEX_START)) {
            this.alignSelf = Align.FLEX_START;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures this item's cross-axis alignment to center across the main axis.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout selfCenter() {
        if (!Objects.equals(this.alignSelf, Align.CENTER)) {
            this.alignSelf = Align.CENTER;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures this item's cross-axis alignment to align at cross-axis end.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout selfEnd() {
        if (!Objects.equals(this.alignSelf, Align.FLEX_END)) {
            this.alignSelf = Align.FLEX_END;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures this item's cross-axis alignment to stretch eligible auto-sized cross dimensions.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout selfStretch() {
        if (!Objects.equals(this.alignSelf, Align.STRETCH)) {
            this.alignSelf = Align.STRETCH;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures this item's cross-axis alignment to request baseline alignment.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout selfBaseline() {
        if (!Objects.equals(this.alignSelf, Align.BASELINE)) {
            this.alignSelf = Align.BASELINE;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures this item's cross-axis alignment to request SPACE_BETWEEN alignment where supported by the solver.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout selfSpaceBetween() {
        if (!Objects.equals(this.alignSelf, Align.SPACE_BETWEEN)) {
            this.alignSelf = Align.SPACE_BETWEEN;
            onChange.run();
        }
        return this;
    }

    /**
     * Configures this item's cross-axis alignment to request SPACE_AROUND alignment where supported by the solver.
     * Notifies only if the stored enum changes; other axis policies are retained.
     *
     * @return this layout
     */
    public Layout selfSpaceAround() {
        if (!Objects.equals(this.alignSelf, Align.SPACE_AROUND)) {
            this.alignSelf = Align.SPACE_AROUND;
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the configured relative-flow or absolute positioning mode without running layout or notifying.
     *
     * @return current position type
     */
    public PositionType getPositionType() {
        return positionType;
    }

    /**
     * Sets the relative-flow or absolute positioning mode and notifies only on an actual stored change.
     * The enum is required; unsupported combinations are interpreted by the solver.
     *
     * @param positionType requested position type
     * @return this layout
     * @throws NullPointerException if positionType is null
     */
    public Layout positionType(PositionType positionType) {
        if (!Objects.equals(this.positionType, Objects.requireNonNull(positionType))) {
            this.positionType = Objects.requireNonNull(positionType);
            onChange.run();
        }
        return this;
    }

    /**
     * Selects relative positioning through the notifying mode setter, retaining offsets.
     * The item participates in normal layout flow under the solver's rules.
     *
     * @return this layout
     */
    public Layout relative() {
        return positionType(PositionType.RELATIVE);
    }

    /**
     * Selects absolute positioning through the notifying mode setter, retaining offsets.
     * The solver positions the item outside normal sibling flow.
     *
     * @return this layout
     */
    public Layout absolute() {
        return positionType(PositionType.ABSOLUTE);
    }

    /**
     * Reads the configured child line-wrapping policy without running layout or notifying.
     *
     * @return current flex wrap
     */
    public FlexWrap getFlexWrap() {
        return flexWrap;
    }

    /**
     * Sets the child line-wrapping policy and notifies only on an actual stored change.
     * The enum is required; unsupported combinations are interpreted by the solver.
     *
     * @param flexWrap requested flex wrap
     * @return this layout
     * @throws NullPointerException if flexWrap is null
     */
    public Layout flexWrap(FlexWrap flexWrap) {
        if (!Objects.equals(this.flexWrap, Objects.requireNonNull(flexWrap))) {
            this.flexWrap = Objects.requireNonNull(flexWrap);
            onChange.run();
        }
        return this;
    }

    /**
     * Enables normal child line wrapping and notifies only if the policy changes.
     * The available main-axis space determines actual line breaks.
     *
     * @return this layout
     */
    public Layout wrap() {
        if (!Objects.equals(this.flexWrap, FlexWrap.WRAP)) {
            this.flexWrap = FlexWrap.WRAP;
            onChange.run();
        }
        return this;
    }

    /**
     * Disables child line wrapping and notifies only if the policy changes.
     * Size constraints and shrink factors remain unchanged.
     *
     * @return this layout
     */
    public Layout noWrap() {
        if (!Objects.equals(this.flexWrap, FlexWrap.NO_WRAP)) {
            this.flexWrap = FlexWrap.NO_WRAP;
            onChange.run();
        }
        return this;
    }

    /**
     * Selects reversed wrapping-line placement and notifies only on change.
     * The flex direction and child collection order are retained.
     *
     * @return this layout
     */
    public Layout wrapReverse() {
        if (!Objects.equals(this.flexWrap, FlexWrap.WRAP_REVERSE)) {
            this.flexWrap = FlexWrap.WRAP_REVERSE;
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the configured gap between layout rows in UI units without running layout or notifying.
     *
     * @return current row gap
     */
    public float getRowGap() {
        return rowGap;
    }

    /**
     * Sets the gap between layout rows in UI units and notifies only on an actual stored change.
     * The input is retained without clamping or finiteness validation.
     *
     * @param rowGap requested row gap
     * @return this layout
     */
    public Layout rowGap(float rowGap) {
        if (Float.compare(this.rowGap, rowGap) != 0) {
            this.rowGap = rowGap;
            onChange.run();
        }
        return this;
    }

    /**
     * Reads the configured gap between layout columns in UI units without running layout or notifying.
     *
     * @return current column gap
     */
    public float getColumnGap() {
        return columnGap;
    }

    /**
     * Sets the gap between layout columns in UI units and notifies only on an actual stored change.
     * The input is retained without clamping or finiteness validation.
     *
     * @param columnGap requested column gap
     * @return this layout
     */
    public Layout columnGap(float columnGap) {
        if (Float.compare(this.columnGap, columnGap) != 0) {
            this.columnGap = columnGap;
            onChange.run();
        }
        return this;
    }

    /**
     * Sets both row and column gaps in UI points. Each changed gap invokes the callback
     * separately after its assignment; the operation is not a single notification.
     *
     * @param gap requested spacing, retained without validation
     * @return this layout
     */
    public Layout gap(float gap) {
        if (Float.compare(this.rowGap, gap) != 0) {
            this.rowGap = gap;
            onChange.run();
        }
        if (Float.compare(this.columnGap, gap) != 0) {
            this.columnGap = gap;
            onChange.run();
        }
        return this;
    }

    /**
     * Restores preferred, minimum, and maximum width/height specifications to auto.
     * Each changed constraint can notify separately; spacing and flex settings remain.
     *
     * @return this layout
     */
    public Layout resetSize() {
        return widthAuto().heightAuto().minWidthAuto().minHeightAuto().maxWidthAuto().maxHeightAuto();
    }

    /**
     * Restores all position offsets to auto without resetting relative/absolute mode.
     * Changed edges notify through their setters.
     *
     * @return this layout
     */
    public Layout resetPosition() {
        return edgesAuto();
    }

    /**
     * Assigns zero-point margins on all sides through the uniform margin helper.
     * This path does not invoke the change callback.
     *
     * @return this layout
     */
    public Layout resetMargin() {
        return margin(0);
    }

    /**
     * Assigns zero-point padding on all sides through the uniform padding helper.
     * This path does not invoke the change callback.
     *
     * @return this layout
     */
    public Layout resetPadding() {
        return padding(0);
    }

    /**
     * Restores zero grow/shrink, auto basis, column direction, start justification,
     * stretched items, auto self alignment, relative positioning, no wrap, and zero
     * gaps. Changed properties notify separately; dimensions and spacing are retained.
     *
     * @return this layout
     */
    public Layout resetFlex() {
        return noGrow().noShrink().flexBasisAuto().column().justifyStart()
                .itemsStretch().selfAuto().relative().noWrap().gap(0);
    }

    /**
     * Runs size, position, margin, padding, and flex resets in sequence. Callback
     * notifications are not coalesced; uniform margin/padding resets themselves do
     * not notify, so a spacing-only reset may require explicit invalidation.
     *
     * @return this layout
     */
    public Layout reset() {
        return resetSize()
                .resetPosition()
                .resetMargin()
                .resetPadding()
                .resetFlex();
    }
}
