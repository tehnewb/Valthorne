package valthorne.ui;

import valthorne.math.Vector2f;

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

    /**
     * Sets the position of the object within a two-dimensional coordinate system.
     *
     * @param x the x-coordinate to set for the object.
     * @param y the y-coordinate to set for the object.
     */
    void setPosition(float x, float y);

    /**
     * Sets the x-coordinate of an object in a two-dimensional space.
     *
     * @param x the x-coordinate to set for the object.
     */
    void setX(float x);

    /**
     * Sets the y-coordinate of the object in a two-dimensional space.
     *
     * @param y the y-coordinate to set for the object.
     */
    void setY(float y);

}
