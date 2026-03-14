package valthorne.ui;

import org.lwjgl.util.yoga.Yoga;
import valthorne.Window;
import valthorne.collections.bits.ShortBits;
import valthorne.event.events.*;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.geometry.Rectangle;
import valthorne.ui.nodes.Tooltip;
import valthorne.ui.theme.*;

/**
 * <h1>UINode</h1>
 *
 * <p>
 * {@code UINode} is the abstract foundation for every UI object in the Valthorne UI system.
 * It is responsible for holding layout data, storing interaction state, resolving theme styles,
 * managing Yoga layout nodes, and providing the event hooks needed for interactive controls.
 * A node may represent a simple visual element, a container, or a specialized interactive
 * control such as a button, checkbox, text field, slider, tooltip host, or any future custom
 * component you create.
 * </p>
 *
 * <p>
 * The class is designed around a few core responsibilities:
 * </p>
 *
 * <ul>
 *     <li><b>Layout</b> through a {@link Layout} object and a native Yoga node.</li>
 *     <li><b>State management</b> through a compact {@link ShortBits} bitset.</li>
 *     <li><b>Theme/style resolution</b> through {@link ThemeData}, {@link StyleMap}, and {@link ResolvedStyle}.</li>
 *     <li><b>Bounds tracking</b> through a cached {@link Rectangle} updated from Yoga layout results.</li>
 *     <li><b>Input hooks</b> through overridable mouse, keyboard, and window-resize methods.</li>
 * </ul>
 *
 * <p>
 * Nodes keep their state extremely compact by storing booleans such as visible, enabled,
 * hovered, focused, pressed, checked, and dragging as bits inside a {@link ShortBits} instance.
 * This lets the node expose a large set of UI states without requiring a separate field for each
 * one. These state flags are also used when resolving the current style state for theming.
 * </p>
 *
 * <p>
 * Layout is handled through Yoga. Each node may attach a native Yoga node when it becomes part
 * of a UI tree. Once Yoga performs layout calculations, this class reads the computed position
 * and size values and caches them into its {@link #bounds} rectangle. Rendering code can then
 * use these cached values directly.
 * </p>
 *
 * <p>
 * Styling is lazy and cached. The first time {@link #getStyle()} is requested, the current theme,
 * style name, style overrides, and state bits are combined to produce a {@link ResolvedStyle}.
 * Whenever something changes that may affect appearance, such as hover state or style overrides,
 * the cached style is invalidated so it can be rebuilt later.
 * </p>
 *
 * <p>
 * This class does not render anything by itself. Instead, subclasses implement
 * {@link #draw(TextureBatch)} and define their own visuals. Likewise, subclasses decide how to
 * react to input by overriding whichever event methods they care about.
 * </p>
 *
 * <h2>Usage flow</h2>
 *
 * <ol>
 *     <li>Create a subclass of {@code UINode}.</li>
 *     <li>Override lifecycle methods such as {@link #onCreate()}, {@link #onDestroy()}, {@link #update(float)}, and {@link #draw(TextureBatch)}.</li>
 *     <li>Optionally override input handlers such as {@link #onMousePress(MousePressEvent)} or {@link #onKeyPress(KeyPressEvent)}.</li>
 *     <li>Configure layout through {@link #getLayout()}.</li>
 *     <li>Read render-space values through {@link #getRenderX()}, {@link #getRenderY()}, {@link #getWidth()}, and {@link #getHeight()}.</li>
 * </ol>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * public class MyNode extends UINode {
 *
 *     @Override
 *     public void onCreate() {
 *         getLayout().width(Value.pixels(120));
 *         getLayout().height(Value.pixels(40));
 *     }
 *
 *     @Override
 *     public void onDestroy() {
 *     }
 *
 *     @Override
 *     public void update(float delta) {
 *     }
 *
 *     @Override
 *     public void draw(TextureBatch batch) {
 *         ResolvedStyle style = getStyle();
 *         if (style != null) {
 *             Drawable background = style.get(Panel.BACKGROUND_KEY);
 *             if (background != null) {
 *                 background.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());
 *             }
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>
 * In the example above, the custom node uses Yoga-driven layout, resolves theme styling through
 * {@link #getStyle()}, and responds to mouse input by changing its pressed state, which can then
 * affect the resolved style.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public abstract class UINode {

    /**
     * Bit index used to mark whether the node is visible.
     */
    public static final int VISIBLE_BIT = 0;

    /**
     * Bit index used to mark whether the node is enabled.
     */
    public static final int ENABLED_BIT = 1;

    /**
     * Bit index used to mark whether the node's layout is dirty.
     */
    public static final int LAYOUT_DIRTY_BIT = 2;

    /**
     * Bit index used to mark whether the node is currently hovered.
     */
    public static final int HOVERED_BIT = 3;

    /**
     * Bit index used to mark whether the node can be clicked.
     */
    public static final int CLICKABLE_BIT = 4;

    /**
     * Bit index used to mark whether the node can receive focus.
     */
    public static final int FOCUSABLE_BIT = 5;

    /**
     * Bit index used to mark whether the node can respond to scroll input.
     */
    public static final int SCROLLABLE_BIT = 6;

    /**
     * Bit index used to mark whether the node is currently pressed.
     */
    public static final int PRESSED_BIT = 7;

    /**
     * Bit index used to mark whether the node is currently focused.
     */
    public static final int FOCUSED_BIT = 8;

    /**
     * Bit index used to mark whether the node is selected.
     */
    public static final int SELECTED_BIT = 9;

    /**
     * Bit index used to mark whether the node is checked.
     */
    public static final int CHECKED_BIT = 10;

    /**
     * Bit index used to mark whether the node is currently being dragged.
     */
    public static final int DRAGGING_BIT = 11;

    private final Layout layout = new Layout(); // Layout configuration owned by this node.
    private final ShortBits bits = new ShortBits(); // Compact bitset holding node interaction and state flags.
    private Tooltip tooltip; // Optional tooltip displayed for this node.

    private UIContainer parent; // Parent container that owns this node.
    private UIRoot root; // Root UI tree that this node belongs to.
    private ThemeData theme; // Optional local theme override for this node.
    private String styleName; // Optional named style used during theme resolution.
    private StyleMap styleOverrides; // Per-node style overrides layered on top of the theme.
    private ResolvedStyle cachedStyle; // Cached resolved style for the current theme and state.

    private long yogaMemoryAddress; // Native Yoga node pointer used for layout calculations.
    private final Rectangle bounds = new Rectangle(); // Cached bounds built from Yoga layout results.

    /**
     * Creates a new UI node with its default state initialized.
     *
     * <p>
     * By default, a newly created node starts as visible, enabled, layout-dirty,
     * and clickable. These defaults make the node ready to participate in layout
     * and interaction immediately after construction.
     * </p>
     */
    protected UINode() {
        bits.set(VISIBLE_BIT, true);
        bits.set(ENABLED_BIT, true);
        bits.set(LAYOUT_DIRTY_BIT, true);
        bits.set(CLICKABLE_BIT, true);
    }

    /**
     * Called when a key is pressed while this node is the active receiver of keyboard input.
     *
     * <p>
     * The base implementation does nothing. Subclasses override this method when they need
     * keyboard behavior such as confirming actions, moving focus internally, typing text,
     * toggling values, or reacting to shortcuts.
     * </p>
     *
     * @param event the key press event containing the pressed key and modifier state
     */
    public void onKeyPress(KeyPressEvent event) {}

    /**
     * Called when a key is released while this node is the active receiver of keyboard input.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method when they need
     * release-specific behavior, such as ending a held state or confirming a key interaction only
     * when the key is released.
     * </p>
     *
     * @param event the key release event containing the released key and modifier state
     */
    public void onKeyRelease(KeyReleaseEvent event) {}

    /**
     * Called when a mouse button is pressed on this node.
     *
     * <p>
     * The base implementation does nothing. Subclasses typically override this method to begin
     * press state handling, activate dragging, capture input, or trigger visual state changes.
     * </p>
     *
     * @param event the mouse press event containing button and pointer position data
     */
    public void onMousePress(MousePressEvent event) {}

    /**
     * Called when a mouse button is released on this node.
     *
     * <p>
     * The base implementation does nothing. Subclasses usually override this method to end press
     * handling, commit clicks, stop dragging, or restore state after interaction.
     * </p>
     *
     * @param event the mouse release event containing button and pointer position data
     */
    public void onMouseRelease(MouseReleaseEvent event) {}

    /**
     * Called while the mouse is dragged across this node.
     *
     * <p>
     * The base implementation does nothing. Subclasses override this method for behaviors such as
     * slider dragging, selection box updates, custom repositioning, or scroll thumb movement.
     * </p>
     *
     * @param event the mouse drag event containing drag coordinates and button information
     */
    public void onMouseDrag(MouseDragEvent event) {}

    /**
     * Called when the mouse moves over or across this node.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method for hover effects,
     * tooltips, live previews, pointer tracking, or other movement-based interaction logic.
     * </p>
     *
     * @param event the mouse move event containing pointer movement information
     */
    public void onMouseMove(MouseMoveEvent event) {}

    /**
     * Called when the mouse wheel or scroll input is used while this node is the target.
     *
     * <p>
     * The base implementation does nothing. Subclasses override this method for things such as
     * scrolling content, zooming, stepping sliders, or changing selected values.
     * </p>
     *
     * @param event the mouse scroll event containing scroll offsets
     */
    public void onMouseScroll(MouseScrollEvent event) {}

    /**
     * Called when the window is resized.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method to respond to
     * changes in available space, update cached render data, or refresh state that depends on
     * window dimensions.
     * </p>
     *
     * @param event the resize event describing the new window size
     */
    public void onWindowResize(WindowResizeEvent event) {}

    /**
     * Returns the layout object owned by this node.
     *
     * <p>
     * The returned {@link Layout} stores the node's desired Yoga layout configuration, including
     * dimensions, margins, padding, alignment, flex behavior, and related settings. Changes to this
     * layout normally require the node tree to be relaid out before updated values appear in the
     * node's computed bounds.
     * </p>
     *
     * @return the node's layout object
     */
    public final Layout getLayout() {
        return layout;
    }

    /**
     * Returns the internal state bitset used by this node.
     *
     * <p>
     * This can be used for direct low-level inspection or advanced integrations where you need
     * direct access to the packed state bits rather than using the convenience state methods.
     * </p>
     *
     * @return the internal {@link ShortBits} state storage
     */
    public final ShortBits getBits() {
        return bits;
    }

    /**
     * Returns the boolean value of a specific bit index in the node state bitset.
     *
     * <p>
     * This is a convenience method for quickly checking one of the node's packed flags.
     * </p>
     *
     * @param index the bit index to inspect
     * @return true if the bit is set, otherwise false
     */
    public final boolean getBit(int index) {
        return bits.get(index);
    }

    /**
     * Sets the value of a specific bit in the node state bitset.
     *
     * <p>
     * If the bit value actually changes, the node invalidates its cached style because the new
     * state may affect visual appearance. If the changed bit is related to visibility or layout
     * dirtiness, the node also marks layout as dirty.
     * </p>
     *
     * @param index the bit index to modify
     * @param value the value to store in that bit
     */
    public final void setBit(int index, boolean value) {
        if (bits.get(index) == value) return;

        bits.set(index, value);
        invalidateStyle();

        if (index == VISIBLE_BIT || index == LAYOUT_DIRTY_BIT) markLayoutDirty();
    }

    /**
     * Returns whether this node is visible.
     *
     * <p>
     * Visibility is stored as a bit flag. Invisible nodes may still exist in the UI tree,
     * but whether they participate in rendering or hit testing depends on the surrounding UI logic.
     * </p>
     *
     * @return true if the node is visible
     */
    public final boolean isVisible() {
        return bits.get(VISIBLE_BIT);
    }

    /**
     * Sets whether this node is visible.
     *
     * <p>
     * Changing visibility invalidates the cached style and marks layout dirty so containers and
     * layout calculations can react to the new state.
     * </p>
     *
     * @param visible true to make the node visible, false to hide it
     */
    public final void setVisible(boolean visible) {
        if (bits.get(VISIBLE_BIT) == visible) return;

        bits.set(VISIBLE_BIT, visible);
        invalidateStyle();
        markLayoutDirty();
    }

    /**
     * Returns whether this node is disabled.
     *
     * <p>
     * Internally, disabled is represented by the inverse of the enabled bit.
     * </p>
     *
     * @return true if the node is disabled
     */
    public boolean isDisabled() {
        return !bits.get(ENABLED_BIT);
    }

    /**
     * Returns whether this node is enabled.
     *
     * @return true if the node is enabled
     */
    public boolean isEnabled() {
        return bits.get(ENABLED_BIT);
    }

    /**
     * Sets whether this node is enabled.
     *
     * <p>
     * Changing enabled state invalidates the current style because enabled and disabled states often
     * resolve to different visuals.
     * </p>
     *
     * @param enabled true to enable the node, false to disable it
     */
    public void setEnabled(boolean enabled) {
        if (bits.get(ENABLED_BIT) == enabled) return;

        bits.set(ENABLED_BIT, enabled);
        invalidateStyle();
    }

    /**
     * Returns whether this node is marked as layout dirty.
     *
     * <p>
     * A layout-dirty node needs its Yoga layout reapplied or recalculated before its bounds can be
     * considered current.
     * </p>
     *
     * @return true if the layout is dirty
     */
    public boolean isLayoutDirty() {
        return bits.get(LAYOUT_DIRTY_BIT);
    }

    /**
     * Returns whether this node is currently hovered.
     *
     * @return true if the node is hovered
     */
    public boolean isHovered() {
        return bits.get(HOVERED_BIT);
    }

    /**
     * Sets whether this node is currently hovered.
     *
     * <p>
     * Changing hover state invalidates the cached style so hover-specific theme values can be resolved.
     * </p>
     *
     * @param hovered true if the node should be considered hovered
     */
    public void setHovered(boolean hovered) {
        if (bits.get(HOVERED_BIT) == hovered) return;

        bits.set(HOVERED_BIT, hovered);
        invalidateStyle();
    }

    /**
     * Returns whether this node is clickable.
     *
     * @return true if the node can receive click interactions
     */
    public boolean isClickable() {
        return bits.get(CLICKABLE_BIT);
    }

    /**
     * Sets whether this node can receive click interactions.
     *
     * <p>
     * Changing clickability invalidates the current style because clickable and non-clickable states
     * may be styled differently by the theme.
     * </p>
     *
     * @param clickable true to make the node clickable
     */
    public void setClickable(boolean clickable) {
        if (bits.get(CLICKABLE_BIT) == clickable) return;

        bits.set(CLICKABLE_BIT, clickable);
        invalidateStyle();
    }

    /**
     * Returns whether this node can receive focus.
     *
     * @return true if the node is focusable
     */
    public boolean isFocusable() {
        return bits.get(FOCUSABLE_BIT);
    }

    /**
     * Sets whether this node can receive focus.
     *
     * <p>
     * Changing focusability invalidates the current style because focusable controls are often styled
     * differently from non-focusable ones.
     * </p>
     *
     * @param focusable true to allow this node to receive focus
     */
    public void setFocusable(boolean focusable) {
        if (bits.get(FOCUSABLE_BIT) == focusable) return;

        bits.set(FOCUSABLE_BIT, focusable);
        invalidateStyle();
    }

    /**
     * Returns whether this node is scrollable.
     *
     * @return true if this node responds to scroll interaction
     */
    public boolean isScrollable() {
        return bits.get(SCROLLABLE_BIT);
    }

    /**
     * Sets whether this node is scrollable.
     *
     * <p>
     * Changing this flag invalidates style because scrollable and non-scrollable elements may resolve
     * differently in some themes.
     * </p>
     *
     * @param scrollable true to make the node scrollable
     */
    public void setScrollable(boolean scrollable) {
        if (bits.get(SCROLLABLE_BIT) == scrollable) return;

        bits.set(SCROLLABLE_BIT, scrollable);
        invalidateStyle();
    }

    /**
     * Returns whether this node is currently pressed.
     *
     * @return true if the node is pressed
     */
    public boolean isPressed() {
        return bits.get(PRESSED_BIT);
    }

    /**
     * Sets whether this node is currently pressed.
     *
     * <p>
     * Press state is commonly used by interactive controls such as buttons, sliders, and checkboxes.
     * When the state changes, cached styling is invalidated.
     * </p>
     *
     * @param pressed true to mark the node as pressed
     */
    public void setPressed(boolean pressed) {
        if (bits.get(PRESSED_BIT) == pressed) return;

        bits.set(PRESSED_BIT, pressed);
        invalidateStyle();
    }

    /**
     * Returns whether this node is currently focused.
     *
     * @return true if the node is focused
     */
    public boolean isFocused() {
        return bits.get(FOCUSED_BIT);
    }

    /**
     * Sets whether this node is currently focused.
     *
     * <p>
     * Focus state affects keyboard routing and usually visual appearance, so style is invalidated
     * when this value changes.
     * </p>
     *
     * @param focused true to mark the node as focused
     */
    public void setFocused(boolean focused) {
        if (bits.get(FOCUSED_BIT) == focused) return;

        bits.set(FOCUSED_BIT, focused);
        invalidateStyle();
    }

    /**
     * Returns whether this node is selected.
     *
     * @return true if the node is selected
     */
    public boolean isSelected() {
        return bits.get(SELECTED_BIT);
    }

    /**
     * Sets whether this node is selected.
     *
     * <p>
     * Selection is useful for list items, tabs, menus, and similar components.
     * Style is invalidated when the state changes.
     * </p>
     *
     * @param selected true to mark the node as selected
     */
    public void setSelected(boolean selected) {
        if (bits.get(SELECTED_BIT) == selected) return;

        bits.set(SELECTED_BIT, selected);
        invalidateStyle();
    }

    /**
     * Returns whether this node is checked.
     *
     * @return true if the node is checked
     */
    public boolean isChecked() {
        return bits.get(CHECKED_BIT);
    }

    /**
     * Sets whether this node is checked.
     *
     * <p>
     * Checked state is typically used by controls such as checkboxes, toggles, or custom on/off
     * controls. Cached style is invalidated when the state changes.
     * </p>
     *
     * @param checked true to mark the node as checked
     */
    public void setChecked(boolean checked) {
        if (bits.get(CHECKED_BIT) == checked) return;

        bits.set(CHECKED_BIT, checked);
        invalidateStyle();
    }

    /**
     * Returns whether this node is currently being dragged.
     *
     * @return true if the node is dragging
     */
    public boolean isDragging() {
        return bits.get(DRAGGING_BIT);
    }

    /**
     * Sets whether this node is currently being dragged.
     *
     * <p>
     * Dragging state can affect both behavior and style, so cached styling is invalidated when it changes.
     * </p>
     *
     * @param dragging true to mark the node as dragging
     */
    public void setDragging(boolean dragging) {
        if (bits.get(DRAGGING_BIT) == dragging) return;

        bits.set(DRAGGING_BIT, dragging);
        invalidateStyle();
    }

    /**
     * Resolves and returns the current style for this node.
     *
     * <p>
     * The result is cached after the first resolution. The cache is invalidated whenever state,
     * theme, style name, or overrides change. If no theme is available, this method returns null.
     * </p>
     *
     * @return the resolved style for this node, or null if no theme is available
     */
    protected final ResolvedStyle getStyle() {
        ThemeData theme = getTheme();

        if (theme == null) return null;

        if (cachedStyle == null) cachedStyle = theme.resolve(getClass(), styleName, getStyleState(), styleOverrides);

        return cachedStyle;
    }

    /**
     * Builds and returns the style state describing the current interactive state of this node.
     *
     * <p>
     * This method maps the node's state bits into a {@link StyleState} object which is then used
     * during theme resolution. Subclasses may override this if they need to inject custom style-state
     * behavior on top of the default flags.
     * </p>
     *
     * @return a style state representing the current node state
     */
    protected StyleState getStyleState() {
        StyleState state = new StyleState();
        state.set(StyleState.HOVERED, isHovered());
        state.set(StyleState.PRESSED, isPressed());
        state.set(StyleState.FOCUSED, isFocused());
        state.set(StyleState.DISABLED, isDisabled());
        state.set(StyleState.CHECKED, isChecked());
        state.set(StyleState.SELECTED, isSelected());
        state.set(StyleState.DRAGGING, isDragging());
        return state;
    }

    /**
     * Applies a style override for this node.
     *
     * <p>
     * Overrides are stored locally in a {@link StyleMap} and layered on top of the resolved theme
     * values. Setting an override invalidates the cached style so the new value can be used.
     * </p>
     *
     * @param key   the style key to override
     * @param value the value to store for that key
     * @param <T>   the value type associated with the style key
     */
    public final <T> void setStyle(StyleKey<T> key, T value) {
        if (styleOverrides == null) styleOverrides = new StyleMap();

        styleOverrides.set(key, value);
        invalidateStyle();
    }

    /**
     * Removes a previously applied local style override.
     *
     * <p>
     * If no override map exists, the method does nothing.
     * </p>
     *
     * @param key the style key whose override should be removed
     */
    public final void clearStyle(StyleKey<?> key) {
        if (styleOverrides == null) return;

        styleOverrides.remove(key);
        invalidateStyle();
    }

    /**
     * Removes all local style overrides from this node.
     *
     * <p>
     * If no overrides exist, the method does nothing. Clearing overrides invalidates the cached style
     * so theme values can be re-resolved without local replacements.
     * </p>
     */
    public final void clearStyles() {
        if (styleOverrides == null) return;

        styleOverrides.clear();
        invalidateStyle();
    }

    /**
     * Returns the effective theme used by this node.
     *
     * <p>
     * If a local theme has been assigned directly to this node, that theme is returned.
     * Otherwise, the method asks the parent container for its effective theme, allowing theme
     * inheritance through the UI tree.
     * </p>
     *
     * @return the effective theme, or null if no theme is available
     */
    public final ThemeData getTheme() {
        if (theme != null) return theme;

        return parent != null ? parent.getTheme() : null;
    }

    /**
     * Returns the local theme assigned directly to this node.
     *
     * <p>
     * Unlike {@link #getTheme()}, this method does not inherit from the parent.
     * </p>
     *
     * @return the local theme assigned to this node, or null if none is assigned
     */
    public final ThemeData getLocalTheme() {
        return theme;
    }

    /**
     * Assigns a local theme to this node.
     *
     * <p>
     * Changing the theme invalidates style resolution for this node and its descendants, and also
     * marks layout dirty so any theme-dependent layout values can be reapplied.
     * </p>
     *
     * @param theme the theme to assign locally to this node
     */
    public final void setTheme(ThemeData theme) {
        if (this.theme == theme) return;

        this.theme = theme;
        invalidateStyleTree();
        markLayoutDirty();
    }

    /**
     * Returns the optional named style used when resolving theme rules for this node.
     *
     * @return the style name, or null if none is set
     */
    public final String getStyleName() {
        return styleName;
    }

    /**
     * Sets the optional named style used during theme resolution.
     *
     * <p>
     * Changing the style name invalidates the cached style and marks layout dirty.
     * </p>
     *
     * @param styleName the style name to assign to this node
     */
    public final void setStyleName(String styleName) {
        if (this.styleName == null ? styleName == null : this.styleName.equals(styleName)) return;

        this.styleName = styleName;
        invalidateStyle();
        markLayoutDirty();
    }

    /**
     * Invalidates the cached resolved style for this node.
     *
     * <p>
     * The next call to {@link #getStyle()} will perform a fresh resolution.
     * </p>
     */
    protected void invalidateStyle() {
        cachedStyle = null;
    }

    /**
     * Invalidates cached styles for this node and, in subclasses, potentially for descendant nodes.
     *
     * <p>
     * The base implementation only invalidates this node. Containers may override this method
     * to propagate invalidation to children.
     * </p>
     */
    protected void invalidateStyleTree() {
        invalidateStyle();
    }

    /**
     * Attaches a native Yoga node for this UI node using the supplied Yoga configuration.
     *
     * <p>
     * If a Yoga node is already attached, this method does nothing. Once attached, the node calls
     * {@link #onNodeCreated(long)} and marks itself layout dirty.
     * </p>
     *
     * @param config the native Yoga configuration pointer used to create the Yoga node
     */
    final void attachToRoot(long config) {
        if (yogaMemoryAddress != UIConstants.NULL) return;

        yogaMemoryAddress = Yoga.YGNodeNewWithConfig(config);
        onNodeCreated(yogaMemoryAddress);
        markLayoutDirty();
    }

    /**
     * Detaches and frees the native Yoga node owned by this UI node.
     *
     * <p>
     * If no Yoga node is attached, this method does nothing. Before freeing the node, it calls
     * {@link #onNodeWillDestroy(long)} so subclasses can react to the teardown.
     * </p>
     */
    final void detachFromRoot() {
        if (yogaMemoryAddress == UIConstants.NULL) return;

        onNodeWillDestroy(yogaMemoryAddress);
        Yoga.YGNodeFree(yogaMemoryAddress);
        yogaMemoryAddress = UIConstants.NULL;
    }

    /**
     * Called immediately after the Yoga node has been created for this UI node.
     *
     * <p>
     * The default implementation simply calls {@link #onCreate()}. Subclasses may override this
     * method if they need direct access to the Yoga node pointer during creation.
     * </p>
     *
     * @param yogaNode the native Yoga node pointer that was created
     */
    protected void onNodeCreated(long yogaNode) {
        onCreate();
    }

    /**
     * Called immediately before the Yoga node owned by this UI node is destroyed.
     *
     * <p>
     * The default implementation simply calls {@link #onDestroy()}. Subclasses may override this
     * method if they need direct access to the Yoga node pointer during destruction.
     * </p>
     *
     * @param yogaNode the native Yoga node pointer that is about to be destroyed
     */
    protected void onNodeWillDestroy(long yogaNode) {
        onDestroy();
    }

    /**
     * Returns the parent container of this node.
     *
     * @return the parent container, or null if this node is not attached to one
     */
    public final UIContainer getParent() {
        return parent;
    }

    /**
     * Assigns the parent container for this node.
     *
     * <p>
     * This method is intended for internal UI tree management. Assigning a new parent invalidates
     * the style tree and marks layout dirty because inherited theme and coordinate relationships
     * may have changed.
     * </p>
     *
     * @param parent the new parent container
     */
    final void setParent(UIContainer parent) {
        this.parent = parent;
        invalidateStyleTree();
        markLayoutDirty();
    }

    /**
     * Returns the UI root that owns this node.
     *
     * @return the root UI, or null if this node is not attached to a root
     */
    public UIRoot getRoot() {
        return root;
    }

    /**
     * Assigns the UI root that owns this node.
     *
     * <p>
     * This method is used internally when attaching nodes into a UI tree. Assigning a new root
     * invalidates the style tree and marks layout dirty because inherited theme context and render
     * space may have changed.
     * </p>
     *
     * @param root the new root UI object
     */
    final void setRoot(UIRoot root) {
        this.root = root;
        invalidateStyleTree();
        markLayoutDirty();
    }

    /**
     * Returns the native Yoga node memory address associated with this UI node.
     *
     * @return the native Yoga node pointer, or {@link UIConstants#NULL} if none exists
     */
    public final long getYogaMemoryAddress() {
        return yogaMemoryAddress;
    }

    /**
     * Returns whether this node currently has a native Yoga node attached.
     *
     * @return true if a Yoga node exists for this UI node
     */
    public final boolean hasYogaNode() {
        return yogaMemoryAddress != UIConstants.NULL;
    }

    /**
     * Marks this node's layout as dirty.
     *
     * <p>
     * This causes the node to be considered out of date with respect to layout. The dirty state is
     * also propagated upward to the parent so ancestor containers know they must participate in a
     * future layout pass.
     * </p>
     */
    public void markLayoutDirty() {
        bits.set(LAYOUT_DIRTY_BIT, true);

        if (parent != null) parent.markLayoutDirty();
    }

    /**
     * Clears the layout-dirty state on this node.
     *
     * <p>
     * This method is intended for internal use after layout has been successfully recomputed.
     * </p>
     */
    final void clearLayoutDirty() {
        bits.set(LAYOUT_DIRTY_BIT, false);
    }

    /**
     * Updates the cached bounds of this node from the current Yoga layout results.
     *
     * <p>
     * If the node does not have a Yoga node attached, this method does nothing. Otherwise it reads
     * the computed width and height from Yoga and updates the cached {@link #bounds}. The render-space
     * position is also recomputed using {@link #getRenderX()} and {@link #getRenderY()}.
     * </p>
     */
    final void updateComputedLayout() {
        if (yogaMemoryAddress == UIConstants.NULL) return;

        float width = Yoga.YGNodeLayoutGetWidth(yogaMemoryAddress);
        float height = Yoga.YGNodeLayoutGetHeight(yogaMemoryAddress);

        bounds.setSize(width, height);
        bounds.setPosition(getRenderX(), getRenderY());
    }

    /**
     * Returns the render-space X position of this node.
     *
     * <p>
     * This value is based on the node's absolute X position in the UI tree.
     * </p>
     *
     * @return the render-space X coordinate
     */
    public final float getRenderX() {
        return getAbsoluteX();
    }

    /**
     * Returns the render-space Y position of this node.
     *
     * <p>
     * Yoga layout uses a top-based coordinate system, while rendering in the engine uses a bottom-left
     * style coordinate system. This method converts the node's absolute Yoga position into render space.
     * </p>
     *
     * @return the render-space Y coordinate
     */
    public final float getRenderY() {
        return getRenderSpaceHeight() - getAbsoluteY() - getHeight();
    }

    /**
     * Returns the node's local X position from Yoga layout results.
     *
     * @return the local X position relative to the parent
     */
    public final float getX() {
        return Yoga.YGNodeLayoutGetLeft(yogaMemoryAddress);
    }

    /**
     * Returns the node's local Y position from Yoga layout results.
     *
     * @return the local Y position relative to the parent
     */
    public final float getY() {
        return Yoga.YGNodeLayoutGetTop(yogaMemoryAddress);
    }

    /**
     * Returns the computed width of this node.
     *
     * <p>
     * This value is read from the cached bounds, which are updated from Yoga layout results.
     * </p>
     *
     * @return the node width
     */
    public final float getWidth() {
        return bounds.getWidth();
    }

    /**
     * Returns the computed height of this node.
     *
     * <p>
     * This value is read from the cached bounds, which are updated from Yoga layout results.
     * </p>
     *
     * @return the node height
     */
    public final float getHeight() {
        return bounds.getHeight();
    }

    /**
     * Returns the absolute X position of this node in the UI tree.
     *
     * <p>
     * This is computed by accumulating this node's local X position with all ancestor X positions.
     * </p>
     *
     * @return the absolute X position
     */
    public final float getAbsoluteX() {
        return parent == null ? getX() : parent.getAbsoluteX() + getX();
    }

    /**
     * Returns the absolute Y position of this node in the UI tree.
     *
     * <p>
     * This is computed by accumulating this node's local Y position with all ancestor Y positions.
     * </p>
     *
     * @return the absolute Y position
     */
    public final float getAbsoluteY() {
        return parent == null ? getY() : parent.getAbsoluteY() + getY();
    }

    /**
     * Returns whether the given point lies inside this node's cached bounds.
     *
     * <p>
     * The coordinates are expected to already be in render space.
     * </p>
     *
     * @param px the point X coordinate
     * @param py the point Y coordinate
     * @return true if the point lies inside the node bounds
     */
    public boolean contains(float px, float py) {
        return bounds.contains(px, py);
    }

    /**
     * Returns the render-space height used for converting Yoga coordinates into drawing coordinates.
     *
     * <p>
     * If a root with a viewport exists, the viewport's world height is used. Otherwise the root height
     * is used if it is greater than zero. If neither is available, the method falls back to the current
     * window height.
     * </p>
     *
     * @return the height of the render space
     */
    protected float getRenderSpaceHeight() {
        if (root != null) {
            if (root.getViewport() != null) return root.getViewport().getWorldHeight();

            if (root.getHeight() > 0f) return root.getHeight();
        }

        return Window.getHeight();
    }

    /**
     * Called when this node is created or when its Yoga node is attached.
     *
     * <p>
     * Subclasses implement this method to perform initialization that depends on the node being
     * fully created and ready to enter the UI tree.
     * </p>
     */
    public abstract void onCreate();

    /**
     * Called when this node is about to be destroyed or detached from its Yoga node.
     *
     * <p>
     * Subclasses implement this method to release owned resources, unregister temporary state,
     * or perform cleanup before the node is removed.
     * </p>
     */
    public abstract void onDestroy();

    /**
     * Updates this node for the current frame.
     *
     * <p>
     * Subclasses implement this method to perform time-based behavior such as animations,
     * cursor blinking, hover timers, interpolation, or custom logic.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous update
     */
    public abstract void update(float delta);

    /**
     * Draws this node using the supplied texture batch.
     *
     * <p>
     * Subclasses implement this method to render their current visual appearance using the
     * node's computed render position and size.
     * </p>
     *
     * @param batch the texture batch used for drawing
     */
    public abstract void draw(TextureBatch batch);

    /**
     * Applies this node's {@link Layout} data to its native Yoga node.
     *
     * <p>
     * This method delegates to {@link UIConstants#applyLayout(long, Layout)} and is typically used
     * during layout passes to push the node's desired layout properties into Yoga.
     * </p>
     */
    protected void applyLayout() {
        UIConstants.applyLayout(yogaMemoryAddress, layout);
    }

    /**
     * Called after layout has been applied and computed.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method when they need to
     * react after layout results are available, such as caching positions, refreshing visual data,
     * or updating child-dependent measurements.
     * </p>
     */
    protected void afterLayout() {
    }

    /**
     * Returns the tooltip assigned to this node.
     *
     * @return the tooltip, or null if none is assigned
     */
    public Tooltip getTooltip() {
        return tooltip;
    }

    /**
     * Assigns a tooltip object directly to this node.
     *
     * @param tooltip the tooltip to assign, or null to remove it
     */
    public void setTooltip(Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    /**
     * Assigns tooltip text to this node.
     *
     * <p>
     * If the provided text is null or blank, the tooltip is removed. Otherwise a new
     * {@link Tooltip} is created if necessary, or the existing tooltip text is updated.
     * </p>
     *
     * @param text the tooltip text to assign
     */
    public void setTooltip(String text) {
        if (text == null || text.isBlank()) {
            this.tooltip = null;
            return;
        }

        if (this.tooltip == null) this.tooltip = new Tooltip(text);
        else this.tooltip.text(text);
    }
}