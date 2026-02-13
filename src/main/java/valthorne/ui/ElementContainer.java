package valthorne.ui;

import valthorne.graphics.DrawFunction;
import valthorne.math.geometry.Rectangle;
import valthorne.viewport.Viewport;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Represents a container for storing and managing multiple {@link Element} instances.
 * An ElementContainer can hold child elements, enabling hierarchical element structuring.
 * It provides functionalities such as adding, removing, retrieving, and locating elements
 * based on position. It also supports updating and rendering all child elements.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public abstract class ElementContainer extends Element {

    private final DrawFunction draw = this::drawElements;
    protected Element[] elements = new Element[8]; // Array to store child elements with initial capacity of 8
    protected int size; // Current number of elements in the container

    /**
     * Adds a new {@link Element} to the container. If the internal storage
     * array is full, it will resize the array to accommodate additional elements.
     * The added element is set as a child of this container and is assigned an
     * index based on its position in the container.
     *
     * @param element the element to be added to this container. Must not be null.
     */
    public void addElement(Element element) {
        if (size == elements.length)
            elements = Arrays.copyOf(elements, elements.length * 2);

        element.setParent(this);
        element.setUI(this.getUI());
        element.setIndex(size);
        elements[size++] = element;
        onAdd(element);
        element.onAdd();
    }

    /**
     * Removes the specified {@link Element} from this container. If the element to
     * be removed is not the last element in the container, the last element in the
     * container will be swapped into the position of the removed element to
     * maintain continuity in the array.
     * <p>
     * After removal, the element's parent reference is set to null, and its index
     * is set to -1.
     *
     * @param element the {@link Element} to be removed from this container. Must
     *                not be null and must be a valid element in this container.
     */
    public void removeElement(Element element) {
        int index = element.getIndex();
        if (index < 0 || index >= size)
            return;

        int last = size - 1;

        if (index != last) {
            Element swap = elements[last];
            elements[index] = swap;
            swap.setIndex(index);
        }

        elements[last] = null;
        element.setUI(null);
        element.setParent(null);
        element.setIndex(-1);
        size--;

        onRemove(element);
        element.onRemove();
    }

    /**
     * Recursively searches for an {@link Element} at a given position (x, y) within
     * the container and its child elements. The method includes an optional
     * 'click' flag to determine if click-through elements should be considered.
     *
     * @param x     the x-coordinate to search for an element.
     * @param y     the y-coordinate to search for an element.
     * @param flags the flags required in an element to be found.
     * @return the {@link Element} located at the specified position, or
     * {@code null} if no element exists at that position.
     */
    public Element findElementAt(float x, float y, byte flags) {
        // 1) If we have clip bounds, do not allow hits outside of it.
        Rectangle clip = this.getClipBounds();
        if (clip != null && !clip.contains(x, y)) {
            return null;
        }

        // 2) Search children from top-most to bottom-most.
        for (int i = size - 1; i >= 0; i--) {
            Element child = elements[i];

            if (child == null || child.isHidden())
                continue;

            // If the child has its own clip bounds, enforce it before descending/hit.
            Rectangle childClip = child.getClipBounds();
            if (childClip != null && !childClip.contains(x, y)) {
                continue;
            }

            if (child instanceof ElementContainer container) {
                Element hit = container.findElementAt(x, y, flags);
                if (hit != null && (hit.getFlags() & flags) == flags) {
                    return hit;
                }
            }

            if (child.inside(x, y) && (child.getFlags() & flags) == flags)
                return child;
        }

        // 3) Finally, consider the container itself (still clip-guarded above).
        if (inside(x, y) && (getFlags() & flags) == flags)
            return this;

        return null;
    }

    @Override
    public void update(float delta) {
        for (int i = 0; i < size; i++) {
            Element element = elements[i];
            if (element == null || element.isHidden())
                continue;
            element.update(delta);
        }
    }

    @Override
    public void draw() {
        Viewport viewport = getUI().getViewport();
        if (this.getClipBounds() != null) {
            Rectangle clip = this.getClipBounds();
            viewport.applyScissor(clip.getX(), clip.getY(), clip.getWidth(), clip.getHeight(), draw);
        } else {
            drawElements();
        }
    }

    private void drawElements() {
        Viewport viewport = getUI().getViewport();
        for (int i = 0; i < size; i++) {
            Element element = elements[i];
            if (element == null || element.isHidden())
                continue;

            if (element.getClipBounds() != null) {
                Rectangle clip = element.getClipBounds();
                viewport.applyScissor(clip.getX(), clip.getY(), clip.getWidth(), clip.getHeight(), element);
            } else {
                element.draw();
            }
        }
    }

    @Override
    public void layout() {
        super.layout();

        for (int i = 0; i < size; i++) {
            Element element = elements[i];
            if (element == null || element.isHidden())
                continue;

            element.layout();

            if (element instanceof ElementContainer container) {
                container.layout();
            }
        }
    }

    /**
     * Invoked when a new {@link Element} is added to the container. This method
     * can be used to perform additional setup or initialization specific to the
     * newly added element. The implementation may also handle internal states or
     * dependencies associated with the container and its elements.
     *
     * @param element the {@link Element} to be added. Must not be null.
     */
    protected abstract void onAdd(Element element);

    /**
     * Invoked when an {@link Element} is removed from the container. This method
     * can be used to perform any cleanup or finalization tasks specific to the
     * element being removed, such as releasing resources or updating internal states.
     *
     * @param element the {@link Element} that is being removed. Must not be null.
     */
    protected abstract void onRemove(Element element);

    /**
     * Provides an unmodifiable collection of all {@link Element} instances
     * contained within this container. The returned collection reflects the
     * current state of the container and cannot be modified directly.
     *
     * @return a collection of {@link Element} instances currently in this container.
     */
    public Collection<Element> getElements() {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }
}
