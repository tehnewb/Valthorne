package valthorne.ui;

import valthorne.Window;
import valthorne.event.events.*;
import valthorne.graphics.DrawFunction;
import valthorne.math.geometry.Rectangle;

/**
 * Abstract base class representing a user interface element in a graphical user interface system.
 * <p>
 * An Element is the fundamental building block for creating interactive UI components. It maintains
 * its state through a series of bit flags that track various conditions such as:
 * <ul>
 *   <li>Visibility (hidden/shown)</li>
 *   <li>Interactivity (enabled/disabled)</li>
 *   <li>Input focus</li>
 *   <li>Mouse interaction (hover, press)</li>
 *   <li>Focus capability</li>
 * </ul>
 * <p>
 * Elements can be organized in a hierarchical structure where each element may have a parent
 * element and can be positioned within its parent using an index. This hierarchy is particularly
 * useful for implementing complex UI layouts and managing event propagation.
 * <p>
 * The class provides:
 * <ul>
 *   <li>Abstract methods for rendering and updating the element</li>
 *   <li>Event handling for mouse and keyboard interactions</li>
 *   <li>State management through bit flags</li>
 *   <li>Parent-child relationship management</li>
 *   <li>Spatial hit testing through the inside() method</li>
 * </ul>
 * <p>
 * Subclasses must implement the {@link #update(float)} and {@link #draw()} methods to define
 * their specific behavior and appearance.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public abstract class Element implements Dimensional, DrawFunction {

    public static final byte HIDDEN = 1;      // Flag indicating if element is not visible
    public static final byte DISABLED = 1 << 1; // Flag indicating if element is non-interactive
    public static final byte HOVERED = 1 << 2;  // Flag indicating if mouse is over element
    public static final byte FOCUSED = 1 << 3;   // Flag indicating if element has keyboard focus
    public static final byte PRESSED = 1 << 4;   // Flag indicating if element is being clicked
    public static final byte FOCUSABLE = 1 << 5; // Flag indicating if element can receive focus
    public static final byte CLICKABLE = 1 << 6; // Flag indicating if element can be clicked through
    public static final byte SCROLLABLE = (byte) (1 << 7); // Flag indicating if element is scrollable

    private float localX, localY;
    protected float x, y;  // The position of the element in 2D space
    protected float width, height;  // The dimensions (width and height) of the element
    private Element parent;     // Reference to parent element in hierarchy
    private int index = -1;     // Index of element within its parent container
    private byte flags = CLICKABLE;         // Bit flags storing element state
    private Layout layout;
    private UI ui;

    private Rectangle clipBounds;

    /**
     * Updates the state of the element. This method is called to perform
     * time-dependent updates, such as animations or other dynamic behaviors.
     * Subclasses must provide the implementation for this method.
     *
     * @param delta the time elapsed since the last update, in seconds
     */
    public abstract void update(float delta);

    /**
     * Renders the visual representation of this element.
     * <p>
     * This method is typically invoked to draw the element on the screen, including
     * any styles, content, or visual decorations associated with it. Subclasses must
     * provide their specific implementation of the drawing behavior for the element.
     * <p>
     * The specific rendering logic can vary depending on the element type and its
     * visual representation. This method must handle all necessary graphics operations
     * to ensure that the element is properly displayed.
     */
    public abstract void draw();

    /**
     * Handles the key press event for the element.
     * This method is triggered when a key is pressed while the element has focus or is otherwise
     * eligible to receive keyboard input. The implementation can be customized to handle specific
     * key presses or modify the state of the element based on the input.
     *
     * @param event the KeyPressEvent object containing information about the key press,
     *              including the pressed key and any associated modifier keys
     */
    public void onKeyPress(KeyPressEvent event) {}

    /**
     * Handles the key release event for the element.
     * This method is triggered when a key is released while the element has focus or is otherwise
     * eligible to receive keyboard input. The implementation can be customized to handle specific
     * key releases or modify the state of the element based on the input.
     *
     * @param event the KeyReleaseEvent object containing information about the key release,
     *              including the released key and any associated modifier keys
     */
    public void onKeyRelease(KeyReleaseEvent event) {}

    /**
     * Handles the mouse press event for the element.
     * This method is called when a mouse button is pressed while the cursor is over the element.
     * Implementations can define specific behavior, such as updating the element's state
     * or triggering custom actions in response to the event.
     *
     * @param event the MousePressEvent object containing details about the mouse press,
     *              including the button pressed, any associated modifiers, and the mouse
     *              cursor's position (x and y coordinates) at the time of the event
     */
    public void onMousePress(MousePressEvent event) {}

    /**
     * Handles the mouse release event for the element.
     * This method is invoked when a mouse button is released while the cursor is over the element.
     * The implementation can define behavior such as updating the element's state
     * or triggering specific actions in response to the event.
     *
     * @param event the MouseReleaseEvent object containing details about the mouse release,
     *              including the button released, any associated modifiers, and the mouse
     *              cursor's position (x and y coordinates) at the time of the event
     */
    public void onMouseRelease(MouseReleaseEvent event) {}

    /**
     * Handles the mouse drag event for the element.
     * This method is invoked when the user drags the mouse while holding a button
     * and the cursor is over the element. The implementation can define specific
     * behavior, such as updating the element's state or triggering custom actions
     * in response to the drag event.
     *
     * @param event the MouseDragEvent object containing details about the mouse drag,
     *              including the button being held, any associated modifiers, the initial
     *              cursor position (fromX, fromY), and the final cursor position (toX, toY)
     */
    public void onMouseDrag(MouseDragEvent event) {}

    /**
     * Handles the mouse move event for the element.
     * This method is triggered when the mouse cursor moves over the element.
     * Implementations can define specific behavior such as tracking the cursor position
     * or updating the element's state based on the movement.
     *
     * @param event the MouseMoveEvent object containing details about the mouse movement,
     *              including the starting and ending cursor positions (fromX, fromY, toX, toY)
     *              and any associated modifier keys.
     */
    public void onMouseMove(MouseMoveEvent event) {}

    /**
     * Handles the mouse scroll event for the element.
     * This method is called when a mouse scroll interaction occurs
     * over the element. It processes events such as vertical or horizontal
     * scrolling and can be used to implement specific behaviors like zooming
     * or panning.
     *
     * @param event the MouseScrollEvent object containing details about the scroll interaction,
     *              including the horizontal (xOffset) and vertical (yOffset) scroll amounts
     */
    public void onMouseScroll(MouseScrollEvent event) {}

    /**
     * Handles the actions to be performed when the window is resized.
     *
     * @param event the event object containing details about the window resize action
     */
    public void onWindowResize(WindowResizeEvent event) {}

    /**
     * Determines whether the specified point (x, y) is located within the bounds
     * of the element.
     *
     * @param x the x-coordinate of the point to check
     * @param y the y-coordinate of the point to check
     * @return true if the point is within the bounds of the element; false otherwise
     */
    public boolean inside(float x, float y) {
        if (isHidden()) return false;

        return x >= this.x && x <= this.x + width && y >= this.y && y <= this.y + height;
    }

    /**
     * Updates the layout of the current object based on its layout properties and parent configuration.
     * This method calculates and resolves the position, size, and padding of the object considering
     * the parent's dimensions and window size if no parent is present.
     * <p>
     * Behavior:
     * - If no layout is defined, this method immediately exits.
     * - If a parent is present, its position and dimensions are used as a reference for calculations.
     * - If a parent is not present, the window's dimensions are used as the reference.
     * - Resolves padding values for left, right, top, and bottom based on the layout and parent/window size.
     * - Determines the resolved position (`x`, `y`) and size (`width`, `height`) of the object:
     * - If a layout value is set to "AUTO," fallback values such as `localX`, `localY`, `width`, or `height` are used.
     * - Otherwise, the layout's specified values are resolved using the parent/window dimensions or position.
     * - Adjusts the object's position and size by applying the calculated padding values.
     * <p>
     * The resulting position and size determined during this method's execution are applied to
     * the object using its `setPosition` and `setSize` methods.
     */
    public void layout() {
        boolean hasParent = parent != null;

        if (layout == null) {
            if (hasParent) {
                setPosition(parent.x + localX, parent.y + localY);
                setSize(width, height);
                return;
            }
            return;
        }

        float px = hasParent ? parent.x : 0f;
        float py = hasParent ? parent.y : 0f;
        float pw = hasParent ? parent.width : Window.getWidth();
        float ph = hasParent ? parent.height : Window.getHeight();

        float padL = layout.getLeftPadding().resolve(0, pw, 0);
        float padR = layout.getRightPadding().resolve(0, pw, 0);
        float padT = layout.getTopPadding().resolve(0, ph, 0);
        float padB = layout.getBottomPadding().resolve(0, ph, 0);

        float resolvedX = layout.getX().is(ValueType.AUTO) ? (px + this.localX) : layout.getX().resolve(px, pw, px);
        float resolvedY = layout.getY().is(ValueType.AUTO) ? (py + this.localY) : layout.getY().resolve(py, ph, py);
        float resolvedW = layout.getWidth().is(ValueType.AUTO) ? this.width : layout.getWidth().resolve(0, pw, pw);
        float resolvedH = layout.getHeight().is(ValueType.AUTO) ? this.height : layout.getHeight().resolve(0, ph, ph);

        setPosition(resolvedX, resolvedY);
        setSize(resolvedW + padL + padR, resolvedH + padT + padB);
    }


    /**
     * Retrieves the index of this element.
     *
     * @return the current index of the element
     */
    protected int getIndex() {
        return index;
    }

    /**
     * Sets the index of this element.
     *
     * @param index the index to assign to this element
     */
    protected void setIndex(int index) {
        this.index = index;
    }

    /**
     * Retrieves the parent element of this element.
     *
     * @return the parent element of this element, or null if this element has no parent
     */
    public Element getParent() {
        return parent;
    }

    /**
     * Sets the parent element for this element.
     * This method assigns the specified parent element to the current element,
     * establishing a hierarchical relationship.
     *
     * @param parent the parent element to set for this element
     */
    public void setParent(Element parent) {
        this.parent = parent;
    }

    /**
     * Determines whether this element is focusable.
     * A focusable element can gain input focus, allowing it to receive user interaction
     * such as key events or other input processes.
     *
     * @return true if the element can be focused; false otherwise
     */
    public boolean isFocusable() {
        return (flags & FOCUSABLE) != 0;
    }

    /**
     * Sets whether this element is focusable.
     * A focusable element can gain input focus, allowing it to receive user interaction
     * such as key events or other input processes.
     *
     * @param value true if the element should be focusable; false otherwise
     */
    public void setFocusable(boolean value) {
        if (value) flags |= FOCUSABLE;
        else flags &= ~FOCUSABLE;
    }

    /**
     * Determines whether this element is hidden.
     *
     * @return true if the element is hidden; false otherwise
     */
    public boolean isHidden() {
        return (flags & HIDDEN) != 0;
    }

    /**
     * Sets the hidden state of the element.
     * This method determines whether the element is visible or hidden.
     *
     * @param value true to hide the element; false to make it visible
     */
    public void setHidden(boolean value) {
        if (value) flags |= HIDDEN;
        else flags &= ~HIDDEN;
    }

    /**
     * Determines whether this element is disabled.
     *
     * @return true if the element is disabled; false otherwise
     */
    public boolean isDisabled() {
        return (flags & DISABLED) != 0;
    }

    /**
     * Sets the disabled state of the element.
     * This method updates the internal state of the element to reflect whether it is disabled.
     * Disabled elements are typically non-interactive and may appear visually distinct.
     *
     * @param value true to disable the element; false to enable it.
     */
    public void setDisabled(boolean value) {
        if (value) flags |= DISABLED;
        else flags &= ~DISABLED;
    }

    /**
     * Checks whether this element is currently in the pressed state.
     *
     * @return true if the element is pressed; false otherwise
     */
    public boolean isPressed() {
        return (flags & PRESSED) != 0;
    }

    /**
     * Sets the pressed state of the element.
     * This method updates the internal state of the element to reflect whether it is pressed.
     *
     * @param value true to set the element as pressed; false to unpress the element
     */
    public void setPressed(boolean value) {
        if (value) flags |= PRESSED;
        else flags &= ~PRESSED;
    }

    /**
     * Determines whether the click-through behavior is enabled based on the current flags.
     *
     * @return true if the click-through behavior is enabled, false otherwise.
     */
    public boolean isClickThrough() {
        return (flags & CLICKABLE) == 0;
    }

    /**
     * Sets the click-through behavior for the component.
     * Enables or disables whether the component allows click-through interactions.
     *
     * @param value true to enable click-through behavior, false to disable it
     */
    public void setClickThrough(boolean value) {
        if (value) flags &= ~CLICKABLE;
        else flags |= CLICKABLE;
    }

    /**
     * Determines whether this element is currently in the "hovered" state.
     * The "hovered" state typically indicates that the mouse cursor is over the element.
     *
     * @return true if the element is hovered; false otherwise
     */
    public boolean isHovered() {
        return (flags & HOVERED) != 0;
    }

    /**
     * Sets the "hovered" state of the element.
     * The "hovered" state typically indicates whether the mouse cursor is currently over the element.
     *
     * @param value true to set the element as hovered; false to remove the hovered state
     */
    protected void setHovered(boolean value) {
        if (value) flags |= HOVERED;
        else flags &= ~HOVERED;
    }

    /**
     * Determines whether this element is currently in the focused state.
     * The focused state indicates that the element has input focus
     * and can receive user interactions like key events.
     *
     * @return true if the element is focused; false otherwise
     */
    public boolean isFocused() {
        return (flags & FOCUSED) != 0;
    }

    /**
     * Updates the focused state of this element.
     * The focused state indicates whether this element has input focus
     * and can receive interactions such as key events.
     *
     * @param value true to set the element as focused; false to remove the focused state
     */
    protected void setFocused(boolean value) {
        if (value) flags |= FOCUSED;
        else flags &= ~FOCUSED;
    }

    /**
     * Determines if the object is scrollable based on the internal flags.
     *
     * @return true if the object is scrollable; false otherwise.
     */
    public boolean isScrollable() {
        return (flags & SCROLLABLE) != 0;
    }

    /**
     * Sets the scrollable property of the object. If set to true, the object will
     * allow scrolling functionality. If set to false, scrolling will be disabled.
     *
     * @param value a boolean indicating whether the object should be scrollable (true)
     *              or not (false)
     */
    public void setScrollable(boolean value) {
        if (value) flags |= SCROLLABLE;
        else flags &= ~SCROLLABLE;
    }

    /**
     * Retrieves the value of the flags.
     *
     * @return the byte value representing the flags.
     */
    public byte getFlags() {
        return flags;
    }

    /**
     * Retrieves the width of the element.
     *
     * @return the width of the element.
     */
    public float getWidth() {
        return width;
    }

    /**
     * Retrieves the height of the entity.
     *
     * @return the height as a short value.
     */
    public float getHeight() {
        return height;
    }

    /**
     * Sets the size of the object based on the provided width and height values.
     *
     * @param width  the width value, which can be provided in pixels, percentage, or as a special value (AUTO or FILL)
     * @param height the height value, which can be provided in pixels, percentage, or as a special value (AUTO or FILL)
     */
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Sets the clipping bounds for this object.
     *
     * @param clipBounds the rectangle representing the new clipping bounds to be set.
     *                   If null, clipping is disabled.
     */
    public void setClipBounds(Rectangle clipBounds) {
        this.clipBounds = clipBounds;
    }

    /**
     * Retrieves the current clipping bounds.
     *
     * @return a {@code Rectangle} object representing the clipping bounds.
     */
    public Rectangle getClipBounds() {
        return clipBounds;
    }

    /**
     * Sets the position of an object by specifying the x and y coordinates.
     *
     * @param x the x-coordinate of the position
     * @param y the y-coordinate of the position
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;

        if (parent != null) {
            this.localX = x - parent.x;
            this.localY = y - parent.y;
        } else {
            this.localX = x;
            this.localY = y;
        }
    }

    /**
     * Retrieves the x-coordinate value.
     *
     * @return the x-coordinate as a float
     */
    public float getX() {
        return x;
    }

    /**
     * Retrieves the y-coordinate value.
     *
     * @return the y-coordinate as a float
     */
    public float getY() {
        return y;
    }

    /**
     * Retrieves the current layout object.
     *
     * @return the Layout object representing the current layout configuration
     */
    public Layout getLayout() {
        return layout;
    }

    /**
     * Sets the layout for the current object.
     *
     * @param layout the Layout object to be set
     */
    public void setLayout(Layout layout) {
        this.layout = layout;
    }

    /**
     * Retrieves the UI instance associated with this component or context.
     *
     * @return the UI instance
     */
    public UI getUI() {
        return ui;
    }

    /**
     * Sets the UI for this element and propagates the UI to child elements
     * if the object is an instance of ElementContainer.
     *
     * @param ui the UI instance to be set for this element and its children
     */
    public void setUI(UI ui) {
        this.ui = ui;

        if (this instanceof ElementContainer container) {
            for (int i = 0; i < container.size; i++) {
                Element child = container.elements[i];
                if (child != null) {
                    child.setUI(ui);
                }
            }
        }
    }
}
