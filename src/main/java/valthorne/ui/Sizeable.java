package valthorne.ui;

/**
 * The Sizeable interface defines a contract for objects that have width and height properties.
 * Implementing classes are expected to provide mechanisms to retrieve their dimensions.
 * <p>
 * Methods in this interface are typically used for layout constraints, size calculations,
 * or bounding operations on objects that have spatial characteristics.
 * <p>
 * It is designed to be implemented by classes that require width and height attributes
 * to define their size properties.
 *
 * @author Albert Beaupre
 * @since December 24th, 2025
 */
public interface Sizeable {

    /**
     * Retrieves the width of the object implementing this method.
     *
     * @return the width of the object as a float value.
     */
    float getWidth();

    /**
     * Retrieves the height of the object implementing this method.
     *
     * @return the height of the object as a float value.
     */
    float getHeight();
}
