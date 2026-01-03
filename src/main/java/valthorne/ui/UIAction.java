package valthorne.ui;

/**
 * Represents a user interface action to be performed on a given element.
 * This interface defines a contract for executing an action on a specific type of element.
 *
 * @param <E> the type of element on which the action will be performed,
 *            must extend the Element class.
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public interface UIAction<E extends Element> {

    /**
     * Performs the defined action on the given element. This method serves as the
     * core functionality of this interface, allowing an implementation to apply
     * specific behavior to the provided element.
     *
     * @param element the element on which the action is to be performed. Must be of type E,
     *                which extends the base class Element.
     */
    void perform(E element);

}
