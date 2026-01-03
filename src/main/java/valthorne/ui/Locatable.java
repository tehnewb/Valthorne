package valthorne.ui;

/**
 * The Locatable interface provides a contract for classes that represent objects with
 * a specific position in a two-dimensional coordinate system.
 * Implementing classes are required to provide methods to retrieve the x and y
 * coordinates of the object.
 *
 * @author Albert Beaupre
 * @since December 24th, 2025
 */
public interface Locatable {

    /**
     * Retrieves the x-coordinate of the object in a two-dimensional space.
     *
     * @return the x-coordinate as a float.
     */
    float getX();

    /**
     * Retrieves the y-coordinate of the object in a two-dimensional space.
     *
     * @return the y-coordinate as a float.
     */
    float getY();

}
