package valthorne.ui;

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
        if (size == elements.length) {
            elements = Arrays.copyOf(elements, elements.length * 2);
        }

        element.setParent(this);
        element.setUI(this.getUI());
        element.setIndex(size);
        elements[size++] = element;
        onAdd(element);
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
    }

    /**
     * Searches for and returns the top-most {@code Element} located at the specified coordinates (x, y)
     * within this container. The method traverses through the child elements in reverse order, checking
     * for visibility, enabled state, and whether the specified coordinates are within the bounds of
     * each element.
     *
     * @param x the x-coordinate to check.
     * @param y the y-coordinate to check.
     * @return the top-most {@code Element} at the specified coordinates, or {@code null} if no element
     * exists at that position.
     */
    public Element findElementAt(float x, float y) {
        for (int i = size - 1; i >= 0; i--) {
            Element child = elements[i];

            if (child == null || child.isHidden() || child.isDisabled())
                continue;

            if (child instanceof ElementContainer container) {
                Element hit = container.findElementAt(x, y);
                if (hit != null)
                    return hit;
            }

            if (child.inside(x, y))
                return child;
        }

        return inside(x, y) ? this : null;
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
        for (int i = 0; i < size; i++) {
            Element element = elements[i];
            if (element == null || element.isHidden())
                continue;
            element.draw();
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
