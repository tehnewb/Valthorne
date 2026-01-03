package valthorne.ui;

import valthorne.event.events.*;

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
public abstract class Element implements Dimensional {

    private static final byte HIDDEN = 1;      // Flag indicating if element is not visible
    private static final byte DISABLED = 1 << 1; // Flag indicating if element is non-interactive
    private static final byte HOVERED = 1 << 2;  // Flag indicating if mouse is over element
    private static final byte FOCUSED = 1 << 3;   // Flag indicating if element has keyboard focus
    private static final byte PRESSED = 1 << 4;   // Flag indicating if element is being clicked
    private static final byte FOCUSABLE = 1 << 5; // Flag indicating if element can receive focus

    private Element parent;     // Reference to parent element in hierarchy
    private int index = -1;     // Index of element within its parent container
    private byte flags;         // Bit flags storing element state

    protected float x, y;  // The position of the element in 2D space
    protected float width, height;  // The dimensions (width and height) of the element

    private Layout layout;

    private UI ui;

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
     * Sets the index of this element.
     *
     * @param index the index to assign to this element
     */
    protected void setIndex(int index) {
        this.index = index;
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
     * Retrieves the parent element of this element.
     *
     * @return the parent element of this element, or null if this element has no parent
     */
    public Element getParent() {
        return parent;
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
     * Sets the size of the object by specifying the width and height.
     *
     * @param width  the width of the object
     * @param height the height of the object
     */
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
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

    /**
     * Retrieves the UI instance associated with this component or context.
     *
     * @return the UI instance
     */
    public UI getUI() {
        return ui;
    }

    /**
     * Retrieves the current layout.
     *
     * @return the current Layout object
     */
    public Layout getLayout() {
        if (layout == null)
            layout = new Layout();
        return layout;
    }

    /**
     * Sets the layout to be used.
     *
     * @param layout the layout object to be set for this component
     */
    public void setLayout(Layout layout) {
        this.layout = layout;
    }
}
