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

    /**
     * Sets the size of the object by specifying its width and height.
     * This method adjusts both the width and height properties to the provided values.
     * It is typically used to define or update the spatial dimensions of the object.
     *
     * @param width  the new width value for the object, specified as a float. It represents the horizontal dimension.
     * @param height the new height value for the object, specified as a float. It represents the vertical dimension.
     */
    void setSize(float width, float height);

    /**
     * Sets the width of the object.
     * This method adjusts the width property, which might be used in layout calculations or spatial transformations.
     *
     * @param width the new width value for the object, specified as a float. It represents the horizontal dimension.
     */
    void setWidth(float width);

    /**
     * Sets the height of the object.
     * This method adjusts the height property, which might be used in layout calculations or spatial transformations.
     *
     * @param height the new height value for the object, specified as a float. It represents the vertical dimension.
     */
    void setHeight(float height);
}
